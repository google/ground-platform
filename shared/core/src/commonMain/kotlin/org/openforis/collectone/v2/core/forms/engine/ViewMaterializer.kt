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

import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.IntentConfig
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.ViewComponent
import org.openforis.collectone.v2.core.forms.media.MediaCapture
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.model.RepeatInstanceState
import org.openforis.collectone.v2.core.forms.model.ResolvedIntent
import org.openforis.collectone.v2.core.forms.model.ValidationStatus
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext

/**
 * Turns the form's [ViewComponent] tree into the [ComponentState] tree the runners render: expands
 * repeats into their instances, inherits relevance and read-only state from containers, and
 * resolves labels, hints, choices and intents.
 *
 * @property fieldStates every field's state, from [FieldStateEvaluator].
 * @property relevancyMap relevance keyed by canonical path, from [RelevanceEvaluator].
 * @property dynamicRepeatCounts evaluated `jr:count` keyed by repeat path.
 * @property effectiveRecord the record without non-relevant values.
 * @property rootEvalContext an XPath context over [effectiveRecord].
 */
internal class ViewMaterializer(
  private val pass: EvaluationPass,
  private val fieldStates: Map<String, FieldState>,
  private val relevancyMap: Map<String, Boolean>,
  private val dynamicRepeatCounts: Map<String, Int>,
  private val effectiveRecord: RecordInstance,
  private val rootEvalContext: EvaluationContext,
) {

  private val compiled = pass.compiled
  private val labels = LabelResolver(compiled, pass.activeLanguage)
  private val choices = ChoiceOptionsResolver(pass, labels)

  /** Materializes the form's top-level view components. */
  fun materialize(): List<ComponentState> =
    materializeViewComponents(
      components = compiled.formDef.view?.components ?: emptyList(),
      parent =
        ParentScope(
          canonicalPath = pass.rootCanonicalPath,
          relativePath = "",
          isRelevant = true,
          isReadOnly = false,
        ),
    )

  /** The container a component sits in, and the relevance and read-only state it inherits. */
  private data class ParentScope(
    val canonicalPath: String,
    val relativePath: String,
    val isRelevant: Boolean,
    val isReadOnly: Boolean,
  )

  private fun materializeViewComponents(
    components: List<ViewComponent>,
    parent: ParentScope,
  ): List<ComponentState> = components.mapIndexedNotNull { idx, comp ->
    when {
      comp.control != null -> materializeControl(comp.control, idx, parent)
      comp.group != null -> materializeGroup(comp.group, parent)
      comp.repeat != null -> materializeRepeat(comp.repeat, parent)
      else -> null
    }
  }

  private fun materializeControl(
    control: ControlDef,
    idx: Int,
    parent: ParentScope,
  ): ComponentState.ControlState {
    val relPath = compiled.resolveComponentRelativePath(control.field_ref, parent.relativePath)
    val canonicalPath =
      if (control.field_ref.isNotEmpty()) {
        RecordMutator.resolveCanonicalPath(
          rawPath = control.field_ref,
          rootName = compiled.rootName,
          rootAliases = compiled.rootAliases,
          contextCanonicalPath = parent.canonicalPath,
        )
      } else {
        "${parent.canonicalPath}/_control_$idx"
      }

    val baseFieldState =
      fieldStates[canonicalPath]
        ?: FieldState(
          canonicalPath = canonicalPath,
          relativePath = relPath,
          binding = compiled.bindingsByRelativePath[relPath]?.binding,
          dataType = compiled.resolveDataType(relPath),
          value = null,
          rawValue = null,
          isEmpty = true,
          isRelevant = parent.isRelevant && (relevancyMap[canonicalPath] ?: true),
          isRequired = false,
          isReadOnly = parent.isReadOnly || control.type == ControlType.CONTROL_TRIGGER,
          isCalculated = false,
          validationStatus = ValidationStatus.Valid,
        )
    val fieldState =
      if (parent.isReadOnly && !baseFieldState.isReadOnly) {
        baseFieldState.copy(isReadOnly = true)
      } else {
        baseFieldState
      }

    val isRelevant = parent.isRelevant && fieldState.isRelevant
    val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)
    return ComponentState.ControlState(
      canonicalPath = canonicalPath,
      controlDef = control,
      fieldState = fieldState,
      isRelevant = isRelevant,
      label = labels.resolve(control.label, nodeCtx),
      hint = labels.resolve(control.hint, nodeCtx),
      appearance = control.appearance,
      options = if (isRelevant) choices.resolve(control, nodeCtx) else emptyList(),
      resolvedIntent = control.intent?.let { resolveIntentConfig(it, nodeCtx) },
      mediaCapture = MediaCapture.resolveSpec(control, fieldState.binding),
    )
  }

  private fun materializeGroup(group: GroupDef, parent: ParentScope): ComponentState.GroupState {
    val relPath = compiled.resolveComponentRelativePath(group.field_ref, parent.relativePath)
    val hasOwnNode = group.field_ref.isNotEmpty()
    val canonicalPath =
      if (hasOwnNode) {
        RecordMutator.resolveCanonicalPath(
          rawPath = group.field_ref,
          rootName = compiled.rootName,
          rootAliases = compiled.rootAliases,
          contextCanonicalPath = parent.canonicalPath,
        )
      } else {
        parent.canonicalPath
      }
    val groupOwnRelevant = if (hasOwnNode) (relevancyMap[canonicalPath] ?: true) else true
    val isRelevant = parent.isRelevant && groupOwnRelevant
    val groupOwnReadOnly =
      if (hasOwnNode) (fieldStates[canonicalPath]?.isReadOnly ?: false) else false
    val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)
    return ComponentState.GroupState(
      canonicalPath = canonicalPath,
      groupDef = group,
      isRelevant = isRelevant,
      label = labels.resolve(group.label, nodeCtx),
      appearance = group.appearance,
      resolvedIntent = group.intent?.let { resolveIntentConfig(it, nodeCtx) },
      children =
        materializeViewComponents(
          components = group.components,
          parent =
            ParentScope(
              canonicalPath = canonicalPath,
              relativePath = relPath,
              isRelevant = isRelevant,
              isReadOnly = parent.isReadOnly || groupOwnReadOnly,
            ),
        ),
    )
  }

  private fun materializeRepeat(
    repeat: RepeatDef,
    parent: ParentScope,
  ): ComponentState.RepeatGroupState {
    val relPath = compiled.resolveComponentRelativePath(repeat.field_ref, parent.relativePath)
    val canonicalPath =
      RecordMutator.resolveCanonicalPath(
        rawPath = repeat.field_ref,
        rootName = compiled.rootName,
        rootAliases = compiled.rootAliases,
        contextCanonicalPath = parent.canonicalPath,
      )
    val isRelevant = parent.isRelevant && (relevancyMap[canonicalPath] ?: true)
    val isRepeatReadOnly = parent.isReadOnly || (fieldStates[canonicalPath]?.isReadOnly ?: false)
    val nodeCtx = positionContextAtPath(rootEvalContext, canonicalPath)

    val repeatNodes =
      RecordMutator.getRepeatInstances(
        rootNode = effectiveRecord.data_ ?: RecordNode(),
        rootAliases = compiled.rootAliases,
        repeatCanonicalPath = canonicalPath,
      )
    val isUserResizable = isRelevant && !repeat.no_add_remove && repeat.count_expression.isBlank()

    val instances =
      repeatNodes.indices.map { idx ->
        val idx1 = idx + 1
        val instancePath = "$canonicalPath[$idx1]"
        val instanceRelevant = isRelevant && (relevancyMap[instancePath] ?: true)
        val instanceCtx =
          positionContextAtPath(
            baseContext = rootEvalContext,
            canonicalPath = instancePath,
            explicitPosition = idx1,
            explicitSize = repeatNodes.size,
          )
        RepeatInstanceState(
          canonicalPath = instancePath,
          repeatIndex = idx1,
          label = labels.resolve(repeat.label, instanceCtx),
          isRelevant = instanceRelevant,
          children =
            materializeViewComponents(
              components = repeat.components,
              parent =
                ParentScope(
                  canonicalPath = instancePath,
                  relativePath = relPath,
                  isRelevant = instanceRelevant,
                  isReadOnly = isRepeatReadOnly || (fieldStates[instancePath]?.isReadOnly ?: false),
                ),
            ),
        )
      }

    return ComponentState.RepeatGroupState(
      canonicalPath = canonicalPath,
      repeatDef = repeat,
      isRelevant = isRelevant,
      label = labels.resolve(repeat.label, nodeCtx),
      appearance = repeat.appearance,
      targetCount = dynamicRepeatCounts[canonicalPath],
      canAddInstance = isUserResizable,
      canRemoveInstance = isUserResizable && repeatNodes.isNotEmpty(),
      instances = instances,
    )
  }

  private fun resolveIntentConfig(
    intentConfig: IntentConfig,
    evalContext: EvaluationContext,
  ): ResolvedIntent {
    val evaluatedParams =
      intentConfig.parameters.mapValues { (_, rawExprOrLiteral) ->
        try {
          val expr = compiled.compileExpression(rawExprOrLiteral)
          expr?.evaluateString(evalContext) ?: rawExprOrLiteral
        } catch (_: Exception) {
          rawExprOrLiteral
        }
      }
    return ResolvedIntent(
      intentUri = intentConfig.intent_uri,
      parameters = evaluatedParams,
      responseMappings = intentConfig.response_mappings,
    )
  }
}
