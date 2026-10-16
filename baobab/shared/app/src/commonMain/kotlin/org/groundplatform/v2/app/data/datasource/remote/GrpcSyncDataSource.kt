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
package org.groundplatform.v2.app.data.datasource.remote

import com.squareup.wire.Instant
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ListMutationLogsRequest
import groundplatform.v2.data.ListPlacesRequest
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.data.MutateDataResponse
import groundplatform.v2.data.RecordImpactEventsRequest
import groundplatform.v2.data.StreamEntitiesRequest
import groundplatform.v2.data.StreamImpactEventsRequest
import groundplatform.v2.data.StreamSubmissionsRequest
import groundplatform.v2.data.SubmissionMutation
import groundplatform.v2.data.SubmissionPatch
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.StreamLibrariesRequest
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.GetSurveyResponse
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.StreamOrganizationsRequest
import groundplatform.v2.survey.StreamSurveyOverviewsRequest
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyState
import kotlinx.coroutines.flow.toList
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.StoredPreferences
import org.groundplatform.v2.app.data.datasource.local.store.SyncWatermarks
import org.groundplatform.v2.app.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.app.domain.model.MutationLogItem
import org.groundplatform.v2.app.domain.model.MutationOperationKind
import org.groundplatform.v2.app.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.UserSettings

/** The saved revision of a survey (with its ACL) and the organizer's working copy, if any. */
private data class SurveyRevisions(val saved: GetSurveyResponse, val workingCopy: SurveyDef?)

/**
 * Remote synchronization data source for clients that keep an offline copy (mobile). Runs bounded
 * delta streams (`Stream*` with `since_timestamp` watermarks) against [CollectOneGrpcClient] and
 * applies the deltas atomically to [LocalStore]. Foreground freshness comes from repeating
 * [pullIncrementalCatchUp] on a timer (`docs/technical/sync-engine.md`, Foreground Polling).
 */
class GrpcSyncDataSource(val grpcClient: CollectOneGrpcClient) {
  private var watermarks = SyncWatermarks()

  /** Current sync watermarks across all resource streams. */
  fun currentWatermarks(): SyncWatermarks = watermarks

  /** Forgets all watermarks, so the next pull starts from the beginning. */
  fun resetWatermarks() {
    watermarks = SyncWatermarks()
  }

  /**
   * Performs an initial or full catch-up pull from the gRPC backend service using
   * `StreamSurveyOverviews` for the survey catalog, `GetSurvey` for full survey definitions, and
   * bounded delta streams (`StreamOrganizations`, `StreamLibraries`, `StreamEntities`,
   * `StreamSubmissions`) to populate [store] inside a single atomic transaction.
   */
  suspend fun seedStoreFromGrpc(
    store: LocalStore,
    defaultActiveSurveyId: String,
    seedVersion: Int,
  ) {
    watermarks = SyncWatermarks()
    val orgDeltas =
      grpcClient.streamOrganizations(StreamOrganizationsRequest(include_deleted = false)).toList()
    val libDeltas =
      grpcClient.streamLibraries(StreamLibrariesRequest(include_deleted = false)).toList()
    val surveyOverviews =
      grpcClient
        .streamSurveyOverviews(StreamSurveyOverviewsRequest(include_deleted = false))
        .toList()
    val fullSurveys = surveyOverviews.map { overview -> fetchSurveyRevisions(overview) }
    val entityDeltas =
      grpcClient.streamEntities(StreamEntitiesRequest(include_deleted = false)).toList()
    val submissionDeltas =
      grpcClient.streamSubmissions(StreamSubmissionsRequest(include_deleted = false)).toList()
    val placesResponse = grpcClient.listPlaces(ListPlacesRequest())
    val mutationLogsResponse = grpcClient.listMutationLogs(ListMutationLogsRequest())

    store.transaction {
      applySnapshotToTransaction(
        orgDeltas = orgDeltas,
        libDeltas = libDeltas,
        fullSurveys = fullSurveys,
        entityDeltas = entityDeltas,
        submissionDeltas = submissionDeltas,
        places = placesResponse.places.map(GrpcProtoMapper::placeFromProto),
        mutationLogs =
          mutationLogsResponse.mutation_logs.map(GrpcProtoMapper::mutationLogFromProto),
        defaultActiveSurveyId = defaultActiveSurveyId,
        seedVersion = seedVersion,
      )
    }

    // Advance watermarks to the highest commit_timestamp observed per stream.
    watermarks =
      SyncWatermarks(
        organizationsWatermark = orgDeltas.lastOrNull()?.commit_timestamp,
        librariesWatermark = libDeltas.lastOrNull()?.commit_timestamp,
        surveysWatermark = surveyOverviews.lastOrNull()?.commit_timestamp,
        entitiesWatermarkBySurvey =
          entityDeltas
            .groupBy { it.survey_id }
            .mapValues { (_, list) -> list.last().commit_timestamp!! },
        submissionsWatermarkBySurvey =
          submissionDeltas
            .groupBy { it.survey_id }
            .mapValues { (_, list) -> list.last().commit_timestamp!! },
      )
  }

