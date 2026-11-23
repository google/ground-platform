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
package org.openforis.collectone.v2.app.ui.map

import kotlin.math.hypot
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * How the map draws, frames, and labels a map feature from its stored geometry
 * ([GeospatialEntityItem.geometry]), with every part of a multi-part geometry.
 */
object EntityGeometry {
  /**
   * The vertices drawn as handles on lines and polygons, of every part (a polygon's exterior ring);
   * empty for points.
   */
  fun vertices(geometry: Geometry): List<LatLng> =
    geometry.parts.flatMap { part ->
      when (part) {
        is Geometry.LineString -> part.points
        is Geometry.Polygon -> part.rings.first()
        else -> emptyList()
      }
    }

  /** Bounds of [entity]'s geometry, or `null` for a record without one. */
  fun bounds(entity: GeospatialEntityItem): LngLatBounds? = entity.geometry?.bounds()

  /**
   * Where the name of [geometry] sits: halfway along a line, or at a point's or polygon's
   * representative point; for a multi-part geometry, on its first part.
   */
  fun labelPosition(geometry: Geometry): LatLng {
    val line = geometry.parts.first() as? Geometry.LineString ?: return geometry.representativePoint
    return halfwayAlong(line.points)
  }

  /** The point halfway along the length of [points], measured in degrees. */
  private fun halfwayAlong(points: List<LatLng>): LatLng {
    val segments = points.zipWithNext()
    var remaining = segments.sumOf { (a, b) -> degreesBetween(a, b) } / 2
    for ((a, b) in segments) {
      val length = degreesBetween(a, b)
      if (remaining <= length && length > 0) {
        val t = remaining / length
        return LatLng(
          a.latitude + (b.latitude - a.latitude) * t,
          a.longitude + (b.longitude - a.longitude) * t,
        )
      }
      remaining -= length
    }
    return points.last()
  }

  private fun degreesBetween(a: LatLng, b: LatLng): Double =
    hypot(b.latitude - a.latitude, b.longitude - a.longitude)
}
