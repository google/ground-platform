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
package org.openforis.collectone.v2.app.data.seed.backend

import kotlin.time.Instant
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/** Hardcoded sample standalone submissions and submission geometries for the sample data. */
object BackendStaticSubmissionsData {
  /** Returns the preloaded standalone [SubmissionPreviewItem] list for [surveyId]. */
  fun standaloneSubmissionsForSurvey(surveyId: String): List<SubmissionPreviewItem> =
    when (surveyId) {
      "00000036-1a1c-5c51-997f-28b67d813aa3" -> emptyList()
      "00000031-ffc6-5c8e-934c-d14ec07533ad" -> emptyList()
      "00000034-c088-5ab9-8b98-e6bccb3dca71" -> emptyList()
      "00000032-8d62-5717-8074-f4917e0cd76d" -> emptyList()
      BackendStaticEntitiesData.GOOGLE_EARTH_SURVEY_ID -> emptyList()
      BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID ->
        MekongDeltaSampleData.standaloneSubmissions()
      else -> defaultStandaloneSubmissions()
    }

  /** Returns the preloaded [SubmissionGeometryPolygon] list for [surveyId]. */
  fun submissionGeometriesForSurvey(surveyId: String): List<SubmissionGeometryPolygon> =
    when (surveyId) {
      "00000036-1a1c-5c51-997f-28b67d813aa3" ->
        MekongDeltaSampleData.relocateSubmissionGeometries(
          authoredEntities = BackendStaticEntitiesData.authoredEntitiesForSurvey(surveyId),
          geometries = authoredSubmissionGeometriesForSurvey(surveyId),
          targetArea = BackendStaticSurveyEditorData.arushaSurveyArea(),
        )
      "00000034-c088-5ab9-8b98-e6bccb3dca71" ->
        MekongDeltaSampleData.relocateSubmissionGeometries(
          authoredEntities = BackendStaticEntitiesData.authoredEntitiesForSurvey(surveyId),
          geometries = authoredSubmissionGeometriesForSurvey(surveyId),
          targetArea = BackendStaticSurveyEditorData.paraSurveyArea(),
        )
      "00000031-ffc6-5c8e-934c-d14ec07533ad" ->
        MekongDeltaSampleData.relocateSubmissionGeometries(
          authoredEntities = BackendStaticEntitiesData.authoredEntitiesForSurvey(surveyId),
          geometries = authoredSubmissionGeometriesForSurvey(surveyId),
          targetArea = BackendStaticSurveyEditorData.ashantiSurveyArea(),
        )
      "00000032-8d62-5717-8074-f4917e0cd76d" ->
        MekongDeltaSampleData.relocateSubmissionGeometries(
          authoredEntities = BackendStaticEntitiesData.authoredEntitiesForSurvey(surveyId),
          geometries = authoredSubmissionGeometriesForSurvey(surveyId),
          targetArea = BackendStaticSurveyEditorData.kakamegaSurveyArea(),
        )
      BackendStaticEntitiesData.GOOGLE_EARTH_SURVEY_ID -> emptyList()
      BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID ->
        MekongDeltaSampleData.submissionGeometries()
      else -> defaultSubmissionGeometries()
    }

