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
package org.openforis.collectone.v2.app.client.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.map.LngLatBounds

class SimulatedOfflineRegionClientTest {
  /** About 7 × 7 km around the equator and the prime meridian, inside one zoom 6 tile. */
  private val bounds = LngLatBounds(0.01, 0.01, 0.07, 0.07)

  private fun request(type: OfflineImageryType = OfflineImageryType.SATELLITE) =
    OfflineRegionRequest("area-satellite", bounds, type)

  @Test
  fun tileCount_coversTheBoundsAtEachZoom() {
    // One tile per side at zoom 0 covers the world.
    assertEquals(
      1,
      SimulatedOfflineRegionClient.tileCount(LngLatBounds(-180.0, -85.0, 180.0, 85.0), 0),
    )
    assertEquals(
      4,
      SimulatedOfflineRegionClient.tileCount(LngLatBounds(-180.0, -85.0, 180.0, 85.0), 1),
    )
    // A small area away from tile edges is one tile at zoom 6.
    assertEquals(1, SimulatedOfflineRegionClient.tileCount(bounds, 6))
    // At zoom 16 a tile is about 611 m at the equator: 0.06° (6.7 km) spans 11 or 12 tiles.
    val zoom16 = SimulatedOfflineRegionClient.tileCount(bounds, 16)
    assertTrue(zoom16 in 121L..144L, "zoom 16 tiles: $zoom16")
  }

  @Test
  fun estimate_isTheTilesOfZoomSixToSixteen_timesTheTypicalTileSize() = runTest {
    val client = SimulatedOfflineRegionClient()
    val tiles = SimulatedOfflineRegionClient.tileCount(request())

    val satellite = client.estimate(request(OfflineImageryType.SATELLITE))
    val terrain = client.estimate(request(OfflineImageryType.TERRAIN))

    assertEquals(tiles * 120_000, satellite.storageBytes)
    assertEquals(tiles * 40_000, terrain.storageBytes)
    assertEquals(SimulatedOfflineRegionClient.ERROR_MARGIN, satellite.errorMargin)
    // Tens of MB for a 50 km² satellite layer.
    assertTrue(satellite.storageBytes in 10_000_000L..60_000_000L, "${satellite.storageBytes}")
  }

  @Test
  fun download_advancesInPacedSteps_andCompletes() = runTest {
    val client = SimulatedOfflineRegionClient()
    val progress = mutableListOf<OfflineRegionProgress>()
    val job = launch { client.download(request()).collect { progress += it } }

    runCurrent()
    assertEquals(listOf(0L), progress.map { it.completed })

    advanceTimeBy(SimulatedOfflineRegionClient.MIN_DURATION / 2)
    runCurrent()
    assertTrue(progress.last().fraction in 0.3f..0.7f, "${progress.last().fraction}")
    assertTrue(client.regionIds().isEmpty())

    advanceTimeBy(SimulatedOfflineRegionClient.MAX_DURATION)
    runCurrent()
    assertTrue(job.isCompleted)
    assertEquals(SimulatedOfflineRegionClient.STEPS + 1, progress.size)
    assertEquals(1f, progress.last().fraction)
    assertEquals(client.estimate(request()).storageBytes, progress.last().completedBytes)
    assertEquals(setOf("area-satellite"), client.regionIds())
  }

  @Test
  fun downloads_takeSixToTenSeconds() {
    assertEquals(6.seconds, SimulatedOfflineRegionClient.duration(0))
    assertEquals(8.seconds, SimulatedOfflineRegionClient.duration(20_000_000))
    assertEquals(10.seconds, SimulatedOfflineRegionClient.duration(5_000_000_000))
  }

  @Test
  fun goingOffline_failsTheDownloadWithNetwork() = runTest {
    var online = true
    val client = SimulatedOfflineRegionClient(isOnline = { online })
    var failure: Throwable? = null
    val progress = mutableListOf<OfflineRegionProgress>()
    launch {
      try {
        client.download(request()).collect { progress += it }
      } catch (e: OfflineRegionException) {
        failure = e
      }
    }
    advanceTimeBy(2.seconds)
    runCurrent()
    online = false
    advanceTimeBy(1.seconds)
    runCurrent()

    assertEquals(DomainError.Network, (failure as OfflineRegionException).error)
    assertTrue(progress.last().fraction < 1f)
    assertTrue(client.regionIds().isEmpty())
  }

  @Test
  fun remove_forgetsACompletedRegion() = runTest {
    val client = SimulatedOfflineRegionClient()
    client.download(request()).toList()

    client.remove("area-satellite")

    assertTrue(client.regionIds().isEmpty())
    assertEquals(OfflineRegionMode.SIMULATED, client.mode)
  }

  @Test
  fun unavailableClient_failsEstimatesAndDownloads() = runTest {
    val client = UnavailableOfflineRegionClient()

    assertEquals(OfflineRegionMode.UNAVAILABLE, client.mode)
    assertFailsWith<OfflineRegionException> { client.estimate(request()) }
    assertFailsWith<OfflineRegionException> { client.download(request()).toList() }
    assertTrue(client.regionIds().isEmpty())
  }

  @Test
  fun regionIds_nameTheAreaAndImageryType() {
    assertEquals(
      "a1-terrain",
      OfflineRegionRequest.regionIdOf("a1", OfflineImageryType.TERRAIN),
    )
    assertEquals(
      "a1-satellite",
      OfflineRegionRequest.regionIdOf("a1", OfflineImageryType.SATELLITE),
    )
  }
}
