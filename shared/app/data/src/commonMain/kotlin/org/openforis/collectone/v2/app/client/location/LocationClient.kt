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
package org.openforis.collectone.v2.app.client.location

import org.openforis.collectone.v2.app.domain.repository.DeviceLocationSnapshot

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
    snapshot =
      snapshot.copy(
        normalizedX = clampedX,
        normalizedY = clampedY,
        coordinatesLabel = coordinatesLabel ?: snapshot.coordinatesLabel,
        gnssAccuracyMeters = null,
        latitude = null,
        longitude = null,
        isKnown = true,
      )
    return snapshot
  }

  fun updateRealDeviceLocation(
    normalizedX: Float,
    normalizedY: Float,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double?,
    coordinatesLabel: String,
  ): DeviceLocationSnapshot {
    snapshot =
      snapshot.copy(
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        coordinatesLabel = coordinatesLabel,
        gnssAccuracyMeters = accuracyMeters,
        latitude = latitude,
        longitude = longitude,
        isKnown = true,
      )
    return snapshot
  }

  fun reset(): DeviceLocationSnapshot {
    snapshot = DeviceLocationSnapshot()
    return snapshot
  }
}
