/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.web.client.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.groundplatform.v2.app.client.pdf.pdfLiteral
import org.groundplatform.v2.app.domain.model.ImpactEventType
import org.groundplatform.v2.web.domain.model.ImpactActivityItem
import org.groundplatform.v2.web.domain.model.ImpactAttributionLevel
import org.groundplatform.v2.web.domain.model.ImpactAttributionSummary
import org.groundplatform.v2.web.domain.model.ImpactBreakdownItem
import org.groundplatform.v2.web.domain.model.ImpactDiagnosticNote
import org.groundplatform.v2.web.domain.model.ImpactFeatureTotals
import org.groundplatform.v2.web.domain.model.ImpactGoalSection
import org.groundplatform.v2.web.domain.model.ImpactIndicator
import org.groundplatform.v2.web.domain.model.ImpactSummary
import org.groundplatform.v2.web.domain.model.ImpactSummaryScope

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
            title = "Deforestation-free supply chains",
            indicators =
              listOf(
                ImpactIndicator(
                  conceptId = "eudr.commodity",
                  label = "Commodity",
                  valueText = "3 map features",
                  breakdown = listOf(ImpactBreakdownItem("Coffee", "3 map features", 1f)),
                )
              ),
          )
        ),
      ownIndicators =
        listOf(ImpactIndicator("org.kenya_forest_service.shade_tree_count", "Shade trees", "117")),
      activity = listOf(ImpactActivityItem(ImpactEventType.EXPORT, "Downloads", 2, 3)),
      attribution =
        ImpactAttributionSummary(distribution = mapOf(ImpactAttributionLevel.DATA_COLLECTED to 2L)),
      diagnostics = listOf(ImpactDiagnosticNote("Cherry delivered (kg): 1 value isn't a number.")),
    )

  @Test
  fun summary_printsTotalsGoalsIndicatorsUseAndWhatWasntCounted() {
    val pdf =
      ImpactPdfReports.summary(summary, generatedAtEpochMillis = 0, filterLabel = "Country: Kenya")
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
