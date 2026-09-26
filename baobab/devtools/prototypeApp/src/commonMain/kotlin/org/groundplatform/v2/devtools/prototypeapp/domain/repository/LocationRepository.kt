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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

/**
 * Domain snapshot of the collector's device GNSS location and satellite telemetry.
 */
data class DeviceLocationSnapshot(
  val normalizedX: Float = 0.50f,
  val normalizedY: Float = 0.50f,
  val coordinatesLabel: String = "-0.4198°, 36.9512° (±3.2m GPS)",
  val gnssSatelliteCount: Int = 18,
  val gnssAccuracyMeters: Double = 2.1,
)

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

  fun resetToDefaults(): DeviceLocationSnapshot
}
