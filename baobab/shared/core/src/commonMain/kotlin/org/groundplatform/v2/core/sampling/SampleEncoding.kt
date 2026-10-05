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
package org.groundplatform.v2.core.sampling

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Encoding of a plot's reserved `samples` and `sample_ids` properties.
 *
 * `samples` uses the ODK geotrace string format: points separated by `;` with no surrounding
 * spaces, each point exactly four space-separated numbers `lat lng altitude accuracy` (altitude and
 * accuracy are 0). Keeping exactly four tokens per point lets XForms read point *i* (1-based) with
 * standard functions only: `selected-at(translate(samples, ';', ' '), 4 * (i - 1))` is its latitude
 * and the next token its longitude.
 *
 * Numbers are written with at most 7 decimals and no exponent, using integer arithmetic so the
 * output is byte-identical on every platform (`Double.toString` differs between JVM and JS).
 */
object SampleEncoding {
  /** Encodes [points] as a geotrace string, e.g. `"-1.0001 37.0002 0 0;-1.0003 37.0004 0 0"`. */
  fun encodeSamples(points: List<GeoCoord>): String =
    points.joinToString(";") { "${formatDegrees(it.lat)} ${formatDegrees(it.lng)} 0 0" }

  /** Space-separated sample IDs; defaults to `"1 2 … n"`. IDs must not contain whitespace. */
  fun encodeSampleIds(ids: List<String>): String {
    require(ids.none { id -> id.isEmpty() || id.any { it.isWhitespace() } }) {
      "Sample IDs must be non-empty and must not contain whitespace"
    }
    return ids.joinToString(" ")
  }

  /** Default sample IDs for [count] samples: `"1"`, `"2"`, …. */
  fun defaultSampleIds(count: Int): List<String> = List(count) { (it + 1).toString() }

  /**
   * Decodes a geotrace string into points, ignoring altitude and accuracy. Accepts `;` separators
   * with optional surrounding whitespace and points with 2–4 numbers. Returns null if malformed.
   */
  fun decodeSamples(value: String): List<GeoCoord>? {
    if (value.isBlank()) return emptyList()
    return value.split(';').map { point ->
      val parts = point.trim().split(WHITESPACE)
      if (parts.size !in 2..4) return null
      val lat = parts[0].toDoubleOrNull() ?: return null
      val lng = parts[1].toDoubleOrNull() ?: return null
      if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
      GeoCoord(lat, lng)
    }
  }

  /** Decodes space-separated sample IDs. */
  fun decodeSampleIds(value: String): List<String> =
    value.trim().split(WHITESPACE).filter { it.isNotEmpty() }

  /** Formats degrees with up to 7 decimals, trailing zeros trimmed, never in exponent notation. */
  fun formatDegrees(deg: Double): String {
    val scaled = (deg * 1e7).roundToLong()
    val magnitude = abs(scaled)
    val whole = magnitude / 10_000_000
    val fraction = (magnitude % 10_000_000).toString().padStart(7, '0').trimEnd('0')
    val sign = if (scaled < 0) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
  }

  private val WHITESPACE = Regex("\\s+")
}
