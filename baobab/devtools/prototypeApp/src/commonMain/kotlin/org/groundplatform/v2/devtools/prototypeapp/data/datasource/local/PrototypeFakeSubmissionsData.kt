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
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.local

import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus

/** Hardcoded sample standalone submissions and submission geometries for the prototype app. */
object PrototypeFakeSubmissionsData {
  /** Returns the preloaded standalone [SubmissionPreviewItem] list for [surveyId]. */
  fun standaloneSubmissionsForSurvey(surveyId: String): List<SubmissionPreviewItem> =
    when (surveyId) {
      "survey-single-point-land-use" -> emptyList()
      "survey-commodity-perimeter-center" -> emptyList()
      "survey-sample-plots-forest" -> emptyList()
      "survey-household-past-individuals" -> emptyList()
      else -> defaultStandaloneSubmissions()
    }

  /** Returns the preloaded [SubmissionGeometryPolygon] list for [surveyId]. */
  fun submissionGeometriesForSurvey(surveyId: String): List<SubmissionGeometryPolygon> =
    when (surveyId) {
      "survey-single-point-land-use" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-splu-01",
            submissionId = "sub-splu-01",
            entityId = "",
            layerId = "layer-form-single-point-land-use",
            formId = "form-single-point-land-use",
            formTitle = "Simple Point & Land Use Observation",
            fieldPath = "sample_point",
            questionLabel = "Sample Location Point (geopoint <=10m GPS)",
            shortMapBadge = "📍 Primary Forest (±4.8m)",
            collectorName = "Maya Lin",
            timestamp = "2026-09-24 08:15 UTC",
            areaHectares = 0.05,
            vertexCount = 4,
            normalizedX = 0.30f,
            normalizedY = 0.34f,
            widthFraction = 0.14f,
            heightFraction = 0.10f,
            colorHex = 0xFF2E7D32,
          ),
          SubmissionGeometryPolygon(
            id = "geom-splu-02",
            submissionId = "sub-splu-02",
            entityId = "",
            layerId = "layer-form-single-point-land-use",
            formId = "form-single-point-land-use",
            formTitle = "Simple Point & Land Use Observation",
            fieldPath = "sample_point",
            questionLabel = "Sample Location Point (geopoint <=10m GPS)",
            shortMapBadge = "📍 Agroforestry (±6.2m)",
            collectorName = "David Kamau",
            timestamp = "2026-09-24 09:05 UTC",
            areaHectares = 0.05,
            vertexCount = 4,
            normalizedX = 0.54f,
            normalizedY = 0.56f,
            widthFraction = 0.14f,
            heightFraction = 0.10f,
            colorHex = 0xFF2E7D32,
          ),
          SubmissionGeometryPolygon(
            id = "geom-splu-03",
            submissionId = "sub-splu-03",
            entityId = "",
            layerId = "layer-form-single-point-land-use",
            formId = "form-single-point-land-use",
            formTitle = "Simple Point & Land Use Observation",
            fieldPath = "sample_point",
            questionLabel = "Sample Location Point (geopoint <=10m GPS)",
            shortMapBadge = "📍 Riparian Buffer (±5.1m)",
            collectorName = "Grace Wanjiku",
            timestamp = "2026-09-24 09:42 UTC",
            areaHectares = 0.05,
            vertexCount = 4,
            normalizedX = 0.68f,
            normalizedY = 0.30f,
            widthFraction = 0.14f,
            heightFraction = 0.10f,
            colorHex = 0xFF0277BD,
          ),
        )
      "survey-sample-plots-forest" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-sp-01",
            submissionId = "sub-sp01-assess-2026",
            entityId = "plot_sp01",
            layerId = "layer-form-sample-plots-forest",
            formId = "form-sample-plots-forest",
            formTitle = "Sample Plot Entity & Forest Stand Assessment",
            fieldPath = "sample_plot_entity",
            questionLabel = "Sample Plot Assessment Footprint (20m radius)",
            shortMapBadge = "SP-01 • 78% Canopy (📷)",
            collectorName = "Maya Lin",
            timestamp = "2026-09-23 09:20 UTC",
            areaHectares = 0.13,
            vertexCount = 12,
            normalizedX = 0.30f,
            normalizedY = 0.34f,
            widthFraction = 0.18f,
            heightFraction = 0.13f,
            colorHex = 0xFF00897B,
          ),
          SubmissionGeometryPolygon(
            id = "geom-sp-02",
            submissionId = "sub-sp02-assess-2026",
            entityId = "plot_sp02",
            layerId = "layer-form-sample-plots-forest",
            formId = "form-sample-plots-forest",
            formTitle = "Sample Plot Entity & Forest Stand Assessment",
            fieldPath = "sample_plot_entity",
            questionLabel = "Sample Plot Assessment Footprint (20m radius)",
            shortMapBadge = "SP-02 • 84% Canopy (📷)",
            collectorName = "Samuel Kariuki",
            timestamp = "2026-09-23 11:05 UTC",
            areaHectares = 0.13,
            vertexCount = 12,
            normalizedX = 0.56f,
            normalizedY = 0.42f,
            widthFraction = 0.18f,
            heightFraction = 0.13f,
            colorHex = 0xFF00897B,
          ),
          SubmissionGeometryPolygon(
            id = "geom-sp-03",
            submissionId = "sub-sp03-assess-2026",
            entityId = "plot_sp03",
            layerId = "layer-form-sample-plots-forest",
            formId = "form-sample-plots-forest",
            formTitle = "Sample Plot Entity & Forest Stand Assessment",
            fieldPath = "sample_plot_entity",
            questionLabel = "Sample Plot Assessment Footprint (20m radius)",
            shortMapBadge = "SP-03 • 65% Canopy (📷)",
            collectorName = "Maya Lin",
            timestamp = "2026-09-24 07:50 UTC",
            areaHectares = 0.13,
            vertexCount = 12,
            normalizedX = 0.26f,
            normalizedY = 0.62f,
            widthFraction = 0.18f,
            heightFraction = 0.13f,
            colorHex = 0xFF00897B,
          ),
        )
      "survey-commodity-perimeter-center" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-eudr-0419",
            submissionId = "sub-eudr-gh-0419",
            entityId = "",
            layerId = "layer-form-commodity-perimeter",
            formId = "form-commodity-perimeter-center",
            formTitle = "Commodity Plot Perimeter & Center Mapping",
            fieldPath = "plot_perimeter",
            questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
            shortMapBadge = "Cocoa 1.42ha • Center ±2.4m",
            collectorName = "Maya Lin",
            timestamp = "2026-09-24 08:30 UTC",
            areaHectares = 1.42,
            vertexCount = 5,
            normalizedX = 0.31f,
            normalizedY = 0.35f,
            widthFraction = 0.24f,
            heightFraction = 0.16f,
            colorHex = 0xFFD84315,
          ),
          SubmissionGeometryPolygon(
            id = "geom-eudr-0422",
            submissionId = "sub-eudr-gh-0422",
            entityId = "",
            layerId = "layer-form-commodity-perimeter",
            formId = "form-commodity-perimeter-center",
            formTitle = "Commodity Plot Perimeter & Center Mapping",
            fieldPath = "plot_perimeter",
            questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
            shortMapBadge = "Coffee 0.95ha • Center ±3.1m",
            collectorName = "Samuel Kariuki",
            timestamp = "2026-09-24 09:25 UTC",
            areaHectares = 0.95,
            vertexCount = 4,
            normalizedX = 0.56f,
            normalizedY = 0.52f,
            widthFraction = 0.21f,
            heightFraction = 0.14f,
            colorHex = 0xFF2E7D32,
          ),
          SubmissionGeometryPolygon(
            id = "geom-eudr-0428",
            submissionId = "sub-eudr-gh-0428",
            entityId = "",
            layerId = "layer-form-commodity-perimeter",
            formId = "form-commodity-perimeter-center",
            formTitle = "Commodity Plot Perimeter & Center Mapping",
            fieldPath = "plot_perimeter",
            questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
            shortMapBadge = "Rubber 2.18ha • Center ±4.2m",
            collectorName = "David Kamau",
            timestamp = "2026-09-24 10:12 UTC",
            areaHectares = 2.18,
            vertexCount = 5,
            normalizedX = 0.69f,
            normalizedY = 0.29f,
            widthFraction = 0.26f,
            heightFraction = 0.18f,
            colorHex = 0xFF6A1B9A,
          ),
        )
      "survey-household-past-individuals" ->
        listOf(
          SubmissionGeometryPolygon(
            id = "geom-ind-101",
            submissionId = "sub-ind101-wave4",
            entityId = "ind_101",
            layerId = "layer-form-household-survey",
            formId = "form-household-past-individuals",
            formTitle = "Household Follow-Up Survey (Past Individuals)",
            fieldPath = "compound_gps",
            questionLabel = "Household Compound GPS Verification",
            shortMapBadge = "HH-KAK-014 • 6 Members",
            collectorName = "Grace Wanjiku",
            timestamp = "2026-09-23 14:10 UTC",
            areaHectares = 0.08,
            vertexCount = 4,
            normalizedX = 0.32f,
            normalizedY = 0.36f,
            widthFraction = 0.15f,
            heightFraction = 0.11f,
            colorHex = 0xFF0277BD,
          ),
          SubmissionGeometryPolygon(
            id = "geom-ind-103",
            submissionId = "sub-ind103-wave4",
            entityId = "ind_103",
            layerId = "layer-form-household-survey",
            formId = "form-household-past-individuals",
            formTitle = "Household Follow-Up Survey (Past Individuals)",
            fieldPath = "compound_gps",
            questionLabel = "Household Compound GPS Verification",
            shortMapBadge = "HH-KAK-022 • Present",
            collectorName = "Maya Lin",
            timestamp = "2026-09-23 15:40 UTC",
            areaHectares = 0.08,
            vertexCount = 4,
            normalizedX = 0.58f,
            normalizedY = 0.48f,
            widthFraction = 0.15f,
            heightFraction = 0.11f,
            colorHex = 0xFF0277BD,
          ),
        )
      else -> defaultSubmissionGeometries()
    }

  /**
   * Sample Submission Geometries (`SubmissionGeometryPolygon`) corresponding to geometry
   * questions/fields (`FormGeometrySource { form_id, field_path }`) in the survey's forms.
   * Submissions are not shown as layers in the `Layers` sheet; these geometries are used to
   * resolve coordinates and bounds when inspecting or navigating to a submission.
   */
  fun defaultSubmissionGeometries(): List<SubmissionGeometryPolygon> =
    listOf(
      SubmissionGeometryPolygon(
        id = "geom-sub-nyr-104",
        submissionId = "sub-nyr-104-baseline",
        entityId = "entity-nyr-104",
        layerId = "layer-form-walked-perimeter",
        formId = "form-eudr-baseline",
        formTitle = "EUDR Parcel Baseline Registration",
        fieldPath = "parcel/walked_perimeter_geoshape",
        questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
        shortMapBadge = "Walked Perimeter (NYR-104)",
        collectorName = "Maya Lin",
        timestamp = "2026-09-18 10:14 UTC",
        areaHectares = 1.81,
        vertexCount = 28,
        normalizedX = 0.295f,
        normalizedY = 0.345f,
        widthFraction = 0.25f,
        heightFraction = 0.17f,
        colorHex = 0xFF66BB6A,
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-nyr-108",
        submissionId = "sub-nyr-108-baseline",
        entityId = "entity-nyr-108",
        layerId = "layer-form-walked-perimeter",
        formId = "form-eudr-baseline",
        formTitle = "EUDR Parcel Baseline Registration",
        fieldPath = "parcel/walked_perimeter_geoshape",
        questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
        shortMapBadge = "Walked Perimeter (NYR-108)",
        collectorName = "Samuel Kariuki",
        timestamp = "2026-09-17 16:02 UTC",
        areaHectares = 2.41,
        vertexCount = 34,
        normalizedX = 0.355f,
        normalizedY = 0.665f,
        widthFraction = 0.26f,
        heightFraction = 0.17f,
        colorHex = 0xFF66BB6A,
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-shade-201-w3",
        submissionId = "sub-shade-201-wave3",
        entityId = "entity-shade-201",
        layerId = "layer-form-canopy-subzone",
        formId = "form-shade-canopy-audit",
        formTitle = "Seasonal Shade Tree & Canopy Audit",
        fieldPath = "audit/canopy_sample_polygon",
        questionLabel = "Surveyed Canopy Regeneration Sub-Plot (geoshape)",
        shortMapBadge = "Canopy Sub-Plot (W3)",
        collectorName = "Maya Lin",
        timestamp = "2026-09-19 08:45 UTC",
        areaHectares = 1.24,
        vertexCount = 19,
        normalizedX = 0.675f,
        normalizedY = 0.315f,
        widthFraction = 0.24f,
        heightFraction = 0.16f,
        colorHex = 0xFF42A5F5,
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-wsh-01-sep",
        submissionId = "sub-wsh-01-sep",
        entityId = "entity-station-01",
        layerId = "layer-form-riparian-buffer",
        formId = "form-water-quality",
        formTitle = "Washing Station Effluent & Water Check",
        fieldPath = "inspection/riparian_buffer_zone",
        questionLabel = "Riparian Filtration Buffer Polygon (geoshape)",
        shortMapBadge = "Riparian Buffer (WSH-01)",
        collectorName = "Maya Lin",
        timestamp = "2026-09-18 17:30 UTC",
        areaHectares = 0.48,
        vertexCount = 14,
        normalizedX = 0.725f,
        normalizedY = 0.635f,
        widthFraction = 0.22f,
        heightFraction = 0.15f,
        colorHex = 0xFFFFCA28,
      ),
      SubmissionGeometryPolygon(
        id = "geom-sub-standalone-pest-01",
        submissionId = "sub-standalone-pest-01",
        entityId = "",
        layerId = "layer-form-pest-sighting-zone",
        formId = "form-pest-disease-sighting",
        formTitle = "Opportunistic Berry Borer & Rust Sighting",
        fieldPath = "sighting/affected_buffer_geoshape",
        questionLabel = "Affected Roadside Buffer Zone (geoshape)",
        shortMapBadge = "Pest Sighting (Standalone)",
        collectorName = "Maya Lin",
        timestamp = "2026-09-19 11:20 UTC",
        areaHectares = 0.32,
        vertexCount = 11,
        normalizedX = 0.52f,
        normalizedY = 0.46f,
        widthFraction = 0.18f,
        heightFraction = 0.13f,
        colorHex = 0xFFAB47BC,
      ),
    )

  /**
   * Sample standalone submissions (`entityId = ""`, `hasAttachedEntity == false`) recorded
   * directly in the field without being attached to any `GeospatialEntityItem`.
   */
  fun defaultStandaloneSubmissions(): List<SubmissionPreviewItem> =
    listOf(
      SubmissionPreviewItem(
        id = "sub-standalone-pest-01",
        entityId = "",
        entityLabel = "",
        formId = "form-pest-disease-sighting",
        formTitle = "Opportunistic Berry Borer & Rust Sighting",
        formVersion = "v2026.09.1",
        collectorName = "Maya Lin",
        collectorEmail = "maya.lin@groundplatform.org",
        timestamp = "2026-09-19 11:20 UTC",
        targetTypeLabel = "Standalone Field Log",
        syncStatus = SyncStatus.SYNCED,
        coordinatesLabel = "0.4204°S, 36.9521°E (±2.4m GPS)",
        normalizedX = 0.52f,
        normalizedY = 0.46f,
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
        id = "sub-standalone-erosion-02",
        entityId = "",
        entityLabel = "",
        formId = "form-pest-disease-sighting",
        formTitle = "Opportunistic Berry Borer & Rust Sighting",
        formVersion = "v2026.09.1",
        collectorName = "Samuel Kariuki",
        collectorEmail = "s.kariuki@kenyaforestry.org",
        timestamp = "2026-09-18 15:05 UTC",
        targetTypeLabel = "Standalone Field Log",
        syncStatus = SyncStatus.UPLOADING,
        coordinatesLabel = "0.4231°S, 36.9495°E (±3.1m GPS)",
        normalizedX = 0.48f,
        normalizedY = 0.54f,
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
}
