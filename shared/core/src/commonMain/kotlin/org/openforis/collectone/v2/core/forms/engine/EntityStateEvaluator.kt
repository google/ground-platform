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

import kotlin.math.roundToInt
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.model.EntityState
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/**
 * Evaluates the form's ODK Entities declarations: whether each entity is created or updated, its
 * id, label, offline sync versions and the properties saved from `entities:saveto` fields.
 */
internal class EntityStateEvaluator(private val compiled: CompiledForm) {

  /** Evaluates every declaration against [rootEvalContext] using the given [fieldStates]. */
  fun evaluate(
    fieldStates: Map<String, FieldState>,
    rootEvalContext: EvaluationContext,
  ): List<EntityState> {
    val declarations = compiled.formDef.model?.entities ?: emptyList()
    if (declarations.isEmpty()) return emptyList()

    // Collect global entity_saveto field values from relevant fields
    val saveToProperties = linkedMapOf<String, TypedValue>()
    for (fs in fieldStates.values) {
      if (!fs.isRelevant || fs.isEmpty) continue
      val saveTo = fs.binding?.entity_saveto?.takeIf { it.isNotEmpty() } ?: continue
      val tv =
        fs.value?.scalar_value ?: XPathValue.fromFieldValue(fs.value).toTypedValue(fs.dataType)
      if (tv != null) {
        saveToProperties[saveTo] = tv
      }
    }

    return declarations.map { decl ->
      val createExpr = compiled.compileExpression(decl.create_condition)
      val updateExpr = compiled.compileExpression(decl.update_condition)
      val shouldCreate =
        when {
          createExpr != null -> createExpr.evaluateBoolean(rootEvalContext)
          updateExpr == null -> true
          else -> false
        }
      val shouldUpdate = updateExpr?.evaluateBoolean(rootEvalContext) ?: false

      val idExpr = compiled.compileExpression(decl.entity_id_expression)
      val entityId = idExpr?.evaluateString(rootEvalContext) ?: ""

      val labelExpr = compiled.compileExpression(decl.label_expression)
      val label = labelExpr?.evaluateString(rootEvalContext) ?: ""

      val sync = decl.sync_metadata
      val baseVer =
        compiled
          .compileExpression(sync?.base_version_expression)
          ?.evaluateNumber(rootEvalContext)
          ?.takeIf { !it.isNaN() }
          ?.roundToInt()
      val trunkVer =
        compiled
          .compileExpression(sync?.trunk_version_expression)
          ?.evaluateNumber(rootEvalContext)
          ?.takeIf { !it.isNaN() }
          ?.roundToInt()
      val branchId =
        compiled
          .compileExpression(sync?.branch_id_expression)
          ?.evaluateString(rootEvalContext)
          ?.takeIf { it.isNotEmpty() }

      val mappedProps = linkedMapOf<String, TypedValue>()
      mappedProps.putAll(saveToProperties)
      for (mapping in decl.property_mappings) {
        if (mapping.entity_property.isEmpty() || mapping.source_field_path.isEmpty()) continue
        val canonicalSource =
          RecordMutator.resolveCanonicalPath(
            rawPath = mapping.source_field_path,
            rootName = compiled.rootName,
            rootAliases = compiled.rootAliases,
          )
        val sourceState = fieldStates[canonicalSource]
        if (sourceState != null && sourceState.isRelevant && !sourceState.isEmpty) {
          val tv =
            sourceState.value?.scalar_value
              ?: XPathValue.fromFieldValue(sourceState.value).toTypedValue(sourceState.dataType)
          if (tv != null) {
            mappedProps[mapping.entity_property] = tv
          }
        }
      }

      EntityState(
        dataset = decl.dataset,
        declaration = decl,
        shouldCreate = shouldCreate,
        shouldUpdate = shouldUpdate,
        entityId = entityId,
        label = label,
        baseVersion = baseVer,
        trunkVersion = trunkVer,
        branchId = branchId,
        properties = mappedProps,
      )
    }
  }
}
