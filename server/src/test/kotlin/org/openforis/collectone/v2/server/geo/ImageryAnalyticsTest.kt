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

import google.earthengine.v1main.Expression
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.survey.DateWindow
import openforis.collectone.v1.survey.PlotRef
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource

class ImageryAnalyticsTest {
  private val now = Instant.parse("2026-10-29T12:00:00Z")
  private val computed = CopyOnWriteArrayList<Pair<Expression, String>>()
  private val tiles = CopyOnWriteArrayList<Expression>()

  private fun analytics(
    requestsPerMinute: Int = 60,
    respond: (Expression) -> Any? = { landsatResult },
  ) =
    ImageryAnalytics(
      valueClient = { expression, tag ->
        computed += expression to tag
        respond(expression)
      },
      tileClient =
        EarthEngineTileClient.create(clock = { now }) { expression ->
          tiles += expression
          "https://earthengine.googleapis.com/v1/projects/p/maps/scene-${tiles.size}/tiles/{z}/{x}/{y}"
        },
      clock = { now },
      requestsPerMinute = requestsPerMinute,
    )

  private val square =
    PlotGeometry.polygon(
      listOf(
        GeoPoint(latitude = -6.900, longitude = -55.300),
        GeoPoint(latitude = -6.900, longitude = -55.299),
        GeoPoint(latitude = -6.899, longitude = -55.299),
        GeoPoint(latitude = -6.899, longitude = -55.300),
      )
    )

  private val landsatResult =
    listOf(
      mapOf(
        "t" to 1.4920913330980E12,
        "id" to "LANDSAT/LC08/C02/T1_TOA/LC08_227065_20170413",
        "p" to "LANDSAT_8",
        "v" to mapOf("NDVI" to 0.81, "NDFI" to 0.9, "SWIR1" to 0.14),
      ),
      mapOf(
        "t" to 1.6920313330980E12,
        "id" to "LANDSAT/LC09/C02/T1_TOA/LC09_227065_20230814",
        "p" to "LANDSAT_9",
        "v" to mapOf("NDVI" to 0.32, "NDFI" to null, "SWIR1" to 0.27),
      ),
    )

  @Test
  fun templates_allLoadAsFunctionsWithTheExpectedArguments() {
    val expected =
      mapOf(
        "landsat_time_series" to listOf("geometry", "start", "end"),
        "sentinel2_time_series" to listOf("geometry", "start", "end"),
        "sentinel1_time_series" to listOf("geometry", "start", "end"),
        "plot_statistics" to listOf("geometry", "popYear"),
      )
    assertEquals(expected.keys, ImageryAnalytics.TEMPLATE_NAMES.toSet())
    for ((name, arguments) in expected) {
      val template = EarthEngineTemplate.load(name)
      assertEquals(arguments, template.argumentNames, name)
      assertTrue(template.version >= 1, name)
    }
  }

  @Test
  fun invoke_callsTheDefinitionByReference() {
    val template = EarthEngineTemplate.load("plot_statistics")
    val expression =
      template.invoke(
        mapOf(
          "geometry" to EarthEngineNodes.point(1.0, 2.0),
          "popYear" to EarthEngineNodes.constant(2026),
        )
      )
    val invocation = expression.values.getValue(expression.result).function_invocation_value!!
    assertEquals(template.expression.result, invocation.function_reference)
    assertEquals(setOf("geometry", "popYear"), invocation.arguments.keys)
    assertTrue(expression.values.keys.containsAll(template.expression.values.keys))
    assertFailsWith<IllegalArgumentException> { template.invoke(emptyMap()) }
  }

  @Test
  fun timeSeries_parsesObservationsByColumn() = runTest {
    val response = analytics().timeSeries("user", square, TimeSeriesSource.LANDSAT, window = null)

    assertEquals(listOf(1_492_091_333_098L, 1_692_031_333_098L), response.time_start_ms)
    assertEquals(listOf("LANDSAT_8", "LANDSAT_9"), response.platforms)
    assertEquals("LANDSAT/LC09/C02/T1_TOA/LC09_227065_20230814", response.scene_ids[1])
    assertEquals(listOf(0.81, 0.32), response.bands.getValue("NDVI").values)
    assertTrue(response.bands.getValue("NDFI").values[1].isNaN())
    assertTrue(response.bands.getValue("NBR").values.all { it.isNaN() })
    assertEquals(DateWindow(start_date = "2016-10-30", end_date = "2026-10-30"), response.window)
    assertEquals(listOf(LANDSAT_ATTRIBUTION), response.attributions)
    assertEquals("c1-ts-series", computed.single().second)
  }

