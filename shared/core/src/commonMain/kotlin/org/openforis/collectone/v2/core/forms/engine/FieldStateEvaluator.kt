/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.core.forms.engine

import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.RangeConfig
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.ViewComponent
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.model.ValidationError
import org.openforis.collectone.v2.core.forms.model.ValidationErrorKind
import org.openforis.collectone.v2.core.forms.model.ValidationStatus
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** Every field's [FieldState] keyed by canonical path, plus all the validation errors found. */
internal data class FieldEvaluation(
  val fieldStates: Map<String, FieldState>,
  val validationErrors: List<ValidationError>,
)

/**
 * Builds the [FieldState] of every concrete field: its value, inherited relevance and read-only
 * state, `required`, and the validation checks (required, `constraint`, range bounds) with their
 * localized `jr:requiredMsg` / `jr:constraintMsg` messages.
 */
internal class FieldStateEvaluator(
  private val pass: EvaluationPass,
  private val effectiveRecord: RecordInstance,
  private val rawNode: RecordNode,
  private val relevancyMap: Map<String, Boolean>,
  private val rootEvalContext: EvaluationContext,
) {

  private val compiled = pass.compiled
  private val activeLanguage = pass.activeLanguage

  /** Any RangeConfig from ViewDef controls, by relative path. */
  private val rangeConfigsByRelPath = collectRangeConfigs()

  /**
   * Evaluates every field in [rawNode]. Values come from [effectiveRecord] (the pruned record), so
   * non-relevant fields report no value while [FieldState.rawValue] keeps what was entered. The
   * first instance of a repeat is also keyed by its unindexed path.
   */
  fun evaluate(): FieldEvaluation {
    val fieldStates = linkedMapOf<String, FieldState>()
    val allErrors = mutableListOf<ValidationError>()
    val readOnlyMap = linkedMapOf(pass.rootCanonicalPath to false)

    val allRelativePaths =
      (compiled.schemaByRelativePath.keys + compiled.bindingsByRelativePath.keys)
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { it.count { ch -> ch == '/' } }

    for (relPath in allRelativePaths) {
      for (canonicalPath in pass.concretePaths(rawNode, relPath)) {
        val parentCanonical = canonicalPath.substringBeforeLast('/', pass.rootCanonicalPath)
        val state = evaluateField(canonicalPath, relPath, readOnlyMap[parentCanonical] ?: false)
        readOnlyMap.putWithFirstInstanceAlias(canonicalPath, state.isReadOnly)
        (state.validationStatus as? ValidationStatus.Invalid)?.let { allErrors.addAll(it.errors) }
        fieldStates.putWithFirstInstanceAlias(canonicalPath, state)
      }
    }
    return FieldEvaluation(fieldStates, allErrors)
  }

  private fun evaluateField(
    canonicalPath: String,
    relPath: String,
    parentReadOnly: Boolean,
  ): FieldState {
    val compiledBinding = compiled.bindingsByRelativePath[relPath]
    val binding = compiledBinding?.binding
    val isRelevant = relevancyMap[canonicalPath] ?: true
    val rawVal =
      RecordMutator.getFieldValue(
        rootNode = rawNode,
        metadata = effectiveRecord.metadata,
        rootAliases = compiled.rootAliases,
        canonicalPath = canonicalPath,
      )
    val effectiveVal =
      if (isRelevant) {
        RecordMutator.getFieldValue(
          rootNode = effectiveRecord.data_ ?: RecordNode(),
          metadata = effectiveRecord.metadata,
          rootAliases = compiled.rootAliases,
          canonicalPath = canonicalPath,
        )
      } else {
        null
      }
    val isEmpty = RecordMutator.isFieldValueEmpty(effectiveVal)
    val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)

    val isRequired = isRelevant && compiledBinding?.requiredExpr?.evaluateBoolean(nodeCtx) == true
    val ownReadOnly =
      when {
        binding?.read_only == true -> true
        isRelevant && compiledBinding?.readOnlyExpr != null ->
          compiledBinding.readOnlyExpr.evaluateBoolean(nodeCtx)
        else -> false
      }
    val isVariantSkipped =
      pass.environment.skippedFieldPaths.isNotEmpty() &&
        (relPath in pass.environment.skippedFieldPaths ||
          canonicalPath in pass.environment.skippedFieldPaths ||
          "/$relPath" in pass.environment.skippedFieldPaths)
    val fieldErrors =
      if (isRelevant && !isVariantSkipped) {
        validate(
          canonicalPath = canonicalPath,
          relPath = relPath,
          compiledBinding = compiledBinding,
          value = effectiveVal,
          isRequired = isRequired,
          nodeCtx = nodeCtx,
        )
      } else {
        emptyList()
      }

    return FieldState(
      canonicalPath = canonicalPath,
      relativePath = relPath,
      binding = binding,
      dataType = compiled.resolveDataType(relPath),
      value = effectiveVal,
      rawValue = rawVal,
      isEmpty = isEmpty,
      isRelevant = isRelevant,
      isRequired = isRequired,
      isReadOnly = parentReadOnly || ownReadOnly,
      isCalculated = compiledBinding?.calculateExpr != null,
      validationStatus =
        if (fieldErrors.isEmpty()) ValidationStatus.Valid
        else ValidationStatus.Invalid(fieldErrors),
    )
  }

  /** The required, constraint and range checks of a relevant field, in that order. */
  private fun validate(
    canonicalPath: String,
    relPath: String,
    compiledBinding: CompiledFieldBinding?,
    value: FieldValue?,
    isRequired: Boolean,
    nodeCtx: EvaluationContext,
  ): List<ValidationError> = buildList {
    val binding = compiledBinding?.binding
    val isEmpty = RecordMutator.isFieldValueEmpty(value)
    if (isRequired && isEmpty) {
      val msg =
        resolveBindingMessage(
          rawMessage = binding?.required_message ?: "",
          defaultMessage = "This field is required.",
          relPath = relPath,
          messageTypeSuffix = "requiredMsg",
          evalContext = nodeCtx,
        )
      add(ValidationError(canonicalPath, ValidationErrorKind.REQUIRED_MISSING, msg))
    }

    // Constraints are only evaluated on non-empty values.
    val constraintExpr = compiledBinding?.constraintExpr
    if (!isEmpty && constraintExpr != null && !constraintExpr.evaluateBoolean(nodeCtx)) {
      val msg =
        resolveBindingMessage(
          rawMessage = binding?.constraint_message ?: "",
          defaultMessage = "Value violates constraint: ${binding?.constraint_expression}",
          relPath = relPath,
          messageTypeSuffix = "constraintMsg",
          evalContext = nodeCtx,
        )
      add(ValidationError(canonicalPath, ValidationErrorKind.CONSTRAINT_VIOLATED, msg))
    }

    val rangeConfig = rangeConfigsByRelPath[relPath]
    if (!isEmpty && rangeConfig != null && rangeConfig.start < rangeConfig.end) {
      val numVal = XPathValue.fromFieldValue(value).toNumber()
      if (!numVal.isNaN() && (numVal < rangeConfig.start || numVal > rangeConfig.end)) {
        add(
          ValidationError(
            fieldPath = canonicalPath,
            kind = ValidationErrorKind.RANGE_OUT_OF_BOUNDS,
            message = "Value $numVal must be between ${rangeConfig.start} and ${rangeConfig.end}.",
          )
        )
      }
    }
  }

  private fun resolveBindingMessage(
    rawMessage: String,
    defaultMessage: String,
    relPath: String,
    messageTypeSuffix: String,
    evalContext: EvaluationContext,
  ): String {
    val trimmed = rawMessage.trim()
    val catalog = compiled.formDef.model?.translations
    if (trimmed.isEmpty()) {
      if (catalog != null && relPath.isNotEmpty()) {
        val candidates =
          listOf(
            "/${compiled.rootName}/$relPath:jr:$messageTypeSuffix",
            "/${compiled.rootName}/$relPath:$messageTypeSuffix",
            "$relPath:jr:$messageTypeSuffix",
            "$relPath:$messageTypeSuffix",
          )
        for (candidate in candidates) {
          val match = lookupLocalizedString(catalog, activeLanguage, candidate)?.value_
          if (!match.isNullOrEmpty()) return match
        }
      }
      return defaultMessage
    }
    if (trimmed.startsWith("jr:itext(")) {
      val expr = compiled.compileExpression(trimmed)
      val evaluated = expr?.evaluateString(evalContext)
      if (!evaluated.isNullOrEmpty()) return evaluated
    }
    val localized = lookupLocalizedString(catalog, activeLanguage, trimmed)
    return localized?.value_?.takeIf { it.isNotEmpty() } ?: trimmed
  }

  private fun collectRangeConfigs(): Map<String, RangeConfig> {
    val map = mutableMapOf<String, RangeConfig>()
    fun visit(components: List<ViewComponent>, parentPath: String) {
      for (comp in components) {
        when {
          comp.control != null -> {
            val c = comp.control
            val rel = compiled.resolveComponentRelativePath(c.field_ref, parentPath)
            if (c.range_config != null && rel.isNotEmpty()) {
              map[rel] = c.range_config
            }
          }
          comp.group != null -> {
            val g = comp.group
            visit(g.components, compiled.resolveComponentRelativePath(g.field_ref, parentPath))
          }
          comp.repeat != null -> {
            val r = comp.repeat
            visit(r.components, compiled.resolveComponentRelativePath(r.field_ref, parentPath))
          }
        }
      }
    }
    visit(compiled.formDef.view?.components ?: emptyList(), "")
    return map
  }
}

/** Stores [value] at [canonicalPath] and, for a repeat's first instance, at its unindexed path. */
private fun <V> MutableMap<String, V>.putWithFirstInstanceAlias(canonicalPath: String, value: V) {
  this[canonicalPath] = value
  if (canonicalPath.endsWith("[1]")) {
    getOrPut(canonicalPath.removeSuffix("[1]")) { value }
  }
}
