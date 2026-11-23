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
package org.openforis.collectone.v2.web.domain.usecase

import kotlin.math.roundToLong
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.Countries
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.core.impact.ImpactAggregateRow
import org.openforis.collectone.v2.core.impact.ImpactAggregator
import org.openforis.collectone.v2.core.impact.ImpactConfig
import org.openforis.collectone.v2.core.impact.ImpactDiagnostics
import org.openforis.collectone.v2.core.impact.ImpactInput
import org.openforis.collectone.v2.core.impact.ImpactMetric
import org.openforis.collectone.v2.core.impact.ImpactRunResult
import org.openforis.collectone.v2.core.impact.ImpactScopeType
import org.openforis.collectone.v2.core.impact.ImpactSurvey
import org.openforis.collectone.v2.core.impact.OUTCOME_UNANSWERED
import org.openforis.collectone.v2.core.impact.SkippedLinkReason
import org.openforis.collectone.v2.core.impact.UnnormalizableReason
import org.openforis.collectone.v2.web.domain.model.ImpactActivityItem
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionLevel
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionSummary
import org.openforis.collectone.v2.web.domain.model.ImpactBreakdownItem
import org.openforis.collectone.v2.web.domain.model.ImpactCellSummary
import org.openforis.collectone.v2.web.domain.model.ImpactCountedMessage
import org.openforis.collectone.v2.web.domain.model.ImpactCountryTotals
import org.openforis.collectone.v2.web.domain.model.ImpactDiagnosticNote
import org.openforis.collectone.v2.web.domain.model.ImpactFeatureTotals
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.domain.model.ImpactGoalSection
import org.openforis.collectone.v2.web.domain.model.ImpactGoals
import org.openforis.collectone.v2.web.domain.model.ImpactHint
import org.openforis.collectone.v2.web.domain.model.ImpactIndicator
import org.openforis.collectone.v2.web.domain.model.ImpactMessage
import org.openforis.collectone.v2.web.domain.model.ImpactOutcomeSummary
import org.openforis.collectone.v2.web.domain.model.ImpactSuggestedGroup
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.domain.model.ImpactSummaryScope
import org.openforis.collectone.v2.web.domain.model.ImpactText

/** One survey's data as the impact numbers read it. */
data class ImpactSurveySource(
  val survey: SurveyPreviewItem,
  val content: SurveyContent,
  /** The survey's editor draft (dataset columns, form questions, and their concept links). */
  val draft: SurveyEditorDraft?,
)

/** One aggregation run with what's needed to describe its rows in plain language. */
data class ImpactSnapshot(
  val input: ImpactInput,
  val result: ImpactRunResult,
  val surveys: List<SurveyPreviewItem>,
  val organizations: List<Organization>,
  /** Every concept of every library, by ID. */
  val concepts: Map<String, LibraryConcept>,
) {
  companion object {
    val EMPTY =
      ImpactSnapshot(
        input = ImpactInput(emptyList(), emptyList()),
        result = ImpactRunResult(emptyList(), ImpactDiagnostics(), emptyMap()),
        surveys = emptyList(),
        organizations = emptyList(),
        concepts = emptyMap(),
      )
  }
}

/**
 * Computes impact numbers with the shared aggregation job
 * (`org.openforis.collectone.v2.core.impact`, see `docs/technical/backend/impact-aggregation.md`)
 * from the local data store's surveys, map features, libraries, impact events, and survey outcomes,
 * and describes them in plain language for the survey, organization, and platform-wide Impact views
 * and PDF summaries.
 *
 * Linked values come from map feature properties whose layer column is linked to a concept, or that
 * a linked form question saves to; choice answers are written as their standard codes. The sample
 * data's map shapes are illustrative, so each feature passes its recorded area and a representative
 * point (for grid cells) rather than its drawn outline.
 */
