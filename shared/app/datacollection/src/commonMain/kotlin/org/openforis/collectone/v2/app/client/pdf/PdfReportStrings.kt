/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.client.pdf

import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.domain.model.CoordinateDegrees
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_coordinates
import org.openforis.collectone.v2.app.resources.app_coordinates_latitude_north
import org.openforis.collectone.v2.app.resources.app_coordinates_latitude_south
import org.openforis.collectone.v2.app.resources.app_coordinates_longitude_east
import org.openforis.collectone.v2.app.resources.app_coordinates_longitude_west
import org.openforis.collectone.v2.app.resources.app_measurement
import org.openforis.collectone.v2.app.resources.domain_entity_geometry_kind_none
import org.openforis.collectone.v2.app.resources.domain_geometry_summary_line
import org.openforis.collectone.v2.app.resources.domain_geometry_summary_point
import org.openforis.collectone.v2.app.resources.domain_geometry_summary_polygon
import org.openforis.collectone.v2.app.resources.domain_pdf_bounds
import org.openforis.collectone.v2.app.resources.domain_pdf_collected
import org.openforis.collectone.v2.app.resources.domain_pdf_collected_by
import org.openforis.collectone.v2.app.resources.domain_pdf_coordinates
import org.openforis.collectone.v2.app.resources.domain_pdf_data_collector
import org.openforis.collectone.v2.app.resources.domain_pdf_details
import org.openforis.collectone.v2.app.resources.domain_pdf_entity_footer
import org.openforis.collectone.v2.app.resources.domain_pdf_entity_target
import org.openforis.collectone.v2.app.resources.domain_pdf_form_version
import org.openforis.collectone.v2.app.resources.domain_pdf_geo_id
import org.openforis.collectone.v2.app.resources.domain_pdf_geometry
import org.openforis.collectone.v2.app.resources.domain_pdf_layer
import org.openforis.collectone.v2.app.resources.domain_pdf_location
import org.openforis.collectone.v2.app.resources.domain_pdf_location_point
import org.openforis.collectone.v2.app.resources.domain_pdf_no_attached_feature
import org.openforis.collectone.v2.app.resources.domain_pdf_no_attached_feature_at
import org.openforis.collectone.v2.app.resources.domain_pdf_no_responses
import org.openforis.collectone.v2.app.resources.domain_pdf_no_submissions
import org.openforis.collectone.v2.app.resources.domain_pdf_page_of
import org.openforis.collectone.v2.app.resources.domain_pdf_properties
import org.openforis.collectone.v2.app.resources.domain_pdf_responses
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_analysis
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_before_after
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_commodities
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_cutoff_2020
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_high
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_indicators
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_intro
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_low
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_more_info
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_treecover
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_unknown
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_yearly_chart
import org.openforis.collectone.v2.app.resources.domain_pdf_risk_yearly_no_activity
import org.openforis.collectone.v2.app.resources.domain_pdf_running_header
import org.openforis.collectone.v2.app.resources.domain_pdf_submission_footer
import org.openforis.collectone.v2.app.resources.domain_pdf_submission_subtitle
import org.openforis.collectone.v2.app.resources.domain_pdf_submissions
import org.openforis.collectone.v2.app.resources.domain_pdf_vertex
import org.openforis.collectone.v2.app.ui.text.amountText
import org.openforis.collectone.v2.app.ui.text.nameRes
import org.openforis.collectone.v2.map.LatLng

/**
 * User-visible text of generated PDF reports, as templates with positional `%1$s` placeholders,
 * from string resources ([load]). Load it when a report is generated, so the report is written in
 * the language selected at that moment.
 */
