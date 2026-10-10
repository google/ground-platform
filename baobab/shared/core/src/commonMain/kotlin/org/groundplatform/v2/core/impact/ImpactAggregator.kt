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

/**
 * The impact aggregation job (docs/technical/backend/impact-aggregation.md): GeoID deduplication,
 * concept aggregation, scope rules, events and outcomes, the attribution score, and thresholded
 * country and grid cell aggregates. Pure and deterministic: the same input always gives the same
 * rows in the same order.
 *
 * **Concepts.** Each survey's `linkedConceptIds` resolve against its library: global concepts plus
 * its organization's. Unknown, deprecated, and other organizations' concepts are skipped and
 * reported once per (survey, concept) in [ImpactDiagnostics.skippedLinks]. A feature's values for
 * concepts its survey doesn't link are ignored.
 *
 * **Deduplication.** Within each scope, features sharing a trimmed GeoID count once; features
 * without one count individually. The winner is the most recently updated duplicate (ties: smallest
 * entity ID, then survey ID) and alone provides area and values; values are never merged across
 * duplicates. Area is the geodesic WGS 84 area (polygon exterior minus holes), falling back to
 * [ImpactFeature.areaHa] when the geometry is missing or has no area.
 *
 * **Scopes** (rows are ordered by scope in this order, then by metric in [ImpactMetric] order):
 * * `SURVEY` (scope ID = survey ID): the survey's features and all its resolved concepts.
 * * `ORGANIZATION` (organization ID): all the organization's surveys, deduplicated together; the
 *   union of their resolved concepts. Personal surveys have no organization scope.
 * * `COUNTRY` (ISO code): global-scope winners grouped by their country (the feature's, else its
 *   organization's); global non-`SENSITIVE` concepts linked by the surveys of those winners. Below
 *   [ImpactConfig.countryMinFeatures] only a suppressed FEATURES row with zeroed counts is emitted.
 * * `GLOBAL` (empty scope ID): surveys of organizations that didn't opt out (personal surveys
 *   included), deduplicated together; main rows for global non-`SENSITIVE` concepts; plus
 *   organization-suggested rows (one set per suggested goal, computed over that organization's own
 *   deduplicated features) for non-`SENSITIVE` organization concepts with goals. Organization
 *   concepts without goals are excluded.
 * * `CELL` (S2 token): FEATURES rows only; see [cellAggregation] for thresholds and merging.
 *
 * **Metrics per scope:** a FEATURES row (all scopes); CONCEPT rows (see [conceptRows]);
 * DISTINCT_VALUES for the first [ImpactConfig.distinctValueConceptIds] concept in the scope's
 * concepts after privacy rules; EVENT, OUTCOME, and ATTRIBUTION rows at survey, organization, and
 * global scope (global attribution counts only surveys with a global purpose).
 *
 * Runs in O(n log n) in the number of features, so clients can recompute on every change.
 */
