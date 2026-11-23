/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.workbench.ui

import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.state.MobileWorkbenchHooks
import org.openforis.collectone.v2.workbench.ui.state.WorkbenchEvent
import org.openforis.collectone.v2.workbench.ui.viewmodel.WorkbenchActions
import org.openforis.collectone.v2.workbench.ui.viewmodel.WorkbenchViewModel

/**
 * The workbench around the [mobile] app preview: its chrome's [viewModel] (the simulated device's
 * form factor, orientation, and airplane mode, the live XForms editor and example survey switcher,
 * and the simulation and reset tools), whose views observe [WorkbenchViewModel.uiState] directly.
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
        connectivityRepository = mobile.dataHolder.connectivityRepository,
      ),
  )

  private val dataCollection = mobile.viewModels.dataCollection

  /** Simulates uploads (when the sync engine is off) and follows survey activation in [mobile]. */
  private val mobileHooks =
    object : MobileWorkbenchHooks {
      override fun onSurveyActivated(surveyId: String) = viewModel.onSurveyActivated(surveyId)

      override fun cycleEntitySyncStatus(entityId: String) {
        if (!mobile.dataHolder.enableSyncEngine) viewModel.cycleEntitySyncStatus(entityId)
      }

      override fun cycleSubmissionSyncStatus(submissionId: String) {
        if (!mobile.dataHolder.enableSyncEngine) viewModel.cycleSubmissionSyncStatus(submissionId)
      }

      override fun retryFailedUploads() {
        if (mobile.dataHolder.enableSyncEngine) {
          mobile.viewModels.dashboard.syncAllOutboxMutations()
          return
        }
        val records = dataCollection.uiState.value
        records.entities
          .filter { it.syncStatus == SyncStatus.FAILED }
          .forEach { viewModel.cycleEntitySyncStatus(it.id) }
        records.allSubmissions
          .filter { it.syncStatus == SyncStatus.FAILED }
          .forEach { viewModel.cycleSubmissionSyncStatus(it.id) }
        val activeSurveyId = mobile.viewModels.dashboard.uiState.value.activeSurveyId
        val mutationRepository = mobile.dataHolder.mutationRepository
        mobile.dataHolder.launch {
          val current = mutationRepository.getMutations()
          if (
            current.any { it.surveyId == activeSurveyId && it.state == MutationSyncState.FAILED }
          ) {
            mutationRepository.setMutations(
              current.map { item ->
                if (item.surveyId == activeSurveyId && item.state == MutationSyncState.FAILED) {
                  item.copy(state = MutationSyncState.UPLOADING, stateDetail = "Retrying upload")
                } else {
                  item
                }
              }
            )
          }
        }
      }
    }

  init {
    mobile.dataHolder.scope.launch { viewModel.events.collect(::onWorkbenchEvent) }
    mobile.workbenchHooks = mobileHooks
    dataCollection.updateCustomFormDef(
      DEFAULT_WORKBENCH_FORM_DEF,
      isPreset = true,
    )
  }

  /** Applies a workbench outcome to the driven sessions. */
  private fun onWorkbenchEvent(event: WorkbenchEvent) {
    val onboarding = mobile.viewModels.onboarding
    when (event) {
      is WorkbenchEvent.CustomFormDefChanged ->
        dataCollection.updateCustomFormDef(
          formDef = event.formDef,
          isPreset = event.isPreset,
          relaunchOpenForm = event.relaunchOpenForm,
        )
      is WorkbenchEvent.ExampleSurveySelected -> {
        onboarding.openSurvey(event.surveyId)
        if (event.launchImmediately) {
          dataCollection.launchActiveOrDefaultFormForTesting()
        }
      }
      is WorkbenchEvent.DefaultSurveyRestored -> onboarding.openSurvey(event.surveyId)
      WorkbenchEvent.SampleDataReset -> mobile.reset()
      is WorkbenchEvent.ConnectivityChanged -> mobile.onConnectivityChanged(event.isOnline)
      is WorkbenchEvent.Notice -> mobile.notices.show(event.message)
    }
  }
}
