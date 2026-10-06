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

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyStats
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorDraft

/**
 * Local data store: the app's single source of truth for survey data.
 *
 * All reads and writes go through this interface. On web it is an ephemeral in-memory cache
 * ([InMemoryLocalStore]); on mobile it will be backed by persistent storage used for offline work.
 * A future sync engine downloads remote state into the store and drains the outbox ([mutations])
 * from it.
 *
 * Survey-scoped collections are keyed by survey ID, so data for every survey (not just the active
 * one) lives in the store, and edits survive switching surveys.
 *
 * Reads are observable [Flow]s. Writes happen inside [transaction], which applies all changes
 * atomically and notifies observers once.
 */
interface LocalStore {
  /** All surveys shared with the user, in display order. */
  fun observeSurveys(): Flow<List<SurveyPreviewItem>>

  /**
   * All organizations known to the user (ones they belong to or can discover), in display order.
   */
  fun observeOrganizations(): Flow<List<Organization>>

  /** Forms in [surveyId]. */
  fun observeForms(surveyId: String): Flow<List<FormPreviewItem>>

  /** Map layers in [surveyId]. */
  fun observeMapLayers(surveyId: String): Flow<List<MapLayerItem>>

  /** Map features in [surveyId], each joined with its linked submissions. */
  fun observeEntities(surveyId: String): Flow<List<GeospatialEntityItem>>

  /** Submissions in [surveyId] that aren't linked to a map feature. */
  fun observeStandaloneSubmissions(surveyId: String): Flow<List<SubmissionPreviewItem>>

  /** Geometries recorded in submissions in [surveyId]. */
  fun observeSubmissionGeometries(surveyId: String): Flow<List<SubmissionGeometryPolygon>>

  /** Survey-level settings for [surveyId] (for example its primary XForms definition). */
  fun observeSurveyConfig(surveyId: String): Flow<SurveyConfig?>

  /** Survey-level settings for every survey that has them, keyed by survey ID. */
  fun observeSurveyConfigs(): Flow<Map<String, SurveyConfig>>

  /** The Survey editor's draft of [surveyId], or `null` if it has never been edited. */
  fun observeSurveyEditorDraft(surveyId: String): Flow<SurveyEditorDraft?>

  /** Map feature and submission counts for every survey, keyed by survey ID. */
  fun observeSurveyStats(): Flow<Map<String, SurveyStats>>

  /** Local mutation log (outbox and uploaded history) across all surveys. */
  fun observeMutations(): Flow<List<MutationLogItem>>

  /** Searchable places and landmarks. */
  fun observePlaces(): Flow<List<SurveyPlaceItem>>

  /** Offline basemap tile packages. */
  fun observeOfflineTilePackages(): Flow<List<OfflineTilePackageItem>>

  /** Device-level preferences (active survey, user settings, media cache stats). */
  fun observePreferences(): Flow<StoredPreferences>

  /**
   * Runs [block] atomically. Reads inside the block see earlier writes from the same block.
   * Observers are notified once, after the block completes. If [block] throws, no changes are
   * applied. A transaction started inside another transaction on the same store joins it, so use
   * cases can group several repository calls into one atomic change.
   */
  suspend fun <R> transaction(block: suspend LocalStoreTransaction.() -> R): R

  /** Deletes all data in the store. */
  suspend fun clear()
}

/** Read and write access to the store inside a [LocalStore.transaction]. */
interface LocalStoreTransaction {
  // Surveys -------------------------------------------------------------------------------------

  fun surveys(): List<SurveyPreviewItem>

  fun survey(surveyId: String): SurveyPreviewItem? = surveys().firstOrNull { it.id == surveyId }

  /** Inserts or replaces a survey. New surveys are appended. */
  fun upsertSurvey(survey: SurveyPreviewItem)

  /** Replaces the survey with [surveyId] with `transform(existing)`; returns the result. */
  fun updateSurvey(
    surveyId: String,
    transform: (SurveyPreviewItem) -> SurveyPreviewItem,
  ): SurveyPreviewItem? {
    val existing = survey(surveyId) ?: return null
    val updated = transform(existing)
    upsertSurvey(updated)
    return updated
  }

  fun surveyConfig(surveyId: String): SurveyConfig?

  fun putSurveyConfig(surveyId: String, config: SurveyConfig)

  fun surveyEditorDraft(surveyId: String): SurveyEditorDraft?