object ImpactAggregator {
  fun run(input: ImpactInput, config: ImpactConfig = ImpactConfig()): ImpactRunResult {
    val conceptsById = firstById(input.concepts) { it.id }
    val organizationsById = firstById(input.organizations) { it.id }
    val skippedLinks = mutableListOf<SkippedLink>()
    val unnormalizable = mutableListOf<UnnormalizableValue>()

    // Snapshot and scope: resolve links and precompute area and normalized numbers per feature.
    val surveys =
      input.surveys
        .sortedBy { it.id }
        .map { survey ->
          SurveyContext(
            survey = survey,
            organization = survey.organizationId?.let { organizationsById[it] },
            concepts = resolveLinks(survey, conceptsById, skippedLinks),
          )
        }
    val featuresBySurvey: List<List<FeatureRef>> = surveys.map { context ->
      context.survey.features.map { featureRef(context, it, unnormalizable) }
    }
    val attributions = surveys.associate {
      it.id to ImpactAttribution(it.id, attributionBasis(it.survey, config))
    }

    val rows = mutableListOf<ImpactAggregateRow>()

    // Survey scope: everything the survey links, including SENSITIVE and organization concepts.
    for ((i, survey) in surveys.withIndex()) {
      val winners = dedup(featuresBySurvey[i])
      rows +=
        scopeRows(
          ImpactScopeType.SURVEY,
          survey.id,
          winners,
          survey.concepts,
          config,
          listOf(survey),
          listOf(attributions.getValue(survey.id)),
        )
    }

    // Organization scope: all the organization's surveys, deduplicated together.
    val surveyIndicesByOrganization =
      surveys.indices
        .filter { surveys[it].survey.organizationId != null }
        .groupBy { surveys[it].survey.organizationId!! }
    val organizationWinners = HashMap<String, List<FeatureRef>>()
    val organizationConcepts = HashMap<String, Map<String, ImpactConcept>>()
    for (organizationId in surveyIndicesByOrganization.keys.sorted()) {
      val indices = surveyIndicesByOrganization.getValue(organizationId)
      val organizationSurveys = indices.map { surveys[it] }
      val winners = dedup(indices.flatMap { featuresBySurvey[it] })
      val concepts = unionConcepts(organizationSurveys)
      organizationWinners[organizationId] = winners
      organizationConcepts[organizationId] = concepts
      rows +=
        scopeRows(
          ImpactScopeType.ORGANIZATION,
          organizationId,
          winners,
          concepts,
          config,
          organizationSurveys,
          organizationSurveys.map { attributions.getValue(it.id) },
        )
    }

    // Platform-wide scopes: opted-out organizations never contribute.
    val eligible = surveys.indices.filter { surveys[it].isPlatformEligible }
    val eligibleSurveys = eligible.map { surveys[it] }
    val globalWinners = dedup(eligible.flatMap { featuresBySurvey[it] })

    // Country scope: global winners grouped by country, so each plot counts in one country.
    val winnersByCountry = HashMap<String, MutableList<FeatureRef>>()
    for (winner in globalWinners) {
      val country = countryOf(winner) ?: continue
      winnersByCountry.getOrPut(country) { mutableListOf() } += winner
    }
    var suppressedCountryCount = 0
    for (country in winnersByCountry.keys.sorted()) {
      val winners = winnersByCountry.getValue(country)
      if (winners.size < config.countryMinFeatures) {
        suppressedCountryCount++
        rows +=
          ImpactAggregateRow(
            scopeType = ImpactScopeType.COUNTRY,
            scopeId = country,
            metric = ImpactMetric.FEATURES,
            suppressed = true,
          )
        continue
      }
      val contributing = winners.map { it.survey }.distinctBy { it.id }
      rows +=
        scopeRows(
          ImpactScopeType.COUNTRY,
          country,
          winners,
          platformConcepts(unionConcepts(contributing)),
          config,
          surveys = null,
          attributions = null,
        )
    }

    // Global scope.
    val globalScope = ImpactScopeType.GLOBAL
    val globalRows =
      scopeRows(
        globalScope,
        "",
        globalWinners,
        platformConcepts(unionConcepts(eligibleSurveys)),
        config,
        eligibleSurveys,
        eligibleSurveys
          .filter { survey -> survey.survey.purposes.any { it.isGlobal } }
          .map { attributions.getValue(it.id) },
        suggested = organizationSuggestedRows(organizationWinners, organizationConcepts, surveys),
      )
    rows += globalRows

    // Cell scope.
    val cells = cellAggregation(globalWinners, config)
    rows += cells.rows

    return ImpactRunResult(
      rows = rows,
      diagnostics =
        ImpactDiagnostics(
          skippedLinks = skippedLinks,
          unnormalizableValues =
            unnormalizable.sortedWith(
              compareBy({ it.surveyId }, { it.entityId }, { it.conceptId })
            ),
          suppressedCellCount = cells.suppressedCellCount,
          suppressedCellFeatureCount = cells.suppressedCellFeatureCount,
          suppressedCountryCount = suppressedCountryCount,
        ),
      attributions = attributions,
    )
  }

  private fun featureRef(
    survey: SurveyContext,
    feature: ImpactFeature,
    unnormalizable: MutableList<UnnormalizableValue>,
  ): FeatureRef {
    val numbers = HashMap<String, Double>()
    for (concept in survey.concepts.values) {
      if (
        concept.aggregation != ImpactAggregation.SUM &&
          concept.aggregation != ImpactAggregation.MEAN
      ) {
        continue
      }
      val value = feature.values[concept.id] ?: continue
      normalizeNumber(survey.id, feature.entityId, concept, value, unnormalizable)?.let {
        numbers[concept.id] = it
      }
    }
    return FeatureRef(
      survey = survey,
      feature = feature,
      dedupKey = dedupKey(survey.id, feature),
      areaHa = featureAreaHa(feature),
      numbers = numbers,
    )
  }

  /**
   * Rows of one scope in metric order: FEATURES, main CONCEPT rows (by concept ID, then code),
   * [suggested] rows, DISTINCT_VALUES, then EVENT, OUTCOME, and ATTRIBUTION rows when [surveys] and
   * [attributions] are given.
   */
  private fun scopeRows(
    scopeType: ImpactScopeType,
    scopeId: String,
    winners: List<FeatureRef>,
    concepts: Map<String, ImpactConcept>,
    config: ImpactConfig,
    surveys: List<SurveyContext>?,
    attributions: List<ImpactAttribution>?,
    suggested: List<ImpactAggregateRow> = emptyList(),
  ): List<ImpactAggregateRow> = buildList {
    add(featuresRow(scopeType, scopeId, winners))
    for (id in concepts.keys.sorted()) {
      addAll(conceptRows(scopeType, scopeId, winners, concepts.getValue(id)))
    }
    addAll(suggested)
    distinctValueConcept(config, concepts)?.let {
      add(distinctValuesRow(scopeType, scopeId, winners, it))
    }
    if (surveys != null) {
      addAll(eventRows(scopeType, scopeId, surveys))
      addAll(outcomeRows(scopeType, scopeId, surveys))
    }
    if (attributions != null) addAll(attributionRows(scopeType, scopeId, attributions))
  }