  private fun authoredSubmissionGeometriesForSurvey(
    surveyId: String
  ): List<SubmissionGeometryPolygon> =
    when (surveyId) {
      "00000036-1a1c-5c51-997f-28b67d813aa3" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-splu-01",
            submissionId = "0000002a-d6b0-55b4-be22-99d4f83950b9",
            entityId = "",
            layerId = "layer-form-single-point-land-use",
            formId = "form-single-point-land-use",
            formTitle = "Simple Point & Land Use Observation",
            fieldPath = "sample_point",
            questionLabel = "Sample Location Point (geopoint <=10m GPS)",
            shortMapBadge = "📍 Primary Forest (±4.8m)",
            collectorName = "Maya Lin",
            areaHectares = 0.05,
            vertexCount = 4,
            colorHex = 0xFF2E7D32,
            geometry =
              rectangle(west = 36.94602, south = -0.41682, east = 36.94798, north = -0.41502),
          ),
          SubmissionGeometryPolygon(
            id = "geom-splu-02",
            submissionId = "0000002b-918d-55c7-ba85-a63c4a250379",
            entityId = "",
            layerId = "layer-form-single-point-land-use",
            formId = "form-single-point-land-use",
            formTitle = "Simple Point & Land Use Observation",
            fieldPath = "sample_point",
            questionLabel = "Sample Location Point (geopoint <=10m GPS)",
            shortMapBadge = "📍 Agroforestry (±6.2m)",
            collectorName = "David Kamau",
            areaHectares = 0.05,
            vertexCount = 4,
            colorHex = 0xFF2E7D32,
            geometry =
              rectangle(west = 36.94938, south = -0.42078, east = 36.95134, north = -0.41898),
          ),
          SubmissionGeometryPolygon(
            id = "geom-splu-03",
            submissionId = "0000002c-0423-586f-937e-81d7e9a92b77",
            entityId = "",
            layerId = "layer-form-single-point-land-use",
            formId = "form-single-point-land-use",
            formTitle = "Simple Point & Land Use Observation",
            fieldPath = "sample_point",
            questionLabel = "Sample Location Point (geopoint <=10m GPS)",
            shortMapBadge = "📍 Riparian Buffer (±5.1m)",
            collectorName = "Grace Wanjiku",
            areaHectares = 0.05,
            vertexCount = 4,
            colorHex = 0xFF0277BD,
            geometry = rectangle(west = 36.95134, south = -0.4161, east = 36.9533, north = -0.4143),
          ),
        )
      "00000034-c088-5ab9-8b98-e6bccb3dca71" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-sp-01",
            submissionId = "00000027-ca75-5659-b125-262308b7375c",
            entityId = "00000016-cc1f-5125-8841-166ab5b5f68f",
            layerId = "layer-form-sample-plots-forest",
            formId = "form-sample-plots-forest",
            formTitle = "Sample Plot Entity & Forest Stand Assessment",
            fieldPath = "sample_plot_entity",
            questionLabel = "Sample Plot Assessment Footprint (20m radius)",
            shortMapBadge = "SP-01 • 78% Canopy (📷)",
            collectorName = "Maya Lin",
            areaHectares = 0.13,
            vertexCount = 12,
            colorHex = 0xFF00897B,
            geometry =
              rectangle(west = 36.94654, south = -0.41749, east = 36.94906, north = -0.41515),
          ),
          SubmissionGeometryPolygon(
            id = "geom-sp-02",
            submissionId = "00000028-86de-51b6-9d99-71dad6fafd8c",
            entityId = "00000017-d1a0-51d3-a686-e6ca450c23b2",
            layerId = "layer-form-sample-plots-forest",
            formId = "form-sample-plots-forest",
            formTitle = "Sample Plot Entity & Forest Stand Assessment",
            fieldPath = "sample_plot_entity",
            questionLabel = "Sample Plot Assessment Footprint (20m radius)",
            shortMapBadge = "SP-02 • 84% Canopy (📷)",
            collectorName = "Samuel Kariuki",
            areaHectares = 0.13,
            vertexCount = 12,
            colorHex = 0xFF00897B,
            geometry =
              rectangle(west = 36.95018, south = -0.41893, east = 36.9527, north = -0.41659),
          ),
          SubmissionGeometryPolygon(
            id = "geom-sp-03",
            submissionId = "00000029-4808-5225-9de5-551d5afe8ffc",
            entityId = "00000018-4d16-5ab7-a57d-7dae5949cb7a",
            layerId = "layer-form-sample-plots-forest",
            formId = "form-sample-plots-forest",
            formTitle = "Sample Plot Entity & Forest Stand Assessment",
            fieldPath = "sample_plot_entity",
            questionLabel = "Sample Plot Assessment Footprint (20m radius)",
            shortMapBadge = "SP-03 • 65% Canopy (📷)",
            collectorName = "Maya Lin",
            areaHectares = 0.13,
            vertexCount = 12,
            colorHex = 0xFF00897B,
            geometry =
              rectangle(west = 36.94598, south = -0.42253, east = 36.9485, north = -0.42019),
          ),
        )
      "00000031-ffc6-5c8e-934c-d14ec07533ad" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-eudr-0419",
            submissionId = "0000001b-1ba0-5775-b6cd-033a11d861d4",
            entityId = "",
            layerId = "layer-form-commodity-perimeter",
            formId = "form-commodity-perimeter-center",
            formTitle = "Commodity Plot Perimeter & Center Mapping",
            fieldPath = "plot_perimeter",
            questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
            shortMapBadge = "Cocoa 1.42ha • Center ±2.4m",
            collectorName = "Maya Lin",
            areaHectares = 1.42,
            vertexCount = 5,
            colorHex = 0xFFD84315,
            geometry =
              rectangle(west = 36.94666, south = -0.41824, east = 36.95002, north = -0.41536),
          ),
          SubmissionGeometryPolygon(
            id = "geom-eudr-0422",
            submissionId = "0000001c-e7b4-5cb1-84e0-5f506f6afc8b",
            entityId = "",
            layerId = "layer-form-commodity-perimeter",
            formId = "form-commodity-perimeter-center",
            formTitle = "Commodity Plot Perimeter & Center Mapping",
            fieldPath = "plot_perimeter",
            questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
            shortMapBadge = "Coffee 0.95ha • Center ±3.1m",
            collectorName = "Samuel Kariuki",
            areaHectares = 0.95,
            vertexCount = 4,
            colorHex = 0xFF2E7D32,
            geometry =
              rectangle(west = 36.95037, south = -0.42112, east = 36.95331, north = -0.4186),
          ),
          SubmissionGeometryPolygon(
            id = "geom-eudr-0428",
            submissionId = "0000001d-f3d9-53c0-ba3f-9b2f49e8e5cc",
            entityId = "",
            layerId = "layer-form-commodity-perimeter",
            formId = "form-commodity-perimeter-center",
            formTitle = "Commodity Plot Perimeter & Center Mapping",
            fieldPath = "plot_perimeter",
            questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
            shortMapBadge = "Rubber 2.18ha • Center ±4.2m",
            collectorName = "David Kamau",
            areaHectares = 2.18,
            vertexCount = 5,
            colorHex = 0xFF6A1B9A,
            geometry =
              rectangle(west = 36.95184, south = -0.41734, east = 36.95548, north = -0.4141),
          ),
        )
      "00000032-8d62-5717-8074-f4917e0cd76d" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-ind-101",
            submissionId = "0000001e-d0b8-5510-ba2f-626c8db19409",
            entityId = "0000000c-a9ae-59f6-ac51-7f5a8777598f",
            layerId = "layer-form-household-survey",
            formId = "form-household-past-individuals",
            formTitle = "Household Follow-Up Survey (Past Individuals)",
            fieldPath = "compound_gps",
            questionLabel = "Household Compound GPS Verification",
            shortMapBadge = "HH-KAK-014 • 6 Members",
            collectorName = "Grace Wanjiku",
            areaHectares = 0.08,
            vertexCount = 4,
            colorHex = 0xFF0277BD,
            geometry =
              rectangle(west = 36.94663, south = -0.41747, east = 36.94873, north = -0.41549),
          ),
          SubmissionGeometryPolygon(
            id = "geom-ind-103",
            submissionId = "00000020-efff-56da-8ad2-db47db9476d9",
            entityId = "0000000e-95ee-5695-ad0b-54576e8d7785",
            layerId = "layer-form-household-survey",
            formId = "form-household-past-individuals",
            formTitle = "Household Follow-Up Survey (Past Individuals)",
            fieldPath = "compound_gps",
            questionLabel = "Household Compound GPS Verification",
            shortMapBadge = "HH-KAK-022 • Present",
            collectorName = "Maya Lin",
            areaHectares = 0.08,
            vertexCount = 4,
            colorHex = 0xFF0277BD,
            geometry =
              rectangle(west = 36.95027, south = -0.41963, east = 36.95237, north = -0.41765),
          ),
        )
      BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID ->
        MekongDeltaSampleData.submissionGeometries()
      else -> defaultSubmissionGeometries()
    }

  /**
   * Sample Submission Geometries (`SubmissionGeometryPolygon`) corresponding to geometry
   * questions/fields (`FormGeometrySource { form_id, field_path }`) in the survey's forms.
   * Submissions are not shown as layers in the `Layers` sheet; these geometries are used to resolve
   * coordinates and bounds when inspecting or navigating to a submission.
   */
  fun defaultSubmissionGeometries(): List<SubmissionGeometryPolygon> =
    listOf(
      SubmissionGeometryPolygon(
        id = "geom-sub-nyr-104",
        submissionId = "00000022-c99b-5e7e-b810-c506ebf06f19",
        entityId = "00000007-640e-5ed7-9874-4d6b6717f21d",
        layerId = "layer-form-walked-perimeter",
        formId = "form-eudr-baseline",
        formTitle = "EUDR Parcel Baseline Registration",
        fieldPath = "parcel/walked_perimeter_geoshape",
        questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
        shortMapBadge = "Walked Perimeter (NYR-104)",
        collectorName = "Maya Lin",
        areaHectares = 1.81,
        vertexCount = 28,
        colorHex = 0xFF66BB6A,
        geometry = rectangle(west = 36.94658, south = -0.41854, east = 36.95008, north = -0.41548),
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-nyr-108",
        submissionId = "00000023-36c6-5f07-a7a8-5922ec72bed0",
        entityId = "00000008-15d0-51f2-84eb-a72dfea5a747",
        layerId = "layer-form-walked-perimeter",
        formId = "form-eudr-baseline",
        formTitle = "EUDR Parcel Baseline Registration",
        fieldPath = "parcel/walked_perimeter_geoshape",
        questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
        shortMapBadge = "Walked Perimeter (NYR-108)",
        collectorName = "Samuel Kariuki",
        areaHectares = 2.41,
        vertexCount = 34,
        colorHex = 0xFF66BB6A,
        geometry = rectangle(west = 36.94735, south = -0.4243, east = 36.95099, north = -0.42124),
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-shade-201-w3",
        submissionId = "00000026-cca2-5594-b83f-02266f40048e",
        entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
        layerId = "layer-form-canopy-subzone",
        formId = "form-shade-canopy-audit",
        formTitle = "Seasonal Shade Tree & Canopy Audit",
        fieldPath = "audit/canopy_sample_polygon",
        questionLabel = "Surveyed Canopy Regeneration Sub-Plot (geoshape)",
        shortMapBadge = "Canopy Sub-Plot (W3)",
        collectorName = "Maya Lin",
        areaHectares = 1.24,
        vertexCount = 19,
        colorHex = 0xFF42A5F5,
        geometry = rectangle(west = 36.95197, south = -0.41791, east = 36.95533, north = -0.41503),
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-wsh-01-sep",
        submissionId = "00000030-ca50-56e0-8458-9c5728954d52",
        entityId = "0000000b-7633-5116-9d6d-50581002d8cf",
        layerId = "layer-form-riparian-buffer",
        formId = "form-water-quality",
        formTitle = "Washing Station Effluent & Water Check",
        fieldPath = "inspection/riparian_buffer_zone",
        questionLabel = "Riparian Filtration Buffer Polygon (geoshape)",
        shortMapBadge = "Riparian Buffer (WSH-01)",
        collectorName = "Maya Lin",
        areaHectares = 0.48,
        vertexCount = 14,
        colorHex = 0xFFFFCA28,
        geometry = rectangle(west = 36.95281, south = -0.42358, east = 36.95589, north = -0.42088),
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-standalone-pest-01",
        submissionId = "0000002e-4118-5239-bcc9-78dda11063f5",
        entityId = "",
        layerId = "layer-form-pest-sighting-zone",
        formId = "form-pest-disease-sighting",
        formTitle = "Opportunistic Berry Borer & Rust Sighting",
        fieldPath = "sighting/affected_buffer_geoshape",
        questionLabel = "Affected Roadside Buffer Zone (geoshape)",
        shortMapBadge = "Pest Sighting (Standalone)",
        collectorName = "Maya Lin",
        areaHectares = 0.32,
        vertexCount = 11,
        colorHex = 0xFFAB47BC,
        geometry = rectangle(west = 36.95022, south = -0.42025, east = 36.95274, north = -0.41791),
      ),
    )

  /**
   * Sample standalone submissions (`entityId = ""`, `hasAttachedEntity == false`) recorded directly
   * in the field without being attached to any `GeospatialEntityItem`.
   */
  fun defaultStandaloneSubmissions(): List<SubmissionPreviewItem> =
    listOf(
      SubmissionPreviewItem(
        id = "0000002e-4118-5239-bcc9-78dda11063f5",
        entityId = "",
        entityLabel = "",
        formId = "form-pest-disease-sighting",
        formTitle = "Opportunistic Berry Borer & Rust Sighting",
        formVersion = "v2026.09.1",
        collectorName = "Maya Lin",
        collectorEmail = "maya.lin@collect-one.example.org",
        clientUpdatedAt = Instant.parse("2026-09-19T11:20:00Z"),
        serverUpdatedAt = Instant.parse("2026-09-19T11:22:00Z"),
        syncStatus = SyncStatus.SYNCED,
        location = LatLng(latitude = -0.4204, longitude = 36.9521),
        fields =
          listOf(
            SubmissionFieldEntry(
              questionName = "sighting/affected_buffer_geoshape",
              questionLabel = "Affected Roadside Buffer Zone (geoshape)",
              answerValue = "Polygon (11 vertices • 0.32 ha • Chinga Feeder Road)",
            ),
            SubmissionFieldEntry(
              questionName = "pest_or_hazard_type",
              questionLabel = "Observed Pest, Pathogen, or Hazard",
              answerValue = "Coffee Leaf Rust (Hemileia vastatrix) & Berry Borer",
            ),
            SubmissionFieldEntry(
              questionName = "severity_rating",
              questionLabel = "Outbreak Severity Rating",
              answerValue = "Moderate — Localized to roadside volunteer shrubs",
            ),
            SubmissionFieldEntry(
              questionName = "recommended_action",
              questionLabel = "Recommended Agronomist Action",
              answerValue = "Prune volunteer shrubs & notify neighboring Block B growers",
            ),
            SubmissionFieldEntry(
              questionName = "gps_observation_point",
              questionLabel = "Observation GNSS Fix",
              answerValue = "0.4204°S, 36.9521°E (1,812m • ±2.4m)",
            ),
          ),
      ),
      SubmissionPreviewItem(
        id = "0000002d-a881-53e1-90c1-b3e3ad027756",
        entityId = "",
        entityLabel = "",
        formId = "form-pest-disease-sighting",
        formTitle = "Opportunistic Berry Borer & Rust Sighting",
        formVersion = "v2026.09.1",
        collectorName = "Samuel Kariuki",
        collectorEmail = "s.kariuki@kenyaforestry.org",
        clientUpdatedAt = Instant.parse("2026-09-18T15:05:00Z"),
        serverUpdatedAt = Instant.parse("2026-09-18T15:07:00Z"),
        syncStatus = SyncStatus.UPLOADING,
        location = LatLng(latitude = -0.4231, longitude = 36.9495),
        fields =
          listOf(
            SubmissionFieldEntry(
              questionName = "pest_or_hazard_type",
              questionLabel = "Observed Pest, Pathogen, or Hazard",
              answerValue = "Gully Erosion & Culvert Washout Along Access Track",
            ),
            SubmissionFieldEntry(
              questionName = "severity_rating",
              questionLabel = "Outbreak Severity Rating",
              answerValue = "High — Sediment runoff entering Gura River tributary",
            ),
            SubmissionFieldEntry(
              questionName = "recommended_action",
              questionLabel = "Recommended Agronomist Action",
              answerValue = "Install vetiver grass check-dams before October short rains",
            ),
            SubmissionFieldEntry(
              questionName = "gps_observation_point",
              questionLabel = "Observation GNSS Fix",
              answerValue = "0.4231°S, 36.9495°E (1,798m • ±3.1m)",
            ),
          ),
      ),
    )

  /** A rectangular polygon with the given WGS 84 bounds, its corners clockwise from north-west. */
  private fun rectangle(west: Double, south: Double, east: Double, north: Double): Geometry =
    Geometry.Polygon(
      listOf(
        listOf(
          LatLng(north, west),
          LatLng(north, east),
          LatLng(south, east),
          LatLng(south, west),
        )
      )
    )
}
