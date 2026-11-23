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
package org.openforis.collectone.v2.app.ui.datacollection

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.forms.DataType
import org.openforis.collectone.v2.app.client.pdf.PdfExportResult
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_pdf_saved
import org.openforis.collectone.v2.app.resources.app_pdf_saved_with_summary
import org.openforis.collectone.v2.app.resources.app_pdf_share_failed
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_map_feature
import org.openforis.collectone.v2.app.resources.domain_notice_entity_submitted
import org.openforis.collectone.v2.app.resources.domain_quota_max_submissions_per_entity
import org.openforis.collectone.v2.app.testing.takeReceivedItems
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm

/**
 * Fixtures are built outside `runNow` on purpose: the view model's `Dispatchers.Unconfined` scope
 * only runs nested coroutines after the current one yields, so a fixture built inside an Unconfined
 * block would not see its own state updates until the block ends.
 */
class DataCollectionViewModelTest {
  @Test
  fun initialState_hasNoSelectionAndNoOpenForm_withSeededContent() {
    val f = DataCollectionFixture()
    assertNull(f.uiState.selectedEntityId)
    assertNull(f.uiState.selectedSubmissionId)
    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertFalse(f.uiState.hasSelectedRecord)
    assertTrue(f.uiState.forms.isNotEmpty())
    assertTrue(f.uiState.entities.isNotEmpty())
    assertTrue(f.uiState.mobileForms.all { it.availability.includesMobile })
    assertTrue(f.uiState.webForms.all { it.availability.includesWeb })
    assertEquals(
      f.uiState.entities.sumOf { it.submissions.size } + f.uiState.standaloneSubmissions.size,
      f.uiState.allSubmissions.size,
    )
    assertTrue(f.uiState.canSharePdfFiles)
  }

