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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.client.pdf.pdfLiteral
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale

/**
 * PDF reports are written in the language selected when they're asked for: switching the app's
 * language (which [ProvideAppLocale] applies) and then asking for a report gives a report in the
 * new language, from the same ViewModel.
 */
@OptIn(ExperimentalTestApi::class)
class PdfReportLanguageTest {
  // On the JVM the app locale is the default locale, which the other tests expect in English.
  private val defaultLocale = Locale.getDefault()

  @AfterTest
  fun restoreDefaultLocale() {
    Locale.setDefault(defaultLocale)
  }

  @Test
  fun switchingTheLanguage_beforeAskingForAReport_writesItInTheNewLanguage() {
    val f = DataCollectionFixture()
    runDesktopComposeUiTest {
      var languageCode by mutableStateOf("en")
      setContent { ProvideAppLocale(languageCode) {} }

      f.viewModel.downloadEntityPdf(MAP_FEATURE_ID)
      waitUntil(timeoutMillis = REPORT_TIMEOUT_MILLIS) { f.pdfExportClient.savedBytes.size == 1 }
      languageCode = "es"
      waitForIdle()
      f.viewModel.downloadEntityPdf(MAP_FEATURE_ID)
      waitUntil(timeoutMillis = REPORT_TIMEOUT_MILLIS) { f.pdfExportClient.savedBytes.size == 2 }
    }

    val (englishReport, spanishReport) = f.pdfExportClient.savedBytes.map { it.decodeToString() }
    assertTrue(englishReport.contains(pdfLiteral("Details")), "English section heading")
    assertTrue(englishReport.contains(pdfLiteral("Submissions")), "English section heading")
    assertTrue(spanishReport.contains(pdfLiteral("Detalles")), "Spanish section heading")
    assertTrue(spanishReport.contains(pdfLiteral("Envíos")), "Spanish section heading")
    assertFalse(spanishReport.contains(pdfLiteral("Details")), "No English heading left")
  }

  private companion object {
    const val MAP_FEATURE_ID = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val REPORT_TIMEOUT_MILLIS = 10_000L
  }
}
