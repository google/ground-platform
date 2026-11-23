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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.forms.DataType
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_quota_max_submissions_per_entity
import org.openforis.collectone.v2.app.testing.takeReceivedItems
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Multiplatform unit tests for [DataCollectionViewModel] and [DataCollectionUiState] within
 * `:shared:app:datacollection`.
 *
 * Kept free of PDF report generation so every test runs cleanly in both `jvmTest` and Android
 * `testDebugUnitTest` (PDF tests live in [DataCollectionPdfExportsTest] in `jvmTest`).
 */
class DataCollectionViewModelActionsTest {
  @Test
  fun savedStateHandle_restoresAndPersistsSelectedEntityAndSubmission() {
    val holder = sampleDataHolder()
    val savedState =
      SavedStateHandle(
        mapOf("selectedEntityId" to "entity-a", "selectedSubmissionId" to "sub-initial")
      )
    val vm =
      DataCollectionViewModel(
        surveyRepository = holder.surveyRepository,
        settingsRepository = holder.settingsRepository,
        authRepository = holder.authRepository,
        locationRepository = holder.locationRepository,
        completeFormSubmission = holder.completeFormSubmissionUseCase,
        launchForm = holder.launchFormUseCase,
        connectivityRepository = holder.connectivityRepository,
        savedState = savedState,
      )

    assertEquals("entity-a", vm.uiState.value.selectedEntityId)
    assertEquals("sub-initial", vm.uiState.value.selectedSubmissionId)
    assertTrue(vm.uiState.value.hasSelectedRecord)

    vm.onNavigationStarted("entity-b", "sub-next")
    assertEquals("entity-b", savedState["selectedEntityId"])
    assertEquals("sub-next", savedState["selectedSubmissionId"])

    vm.clearSelection()
    assertNull(savedState["selectedEntityId"])
    assertNull(savedState["selectedSubmissionId"])
    assertFalse(vm.uiState.value.hasSelectedRecord)
  }

  @Test
  fun recordSelection_emitsExpectedShellEventsForSubmissionsGeometriesAndList() = runTest {
    val f = DataCollectionFixture()
    val firstEntity = f.uiState.entities.first { it.submissions.isNotEmpty() }
    val secondEntity =
      f.uiState.entities.first { it.id != firstEntity.id && it.submissions.isNotEmpty() }
    val geom = f.uiState.submissionGeometries.first { it.entityId.isNotBlank() }

    f.viewModel.onEntitySelected(firstEntity.id)
    f.viewModel.events.test {
      // Selecting a submission on another entity frames that entity and switches the table.
      val otherSub = secondEntity.submissions.first()
      f.viewModel.selectSubmissionDetail(otherSub.id)
      assertEquals(secondEntity.id, f.uiState.selectedEntityId)
      assertEquals(otherSub.id, f.uiState.selectedSubmissionId)
      assertEquals(secondEntity, f.uiState.selectedEntity)
      assertEquals(otherSub, f.uiState.selectedSubmission)
      assertEquals(
        listOf(
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.SubmissionOpened(secondEntity.datasetId),
          DataCollectionEvent.EntityHighlighted(secondEntity.id, frameAgain = true),
        ),
        takeReceivedItems(),
      )

      // Selecting the same entity's submission again neither re-frames nor switches dataset.
      f.viewModel.selectSubmissionDetail(otherSub.id)
      assertEquals(
        listOf(
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.SubmissionOpened(null),
          DataCollectionEvent.EntityHighlighted(secondEntity.id, frameAgain = false),
        ),
        takeReceivedItems(),
      )

      // Returning to properties closes only the submission, keeping the feature selected.
      f.viewModel.showEntityProperties()
      assertEquals(secondEntity.id, f.uiState.selectedEntityId)
      assertNull(f.uiState.selectedSubmissionId)
      assertEquals(listOf(DataCollectionEvent.DetailsReturnedToProperties), takeReceivedItems())

      // Selecting a submission geometry highlights its feature and closes place/layer overlays.
      f.viewModel.selectSubmissionGeometry(geom.id)
      assertEquals(geom.entityId, f.uiState.selectedEntityId)
      assertEquals(geom.submissionId, f.uiState.selectedSubmissionId)
      assertEquals(
        listOf(
          DataCollectionEvent.PlaceSelectionCleared,
          DataCollectionEvent.EntityHighlighted(geom.entityId),
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.LayersSheetClosed,
        ),
        takeReceivedItems(),
      )

      // Unknown geometry IDs and null submission details emit nothing.
      f.viewModel.selectSubmissionGeometry("missing-geometry")
      f.viewModel.selectSubmissionDetail(null)
      assertNull(f.uiState.selectedSubmissionId)
      expectNoEvents()

      // Returning to the bottom-sheet list clears selection and emits the full list sequence.
      f.viewModel.returnToBottomSheetList()
      assertFalse(f.uiState.hasSelectedRecord)
      assertEquals(
        listOf(
          DataCollectionEvent.EntityHighlighted(null),
          DataCollectionEvent.DetailsReturnedToProperties,
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.ListShown,
          DataCollectionEvent.LayersSheetClosed,
        ),
        takeReceivedItems(),
      )
    }
  }

