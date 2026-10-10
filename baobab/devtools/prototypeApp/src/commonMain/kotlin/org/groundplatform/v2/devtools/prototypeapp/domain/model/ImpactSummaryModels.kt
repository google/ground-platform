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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/** Whose numbers an [ImpactSummary] shows. */
enum class ImpactSummaryScope {
  SURVEY,
  ORGANIZATION,
  /** Every organization that takes part in platform-wide numbers. */
  GLOBAL,
}

/** Map features of a scope, counting plots mapped more than once (same GeoID) once. */
data class ImpactFeatureTotals(
  /** Unique map features. */
  val uniqueCount: Long = 0,
  /** Area of the unique map features in hectares. */
  val areaHa: Double = 0.0,
  /** Map features before removing duplicates. */
  val mappedCount: Long = 0,
) {
  /** Map features that duplicate another one (the same plot mapped again). */
  val duplicateCount: Long
    get() = (mappedCount - uniqueCount).coerceAtLeast(0)
}

/** One bar of an indicator's breakdown, e.g. "Coffee · 3 plots". */
data class ImpactBreakdownItem(
  val label: String,
  val valueText: String,
  /** Share of the indicator's total, 0–1, for the bar length. */
  val fraction: Float,
)

/** One number on an impact card, from a linked standard field (concept). */
data class ImpactIndicator(
  val conceptId: String,
  val label: String,
  /** The headline number with its unit, e.g. `"3 plots"` or `"5,820 kg"`. */
  val valueText: String,
  /** What the number covers, e.g. `"Total of 3 plots"`. */
  val detail: String = "",
  val breakdown: List<ImpactBreakdownItem> = emptyList(),
)

/** Indicators that contribute to one goal, e.g. "Deforestation-free supply chains". */
data class ImpactGoalSection(
  /** Goal ID, or blank for standard fields without a goal. */
  val goalId: String,
  val title: String,
  val indicators: List<ImpactIndicator>,
)

/** Another organization's own indicators under a goal they suggest it counts toward. */
data class ImpactSuggestedGroup(
  val goalId: String,
  val goalTitle: String,
  val organizationId: String,
  val organizationName: String,
  val indicators: List<ImpactIndicator>,
)

/** One kind of "data was used" activity, e.g. 4 exports covering 3 plots. */
data class ImpactActivityItem(
  val type: ImpactEventType,
  val label: String,
  val count: Long,
  val featureCount: Long = 0,
  val areaHa: Double = 0.0,
)

/** Answers to "What happened with this data?". */
data class ImpactOutcomeSummary(
  /** Surveys per use, most common first. */
  val uses: List<Pair<SurveyOutcomeKind, Long>> = emptyList(),
  /** Surveys answered "Not yet". */
  val notYetCount: Long = 0,
  /** Closed surveys nobody answered for. */
  val unansweredCount: Long = 0,
) {
  val isEmpty: Boolean
    get() = uses.isEmpty() && notYetCount == 0L && unansweredCount == 0L
}

/** How directly data contributed to results, on a 1–5 scale (see [ImpactAttributionLevel]). */
data class ImpactAttributionSummary(
  /** The survey's level, for a survey summary. */
  val level: ImpactAttributionLevel? = null,
  /** Surveys per level, for organization and platform-wide summaries. */
  val distribution: Map<ImpactAttributionLevel, Long> = emptyMap(),
)

/** Attribution levels in plain language (see `docs/technical/backend/impact-aggregation.md`). */
enum class ImpactAttributionLevel(val score: Int, val label: String, val explanation: String) {
  OFFICIAL_SUBMISSION(
    5,
    "Used in an official submission",
    "The organizer said the data went into an official submission, such as an EUDR due " +
      "diligence statement, a FERM report, or a protected area assessment.",
  ),
  PARTNER_USE(
    4,
    "Used by a partner",
    "The data was sent to a partner, or used by buyers, for land titling, or to check a map or " +
      "model.",
  ),
  PURPOSE_EXPORT(
    3,
    "Exported for its purpose",
    "The data was downloaded in a format one of its purposes provides, such as EUDR GeoJSON.",
  ),
  DATA_COLLECTED(
    2,
    "Data collected",
    "Data was collected, but we don't know yet how it was used.",
  ),
  EXPLORATORY(
    1,
    "Getting started",
    "A test or exploratory survey, or one with no data yet.",
  ),
}

/** Platform-wide totals for one country. */
data class ImpactCountryTotals(
  val countryCode: String,
  val countryName: String,
  val featureCount: Long,
  val areaHa: Double,
  /** Fewer than the minimum number of map features: shown as "fewer than 10". */
  val isSuppressed: Boolean,
)

