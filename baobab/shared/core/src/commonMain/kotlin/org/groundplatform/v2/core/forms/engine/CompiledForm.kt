/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.forms.engine

import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EventType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RepeatDef
import groundplatform.v2.forms.ViewComponent
import org.groundplatform.v2.core.forms.xpath.CompiledXPathExpression
import org.groundplatform.v2.core.forms.xpath.InMemorySecondaryInstanceProvider
import org.groundplatform.v2.core.forms.xpath.SecondaryInstanceProvider
import org.groundplatform.v2.core.forms.xpath.XPathEngine
import org.groundplatform.v2.core.forms.xpath.ast.XPathDependency

/**
 * Pre-compiled, immutable representation of a [FormDef] with cached XPath ASTs, indexed schema/view
 * definitions, and a topologically sorted recalculation DAG.
 */
class CompiledForm internal constructor(val formDef: FormDef) {

  /** Canonical primary instance root element name (e.g., `"household"` or `"data"`). */
  val rootName: String = RecordMutator.resolveRootName(formDef)

  /** All accepted root element aliases (`rootName`, `"data"`, `form_id`). */
  val rootAliases: Set<String> = RecordMutator.resolveRootAliases(formDef)

  /** Default inline secondary dataset provider parsed from `FormDef.model.secondary_instances`. */
  val inlineSecondaryInstanceProvider: SecondaryInstanceProvider by lazy {
    InMemorySecondaryInstanceProvider.fromFormDef(formDef)
  }

  /** Cache of compiled XPath expressions keyed by source expression string. */
  private val expressionCache = mutableMapOf<String, CompiledXPathExpression>()

  /** Compiles [expr] or returns the cached [CompiledXPathExpression] (`null` if blank). */
  fun compileExpression(expr: String?): CompiledXPathExpression? {
    val trimmed = expr?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    return expressionCache.getOrPut(trimmed) { XPathEngine.compile(trimmed) }
  }

  /** Schema [FieldDefinition]s indexed by normalized relative path (e.g., `"person/age"`). */
  val schemaByRelativePath: Map<String, FieldDefinition> = buildSchemaIndex()

  /** Pre-compiled [CompiledFieldBinding]s indexed by normalized relative path. */
  val bindingsByRelativePath: Map<String, CompiledFieldBinding> = buildBindingsIndex()

  /**
   * All [CompiledFieldBinding]s sorted in topological dependency order so upstream calculations and
   * parent group relevancy rules evaluate before downstream dependent fields.
   */
  val topologicalBindings: List<CompiledFieldBinding> = computeTopologicalOrder()

  /** Repeat definitions discovered in the view hierarchy, paired with their relative path. */
  val repeatDefsByRelativePath: Map<String, RepeatDef> = buildRepeatIndex()

  /** Actions triggered on `EVENT_VALUE_CHANGED`, keyed by watched source field relative path. */
  val valueChangedActionsBySourcePath: Map<String, List<ActionDef>> = buildValueChangedActionIndex()

  /**
   * Actions triggered on `EVENT_REPEAT_INSERT`, keyed by the target repeat group's relative path.
   */
  val repeatInsertActionsByRepeatPath: Map<String, List<ActionDef>> = buildRepeatInsertActionIndex()

  /** Resolves the [DataType] for a relative path from its binding or schema definition. */
  fun resolveDataType(relativePath: String): DataType {
    val bindingType = bindingsByRelativePath[relativePath]?.binding?.type
    if (bindingType != null && bindingType != DataType.DATA_TYPE_UNSPECIFIED) {
      return bindingType
    }
    val schemaType = schemaByRelativePath[relativePath]?.type
    if (schemaType != null && schemaType != DataType.DATA_TYPE_UNSPECIFIED) {
      return schemaType
    }
    return DataType.TYPE_STRING
  }

  private fun buildSchemaIndex(): Map<String, FieldDefinition> {
    val map = linkedMapOf<String, FieldDefinition>()
    fun visit(fields: List<FieldDefinition>, prefix: String) {
      for (f in fields) {
        val path = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
        map[path] = f
        if (f.fields.isNotEmpty()) {
          visit(f.fields, path)
        }
      }
    }
    visit(formDef.model?.primary_instance?.record_schema?.fields ?: emptyList(), "")
    return map
  }

