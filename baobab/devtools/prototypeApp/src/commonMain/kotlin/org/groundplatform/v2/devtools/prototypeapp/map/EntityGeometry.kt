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
package org.groundplatform.v2.devtools.prototypeapp.map

import org.groundplatform.v2.devtools.prototypeapp.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.geometryKind
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/**
 * The geometry the prototype draws for a map feature. Sample entities only carry a normalized
 * center, so lines and polygons are synthesized around it with fixed shapes; this is the single
 * definition that both drawing and camera framing use.
 */
internal object EntityGeometry {
  /** Prefix of the bulk-generated sample entities, drawn smaller and without labels. */
  private const val GENERATED_ID_PREFIX = "entity-rnd-"

  fun isGenerated(entity: GeospatialEntityItem): Boolean = entity.id.startsWith(GENERATED_ID_PREFIX)

  /** [entity]'s geometry in the survey at [anchor], or `null` for records without geometry. */
  fun of(entity: GeospatialEntityItem, anchor: SurveyMapAnchor): Geometry? {
    val cx = entity.normalizedX.toDouble()
    val cy = entity.normalizedY.toDouble()
    fun at(dx: Double, dy: Double): LatLng = anchor.toLatLng(cx + dx, cy + dy)
    return when (entity.geometryKind) {
      EntityGeometryKind.POLYGON -> {
        val (w, h) = if (isGenerated(entity)) 0.022 to 0.015 else 0.22 to 0.11
        Geometry.Polygon(
          listOf(
            listOf(
              at(-w * 0.48, -h * 0.42),
              at(w * 0.45, -h * 0.5),
              at(w * 0.52, h * 0.38),
              at(-w * 0.4, h * 0.48),
            )
          )
        )
      }
      EntityGeometryKind.LINE -> {
        val w = 0.24
        val h = 0.12
        Geometry.LineString(
          listOf(
            at(-w * 0.52, h * 0.44),
            at(-w * 0.16, -h * 0.18),
            at(w * 0.18, h * 0.22),
            at(w * 0.52, -h * 0.42),
          )
        )
      }
      EntityGeometryKind.POINT -> Geometry.Point(at(0.0, 0.0))
      EntityGeometryKind.NONE -> null
    }
  }

  /** The vertices drawn as handles on lines and polygons; empty for points. */
  fun vertices(geometry: Geometry): List<LatLng> =
    when (geometry) {
      is Geometry.Point -> emptyList()
      is Geometry.LineString -> geometry.points
      is Geometry.Polygon -> geometry.rings.first()
    }

  /** Bounds of [entity]'s geometry; a zero-size box at its center when it has none. */
  fun bounds(entity: GeospatialEntityItem, anchor: SurveyMapAnchor): LngLatBounds {
    val geometry =
      of(entity, anchor)
        ?: Geometry.Point(
          anchor.toLatLng(entity.normalizedX.toDouble(), entity.normalizedY.toDouble())
        )
    return geometry.bounds()
  }
}
