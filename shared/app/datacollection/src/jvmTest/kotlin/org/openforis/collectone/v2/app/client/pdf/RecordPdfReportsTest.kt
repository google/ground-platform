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
package org.openforis.collectone.v2.app.client.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_pdf_saved_with_summary
import org.openforis.collectone.v2.app.resources.domain_pdf_summary
import org.openforis.collectone.v2.app.ui.datacollection.awaitPdfExportMessage
import org.openforis.collectone.v2.app.ui.datacollection.awaitSharedPdfSheet
import org.openforis.collectone.v2.app.ui.session.TestAppSession
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * Tests the entity and submission reports generated from the sample data.
 *
 * JVM-only: reports load their text with Compose resources' `getString`, which on Android reads
 * `android.content.res.Resources`, a stub that throws in Android unit tests.
 */
class RecordPdfReportsTest {

  private val generatedAt =
    Instant.fromEpochMilliseconds(1_790_000_000_000L) // 2026-09-21 14:13 UTC

  @Test
  fun entityReport_containsTheEntitysDetailsPropertiesAndSubmissions() = runTest {
    val state = TestAppSession(sampleDataHolder())
    val entity =
      state.viewModels.dataCollection.uiState.value.entities.first { it.submissions.isNotEmpty() }

    val pdf = assertNotNull(state.viewModels.dataCollection.generateEntityPdf(entity.id))
    val text = pdf.bytes.decodeToString()

    assertTrue(text.startsWith("%PDF-1.4"))
    assertTrue(pdf.pageCount >= 1)
    assertEquals(RecordPdfReports.entityFileName(entity), pdf.fileName)
    assertTrue(pdf.fileName.endsWith(".pdf"))
    assertTrue(text.contains(pdfLiteral(entity.label)), "Title should be drawn")
    assertTrue(text.contains(pdfLiteral(entity.geoId)), "GeoID should be listed")
    assertTrue(text.contains(pdfLiteral("Submissions")))
    // Device-local upload state, summary counts, and internal IDs are not printed.
    assertFalse(text.contains(pdfLiteral("Upload status")))
    val englishSyncStatus =
      when (entity.syncStatus) {
        SyncStatus.UPLOADING -> "Uploading"
        SyncStatus.SYNCED -> "Synced"
        SyncStatus.FAILED -> "Failed"
      }
    assertFalse(text.contains(pdfLiteral(englishSyncStatus)))
    assertFalse(text.contains(pdfLiteral("Submissions (")))
    assertFalse(text.contains(pdfLiteral("Record ID")))
    assertTrue(text.contains(pdfLiteral("Details")))
  }

  @Test
  fun submissionReport_listsEveryResponse() = runTest {
    val state = TestAppSession(sampleDataHolder())
    val submission =
      state.viewModels.dataCollection.uiState.value.allSubmissions.first { it.fields.isNotEmpty() }

    val pdf = assertNotNull(state.viewModels.dataCollection.generateSubmissionPdf(submission.id))
    val text = pdf.bytes.decodeToString()

    assertEquals(RecordPdfReports.submissionFileName(submission), pdf.fileName)
    assertTrue(text.contains(pdfLiteral(submission.formTitle)))
    assertTrue(text.contains(pdfLiteral("Responses")))
    assertFalse(text.contains(pdfLiteral("Submission ID")))
    assertFalse(text.contains(pdfLiteral("Upload status")))
  }

  @Test
  fun longSubmissions_flowOntoMorePagesWithPageNumbers() = runTest {
    val state = TestAppSession(sampleDataHolder())
    val base = state.viewModels.dataCollection.uiState.value.allSubmissions.first()
    val longSubmission =
      base.copy(
        fields =
          (1..120).map { i ->
            SubmissionFieldEntry(
              questionName = "section_${i / 40}/q$i",
              questionLabel = "Question $i",
              answerValue = "Answer $i with enough words to wrap onto a second line in the PDF.",
            )
          }
      )

    val pdf = RecordPdfReports.submissionReport(longSubmission, "Survey", null, generatedAt)
    val text = pdf.bytes.decodeToString()

    assertTrue(pdf.pageCount > 2, "Expected several pages, got ${pdf.pageCount}")
    assertTrue(text.contains(pdfLiteral("Page 1 of ${pdf.pageCount}")))
    assertTrue(text.contains(pdfLiteral("Page ${pdf.pageCount} of ${pdf.pageCount}")))
    assertTrue(text.contains(pdfLiteral("Section 1")), "XForms group label should be drawn")
  }

  @Test
  fun unknownIds_generateNothing() = runTest {
    val state = TestAppSession(sampleDataHolder())

    assertNull(state.viewModels.dataCollection.generateEntityPdf("missing"))
    assertNull(state.viewModels.dataCollection.generateSubmissionPdf("missing"))
  }

  @Test
  fun shareEntityPdf_opensSheetWithTheGeneratedFile() = runTest {
    val state = TestAppSession(sampleDataHolder())
    val entity = state.viewModels.dataCollection.uiState.value.entities.first()

    state.viewModels.dataCollection.shareEntityPdf(entity.id)

    val sheet = state.viewModels.dataCollection.awaitSharedPdfSheet()
    assertEquals(RecordPdfReports.entityFileName(entity), sheet.pdfFileName)
    assertTrue(sheet.pageCount >= 1)
    assertTrue(sheet.fileSizeLabel.endsWith("B"))

    state.viewModels.dataCollection.closeSharePdfSheet()
    assertNull(state.viewModels.dataCollection.uiState.value.activeSharedPdfSheet)
  }

  @Test
  fun downloadPdf_reportsTheSavedFile() = runTest {
    val state = TestAppSession(sampleDataHolder())
    val submission = state.viewModels.dataCollection.uiState.value.allSubmissions.first()

    state.viewModels.dataCollection.downloadSubmissionPdf(submission.id)

    val message = assertIs<UiText.Resource>(state.viewModels.dataCollection.awaitPdfExportMessage())
    assertEquals(Res.string.app_pdf_saved_with_summary, message.resource)
    assertEquals(RecordPdfReports.submissionFileName(submission), message.args.first())
    assertEquals(Res.plurals.domain_pdf_summary, assertIs<UiText.Plural>(message.args[1]).resource)
    state.viewModels.dataCollection.dismissPdfExportMessage()
    assertNull(state.viewModels.dataCollection.uiState.value.pdfExportMessage)
  }
}
