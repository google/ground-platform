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
package org.openforis.collectone.v2.core.forms.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserGeoLocationFixTest {

  @Test
  fun fullReading_mapsEveryCoordinate() {
    val fix =
      browserGeoLocationFix(
        latitude = -1.292066,
        longitude = 36.821946,
        altitudeMeters = 1680.0,
        accuracyMeters = 3.2,
      )

    assertEquals(
      GeoLocationFix(
        latitude = -1.292066,
        longitude = 36.821946,
        altitudeMeters = 1680.0,
        accuracyMeters = 3.2,
      ),
      fix,
    )
  }

  @Test
  fun readingWithoutAltitude_hasUnknownAltitude() {
    val fix =
      browserGeoLocationFix(
        latitude = 10.0,
        longitude = 20.0,
        altitudeMeters = Double.NaN,
        accuracyMeters = 5.0,
      )

    assertNull(fix?.altitudeMeters)
  }

  @Test
  fun readingWithoutAccuracy_hasUnknownAccuracy() {
    val fix =
      browserGeoLocationFix(
        latitude = 10.0,
        longitude = 20.0,
        altitudeMeters = 0.0,
        accuracyMeters = Double.NaN,
      )

    assertNull(fix?.accuracyMeters)
  }

  @Test
  fun readingWithNegativeAccuracy_hasUnknownAccuracy() {
    val fix =
      browserGeoLocationFix(
        latitude = 10.0,
        longitude = 20.0,
        altitudeMeters = 0.0,
        accuracyMeters = -1.0,
      )

    assertNull(fix?.accuracyMeters)
  }

  @Test
  fun readingWithoutLatitude_isNoFix() {
    val fix =
      browserGeoLocationFix(
        latitude = Double.NaN,
        longitude = 20.0,
        altitudeMeters = 0.0,
        accuracyMeters = 5.0,
      )

    assertNull(fix)
  }

  @Test
  fun readingWithInfiniteLongitude_isNoFix() {
    val fix =
      browserGeoLocationFix(
        latitude = 10.0,
        longitude = Double.POSITIVE_INFINITY,
        altitudeMeters = 0.0,
        accuracyMeters = 5.0,
      )

    assertNull(fix)
  }
}
