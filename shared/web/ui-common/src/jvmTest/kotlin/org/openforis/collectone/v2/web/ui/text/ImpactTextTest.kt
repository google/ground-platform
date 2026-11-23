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
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.ImpactEventRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionLevel
import org.openforis.collectone.v2.web.domain.model.ImpactCountedMessage
import org.openforis.collectone.v2.web.domain.model.ImpactGoals
import org.openforis.collectone.v2.web.domain.model.ImpactHint
import org.openforis.collectone.v2.web.domain.model.ImpactIndicator
import org.openforis.collectone.v2.web.domain.model.ImpactMessage
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.domain.model.ImpactText
import org.openforis.collectone.v2.web.domain.usecase.ComputeImpactUseCase
import org.openforis.collectone.v2.web.domain.usecase.ImpactSnapshot
import org.openforis.collectone.v2.web.domain.usecase.ImpactSurveySource

/**
 * The English text of impact summaries over the seeded sample data, as the dashboards and the PDF
 * summary show it.
 */
class ImpactTextTest {
  private val compute = ComputeImpactUseCase()

  private fun english(text: ImpactText): String = runBlocking { text.load() }

  private fun snapshot(store: LocalStore): ImpactSnapshot = runNow {
    val surveys = SurveyRepositoryImpl(store)
    val editor = SurveyEditorRepositoryImpl(store)
    compute(
      sources =
        surveys.getSurveys().map {
          ImpactSurveySource(
            it,
            surveys.observeSurveyContent(it.id).first(),
            editor.getDraft(it.id),
          )
        },
      libraries = LibraryRepositoryImpl(store).observeLibraries().first(),
      organizations = OrganizationRepositoryImpl(store).getOrganizations(),
      events = ImpactEventRepositoryImpl(store).getEvents(),
    )
  }

  private fun storeWithEveryActivity(): LocalStore {
    val store = seededStore()
    val events = ImpactEventRepositoryImpl(store)
    runNow {
      ImpactEventType.entries.forEach { type ->
        events.append(
          ImpactEvent(
            id = "evt-${type.name}",
            type = type,
            surveyId = KENYA,
            organizationId = KFS,
            featureCount = 3,
            areaHa = 5.85,
            occurredAt = "2026-10-01T00:00:00Z",
            actorUserId = "uid",
            isUploaded = true,
          )
        )
      }
    }
    return store
  }

  /** Every piece of text in [summary], one line each. */
  private fun lines(summary: ImpactSummary): List<String> = buildList {
    fun indicator(prefix: String, it: ImpactIndicator) {
      add("$prefix ${it.label}: ${english(it.valueText)} | ${english(it.detail)}")
      it.breakdown.forEach { bar -> add("$prefix   ${bar.label}: ${english(bar.valueText)}") }
    }
    summary.goals.forEach { goal ->
      add("goal: ${english(goal.title)}")
      goal.indicators.forEach { indicator("  indicator", it) }
    }
    summary.ownIndicators.forEach { indicator("own", it) }
    summary.suggestedIndicators.forEach { group ->
      add("suggested: ${english(group.goalTitle)} (${group.organizationName})")
      group.indicators.forEach { indicator("  indicator", it) }
    }
    summary.activity.forEach {
      add("activity: ${runBlocking { getString(it.type.activityLabelRes) }} ${it.count}")
    }
    summary.diagnostics.forEach { add("note: ${english(it.message)}") }
    summary.hints.forEach { add("hint: ${runBlocking { getString(it.messageRes) }}") }
  }

  @Test
  fun attributionLevels() {
    assertEquals(
      listOf(
        "Used in an official submission",
        "Used by a partner",
        "Exported for its purpose",
        "Data collected",
        "Getting started",
      ),
      ImpactAttributionLevel.entries.map { runBlocking { getString(it.labelRes) } },
    )
    assertEquals(
      listOf(
        "The organizer said the data went into an official submission, such as an EUDR due " +
          "diligence statement, a FERM report, or a protected area assessment.",
        "The data was sent to a partner, or used by buyers, for land titling, or to check a map " +
          "or model.",
        "The data was downloaded in a format one of its purposes provides, such as EUDR GeoJSON.",
        "Data was collected, but we don't know yet how it was used.",
        "A test or exploratory survey, or one with no data yet.",
      ),
      ImpactAttributionLevel.entries.map { runBlocking { getString(it.explanationRes) } },
    )
  }

  @Test
  fun hints() {
    assertEquals(
      listOf(
        "Numbers appear here once data is collected.",
        "Pick what the data will be used for in Survey details, so results can be grouped by goal.",
        "Link form questions or layer columns to standard fields (like Commodity), so their " +
          "answers can be added up.",
      ),
      ImpactHint.entries.map { runBlocking { getString(it.messageRes) } },
    )
  }

