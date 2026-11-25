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
package org.openforis.collectone.v2.app.domain.repository

import org.openforis.collectone.v2.map.LatLng

/** Domain snapshot of the collector's device GNSS location and satellite telemetry. */
data class DeviceLocationSnapshot(
  val normalizedX: Float = 0.50f,
  val normalizedY: Float = 0.50f,
  val coordinatesLabel: String = "",
  val gnssAccuracyMeters: Double? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val isKnown: Boolean = false,
) {
  /** True once a real GNSS fix or explicit simulated GPS position has been recorded. */
  val hasLocation: Boolean
    get() = isKnown || (latitude != null && longitude != null)

  /** Real device coordinates when reported by a live location source, or `null`. */
  fun latLngOrNull(): LatLng? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return LatLng(lat, lon)
  }
}

/**
 * Domain repository contract for reading and updating device GNSS location and satellite accuracy.
 */
interface LocationRepository {
  fun getLocationSnapshot(): DeviceLocationSnapshot

  fun updateGpsLocation(
    normalizedX: Float,
    normalizedY: Float,
    coordinatesLabel: String? = null,
  ): DeviceLocationSnapshot

  fun updateDeviceLocation(
    normalizedX: Float,
    normalizedY: Float,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double?,
    coordinatesLabel: String,
  ): DeviceLocationSnapshot = updateGpsLocation(normalizedX, normalizedY, coordinatesLabel)

  fun resetToDefaults(): DeviceLocationSnapshot
}
