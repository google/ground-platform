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
package org.openforis.collectone.v2.server.geo

import io.grpc.Status
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.survey.DateWindow
import openforis.collectone.v1.survey.DoubleSeries
import openforis.collectone.v1.survey.GetPlotStatisticsResponse
import openforis.collectone.v1.survey.GetPlotTimeSeriesResponse
import openforis.collectone.v1.survey.GetSceneTileUrlResponse
import openforis.collectone.v1.survey.PlotRef
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.core.geo.GeometryProtos

/**
 * Plot statistics, time series, and historical scenes for the web console's "Time Series & Imagery"
 * panel (Collect Earth Online's Geo-Dash), computed with Earth Engine.
 *
 * Results are cached per server instance and concurrent identical requests share one computation.
 * Requests that miss the cache are rate limited per caller, and at most [maxConcurrentComputations]
 * Earth Engine computations run at once, keeping all instances under the project's concurrent
 * request quota.
 */
class ImageryAnalytics(
  private val valueClient: EarthEngineValueClient,
  private val tileClient: EarthEngineTileClient,
  private val clock: () -> Instant = { Instant.now() },
  maxConcurrentComputations: Int = 8,
  requestsPerMinute: Int = 60,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val computations = Semaphore(maxConcurrentComputations)
  private val rateLimiter = RateLimiter(requestsPerMinute, clock)
  private val templates: Map<String, EarthEngineTemplate> by lazy {
    TEMPLATE_NAMES.associateWith { EarthEngineTemplate.load(it) }
  }
  private val statisticsCache =
    CoalescingTtlCache<String, GetPlotStatisticsResponse>(
      ttl = Duration.ofHours(24),
      maxEntries = 5_000,
      clock = clock,
      scope = scope,
    )
  private val seriesCache =
    CoalescingTtlCache<String, GetPlotTimeSeriesResponse>(
      ttl = Duration.ofHours(6),
      maxEntries = 500,
      clock = clock,
      scope = scope,
    )

  /** Elevation and population of [plot], for the caller identified by [callerKey]. */
  suspend fun statistics(callerKey: String, plot: PlotGeometry): GetPlotStatisticsResponse {
    val template = templates.getValue(PLOT_STATISTICS)
    val populationYear = min(today().year, LAST_POPULATION_YEAR)
    val key = "v${template.version}|$populationYear|${plot.cacheKey}"
    return statisticsCache.getOrCompute(key, beforeCompute = { rateLimiter.acquire(callerKey) }) {
      val expression =
        template.invoke(
          mapOf(
            "geometry" to plot.node(),
            "popYear" to EarthEngineNodes.constant(populationYear),
          )
        )
      parseStatistics(compute(expression, "c1-ts-stats"), plot, populationYear)
    }
  }

  /** Per-observation band means over [plot] from [source], within [window] (default 10 years). */
  suspend fun timeSeries(
    callerKey: String,
    plot: PlotGeometry,
    source: TimeSeriesSource,
    window: DateWindow?,
  ): GetPlotTimeSeriesResponse {
    val templateName =
      SERIES_TEMPLATES[source]
        ?: throw Status.INVALID_ARGUMENT.withDescription("source must be set").asRuntimeException()
    val template = templates.getValue(templateName)
    val resolvedWindow = resolveWindow(window)
    val key =
      "v${template.version}|$source|${resolvedWindow.start_date}|${resolvedWindow.end_date}|" +
        plot.cacheKey
    return seriesCache.getOrCompute(key, beforeCompute = { rateLimiter.acquire(callerKey) }) {
      val expression =
        template.invoke(
          mapOf(
            "geometry" to plot.node(),
            "start" to EarthEngineNodes.constant(resolvedWindow.start_date),
            "end" to EarthEngineNodes.constant(resolvedWindow.end_date),
          )
        )
      parseSeries(compute(expression, "c1-ts-series"), source, resolvedWindow)
    }
  }

  /** Tiles of scene [sceneId] (from a time series) visualized with [stretch]. */
  suspend fun sceneTileUrl(
    callerKey: String,
    sceneId: String,
    stretch: SceneStretch,
  ): GetSceneTileUrlResponse {
    val kind = SceneKind.require(sceneId)
    val appliedStretch = if (kind.supports(stretch)) stretch else kind.defaultStretch
    rateLimiter.acquire(callerKey)
    val resolved =
      scope
        .async {
          computations.withPermit {
            tileClient.resolve(kind.visualization(sceneId, appliedStretch))
          }
        }
        .await()
    return GetSceneTileUrlResponse(
      url_template = resolved.urlTemplate,
      ttl = com.squareup.wire.durationOfSeconds(resolved.ttlSeconds, 0L),
      stretch = appliedStretch,
      attribution = kind.attribution,
    )
  }

  private suspend fun compute(
    expression: google.earthengine.v1main.Expression,
    workloadTag: String,
  ): Any? = computations.withPermit { valueClient.compute(expression, workloadTag) }

  private fun today(): LocalDate = clock().atZone(ZoneOffset.UTC).toLocalDate()

  /**
   * [window] validated and clamped to the archive: missing dates default to the 10 years ending
   * today, and the end may not pass tomorrow.
   */
  internal fun resolveWindow(window: DateWindow?): DateWindow {
    val tomorrow = today().plusDays(1)
    fun parse(value: String?, field: String): LocalDate? =
      value
        ?.takeIf { it.isNotBlank() }
        ?.let {
          try {
            LocalDate.parse(it)
          } catch (e: DateTimeParseException) {
            throw Status.INVALID_ARGUMENT.withDescription("window.$field must be YYYY-MM-DD")
              .withCause(e)
              .asRuntimeException()
          }
        }
    val end = minOf(parse(window?.end_date, "end_date") ?: tomorrow, tomorrow)
    val start =
      maxOf(parse(window?.start_date, "start_date") ?: end.minusYears(DEFAULT_YEARS), ARCHIVE_START)
    if (!start.isBefore(end)) {
      throw Status.INVALID_ARGUMENT.withDescription("window.start_date must be before end_date")
        .asRuntimeException()
    }
    return DateWindow(start_date = start.toString(), end_date = end.toString())
  }

  companion object {
    private const val PLOT_STATISTICS = "plot_statistics"
    private val SERIES_TEMPLATES =
      mapOf(
        TimeSeriesSource.LANDSAT to "landsat_time_series",
        TimeSeriesSource.SENTINEL_2 to "sentinel2_time_series",
        TimeSeriesSource.SENTINEL_1 to "sentinel1_time_series",
      )
    internal val TEMPLATE_NAMES = SERIES_TEMPLATES.values + PLOT_STATISTICS

    /** Bands each source returns, in display order. */
    val SERIES_BANDS: Map<TimeSeriesSource, List<String>> =
      mapOf(
        TimeSeriesSource.LANDSAT to
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
          ),
        TimeSeriesSource.SENTINEL_2 to listOf("NDVI", "EVI", "EVI2", "NBR", "NDMI", "NDWI"),
        TimeSeriesSource.SENTINEL_1 to listOf("VV", "VH", "VV/VH", "RFDI"),
      )

    private const val DEFAULT_YEARS = 10L
    private val ARCHIVE_START: LocalDate = LocalDate.of(1972, 7, 23)
    private const val LAST_POPULATION_YEAR = 2030

    const val ELEVATION_ATTRIBUTION =
      "Copernicus DEM GLO-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018, " +
        "provided under COPERNICUS by the European Union and ESA"
    const val POPULATION_ATTRIBUTION = "WorldPop (www.worldpop.org), CC BY 4.0"

    internal fun parseStatistics(
      result: Any?,
      plot: PlotGeometry,
      populationYear: Int,
    ): GetPlotStatisticsResponse {
      val values = result as? Map<*, *> ?: emptyMap<Any, Any>()
      fun number(key: String) = (values[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }
      return GetPlotStatisticsResponse(
        elevation_min_meters = number("elevation_min"),
        elevation_mean_meters = number("elevation_mean"),
        elevation_max_meters = number("elevation_max"),
        population = if (plot.isPoint) null else number("population"),
        population_density_per_km2 = number("population_density_per_km2"),
        population_year = populationYear,
        attributions = listOf(ELEVATION_ATTRIBUTION, POPULATION_ATTRIBUTION),
      )
    }

    internal fun parseSeries(
      result: Any?,
      source: TimeSeriesSource,
      window: DateWindow,
    ): GetPlotTimeSeriesResponse {
      val observations =
        (result as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.filter { it["t"] is Number }
      return GetPlotTimeSeriesResponse(
        time_start_ms = observations.map { (it["t"] as Number).toLong() },
        scene_ids = observations.map { it["id"] as? String ?: "" },
        platforms = observations.map { it["p"] as? String ?: "" },
        bands =
          SERIES_BANDS.getValue(source).associateWith { band ->
            DoubleSeries(
              values =
                observations.map { observation ->
                  ((observation["v"] as? Map<*, *>)?.get(band) as? Number)?.toDouble() ?: Double.NaN
                }
            )
          },
        window = window,
        attributions =
          listOf(
            if (source == TimeSeriesSource.LANDSAT) LANDSAT_ATTRIBUTION else SENTINEL_ATTRIBUTION
          ),
      )
    }
  }
}

/**
 * A validated plot: one point, or a closed polygon ring, as `[longitude, latitude]` pairs (WGS 84).
 * Polygons are capped at [MAX_VERTICES] vertices and [MAX_AREA_HECTARES] to bound Earth Engine
 * cost.
 */
class PlotGeometry private constructor(private val coordinates: List<List<Double>>) {
  val isPoint: Boolean
    get() = coordinates.size == 1

  /** Geodesic area in square meters (0 for points). */
  val areaSquareMeters: Double
    get() = if (isPoint) 0.0 else sphericalArea(coordinates)

  /** Key identifying the geometry in caches (coordinates rounded to ~1 cm). */
  val cacheKey: String =
    coordinates.joinToString(";") { (lon, lat) ->
      String.format(Locale.ROOT, "%.7f,%.7f", lon, lat)
    }

  internal fun node() =
    if (isPoint) {
      EarthEngineNodes.point(coordinates[0][0], coordinates[0][1])
    } else {
      EarthEngineNodes.polygon(coordinates)
    }

  companion object {
    const val MAX_VERTICES = 1_000
    const val MAX_AREA_HECTARES = 1_000.0
    private const val EARTH_RADIUS_METERS = 6_378_137.0

    fun point(point: GeoPoint): PlotGeometry {
      checkCoordinates(point)
      return PlotGeometry(listOf(listOf(point.longitude, point.latitude)))
    }

    /** A polygon from [points]; the ring is closed if needed. */
    fun polygon(points: List<GeoPoint>): PlotGeometry {
      points.forEach(::checkCoordinates)
      val ring = points.map { listOf(it.longitude, it.latitude) }.toMutableList()
      if (ring.isNotEmpty() && ring.first() != ring.last()) ring += ring.first()
      if (ring.distinct().size < 3) {
        throw invalid("A plot polygon needs at least three distinct vertices")
      }
      if (ring.size - 1 > MAX_VERTICES) {
        throw invalid("Plot polygons may have at most $MAX_VERTICES vertices")
      }
      val geometry = PlotGeometry(ring)
      if (geometry.areaSquareMeters / 10_000 > MAX_AREA_HECTARES) {
        throw invalid(
          "Plots larger than ${MAX_AREA_HECTARES.toInt()} ha are too large for time series"
        )
      }
      return geometry
    }

    /**
     * The geometry to analyze for [plot]: the stored point or polygon of its map feature [entity]
     * (`null` when the feature isn't on the server), else the point or polygon the client drew.
     * Lines aren't supported.
     */
    fun of(plot: PlotRef?, entity: EntityRecord?): PlotGeometry {
      val stored = entity?.let(GeometryProtos::of)
      stored?.let(::pointOrPolygon)?.let {
        return it
      }
      plot?.geometry?.let(::pointOrPolygon)?.let {
        return it
      }
      val entityId = plot?.entity_id.orEmpty()
      throw when {
        stored != null -> invalid("Time series need a point or polygon, not a line")
        entity != null -> invalid("This map feature has no location")
        entityId.isNotBlank() ->
          Status.NOT_FOUND.withDescription("Map feature not found: $entityId").asRuntimeException()
        else -> invalid("plot needs a point or polygon")
      }
    }

    /** The point, or the exterior ring of the polygon, of [geometry]; `null` for other types. */
    private fun pointOrPolygon(geometry: Geometry): PlotGeometry? {
      geometry.point?.let {
        return point(GeometryProtos.toGeoPoint(it))
      }
      geometry.polygon?.let { coordinates ->
        val exterior = coordinates.rings.firstOrNull()?.positions.orEmpty()
        return polygon(exterior.map(GeometryProtos::toGeoPoint))
      }
      return null
    }

    private fun checkCoordinates(point: GeoPoint) {
      if (
        !point.latitude.isFinite() ||
          !point.longitude.isFinite() ||
          abs(point.latitude) > 90 ||
          abs(point.longitude) > 180
      ) {
        throw invalid("Coordinates out of range: ${point.latitude}, ${point.longitude}")
      }
    }

    private fun invalid(message: String) =
      Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException()

    /** Area of a closed ring on a sphere (Chamberlain & Duquette, 2007). */
    private fun sphericalArea(ring: List<List<Double>>): Double {
      var sum = 0.0
      for (i in 0 until ring.size - 1) {
        val (lon1, lat1) = ring[i]
        val (lon2, lat2) = ring[i + 1]
        sum +=
          Math.toRadians(lon2 - lon1) * (2 + sin(Math.toRadians(lat1)) + sin(Math.toRadians(lat2)))
      }
      return abs(sum * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2)
    }
  }
}

/**
 * A per-instance cache whose entries expire after [ttl]. Concurrent requests for the same key share
 * one computation; failures aren't cached.
 */
internal class CoalescingTtlCache<K : Any, V : Any>(
  private val ttl: Duration,
  private val maxEntries: Int,
  private val clock: () -> Instant,
  private val scope: CoroutineScope,
) {
  private class Entry<V>(val expiresAt: Instant) {
    lateinit var result: Deferred<Result<V>>
    @Volatile var failed = false
  }

  private val entries = ConcurrentHashMap<K, Entry<V>>()

  /**
   * The cached value for [key], or the result of [compute]. [beforeCompute] runs (and may throw)
   * only when a new computation starts.
   */
  suspend fun getOrCompute(key: K, beforeCompute: () -> Unit = {}, compute: suspend () -> V): V {
    val now = clock()
    val entry =
      entries.compute(key) { _, current ->
        if (current != null && current.isUsable(now)) {
          current
        } else {
          beforeCompute()
          Entry<V>(now.plus(ttl)).also { created ->
            created.result =
              scope.async(start = CoroutineStart.LAZY) {
                runCatching { compute() }.also { if (it.isFailure) created.failed = true }
              }
          }
        }
      }!!
    entry.result.start()
    val result = entry.result.await()
    result.exceptionOrNull()?.let { error ->
      entries.remove(key, entry)
      throw error
    }
    if (entries.size > maxEntries) evict(now)
    return result.getOrThrow()
  }

  private fun Entry<V>.isUsable(now: Instant): Boolean =
    !result.isCompleted || (!failed && expiresAt.isAfter(now))

  private fun evict(now: Instant) {
    entries.entries.removeIf { (_, entry) -> entry.result.isCompleted && !entry.isUsable(now) }
    val excess = entries.size - maxEntries
    if (excess > 0) {
      entries.entries
        .filter { it.value.result.isCompleted }
        .sortedBy { it.value.expiresAt }
        .take(excess)
        .forEach { entries.remove(it.key, it.value) }
    }
  }
}

/** Token-bucket rate limiter: up to [perMinute] requests per minute per key, in bursts. */
internal class RateLimiter(private val perMinute: Int, private val clock: () -> Instant) {
  private class Bucket(var tokens: Double, var updatedAt: Instant)

  private val buckets = ConcurrentHashMap<String, Bucket>()

  /** Takes one token for [key], or throws `RESOURCE_EXHAUSTED`. */
  fun acquire(key: String) {
    val now = clock()
    val bucket = buckets.computeIfAbsent(key) { Bucket(perMinute.toDouble(), now) }
    synchronized(bucket) {
      val elapsedMinutes = Duration.between(bucket.updatedAt, now).toMillis() / 60_000.0
      bucket.tokens = min(perMinute.toDouble(), bucket.tokens + elapsedMinutes * perMinute)
      bucket.updatedAt = now
      if (bucket.tokens < 1.0) {
        throw Status.RESOURCE_EXHAUSTED.withDescription(
            "Too many time series requests; try again in a minute"
          )
          .asRuntimeException()
      }
      bucket.tokens -= 1.0
    }
  }
}