  private fun buildBindingsIndex(): Map<String, CompiledFieldBinding> {
    val map = linkedMapOf<String, CompiledFieldBinding>()
    for (b in formDef.model?.bindings ?: emptyList()) {
      val relPath = RecordMutator.normalizeSchemaPath(b.field_path, rootAliases)
      if (relPath.isNotEmpty()) {
        map[relPath] =
          CompiledFieldBinding(
            relativePath = relPath,
            binding = b,
            relevantExpr = compileExpression(b.relevant_expression),
            calculateExpr = compileExpression(b.calculate_expression),
            constraintExpr = compileExpression(b.constraint_expression),
            requiredExpr = compileExpression(b.required_expression),
          )
      }
    }
    return map
  }

  private fun computeTopologicalOrder(): List<CompiledFieldBinding> {
    val allBindings = bindingsByRelativePath.values.toList()
    if (allBindings.size <= 1) return allBindings

    val indexByPath = allBindings.withIndex().associate { (i, b) -> b.relativePath to i }
    val adj = Array(allBindings.size) { mutableSetOf<Int>() }
    val inDegree = IntArray(allBindings.size)

    for ((targetIdx, compiled) in allBindings.withIndex()) {
      val targetPath = compiled.relativePath
      // 1. Enclosing parent group/repeat bindings should evaluate before child bindings
      val parentSlash = targetPath.lastIndexOf('/')
      if (parentSlash > 0) {
        val parentPath = targetPath.substring(0, parentSlash)
        val parentIdx = indexByPath[parentPath]
        if (parentIdx != null && parentIdx != targetIdx && adj[parentIdx].add(targetIdx)) {
          inDegree[targetIdx]++
        }
      }

      // 2. Dependencies from calculate_expression and relevant_expression
      val deps = buildSet {
        compiled.calculateExpr?.dependencies?.let { addAll(it) }
        compiled.relevantExpr?.dependencies?.let { addAll(it) }
      }
      for (dep in deps) {
        if (dep is XPathDependency.FieldDependency) {
          val resolvedDepPath = resolveDependencyRelativePath(dep.path, dep.isAbsolute, targetPath)
          val sourceIdx = indexByPath[resolvedDepPath]
          if (sourceIdx != null && sourceIdx != targetIdx && adj[sourceIdx].add(targetIdx)) {
            inDegree[targetIdx]++
          }
        }
      }
    }

    // Kahn's algorithm preserving declaration order via priority/queue
    val result = mutableListOf<CompiledFieldBinding>()
    val visited = BooleanArray(allBindings.size)
    val ready = ArrayDeque<Int>()
    for (i in allBindings.indices) {
      if (inDegree[i] == 0) {
        ready.addLast(i)
      }
    }

    while (result.size < allBindings.size) {
      if (ready.isEmpty()) {
        // Kahn's algorithm running dry with bindings left over means the dependency graph has a
        // cycle (e.g. `a = b + 1` and `b = a + 1`). Previously this picked an arbitrary unvisited
        // binding and carried on, which silently produced values that depended on declaration
        // order. Fail the compile instead, matching how ODK Collect/JavaRosa reject cyclic forms.
        val cyclePaths = allBindings.filterIndexed { i, _ -> !visited[i] }.map { it.relativePath }
        throw CyclicDependencyException(cyclePaths.sorted())
      }
      val current = ready.removeFirst()
      if (visited[current]) continue
      visited[current] = true
      result.add(allBindings[current])
      for (next in adj[current]) {
        inDegree[next]--
        if (inDegree[next] <= 0 && !visited[next]) {
          ready.addLast(next)
        }
      }
    }

    return result
  }

  private fun resolveDependencyRelativePath(
    depPath: String,
    isAbsolute: Boolean,
    contextRelativePath: String,
  ): String {
    if (isAbsolute) {
      return RecordMutator.normalizeSchemaPath(depPath, rootAliases)
    }
    // In XPath, relative paths inside a field binding (e.g. "../other" or "sibling") are evaluated
    // with `.` = the target field node itself (so `..` is the parent group/repeat).
    val contextCanonical =
      if (contextRelativePath.isEmpty()) "/$rootName" else "/$rootName/$contextRelativePath"
    val resolvedCanonical =
      RecordMutator.resolveCanonicalPath(
        rawPath = depPath,
        rootName = rootName,
        rootAliases = rootAliases,
        contextCanonicalPath = contextCanonical,
      )
    return RecordMutator.normalizeSchemaPath(resolvedCanonical, rootAliases)
  }

  private fun buildRepeatIndex(): Map<String, RepeatDef> {
    val map = linkedMapOf<String, RepeatDef>()
    fun visit(components: List<ViewComponent>, parentPath: String) {
      for (comp in components) {
        when {
          comp.group != null -> {
            val g = comp.group
            val gPath = resolveComponentRelativePath(g.field_ref, parentPath)
            visit(g.components, gPath)
          }
          comp.repeat != null -> {
            val r = comp.repeat
            val rPath = resolveComponentRelativePath(r.field_ref, parentPath)
            if (rPath.isNotEmpty()) {
              map[rPath] = r
            }
            visit(r.components, rPath)
          }
        }
      }
    }
    visit(formDef.view?.components ?: emptyList(), "")
    return map
  }

