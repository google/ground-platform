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
package org.openforis.collectone.v2.web.ui.timeseries

import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.app.domain.model.editor.utcMillisToIsoDate
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/** A value loaded from the server. */
sealed interface Loadable<out T> {
  /** Nothing requested yet. */
  data object Idle : Loadable<Nothing>

  data object Loading : Loadable<Nothing>

  data class Loaded<T>(val value: T) : Loadable<T>

  /** The request failed for [reason]; [detail] is the server's explanation, if any. */
  data class Failed(val reason: TimeSeriesError, val detail: String? = null) : Loadable<Nothing>
}

/** Why loading plot statistics, a time series, or a scene failed. */
enum class TimeSeriesError {
  /** The survey isn't on the server yet (it hasn't been uploaded). */
  NOT_UPLOADED,

  /** The plot can't be analyzed (e.g. too large); see [Loadable.Failed.detail]. */
  UNSUPPORTED_PLOT,

  /** Too many requests; retry in a minute. */
  BUSY,

  /** Earth Engine took too long; a shorter window may help. */
  TIMEOUT,

  /** Earth Engine or the server is unavailable. */
  UNAVAILABLE,

  /** The user can't view the survey. */
  DENIED,
  OTHER,
}

/** The map feature the "Time series & imagery" panel describes. */
data class TimeSeriesPlot(
  val surveyId: String,
  val entityId: String,
  val label: String,
  val geometryType: GeometryType?,
  val areaHectares: Double,
  /** Vertices of the feature as drawn on the survey map: one for points, the ring of polygons. */
  val vertices: List<LatLng> = emptyList(),
) {
  /** Points and polygons have time series; lines and records without a location don't. */
  val isSupported: Boolean
    get() = geometryType == GeometryType.POINT || geometryType == GeometryType.POLYGON
}

/** Elevation and population of a plot (`GetPlotStatisticsResponse`). */
data class PlotStatistics(
  val elevationMinMeters: Double?,
  val elevationMeanMeters: Double?,
  val elevationMaxMeters: Double?,
  val population: Double?,
  val populationDensityPerKm2: Double?,
  val populationYear: Int,
  val attributions: List<String>,
)

/** Plot means per observation (`GetPlotTimeSeriesResponse`), by column. */
data class PlotTimeSeries(
  val source: TimeSeriesSource,
  val timesMs: List<Long>,
  val sceneIds: List<String>,
  val platforms: List<String>,
  val bands: Map<String, List<Double>>,
  val windowLabel: String,
  val attributions: List<String>,
) {
  val size: Int
    get() = timesMs.size

  /** Values of [band] in observation order; NaN where masked. */
  fun values(band: String): List<Double> = bands[band].orEmpty()

  /** Number of observations with a value for [band]. */
  fun validCount(band: String): Int = values(band).count { !it.isNaN() }
}

/** One observation of the charted band. */
data class TimeSeriesObservation(
  val index: Int,
  val timeMs: Long,
  val platform: String,
  val sceneId: String,
  val value: Double,
) {
  /** Acquisition date, `YYYY-MM-DD` (UTC). */
  val date: String
    get() = utcMillisToIsoDate(timeMs)

  /** Readable platform name, e.g. `Landsat 8`, `Sentinel-2A`. */
  val platformLabel: String
    get() = TimeSeriesUiState.platformLabel(platform)
}

/** The historical scene shown on the map for the selected observation. */
data class PinnedScene(
  val observation: TimeSeriesObservation,
  val stretch: SceneStretch,
  /** Stretches the scene supports, in display order. */
  val stretches: List<SceneStretch>,
  val tiles: Loadable<String> = Loadable.Loading,
  val attribution: String = "",
)

/** State of the dashboard's "Time series & imagery" panel. */
data class TimeSeriesUiState(
  /** False when the web console has no backend (time series need Earth Engine). */
  val isAvailable: Boolean = true,
  val plot: TimeSeriesPlot? = null,
  val source: TimeSeriesSource = TimeSeriesSource.LANDSAT,
  val band: String = "NDVI",
  val statistics: Loadable<PlotStatistics> = Loadable.Idle,
  val series: Loadable<PlotTimeSeries> = Loadable.Idle,
  /** Index of the selected observation in [series]. */
  val selectedIndex: Int? = null,
  val pinnedScene: PinnedScene? = null,
) {
  /** Bands of [source], in display order. */
  val bands: List<String>
    get() = bandsOf(source)

  val loadedSeries: PlotTimeSeries?
    get() = (series as? Loadable.Loaded)?.value

  /** The observation at [selectedIndex], if it has a value for [band]. */
  val selectedObservation: TimeSeriesObservation?
    get() {
      val series = loadedSeries ?: return null
      val index = selectedIndex?.takeIf { it in 0 until series.size } ?: return null
      return observation(series, index)
    }

  /** Observation [index] of [series] for the charted [band], or `null` if it has no value. */
  fun observation(series: PlotTimeSeries, index: Int): TimeSeriesObservation? {
    val value = series.values(band).getOrNull(index)?.takeIf { !it.isNaN() } ?: return null
    return TimeSeriesObservation(
      index = index,
      timeMs = series.timesMs[index],
      platform = series.platforms.getOrElse(index) { "" },
      sceneId = series.sceneIds.getOrElse(index) { "" },
      value = value,
    )
  }

  companion object {
    /** Sources offered in the panel, in display order. */
    val SOURCES: List<TimeSeriesSource> =
      listOf(TimeSeriesSource.LANDSAT, TimeSeriesSource.SENTINEL_2, TimeSeriesSource.SENTINEL_1)

    fun bandsOf(source: TimeSeriesSource): List<String> =
      when (source) {
        TimeSeriesSource.SENTINEL_2 -> listOf("NDVI", "EVI", "EVI2", "NBR", "NDMI", "NDWI")
        TimeSeriesSource.SENTINEL_1 -> listOf("VV", "VH", "VV/VH", "RFDI")
        else ->
          listOf(
            "NDVI",
            "NDFI",
            "EVI",
            "EVI2",
            "NBR",
            "NDMI",
            "NDWI",
            "BLUE",
            "GREEN",
            "RED",
            "NIR",
            "SWIR1",
            "SWIR2",
          )
      }

    /** Stretches available for scenes of [source]. */
    fun stretchesOf(source: TimeSeriesSource): List<SceneStretch> =
      if (source == TimeSeriesSource.SENTINEL_1) {
        listOf(SceneStretch.SAR_VV_VH_RATIO)
      } else {
        listOf(SceneStretch.SWIR_NIR_RED, SceneStretch.TRUE_COLOR, SceneStretch.NIR_SWIR_RED)
      }

    /** E.g. `LANDSAT_8` → `Landsat 8`, `SENTINEL-1A` → `Sentinel-1A`. */
    fun platformLabel(platform: String): String =
      when {
        platform.startsWith("LANDSAT_") -> "Landsat " + platform.removePrefix("LANDSAT_")
        platform.startsWith("SENTINEL-") -> "Sentinel-" + platform.removePrefix("SENTINEL-")
        else -> platform
      }
  }
}
