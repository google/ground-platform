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
package org.groundplatform.v2.app.data.seed.backend

import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.SyncStatus

/** Hardcoded sample geospatial entities for the sample data. */
object BackendStaticEntitiesData {
  /** Returns the preloaded [GeospatialEntityItem] list for [surveyId]. */
  fun entitiesForSurvey(surveyId: String): List<GeospatialEntityItem> =
    when (surveyId) {
      "00000036-1a1c-5c51-997f-28b67d813aa3" ->
        listOf(
          GeospatialEntityItem(
            id = "00000004-36ce-5fff-b07c-6224a74c6258",
            label = "Primary Forest • -0.4182°, 36.9491°",
            datasetId = "land_use_observations",
            datasetName = "Land Use Observations",
            layerId = "layer-land-use-observations",
            geoId = "ENT-SPLU-01",
            geometryTypeLabel = "Point",
            areaHectares = 0.05,
            perimeterMeters = 25,
            coordinatesLabel = "0.4182° S, 36.9491° E",
            normalizedX = 0.30f,
            normalizedY = 0.34f,
            colorHex = 0xFF2E7D32,
            properties =
              mapOf(
                "land_use" to "primary_forest",
                "observer_note" to "Closed-canopy indigenous montane forest stand.",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000002a-d6b0-55b4-be22-99d4f83950b9",
                  entityId = "00000004-36ce-5fff-b07c-6224a74c6258",
                  entityLabel = "Primary Forest • -0.4182°, 36.9491°",
                  formId = "form-single-point-land-use",
                  formTitle = "Simple Point & Land Use Observation",
                  formVersion = "2026092401",
                  collectorName = "Maya Lin",
                  collectorEmail = "maya.lin@ground-demo.org",
                  timestamp = "2026-09-24 08:15 UTC",
                  targetTypeLabel = "Land Use Observation",
                  coordinatesLabel = "0.4182° S, 36.9491° E (±4.8m <=10m GPS)",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "sample_point",
                        questionLabel = "Sample Location Point (geopoint)",
                        answerValue = "-0.418200 36.949100 1742.0 4.8 (Pan Allowed, <=10m GPS)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "land_use",
                        questionLabel = "Primary Land Use Category",
                        answerValue = "primary_forest (Primary / Intact Natural Forest)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "observer_note",
                        questionLabel = "Optional Field Note",
                        answerValue = "Closed-canopy indigenous montane forest stand.",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000005-1b8a-5b6c-a70f-2ad85e5e50fd",
            label = "Agroforestry • -0.4209°, 36.9524°",
            datasetId = "land_use_observations",
            datasetName = "Land Use Observations",
            layerId = "layer-land-use-observations",
            geoId = "ENT-SPLU-02",
            geometryTypeLabel = "Point",
            areaHectares = 0.05,
            perimeterMeters = 25,
            coordinatesLabel = "0.4209° S, 36.9524° E",
            normalizedX = 0.54f,
            normalizedY = 0.56f,
            colorHex = 0xFF2E7D32,
            properties =
              mapOf(
                "land_use" to "agroforestry_shade",
                "observer_note" to "Terraced shade coffee intercropped with Cordia & Grevillea.",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000002b-918d-55c7-ba85-a63c4a250379",
                  entityId = "00000005-1b8a-5b6c-a70f-2ad85e5e50fd",
                  entityLabel = "Agroforestry • -0.4209°, 36.9524°",
                  formId = "form-single-point-land-use",
                  formTitle = "Simple Point & Land Use Observation",
                  formVersion = "2026092401",
                  collectorName = "David Kamau",
                  collectorEmail = "d.kamau@nyericoop.ke",
                  timestamp = "2026-09-24 09:05 UTC",
                  targetTypeLabel = "Land Use Observation",
                  coordinatesLabel = "0.4209° S, 36.9524° E (±6.2m <=10m GPS)",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "sample_point",
                        questionLabel = "Sample Location Point (geopoint)",
                        answerValue = "-0.420900 36.952400 1695.0 6.2 (Pan Allowed, <=10m GPS)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "land_use",
                        questionLabel = "Primary Land Use Category",
                        answerValue = "agroforestry_shade (Agroforestry / Shade-Grown Tree Crop)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "observer_note",
                        questionLabel = "Optional Field Note",
                        answerValue = "Terraced shade coffee intercropped with Cordia & Grevillea.",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000006-4ef1-5ef5-a180-ebabd6f0f7a8",
            label = "Wetland Riparian • -0.4168°, 36.9538°",
            datasetId = "land_use_observations",
            datasetName = "Land Use Observations",
            layerId = "layer-land-use-observations",
            geoId = "ENT-SPLU-03",
            geometryTypeLabel = "Point",
            areaHectares = 0.05,
            perimeterMeters = 25,
            coordinatesLabel = "0.4168° S, 36.9538° E",
            normalizedX = 0.68f,
            normalizedY = 0.30f,
            colorHex = 0xFF2E7D32,
            properties =
              mapOf(
                "land_use" to "wetland_riparian",
                "observer_note" to "15m vegetated riparian buffer along Chania stream.",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000002c-0423-586f-937e-81d7e9a92b77",
                  entityId = "00000006-4ef1-5ef5-a180-ebabd6f0f7a8",
                  entityLabel = "Wetland Riparian • -0.4168°, 36.9538°",
                  formId = "form-single-point-land-use",
                  formTitle = "Simple Point & Land Use Observation",
                  formVersion = "2026092401",
                  collectorName = "Grace Wanjiku",
                  collectorEmail = "g.wanjiku@nyericoop.ke",
                  timestamp = "2026-09-24 09:42 UTC",
                  targetTypeLabel = "Land Use Observation",
                  coordinatesLabel = "0.4168° S, 36.9538° E (±5.1m <=10m GPS)",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "sample_point",
                        questionLabel = "Sample Location Point (geopoint)",
                        answerValue = "-0.416800 36.953800 1668.0 5.1 (Pan Allowed, <=10m GPS)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "land_use",
                        questionLabel = "Primary Land Use Category",
                        answerValue = "wetland_riparian (Wetland / Riparian Buffer)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "observer_note",
                        questionLabel = "Optional Field Note",
                        answerValue = "15m vegetated riparian buffer along Chania stream.",
                      ),
                    ),
                )
              ),
          ),
        )
      "00000031-ffc6-5c8e-934c-d14ec07533ad" ->
        listOf(
          GeospatialEntityItem(
            id = "00000001-fd33-53c6-bdc2-e3760ec30cdb",
            label = "Cocoa • EUDR-GH-0419",
            datasetId = "commodity_plots",
            datasetName = "Commodity Plots",
            layerId = "layer-commodity-plots",
            geoId = "EUDR-GH-0419",
            geometryTypeLabel = "Polygon",
            areaHectares = 1.42,
            perimeterMeters = 512,
            coordinatesLabel = "0.4194° S, 36.9510° E",
            normalizedX = 0.31f,
            normalizedY = 0.35f,
            colorHex = 0xFFD84315,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#D84315",
                "fill" to "#D84315",
                "commodity_type" to "cocoa",
                "farmer_parcel_code" to "EUDR-GH-0419",
                "estimated_area_ha" to "1.42 ha",
                "deforestation_free_attestation" to "yes_verified",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000001b-1ba0-5775-b6cd-033a11d861d4",
                  entityId = "00000001-fd33-53c6-bdc2-e3760ec30cdb",
                  entityLabel = "Cocoa • EUDR-GH-0419",
                  formId = "form-commodity-perimeter-center",
                  formTitle = "Commodity Plot Perimeter & Center Mapping",
                  formVersion = "2026092401",
                  collectorName = "Maya Lin",
                  collectorEmail = "maya.lin@ground-demo.org",
                  timestamp = "2026-09-24 08:30 UTC",
                  targetTypeLabel = "Commodity Plot",
                  coordinatesLabel = "Center: 0.41935° S, 36.95100° E (±2.4m <=5m GPS, No Pan)",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "commodity_type",
                        questionLabel = "Forest-Risk Commodity Class (EUDR)",
                        answerValue = "cocoa (Cocoa • Theobroma cacao)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "farmer_parcel_code",
                        questionLabel = "Producer / Cooperative Parcel Code",
                        answerValue = "EUDR-GH-0419",
                      ),
                      SubmissionFieldEntry(
                        questionName = "plot_perimeter",
                        questionLabel = "Commodity Plot Perimeter Walk (Pan Override Allowed)",
                        answerValue =
                          "5 vertices walked (1.42 ha; manual pan adjustment applied on NE boundary corner)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "plot_center_point",
                        questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                        answerValue = "-0.419350 36.951000 1679.0 2.4 (±2.4m Hardware GPS Lock)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "estimated_area_ha",
                        questionLabel = "Estimated Plot Area (Hectares)",
                        answerValue = "1.42 ha",
                      ),
                      SubmissionFieldEntry(
                        questionName = "deforestation_free_attestation",
                        questionLabel = "Post-2020 Deforestation-Free Verification",
                        answerValue =
                          "yes_verified (Confirmed No Forest Conversion Since Dec 31, 2020)",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000002-4805-5b4b-9807-85db6e524462",
            label = "Coffee • EUDR-GH-0422",
            datasetId = "commodity_plots",
            datasetName = "Commodity Plots",
            layerId = "layer-commodity-plots",
            geoId = "EUDR-GH-0422",
            geometryTypeLabel = "Polygon",
            areaHectares = 0.95,
            perimeterMeters = 380,
            coordinatesLabel = "0.4211° S, 36.9487° E",
            normalizedX = 0.56f,
            normalizedY = 0.52f,
            colorHex = 0xFFD84315,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#D84315",
                "fill" to "#D84315",
                "commodity_type" to "coffee",
                "farmer_parcel_code" to "EUDR-GH-0422",
                "estimated_area_ha" to "0.95 ha",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000001c-e7b4-5cb1-84e0-5f506f6afc8b",
                  entityId = "00000002-4805-5b4b-9807-85db6e524462",
                  entityLabel = "Coffee • EUDR-GH-0422",
                  formId = "form-commodity-perimeter-center",
                  formTitle = "Commodity Plot Perimeter & Center Mapping",
                  formVersion = "2026092401",
                  collectorName = "Samuel Kariuki",
                  collectorEmail = "s.kariuki@nyericoop.ke",
                  timestamp = "2026-09-24 09:25 UTC",
                  targetTypeLabel = "Commodity Plot",
                  coordinatesLabel = "Center: 0.42110° S, 36.94870° E (±3.1m <=5m GPS, No Pan)",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "commodity_type",
                        questionLabel = "Forest-Risk Commodity Class (EUDR)",
                        answerValue = "coffee (Coffee • Coffea arabica)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "farmer_parcel_code",
                        questionLabel = "Producer / Cooperative Parcel Code",
                        answerValue = "EUDR-GH-0422",
                      ),
                      SubmissionFieldEntry(
                        questionName = "plot_perimeter",
                        questionLabel = "Commodity Plot Perimeter Walk (Pan Override Allowed)",
                        answerValue = "4 vertices walked (0.95 ha)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "plot_center_point",
                        questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                        answerValue = "-0.421100 36.948700 1704.0 3.1 (±3.1m Hardware GPS Lock)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "estimated_area_ha",
                        questionLabel = "Estimated Plot Area (Hectares)",
                        answerValue = "0.95 ha",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000003-1a57-598a-80a0-e238b897e00a",
            label = "Rubber • EUDR-GH-0428",
            datasetId = "commodity_plots",
            datasetName = "Commodity Plots",
            layerId = "layer-commodity-plots",
            geoId = "EUDR-GH-0428",
            geometryTypeLabel = "Polygon",
            areaHectares = 2.18,
            perimeterMeters = 640,
            coordinatesLabel = "0.4176° S, 36.9532° E",
            normalizedX = 0.69f,
            normalizedY = 0.29f,
            colorHex = 0xFFD84315,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#D84315",
                "fill" to "#D84315",
                "commodity_type" to "rubber",
                "farmer_parcel_code" to "EUDR-GH-0428",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000001d-f3d9-53c0-ba3f-9b2f49e8e5cc",
                  entityId = "00000003-1a57-598a-80a0-e238b897e00a",
                  entityLabel = "Rubber • EUDR-GH-0428",
                  formId = "form-commodity-perimeter-center",
                  formTitle = "Commodity Plot Perimeter & Center Mapping",
                  formVersion = "2026092401",
                  collectorName = "David Kamau",
                  collectorEmail = "d.kamau@nyericoop.ke",
                  timestamp = "2026-09-24 10:12 UTC",
                  targetTypeLabel = "Commodity Plot",
                  coordinatesLabel = "Center: 0.41760° S, 36.95320° E (±4.2m <=5m GPS, No Pan)",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "commodity_type",
                        questionLabel = "Forest-Risk Commodity Class (EUDR)",
                        answerValue = "rubber (Natural Rubber • Hevea brasiliensis)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "farmer_parcel_code",
                        questionLabel = "Producer / Cooperative Parcel Code",
                        answerValue = "EUDR-GH-0428",
                      ),
                      SubmissionFieldEntry(
                        questionName = "plot_perimeter",
                        questionLabel = "Commodity Plot Perimeter Walk (Pan Override Allowed)",
                        answerValue =
                          "5 vertices walked (2.18 ha; pan override along stream ravine)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "plot_center_point",
                        questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                        answerValue = "-0.417600 36.953200 1662.0 4.2 (±4.2m Hardware GPS Lock)",
                      ),
                    ),
                )
              ),
          ),
        )
      "00000034-c088-5ab9-8b98-e6bccb3dca71" ->
        listOf(
          GeospatialEntityItem(
            id = "00000016-cc1f-5125-8841-166ab5b5f68f",
            label = "Plot SP-01 • Upper Montane Buffer",
            datasetId = "sample_plots",
            datasetName = "Permanent Forest Sample Plots",
            layerId = "layer-sample-plots",
            geoId = "SP-01-MONTANE",
            geometryTypeLabel = "Polygon",
            areaHectares = 0.13,
            perimeterMeters = 126,
            coordinatesLabel = "0.4182° S, 36.9491° E",
            normalizedX = 0.30f,
            normalizedY = 0.34f,
            colorHex = 0xFF1B5E20,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#1B5E20",
                "fill" to "#1B5E20",
                "plot_code" to "SP-01",
                "stratum" to "Montane Moist Indigenous Forest",
                "elevation_m" to "1840m",
                "plot_radius_m" to "20m (0.125 ha)",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "00000027-ca75-5659-b125-262308b7375c",
                  entityId = "00000016-cc1f-5125-8841-166ab5b5f68f",
                  entityLabel = "Plot SP-01 • Upper Montane Buffer",
                  formId = "form-sample-plots-forest",
                  formTitle = "Sample Plot Entity & Forest Stand Assessment",
                  formVersion = "2026092401",
                  collectorName = "Maya Lin",
                  collectorEmail = "maya.lin@ground-demo.org",
                  timestamp = "2026-09-23 09:20 UTC",
                  targetTypeLabel = "Sample Plot",
                  coordinatesLabel = "0.4182° S, 36.9491° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "sample_plot_entity",
                        questionLabel = "Select Predefined Sample Plot Entity",
                        answerValue =
                          "00000016-cc1f-5125-8841-166ab5b5f68f (Plot SP-01 • Upper Montane Buffer)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "selected_plot_stratum",
                        questionLabel = "Preloaded Plot Forest Stratum",
                        answerValue = "Montane Moist Indigenous Forest",
                      ),
                      SubmissionFieldEntry(
                        questionName = "canopy_photo",
                        questionLabel = "North-Facing Hemispherical Canopy Photo",
                        answerValue = "📷 hemispherical_canopy_sp01.jpg",
                      ),
                      SubmissionFieldEntry(
                        questionName = "canopy_cover_pct",
                        questionLabel = "Measured Canopy Closure (%)",
                        answerValue = "78%",
                      ),
                      SubmissionFieldEntry(
                        questionName = "dominant_species",
                        questionLabel = "Dominant Overstory Tree Species",
                        answerValue = "Podocarpus latifolius",
                      ),
                      SubmissionFieldEntry(
                        questionName = "stand_basal_area_m2_ha",
                        questionLabel = "Stand Basal Area (m²/ha)",
                        answerValue = "28.5",
                      ),
                      SubmissionFieldEntry(
                        questionName = "regenerating_saplings_count",
                        questionLabel = "Natural Regeneration Count (Saplings > 50cm)",
                        answerValue = "18",
                      ),
                      SubmissionFieldEntry(
                        questionName = "disturbance_indicators",
                        questionLabel = "Observed Forest Disturbance Signs",
                        answerValue = "none (Undisturbed Stand)",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000017-d1a0-51d3-a686-e6ca450c23b2",
            label = "Plot SP-02 • Riparian Gallery Transect",
            datasetId = "sample_plots",
            datasetName = "Permanent Forest Sample Plots",
            layerId = "layer-sample-plots",
            geoId = "SP-02-RIPARIAN",
            geometryTypeLabel = "Polygon",
            areaHectares = 0.13,
            perimeterMeters = 126,
            coordinatesLabel = "0.4196° S, 36.9522° E",
            normalizedX = 0.56f,
            normalizedY = 0.42f,
            colorHex = 0xFF1B5E20,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#1B5E20",
                "fill" to "#1B5E20",
                "plot_code" to "SP-02",
                "stratum" to "Riparian Corridor Restoration",
                "elevation_m" to "1715m",
                "plot_radius_m" to "20m (0.125 ha)",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "00000028-86de-51b6-9d99-71dad6fafd8c",
                  entityId = "00000017-d1a0-51d3-a686-e6ca450c23b2",
                  entityLabel = "Plot SP-02 • Riparian Gallery Transect",
                  formId = "form-sample-plots-forest",
                  formTitle = "Sample Plot Entity & Forest Stand Assessment",
                  formVersion = "2026092401",
                  collectorName = "Samuel Kariuki",
                  collectorEmail = "s.kariuki@nyericoop.ke",
                  timestamp = "2026-09-23 11:05 UTC",
                  targetTypeLabel = "Sample Plot",
                  coordinatesLabel = "0.4196° S, 36.9522° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "sample_plot_entity",
                        questionLabel = "Select Predefined Sample Plot Entity",
                        answerValue =
                          "00000017-d1a0-51d3-a686-e6ca450c23b2 (Plot SP-02 • Riparian Gallery Transect)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "canopy_photo",
                        questionLabel = "North-Facing Hemispherical Canopy Photo",
                        answerValue = "📷 hemispherical_canopy_sp02.jpg",
                      ),
                      SubmissionFieldEntry(
                        questionName = "canopy_cover_pct",
                        questionLabel = "Measured Canopy Closure (%)",
                        answerValue = "84%",
                      ),
                      SubmissionFieldEntry(
                        questionName = "dominant_species",
                        questionLabel = "Dominant Overstory Tree Species",
                        answerValue = "Syzygium guineense",
                      ),
                      SubmissionFieldEntry(
                        questionName = "stand_basal_area_m2_ha",
                        questionLabel = "Stand Basal Area (m²/ha)",
                        answerValue = "31.2",
                      ),
                      SubmissionFieldEntry(
                        questionName = "regenerating_saplings_count",
                        questionLabel = "Natural Regeneration Count (Saplings > 50cm)",
                        answerValue = "22",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000018-4d16-5ab7-a57d-7dae5949cb7a",
            label = "Plot SP-03 • Shade Agroforestry Core",
            datasetId = "sample_plots",
            datasetName = "Permanent Forest Sample Plots",
            layerId = "layer-sample-plots",
            geoId = "SP-03-AGROFOREST",
            geometryTypeLabel = "Polygon",
            areaHectares = 0.13,
            perimeterMeters = 126,
            coordinatesLabel = "0.4214° S, 36.9484° E",
            normalizedX = 0.26f,
            normalizedY = 0.62f,
            colorHex = 0xFF1B5E20,
            properties =
              mapOf(
                "status" to "In progress",
                "marker-symbol" to "◐",
                "marker-color" to "#F9AB00",
                "stroke" to "#F9AB00",
                "fill" to "#F9AB00",
                "plot_code" to "SP-03",
                "stratum" to "Multi-Strata Shade Coffee",
                "elevation_m" to "1690m",
                "plot_radius_m" to "20m (0.125 ha)",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "00000029-4808-5225-9de5-551d5afe8ffc",
                  entityId = "00000018-4d16-5ab7-a57d-7dae5949cb7a",
                  entityLabel = "Plot SP-03 • Shade Agroforestry Core",
                  formId = "form-sample-plots-forest",
                  formTitle = "Sample Plot Entity & Forest Stand Assessment",
                  formVersion = "2026092401",
                  collectorName = "Maya Lin",
                  collectorEmail = "maya.lin@ground-demo.org",
                  timestamp = "2026-09-24 07:50 UTC",
                  targetTypeLabel = "Sample Plot",
                  coordinatesLabel = "0.4214° S, 36.9484° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "sample_plot_entity",
                        questionLabel = "Select Predefined Sample Plot Entity",
                        answerValue =
                          "00000018-4d16-5ab7-a57d-7dae5949cb7a (Plot SP-03 • Shade Agroforestry Core)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "canopy_photo",
                        questionLabel = "North-Facing Hemispherical Canopy Photo",
                        answerValue = "📷 hemispherical_canopy_sp03.jpg",
                      ),
                      SubmissionFieldEntry(
                        questionName = "canopy_cover_pct",
                        questionLabel = "Measured Canopy Closure (%)",
                        answerValue = "65%",
                      ),
                      SubmissionFieldEntry(
                        questionName = "dominant_species",
                        questionLabel = "Dominant Overstory Tree Species",
                        answerValue = "Cordia africana",
                      ),
                      SubmissionFieldEntry(
                        questionName = "stand_basal_area_m2_ha",
                        questionLabel = "Stand Basal Area (m²/ha)",
                        answerValue = "19.4",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000019-882d-5830-a254-1fe5abf727e3",
            label = "Plot SP-04 • Community Forest Edge",
            datasetId = "sample_plots",
            datasetName = "Permanent Forest Sample Plots",
            layerId = "layer-sample-plots",
            geoId = "SP-04-EDGE",
            geometryTypeLabel = "Polygon",
            areaHectares = 0.13,
            perimeterMeters = 126,
            coordinatesLabel = "0.4172° S, 36.9541° E",
            normalizedX = 0.72f,
            normalizedY = 0.28f,
            colorHex = 0xFF1B5E20,
            properties =
              mapOf(
                "status" to "Pending",
                "marker-symbol" to "○",
                "marker-color" to "#E65100",
                "stroke" to "#E65100",
                "fill" to "#E65100",
                "plot_code" to "SP-04",
                "stratum" to "Secondary Enrichment Planting",
                "elevation_m" to "1795m",
                "plot_radius_m" to "20m (0.125 ha)",
              ),
            submissions = emptyList(),
          ),
          GeospatialEntityItem(
            id = "0000001a-f05a-503e-9697-12c420a33cdb",
            label = "Plot SP-05 • Ridge Benchmark Control",
            datasetId = "sample_plots",
            datasetName = "Permanent Forest Sample Plots",
            layerId = "layer-sample-plots",
            geoId = "SP-05-BENCHMARK",
            geometryTypeLabel = "Polygon",
            areaHectares = 0.13,
            perimeterMeters = 126,
            coordinatesLabel = "0.4228° S, 36.9535° E",
            normalizedX = 0.68f,
            normalizedY = 0.68f,
            colorHex = 0xFF1B5E20,
            properties =
              mapOf(
                "status" to "Pending",
                "marker-symbol" to "○",
                "marker-color" to "#E65100",
                "stroke" to "#E65100",
                "fill" to "#E65100",
                "plot_code" to "SP-05",
                "stratum" to "Intact Reference Stand",
                "elevation_m" to "1910m",
                "plot_radius_m" to "20m (0.125 ha)",
              ),
            submissions = emptyList(),
          ),
        )
      "00000032-8d62-5717-8074-f4917e0cd76d" ->
        listOf(
          GeospatialEntityItem(
            id = "0000000c-a9ae-59f6-ac51-7f5a8777598f",
            label = "IND-101 • Amina Wanjiku (HH-KAK-014, Head)",
            datasetId = "past_individuals",
            datasetName = "Preloaded Past Household Individuals",
            layerId = "layer-past-individuals",
            geoId = "IND-101-KAK014",
            geometryTypeLabel = "Point",
            areaHectares = 0.0,
            perimeterMeters = 0,
            coordinatesLabel = "0.4184° S, 36.9495° E",
            normalizedX = 0.32f,
            normalizedY = 0.36f,
            colorHex = 0xFF6A1B9A,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#6A1B9A",
                "fill" to "#6A1B9A",
                "individual_id" to "0000000c-a9ae-59f6-ac51-7f5a8777598f",
                "household_code" to "HH-KAK-014",
                "relationship" to "Household Head",
                "baseline_age_2022" to "44",
                "prior_occupation_2022" to "Smallholder Coffee & Maize",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000001e-d0b8-5510-ba2f-626c8db19409",
                  entityId = "0000000c-a9ae-59f6-ac51-7f5a8777598f",
                  entityLabel = "IND-101 • Amina Wanjiku (HH-KAK-014, Head)",
                  formId = "form-household-past-individuals",
                  formTitle = "Household Follow-Up Survey (Past Individuals)",
                  formVersion = "2026092401",
                  collectorName = "Grace Wanjiku",
                  collectorEmail = "g.wanjiku@nyericoop.ke",
                  timestamp = "2026-09-23 14:10 UTC",
                  targetTypeLabel = "Past Individual",
                  coordinatesLabel = "0.4184° S, 36.9495° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "primary_respondent_id",
                        questionLabel = "Select Preloaded Past Individual",
                        answerValue =
                          "0000000c-a9ae-59f6-ac51-7f5a8777598f (IND-101 • Amina Wanjiku)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "residency_status",
                        questionLabel = "2026 Residency Verification Status",
                        answerValue = "present_resident (Still Residing in Household)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "current_primary_occupation",
                        questionLabel = "Current Primary Livelihood / Occupation",
                        answerValue = "smallholder_farming",
                      ),
                      SubmissionFieldEntry(
                        questionName = "household_size_today",
                        questionLabel = "Total Household Members Currently Residing",
                        answerValue = "6",
                      ),
                      SubmissionFieldEntry(
                        questionName = "dwelling_roof_material",
                        questionLabel = "Main Dwelling Roof Material",
                        answerValue = "iron_sheet (Corrugated Iron / Mabati)",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "0000000d-2972-591c-b23d-4918d3e3b7b1",
            label = "IND-102 • Samuel Ochieng (HH-KAK-014, Spouse)",
            datasetId = "past_individuals",
            datasetName = "Preloaded Past Household Individuals",
            layerId = "layer-past-individuals",
            geoId = "IND-102-KAK014",
            geometryTypeLabel = "Point",
            areaHectares = 0.0,
            perimeterMeters = 0,
            coordinatesLabel = "0.4187° S, 36.9501° E",
            normalizedX = 0.38f,
            normalizedY = 0.39f,
            colorHex = 0xFF6A1B9A,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#6A1B9A",
                "fill" to "#6A1B9A",
                "individual_id" to "0000000d-2972-591c-b23d-4918d3e3b7b1",
                "household_code" to "HH-KAK-014",
                "relationship" to "Spouse",
                "baseline_age_2022" to "47",
                "prior_occupation_2022" to "Dairy & Agro-Processing",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "0000001f-fb14-5f59-8a88-7663b04cf6ac",
                  entityId = "0000000d-2972-591c-b23d-4918d3e3b7b1",
                  entityLabel = "IND-102 • Samuel Ochieng (HH-KAK-014, Spouse)",
                  formId = "form-household-past-individuals",
                  formTitle = "Household Follow-Up Survey (Past Individuals)",
                  formVersion = "2026092401",
                  collectorName = "Grace Wanjiku",
                  collectorEmail = "g.wanjiku@nyericoop.ke",
                  timestamp = "2026-09-23 14:28 UTC",
                  targetTypeLabel = "Past Individual",
                  coordinatesLabel = "0.4187° S, 36.9501° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "primary_respondent_id",
                        questionLabel = "Select Preloaded Past Individual",
                        answerValue =
                          "0000000d-2972-591c-b23d-4918d3e3b7b1 (IND-102 • Samuel Ochieng)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "residency_status",
                        questionLabel = "2026 Residency Verification Status",
                        answerValue = "present_resident",
                      ),
                      SubmissionFieldEntry(
                        questionName = "current_primary_occupation",
                        questionLabel = "Current Primary Livelihood / Occupation",
                        answerValue = "agri_processing",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "0000000e-95ee-5695-ad0b-54576e8d7785",
            label = "IND-103 • Grace Atieno (HH-KAK-022, Head)",
            datasetId = "past_individuals",
            datasetName = "Preloaded Past Household Individuals",
            layerId = "layer-past-individuals",
            geoId = "IND-103-KAK022",
            geometryTypeLabel = "Point",
            areaHectares = 0.0,
            perimeterMeters = 0,
            coordinatesLabel = "0.4202° S, 36.9526° E",
            normalizedX = 0.58f,
            normalizedY = 0.48f,
            colorHex = 0xFF6A1B9A,
            properties =
              mapOf(
                "status" to "Completed",
                "marker-symbol" to "✓",
                "marker-color" to "#1E8E3E",
                "stroke" to "#6A1B9A",
                "fill" to "#6A1B9A",
                "individual_id" to "0000000e-95ee-5695-ad0b-54576e8d7785",
                "household_code" to "HH-KAK-022",
                "relationship" to "Household Head",
                "baseline_age_2022" to "38",
                "prior_occupation_2022" to "Tree Nursery & Seedlings",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "00000020-efff-56da-8ad2-db47db9476d9",
                  entityId = "0000000e-95ee-5695-ad0b-54576e8d7785",
                  entityLabel = "IND-103 • Grace Atieno (HH-KAK-022, Head)",
                  formId = "form-household-past-individuals",
                  formTitle = "Household Follow-Up Survey (Past Individuals)",
                  formVersion = "2026092401",
                  collectorName = "Maya Lin",
                  collectorEmail = "maya.lin@ground-demo.org",
                  timestamp = "2026-09-23 15:40 UTC",
                  targetTypeLabel = "Past Individual",
                  coordinatesLabel = "0.4202° S, 36.9526° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "primary_respondent_id",
                        questionLabel = "Select Preloaded Past Individual",
                        answerValue =
                          "0000000e-95ee-5695-ad0b-54576e8d7785 (IND-103 • Grace Atieno)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "residency_status",
                        questionLabel = "2026 Residency Verification Status",
                        answerValue = "present_resident",
                      ),
                      SubmissionFieldEntry(
                        questionName = "current_primary_occupation",
                        questionLabel = "Current Primary Livelihood / Occupation",
                        answerValue = "off_farm_trade",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "0000000f-23a2-50ad-b0e8-98639587eba4",
            label = "IND-104 • David Barasa (HH-KAK-014, Child)",
            datasetId = "past_individuals",
            datasetName = "Preloaded Past Household Individuals",
            layerId = "layer-past-individuals",
            geoId = "IND-104-KAK014",
            geometryTypeLabel = "Point",
            areaHectares = 0.0,
            perimeterMeters = 0,
            coordinatesLabel = "0.4215° S, 36.9488° E",
            normalizedX = 0.29f,
            normalizedY = 0.61f,
            colorHex = 0xFF6A1B9A,
            properties =
              mapOf(
                "status" to "In progress",
                "marker-symbol" to "◐",
                "marker-color" to "#F9AB00",
                "stroke" to "#F9AB00",
                "fill" to "#F9AB00",
                "individual_id" to "0000000f-23a2-50ad-b0e8-98639587eba4",
                "household_code" to "HH-KAK-014",
                "relationship" to "Adult Child",
                "baseline_age_2022" to "19",
                "prior_occupation_2022" to "Secondary Student",
              ),
            submissions =
              listOf(
                SubmissionPreviewItem(
                  id = "00000021-10bd-5b6d-9ba9-c448895f329c",
                  entityId = "0000000f-23a2-50ad-b0e8-98639587eba4",
                  entityLabel = "IND-104 • David Barasa (HH-KAK-014, Child)",
                  formId = "form-household-past-individuals",
                  formTitle = "Household Follow-Up Survey (Past Individuals)",
                  formVersion = "2026092401",
                  collectorName = "Maya Lin",
                  collectorEmail = "maya.lin@ground-demo.org",
                  timestamp = "2026-09-24 08:10 UTC",
                  targetTypeLabel = "Past Individual",
                  coordinatesLabel = "0.4215° S, 36.9488° E",
                  fields =
                    listOf(
                      SubmissionFieldEntry(
                        questionName = "primary_respondent_id",
                        questionLabel = "Select Preloaded Past Individual",
                        answerValue =
                          "0000000f-23a2-50ad-b0e8-98639587eba4 (IND-104 • David Barasa)",
                      ),
                      SubmissionFieldEntry(
                        questionName = "residency_status",
                        questionLabel = "2026 Residency Verification Status",
                        answerValue = "moved_within_district",
                      ),
                      SubmissionFieldEntry(
                        questionName = "migration_destination",
                        questionLabel = "Destination Community / District",
                        answerValue = "Kakamega Town Polytechnic Campus",
                      ),
                    ),
                )
              ),
          ),
          GeospatialEntityItem(
            id = "00000010-1195-5b30-ab83-7939d81cd71f",
            label = "IND-105 • Esther Nekesa (HH-KAK-031, Head)",
            datasetId = "past_individuals",
            datasetName = "Preloaded Past Household Individuals",
            layerId = "layer-past-individuals",
            geoId = "IND-105-KAK031",
            geometryTypeLabel = "Point",
            areaHectares = 0.0,
            perimeterMeters = 0,
            coordinatesLabel = "0.4174° S, 36.9539° E",
            normalizedX = 0.70f,
            normalizedY = 0.30f,
            colorHex = 0xFF6A1B9A,
            properties =
              mapOf(
                "status" to "Pending",
                "marker-symbol" to "○",
                "marker-color" to "#E65100",
                "stroke" to "#E65100",
                "fill" to "#E65100",
                "individual_id" to "00000010-1195-5b30-ab83-7939d81cd71f",
                "household_code" to "HH-KAK-031",
                "relationship" to "Household Head",
                "baseline_age_2022" to "61",
                "prior_occupation_2022" to "Beekeeping & Honey Cooperative",
              ),
            submissions = emptyList(),
          ),
          GeospatialEntityItem(
            id = "00000011-d6fa-5a2d-8848-5419c62a0359",
            label = "IND-106 • Peter Wafula (HH-KAK-039, Head)",
            datasetId = "past_individuals",
            datasetName = "Preloaded Past Household Individuals",
            layerId = "layer-past-individuals",
            geoId = "IND-106-KAK039",
            geometryTypeLabel = "Point",
            areaHectares = 0.0,
            perimeterMeters = 0,
            coordinatesLabel = "0.4226° S, 36.9532° E",
            normalizedX = 0.66f,
            normalizedY = 0.66f,
            colorHex = 0xFF6A1B9A,
            properties =
              mapOf(
                "status" to "Pending",
                "marker-symbol" to "○",
                "marker-color" to "#E65100",
                "stroke" to "#E65100",
                "fill" to "#E65100",
                "individual_id" to "00000011-d6fa-5a2d-8848-5419c62a0359",
                "household_code" to "HH-KAK-039",
                "relationship" to "Household Head",
                "baseline_age_2022" to "52",
                "prior_occupation_2022" to "Smallholder Tea & Agroforestry",
              ),
            submissions = emptyList(),
          ),
        )
      else -> defaultGeospatialEntities()
    }

  /**
   * Sample Geospatial Entities (`EntityRecord`s) across **Polygon**, **LineString**, and **Point**
   * geometries showcasing the 3-stage `simplestyle-spec` marker progression driven by `save_to`:
   * - Stage 1 (`"○"` Empty Circle, `#E65100` Orange): `00000009-1699-51bb-b7a3-f90a60bedd4d`
   *   (Pending baseline, 0 submissions)
   * - Stage 2 (`"◐"` Half-Filled Circle, `#F9AB00` Amber): `00000008-15d0-51f2-84eb-a72dfea5a747` &
   *   `0000000b-7633-5116-9d6d-50581002d8cf` (In progress, 1st stage recorded)
   * - Stage 3 (`"✓"` Checkmark, `#1E8E3E` Green / `#1565C0` Blue):
   *   `00000007-640e-5ed7-9874-4d6b6717f21d` & `0000000a-56d7-5c1d-9457-7457267120a3` (Completed)
   */
  fun defaultGeospatialEntities(): List<GeospatialEntityItem> =
    listOf(
      // 1. Stage 3 — Completed Polygon Entity ("✓" Checkmark marker)
      GeospatialEntityItem(
        id = "00000007-640e-5ed7-9874-4d6b6717f21d",
        label = "Plot NYR-104 • Kamau Family Parcel",
        datasetId = "coffee_parcels",
        datasetName = "Smallholder Coffee Parcels",
        layerId = "layer-coffee-parcels",
        geoId = "S2-10c4a89e2f",
        geometryTypeLabel = "Polygon",
        areaHectares = 1.84,
        perimeterMeters = 542,
        coordinatesLabel = "0.4182°S, 36.9481°E",
        normalizedX = 0.28f,
        normalizedY = 0.32f,
        colorHex = 0xFF2E7D32,
        properties =
          mapOf(
            "status" to "Completed",
            "marker-symbol" to "✓",
            "marker-color" to "#1E8E3E",
            "stroke" to "#1E8E3E",
            "fill" to "#1E8E3E",
            "Farmer / Owner" to "Josephat Kamau",
            "Cooperative" to "Othaya Farmers Co-op",
            "Washing Station" to "0000000b-7633-5116-9d6d-50581002d8cf",
            "Primary Cultivar" to "SL28 & Ruiru 11",
            "Elevation" to "1,820 m",
            "Commodity" to "Coffee",
            "Deforestation-free" to "Yes",
            "Shade trees" to "38",
            "Cherry delivered (kg)" to "640",
          ),
        submissions =
          listOf(
            SubmissionPreviewItem(
              id = "00000022-c99b-5e7e-b810-c506ebf06f19",
              entityId = "00000007-640e-5ed7-9874-4d6b6717f21d",
              entityLabel = "Plot NYR-104 • Kamau Family Parcel",
              formId = "form-eudr-baseline",
              formTitle = "EUDR Parcel Baseline Registration",
              formVersion = "v2026.09.1",
              collectorName = "Maya Lin",
              collectorEmail = "maya.lin@groundplatform.org",
              timestamp = "2026-09-18 10:14 UTC",
              targetTypeLabel = "Coffee Parcel",
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "deforestation_free_since_2020",
                    questionLabel = "Deforestation-free since Dec 2020 (EUDR)",
                    answerValue = "Yes — Verified via perimeter walk & canopy history",
                  ),
                  SubmissionFieldEntry(
                    questionName = "productive_coffee_stems",
                    questionLabel = "Productive coffee stems count",
                    answerValue = "1,420 stems",
                  ),
                  SubmissionFieldEntry(
                    questionName = "canopy_shade_pct",
                    questionLabel = "Canopy shade cover (%)",
                    answerValue = "42%",
                  ),
                  SubmissionFieldEntry(
                    questionName = "intercropped_species",
                    questionLabel = "Intercropped shade species",
                    answerValue = "Grevillea robusta, Macadamia, Cordia africana",
                  ),
                  SubmissionFieldEntry(
                    questionName = "parcel/walked_perimeter_geoshape",
                    questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
                    answerValue = "Polygon (28 vertices • 1.81 ha • ±2.1m GNSS)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "gps_horizontal_accuracy",
                    questionLabel = "Hardware GNSS Horizontal Accuracy",
                    answerValue = "2.1 m (Walked perimeter, 28 vertices)",
                  ),
                ),
            )
          ),
      ),
      // 2. Stage 3 — Completed LineString Entity ("✓" Checkmark marker on a Transect Line!)
      GeospatialEntityItem(
        id = "0000000a-56d7-5c1d-9457-7457267120a3",
        label = "Transect SHD-201 • Chinga North Agroforestry",
        datasetId = "shade_monitoring_plots",
        datasetName = "Shade Tree Monitoring Plots",
        layerId = "layer-shade-transects",
        geoId = "S2-10c4b12d9a",
        geometryTypeLabel = "LineString",
        areaHectares = 3.12,
        perimeterMeters = 738,
        coordinatesLabel = "0.4245°S, 36.9560°E",
        normalizedX = 0.65f,
        normalizedY = 0.29f,
        colorHex = 0xFF1565C0,
        properties =
          mapOf(
            "status" to "Completed",
            "marker-symbol" to "✓",
            "marker-color" to "#1565C0",
            "stroke" to "#1565C0",
            "fill" to "#1565C0",
            "Community Group" to "Chinga Restoration CFA",
            "Adjacent Parcel" to "00000007-640e-5ed7-9874-4d6b6717f21d",
            "Target survival rate" to "85%",
            "Planting Cohort" to "2025 Long Rains",
            "Elevation" to "1,865 m",
          ),
        submissions =
          listOf(
            SubmissionPreviewItem(
              id = "00000026-cca2-5594-b83f-02266f40048e",
              entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
              entityLabel = "Transect SHD-201 • Chinga North Agroforestry",
              formId = "form-shade-canopy-audit",
              formTitle = "Seasonal Shade Tree & Canopy Audit",
              formVersion = "v2026.09.2",
              collectorName = "Maya Lin",
              collectorEmail = "maya.lin@groundplatform.org",
              timestamp = "2026-09-19 08:45 UTC",
              targetTypeLabel = "Shade Tree Monitoring Plot",
              syncStatus = SyncStatus.UPLOADING,
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "audit/canopy_sample_polygon",
                    questionLabel = "Surveyed Canopy Regeneration Sub-Plot (geoshape)",
                    answerValue = "Polygon (19 vertices • 1.24 ha • North Ridge)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "surviving_saplings_count",
                    questionLabel = "Surviving indigenous saplings",
                    answerValue = "188 of 200 (94% survival)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "mean_canopy_height_m",
                    questionLabel = "Mean sapling height (m)",
                    answerValue = "2.65 m",
                  ),
                  SubmissionFieldEntry(
                    questionName = "soil_moisture_status",
                    questionLabel = "Topsoil moisture & mulch cover",
                    answerValue = "Moist — Heavy leaf litter mulch intact",
                  ),
                  SubmissionFieldEntry(
                    questionName = "pest_observation",
                    questionLabel = "Observed pest or browsing damage",
                    answerValue = "None detected",
                  ),
                ),
            ),
            SubmissionPreviewItem(
              id = "00000025-7bc5-5b86-839c-a76cee54cc81",
              entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
              entityLabel = "Transect SHD-201 • Chinga North Agroforestry",
              formId = "form-shade-canopy-audit",
              formTitle = "Seasonal Shade Tree & Canopy Audit",
              formVersion = "v2026.06.0",
              collectorName = "Samuel Kariuki",
              collectorEmail = "s.kariuki@kenyaforestry.org",
              timestamp = "2026-06-14 14:20 UTC",
              targetTypeLabel = "Shade Tree Monitoring Plot",
              syncStatus = SyncStatus.SYNCED,
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "surviving_saplings_count",
                    questionLabel = "Surviving indigenous saplings",
                    answerValue = "191 of 200 (95.5% survival)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "mean_canopy_height_m",
                    questionLabel = "Mean sapling height (m)",
                    answerValue = "2.10 m",
                  ),
                  SubmissionFieldEntry(
                    questionName = "soil_moisture_status",
                    questionLabel = "Topsoil moisture & mulch cover",
                    answerValue = "Moderate — Post-rains weeding completed",
                  ),
                ),
            ),
            SubmissionPreviewItem(
              id = "00000024-19de-50f6-ba66-f4b110edf69d",
              entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
              entityLabel = "Transect SHD-201 • Chinga North Agroforestry",
              formId = "form-deforestation-alert",
              formTitle = "GLAD Canopy Disturbance Alert Verification",
              formVersion = "v2026.03.1",
              collectorName = "Grace Wanjiku",
              collectorEmail = "g.wanjiku@kenyaforestry.org",
              timestamp = "2026-03-08 11:05 UTC",
              targetTypeLabel = "Shade Tree Monitoring Plot",
              syncStatus = SyncStatus.SYNCED,
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "surviving_saplings_count",
                    questionLabel = "Surviving indigenous saplings",
                    answerValue = "196 of 200 (98% initial establishment)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "mean_canopy_height_m",
                    questionLabel = "Mean sapling height (m)",
                    answerValue = "1.45 m",
                  ),
                ),
            ),
          ),
      ),
      // 3. Stage 2 — Half-Filled Circle Polygon Entity ("◐" In progress after household
      // interview, awaiting EUDR verification)
      GeospatialEntityItem(
        id = "00000008-15d0-51f2-84eb-a72dfea5a747",
        label = "Plot NYR-108 • Njeri Cooperative Block B",
        datasetId = "coffee_parcels",
        datasetName = "Smallholder Coffee Parcels",
        layerId = "layer-coffee-parcels",
        geoId = "S2-10c4a91c04",
        geometryTypeLabel = "Polygon",
        areaHectares = 2.45,
        perimeterMeters = 615,
        coordinatesLabel = "0.4290°S, 36.9442°E",
        normalizedX = 0.34f,
        normalizedY = 0.64f,
        colorHex = 0xFF2E7D32,
        properties =
          mapOf(
            "status" to "In progress",
            "marker-symbol" to "◐",
            "marker-color" to "#F9AB00",
            "stroke" to "#F9AB00",
            "fill" to "#F9AB00",
            "Farmer / Owner" to "Beatrice Njeri",
            "Cooperative" to "Othaya Farmers Co-op",
            "Washing Station" to "0000000b-7633-5116-9d6d-50581002d8cf",
            "Primary Cultivar" to "Batian & SL34",
            "Elevation" to "1,795 m",
            "Commodity" to "Coffee",
            "Deforestation-free" to "Yes",
            "Shade trees" to "52",
            "Cherry delivered (kg)" to "910",
          ),
        submissions =
          listOf(
            SubmissionPreviewItem(
              id = "00000023-36c6-5f07-a7a8-5922ec72bed0",
              entityId = "00000008-15d0-51f2-84eb-a72dfea5a747",
              entityLabel = "Plot NYR-108 • Njeri Cooperative Block B",
              formId = "form-household-interview",
              formTitle = "Smallholder Household Socio-Economic Survey",
              formVersion = "v2026.09.1",
              collectorName = "Samuel Kariuki",
              collectorEmail = "s.kariuki@kenyaforestry.org",
              timestamp = "2026-09-17 16:02 UTC",
              targetTypeLabel = "Coffee Parcel",
              syncStatus = SyncStatus.UPLOADING,
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "parcel/walked_perimeter_geoshape",
                    questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
                    answerValue = "Polygon (34 vertices • 2.41 ha • ±1.9m GNSS)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "deforestation_free_since_2020",
                    questionLabel = "Deforestation-free since Dec 2020 (EUDR)",
                    answerValue = "Yes — Verified perennial agroforestry parcel",
                  ),
                  SubmissionFieldEntry(
                    questionName = "productive_coffee_stems",
                    questionLabel = "Productive coffee stems count",
                    answerValue = "1,980 stems",
                  ),
                  SubmissionFieldEntry(
                    questionName = "canopy_shade_pct",
                    questionLabel = "Canopy shade cover (%)",
                    answerValue = "38%",
                  ),
                ),
            )
          ),
      ),
      // 4. Stage 1 — Initialized Empty Circle Polygon Entity ("○" Pending baseline, 0
      // submissions)
      GeospatialEntityItem(
        id = "00000009-1699-51bb-b7a3-f90a60bedd4d",
        label = "Plot NYR-112 • Kariuki Hillside Parcel",
        datasetId = "coffee_parcels",
        datasetName = "Smallholder Coffee Parcels",
        layerId = "layer-coffee-parcels",
        geoId = "S2-10c4a98e7b",
        geometryTypeLabel = "Polygon",
        areaHectares = 1.56,
        perimeterMeters = 498,
        coordinatesLabel = "0.4150°S, 36.9585°E",
        normalizedX = 0.24f,
        normalizedY = 0.80f,
        colorHex = 0xFF2E7D32,
        properties =
          mapOf(
            "status" to "Pending",
            "marker-symbol" to "○",
            "marker-color" to "#E65100",
            "stroke" to "#E65100",
            "fill" to "#E65100",
            "Farmer / Owner" to "Daniel Kariuki",
            "Cooperative" to "Othaya Farmers Co-op",
            "Washing Station" to "0000000b-7633-5116-9d6d-50581002d8cf",
            "Primary Cultivar" to "SL28 & Batian",
            "Elevation" to "1,845 m",
            "Commodity" to "Coffee",
            "Deforestation-free" to "Unknown",
            "Shade trees" to "27",
            "Cherry delivered (kg)" to "Not weighed",
          ),
        submissions = emptyList(),
        syncStatus = SyncStatus.FAILED,
      ),
      // 5. Stage 2 — Half-Filled Circle Point Entity ("◐" Cooperative Washing Station)
      GeospatialEntityItem(
        id = "0000000b-7633-5116-9d6d-50581002d8cf",
        label = "Station WSH-01 • Gura River Wet Mill",
        datasetId = "washing_stations",
        datasetName = "Cooperative Washing Stations",
        layerId = "layer-water-points",
        geoId = "S2-10c4a77f11",
        geometryTypeLabel = "Point",
        areaHectares = 0.15,
        perimeterMeters = 160,
        coordinatesLabel = "0.4212°S, 36.9518°E",
        normalizedX = 0.72f,
        normalizedY = 0.62f,
        colorHex = 0xFFEF6C00,
        properties =
          mapOf(
            "status" to "In progress",
            "marker-symbol" to "◐",
            "marker-color" to "#EF6C00",
            "stroke" to "#EF6C00",
            "fill" to "#EF6C00",
            "Station Manager" to "Peter Mwangi",
            "Water Source" to "Gura River Intake",
            "Eco-Pulper Installed" to "Yes (Closed-loop recirculation)",
          ),
        submissions =
          listOf(
            SubmissionPreviewItem(
              id = "00000030-ca50-56e0-8458-9c5728954d52",
              entityId = "0000000b-7633-5116-9d6d-50581002d8cf",
              entityLabel = "Station WSH-01 • Gura River Wet Mill",
              formId = "form-water-quality",
              formTitle = "Washing Station Effluent & Water Check",
              formVersion = "v2026.08.4",
              collectorName = "Maya Lin",
              collectorEmail = "maya.lin@groundplatform.org",
              timestamp = "2026-09-18 17:30 UTC",
              targetTypeLabel = "Cooperative Washing Station",
              syncStatus = SyncStatus.FAILED,
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "inspection/riparian_buffer_zone",
                    questionLabel = "Riparian Filtration Buffer Polygon (geoshape)",
                    answerValue = "Polygon (14 vertices • 0.48 ha • Constructed Wetland)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "water_ph",
                    questionLabel = "Downstream water pH reading",
                    answerValue = "6.8 pH (Within normal range)",
                  ),
                  SubmissionFieldEntry(
                    questionName = "recirculation_active",
                    questionLabel = "Recirculation tank operational",
                    answerValue = "Yes — Zero untreated discharge",
                  ),
                ),
            ),
            SubmissionPreviewItem(
              id = "0000002f-ac7c-58b4-add0-71d03269fda7",
              entityId = "0000000b-7633-5116-9d6d-50581002d8cf",
              entityLabel = "Station WSH-01 • Gura River Wet Mill",
              formId = "form-water-quality",
              formTitle = "Washing Station Effluent & Water Check",
              formVersion = "v2026.08.4",
              collectorName = "Grace Wanjiku",
              collectorEmail = "g.wanjiku@kenyaforestry.org",
              timestamp = "2026-08-22 09:50 UTC",
              targetTypeLabel = "Cooperative Washing Station",
              fields =
                listOf(
                  SubmissionFieldEntry(
                    questionName = "water_ph",
                    questionLabel = "Downstream water pH reading",
                    answerValue = "6.7 pH",
                  ),
                  SubmissionFieldEntry(
                    questionName = "recirculation_active",
                    questionLabel = "Recirculation tank operational",
                    answerValue = "Yes",
                  ),
                ),
            ),
          ),
      ),
    )
}
