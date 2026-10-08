/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorProjection
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.publishedFormXml
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase

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
      putSurveyEditorDraft(surveyId, draft)

      // Publish each Form's XForms; Forms removed from the draft lose theirs.
      val config = surveyConfig(surveyId) ?: SurveyConfig(primaryFormXml = "")
      val editorXml = draft.forms.associate { it.form.formId to draft.publishedFormXml(it) }
      val removedIds =
        (previous.forms.map { it.form.formId } + forms(surveyId).map { it.id }).toSet() -
          editorXml.keys
      putSurveyConfig(
        surveyId,
        config.copy(formXmlById = config.formXmlById - removedIds + editorXml),
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
      putForms(surveyId, projected.forms)
      putMapLayers(surveyId, projected.mapLayers)
      putEntities(surveyId, projected.entities)

      // The survey list owns the title, description, and organization; copy edits there.
      updateSurvey(surveyId) { item ->
        item.copy(
          title = draft.details.title.ifBlank { item.title },
          description = draft.details.description.ifBlank { item.description },
          organizationId = draft.details.organizationId,
          entityCount = projected.entities.size,
        )
      }
    }
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
        formXml = { form ->
          config?.formXmlById?.get(form.id) ?: fallbackForms.builtInFallbackXFormsXmlForForm(form)
        },
        mapLayers = layers,
        entities = entities,
      )
    }
}
