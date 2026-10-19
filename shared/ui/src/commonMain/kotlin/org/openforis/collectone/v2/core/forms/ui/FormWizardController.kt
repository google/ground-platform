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
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.GeoTrace
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.TypedValue
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.media.MediaAttachment
import org.openforis.collectone.v2.core.forms.media.MediaAttachmentRejectedException
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.model.ValidationError
import org.openforis.collectone.v2.core.forms.model.ValidationStatus

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
)

/**
 * Human-readable preview summary of a single repeat instance for repeat hubs and overview screens.
 */
data class RepeatInstanceSummary(
  val repeatIndex: Int,
  val title: String,
  val previewText: String,
  val instancePath: String,
  val answeredCount: Int = 0,
)

/** Represents a single navigable screen in the mobile one-question-at-a-time form runner. */
sealed interface FormWizardStep {
  /** Stable identifier used to preserve user position when dynamic steps are recomputed. */
  val stepKey: String

  /** Display title for step overview lists and navigation headers. */
  val title: String

  /** Enclosing group / repeat breadcrumb labels leading to this step. */
  val breadcrumbs: List<String>

  /** Optional enclosing repeat iteration metadata if this step lives inside a repeat. */
  val repeatContext: RepeatStepContext?

  /** Standard single-question screen rendering a single [ComponentState.ControlState]. */
  data class QuestionStep(
    override val stepKey: String,
    override val title: String,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val control: ComponentState.ControlState,
  ) : FormWizardStep

  /**
   * Multi-question group screen for groups configured with `appearance="field-list"`, rendering all
   * relevant child controls on a single screen per the XForms specification.
   */
  data class FieldListGroupStep(
    override val stepKey: String,
    override val title: String,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val controls: List<ComponentState.ControlState>,
  ) : FormWizardStep

  /**
   * Repeat group management screen allowing data collectors to inspect existing iterations, jump to
   * a specific repeat instance, remove an instance, or add a new repeat instance.
   */
  data class RepeatHubStep(
    override val stepKey: String,
    override val title: String,
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
    override val title: String = "Review & submit",
    override val breadcrumbs: List<String> = emptyList(),
    override val repeatContext: RepeatStepContext? = null,
  ) : FormWizardStep
}

/**
 * Reactive controller driving single-question-per-screen navigation and live form state mutations
 * over a [FormSession].
 */
