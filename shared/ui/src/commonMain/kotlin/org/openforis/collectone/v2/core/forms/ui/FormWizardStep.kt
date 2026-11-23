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

import openforis.collectone.v1.forms.DataType
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.ValidationStatus.Invalid
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review_and_submit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_untitled_question_group

/**
 * Context describing the enclosing repeat group iteration when a question or group is rendered
 * inside a repeat instance.
 */
data class RepeatStepContext(
  val repeatGroupPath: String,
  val repeatGroupLabel: String,
  val instancePath: String,
  val repeatIndex: Int,
  val totalInstances: Int,
  val instanceLabel: String,
  val canAddInstance: Boolean,
  val canRemoveInstance: Boolean,
  val targetCount: Int? = null,
)

/**
 * Human-readable preview summary of a single repeat instance for repeat hubs and overview screens.
 *
 * [previewParts] holds up to three answer previews, shown joined with ` • `; it is empty when the
 * instance has no answers yet. [answeredCount] counts every relevant question with a value
 * (including calculated ones), i.e. what deleting the entry would discard; [status] says how far
 * the collector got with the entry's own inputs.
 */
data class RepeatInstanceSummary(
  val repeatIndex: Int,
  val title: String,
  val previewParts: List<FormUiText>,
  val instancePath: String,
  val answeredCount: Int = 0,
  val status: RepeatEntryStatus = RepeatEntryStatus.NOT_STARTED,
)

/** How far a collector got with one repeat entry, shown as the entry's status icon. */
enum class RepeatEntryStatus {
  /** None of the entry's inputs has a value yet. */
  NOT_STARTED,

  /** Some inputs have values, but a required one is still empty. */
  IN_PROGRESS,

  /** Every required input has a valid value (optional ones may still be empty). */
  COMPLETE,

  /** An answer in the entry fails validation. */
  NEEDS_ATTENTION;

  companion object {
    /**
     * Status of an entry made of [controls] (its relevant questions). Calculated and read-only
     * questions need no input, so they never hold an entry back; an entry without any input is
     * [COMPLETE].
     */
    fun of(controls: List<ComponentState.ControlState>): RepeatEntryStatus {
      val inputs = controls.filter { !it.fieldState.isCalculated && !it.fieldState.isReadOnly }
      return when {
        inputs.any { !it.fieldState.isEmpty && it.fieldState.validationStatus is Invalid } ->
          NEEDS_ATTENTION
        inputs.isNotEmpty() && inputs.all { it.fieldState.isEmpty } -> NOT_STARTED
        inputs.any { it.fieldState.isRequired && it.fieldState.isEmpty } -> IN_PROGRESS
        else -> COMPLETE
      }
    }
  }
}

/** Represents a single navigable screen in the mobile one-question-at-a-time form runner. */
sealed interface FormWizardStep {
  /** Stable identifier used to preserve user position when dynamic steps are recomputed. */
  val stepKey: String

  /** Display title for step overview lists and navigation headers. */
  val title: FormUiText

  /** Enclosing group / repeat breadcrumb labels leading to this step. */
  val breadcrumbs: List<String>

  /** Optional enclosing repeat iteration metadata if this step lives inside a repeat. */
  val repeatContext: RepeatStepContext?

  /** Standard single-question screen rendering a single [ComponentState.ControlState]. */
  data class QuestionStep(
    override val stepKey: String,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val control: ComponentState.ControlState,
  ) : FormWizardStep {
    /** The question's label, or its data name when it has none. */
    val questionLabel: String
      get() = control.questionLabel()

    override val title: FormUiText
      get() = FormUiText.Verbatim(questionLabel)
  }

