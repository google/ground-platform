/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.GeoTrace
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.engine.FormEnvironment
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState
import org.groundplatform.v2.core.forms.model.ValidationError
import org.groundplatform.v2.core.forms.model.ValidationStatus

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
   * relevant child controls on a single screen per the ODK XForms specification.
   */
  data class FieldListGroupStep(
    override val stepKey: String,
    override val title: String,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val controls: List<ComponentState.ControlState>,
  ) : FormWizardStep

  /**
   * Repeat group management screen allowing enumerators to inspect existing iterations, jump to a
   * specific repeat instance, remove an instance, or add a new repeat instance.
   */
  data class RepeatHubStep(
    override val stepKey: String,
    override val title: String,
    override val breadcrumbs: List<String>,
    override val repeatContext: RepeatStepContext?,
    val repeatGroup: ComponentState.RepeatGroupState,
  ) : FormWizardStep

  /**
   * Final summary and review screen displaying all relevant questions, validation statuses,
   * evaluated ODK Entities, and the final submission button.
   */
  data class SummaryStep(
    override val stepKey: String = "__summary__",
    override val title: String = "Review & Submit",
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
   * Advances to the next screen. If [enforceValidation] is true and the current question has
   * validation errors (`REQUIRED_MISSING` or `CONSTRAINT_VIOLATED`), stays on the current step and
   * highlights the validation warning banner.
   */
  fun nextStep(enforceValidation: Boolean = true): Boolean {
    if (enforceValidation && currentStepErrors.isNotEmpty()) {
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
    val idx = steps.indexOfFirst { step ->
      when (step) {
        is FormWizardStep.QuestionStep -> step.control.canonicalPath == canonicalPath
        is FormWizardStep.FieldListGroupStep ->
          step.controls.any { it.canonicalPath == canonicalPath }
        else -> false
      }
    }
    if (idx >= 0) {
      jumpToStep(idx)
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
  }

  fun clearField(path: String) {
    showCurrentStepValidationWarning = false
    submissionResult = null
    session.clearField(path)
  }

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
                      title = groupTitle ?: "Question Group",
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
                  ?: component.canonicalPath.substringAfterLast('/').ifEmpty { "Repeat Section" }
              val relevantInstances = component.instances.filter { it.isRelevant }

              for (instance in relevantInstances) {
                val instanceTitle =
                  instance.label?.text?.takeIf { it.isNotBlank() }
                    ?: "$repeatTitle #${instance.repeatIndex}"
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