  /**
   * Organization-suggested rows at global scope: for each organization that didn't opt out (by ID),
   * each of its non-`SENSITIVE` concepts with suggested goals (by ID), and each goal (in listed
   * order), the concept's rows over the organization's own deduplicated features.
   */
  private fun organizationSuggestedRows(
    organizationWinners: Map<String, List<FeatureRef>>,
    organizationConcepts: Map<String, Map<String, ImpactConcept>>,
    surveys: List<SurveyContext>,
  ): List<ImpactAggregateRow> {
    val optedOut =
      surveys.filter { !it.isPlatformEligible }.mapNotNullTo(HashSet()) { it.survey.organizationId }
    val rows = mutableListOf<ImpactAggregateRow>()
    for (organizationId in organizationWinners.keys.sorted()) {
      if (organizationId in optedOut) continue
      val winners = organizationWinners.getValue(organizationId)
      val concepts = organizationConcepts.getValue(organizationId)
      for (id in concepts.keys.sorted()) {
        val concept = concepts.getValue(id)
        if (concept.isGlobal || concept.privacy == ImpactPrivacy.SENSITIVE) continue
        for (goal in concept.goals.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
          conceptRows(ImpactScopeType.GLOBAL, "", winners, concept).mapTo(rows) {
            it.copy(
              organizationSuggested = true,
              organizationId = concept.ownerOrganizationId,
              goal = goal,
            )
          }
        }
      }
    }
    return rows
  }

  /** Union of [surveys]' resolved concepts. */
  private fun unionConcepts(surveys: List<SurveyContext>): Map<String, ImpactConcept> {
    val union = HashMap<String, ImpactConcept>()
    for (survey in surveys) union.putAll(survey.concepts)
    return union
  }

  /** Concepts allowed in main platform-wide rows: global and not `SENSITIVE`. */
  private fun platformConcepts(concepts: Map<String, ImpactConcept>): Map<String, ImpactConcept> =
    concepts.filterValues {
      it.isGlobal && it.privacy != ImpactPrivacy.SENSITIVE
    }

  private fun <T> firstById(items: List<T>, id: (T) -> String): Map<String, T> {
    val byId = HashMap<String, T>()
    for (item in items) {
      val key = id(item)
      if (key !in byId) byId[key] = item
    }
    return byId
  }
}

/**
 * UCUM unit normalization for the units seed concepts use (The Unified Code for Units of Measure,
 * case-sensitive codes, https://ucum.org/ucum).
 */
object ImpactUnits {
  /**
   * Size of each supported case-sensitive UCUM code in its dimension's smallest supported unit.
   * Factors are whole numbers so conversions multiply and divide exact values.
   */
  private class UnitFactor(val dimension: String, val size: Double)

  private val UNITS: Map<String, UnitFactor> =
    mapOf(
      // Area, in m2. UCUM: `har` is the hectare (`ha` would be hecto-annum).
      "m2" to UnitFactor("area", 1.0),
      "har" to UnitFactor("area", 10_000.0),
      "km2" to UnitFactor("area", 1_000_000.0),
      // Mass, in g. UCUM: `t` is the metric tonne.
      "g" to UnitFactor("mass", 1.0),
      "kg" to UnitFactor("mass", 1_000.0),
      "t" to UnitFactor("mass", 1_000_000.0),
      // Length, in cm.
      "cm" to UnitFactor("length", 1.0),
      "m" to UnitFactor("length", 100.0),
      "km" to UnitFactor("length", 100_000.0),
      // Volume, in L. UCUM defines both `l` and `L` for the liter.
      "L" to UnitFactor("volume", 1.0),
      "l" to UnitFactor("volume", 1.0),
      "m3" to UnitFactor("volume", 1_000.0),
    )

  /**
   * Converts [value] in [from] to [to] (UCUM codes `har`, `m2`, `km2`, `g`, `kg`, `t`, `cm`, `m`,
   * `km`, `m3`, `L`/`l`), or `null` if either unit is unsupported or they measure different things.
   * Codes are case-sensitive, as in UCUM. Identical codes always convert to themselves (so concepts
   * with other units, such as `%`, accept values stated in their own unit). Blank units are
   * unitless and only convert to blank.
   */
  fun convert(value: Double, from: String, to: String): Double? {
    if (from.isBlank() || to.isBlank()) return if (from.isBlank() && to.isBlank()) value else null
    if (from == to) return value
    val source = UNITS[from] ?: return null
    val target = UNITS[to] ?: return null
    if (source.dimension != target.dimension) return null
    return value * source.size / target.size
  }
}
