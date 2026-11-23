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

import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * The fixed shapes the app gives a map feature that has a type but no vertices of its own: rows
 * stored before entities had a geometry, features created from a form without a geometry answer,
 * and Map layer rows without vertices. Lines and polygons are synthesized around a center.
 */
object EntityShape {
  /** The [type] shape centered on [center], as a geometry. */
  fun placeholder(type: GeometryType, center: LatLng): Geometry {
    val vertices = vertices(type, center)
    return when (type) {
      GeometryType.POINT -> Geometry.Point(vertices.single())
      GeometryType.LINE_STRING -> Geometry.LineString(vertices)
      GeometryType.POLYGON -> Geometry.Polygon(listOf(vertices))
    }
  }

  /**
   * Vertices of a [type] shape centered on [center]. Offsets are fractions of the sample survey
   * span ([SurveyMapAnchor.SPAN_LONGITUDE] by [SurveyMapAnchor.SPAN_LATITUDE]), `y` down.
   */
  fun vertices(type: GeometryType, center: LatLng): List<LatLng> {
    fun at(dx: Double, dy: Double): LatLng =
      LatLng(
        latitude = center.latitude - dy * SurveyMapAnchor.SPAN_LATITUDE,
        longitude = center.longitude + dx * SurveyMapAnchor.SPAN_LONGITUDE,
      )
    return when (type) {
      GeometryType.POLYGON -> {
        val w = 0.22
        val h = 0.11
        listOf(
          at(-w * 0.48, -h * 0.42),
          at(w * 0.45, -h * 0.5),
          at(w * 0.52, h * 0.38),
          at(-w * 0.4, h * 0.48),
        )
      }
      GeometryType.LINE_STRING -> {
        val w = 0.24
        val h = 0.12
        listOf(
          at(-w * 0.52, h * 0.44),
          at(-w * 0.16, -h * 0.18),
          at(w * 0.18, h * 0.22),
          at(w * 0.52, -h * 0.42),
        )
      }
      GeometryType.POINT -> listOf(center)
    }
  }
}
