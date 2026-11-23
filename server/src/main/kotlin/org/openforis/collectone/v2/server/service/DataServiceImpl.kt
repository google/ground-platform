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
package org.openforis.collectone.v2.server.service

import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutationResult
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityCluster
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.GetAppliedMutationsResponse
import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.GetEntityResponse
import openforis.collectone.v1.data.GetImpactAggregatesRequest
import openforis.collectone.v1.data.GetMediaDownloadUrlRequest
import openforis.collectone.v1.data.GetMediaDownloadUrlResponse
import openforis.collectone.v1.data.GetSubmissionRequest
import openforis.collectone.v1.data.GetSubmissionResponse
import openforis.collectone.v1.data.ListAuditRecordsRequest
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListEntitiesResponse
import openforis.collectone.v1.data.ListEntityClustersRequest
import openforis.collectone.v1.data.ListEntityClustersResponse
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.ListPlacesRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.ListSubmissionsResponse
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.data.PrepareMediaUploadRequest
import openforis.collectone.v1.data.PrepareMediaUploadResponse
import openforis.collectone.v1.data.QueueFilterMode
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.SubmissionRecord
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.core.sync.EntityFieldMask
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.ListParams
import org.openforis.collectone.v2.server.data.SyncLists
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.data.changedAfter
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier

/**
 * `DataService`: map features (entities) and submissions, plot locks, media URLs, and the reporting
 * records (impact events, audit records, mutation logs) of the surveys a caller can see. Plot locks
 * are in [PlotLocks] and the reporting RPCs in [DataReports].
 */
