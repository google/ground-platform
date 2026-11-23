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
package org.openforis.collectone.v2.app.domain.model

import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * The geometry of an XForms geo value as forms save it to an entity's `geometry` (ODK XForms, Data
 * Types: `"lat lng alt acc"` points separated by `;`): one point is a Point, a closed ring of three
 * or more distinct points a Polygon, and other runs of points a LineString. `null` when [text] has
 * no valid point.
 */
fun odkGeometry(text: String): Geometry? {
  val points =
    text
      .split(';')
      .mapNotNull { XPathValue.parseGeoPointString(it) }
      .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
      .map { LatLng(it.latitude, it.longitude) }
  val isClosedRing = points.size >= 4 && points.first() == points.last()
  return when {
    points.isEmpty() -> null
    points.size == 1 -> Geometry.Point(points.single())
    isClosedRing -> Geometry.Polygon(listOf(points.dropLast(1)))
    else -> Geometry.LineString(points)
  }
}
