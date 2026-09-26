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

import kotlin.math.abs
import kotlin.math.roundToInt
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.DeviceLocationSnapshot

/**
 * Device hardware client (`*Client`) encapsulating GNSS receiver and GPS sensor readings per
 * Section 4 of `docs/technical/client/architecture.md`.
 */
class LocationClient {
  private var snapshot: DeviceLocationSnapshot = DeviceLocationSnapshot()

  fun readCurrentLocation(): DeviceLocationSnapshot = snapshot

  fun updateSimulatedGpsLocation(
    normalizedX: Float,
    normalizedY: Float,
    coordinatesLabel: String? = null,
  ): DeviceLocationSnapshot {
    val clampedX = normalizedX.coerceIn(0.08f, 0.92f)
    val clampedY = normalizedY.coerceIn(0.08f, 0.92f)
    val resolvedLabel =
      coordinatesLabel
        ?: run {
          val lat = -0.4150 - (clampedY * 0.0120)
          val lon = 36.9450 + (clampedX * 0.0120)
          val latRounded = ((lat * 10000.0).roundToInt()) / 10000.0
          val lonRounded = ((lon * 10000.0).roundToInt()) / 10000.0
          "${latRounded}°, ${lonRounded}° (±2.8m GPS)"
        }
    snapshot =
      snapshot.copy(
        normalizedX = clampedX,
        normalizedY = clampedY,
        coordinatesLabel = resolvedLabel,
      )
    return snapshot
  }

  fun reset(): DeviceLocationSnapshot {
    snapshot = DeviceLocationSnapshot()
    return snapshot
  }
}
