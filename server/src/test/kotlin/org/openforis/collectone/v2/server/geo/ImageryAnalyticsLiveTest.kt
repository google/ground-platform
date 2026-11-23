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

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.survey.DateWindow
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource

/**
 * Calls the real Earth Engine API through [ImageryAnalytics], to check the generated templates end
 * to end. Skipped unless `EE_LIVE_TEST=1` and `EE_SERVICE_ACCOUNT_FILE` names a key whose account
 * may run computations, e.g.:
 * ```
 * EE_LIVE_TEST=1 EE_SERVICE_ACCOUNT_FILE=/path/key.json \
 *   ./gradlew :server:test --tests '*ImageryAnalyticsLiveTest'
 * ```
 */
class ImageryAnalyticsLiveTest {
  private val enabled = System.getenv("EE_LIVE_TEST") == "1"

  private val plot =
    PlotGeometry.polygon(
      listOf(
        GeoPoint(latitude = -6.900, longitude = -55.300),
        GeoPoint(latitude = -6.900, longitude = -55.299),
        GeoPoint(latitude = -6.899, longitude = -55.299),
        GeoPoint(latitude = -6.899, longitude = -55.300),
      )
    )

  @Test
  fun computesStatisticsSeriesAndScenesWithEarthEngine() = runTest {
    if (!enabled) return@runTest
    val tiles = EarthEngineTileClient.fromEnvironment()
    val analytics = ImageryAnalytics(EarthEngineValueClient.fromEnvironment(), tiles)

    val statistics = analytics.statistics("live-test", plot)
    println("Statistics: $statistics")
    assertTrue((statistics.elevation_mean_meters ?: 0.0) in 150.0..400.0)

    for (source in
      listOf(TimeSeriesSource.LANDSAT, TimeSeriesSource.SENTINEL_2, TimeSeriesSource.SENTINEL_1)) {
      val started = System.nanoTime()
      val series =
        analytics.timeSeries(
          "live-test",
          plot,
          source,
          DateWindow(start_date = "2022-01-01", end_date = "2024-01-01"),
        )
      val millis = (System.nanoTime() - started) / 1_000_000
      println("$source: ${series.time_start_ms.size} observations in $millis ms")
      assertTrue(series.time_start_ms.isNotEmpty(), "$source returned no observations")
      assertTrue(series.scene_ids.all { SceneKind.of(it) != null }, "$source scene IDs")

      val scene =
        analytics.sceneTileUrl("live-test", series.scene_ids.first(), SceneStretch.SWIR_NIR_RED)
      println("$source scene: ${scene.stretch} ${scene.url_template}")
      assertTrue(scene.url_template.startsWith("https://earthengine.googleapis.com/v1/projects/"))
    }
  }
}
