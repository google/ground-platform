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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent as ImpactEventProto
import openforis.collectone.v1.data.ImpactEventType as ImpactEventTypeProto
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.PlaceRecord
import openforis.collectone.v1.data.PlaceSource as PlaceSourceProto
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import openforis.collectone.v1.data.SubmissionGeometryPolygonDef
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.PlaceSource
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Bidirectional mapper between Wire-generated data record Protocol Buffer messages
 * (`openforis.collectone.v1.data.*`: entities, submissions, places, mutation logs and impact
 * events) and `shared/app` domain models stored in `LocalStore`.
 */
object ProtoRecordMapper {
  // ---------------------------------------------------------------------------
  // Entities & Submissions ↔ EntityRecord & SubmissionRecord
  // ---------------------------------------------------------------------------

  fun entityToProto(
    surveyId: String,
    entity: GeospatialEntityItem,
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): EntityRecord =
    EntityRecord(
      entity_id = entity.id,
      dataset_id = entity.datasetId,
      revision = 1L,
      label = entity.label,
      properties = entity.properties.mapValues { (_, v) -> TypedValue(string_value = v) },
      is_field_created = entity.isFieldCreated,
      is_deleted = isDeleted,
      survey_id = surveyId,
      commit_timestamp = commitTimestamp,
      dataset_name = entity.datasetName,
      layer_id = entity.layerId,
      geo_id = entity.geoId,
      geometry = entity.geometry?.let(GeometryMapper::toProto),
      area_hectares = entity.areaHectares,
      perimeter_meters = entity.perimeterMeters,
      color_hex = entity.colorHex,
      singular_type_label = entity.typeNoun,
      sync_status_key = entity.syncStatus.name,
    )

  /**
   * Maps [proto] to a map feature. `EntityRecord` has no generated flag: whether a feature is a
   * generated sample plot is a property of its dataset (`EntityDatasetDef.generator`), so callers
   * pass [isGenerated] from [ProtoSurveyMapper.generatedDatasetIds].
   */
  fun entityFromProto(
    proto: EntityRecord,
    submissions: List<SubmissionPreviewItem>,
    isGenerated: Boolean,
  ): GeospatialEntityItem {
    val fallbackSyncStatus =
      SyncStatus.entries.firstOrNull { it.name == proto.sync_status_key } ?: SyncStatus.SYNCED
    return GeospatialEntityItem(
      id = proto.entity_id,
      label = proto.label,
      datasetId = proto.dataset_id,
      datasetName = proto.dataset_name,
      layerId = proto.layer_id,
      geoId = proto.geo_id,
      geometry =
        GeometryProtos.of(proto)?.let(GeometryMapper::fromProto) ?: LegacyEntityGeometry.of(proto),
      areaHectares = proto.area_hectares,
      perimeterMeters = proto.perimeter_meters,
      colorHex = proto.color_hex,
      properties = proto.properties.mapValues { (_, v) -> v.string_value.orEmpty() },
      submissions = submissions,
      typeNoun = proto.singular_type_label.takeUnless { it in LEGACY_TYPE_NOUNS }.orEmpty(),
      isFieldCreated = proto.is_field_created,
      isGenerated = isGenerated,
      syncStatus =
        if (submissions.isNotEmpty()) {
          org.openforis.collectone.v2.app.domain.model.deriveEntitySyncStatus(
            submissions,
            fallbackSyncStatus,
          )
        } else {
          fallbackSyncStatus
        },
    )
  }

  /**
   * The English texts older clients stored as type nouns for features and submissions whose survey
   * named none: their generic fallback, and the type of standalone submissions. They read as blank,
   * so the UI names the type in the user's language.
   */
  private val LEGACY_TYPE_NOUNS = setOf("Location", "Standalone Field Log")

