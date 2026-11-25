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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.client.offline.OfflineRegionClient
import org.openforis.collectone.v2.app.client.offline.OfflineRegionEstimate
import org.openforis.collectone.v2.app.client.offline.OfflineRegionProgress
import org.openforis.collectone.v2.app.client.offline.OfflineRegionRequest
import org.openforis.collectone.v2.app.client.offline.SimulatedOfflineRegionClient
import org.openforis.collectone.v2.app.client.offline.UnavailableOfflineRegionClient
import org.openforis.collectone.v2.app.client.places.OfflineAreaNameClient
import org.openforis.collectone.v2.app.client.places.PlaceholderOfflineAreaNameClient
import org.openforis.collectone.v2.app.data.datasource.local.offline.InMemoryOfflineMapAreaStore
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.testing.FakeClock
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapRepositoryImplTest {
  private val satellite = OfflineImageryType.SATELLITE
  private val terrain = OfflineImageryType.TERRAIN
  private val both = setOf(satellite, terrain)
  private val bounds = LngLatBounds(36.90, -0.40, 36.95, -0.37)
  private val name = OfflineAreaName.Named("Cedar Ridge")
  private val clock = FakeClock(Instant.parse("2026-11-08T10:00:00Z"))
  private val store = InMemoryOfflineMapAreaStore()
  private var online = true
  private var nextId = 0

  /** Longer than any simulated download. */
  private val downloadTime = SimulatedOfflineRegionClient.MAX_DURATION + 1.seconds

  private fun TestScope.repository(
    client: OfflineRegionClient = SimulatedOfflineRegionClient { online },
    nameClient: OfflineAreaNameClient = OfflineAreaNameClient { _, _ -> null },
    scope: CoroutineScope = backgroundScope,
  ) =
    OfflineMapRepositoryImpl(
      store = store,
      regionClient = client,
      nameClient = nameClient,
      clock = clock,
      scope = scope,
      newAreaId = { "area-${++nextId}" },
    )

  private suspend fun Flow<List<OfflineMapArea>>.now() = first()

  private suspend fun OfflineMapRepositoryImpl.area(id: String = "area-1") =
    observeAreas().now().firstOrNull { it.id == id }

  @Test
  fun saving_addsAnAreaWithALayerPerType_thatProgressesToReady() = runTest {
    val client = SimulatedOfflineRegionClient { online }
    val repository = repository(client)

    val result = repository.save(bounds, name, both)
    runCurrent()

    assertEquals(DomainResult.Success("area-1"), result)
    val saving = repository.area()!!
    assertEquals(name, saving.name)
    assertEquals(clock.current, saving.createdAt)
    assertEquals(listOf(satellite, terrain), saving.layers.map { it.type })
    assertTrue(saving.isSaving)
    val satelliteEstimate =
      client.estimate(OfflineRegionRequest("x", bounds, satellite)).storageBytes
    assertEquals(DataSize(satelliteEstimate), saving.layer(satellite)?.size)

    advanceTimeBy(SimulatedOfflineRegionClient.MIN_DURATION / 2)
    runCurrent()
    val halfway = repository.area()!!.layer(satellite)!!
    assertEquals(OfflineAreaLayerStatus.SAVING, halfway.status)
    assertTrue(halfway.progress in 0.2f..0.8f, "${halfway.progress}")
    assertTrue(halfway.savedSize > DataSize.ZERO)

    advanceTimeBy(downloadTime)
    runCurrent()
    val saved = repository.area()!!
    assertEquals(
      listOf(OfflineAreaLayerStatus.READY, OfflineAreaLayerStatus.READY),
      saved.layers.map { it.status },
    )
    assertEquals(1f, saved.layer(satellite)?.progress)
    assertEquals(DataSize(satelliteEstimate), saved.layer(satellite)?.size)
    assertEquals(setOf("area-1-satellite", "area-1-terrain"), client.regionIds())
    assertEquals(
      listOf(OfflineAreaLayerStatus.READY, OfflineAreaLayerStatus.READY),
      store.load().single().layers.map { it.status },
    )
  }

  @Test
  fun newestAreas_comeFirst() = runTest {
    val repository = repository()
    repository.save(bounds, name, setOf(satellite))
    clock.advanceBy(60.seconds)
    repository.save(bounds, OfflineAreaName.Named("Fern Valley"), setOf(satellite))

    assertEquals(listOf("area-2", "area-1"), repository.observeAreas().now().map { it.id })
  }

  @Test
  fun losingTheConnection_pausesTheLayer_andResumingFinishesIt() = runTest {
    val repository = repository()
    repository.save(bounds, name, setOf(satellite))
    advanceTimeBy(2.seconds)
    online = false
    advanceTimeBy(1.seconds)
    runCurrent()

    val paused = repository.area()!!.layer(satellite)!!
    assertEquals(OfflineAreaLayerStatus.PAUSED, paused.status)
    assertEquals(DomainError.Network, paused.failure)
    assertEquals(OfflineAreaLayerStatus.PAUSED, store.load().single().layers.single().status)

    online = true
    repository.resume("area-1")
    runCurrent()
    val resumed = repository.area()!!.layer(satellite)!!
    assertEquals(OfflineAreaLayerStatus.SAVING, resumed.status)
    assertNull(resumed.failure)

    advanceTimeBy(downloadTime)
    runCurrent()
    assertEquals(OfflineAreaLayerStatus.READY, repository.area()!!.layer(satellite)?.status)
  }

  @Test
  fun otherErrors_failTheLayer() = runTest {
    val repository = repository(UnavailableOfflineRegionClient())

    repository.save(bounds, name, setOf(satellite))
    runCurrent()

    val failed = repository.area()!!.layer(satellite)!!
    assertEquals(OfflineAreaLayerStatus.FAILED, failed.status)
    assertIs<DomainError.Unknown>(failed.failure)
    assertNull(failed.size)
  }

  @Test
  fun cancellingOneLayer_removesIt_andCancellingTheLastRemovesTheArea() = runTest {
    val client = SimulatedOfflineRegionClient { online }
    val repository = repository(client)
    repository.save(bounds, name, both)
    advanceTimeBy(2.seconds)

    repository.cancel("area-1", terrain)
    runCurrent()
    assertEquals(listOf(satellite), repository.area()!!.layers.map { it.type })

    repository.cancel("area-1")
    advanceTimeBy(downloadTime)
    runCurrent()
    assertNull(repository.area())
    assertTrue(store.load().isEmpty())
    assertTrue(client.regionIds().isEmpty(), "Cancelled downloads never complete")
  }

  @Test
  fun removingLayersAndAreas_removesTheirRegions() = runTest {
    val client = SimulatedOfflineRegionClient { online }
    val repository = repository(client)
    repository.save(bounds, name, both)
    repository.save(bounds, OfflineAreaName.Named("Fern Valley"), setOf(terrain))
    advanceTimeBy(downloadTime)
    runCurrent()

    repository.removeLayer("area-1", satellite)
    assertEquals(listOf(terrain), repository.area("area-1")!!.layers.map { it.type })
    assertEquals(setOf("area-1-terrain", "area-2-terrain"), client.regionIds())

    repository.removeLayer("area-1", terrain)
    assertNull(repository.area("area-1"))

    repository.removeArea("area-2")
    assertTrue(repository.observeAreas().now().isEmpty())
    assertTrue(client.regionIds().isEmpty())
    assertTrue(store.load().isEmpty())
  }

  @Test
  fun layersLeftSaving_arePausedOnTheNextLaunch() = runTest {
    store.insertArea(
      OfflineMapArea(
        id = "stored",
        name = name,
        bounds = bounds,
        createdAt = clock.current,
        layers =
          listOf(
            OfflineAreaLayer(satellite, OfflineAreaLayerStatus.SAVING, size = DataSize(10)),
            OfflineAreaLayer(terrain, OfflineAreaLayerStatus.READY, size = DataSize(5)),
          ),
      )
    )

    val area = repository().area("stored")!!

    assertEquals(OfflineAreaLayerStatus.PAUSED, area.layer(satellite)?.status)
    assertEquals(OfflineAreaLayerStatus.READY, area.layer(terrain)?.status)
    assertEquals(
      OfflineAreaLayerStatus.PAUSED,
      store.load().single().layer(satellite)?.status,
    )
  }

  @Test
  fun nativeMode_reconcilesTheStoredAreasWithTheRegions() = runTest {
    store.insertArea(
      OfflineMapArea(
        id = "kept",
        name = name,
        bounds = bounds,
        createdAt = clock.current,
        layers =
          listOf(
            OfflineAreaLayer(satellite, OfflineAreaLayerStatus.READY, size = DataSize(10)),
            OfflineAreaLayer(terrain, OfflineAreaLayerStatus.READY, size = DataSize(5)),
          ),
      )
    )
    // The satellite region survived; the terrain one is gone, and an orphan region is left.
    val client = FakeNativeRegionClient(setOf("kept-satellite", "lost-area-terrain"))

    val area = repository(client).area("kept")!!

    assertEquals(OfflineAreaLayerStatus.READY, area.layer(satellite)?.status)
    assertEquals(OfflineAreaLayerStatus.PAUSED, area.layer(terrain)?.status)
    assertEquals(setOf("kept-satellite"), client.regionIds())
  }

  @Test
  fun estimates_coverEachType_andFailuresAreTyped() = runTest {
    val client = SimulatedOfflineRegionClient()
    val sizes = repository(client).estimateSizes(bounds, both)

    val estimated = assertIs<DomainResult.Success<Map<OfflineImageryType, DataSize>>>(sizes).value
    assertEquals(both, estimated.keys)
    assertTrue(estimated.getValue(satellite) > estimated.getValue(terrain))

    val unavailable = repository(UnavailableOfflineRegionClient()).estimateSizes(bounds, both)
    assertIs<DomainResult.Failure>(unavailable)
  }

  @Test
  fun simulatedSaves_usePlaceholderNames() = runTest {
    val repository = repository(nameClient = { _, _ -> "A real place" })

    assertEquals(OfflineRegionMode.SIMULATED, repository.regionMode)
    assertEquals(
      OfflineAreaName.Named(PlaceholderOfflineAreaNameClient.nameAt(bounds.center)),
      repository.resolveName(bounds, 13.0),
    )
  }

  @Test
  fun otherModes_useTheNameClient_andFallBackToTheCenter() = runTest {
    val named = repository(FakeNativeRegionClient(), nameClient = { _, _ -> "Othaya, Nyeri" })
    val unnamed = repository(FakeNativeRegionClient(), nameClient = { _, _ -> null })

    assertEquals(OfflineAreaName.Named("Othaya, Nyeri"), named.resolveName(bounds, 13.0))
    // A frame whose center is exact in floating point.
    val frame = LngLatBounds(36.0, -1.0, 36.5, -0.5)
    assertEquals(
      OfflineAreaName.NearCoordinates(LatLng(-0.75, 36.25)),
      unnamed.resolveName(frame, 13.0),
    )
  }

  /** A NATIVE client over the simulated one, starting with [initialRegionIds] on the device. */
  private class FakeNativeRegionClient(initialRegionIds: Set<String> = emptySet()) :
    OfflineRegionClient {
    private val simulated = SimulatedOfflineRegionClient()
    private var regionIds = initialRegionIds

    override val mode = OfflineRegionMode.NATIVE

    override suspend fun estimate(request: OfflineRegionRequest): OfflineRegionEstimate =
      simulated.estimate(request)

    override fun download(request: OfflineRegionRequest): Flow<OfflineRegionProgress> =
      simulated.download(request)

    override suspend fun regionIds(): Set<String> = regionIds

    override suspend fun remove(regionId: String) {
      regionIds = regionIds - regionId
    }
  }
}
