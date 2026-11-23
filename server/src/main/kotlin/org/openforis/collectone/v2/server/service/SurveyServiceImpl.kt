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

import com.squareup.wire.Instant
import io.grpc.Status
import io.grpc.StatusException
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AcceptSurveyInviteResponse
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsResponse
import openforis.collectone.v1.survey.EntityType
import openforis.collectone.v1.survey.GetSurveyAclRequest
import openforis.collectone.v1.survey.GetSurveyAclResponse
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsResponse
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.MutateSurveyResponse
import openforis.collectone.v1.survey.PeerDataVisibility
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SearchPublicSurveysResponse
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyOverviewResult
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.survey.SurveyThumbnailFeature
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.io.GeoGeometry
import org.openforis.collectone.v2.core.geo.io.GeoJsonReader
import org.openforis.collectone.v2.core.mutation.SurveyMutations
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.ListParams
import org.openforis.collectone.v2.server.data.SyncLists
import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.messaging.InviteNotifier
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier

/**
 * `SurveyService`: survey catalogs and overviews, survey definitions and ACLs, survey mutations
 * (including working copies, sharing, lifecycle, and quotas), and survey invitations.
 */
internal class SurveyServiceImpl(
  private val store: UserDataStore,
  private val syncLists: SyncLists,
  private val topicNotifier: SurveyTopicNotifier,
  private val inviteNotifier: InviteNotifier,
) : SurveyServiceServer {

  override suspend fun ListSurveyOverviews(
    request: ListSurveyOverviewsRequest
  ): ListSurveyOverviewsResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val page = store.read { r ->
      val access = Access(r, caller)
      // Surveys the caller can't list appear as tombstones, so a delta removes surveys whose
      // sharing
      // policy no longer grants access.
      val overviews =
        r.listCandidates(access, caller)
          .filter { s -> orgId.isBlank() || s.organization_id == orgId }
          .map { s ->
            val overview = r.overviewOf(s)
            if (access.listsSurvey(s, r.acl(s.survey_id))) overview
            else
              SurveyOverview(
                survey_id = s.survey_id,
                state = SurveyState.DELETED,
                commit_timestamp = overview.commit_timestamp,
                record_version = overview.record_version,
              )
          }
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "surveys|organization_id=$orgId",
        records = overviews,
        commitTimestamp = SurveyOverview::commit_timestamp,
        id = SurveyOverview::survey_id,
        query =
          commitOrderQuery(SurveyOverview::commit_timestamp) { it.state != SurveyState.DELETED },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
        accessEpoch = r.accessEpochOf(caller),
      )
    }
    return ListSurveyOverviewsResponse(
      overviews = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse {
    val caller = currentCaller()
    return store.read { r -> r.getSurvey(request, Access(r, caller)) }
  }

  private fun UserDataReader.getSurvey(
    request: GetSurveyRequest,
    access: Access,
  ): GetSurveyResponse {
    val r = this
    val role =
      access.requireSurvey(
        request.survey_id,
        if (request.working_copy != null) SurveyRole.SURVEY_ORGANIZER else SurveyRole.VIEWER,
      )
    r.checkNotTrashed(request.survey_id)
    val saved = r.survey(request.survey_id)
    val requestedVersion = request.saved_version
    val survey =
      when {
        request.working_copy != null -> r.workingCopy(request.survey_id)
        !requestedVersion.isNullOrBlank() -> r.surveyRevision(request.survey_id, requestedVersion)
        else -> saved
      }
    return GetSurveyResponse(
      survey = survey,
      acl = r.acl(request.survey_id)?.let { access.viewOf(it, role) },
    )
  }

  override suspend fun GetSurveyAcl(request: GetSurveyAclRequest): GetSurveyAclResponse {
    val caller = currentCaller()
    return store.read { r ->
      val access = Access(r, caller)
      val role = access.requireSurvey(request.survey_id, SurveyRole.VIEWER)
      GetSurveyAclResponse(
        acl =
          access.viewOf(
            r.acl(request.survey_id) ?: SurveyAcl(survey_id = request.survey_id),
            role,
          )
      )
    }
  }

  override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse {
    val surveyId = request.survey_id
    val caller = currentCaller()
    // Anyone signed in may create a survey; changing one takes an organizer.
    fun UserDataReader.requireOrganizerIfExists() {
      if (survey(surveyId) != null) {
        Access(this, caller).requireSurvey(surveyId, SurveyRole.SURVEY_ORGANIZER)
      }
    }
    val committed = store.write { tx ->
      tx.requireOrganizerIfExists()
      // Replays are recognized in the transaction that records new mutations as applied, so
      // concurrent attempts of one mutation apply it once.
      val toApply =
        tx.newMutations("surveys/$surveyId", request.mutations) {
          MutationIdentity(it.client_id, it.client_seq)
        }
      if (toApply.isEmpty()) return@write null
      SurveyMutationApplier(tx, request, caller, toApply).apply()
    }
    // Every mutation is a replay: report the current state.
    val applied = committed.value ?: return currentSurvey(surveyId, request)
    val ts = committed.commitTimestamp
    if (applied.definitionChanged) {
      topicNotifier.notifySurveyDefChanged(surveyId, applied.saved.record_version)
    }
    if (applied.invites.isNotEmpty()) {
      inviteNotifier.notifySurveyInvites(applied.invites)
    }
    val survey = if (request.working_copy) applied.workingCopy ?: applied.saved else applied.saved
    return MutateSurveyResponse(
      survey_id = surveyId,
      survey = survey.copy(commit_timestamp = ts),
      acl = applied.acl.copy(commit_timestamp = ts),
      commit_timestamp = ts,
    )
  }

  /** The survey's current state, as `MutateSurvey` reports it when it changes nothing. */
  private suspend fun currentSurvey(
    surveyId: String,
    request: MutateSurveyRequest,
  ): MutateSurveyResponse {
    val response = store.read { r ->
      val existingSaved = r.survey(surveyId)
      MutateSurveyResponse(
        survey_id = surveyId,
        survey =
          if (request.working_copy) r.workingCopy(surveyId) ?: existingSaved else existingSaved,
        acl = r.acl(surveyId),
        commit_timestamp = existingSaved?.commit_timestamp,
      )
    }
    return response.commit_timestamp?.let { response }
      ?: response.copy(commit_timestamp = store.watermark())
  }

  override suspend fun AcceptSurveyInvite(
    request: AcceptSurveyInviteRequest
  ): AcceptSurveyInviteResponse {
    if (request.invite_token.isBlank()) {
      throw StatusException(
        Status.INVALID_ARGUMENT.withDescription("invite_token must not be blank")
      )
    }
    val caller = currentCaller()?.takeUnless { it.isDefault }
    val userId = callerUserId(request.user_id)
    val committed = store.write { tx ->
      val targetSurvey = tx.survey(request.survey_id)
      checkNotClosed(targetSurvey, ClosedSurveyRefusal.EDITS)
      val acl = tx.acl(request.survey_id) ?: SurveyAcl(survey_id = request.survey_id)
      val matchedEntry =
        acl.entries.firstOrNull {
          it.invite_token.isNotBlank() && it.invite_token == request.invite_token
        }
      if (
        matchedEntry != null && matchedEntry.invitation_status != AclEntry.InvitationStatus.ACCEPTED
      ) {
        val limits = tx.resolveEffectiveQuota(targetSurvey, targetSurvey?.organization_id)
        if (limits.max_surveys_per_user > 0) {
          val resolvedUserId = userId.ifBlank { matchedEntry.user_id }
          val resolvedEmail =
            caller?.normalizedEmail?.takeIf { caller.emailVerified && it.isNotBlank() }
              ?: matchedEntry.email.trim().lowercase()
          val candidateSurveyIds = buildSet {
            if (resolvedUserId.isNotBlank()) addAll(tx.surveyIdsWithAclUser(resolvedUserId))
            if (resolvedEmail.isNotBlank()) addAll(tx.surveyIdsWithAclEmail(resolvedEmail))
          }
          val joinedCount = candidateSurveyIds.count { sid ->
            if (sid == request.survey_id) return@count false
            val s = tx.survey(sid) ?: return@count false
            if (s.state == SurveyState.DELETED || s.trash_time != null) return@count false
            val sAcl = tx.acl(sid) ?: return@count false
            sAcl.entries.any { e ->
              e.invitation_status == AclEntry.InvitationStatus.ACCEPTED &&
                ((resolvedUserId.isNotBlank() && e.user_id == resolvedUserId) ||
                  (resolvedEmail.isNotBlank() && e.email.trim().lowercase() == resolvedEmail))
            }
          }
          if (joinedCount >= limits.max_surveys_per_user) {
            throw StatusException(
              Status.RESOURCE_EXHAUSTED.withDescription(
                "Survey membership quota exceeded: maximum surveys per user reached (${limits.max_surveys_per_user})"
              )
            )
          }
        }
      }
      val updatedEntries =
        acl.entries.map { entry ->
          if (entry.invite_token.isNotBlank() && entry.invite_token == request.invite_token) {
            entry.copy(
              invitation_status = AclEntry.InvitationStatus.ACCEPTED,
              invite_token = "",
              user_id = userId.ifBlank { entry.user_id },
              profile = request.profile ?: entry.profile,
            )
          } else {
            entry
          }
        }
      val access = AccessChanges()
      val written =
        tx.putAclWithRecordVersion(request.survey_id, acl.copy(entries = updatedEntries), access)
      tx.applyAccessChanges(access)
      written
    }
    val ts = committed.commitTimestamp
    topicNotifier.notifySurveyDefChanged(request.survey_id, committed.value.second.record_version)
    return AcceptSurveyInviteResponse(
      survey = committed.value.first?.copy(commit_timestamp = ts),
      acl = committed.value.second.copy(commit_timestamp = ts),
      commit_timestamp = ts,
    )
  }

  override suspend fun BatchGetSurveyOverviews(
    request: BatchGetSurveyOverviewsRequest
  ): BatchGetSurveyOverviewsResponse {
    require(request.survey_ids.size <= MAX_BATCH_SURVEY_IDS) {
      "At most $MAX_BATCH_SURVEY_IDS survey_ids per request"
    }
    val caller = currentCaller()
    return store.read { r ->
      val access = Access(r, caller)
      BatchGetSurveyOverviewsResponse(
        results =
          request.survey_ids.map { id ->
            val saved = r.survey(id)
            when {
              saved == null ->
                SurveyOverviewResult(survey_id = id, error_code = Status.Code.NOT_FOUND.value())
              // A purged survey's tombstone tells every former holder it's gone.
              saved.state != SurveyState.DELETED &&
                access.surveyRole(saved, r.acl(id)) == SurveyRole.NONE ->
                SurveyOverviewResult(
                  survey_id = id,
                  error_code = Status.Code.PERMISSION_DENIED.value(),
                )
              else -> SurveyOverviewResult(survey_id = id, overview = r.overviewOf(saved))
            }
          }
      )
    }
  }

  override suspend fun SearchPublicSurveys(
    request: SearchPublicSurveysRequest
  ): SearchPublicSurveysResponse {
    val q = request.query.trim().lowercase()
    val matching = store.read { r ->
      r.searchPublicSurveys(q)
        .filter { s ->
          r.acl(s.survey_id)?.sharing_policy == SharingPolicy.PUBLIC &&
            s.trash_time == null &&
            s.state != SurveyState.DELETED &&
            (q.isEmpty() ||
              s.title.lowercase().contains(q) ||
              s.description.lowercase().contains(q))
        }
        .sortedBy { it.survey_id }
        .map { s -> r.overviewOf(s) }
    }
    val (items, nextPageToken) = page(matching, request.page_size, request.page_token)
    return SearchPublicSurveysResponse(overviews = items, next_page_token = nextPageToken)
  }

  private val instantComparator =
    Comparator<Instant?> { a, b ->
      when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        a.epochSecond != b.epochSecond -> a.epochSecond.compareTo(b.epochSecond)
        else -> a.nano.compareTo(b.nano)
      }
    }

  /**
   * Builds the catalog overview of [surveyDef]. Its `commit_timestamp` is the later of the survey's
   * and [acl]'s, so access changes also reach `ListSurveyOverviews` delta polls. A trashed survey's
   * overview is a tombstone (`state == DELETED`) carrying `trash_time`, so lists drop it.
   */
  private fun UserDataReader.overviewOf(surveyDef: SurveyDef): SurveyOverview {
    val acl = acl(surveyDef.survey_id)
    val workingDef = workingCopy(surveyDef.survey_id)
    val effectiveFeatureDef =
      if (
        surveyDef.entity_datasets.isEmpty() &&
          workingDef != null &&
          workingDef.entity_datasets.isNotEmpty()
      ) {
        workingDef
      } else {
        surveyDef
      }
    val liveEntities = entities(surveyDef.survey_id, includeDeleted = false)
    val liveGeoCount = liveEntities.count(GeometryProtos::hasGeometry)
    val datasetGeoCount =
      effectiveFeatureDef.entity_datasets
        .filter { it.type == EntityType.GEOSPATIAL }
        .sumOf { it.rows.size }
    val resolvedEntityCount = maxOf(surveyDef.entity_count, liveGeoCount, datasetGeoCount)
    return SurveyOverview(
      survey_id = surveyDef.survey_id,
      title = surveyDef.title,
      description = surveyDef.description,
      organization_id = surveyDef.organization_id,
      owner_id = surveyDef.owner_id,
      state = if (surveyDef.trash_time != null) SurveyState.DELETED else surveyDef.state,
      trash_time = surveyDef.trash_time,
      version = surveyDef.version,
      saved_revision_number = surveyDef.saved_revision_number,
      location_label = surveyDef.location_label,
      is_downloaded = surveyDef.is_downloaded,
      thumbnail_theme = surveyDef.thumbnail_theme,
      entity_count = resolvedEntityCount,
      survey_area = surveyDef.map_config?.survey_area ?: workingDef?.map_config?.survey_area,
      outcome = surveyDef.outcome,
      closed_at = surveyDef.closed_at,
      commit_timestamp =
        listOfNotNull(surveyDef.commit_timestamp, acl?.commit_timestamp)
          .maxWithOrNull(instantComparator),
      record_version = maxOf(surveyDef.record_version, acl?.record_version ?: 0L),
      has_working_copy = workingDef != null,
      sharing_policy = acl?.sharing_policy ?: SharingPolicy.SHARING_POLICY_UNSPECIFIED,
      peer_data_visibility =
        acl?.peer_data_visibility ?: PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED,
      collector_consent_required = acl?.collector_consent_required ?: false,
      collector_consent_text = acl?.collector_consent_text.orEmpty(),
      default_role =
        acl?.default_role?.takeIf { it in SurveyMutations.SHARING_ROLES } ?: Role.DATA_COLLECTOR,
      quota_limits = surveyDef.quota_limits,
      thumbnail_features =
        if (surveyDef.trash_time != null) {
          emptyList()
        } else {
          thumbnailFeaturesOf(effectiveFeatureDef, liveEntities)
        },
    )
  }

  private fun thumbnailFeaturesOf(
    surveyDef: SurveyDef,
    liveEntities: List<EntityRecord>,
  ): List<SurveyThumbnailFeature> {
    val layers = surveyDef.map_config?.layers.orEmpty()
    val layersById = layers.associateBy { it.id }
    val layersByDatasetId =
      layers
        .mapNotNull { layer ->
          layer.entity_dataset_id?.takeIf { it.isNotBlank() }?.let { it to layer }
        }
        .toMap()
    val visibleLayerIds = layers.filter { it.visible_by_default }.map { it.id }.toSet()
    val visibleDatasetIds =
      layers
        .filter { it.visible_by_default }
        .mapNotNull { it.entity_dataset_id?.takeIf(String::isNotBlank) }
        .toSet()

    if (liveEntities.isEmpty()) {
      return thumbnailFeaturesFromDatasets(
        surveyDef = surveyDef,
        layersById = layersById,
        layersByDatasetId = layersByDatasetId,
        visibleLayerIds = visibleLayerIds,
        visibleDatasetIds = visibleDatasetIds,
      )
    }

    val sourceEntities =
      if (visibleLayerIds.isEmpty() && visibleDatasetIds.isEmpty()) {
        liveEntities
      } else {
        liveEntities
          .filter { entity ->
            entity.layer_id in visibleLayerIds ||
              (entity.dataset_id.isNotBlank() && entity.dataset_id in visibleDatasetIds)
          }
          .ifEmpty { liveEntities }
      }

    return sourceEntities
      .mapNotNull { entity ->
        val geometry = GeometryProtos.of(entity) ?: return@mapNotNull null
        val layer = layersById[entity.layer_id] ?: layersByDatasetId[entity.dataset_id]
        val resolvedColor =
          parseCssHexColor(layer?.default_style?.color)
            ?: entity.color_hex.takeIf { it != 0L }
            ?: DEFAULT_THUMBNAIL_COLOR_HEX
        SurveyThumbnailFeature(
          entity_id = entity.entity_id,
          geometry = geometry,
          color_hex = 0xFF000000L or (resolvedColor and 0xFFFFFFL),
        )
      }
      .sampleEvenly(MAX_OVERVIEW_THUMBNAIL_FEATURES)
  }

  private fun thumbnailFeaturesFromDatasets(
    surveyDef: SurveyDef,
    layersById: Map<String, openforis.collectone.v1.survey.LayerDef>,
    layersByDatasetId: Map<String, openforis.collectone.v1.survey.LayerDef>,
    visibleLayerIds: Set<String>,
    visibleDatasetIds: Set<String>,
  ): List<SurveyThumbnailFeature> {
    val geoDatasets =
      surveyDef.entity_datasets.filter {
        it.type == EntityType.GEOSPATIAL && it.rows.isNotEmpty()
      }
    if (geoDatasets.isEmpty()) return emptyList()
    val sourceDatasets =
      if (visibleLayerIds.isEmpty() && visibleDatasetIds.isEmpty()) {
        geoDatasets
      } else {
        geoDatasets
          .filter { ds -> ds.id in visibleDatasetIds || ds.id in visibleLayerIds }
          .ifEmpty { geoDatasets }
      }
    val candidateRows = sourceDatasets.flatMap { dataset ->
      val layer = layersByDatasetId[dataset.id] ?: layersById[dataset.id]
      val resolvedColor =
        parseCssHexColor(layer?.default_style?.color)
          ?: parseCssHexColor(dataset.default_style?.color)
          ?: DEFAULT_THUMBNAIL_COLOR_HEX
      val opaqueColor = 0xFF000000L or (resolvedColor and 0xFFFFFFL)
      dataset.rows.mapIndexedNotNull { index, row ->
        val entityId = row.row_key.ifBlank { "${dataset.id}_$index" }
        val rowGeom = row.geometry?.takeIf { GeometryProtos.typeOf(it) != null }
        if (rowGeom != null) {
          SurveyThumbnailFeature(
            entity_id = entityId,
            geometry = rowGeom,
            color_hex = opaqueColor,
          )
        } else {
          val geoJson =
            row.values["geometry"]?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
          val parsed =
            GeoJsonReader.read(geoJson).features.firstOrNull()?.geometry
              ?: return@mapIndexedNotNull null
          SurveyThumbnailFeature(
            entity_id = entityId,
            geometry = parsed.toProtoGeometry(),
            color_hex = opaqueColor,
          )
        }
      }
    }
    return candidateRows.sampleEvenly(MAX_OVERVIEW_THUMBNAIL_FEATURES)
  }

  private fun GeoGeometry.toProtoGeometry(): Geometry =
    when (this) {
      is GeoGeometry.Point -> GeometryProtos.point(latitude = coord.lat, longitude = coord.lng)
      is GeoGeometry.LineString ->
        GeometryProtos.lineString(coords.map { GeometryProtos.position(it.lat, it.lng) })
      is GeoGeometry.Polygon ->
        GeometryProtos.polygon(listOf(ring.map { GeometryProtos.position(it.lat, it.lng) }))
    }

  private fun <T> List<T>.sampleEvenly(maxCount: Int): List<T> {
    if (maxCount <= 0) return emptyList()
    if (size <= maxCount) return this
    if (maxCount == 1) return listOf(first())
    return List(maxCount) { index ->
      val sourceIndex = ((index.toLong() * (size - 1)) / (maxCount - 1)).toInt()
      this[sourceIndex]
    }
  }

  private fun parseCssHexColor(raw: String?): Long? {
    val hex = raw?.trim()?.removePrefix("#")?.takeIf { it.length == 6 || it.length == 8 }
    val parsed = hex?.toLongOrNull(16) ?: return null
    return 0xFF000000L or (parsed and 0xFFFFFFL)
  }

  companion object {
    /** Largest number of IDs served by one `BatchGetSurveyOverviews` request. */
    const val MAX_BATCH_SURVEY_IDS: Int = 100

    /** Maximum number of map features attached to a catalog `SurveyOverview`. */
    const val MAX_OVERVIEW_THUMBNAIL_FEATURES: Int = 150

    private const val DEFAULT_THUMBNAIL_COLOR_HEX: Long = 0xFF2E7D32L
  }
}
