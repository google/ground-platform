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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
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
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

class ProtoRecordMapperRoundTripTest {
  private val submission =
    SubmissionPreviewItem(
      id = "sub-1",
      entityId = "plot-7",
      entityLabel = "Plot 7",
      formId = "tree-inventory",
      formTitle = "Tree inventory",
      formVersion = "3",
      collectorName = "Amina Otieno",
      collectorEmail = "amina@example.org",
      clientUpdatedAt = Instant.parse("2026-04-02T09:15:00Z"),
      serverUpdatedAt = Instant.parse("2026-04-02T09:17:00Z"),
      fields =
        listOf(
          SubmissionFieldEntry("species", "Species", "Grevillea robusta"),
          SubmissionFieldEntry("dbh_cm", "Diameter (cm)", "31.5"),
        ),
      targetTypeNoun = "Monitoring plot",
      syncStatus = SyncStatus.FAILED,
      location = LatLng(-0.4198, 36.9512),
    )

  @Test
  fun submission_roundTrips() {
    val proto = ProtoRecordMapper.submissionToProto(surveyId = "survey-1", submission = submission)

    assertEquals(submission, ProtoRecordMapper.submissionFromProto(proto))
  }

  @Test
  fun entity_roundTripsWithItsSubmissions() {
    val entity =
      GeospatialEntityItem(
        id = "plot-7",
        label = "Plot 7",
        datasetId = "monitoring_plots",
        datasetName = "Monitoring plots",
        layerId = "layer-plots",
        geoId = "geo-123",
        geometry =
          Geometry.Polygon(
            listOf(
              listOf(LatLng(-0.4190, 36.9500), LatLng(-0.4190, 36.9520), LatLng(-0.4205, 36.9520))
            )
          ),
        areaHectares = 1.25,
        perimeterMeters = 460,
        colorHex = 0xFF2E7D32L,
        properties = mapOf("owner" to "Cooperative A", "slope" to "steep"),
        submissions = listOf(submission),
        typeNoun = "Monitoring plot",
        syncStatus = SyncStatus.FAILED,
        isFieldCreated = true,
      )

    val proto = ProtoRecordMapper.entityToProto(surveyId = "survey-1", entity = entity)

    assertEquals(
      entity,
      ProtoRecordMapper.entityFromProto(
        proto,
        submissions = listOf(submission),
        isGenerated = false,
      ),
    )
  }

  @Test
  fun generatedEntity_roundTripsWhenItsDatasetIsGenerated() {
    // `EntityRecord` has no generated flag; the dataset's generator decides it.
    val entity =
      GeospatialEntityItem(
        id = "sample-plot-1",
        label = "Sample plot 1",
        datasetId = "sample_plots",
        datasetName = "Sample plots",
        layerId = "layer-sample-plots",
        geoId = "",
        geometry =
          Geometry.Polygon(
            listOf(
              listOf(LatLng(-0.4190, 36.9500), LatLng(-0.4190, 36.9520), LatLng(-0.4205, 36.9520))
            )
          ),
        areaHectares = 0.1,
        perimeterMeters = 126,
        colorHex = 0xFF2E7D32L,
        properties = emptyMap(),
        submissions = emptyList(),
        isGenerated = true,
      )

    val proto = ProtoRecordMapper.entityToProto(surveyId = "survey-1", entity = entity)

    assertEquals(
      entity,
      ProtoRecordMapper.entityFromProto(proto, submissions = emptyList(), isGenerated = true),
    )
  }

  @Test
  fun geometryPolygon_roundTrips() {
    val polygon =
      SubmissionGeometryPolygon(
        id = "poly-1",
        submissionId = "sub-1",
        entityId = "plot-7",
        layerId = "layer-plots",
        formId = "tree-inventory",
        formTitle = "Tree inventory",
        fieldPath = "/data/canopy_area",
        questionLabel = "Canopy area",
        shortMapBadge = "CA",
        collectorName = "Amina Otieno",
        areaHectares = 0.08,
        vertexCount = 6,
        colorHex = 0xFF1565C0L,
        geometry =
          Geometry.Polygon(
            listOf(
              listOf(LatLng(-0.4190, 36.9500), LatLng(-0.4190, 36.9520), LatLng(-0.4205, 36.9520))
            )
          ),
      )

    val proto = ProtoRecordMapper.geometryPolygonToProto(polygon)

    assertEquals(polygon, ProtoRecordMapper.geometryPolygonFromProto(proto, surveyId = "survey-1"))
  }

  @Test
  fun place_roundTrips() {
    val place =
      SurveyPlaceItem(
        id = "place-1",
        name = "Nyeri",
        categoryLabel = "Town",
        regionSubtitle = "Nyeri County, Kenya",
        normalizedX = 0.5f,
        normalizedY = 0.5f,
        zoomDelta = 1.5f,
        position = LatLng(-0.4198, 36.9512),
        bounds = LngLatBounds(36.90, -0.46, 37.00, -0.38),
        targetZoom = 12f,
        mapboxPlaceId = "mapbox-place-1",
        source = PlaceSource.PLACES_API,
      )

    val proto = ProtoRecordMapper.placeToProto(place)

    assertEquals(place, ProtoRecordMapper.placeFromProto(proto))
  }

  @Test
  fun mutationLog_roundTrips() {
    val log =
      MutationLogItem(
        id = "mut-1",
        surveyId = "survey-1",
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title = "Map feature modified",
        targetLabel = "Plot 7",
        entityId = "plot-7",
        submissionId = "sub-1",
        actorName = "Amina Otieno",
        state = MutationSyncState.RETRYING,
        stateDetail = "Retrying in 30 s",
        operationTimestamp = "2026-04-02T09:15:00Z",
        startedTimestamp = "2026-04-02T09:16:00Z",
        completedTimestamp = "2026-04-02T09:17:00Z",
        payloadSummary = "label, properties",
      )

    val proto = ProtoRecordMapper.mutationLogToProto(log)

    assertEquals(log, ProtoRecordMapper.mutationLogFromProto(proto))
  }

  @Test
  fun impactEvent_roundTrips() {
    // Events read back from a record are uploaded by definition.
    val event =
      ImpactEvent(
        id = "impact-1",
        type = ImpactEventType.PARTNER_PUSH,
        surveyId = "survey-1",
        organizationId = "org-1",
        purposeIds = listOf("eudr", "carbon"),
        exportProfileId = "profile-geojson",
        featureCount = 42,
        areaHa = 12.5,
        occurredAt = "2026-04-02T09:15:00Z",
        actorUserId = "user-1",
        isUploaded = true,
      )

    val proto = ProtoRecordMapper.impactEventToProto(event)

    assertEquals(event, ProtoRecordMapper.impactEventFromProto(proto))
  }

  @Test
  fun pendingImpactEvent_readsBackAsUploaded() {
    // `isUploaded` is client-only: whatever the service returns has been uploaded.
    val pending =
      ImpactEvent(
        id = "impact-2",
        type = ImpactEventType.RECEIPT_GENERATED,
        surveyId = "survey-1",
        organizationId = null,
        occurredAt = "2026-04-02T09:15:00Z",
        actorUserId = "user-1",
        isUploaded = false,
      )

    val proto = ProtoRecordMapper.impactEventToProto(pending)

    assertEquals(
      pending.copy(isUploaded = true),
      ProtoRecordMapper.impactEventFromProto(proto),
    )
  }
}
