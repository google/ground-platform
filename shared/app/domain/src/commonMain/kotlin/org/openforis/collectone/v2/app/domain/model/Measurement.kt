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

import kotlin.math.floor
import kotlin.math.round

/**
 * A unit of the lengths and areas people read. The UI names it in the user's language; [symbol] is
 * its English symbol, only for text that doesn't follow the UI language (text stored in records).
 */
enum class MeasurementUnit(val symbol: String) {
  METER("m"),
  KILOMETER("km"),
  FOOT("ft"),
  MILE("mi"),
  HECTARE("ha"),
  ACRE("acres"),
  SQUARE_KILOMETER("km²"),
}

/**
 * A length or area to show: [value] in [unit], rounded to [decimals] decimal places (0 for a whole
 * number), or as measured or stored when [decimals] is `null`. The UI writes the number and names
 * the unit in the user's language, e.g. `120 m` or `2.97 acres`.
 */
data class Measurement(val value: Double, val unit: MeasurementUnit, val decimals: Int?)

/**
 * The length of a map scale bar of [meters] (a round length such as 200 m or 5 km): meters below 1
 * km and kilometers from there, rounded to tenths, and whole when they are.
 */
fun scaleBarLength(meters: Double): Measurement {
  val (value, unit) =
    if (meters >= 1000) meters / 1000 to MeasurementUnit.KILOMETER
    else meters to MeasurementUnit.METER
  val tenths = round(value * 10) / 10
  return Measurement(tenths, unit, decimals = if (tenths == floor(tenths)) 0 else 1)
}

/** Feet in a meter. */
internal const val FEET_PER_METER = 3.28084

/** Acres in a hectare. */
internal const val ACRES_PER_HECTARE = 2.47105
