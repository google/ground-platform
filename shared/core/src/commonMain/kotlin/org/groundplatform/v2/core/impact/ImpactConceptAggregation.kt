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
package org.groundplatform.v2.core.impact

/*
 * Concept aggregation (docs/technical/backend/impact-aggregation.md, "Concept Aggregation"): value
 * normalization and the per-rule rows of one scope.
 */

/**
 * Decimal numbers as captured by forms: XML Schema `xsd:decimal` / `xsd:double` lexical forms (XML
 * Schema 1.1 Part 2, sections 3.3.3 and 3.3.5) without the special values `INF` and `NaN`. Parsed
 * with an explicit pattern because platform parsers differ (the JVM also accepts hex and `f`/`d`
 * suffixes), and aggregates must match across platforms.
 */
private val DECIMAL = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?""")

/** Parses [text] as a finite decimal number, or returns `null`. */
internal fun parseImpactNumber(text: String): Double? {
  val trimmed = text.trim()
  if (!DECIMAL.matches(trimmed)) return null
  return trimmed.toDouble().takeIf { it.isFinite() }
}

/**
 * Normalizes [value] of [concept] (a SUM or MEAN concept) to the concept's unit. Blank values are
 * missing, not errors. A value without a unit (`null` or blank) is already in the concept's unit.
 * Unparseable or unconvertible values are reported to [diagnostics] and skipped.
 */
internal fun normalizeNumber(
  surveyId: String,
  entityId: String,
  concept: ImpactConcept,
  value: ImpactValue,
  diagnostics: MutableList<UnnormalizableValue>,
): Double? {
  if (value.text.isBlank()) return null
  fun skip(reason: UnnormalizableReason): Double? {
    diagnostics +=
      UnnormalizableValue(surveyId, concept.id, entityId, value.text, value.unit, reason)
    return null
  }
  val number = parseImpactNumber(value.text) ?: return skip(UnnormalizableReason.NOT_A_NUMBER)
  val unit = value.unit?.trim()
  if (unit.isNullOrEmpty()) return number
  return ImpactUnits.convert(number, unit, concept.unit.trim())
    ?: skip(UnnormalizableReason.UNSUPPORTED_UNIT)
}

private val WHITESPACE = Regex("""\s+""")

/** Distinct codes of a select-one or select-multiple value (space-separated, XLSForm style). */
internal fun codesOf(value: ImpactValue?): List<String> =
  value?.text?.trim()?.takeIf { it.isNotEmpty() }?.split(WHITESPACE)?.distinct().orEmpty()

/** The FEATURES row of a scope: deduplicated features and their area. */
internal fun featuresRow(
  scopeType: ImpactScopeType,
  scopeId: String,
  winners: List<FeatureRef>,
): ImpactAggregateRow =
  ImpactAggregateRow(
    scopeType = scopeType,
    scopeId = scopeId,
    metric = ImpactMetric.FEATURES,
    featureCount = winners.size.toLong(),
    areaHa = winners.sumOf { it.areaHa },
  )

/**
 * Rows of [concept] over the deduplicated [winners] of one scope, per its aggregation rule.
 * `feature_count` and `area_ha` always describe the features counted by the row; `value` holds the
 * share, sum, or mean.
 * * `COUNT_DISTINCT_FEATURES`: one row; features with a non-blank value.
 * * `COUNT_BY_CODE`: one row per code (ordered by code); a feature counts once per code it has.
 * * `SHARE_BY_CODE`: as above, with `value` = the code's features / features with any code.
 * * `SUM`, `MEAN`: one row; `value` is the sum or mean (0 when no feature has a value) of values
 *   normalized to the concept's unit, over the features with a value.
 * * `NONE`: no rows.
 */
internal fun conceptRows(
  scopeType: ImpactScopeType,
  scopeId: String,
  winners: List<FeatureRef>,
  concept: ImpactConcept,
): List<ImpactAggregateRow> {
  fun row(code: String = "", featureCount: Long, areaHa: Double, value: Double = 0.0) =
    ImpactAggregateRow(
      scopeType = scopeType,
      scopeId = scopeId,
      metric = ImpactMetric.CONCEPT,
      conceptId = concept.id,
      code = code,
      featureCount = featureCount,
      areaHa = areaHa,
      value = value,
    )
  return when (concept.aggregation) {
    ImpactAggregation.COUNT_DISTINCT_FEATURES -> {
      var count = 0L
      var area = 0.0
      for (winner in winners) {
        if (winner.value(concept)?.text?.isNotBlank() == true) {
          count++
          area += winner.areaHa
        }
      }
      listOf(row(featureCount = count, areaHa = area))
    }
    ImpactAggregation.COUNT_BY_CODE,
    ImpactAggregation.SHARE_BY_CODE -> {
      val counts = HashMap<String, Long>()
      val areas = HashMap<String, Double>()
      var withAnyCode = 0L
      for (winner in winners) {
        val codes = codesOf(winner.value(concept))
        if (codes.isEmpty()) continue
        withAnyCode++
        for (code in codes) {
          counts[code] = (counts[code] ?: 0L) + 1
          areas[code] = (areas[code] ?: 0.0) + winner.areaHa
        }
      }
      val share = concept.aggregation == ImpactAggregation.SHARE_BY_CODE
      counts.keys.sorted().map { code ->
        val count = counts.getValue(code)
        row(
          code = code,
          featureCount = count,
          areaHa = areas.getValue(code),
          value = if (share) count.toDouble() / withAnyCode else 0.0,
        )
      }
    }
    ImpactAggregation.SUM,
    ImpactAggregation.MEAN -> {
      var count = 0L
      var area = 0.0
      var sum = 0.0
      for (winner in winners) {
        val number = winner.numbers[concept.id] ?: continue
        count++
        area += winner.areaHa
        sum += number
      }
      val value =
        when {
          concept.aggregation == ImpactAggregation.SUM -> sum
          count > 0 -> sum / count
          else -> 0.0
        }
      listOf(row(featureCount = count, areaHa = area, value = value))
    }
    ImpactAggregation.NONE -> emptyList()
  }
}

/**
 * The DISTINCT_VALUES row of [concept]: `value` is the number of distinct trimmed, non-blank values
 * (compared case-insensitively) among [winners]; `feature_count` and `area_ha` cover the features
 * with a value. Computed regardless of the concept's aggregation rule.
 */
internal fun distinctValuesRow(
  scopeType: ImpactScopeType,
  scopeId: String,
  winners: List<FeatureRef>,
  concept: ImpactConcept,
): ImpactAggregateRow {
  val distinct = HashSet<String>()
  var count = 0L
  var area = 0.0
  for (winner in winners) {
    val text = winner.value(concept)?.text?.trim()
    if (text.isNullOrEmpty()) continue
    count++
    area += winner.areaHa
    distinct += text.lowercase()
  }
  return ImpactAggregateRow(
    scopeType = scopeType,
    scopeId = scopeId,
    metric = ImpactMetric.DISTINCT_VALUES,
    conceptId = concept.id,
    featureCount = count,
    areaHa = area,
    value = distinct.size.toDouble(),
  )
}

/**
 * The [ImpactConfig.distinctValueConceptIds] concept a scope reports: the first one in [concepts]
 * (the scope's concepts after privacy rules), or `null`.
 */
internal fun distinctValueConcept(
  config: ImpactConfig,
  concepts: Map<String, ImpactConcept>,
): ImpactConcept? = config.distinctValueConceptIds.firstNotNullOfOrNull { concepts[it] }