  /**
   * Executes an incremental catch-up pull from the gRPC backend using the current
   * `lastSyncedTimestamp` watermarks (`since_timestamp`), applying all new, updated, and
   * soft-deleted tombstone records (`commit_timestamp > since_timestamp`) to [store] and advancing
   * each watermark.
   */
  suspend fun pullIncrementalCatchUp(
    store: LocalStore,
    surveyId: String? = null,
    untilTimestamp: Instant? = null,
  ): SyncWatermarks {
    val orgDeltas =
      grpcClient
        .streamOrganizations(
          StreamOrganizationsRequest(
            since_timestamp = watermarks.organizationsWatermark,
            until_timestamp = untilTimestamp,
            include_deleted = true,
          )
        )
        .toList()
    val libDeltas =
      grpcClient
        .streamLibraries(
          StreamLibrariesRequest(
            since_timestamp = watermarks.librariesWatermark,
            until_timestamp = untilTimestamp,
            include_deleted = true,
          )
        )
        .toList()
    val surveyOverviews =
      grpcClient
        .streamSurveyOverviews(
          StreamSurveyOverviewsRequest(
            since_timestamp = watermarks.surveysWatermark,
            until_timestamp = untilTimestamp,
            include_deleted = true,
          )
        )
        .toList()
        .filter { surveyId == null || it.survey_id == surveyId }

    val fullSurveyResponsesById =
      surveyOverviews
        .filter { it.state != SurveyState.DELETED }
        .associate { overview ->
          overview.survey_id to fetchSurveyRevisions(overview)
        }

    val targetSurveyIds =
      if (surveyId != null) {
        listOf(surveyId)
      } else {
        val existingSurveyIds = store.transaction { surveys().map { it.id } }
        (existingSurveyIds + surveyOverviews.map { it.survey_id }).distinct()
      }

    val entityDeltasBySurvey = mutableMapOf<String, List<EntityRecord>>()
    val subDeltasBySurvey = mutableMapOf<String, List<SubmissionRecord>>()
    for (sid in targetSurveyIds) {
      val entSince = watermarks.entitiesWatermarkBySurvey[sid]
      val ents =
        grpcClient
          .streamEntities(
            StreamEntitiesRequest(
              survey_id = sid,
              since_timestamp = entSince,
              until_timestamp = untilTimestamp,
              include_deleted = true,
            )
          )
          .toList()
      if (ents.isNotEmpty()) entityDeltasBySurvey[sid] = ents

      val subSince = watermarks.submissionsWatermarkBySurvey[sid]
      val subs =
        grpcClient
          .streamSubmissions(
            StreamSubmissionsRequest(
              survey_id = sid,
              since_timestamp = subSince,
              until_timestamp = untilTimestamp,
              include_deleted = true,
            )
          )
          .toList()
      if (subs.isNotEmpty()) subDeltasBySurvey[sid] = subs
    }

    val impactDeltas =
      grpcClient
        .streamImpactEvents(
          StreamImpactEventsRequest(
            survey_id = surveyId.orEmpty(),
            since_timestamp = watermarks.impactEventsWatermark,
            until_timestamp = untilTimestamp,
          )
        )
        .toList()

    store.transaction {
      // Apply organization deltas
      for (orgProto in orgDeltas) {
        if (orgProto.is_deleted) {
          deleteOrganization(orgProto.organization_id)
        } else {
          upsertOrganization(GrpcProtoMapper.organizationFromProto(orgProto))
        }
      }

      // Apply library deltas
      for (libProto in libDeltas) {
        if (!libProto.is_deleted) {
          putLibrary(GrpcProtoMapper.libraryFromProto(libProto))
        }
      }

      // Apply entity & submission deltas per survey
      for (sid in (entityDeltasBySurvey.keys + subDeltasBySurvey.keys)) {
        val entDeltas = entityDeltasBySurvey[sid].orEmpty()
        val subDeltas = subDeltasBySurvey[sid].orEmpty()
        applyEntityAndSubmissionDeltas(sid, entDeltas, subDeltas)
      }

      // Apply survey overview & full survey updates
      for (overview in surveyOverviews) {
        if (overview.state == SurveyState.DELETED) {
          deleteSurvey(overview.survey_id)
        } else {
          val sid = overview.survey_id
          val fullResponse = fullSurveyResponsesById[sid]
          val surveyDef = fullResponse?.saved?.survey ?: continue
          val forms = GrpcProtoMapper.formsFromProto(surveyDef)
          val mapLayers = GrpcProtoMapper.mapLayersFromProto(surveyDef)
          val config = GrpcProtoMapper.surveyConfigFromProto(surveyDef)
          val currentEntities = entities(sid)
          val preview =
            GrpcProtoMapper.surveyPreviewFromOverviewProto(
              overview = overview,
              mapLayers = mapLayers,
              entities = currentEntities,
            )
          upsertSurvey(preview)
          putForms(sid, forms)
          putMapLayers(sid, mapLayers)
          putSurveyConfig(sid, config)
          if (fullResponse.workingCopy != null) {
            putSurveyEditorDraft(
              sid,
              GrpcProtoMapper.surveyEditorDraftFromProto(
                surveyDef = fullResponse.workingCopy,
                acl = fullResponse.saved.acl,
                surveyPreview = preview,
                forms = forms,
                config = config,
                mapLayers = mapLayers,
                entities = currentEntities,
              ),
            )
          }
        }
      }

      // Apply impact event deltas
      if (impactDeltas.isNotEmpty()) {
        val existingById = impactEvents().associateBy { it.id }.toMutableMap()
        for (evProto in impactDeltas) {
          val domainEv = GrpcProtoMapper.impactEventFromProto(evProto)
          existingById[domainEv.id] = domainEv
        }
        putImpactEvents(existingById.values.toList())
      }
    }

    // Update watermarks
    val updatedEntWatermarks = watermarks.entitiesWatermarkBySurvey.toMutableMap()
    entityDeltasBySurvey.forEach { (sid, list) ->
      list.lastOrNull()?.commit_timestamp?.let { updatedEntWatermarks[sid] = it }
    }
    val updatedSubWatermarks = watermarks.submissionsWatermarkBySurvey.toMutableMap()
    subDeltasBySurvey.forEach { (sid, list) ->
      list.lastOrNull()?.commit_timestamp?.let { updatedSubWatermarks[sid] = it }
    }

    watermarks =
      watermarks.copy(
        organizationsWatermark =
          orgDeltas.lastOrNull()?.commit_timestamp ?: watermarks.organizationsWatermark,
        librariesWatermark =
          libDeltas.lastOrNull()?.commit_timestamp ?: watermarks.librariesWatermark,
        surveysWatermark =
          surveyOverviews.lastOrNull()?.commit_timestamp ?: watermarks.surveysWatermark,
        entitiesWatermarkBySurvey = updatedEntWatermarks,
        submissionsWatermarkBySurvey = updatedSubWatermarks,
        impactEventsWatermark =
          impactDeltas.lastOrNull()?.commit_timestamp ?: watermarks.impactEventsWatermark,
      )
    return watermarks
  }