  @Test
  fun launchFormFromFab_opensEntityRefStepForEntityForms() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.events.test {
      f.viewModel.openAvailableFormsSheet()
      assertTrue(f.uiState.isAvailableFormsSheetOpen)
      assertTrue(takeReceivedItems().contains(DataCollectionEvent.FormsSheetOpened))

      f.viewModel.launchFormFromFab("form-eudr-baseline", pickerMode = MainSurveyViewMode.LIST)

      assertFalse(f.uiState.isAvailableFormsSheetOpen)
      assertTrue(f.uiState.isDataCollectionFormOpen)
      assertTrue(f.uiState.wasFormLaunchedWithoutEntity)
      assertNull(f.uiState.activeDataCollectionEntityId)
      assertEquals("form-eudr-baseline", f.uiState.activeDataCollectionFormId)
      assertEquals(MainSurveyViewMode.LIST, f.uiState.entityRefSelectorViewMode)
      assertTrue(f.uiState.isCurrentFormStepEntityRef)
      assertEquals(
        listOf(
          "00000007-640e-5ed7-9874-4d6b6717f21d",
          "00000008-15d0-51f2-84eb-a72dfea5a747",
          "00000009-1699-51bb-b7a3-f90a60bedd4d",
        ),
        f.uiState.eligibleEntitiesForActiveForm.map { it.id },
      )
      val events = takeReceivedItems()
      assertTrue(events.contains(DataCollectionEvent.FormOpened))
      assertTrue(events.contains(DataCollectionEvent.LayersSheetClosed))
    }
  }

  @Test
  fun entityRefSearch_filtersCandidates_andClearResets() {
    val f = DataCollectionFixture()
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    assertEquals(3, f.uiState.filteredEntityRefCandidates.size)

    f.viewModel.updateEntityRefSearchQuery("108")
    assertEquals(
      listOf("00000008-15d0-51f2-84eb-a72dfea5a747"),
      f.uiState.filteredEntityRefCandidates.map { it.id },
    )

    f.viewModel.clearEntityRefSearchQuery()
    assertEquals("", f.uiState.entityRefSearchQuery)
    assertEquals(3, f.uiState.filteredEntityRefCandidates.size)
  }

  @Test
  fun selectEntityRefForActiveForm_fillsForm_selectsAndFramesFeature() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    val controller = assertNotNull(f.uiState.activeFormWizardController)
    assertFalse(controller.nextStep())
    val epochBefore = f.uiState.entityRefFramingEpoch
    f.viewModel.events.test {
      f.viewModel.selectEntityRefForActiveForm("00000008-15d0-51f2-84eb-a72dfea5a747")

      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.activeDataCollectionEntityId)
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.selectedEntityId)
      assertEquals(epochBefore + 1, f.uiState.entityRefFramingEpoch)
      assertEquals(
        "00000008-15d0-51f2-84eb-a72dfea5a747",
        controller.stringAt(LaunchFormUseCase.ENTITY_REF_FIELD_PATH),
      )
      val events = takeReceivedItems()
      assertEquals(
        DataCollectionEvent.EntityHighlighted("00000008-15d0-51f2-84eb-a72dfea5a747"),
        events[0],
      )
      val recentered = assertIs<DataCollectionEvent.MapRecenteredOnEntity>(events[1])
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", recentered.entity.id)
      assertTrue(controller.nextStep())
      assertFalse(f.uiState.isCurrentFormStepEntityRef)

      // A map feature from another data table is rejected.
      val form = assertNotNull(f.uiState.activeDataCollectionForm)
      val other = f.uiState.entities.first { it.datasetId != form.targetDatasetId }
      takeReceivedItems() // Drops the events so far, to assert on the next action alone.
      f.viewModel.selectEntityRefForActiveForm(other.id)
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.activeDataCollectionEntityId)
      assertEquals(epochBefore + 1, f.uiState.entityRefFramingEpoch)
      expectNoEvents()
    }
  }

  @Test
  fun entityRefTargetFromSelection_isTheFeatureSelectedBeforeTheFormOpened() {
    val f = DataCollectionFixture()
    f.viewModel.onEntitySelected("00000008-15d0-51f2-84eb-a72dfea5a747")

    f.viewModel.launchFormFromFab("form-eudr-baseline")

    assertNull(f.uiState.activeDataCollectionEntityId)
    val target = assertNotNull(f.uiState.entityRefTargetFromSelection)
    assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", target.id)

    f.viewModel.selectEntityRefForActiveForm(target.id)

    assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.activeDataCollectionEntityId)
    assertNull(f.uiState.entityRefTargetFromSelection)
  }

  @Test
  fun entityRefTargetFromSelection_followsASelectionMadeAfterTheFormOpened() {
    val f = DataCollectionFixture()
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    assertNull(f.uiState.entityRefTargetFromSelection)

    f.viewModel.onEntitySelected("00000009-1699-51bb-b7a3-f90a60bedd4d")

    assertEquals(
      "00000009-1699-51bb-b7a3-f90a60bedd4d",
      f.uiState.entityRefTargetFromSelection?.id,
    )
  }

  @Test
  fun entityRefTargetFromSelection_ignoresFeaturesOfOtherDataTables() {
    val f = DataCollectionFixture()
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    val form = assertNotNull(f.uiState.activeDataCollectionForm)
    val other = f.uiState.entities.first { it.datasetId != form.targetDatasetId }

    f.viewModel.onEntitySelected(other.id)

    assertNull(f.uiState.entityRefTargetFromSelection)
  }

  @Test
  fun launchFormForEntity_prepopulatesReference_selectsEntity_andOpensForm() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.events.test {
      f.viewModel.launchFormForEntity("00000009-1699-51bb-b7a3-f90a60bedd4d", "form-eudr-baseline")

      assertTrue(f.uiState.isDataCollectionFormOpen)
      assertFalse(f.uiState.wasFormLaunchedWithoutEntity)
      assertEquals("00000009-1699-51bb-b7a3-f90a60bedd4d", f.uiState.activeDataCollectionEntityId)
      assertEquals("00000009-1699-51bb-b7a3-f90a60bedd4d", f.uiState.selectedEntityId)
      assertEquals("form-eudr-baseline", f.uiState.activeDataCollectionFormId)
      assertFalse(f.uiState.isCurrentFormStepEntityRef)
      val controller = assertNotNull(f.uiState.activeFormWizardController)
      if (LaunchFormUseCase.ENTITY_REF_FIELD_PATH in controller.formState.fieldStates) {
        assertEquals(
          "00000009-1699-51bb-b7a3-f90a60bedd4d",
          controller.stringAt(LaunchFormUseCase.ENTITY_REF_FIELD_PATH),
        )
      }
      assertEquals(
        listOf(
          DataCollectionEvent.EntityHighlighted("00000009-1699-51bb-b7a3-f90a60bedd4d"),
          DataCollectionEvent.LayersSheetClosed,
          DataCollectionEvent.FormOpened,
        ),
        takeReceivedItems(),
      )
    }
  }

  @Test
  fun formsForEntity_onWebOnlyReturnsFormsWithEntitySelectQuestion() {
    val f = DataCollectionFixture()
    val entity = f.uiState.entities.first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
    val withEntitySelect =
      FormPreviewItem(
        id = "form-with-select",
        title = "Form With Select",
        description = "",
        version = "1",
        targetDatasetId = entity.datasetId,
        targetDatasetName = entity.datasetName,
        questionCount = 3,
        ctaLabel = "Collect",
        availability = org.openforis.collectone.v2.app.domain.model.FormAvailability.BOTH,
        hasEntitySelectQuestion = true,
      )
    val withoutEntitySelect =
      FormPreviewItem(
        id = "form-without-select",
        title = "Form Without Select",
        description = "",
        version = "1",
        targetDatasetId = entity.datasetId,
        targetDatasetName = entity.datasetName,
        questionCount = 3,
        ctaLabel = "Collect",
        availability = org.openforis.collectone.v2.app.domain.model.FormAvailability.BOTH,
        hasEntitySelectQuestion = false,
      )
    val state = f.uiState.copy(forms = listOf(withEntitySelect, withoutEntitySelect))

    val webForms = state.formsForEntity(entity, onWeb = true)
    assertEquals(listOf(withEntitySelect), webForms)

    val mobileForms = state.formsForEntity(entity, onWeb = false)
    assertEquals(listOf(withEntitySelect, withoutEntitySelect), mobileForms)
  }

  @Test
  fun launchFormForEntity_ignoresFormsOfAnotherDataset() = runTest {
    val f = DataCollectionFixture()
    val entity = f.uiState.entities.first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
    val foreignForm =
      f.uiState.forms.first { it.requiresEntity && it.targetDatasetId != entity.datasetId }
    f.viewModel.events.test {
      f.viewModel.launchFormForEntity(entity.id, foreignForm.id)
      assertFalse(f.uiState.isDataCollectionFormOpen)
      expectNoEvents()
    }
  }

  @Test
  fun closeActiveFormRunner_clearsFormState_butKeepsSelection() {
    val f = DataCollectionFixture()
    f.viewModel.launchFormForEntity("00000009-1699-51bb-b7a3-f90a60bedd4d", "form-eudr-baseline")
    f.viewModel.closeActiveFormRunner()
    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertNull(f.uiState.activeDataCollectionEntityId)
    assertNull(f.uiState.activeDataCollectionFormId)
    assertFalse(f.uiState.wasFormLaunchedWithoutEntity)
    assertEquals("00000009-1699-51bb-b7a3-f90a60bedd4d", f.uiState.selectedEntityId)
  }

  @Test
  fun completeActiveFormSubmission_recordsSubmission_closesForm_andNotifies() = runTest {
    val f = DataCollectionFixture()
    val before =
      f.uiState.entities.first { it.id == "00000008-15d0-51f2-84eb-a72dfea5a747" }.submissions.size
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    f.viewModel.selectEntityRefForActiveForm("00000008-15d0-51f2-84eb-a72dfea5a747")
    val controller = assertNotNull(f.uiState.activeFormWizardController)
    f.viewModel.events.test {
      f.viewModel.completeActiveFormSubmission(controller.formState.recordInstance)

      assertFalse(f.uiState.isDataCollectionFormOpen)
      assertFalse(f.uiState.wasFormLaunchedWithoutEntity)
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.selectedEntityId)
      val after = f.uiState.entities.first { it.id == "00000008-15d0-51f2-84eb-a72dfea5a747" }
      assertEquals(before + 1, after.submissions.size)
      assertEquals(SyncStatus.UPLOADING, after.submissions.first().syncStatus)
      val events = takeReceivedItems()
      assertEquals(
        DataCollectionEvent.EntityHighlighted("00000008-15d0-51f2-84eb-a72dfea5a747"),
        events[0],
      )
      val notice = assertIs<DataCollectionEvent.Notice>(events[1])
      assertEquals(
        Res.string.domain_notice_entity_submitted,
        assertIs<UiText.Resource>(notice.message).resource,
      )
    }
  }

  @Test
  fun completeActiveFormSubmission_recordsTheDeviceClockAsClientUpdatedAt() = runTest {
    val f = DataCollectionFixture()
    f.submissionClock.current = Instant.parse("2026-10-01T09:30:00Z")
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    f.viewModel.selectEntityRefForActiveForm("00000008-15d0-51f2-84eb-a72dfea5a747")
    val controller = assertNotNull(f.uiState.activeFormWizardController)

    f.viewModel.completeActiveFormSubmission(controller.formState.recordInstance)

    val saved =
      f.uiState.entities
        .first { it.id == "00000008-15d0-51f2-84eb-a72dfea5a747" }
        .submissions
        .first()
    assertEquals(Instant.parse("2026-10-01T09:30:00Z"), saved.clientUpdatedAt)
    assertNull(saved.serverUpdatedAt)
    assertEquals("2026-10-01 09:30 UTC", saved.updatedAtLabel)
  }

  @Test
  fun completeActiveFormSubmission_whenQuotaExceeded_emitsNoticeAndDoesNotSave() = runTest {
    val f = DataCollectionFixture()
    val activeId = runNow { f.surveyRepository.getActiveSurveyId() }
    runNow {
      f.surveyRepository.updateSurvey(activeId) { survey ->
        survey.copy(quotaLimits = survey.quotaLimits.copy(maxSubmissionsPerEntityPerUser = 1))
      }
    }
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    f.viewModel.selectEntityRefForActiveForm("00000008-15d0-51f2-84eb-a72dfea5a747")
    val controller = assertNotNull(f.uiState.activeFormWizardController)
    f.viewModel.completeActiveFormSubmission(controller.formState.recordInstance)
    assertFalse(f.uiState.isDataCollectionFormOpen)

    f.viewModel.launchFormFromFab("form-eudr-baseline")
    f.viewModel.selectEntityRefForActiveForm("00000008-15d0-51f2-84eb-a72dfea5a747")
    val controller2 = assertNotNull(f.uiState.activeFormWizardController)
    f.viewModel.events.test {
      f.viewModel.completeActiveFormSubmission(controller2.formState.recordInstance)
      val notice = takeReceivedItems().filterIsInstance<DataCollectionEvent.Notice>().firstOrNull()
      assertNotNull(notice)
      assertEquals(
        Res.plurals.domain_quota_max_submissions_per_entity,
        assertIs<UiText.Plural>(notice.message).resource,
      )
    }
  }

  @Test
  fun completeActiveFormSubmission_newFeatureHasOnlyItsSaveToProperties() {
    val f = DataCollectionFixture()
    runNow { f.surveyRepository.setActiveSurveyId("00000036-1a1c-5c51-997f-28b67d813aa3") }
    f.viewModel.onSurveyActivated()
    val before = f.uiState.entities.map { it.id }.toSet()
    f.viewModel.launchFormFromFab("form-single-point-land-use")
    f.viewModel.acceptCollectorConsent()
    val controller = assertNotNull(f.uiState.activeFormWizardController)
    controller.updateGeoPoint("/data/sample_point", -1.292066, 36.821946, accuracyMeters = 4.0)
    controller.updateString("/data/land_use", "primary_forest")
    controller.updateString("/data/observer_note", "Closed canopy.")

    f.viewModel.completeActiveFormSubmission(controller.formState.recordInstance)

    val created = f.uiState.entities.single { it.id !in before }
    // `sample_point` is saved to `geometry`, so it isn't stored again under its question name.
    assertEquals(setOf("geometry", "land_use", "observer_note"), created.properties.keys)
    assertEquals("primary_forest", created.properties["land_use"])
  }

  @Test
  fun completeStandaloneSubmission_selectsTheNewSubmission() {
    val f = DataCollectionFixture()
    val before = f.uiState.standaloneSubmissions.size
    f.viewModel.launchFormFromFab("form-pest-disease-sighting")
    assertTrue(f.uiState.isDataCollectionFormOpen)
    assertFalse(f.uiState.isCurrentFormStepEntityRef)

    f.viewModel.completeActiveFormSubmission()

    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertEquals(before + 1, f.uiState.standaloneSubmissions.size)
    assertNull(f.uiState.selectedEntityId)
    assertEquals(f.uiState.standaloneSubmissions.first().id, f.uiState.selectedSubmissionId)
    assertNotNull(f.uiState.selectedSubmission)
  }

  @Test
  fun selectSubmissionDetail_ofAnotherFeature_framesIt_andSwitchesTable() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.onEntitySelected("00000007-640e-5ed7-9874-4d6b6717f21d")
    val target = f.uiState.entities.first { it.id == "00000008-15d0-51f2-84eb-a72dfea5a747" }
    val submission = target.submissions.first()
    f.viewModel.events.test {
      f.viewModel.selectSubmissionDetail(submission.id)

      assertEquals(submission.id, f.uiState.selectedSubmissionId)
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.selectedEntityId)
      assertEquals(submission, f.uiState.selectedSubmission)
      assertTrue(f.uiState.hasSelectedRecord)
      assertEquals(
        listOf(
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.SubmissionOpened(target.datasetId),
          DataCollectionEvent.EntityHighlighted(
            "00000008-15d0-51f2-84eb-a72dfea5a747",
            frameAgain = true,
          ),
        ),
        takeReceivedItems(),
      )

      // Opening a submission of the already selected feature neither frames nor switches the table.
      f.viewModel.selectSubmissionDetail(submission.id)
      assertEquals(
        listOf(
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.SubmissionOpened(null),
          DataCollectionEvent.EntityHighlighted(
            "00000008-15d0-51f2-84eb-a72dfea5a747",
            frameAgain = false,
          ),
        ),
        takeReceivedItems(),
      )

      f.viewModel.selectSubmissionDetail(null)
      assertNull(f.uiState.selectedSubmissionId)
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.selectedEntityId)
      expectNoEvents()
    }
  }

  @Test
  fun selectSubmissionGeometry_selectsItsSubmissionAndFeature() = runTest {
    val f = DataCollectionFixture()
    val geom = f.uiState.submissionGeometries.first { it.entityId.isNotBlank() }
    f.viewModel.events.test {
      f.viewModel.selectSubmissionGeometry(geom.id)
      assertEquals(geom.submissionId, f.uiState.selectedSubmissionId)
      assertEquals(geom.entityId, f.uiState.selectedEntityId)
      assertEquals(
        listOf(
          DataCollectionEvent.PlaceSelectionCleared,
          DataCollectionEvent.EntityHighlighted(geom.entityId),
          DataCollectionEvent.EntityBottomSheetExpanded(true),
          DataCollectionEvent.LayersSheetClosed,
        ),
        takeReceivedItems(),
      )
    }
  }

  @Test
  fun returnToBottomSheetList_clearsSelection_andShowsList() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.onEntitySelected("00000007-640e-5ed7-9874-4d6b6717f21d")
    f.viewModel.selectSubmissionDetail(
      f.uiState.entities
        .first { it.id == "00000007-640e-5ed7-9874-4d6b6717f21d" }
        .submissions
        .first()
        .id
    )
    f.viewModel.events.test {
      f.viewModel.returnToBottomSheetList()

      assertNull(f.uiState.selectedEntityId)
      assertNull(f.uiState.selectedSubmissionId)
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
  fun showEntityProperties_closesSubmission_butKeepsFeature() = runTest {
    val f = DataCollectionFixture()
    val entity = f.uiState.entities.first { it.id == "00000007-640e-5ed7-9874-4d6b6717f21d" }
    f.viewModel.selectSubmissionDetail(entity.submissions.first().id)
    f.viewModel.events.test {
      f.viewModel.showEntityProperties()
      assertNull(f.uiState.selectedSubmissionId)
      assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", f.uiState.selectedEntityId)
      assertEquals(DataCollectionEvent.DetailsReturnedToProperties, awaitItem())
    }
  }

  @Test
  fun mapSelections_flowIn_withoutEvents() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.selectSubmissionDetail(
      f.uiState.entities
        .first { it.id == "00000007-640e-5ed7-9874-4d6b6717f21d" }
        .submissions
        .first()
        .id
    )
    f.viewModel.events.test {
      f.viewModel.onEntitySelected("00000009-1699-51bb-b7a3-f90a60bedd4d")
      assertEquals("00000009-1699-51bb-b7a3-f90a60bedd4d", f.uiState.selectedEntityId)
      assertNull(f.uiState.selectedSubmissionId)

      f.viewModel.onNavigationStarted("00000008-15d0-51f2-84eb-a72dfea5a747", "sub-x")
      assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.selectedEntityId)
      assertEquals("sub-x", f.uiState.selectedSubmissionId)

      f.viewModel.onEntitySelected(null)
      assertFalse(f.uiState.hasSelectedRecord)
      expectNoEvents()
    }
  }

  @Test
  fun onSurveyActivated_dropsSelectionFormAndSheet() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.launchFormForEntity("00000009-1699-51bb-b7a3-f90a60bedd4d", "form-eudr-baseline")
    f.viewModel.openAvailableFormsSheet()
    f.viewModel.events.test {
      f.viewModel.onSurveyActivated()

      assertNull(f.uiState.selectedEntityId)
      assertNull(f.uiState.selectedSubmissionId)
      assertFalse(f.uiState.isDataCollectionFormOpen)
      assertFalse(f.uiState.isAvailableFormsSheetOpen)
      assertEquals(
        listOf(
          DataCollectionEvent.EntityHighlighted(null),
          DataCollectionEvent.EntityBottomSheetExpanded(false),
        ),
        takeReceivedItems(),
      )
    }
  }

  @Test
  fun reset_keepsCustomFormDef() {
    val f = DataCollectionFixture()
    val formDef =
      XFormsXmlSerializer.deserializeFormDef(WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml)
    f.viewModel.updateCustomFormDef(formDef, isPreset = true)
    f.viewModel.launchFormForEntity("00000009-1699-51bb-b7a3-f90a60bedd4d", "form-eudr-baseline")
    f.viewModel.openEntityQrCode("00000009-1699-51bb-b7a3-f90a60bedd4d")
    f.viewModel.reset()
    assertFalse(f.uiState.isDataCollectionFormOpen)
    assertNull(f.uiState.activeQrCodeEntityId)
    assertNull(f.uiState.selectedEntityId)
    assertEquals(formDef, f.uiState.customFormDef)
  }

  @Test
  fun updateCustomFormDef_relaunchesOpenForm_keepingPickedFeature() {
    val f = DataCollectionFixture()
    f.viewModel.launchFormFromFab("form-eudr-baseline")
    f.viewModel.selectEntityRefForActiveForm("00000008-15d0-51f2-84eb-a72dfea5a747")
    val before = assertNotNull(f.uiState.activeFormWizardController)

    f.viewModel.updateCustomFormDef(null, isPreset = false)

    val after = assertNotNull(f.uiState.activeFormWizardController)
    assertTrue(before !== after)
    assertNull(f.uiState.customFormDef)
    assertEquals("00000008-15d0-51f2-84eb-a72dfea5a747", f.uiState.activeDataCollectionEntityId)
    assertTrue(f.uiState.isCurrentFormStepEntityRef)

    // While the workbench XML is invalid the open form is left alone.
    f.viewModel.updateCustomFormDef(null, isPreset = false, relaunchOpenForm = false)
    assertTrue(after === f.uiState.activeFormWizardController)
  }

  @Test
  fun webFormFocusAndFraming_carryFreshTokens() {
    val f = DataCollectionFixture()
    f.viewModel.focusWebFormQuestion("/data/a")
    val first = assertNotNull(f.uiState.webFormFocusRequest)
    f.viewModel.focusWebFormQuestion("/data/a")
    val second = assertNotNull(f.uiState.webFormFocusRequest)
    assertEquals("/data/a", second.path)
    assertTrue(second.token > first.token)
    f.viewModel.consumeWebFormFocusRequest()
    assertNull(f.uiState.webFormFocusRequest)

    val bounds = org.openforis.collectone.v2.map.LngLatBounds(36.0, -1.0, 37.0, 0.0)
    f.viewModel.requestWebMapFraming(bounds, 14.0)
    val framing = assertNotNull(f.uiState.webMapFramingRequest)
    assertEquals(bounds, framing.bounds)
    assertEquals(14.0, framing.maxZoom)
    f.viewModel.requestWebMapFraming(bounds, 14.0)
    assertTrue(assertNotNull(f.uiState.webMapFramingRequest).token > framing.token)
  }

  @Test
  fun closingTheForm_stopsWebMapDrawing_andDropsFocusRequest() {
    val f = DataCollectionFixture()
    f.viewModel.launchFormFromFab("form-pest-disease-sighting")
    val controller = assertNotNull(f.uiState.activeFormWizardController)
    val geometryField =
      controller.formState.fieldStates.values.firstOrNull { field ->
        field.dataType == DataType.TYPE_GEOPOINT ||
          field.dataType == DataType.TYPE_GEOSHAPE ||
          field.dataType == DataType.TYPE_GEOTRACE
      }
    if (geometryField != null) {
      f.viewModel.webMapDrawing.startDrawing(
        geometryField.canonicalPath,
        kind =
          when (geometryField.dataType) {
            DataType.TYPE_GEOPOINT -> MapDrawingKind.POINT
            DataType.TYPE_GEOTRACE -> MapDrawingKind.LINE
            else -> MapDrawingKind.POLYGON
          },
      )
      assertTrue(f.uiState.isWebMapDrawing)
    }
    f.viewModel.focusWebFormQuestion("/data/x")

    f.viewModel.closeActiveFormRunner()

    assertFalse(f.uiState.isWebMapDrawing)
    assertNull(f.uiState.webFormFocusRequest)
    assertTrue(f.uiState.webFormGeometries.isEmpty())
  }

  @Test
  fun qrCodeDialog_opensAndCloses() {
    val f = DataCollectionFixture()
    f.viewModel.openEntityQrCode("00000007-640e-5ed7-9874-4d6b6717f21d")
    assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", f.uiState.activeQrCodeEntity?.id)
    f.viewModel.closeEntityQrCode()
    assertNull(f.uiState.activeQrCodeEntity)
  }

  @Test
  fun shareEntityPdf_opensSheet_andSharingClosesIt() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.shareEntityPdf("00000007-640e-5ed7-9874-4d6b6717f21d")
    val sheet = f.viewModel.awaitSharedPdfSheet()
    assertEquals("00000007-640e-5ed7-9874-4d6b6717f21d", sheet.targetId)
    assertTrue(sheet.pdfFileName.endsWith(".pdf"))

    f.viewModel.previewActivePdf()
    assertEquals(listOf(sheet.pdfFileName), f.pdfExportClient.previewed)

    assertEquals(
      Res.string.app_pdf_share_title_map_feature,
      assertIs<UiText.Resource>(sheet.shareTitle).resource,
    )
    f.viewModel.shareActivePdf("Coffee parcel report")
    assertEquals(sheet.pdfFileName to "Coffee parcel report", f.pdfExportClient.shared.single())
    assertNull(f.uiState.activeSharedPdfSheet)
    assertNull(f.uiState.pdfExportMessage)
  }

  @Test
  fun shareActivePdf_reportsSavedFailedAndCancelled() = runTest {
    val f = DataCollectionFixture()
    f.pdfExportClient.nextShareResult = PdfExportResult.FAILED
    f.viewModel.shareEntityPdf("00000007-640e-5ed7-9874-4d6b6717f21d")
    val fileName = f.viewModel.awaitSharedPdfSheet().pdfFileName
    f.viewModel.shareActivePdf(SHARE_TITLE)
    assertNotNull(f.uiState.activeSharedPdfSheet)
    assertEquals(
      UiText.Resource(Res.string.app_pdf_share_failed, listOf(fileName)),
      f.uiState.pdfExportMessage,
    )

    f.pdfExportClient.nextShareResult = PdfExportResult.CANCELLED
    f.viewModel.dismissPdfExportMessage()
    f.viewModel.shareActivePdf(SHARE_TITLE)
    assertNotNull(f.uiState.activeSharedPdfSheet)
    assertNull(f.uiState.pdfExportMessage)

    f.pdfExportClient.nextShareResult = PdfExportResult.SAVED
    f.viewModel.shareActivePdf(SHARE_TITLE)
    assertNull(f.uiState.activeSharedPdfSheet)
    assertEquals(
      UiText.Resource(Res.string.app_pdf_saved, listOf(fileName)),
      f.uiState.pdfExportMessage,
    )
  }

  @Test
  fun saveActivePdf_andDownloads_saveThroughTheClient() = runTest {
    val f = DataCollectionFixture()
    f.viewModel.shareSubmissionPdf("00000026-cca2-5594-b83f-02266f40048e")
    val sheet = f.viewModel.awaitSharedPdfSheet()
    assertEquals("00000026-cca2-5594-b83f-02266f40048e", sheet.targetId)
    f.viewModel.saveActivePdf()
    assertEquals(listOf(sheet.pdfFileName), f.pdfExportClient.saved)
    assertNull(f.uiState.activeSharedPdfSheet)
    val message = assertIs<UiText.Resource>(f.uiState.pdfExportMessage)
    assertEquals(Res.string.app_pdf_saved_with_summary, message.resource)
    assertEquals(sheet.pdfFileName, message.args.first())

    f.viewModel.downloadEntityPdf("00000007-640e-5ed7-9874-4d6b6717f21d")
    f.viewModel.downloadSubmissionPdf("00000026-cca2-5594-b83f-02266f40048e")
    f.viewModel.awaitUiState { f.pdfExportClient.saved.size == 3 }
    assertNull(f.uiState.activeSharedPdfSheet)

    f.viewModel.closeSharePdfSheet()
    // Unknown IDs are dropped right away, without generating anything.
    f.viewModel.downloadEntityPdf("missing")
    assertEquals(3, f.pdfExportClient.saved.size)
  }

  @Test
  fun canSharePdfFiles_followsTheClient() {
    val f = DataCollectionFixture(pdfExportClient = RecordingPdfExportClient(canShareFiles = false))
    assertFalse(f.uiState.canSharePdfFiles)
  }

  @Test
  fun publicDataSurvey_requiresConsentOncePerSurvey_beforeLaunchingForm() {
    val f = DataCollectionFixture()
    // Seeded "00000036-1a1c-5c51-997f-28b67d813aa3" is PUBLIC with PeerDataVisibility.ALL (Public
    // data).
    runNow { f.surveyRepository.setActiveSurveyId("00000036-1a1c-5c51-997f-28b67d813aa3") }
    f.viewModel.onSurveyActivated()
    assertTrue(f.uiState.isActiveSurveyPublicData)
    assertFalse(f.uiState.isActiveSurveyPrivateSubmissions)

    val formId = f.uiState.forms.first().id
    f.viewModel.launchFormFromFab(formId)

    // Form does not open yet; informed consent prompt is presented.
    assertFalse(f.uiState.isDataCollectionFormOpen)
    val prompt = assertNotNull(f.uiState.pendingCollectorConsent)
    assertEquals("00000036-1a1c-5c51-997f-28b67d813aa3", prompt.surveyId)
    assertTrue(prompt.isPublicData)

    // Dismissing cancels the launch without opening the form.
    f.viewModel.dismissCollectorConsent()
    assertNull(f.uiState.pendingCollectorConsent)
    assertFalse(f.uiState.isDataCollectionFormOpen)

    // Launching again and accepting opens the form and remembers consent for this survey.
    f.viewModel.launchFormFromFab(formId)
    assertNotNull(f.uiState.pendingCollectorConsent)
    f.viewModel.acceptCollectorConsent()
    assertNull(f.uiState.pendingCollectorConsent)
    assertTrue(f.uiState.isDataCollectionFormOpen)

    // Subsequent launches in the same survey open immediately without re-prompting.
    f.viewModel.closeActiveFormRunner()
    f.viewModel.launchFormFromFab(formId)
    assertNull(f.uiState.pendingCollectorConsent)
    assertTrue(f.uiState.isDataCollectionFormOpen)
  }

  @Test
  fun privateSubmissionsSurvey_opensFormDirectlyWithoutConsentPrompt() {
    val f = DataCollectionFixture()
    // Seeded "00000031-ffc6-5c8e-934c-d14ec07533ad" is ANYONE_WITH_LINK + OWN_ONLY (Private
    // submissions).
    runNow { f.surveyRepository.setActiveSurveyId("00000031-ffc6-5c8e-934c-d14ec07533ad") }
    f.viewModel.onSurveyActivated()
    assertFalse(f.uiState.isActiveSurveyPublicData)
    assertTrue(f.uiState.isActiveSurveyPrivateSubmissions)

    val formId = f.uiState.forms.first().id
    f.viewModel.launchFormFromFab(formId)
    assertNull(f.uiState.pendingCollectorConsent)
    assertTrue(f.uiState.isDataCollectionFormOpen)
  }

  @Test
  fun customOrganizerConsent_promptsEvenWhenSubmissionsArePrivate() {
    val f = DataCollectionFixture()
    val surveyId = f.uiState.activeSurveyId
    runNow {
      f.surveyRepository.updateSurvey(surveyId) {
        it.copy(
          sharingPolicy = SharingPolicy.RESTRICTED,
          peerDataVisibility = PeerDataVisibility.OWN_ONLY,
          collectorConsentRequired = true,
          collectorConsentText = "Custom FPIC community agreement.",
        )
      }
    }

    val entity = f.uiState.entities.first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
    f.viewModel.launchFormForEntity(entity.id, "form-eudr-baseline")
    assertFalse(f.uiState.isDataCollectionFormOpen)
    val prompt = assertNotNull(f.uiState.pendingCollectorConsent)
    assertFalse(prompt.isPublicData)
    assertEquals("Custom FPIC community agreement.", prompt.customConsentText)

    f.viewModel.acceptCollectorConsent()
    assertNull(f.uiState.pendingCollectorConsent)
    assertTrue(f.uiState.isDataCollectionFormOpen)
    assertEquals(entity.id, f.uiState.activeDataCollectionEntityId)
  }

  @Test
  fun viewerRole_disablesDataCollection_andPreventsFormLaunch() {
    val f = DataCollectionFixture()
    f.authClient.signInAs(displayName = "Viewer", email = "viewer@example.org")
    val surveyId = f.uiState.activeSurveyId
    runNow {
      f.surveyRepository.updateSurvey(surveyId) {
        it.copy(
          ownerEmail = "other_owner@example.org",
          sharingPolicy = SharingPolicy.PUBLIC,
          defaultRole = org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole.VIEWER,
        )
      }
    }
    f.viewModel.onSurveyActivated()

    val form = f.uiState.forms.first()
    val entity = f.uiState.entities.first()
    assertFalse(f.uiState.canCollectData)
    assertFalse(f.uiState.isFormButtonEnabled(entity, form))

    f.viewModel.openAvailableFormsSheet()
    assertFalse(f.uiState.isAvailableFormsSheetOpen)

    f.viewModel.launchFormFromFab(form.id)
    assertFalse(f.uiState.isDataCollectionFormOpen)

    f.viewModel.launchFormForEntity(entity.id, form.id)
    assertFalse(f.uiState.isDataCollectionFormOpen)
  }
}

/** The string answer at [path], or `null` when the Form lacks the question or it is empty. */
private fun FormWizardController.stringAt(path: String): String? =
  formState.fieldStates[path]?.value?.scalar_value?.string_value

private const val SHARE_TITLE = "Coffee parcel report"