  /**
   * Multi-question group screen for groups configured with `appearance="field-list"`, rendering all
   * relevant child controls on a single screen per the XForms specification.
   */
  data class FieldListGroupStep(
    override val stepKey: String,
    /** The group's label, else its data name; `null` when it has neither. */
    val groupLabel: String?,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val controls: List<ComponentState.ControlState>,
  ) : FormWizardStep {
    override val title: FormUiText
      get() =
        groupLabel?.let(FormUiText::Verbatim)
          ?: FormUiText.Resource(Res.string.forms_untitled_question_group)
  }

  /**
   * Repeat group management screen allowing data collectors to inspect existing iterations, jump to
   * a specific repeat instance, remove an instance, or add a new repeat instance.
   */
  data class RepeatHubStep(
    override val stepKey: String,
    override val title: FormUiText,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val repeatGroup: ComponentState.RepeatGroupState,
    val summaries: List<RepeatInstanceSummary> = emptyList(),
  ) : FormWizardStep

  /**
   * Final summary and review screen displaying all relevant questions, validation statuses,
   * evaluated XForms Entities, and the final submission button.
   */
  data class SummaryStep(
    override val stepKey: String = "__summary__",
    override val title: FormUiText = FormUiText.Resource(Res.string.forms_review_and_submit),
    override val breadcrumbs: List<String> = emptyList(),
    override val repeatContext: RepeatStepContext? = null,
  ) : FormWizardStep
}

/** The question's label, or its data name (the last path segment) when it has none. */
fun ComponentState.ControlState.questionLabel(): String =
  label?.text?.takeIf { it.isNotBlank() } ?: canonicalPath.substringAfterLast('/')

/**
 * Returns `true` if [control] represents a geometry question (`TYPE_GEOPOINT`, `TYPE_GEOTRACE`, or
 * `TYPE_GEOSHAPE`) that has not yet captured the minimum required vertices:
 * - `TYPE_GEOPOINT`: value is null
 * - `TYPE_GEOTRACE`: fewer than 2 points
 * - `TYPE_GEOSHAPE`: fewer than 3 points
 */
fun isControlWaitingForGeometryCapture(control: ComponentState.ControlState): Boolean {
  val fieldState = control.fieldState
  val scalar = fieldState.value?.scalar_value
  return when (fieldState.dataType) {
    DataType.TYPE_GEOPOINT -> scalar?.geopoint_value == null
    DataType.TYPE_GEOTRACE -> (scalar?.geotrace_value?.points?.size ?: 0) < 2
    DataType.TYPE_GEOSHAPE -> (scalar?.geoshape_value?.points?.size ?: 0) < 3
    else -> false
  }
}

/**
 * Returns `true` if [step] contains a geometry question (`TYPE_GEOPOINT`, `TYPE_GEOTRACE`, or
 * `TYPE_GEOSHAPE`) that has not yet had sufficient coordinates captured.
 */
fun isWaitingForGeometryCapture(
  step: FormWizardStep,
  variant: FormVariant = FormVariant.MOBILE,
): Boolean =
  when (step) {
    is FormWizardStep.QuestionStep ->
      incompatibleQuestionTypeForVariant(step.control, variant) == null &&
        isControlWaitingForGeometryCapture(step.control)
    is FormWizardStep.FieldListGroupStep ->
      step.controls.any {
        incompatibleQuestionTypeForVariant(it, variant) == null &&
          isControlWaitingForGeometryCapture(it)
      }
    else -> false
  }

/**
 * Returns `true` if [step] represents an optional question (all its input controls are not
 * required, not read-only, and not calculated).
 */
fun isStepOptional(step: FormWizardStep): Boolean =
  when (step) {
    is FormWizardStep.QuestionStep ->
      !step.control.fieldState.isRequired &&
        !step.control.fieldState.isReadOnly &&
        !step.control.fieldState.isCalculated
    is FormWizardStep.FieldListGroupStep ->
      step.controls.isNotEmpty() &&
        step.controls.all {
          !it.fieldState.isRequired && !it.fieldState.isReadOnly && !it.fieldState.isCalculated
        }
    else -> false
  }