  @Test
  fun consentPrompt_gatesBothFabAndEntityLaunchesOncePerSurvey() {
    val f = DataCollectionFixture()
    // Calling accept without a pending consent launch is a no-op.
    f.viewModel.acceptCollectorConsent()
    assertFalse(f.uiState.isDataCollectionFormOpen)

    val surveyId = f.uiState.activeSurveyId
    runNow {
      f.surveyRepository.updateSurvey(surveyId) {
        it.copy(
          sharingPolicy = SharingPolicy.RESTRICTED,
          peerDataVisibility = PeerDataVisibility.OWN_ONLY,
          collectorConsentRequired = true,
          collectorConsentText = "Community data agreement.",
        )
      }
    }

    val form = f.uiState.forms.first { it.requiresEntity }
    val entity = f.uiState.entities.first { it.datasetId == form.targetDatasetId }

    // FAB launch pauses on consent; dismissing cancels without opening.
    f.viewModel.openAvailableFormsSheet()
    f.viewModel.launchFormFromFab(form.id, pickerMode = MainSurveyViewMode.LIST)
    assertFalse(f.uiState.isAvailableFormsSheetOpen)
    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertEquals("Community data agreement.", f.uiState.pendingCollectorConsent?.customConsentText)

    f.viewModel.dismissCollectorConsent()
    assertNull(f.uiState.pendingCollectorConsent)
    assertFalse(f.uiState.isDataCollectionFormOpen)

    // Entity launch pauses on consent; accepting opens for that entity and remembers the survey.
    f.viewModel.launchFormForEntity(entity.id, form.id)
    assertNotNull(f.uiState.pendingCollectorConsent)
    f.viewModel.acceptCollectorConsent()
    assertNull(f.uiState.pendingCollectorConsent)
    assertTrue(f.uiState.isDataCollectionFormOpen)
    assertEquals(entity.id, f.uiState.activeDataCollectionEntityId)
    assertEquals(entity, f.uiState.activeDataCollectionEntity)

    // Subsequent FAB launch in the same survey skips consent.
    f.viewModel.closeActiveFormRunner()
    f.viewModel.launchFormFromFab(form.id)
    assertNull(f.uiState.pendingCollectorConsent)
    assertTrue(f.uiState.isDataCollectionFormOpen)
  }

  @Test
  fun viewerAccess_blocksSheetFormLaunchesAndSubmission() {
    val f = DataCollectionFixture()
    f.authClient.signInAs(displayName = "Read Only", email = "viewer@example.org")
    val surveyId = f.uiState.activeSurveyId
    runNow {
      f.surveyRepository.updateSurvey(surveyId) {
        it.copy(
          ownerEmail = "organizer@example.org",
          sharingPolicy = SharingPolicy.PUBLIC,
          defaultRole = CollaboratorRole.VIEWER,
        )
      }
    }
    f.viewModel.onSurveyActivated()

    val form = f.uiState.forms.first()
    val entity = f.uiState.entities.first()
    val beforeSubmissions = f.uiState.allSubmissions.size
    assertFalse(f.uiState.canCollectData)

    f.viewModel.openAvailableFormsSheet()
    f.viewModel.launchFormFromFab(form.id)
    f.viewModel.launchFormForEntity(entity.id, form.id)
    f.viewModel.completeActiveFormSubmission()

    assertFalse(f.uiState.isAvailableFormsSheetOpen)
    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertEquals(beforeSubmissions, f.uiState.allSubmissions.size)
  }

  @Test
  fun submissionsAndQuota_recordEntityAndStandaloneFormsAndEnforcePerEntityCap() = runTest {
    val f = DataCollectionFixture()
    val activeId = f.uiState.activeSurveyId
    runNow {
      f.surveyRepository.updateSurvey(activeId) { survey ->
        survey.copy(quotaLimits = survey.quotaLimits.copy(maxSubmissionsPerEntityPerUser = 1))
      }
    }

    val entityForm = f.uiState.forms.first { it.requiresEntity }
    val target =
      f.uiState.entities.first {
        it.datasetId == entityForm.targetDatasetId && it.submissions.isEmpty()
      }
    f.viewModel.launchFormFromFab(entityForm.id)
    f.viewModel.selectEntityRefForActiveForm(target.id)
    val ctrl = assertNotNull(f.uiState.activeFormWizardController)
    f.viewModel.completeActiveFormSubmission(ctrl.formState.recordInstance)
    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertEquals(1, f.uiState.entities.first { it.id == target.id }.submissions.size)

    // Second submission for the same entity hits the per-entity cap and emits a quota notice.
    f.viewModel.launchFormForEntity(target.id, entityForm.id)
    f.viewModel.events.test {
      f.viewModel.completeActiveFormSubmission()
      val notice = takeReceivedItems().filterIsInstance<DataCollectionEvent.Notice>().single()
      assertEquals(
        Res.plurals.domain_quota_max_submissions_per_entity,
        assertIs<UiText.Plural>(notice.message).resource,
      )
    }
    assertEquals(1, f.uiState.entities.first { it.id == target.id }.submissions.size)
    f.viewModel.closeActiveFormRunner()

    // Standalone submission selects the newly created standalone record.
    val standaloneBefore = f.uiState.standaloneSubmissions.size
    val standaloneForm = f.uiState.forms.first { !it.requiresEntity }
    f.viewModel.launchFormFromFab(standaloneForm.id)
    f.viewModel.completeActiveFormSubmission()
    assertEquals(standaloneBefore + 1, f.uiState.standaloneSubmissions.size)
    assertNull(f.uiState.selectedEntityId)
    assertEquals(f.uiState.standaloneSubmissions.first().id, f.uiState.selectedSubmissionId)
  }

