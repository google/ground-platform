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

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import openforis.collectone.v1.forms.GeoPoint

/** A location fix reported by the device's positioning hardware. */
data class GeoLocationFix(
  val latitude: Double,
  val longitude: Double,
  val altitudeMeters: Double? = null,
  /** Horizontal accuracy radius in meters, when the platform reports one. */
  val accuracyMeters: Double? = null,
)

/**
 * Host-platform bridge that reports the collector's current location to GeoPoint, GeoTrace, and
 * GeoShape questions.
 *
 * Shared UI stays free of platform APIs; each host app supplies an implementation via
 * [LocalGeoLocationSource].
 */
interface GeoLocationSource {
  /** The latest fix, or `null` while no location is available (no permission, no signal, ...). */
  val currentFix: StateFlow<GeoLocationFix?>
}

/** A [GeoLocationSource] that never has a fix, used when the host provides no location. */
object NoGeoLocationSource : GeoLocationSource {
  override val currentFix: StateFlow<GeoLocationFix?> = MutableStateFlow(null).asStateFlow()
}

/**
 * Host-provided [GeoLocationSource] used by location questions. Defaults to [NoGeoLocationSource],
 * so location questions wait for a real fix instead of recording made-up coordinates.
 */
val LocalGeoLocationSource: ProvidableCompositionLocal<GeoLocationSource> =
  staticCompositionLocalOf {
    NoGeoLocationSource
  }

/**
 * The point to record when the collector captures a location: [fix] shifted by the question's map
 * pan offset (zero when the map follows GPS), with the fix's altitude and accuracy. Returns `null`
 * while there is no [fix], since there is nothing real to record.
 */
internal fun geoCapturePoint(
  fix: GeoLocationFix?,
  panOffsetLat: Double = 0.0,
  panOffsetLon: Double = 0.0,
): GeoPoint? = fix?.let {
  GeoPoint(
    latitude = roundGeoCoord6(it.latitude + panOffsetLat),
    longitude = roundGeoCoord6(it.longitude + panOffsetLon),
    altitude_meters = it.altitudeMeters,
    accuracy_meters = it.accuracyMeters,
  )
}
