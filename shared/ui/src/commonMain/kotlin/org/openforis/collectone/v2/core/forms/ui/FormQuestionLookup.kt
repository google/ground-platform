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

import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState

/**
 * Finds questions by the loosely written paths that reach the wizard from outside the form engine:
 * validation errors and map drawing hosts use canonical paths (`/data/plot[2]/tree`), while
 * cascading selects and some callers use a bare data name (`tree`) or a path relative to a group.
 */
internal object FormQuestionLookup {
  /**
   * Whether [fullPath] and [target] name the same question: equal paths, or equal last segments.
   * Matching by last segment is deliberately lenient (two questions with the same data name in
   * different groups match), because callers often only know the data name.
   */
  fun matchesPath(fullPath: String, target: String): Boolean {
    val cleanTarget = target.trim()
    val cleanFull = fullPath.trim()
    return cleanFull == cleanTarget ||
      cleanFull.substringAfterLast('/') == cleanTarget ||
      cleanTarget.substringAfterLast('/') == cleanFull.substringAfterLast('/')
  }

  /** Whether [step] shows the question at [canonicalPath] (see [matchesPath]). */
  fun stepContainsField(step: FormWizardStep, canonicalPath: String): Boolean =
    when (step) {
      is FormWizardStep.QuestionStep -> matchesPath(step.control.canonicalPath, canonicalPath)
      is FormWizardStep.FieldListGroupStep ->
        step.controls.any { matchesPath(it.canonicalPath, canonicalPath) }
      else -> false
    }

  /**
   * Every question outside repeats in [components], relevant or not, in form order. Questions in
   * repeats are skipped: their paths depend on the entry, so they can't be matched by data name.
   */
  fun collectAllControls(components: List<ComponentState>): List<ComponentState.ControlState> {
    val result = mutableListOf<ComponentState.ControlState>()
    fun walk(list: List<ComponentState>) {
      for (item in list) {
        when (item) {
          is ComponentState.ControlState -> result.add(item)
          is ComponentState.GroupState -> walk(item.children)
          is ComponentState.RepeatGroupState -> Unit
        }
      }
    }
    walk(components)
    return result
  }

  /**
   * Collector-facing title of the question at [canonicalPath], or `null` when no label is found.
   *
   * Looks in order at the visible [steps] (the question's label, else its step's title), then at
   * every question of [formState] including non-relevant ones, and finally at the raw view
   * definition, so that messages about a question that is currently hidden still name it.
   */
  fun questionTitleOrNull(
    steps: List<FormWizardStep>,
    formState: FormState,
    canonicalPath: String,
  ): String? {
    val step = steps.firstOrNull { stepContainsField(it, canonicalPath) }
    val control =
      when (step) {
        is FormWizardStep.QuestionStep -> step.control
        is FormWizardStep.FieldListGroupStep ->
          step.controls.firstOrNull { matchesPath(it.canonicalPath, canonicalPath) }
        else -> null
      }
    val title =
      control?.label?.text?.takeIf { it.isNotBlank() }
        ?: when (step) {
          is FormWizardStep.QuestionStep -> step.questionLabel
          is FormWizardStep.FieldListGroupStep -> step.groupLabel
          else -> null
        }?.takeIf { it.isNotBlank() }
    if (title != null) return title

    val leaf = canonicalPath.trim().substringAfterLast('/')
    val componentControl =
      collectAllControls(formState.rootComponents).firstOrNull {
        matchesPath(it.canonicalPath, canonicalPath) || it.controlDef.field_ref == leaf
      }
    return componentControl?.label?.text?.takeIf { it.isNotBlank() }
      ?: formState.formDef.view
        ?.components
        ?.mapNotNull { it.control }
        ?.firstOrNull { it.field_ref == leaf || it.field_ref == canonicalPath }
        ?.label
        ?.text
        ?.takeIf { it.isNotBlank() }
  }
}

/**
 * Returns `true` if [step] represents an `entityref` question step (bound to a target entity
 * reference field such as `/data/target_entity` or configured with `appearance="map-select"` /
 * `"entityref"`). Shared by web data collection and the KML balloon form runner.
 */
fun isEntityReferenceStep(step: FormWizardStep?): Boolean {
  val controls =
    when (step) {
      is FormWizardStep.QuestionStep -> listOf(step.control)
      is FormWizardStep.FieldListGroupStep -> step.controls
      else -> emptyList()
    }
  return controls.any { ctrl ->
    val path = ctrl.canonicalPath
    val fieldRef = ctrl.controlDef.field_ref
    path.endsWith("/target_entity") ||
      fieldRef == "target_entity" ||
      path.endsWith("/entity_id") ||
      fieldRef == "entity_id" ||
      path.endsWith("/sample_plot_entity") ||
      fieldRef == "sample_plot_entity" ||
      path.endsWith("/past_individual_id") ||
      fieldRef == "past_individual_id" ||
      path.endsWith("/primary_respondent_id") ||
      fieldRef == "primary_respondent_id" ||
      ctrl.appearance.contains("map-select", ignoreCase = true) ||
      ctrl.appearance.contains("entityref", ignoreCase = true)
  }
}
