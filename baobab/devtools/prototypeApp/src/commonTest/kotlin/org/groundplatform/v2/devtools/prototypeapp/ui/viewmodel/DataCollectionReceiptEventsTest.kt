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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PdfExportResult
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase

/**
 * Receipt activity of [DataCollectionViewModel]: PDF reports record `RECEIPT_GENERATED` when made
 * and `RECEIPT_SHARED` when shared or saved, queue offline, and upload with the next sync.
 */
class DataCollectionReceiptEventsTest {
  private val DataCollectionFixture.recorded: List<ImpactEvent>
    get() = runNow { impactEventRepository.getEvents() }

  private fun DataCollectionFixture.recorded(type: ImpactEventType) = recorded.filter {
    it.type == type
  }

  private fun DataCollectionFixture.syncMutations() =
    SyncMutationsUseCase(
      mutationRepository = MutationRepositoryImpl(store),
      surveyRepository = surveyRepository,
      transactionRunner = LocalStoreTransactionRunner(store),
      connectivityRepository = connectivity,
      impactEventRepository = impactEventRepository,
    )

  @Test
  fun shareEntityPdf_recordsOneReceiptGenerated_forTheActiveSurveyAndFeature() {
    val f = DataCollectionFixture()
    val surveyId = f.uiState.activeSurveyId
    val entity = f.uiState.entities.first { it.id == "entity-nyr-104" }

    f.viewModel.shareEntityPdf(entity.id)
    // Reading state and previewing the open sheet record nothing more.
    f.uiState.activeSharedPdfSheet
    f.viewModel.previewActivePdf()

    val event = f.recorded.single()
    assertEquals(ImpactEventType.RECEIPT_GENERATED, event.type)
    assertEquals(surveyId, event.surveyId)
    assertEquals(1, event.featureCount)
    assertEquals(entity.areaHectares, event.areaHa)
    val survey = runNow { f.surveyRepository.getSurveys() }.first { it.id == surveyId }
    assertEquals(survey.organizationId, event.organizationId)
    assertTrue(event.organizationId != null, "The sample survey belongs to an organization")
    assertEquals(
      runNow { SurveyEditorRepositoryImpl(f.store).getDraft(surveyId) }.details.purposeIds,
      event.purposeIds,
    )
    assertTrue(event.purposeIds.isNotEmpty(), "The sample survey has Purpose Packs")
    assertEquals(runNow { f.authRepository.getSession() }.profile.email, event.actorUserId)
    assertNull(event.exportProfileId)
    assertTrue(event.isUploaded, "Recorded online, the event uploads right away")
  }

  @Test
  fun shareActivePdf_recordsReceiptShared_onlyWhenSharedOrSaved() {
    val f = DataCollectionFixture()
    f.viewModel.shareEntityPdf("entity-nyr-104")

    f.pdfExportClient.nextShareResult = PdfExportResult.FAILED
    f.viewModel.shareActivePdf()
    f.pdfExportClient.nextShareResult = PdfExportResult.CANCELLED
    f.viewModel.shareActivePdf()
    assertTrue(f.recorded(ImpactEventType.RECEIPT_SHARED).isEmpty())

    f.pdfExportClient.nextShareResult = PdfExportResult.SHARED
    f.viewModel.shareActivePdf()
    val generated = f.recorded(ImpactEventType.RECEIPT_GENERATED).single()
    val shared = f.recorded(ImpactEventType.RECEIPT_SHARED).single()
    assertEquals(generated.surveyId, shared.surveyId)
    assertEquals(generated.featureCount, shared.featureCount)
    assertEquals(generated.areaHa, shared.areaHa)

    // Saving from the system share sheet counts as sharing too.
    f.viewModel.shareEntityPdf("entity-nyr-104")
    f.pdfExportClient.nextShareResult = PdfExportResult.SAVED
    f.viewModel.shareActivePdf()
    assertEquals(2, f.recorded(ImpactEventType.RECEIPT_SHARED).size)
  }

  @Test
  fun saveActivePdf_recordsReceiptShared_forTheSubmissionsFeature() {
    val f = DataCollectionFixture()
    val submission = f.uiState.allSubmissions.first { it.id == "sub-shade-201-wave3" }
    val entity = f.uiState.entities.first { it.id == submission.entityId }

    f.viewModel.shareSubmissionPdf(submission.id)
    f.viewModel.saveActivePdf()

    assertEquals(
      listOf(ImpactEventType.RECEIPT_GENERATED, ImpactEventType.RECEIPT_SHARED),
      f.recorded.map { it.type },
    )
    f.recorded.forEach { event ->
      assertEquals(1, event.featureCount)
      assertEquals(entity.areaHectares, event.areaHa)
    }
  }

