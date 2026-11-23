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
package org.openforis.collectone.v2.app.data.datasource.remote

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import openforis.collectone.v1.survey.DateWindow
import openforis.collectone.v1.survey.DoubleSeries
import openforis.collectone.v1.survey.GetPlotStatisticsRequest
import openforis.collectone.v1.survey.GetPlotStatisticsResponse
import openforis.collectone.v1.survey.GetPlotTimeSeriesRequest
import openforis.collectone.v1.survey.GetPlotTimeSeriesResponse
import openforis.collectone.v1.survey.GetSceneTileUrlRequest
import openforis.collectone.v1.survey.GetSceneTileUrlResponse
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource

/**
 * Deterministic stand-ins for `GeoDataService`'s Earth Engine analytics, so tests and demos run
 * without credentials: stable forest with a seasonal cycle, a clearing in August 2023, and partial
 * regrowth afterwards. The same plot always yields the same series.
 */
internal object SeedImageryAnalytics {
  const val ATTRIBUTION = "Demo data"

  /** First observation (2016-01-05) and spacing of the synthetic series. */
  private const val FIRST_OBSERVATION_MS = 1_451_952_000_000L
  private const val DAY_MS = 86_400_000L
  private const val YEAR_MS = 365.25 * DAY_MS
  private const val DISTURBANCE_MS = 1_691_884_800_000L // 2023-08-13

  fun statistics(request: GetPlotStatisticsRequest): GetPlotStatisticsResponse =
    GetPlotStatisticsResponse(
      elevation_min_meters = 412.0,
      elevation_mean_meters = 425.5,
      elevation_max_meters = 438.0,
      population = if (request.plot?.geometry?.point != null) null else 14.0,
      population_density_per_km2 = 290.0,
      population_year = 2026,
      attributions = listOf(ATTRIBUTION),
    )

  fun timeSeries(request: GetPlotTimeSeriesRequest): GetPlotTimeSeriesResponse {
    val (platform, spacingDays, bands) =
      when (request.source) {
        TimeSeriesSource.SENTINEL_2 ->
          Triple("Sentinel-2A", 10, listOf("NDVI", "EVI", "EVI2", "NBR", "NDMI", "NDWI"))
        TimeSeriesSource.SENTINEL_1 ->
          Triple("SENTINEL-1A", 12, listOf("VV", "VH", "VV/VH", "RFDI"))
        else ->
          Triple(
            "LANDSAT_8",
            16,
            listOf("NDVI", "NDFI", "EVI", "EVI2", "NBR", "NDMI", "NDWI") +
              listOf("BLUE", "GREEN", "RED", "NIR", "SWIR1", "SWIR2"),
          )
      }
    val random = Random(request.plot.hashCode() xor request.source.value)
    val times =
      generateSequence(FIRST_OBSERVATION_MS) { it + spacingDays * DAY_MS }
        .takeWhile { it < FIRST_OBSERVATION_MS + (10 * YEAR_MS).toLong() }
        .filter { random.nextDouble() > 0.35 } // Cloudy or missing acquisitions.
        .toList()
    val prefix =
      when (request.source) {
        TimeSeriesSource.SENTINEL_2 -> "COPERNICUS/S2_SR_HARMONIZED/DEMO_"
        TimeSeriesSource.SENTINEL_1 -> "COPERNICUS/S1_GRD/DEMO_"
        else -> "LANDSAT/LC08/C02/T1_TOA/DEMO_"
      }
    return GetPlotTimeSeriesResponse(
      time_start_ms = times,
      scene_ids = times.map { "$prefix$it" },
      platforms = times.map { platform },
      bands =
        bands.associateWith { band ->
          DoubleSeries(values = times.map { value(band, it, random.nextDouble() - 0.5) })
        },
      window = request.window ?: DateWindow(start_date = "2016-01-01", end_date = "2026-01-01"),
      attributions = listOf(ATTRIBUTION),
    )
  }

  fun sceneTileUrl(request: GetSceneTileUrlRequest): GetSceneTileUrlResponse =
    GetSceneTileUrlResponse(
      url_template =
        "https://mock.earthengine.googleapis.com/v1/projects/mock/maps/" +
          "scene-${request.scene_id.hashCode().toUInt()}-${request.stretch.value}/tiles/{z}/{x}/{y}",
      ttl = com.squareup.wire.durationOfSeconds(7200L, 0L),
      stretch =
        if (request.stretch == SceneStretch.SCENE_STRETCH_UNSPECIFIED) SceneStretch.TRUE_COLOR
        else request.stretch,
      attribution = ATTRIBUTION,
    )

  /** Vegetation signal: 1 for intact forest, dropping to 0 at the clearing, then regrowing. */
  private fun vigor(timeMs: Long): Double {
    if (timeMs < DISTURBANCE_MS) return 1.0
    val yearsSince = (timeMs - DISTURBANCE_MS) / YEAR_MS
    return (yearsSince * 0.35).coerceAtMost(0.7)
  }

  private fun value(band: String, timeMs: Long, noise: Double): Double {
    val season = sin(2 * PI * (timeMs - FIRST_OBSERVATION_MS) / YEAR_MS)
    val v = vigor(timeMs)
    return when (band) {
      "NDVI" -> 0.25 + 0.6 * v + 0.04 * season + 0.03 * noise
      "NDFI" -> -0.3 + 1.1 * v + 0.03 * season + 0.05 * noise
      "EVI" -> 0.15 + 0.4 * v + 0.03 * season + 0.03 * noise
      "EVI2" -> 0.15 + 0.38 * v + 0.03 * season + 0.03 * noise
      "NBR" -> 0.05 + 0.6 * v + 0.03 * season + 0.04 * noise
      "NDMI" -> 0.0 + 0.35 * v + 0.03 * season + 0.03 * noise
      "NDWI" -> -0.2 - 0.45 * v + 0.02 * season + 0.03 * noise
      "BLUE" -> 0.09 - 0.02 * v + 0.005 * noise
      "GREEN" -> 0.10 - 0.02 * v + 0.005 * noise
      "RED" -> 0.12 - 0.08 * v + 0.01 * noise
      "NIR" -> 0.22 + 0.10 * v + 0.01 * season + 0.01 * noise
      "SWIR1" -> 0.28 - 0.14 * v + 0.01 * noise
      "SWIR2" -> 0.20 - 0.13 * v + 0.01 * noise
      "VV" -> -9.0 + 2.5 * v + 0.6 * noise
      "VH" -> -18.0 + 4.5 * v + 0.6 * noise
      "VV/VH" -> (-9.0 + 2.5 * v) / (-18.0 + 4.5 * v)
      "RFDI" -> 0.75 - 0.25 * v + 0.03 * noise
      else -> Double.NaN
    }
  }
}