  /**
   * Fetches the latest saved revision of a survey and, only when [overview] reports unsaved edits
   * (`has_working_copy`), the organizer's working copy.
   */
  private suspend fun fetchSurveyRevisions(overview: SurveyOverview): SurveyRevisions {
    val surveyId = overview.survey_id
    return SurveyRevisions(
      saved = grpcClient.getSurvey(GetSurveyRequest(survey_id = surveyId)),
      workingCopy =
        if (overview.has_working_copy) {
          grpcClient.getSurvey(GetSurveyRequest(survey_id = surveyId, working_copy = true)).survey
        } else {
          null
        },
    )
  }

  /**
   * Pushes outbox mutations (`MutationLogItem`) to the gRPC backend via `MutateData` and returns
   * the backend `MutateDataResponse` with the authoritative server `commit_timestamp`.
   */
  suspend fun pushOutboxMutations(
    surveyId: String,
    items: List<MutationLogItem>,
  ): MutateDataResponse {
    val protoMutations = items.map { item ->
      when (item.operationKind) {
        MutationOperationKind.CREATE_SUBMISSION ->
          DataMutation(
            mutation_id = item.id,
            submission_mutation =
              SubmissionMutation(
                submission_id = item.submissionId ?: "sub-${item.id}",
                form_id = "form-default",
                base_revision = 0L,
                upsert =
                  SubmissionRecord(
                    submission_id = item.submissionId ?: "sub-${item.id}",
                    survey_id = surveyId,
                    form_id = "form-default",
                    entity_id = item.entityId,
                    timestamp_label = item.operationTimestamp,
                    collector_name = item.actorName,
                  ),
              ),
          )
        MutationOperationKind.UPDATE_SUBMISSION ->
          DataMutation(
            mutation_id = item.id,
            submission_mutation =
              SubmissionMutation(
                submission_id = item.submissionId ?: "sub-${item.id}",
                form_id = "form-default",
                base_revision = 1L,
                update = SubmissionPatch(),
              ),
          )
        MutationOperationKind.DELETE_SUBMISSION ->
          DataMutation(
            mutation_id = item.id,
            submission_mutation =
              SubmissionMutation(
                submission_id = item.submissionId ?: "sub-${item.id}",
                form_id = "form-default",
                base_revision = 1L,
                delete = true,
              ),
          )
        MutationOperationKind.CREATE_ENTITY ->
          DataMutation(
            mutation_id = item.id,
            entity_mutation =
              EntityMutation(
                entity_id = item.entityId,
                base_revision = 0L,
                upsert =
                  EntityRecord(
                    entity_id = item.entityId,
                    survey_id = surveyId,
                    label = item.targetLabel,
                  ),
              ),
          )
        MutationOperationKind.UPDATE_ENTITY ->
          DataMutation(
            mutation_id = item.id,
            entity_mutation =
              EntityMutation(
                entity_id = item.entityId,
                base_revision = 1L,
                update = EntityPatch(),
              ),
          )
        MutationOperationKind.DELETE_ENTITY ->
          DataMutation(
            mutation_id = item.id,
            entity_mutation =
              EntityMutation(
                entity_id = item.entityId,
                base_revision = 1L,
                delete = true,
              ),
          )
        MutationOperationKind.UPLOAD_MEDIA -> DataMutation(mutation_id = item.id)
      }
    }
    return grpcClient.mutateData(
      MutateDataRequest(
        survey_id = surveyId,
        mutations = protoMutations,
      )
    )
  }

