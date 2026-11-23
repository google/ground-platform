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
package org.openforis.collectone.v2.core.forms.ui

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.model.RepeatInstanceState
import org.openforis.collectone.v2.core.forms.temporal.formatIsoInstant
import org.openforis.collectone.v2.core.forms.temporal.formatIsoTime
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_no
import org.openforis.collectone.v2.core.forms.ui.resources.forms_preview_location_recorded
import org.openforis.collectone.v2.core.forms.ui.resources.forms_preview_shape_recorded
import org.openforis.collectone.v2.core.forms.ui.resources.forms_preview_trace_recorded
import org.openforis.collectone.v2.core.forms.ui.resources.forms_yes

/**
 * Stateless derivation of the wizard's screens from a [FormState]: [FormWizardController] rebuilds
 * them on every state change, and the data collection screen uses them to list a record's answers.
 */
internal object FormWizardSteps {
  /**
   * Flattens the hierarchical [FormState.rootComponents] tree into the ordered wizard screens,
   * always ending with the [FormWizardStep.SummaryStep].
   *
   * Non-relevant components (and their subtrees) produce no screen. A plain group contributes its
   * children's screens and a breadcrumb; a `field-list` group becomes one screen holding all its
   * relevant descendant questions (XForms appearance, ODK XForms spec "Groups"). Each relevant
   * repeat instance contributes its children's screens, followed by one repeat hub for the whole
   * repeat when instances can be added or removed, or when there are none (so an empty repeat is
   * still reachable).
   *
   * Step keys are derived from canonical paths (`group:` and `repeat-hub:` prefixes, the summary's
   * fixed key), so they stay stable while answers change relevance and screens appear or disappear.
   */
  fun buildSteps(state: FormState): List<FormWizardStep> {
    val result = mutableListOf<FormWizardStep>()

    fun visitComponents(
      components: List<ComponentState>,
      breadcrumbs: List<String>,
      repeatContext: RepeatStepContext?,
    ) {
      for (component in components) {
        if (!component.isRelevant) continue
        when (component) {
          is ComponentState.ControlState -> {
            result.add(
              FormWizardStep.QuestionStep(
                stepKey = component.canonicalPath,
                breadcrumbs = breadcrumbs,
                repeatContext = repeatContext,
                control = component,
              )
            )
          }
          is ComponentState.GroupState -> {
            val groupTitle =
              component.label?.text?.takeIf { it.isNotBlank() }
                ?: component.canonicalPath.substringAfterLast('/').takeIf { it.isNotBlank() }
            val nextBreadcrumbs = if (groupTitle != null) breadcrumbs + groupTitle else breadcrumbs
            val isFieldList = "field-list" in appearanceTokens(component.appearance)
            if (isFieldList) {
              val directControls = collectRelevantControls(component.children)
              if (directControls.isNotEmpty()) {
                result.add(
                  FormWizardStep.FieldListGroupStep(
                    stepKey = "group:${component.canonicalPath}",
                    groupLabel = groupTitle,
                    breadcrumbs = breadcrumbs,
                    repeatContext = repeatContext,
                    controls = directControls,
                  )
                )
              }
            } else {
              visitComponents(component.children, nextBreadcrumbs, repeatContext)
            }
          }
          is ComponentState.RepeatGroupState -> {
            val repeatTitle =
              component.label?.text?.takeIf { it.isNotBlank() }
                ?: component.canonicalPath.substringAfterLast('/')
            val relevantInstances = component.instances.filter { it.isRelevant }

            for (instance in relevantInstances) {
              val instanceTitle = repeatInstanceTitle(instance, repeatTitle)
              val instanceContext =
                RepeatStepContext(
                  repeatGroupPath = component.canonicalPath,
                  repeatGroupLabel = repeatTitle,
                  instancePath = instance.canonicalPath,
                  repeatIndex = instance.repeatIndex,
                  totalInstances = relevantInstances.size,
                  instanceLabel = instanceTitle,
                  canAddInstance = component.canAddInstance,
                  canRemoveInstance = component.canRemoveInstance,
                  targetCount = component.targetCount,
                )
              visitComponents(
                components = instance.children,
                breadcrumbs = breadcrumbs + instanceTitle,
                repeatContext = instanceContext,
              )
            }

            val summaries = relevantInstances.map { instance ->
              val instTitle = repeatInstanceTitle(instance, repeatTitle)
              val controls = collectEntryControls(instance.children)
              val answeredValues = controls.mapNotNull { formatControlPreview(it) }
              RepeatInstanceSummary(
                repeatIndex = instance.repeatIndex,
                title = instTitle,
                previewParts = answeredValues.take(3),
                instancePath = instance.canonicalPath,
                answeredCount = answeredValues.size,
                status = RepeatEntryStatus.of(controls),
              )
            }

            // Always provide a RepeatHubStep if the user can add/remove instances or if 0
            // instances exist
            if (
              component.canAddInstance || component.canRemoveInstance || relevantInstances.isEmpty()
            ) {
              result.add(
                FormWizardStep.RepeatHubStep(
                  stepKey = "repeat-hub:${component.canonicalPath}",
                  title = FormUiText.Verbatim("$repeatTitle (${relevantInstances.size})"),
                  breadcrumbs = breadcrumbs,
                  repeatContext = repeatContext,
                  repeatGroup = component,
                  summaries = summaries,
                )
              )
            }
          }
        }
      }
    }

    visitComponents(
      components = state.rootComponents,
      breadcrumbs = emptyList(),
      repeatContext = null,
    )
    result.add(FormWizardStep.SummaryStep())
    return result
  }

