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
package org.openforis.collectone.v2.web.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind

/**
 * Display text of impact numbers: either [Plain] text (user data such as a concept label, or a
 * formatted number) or a semantic [Message] or [Plural] with arguments, localized by the
 * presentation layer (`ImpactText.resolve()` in the UI, `load()` for PDF summaries). Arguments that
 * are themselves [ImpactText] are localized first.
 */
sealed interface ImpactText {
  /** Text shown as is. */
  data class Plain(val value: String) : ImpactText

  /** The message [key] formatted with positional [args]. */
  data class Message(val key: ImpactMessage, val args: List<Any> = emptyList()) : ImpactText

  /** The message [key] worded for [quantity], formatted with positional [args]. */
  data class Plural(
    val key: ImpactCountedMessage,
    val quantity: Long,
    val args: List<Any> = emptyList(),
  ) : ImpactText

  /**
   * A length or area: the UI writes its number like other impact numbers ([ImpactFormat.number])
   * and names its unit in the user's language.
   */
  data class Measured(val measurement: Measurement) : ImpactText

  /** Whether this is [Plain] blank text (nothing to show). */
  val isBlank: Boolean
    get() = this is Plain && value.isBlank()

  companion object {
    val EMPTY: ImpactText = Plain("")

    fun of(key: ImpactMessage, vararg args: Any): ImpactText = Message(key, args.toList())

    /** [key] for [count], with the count (formatted with separators) as its first argument. */
    fun count(key: ImpactCountedMessage, count: Long): ImpactText =
      Plural(key, count, listOf(ImpactFormat.count(count)))
  }
}

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
  val valueText: ImpactText,
  /** Share of the indicator's total, 0–1, for the bar length. */
  val fraction: Float,
)

/** One number on an impact card, from a linked standard field (concept). */
data class ImpactIndicator(
  val conceptId: String,
  val label: String,
  /** The headline number with its unit, e.g. `"3 plots"` or `"5,820 kg"`. */
  val valueText: ImpactText,
  /** What the number covers, e.g. `"Total of 3 plots"`. */
  val detail: ImpactText = ImpactText.EMPTY,
  val breakdown: List<ImpactBreakdownItem> = emptyList(),
)

/** Indicators that contribute to one goal, e.g. "Deforestation-free supply chains". */
data class ImpactGoalSection(
  /** Goal ID, or blank for standard fields without a goal. */
  val goalId: String,
  val title: ImpactText,
  val indicators: List<ImpactIndicator>,
)

/** Another organization's own indicators under a goal they suggest it counts toward. */
data class ImpactSuggestedGroup(
  val goalId: String,
  val goalTitle: ImpactText,
  val organizationId: String,
  val organizationName: String,
  val indicators: List<ImpactIndicator>,
)

/** One kind of "data was used" activity, e.g. 4 exports covering 3 plots. */
data class ImpactActivityItem(
  val type: ImpactEventType,
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
enum class ImpactAttributionLevel(val score: Int) {
  OFFICIAL_SUBMISSION(5),
  PARTNER_USE(4),
  PURPOSE_EXPORT(3),
  DATA_COLLECTED(2),
  EXPLORATORY(1),
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
data class ImpactDiagnosticNote(val message: ImpactText)

/** What to do to get numbers, when a summary has none yet. */
enum class ImpactHint {
  COLLECT_DATA,
  PICK_PURPOSE,
  LINK_FIELDS,
}

/**
 * Impact numbers of one survey, organization, or the whole platform, grouped by goal and written in
 * plain language. Built from the shared aggregation job
 * (`org.openforis.collectone.v2.core.impact`), so the dashboards, the PDF summary, and the backend
 * agree.
 *
 * A summary is either the full view its members see, or the **published** view ([isPublished])
 * anyone signed in to Collect One sees on the Impact page: anonymous totals from `AGGREGATE_PUBLIC`
 * standard fields only, with no diagnostics or setup hints, and nothing at all below the
 * publication threshold ([isBelowPublicationThreshold]).
 */
data class ImpactSummary(
  val scope: ImpactSummaryScope,
  val scopeId: String,
  val title: String,
  val features: ImpactFeatureTotals = ImpactFeatureTotals(),
  /** Distinct producers (by ID or name) on the map features, or `null` when none is linked. */
  val producerCount: Long? = null,
  /** Standard field [producerCount] counts, or `null` when none is linked. */
  val producerConceptId: String? = null,
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
  /** Whether this is the published view anyone signed in can see, not the members' full view. */
  val isPublished: Boolean = false,
  /**
   * Published view only: too few map features to publish anything yet, so every number is left out
   * (see `ImpactConfig.organizationPublicMinFeatures`).
   */
  val isBelowPublicationThreshold: Boolean = false,
) {
  /** Whether there's nothing to show yet. */
  val isEmpty: Boolean
    get() = features.mappedCount == 0L && goals.isEmpty() && ownIndicators.isEmpty()
}

/** Plain-language goal names. */
object ImpactGoals {
  /** Known goals, in the (English) alphabetical order of their names. */
  private val LABELS =
    linkedMapOf(
      "deforestation_free_supply_chains" to ImpactMessage.GOAL_DEFORESTATION_FREE_SUPPLY_CHAINS,
      "ecosystem_restoration" to ImpactMessage.GOAL_ECOSYSTEM_RESTORATION,
      "forest_carbon_and_conservation" to ImpactMessage.GOAL_FOREST_CARBON_AND_CONSERVATION,
      "iplc_rights_and_tenure" to ImpactMessage.GOAL_IPLC_RIGHTS_AND_TENURE,
      "legal_timber" to ImpactMessage.GOAL_LEGAL_TIMBER,
      "open_earth_data" to ImpactMessage.GOAL_OPEN_EARTH_DATA,
      "effective_protected_areas" to ImpactMessage.GOAL_EFFECTIVE_PROTECTED_AREAS,
      "smallholder_livelihoods" to ImpactMessage.GOAL_SMALLHOLDER_LIVELIHOODS,
    )

  private val ORDER = LABELS.keys.toList()

  /** Display name of goal [id] (e.g. `ecosystem_restoration` → "Ecosystem restoration"). */
  fun label(id: String): ImpactText =
    when {
      id.isBlank() -> ImpactText.of(ImpactMessage.OTHER_STANDARD_FIELDS)
      else ->
        LABELS[id]?.let { ImpactText.of(it) }
          ?: ImpactText.Plain(
            id.replace('_', ' ').replaceFirstChar {
              if (it.isLowerCase()) it.titlecase() else "$it"
            }
          )
    }

  /**
   * Orders goals by name: known goals first, then others by ID, standard fields without one last.
   */
  val ORDER_COMPARATOR: Comparator<String> =
    compareBy<String>(
      { it.isBlank() },
      { ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } },
      { it },
    )
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

  /** An area of [value] hectares, rounded to two decimal places under 10 ha and one from there. */
  fun hectares(value: Double): Measurement {
    val decimals = if (abs(value) < 10) 2 else 1
    var scale = 1.0
    repeat(decimals) { scale *= 10 }
    return Measurement(round(value * scale) / scale, MeasurementUnit.HECTARE, decimals)
  }

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
