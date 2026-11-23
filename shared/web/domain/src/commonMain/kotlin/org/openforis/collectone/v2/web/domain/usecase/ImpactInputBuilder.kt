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
package org.openforis.collectone.v2.web.domain.usecase

import org.openforis.collectone.v2.app.domain.model.Countries
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.toGeoCoord
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.core.impact.ImpactAggregation
import org.openforis.collectone.v2.core.impact.ImpactConcept
import org.openforis.collectone.v2.core.impact.ImpactEventKind
import org.openforis.collectone.v2.core.impact.ImpactEventRecord
import org.openforis.collectone.v2.core.impact.ImpactFeature
import org.openforis.collectone.v2.core.impact.ImpactGeometry
import org.openforis.collectone.v2.core.impact.ImpactInput
import org.openforis.collectone.v2.core.impact.ImpactOrganization
import org.openforis.collectone.v2.core.impact.ImpactOutcomeKind
import org.openforis.collectone.v2.core.impact.ImpactPrivacy
import org.openforis.collectone.v2.core.impact.ImpactPurpose
import org.openforis.collectone.v2.core.impact.ImpactSurvey
import org.openforis.collectone.v2.core.impact.ImpactValue

/**
 * Builds the [ImpactInput] consumed by `ImpactAggregator` from the web console's stored surveys,
 * editor drafts, organization libraries, organizations, and recorded impact events.
 *
 * Linked values come from map feature properties whose layer column is linked to a concept, or that
 * a linked form question saves to (`SaveToMode.CREATE` matching by column name, or
 * `SaveToMode.UPDATE` via explicit field mappings); choice answers are normalized to their standard
 * concept codes. Because sample data map shapes are illustrative, each feature supplies its
 * recorded area in hectares and a representative point (for grid cells) rather than its full
 * geometry outline.
 */
internal class ImpactInputBuilder(
  private val resolveLibrary: ResolveLibraryUseCase = ResolveLibraryUseCase()
) {
  /** Builds the [ImpactInput] for [sources]. */
  fun build(
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

  /** Maps a domain [LibraryConcept] to its aggregator [ImpactConcept] descriptor. */
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

  /**
   * Builds the [ImpactSurvey] input for [source], resolving its organization library, purpose
   * packs, linked concept IDs (from dataset columns, draft form questions, or synced config), map
   * features, and "data was used" [events].
   */
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
          .filter { it.geometryType != null }
          .map { featureInput(it, columns[it.datasetId].orEmpty(), concepts) },
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
    if (dataset.kind != DatasetKind.MAP_LAYER) return emptyList()
    val result = linkedMapOf<String, LinkedColumn>()
    for (entry in draft.forms) {
      for (question in entry.form.questions) {
        val column = questionLinkedColumn(entry.key, entry.form, question, dataset)
        if (column != null) {
          result.getOrPut(column.name) { column }
        }
      }
    }
    dataset.properties.forEach { property ->
      val link = property.conceptLink
      if (link != null) {
        // A direct column link wins over a question's, but keeps the question's choice codes.
        val codes = result[property.name]?.choiceCodes.orEmpty()
        result[property.name] = LinkedColumn(property.name, property.label, link.conceptId, codes)
      }
    }
    return result.values.toList()
  }

  /**
   * Resolves the [LinkedColumn] in [dataset] populated by a concept-linked [question] in [form],
   * including any choice value → standard code mappings.
   */
  private fun questionLinkedColumn(
    formKey: String,
    form: EditorForm,
    question: EditorQuestion,
    dataset: EntityDataset,
  ): LinkedColumn? {
    val link = question.conceptLink ?: return null
    val column = targetColumnName(formKey, form, question, dataset) ?: return null
    val property = dataset.properties.firstOrNull { it.name == column }
    return LinkedColumn(
      name = column,
      label = property?.label ?: column,
      conceptId = link.conceptId,
      choiceCodes = question.choices.mapNotNull { c -> c.code?.let { c.value to it } }.toMap(),
    )
  }

  /**
   * Returns the property name in [dataset] that [question] writes into under [form]'s `SaveToMode`,
   * or `null` when [form] does not write [question] to [dataset].
   */
  private fun targetColumnName(
    formKey: String,
    form: EditorForm,
    question: EditorQuestion,
    dataset: EntityDataset,
  ): String? =
    when {
      dataset.linkedFormKey == formKey && form.saveTo.mode == SaveToMode.CREATE ->
        question.name.takeIf { name -> dataset.properties.any { it.name == name } }
      form.saveTo.mode == SaveToMode.UPDATE && form.saveTo.targetDatasetId == dataset.id ->
        form.saveTo.mappings.firstOrNull { it.questionKey == question.key }?.property
      else -> null
    }

  private fun featureInput(
    entity: GeospatialEntityItem,
    columns: List<LinkedColumn>,
    concepts: Map<String, LibraryConcept>,
  ): ImpactFeature {
    val values = buildMap {
      for (column in columns) {
        val raw =
          (entity.properties[column.name] ?: entity.properties[column.label]).orEmpty().trim()
        if (raw.isNotEmpty()) {
          val value = column.choiceCodes[raw] ?: codeOf(concepts[column.conceptId], raw)
          put(column.conceptId, ImpactValue(value))
        }
      }
    }
    val country =
      values[COUNTRY_CONCEPT_ID]
        ?.text
        ?.takeIf { Countries.isValidCode(it) }
        ?.let(Countries::normalizeCode)
    val position = entity.geometry?.representativePoint?.toGeoCoord()
    return ImpactFeature(
      entityId = entity.id,
      geoId = entity.geoId.trim().ifEmpty { null },
      updatedAtMillis =
        entity.submissions.mapNotNull { it.updatedAt }.maxOrNull()?.toEpochMilliseconds() ?: 0L,
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

  private companion object {
    const val COUNTRY_CONCEPT_ID = "core.country"
  }
}
