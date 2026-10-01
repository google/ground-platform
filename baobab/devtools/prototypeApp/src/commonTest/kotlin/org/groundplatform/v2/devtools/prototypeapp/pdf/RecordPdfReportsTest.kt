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
package org.groundplatform.v2.devtools.prototypeapp.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry

/** Tests the entity and submission reports generated from the prototype's sample data. */
class RecordPdfReportsTest {

  private val generatedAt = 1_790_000_000_000L // 2026-09-21 14:13 UTC

  @Test
  fun entityReport_containsTheEntitysDetailsPropertiesAndSubmissions() {
    val state = PrototypeAppState()
    val entity = state.entities.first { it.submissions.isNotEmpty() }

    val pdf = assertNotNull(state.generateEntityPdf(entity.id))
    val text = pdf.bytes.decodeToString()

    assertTrue(text.startsWith("%PDF-1.4"))
    assertTrue(pdf.pageCount >= 1)
    assertEquals(RecordPdfReports.entityFileName(entity), pdf.fileName)
    assertTrue(pdf.fileName.endsWith(".pdf"))
    assertTrue(text.contains(pdfLiteral(entity.label)), "Title should be drawn")
    assertTrue(text.contains(pdfLiteral(entity.geoId)), "GeoID should be listed")
    assertTrue(text.contains(pdfLiteral("SUBMISSIONS")))
    // Device-local upload state and summary counts are not printed.
    assertFalse(text.contains(pdfLiteral("Upload status")))
    assertFalse(text.contains(pdfLiteral(entity.syncStatus.label)))
    assertFalse(text.contains(pdfLiteral("SUBMISSIONS (")))
    assertTrue(text.contains(pdfLiteral("Record ID")))
    assertTrue(text.contains(pdfLiteral("DETAILS")))
  }

  @Test
  fun submissionReport_listsEveryResponse() {
    val state = PrototypeAppState()
    val submission = state.allSubmissions.first { it.fields.isNotEmpty() }

    val pdf = assertNotNull(state.generateSubmissionPdf(submission.id))
    val text = pdf.bytes.decodeToString()

    assertEquals(RecordPdfReports.submissionFileName(submission), pdf.fileName)
    assertTrue(text.contains(pdfLiteral(submission.formTitle)))
    assertTrue(text.contains(pdfLiteral("RESPONSES")))
    assertTrue(text.contains(pdfLiteral("Submission ID")))
    assertFalse(text.contains(pdfLiteral("Upload status")))
  }

  @Test
  fun longSubmissions_flowOntoMorePagesWithPageNumbers() {
    val state = PrototypeAppState()
    val base = state.allSubmissions.first()
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
  fun unknownIds_generateNothing() {
    val state = PrototypeAppState()

    assertNull(state.generateEntityPdf("missing"))
    assertNull(state.generateSubmissionPdf("missing"))
  }

  @Test
  fun shareEntityPdf_opensSheetWithTheGeneratedFile() {
    val state = PrototypeAppState()
    val entity = state.entities.first()

    state.shareEntityPdf(entity.id)

    val sheet = assertNotNull(state.activeSharedPdfSheet)
    assertEquals(RecordPdfReports.entityFileName(entity), sheet.pdfFileName)
    assertTrue(sheet.pageCount >= 1)
    assertTrue(sheet.fileSizeLabel.endsWith("B"))

    state.closeSharePdfSheet()
    assertNull(state.activeSharedPdfSheet)
  }

  @Test
  fun downloadPdf_reportsTheSavedFile() {
    val state = PrototypeAppState()
    val submission = state.allSubmissions.first()

    state.downloadSubmissionPdf(submission.id)

    val message = assertNotNull(state.pdfExportMessage)
    assertTrue(message.contains(RecordPdfReports.submissionFileName(submission)))
    state.dismissPdfExportMessage()
    assertNull(state.pdfExportMessage)
  }

  @Test
  fun fileNamesAndLabels_areSafeAndReadable() {
    assertEquals(
      "coffee-parcel-eudr-gh-0419",
      RecordPdfReports.slug("Coffee Parcel / EUDR-GH-0419"),
    )
    assertEquals("record", RecordPdfReports.slug("中文"))
    assertEquals("S2-10c4a89e2f", RecordPdfReports.slug("S2:10c4a89e2f", lowercase = false))
    assertEquals("Site visit › Canopy", RecordPdfReports.humanizeGroupPath("site_visit/canopy"))
  }

  @Test
  fun formatUtcTimestamp_convertsEpochMillis() {
    assertEquals("1970-01-01 00:00 UTC", formatUtcTimestamp(0))
    assertEquals("2000-02-29 23:59 UTC", formatUtcTimestamp(951_868_740_000))
    assertEquals("2026-10-01 16:38 UTC", formatUtcTimestamp(1_790_872_680_000))
  }
}