  fun putSurveyEditorDraft(surveyId: String, draft: SurveyEditorDraft)

  // Organizations -------------------------------------------------------------------------------

  fun organizations(): List<Organization>

  fun organization(organizationId: String): Organization? =
    organizations().firstOrNull { it.id == organizationId }

  /** Inserts or replaces an organization. New organizations are appended. */
  fun upsertOrganization(organization: Organization)

  /** Replaces the organization with [organizationId] with `transform(existing)`; returns it. */
  fun updateOrganization(
    organizationId: String,
    transform: (Organization) -> Organization,
  ): Organization? {
    val existing = organization(organizationId) ?: return null
    val updated = transform(existing)
    upsertOrganization(updated)
    return updated
  }

  /** Deletes an organization. Surveys that belonged to it become personal surveys. */
  fun deleteOrganization(organizationId: String)

  // Forms ---------------------------------------------------------------------------------------

  fun forms(surveyId: String): List<FormPreviewItem>

  /** Replaces all forms in [surveyId]. */
  fun putForms(surveyId: String, forms: List<FormPreviewItem>)

  // Map layers ----------------------------------------------------------------------------------

  fun mapLayers(surveyId: String): List<MapLayerItem>

  /** Replaces all map layers in [surveyId]. */
  fun putMapLayers(surveyId: String, layers: List<MapLayerItem>)

  // Map features (entities) ---------------------------------------------------------------------

  /** Map features in [surveyId], each joined with its linked submissions. */
  fun entities(surveyId: String): List<GeospatialEntityItem>

  fun entity(surveyId: String, entityId: String): GeospatialEntityItem?

  /** Number of map features in [surveyId]. */
  fun entityCount(surveyId: String): Int

  /**
   * Inserts or replaces [entities] in [surveyId] together with their linked submissions. The
   * entity's `submissions` list becomes the complete, ordered list of submissions linked to it. New
   * entities are appended.
   */
  fun upsertEntities(surveyId: String, entities: List<GeospatialEntityItem>)

  fun upsertEntity(surveyId: String, entity: GeospatialEntityItem) =
    upsertEntities(surveyId, listOf(entity))

  /** Replaces all map features (and their linked submissions) in [surveyId]. */
  fun putEntities(surveyId: String, entities: List<GeospatialEntityItem>)

  fun deleteEntity(surveyId: String, entityId: String)

  // Submissions ---------------------------------------------------------------------------------

  fun standaloneSubmissions(surveyId: String): List<SubmissionPreviewItem>

  /** Replaces all standalone submissions in [surveyId]. */
  fun putStandaloneSubmissions(surveyId: String, submissions: List<SubmissionPreviewItem>)

  /** Total number of submissions in [surveyId], linked and standalone. */
  fun submissionCount(surveyId: String): Int

  fun submissionGeometries(surveyId: String): List<SubmissionGeometryPolygon>

  /** Replaces all submission geometries in [surveyId]. */
  fun putSubmissionGeometries(surveyId: String, geometries: List<SubmissionGeometryPolygon>)

  // Mutations (outbox) --------------------------------------------------------------------------

  fun mutations(): List<MutationLogItem>

  /** Replaces the whole mutation log. */
  fun putMutations(mutations: List<MutationLogItem>)

  // Places --------------------------------------------------------------------------------------

  fun places(): List<SurveyPlaceItem>

  fun putPlaces(places: List<SurveyPlaceItem>)

  // Offline tile packages -----------------------------------------------------------------------

  fun offlineTilePackages(): List<OfflineTilePackageItem>

  fun putOfflineTilePackages(packages: List<OfflineTilePackageItem>)

  // Preferences ---------------------------------------------------------------------------------

  fun preferences(): StoredPreferences

  fun putPreferences(preferences: StoredPreferences)

  fun updatePreferences(transform: (StoredPreferences) -> StoredPreferences): StoredPreferences {
    val updated = transform(preferences())
    putPreferences(updated)
    return updated
  }
}

/** Device-level preferences stored in the [LocalStore]. */
data class StoredPreferences(
  val activeSurveyId: String = "",
  val userSettings: UserSettings = UserSettings(),
  val uploadedMediaCacheSizeLabel: String = "0 MB",
  val uploadedMediaFileCount: Int = 0,
  /** Version of the sample data written by the seeder, or 0 if the store was never seeded. */
  val seedVersion: Int = 0,
)
