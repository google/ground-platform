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
package org.openforis.collectone.v2.core.forms.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.forms.GeoPoint

class GeoLocationSourceTest {

  @Test
  fun noGeoLocationSource_neverHasAFix() {
    assertNull(NoGeoLocationSource.currentFix.value)
  }

  @Test
  fun geoCapturePoint_withoutFix_recordsNothing() {
    assertNull(geoCapturePoint(fix = null))
    assertNull(geoCapturePoint(fix = null, panOffsetLat = 0.001, panOffsetLon = 0.001))
  }

  @Test
  fun geoCapturePoint_recordsTheFixRoundedToSixDecimals() {
    val fix =
      GeoLocationFix(
        latitude = 51.50735091,
        longitude = -0.12775829,
        altitudeMeters = 11.0,
        accuracyMeters = 4.5,
      )

    assertEquals(
      GeoPoint(
        latitude = 51.507351,
        longitude = -0.127758,
        altitude_meters = 11.0,
        accuracy_meters = 4.5,
      ),
      geoCapturePoint(fix),
    )
  }

  @Test
  fun geoCapturePoint_whenPanned_recordsTheCrosshairWithTheFixAccuracy() {
    val fix = GeoLocationFix(latitude = 10.0, longitude = 20.0, accuracyMeters = 3.0)

    assertEquals(
      GeoPoint(latitude = 10.0005, longitude = 19.9995, accuracy_meters = 3.0),
      geoCapturePoint(fix, panOffsetLat = 0.0005, panOffsetLon = -0.0005),
    )
  }

  @Test
  fun geoCapturePoint_keepsUnknownAltitudeAndAccuracyUnknown() {
    val point = geoCapturePoint(GeoLocationFix(latitude = 1.0, longitude = 2.0))

    assertNull(point?.altitude_meters)
    assertNull(point?.accuracy_meters)
  }
}