data class PdfReportStrings(
  val runningHeader: String,
  val pageOf: String,
  val locationPoint: String,
  val bounds: String,
  val entityFooter: String,
  val submissionFooter: String,
  val submissionSubtitle: String,
  val details: String,
  val layer: String,
  val geometry: String,
  val geoId: String,
  val coordinates: String,
  val location: String,
  val vertex: String,
  val properties: String,
  val submissions: String,
  val noSubmissions: String,
  val collectedBy: String,
  val entityTarget: String,
  val noAttachedFeatureAt: String,
  val noAttachedFeature: String,
  val dataCollector: String,
  val collected: String,
  val formVersion: String,
  val responses: String,
  val noResponses: String,
  val geometryPolygon: String,
  val geometryLine: String,
  val geometryPoint: String,
  val geometryNone: String,
  val coordinatePair: String,
  val latitudeNorth: String,
  val latitudeSouth: String,
  val longitudeEast: String,
  val longitudeWest: String,
  val measurement: String,
  val unitNames: Map<MeasurementUnit, String>,
  val riskAnalysis: String = "Risk analysis (Open Foris Whisp)",
  val riskIntro: String = "Deforestation and land-use risk screening powered by Open Foris Whisp.",
  val riskHigh: String = "High risk",
  val riskMoreInfo: String = "More info needed",
  val riskLow: String = "Low risk",
  val riskUnknown: String = "Unknown",
  val riskYearlyChart: String = "Annual disturbance & loss",
  val riskYearlyNoActivity: String =
    "No annual disturbance, forest loss, or fire detected across monitored years.",
  val riskCutoff2020: String = "2020 cutoff",
  val riskBeforeAfter: String = "Before vs. after 2020",
  val riskIndicators: String = "Key indicators",
  val riskTreecover: String = "Tree cover & forest (2020)",
  val riskCommodities: String = "Commodities & land use",
) {
  /**
   * Localized coordinates of [position], e.g. `0.4160°S, 36.9470°E`, as the UI's `coordinatesText`
   * writes them.
   */
  fun coordinatesText(position: LatLng): String {
    val lat = CoordinateDegrees.of(position.latitude)
    val lng = CoordinateDegrees.of(position.longitude)
    return formatTemplate(
      coordinatePair,
      formatTemplate(if (lat.isNegative) latitudeSouth else latitudeNorth, lat.magnitude),
      formatTemplate(if (lng.isNegative) longitudeWest else longitudeEast, lng.magnitude),
    )
  }

  /** [measurement] in the report's language, as the UI's `toUiText` writes it, e.g. `120 m`. */
  fun measurementText(measurement: Measurement): String =
    formatTemplate(
      this.measurement,
      measurement.amountText(),
      unitNames.getValue(measurement.unit),
    )

  companion object {
    /**
     * Loads the report text in the language selected now from Compose string resources.
     *
     * Note: Compose Multiplatform's [getString] reads `android.content.res.Resources.getSystem()`
     * on Android, which is unmocked in host-side Android unit tests (`testDebugUnitTest`). Unit
     * tests either pass an explicit [PdfReportStrings] instance or run in `jvmTest`.
     */
    suspend fun load(): PdfReportStrings =
      PdfReportStrings(
        runningHeader = getString(Res.string.domain_pdf_running_header),
        pageOf = getString(Res.string.domain_pdf_page_of),
        locationPoint = getString(Res.string.domain_pdf_location_point),
        bounds = getString(Res.string.domain_pdf_bounds),
        entityFooter = getString(Res.string.domain_pdf_entity_footer),
        submissionFooter = getString(Res.string.domain_pdf_submission_footer),
        submissionSubtitle = getString(Res.string.domain_pdf_submission_subtitle),
        details = getString(Res.string.domain_pdf_details),
        layer = getString(Res.string.domain_pdf_layer),
        geometry = getString(Res.string.domain_pdf_geometry),
        geoId = getString(Res.string.domain_pdf_geo_id),
        coordinates = getString(Res.string.domain_pdf_coordinates),
        location = getString(Res.string.domain_pdf_location),
        vertex = getString(Res.string.domain_pdf_vertex),
        properties = getString(Res.string.domain_pdf_properties),
        submissions = getString(Res.string.domain_pdf_submissions),
        noSubmissions = getString(Res.string.domain_pdf_no_submissions),
        collectedBy = getString(Res.string.domain_pdf_collected_by),
        entityTarget = getString(Res.string.domain_pdf_entity_target),
        noAttachedFeatureAt = getString(Res.string.domain_pdf_no_attached_feature_at),
        noAttachedFeature = getString(Res.string.domain_pdf_no_attached_feature),
        dataCollector = getString(Res.string.domain_pdf_data_collector),
        collected = getString(Res.string.domain_pdf_collected),
        formVersion = getString(Res.string.domain_pdf_form_version),
        responses = getString(Res.string.domain_pdf_responses),
        noResponses = getString(Res.string.domain_pdf_no_responses),
        geometryPolygon = getString(Res.string.domain_geometry_summary_polygon),
        geometryLine = getString(Res.string.domain_geometry_summary_line),
        geometryPoint = getString(Res.string.domain_geometry_summary_point),
        geometryNone = getString(Res.string.domain_entity_geometry_kind_none),
        coordinatePair = getString(Res.string.app_coordinates),
        latitudeNorth = getString(Res.string.app_coordinates_latitude_north),
        latitudeSouth = getString(Res.string.app_coordinates_latitude_south),
        longitudeEast = getString(Res.string.app_coordinates_longitude_east),
        longitudeWest = getString(Res.string.app_coordinates_longitude_west),
        measurement = getString(Res.string.app_measurement),
        unitNames = MeasurementUnit.entries.associateWith { getString(it.nameRes) },
        riskAnalysis = getString(Res.string.domain_pdf_risk_analysis),
        riskIntro = getString(Res.string.domain_pdf_risk_intro),
        riskHigh = getString(Res.string.domain_pdf_risk_high),
        riskMoreInfo = getString(Res.string.domain_pdf_risk_more_info),
        riskLow = getString(Res.string.domain_pdf_risk_low),
        riskUnknown = getString(Res.string.domain_pdf_risk_unknown),
        riskYearlyChart = getString(Res.string.domain_pdf_risk_yearly_chart),
        riskYearlyNoActivity = getString(Res.string.domain_pdf_risk_yearly_no_activity),
        riskCutoff2020 = getString(Res.string.domain_pdf_risk_cutoff_2020),
        riskBeforeAfter = getString(Res.string.domain_pdf_risk_before_after),
        riskIndicators = getString(Res.string.domain_pdf_risk_indicators),
        riskTreecover = getString(Res.string.domain_pdf_risk_treecover),
        riskCommodities = getString(Res.string.domain_pdf_risk_commodities),
      )
  }
}

private val POSITIONAL_PLACEHOLDER = Regex("%(\\d+)\\$[sd]")

/** [template] with its positional `%1$s` / `%1$d` placeholders replaced by [args]. */
internal fun formatTemplate(template: String, vararg args: Any): String =
  POSITIONAL_PLACEHOLDER.replace(template) { match ->
    args.getOrNull(match.groupValues[1].toInt() - 1)?.toString() ?: match.value
  }
