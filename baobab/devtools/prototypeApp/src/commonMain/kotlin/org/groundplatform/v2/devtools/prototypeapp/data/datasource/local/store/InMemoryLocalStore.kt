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
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyStats
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorDraft

/**
 * Ephemeral, in-memory [LocalStore] used on web (as a cache) and in tests.
 *
 * State is an immutable snapshot held in a [MutableStateFlow]. A transaction edits a private copy
 * and publishes it in one step, so observers see each transaction exactly once. Writes never
 * suspend, so on an immediate dispatcher they complete synchronously.
 */
class InMemoryLocalStore : LocalStore {
  private val state = MutableStateFlow(StoreState())
  private val writeLock = Mutex()

  override fun observeSurveys(): Flow<List<SurveyPreviewItem>> =
    state.map { it.surveys }.distinctUntilChanged { a, b -> a === b }

  override fun observeForms(surveyId: String): Flow<List<FormPreviewItem>> =
    observeSurvey(surveyId) { it.forms }

  override fun observeMapLayers(surveyId: String): Flow<List<MapLayerItem>> =
    observeSurvey(surveyId) { it.mapLayers }

  override fun observeEntities(surveyId: String): Flow<List<GeospatialEntityItem>> =
    state
      .map { it.surveyData[surveyId] ?: SurveyData.EMPTY }
      .distinctUntilChanged { a, b ->
        a.entities === b.entities && a.entitySubmissions === b.entitySubmissions
      }
      .map { it.joinedEntities() }

  override fun observeStandaloneSubmissions(surveyId: String): Flow<List<SubmissionPreviewItem>> =
    observeSurvey(surveyId) { it.standaloneSubmissions }

  override fun observeSubmissionGeometries(
    surveyId: String
  ): Flow<List<SubmissionGeometryPolygon>> = observeSurvey(surveyId) { it.submissionGeometries }

  override fun observeSurveyConfig(surveyId: String): Flow<SurveyConfig?> =
    state.map { it.surveyData[surveyId]?.config }.distinctUntilChanged()

  override fun observeSurveyEditorDraft(surveyId: String): Flow<SurveyEditorDraft?> =
    state.map { it.surveyData[surveyId]?.editorDraft }.distinctUntilChanged()

  override fun observeSurveyConfigs(): Flow<Map<String, SurveyConfig>> =
    state
      .map { it.surveyData }
      .distinctUntilChanged { a, b -> a === b }
      .map { data -> buildMap { data.forEach { (id, d) -> d.config?.let { put(id, it) } } } }
      .distinctUntilChanged()

  override fun observeSurveyStats(): Flow<Map<String, SurveyStats>> =
    state
      .map { it.surveyData }
      .distinctUntilChanged { a, b -> a === b }
      .map { data ->
        data.mapValues { (_, d) -> SurveyStats(d.entities.size, d.submissionCount()) }
      }
      .distinctUntilChanged()

  override fun observeMutations(): Flow<List<MutationLogItem>> =
    state.map { it.mutations }.distinctUntilChanged { a, b -> a === b }

  override fun observePlaces(): Flow<List<SurveyPlaceItem>> =
    state.map { it.places }.distinctUntilChanged { a, b -> a === b }

  override fun observeOfflineTilePackages(): Flow<List<OfflineTilePackageItem>> =
    state.map { it.offlineTilePackages }.distinctUntilChanged { a, b -> a === b }

  override fun observePreferences(): Flow<StoredPreferences> =
    state.map { it.preferences }.distinctUntilChanged()

  override suspend fun <R> transaction(block: suspend LocalStoreTransaction.() -> R): R {
    // Nested call inside an open transaction on this store: join it.
    val open = currentCoroutineContext()[OpenTransaction]
    if (open != null && open.store === this) return open.tx.block()
    return writeLock.withLock {
      val tx = Transaction(state.value)
      val result = withContext(OpenTransaction(this, tx)) { tx.block() }
      if (tx.working !== state.value) state.value = tx.working
      result
    }
  }

  override suspend fun clear() {
    val open = currentCoroutineContext()[OpenTransaction]
    if (open != null && open.store === this) {
      open.tx.working = StoreState()
      return
    }
    writeLock.withLock { state.value = StoreState() }
  }

  /** Marks the coroutine as running inside [tx], so nested transactions join it. */
  private class OpenTransaction(val store: InMemoryLocalStore, val tx: Transaction) :
    AbstractCoroutineContextElement(OpenTransaction) {
    companion object Key : CoroutineContext.Key<OpenTransaction>
  }

  private fun <T> observeSurvey(surveyId: String, select: (SurveyData) -> List<T>): Flow<List<T>> =
    state
      .map { select(it.surveyData[surveyId] ?: SurveyData.EMPTY) }
      .distinctUntilChanged { a, b -> a === b }

  /** Immutable snapshot of the whole store. */
  private data class StoreState(
    val surveys: List<SurveyPreviewItem> = emptyList(),
    val surveyData: Map<String, SurveyData> = emptyMap(),
    val mutations: List<MutationLogItem> = emptyList(),
    val places: List<SurveyPlaceItem> = emptyList(),
    val offlineTilePackages: List<OfflineTilePackageItem> = emptyList(),
    val preferences: StoredPreferences = StoredPreferences(),
  )