  /** Uploads offline impact events via `RecordImpactEvents`. */
  suspend fun pushImpactEvents(events: List<org.groundplatform.v2.app.domain.model.ImpactEvent>) {
    if (events.isEmpty()) return
    grpcClient.recordImpactEvents(
      RecordImpactEventsRequest(events = events.map { GrpcProtoMapper.impactEventToProto(it) })
    )
  }

  private fun LocalStoreTransaction.applyEntityAndSubmissionDeltas(
    surveyId: String,
    entDeltas: List<EntityRecord>,
    subDeltas: List<SubmissionRecord>,
  ) {
    val existingEntities = entities(surveyId)
    val existingStandalone = standaloneSubmissions(surveyId)
    val existingGeoms = submissionGeometries(surveyId)

    val allSubmissionsById = LinkedHashMap<String, SubmissionPreviewItem>()
    existingEntities.flatMap { it.submissions }.forEach { allSubmissionsById[it.id] = it }
    existingStandalone.forEach { allSubmissionsById[it.id] = it }

    val allGeomsById = LinkedHashMap<String, SubmissionGeometryPolygon>()
    existingGeoms.forEach { allGeomsById[it.id] = it }

    for (subProto in subDeltas) {
      if (subProto.is_deleted) {
        allSubmissionsById.remove(subProto.submission_id)
        val toRemoveGeoms =
          allGeomsById.values.filter { it.submissionId == subProto.submission_id }.map { it.id }
        toRemoveGeoms.forEach { allGeomsById.remove(it) }
      } else {
        allSubmissionsById[subProto.submission_id] = GrpcProtoMapper.submissionFromProto(subProto)
        subProto.geometry_polygons.forEach { polyProto ->
          val poly = GrpcProtoMapper.geometryPolygonFromProto(polyProto)
          allGeomsById[poly.id] = poly
        }
      }
    }

    val linkedSubsByEntityId =
      allSubmissionsById.values.filter { it.entityId.isNotBlank() }.groupBy { it.entityId }
    val standaloneList = allSubmissionsById.values.filter { it.entityId.isBlank() }

    val entityMap =
      LinkedHashMap<String, org.groundplatform.v2.app.domain.model.GeospatialEntityItem>()
    existingEntities.forEach { ent ->
      entityMap[ent.id] = ent.copy(submissions = linkedSubsByEntityId[ent.id].orEmpty())
    }

    for (entProto in entDeltas) {
      if (entProto.is_deleted) {
        entityMap.remove(entProto.entity_id)
      } else {
        val subs = linkedSubsByEntityId[entProto.entity_id].orEmpty()
        entityMap[entProto.entity_id] = GrpcProtoMapper.entityFromProto(entProto, subs)
      }
    }

    putEntities(surveyId, entityMap.values.toList())
    putStandaloneSubmissions(surveyId, standaloneList)
    putSubmissionGeometries(surveyId, allGeomsById.values.toList())
  }