internal class DataServiceImpl(
  private val store: UserDataStore,
  private val syncLists: SyncLists,
  private val topicNotifier: SurveyTopicNotifier,
  private val entityEnricher: EntityEnricher = NoopEntityEnricher,
  private val geoIdRegistrar: GeoIdRegistrar = NoopGeoIdRegistrar,
) : DataServiceServer {
  private val plotLocks = PlotLocks(store, topicNotifier)
  private val reports = DataReports(store, syncLists)

  /**
   * Which records of a survey a caller sees: all, or only those it created (`OWN_ONLY` below
   * organizer). [key] goes into entity and submission tokens, so a token issued under another
   * visibility (after a `peer_data_visibility` flip, a role change, or for another user) fails with
   * `FAILED_PRECONDITION` and the client runs a full sync of the survey.
   */
  private class DataVisibility(val key: String, private val ownerId: String?) {
    operator fun invoke(createdBy: String): Boolean = ownerId == null || createdBy == ownerId
  }

  /** The caller's [DataVisibility] for survey [surveyId], after checking it can view the survey. */
  private fun Access.dataVisibility(surveyId: String): DataVisibility {
    val role = requireSurvey(surveyId, SurveyRole.VIEWER)
    return if (seesOwnRecordsOnly(surveyId, role)) DataVisibility("own:$userId", userId)
    else DataVisibility("all", null)
  }

  override suspend fun GetEntity(request: GetEntityRequest): GetEntityResponse {
    val caller = currentCaller()
    val entity =
      store.read { r ->
        val visible = Access(r, caller).dataVisibility(request.survey_id)
        r.entity(request.survey_id, request.entity_id)?.takeIf {
          visible(it.audit_info?.created_by.orEmpty())
        }
      } ?: EntityRecord(survey_id = request.survey_id, entity_id = request.entity_id)
    return GetEntityResponse(entity = entity)
  }

  override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse {
    val queryFieldsSet = listEntitiesQueryFields(request)
    val matchesEntity: (EntityRecord) -> Boolean = { e ->
      (request.dataset_id.isBlank() || e.dataset_id == request.dataset_id) &&
        (request.include_deleted || !e.is_deleted) &&
        request.filter.all { (key, value) -> e.properties[key]?.string_value == value }
    }
    val query =
      orderedQuery(
        orderBy = request.order_by,
        commitTimestamp = EntityRecord::commit_timestamp,
        stringKeys = mapOf("label" to EntityRecord::label),
        fingerprint =
          "dataset_id=${request.dataset_id}|include_deleted=${request.include_deleted}|" +
            "filter=${request.filter.toSortedMap()}|order_by=${request.order_by.trim().lowercase()}",
        matches = matchesEntity,
      )
    val caller = currentCaller()
    if (request.sync_token.isBlank() && request.entity_ids.isNotEmpty()) {
      return listEntitiesByIds(request, caller, matchesEntity)
    }
    var propertyKeys = emptyList<String>()
    val page = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.checkNotTrashed(request.survey_id)
      syncLists.list(
        params =
          ListParams(request.page_size, request.page_token, request.sync_token, request.offset),
        scopeKey = "entities|survey_id=${request.survey_id}",
        changedAfter = { after, limit -> r.entitiesChangedAfter(request.survey_id, after, limit) },
        queryRecords = {
          val rows = r.entities(request.survey_id, request.dataset_id, request.include_deleted)
          if (request.include_property_keys) {
            propertyKeys =
              distinctPropertyKeys(
                rows.filter {
                  matchesEntity(it) && visible(it.audit_info?.created_by.orEmpty())
                }
              )
          }
          rows
        },
        commitTimestamp = EntityRecord::commit_timestamp,
        id = EntityRecord::entity_id,
        query = query,
        queryFieldsSet = queryFieldsSet,
        watermark = r.snapshotTimestamp,
        accessEpoch = visible.key,
        visible = { visible(it.audit_info?.created_by.orEmpty()) },
      )
    }
    val entities = applyEntityFieldMask(page.items, request.field_mask)
    return ListEntitiesResponse(
      entities = entities,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
      total_size = page.totalSize,
      property_keys = propertyKeys,
    )
  }

  private fun listEntitiesQueryFields(request: ListEntitiesRequest): List<String> = buildList {
    if (request.dataset_id.isNotBlank()) add("dataset_id")
    if (request.s2_cell_tokens.isNotEmpty()) add("s2_cell_tokens")
    if (request.include_deleted) add("include_deleted")
    if (request.bbox != null) add("bbox")
    if (request.filter.isNotEmpty()) add("filter")
    if (request.queue_filter_mode != QueueFilterMode.QUEUE_FILTER_MODE_UNSPECIFIED) {
      add("queue_filter_mode")
    }
    if (request.order_by.isNotBlank()) add("order_by")
    if (request.offset != 0) add("offset")
    if (request.entity_ids.isNotEmpty()) add("entity_ids")
    if (request.include_property_keys) add("include_property_keys")
  }

  private fun applyEntityFieldMask(
    items: List<EntityRecord>,
    fieldMask: List<String>,
  ): List<EntityRecord> =
    if (fieldMask.isEmpty()) items else items.map { EntityFieldMask.apply(it, fieldMask) }

  private suspend fun listEntitiesByIds(
    request: ListEntitiesRequest,
    caller: Caller?,
    matchesEntity: (EntityRecord) -> Boolean,
  ): ListEntitiesResponse {
    val rows = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.checkNotTrashed(request.survey_id)
      val byId =
        r.entitiesByIds(request.survey_id, request.entity_ids, request.include_deleted)
          .filter { matchesEntity(it) && visible(it.audit_info?.created_by.orEmpty()) }
          .associateBy { it.entity_id }
      request.entity_ids.distinct().mapNotNull { byId[it] }
    }
    val propertyKeys =
      if (request.include_property_keys) distinctPropertyKeys(rows) else emptyList()
    val entities =
      if (request.field_mask.isEmpty()) rows
      else rows.map { EntityFieldMask.apply(it, request.field_mask) }
    return ListEntitiesResponse(
      entities = entities,
      total_size = entities.size,
      property_keys = propertyKeys,
    )
  }

  private fun distinctPropertyKeys(entities: List<EntityRecord>): List<String> {
    val keys = LinkedHashSet<String>()
    for (entity in entities) {
      keys.addAll(entity.properties.keys)
    }
    return keys.toList()
  }

  // Placeholder until entities are indexed by S2 cell: one fixed cluster holding every visible
  // entity of the dataset, or none.
  override suspend fun ListEntityClusters(
    request: ListEntityClustersRequest
  ): ListEntityClustersResponse {
    val caller = currentCaller()
    val entities = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.entities(request.survey_id, request.dataset_id, includeDeleted = false).filter {
        visible(it.audit_info?.created_by.orEmpty())
      }
    }
    if (entities.isEmpty()) return ListEntityClustersResponse()
    val cluster =
      EntityCluster(
        s2_cell_token = "10d96b",
        centroid = Position(longitude = 36.9498, latitude = -0.4192),
        total_count = entities.size,
      )
    return ListEntityClustersResponse(clusters = listOf(cluster))
  }

  override suspend fun GetSubmission(request: GetSubmissionRequest): GetSubmissionResponse {
    val caller = currentCaller()
    val sub =
      store.read { r ->
        val visible = Access(r, caller).dataVisibility(request.survey_id)
        r.submission(request.survey_id, request.submission_id)?.takeIf {
          visible(it.audit_info?.created_by.orEmpty())
        }
      } ?: SubmissionRecord(survey_id = request.survey_id, submission_id = request.submission_id)
    return GetSubmissionResponse(submission = sub)
  }

  override suspend fun ListSubmissions(request: ListSubmissionsRequest): ListSubmissionsResponse {
    val queryFieldsSet = buildList {
      if (request.form_id.isNotBlank()) add("form_id")
      if (request.entity_id.isNotBlank()) add("entity_id")
      if (request.include_deleted) add("include_deleted")
      if (request.filter.isNotEmpty()) add("filter")
      if (request.order_by.isNotBlank()) add("order_by")
    }
    val query =
      orderedQuery(
        orderBy = request.order_by,
        commitTimestamp = SubmissionRecord::commit_timestamp,
        stringKeys = mapOf("collector_name" to SubmissionRecord::collector_name),
        fingerprint =
          "form_id=${request.form_id}|entity_id=${request.entity_id}|" +
            "include_deleted=${request.include_deleted}|filter=${request.filter.toSortedMap()}|" +
            "order_by=${request.order_by.trim().lowercase()}",
      ) { sub ->
        (request.form_id.isBlank() || sub.form_id == request.form_id) &&
          (request.entity_id.isBlank() || sub.entity_id == request.entity_id) &&
          (request.include_deleted || !sub.is_deleted) &&
          request.filter.all { (key, value) ->
            when (key) {
              "collector_email" -> sub.collector_email == value
              "collector_name" -> sub.collector_name == value
              else -> true
            }
          }
      }
    val caller = currentCaller()
    val page = store.read { r ->
      val visible = Access(r, caller).dataVisibility(request.survey_id)
      r.checkNotTrashed(request.survey_id)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "submissions|survey_id=${request.survey_id}",
        changedAfter = { after, limit ->
          r.submissionsChangedAfter(request.survey_id, after, limit)
        },
        queryRecords = {
          r.submissions(request.survey_id, request.form_id, request.include_deleted)
        },
        commitTimestamp = SubmissionRecord::commit_timestamp,
        id = SubmissionRecord::submission_id,
        query = query,
        queryFieldsSet = queryFieldsSet,
        watermark = r.snapshotTimestamp,
        accessEpoch = visible.key,
        visible = { visible(it.audit_info?.created_by.orEmpty()) },
      )
    }
    return ListSubmissionsResponse(
      submissions = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun MutateData(request: MutateDataRequest): MutateDataResponse {
    val surveyId = request.survey_id
    val scope = "surveys/$surveyId/data"
    val caller = currentCaller()
    val committed = store.write { tx ->
      val access = Access(tx, caller)
      val role = access.requireSurvey(surveyId, SurveyRole.DATA_COLLECTOR)
      // Replays are recognized in the transaction that records new mutations as applied, so
      // concurrent attempts of one mutation apply it once.
      val replays = ReplayFilter(tx, scope)
      val isReplay =
        request.mutations.map { replays.isReplay(MutationIdentity(it.client_id, it.client_seq)) }
      // New records are attributed to the authenticated caller.
      val creator = caller?.takeUnless { it.isDefault }?.userId
      val applier = DataMutationApplier(tx, surveyId, access, role, creator)
      if (isReplay.all { it }) {
        // Every mutation is a replay: report the records' current state without writing.
        return@write AppliedDataMutations(
          results = request.mutations.map { applier.duplicate(it) to false },
          entitiesChanged = false,
          enrichmentCandidates = emptyList(),
          geoIdCandidates = emptyList(),
        )
      }
      checkNotClosed(tx.survey(surveyId), ClosedSurveyRefusal.DATA_CHANGES)
      val applied = mutableListOf<MutationIdentity>()
      val results =
        request.mutations.zip(isReplay).map { (mutation, replayed) ->
          if (replayed) {
            applier.duplicate(mutation) to false
          } else {
            applier.apply(mutation).also {
              applied += MutationIdentity(mutation.client_id, mutation.client_seq)
            } to true
          }
        }
      val entitiesChanged = applier.writeChanges()
      val enrichmentCandidates = applier.enrichmentCandidates()
      val geoIdCandidates = applier.geoIdCandidates()
      tx.recordApplied(scope, identities = applied)
      AppliedDataMutations(results, entitiesChanged, enrichmentCandidates, geoIdCandidates)
    }
    val applied = committed.value
    val results = applied.results
    if (results.none { (_, written) -> written }) {
      return MutateDataResponse(
        results = results.map { it.first },
        commit_timestamp = store.watermark(),
      )
    }
    val ts = committed.commitTimestamp
    if (applied.entitiesChanged) {
      topicNotifier.notifySurveyEntitiesChanged(surveyId, ts)
    }
    if (applied.enrichmentCandidates.isNotEmpty()) {
      entityEnricher.enrichEntities(applied.enrichmentCandidates)
    }
    if (applied.geoIdCandidates.isNotEmpty()) {
      geoIdRegistrar.registerEntities(applied.geoIdCandidates)
    }
    return MutateDataResponse(
      results =
        results.map { (result, written) ->
          if (!written) result
          else
            result.copy(
              entity = result.entity?.copy(commit_timestamp = ts),
              submission = result.submission?.copy(commit_timestamp = ts),
              commit_timestamp = ts,
            )
        },
      commit_timestamp = ts,
    )
  }

  override suspend fun AcquirePlotLock(request: AcquirePlotLockRequest) = plotLocks.acquire(request)

  override suspend fun ReleasePlotLock(request: ReleasePlotLockRequest) = plotLocks.release(request)

  // Media storage isn't implemented yet: these URLs follow the planned layout (one object per
  // survey and content hash) on a placeholder host.
  override suspend fun PrepareMediaUpload(
    request: PrepareMediaUploadRequest
  ): PrepareMediaUploadResponse {
    store.requireSurveyRole(request.survey_id, SurveyRole.DATA_COLLECTOR)
    return PrepareMediaUploadResponse(
      upload_url =
        "https://storage.collect-one.example.org/upload/${request.survey_id}/${request.sha256_hash}",
      media_uri = "gs://collect-one-media/${request.survey_id}/${request.sha256_hash}",
      already_exists = false,
    )
  }

  override suspend fun GetMediaDownloadUrl(
    request: GetMediaDownloadUrlRequest
  ): GetMediaDownloadUrlResponse {
    store.requireSurveyRole(request.survey_id, SurveyRole.VIEWER)
    return GetMediaDownloadUrlResponse(
      download_url =
        "https://storage.collect-one.example.org/download/${request.survey_id}/${request.media_uri}"
    )
  }

  override suspend fun RecordImpactEvents(request: RecordImpactEventsRequest) =
    reports.recordImpactEvents(request)

  override suspend fun ListImpactEvents(request: ListImpactEventsRequest) =
    reports.listImpactEvents(request)

  override suspend fun GetImpactAggregates(request: GetImpactAggregatesRequest) =
    reports.impactAggregates(request)

  override suspend fun ListAuditRecords(request: ListAuditRecordsRequest) =
    reports.listAuditRecords(request)

  override suspend fun ListPlaces(request: ListPlacesRequest) = reports.listPlaces(request)

  override suspend fun ListMutationLogs(request: ListMutationLogsRequest) =
    reports.listMutationLogs(request)

  override suspend fun GetAppliedMutations(
    request: GetAppliedMutationsRequest
  ): GetAppliedMutationsResponse {
    require(request.client_id.isNotBlank()) { "client_id is required" }
    val scopes = request.scopes.toSet()
    return GetAppliedMutationsResponse(
      watermarks =
        store
          .read { it.appliedWatermarks(request.client_id) }
          .filter { scopes.isEmpty() || it.scope in scopes }
    )
  }
}

private data class AppliedDataMutations(
  val results: List<Pair<DataMutationResult, Boolean>>,
  val entitiesChanged: Boolean,
  val enrichmentCandidates: List<EntityEnrichmentCandidate>,
  val geoIdCandidates: List<GeoIdRegistrationCandidate>,
)

/** Fails with `PERMISSION_DENIED` unless the caller has at least [role] on [surveyId]. */
internal suspend fun UserDataStore.requireSurveyRole(surveyId: String, role: SurveyRole) {
  val caller = currentCaller()
  read { r -> Access(r, caller).requireSurvey(surveyId, role) }
}