/** How map areas were grouped for platform-wide numbers, keeping small groups private. */
data class ImpactCellSummary(
  val publishedCellCount: Int = 0,
  val suppressedCellCount: Int = 0,
  val suppressedFeatureCount: Long = 0,
)

/** Something the numbers couldn't include, with why. */
data class ImpactDiagnosticNote(val message: String)

/** What to do to get numbers, when a summary has none yet. */
enum class ImpactHint(val message: String) {
  COLLECT_DATA("Numbers appear here once data is collected."),
  PICK_PURPOSE(
    "Pick what the data will be used for in Survey details, so results can be grouped by goal."
  ),
  LINK_FIELDS(
    "Link form questions or layer columns to standard fields (like Commodity), so their answers " +
      "can be added up."
  ),
}

/**
 * Impact numbers of one survey, organization, or the whole platform, grouped by goal and written in
 * plain language. Built from the shared aggregation job (`org.groundplatform.v2.core.impact`), so
 * the dashboards, the PDF summary, and the backend agree.
 */
data class ImpactSummary(
  val scope: ImpactSummaryScope,
  val scopeId: String,
  val title: String,
  val features: ImpactFeatureTotals = ImpactFeatureTotals(),
  /** Distinct producers (by ID or name) on the map features, or `null` when none is linked. */
  val producerCount: Long? = null,
  /** Indicators from global standard fields, grouped by goal. */
  val goals: List<ImpactGoalSection> = emptyList(),
  /** Indicators from the organization's own standard fields (survey and organization scope). */
  val ownIndicators: List<ImpactIndicator> = emptyList(),
  /** Organization-suggested indicators (platform-wide scope only), never added to main totals. */
  val suggestedIndicators: List<ImpactSuggestedGroup> = emptyList(),
  val activity: List<ImpactActivityItem> = emptyList(),
  val outcomes: ImpactOutcomeSummary = ImpactOutcomeSummary(),
  val attribution: ImpactAttributionSummary = ImpactAttributionSummary(),
  /** Platform-wide scope only. */
  val countries: List<ImpactCountryTotals> = emptyList(),
  /** Platform-wide scope only. */
  val cells: ImpactCellSummary? = null,
  /** Organizations that keep their data out of platform-wide numbers (platform-wide scope only). */
  val excludedOrganizationCount: Int = 0,
  val diagnostics: List<ImpactDiagnosticNote> = emptyList(),
  val hints: List<ImpactHint> = emptyList(),
) {
  /** Whether there's nothing to show yet. */
  val isEmpty: Boolean
    get() = features.mappedCount == 0L && goals.isEmpty() && ownIndicators.isEmpty()
}

/** Plain-language goal names. */
object ImpactGoals {
  private val LABELS =
    mapOf(
      "deforestation_free_supply_chains" to "Deforestation-free supply chains",
      "ecosystem_restoration" to "Ecosystem restoration",
      "effective_protected_areas" to "Protected areas",
      "iplc_rights_and_tenure" to "Indigenous and community land rights",
      "legal_timber" to "Legal timber",
    )

  /** Title used for standard fields that don't contribute to a goal. */
  const val OTHER_TITLE = "Other standard fields"

  /** Display name of goal [id] (e.g. `ecosystem_restoration` → "Ecosystem restoration"). */
  fun label(id: String): String =
    when {
      id.isBlank() -> OTHER_TITLE
      else ->
        LABELS[id]
          ?: id.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else "$it"
          }
    }
}

/** Number formatting for impact numbers, without locale-dependent APIs (common code). */
object ImpactFormat {
  /** [value] with thousands separators and up to [decimals] decimal places (trailing zeros cut). */
  fun number(value: Double, decimals: Int = 1): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (fraction.isEmpty()) "$sign$grouped" else "$sign$grouped.$fraction"
  }

  fun count(value: Long): String = number(value.toDouble(), 0)

  /** `"1 plot"` / `"3 plots"`. */
  fun count(value: Long, singular: String, plural: String = "${singular}s"): String =
    "${count(value)} ${if (value == 1L) singular else plural}"

  /** Hectares, e.g. `"5.9 ha"`, with two decimals under 10 ha. */
  fun hectares(value: Double): String = "${number(value, if (abs(value) < 10) 2 else 1)} ha"

  /** A share 0–1 as a whole percentage, e.g. `"67%"`. */
  fun percent(fraction: Double): String = "${(fraction * 100).roundToLong()}%"

  /** Display symbol of a UCUM unit code (`har` → `ha`, `m2` → `m²`). */
  fun unit(ucum: String): String =
    when (ucum) {
      "har" -> "ha"
      "m2" -> "m²"
      "km2" -> "km²"
      "m3" -> "m³"
      else -> ucum
    }
}