  @Test
  fun timeSeries_passesTheWindowAndGeometryToTheTemplate() = runTest {
    analytics()
      .timeSeries(
        "user",
        square,
        TimeSeriesSource.SENTINEL_1,
        DateWindow(start_date = "2020-01-01", end_date = "2021-01-01"),
      )
    val (expression, _) = computed.single()
    val arguments =
      expression.values.getValue(expression.result).function_invocation_value!!.arguments
    assertEquals("2020-01-01", arguments.getValue("start").constant_value)
    assertEquals("2021-01-01", arguments.getValue("end").constant_value)
    assertEquals(
      "GeometryConstructors.Polygon",
      arguments.getValue("geometry").function_invocation_value?.function_name,
    )
    assertEquals(
      EarthEngineTemplate.load("sentinel1_time_series").expression.result,
      expression.values.getValue(expression.result).function_invocation_value?.function_reference,
    )
  }

  @Test
  fun timeSeries_cachesResultsAndSharesConcurrentComputations() = runTest {
    val started = CountDownLatch(1)
    val calls = AtomicInteger()
    val analytics =
      analytics(
        respond = {
          calls.incrementAndGet()
          started.await(5, TimeUnit.SECONDS)
          landsatResult
        }
      )
    val requests =
      (1..5).map {
        async { analytics.timeSeries("user-$it", square, TimeSeriesSource.LANDSAT, null) }
      }
    started.countDown()
    requests.awaitAll()
    analytics.timeSeries("user", square, TimeSeriesSource.LANDSAT, null)

    assertEquals(1, calls.get())
  }

  @Test
  fun timeSeries_doesNotCacheFailures() = runTest {
    var fail = true
    val analytics =
      analytics(
        respond = {
          if (fail) throw Status.UNAVAILABLE.asRuntimeException()
          landsatResult
        }
      )
    assertFailsWith<StatusRuntimeException> {
      analytics.timeSeries("user", square, TimeSeriesSource.LANDSAT, null)
    }
    fail = false
    assertEquals(
      2,
      analytics.timeSeries("user", square, TimeSeriesSource.LANDSAT, null).time_start_ms.size,
    )
  }

  @Test
  fun timeSeries_rateLimitsCacheMissesPerCaller() = runTest {
    val analytics = analytics(requestsPerMinute = 2)
    val windows = (2018..2021).map { DateWindow(start_date = "$it-01-01", end_date = "$it-12-31") }
    analytics.timeSeries("busy", square, TimeSeriesSource.LANDSAT, windows[0])
    analytics.timeSeries("busy", square, TimeSeriesSource.LANDSAT, windows[1])
    // Cache hits don't count against the budget.
    analytics.timeSeries("busy", square, TimeSeriesSource.LANDSAT, windows[0])
    val error =
      assertFailsWith<StatusRuntimeException> {
        analytics.timeSeries("busy", square, TimeSeriesSource.LANDSAT, windows[2])
      }
    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
    // Other callers have their own budget.
    analytics.timeSeries("calm", square, TimeSeriesSource.LANDSAT, windows[3])
  }

  @Test
  fun resolveWindow_validatesAndClamps() {
    val analytics = analytics()
    assertEquals(
      DateWindow(start_date = "1972-07-23", end_date = "2026-10-30"),
      analytics.resolveWindow(DateWindow(start_date = "1900-01-01", end_date = "2099-01-01")),
    )
    for (bad in
      listOf(
        DateWindow(start_date = "2020-13-01"),
        DateWindow(start_date = "2021-01-01", end_date = "2020-01-01"),
      )) {
      val error = assertFailsWith<StatusRuntimeException> { analytics.resolveWindow(bad) }
      assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }
  }