class FormWizardController(
  val session: FormSession,
  private val onRecordUpdated: ((RecordInstance, FormState) -> Unit)? = null,
) {
  constructor(
    formDef: FormDef,
    existingRecord: RecordInstance? = null,
    environment: FormEnvironment = FormEnvironment.DEFAULT,
    onRecordUpdated: ((RecordInstance, FormState) -> Unit)? = null,
  ) : this(
    session =
      FormSession(
        formDef = formDef,
        existingRecord = existingRecord,
        isFirstLoad = existingRecord == null,
        environment = environment,
      ),
    onRecordUpdated = onRecordUpdated,
  )

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

  /** Most recent submission finalization result, if the user clicked Submit. */
  var submissionResult: FinalizationResult? by mutableStateOf(null)
    private set

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
    // Notify initial state
    onRecordUpdated?.invoke(formState.recordInstance, formState)
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
    get() = isWaitingForGeometryCapture(currentStep)

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
    val idx = steps.indexOfFirst { step -> stepContainsField(step, canonicalPath) }
    if (idx >= 0) {
      jumpToStep(idx)
    }
  }

  /**
   * Collector-facing title of the question at [canonicalPath] (its label), for messages that refer
   * to a field. Falls back to the step title, never to the internal data name.
   */
  fun questionTitleFor(canonicalPath: String): String {
    val step = steps.firstOrNull { stepContainsField(it, canonicalPath) }
    val control =
      when (step) {
        is FormWizardStep.QuestionStep -> step.control
        is FormWizardStep.FieldListGroupStep ->
          step.controls.firstOrNull { matchesPath(it.canonicalPath, canonicalPath) }
        else -> null
      }
    val title =
      control?.label?.text?.takeIf { it.isNotBlank() } ?: step?.title?.takeIf { it.isNotBlank() }
    if (title != null && title != "This question") return title

    val leaf = canonicalPath.trim().substringAfterLast('/')
    val componentControl =
      collectAllControls(formState.rootComponents).firstOrNull {
        matchesPath(it.canonicalPath, canonicalPath) || it.controlDef.field_ref == leaf
      }
    return componentControl?.label?.text?.takeIf { it.isNotBlank() }
      ?: formState.formDef.view
        ?.components
        ?.mapNotNull { it.control }
        ?.firstOrNull {
          it.field_ref == leaf || it.field_ref == canonicalPath
        }
        ?.label
        ?.text
        ?.takeIf { it.isNotBlank() }
      ?: "This question"
  }

  private fun stepContainsField(step: FormWizardStep, canonicalPath: String): Boolean =
    when (step) {
      is FormWizardStep.QuestionStep -> matchesPath(step.control.canonicalPath, canonicalPath)
      is FormWizardStep.FieldListGroupStep ->
        step.controls.any { matchesPath(it.canonicalPath, canonicalPath) }
      else -> false
    }

  private fun matchesPath(fullPath: String, target: String): Boolean {
    val cleanTarget = target.trim()
    val cleanFull = fullPath.trim()
    return cleanFull == cleanTarget ||
      cleanFull.substringAfterLast('/') == cleanTarget ||
      cleanTarget.substringAfterLast('/') == cleanFull.substringAfterLast('/')
  }

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

    val fieldState = formState.findFieldState(parentRef)
    val parentPath = fieldState?.canonicalPath ?: parentRef
    val parentLabel =
      questionTitleFor(parentPath).let {
        if (it == "This question") parentRef.substringAfterLast('/') else it
      }

    val stringVal = fieldState?.value?.scalar_value?.string_value?.trim()
    val listVal = fieldState?.value?.list_value?.values?.mapNotNull { it.string_value?.trim() }
    val selectedValues =
      when {
        !listVal.isNullOrEmpty() -> listVal.filter { it.isNotBlank() }
        !stringVal.isNullOrBlank() ->
          stringVal.split(' ').map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
      }

    return ParentQuestionInfo(
      path = parentPath,
      label = parentLabel,
      selectedValues = selectedValues,
      isAnswered = selectedValues.isNotEmpty(),
    )
  }

  private fun collectAllControls(
    components: List<ComponentState>
  ): List<ComponentState.ControlState> {
    val result = mutableListOf<ComponentState.ControlState>()
    fun walk(list: List<ComponentState>) {
      for (item in list) {
        when (item) {
          is ComponentState.ControlState -> result.add(item)
          is ComponentState.GroupState -> walk(item.children)
          is ComponentState.RepeatGroupState -> {
            // Repeat instances are dynamic
          }
        }
      }
    }
    walk(components)
    return result
  }

  private fun pruneCascadingChildAnswers(
    changedPath: String,
    visited: MutableSet<String> = mutableSetOf(),
  ) {
    if (changedPath in visited) return
    visited.add(changedPath)

    val leafName = changedPath.substringAfterLast('/')
    val parentField = formState.findFieldState(changedPath)
    val parentStringVal = parentField?.value?.scalar_value?.string_value?.trim()
    val parentListVal =
      parentField?.value?.list_value?.values?.mapNotNull { it.string_value?.trim() }
    val parentValues =
      when {
        !parentListVal.isNullOrEmpty() -> parentListVal.filter { it.isNotBlank() }.toSet()
        !parentStringVal.isNullOrBlank() ->
          parentStringVal.split(' ').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        else -> emptySet()
      }

    val allControls = collectAllControls(formState.rootComponents)
    for (child in allControls) {
      val childParentRef = child.controlDef.parent_question_key.trim()
      if (childParentRef.isBlank()) continue
      if (
        childParentRef != changedPath &&
          childParentRef != leafName &&
          !changedPath.endsWith("/$childParentRef") &&
          !childParentRef.endsWith("/$leafName")
      ) {
        continue
      }

      val childField = formState.findFieldState(child.canonicalPath) ?: continue
      val childString = childField.value?.scalar_value?.string_value?.trim()
      val childList = childField.value?.list_value?.values?.mapNotNull { it.string_value?.trim() }

      if (parentValues.isEmpty()) {
        val hasChildValue = !childString.isNullOrBlank() || !childList.isNullOrEmpty()
        if (hasChildValue) {
          session.clearField(child.canonicalPath)
          pruneCascadingChildAnswers(child.canonicalPath, visited)
        }
      } else {
        if (!childString.isNullOrBlank()) {
          val selectedOption = child.options.firstOrNull { it.value == childString }
          val isInvalid =
            selectedOption != null &&
              selectedOption.parentValue.isNotBlank() &&
              selectedOption.parentValue !in parentValues
          if (isInvalid) {
            session.clearField(child.canonicalPath)
            pruneCascadingChildAnswers(child.canonicalPath, visited)
          }
        } else if (!childList.isNullOrEmpty()) {
          val validValues = childList.filter { valVal ->
            val opt = child.options.firstOrNull { it.value == valVal }
            opt == null || opt.parentValue.isBlank() || opt.parentValue in parentValues
          }
          if (validValues.size != childList.size) {
            if (validValues.isEmpty()) {
              session.clearField(child.canonicalPath)
            } else {
              session.updateMultiSelect(child.canonicalPath, validValues)
            }
            pruneCascadingChildAnswers(child.canonicalPath, visited)
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Field & Repeat Mutation Actions
  // ---------------------------------------------------------------------------

  fun updateString(path: String, value: String) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (value.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateString(path, value)
    }
    pruneCascadingChildAnswers(path)
  }

  fun updateInt(path: String, value: Int?) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (value == null) session.clearField(path) else session.updateInt(path, value)
  }

  fun updateLong(path: String, value: Long?) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (value == null) session.clearField(path) else session.updateLong(path, value)
  }

  fun updateDouble(path: String, value: Double?) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (value == null) session.clearField(path) else session.updateDouble(path, value)
  }

  fun updateBoolean(path: String, value: Boolean?) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (value == null) session.clearField(path) else session.updateBoolean(path, value)
  }

  fun updateDate(path: String, year: Int, month: Int, day: Int) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.updateField(
      path,
      FieldValue(
        scalar_value = TypedValue(date_value = Date(year = year, month = month, day = day))
      ),
    )
  }

  fun updateTime(path: String, hours: Int, minutes: Int, seconds: Int = 0) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.updateField(
      path,
      FieldValue(
        scalar_value =
          TypedValue(time_value = TimeOfDay(hours = hours, minutes = minutes, seconds = seconds))
      ),
    )
  }

  fun updateTimestamp(path: String, epochSeconds: Long) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.updateField(
      path,
      FieldValue(scalar_value = TypedValue(timestamp_value = ofEpochSecond(epochSeconds, 0L))),
    )
  }

  fun updateGeoPoint(
    path: String,
    latitude: Double,
    longitude: Double,
    altitudeMeters: Double = 0.0,
    accuracyMeters: Double = 3.5,
  ) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.updateGeoPoint(
      path = path,
      latitude = latitude,
      longitude = longitude,
      altitudeMeters = altitudeMeters,
      accuracyMeters = accuracyMeters,
    )
  }

  fun updateGeoTrace(path: String, points: List<GeoPoint>) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (points.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateField(
        path,
        FieldValue(scalar_value = TypedValue(geotrace_value = GeoTrace(points = points))),
      )
    }
  }

  fun updateGeoShape(path: String, points: List<GeoPoint>) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (points.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateField(
        path,
        FieldValue(scalar_value = TypedValue(geoshape_value = GeoShape(points = points))),
      )
    }
  }

  fun updateMultiSelect(path: String, selectedValues: List<String>) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    if (selectedValues.isEmpty()) {
      session.clearField(path)
    } else {
      session.updateMultiSelect(path, selectedValues)
    }
    pruneCascadingChildAnswers(path)
  }

  fun clearField(path: String) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.clearField(path)
    pruneCascadingChildAnswers(path)
  }

  /**
   * Answers the photo / video / audio question at [path] with [attachment]. Returns an error
   * message if the attachment was rejected (e.g. wrong media type), or `null` on success.
   */
  fun attachMedia(path: String, attachment: MediaAttachment): String? {
    showCurrentStepValidationWarning = false
    submissionResult = null
    return try {
      session.attachMedia(path, attachment)
      null
    } catch (e: MediaAttachmentRejectedException) {
      e.message ?: "Attachment rejected"
    }
  }

  /** Clears the media question at [path] and discards its attachment. */
  fun removeMedia(path: String) {
    showCurrentStepValidationWarning = false
    submissionResult = null
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
    showCurrentStepValidationWarning = false
    submissionResult = null
    val updatedState = session.addRepeatInstance(repeatPath)
    val repeatGroup = findRepeatGroup(updatedState.rootComponents, repeatPath)
    val newInstancePath = repeatGroup?.instances?.lastOrNull()?.canonicalPath
    if (newInstancePath != null) {
      val targetIdx = steps.indexOfFirst { step ->
        step.repeatContext?.instancePath == newInstancePath
      }
      if (targetIdx >= 0) {
        currentStepIndex = targetIdx
      }
    }
  }

  /** Removes the 1-based [repeatIndex] instance from [repeatPath]. */
  fun removeRepeatInstance(repeatPath: String, repeatIndex: Int) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.removeRepeatInstance(repeatPath, repeatIndex)
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

  /** Switches active translation language. */
  fun setLanguage(language: String) {
    session.setLanguage(language)
  }

  /** Finalizes and validates the form session. */
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
     * mobile wizard screens.
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
              val title =
                component.label?.text?.takeIf { it.isNotBlank() }
                  ?: component.canonicalPath.substringAfterLast('/')
              result.add(
                FormWizardStep.QuestionStep(
                  stepKey = component.canonicalPath,
                  title = title,
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
              val nextBreadcrumbs =
                if (groupTitle != null) breadcrumbs + groupTitle else breadcrumbs
              val isFieldList = component.appearance.split(' ').contains("field-list")
              if (isFieldList) {
                val directControls = collectRelevantControls(component.children)
                if (directControls.isNotEmpty()) {
                  result.add(
                    FormWizardStep.FieldListGroupStep(
                      stepKey = "group:${component.canonicalPath}",
                      title = groupTitle ?: "Question group",
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
                  ?: component.canonicalPath.substringAfterLast('/').ifEmpty { "Repeat section" }
              val relevantInstances = component.instances.filter { it.isRelevant }

              for (instance in relevantInstances) {
                val dynamicLabel = instance.label?.text?.trim()
                val instanceTitle =
                  if (!dynamicLabel.isNullOrBlank() && dynamicLabel != repeatTitle) {
                    dynamicLabel
                  } else {
                    "$repeatTitle #${instance.repeatIndex}"
                  }
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
                  )
                visitComponents(
                  components = instance.children,
                  breadcrumbs = breadcrumbs + instanceTitle,
                  repeatContext = instanceContext,
                )
              }

              val summaries = relevantInstances.map { instance ->
                val dynamicLabel = instance.label?.text?.trim()
                val instTitle =
                  if (!dynamicLabel.isNullOrBlank() && dynamicLabel != repeatTitle) {
                    dynamicLabel
                  } else {
                    "$repeatTitle #${instance.repeatIndex}"
                  }
                val controls = collectRelevantControls(instance.children)
                val answeredValues = controls.mapNotNull { formatControlPreview(it) }
                val preview =
                  when {
                    answeredValues.isEmpty() -> "Tap to fill out details"
                    else -> answeredValues.take(3).joinToString(" • ")
                  }
                RepeatInstanceSummary(
                  repeatIndex = instance.repeatIndex,
                  title = instTitle,
                  previewText = preview,
                  instancePath = instance.canonicalPath,
                  answeredCount = answeredValues.size,
                )
              }

              // Always provide a RepeatHubStep if the user can add/remove instances or if 0
              // instances exist
              if (
                component.canAddInstance ||
                  component.canRemoveInstance ||
                  relevantInstances.isEmpty()
              ) {
                result.add(
                  FormWizardStep.RepeatHubStep(
                    stepKey = "repeat-hub:${component.canonicalPath}",
                    title = "$repeatTitle (${relevantInstances.size})",
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

    fun formatControlPreview(control: ComponentState.ControlState): String? {
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
        val b = scalar.bool_value
        if (b != null) return if (b) "Yes" else "No"
        val date = scalar.date_value
        if (date != null && (date.year != 0 || date.month != 0 || date.day != 0)) {
          return "${date.year}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
        }
        if (scalar.geopoint_value != null) return "Location recorded"
        if (scalar.geotrace_value != null) return "Trace recorded"
        if (scalar.geoshape_value != null) return "Shape recorded"
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
  }
}

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
fun isWaitingForGeometryCapture(step: FormWizardStep): Boolean =
  when (step) {
    is FormWizardStep.QuestionStep -> isControlWaitingForGeometryCapture(step.control)
    is FormWizardStep.FieldListGroupStep ->
      step.controls.any { isControlWaitingForGeometryCapture(it) }
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