  /**
   * Title of a repeat entry: its label as evaluated for that entry (a label that references the
   * entry's answers) when it differs from the repeat's own label, else "<repeat label> #<1-based
   * index>".
   */
  private fun repeatInstanceTitle(instance: RepeatInstanceState, repeatTitle: String): String {
    val dynamicLabel = instance.label?.text?.trim()
    return if (!dynamicLabel.isNullOrBlank() && dynamicLabel != repeatTitle) {
      dynamicLabel
    } else {
      "$repeatTitle #${instance.repeatIndex}"
    }
  }

  /**
   * Short preview of [control]'s answer for repeat hub summaries: verbatim text for values shown as
   * entered (choice labels instead of codes), a translated label for booleans and geometry, or
   * `null` when the question is unanswered.
   */
  fun formatControlPreview(control: ComponentState.ControlState): FormUiText? =
    formatControlPreviewText(control)?.let(FormUiText::Verbatim)
      ?: formatControlPreviewLabel(control)?.let { FormUiText.Resource(it) }

  /** Preview of [control]'s answer for values shown as entered (text, choices, numbers, dates). */
  private fun formatControlPreviewText(control: ComponentState.ControlState): String? {
    val value = control.fieldState.value ?: return null
    val scalar = value.scalar_value
    if (scalar != null) {
      val str = scalar.string_value?.trim()
      if (!str.isNullOrBlank()) {
        val matchingOpt = control.options.firstOrNull { it.value == str }
        return matchingOpt?.label?.text?.ifBlank { str } ?: str
      }
      if (scalar.int32_value != null) return scalar.int32_value.toString()
      if (scalar.int64_value != null) return scalar.int64_value.toString()
      val d = scalar.double_value
      if (d != null) {
        return if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
      }
      if (scalar.bool_value != null) return null
      val date = scalar.date_value
      if (date != null) {
        return if (date.year > 0) {
          "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
        } else {
          null
        }
      }
      scalar.time_value?.let {
        return formatIsoTime(it)
      }
      scalar.timestamp_value?.let {
        return formatIsoInstant(it)
      }
      if (
        scalar.geopoint_value != null ||
          scalar.geotrace_value != null ||
          scalar.geoshape_value != null
      ) {
        return null
      }
    }
    val list = value.list_value
    if (list != null && list.values.isNotEmpty()) {
      val labels =
        list.values.mapNotNull { item ->
          val v = item.string_value ?: return@mapNotNull null
          control.options.firstOrNull { it.value == v }?.label?.text?.ifBlank { v } ?: v
        }
      if (labels.isNotEmpty()) return labels.joinToString(", ")
    }
    return null
  }

  /** Preview of [control]'s answer for values shown as a translated label (booleans, geometry). */
  private fun formatControlPreviewLabel(control: ComponentState.ControlState): StringResource? {
    val scalar = control.fieldState.value?.scalar_value ?: return null
    scalar.bool_value?.let {
      return if (it) Res.string.forms_yes else Res.string.forms_no
    }
    if (scalar.geopoint_value != null) return Res.string.forms_preview_location_recorded
    if (scalar.geotrace_value != null) return Res.string.forms_preview_trace_recorded
    if (scalar.geoshape_value != null) return Res.string.forms_preview_shape_recorded
    return null
  }

  private fun collectRelevantControls(
    components: List<ComponentState>
  ): List<ComponentState.ControlState> {
    val list = mutableListOf<ComponentState.ControlState>()
    for (comp in components) {
      if (!comp.isRelevant) continue
      when (comp) {
        is ComponentState.ControlState -> list.add(comp)
        is ComponentState.GroupState -> list.addAll(collectRelevantControls(comp.children))
        is ComponentState.RepeatGroupState -> {}
      }
    }
    return list
  }

  /**
   * Every relevant question inside a repeat entry, including those inside nested repeat entries, so
   * the entry's status, preview, and delete confirmation reflect all of its descendant answers.
   */
  private fun collectEntryControls(
    components: List<ComponentState>
  ): List<ComponentState.ControlState> {
    val list = mutableListOf<ComponentState.ControlState>()
    for (comp in components) {
      if (!comp.isRelevant) continue
      when (comp) {
        is ComponentState.ControlState -> list.add(comp)
        is ComponentState.GroupState -> list.addAll(collectEntryControls(comp.children))
        is ComponentState.RepeatGroupState ->
          comp.instances
            .filter { it.isRelevant }
            .forEach { list.addAll(collectEntryControls(it.children)) }
      }
    }
    return list
  }
}
