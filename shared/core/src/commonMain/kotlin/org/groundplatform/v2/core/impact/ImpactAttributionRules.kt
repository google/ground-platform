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
 * Events, outcomes, and attribution (docs/technical/backend/impact-aggregation.md, "Events,
 * Outcomes, and Attribution").
 */

/** Outcomes that are official or regulatory submissions (score 5). */
private val OFFICIAL_OUTCOMES =
  setOf(
    ImpactOutcomeKind.SUBMITTED_EUDR_DDS,
    ImpactOutcomeKind.REPORTED_FERM,
    ImpactOutcomeKind.PROTECTED_AREA_MANAGEMENT,
  )

/** Outcomes confirming use by a partner or in a partner system (score 4). */
private val PARTNER_OUTCOMES =
  setOf(
    ImpactOutcomeKind.SHARED_WITH_BUYERS,
    ImpactOutcomeKind.LAND_TITLING,
    ImpactOutcomeKind.TRAINED_OR_VALIDATED_MODEL,
  )

/**
 * The attribution basis of [survey]; the first condition that applies wins:
 * 1. [AttributionBasis.OFFICIAL_SUBMISSION]: the outcome includes `SUBMITTED_EUDR_DDS`,
 *    `REPORTED_FERM`, or `PROTECTED_AREA_MANAGEMENT`.
 * 2. [AttributionBasis.PARTNER_USE]: a `PARTNER_PUSH` event, or the outcome includes
 *    `SHARED_WITH_BUYERS`, `LAND_TITLING`, or `TRAINED_OR_VALIDATED_MODEL`.
 * 3. [AttributionBasis.PURPOSE_EXPORT]: an `EXPORT` event whose export profile one of the survey's
 *    purposes enables.
 * 4. [AttributionBasis.EXPLORATORY]: no purposes and fewer than
 *    [ImpactConfig.exploratoryMaxFeatures] features.
 * 5. [AttributionBasis.DATA_COLLECTED]: at least one feature or submission.
 * 6. [AttributionBasis.EXPLORATORY] otherwise (no data yet).
 */
internal fun attributionBasis(survey: ImpactSurvey, config: ImpactConfig): AttributionBasis {
  val outcome = survey.outcome.orEmpty()
  if (outcome.any { it in OFFICIAL_OUTCOMES }) return AttributionBasis.OFFICIAL_SUBMISSION
  if (
    survey.events.any { it.kind == ImpactEventKind.PARTNER_PUSH } ||
      outcome.any { it in PARTNER_OUTCOMES }
  ) {
    return AttributionBasis.PARTNER_USE
  }
  val enabledProfiles = survey.purposes.flatMapTo(HashSet()) { it.exportProfileIds }
  if (
    survey.events.any {
      it.kind == ImpactEventKind.EXPORT &&
        it.exportProfileId != null &&
        it.exportProfileId in enabledProfiles
    }
  ) {
    return AttributionBasis.PURPOSE_EXPORT
  }
  if (survey.purposes.isEmpty() && survey.features.size < config.exploratoryMaxFeatures) {
    return AttributionBasis.EXPLORATORY
  }
  if (survey.features.isNotEmpty() || survey.submissionCount > 0) {
    return AttributionBasis.DATA_COLLECTED
  }
  return AttributionBasis.EXPLORATORY
}

/**
 * Outcome keys [survey] counts toward: [OUTCOME_UNANSWERED] for a closed survey nobody answered;
 * `NOT_YET` for an empty answer or one with only `NOT_YET`; otherwise each answered kind (`NOT_YET`
 * is ignored when mixed with others). An open survey counts only an answer it already has.
 */
internal fun outcomeKeys(survey: ImpactSurvey): List<String> {
  val outcome =
    survey.outcome ?: return if (survey.isClosed) listOf(OUTCOME_UNANSWERED) else emptyList()
  val used = ImpactOutcomeKind.entries.filter { it != ImpactOutcomeKind.NOT_YET && it in outcome }
  return if (used.isEmpty()) listOf(ImpactOutcomeKind.NOT_YET.name) else used.map { it.name }
}

/** Order of OUTCOME rows: [ImpactOutcomeKind] order, then [OUTCOME_UNANSWERED]. */
private val OUTCOME_ORDER = ImpactOutcomeKind.entries.map { it.name } + OUTCOME_UNANSWERED

/**
 * EVENT rows of [surveys], one per event kind with at least one event (in [ImpactEventKind] order):
 * `value` is the event count, `feature_count` and `area_ha` the sums recorded on the events
 * (already deduplicated at recording time, so not deduplicated again).
 */
internal fun eventRows(
  scopeType: ImpactScopeType,
  scopeId: String,
  surveys: List<SurveyContext>,
): List<ImpactAggregateRow> {
  val kinds = ImpactEventKind.entries
  val counts = LongArray(kinds.size)
  val features = LongArray(kinds.size)
  val areas = DoubleArray(kinds.size)
  for (survey in surveys) {
    for (event in survey.survey.events) {
      val i = event.kind.ordinal
      counts[i]++
      features[i] += event.featureCount
      areas[i] += event.areaHa
    }
  }
  return kinds.indices
    .filter { counts[it] > 0 }
    .map {
      ImpactAggregateRow(
        scopeType = scopeType,
        scopeId = scopeId,
        metric = ImpactMetric.EVENT,
        eventKind = kinds[it],
        featureCount = features[it],
        areaHa = areas[it],
        value = counts[it].toDouble(),
      )
    }
}

/** OUTCOME rows of [surveys]: `value` is the number of surveys per [outcomeKeys] key. */
internal fun outcomeRows(
  scopeType: ImpactScopeType,
  scopeId: String,
  surveys: List<SurveyContext>,
): List<ImpactAggregateRow> {
  val counts = HashMap<String, Int>()
  for (survey in surveys) {
    for (key in outcomeKeys(survey.survey)) counts[key] = (counts[key] ?: 0) + 1
  }
  return OUTCOME_ORDER.mapNotNull { key ->
    counts[key]?.let {
      ImpactAggregateRow(
        scopeType = scopeType,
        scopeId = scopeId,
        metric = ImpactMetric.OUTCOME,
        outcomeKind = key,
        value = it.toDouble(),
      )
    }
  }
}

/** ATTRIBUTION rows: `value` is the number of [attributions] per score, by ascending score. */
internal fun attributionRows(
  scopeType: ImpactScopeType,
  scopeId: String,
  attributions: List<ImpactAttribution>,
): List<ImpactAggregateRow> =
  attributions
    .groupingBy { it.score }
    .eachCount()
    .entries
    .sortedBy { it.key }
    .map { (score, count) ->
      ImpactAggregateRow(
        scopeType = scopeType,
        scopeId = scopeId,
        metric = ImpactMetric.ATTRIBUTION,
        attributionScore = score,
        value = count.toDouble(),
      )
    }
