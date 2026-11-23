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
package org.openforis.collectone.v2.app.ui.text

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.StraightLineVector

class WayfindingTextTest {
  private fun vector(distance: Measurement) =
    StraightLineVector(
      fromNormalizedX = 0f,
      fromNormalizedY = 0f,
      toNormalizedX = 0.1f,
      toNormalizedY = 0f,
      distanceMeters = 156,
      distance = distance,
      bearingDegrees = 90,
      cardinalDirection = "E",
      estimatedWalkMinutes = 2,
      hasArrived = false,
    )

  private fun resolve(text: UiText): String = runBlocking { text.resolveString() }

  @Test
  fun badge_isTheDistanceAndBearing() {
    val metric = vector(Measurement(156.0, MeasurementUnit.METER, decimals = 0))
    val imperial = vector(Measurement(1.2, MeasurementUnit.MILE, decimals = 1))

    assertEquals("156 m • 90° E", resolve(wayfindingBadgeText(metric)))
    assertEquals("1.2 mi • 90° E", resolve(wayfindingBadgeText(imperial)))
  }

  @Test
  fun badge_namesTheDistanceUnitInTheUsersLanguage() {
    val feet = vector(Measurement(512.0, MeasurementUnit.FOOT, decimals = 0))
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("es"))
    val spanish =
      try {
        resolve(wayfindingBadgeText(feet))
      } finally {
        Locale.setDefault(previous)
      }

    assertEquals("512 pies • 90° E", spanish)
  }
}
