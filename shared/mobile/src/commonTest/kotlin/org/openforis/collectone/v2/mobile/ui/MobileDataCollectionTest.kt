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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** Data collection: launching forms, picking their map feature, and saving submissions. */
class MobileDataCollectionTest {
  private val state =
    MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
  private val dataCollection = state.viewModels.dataCollection

  private val uiState
    get() = state.viewModels.dataCollection.uiState.value

  private val controller
    get() = assertNotNull(uiState.activeFormWizardController)

  private fun entity(id: String) =
    state.viewModels.dataCollection.uiState.value.entities.first { it.id == id }

  /** Launches the EUDR baseline form from the FAB, which starts on its `entityref` step. */
  private fun launchEudrFormFromFab() =
    state.viewModels.dataCollection.launchFormFromFab("form-eudr-baseline")

  // --- Available forms ---

  @Test
  fun openAvailableFormsSheet_opensTheSheet() {
    dataCollection.openAvailableFormsSheet()

    assertTrue(uiState.isAvailableFormsSheetOpen)
  }

  @Test
  fun sampleSurvey_offersFiveEntityFormsAndOneStandaloneForm() {
    assertEquals(5, state.viewModels.dataCollection.uiState.value.forms.count { it.requiresEntity })
    assertEquals(
      1,
      state.viewModels.dataCollection.uiState.value.forms.count { !it.requiresEntity },
    )
  }

  // --- Launching from the FAB ---

  @Test
  fun launchFormFromFab_closesTheSheetAndOpensTheForm() {
    dataCollection.openAvailableFormsSheet()

    launchEudrFormFromFab()

    assertFalse(uiState.isAvailableFormsSheetOpen)
    assertTrue(uiState.isDataCollectionFormOpen)
    assertEquals("form-eudr-baseline", uiState.activeDataCollectionFormId)
  }

  @Test
  fun launchFormFromFab_startsWithoutAMapFeature() {
    launchEudrFormFromFab()

    assertTrue(uiState.wasFormLaunchedWithoutEntity)
    assertNull(uiState.activeDataCollectionEntityId)
  }

  @Test
  fun formLaunchedWithoutAMapFeature_startsOnTheEntityRefStep() {
    launchEudrFormFromFab()

    assertTrue(uiState.isCurrentFormStepEntityRef)
    assertEquals(0, controller.currentStepIndex)
  }

  @Test
  fun entityRefStep_blocksAdvancingUntilAMapFeatureIsPicked() {
    launchEudrFormFromFab()

    assertFalse(controller.nextStep())
    assertEquals(0, controller.currentStepIndex)
  }

  @Test
  fun entityRefPicker_opensOnTheMap() {
    launchEudrFormFromFab()

    assertEquals(MainSurveyViewMode.MAP, uiState.entityRefSelectorViewMode)
  }

  @Test
  fun entityRefPicker_listsTheFormsEligibleMapFeatures() {
    launchEudrFormFromFab()

    dataCollection.updateEntityRefSelectorViewMode(MainSurveyViewMode.LIST)

    assertEquals(MainSurveyViewMode.LIST, uiState.entityRefSelectorViewMode)
    assertEquals(
      listOf(NYR_104, NYR_108, NYR_112),
      uiState.eligibleEntitiesForActiveForm.map { it.id },
    )
    assertEquals(3, uiState.filteredEntityRefCandidates.size)
  }

  @Test
  fun entityRefSearch_filtersTheCandidates() {
    launchEudrFormFromFab()

    dataCollection.updateEntityRefSearchQuery("108")

    assertEquals(listOf(NYR_108), uiState.filteredEntityRefCandidates.map { it.id })
  }

  @Test
  fun clearEntityRefSearchQuery_listsEveryCandidateAgain() {
    launchEudrFormFromFab()
    dataCollection.updateEntityRefSearchQuery("108")

    dataCollection.clearEntityRefSearchQuery()

    assertEquals(3, uiState.filteredEntityRefCandidates.size)
  }

  // --- Picking the map feature ---