class ComputeImpactUseCase(
  private val config: ImpactConfig = ImpactConfig(),
  private val resolveLibrary: ResolveLibraryUseCase = ResolveLibraryUseCase(),
) {
  /** Runs the aggregation over everything stored. */
  operator fun invoke(
    sources: List<ImpactSurveySource>,
    libraries: Map<String, OrganizationLibrary>,
    organizations: List<Organization>,
    events: List<ImpactEvent>,
  ): ImpactSnapshot {
    val input = input(sources, libraries, organizations, events)
    return ImpactSnapshot(
      input = input,
      result = ImpactAggregator.run(input, config),
      surveys = sources.map { it.survey },
      organizations = organizations,
      concepts = libraries.values.flatMap { it.concepts }.associateBy { it.id },
    )
  }

  private val inputBuilder = ImpactInputBuilder(resolveLibrary)

  // --- Input ---

  /** The aggregation input for [sources]. */
  fun input(
    sources: List<ImpactSurveySource>,
    libraries: Map<String, OrganizationLibrary>,
    organizations: List<Organization>,
    events: List<ImpactEvent>,
  ): ImpactInput = inputBuilder.build(sources, libraries, organizations, events)

  // --- Summaries ---

  /** Impact numbers of survey [surveyId], or `null` if it isn't in [snapshot]. */
  fun surveySummary(snapshot: ImpactSnapshot, surveyId: String): ImpactSummary? {
    val survey = snapshot.surveys.firstOrNull { it.id == surveyId } ?: return null
    val input = snapshot.input.surveys.firstOrNull { it.id == surveyId } ?: return null
    val rows = snapshot.result.rows(ImpactScopeType.SURVEY, surveyId)
    val level = snapshot.result.attributions[surveyId]?.let { level(it.basis.name) }
    return summarize(
        scope = ImpactSummaryScope.SURVEY,
        scopeId = surveyId,
        title = survey.title,
        rows = rows,
        snapshot = snapshot,
        mappedCount = input.features.size.toLong(),
        diagnostics = snapshot.result.diagnostics.forSurvey(surveyId),
      )
      .let { summary ->
        summary.copy(
          attribution = ImpactAttributionSummary(level = level),
          hints =
            buildList {
              if (input.features.isEmpty() && input.submissionCount == 0)
                add(ImpactHint.COLLECT_DATA)
              if (input.purposes.isEmpty()) add(ImpactHint.PICK_PURPOSE)
              if (
                summary.goals.isEmpty() &&
                  summary.ownIndicators.isEmpty() &&
                  summary.producerCount == null
              ) {
                add(ImpactHint.LINK_FIELDS)
              }
            },
        )
      }
  }

  /**
   * Impact numbers of organization [organizationId], optionally only for survey [surveyId] and map
   * features in country [countryCode] (re-aggregated, so plots shared between its surveys still
   * count once). `null` if the organization isn't in [snapshot].
   */
  fun organizationSummary(
    snapshot: ImpactSnapshot,
    organizationId: String,
    surveyId: String? = null,
    countryCode: String? = null,
  ): ImpactSummary? {
    val organization = snapshot.organizations.firstOrNull { it.id == organizationId } ?: return null
    val result: ImpactRunResult
    val surveys: List<ImpactSurvey>
    if (surveyId == null && countryCode == null) {
      result = snapshot.result
      surveys = snapshot.input.surveys.filter { it.organizationId == organizationId }
    } else {
      surveys =
        snapshot.input.surveys
          .filter { it.organizationId == organizationId && (surveyId == null || it.id == surveyId) }
          .map { survey ->
            if (countryCode == null) survey
            else
              survey.copy(
                features =
                  survey.features.filter {
                    (it.countryCode ?: organization.countryCode) == countryCode
                  }
              )
          }
      result = ImpactAggregator.run(snapshot.input.copy(surveys = surveys), config)
    }
    val rows = result.rows(ImpactScopeType.ORGANIZATION, organizationId)
    val surveyIds = surveys.map { it.id }.toSet()
    val summary =
      summarize(
        scope = ImpactSummaryScope.ORGANIZATION,
        scopeId = organizationId,
        title = organization.name,
        rows = rows,
        snapshot = snapshot,
        mappedCount = surveys.sumOf { it.features.size }.toLong(),
        diagnostics = result.diagnostics.forSurveys(surveyIds),
      )
    return summary.copy(
      hints =
        buildList {
          if (summary.features.mappedCount == 0L) add(ImpactHint.COLLECT_DATA)
          if (surveys.all { it.purposes.isEmpty() }) add(ImpactHint.PICK_PURPOSE)
          if (summary.goals.isEmpty() && summary.ownIndicators.isEmpty())
            add(ImpactHint.LINK_FIELDS)
        }
    )
  }

  /** Countries organization [organizationId]'s map features are in, for its country filter. */
  fun organizationCountries(snapshot: ImpactSnapshot, organizationId: String): List<String> {
    val fallback = snapshot.organizations.firstOrNull { it.id == organizationId }?.countryCode
    return snapshot.input.surveys
      .filter { it.organizationId == organizationId }
      .flatMap { survey -> survey.features.mapNotNull { it.countryCode ?: fallback } }
      .distinct()
      .sortedBy { Countries.byCode(it)?.name ?: it }
  }

  /**
   * Platform-wide numbers: organizations that keep their data out of platform-wide numbers are left
   * out, organization concepts appear only as organization-suggested indicators, and countries and
   * map areas with too few map features are hidden.
   */
  fun globalSummary(snapshot: ImpactSnapshot): ImpactSummary {
    val excluded =
      snapshot.input.organizations.filter { it.excludeFromPlatformAggregates }.map { it.id }.toSet()
    val eligible = snapshot.input.surveys.filter { it.organizationId !in excluded }
    val rows = snapshot.result.rows(ImpactScopeType.GLOBAL, "")
    val summary =
      summarize(
        scope = ImpactSummaryScope.GLOBAL,
        scopeId = "",
        // Data only (e.g. the PDF file name); the UI and the PDF show a translated title.
        title = GLOBAL_TITLE,
        rows = rows.filterNot { it.organizationSuggested },
        snapshot = snapshot,
        mappedCount = eligible.sumOf { it.features.size }.toLong(),
        diagnostics = snapshot.result.diagnostics.forSurveys(eligible.map { it.id }.toSet()),
      )
    val suggested =
      rows
        .filter { it.organizationSuggested }
        .groupBy { it.goal to it.organizationId }
        .map { (key, groupRows) ->
          val (goal, organizationId) = key
          ImpactSuggestedGroup(
            goalId = goal,
            goalTitle = ImpactGoals.label(goal),
            organizationId = organizationId,
            organizationName =
              snapshot.organizations.firstOrNull { it.id == organizationId }?.name
                ?: organizationId,
            indicators =
              groupRows
                .groupBy { it.conceptId }
                .mapNotNull { (id, r) -> indicator(snapshot, id, r) },
          )
        }
        .filter { it.indicators.isNotEmpty() }
        .sortedWith(
          compareBy(ImpactGoals.ORDER_COMPARATOR) { group: ImpactSuggestedGroup -> group.goalId }
            .thenBy { it.organizationName }
        )
    val cellRows = snapshot.result.rows.filter { it.scopeType == ImpactScopeType.CELL }
    val diagnostics = snapshot.result.diagnostics
    return summary.copy(
      suggestedIndicators = suggested,
      countries =
        snapshot.result.rows
          .filter { it.scopeType == ImpactScopeType.COUNTRY && it.metric == ImpactMetric.FEATURES }
          .map {
            ImpactCountryTotals(
              countryCode = it.scopeId,
              countryName = Countries.byCode(it.scopeId)?.name ?: it.scopeId,
              featureCount = it.featureCount,
              areaHa = it.areaHa,
              isSuppressed = it.suppressed,
            )
          }
          .sortedBy { it.countryName },
      cells =
        ImpactCellSummary(
          publishedCellCount = cellRows.count { !it.suppressed },
          suppressedCellCount = diagnostics.suppressedCellCount,
          suppressedFeatureCount = diagnostics.suppressedCellFeatureCount,
        ),
      excludedOrganizationCount = excluded.size,
      hints =
        if (summary.features.mappedCount == 0L) listOf(ImpactHint.COLLECT_DATA) else emptyList(),
    )
  }

  /**
   * Organization [organizationId]'s **published** numbers: what anyone signed in to Collect One
   * sees on the Impact page, optionally only for map features in country [countryCode]. Every
   * organization publishes them. See [publish] for what's left out. `null` if the organization
   * isn't in [snapshot] or is the synthetic `"All users"` organization.
   */
  fun publishedOrganizationSummary(
    snapshot: ImpactSnapshot,
    organizationId: String,
    countryCode: String? = null,
  ): ImpactSummary? {
    if (snapshot.organizations.firstOrNull { it.id == organizationId }?.isSynthetic != false) {
      return null
    }
    return organizationSummary(snapshot, organizationId, surveyId = null, countryCode = countryCode)
      ?.let { publish(snapshot, it) }
  }

  /**
   * Platform-wide numbers as published to everyone signed in: [globalSummary] limited to
   * `AGGREGATE_PUBLIC` standard fields. Managers of `"All users"` see [globalSummary] itself.
   */
  fun publishedGlobalSummary(snapshot: ImpactSnapshot): ImpactSummary =
    publish(snapshot, globalSummary(snapshot))

  /**
   * [summary] as published outside its organization: anonymous totals only.
   * * Indicators (and the producer count) only from `AGGREGATE_PUBLIC` standard fields; `ORG_ONLY`
   *   and `SENSITIVE` ones stay with the organization.
   * * No diagnostics (they quote captured values) and no setup hints (they're for organizers).
   * * For an organization, nothing but the title when it has fewer than
   *   [ImpactConfig.organizationPublicMinFeatures] unique map features (in the filtered country, if
   *   any). Platform-wide numbers are already thresholded by country and map area.
   */
  private fun publish(snapshot: ImpactSnapshot, summary: ImpactSummary): ImpactSummary {
    if (
      summary.scope == ImpactSummaryScope.ORGANIZATION &&
        summary.features.uniqueCount < config.organizationPublicMinFeatures
    ) {
      return ImpactSummary(
        scope = summary.scope,
        scopeId = summary.scopeId,
        title = summary.title,
        isPublished = true,
        isBelowPublicationThreshold = true,
      )
    }
    fun isPublic(conceptId: String?) =
      conceptId != null &&
        snapshot.concepts[conceptId]?.privacyClass == PrivacyClass.AGGREGATE_PUBLIC
    fun List<ImpactIndicator>.published() = filter { isPublic(it.conceptId) }
    val isProducerPublic = isPublic(summary.producerConceptId)
    return summary.copy(
      producerCount = summary.producerCount.takeIf { isProducerPublic },
      producerConceptId = summary.producerConceptId.takeIf { isProducerPublic },
      goals =
        summary.goals
          .map { it.copy(indicators = it.indicators.published()) }
          .filter { it.indicators.isNotEmpty() },
      ownIndicators = summary.ownIndicators.published(),
      suggestedIndicators =
        summary.suggestedIndicators
          .map { it.copy(indicators = it.indicators.published()) }
          .filter { it.indicators.isNotEmpty() },
      diagnostics = emptyList(),
      hints = emptyList(),
      isPublished = true,
    )
  }

  /**
   * Assembles an [ImpactSummary] for [scope] and [scopeId] from the aggregator's [rows],
   * partitioning confirmed concept indicators into global goal sections vs. organization-defined
   * indicators and pairing them with activity, outcome, attribution, and diagnostic notes.
   */
  private fun summarize(
    scope: ImpactSummaryScope,
    scopeId: String,
    title: String,
    rows: List<ImpactAggregateRow>,
    snapshot: ImpactSnapshot,
    mappedCount: Long,
    diagnostics: ImpactDiagnostics,
  ): ImpactSummary {
    val features = rows.firstOrNull { it.metric == ImpactMetric.FEATURES }
    val conceptRows = rows.filter { it.metric == ImpactMetric.CONCEPT && !it.organizationSuggested }
    val indicators =
      conceptRows.groupBy { it.conceptId }.mapNotNull { (id, r) -> indicator(snapshot, id, r) }
    val (global, own) = indicators.partition { snapshot.concepts[it.conceptId]?.isGlobal ?: true }
    val goals =
      global
        .flatMap { indicator ->
          snapshot.concepts[indicator.conceptId]
            ?.goals
            .orEmpty()
            .ifEmpty { listOf("") }
            .map { it to indicator }
        }
        .groupBy({ it.first }, { it.second })
        .map { (goal, list) -> ImpactGoalSection(goal, ImpactGoals.label(goal), list) }
        // Goals first (by name), standard fields without a goal last.
        .sortedWith(compareBy(ImpactGoals.ORDER_COMPARATOR) { it.goalId })
    val events = rows.filter { it.metric == ImpactMetric.EVENT }
    return ImpactSummary(
      scope = scope,
      scopeId = scopeId,
      title = title,
      features =
        ImpactFeatureTotals(
          uniqueCount = features?.featureCount ?: 0,
          areaHa = features?.areaHa ?: 0.0,
          mappedCount = mappedCount,
        ),
      producerCount =
        rows.firstOrNull { it.metric == ImpactMetric.DISTINCT_VALUES }?.value?.roundToLong(),
      producerConceptId = rows.firstOrNull { it.metric == ImpactMetric.DISTINCT_VALUES }?.conceptId,
      goals = goals,
      ownIndicators = own,
      activity =
        ACTIVITY_TYPES.mapNotNull { type ->
          val row = events.firstOrNull { it.eventKind?.name == type.name }
          if (row == null && type !in ALWAYS_SHOWN_ACTIVITY) return@mapNotNull null
          ImpactActivityItem(
            type = type,
            count = row?.value?.roundToLong() ?: 0,
            featureCount = row?.featureCount ?: 0,
            areaHa = row?.areaHa ?: 0.0,
          )
        },
      outcomes = outcomes(rows.filter { it.metric == ImpactMetric.OUTCOME }),
      attribution =
        ImpactAttributionSummary(
          distribution =
            rows
              .filter { it.metric == ImpactMetric.ATTRIBUTION }
              .mapNotNull { row ->
                ImpactAttributionLevel.entries
                  .firstOrNull { it.score == row.attributionScore }
                  ?.let { it to row.value.roundToLong() }
              }
              .toMap()
        ),
      diagnostics = notes(snapshot, diagnostics),
    )
  }

  /**
   * Tallies closed-survey [ImpactMetric.OUTCOME] rows into answered uses (sorted by count
   * descending, then enum order), "not yet" responses, and unanswered closed surveys.
   */
  private fun outcomes(rows: List<ImpactAggregateRow>): ImpactOutcomeSummary {
    var notYet = 0L
    var unanswered = 0L
    val uses = mutableListOf<Pair<SurveyOutcomeKind, Long>>()
    rows.forEach { row ->
      val count = row.value.roundToLong()
      when (row.outcomeKind) {
        OUTCOME_UNANSWERED -> unanswered += count
        SurveyOutcomeKind.NOT_YET.name -> notYet += count
        else ->
          SurveyOutcomeKind.entries
            .firstOrNull { it.name == row.outcomeKind }
            ?.let { uses += it to count }
      }
    }
    return ImpactOutcomeSummary(
      uses = uses.sortedByDescending { it.second },
      notYetCount = notYet,
      unansweredCount = unanswered,
    )
  }

  /** A plain-language indicator for [conceptId]'s rows. */
  private fun indicator(
    snapshot: ImpactSnapshot,
    conceptId: String,
    rows: List<ImpactAggregateRow>,
  ): ImpactIndicator? {
    val concept = snapshot.concepts[conceptId]
    val label = concept?.label?.text?.ifBlank { null } ?: conceptId
    val first = rows.firstOrNull() ?: return null
    val unit = concept?.unit.orEmpty().let(ImpactFormat::unit)
    fun withUnit(value: String) = ImpactText.Plain(if (unit.isBlank()) value else "$value $unit")
    fun features(n: Long) = ImpactText.count(ImpactCountedMessage.MAP_FEATURES, n)
    return when (concept?.aggregation) {
      ConceptAggregation.COUNT_BY_CODE -> {
        val total = rows.sumOf { it.featureCount }
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = features(total),
          detail = ImpactText.of(ImpactMessage.DETAIL_BY, label.lowercase()),
          breakdown =
            rows
              .sortedByDescending { it.featureCount }
              .map { row ->
                ImpactBreakdownItem(
                  label = codeLabel(concept, row.code),
                  valueText =
                    if (row.areaHa > 0) {
                      ImpactText.of(
                        ImpactMessage.VALUE_WITH_AREA,
                        features(row.featureCount),
                        ImpactText.Measured(ImpactFormat.hectares(row.areaHa)),
                      )
                    } else {
                      features(row.featureCount)
                    },
                  fraction = if (total > 0) row.featureCount.toFloat() / total else 0f,
                )
              },
        )
      }
      ConceptAggregation.SHARE_BY_CODE -> {
        val answered =
          rows.firstOrNull { it.value > 0 }?.let { (it.featureCount / it.value).roundToLong() } ?: 0
        val top = rows.maxByOrNull { it.value } ?: first
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText =
            ImpactText.of(
              ImpactMessage.SHARE_VALUE,
              ImpactFormat.percent(top.value),
              codeLabel(concept, top.code),
            ),
          detail = ImpactText.of(ImpactMessage.DETAIL_OF_ANSWERED, features(answered)),
          breakdown =
            rows
              .sortedByDescending { it.value }
              .map {
                ImpactBreakdownItem(
                  label = codeLabel(concept, it.code),
                  valueText = ImpactText.Plain(ImpactFormat.percent(it.value)),
                  fraction = it.value.toFloat(),
                )
              },
        )
      }
      ConceptAggregation.SUM ->
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = withUnit(ImpactFormat.number(first.value)),
          detail = ImpactText.of(ImpactMessage.DETAIL_TOTAL_FROM, features(first.featureCount)),
        )
      ConceptAggregation.MEAN ->
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = withUnit(ImpactFormat.number(first.value)),
          detail = ImpactText.of(ImpactMessage.DETAIL_AVERAGE_OF, features(first.featureCount)),
        )
      else ->
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = features(first.featureCount),
          detail =
            if (first.areaHa > 0) {
              ImpactText.of(
                ImpactMessage.DETAIL_COVERING,
                ImpactText.Measured(ImpactFormat.hectares(first.areaHa)),
              )
            } else {
              ImpactText.EMPTY
            },
        )
    }
  }

  /** Display label for categorical [code] in [concept]'s code list, falling back to [code]. */
  private fun codeLabel(concept: LibraryConcept?, code: String): String =
    concept?.codeList?.firstOrNull { it.code == code }?.label?.text?.ifBlank { null } ?: code

  /** What [diagnostics] couldn't count, in plain language. */
  private fun notes(
    snapshot: ImpactSnapshot,
    diagnostics: ImpactDiagnostics,
  ): List<ImpactDiagnosticNote> {
    fun label(id: String) = snapshot.concepts[id]?.label?.text?.ifBlank { null } ?: id
    val links =
      diagnostics.skippedLinks
        .distinctBy { it.conceptId to it.reason }
        .map { link ->
          val message =
            when (link.reason) {
              SkippedLinkReason.UNKNOWN -> ImpactMessage.NOTE_LINK_UNKNOWN
              SkippedLinkReason.DEPRECATED -> ImpactMessage.NOTE_LINK_DEPRECATED
              SkippedLinkReason.NOT_IN_LIBRARY -> ImpactMessage.NOTE_LINK_NOT_IN_LIBRARY
            }
          ImpactDiagnosticNote(ImpactText.of(message, label(link.conceptId)))
        }
    val values =
      diagnostics.unnormalizableValues
        .groupBy { it.conceptId to it.reason }
        .map { (key, list) ->
          val (conceptId, reason) = key
          val examples = list.map { it.value }.distinct().take(2).joinToString(", ") { "\"$it\"" }
          val message =
            when (reason) {
              UnnormalizableReason.NOT_A_NUMBER -> ImpactCountedMessage.NOTE_NOT_A_NUMBER
              UnnormalizableReason.UNSUPPORTED_UNIT -> ImpactCountedMessage.NOTE_UNSUPPORTED_UNIT
            }
          ImpactDiagnosticNote(
            ImpactText.Plural(
              message,
              list.size.toLong(),
              listOf(label(conceptId), list.size.toString(), examples),
            )
          )
        }
    return links + values
  }

  /** Resolves an [ImpactAttributionLevel] by its serialized enum name, or `null` if unknown. */
  private fun level(basisName: String): ImpactAttributionLevel? =
    ImpactAttributionLevel.entries.firstOrNull { it.name == basisName }

  /** Restricts [ImpactDiagnostics] to skipped links and unnormalizable values in [surveyIds]. */
  private fun ImpactDiagnostics.forSurveys(surveyIds: Set<String>) =
    ImpactDiagnostics(
      skippedLinks = skippedLinks.filter { it.surveyId in surveyIds },
      unnormalizableValues = unnormalizableValues.filter { it.surveyId in surveyIds },
    )

  companion object {
    /** [ImpactSummary.title] of the platform-wide summary (data, e.g. for its PDF file name). */
    const val GLOBAL_TITLE = "All Collect One users"

    /** Activity shown on Impact views, in order. */
    private val ACTIVITY_TYPES =
      listOf(
        ImpactEventType.EXPORT,
        ImpactEventType.PARTNER_PUSH,
        ImpactEventType.RECEIPT_GENERATED,
        ImpactEventType.RECEIPT_SHARED,
        ImpactEventType.DOI_MINTED,
      )

    /** Activity shown even when it hasn't happened yet. */
    private val ALWAYS_SHOWN_ACTIVITY =
      setOf(
        ImpactEventType.EXPORT,
        ImpactEventType.RECEIPT_GENERATED,
        ImpactEventType.RECEIPT_SHARED,
      )
  }
}
