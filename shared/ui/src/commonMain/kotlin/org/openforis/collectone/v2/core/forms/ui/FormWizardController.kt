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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.media.MediaAttachment
import org.openforis.collectone.v2.core.forms.media.MediaAttachmentRejectedException
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.model.ValidationError
import org.openforis.collectone.v2.core.forms.model.ValidationStatus
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_attachment_rejected
import org.openforis.collectone.v2.core.forms.ui.resources.forms_this_question

/**
 * Screen state of the one-question-per-screen form runner over a [FormSession]: which step is
 * shown, the validation banner, the overview sheet, and the last submission attempt.
 *
 * Its properties are Compose snapshot state, so composables read them directly; it is not a
 * ViewModel and isn't thread-safe (call it from the UI thread). Edits go through the session, whose
 * synchronous state listener rebuilds [steps] and keeps the user on the same step by its key (see
 * [FormWizardStep.stepKey]) when relevance changes add or remove screens. [onRecordUpdated] gets
 * the initial state on construction and every later one, so hosts can autosave drafts.
 */
class FormWizardController(
  val session: FormSession,
  private val onRecordUpdated: ((RecordInstance, FormState) -> Unit)? = null,
  variant: FormVariant = FormVariant.MOBILE,
) {
  constructor(
    formDef: FormDef,
    existingRecord: RecordInstance? = null,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
    onRecordUpdated: ((RecordInstance, FormState) -> Unit)? = null,
    variant: FormVariant = FormVariant.MOBILE,
  ) : this(
    session =
      FormSession(
        formDef = formDef,
        existingRecord = existingRecord,
        isFirstLoad = existingRecord == null,
        environment = environment,
      ),
    onRecordUpdated = onRecordUpdated,
    variant = variant,
  )

  private val baseSkippedFieldPaths: Set<String> = session.environment.skippedFieldPaths

  /** Active [FormVariant] under which this form session is rendered. */
  var variant: FormVariant by mutableStateOf(variant)
    private set

  /** Current immutable [FormState] snapshot from [FormSession]. */
  var formState: FormState by mutableStateOf(session.state)
    private set

  /** Ordered sequence of visible mobile screens derived from [formState]. */
  var steps: List<FormWizardStep> by mutableStateOf(buildSteps(session.state))
    private set

  /** 0-based index into [steps] for the currently displayed screen. */
  var currentStepIndex: Int by mutableStateOf(0)
    private set

  /** Whether the current step's validation errors should be prominently highlighted. */
  var showCurrentStepValidationWarning: Boolean by mutableStateOf(false)
    private set

  /** Whether the question overview jump sheet is currently open. */
  var isOverviewOpen: Boolean by mutableStateOf(false)
    private set

  /** Most recent submission finalization result, if the user clicked Submit. */
  var submissionResult: FinalizationResult? by mutableStateOf(null)
    private set

  /** Map editing state of each geo question shown so far, by canonical path. */
  private val geoEditStates = mutableMapOf<String, GeoEditState>()

  init {
    session.addStateListener { newState ->
      val currentKey = currentStep.stepKey
      formState = newState
      val newSteps = buildSteps(newState)
      steps = newSteps
      val preservedIdx = newSteps.indexOfFirst { it.stepKey == currentKey }
      currentStepIndex =
        when {
          preservedIdx >= 0 -> preservedIdx
          newSteps.isEmpty() -> 0
          else -> currentStepIndex.coerceIn(0, newSteps.lastIndex)
        }
      onRecordUpdated?.invoke(newState.recordInstance, newState)
    }
    if (variant != FormVariant.MOBILE) {
      updateVariant(variant)
    } else {
      onRecordUpdated?.invoke(formState.recordInstance, formState)
    }
  }

  /**
   * Updates the active [FormVariant], marking any fields incompatible with [newVariant] as skipped
   * in [FormSession.environment] so they do not fail validation on submission.
   */
  fun updateVariant(newVariant: FormVariant) {
    val incompatiblePaths = collectIncompatibleFieldPaths(session.compiledForm, newVariant)
    val nextSkipped = baseSkippedFieldPaths + incompatiblePaths
    variant = newVariant
    if (session.environment.skippedFieldPaths != nextSkipped) {
      session.updateEnvironment(session.environment.copy(skippedFieldPaths = nextSkipped))
    }
  }

  /** Currently active [FormWizardStep]. */
  val currentStep: FormWizardStep
    get() = steps.getOrElse(currentStepIndex) { FormWizardStep.SummaryStep() }

  /** Total number of steps (including the final Review & Submit screen). */
  val totalSteps: Int
    get() = steps.size

  /** Total number of question/group steps (excluding the final Review & Submit screen). */
  val totalQuestionSteps: Int
    get() = (steps.size - 1).coerceAtLeast(0)

  /** Returns true if there is a previous screen before [currentStepIndex]. */
  val canGoBack: Boolean
    get() = currentStepIndex > 0

  /** Returns true if there is a subsequent screen after [currentStepIndex]. */
  val canGoNext: Boolean
    get() = currentStepIndex < steps.lastIndex

  /** Validation errors applicable to the controls on the [currentStep]. */
  val currentStepErrors: List<ValidationError>
    get() =
      when (val step = currentStep) {
        is FormWizardStep.QuestionStep -> errorsForControl(step.control)
        is FormWizardStep.FieldListGroupStep -> step.controls.flatMap { errorsForControl(it) }
        is FormWizardStep.RepeatHubStep -> emptyList()
        is FormWizardStep.SummaryStep -> formState.validationErrors
      }

  /**
   * Whether the [currentStep] contains a geometry (`TYPE_GEOPOINT`, `TYPE_GEOTRACE`, or
   * `TYPE_GEOSHAPE`) question waiting for coordinates / vertices to be captured before advancing
   * via "Next".
   */
  val isCurrentStepWaitingForLocationCapture: Boolean
    get() = isWaitingForGeometryCapture(currentStep, variant)

  /**
   * Whether the [currentStep] is an optional question (all its input controls are not required).
   */
  val isCurrentStepOptional: Boolean
    get() = isStepOptional(currentStep)

  /**
   * Advances to the next screen. If [enforceValidation] is true and the current question has
   * validation errors (`REQUIRED_MISSING` or `CONSTRAINT_VIOLATED`) or is waiting for a location
   * capture, stays on the current step and highlights the validation warning banner.
   */
  fun nextStep(enforceValidation: Boolean = true): Boolean {
    if (
      enforceValidation &&
        (currentStepErrors.isNotEmpty() || isCurrentStepWaitingForLocationCapture)
    ) {
      showCurrentStepValidationWarning = true
      return false
    }
    showCurrentStepValidationWarning = false
    if (currentStepIndex < steps.lastIndex) {
      currentStepIndex++
      return true
    }
    return false
  }

  /** Opens the question overview jump sheet. */
  fun openOverview() {
    isOverviewOpen = true
  }

  /** Closes the question overview jump sheet. */
  fun closeOverview() {
    isOverviewOpen = false
  }

  /** Navigates back to the previous screen. */
  fun previousStep() {
    showCurrentStepValidationWarning = false
    if (currentStepIndex > 0) {
      currentStepIndex--
    }
  }

  /** Jumps directly to the specified 0-based step [index]. */
  fun jumpToStep(index: Int) {
    if (index in steps.indices) {
      showCurrentStepValidationWarning = false
      currentStepIndex = index
      isOverviewOpen = false
    }
  }

  /** Jumps to the step containing the control with [canonicalPath]. */
  fun jumpToField(canonicalPath: String) {
    val idx = steps.indexOfFirst { step ->
      FormQuestionLookup.stepContainsField(step, canonicalPath)
    }
    if (idx >= 0) {
      jumpToStep(idx)
    }
  }

  /**
   * Collector-facing title of the question at [canonicalPath] (its label), for messages that refer
   * to a field. Falls back to a localized "This question" when the question can't be found.
   */
  fun questionTitleFor(canonicalPath: String): FormUiText =
    questionTitleOrNull(canonicalPath)?.let(FormUiText::Verbatim)
      ?: FormUiText.Resource(Res.string.forms_this_question)

  /**
   * Like [questionTitleFor], but returns `null` instead of a generic stand-in when the question
   * can't be found.
   */
  fun questionTitleOrNull(canonicalPath: String): String? =
    FormQuestionLookup.questionTitleOrNull(steps, formState, canonicalPath)

  /**
   * The parent question of a cascading select, for the child's choice filtering and its "answer
   * <parent> first" hint. [selectedValues] holds the parent's choice codes.
   */
  data class ParentQuestionInfo(
    val path: String,
    val label: String,
    val selectedValues: List<String>,
    val isAnswered: Boolean,
  )

  /**
   * Evaluates cascading filter metadata for [control] if it declares
   * [ControlDef.parent_question_key]. Returns `null` if the control is not cascading.
   */
  fun parentQuestionInfoFor(control: ComponentState.ControlState): ParentQuestionInfo? {
    val parentRef = control.controlDef.parent_question_key.trim()
    if (parentRef.isBlank()) return null

    val fieldState = CascadingSelects.parentFieldFor(formState, control)
    val parentPath = fieldState?.canonicalPath ?: parentRef
    val parentLabel = questionTitleOrNull(parentPath) ?: parentRef.substringAfterLast('/')

    val selectedValues = CascadingSelects.selectedValues(fieldState)

    return ParentQuestionInfo(
      path = parentPath,
      label = parentLabel,
      selectedValues = selectedValues,
      isAnswered = selectedValues.isNotEmpty(),
    )
  }

  // ---------------------------------------------------------------------------
  // Field & Repeat Mutation Actions
  // ---------------------------------------------------------------------------

  /**
   * Resets the per-attempt feedback before an answer changes: the validation banner and the last
   * submission result describe the previous answers, so they'd be stale after the edit.
   */
  private fun beginEdit() {
    showCurrentStepValidationWarning = false
    submissionResult = null
  }

  /**
   * Answers a text or select question. An empty string clears the answer (an XForms empty value),
   * and cascading selects under [path] drop choices the new answer no longer allows.
   */
  fun updateString(path: String, value: String) {
    beginEdit()
    if (value.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateString(path, value)
    }
    CascadingSelects.pruneChildAnswers(session, path)
  }

  /** Answers a numeric question; `null` (an empty or unparsable input) clears it. */
  fun updateInt(path: String, value: Int?) {
    beginEdit()
    if (value == null) session.clearField(path) else session.updateInt(path, value)
  }

  fun updateLong(path: String, value: Long?) {
    beginEdit()
    if (value == null) session.clearField(path) else session.updateLong(path, value)
  }

  fun updateDouble(path: String, value: Double?) {
    beginEdit()
    if (value == null) session.clearField(path) else session.updateDouble(path, value)
  }

  fun updateBoolean(path: String, value: Boolean?) {
    beginEdit()
    if (value == null) session.clearField(path) else session.updateBoolean(path, value)
  }

  /** Answers a date question; [month] and [day] are 1-based (`google.type.Date`). */
  fun updateDate(path: String, year: Int, month: Int, day: Int) {
    beginEdit()
    session.updateField(
      path,
      FieldValue(
        scalar_value = TypedValue(date_value = Date(year = year, month = month, day = day))
      ),
    )
  }

  fun updateTime(path: String, hours: Int, minutes: Int, seconds: Int = 0) {
    beginEdit()
    session.updateField(
      path,
      FieldValue(
        scalar_value =
          TypedValue(time_value = TimeOfDay(hours = hours, minutes = minutes, seconds = seconds))
      ),
    )
  }

  /** Answers a date-time question with an instant in seconds since the Unix epoch (UTC). */
  fun updateTimestamp(path: String, epochSeconds: Long) {
    beginEdit()
    session.updateField(
      path,
      FieldValue(scalar_value = TypedValue(timestamp_value = ofEpochSecond(epochSeconds, 0L))),
    )
  }

  /**
   * Answers a geopoint question. The widgets always pass the captured accuracy (`null` when there
   * is none); the 3.5 m default only serves callers that don't track it, such as tests.
   */
  fun updateGeoPoint(
    path: String,
    latitude: Double,
    longitude: Double,
    altitudeMeters: Double? = null,
    accuracyMeters: Double? = 3.5,
  ) {
    beginEdit()
    session.updateGeoPoint(
      path = path,
      latitude = latitude,
      longitude = longitude,
      altitudeMeters = altitudeMeters,
      accuracyMeters = accuracyMeters,
    )
  }

  /**
   * Answers a geotrace (line) question; an empty list clears it. Fewer than 2 points is stored as
   * is: the step still blocks Next until the trace is complete (see [isWaitingForGeometryCapture]).
   */
  fun updateGeoTrace(path: String, points: List<GeoPoint>) {
    beginEdit()
    if (points.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateField(
        path,
        FieldValue(scalar_value = TypedValue(geotrace_value = GeoTrace(points = points))),
      )
    }
  }

  /**
   * Answers a geoshape (polygon) question with its vertices, listed without repeating the first
   * one; an empty list clears it.
   */
  fun updateGeoShape(path: String, points: List<GeoPoint>) {
    beginEdit()
    if (points.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateField(
        path,
        FieldValue(scalar_value = TypedValue(geoshape_value = GeoShape(points = points))),
      )
    }
  }

  /**
   * Answers a select-multiple or rank question with choice codes in order; an empty list clears it.
   * Cascading selects under [path] are pruned as in [updateString].
   */
  fun updateMultiSelect(path: String, selectedValues: List<String>) {
    beginEdit()
    if (selectedValues.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateMultiSelect(path, selectedValues)
    }
    CascadingSelects.pruneChildAnswers(session, path)
  }

  /** Clears the answer at [path] and prunes cascading selects under it. */
  fun clearField(path: String) {
    beginEdit()
    session.clearField(path)
    CascadingSelects.pruneChildAnswers(session, path)
  }

  /**
   * Answers the photo / video / audio question at [path] with [attachment]. Returns why the
   * attachment was rejected (e.g. wrong media type), or `null` on success.
   */
  fun attachMedia(path: String, attachment: MediaAttachment): FormUiText? {
    beginEdit()
    return try {
      session.attachMedia(path, attachment)
      null
    } catch (e: MediaAttachmentRejectedException) {
      e.message?.let(FormUiText::Verbatim)
        ?: FormUiText.Resource(Res.string.forms_attachment_rejected)
    }
  }

  /** Clears the media question at [path] and discards its attachment. */
  fun removeMedia(path: String) {
    beginEdit()
    session.removeMedia(path)
  }

  /** The attachment currently answering the media question at [path], if its bytes are loaded. */
  fun attachmentFor(path: String): MediaAttachment? = session.attachmentFor(path)

  /** Attachments that accompany a submission of the current record (relevant answers only). */
  val referencedAttachments: List<MediaAttachment>
    get() = session.referencedAttachments()

  /**
   * Appends a new repeat instance to [repeatPath] and navigates directly to the first question of
   * the newly created repeat instance.
   */
  fun addRepeatInstanceAndOpen(repeatPath: String) {
    beginEdit()
    val updatedState = session.addRepeatInstance(repeatPath)
    val repeatGroup = findRepeatGroup(updatedState.rootComponents, repeatPath)
    val newInstancePath = repeatGroup?.instances?.lastOrNull()?.canonicalPath
    if (newInstancePath != null) {
      val targetIdx = steps.indexOfFirst { it.isWithinRepeatInstance(newInstancePath) }
      if (targetIdx >= 0) {
        currentStepIndex = targetIdx
      }
    }
  }

  /** Removes the 1-based [repeatIndex] instance from [repeatPath]. */
  fun removeRepeatInstance(repeatPath: String, repeatIndex: Int) {
    beginEdit()
    session.removeRepeatInstance(repeatPath, repeatIndex)
    // Later instances shift down to the removed index, so per-path map history would now belong to
    // a different entry.
    geoEditStates.keys.removeAll { it.startsWith("$repeatPath[") }
  }

  /**
   * Map editing state (pan, zoom, walking, undo / redo) of the geo question at [path]. The same
   * instance is returned for the whole session, so it survives recomposition and navigating away
   * from the question and back.
   */
  fun geoEditState(path: String): GeoEditState = geoEditStates.getOrPut(path) { GeoEditState() }

  /** Jumps to the first step of the repeat entry at [instancePath], if it has any. */
  fun openRepeatInstance(instancePath: String) {
    val targetIdx = steps.indexOfFirst { it.isWithinRepeatInstance(instancePath) }
    if (targetIdx >= 0) {
      jumpToStep(targetIdx)
    }
  }

  /**
   * Instance path of the previous relevant entry in [repeatContext]'s repeat group, or `null` at
   * the first entry.
   */
  internal fun previousRepeatInstancePath(repeatContext: RepeatStepContext): String? {
    val siblings = siblingRepeatInstancePaths(repeatContext.repeatGroupPath)
    val pos = siblings.indexOf(repeatContext.instancePath)
    return if (pos > 0) siblings[pos - 1] else null
  }

  /**
   * Instance path of the next relevant entry in [repeatContext]'s repeat group, or `null` at the
   * last entry.
   */
  internal fun nextRepeatInstancePath(repeatContext: RepeatStepContext): String? {
    val siblings = siblingRepeatInstancePaths(repeatContext.repeatGroupPath)
    val pos = siblings.indexOf(repeatContext.instancePath)
    return if (pos >= 0 && pos < siblings.lastIndex) siblings[pos + 1] else null
  }

  /**
   * Jumps from [fromInstancePath] to sibling entry [toInstancePath], landing on the corresponding
   * question step when it is relevant in [toInstancePath] and falling back to [toInstancePath]'s
   * first step otherwise.
   */
  internal fun openSiblingRepeatInstance(fromInstancePath: String, toInstancePath: String) {
    val activeKey = currentStep.stepKey
    val matchingKey =
      when {
        activeKey.startsWith("$fromInstancePath/") ->
          toInstancePath + activeKey.removePrefix(fromInstancePath)
        activeKey.startsWith("group:$fromInstancePath/") ->
          "group:$toInstancePath" + activeKey.removePrefix("group:$fromInstancePath")
        activeKey.startsWith("repeat-hub:$fromInstancePath/") ->
          "repeat-hub:$toInstancePath" + activeKey.removePrefix("repeat-hub:$fromInstancePath")
        else -> null
      }
    val matchingIdx = matchingKey?.let { key -> steps.indexOfFirst { it.stepKey == key } } ?: -1
    if (matchingIdx >= 0) {
      jumpToStep(matchingIdx)
    } else {
      openRepeatInstance(toInstancePath)
    }
  }

  private fun siblingRepeatInstancePaths(repeatGroupPath: String): List<String> =
    steps
      .mapNotNull { step ->
        val instancePath = step.repeatContext?.instancePath ?: return@mapNotNull null
        repeatInstancePrefixes(instancePath).firstOrNull {
          it.substringBeforeLast('[') == repeatGroupPath
        }
      }
      .distinct()

  private fun FormWizardStep.isWithinRepeatInstance(instancePath: String): Boolean {
    val path = repeatContext?.instancePath ?: return false
    return path == instancePath || path.startsWith("$instancePath/")
  }

  /** Navigates directly to the [FormWizardStep.RepeatHubStep] for repeat group [repeatPath]. */
  fun openRepeatHub(repeatPath: String) {
    val targetIdx = steps.indexOfFirst {
      it is FormWizardStep.RepeatHubStep && it.repeatGroup.canonicalPath == repeatPath
    }
    if (targetIdx >= 0) {
      jumpToStep(targetIdx)
    }
  }

  /**
   * Switches the form's language (an XForms `itext` translation); labels, hints, and choice labels
   * re-evaluate, and answers are kept because they store choice codes, not labels.
   */
  fun setLanguage(language: String) {
    session.setLanguage(language)
  }

  /**
   * Validates the whole record for submission and stamps its end-of-form preloads (ODK `end`). On
   * failure the validation banner is shown; the user stays on the current step and finds the errors
   * on the summary step.
   */
  fun finalizeForm(): FinalizationResult {
    val result = session.finalize()
    submissionResult = result
    if (result is FinalizationResult.ValidationFailure) {
      showCurrentStepValidationWarning = true
    }
    return result
  }

  private fun errorsForControl(control: ComponentState.ControlState): List<ValidationError> =
    when (val status = control.fieldState.validationStatus) {
      is ValidationStatus.Valid -> emptyList()
      is ValidationStatus.Invalid -> status.errors
    }

  private fun findRepeatGroup(
    components: List<ComponentState>,
    targetPath: String,
  ): ComponentState.RepeatGroupState? {
    for (component in components) {
      when (component) {
        is ComponentState.RepeatGroupState -> {
          if (component.canonicalPath == targetPath) return component
          for (inst in component.instances) {
            findRepeatGroup(inst.children, targetPath)?.let {
              return it
            }
          }
        }
        is ComponentState.GroupState -> {
          findRepeatGroup(component.children, targetPath)?.let {
            return it
          }
        }
        is ComponentState.ControlState -> {}
      }
    }
    return null
  }

  companion object {
    /**
     * Flattens the hierarchical [FormState.rootComponents] tree into an ordered list of relevant
     * mobile wizard screens; see [FormWizardSteps.buildSteps].
     */
    fun buildSteps(state: FormState): List<FormWizardStep> = FormWizardSteps.buildSteps(state)

    /** Short preview of [control]'s answer; see [FormWizardSteps.formatControlPreview]. */
    fun formatControlPreview(control: ComponentState.ControlState): FormUiText? =
      FormWizardSteps.formatControlPreview(control)
  }
}
