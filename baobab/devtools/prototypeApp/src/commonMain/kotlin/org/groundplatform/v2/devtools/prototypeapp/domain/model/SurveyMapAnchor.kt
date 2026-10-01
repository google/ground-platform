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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.math.round
import org.groundplatform.v2.map.LatLng

/**
 * Where a prototype survey sits on the real map. Fake map features are stored in normalized survey
 * coordinates (`nx`, `ny` in `[0, 1]`, `y` down) and placed on the map relative to this anchor.
 *
 * This table is the single source of survey positions.
 */
data class SurveyMapAnchor(val center: LatLng, val zoom: Double, val label: String) {
  /** Geographic position of normalized survey coordinates. */
  fun toLatLng(nx: Double, ny: Double): LatLng =
    LatLng(
      latitude = center.latitude - (ny - 0.5) * SPAN_LATITUDE,
      longitude = center.longitude + (nx - 0.5) * SPAN_LONGITUDE,
    )

  /**
   * Normalized survey coordinates `(nx, ny)` of a geographic position; may fall outside `[0, 1]`.
   */
  fun toNormalized(point: LatLng): Pair<Double, Double> =
    (0.5 + (point.longitude - center.longitude) / SPAN_LONGITUDE) to
      (0.5 + (center.latitude - point.latitude) / SPAN_LATITUDE)

  companion object {
    /** Degrees of longitude covered by the normalized `[0, 1]` survey width. */
    const val SPAN_LONGITUDE = 0.014

    /** Degrees of latitude covered by the normalized `[0, 1]` survey height. */
    const val SPAN_LATITUDE = 0.018

    const val DEFAULT_SURVEY_ID = "survey-kenya-coffee"

    private val anchors: Map<String, SurveyMapAnchor> =
      mapOf(
        "survey-single-point-land-use" to
          anchor(-0.4188, 36.9498, 15.3, "Nyeri Land Use 0.42°S, 36.95°E"),
        "survey-sample-plots-forest" to
          anchor(-0.4192, 36.9506, 15.4, "Aberdare Sample Plots 0.42°S, 36.95°E"),
        "survey-commodity-perimeter-center" to
          anchor(-0.4195, 36.9510, 15.3, "Mt. Kenya EUDR Plots 0.42°S, 36.95°E"),
        "survey-household-past-individuals" to
          anchor(-0.4190, 36.9502, 15.3, "Nyeri Household Panel 0.42°S, 36.95°E"),
        DEFAULT_SURVEY_ID to anchor(-0.4198, 36.9512, 15.3, "Nyeri 0.42°S, 36.95°E"),
        "survey-amazon-canopy" to anchor(-3.4653, -62.2159, 15.0, "Pará 3.46°S, 62.21°W"),
        "survey-serengeti-corridor" to anchor(-2.3333, 34.8328, 14.8, "Serengeti 2.33°S, 34.83°E"),
        "survey-mekong-mangroves" to anchor(9.8249, 106.3422, 15.0, "Cần Thơ 9.82°N, 106.34°E"),
        "survey-andean-watershed" to anchor(-13.532, -71.9675, 15.1, "Cusco 13.53°S, 71.97°W"),
        "survey-borneo-peatland" to anchor(-2.2136, 113.9213, 14.9, "Kalteng 2.21°S, 113.92°E"),
      )

    /** Anchor for [surveyId], falling back to the default survey's. */
    fun forSurvey(surveyId: String): SurveyMapAnchor =
      anchors[surveyId] ?: anchors.getValue(DEFAULT_SURVEY_ID)

    private fun anchor(lat: Double, lng: Double, zoom: Double, label: String) =
      SurveyMapAnchor(LatLng(lat, lng), zoom, label)
  }
}

/** Rounds to [decimals] places, e.g. for stable normalized coordinates. */
internal fun Double.roundTo(decimals: Int): Double {
  var factor = 1.0
  repeat(decimals) { factor *= 10 }
  return round(this * factor) / factor
}
