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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.math.roundToLong
import org.groundplatform.v2.core.impact.ImpactAggregateRow
import org.groundplatform.v2.core.impact.ImpactAggregation
import org.groundplatform.v2.core.impact.ImpactAggregator
import org.groundplatform.v2.core.impact.ImpactConcept
import org.groundplatform.v2.core.impact.ImpactConfig
import org.groundplatform.v2.core.impact.ImpactDiagnostics
import org.groundplatform.v2.core.impact.ImpactEventKind
import org.groundplatform.v2.core.impact.ImpactEventRecord
import org.groundplatform.v2.core.impact.ImpactFeature
import org.groundplatform.v2.core.impact.ImpactGeometry
import org.groundplatform.v2.core.impact.ImpactInput
import org.groundplatform.v2.core.impact.ImpactLatLng
import org.groundplatform.v2.core.impact.ImpactMetric
import org.groundplatform.v2.core.impact.ImpactOrganization
import org.groundplatform.v2.core.impact.ImpactOutcomeKind
import org.groundplatform.v2.core.impact.ImpactPrivacy
import org.groundplatform.v2.core.impact.ImpactPurpose
import org.groundplatform.v2.core.impact.ImpactRunResult
import org.groundplatform.v2.core.impact.ImpactScopeType
import org.groundplatform.v2.core.impact.ImpactSurvey
import org.groundplatform.v2.core.impact.ImpactValue
import org.groundplatform.v2.core.impact.OUTCOME_UNANSWERED
import org.groundplatform.v2.core.impact.SkippedLinkReason
import org.groundplatform.v2.core.impact.UnnormalizableReason
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptAggregation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Countries
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityShape
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactActivityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactAttributionLevel
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactAttributionSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactBreakdownItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCellSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCountryTotals
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactDiagnosticNote
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactFeatureTotals
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactFormat
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactGoalSection
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactGoals
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactHint
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactIndicator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactOutcomeSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSuggestedGroup
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummaryScope
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.epochMillisOfIsoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent

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
 * Computes impact numbers with the shared aggregation job (`org.groundplatform.v2.core.impact`, see
 * `docs/technical/backend/impact-aggregation.md`) from the prototype's surveys, map features,
 * libraries, impact events, and survey outcomes, and describes them in plain language for the
 * survey, organization, and platform-wide Impact views and PDF summaries.
 *
 * Linked values come from map feature properties whose layer column is linked to a concept, or that
 * a linked form question saves to; choice answers are written as their standard codes. The
 * prototype's map shapes are illustrative, so each feature passes its recorded area and a
 * representative point (for grid cells) rather than its drawn outline.
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

  // --- Input ---

  /** The aggregation input for [sources]. */
  fun input(
    sources: List<ImpactSurveySource>,
    libraries: Map<String, OrganizationLibrary>,
    organizations: List<Organization>,
    events: List<ImpactEvent>,
  ): ImpactInput {
    val eventsBySurvey = events.groupBy { it.surveyId }
    val concepts = libraries.values.flatMap { it.concepts }.associateBy { it.id }
    return ImpactInput(
      surveys =
        sources.map { source ->
          surveyInput(source, libraries, concepts, eventsBySurvey[source.survey.id].orEmpty())
        },
      concepts = libraries.values.flatMap { it.concepts }.distinctBy { it.id }.map(::conceptInput),
      organizations =
        organizations
          .filterNot { it.isSynthetic }
          .map {
            ImpactOrganization(
              id = it.id,
              countryCode = it.countryCode,
              excludeFromPlatformAggregates = it.excludeFromPlatformAggregates,
            )
          },
    )
  }

  private fun conceptInput(concept: LibraryConcept) =
    ImpactConcept(
      id = concept.id,
      ownerOrganizationId = concept.organizationId,
      aggregation = ImpactAggregation.valueOf(concept.aggregation.name),
      privacy = ImpactPrivacy.valueOf(concept.privacyClass.name),
      unit = concept.unit,
      goals = concept.goals,
      isDeprecated = concept.isDeprecated,
    )

  private fun surveyInput(
    source: ImpactSurveySource,
    libraries: Map<String, OrganizationLibrary>,
    concepts: Map<String, LibraryConcept>,
    events: List<ImpactEvent>,
  ): ImpactSurvey {
    val survey = source.survey
    val draft = source.draft
    val library = resolveLibrary.forOrganization(libraries, survey.organizationId)
    val purposeIds = draft?.details?.purposeIds ?: source.content.config?.purposeIds.orEmpty()
    val columns =
      draft?.let { d -> d.datasets.associate { it.id to linkedColumns(it, d) } }.orEmpty()
    val linked = buildSet {
      columns.values.forEach { dataset -> dataset.forEach { add(it.conceptId) } }
      draft?.forms?.forEach { entry ->
        entry.form.questions.forEach { q -> q.conceptLink?.let { add(it.conceptId) } }
      }
      source.content.config?.formConceptLinks?.forEach { links ->
        links.field_concepts.values.forEach { ref ->
          ref.concept_id.takeIf { it.isNotBlank() }?.let(::add)
        }
      }
    }
    val anchor = SurveyMapAnchor.forSurvey(survey.id)
    return ImpactSurvey(
      id = survey.id,
      organizationId = survey.organizationId,
      purposes =
        purposeIds.map { id ->
          val pack = library.purposePack(id)
          ImpactPurpose(
            id = id,
            isGlobal = pack?.isGlobal ?: false,
            exportProfileIds = pack?.exportProfileIds.orEmpty().toSet(),
          )
        },
      linkedConceptIds = linked,
      features =
        source.content.entities
          .filter { it.geometryKind != EntityGeometryKind.NONE }
          .map { featureInput(it, columns[it.datasetId].orEmpty(), concepts, anchor) },
      submissionCount = source.content.standaloneSubmissions.size,
      events =
        events.map {
          ImpactEventRecord(
            kind = ImpactEventKind.valueOf(it.type.name),
            featureCount = it.featureCount.toLong(),
            areaHa = it.areaHa,
            exportProfileId = it.exportProfileId,
          )
        },
      isClosed = survey.isClosed,
      outcome =
        survey.outcome?.outcomes?.mapTo(mutableSetOf()) { ImpactOutcomeKind.valueOf(it.name) },
    )
  }

  /** A dataset column that holds a concept's values, with its choices' standard codes. */
  private data class LinkedColumn(
    val name: String,
    val label: String,
    val conceptId: String,
    val choiceCodes: Map<String, String>,
  )

  /**
   * [dataset]'s columns linked to a concept: columns linked directly, and columns a linked question
   * of [draft] saves to (a new feature's answers, or a mapped update), as in the export profiles.
   */
  private fun linkedColumns(dataset: EntityDataset, draft: SurveyEditorDraft): List<LinkedColumn> {
    val result = linkedMapOf<String, LinkedColumn>()
    for (entry in draft.forms) {
      val form = entry.form
      for (question in form.questions) {
        val link = question.conceptLink ?: continue
        val column =
          when {
            dataset.linkedFormKey == entry.key && form.saveTo.mode == SaveToMode.CREATE ->
              question.name.takeIf { name -> dataset.properties.any { it.name == name } }
            form.saveTo.mode == SaveToMode.UPDATE && form.saveTo.targetDatasetId == dataset.id ->
              form.saveTo.mappings.firstOrNull { it.questionKey == question.key }?.property
            else -> null
          } ?: continue
        val property = dataset.properties.firstOrNull { it.name == column }
        result.getOrPut(column) {
          LinkedColumn(
            name = column,
            label = property?.label ?: column,
            conceptId = link.conceptId,
            choiceCodes =
              question.choices.mapNotNull { c -> c.code?.let { c.value to it } }.toMap(),
          )
        }
      }
    }
    dataset.properties.forEach { property ->
      val link = property.conceptLink ?: return@forEach
      // A direct column link wins over a question's, but keeps the question's choice codes.
      val codes = result[property.name]?.choiceCodes.orEmpty()
      result[property.name] = LinkedColumn(property.name, property.label, link.conceptId, codes)
    }
    return if (dataset.kind == DatasetKind.MAP_LAYER) result.values.toList() else emptyList()
  }

  private fun featureInput(
    entity: GeospatialEntityItem,
    columns: List<LinkedColumn>,
    concepts: Map<String, LibraryConcept>,
    anchor: SurveyMapAnchor,
  ): ImpactFeature {
    val values = buildMap {
      for (column in columns) {
        val raw =
          (entity.properties[column.name] ?: entity.properties[column.label]).orEmpty().trim()
        if (raw.isEmpty()) continue
        val value = column.choiceCodes[raw] ?: codeOf(concepts[column.conceptId], raw)
        put(column.conceptId, ImpactValue(value))
      }
    }
    val country =
      values[COUNTRY_CONCEPT_ID]
        ?.text
        ?.takeIf { Countries.isValidCode(it) }
        ?.let(Countries::normalizeCode)
    val vertices = EntityShape.vertices(entity, anchor)
    val position =
      vertices
        .takeIf { it.isNotEmpty() }
        ?.let { points ->
          ImpactLatLng(
            points.sumOf { it.latitude } / points.size,
            points.sumOf { it.longitude } / points.size,
          )
        }
    return ImpactFeature(
      entityId = entity.id,
      geoId = entity.geoId.trim().ifEmpty { null },
      updatedAtMillis = entity.submissions.maxOfOrNull { timestampMillis(it.timestamp) } ?: 0L,
      geometry = position?.let { ImpactGeometry.Point(it) },
      areaHa = entity.areaHectares,
      countryCode = country,
      values = values,
    )
  }

  /**
   * [raw] as [concept]'s code-list code when it matches a code or a label in any language (ignoring
   * case), e.g. `"Coffee"` → `coffee`; otherwise [raw] unchanged.
   */
  private fun codeOf(concept: LibraryConcept?, raw: String): String {
    val items = concept?.codeList.orEmpty()
    if (items.isEmpty()) return raw
    return items
      .firstOrNull { item ->
        item.code.equals(raw, ignoreCase = true) ||
          item.label.values.values.any { it.equals(raw, ignoreCase = true) }
      }
      ?.code ?: raw
  }

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
        title = "All Ground users",
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
        .sortedWith(compareBy({ it.goalTitle }, { it.organizationName }))
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
            .map {
              it to indicator
            }
        }
        .groupBy({ it.first }, { it.second })
        .map { (goal, list) -> ImpactGoalSection(goal, ImpactGoals.label(goal), list) }
        // Goals first (by name), standard fields without a goal last.
        .sortedWith(compareBy({ it.goalId.isBlank() }, { it.title }))
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
      goals = goals,
      ownIndicators = own,
      activity =
        ACTIVITY_TYPES.mapNotNull { type ->
          val row = events.firstOrNull { it.eventKind?.name == type.name }
          if (row == null && type !in ALWAYS_SHOWN_ACTIVITY) return@mapNotNull null
          ImpactActivityItem(
            type = type,
            label = activityLabel(type),
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
    fun withUnit(value: String) = if (unit.isBlank()) value else "$value $unit"
    fun features(n: Long) = ImpactFormat.count(n, "map feature")
    return when (concept?.aggregation) {
      ConceptAggregation.COUNT_BY_CODE -> {
        val total = rows.sumOf { it.featureCount }
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = features(total),
          detail = "By ${label.lowercase()}",
          breakdown =
            rows
              .sortedByDescending { it.featureCount }
              .map { row ->
                ImpactBreakdownItem(
                  label = codeLabel(concept, row.code),
                  valueText =
                    features(row.featureCount) +
                      if (row.areaHa > 0) " · ${ImpactFormat.hectares(row.areaHa)}" else "",
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
          valueText = "${ImpactFormat.percent(top.value)} ${codeLabel(concept, top.code)}",
          detail = "Of ${features(answered)} with an answer",
          breakdown =
            rows
              .sortedByDescending { it.value }
              .map {
                ImpactBreakdownItem(
                  label = codeLabel(concept, it.code),
                  valueText = ImpactFormat.percent(it.value),
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
          detail = "Total from ${features(first.featureCount)}",
        )
      ConceptAggregation.MEAN ->
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = withUnit(ImpactFormat.number(first.value)),
          detail = "Average of ${features(first.featureCount)}",
        )
      else ->
        ImpactIndicator(
          conceptId = conceptId,
          label = label,
          valueText = features(first.featureCount),
          detail = if (first.areaHa > 0) "Covering ${ImpactFormat.hectares(first.areaHa)}" else "",
        )
    }
  }

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
          val why =
            when (link.reason) {
              SkippedLinkReason.UNKNOWN -> "links to a standard field that no longer exists"
              SkippedLinkReason.DEPRECATED -> "links to a standard field that was retired"
              SkippedLinkReason.NOT_IN_LIBRARY ->
                "links to another organization's standard field, so it isn't counted here"
            }
          ImpactDiagnosticNote("A field ${why} (${label(link.conceptId)}).")
        }
    val values =
      diagnostics.unnormalizableValues
        .groupBy { it.conceptId to it.reason }
        .map { (key, list) ->
          val (conceptId, reason) = key
          val examples = list.map { it.value }.distinct().take(2).joinToString(", ") { "\"$it\"" }
          val what = if (list.size == 1) "1 value" else "${list.size} values"
          val why =
            when (reason) {
              UnnormalizableReason.NOT_A_NUMBER ->
                if (list.size == 1) "isn't a number" else "aren't numbers"
              UnnormalizableReason.UNSUPPORTED_UNIT -> "use a unit that can't be converted"
            }
          ImpactDiagnosticNote("${label(conceptId)}: $what $why ($examples), so left out.")
        }
    return links + values
  }

  private fun level(basisName: String): ImpactAttributionLevel? =
    ImpactAttributionLevel.entries.firstOrNull { it.name == basisName }

  private fun ImpactDiagnostics.forSurveys(surveyIds: Set<String>) =
    ImpactDiagnostics(
      skippedLinks = skippedLinks.filter { it.surveyId in surveyIds },
      unnormalizableValues = unnormalizableValues.filter { it.surveyId in surveyIds },
    )

  companion object {
    private const val COUNTRY_CONCEPT_ID = "core.country"

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

    private fun activityLabel(type: ImpactEventType): String =
      when (type) {
        ImpactEventType.EXPORT -> "Downloads"
        ImpactEventType.PARTNER_PUSH -> "Sent to partners"
        ImpactEventType.RECEIPT_GENERATED -> "Receipts created"
        ImpactEventType.RECEIPT_SHARED -> "Receipts shared"
        ImpactEventType.DOI_MINTED -> "DOIs minted"
        ImpactEventType.SURVEY_CLOSED -> "Surveys closed"
      }

    /**
     * Epoch milliseconds of a submission timestamp (`2026-09-18 10:14 UTC` or ISO 8601), or 0 if it
     * can't be read.
     */
    internal fun timestampMillis(timestamp: String): Long {
      val match = TIMESTAMP.find(timestamp.trim()) ?: return 0
      val (date, hour, minute) = match.destructured
      return epochMillisOfIsoUtc("${date}T$hour:${minute}:00Z") ?: 0
    }

    private val TIMESTAMP = Regex("""^(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})""")
  }
}
