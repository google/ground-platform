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
package org.openforis.collectone.v2.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.forms.ui.GeoLocationFix

class LocationFixSelectorTest {
  @Test
  fun firstFix_isTaken() {
    assertTrue(
      LocationFixSelector.isBetter(network(atMillis = 0, accuracy = 800.0), current = null)
    )
  }

  @Test
  fun moreAccurateGpsFix_replacesANetworkFix() {
    assertTrue(
      LocationFixSelector.isBetter(gps(atMillis = 1_000, accuracy = 5.0), network(0, 800.0))
    )
  }

  @Test
  fun lessAccurateNetworkFix_doesNotReplaceAFreshGpsFix() {
    assertFalse(
      LocationFixSelector.isBetter(network(atMillis = 2_000, accuracy = 30.0), gps(1_000, 5.0))
    )
  }

  @Test
  fun networkFix_replacesAGpsFixThatWentStale() {
    assertTrue(
      LocationFixSelector.isBetter(network(atMillis = 40_000, accuracy = 900.0), gps(0, 5.0))
    )
  }

  @Test
  fun muchOlderFix_neverReplacesTheCurrentOne() {
    assertFalse(
      LocationFixSelector.isBetter(gps(atMillis = 0, accuracy = 3.0), network(40_000, 900.0))
    )
  }

  @Test
  fun newerFixFromTheSameProvider_replacesItUnlessFarLessAccurate() {
    assertTrue(
      LocationFixSelector.isBetter(gps(atMillis = 2_000, accuracy = 12.0), gps(1_000, 5.0))
    )
    assertFalse(
      LocationFixSelector.isBetter(gps(atMillis = 2_000, accuracy = 400.0), gps(1_000, 5.0))
    )
  }

  @Test
  fun newerAsAccurateFix_fromAnotherProvider_replacesIt() {
    assertTrue(
      LocationFixSelector.isBetter(network(atMillis = 2_000, accuracy = 20.0), gps(1_000, 20.0))
    )
  }

  @Test
  fun fixWithoutAccuracy_countsAsTheLeastAccurate() {
    assertFalse(
      LocationFixSelector.isBetter(network(atMillis = 2_000, accuracy = null), gps(1_000, 50.0))
    )
  }

  private fun gps(atMillis: Long, accuracy: Double?) = fix("gps", atMillis, accuracy)

  private fun network(atMillis: Long, accuracy: Double?) = fix("network", atMillis, accuracy)

  private fun fix(provider: String, atMillis: Long, accuracy: Double?) =
    ProviderFix(
      fix = GeoLocationFix(latitude = -1.29, longitude = 36.82, accuracyMeters = accuracy),
      provider = provider,
      elapsedRealtimeMillis = atMillis,
    )
}
