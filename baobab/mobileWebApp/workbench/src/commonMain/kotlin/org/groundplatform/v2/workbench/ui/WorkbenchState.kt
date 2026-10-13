/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.workbench.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.domain.model.DeviceFormFactor
import org.groundplatform.v2.app.domain.model.DeviceOrientation
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.mobile.ui.state.SyncStatusSimulator
import org.groundplatform.v2.workbench.ui.state.WorkbenchEvent
import org.groundplatform.v2.workbench.ui.state.WorkbenchUiState
import org.groundplatform.v2.workbench.ui.viewmodel.WorkbenchActions
import org.groundplatform.v2.workbench.ui.viewmodel.WorkbenchViewModel

/**
 * State holder of the prototype workbench around the [mobile] app preview: the simulated device
 * (form factor, orientation, airplane mode), the live XForms editor and example survey switcher,
 * and the simulation and reset tools ([WorkbenchViewModel]).
 *
 * The workbench drives the [mobile] app: it applies its form definition, upload simulator, and
 * resets to it.
 */
class WorkbenchState
private constructor(
  val mobile: MobileAppState,
  /** ViewModel of the workbench chrome. Its views observe [WorkbenchViewModel.uiState] directly. */
  val viewModel: WorkbenchViewModel,
) : WorkbenchActions by viewModel {

  constructor(
    mobile: MobileAppState
  ) : this(
    mobile = mobile,
    viewModel =
      WorkbenchViewModel(
        surveyRepository = mobile.dataHolder.surveyRepository,
        sampleDataRepository = mobile.dataHolder.sampleDataRepository,
        scope = mobile.dataHolder.scope,
        connectivityRepository = mobile.dataHolder.connectivityRepository,
      ),
  )

  private var state by mutableStateOf(viewModel.uiState.value)

  /** Latest [WorkbenchUiState], for views that build the workbench chrome. */
  val uiState: WorkbenchUiState
    get() = state

  private val syncStatusSimulator =
    object : SyncStatusSimulator {
      override fun cycleEntitySyncStatus(entityId: String) =
        viewModel.cycleEntitySyncStatus(entityId)

      override fun cycleSubmissionSyncStatus(submissionId: String) =
        viewModel.cycleSubmissionSyncStatus(submissionId)
    }

  init {
    val scope = mobile.dataHolder.scope
    scope.launch { viewModel.uiState.collect { state = it } }
    scope.launch { viewModel.events.collect(::onWorkbenchEvent) }
    mobile.surveyActivationListener = viewModel::onSurveyActivated
    mobile.syncStatusSimulator = syncStatusSimulator
    mobile.dataCollection.updateCustomFormDef(
      XFormsParseCache.formDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
      isPreset = true,
    )
  }

  /** Applies a workbench outcome to the driven sessions. */
  private fun onWorkbenchEvent(event: WorkbenchEvent) {
    when (event) {
      is WorkbenchEvent.CustomFormDefChanged ->
        mobile.dataCollection.updateCustomFormDef(
          formDef = event.formDef,
          isPreset = event.isPreset,
          relaunchOpenForm = event.relaunchOpenForm,
        )
      is WorkbenchEvent.ExampleSurveySelected -> {
        mobile.openSurvey(event.surveyId)
        if (event.launchImmediately) {
          mobile.launchActiveOrDefaultFormForTesting()
        }
      }
      is WorkbenchEvent.DefaultSurveyRestored -> mobile.openSurvey(event.surveyId)
      WorkbenchEvent.PrototypeReset -> mobile.reset()
      is WorkbenchEvent.ConnectivityChanged -> mobile.onConnectivityChanged(event.isOnline)
      is WorkbenchEvent.Notice -> mobile.showNotice(event.message)
    }
  }

  /** Active hardware preview bezel form factor (`Mobile` vs `Tablet`). */
  val deviceFormFactor: DeviceFormFactor
    get() = state.deviceFormFactor

  /** Active screen orientation of the hardware preview bezel (`Portrait` vs `Landscape`). */
  val deviceOrientation: DeviceOrientation
    get() = state.deviceOrientation

  /** True when the active device is rotated away from its form factor's default orientation. */
  val isDeviceRotated: Boolean
    get() = state.isDeviceRotated

  /** Effective width in `dp` of the hardware bezel for the current form factor and orientation. */
  val effectiveFrameWidthDp: Int
    get() = state.effectiveFrameWidthDp

  /** Effective height in `dp` of the hardware bezel for the current form factor and orientation. */
  val effectiveFrameHeightDp: Int
    get() = state.effectiveFrameHeightDp

  /** Formatted `W × H dp` label for the current form factor and orientation. */
  val effectiveDimensionsLabel: String
    get() = state.effectiveDimensionsLabel

  /** Whether the simulated device is in airplane mode (offline). */
  val isAirplaneMode: Boolean
    get() = state.isAirplaneMode

  /**
   * Currently selected swappable example form ([WorkbenchExampleForm]), or `null` when custom XML
   * has been manually edited.
   */
  val selectedWorkbenchExampleForm: WorkbenchExampleForm?
    get() = state.selectedWorkbenchExampleForm

  /** Custom XForms `<h:html>` definition in the workbench's XForms editor. */
  val customXFormsXml: String
    get() = state.customXFormsXml

  /** Parse error of [customXFormsXml], or `null` when it is valid. */
  val xformsXmlError: String?
    get() = state.xformsXmlError

  /** Incremented when the sample data is reset, so views holding local copies reload them. */
  val dataResetCount: Int
    get() = state.dataResetCount
}
