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
package org.openforis.collectone.v2.app.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** Default key prefix attached to Open Foris Whisp enrichment properties on entities. */
const val WHISP_PROPERTY_PREFIX = "whisp_"

/** True when [this] property key was added by Open Foris Whisp enrichment. */
fun String.isWhispPropertyKey(): Boolean = startsWith(WHISP_PROPERTY_PREFIX)

/**
 * Organizer-defined properties of the entity, excluding both presentation keys and `whisp_*`
 * enrichment fields (which have their own Risk analysis presentation).
 */
val GeospatialEntityItem.regularDisplayProperties: List<Pair<String, String>>
  get() = displayProperties.filterNot { (key, _) -> key.isWhispPropertyKey() }

/** True when the entity carries any `whisp_*` enrichment properties. */
val GeospatialEntityItem.hasWhispAnalysis: Boolean
  get() = properties.keys.any { it.isWhispPropertyKey() }

/** Structured Open Foris Whisp risk analysis parsed from [GeospatialEntityItem.properties]. */
val GeospatialEntityItem.whispAnalysis: WhispAnalysis?
  get() = WhispAnalysis.parse(properties)

/** Deforestation risk verdict ordered from highest to lowest severity. */
enum class WhispRiskLevel {
  HIGH,
  MORE_INFO_NEEDED,
  LOW,
  UNKNOWN,
}

/**
 * One commodity/land-use risk verdict from Whisp (e.g. `risk_pcrop`, `risk_acrop`, `risk_timber`).
 */
data class WhispRiskVerdict(
  val key: String,
  val label: String,
  val level: WhispRiskLevel,
  val rawValue: String,
)

/** Plot-level context and administrative metadata returned by Whisp. */
data class WhispPlotContext(
  val country: String? = null,
  val producerCountry: String? = null,
  val adminLevel1: String? = null,
  val areaHa: String? = null,
  val geometryType: String? = null,
  val inWaterbody: Boolean? = null,
) {
  val isEmpty: Boolean
    get() =
      country == null &&
        producerCountry == null &&
        adminLevel1 == null &&
        areaHa == null &&
        geometryType == null &&
        inWaterbody == null
}

/** One decision-tree indicator (`Ind_01_*`..`Ind_17_*`) returned by Whisp. */
data class WhispIndicator(
  val key: String,
  val label: String,
  val rawValue: String,
  val isFlagged: Boolean?,
)

/** Broad thematic category for a yearly disturbance/loss/fire series. */
enum class WhispYearlyCategory {
  FOREST_LOSS,
  DEGRADATION_AND_ALERTS,
  FIRE,
  OTHER,
}

/** Multi-year disturbance, loss, or fire time series extracted from Whisp annual columns. */
data class WhispYearlySeries(
  val key: String,
  val label: String,
  val category: WhispYearlyCategory,
  val points: List<Pair<Int, Double>>,
) {
  val hasNonZeroValues: Boolean
    get() = points.any { (_, v) -> v > 0.0 }

  val maxValue: Double
    get() = points.maxOfOrNull { (_, v) -> v } ?: 0.0
}

/** Paired pre-2020 vs. post-2020 disturbance summary for a single Whisp monitoring source. */
data class WhispBeforeAfterComparison(
  val key: String,
  val label: String,
  val before2020: Double?,
  val after2020: Double?,
) {
  val hasNonZeroValues: Boolean
    get() = (before2020 ?: 0.0) > 0.0 || (after2020 ?: 0.0) > 0.0
}

/** Single scalar dataset metric (tree cover, commodity overlap, or other contextual layer). */
data class WhispMetric(
  val key: String,
  val label: String,
  val rawValue: String,
  val numericValue: Double?,
)

/**
 * Structured view of an entity's `whisp_*` properties, breaking noisy flat columns into logical
 * risk verdicts, plot context, multi-year disturbance charts, pre/post-2020 summaries, indicators,
 * baseline tree cover, commodities, and raw fields.
 */