  /**
   * [submission] as the client stores and uploads it. The server-owned `audit_info` stays unset:
   * the server stamps it when it applies the upload.
   */
  fun submissionToProto(
    surveyId: String,
    submission: SubmissionPreviewItem,
    geometryPolygons: List<SubmissionGeometryPolygon> = emptyList(),
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): SubmissionRecord =
    SubmissionRecord(
      submission_id = submission.id,
      survey_id = surveyId,
      form_id = submission.formId,
      form_version = submission.formVersion,
      entity_id = submission.entityId,
      revision = 1L,
      is_deleted = isDeleted,
      commit_timestamp = commitTimestamp,
      entity_label = submission.entityLabel,
      form_title = submission.formTitle,
      collector_name = submission.collectorName,
      collector_email = submission.collectorEmail,
      target_type_label = submission.targetTypeNoun,
      sync_status_key = submission.syncStatus.name,
      location = submission.location?.let { GeometryProtos.point(it.latitude, it.longitude) },
      field_entries =
        submission.fields.map { f ->
          SubmissionFieldEntryDef(
            question_name = f.questionName,
            question_label = f.questionLabel,
            answer_value = f.answerValue,
          )
        },
      geometry_polygons = geometryPolygons.map(::geometryPolygonToProto),
      client_updated_at = submission.clientUpdatedAt?.toWireInstant(),
      server_updated_at = submission.serverUpdatedAt?.toWireInstant(),
    )

  fun submissionFromProto(proto: SubmissionRecord): SubmissionPreviewItem =
    SubmissionPreviewItem(
      id = proto.submission_id,
      entityId = proto.entity_id,
      entityLabel = proto.entity_label,
      formId = proto.form_id,
      formTitle = proto.form_title,
      formVersion = proto.form_version,
      collectorName = proto.collector_name,
      // Submissions without a collector email show who the server recorded as their creator.
      collectorEmail = proto.collector_email.ifBlank { proto.audit_info?.created_by.orEmpty() },
      clientUpdatedAt = proto.client_updated_at?.toKotlinInstant(),
      // Submissions stored before either update time existed fall back to their commit time.
      serverUpdatedAt =
        (proto.server_updated_at
            ?: proto.commit_timestamp?.takeIf { proto.client_updated_at == null })
          ?.toKotlinInstant(),
      fields =
        proto.field_entries.map { f ->
          SubmissionFieldEntry(
            questionName = f.question_name,
            questionLabel = f.question_label,
            answerValue = f.answer_value,
          )
        },
      targetTypeNoun = proto.target_type_label.takeUnless { it in LEGACY_TYPE_NOUNS }.orEmpty(),
      syncStatus =
        SyncStatus.entries.firstOrNull { it.name == proto.sync_status_key } ?: SyncStatus.SYNCED,
      location = locationOf(proto),
    )

  /** Where [proto] was recorded: its `location` point, else the position a legacy row stored. */
  private fun locationOf(proto: SubmissionRecord): LatLng? =
    proto.location?.let(GeometryMapper::fromProto)?.representativePoint
      ?: LegacySubmissionGeometry.locationOf(proto)

  fun geometryPolygonToProto(poly: SubmissionGeometryPolygon): SubmissionGeometryPolygonDef =
    SubmissionGeometryPolygonDef(
      id = poly.id,
      submission_id = poly.submissionId,
      entity_id = poly.entityId,
      layer_id = poly.layerId,
      form_id = poly.formId,
      form_title = poly.formTitle,
      field_path = poly.fieldPath,
      question_label = poly.questionLabel,
      short_map_badge = poly.shortMapBadge,
      collector_name = poly.collectorName,
      area_hectares = poly.areaHectares,
      vertex_count = poly.vertexCount,
      color_hex = poly.colorHex,
      geometry = GeometryMapper.toProto(poly.geometry),
    )

  /**
   * [proto], a geometry answer of a submission in [surveyId], or `null` when it has no readable
   * geometry.
   */
  fun geometryPolygonFromProto(
    proto: SubmissionGeometryPolygonDef,
    surveyId: String,
  ): SubmissionGeometryPolygon? {
    val geometry =
      proto.geometry?.let(GeometryMapper::fromProto)
        ?: LegacySubmissionGeometry.geometryOf(proto, surveyId)
        ?: return null
    return SubmissionGeometryPolygon(
      id = proto.id,
      submissionId = proto.submission_id,
      entityId = proto.entity_id,
      layerId = proto.layer_id,
      formId = proto.form_id,
      formTitle = proto.form_title,
      fieldPath = proto.field_path,
      questionLabel = proto.question_label,
      shortMapBadge = proto.short_map_badge,
      collectorName = proto.collector_name,
      areaHectares = proto.area_hectares,
      vertexCount = proto.vertex_count,
      colorHex = proto.color_hex,
      geometry = geometry,
    )
  }

  // ---------------------------------------------------------------------------
  // Places & Mutation Logs & Impact Events
  // ---------------------------------------------------------------------------

