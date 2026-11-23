/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.client.pdf

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.client.pdf.pdfLiteral
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.web.domain.model.ImpactActivityItem
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionLevel
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionSummary
import org.openforis.collectone.v2.web.domain.model.ImpactBreakdownItem
import org.openforis.collectone.v2.web.domain.model.ImpactCountedMessage
import org.openforis.collectone.v2.web.domain.model.ImpactDiagnosticNote
import org.openforis.collectone.v2.web.domain.model.ImpactFeatureTotals
import org.openforis.collectone.v2.web.domain.model.ImpactGoalSection
import org.openforis.collectone.v2.web.domain.model.ImpactIndicator
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.domain.model.ImpactSummaryScope
import org.openforis.collectone.v2.web.domain.model.ImpactText

class ImpactPdfReportsTest {
  private val summary =
    ImpactSummary(
      scope = ImpactSummaryScope.ORGANIZATION,
      scopeId = "00000013-31cc-5c95-90ea-379f624a5c72",
      title = "Kenya Forest Service",
      features = ImpactFeatureTotals(uniqueCount = 14, areaHa = 9.82, mappedCount = 15),
      producerCount = 3,
      goals =
        listOf(
          ImpactGoalSection(
            goalId = "deforestation_free_supply_chains",
            title = ImpactText.Plain("Deforestation-free supply chains"),
            indicators =
              listOf(
                ImpactIndicator(
                  conceptId = "eudr.commodity",
                  label = "Commodity",
                  valueText = mapFeatures(3),
                  breakdown = listOf(ImpactBreakdownItem("Coffee", mapFeatures(3), 1f)),
                )
              ),
          )
        ),
      ownIndicators =
        listOf(
          ImpactIndicator(
            "org.kenya_forest_service.shade_tree_count",
            "Shade trees",
            ImpactText.Plain("117"),
          )
        ),
      activity = listOf(ImpactActivityItem(ImpactEventType.EXPORT, 2, 3)),
      attribution =
        ImpactAttributionSummary(distribution = mapOf(ImpactAttributionLevel.DATA_COLLECTED to 2L)),
      diagnostics =
        listOf(
          ImpactDiagnosticNote(ImpactText.Plain("Cherry delivered (kg): 1 value isn't a number."))
        ),
    )

  @Test
  fun summary_printsTotalsGoalsIndicatorsUseAndWhatWasntCounted() {
    val pdf = runBlocking {
      ImpactPdfReports.summary(
        summary,
        generatedAt = Instant.fromEpochMilliseconds(0),
        filterLabel = "Country: Kenya",
      )
    }
    val text = pdf.bytes.decodeToString()

    assertTrue(text.startsWith("%PDF-"))
    assertEquals("impact-summary-kenya-forest-service.pdf", pdf.fileName)
    listOf(
        "Kenya Forest Service",
        "Organization impact summary",
        "Country: Kenya",
        "9.82 ha",
        "1 map feature mapped again elsewhere",
        "Producers registered",
        "Deforestation-free supply chains",
        "Coffee",
        "Your indicators",
        "Shade trees",
        "How the data was used",
        "3 map features",
        "Data collected",
        "Not counted",
      )
      .forEach { assertTrue(text.contains(drawn(it)), "Missing \"$it\"") }
    assertFalse(text.contains(drawn("Organization-suggested indicators")))
  }

  @Test
  fun summary_numbersItsPagesInTheLanguageSelectedWhenItsGenerated() {
    // On the JVM the app locale is the default locale, which the other tests expect in English.
    val defaultLocale = Locale.getDefault()
    val english = runBlocking {
      ImpactPdfReports.summary(summary, Instant.fromEpochMilliseconds(0))
    }
    val spanish =
      try {
        Locale.setDefault(Locale.forLanguageTag("es"))
        runBlocking { ImpactPdfReports.summary(summary, Instant.fromEpochMilliseconds(0)) }
      } finally {
        Locale.setDefault(defaultLocale)
      }

    assertTrue(
      english.bytes.decodeToString().contains(drawn("Page 1 of ${english.pageCount}")),
      "English page footer",
    )
    assertTrue(
      spanish.bytes.decodeToString().contains(drawn("Página 1 de ${spanish.pageCount}")),
      "Spanish page footer",
    )
  }

  @Test
  fun summary_namesAreaUnitsInTheLanguageSelectedWhenItsGenerated() {
    // On the JVM the app locale is the default locale, which the other tests expect in English.
    val defaultLocale = Locale.getDefault()
    val swahili =
      try {
        Locale.setDefault(Locale.forLanguageTag("sw"))
        runBlocking { ImpactPdfReports.summary(summary, Instant.fromEpochMilliseconds(0)) }
      } finally {
        Locale.setDefault(defaultLocale)
      }
    val text = swahili.bytes.decodeToString()

    assertTrue(text.contains(drawn("9.82 hekta")), "Swahili area")
    assertFalse(text.contains(drawn("9.82 ha")), "No English unit left")
  }

  private fun mapFeatures(count: Long) =
    ImpactText.Plural(ImpactCountedMessage.MAP_FEATURES, count, listOf(count.toString()))

  /** [text] as it appears inside a drawn PDF string (part of a line). */
  private fun drawn(text: String) = pdfLiteral(text).removeSurrounding("(", ")")

  @Test
  fun fileName_isASlugOfTheTitle() {
    assertEquals(
      "impact-summary-all-collect-one-users.pdf",
      ImpactPdfReports.fileName(summary.copy(title = "All Collect One users")),
    )
    assertEquals(
      "impact-summary-collect-one.pdf",
      ImpactPdfReports.fileName(summary.copy(title = "…")),
    )
  }
}
