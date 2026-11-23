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
package org.openforis.collectone.v2.app.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong
import org.openforis.collectone.v2.map.LatLng

/**
 * A latitude or longitude as people read it: [magnitude] is the absolute value in decimal degrees,
 * rounded to [DECIMALS] places and keeping trailing zeros (`"0.4160"`), and [isNegative] tells
 * whether it lies south of the equator or west of the prime meridian once rounded.
 *
 * The UI turns it into localized text with the hemisphere letter (`0.4160°S`); search matches
 * against [magnitude], which is the same in every language.
 */
data class CoordinateDegrees(val magnitude: String, val isNegative: Boolean) {
  companion object {
    /** Decimal places shown: 4 places is about 11 m at the equator. */
    const val DECIMALS = 4

    private const val SCALE = 10_000L

    /** [degrees] (signed decimal degrees) rounded half away from zero for display. */
    fun of(degrees: Double): CoordinateDegrees {
      val scaled = (abs(degrees) * SCALE).roundToLong()
      val whole = scaled / SCALE
      val fraction = (scaled % SCALE).toString().padStart(DECIMALS, '0')
      return CoordinateDegrees(
        magnitude = "$whole.$fraction",
        isNegative = degrees < 0 && scaled > 0,
      )
    }
  }
}

/**
 * `0.4160°S, 36.9470°E`: [position] with untranslated hemisphere letters, the same in every
 * language. Use it only where a locale-neutral form is required: matching search queries and
 * comparing displayed positions. Text people read (place and survey screens, the coordinates search
 * result, geometry summaries, PDF reports) uses the localized coordinates text of the UI
 * (`coordinatesText`, or `PdfReportStrings.coordinatesText`).
 */
fun plainCoordinatesLabel(position: LatLng): String {
  val lat = CoordinateDegrees.of(position.latitude)
  val lng = CoordinateDegrees.of(position.longitude)
  return "${lat.magnitude}°${if (lat.isNegative) 'S' else 'N'}, " +
    "${lng.magnitude}°${if (lng.isNegative) 'W' else 'E'}"
}