  fun placeToProto(place: SurveyPlaceItem): PlaceRecord =
    PlaceRecord(
      id = place.id,
      name = place.name,
      category_label = place.categoryLabel,
      region_subtitle = place.regionSubtitle,
      normalized_x = place.normalizedX,
      normalized_y = place.normalizedY,
      zoom_delta = place.zoomDelta,
      position = GeometryProtos.position(place.position.latitude, place.position.longitude),
      bounds = place.bounds?.let(GeometryMapper::boundsToProto),
      target_zoom = place.targetZoom,
      mapbox_place_id = place.mapboxPlaceId,
      source = placeSourceToProto(place.source),
    )

  fun placeFromProto(proto: PlaceRecord): SurveyPlaceItem =
    SurveyPlaceItem(
      id = proto.id,
      name = proto.name,
      categoryLabel = proto.category_label,
      regionSubtitle = proto.region_subtitle,
      normalizedX = proto.normalized_x,
      normalizedY = proto.normalized_y,
      zoomDelta = proto.zoom_delta,
      position = placePosition(proto),
      bounds = placeBounds(proto),
      targetZoom = proto.target_zoom,
      mapboxPlaceId = proto.mapbox_place_id,
      source = placeSource(proto),
    )

  /**
   * A place record's position: its `position`, else the `longitude` (9) and `latitude` (10) a
   * legacy record stored. Stored data predates validation, so out-of-range values are clamped
   * rather than failing the whole sync.
   */
  private fun placePosition(proto: PlaceRecord): LatLng =
    proto.position?.let { LatLng.coerced(it.latitude, it.longitude) }
      ?: LatLng.coerced(
        ReservedFields.double(proto.unknownFields, PLACE_LEGACY_LATITUDE) ?: 0.0,
        ReservedFields.double(proto.unknownFields, PLACE_LEGACY_LONGITUDE) ?: 0.0,
      )

  /** Reserved field numbers of the legacy position of `PlaceRecord`. */
  internal const val PLACE_LEGACY_LONGITUDE = 9
  internal const val PLACE_LEGACY_LATITUDE = 10

  /**
   * A place record's bounding box, normalized so west ≤ east and south ≤ north: its `bounds`, else
   * the four edges a legacy record stored (fields 11 to 14), or `null` unless all of them are set.
   */
  private fun placeBounds(proto: PlaceRecord): LngLatBounds? =
    proto.bounds?.let(GeometryMapper::boundsFromProto) ?: legacyPlaceBounds(proto)

  private fun legacyPlaceBounds(proto: PlaceRecord): LngLatBounds? {
    val edges = PLACE_LEGACY_BBOX_EDGES.mapNotNull {
      ReservedFields.double(proto.unknownFields, it)
    }
    if (edges.size != PLACE_LEGACY_BBOX_EDGES.size) return null
    return GeometryMapper.normalizedBounds(edges[0], edges[1], edges[2], edges[3])
  }

  private fun placeSourceToProto(source: PlaceSource?): PlaceSourceProto =
    when (source) {
      PlaceSource.PLACES_API -> PlaceSourceProto.PLACE_SOURCE_PLACES_API
      null -> PlaceSourceProto.PLACE_SOURCE_UNSPECIFIED
    }

  /**
   * A place record's source: its `source`, else the one a legacy record named in English in
   * `source_label` (17), or `null` when neither is known.
   */
  private fun placeSource(proto: PlaceRecord): PlaceSource? =
    when (proto.source) {
      PlaceSourceProto.PLACE_SOURCE_PLACES_API -> PlaceSource.PLACES_API
      PlaceSourceProto.PLACE_SOURCE_UNSPECIFIED ->
        PlaceSource.PLACES_API.takeIf {
          ReservedFields.string(proto.unknownFields, PLACE_LEGACY_SOURCE_LABEL)
            .equals(LEGACY_PLACES_API_LABEL, ignoreCase = true)
        }
    }

  /** Reserved field number of the legacy English source label of `PlaceRecord`. */
  internal const val PLACE_LEGACY_SOURCE_LABEL = 17

  /** The label legacy records stored for [PlaceSource.PLACES_API]. */
  private const val LEGACY_PLACES_API_LABEL = "Places API"

  /**
   * Reserved field numbers of the legacy bounding box edges of `PlaceRecord`, in the order west,
   * south, east, north (`bbox_min_lng`, `bbox_min_lat`, `bbox_max_lng`, `bbox_max_lat`).
   */
  private val PLACE_LEGACY_BBOX_EDGES = listOf(11, 12, 13, 14)

