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
package org.groundplatform.v2.devtools.prototypeapp.ui.map

import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityShape
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toLatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/**
 * The geometry the prototype draws for a map feature, built from the vertices [EntityShape]
 * synthesizes around the entity's normalized center, so drawing, camera framing, and the Survey
 * editor all agree on a feature's shape.
 */
internal object EntityGeometry {
  fun isGenerated(entity: GeospatialEntityItem): Boolean = EntityShape.isGenerated(entity)

  /** [entity]'s geometry in the survey at [anchor], or `null` for records without geometry. */
  fun of(entity: GeospatialEntityItem, anchor: SurveyMapAnchor): Geometry? {
    val vertices = EntityShape.vertices(entity, anchor)
    return when (entity.geometryKind) {
      EntityGeometryKind.POLYGON -> Geometry.Polygon(listOf(vertices))
      EntityGeometryKind.LINE -> Geometry.LineString(vertices)
      EntityGeometryKind.POINT -> Geometry.Point(vertices.first())
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