  private fun LocalStoreTransaction.applySnapshotToTransaction(
    orgDeltas: List<Organization>,
    libDeltas: List<OrganizationLibraryRecord>,
    fullSurveys: List<SurveyRevisions>,
    entityDeltas: List<EntityRecord>,
    submissionDeltas: List<SubmissionRecord>,
    places: List<org.groundplatform.v2.app.domain.model.SurveyPlaceItem>,
    mutationLogs: List<MutationLogItem>,
    defaultActiveSurveyId: String,
    seedVersion: Int,
  ) {
    orgDeltas.forEach { upsertOrganization(GrpcProtoMapper.organizationFromProto(it)) }
    libDeltas.forEach { putLibrary(GrpcProtoMapper.libraryFromProto(it)) }

    val entitiesBySurvey = entityDeltas.groupBy { it.survey_id }
    val submissionsBySurvey = submissionDeltas.groupBy { it.survey_id }
    var tilePackages = emptyList<org.groundplatform.v2.app.domain.model.OfflineTilePackageItem>()

    for (fullSurvey in fullSurveys) {
      val surveyDef = fullSurvey.saved.survey ?: continue
      val surveyId = surveyDef.survey_id
      val forms = GrpcProtoMapper.formsFromProto(surveyDef)
      val mapLayers = GrpcProtoMapper.mapLayersFromProto(surveyDef)
      val config = GrpcProtoMapper.surveyConfigFromProto(surveyDef)
      val surveySubs = submissionsBySurvey[surveyId].orEmpty()
      val domainSubs = surveySubs.map(GrpcProtoMapper::submissionFromProto)
      val linkedByEntityId = domainSubs.filter { it.entityId.isNotBlank() }.groupBy { it.entityId }
      val standaloneSubs = domainSubs.filter { it.entityId.isBlank() }
      val geometries = surveySubs.flatMap { sub ->
        sub.geometry_polygons.map(GrpcProtoMapper::geometryPolygonFromProto)
      }
      val entities =
        entitiesBySurvey[surveyId].orEmpty().map { entProto ->
          GrpcProtoMapper.entityFromProto(
            proto = entProto,
            submissions = linkedByEntityId[entProto.entity_id].orEmpty(),
          )
        }

      val preview =
        GrpcProtoMapper.surveyPreviewFromProto(
          surveyDef = surveyDef,
          acl = fullSurvey.saved.acl,
          mapLayers = mapLayers,
          entities = entities,
        )
      upsertSurvey(preview)
      putForms(surveyId, forms)
      putMapLayers(surveyId, mapLayers)
      putEntities(surveyId, entities)
      putStandaloneSubmissions(surveyId, standaloneSubs)
      putSubmissionGeometries(surveyId, geometries)
      putSurveyConfig(surveyId, config)

      val pkgs = GrpcProtoMapper.offlineTilePackagesFromProto(surveyDef)
      if (pkgs.isNotEmpty()) {
        tilePackages = pkgs
      }

      if (fullSurvey.workingCopy != null) {
        putSurveyEditorDraft(
          surveyId,
          GrpcProtoMapper.surveyEditorDraftFromProto(
            surveyDef = fullSurvey.workingCopy,
            acl = fullSurvey.saved.acl,
            surveyPreview = preview,
            forms = forms,
            config = config,
            mapLayers = mapLayers,
            entities = entities,
          ),
        )
      }
    }

    putMutations(mutationLogs)
    putPlaces(places)
    putOfflineTilePackages(tilePackages)
    putPreferences(
      StoredPreferences(
        activeSurveyId = defaultActiveSurveyId,
        userSettings =
          UserSettings(
            language = "en",
            measurementUnits = MeasurementUnitSystem.METRIC,
            shouldUploadPhotosOnWifiOnly = false,
          ),
        uploadedMediaCacheSizeLabel = "38.4 MB",
        uploadedMediaFileCount = 27,
        seedVersion = seedVersion,
      )
    )
  }
}
