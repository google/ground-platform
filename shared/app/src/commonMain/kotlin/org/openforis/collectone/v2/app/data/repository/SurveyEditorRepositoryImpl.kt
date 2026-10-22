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
package org.openforis.collectone.v2.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcProtoMapper
import org.openforis.collectone.v2.app.data.sync.WorkingCopyRecorder
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.editor.FormConceptLinkSync
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorProjection
import org.openforis.collectone.v2.app.domain.model.editor.publishedForm
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.usecase.ResolveFormDefForLaunchUseCase

/**
 * [SurveyEditorRepository] backed by the [LocalStore].
 *
 * Reads bridge runtime data to the editor ([SurveyEditorDerivation]); [saveDraft] bridges the
 * editor back to runtime data ([SurveyEditorProjection]) in one transaction, so runtime views only
 * ever read the runtime collections.
 */
class SurveyEditorRepositoryImpl(
  private val store: LocalStore,
  private val fallbackForms: ResolveFormDefForLaunchUseCase = ResolveFormDefForLaunchUseCase(),
  /** Autosaves in-progress drafts to the server's working copy, when syncing with a server. */
  private val workingCopyRecorder: WorkingCopyRecorder? = null,
) : SurveyEditorRepository {
  override suspend fun getDraft(surveyId: String): SurveyEditorDraft = store.transaction {
    currentDraft(surveyId)
  }

  override fun observeDraft(surveyId: String): Flow<SurveyEditorDraft> =
    combine(
        store.observeSurveyEditorDraft(surveyId),
        store.observeSurveys().map { surveys -> surveys.firstOrNull { it.id == surveyId } },
        combine(store.observeForms(surveyId), store.observeSurveyConfig(surveyId), ::Pair),
        store.observeMapLayers(surveyId),
        store.observeEntities(surveyId),
      ) { stored, survey, (forms, config), layers, entities ->
        currentDraft(surveyId, stored, survey, forms, config, layers, entities)
      }
      .distinctUntilChanged()

  override suspend fun saveDraft(
    surveyId: String,
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft?,
  ) {
    store.transaction {
      // Rows are removed relative to the draft the editor opened, or else the last saved draft (or,
      // before the first save, the current map features), so map features collected since then are
      // kept.
      val previous = previous ?: surveyEditorDraft(surveyId) ?: currentDraft(surveyId)
      val published = publication(surveyId, draft, previous)
      putSurveyEditorDraft(surveyId, draft)
      putSurveyConfig(surveyId, published.config)
      putForms(surveyId, published.forms)
      putMapLayers(surveyId, published.mapLayers)
      putEntities(surveyId, published.entities)
      published.survey?.let(::upsertSurvey)
      // The published survey now holds the autosaved edits; drop the server's working copy.
      workingCopyRecorder?.discard(surveyId)
    }
  }

  override suspend fun autosaveDraft(surveyId: String, draft: SurveyEditorDraft) {
    val recorder = workingCopyRecorder ?: return
    val (baseline, current) =
      store.transaction {
        val opened = surveyEditorDraft(surveyId) ?: currentDraft(surveyId)
        workingCopyOf(surveyId, opened, opened) to workingCopyOf(surveyId, draft, opened)
      }
    if (current != null) recorder.autosave(surveyId, current, baseline)
  }

  override suspend fun discardWorkingCopy(surveyId: String) {
    workingCopyRecorder?.discard(surveyId)
  }

  /** What publishing [draft] writes to the survey's runtime data. */
  private data class Publication(
    val config: SurveyConfig,
    val forms: List<FormPreviewItem>,
    val mapLayers: List<MapLayerItem>,
    val entities: List<GeospatialEntityItem>,
    val survey: SurveyPreviewItem?,
  )

  private fun LocalStoreTransaction.publication(
    surveyId: String,
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft,
  ): Publication {
    // Publish each Form's FormDefinition; Forms removed from the draft lose theirs.
    val config = surveyConfig(surveyId) ?: SurveyConfig()
    val editorForms = draft.forms.associate { it.form.formId to draft.publishedForm(it) }
    val removedIds =
      (previous.forms.map { it.form.formId } + forms(surveyId).map { it.id }).toSet() -
        editorForms.keys
    // Survey-level concept links are authoritative; the published forms also carry them as
    // `ground:concept` bind attributes.
    val publishedConfig =
      config.copy(
        formsById = config.formsById - removedIds + editorForms,
        formConceptLinks = FormConceptLinkSync.toFormConceptLinks(draft.forms.map { it.form }),
        purposeIds = draft.details.purposeIds,
        programIds = draft.details.programIds,
      )

    // Project the draft onto the runtime collections the rest of the app reads.
    val projected =
      SurveyEditorProjection.project(
        draft = draft,
        previous = previous,
        surveyId = surveyId,
        forms = forms(surveyId),
        mapLayers = mapLayers(surveyId),
        entities = entities(surveyId),
      )

    // The survey list owns the title, description, organization, sharing/consent summary, and
    // satellite map thumbnail; copy edits there.
    val survey =
      survey(surveyId)?.let { item ->
        item.copy(
          title = draft.details.title.ifBlank { item.title },
          description = draft.details.description.ifBlank { item.description },
          organizationId = draft.details.organizationId,
          sharingPolicy = draft.sharing.policy,
          peerDataVisibility = draft.sharing.peerDataVisibility,
          collectorConsentRequired = draft.sharing.collectorConsentRequired,
          collectorConsentText = draft.sharing.collectorConsentText,
          entityCount = projected.entities.size,
          quotaLimits = draft.details.quotaLimits,
          thumbnail =
            SurveyThumbnailGenerator.fromDraftAndRuntime(
              surveyId = surveyId,
              draft = draft,
              mapLayers = projected.mapLayers,
              entities = projected.entities,
            ),
        )
      }
    return Publication(
      publishedConfig,
      projected.forms,
      projected.mapLayers,
      projected.entities,
      survey,
    )
  }

  /** The survey definition [draft] would publish, for autosaving to the working copy. */
  private fun LocalStoreTransaction.workingCopyOf(
    surveyId: String,
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft,
  ): SurveyDef? {
    val published = publication(surveyId, draft, previous)
    val survey = published.survey ?: return null
    return GrpcProtoMapper.surveyToProto(
        survey = survey,
        forms = published.forms,
        mapLayers = published.mapLayers,
        config = published.config,
        editorDraft = draft,
      )
      .first
  }

  private fun LocalStoreTransaction.currentDraft(surveyId: String): SurveyEditorDraft =
    currentDraft(
      surveyId = surveyId,
      stored = surveyEditorDraft(surveyId),
      survey = survey(surveyId),
      forms = forms(surveyId),
      config = surveyConfig(surveyId),
      layers = mapLayers(surveyId),
      entities = entities(surveyId),
    )

  /** The stored draft refreshed from the runtime data, or a draft derived from it. */
  private fun currentDraft(
    surveyId: String,
    stored: SurveyEditorDraft?,
    survey: SurveyPreviewItem?,
    forms: List<FormPreviewItem>,
    config: SurveyConfig?,
    layers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyEditorDraft =
    if (stored != null) {
      SurveyEditorDraft.forSurvey(
        surveyId = surveyId,
        stored = SurveyEditorDerivation.refresh(stored, surveyId, layers, entities),
        survey = survey,
      )
    } else {
      SurveyEditorDerivation.derive(
        surveyId = surveyId,
        survey = survey,
        forms = forms,
        formDefinition = { form ->
          config?.formsById?.get(form.id) ?: fallbackForms.builtInFallbackFormForForm(form)
        },
        mapLayers = layers,
        entities = entities,
        conceptLinks = config?.formConceptLinks.orEmpty(),
        purposeIds = config?.purposeIds.orEmpty(),
        programIds = config?.programIds.orEmpty(),
      )
    }
}