  @Test
  fun goalLabels() {
    assertEquals(
      listOf(
        "Deforestation-free supply chains",
        "Ecosystem restoration",
        "Forest carbon and conservation",
        "Indigenous and community land rights",
        "Legal timber",
        "Open land cover and crop reference data",
        "Protected areas",
        "Smallholder livelihoods and market access",
        "Other standard fields",
        "Soil health",
      ),
      listOf(
          "deforestation_free_supply_chains",
          "ecosystem_restoration",
          "forest_carbon_and_conservation",
          "iplc_rights_and_tenure",
          "legal_timber",
          "open_earth_data",
          "effective_protected_areas",
          "smallholder_livelihoods",
          "",
          "soil_health",
        )
        .map { english(ImpactGoals.label(it)) },
    )
  }

  @Test
  fun activityLabelOfClosedSurveys() {
    assertEquals(
      "Surveys closed",
      runBlocking { getString(ImpactEventType.SURVEY_CLOSED.activityLabelRes) },
    )
  }

  @Test
  fun messagesNotInTheSampleSummaries() {
    val features = ImpactText.count(ImpactCountedMessage.MAP_FEATURES, 1)
    assertEquals(
      listOf(
        "1 map feature",
        "Average of 1 map feature",
        "Covering 2.5 ha",
        "A field links to a standard field that no longer exists (Commodity).",
        "A field links to a standard field that was retired (Commodity).",
        "A field links to another organization's standard field, so it isn't counted here " +
          "(Commodity).",
        "Yield: 2 values use a unit that can't be converted (\"5 bags\", \"3 sacks\"), so left out.",
      ),
      listOf(
          features,
          ImpactText.of(ImpactMessage.DETAIL_AVERAGE_OF, features),
          ImpactText.of(ImpactMessage.DETAIL_COVERING, "2.5 ha"),
          ImpactText.of(ImpactMessage.NOTE_LINK_UNKNOWN, "Commodity"),
          ImpactText.of(ImpactMessage.NOTE_LINK_DEPRECATED, "Commodity"),
          ImpactText.of(ImpactMessage.NOTE_LINK_NOT_IN_LIBRARY, "Commodity"),
          ImpactText.Plural(
            ImpactCountedMessage.NOTE_UNSUPPORTED_UNIT,
            2,
            listOf("Yield", "2", "\"5 bags\", \"3 sacks\""),
          ),
        )
        .map(::english),
    )
  }

  @Test
  fun surveySummaryText() {
    val summary = assertNotNull(compute.surveySummary(snapshot(storeWithEveryActivity()), KENYA))
    assertEquals(
      KENYA_GOAL_LINES +
        OWN_INDICATOR_LINES +
        listOf(
          "activity: Downloads 1",
          "activity: Sent to partners 1",
          "activity: Receipts created 1",
          "activity: Receipts shared 1",
          "activity: DOIs minted 1",
          NOT_A_NUMBER_NOTE,
        ),
      lines(summary),
    )
  }

  @Test
  fun organizationSummaryText() {
    val summary = assertNotNull(compute.organizationSummary(snapshot(seededStore()), KFS))
    assertEquals(
      KENYA_GOAL_LINES + OWN_INDICATOR_LINES + NO_ACTIVITY_LINES + NOT_A_NUMBER_NOTE,
      lines(summary),
    )
  }

  @Test
  fun globalSummaryText() {
    val summary = compute.globalSummary(snapshot(seededStore()))
    assertEquals(
      KENYA_GOAL_LINES +
        listOf(
          "suggested: Ecosystem restoration (Kenya Forest Service)",
          "  indicator Shade trees: 117 | Total from 3 map features",
        ) +
        NO_ACTIVITY_LINES +
        NOT_A_NUMBER_NOTE,
      lines(summary),
    )
  }

  @Test
  fun summaryWithoutNumbersText() {
    val summary = assertNotNull(compute.surveySummary(snapshot(seededStore()), UNLINKED_SURVEY))
    assertEquals(
      NO_ACTIVITY_LINES +
        listOf(
          "hint: Pick what the data will be used for in Survey details, so results can be " +
            "grouped by goal.",
          "hint: Link form questions or layer columns to standard fields (like Commodity), so " +
            "their answers can be added up.",
        ),
      lines(summary),
    )
  }

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val UNLINKED_SURVEY = "00000034-c088-5ab9-8b98-e6bccb3dca71"
    const val KFS = SampleOrganizationsData.KENYA_FOREST_SERVICE

    val KENYA_GOAL_LINES =
      listOf(
        "goal: Deforestation-free supply chains",
        "  indicator Commodity: 3 map features | By commodity",
        "  indicator   Coffee: 3 map features · 5.85 ha",
        "  indicator Deforestation-free: 67% Yes | Of 3 map features with an answer",
        "  indicator   Yes: 67%",
        "  indicator   Unknown: 33%",
      )

    val OWN_INDICATOR_LINES =
      listOf(
        "own Cherry delivered (kg): 1,550 kg | Total from 2 map features",
        "own Shade trees: 117 | Total from 3 map features",
      )

    val NO_ACTIVITY_LINES =
      listOf("activity: Downloads 0", "activity: Receipts created 0", "activity: Receipts shared 0")

    const val NOT_A_NUMBER_NOTE =
      "note: Cherry delivered (kg): 1 value isn't a number (\"Not weighed\"), so left out."
  }
}