  @Test
  fun downloads_recordReceiptGeneratedAndShared_andUnknownRecordsNothing() {
    val f = DataCollectionFixture()
    f.viewModel.downloadEntityPdf("entity-nyr-104")
    f.viewModel.downloadSubmissionPdf("sub-shade-201-wave3")
    f.viewModel.downloadEntityPdf("missing")
    f.viewModel.shareSubmissionPdf("missing")

    assertEquals(2, f.recorded(ImpactEventType.RECEIPT_GENERATED).size)
    assertEquals(2, f.recorded(ImpactEventType.RECEIPT_SHARED).size)
  }

  @Test
  fun closingTheSheet_forgetsTheReceipt() {
    val f = DataCollectionFixture()
    f.viewModel.shareEntityPdf("entity-nyr-104")
    f.viewModel.closeSharePdfSheet()
    f.viewModel.saveActivePdf()
    f.viewModel.shareActivePdf()

    assertEquals(listOf(ImpactEventType.RECEIPT_GENERATED), f.recorded.map { it.type })
  }

  @Test
  fun offlineReceipts_queue_andUploadWithTheNextSync() {
    val f = DataCollectionFixture()
    val surveyId = f.uiState.activeSurveyId
    f.connectivity.setOnline(false)

    f.viewModel.downloadEntityPdf("entity-nyr-104")
    assertEquals(2, f.recorded.size)
    assertTrue(f.recorded.none { it.isUploaded }, "Recorded offline, events wait on the device")

    // Syncing while still offline keeps them queued.
    val sync = f.syncMutations()
    assertEquals(
      SyncMutationsUseCase.OFFLINE_UPLOAD_NOTICE,
      runNow { sync.syncAllOutboxMutations(surveyId) },
    )
    assertTrue(f.recorded.none { it.isUploaded })

    f.connectivity.setOnline(true)
    runNow { sync.syncAllOutboxMutations(surveyId) }
    assertTrue(f.recorded.all { it.isUploaded })
    assertEquals(0, runNow { f.impactEventRepository.markAllUploaded() })
  }

  @Test
  fun offlineReceipts_uploadEvenWhenTheOutboxIsEmpty() {
    val f = DataCollectionFixture()
    f.connectivity.setOnline(false)
    f.viewModel.shareEntityPdf("entity-nyr-104")
    f.connectivity.setOnline(true)

    // A survey with no mutations: only the queued activity record uploads.
    val notice = runNow { f.syncMutations().syncAllOutboxMutations("survey-without-mutations") }
    assertEquals("Uploaded 1 activity record(s) to Ground Cloud", notice)
    assertTrue(f.recorded.single().isUploaded)

    // Nothing left to upload.
    assertNull(runNow { f.syncMutations().syncAllOutboxMutations("survey-without-mutations") })
  }

  @Test
  fun dashboard_countsActivityRecordsWaitingToSync() {
    val f = DataCollectionFixture()
    val mutationRepository = MutationRepositoryImpl(f.store)
    val transactionRunner = LocalStoreTransactionRunner(f.store)
    val dashboard =
      DashboardViewModel(
        surveyRepository = f.surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(f.store),
        authRepository = f.authRepository,
        mutationRepository = mutationRepository,
        createSurveyUseCase =
          CreateSurveyUseCase(
            f.surveyRepository,
            SurveyEditorRepositoryImpl(f.store),
            transactionRunner,
          ),
        syncMutationsUseCase = f.syncMutations(),
        scope = f.scope,
        impactEventRepository = f.impactEventRepository,
      )
    assertEquals(0, dashboard.uiState.value.pendingActivityRecordCount)

    f.connectivity.setOnline(false)
    f.viewModel.downloadEntityPdf("entity-nyr-104")
    assertEquals(2, dashboard.uiState.value.pendingActivityRecordCount)

    f.connectivity.setOnline(true)
    dashboard.syncAllOutboxMutations()
    assertEquals(0, dashboard.uiState.value.pendingActivityRecordCount)
    assertFalse(f.recorded.any { !it.isUploaded })
  }
}
