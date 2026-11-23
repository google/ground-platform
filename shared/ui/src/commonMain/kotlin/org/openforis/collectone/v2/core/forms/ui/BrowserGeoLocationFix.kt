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

/**
 * The fix described by a browser `GeolocationCoordinates` reading, or `null` when its position
 * isn't a real coordinate.
 *
 * Browsers report a missing altitude (or accuracy) as `null`, which crosses into Kotlin as
 * [Double.NaN]; such readings, and negative ones, map to an unknown altitude or accuracy.
 */
internal fun browserGeoLocationFix(
  latitude: Double,
  longitude: Double,
  altitudeMeters: Double,
  accuracyMeters: Double,
): GeoLocationFix? {
  if (!latitude.isFinite() || !longitude.isFinite()) return null
  return GeoLocationFix(
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitudeMeters.takeIf { it.isFinite() },
    accuracyMeters = accuracyMeters.takeIf { it.isFinite() && it >= 0.0 },
  )
}
