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
package org.groundplatform.v2.map

import androidx.compose.runtime.Immutable

/** User interactions and camera changes reported by a [GroundMap]. */
@Immutable
sealed interface MapEvent {
  /** A feature drawn by layer [layerId] was tapped at [at]; the topmost hit wins. */
  data class FeatureTapped(val layerId: String, val featureId: String, val at: LatLng) : MapEvent

  /** A [MapMarker] was tapped. */
  data class MarkerTapped(val markerId: String) : MapEvent

  /** The map was tapped where no feature or marker was hit. */
  data class BackgroundTapped(val at: LatLng) : MapEvent

  /** The camera stopped moving, after a gesture ([byGesture]) or an app-driven move. */
  data class CameraIdle(val camera: CameraPosition, val byGesture: Boolean) : MapEvent
}
