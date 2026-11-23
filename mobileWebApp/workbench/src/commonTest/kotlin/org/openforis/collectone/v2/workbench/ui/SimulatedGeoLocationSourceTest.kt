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
package org.openforis.collectone.v2.workbench.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.core.forms.ui.GeoLocationFix

/** The workbench's simulated GPS walks a small octagon, one step per interval. */
class SimulatedGeoLocationSourceTest {

  @Test
  fun startsWithAFixAtTheStartingPoint() {
    val start = GeoLocationFix(latitude = 1.0, longitude = 2.0, accuracyMeters = 3.0)

    assertEquals(start, SimulatedGeoLocationSource(start).currentFix.value)
  }

  @Test
  fun step_turnsFortyFiveDegreesEachStep_keepingAltitudeAndAccuracy() {
    val source =
      SimulatedGeoLocationSource(
        GeoLocationFix(latitude = 0.0, longitude = 0.0, altitudeMeters = 10.0, accuracyMeters = 3.0)
      )

    source.step() // heading 45°: north-east
    assertEquals(
      GeoLocationFix(
        latitude = 0.000057,
        longitude = 0.000057,
        altitudeMeters = 10.0,
        accuracyMeters = 3.0,
      ),
      source.currentFix.value,
    )

    source.step() // heading 90°: north
    assertEquals(0.000137, source.currentFix.value?.latitude)
    assertEquals(0.000057, source.currentFix.value?.longitude)
  }

  @Test
  fun walk_takesOneStepPerInterval_untilCancelled() = runTest {
    val start = GeoLocationFix(latitude = 0.0, longitude = 0.0, accuracyMeters = 3.0)
    val walked = SimulatedGeoLocationSource(start, stepMillis = 1_000L)
    val stepped = SimulatedGeoLocationSource(start)

    val walk = launch { walked.walk() }
    advanceTimeBy(999L)
    runCurrent()
    assertEquals(start, walked.currentFix.value, "no step before the first interval")

    advanceTimeBy(2_000L)
    runCurrent()
    stepped.step()
    stepped.step()
    assertEquals(stepped.currentFix.value, walked.currentFix.value, "two steps after two intervals")

    walk.cancel()
    advanceTimeBy(5_000L)
    runCurrent()
    assertEquals(stepped.currentFix.value, walked.currentFix.value, "no steps once cancelled")
  }

  @Test
  fun simulatedWalkStep_afterEightStepsReturnsNearTheStart() {
    val start = SimulatedGeoLocationSource.DEFAULT_START
    val end = (1..8).fold(start) { fix, step -> simulatedWalkStep(fix, step) }

    assertEquals(start.latitude, end.latitude, absoluteTolerance = 0.000005)
    assertEquals(start.longitude, end.longitude, absoluteTolerance = 0.000005)
  }
}
