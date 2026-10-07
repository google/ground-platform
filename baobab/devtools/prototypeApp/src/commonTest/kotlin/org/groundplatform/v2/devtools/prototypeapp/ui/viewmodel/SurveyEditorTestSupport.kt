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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.groundplatform.v2.core.sampling.SamplingEngine
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GenerateSamplePlotsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorUiState

/**
 * [SurveyEditorRepository] over an in-memory draft, for Survey editor tests that start from a
 * hand-authored draft rather than the seeded store. Every survey ID shares the one draft; saving
 * replaces it and records the call in [saved].
 */
internal class FakeSurveyEditorRepository(initial: SurveyEditorDraft) : SurveyEditorRepository {
  val drafts = MutableStateFlow(initial)
  val saved = mutableListOf<Pair<SurveyEditorDraft, SurveyEditorDraft?>>()

  override suspend fun getDraft(surveyId: String): SurveyEditorDraft = drafts.value

  override fun observeDraft(surveyId: String): Flow<SurveyEditorDraft> = drafts

  override suspend fun saveDraft(
    surveyId: String,
    draft: SurveyEditorDraft,
    previous: SurveyEditorDraft?,
  ) {
    saved += draft to previous
    drafts.update { draft }
  }
}

/** A scope whose coroutines run inline, like the ViewModels' production scope in tests. */
internal fun testScope() = CoroutineScope(Dispatchers.Unconfined + Job())

/**
 * A Survey editor over [draft] (by default the sample survey) backed by an empty store, with a
 * [FakeSurveyEditorRepository]. Build it outside `runNow`, like other ViewModel fixtures.
 */
internal fun surveyEditorViewModel(
  draft: SurveyEditorDraft = SurveyEditorSamples.draft(),
  store: LocalStore = InMemoryLocalStore(),
  editorRepository: SurveyEditorRepository = FakeSurveyEditorRepository(draft),
  surveyRepository: SurveyRepository = SurveyRepositoryImpl(store),
  samplingEngine: SamplingEngine? = null,
  yieldBetweenChunks: suspend () -> Unit = {},
  now: () -> String = { "2026-01-01T00:00:00Z" },
  scope: CoroutineScope = testScope(),
): SurveyEditorViewModel =
  SurveyEditorViewModel(
    surveyRepository = surveyRepository,
    surveyEditorRepository = editorRepository,
    organizationRepository = OrganizationRepositoryImpl(store),
    authRepository = AuthRepositoryImpl(),
    placeRepository = PlaceRepositoryImpl(store),
    generateSamplePlots =
      GenerateSamplePlotsUseCase(
        samplingEngine = samplingEngine,
        yieldBetweenChunks = yieldBetweenChunks,
        now = now,
      ),
    scope = scope,
  )

/** A Survey editor over the seeded store's active survey, through the real repositories. */
internal fun seededSurveyEditorViewModel(
  store: LocalStore,
  scope: CoroutineScope = testScope(),
): SurveyEditorViewModel =
  SurveyEditorViewModel(
    surveyRepository = SurveyRepositoryImpl(store),
    surveyEditorRepository = SurveyEditorRepositoryImpl(store),
    organizationRepository = OrganizationRepositoryImpl(store),
    authRepository = AuthRepositoryImpl(),
    placeRepository = PlaceRepositoryImpl(store),
    scope = scope,
  )

/**
 * A standalone Form editor over [form] with [datasets], like the Form editor tests used to build.
 */
internal fun formEditorViewModel(
  form: EditorForm =
    EditorFormTemplates.blank(formId = FormIds.newFormId(), title = "Untitled form"),
  datasets: List<EditorDataset> = emptyList(),
  scope: CoroutineScope = testScope(),
): FormEditorViewModel = FormEditorViewModel.standalone(form, datasets, scope)

/** The current UI state, for assertions. */
internal val SurveyEditorViewModel.ui: SurveyEditorUiState
  get() = uiState.value

/** The current UI state, for assertions. */
internal val FormEditorViewModel.ui: FormEditorUiState
  get() = uiState.value

/**
 * A [SurveyRepository] whose active survey's entities are replaced by [entities], so tests can
 * simulate submissions on Map layer features without seeding the store.
 */
internal class EntitiesOverrideSurveyRepository(
  private val delegate: SurveyRepository,
  private val entities: StateFlow<List<GeospatialEntityItem>>,
) : SurveyRepository by delegate {
  override fun observeSurveyContent(surveyId: String): Flow<SurveyContent> =
    combine(delegate.observeSurveyContent(surveyId), entities) { content, overrides ->
      content.copy(entities = overrides)
    }
}

/** A minimal map feature of [datasetId] with [submissionCount] submissions. */
internal fun fakeEntityWithSubmissions(
  datasetId: String,
  submissionCount: Int,
): GeospatialEntityItem =
  GeospatialEntityItem(
    id = "$datasetId-1",
    label = "Feature",
    datasetId = datasetId,
    datasetName = datasetId,
    layerId = datasetId,
    geoId = "",
    geometryTypeLabel = "Point",
    areaHectares = 0.0,
    perimeterMeters = 0,
    coordinatesLabel = "",
    normalizedX = 0f,
    normalizedY = 0f,
    colorHex = 0xFF000000,
    properties = emptyMap(),
    submissions =
      List(submissionCount) { i ->
        SubmissionPreviewItem(
          id = "s$i",
          formId = "f",
          formTitle = "Form",
          formVersion = "1",
          collectorName = "",
          collectorEmail = "",
          timestamp = "",
          fields = emptyList(),
        )
      },
  )
