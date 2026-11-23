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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.client.pdf.PdfExportResult
import org.openforis.collectone.v2.app.client.pdf.RecordPdfReports
import org.openforis.collectone.v2.app.client.pdf.pdfLiteral
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_pdf_saved
import org.openforis.collectone.v2.app.resources.app_pdf_saved_with_summary
import org.openforis.collectone.v2.app.resources.app_pdf_share_failed
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * Unit tests for [DataCollectionPdfExports] through [DataCollectionViewModel]: offline entity
 * report filtering, serialized export ordering, Share PDF sheet lifecycle (`shareActivePdf`,
 * `saveActivePdf`, `previewActivePdf`, `closeSharePdfSheet`, `reset`), and receipt event recording.
 *
 * JVM-only: reports load their text with Compose resources' `getString`, which on Android reads
 * `android.content.res.Resources`, a stub that throws in Android unit tests.
 */
class DataCollectionPdfExportsTest {
  private val noSubmissions = pdfLiteral("No submissions recorded yet.")

  @Test
  fun offlineEntityReport_leavesOutOthersSubmissions_unlessOfflineIsIgnored() = runTest {
    val f = DataCollectionFixture()
    // The fake auth client's profile is the collector, even while signed out.
    val collector = f.authClient.currentProfile().email
    val entity =
      f.uiState.entities.first { e ->
        e.submissions.isNotEmpty() &&
          e.submissions.none { it.collectorEmail.equals(collector, ignoreCase = true) }
      }

    val online = assertNotNull(f.viewModel.generateEntityPdf(entity.id)).bytes.decodeToString()
    f.connectivity.setOnline(false)
    val offline = assertNotNull(f.viewModel.generateEntityPdf(entity.id)).bytes.decodeToString()
    val ignoringOffline =
      assertNotNull(f.viewModel.generateEntityPdf(entity.id, ignoreOffline = true))
        .bytes
        .decodeToString()

    assertFalse(online.contains(noSubmissions))
    assertTrue(offline.contains(noSubmissions))
    assertFalse(ignoringOffline.contains(noSubmissions))
  }

  @Test
  fun downloads_finishInTheOrderTheyWereAskedFor() = runTest {
    val f = DataCollectionFixture()
    val entity = f.uiState.entities.first { it.submissions.isNotEmpty() }
    val submission = f.uiState.allSubmissions.first()

    f.viewModel.downloadEntityPdf(entity.id)
    f.viewModel.downloadSubmissionPdf(submission.id)
    f.viewModel.downloadEntityPdf(entity.id, ignoreOffline = true)
    f.viewModel.awaitUiState { f.pdfExportClient.saved.size == 3 }

    assertEquals(
      listOf(
        RecordPdfReports.entityFileName(entity),
        RecordPdfReports.submissionFileName(submission),
        RecordPdfReports.entityFileName(entity),
      ),
      f.pdfExportClient.saved,
    )
  }

  @Test
  fun unknownTargets_andClosedSheetActions_areNoOps() = runTest {
    val f = DataCollectionFixture()

    f.viewModel.shareEntityPdf("missing")
    f.viewModel.shareSubmissionPdf("missing")
    f.viewModel.downloadEntityPdf("missing")
    f.viewModel.downloadSubmissionPdf("missing")
    f.viewModel.shareActivePdf("Nothing open")
    f.viewModel.saveActivePdf()
    f.viewModel.previewActivePdf()

    assertNull(f.viewModel.generateEntityPdf("missing"))
    assertNull(f.viewModel.generateSubmissionPdf("missing"))
    assertNull(f.uiState.activeSharedPdfSheet)
    assertTrue(f.pdfExportClient.saved.isEmpty())
    assertTrue(f.pdfExportClient.shared.isEmpty())
    assertTrue(f.pdfExportClient.previewed.isEmpty())
  }

