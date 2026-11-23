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
package org.openforis.collectone.v2.web.client.timeseries

import openforis.collectone.v1.survey.GetPlotStatisticsRequest
import openforis.collectone.v1.survey.GetPlotTimeSeriesRequest
import openforis.collectone.v1.survey.GetSceneTileUrlRequest
import openforis.collectone.v1.survey.PlotRef
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.ui.timeseries.Loadable
import org.openforis.collectone.v2.web.ui.timeseries.PlotStatistics
import org.openforis.collectone.v2.web.ui.timeseries.PlotTimeSeries
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesError
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesPlot
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesViewModel

/**
 * The Earth Engine analyses of a map feature that the "Time series & imagery" panel shows, read
 * from the server's `GeoDataService` through [client] and mapped to the panel's models.
 *
 * Statistics and time series are cached by [plotKey] (and source) for this instance's lifetime,
 * which is the web session (the Koin module creates one per [TimeSeriesViewModel]): they're
 * expensive Earth Engine reductions, and a plot's numbers don't change unless its geometry does,
 * which changes the key. Scene tile URLs aren't cached; they're signed and expire.
 *
 * Calls are made from the caller's coroutine and throw on failure; [failure] turns the exception
 * into the reason the panel shows. Not thread-safe: the ViewModel calls it from its main-thread
 * scope only.
 */
class PlotImageryRepository(private val client: CollectOneGrpcClient) {
  private val statisticsCache = mutableMapOf<String, PlotStatistics>()
  private val seriesCache = mutableMapOf<String, PlotTimeSeries>()

  /** [plot]'s statistics if they were loaded before in this session. */
  fun cachedStatistics(plot: TimeSeriesPlot): PlotStatistics? = statisticsCache[plotKey(plot)]

  /** [plot]'s time series of [source] if it was loaded before in this session. */
  fun cachedTimeSeries(plot: TimeSeriesPlot, source: TimeSeriesSource): PlotTimeSeries? =
    seriesCache[seriesKey(plot, source)]

  /** Elevation and population of [plot], from the cache or the server. */
  suspend fun statistics(plot: TimeSeriesPlot): PlotStatistics {
    cachedStatistics(plot)?.let {
      return it
    }
    val response =
      client.getPlotStatistics(
        GetPlotStatisticsRequest(survey_id = plot.surveyId, plot = plotRef(plot))
      )
    return PlotStatistics(
        elevationMinMeters = response.elevation_min_meters,
        elevationMeanMeters = response.elevation_mean_meters,
        elevationMaxMeters = response.elevation_max_meters,
        population = response.population,
        populationDensityPerKm2 = response.population_density_per_km2,
        populationYear = response.population_year,
        attributions = response.attributions,
      )
      .also { statisticsCache[plotKey(plot)] = it }
  }

  /** [plot]'s per-observation means of every band of [source], from the cache or the server. */
  suspend fun timeSeries(plot: TimeSeriesPlot, source: TimeSeriesSource): PlotTimeSeries {
    cachedTimeSeries(plot, source)?.let {
      return it
    }
    val response =
      client.getPlotTimeSeries(
        GetPlotTimeSeriesRequest(survey_id = plot.surveyId, plot = plotRef(plot), source = source)
      )
    return PlotTimeSeries(
        source = source,
        timesMs = response.time_start_ms,
        sceneIds = response.scene_ids,
        platforms = response.platforms,
        bands = response.bands.mapValues { (_, series) -> series.values },
        windowLabel = response.window?.let { "${it.start_date} – ${it.end_date}" }.orEmpty(),
        attributions = response.attributions,
      )
      .also { seriesCache[seriesKey(plot, source)] = it }
  }

  /**
   * Tiles of scene [sceneId] drawn with [stretch]. The server may answer with another stretch when
   * the scene doesn't support the one asked for; [SceneTiles.stretch] is the one used.
   */
  suspend fun sceneTiles(surveyId: String, sceneId: String, stretch: SceneStretch): SceneTiles {
    val response =
      client.getSceneTileUrl(
        GetSceneTileUrlRequest(survey_id = surveyId, scene_id = sceneId, stretch = stretch)
      )
    return SceneTiles(
      urlTemplate = response.url_template,
      stretch = response.stretch,
      attribution = response.attribution,
    )
  }

  private fun seriesKey(plot: TimeSeriesPlot, source: TimeSeriesSource) = "${plotKey(plot)}|$source"

  companion object {
    // gRPC status codes GrpcWebTransport has no constant for.
    private const val DEADLINE_EXCEEDED = 4
    private const val RESOURCE_EXHAUSTED = 8

    /**
     * Identifies a plot's data: its feature, and its geometry, which changes when it's edited (so
     * an edited plot is analyzed again rather than served from the cache).
     */
    fun plotKey(plot: TimeSeriesPlot): String =
      "${plot.surveyId}|${plot.entityId}|${plot.geometryType}|" +
        plot.vertices.joinToString(";") { "${it.latitude},${it.longitude}" }

    /**
     * The request's reference to [plot]: its feature, and its geometry (the first part's outline),
     * which the server analyzes when the stored feature has no point or polygon. A polygon needs at
     * least three vertices; anything else is sent without geometry.
     */
    internal fun plotRef(plot: TimeSeriesPlot): PlotRef {
      val positions = plot.vertices.map { GeometryProtos.position(it.latitude, it.longitude) }
      val geometry =
        when {
          plot.geometryType == GeometryType.POINT && positions.isNotEmpty() ->
            GeometryProtos.point(positions.first().latitude, positions.first().longitude)
          plot.geometryType == GeometryType.POLYGON && positions.size >= 3 ->
            GeometryProtos.polygon(listOf(positions))
          else -> null
        }
      return PlotRef(entity_id = plot.entityId, geometry = geometry)
    }

    /**
     * Why a call failed, from its gRPC status: the server answers `NOT_FOUND` for surveys it
     * doesn't have yet, `INVALID_ARGUMENT` for plots Earth Engine can't reduce (with the reason as
     * [Loadable.Failed.detail]), and `RESOURCE_EXHAUSTED` when its Earth Engine quota is spent.
     * Anything that isn't a [GrpcWebException] is [TimeSeriesError.OTHER].
     */
    internal fun failure(e: Exception): Loadable.Failed {
      val grpc = e as? GrpcWebException
      val reason =
        when (grpc?.status) {
          GrpcWebTransport.NOT_FOUND -> TimeSeriesError.NOT_UPLOADED
          GrpcWebTransport.INVALID_ARGUMENT -> TimeSeriesError.UNSUPPORTED_PLOT
          RESOURCE_EXHAUSTED -> TimeSeriesError.BUSY
          DEADLINE_EXCEEDED -> TimeSeriesError.TIMEOUT
          GrpcWebTransport.UNAVAILABLE,
          GrpcWebTransport.FAILED_PRECONDITION -> TimeSeriesError.UNAVAILABLE
          GrpcWebTransport.PERMISSION_DENIED,
          GrpcWebTransport.UNAUTHENTICATED -> TimeSeriesError.DENIED
          else -> TimeSeriesError.OTHER
        }
      return Loadable.Failed(reason, grpc?.description)
    }
  }
}

/** A scene's XYZ tiles as the server signed them, and the stretch they're drawn with. */
data class SceneTiles(val urlTemplate: String, val stretch: SceneStretch, val attribution: String)
