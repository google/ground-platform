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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind

/**
 * Immutable UI state slice representing the survey map viewport, GNSS location telemetry, camera
 * lock state, and active straight-line wayfinding target.
 */
data class MapViewportUiState(
  val userGpsNormalizedX: Float = 0.50f,
  val userGpsNormalizedY: Float = 0.50f,
  val userGpsCoordinatesLabel: String = "-0.4198°, 36.9512° (±3.2m GPS)",
  val gnssSatelliteCount: Int = 18,
  val gnssAccuracyMeters: Double = 2.1,
  val isCameraFollowingUser: Boolean = true,
  val locationLockState: LocationLockState = LocationLockState.LOCKED,
  val isMap3dMode: Boolean = false,
  val mapBearingDegrees: Float = 0f,
  val mapZoomDelta: Float = 0f,
  val mapPanOffsetX: Float = 0f,
  val mapPanOffsetY: Float = 0f,
  val cameraTargetCommandSeq: Int = 0,
  val cameraTargetLng: Double? = null,
  val cameraTargetLat: Double? = null,
  val cameraTargetZoom: Float? = null,
  val navigationTargetKind: NavigationTargetKind? = null,
  val navigationTargetId: String? = null,
)
