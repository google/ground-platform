/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.DEFAULT_PROTOTYPE_XFORMS_XML
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SampleDataRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GeneratePrototypeRandomSitesUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.WorkbenchEvent

/** See [OnboardingViewModelTest] for why fixtures are built outside `runNow`. */
class WorkbenchViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val surveyRepository = SurveyRepositoryImpl(store)
    val sampleDataRepository = SampleDataRepositoryImpl(store)
    val generateRandomSitesUseCase = GeneratePrototypeRandomSitesUseCase(surveyRepository)
    val viewModel =
      WorkbenchViewModel(
        surveyRepository = surveyRepository,
        sampleDataRepository = sampleDataRepository,
        generateRandomSitesUseCase = generateRandomSitesUseCase,
        scope = scope,
      )
    val events = mutableListOf<WorkbenchEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
    }

    val uiState
      get() = viewModel.uiState.value
  }

  @Test
  fun deviceFormFactorAndOrientation_rotateAndToggleCleanly() {
    val f = Fixture()
    assertEquals(DeviceFormFactor.MOBILE, f.uiState.deviceFormFactor)
    assertEquals(DeviceOrientation.PORTRAIT, f.uiState.deviceOrientation)
    assertFalse(f.uiState.isDeviceRotated)

    f.viewModel.rotateDevice()
    assertEquals(DeviceOrientation.LANDSCAPE, f.uiState.deviceOrientation)
    assertTrue(f.uiState.isDeviceRotated)

    f.viewModel.toggleDeviceFormFactor()
    assertEquals(DeviceFormFactor.TABLET, f.uiState.deviceFormFactor)
    assertEquals(DeviceFormFactor.TABLET.defaultOrientation, f.uiState.deviceOrientation)
    assertFalse(f.uiState.isDeviceRotated)

    f.viewModel.selectDeviceOrientation(DeviceOrientation.PORTRAIT)
    assertEquals(DeviceOrientation.PORTRAIT, f.uiState.deviceOrientation)
  }

  @Test
  fun updateCustomXFormsXml_validBlankAndInvalid_updatesStateAndEmitsEvent() {
    val f = Fixture()
    assertNull(f.uiState.xformsXmlError)
    assertNotNull(f.uiState.customFormDef)

    // Invalid XML sets xformsXmlError and emits CustomFormDefChanged(formDef=null,
    // relaunchOpenForm=false)
    f.viewModel.updateCustomXFormsXml("<not-valid-xforms")
    assertNotNull(f.uiState.xformsXmlError)
    assertNull(f.uiState.customFormDef)
    val invalidEvent = f.events.last() as WorkbenchEvent.CustomFormDefChanged
    assertNull(invalidEvent.formDef)
    assertFalse(invalidEvent.relaunchOpenForm)

    // Blank XML clears error and formDef
    f.viewModel.updateCustomXFormsXml("   ")
    assertNull(f.uiState.xformsXmlError)
    assertNull(f.uiState.customFormDef)
    val blankEvent = f.events.last() as WorkbenchEvent.CustomFormDefChanged
    assertNull(blankEvent.formDef)
    assertTrue(blankEvent.relaunchOpenForm)

    // Valid preset XML restores formDef and matches preset
    f.viewModel.updateCustomXFormsXml(WorkbenchExampleForm.SINGLE_POINT_LAND_USE.xformsXml)
    assertNull(f.uiState.xformsXmlError)
    assertNotNull(f.uiState.customFormDef)
    assertEquals(
      WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      f.uiState.selectedWorkbenchExampleForm,
    )
    val validEvent = f.events.last() as WorkbenchEvent.CustomFormDefChanged
    assertNotNull(validEvent.formDef)
    assertTrue(validEvent.isPreset)
  }

  @Test
  fun selectWorkbenchExampleForm_andResetDefault_emitExpectedEvents() {
    val f = Fixture()
    f.viewModel.selectWorkbenchExampleForm(
      WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT,
      launchImmediately = true,
    )
    assertEquals(
      WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT,
      f.uiState.selectedWorkbenchExampleForm,
    )
    assertTrue(
      f.events.contains(
        WorkbenchEvent.ExampleSurveySelected(
          surveyId = "survey-sample-plots-forest",
          launchImmediately = true,
        )
      )
    )

    // Switch active survey in repository, then reset default XForms XML
    runNow { f.surveyRepository.setActiveSurveyId("survey-sample-plots-forest") }
    f.viewModel.onSurveyActivated("survey-sample-plots-forest")
    assertEquals(
      WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT,
      f.uiState.selectedWorkbenchExampleForm,
    )

    f.viewModel.resetDefaultXFormsXml()
    assertTrue(f.events.contains(WorkbenchEvent.DefaultSurveyRestored("survey-kenya-coffee")))
    assertEquals(WorkbenchExampleForm.ALL_FIELD_TYPES, f.uiState.selectedWorkbenchExampleForm)
    assertEquals(DEFAULT_PROTOTYPE_XFORMS_XML, f.uiState.customXFormsXml)
  }

  @Test
  fun addRandomSites_andSyncStatusCycling_updateRepositoryAndEmitNotices() {
    val f = Fixture()
    val beforeCount = f.uiState.entities.size
    f.viewModel.addRandomSites(count = 15)
    assertEquals(beforeCount + 15, f.uiState.entities.size)
    val randomEvent = f.events.last() as WorkbenchEvent.RandomSitesAdded
    assertEquals(15, randomEvent.count)
    assertEquals(beforeCount + 15, randomEvent.totalCount)

    // Cycle entity sync status
    val firstEntity = f.uiState.entities.first()
    val expectedNext = firstEntity.syncStatus.next()
    f.viewModel.cycleEntitySyncStatus(firstEntity.id)
    assertEquals(expectedNext, f.uiState.entities.first { it.id == firstEntity.id }.syncStatus)

    // Cycle submission sync status
    val firstSubmission = f.uiState.allSubmissions.first()
    val expectedSubNext = firstSubmission.syncStatus.next()
    f.viewModel.cycleSubmissionSyncStatus(firstSubmission.id)
    assertEquals(
      expectedSubNext,
      f.uiState.allSubmissions.first { it.id == firstSubmission.id }.syncStatus,
    )

    // Marking entity SYNCED marks all its submissions SYNCED
    f.viewModel.updateEntitySyncStatus(firstEntity.id, SyncStatus.SYNCED)
    val syncedEntity = f.uiState.entities.first { it.id == firstEntity.id }
    assertEquals(SyncStatus.SYNCED, syncedEntity.syncStatus)
    assertTrue(syncedEntity.submissions.all { it.syncStatus == SyncStatus.SYNCED })
  }

  @Test
  fun toggleSurveyDownloaded_andResetPrototypeFlow_restoreSampleData() {
    val f = Fixture()
    val initialEntityCount = f.uiState.entities.size
    val targetSurvey = f.uiState.surveys.first()
    val initialDownloaded = targetSurvey.isDownloaded

    f.viewModel.toggleSurveyDownloaded(targetSurvey.id)
    assertEquals(
      !initialDownloaded,
      f.uiState.surveys.first { it.id == targetSurvey.id }.isDownloaded,
    )

    f.viewModel.addRandomSites(count = 10)
    assertEquals(initialEntityCount + 10, f.uiState.entities.size)

    val beforeResetCount = f.uiState.dataResetCount
    f.viewModel.resetPrototypeFlow()
    assertEquals(beforeResetCount + 1, f.uiState.dataResetCount)
    assertEquals(initialEntityCount, f.uiState.entities.size)
    assertTrue(f.events.contains(WorkbenchEvent.PrototypeReset))
  }
}
