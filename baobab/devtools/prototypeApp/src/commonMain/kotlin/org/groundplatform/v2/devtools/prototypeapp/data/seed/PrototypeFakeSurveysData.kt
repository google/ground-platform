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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem

/** Hardcoded sample surveys, forms, and XForms presets for the prototype app. */
internal object PrototypeFakeSurveysData {
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
    val escapedTitle = form.title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
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

  /**
   * Default sample surveys shared with the user, including the 5 swappable Workbench Example
   * Surveys (each with its own preloaded entities, sample submissions, geometries, map layers, and
   * XForms definition) plus a remote undownloaded survey for offline download testing.
   */
  fun defaultSampleSurveys(): List<SurveyPreviewItem> =
    listOf(
      SurveyPreviewItem(
        id = "survey-single-point-land-use",
        title = "Simple Point & Land Use Survey",
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
        title = "Sample Plot Forest Assessment Survey",
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
        title = "Commodity Plot Perimeter & Center Mapping (EUDR)",
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
        title = "Household Panel Survey (Past Individuals)",
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
        title = "All Form Field Types Showcase (Kenya Coffee)",
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

  /** Maps a [WorkbenchExampleForm] to its canonical Example Survey ID. */
  fun surveyIdForExampleForm(example: WorkbenchExampleForm): String =
    when (example) {
      WorkbenchExampleForm.SINGLE_POINT_LAND_USE -> "survey-single-point-land-use"
      WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT -> "survey-sample-plots-forest"
      WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER -> "survey-commodity-perimeter-center"
      WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS -> "survey-household-past-individuals"
      WorkbenchExampleForm.ALL_FIELD_TYPES -> "survey-kenya-coffee"
    }

  /** Maps an Example Survey ID to its corresponding [WorkbenchExampleForm]. */
  fun exampleFormForSurveyId(surveyId: String): WorkbenchExampleForm =
    when (surveyId) {
      "survey-single-point-land-use" -> WorkbenchExampleForm.SINGLE_POINT_LAND_USE
      "survey-sample-plots-forest" -> WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT
      "survey-commodity-perimeter-center" -> WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER
      "survey-household-past-individuals" -> WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS
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
}
