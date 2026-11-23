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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.openforis.collectone.v2.map.LngLatBounds

class OfflineMapModelsTest {
  private val satellite = OfflineImageryType.SATELLITE
  private val terrain = OfflineImageryType.TERRAIN

  @Test
  fun antimeridian_isSpannedWhenWestIsEastOfEastOrAnEdgeIsBeyond180() {
    assertTrue(LngLatBounds(179.9, 0.0, -179.9, 0.1).spansAntimeridian)
    assertTrue(LngLatBounds(179.9, 0.0, 180.1, 0.1).spansAntimeridian)
    assertTrue(LngLatBounds(-180.1, 0.0, -179.9, 0.1).spansAntimeridian)
    assertFalse(LngLatBounds(36.9, -0.4, 37.0, -0.3).spansAntimeridian)
  }

  @Test
  fun requiredFreeSpace_addsTheMarginAndTheReserve() {
    assertEquals(
      DataSize(120_000_000 + 500_000_000),
      OfflineMapLimits.requiredFreeSpace(DataSize(100_000_000)),
    )
  }

  @Test
  fun area_totalsItsKnownLayerSizes_andFindsLayers() {
    val area =
      OfflineMapArea(
        id = "a",
        name = OfflineAreaName.Named("Cedar Ridge"),
        bounds = LngLatBounds(0.0, 0.0, 0.01, 0.01),
        createdAt = Instant.fromEpochSeconds(0),
        layers =
          listOf(
            OfflineAreaLayer(satellite, OfflineAreaLayerStatus.READY, size = DataSize(30)),
            OfflineAreaLayer(terrain, OfflineAreaLayerStatus.SAVING, progress = 0.5f),
          ),
      )

    assertEquals(DataSize(30), area.totalSize)
    assertTrue(area.isSaving)
    assertEquals(1f, area.layer(satellite)?.progress)
    assertEquals(OfflineAreaLayerStatus.SAVING, area.layer(terrain)?.status)
    assertNull(area.copy(layers = area.layers.take(1)).layer(terrain))
  }

  @Test
  fun estimate_withOtherTypes_checksTheSpaceForTheirTotal() {
    val reserve = OfflineMapLimits.FREE_SPACE_RESERVE
    val estimate =
      OfflineAreaEstimate(
        areaKm2 = 10.0,
        types = setOf(satellite),
        verdict = OfflineAreaVerdict.Ready,
        sizes = mapOf(satellite to DataSize(100_000_000), terrain to DataSize(50_000_000)),
        // Room for satellite (120 MB with the margin) but not both (180 MB).
        freeSpace = reserve + DataSize(150_000_000),
      )

    val both = estimate.withTypes(setOf(satellite, terrain))

    assertEquals(DataSize(150_000_000), both.total())
    assertEquals(OfflineAreaVerdict.NotEnoughSpace(DataSize(30_000_000)), both.verdict)
    assertEquals(OfflineAreaVerdict.Ready, both.withTypes(setOf(terrain)).verdict)
  }

  @Test
  fun estimate_withOtherTypes_keepsVerdictsThatAreNotAboutSpace() {
    val offline = OfflineAreaEstimate(10.0, setOf(satellite), OfflineAreaVerdict.Offline)

    assertEquals(OfflineAreaVerdict.Offline, offline.withTypes(setOf(terrain)).verdict)
    assertEquals(setOf(terrain), offline.withTypes(setOf(terrain)).types)
  }

  @Test
  fun spaceVerdict_isReadyWhenFreeSpaceIsUnknown() {
    assertEquals(
      OfflineAreaVerdict.Ready,
      OfflineAreaEstimate.spaceVerdict(DataSize(Long.MAX_VALUE / 4), freeSpace = null),
    )
  }
}