  @Test
  fun selectEntityRefForActiveForm_targetsAndSelectsThePickedFeature() {
    launchEudrFormFromFab()

    dataCollection.selectEntityRefForActiveForm(NYR_108)

    assertEquals(NYR_108, uiState.activeDataCollectionEntityId)
    assertEquals(NYR_108, state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun pickingAMapFeature_letsTheFormAdvanceToItsQuestions() {
    launchEudrFormFromFab()
    dataCollection.selectEntityRefForActiveForm(NYR_108)

    assertTrue(controller.nextStep())

    assertEquals(1, controller.currentStepIndex)
    assertFalse(uiState.isCurrentFormStepEntityRef)
  }

  @Test
  fun selectEntityRefForActiveForm_framesThePickedFeature() {
    launchEudrFormFromFab()
    val epochBefore = uiState.entityRefFramingEpoch

    dataCollection.selectEntityRefForActiveForm(NYR_108)

    val (lng, lat) = state.viewModels.surveyMap.resolveEntityLngLat(entity(NYR_108))
    val camera = state.viewModels.surveyMap.desiredMapCamera()
    assertEquals(epochBefore + 1, uiState.entityRefFramingEpoch)
    assertFalse(state.viewModels.surveyMap.uiState.value.isCameraFollowingUser)
    assertEquals(lat, camera.center.latitude, 1e-4)
    assertEquals(lng, camera.center.longitude, 1e-4)
  }

  @Test
  fun pickingTheSameFeatureAgain_framesItAgain() {
    launchEudrFormFromFab()
    dataCollection.selectEntityRefForActiveForm(NYR_108)
    val epochBefore = uiState.entityRefFramingEpoch

    dataCollection.selectEntityRefForActiveForm(NYR_108)

    assertEquals(epochBefore + 1, uiState.entityRefFramingEpoch)
  }

  @Test
  fun pickingAFeatureOfAnotherDataTable_isRejected() {
    launchEudrFormFromFab()
    val form = assertNotNull(uiState.activeDataCollectionForm)
    dataCollection.selectEntityRefForActiveForm(NYR_108)
    val epochBefore = uiState.entityRefFramingEpoch
    val cameraBefore = state.viewModels.surveyMap.desiredMapCamera().center
    val other =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.datasetId != form.targetDatasetId
      }

    dataCollection.selectEntityRefForActiveForm(other.id)

    assertEquals(epochBefore, uiState.entityRefFramingEpoch)
    assertEquals(NYR_108, uiState.activeDataCollectionEntityId)
    assertEquals(cameraBefore, state.viewModels.surveyMap.desiredMapCamera().center)
  }

  // --- Saving submissions ---

  @Test
  fun submittingAPickedFeaturesForm_attachesTheSubmissionToIt() {
    launchEudrFormFromFab()
    dataCollection.selectEntityRefForActiveForm(NYR_108)
    controller.nextStep()

    dataCollection.completeActiveFormSubmission(controller.formState.recordInstance)

    assertFalse(uiState.isDataCollectionFormOpen)
    assertFalse(uiState.wasFormLaunchedWithoutEntity)
    assertEquals(2, entity(NYR_108).submissions.size)
    assertEquals(NYR_108, entity(NYR_108).submissions.first().entityId)
  }

  @Test
  fun submittingTheSecondForm_completesTheMapFeature() {
    launchEudrFormFromFab()
    dataCollection.selectEntityRefForActiveForm(NYR_108)
    controller.nextStep()

    dataCollection.completeActiveFormSubmission(controller.formState.recordInstance)

    assertEquals("✓", entity(NYR_108).markerSymbol)
    assertEquals("Completed", entity(NYR_108).workflowStatus)
  }

  @Test
  fun newSubmission_isUploading() {
    launchEudrFormFromFab()
    dataCollection.selectEntityRefForActiveForm(NYR_108)
    controller.nextStep()

    dataCollection.completeActiveFormSubmission(controller.formState.recordInstance)

    assertEquals(SyncStatus.UPLOADING, entity(NYR_108).submissions.first().syncStatus)
    assertEquals(SyncStatus.UPLOADING, entity(NYR_108).syncStatus)
  }

  // --- save_to ---

  /** Opens the land-use survey and submits its point form, whose `save_to` creates a feature. */
  private fun submitLandUseObservation() {
    state.viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
    state.viewModels.dataCollection.launchFormFromFab("form-single-point-land-use")
    dataCollection.acceptCollectorConsent()
    controller.updateGeoPoint("/data/sample_point", latitude = -0.4190, longitude = 36.9500)
    controller.updateString("/data/land_use", "primary_forest")
    controller.updateString("/data/observer_note", "Newly surveyed forest patch")
    dataCollection.completeActiveFormSubmission()
  }

  @Test
  fun saveTo_createsAMapFeatureFromTheSubmission() {
    submitLandUseObservation()

    val createdEntity = state.viewModels.dataCollection.uiState.value.entities.first()
    assertEquals(4, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals("land_use_observations", createdEntity.datasetId)
    assertEquals("layer-land-use-observations", createdEntity.layerId)
    assertEquals("primary_forest", createdEntity.properties["land_use"])
    assertEquals("Newly surveyed forest patch", createdEntity.properties["observer_note"])
  }

  @Test
  fun saveTo_withoutAWorkflowStatus_defaultsNone() {
    submitLandUseObservation()

    assertFalse(state.viewModels.dataCollection.uiState.value.entities.first().hasWorkflowStatus)
  }

  @Test
  fun saveTo_attachesTheSubmissionToTheCreatedFeatureAndSelectsIt() {
    submitLandUseObservation()

    val createdEntity = state.viewModels.dataCollection.uiState.value.entities.first()
    assertEquals(createdEntity.id, createdEntity.submissions.single().entityId)
    assertEquals(createdEntity.id, state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun saveTo_recordsACreateEntityMutation() {
    submitLandUseObservation()

    val createdEntity = state.viewModels.dataCollection.uiState.value.entities.first()
    assertTrue(
      state.viewModels.dashboard.uiState.value.outboxMutations.any {
        it.operationKind == MutationOperationKind.CREATE_ENTITY && it.entityId == createdEntity.id
      }
    )
  }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val NYR_108 = "00000008-15d0-51f2-84eb-a72dfea5a747"
    const val NYR_112 = "00000009-1699-51bb-b7a3-f90a60bedd4d"
  }
}