  @Test
  fun statistics_dropsPopulationForPointsAndToleratesMissingKeys() = runTest {
    val values =
      mapOf(
        "elevation_min" to 412.0,
        "elevation_mean" to 425.5,
        "elevation_max" to 438.0,
        "population" to 149.8,
        "population_density_per_km2" to 17_641.0,
      )
    val point = PlotGeometry.point(GeoPoint(latitude = 6.69, longitude = -1.62))
    val pointStats = analytics(respond = { values }).statistics("user", point)
    assertNull(pointStats.population)
    assertEquals(17_641.0, pointStats.population_density_per_km2)
    assertEquals(2026, pointStats.population_year)

    val atSea = analytics(respond = { emptyMap<String, Any>() }).statistics("user", square)
    assertNull(atSea.elevation_mean_meters)
    assertNull(atSea.population)
    assertEquals(
      listOf(ImageryAnalytics.ELEVATION_ATTRIBUTION, ImageryAnalytics.POPULATION_ATTRIBUTION),
      atSea.attributions,
    )
    assertEquals("c1-ts-stats", computed.last().second)
  }

  @Test
  fun plotGeometry_validatesShapes() {
    assertTrue(square.areaSquareMeters in 12_000.0..12_500.0, "${square.areaSquareMeters}")
    val tooLarge =
      listOf(
        GeoPoint(latitude = 0.0, longitude = 0.0),
        GeoPoint(latitude = 0.0, longitude = 0.5),
        GeoPoint(latitude = 0.5, longitude = 0.5),
      )
    val invalidShapes =
      listOf(
        { PlotGeometry.polygon(tooLarge) },
        { PlotGeometry.polygon(tooLarge.take(2)) },
        { PlotGeometry.point(GeoPoint(latitude = 91.0, longitude = 0.0)) },
        { PlotGeometry.of(null, EntityRecord(trace = GeoTrace(points = tooLarge))) },
        { PlotGeometry.of(null, EntityRecord()) },
        { PlotGeometry.of(PlotRef(), null) },
      )
    for (shape in invalidShapes) {
      val error = assertFailsWith<StatusRuntimeException> { shape() }
      assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }
    val notUploaded =
      assertFailsWith<StatusRuntimeException> { PlotGeometry.of(PlotRef(entity_id = "new"), null) }
    assertEquals(Status.Code.NOT_FOUND, notUploaded.status.code)
    val closed =
      PlotGeometry.of(
        null,
        EntityRecord(
          shape = GeoShape(points = tooLarge.take(2) + GeoPoint(latitude = 0.001, longitude = 0.0))
        ),
      )
    assertTrue(!closed.isPoint)
  }

  @Test
  fun sceneTileUrl_visualizesAllowedScenesWithTheirBands() = runTest {
    val analytics = analytics()
    val oli =
      analytics.sceneTileUrl(
        "user",
        "LANDSAT/LC08/C02/T1_TOA/LC08_227065_20170413",
        SceneStretch.SWIR_NIR_RED,
      )
    assertEquals(SceneStretch.SWIR_NIR_RED, oli.stretch)
    assertEquals(LANDSAT_ATTRIBUTION, oli.attribution)
    assertTrue(oli.url_template.endsWith("/tiles/{z}/{x}/{y}"))
    assertEquals(listOf("B6", "B5", "B4"), selectedBands(tiles.last()))

    analytics.sceneTileUrl(
      "user",
      "LANDSAT/LT05/C02/T1_TOA/LT05_227065_19840621",
      SceneStretch.SWIR_NIR_RED,
    )
    assertEquals(listOf("B5", "B4", "B3"), selectedBands(tiles.last()))

    // SAR scenes ignore optical stretches.
    val sar =
      analytics.sceneTileUrl(
        "user",
        "COPERNICUS/S1_GRD/S1A_IW_GRDH_1SDV_20161102T092349_20161102T092414_013761_016187_A061",
        SceneStretch.TRUE_COLOR,
      )
    assertEquals(SceneStretch.SAR_VV_VH_RATIO, sar.stretch)

    for (sceneId in
      listOf(
        "USERS/someone/private_asset",
        "LANDSAT/LC08/C02/T1_L2/LC08_1",
        "LANDSAT/LC08/C02/T1_TOA/../x",
      )) {
      val error =
        assertFailsWith<StatusRuntimeException> {
          analytics.sceneTileUrl("user", sceneId, SceneStretch.TRUE_COLOR)
        }
      assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }
  }

  /** Band selectors of the `Image.select` under a scene visualization. */
  private fun selectedBands(expression: Expression): Any? =
    expression.values
      .getValue(expression.result)
      .function_invocation_value!!
      .arguments
      .getValue("image")
      .function_invocation_value!!
      .arguments
      .getValue("bandSelectors")
      .constant_value
}
