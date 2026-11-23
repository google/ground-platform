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

import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ActionType
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.model.PlatformEffectRequest

/**
 * Runs XForms event actions (`setvalue`, `odk:setgeopoint`) against a record.
 *
 * `odk:setgeopoint` also queues a [PlatformEffectRequest.SetGeopointRequest] on the
 * [EvaluationPass] so the host can capture a fresh fix.
 */
internal class ActionExecutor(private val pass: EvaluationPass) {

  /**
   * Runs [actions] in order, resolving their targets relative to [contextCanonicalPath], and
   * returns the record after the last one.
   */
  fun execute(
    actions: List<ActionDef>,
    rawNode: RecordNode,
    metadata: RecordMetadata?,
    contextCanonicalPath: String,
  ): RecordUpdate {
    val compiled = pass.compiled
    var currentRaw = rawNode
    var currentMeta = metadata

    for (action in actions) {
      val targetCanonical =
        RecordMutator.resolveCanonicalPath(
          rawPath = action.target_field,
          rootName = compiled.rootName,
          rootAliases = compiled.rootAliases,
          contextCanonicalPath = contextCanonicalPath,
        )
      val targetRel = RecordMutator.normalizeSchemaPath(targetCanonical, compiled.rootAliases)
      val targetType = compiled.resolveDataType(targetRel)

      when {
        action.type == ActionType.ACTION_SET_GEOPOINT -> {
          pass.pendingRequests.add(PlatformEffectRequest.SetGeopointRequest(targetCanonical))
          val location = pass.environment.locationProvider?.invoke()
          if (location != null) {
            val (nextRaw, nextMeta) =
              RecordMutator.setFieldValue(
                rootNode = currentRaw,
                metadata = currentMeta,
                rootAliases = compiled.rootAliases,
                canonicalPath = targetCanonical,
                newValue = FieldValue(scalar_value = TypedValue(geopoint_value = location)),
              )
            currentRaw = nextRaw
            currentMeta = nextMeta
          }
        }
        action.type == ActionType.ACTION_SET_VALUE ||
          !action.value_expression.isNullOrEmpty() ||
          action.literal_value != null -> {
          val exprStr = action.value_expression
          val newFv: FieldValue? =
            if (!exprStr.isNullOrEmpty()) {
              val tempRecord =
                RecordInstance(
                  form_id = compiled.formDef.form_id,
                  form_version = compiled.formDef.version,
                  metadata = currentMeta,
                  data_ = currentRaw,
                )
              val evalCtx = pass.evaluationContext(tempRecord, targetCanonical)
              val expr = compiled.compileExpression(exprStr)
              expr?.evaluateFieldValue(evalCtx, targetType)
            } else if (action.literal_value != null) {
              FieldValue(scalar_value = action.literal_value)
            } else {
              null
            }

          if (newFv != null) {
            val (nextRaw, nextMeta) =
              RecordMutator.setFieldValue(
                rootNode = currentRaw,
                metadata = currentMeta,
                rootAliases = compiled.rootAliases,
                canonicalPath = targetCanonical,
                newValue = newFv,
              )
            currentRaw = nextRaw
            currentMeta = nextMeta
          }
        }
      }
    }

    return RecordUpdate(currentRaw, currentMeta)
  }
}
