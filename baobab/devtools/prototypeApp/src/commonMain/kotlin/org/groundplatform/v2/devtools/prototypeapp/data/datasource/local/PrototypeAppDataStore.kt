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

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LayerSourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationOperationKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

/**
 * Static and in-memory local data store (`PrototypeAppDataStore`) providing all hardcoded sample
 * surveys, geospatial entities, forms, standalone submissions, submission geometries, map layers,
 * places gazetteer, offline basemap tile packages, mutation log records, and XForms presets for
 * `devtools/prototypeApp`.
 */
class PrototypeAppDataStore(
  initialSurveys: List<SurveyPreviewItem> = defaultSampleSurveys(),
  initialActiveSurveyId: String = "survey-kenya-coffee",
  initialMapLayers: List<MapLayerItem> = mapLayersForSurvey(initialActiveSurveyId),
  initialForms: List<FormPreviewItem> = formsForSurvey(initialActiveSurveyId),
  initialEntities: List<GeospatialEntityItem> = entitiesForSurvey(initialActiveSurveyId),
  initialStandaloneSubmissions: List<SubmissionPreviewItem> =
    standaloneSubmissionsForSurvey(initialActiveSurveyId),
  initialSubmissionGeometries: List<SubmissionGeometryPolygon> =
    submissionGeometriesForSurvey(initialActiveSurveyId),
  initialOfflineTilePackages: List<OfflineTilePackageItem> = defaultOfflineTilePackages(),
  initialMutations: List<MutationLogItem> = defaultMutations(),
  initialPlaces: List<SurveyPlaceItem> = defaultSurveyPlaces(),
  initialUserSettings: UserSettings = defaultUserSettings(),
) {
  var surveys: List<SurveyPreviewItem> = initialSurveys
  var activeSurveyId: String = initialActiveSurveyId
  var mapLayers: List<MapLayerItem> = initialMapLayers
  var forms: List<FormPreviewItem> = initialForms
  var entities: List<GeospatialEntityItem> = initialEntities
  var standaloneSubmissions: List<SubmissionPreviewItem> = initialStandaloneSubmissions
  var submissionGeometries: List<SubmissionGeometryPolygon> = initialSubmissionGeometries
  var offlineTilePackages: List<OfflineTilePackageItem> = initialOfflineTilePackages
  var mutations: List<MutationLogItem> = initialMutations
  var places: List<SurveyPlaceItem> = initialPlaces
  var userSettings: UserSettings = initialUserSettings
  var uploadedMediaCacheSizeLabel: String = DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL
  var uploadedMediaFileCount: Int = DEFAULT_UPLOADED_MEDIA_FILE_COUNT

  /** Loads all datasets for [surveyId] into the active data store state. */
  fun loadSurveyDatasets(surveyId: String) {
    activeSurveyId = surveyId
    forms = formsForSurvey(surveyId)
    entities = entitiesForSurvey(surveyId)
    standaloneSubmissions = standaloneSubmissionsForSurvey(surveyId)
    submissionGeometries = submissionGeometriesForSurvey(surveyId)
    mapLayers = mapLayersForSurvey(surveyId)
  }

  /** Resets the data store back to its initial hardcoded defaults. */
  fun resetToDefaults() {
    activeSurveyId = "survey-kenya-coffee"
    surveys = defaultSampleSurveys()
    loadSurveyDatasets(activeSurveyId)
    offlineTilePackages = defaultOfflineTilePackages()
    mutations = defaultMutations()
    places = defaultSurveyPlaces()
    userSettings = defaultUserSettings()
    uploadedMediaCacheSizeLabel = DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL
    uploadedMediaFileCount = DEFAULT_UPLOADED_MEDIA_FILE_COUNT
  }

  companion object {
    const val DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL: String = "38.4 MB"
    const val DEFAULT_UPLOADED_MEDIA_FILE_COUNT: Int = 27
    const val ENTITY_REF_FIELD_PATH: String = "/data/target_entity"

    val DEFAULT_PROTOTYPE_XFORMS_XML: String = WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml

    const val BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML: String =
      """<h:html xmlns="http://www.w3.org/2002/xforms"
        xmlns:h="http://www.w3.org/1999/xhtml"
        xmlns:jr="http://openrosa.org/javarosa">
  <h:head>
    <h:title>Shade Tree &amp; Baobab Biometrics</h:title>
    <model>
      <instance>
        <data id="baobab_biometrics" version="2026091901">
          <meta>
            <instanceID/>
          </meta>
          <species>Adansonia digitata</species>
          <height_m>18.5</height_m>
          <circumference_m>12.2</circumference_m>
          <health_status>healthy</health_status>
        </data>
      </instance>
      <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
      <bind nodeset="/data/species" type="string" required="true()"/>
      <bind nodeset="/data/height_m" type="decimal"/>
      <bind nodeset="/data/circumference_m" type="decimal"/>
      <bind nodeset="/data/health_status" type="string"/>
    </model>
  </h:head>
  <h:body>
    <input ref="/data/species">
      <label>Tree Species</label>
    </input>
    <input ref="/data/height_m">
      <label>Canopy Height (meters)</label>
    </input>
    <input ref="/data/circumference_m">
      <label>Trunk Circumference (meters)</label>
    </input>
    <select1 ref="/data/health_status">
      <label>Crown &amp; Bark Health Status</label>
      <item>
        <label>Healthy</label>
        <value>healthy</value>
      </item>
      <item>
        <label>Stressed</label>
        <value>stressed</value>
      </item>
    </select1>
  </h:body>
</h:html>"""

    fun defaultUserSettings(): UserSettings =
      UserSettings(
        language = "en",
        measurementUnits = MeasurementUnitSystem.METRIC,
        shouldUploadPhotosOnWifiOnly = true,
      )

    fun builtInFallbackXFormsXmlForForm(form: FormPreviewItem): String {
      when (form.id) {
        "form-single-point-land-use" -> return WorkbenchExampleForm.SINGLE_POINT_LAND_USE.xformsXml
        "form-sample-plots-forest" ->
          return WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT.xformsXml
        "form-commodity-perimeter-center" ->
          return WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER.xformsXml
        "form-household-past-individuals" ->
          return WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS.xformsXml
        "form-coffee-parcel" -> return WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml
      }
      val escapedTitle =
        form.title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
      val safeFormId = form.id.replace('-', '_')
      return """<h:html xmlns="http://www.w3.org/2002/xforms"
        xmlns:h="http://www.w3.org/1999/xhtml"
        xmlns:jr="http://openrosa.org/javarosa">
  <h:head>
    <h:title>$escapedTitle</h:title>
    <model>
      <instance>
        <data id="$safeFormId" version="${form.version}">
          <meta>
            <instanceID/>
          </meta>
          <organizer_action_cta>Verified in field (${form.ctaLabel})</organizer_action_cta>
          <collector_observation>Field verification complete</collector_observation>
          <canopy_or_parcel_metric>94</canopy_or_parcel_metric>
        </data>
      </instance>
      <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
      <bind nodeset="/data/organizer_action_cta" type="string" required="true()"/>
      <bind nodeset="/data/collector_observation" type="string"/>
      <bind nodeset="/data/canopy_or_parcel_metric" type="int"/>
    </model>
  </h:head>
  <h:body>
    <input ref="/data/organizer_action_cta">
      <label>Completed Form Action (${form.ctaLabel})</label>
    </input>
    <input ref="/data/collector_observation">
      <label>Field Observation Notes</label>
    </input>
    <input ref="/data/canopy_or_parcel_metric">
      <label>Measured Field Metric / Score (%)</label>
    </input>
  </h:body>
</h:html>"""
    }
    /** Alias for [defaultSurveyPlaces]. */
    fun defaultPlaces(): List<SurveyPlaceItem> = defaultSurveyPlaces()

    /**
     * Default geographic places, towns, landmarks, road junctions, and hydrology features from the
     * Mapbox Places API (`mapbox.places`) searchable via `"Search places or map features..."`.
     */
    fun defaultSurveyPlaces(): List<SurveyPlaceItem> =
      listOf(
        SurveyPlaceItem(
          id = "place-othaya-town",
          name = "Othaya Town Center",
          categoryLabel = "Town",
          regionSubtitle = "Othaya Sub-County, Nyeri • Cooperative Market Hub",
          coordinatesLabel = "0.4192°S, 36.9498°E",
          normalizedX = 0.40f,
          normalizedY = 0.47f,
          zoomDelta = -2.5f,
          longitude = 36.9498,
          latitude = -0.4192,
          bboxMinLng = 36.9220,
          bboxMinLat = -0.4420,
          bboxMaxLng = 36.9780,
          bboxMaxLat = -0.3960,
          mapboxPlaceId = "place.othaya.101",
        ),
        SurveyPlaceItem(
          id = "place-chinga-dam",
          name = "Chinga Dam & Reservoir",
          categoryLabel = "Hydrology / Dam",
          regionSubtitle = "Chinga Ward, Nyeri County • Upper Gura Catchment",
          coordinatesLabel = "0.4258°S, 36.9574°E",
          normalizedX = 0.78f,
          normalizedY = 0.74f,
          zoomDelta = -1.7f,
          longitude = 36.9574,
          latitude = -0.4258,
          bboxMinLng = 36.9430,
          bboxMinLat = -0.4380,
          bboxMaxLng = 36.9720,
          bboxMaxLat = -0.4130,
          mapboxPlaceId = "poi.chinga.102",
        ),
        SurveyPlaceItem(
          id = "place-gura-river-bridge",
          name = "Gura River Crossing",
          categoryLabel = "River Crossing",
          regionSubtitle = "Gura Valley Riparian Corridor • 1,785 m",
          coordinatesLabel = "0.4215°S, 36.9535°E",
          normalizedX = 0.62f,
          normalizedY = 0.56f,
          zoomDelta = 0.2f,
          longitude = 36.9535,
          latitude = -0.4215,
          bboxMinLng = 36.9495,
          bboxMinLat = -0.4255,
          bboxMaxLng = 36.9575,
          bboxMaxLat = -0.4175,
          mapboxPlaceId = "poi.gura.103",
        ),
        SurveyPlaceItem(
          id = "place-karima-forest",
          name = "Karima Hill Forest Reserve",
          categoryLabel = "Forest Reserve",
          regionSubtitle = "Othaya Highlands • Sacred Indigenous Canopy",
          coordinatesLabel = "0.4160°S, 36.9465°E",
          normalizedX = 0.20f,
          normalizedY = 0.22f,
          zoomDelta = -1.7f,
          longitude = 36.9465,
          latitude = -0.4160,
          bboxMinLng = 36.9320,
          bboxMinLat = -0.4280,
          bboxMaxLng = 36.9610,
          bboxMaxLat = -0.4040,
          mapboxPlaceId = "poi.karima.104",
        ),
        SurveyPlaceItem(
          id = "place-nyeri-town",
          name = "Nyeri Town",
          categoryLabel = "Regional Hub",
          regionSubtitle = "Nyeri County Headquarters • Central Highlands",
          coordinatesLabel = "0.4148°S, 36.9510°E",
          normalizedX = 0.49f,
          normalizedY = 0.16f,
          zoomDelta = -3.5f,
          longitude = 36.9510,
          latitude = -0.4148,
          bboxMinLng = 36.8900,
          bboxMinLat = -0.4650,
          bboxMaxLng = 37.0120,
          bboxMaxLat = -0.3650,
          mapboxPlaceId = "place.nyeri.105",
        ),
        SurveyPlaceItem(
          id = "place-iriaini-market",
          name = "Iriaini Coffee & Tea Market",
          categoryLabel = "Village / Market Center",
          regionSubtitle = "Iriaini Ward, Othaya • Smallholder Buying Center",
          coordinatesLabel = "0.4238°S, 36.9478°E",
          normalizedX = 0.26f,
          normalizedY = 0.68f,
          zoomDelta = -0.8f,
          longitude = 36.9478,
          latitude = -0.4238,
          bboxMinLng = 36.9410,
          bboxMinLat = -0.4305,
          bboxMaxLng = 36.9545,
          bboxMaxLat = -0.4170,
          mapboxPlaceId = "poi.iriaini.106",
        ),
        SurveyPlaceItem(
          id = "place-kenya-country",
          name = "Kenya",
          categoryLabel = "Country",
          regionSubtitle = "East Africa • Republic of Kenya",
          coordinatesLabel = "0.0236°N, 37.9062°E",
          normalizedX = 0.50f,
          normalizedY = 0.50f,
          zoomDelta = -10.1f,
          longitude = 37.9062,
          latitude = 0.0236,
          bboxMinLng = 33.9098,
          bboxMinLat = -4.6780,
          bboxMaxLng = 41.8995,
          bboxMaxLat = 5.5060,
          mapboxPlaceId = "country.kenya.01",
        ),
      )

    /**
     * Default sample surveys shared with the user, including the 5 swappable Workbench Example
     * Surveys (each with its own preloaded entities, sample submissions, geometries, map layers,
     * and XForms definition) plus a remote undownloaded survey for offline download testing.
     */
    fun defaultSampleSurveys(): List<SurveyPreviewItem> =
      listOf(
        SurveyPreviewItem(
          id = "survey-single-point-land-use",
          title = "1. Simple Point & Land Use Survey",
          description =
            "Single GPS point (map pan allowed, <= 10m accuracy required) and land-use classification. Forms auto-create land_use_observations entities via save_to.",
          location = "Arusha, Tanzania",
          coordinatesLabel = "3.38°S, 36.68°E",
          offlineSizeLabel = "4.2 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.SAVANNA,
          entityCount = 3,
        ),
        SurveyPreviewItem(
          id = "survey-sample-plots-forest",
          title = "2. Sample Plot Forest Assessment Survey",
          description =
            "Predefined permanent sample plot entities (SP-01 to SP-05) with forest stand assessment form: selecting the sample plot entity, taking a canopy photo, canopy cover %, and basal area.",
          location = "Pará, Brazil",
          coordinatesLabel = "3.46°S, 62.21°W",
          offlineSizeLabel = "14.8 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.RAINFOREST,
          entityCount = 5,
        ),
        SurveyPreviewItem(
          id = "survey-commodity-perimeter-center",
          title = "3. Commodity Plot Perimeter & Center Mapping (EUDR)",
          description =
            "Walk forest-risk commodity plot boundary (GPS override / manual pan allowed while walking) and capture the plot center point. Forms auto-create commodity_plots entities via save_to.",
          location = "Ashanti Region, Ghana",
          coordinatesLabel = "6.69°N, 1.62°W",
          offlineSizeLabel = "11.5 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.WATERSHED,
          entityCount = 3,
        ),
        SurveyPreviewItem(
          id = "survey-household-past-individuals",
          title = "4. Household Panel Survey (Past Individuals)",
          description =
            "Household longitudinal survey using a preloaded roster of past household individuals (IND-101 to IND-106) for residency reconciliation, occupation updates, and new member enrollment.",
          location = "Kakamega, Western Province",
          coordinatesLabel = "0.28°N, 34.75°E",
          offlineSizeLabel = "8.4 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.COASTAL_DELTA,
          entityCount = 6,
        ),
        SurveyPreviewItem(
          id = "survey-kenya-coffee",
          title = "5. All Form Field Types Showcase (Kenya Coffee)",
          description =
            "EUDR traceability polygon mapping, shade-tree biodiversity inventory, and all 20+ XForms field types showcase for cooperative coffee growers.",
          location = "Nyeri County, Kenya",
          coordinatesLabel = "0.42°S, 36.95°E",
          offlineSizeLabel = "9.6 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.HIGHLAND_AGRI,
          entityCount = 5,
        ),
        SurveyPreviewItem(
          id = "survey-serengeti-corridor",
          title = "Mekong Delta Mangrove Restoration",
          description =
            "Coastal shoreline erosion monitoring and sapling survival rate audits across intertidal restoration zones.",
          location = "Cần Thơ, Vietnam",
          coordinatesLabel = "9.82°N, 106.34°E",
          offlineSizeLabel = "12.8 MB",
          isDownloaded = false,
          thumbnailTheme = MapThumbnailTheme.PEATLAND,
          entityCount = 204,
        ),
      )

    private val exampleFormDefCache = mutableMapOf<WorkbenchExampleForm, FormDef?>()

    /** Returns a cached parsed [FormDef] for [example], parsing it at most once. */
    fun cachedExampleFormDef(example: WorkbenchExampleForm): FormDef? =
      exampleFormDefCache.getOrPut(example) {
        try {
          XFormsXmlSerializer.deserializeFormDef(example.xformsXml)
        } catch (_: Exception) {
          null
        }
      }

    /** Maps a [WorkbenchExampleForm] to its canonical Example Survey ID. */
    fun surveyIdForExampleForm(example: WorkbenchExampleForm): String =
      when (example) {
        WorkbenchExampleForm.SINGLE_POINT_LAND_USE -> "survey-single-point-land-use"
        WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT -> "survey-sample-plots-forest"
        WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER -> "survey-commodity-perimeter-center"
        WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS ->
          "survey-household-past-individuals"
        WorkbenchExampleForm.ALL_FIELD_TYPES -> "survey-kenya-coffee"
      }

    /** Maps an Example Survey ID to its corresponding [WorkbenchExampleForm]. */
    fun exampleFormForSurveyId(surveyId: String): WorkbenchExampleForm =
      when (surveyId) {
        "survey-single-point-land-use" -> WorkbenchExampleForm.SINGLE_POINT_LAND_USE
        "survey-sample-plots-forest" -> WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT
        "survey-commodity-perimeter-center" -> WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER
        "survey-household-past-individuals" ->
          WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS
        else -> WorkbenchExampleForm.ALL_FIELD_TYPES
      }

    /** Returns the preloaded entity count for [surveyId] without allocating entity lists. */
    fun preloadedEntityCountForSurvey(surveyId: String): Int =
      when (surveyId) {
        "survey-single-point-land-use" -> 3
        "survey-sample-plots-forest" -> 5
        "survey-commodity-perimeter-center" -> 3
        "survey-household-past-individuals" -> 6
        else -> 5
      }

    /** Returns the preloaded submission count for [surveyId] without allocating submission lists. */
    fun preloadedSubmissionCountForSurvey(surveyId: String): Int =
      when (surveyId) {
        "survey-single-point-land-use" -> 3
        "survey-sample-plots-forest" -> 3
        "survey-commodity-perimeter-center" -> 3
        "survey-household-past-individuals" -> 4
        else -> 9
      }

    /** Returns the preloaded [FormPreviewItem] list for [surveyId]. */
    fun formsForSurvey(surveyId: String): List<FormPreviewItem> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            FormPreviewItem(
              id = "form-single-point-land-use",
              title = "Simple Point & Land Use Observation",
              description =
                "Collect a single point (map pan allowed, <= 10m GPS accuracy required) and primary land-use category. Auto-saves to land_use_observations entity dataset.",
              version = "2026092401",
              targetDatasetId = "land_use_observations",
              targetDatasetName = "Land Use Observations (land_use_observations)",
              questionCount = 3,
              ctaLabel = "Collect Point & Land Use",
              requiresEntity = false,
            )
          )
        "survey-sample-plots-forest" ->
          listOf(
            FormPreviewItem(
              id = "form-sample-plots-forest",
              title = "Sample Plot Entity & Forest Stand Assessment",
              description =
                "Select a predefined sample plot entity (SP-01 to SP-05), capture an upward canopy photo, and record canopy closure %, dominant species, basal area, sapling count, and disturbances.",
              version = "2026092401",
              targetDatasetId = "sample_plots",
              targetDatasetName = "Permanent Forest Sample Plots (sample_plots)",
              questionCount = 9,
              ctaLabel = "Assess Forest Sample Plot",
            )
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            FormPreviewItem(
              id = "form-commodity-perimeter-center",
              title = "Commodity Plot Perimeter & Center Mapping",
              description =
                "Walk the perimeter of a forest-risk commodity plot (manual pan / GPS override allowed) and capture the plot center point. Auto-saves to commodity_plots entity dataset.",
              version = "2026092401",
              targetDatasetId = "commodity_plots",
              targetDatasetName = "Commodity Plots (commodity_plots)",
              questionCount = 6,
              ctaLabel = "Map Plot Perimeter & Center",
              requiresEntity = false,
            )
          )
        "survey-household-past-individuals" ->
          listOf(
            FormPreviewItem(
              id = "form-household-past-individuals",
              title = "Household Follow-Up Survey (Past Individuals)",
              description =
                "Select a preloaded past individual (IND-101 to IND-106), reconcile residency & livelihood status since the 2022 baseline wave, and enroll new household members.",
              version = "2026092401",
              targetDatasetId = "past_individuals",
              targetDatasetName = "Preloaded Past Household Individuals (past_individuals)",
              questionCount = 11,
              ctaLabel = "Conduct Household Follow-Up",
            )
          )
        else -> defaultForms()
      }

    /** Returns the preloaded [GeospatialEntityItem] list for [surveyId]. */
    fun entitiesForSurvey(surveyId: String): List<GeospatialEntityItem> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            GeospatialEntityItem(
              id = "ent-splu-01",
              label = "Primary Forest • -0.4182°, 36.9491°",
              datasetId = "land_use_observations",
              datasetName = "Land Use Observations (land_use_observations)",
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
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1E8E3E",
                  "fill" to "#1E8E3E",
                  "land_use" to "primary_forest",
                  "observer_note" to "Closed-canopy indigenous montane forest stand.",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-splu-01",
                    entityId = "ent-splu-01",
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
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-splu-02",
              label = "Agroforestry • -0.4209°, 36.9524°",
              datasetId = "land_use_observations",
              datasetName = "Land Use Observations (land_use_observations)",
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
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1E8E3E",
                  "fill" to "#1E8E3E",
                  "land_use" to "agroforestry_shade",
                  "observer_note" to "Terraced shade coffee intercropped with Cordia & Grevillea.",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-splu-02",
                    entityId = "ent-splu-02",
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
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-splu-03",
              label = "Wetland Riparian • -0.4168°, 36.9538°",
              datasetId = "land_use_observations",
              datasetName = "Land Use Observations (land_use_observations)",
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
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1E8E3E",
                  "fill" to "#1E8E3E",
                  "land_use" to "wetland_riparian",
                  "observer_note" to "15m vegetated riparian buffer along Chania stream.",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-splu-03",
                    entityId = "ent-splu-03",
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
                  ),
                ),
            ),
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            GeospatialEntityItem(
              id = "ent-eudr-0419",
              label = "Cocoa • EUDR-GH-0419",
              datasetId = "commodity_plots",
              datasetName = "Commodity Plots (commodity_plots)",
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
                    id = "sub-eudr-gh-0419",
                    entityId = "ent-eudr-0419",
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
                          answerValue = "yes_verified (Confirmed No Forest Conversion Since Dec 31, 2020)",
                        ),
                      ),
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-eudr-0422",
              label = "Coffee • EUDR-GH-0422",
              datasetId = "commodity_plots",
              datasetName = "Commodity Plots (commodity_plots)",
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
                    id = "sub-eudr-gh-0422",
                    entityId = "ent-eudr-0422",
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
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-eudr-0428",
              label = "Rubber • EUDR-GH-0428",
              datasetId = "commodity_plots",
              datasetName = "Commodity Plots (commodity_plots)",
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
                    id = "sub-eudr-gh-0428",
                    entityId = "ent-eudr-0428",
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
                          answerValue = "5 vertices walked (2.18 ha; pan override along stream ravine)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_center_point",
                          questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                          answerValue = "-0.417600 36.953200 1662.0 4.2 (±4.2m Hardware GPS Lock)",
                        ),
                      ),
                  ),
                ),
            ),
          )
        "survey-sample-plots-forest" ->
          listOf(
            GeospatialEntityItem(
              id = "plot_sp01",
              label = "Plot SP-01 • Upper Montane Buffer",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
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
                    id = "sub-sp01-assess-2026",
                    entityId = "plot_sp01",
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
                          answerValue = "plot_sp01 (Plot SP-01 • Upper Montane Buffer)",
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
              id = "plot_sp02",
              label = "Plot SP-02 • Riparian Gallery Transect",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
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
                    id = "sub-sp02-assess-2026",
                    entityId = "plot_sp02",
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
                          answerValue = "plot_sp02 (Plot SP-02 • Riparian Gallery Transect)",
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
              id = "plot_sp03",
              label = "Plot SP-03 • Shade Agroforestry Core",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
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
                    id = "sub-sp03-assess-2026",
                    entityId = "plot_sp03",
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
                          answerValue = "plot_sp03 (Plot SP-03 • Shade Agroforestry Core)",
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
              id = "plot_sp04",
              label = "Plot SP-04 • Community Forest Edge",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
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
              id = "plot_sp05",
              label = "Plot SP-05 • Ridge Benchmark Control",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
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
        "survey-household-past-individuals" ->
          listOf(
            GeospatialEntityItem(
              id = "ind_101",
              label = "IND-101 • Amina Wanjiku (HH-KAK-014, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
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
                  "individual_id" to "ind_101",
                  "household_code" to "HH-KAK-014",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "44",
                  "prior_occupation_2022" to "Smallholder Coffee & Maize",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind101-wave4",
                    entityId = "ind_101",
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
                          answerValue = "ind_101 (IND-101 • Amina Wanjiku)",
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
              id = "ind_102",
              label = "IND-102 • Samuel Ochieng (HH-KAK-014, Spouse)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
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
                  "individual_id" to "ind_102",
                  "household_code" to "HH-KAK-014",
                  "relationship" to "Spouse",
                  "baseline_age_2022" to "47",
                  "prior_occupation_2022" to "Dairy & Agro-Processing",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind102-wave4",
                    entityId = "ind_102",
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
                          answerValue = "ind_102 (IND-102 • Samuel Ochieng)",
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
              id = "ind_103",
              label = "IND-103 • Grace Atieno (HH-KAK-022, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
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
                  "individual_id" to "ind_103",
                  "household_code" to "HH-KAK-022",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "38",
                  "prior_occupation_2022" to "Tree Nursery & Seedlings",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind103-wave4",
                    entityId = "ind_103",
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
                          answerValue = "ind_103 (IND-103 • Grace Atieno)",
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
              id = "ind_104",
              label = "IND-104 • David Barasa (HH-KAK-014, Child)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
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
                  "individual_id" to "ind_104",
                  "household_code" to "HH-KAK-014",
                  "relationship" to "Adult Child",
                  "baseline_age_2022" to "19",
                  "prior_occupation_2022" to "Secondary Student",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind104-wave4",
                    entityId = "ind_104",
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
                          answerValue = "ind_104 (IND-104 • David Barasa)",
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
              id = "ind_105",
              label = "IND-105 • Esther Nekesa (HH-KAK-031, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
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
                  "individual_id" to "ind_105",
                  "household_code" to "HH-KAK-031",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "61",
                  "prior_occupation_2022" to "Beekeeping & Honey Cooperative",
                ),
              submissions = emptyList(),
            ),
            GeospatialEntityItem(
              id = "ind_106",
              label = "IND-106 • Peter Wafula (HH-KAK-039, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
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
                  "individual_id" to "ind_106",
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

    /** Returns the preloaded [MapLayerItem] list for [surveyId]. */
    fun mapLayersForSurvey(surveyId: String): List<MapLayerItem> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            MapLayerItem(
              id = "layer-land-use-observations",
              label = "Land Use Observations",
              sourceDescription = "Dataset: land_use_observations (Point)",
              colorHex = 0xFF2E7D32,
              geometryTypeLabel = "Point",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "land-use observation",
              pluralItemLabel = "land-use observations",
            )
          )
        "survey-sample-plots-forest" ->
          listOf(
            MapLayerItem(
              id = "layer-sample-plots",
              label = "Permanent Forest Sample Plots",
              sourceDescription = "Dataset: sample_plots (Polygon, SP-01 to SP-05)",
              colorHex = 0xFF1B5E20,
              geometryTypeLabel = "Polygon",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "sample plot",
              pluralItemLabel = "sample plots",
            ),
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            MapLayerItem(
              id = "layer-commodity-plots",
              label = "Commodity Plots (EUDR)",
              sourceDescription = "Dataset: commodity_plots (Polygon)",
              colorHex = 0xFFD84315,
              geometryTypeLabel = "Polygon",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "commodity plot",
              pluralItemLabel = "commodity plots",
            )
          )
        "survey-household-past-individuals" ->
          listOf(
            MapLayerItem(
              id = "layer-past-individuals",
              label = "Preloaded Past Household Individuals",
              sourceDescription = "Dataset: past_individuals (Point, IND-101 to IND-106)",
              colorHex = 0xFF6A1B9A,
              geometryTypeLabel = "Point",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "past individual",
              pluralItemLabel = "past individuals",
            ),
          )
        else -> defaultMapLayers()
      }

    /**
     * Default map layer definitions (`LayerDef` inside `SurveyDef.map_config.layers`) backed by
     * geospatial entity datasets (`LayerDef.entity_dataset_id`).
     */
    fun defaultMapLayers(): List<MapLayerItem> =
      listOf(
        // Survey Dataset Layers (solid outlines)
        MapLayerItem(
          id = "layer-coffee-parcels",
          label = "Smallholder Coffee Parcels",
          sourceDescription = "Dataset: coffee_parcels (Polygon)",
          colorHex = 0xFF2E7D32,
          geometryTypeLabel = "Polygon",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
          singularItemLabel = "coffee parcel",
          pluralItemLabel = "coffee parcels",
        ),
        MapLayerItem(
          id = "layer-shade-transects",
          label = "Shade Tree Monitoring Plots",
          sourceDescription = "Dataset: shade_monitoring_plots (LineString)",
          colorHex = 0xFF1565C0,
          geometryTypeLabel = "LineString",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
          singularItemLabel = "monitoring plot",
          pluralItemLabel = "monitoring plots",
        ),
        MapLayerItem(
          id = "layer-water-points",
          label = "Cooperative Washing Stations",
          sourceDescription = "Dataset: washing_stations (Point)",
          colorHex = 0xFFEF6C00,
          geometryTypeLabel = "Point",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
          singularItemLabel = "washing station",
          pluralItemLabel = "washing stations",
        ),
      )

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
     * Sample forms configured in the active survey, including 5 entity-targeted forms and 1
     * standalone ("Log Only") form (`targetDatasetId = ""`, `requiresEntity == false`).
     */
    fun defaultForms(): List<FormPreviewItem> =
      listOf(
        FormPreviewItem(
          id = "form-eudr-baseline",
          title = "EUDR Parcel Baseline Registration",
          description =
            "Parcel perimeter georeferencing, deforestation-free attestation, and cultivar count (`save_to` updates `marker-symbol` ○ → ◐ → ✓).",
          version = "v2026.09.1",
          targetDatasetId = "coffee_parcels",
          targetDatasetName = "Smallholder Coffee Parcels",
          questionCount = 8,
          ctaLabel = "Register baseline parcel",
        ),
        FormPreviewItem(
          id = "form-household-interview",
          title = "Smallholder Household Socio-Economic Survey",
          description =
            "Grower household interview, farm income diversification, and cooperative membership (`save_to` updates `marker-symbol` ○ → ◐ → ✓).",
          version = "v2026.09.1",
          targetDatasetId = "coffee_parcels",
          targetDatasetName = "Smallholder Coffee Parcels",
          questionCount = 6,
          ctaLabel = "Interview household",
        ),
        FormPreviewItem(
          id = "form-shade-canopy-audit",
          title = "Seasonal Shade Tree & Canopy Audit",
          description =
            "Longitudinal multi-wave monitoring of native shade tree survival, canopy percentage, and soil moisture.",
          version = "v2026.09.2",
          targetDatasetId = "shade_monitoring_plots",
          targetDatasetName = "Shade Tree Monitoring Plots",
          questionCount = 11,
          ctaLabel = "Record canopy audit",
        ),
        FormPreviewItem(
          id = "form-deforestation-alert",
          title = "GLAD Canopy Disturbance Alert Verification",
          description =
            "Field ground-truthing of satellite canopy disturbance and deforestation alerts.",
          version = "v2026.09.2",
          targetDatasetId = "shade_monitoring_plots",
          targetDatasetName = "Shade Tree Monitoring Plots",
          questionCount = 5,
          ctaLabel = "Validate alert",
        ),
        FormPreviewItem(
          id = "form-water-quality",
          title = "Washing Station Effluent & Water Check",
          description =
            "Periodic water pH, turbidity, and eco-pulper recycling inspection at cooperative stations.",
          version = "v2026.08.4",
          targetDatasetId = "washing_stations",
          targetDatasetName = "Cooperative Washing Stations",
          questionCount = 6,
          ctaLabel = "Inspect water effluent",
        ),
        FormPreviewItem(
          id = "form-pest-disease-sighting",
          title = "Opportunistic Berry Borer & Rust Sighting",
          description =
            "Standalone field observation log for coffee berry borer (Hypothenemus hampei), leaf rust hotspots, or roadside erosion not tied to a registered parcel.",
          version = "v2026.09.1",
          targetDatasetId = "",
          targetDatasetName = "",
          questionCount = 5,
          ctaLabel = "Log field sighting",
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

    /**
     * Sample Geospatial Entities (`EntityRecord`s) across **Polygon**, **LineString**, and
     * **Point** geometries showcasing the 3-stage `simplestyle-spec` marker progression driven by
     * `save_to`:
     * - Stage 1 (`"○"` Empty Circle, `#E65100` Orange): `entity-nyr-112` (Pending baseline, 0
     *   submissions)
     * - Stage 2 (`"◐"` Half-Filled Circle, `#F9AB00` Amber): `entity-nyr-108` & `entity-station-01`
     *   (In progress, 1st stage recorded)
     * - Stage 3 (`"✓"` Checkmark, `#1E8E3E` Green / `#1565C0` Blue): `entity-nyr-104` &
     *   `entity-shade-201` (Completed)
     */
    fun defaultGeospatialEntities(): List<GeospatialEntityItem> =
      listOf(
        // 1. Stage 3 — Completed Polygon Entity ("✓" Checkmark marker)
        GeospatialEntityItem(
          id = "entity-nyr-104",
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
              "Primary Cultivar" to "SL28 & Ruiru 11",
              "Elevation" to "1,820 m",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-nyr-104-baseline",
                entityId = "entity-nyr-104",
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
          id = "entity-shade-201",
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
              "Target survival rate" to "85%",
              "Planting Cohort" to "2025 Long Rains",
              "Elevation" to "1,865 m",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-shade-201-wave3",
                entityId = "entity-shade-201",
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
                id = "sub-shade-201-wave2",
                entityId = "entity-shade-201",
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
                id = "sub-shade-201-wave1",
                entityId = "entity-shade-201",
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
          id = "entity-nyr-108",
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
              "Primary Cultivar" to "Batian & SL34",
              "Elevation" to "1,795 m",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-nyr-108-baseline",
                entityId = "entity-nyr-108",
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
          id = "entity-nyr-112",
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
              "Primary Cultivar" to "SL28 & Batian",
              "Elevation" to "1,845 m",
            ),
          submissions = emptyList(),
          syncStatus = SyncStatus.FAILED,
        ),
        // 5. Stage 2 — Half-Filled Circle Point Entity ("◐" Cooperative Washing Station)
        GeospatialEntityItem(
          id = "entity-station-01",
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
                id = "sub-wsh-01-sep",
                entityId = "entity-station-01",
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
                id = "sub-wsh-01-aug",
                entityId = "entity-station-01",
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

    /** Sample Mapbox vector & satellite raster tile packages for `Offline maps`. */
    fun defaultOfflineTilePackages(): List<OfflineTilePackageItem> =
      listOf(
        OfflineTilePackageItem(
          id = "tiles-nyeri-vector",
          regionName = "Nyeri & Mt. Kenya West Vector Basemap",
          tileTypeLabel = "Vector Tiles (Contours & Roads)",
          zoomRangeLabel = "Zoom 10–18",
          sizeLabel = "14.2 MB",
          isDownloaded = true,
        ),
        OfflineTilePackageItem(
          id = "tiles-nyeri-satellite",
          regionName = "Othaya & Chinga High-Res Satellite Imagery",
          tileTypeLabel = "Satellite Raster Tiles",
          zoomRangeLabel = "Zoom 12–19",
          sizeLabel = "68.5 MB",
          isDownloaded = true,
        ),
        OfflineTilePackageItem(
          id = "tiles-kirinyaga-east",
          regionName = "Kirinyaga Neighboring Cooperative Sector",
          tileTypeLabel = "Hybrid Vector + Raster Tiles",
          zoomRangeLabel = "Zoom 11–18",
          sizeLabel = "42.0 MB",
          isDownloaded = false,
        ),
      )

    /**
     * Sample local mutations (`DataMutation` items) spanning `Pending`, `In progress`, `Uploaded`,
     * and `Failed`, ordered in reverse chronological order with user-friendly operation
     * descriptions.
     */
    fun defaultMutations(): List<MutationLogItem> =
      listOf(
        // --- Pending, In progress, and Failed Mutations (reverse chronological order) ---
        MutationLogItem(
          id = "mut-outbox-04",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "Seasonal Shade Tree & Canopy Audit",
          targetLabel = "Transect SHD-201 • Chinga North Agroforestry",
          entityId = "entity-shade-201",
          submissionId = "sub-shade-201-wave3",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADING,
          stateDetail = "Uploading (74%)",
          operationTimestamp = "2026-09-19 09:40:18 UTC",
          startedTimestamp = "2026-09-19 09:40:22 UTC",
          completedTimestamp = null,
          payloadSummary = "5 responses",
        ),
        MutationLogItem(
          id = "mut-outbox-03",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_SUBMISSION,
          title = "Smallholder Household Socio-Economic Survey",
          targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
          entityId = "entity-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          actorName = "Maya Lin",
          state = MutationSyncState.RETRYING,
          stateDetail = "Retrying over weak signal",
          operationTimestamp = "2026-09-19 09:34:05 UTC",
          startedTimestamp = "2026-09-19 09:34:12 UTC",
          completedTimestamp = null,
          payloadSummary = "Updated household interview",
        ),
        MutationLogItem(
          id = "mut-outbox-02",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_ENTITY,
          title = "Marked parcel as In progress",
          targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
          entityId = "entity-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          actorName = "Samuel Kariuki",
          state = MutationSyncState.QUEUED,
          stateDetail = "Waiting for network connection",
          operationTimestamp = "2026-09-19 09:21:40 UTC",
          startedTimestamp = "2026-09-19 09:21:44 UTC",
          completedTimestamp = null,
          payloadSummary = "Coffee Parcel status updated",
        ),
        MutationLogItem(
          id = "mut-outbox-01",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.DELETE_SUBMISSION,
          title = "Duplicate Parcel Baseline Draft",
          targetLabel = "Plot NYR-112 • Kariuki Hillside Parcel",
          entityId = "entity-nyr-112",
          submissionId = null,
          actorName = "Maya Lin",
          state = MutationSyncState.FAILED,
          stateDetail = "Connection timed out — tap Retry",
          operationTimestamp = "2026-09-19 08:55:10 UTC",
          startedTimestamp = "2026-09-19 08:55:15 UTC",
          completedTimestamp = null,
          payloadSummary = "Deleted draft submission",
        ),
        // --- Uploaded Mutations (reverse chronological order) ---
        MutationLogItem(
          id = "mut-uploaded-05",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "Washing Station Effluent & Water Check",
          targetLabel = "Station WSH-01 • Gura River Wet Mill",
          entityId = "entity-station-01",
          submissionId = "sub-wsh-01-sep",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-18 17:30:04 UTC",
          startedTimestamp = "2026-09-18 17:30:11 UTC",
          completedTimestamp = "2026-09-18 17:30:15 UTC",
          payloadSummary = "3 responses",
        ),
        MutationLogItem(
          id = "mut-uploaded-04",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_ENTITY,
          title = "Marked parcel as Completed",
          targetLabel = "Plot NYR-104 • Kamau Family Parcel",
          entityId = "entity-nyr-104",
          submissionId = "sub-nyr-104-baseline",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-18 10:14:20 UTC",
          startedTimestamp = "2026-09-18 10:14:25 UTC",
          completedTimestamp = "2026-09-18 10:14:27 UTC",
          payloadSummary = "Coffee Parcel status updated",
        ),
        MutationLogItem(
          id = "mut-uploaded-03",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "EUDR Parcel Baseline Registration",
          targetLabel = "Plot NYR-104 • Kamau Family Parcel",
          entityId = "entity-nyr-104",
          submissionId = "sub-nyr-104-baseline",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-18 10:14:02 UTC",
          startedTimestamp = "2026-09-18 10:14:15 UTC",
          completedTimestamp = "2026-09-18 10:14:24 UTC",
          payloadSummary = "4 responses",
        ),
        MutationLogItem(
          id = "mut-uploaded-02",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_SUBMISSION,
          title = "Smallholder Household Socio-Economic Survey",
          targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
          entityId = "entity-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          actorName = "Samuel Kariuki",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-17 16:02:11 UTC",
          startedTimestamp = "2026-09-17 16:05:00 UTC",
          completedTimestamp = "2026-09-17 16:05:08 UTC",
          payloadSummary = "4 responses",
        ),
        MutationLogItem(
          id = "mut-uploaded-01",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "Seasonal Shade Tree & Canopy Audit",
          targetLabel = "Transect SHD-201 • Chinga North Agroforestry",
          entityId = "entity-shade-201",
          submissionId = "sub-shade-201-wave2",
          actorName = "Samuel Kariuki",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-06-14 14:20:09 UTC",
          startedTimestamp = "2026-06-14 14:22:30 UTC",
          completedTimestamp = "2026-06-14 14:22:36 UTC",
          payloadSummary = "3 responses",
        ),
      )
  }
}
