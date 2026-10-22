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
package org.openforis.collectone.v2.workbench.ui.state

import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.app.domain.model.DeviceOrientation
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
import org.openforis.collectone.v2.workbench.ui.DEFAULT_WORKBENCH_XFORMS_XML
import org.openforis.collectone.v2.workbench.ui.XFormsParseCache

/**
 * Immutable UI state for the workbench chrome: the simulated device frame (`Mobile` vs `Tablet`,
 * `Portrait` vs `Landscape`), XForms preset state, and debug/reset state.
 */
data class WorkbenchUiState(
  val deviceFormFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  val deviceOrientation: DeviceOrientation = DeviceFormFactor.MOBILE.defaultOrientation,
  val isAirplaneMode: Boolean = false,
  val customXFormsXml: String = DEFAULT_WORKBENCH_XFORMS_XML,
  val selectedWorkbenchExampleForm: WorkbenchExampleForm? = WorkbenchExampleForm.ALL_FIELD_TYPES,
  val xformsXmlError: String? = null,
  val customFormDef: FormDef? = XFormsParseCache.formDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
  val dataResetCount: Int = 0,
  val activeSurveyId: String = "",
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val surveyConfigs: Map<String, SurveyConfig> = emptyMap(),
  val entities: List<GeospatialEntityItem> = emptyList(),
  val standaloneSubmissions: List<SubmissionPreviewItem> = emptyList(),
) {
  /** True when the active device is rotated away from its form factor's default orientation. */
  val isDeviceRotated: Boolean
    get() = deviceOrientation != deviceFormFactor.defaultOrientation

  /**
   * Effective width in `dp` of the hardware bezel for the current [deviceFormFactor] and
   * [deviceOrientation].
   */
  val effectiveFrameWidthDp: Int
    get() = deviceFormFactor.widthForOrientation(deviceOrientation)

  /**
   * Effective height in `dp` of the hardware bezel for the current [deviceFormFactor] and
   * [deviceOrientation].
   */
  val effectiveFrameHeightDp: Int
    get() = deviceFormFactor.heightForOrientation(deviceOrientation)

  /** Formatted `W × H dp` label for the current [deviceFormFactor] and [deviceOrientation]. */
  val effectiveDimensionsLabel: String
    get() = deviceFormFactor.dimensionsLabelForOrientation(deviceOrientation)

  /** All submissions (entity-attached and standalone) in the active survey. */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = entities.flatMap { it.submissions } + standaloneSubmissions

  /**
   * ID of the stored survey whose primary [FormDefinition] is [example]'s, or `null` if no stored
   * survey uses it.
   */
  fun surveyIdForExampleForm(example: WorkbenchExampleForm): String? =
    surveys.firstOrNull { surveyConfigs[it.id]?.primaryForm == example.formDefinition }?.id
}

/**
 * One-off outcomes of workbench actions that the app shell applies to other feature ViewModels or
 * navigation state.
 */
sealed interface WorkbenchEvent {
  /**
   * The workbench's parsed [FormDef] changed; forward it to `DataCollectionViewModel` so the open
   * form (if any and if [relaunchOpenForm] is `true`) refreshes with it.
   */
  data class CustomFormDefChanged(
    val formDef: FormDef?,
    val isPreset: Boolean,
    val relaunchOpenForm: Boolean = true,
  ) : WorkbenchEvent

  /**
   * An example survey was chosen in the workbench panel; open [surveyId] and optionally launch its
   * form immediately.
   */
  data class ExampleSurveySelected(val surveyId: String, val launchImmediately: Boolean) :
    WorkbenchEvent

  /**
   * The user clicked "Reset Default" while on another survey; open [surveyId]
   * (`00000033-6b50-51cd-ac94-baea2ea06d65`) before restoring the default XForms XML.
   */
  data class DefaultSurveyRestored(val surveyId: String) : WorkbenchEvent

  /** The store was reset to sample data; reset all feature ViewModels and shell state. */
  data object SampleDataReset : WorkbenchEvent

  /** Simulated connectivity changed via the workbench airplane mode toggle. */
  data class ConnectivityChanged(val isOnline: Boolean) : WorkbenchEvent

  /** A transient notice message to surface on the active survey screen. */
  data class Notice(val message: String) : WorkbenchEvent
}