  @Test
  fun shareSheet_handlesPreviewFailedCancelledSavedSharedAndReceipts() = runTest {
    val f = DataCollectionFixture()
    val entity = f.uiState.entities.first { it.submissions.isNotEmpty() }
    val submission = entity.submissions.first()

    f.viewModel.shareEntityPdf(entity.id)
    val entitySheet = f.viewModel.awaitSharedPdfSheet()
    assertEquals(entity.id, entitySheet.targetId)

    f.viewModel.previewActivePdf()
    assertEquals(listOf(entitySheet.pdfFileName), f.pdfExportClient.previewed)

    // FAILED sets an error message and keeps the sheet open.
    f.pdfExportClient.nextShareResult = PdfExportResult.FAILED
    f.viewModel.shareActivePdf("Parcel report")
    assertNotNull(f.uiState.activeSharedPdfSheet)
    assertEquals(
      UiText.Resource(Res.string.app_pdf_share_failed, listOf(entitySheet.pdfFileName)),
      f.uiState.pdfExportMessage,
    )

    // CANCELLED keeps the sheet open without an error message once dismissed.
    f.viewModel.dismissPdfExportMessage()
    f.pdfExportClient.nextShareResult = PdfExportResult.CANCELLED
    f.viewModel.shareActivePdf("Parcel report")
    assertNotNull(f.uiState.activeSharedPdfSheet)
    assertNull(f.uiState.pdfExportMessage)

    // SAVED records RECEIPT_SHARED, closes the sheet, and shows a saved notice.
    f.pdfExportClient.nextShareResult = PdfExportResult.SAVED
    f.viewModel.shareActivePdf("Parcel report")
    assertNull(f.uiState.activeSharedPdfSheet)
    assertEquals(
      UiText.Resource(Res.string.app_pdf_saved, listOf(entitySheet.pdfFileName)),
      f.uiState.pdfExportMessage,
    )

    // Submission sheet: saveActivePdf saves bytes, closes sheet, and records receipt events.
    f.viewModel.shareSubmissionPdf(submission.id)
    val subSheet = f.viewModel.awaitSharedPdfSheet()
    assertEquals(submission.id, subSheet.targetId)
    f.viewModel.saveActivePdf()
    assertNull(f.uiState.activeSharedPdfSheet)
    assertEquals(
      Res.string.app_pdf_saved_with_summary,
      assertIs<UiText.Resource>(f.uiState.pdfExportMessage).resource,
    )

    // SHARED closes the sheet silently without setting pdfExportMessage.
    f.viewModel.dismissPdfExportMessage()
    f.pdfExportClient.nextShareResult = PdfExportResult.SHARED
    f.viewModel.shareEntityPdf(entity.id)
    f.viewModel.awaitSharedPdfSheet()
    f.viewModel.shareActivePdf("Parcel report")
    assertNull(f.uiState.activeSharedPdfSheet)
    assertNull(f.uiState.pdfExportMessage)

    val events = runNow { f.impactEventRepository.getEvents() }
    assertEquals(3, events.count { it.type == ImpactEventType.RECEIPT_GENERATED })
    assertEquals(3, events.count { it.type == ImpactEventType.RECEIPT_SHARED })
  }

  @Test
  fun reset_andCloseSharePdfSheet_dropOpenPdfAndMessage() = runTest {
    val f = DataCollectionFixture()
    val entity = f.uiState.entities.first()
    f.viewModel.shareEntityPdf(entity.id)
    f.viewModel.awaitSharedPdfSheet()
    f.viewModel.closeSharePdfSheet()
    assertNull(f.uiState.activeSharedPdfSheet)

    f.viewModel.shareEntityPdf(entity.id)
    f.viewModel.awaitSharedPdfSheet()
    f.viewModel.downloadEntityPdf(entity.id)
    f.viewModel.awaitPdfExportMessage()

    f.viewModel.reset()

    assertNull(f.uiState.activeSharedPdfSheet)
    assertNull(f.uiState.pdfExportMessage)
    // The sheet's PDF went with it: nothing is left to share or save.
    f.viewModel.saveActivePdf()
    assertEquals(1, f.pdfExportClient.saved.size)
  }
}