  private fun buildValueChangedActionIndex(): Map<String, List<ActionDef>> {
    val map = linkedMapOf<String, MutableList<ActionDef>>()
    fun visit(components: List<ViewComponent>, parentPath: String) {
      for (comp in components) {
        when {
          comp.control != null -> {
            val c = comp.control
            val cPath = resolveComponentRelativePath(c.field_ref, parentPath)
            for (action in c.actions) {
              if (EventType.EVENT_VALUE_CHANGED in action.events && cPath.isNotEmpty()) {
                map.getOrPut(cPath) { mutableListOf() }.add(action)
              }
            }
          }
          comp.group != null -> {
            val g = comp.group
            val gPath = resolveComponentRelativePath(g.field_ref, parentPath)
            visit(g.components, gPath)
          }
          comp.repeat != null -> {
            val r = comp.repeat
            val rPath = resolveComponentRelativePath(r.field_ref, parentPath)
            visit(r.components, rPath)
          }
        }
      }
    }
    visit(formDef.view?.components ?: emptyList(), "")
    return map
  }

  private fun buildRepeatInsertActionIndex(): Map<String, List<ActionDef>> {
    val map = linkedMapOf<String, MutableList<ActionDef>>()
    // 1. Model-level actions with EVENT_REPEAT_INSERT
    for (action in formDef.model?.actions ?: emptyList()) {
      if (EventType.EVENT_REPEAT_INSERT in action.events) {
        val targetRel = RecordMutator.normalizeSchemaPath(action.target_field, rootAliases)
        // Find matching repeat prefix
        val matchingRepeat =
          repeatDefsByRelativePath.keys
            .filter { targetRel == it || targetRel.startsWith("$it/") }
            .maxByOrNull { it.length } ?: ""
        map.getOrPut(matchingRepeat) { mutableListOf() }.add(action)
      }
    }

    // 2. View-level actions inside repeats with EVENT_REPEAT_INSERT
    fun visit(components: List<ViewComponent>, parentPath: String, enclosingRepeatPath: String?) {
      for (comp in components) {
        when {
          comp.control != null -> {
            val c = comp.control
            for (action in c.actions) {
              if (EventType.EVENT_REPEAT_INSERT in action.events && enclosingRepeatPath != null) {
                map.getOrPut(enclosingRepeatPath) { mutableListOf() }.add(action)
              }
            }
          }
          comp.group != null -> {
            val g = comp.group
            val gPath = resolveComponentRelativePath(g.field_ref, parentPath)
            visit(g.components, gPath, enclosingRepeatPath)
          }
          comp.repeat != null -> {
            val r = comp.repeat
            val rPath = resolveComponentRelativePath(r.field_ref, parentPath)
            visit(r.components, rPath, rPath)
          }
        }
      }
    }
    visit(formDef.view?.components ?: emptyList(), "", null)
    return map
  }

  internal fun resolveComponentRelativePath(fieldRef: String, parentRelativePath: String): String {
    val trimmed = fieldRef.trim()
    if (trimmed.isEmpty()) return parentRelativePath
    val normalized = RecordMutator.normalizeSchemaPath(trimmed, rootAliases)
    if (trimmed.startsWith('/') || parentRelativePath.isEmpty()) {
      return normalized
    }
    if (normalized == parentRelativePath || normalized.startsWith("$parentRelativePath/")) {
      return normalized
    }
    return "$parentRelativePath/$normalized"
  }
}

/** A [FieldBinding] paired with its pre-compiled XPath expressions. */
class CompiledFieldBinding(
  val relativePath: String,
  val binding: FieldBinding,
  val relevantExpr: CompiledXPathExpression?,
  val calculateExpr: CompiledXPathExpression?,
  val constraintExpr: CompiledXPathExpression?,
  val requiredExpr: CompiledXPathExpression?,
)

/**
 * Thrown when a form's `calculate` / `relevant` expressions form a circular dependency, so no valid
 * evaluation order exists.
 *
 * @property paths relative paths of the bindings participating in the cycle, sorted for stable
 *   error messages.
 */
class CyclicDependencyException(val paths: List<String>) :
  IllegalArgumentException(
    "Circular dependency between form bindings: ${paths.joinToString(", ")}. " +
      "Check the calculate and relevant expressions on these fields."
  )