  fun mutationLogToProto(
    item: MutationLogItem,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): MutationLogRecord =
    MutationLogRecord(
      id = item.id,
      survey_id = item.surveyId,
      operation_kind_key = item.operationKind.name,
      title = item.title,
      target_label = item.targetLabel,
      entity_id = item.entityId,
      submission_id = item.submissionId,
      actor_name = item.actorName,
      state_key = item.state.name,
      state_detail = item.stateDetail,
      operation_timestamp = item.operationTimestamp,
      started_timestamp = item.startedTimestamp,
      completed_timestamp = item.completedTimestamp,
      payload_summary = item.payloadSummary,
      commit_timestamp = commitTimestamp,
    )

  fun mutationLogFromProto(proto: MutationLogRecord): MutationLogItem =
    MutationLogItem(
      id = proto.id,
      surveyId = proto.survey_id,
      operationKind =
        MutationOperationKind.entries.firstOrNull { it.name == proto.operation_kind_key }
          ?: MutationOperationKind.CREATE_SUBMISSION,
      title = proto.title,
      targetLabel = proto.target_label,
      entityId = proto.entity_id,
      submissionId = proto.submission_id,
      actorName = proto.actor_name,
      state =
        MutationSyncState.entries.firstOrNull { it.name == proto.state_key }
          ?: MutationSyncState.QUEUED,
      stateDetail = proto.state_detail,
      operationTimestamp = proto.operation_timestamp,
      startedTimestamp = proto.started_timestamp,
      completedTimestamp = proto.completed_timestamp,
      payloadSummary = proto.payload_summary,
    )

  fun impactEventToProto(
    event: ImpactEvent,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): ImpactEventProto =
    ImpactEventProto(
      event_id = event.id,
      type =
        when (event.type) {
          ImpactEventType.EXPORT -> ImpactEventTypeProto.EXPORT
          ImpactEventType.PARTNER_PUSH -> ImpactEventTypeProto.PARTNER_PUSH
          ImpactEventType.RECEIPT_GENERATED -> ImpactEventTypeProto.RECEIPT_GENERATED
          ImpactEventType.RECEIPT_SHARED -> ImpactEventTypeProto.RECEIPT_SHARED
          ImpactEventType.DOI_MINTED -> ImpactEventTypeProto.DOI_MINTED
          ImpactEventType.SURVEY_CLOSED -> ImpactEventTypeProto.SURVEY_CLOSED
        },
      survey_id = event.surveyId,
      organization_id = event.organizationId.orEmpty(),
      purpose_ids = event.purposeIds,
      export_profile_id = event.exportProfileId,
      feature_count = event.featureCount.toLong(),
      area_ha = event.areaHa,
      occurred_at = parseIsoToInstant(event.occurredAt),
      actor_user_id = event.actorUserId,
      commit_timestamp = commitTimestamp,
    )

  /**
   * Maps an event read from the user data service. `ImpactEvent` has no upload flag because
   * [ImpactEvent.isUploaded] is client-only bookkeeping: an event the service returns has been
   * uploaded by definition, while events waiting to upload live only in the local store and never
   * go through this mapper.
   */
  fun impactEventFromProto(proto: ImpactEventProto): ImpactEvent =
    ImpactEvent(
      id = proto.event_id,
      type =
        when (proto.type) {
          ImpactEventTypeProto.PARTNER_PUSH -> ImpactEventType.PARTNER_PUSH
          ImpactEventTypeProto.RECEIPT_GENERATED -> ImpactEventType.RECEIPT_GENERATED
          ImpactEventTypeProto.RECEIPT_SHARED -> ImpactEventType.RECEIPT_SHARED
          ImpactEventTypeProto.DOI_MINTED -> ImpactEventType.DOI_MINTED
          ImpactEventTypeProto.SURVEY_CLOSED -> ImpactEventType.SURVEY_CLOSED
          else -> ImpactEventType.EXPORT
        },
      surveyId = proto.survey_id,
      organizationId = proto.organization_id.takeIf { it.isNotBlank() },
      purposeIds = proto.purpose_ids,
      exportProfileId = proto.export_profile_id,
      featureCount = proto.feature_count.toInt(),
      areaHa = proto.area_ha,
      occurredAt = proto.occurred_at?.let { isoUtc(it.toKotlinInstant()) }.orEmpty(),
      actorUserId = proto.actor_user_id,
      isUploaded = true,
    )
}
