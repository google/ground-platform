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
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.model.CoordinateDegrees
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_coordinates
import org.openforis.collectone.v2.app.resources.app_coordinates_latitude_north
import org.openforis.collectone.v2.app.resources.app_coordinates_latitude_south
import org.openforis.collectone.v2.app.resources.app_coordinates_longitude_east
import org.openforis.collectone.v2.app.resources.app_coordinates_longitude_west
import org.openforis.collectone.v2.map.LatLng

/**
 * Localized coordinates of [position], e.g. `0.4160°S, 36.9470°E`: each value to
 * [CoordinateDegrees.DECIMALS] places with its hemisphere in the user's language. Use it for every
 * coordinates text people read; `plainCoordinatesLabel` is only for locale-neutral uses (search).
 */
fun coordinatesText(position: LatLng): UiText {
  val lat = CoordinateDegrees.of(position.latitude)
  val lng = CoordinateDegrees.of(position.longitude)
  val latitudeRes =
    if (lat.isNegative) Res.string.app_coordinates_latitude_south
    else Res.string.app_coordinates_latitude_north
  val longitudeRes =
    if (lng.isNegative) Res.string.app_coordinates_longitude_west
    else Res.string.app_coordinates_longitude_east
  return Res.string.app_coordinates.toUiText(
    latitudeRes.toUiText(lat.magnitude),
    longitudeRes.toUiText(lng.magnitude),
  )
}
