/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.client.location

import kotlin.test.Test
import kotlin.test.assertEquals

class LocationClientTest {
  @Test
  fun updateSimulatedGpsLocation_andReset_updatesSnapshot() {
    val locationClient = LocationClient()
    assertEquals(0.50f, locationClient.readCurrentLocation().normalizedX)

    locationClient.updateSimulatedGpsLocation(0.65f, 0.35f, "-0.4200°, 36.9500°")
    val updated = locationClient.readCurrentLocation()
    assertEquals(0.65f, updated.normalizedX)
    assertEquals(0.35f, updated.normalizedY)
    assertEquals("-0.4200°, 36.9500°", updated.coordinatesLabel)

    locationClient.reset()
    assertEquals(0.50f, locationClient.readCurrentLocation().normalizedX)
  }
}