data class WhispAnalysis(
  val riskVerdicts: List<WhispRiskVerdict>,
  val overallRisk: WhispRiskLevel,
  val unit: String,
  val context: WhispPlotContext,
  val yearlySeries: List<WhispYearlySeries>,
  val beforeAfterComparisons: List<WhispBeforeAfterComparison>,
  val indicators: List<WhispIndicator>,
  val treeCoverMetrics: List<WhispMetric>,
  val commodityMetrics: List<WhispMetric>,
  val otherMetrics: List<WhispMetric>,
  val rawEntries: List<Pair<String, String>>,
) {
  /** True when the underlying Whisp values are percentages (`0..100`). */
  val isPercentUnit: Boolean
    get() = unit == "%"

  /**
   * Series to plot on the yearly disturbance chart: all series with non-zero activity, or the first
   * few series as a zero-baseline reference when no disturbance was detected.
   */
  val chartSeries: List<WhispYearlySeries>
    get() {
      val nonZero = yearlySeries.filter { it.hasNonZeroValues }
      return if (nonZero.isNotEmpty()) nonZero else yearlySeries.take(4)
    }

  /** True when at least one yearly series has a non-zero disturbance/loss/fire measurement. */
  val hasYearlyActivity: Boolean
    get() = yearlySeries.any { it.hasNonZeroValues }

  /** Formats a numeric Whisp metric value with its unit (`%` or `ha`), trimming trailing zeros. */
  fun formatMetricValue(value: Double): String {
    val number = formatCompactDecimal(value)
    return when (unit) {
      "%" -> "$number%"
      "" -> number
      else -> "$number $unit"
    }
  }

  companion object {
    private val YEAR_SUFFIX_REGEX = Regex("""^(.+?)_((?:19|20)\d{2})$""")
    private val BEFORE_2020_REGEX = Regex("""^(.+?)_before_2020$""", RegexOption.IGNORE_CASE)
    private val AFTER_2020_REGEX = Regex("""^(.+?)_after_2020$""", RegexOption.IGNORE_CASE)
    private val INDICATOR_REGEX = Regex("""^Ind_(\d+)_(.+)$""", RegexOption.IGNORE_CASE)

    private val METADATA_KEYS =
      setOf(
        "plotid",
        "external_id",
        "area",
        "plot_area_ha",
        "geometry_type",
        "country",
        "producercountry",
        "admin_level_1",
        "centroid_lon",
        "centroid_lat",
        "unit",
        "in_waterbody",
        "water_flag",
        "geo",
      )

    /**
     * Parses [properties] into a [WhispAnalysis], or returns `null` when no `whisp_*` keys are
     * present.
     */
    fun parse(properties: Map<String, String>): WhispAnalysis? {
      val stripped =
        properties.entries
          .filter { (key, _) -> key.isWhispPropertyKey() }
          .map { (key, value) -> key.removePrefix(WHISP_PROPERTY_PREFIX) to value.trim() }
          .filter { (key, _) -> key.isNotEmpty() }
      if (stripped.isEmpty()) return null

      val byLower = stripped.associate { (k, v) -> k.lowercase() to v }
      val unit = normalizeUnit(byLower["unit"])

      val context =
        WhispPlotContext(
          country = byLower["country"]?.takeIf { it.isNotBlank() },
          producerCountry = byLower["producercountry"]?.takeIf { it.isNotBlank() },
          adminLevel1 = byLower["admin_level_1"]?.takeIf { it.isNotBlank() },
          areaHa = (byLower["area"] ?: byLower["plot_area_ha"])?.takeIf { it.isNotBlank() },
          geometryType = byLower["geometry_type"]?.takeIf { it.isNotBlank() },
          inWaterbody = parseBooleanOrNull(byLower["in_waterbody"] ?: byLower["water_flag"]),
        )

      val consumedKeys = mutableSetOf<String>()

      // 1. Risk verdicts (risk_pcrop, risk_acrop, risk_timber, etc.)
      val riskVerdicts = mutableListOf<WhispRiskVerdict>()
      stripped.forEach { (key, value) ->
        val lower = key.lowercase()
        if (lower.startsWith("risk_") && lower != "risk_inputs_unavailable") {
          consumedKeys += key
          riskVerdicts +=
            WhispRiskVerdict(
              key = key,
              label = riskLabel(key),
              level = parseRiskLevel(value),
              rawValue = value,
            )
        }
      }
      val overallRisk =
        riskVerdicts
          .map { it.level }
          .filter { it != WhispRiskLevel.UNKNOWN }
          .minByOrNull {
            it.ordinal
          } ?: WhispRiskLevel.UNKNOWN

      // 2. Decision-tree indicators (Ind_01_treecover, etc.)
      val indicators = mutableListOf<WhispIndicator>()
      stripped.forEach { (key, value) ->
        if (INDICATOR_REGEX.matches(key)) {
          consumedKeys += key
          indicators +=
            WhispIndicator(
              key = key,
              label = indicatorLabel(key),
              rawValue = value,
              isFlagged = parseIndicatorFlag(value),
            )
        }
      }

      // 3. Yearly time series (same prefix across >= 2 distinct years, excluding before/after)
      val yearCandidates = linkedMapOf<String, MutableList<Triple<String, Int, Double>>>()
      stripped.forEach { (key, value) ->
        if (key in consumedKeys || key.lowercase() in METADATA_KEYS) return@forEach
        if (BEFORE_2020_REGEX.matches(key) || AFTER_2020_REGEX.matches(key)) return@forEach
        val match = YEAR_SUFFIX_REGEX.matchEntire(key) ?: return@forEach
        val prefix = match.groupValues[1]
        val year = match.groupValues[2].toIntOrNull() ?: return@forEach
        val numeric = value.toDoubleOrNull() ?: return@forEach
        yearCandidates.getOrPut(prefix) { mutableListOf() } += Triple(key, year, numeric)
      }
      val yearlySeries =
        yearCandidates
          .filter { (_, entries) -> entries.map { it.second }.distinct().size >= 2 }
          .map { (prefix, entries) ->
            entries.forEach { (originalKey, _, _) -> consumedKeys += originalKey }
            val sortedPoints =
              entries.sortedBy { it.second }.map { (_, year, value) -> year to value }
            WhispYearlySeries(
              key = prefix,
              label = yearlySeriesLabel(prefix),
              category = yearlySeriesCategory(prefix),
              points = sortedPoints,
            )
          }
          .sortedWith(compareBy({ it.category.ordinal }, { it.label }))

      // 4. Before/After 2020 paired summaries
      val beforeMap = linkedMapOf<String, Pair<String, Double>>()
      val afterMap = linkedMapOf<String, Pair<String, Double>>()
      stripped.forEach { (key, value) ->
        if (key in consumedKeys) return@forEach
        val numeric = value.toDoubleOrNull() ?: return@forEach
        BEFORE_2020_REGEX.matchEntire(key)?.let { match ->
          beforeMap[match.groupValues[1]] = key to numeric
        }
        AFTER_2020_REGEX.matchEntire(key)?.let { match ->
          afterMap[match.groupValues[1]] = key to numeric
        }
      }
      val comparisonPrefixes = (beforeMap.keys + afterMap.keys).distinct()
      val beforeAfterComparisons = comparisonPrefixes.mapNotNull { prefix ->
        val beforeEntry = beforeMap[prefix]
        val afterEntry = afterMap[prefix]
        if (beforeEntry == null && afterEntry == null) return@mapNotNull null
        // Only group into Before/After comparison when at least one disturbance/loss/fire/gain
        // summary is present and it's paired or clearly a disturbance summary.
        if (beforeEntry != null || isDisturbanceKey(prefix)) {
          beforeEntry?.let { consumedKeys += it.first }
          afterEntry?.let { consumedKeys += it.first }
          WhispBeforeAfterComparison(
            key = prefix,
            label = yearlySeriesLabel(prefix),
            before2020 = beforeEntry?.second,
            after2020 = afterEntry?.second,
          )
        } else {
          null
        }
      }

      // 5. Remaining metrics classified into Tree Cover, Commodities, and Other
      val treeCoverMetrics = mutableListOf<WhispMetric>()
      val commodityMetrics = mutableListOf<WhispMetric>()
      val otherMetrics = mutableListOf<WhispMetric>()

      stripped.forEach { (key, value) ->
        if (key in consumedKeys || key.lowercase() in METADATA_KEYS) return@forEach
        if (value.isBlank()) return@forEach
        val metric =
          WhispMetric(
            key = key,
            label = humanizeWhispKey(key),
            rawValue = value,
            numericValue = value.toDoubleOrNull(),
          )
        when {
          isCommodityKey(key) -> commodityMetrics += metric
          isTreeCoverKey(key) -> treeCoverMetrics += metric
          else -> otherMetrics += metric
        }
      }

      return WhispAnalysis(
        riskVerdicts = riskVerdicts,
        overallRisk = overallRisk,
        unit = unit,
        context = context,
        yearlySeries = yearlySeries,
        beforeAfterComparisons = beforeAfterComparisons,
        indicators = indicators,
        treeCoverMetrics = treeCoverMetrics,
        commodityMetrics = commodityMetrics,
        otherMetrics = otherMetrics,
        rawEntries = stripped,
      )
    }

    internal fun parseRiskLevel(raw: String): WhispRiskLevel =
      when (raw.trim().lowercase().replace('_', ' ').replace('-', ' ')) {
        "high",
        "high risk" -> WhispRiskLevel.HIGH
        "more info needed",
        "more info",
        "more information needed",
        "medium",
        "medium risk" -> WhispRiskLevel.MORE_INFO_NEEDED
        "low",
        "low risk" -> WhispRiskLevel.LOW
        else -> WhispRiskLevel.UNKNOWN
      }

    private fun normalizeUnit(raw: String?): String =
      when (raw?.trim()?.lowercase()) {
        "percent",
        "percentage",
        "%" -> "%"
        "ha",
        "hectare",
        "hectares" -> "ha"
        null,
        "" -> ""
        else -> raw.trim()
      }

    private fun parseBooleanOrNull(raw: String?): Boolean? =
      when (raw?.trim()?.lowercase()) {
        "true",
        "yes",
        "1" -> true
        "false",
        "no",
        "0" -> false
        else -> null
      }

    private fun parseIndicatorFlag(raw: String): Boolean? =
      when (raw.trim().lowercase()) {
        "yes",
        "true",
        "1",
        "high" -> true
        "no",
        "false",
        "0",
        "low" -> false
        else -> null
      }

    private fun riskLabel(key: String): String =
      when (key.lowercase()) {
        "risk_pcrop" -> "Perennial crops"
        "risk_acrop" -> "Annual crops"
        "risk_timber" -> "Timber"
        else -> humanizeWhispKey(key.removePrefix("risk_").removePrefix("RISK_"))
      }

    private fun indicatorLabel(key: String): String {
      val stripped = INDICATOR_REGEX.matchEntire(key)?.groupValues?.get(2) ?: key
      return when (stripped.lowercase()) {
        "treecover" -> "Tree cover (2020)"
        "commodities" -> "Commodities"
        "disturbance_before_2020" -> "Disturbance before 2020"
        "disturbance_after_2020" -> "Disturbance after 2020"
        "primary_2020" -> "Primary forest (2020)"
        "nat_reg_forest_2020" -> "Naturally regenerating forest (2020)"
        "planted_plantations_2020" -> "Planted / plantations (2020)"
        "planted_plantations_after_2020" -> "Planted / plantations after 2020"
        "treecover_after_2020" -> "Tree cover after 2020"
        "agri_after_2020" -> "Agriculture after 2020"
        "logging_concession_before_2020" -> "Logging concession before 2020"
        "other_land_2020" -> "Other land (2020)"
        "other_land_gain_after_2020" -> "Other land gain after 2020"
        "planted_forest_2020" -> "Planted forest (2020)"
        "cropland_2020" -> "Cropland (2020)"
        "plantation_forest_2020" -> "Plantation forest (2020)"
        "plantation_forest_gain_after_2020" -> "Plantation forest gain after 2020"
        else -> humanizeWhispKey(stripped)
      }
    }

    private fun yearlySeriesLabel(prefix: String): String =
      when (prefix.lowercase()) {
        "gfc_loss_year",
        "gfc_loss" -> "GFC tree cover loss"
        "tmf_def" -> "TMF deforestation"
        "tmf_deg" -> "TMF degradation"
        "radd_year",
        "radd" -> "RADD radar alerts"
        "glad-l_year",
        "glad-l" -> "GLAD-L alerts"
        "glad-s2_year",
        "glad-s2" -> "GLAD-S2 alerts"
        "dist_year",
        "dist" -> "DIST alerts"
        "modis_fire" -> "MODIS fire"
        "esa_fire" -> "ESA burned area"
        else -> humanizeWhispKey(prefix.removeSuffix("_year"))
      }

    private fun yearlySeriesCategory(prefix: String): WhispYearlyCategory {
      val lower = prefix.lowercase()
      return when {
        lower.startsWith("gfc_loss") || lower.startsWith("tmf_def") || "deforestation" in lower ->
          WhispYearlyCategory.FOREST_LOSS
        lower.startsWith("tmf_deg") ||
          lower.startsWith("radd") ||
          lower.startsWith("glad") ||
          lower.startsWith("dist") -> WhispYearlyCategory.DEGRADATION_AND_ALERTS
        "fire" in lower -> WhispYearlyCategory.FIRE
        else -> WhispYearlyCategory.OTHER
      }
    }

    private fun isDisturbanceKey(prefix: String): Boolean {
      val lower = prefix.lowercase()
      return lower.startsWith("gfc_loss") ||
        lower.startsWith("tmf_def") ||
        lower.startsWith("tmf_deg") ||
        lower.startsWith("radd") ||
        lower.startsWith("glad-l") ||
        lower.startsWith("glad-s2") ||
        lower.startsWith("dist") ||
        "fire" in lower ||
        "prodes" in lower ||
        "deter" in lower ||
        "logging" in lower
    }

    private fun isCommodityKey(key: String): Boolean {
      val lower = key.lowercase()
      return "cocoa" in lower ||
        "coffee" in lower ||
        "oil_palm" in lower ||
        "palmoil" in lower ||
        "rubber" in lower ||
        "soy" in lower ||
        "crop" in lower ||
        "pasture" in lower ||
        "tmf_plant" in lower ||
        "perennial" in lower ||
        "annual" in lower ||
        "ag_gain" in lower ||
        "mining" in lower
    }

    private fun isTreeCoverKey(key: String): Boolean {
      val lower = key.lowercase()
      return lower.startsWith("eufo") ||
        "treecover" in lower ||
        "_tc_" in lower ||
        lower.endsWith("_tc") ||
        "forest" in lower ||
        "primary" in lower ||
        "tmf_undist" in lower ||
        "tmf_regrowth" in lower ||
        "nat_reg" in lower ||
        "silviculture" in lower ||
        "planted" in lower ||
        "plantation" in lower ||
        lower.startsWith("ifl_") ||
        lower.startsWith("sbtn_natural")
    }

    /** Formats a Whisp key like `"EUFO_2020"` or `"GFC_TC_2020"` into a readable label. */
    fun humanizeWhispKey(key: String): String {
      val cleaned = key.removePrefix(WHISP_PROPERTY_PREFIX).trim()
      if (cleaned.isEmpty()) return key
      return cleaned
        .split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { token ->
          when {
            token.all { it.isUpperCase() || it.isDigit() || it == '-' } && token.length <= 6 ->
              token
            token.equals("fdap", ignoreCase = true) -> "FDaP"
            token.equals("ha", ignoreCase = true) -> "ha"
            else -> token.replaceFirstChar { it.uppercaseChar() }
          }
        }
    }

    /** Formats [value] cleanly without JVM-only formatters (e.g. `0`, `1.2`, `12.5`). */
    fun formatCompactDecimal(value: Double): String {
      if (!value.isFinite()) return "0"
      val rounded = (value * 10.0).roundToInt() / 10.0
      return if (abs(rounded - rounded.toInt()) < 1e-6) {
        rounded.toInt().toString()
      } else {
        rounded.toString()
      }
    }
  }
}
