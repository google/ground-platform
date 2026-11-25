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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openforis.collectone.v2.core.forms.ui.GeoLocationFix
import org.openforis.collectone.v2.core.forms.ui.GeoLocationSource

/**
 * The workbench's simulated GPS: starts at [start] and, while [walk] runs, takes a short step every
 * [stepMillis], turning 45° each time, so location questions (including walked GeoTrace / GeoShape
 * capture) can be demoed without a real receiver.
 */
class SimulatedGeoLocationSource(
  start: GeoLocationFix = DEFAULT_START,
  private val stepMillis: Long = 5_000L,
) : GeoLocationSource {
  override val isSimulated: Boolean = true
  private val fix = MutableStateFlow(start)
  private var stepCount = 0

  override val currentFix: StateFlow<GeoLocationFix?> = fix.asStateFlow()

  /** Moves the simulated collector one step along their walk. */
  fun step() {
    stepCount += 1
    fix.value = simulatedWalkStep(fix.value, stepCount)
  }

  /** Keeps walking until the calling coroutine is cancelled. */
  suspend fun walk() {
    while (true) {
      delay(stepMillis)
      step()
    }
  }

  companion object {
    /** A plausible field fix used as the simulated starting point. */
    val DEFAULT_START =
      GeoLocationFix(
        latitude = -1.292066,
        longitude = 36.821946,
        altitudeMeters = 1680.0,
        accuracyMeters = 3.2,
      )
  }
}

/** Step size of the simulated walk, in degrees (about 9 m at the equator). */
private const val WALK_STEP_DEGREES = 0.00008

/** Where the simulated collector is after walking step number [step] from [from]. */
internal fun simulatedWalkStep(from: GeoLocationFix, step: Int): GeoLocationFix {
  val headingRad = (step * 45.0) * (PI / 180.0)
  return from.copy(
    latitude = roundTo6(from.latitude + WALK_STEP_DEGREES * sin(headingRad)),
    longitude = roundTo6(from.longitude + WALK_STEP_DEGREES * cos(headingRad)),
  )
}

private fun roundTo6(value: Double): Double = (value * 1_000_000.0).roundToInt() / 1_000_000.0