  /**
   * Data for one survey. Map features are stored without their submissions; linked submissions are
   * stored separately per entity ID (normalized) and joined on read.
   */
  private data class SurveyData(
    val config: SurveyConfig? = null,
    val editorDraft: SurveyEditorDraft? = null,
    val forms: List<FormPreviewItem> = emptyList(),
    val mapLayers: List<MapLayerItem> = emptyList(),
    val entities: Map<String, GeospatialEntityItem> = emptyMap(),
    val entitySubmissions: Map<String, List<SubmissionPreviewItem>> = emptyMap(),
    val standaloneSubmissions: List<SubmissionPreviewItem> = emptyList(),
    val submissionGeometries: List<SubmissionGeometryPolygon> = emptyList(),
  ) {
    fun submissionCount(): Int =
      entitySubmissions.values.sumOf { it.size } + standaloneSubmissions.size

    fun joinedEntities(): List<GeospatialEntityItem> =
      entities.values.map { it.copy(submissions = entitySubmissions[it.id].orEmpty()) }

    companion object {
      val EMPTY = SurveyData()
    }
  }

  private class Transaction(var working: StoreState) : LocalStoreTransaction {
    private fun data(surveyId: String): SurveyData =
      working.surveyData[surveyId] ?: SurveyData.EMPTY

    private fun editSurvey(surveyId: String, edit: (SurveyData) -> SurveyData) {
      working = working.copy(surveyData = working.surveyData + (surveyId to edit(data(surveyId))))
    }

    override fun surveys(): List<SurveyPreviewItem> = working.surveys

    override fun upsertSurvey(survey: SurveyPreviewItem) {
      val index = working.surveys.indexOfFirst { it.id == survey.id }
      val updated =
        if (index < 0) {
          working.surveys + survey
        } else {
          working.surveys.toMutableList().also { it[index] = survey }
        }
      working = working.copy(surveys = updated)
    }

    override fun surveyConfig(surveyId: String): SurveyConfig? = data(surveyId).config

    override fun putSurveyConfig(surveyId: String, config: SurveyConfig) =
      editSurvey(surveyId) { it.copy(config = config) }

    override fun surveyEditorDraft(surveyId: String): SurveyEditorDraft? =
      data(surveyId).editorDraft

    override fun putSurveyEditorDraft(surveyId: String, draft: SurveyEditorDraft) =
      editSurvey(surveyId) { it.copy(editorDraft = draft) }

    override fun forms(surveyId: String): List<FormPreviewItem> = data(surveyId).forms

    override fun putForms(surveyId: String, forms: List<FormPreviewItem>) =
      editSurvey(surveyId) { it.copy(forms = forms.toList()) }

    override fun mapLayers(surveyId: String): List<MapLayerItem> = data(surveyId).mapLayers

    override fun putMapLayers(surveyId: String, layers: List<MapLayerItem>) =
      editSurvey(surveyId) { it.copy(mapLayers = layers.toList()) }

    override fun entities(surveyId: String): List<GeospatialEntityItem> =
      data(surveyId).joinedEntities()

    override fun entity(surveyId: String, entityId: String): GeospatialEntityItem? {
      val d = data(surveyId)
      val stored = d.entities[entityId] ?: return null
      return stored.copy(submissions = d.entitySubmissions[entityId].orEmpty())
    }

    override fun entityCount(surveyId: String): Int = data(surveyId).entities.size

    override fun upsertEntities(surveyId: String, entities: List<GeospatialEntityItem>) {
      if (entities.isEmpty()) return
      editSurvey(surveyId) { d ->
        val nextEntities = LinkedHashMap(d.entities)
        val nextSubmissions = HashMap(d.entitySubmissions)
        for (entity in entities) {
          nextEntities[entity.id] = entity.copy(submissions = emptyList())
          if (entity.submissions.isEmpty()) {
            nextSubmissions.remove(entity.id)
          } else {
            nextSubmissions[entity.id] = entity.submissions.toList()
          }
        }
        d.copy(entities = nextEntities, entitySubmissions = nextSubmissions)
      }
    }

    override fun putEntities(surveyId: String, entities: List<GeospatialEntityItem>) {
      editSurvey(surveyId) { it.copy(entities = emptyMap(), entitySubmissions = emptyMap()) }
      upsertEntities(surveyId, entities)
    }

    override fun deleteEntity(surveyId: String, entityId: String) =
      editSurvey(surveyId) { d ->
        d.copy(entities = d.entities - entityId, entitySubmissions = d.entitySubmissions - entityId)
      }

    override fun standaloneSubmissions(surveyId: String): List<SubmissionPreviewItem> =
      data(surveyId).standaloneSubmissions

    override fun putStandaloneSubmissions(
      surveyId: String,
      submissions: List<SubmissionPreviewItem>,
    ) = editSurvey(surveyId) { it.copy(standaloneSubmissions = submissions.toList()) }

    override fun submissionCount(surveyId: String): Int = data(surveyId).submissionCount()

    override fun submissionGeometries(surveyId: String): List<SubmissionGeometryPolygon> =
      data(surveyId).submissionGeometries

    override fun putSubmissionGeometries(
      surveyId: String,
      geometries: List<SubmissionGeometryPolygon>,
    ) = editSurvey(surveyId) { it.copy(submissionGeometries = geometries.toList()) }

    override fun mutations(): List<MutationLogItem> = working.mutations

    override fun putMutations(mutations: List<MutationLogItem>) {
      working = working.copy(mutations = mutations.toList())
    }

    override fun places(): List<SurveyPlaceItem> = working.places

    override fun putPlaces(places: List<SurveyPlaceItem>) {
      working = working.copy(places = places.toList())
    }

    override fun offlineTilePackages(): List<OfflineTilePackageItem> = working.offlineTilePackages

    override fun putOfflineTilePackages(packages: List<OfflineTilePackageItem>) {
      working = working.copy(offlineTilePackages = packages.toList())
    }

    override fun preferences(): StoredPreferences = working.preferences

    override fun putPreferences(preferences: StoredPreferences) {
      working = working.copy(preferences = preferences)
    }
  }
}