  @Test
  fun entityRefStepCustomFormDefWebDrawingAndQrCode_updateSessionAndComputedUiState() {
    val f = DataCollectionFixture()
    val entityForm = f.uiState.forms.first { it.requiresEntity }
    val target = f.uiState.entities.first { it.datasetId == entityForm.targetDatasetId }

    // Pre-selecting a compatible entity exposes entityRefTargetFromSelection on FAB launch.
    f.viewModel.onEntitySelected(target.id)
    f.viewModel.launchFormFromFab(entityForm.id)
    assertEquals(target.id, f.uiState.entityRefTargetFromSelection?.id)

    f.viewModel.updateEntityRefSelectorViewMode(MainSurveyViewMode.LIST)
    assertEquals(MainSurveyViewMode.LIST, f.uiState.entityRefSelectorViewMode)

    // Search matches label, GeoID, dataset name, and property values.
    f.viewModel.updateEntityRefSearchQuery(target.geoId)
    assertTrue(f.uiState.filteredEntityRefCandidates.any { it.id == target.id })
    val propValue = target.properties.values.firstOrNull { it.length >= 3 }
    if (propValue != null) {
      f.viewModel.updateEntityRefSearchQuery(propValue)
      assertTrue(f.uiState.filteredEntityRefCandidates.any { it.id == target.id })
    }
    f.viewModel.clearEntityRefSearchQuery()
    assertEquals(
      f.uiState.allDatasetEntitiesForForm(entityForm).size,
      f.uiState.filteredEntityRefCandidates.size,
    )

    f.viewModel.selectEntityRefForActiveForm(target.id)
    assertNull(f.uiState.entityRefTargetFromSelection)
    assertEquals(1L, f.uiState.entityRefFramingEpoch)

    // Custom FormDef relaunch keeps the picked feature; reset preserves customFormDef.
    val customDef =
      XFormsXmlSerializer.deserializeFormDef(WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml)
    f.viewModel.updateCustomFormDef(customDef, isPreset = true)
    assertEquals(customDef, f.uiState.customFormDef)
    assertEquals(target.id, f.uiState.activeDataCollectionEntityId)

    val ctrl = assertNotNull(f.uiState.activeFormWizardController)
    val geoPath =
      ctrl.formState.fieldStates.values
        .first { it.dataType == DataType.TYPE_GEOPOINT }
        .canonicalPath
    f.viewModel.webMapDrawing.startDrawing(geoPath, MapDrawingKind.POINT)
    assertTrue(f.uiState.isWebMapDrawing)
    f.viewModel.addWebMapDrawingVertex(LatLng(-0.41, 36.95))
    assertFalse(f.uiState.isWebMapDrawing)
    assertTrue(f.uiState.webFormGeometries.any { it.path == geoPath })

    f.viewModel.focusWebFormQuestion(geoPath)
    assertEquals(geoPath, f.uiState.webFormFocusRequest?.path)
    f.viewModel.consumeWebFormFocusRequest()
    assertNull(f.uiState.webFormFocusRequest)

    val bounds = LngLatBounds(36.0, -1.0, 37.0, 0.0)
    f.viewModel.requestWebMapFraming(bounds, 15.0)
    assertEquals(bounds, f.uiState.webMapFramingRequest?.bounds)

    f.viewModel.openEntityQrCode(target.id)
    assertEquals(target.id, f.uiState.activeQrCodeEntity?.id)
    f.viewModel.closeEntityQrCode()
    assertNull(f.uiState.activeQrCodeEntity)

    // Platform form filtering distinguishes mobile vs web entity-select requirement.
    val webOnlyNoSelect =
      FormPreviewItem(
        id = "web-no-select",
        title = "Web Only",
        description = "",
        version = "1",
        targetDatasetId = target.datasetId,
        targetDatasetName = target.datasetName,
        questionCount = 1,
        ctaLabel = "Go",
        availability = FormAvailability.WEB,
        hasEntitySelectQuestion = false,
      )
    val customState = f.uiState.copy(forms = listOf(entityForm, webOnlyNoSelect))
    assertTrue(customState.mobileForms.none { it.id == "web-no-select" })
    assertTrue(customState.webForms.any { it.id == "web-no-select" })
    assertTrue(customState.formsForEntity(target, onWeb = true).none { it.id == "web-no-select" })

    f.viewModel.onSurveyActivated()
    assertFalse(f.uiState.isDataCollectionFormOpen)
    f.viewModel.reset()
    assertEquals(customDef, f.uiState.customFormDef)
  }
}
