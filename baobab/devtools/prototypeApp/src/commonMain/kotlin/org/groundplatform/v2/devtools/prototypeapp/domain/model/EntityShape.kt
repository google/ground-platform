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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toLatLng
import org.groundplatform.v2.map.LatLng

/** Shape of a map feature's geometry. */
enum class EntityShapeKind {
  POINT,
  LINE,
  POLYGON;

  companion object {
    /** Shape named by a [GeospatialEntityItem.geometryTypeLabel], or `null` for no geometry. */
    fun of(geometryTypeLabel: String): EntityShapeKind? =
      when {
        geometryTypeLabel.contains("polygon", ignoreCase = true) -> POLYGON
        geometryTypeLabel.contains("line", ignoreCase = true) -> LINE
        geometryTypeLabel.contains("point", ignoreCase = true) -> POINT
        else -> null
      }
  }
}

/**
 * The vertices the prototype draws for a map feature. Sample entities only carry a normalized
 * center, so lines and polygons are synthesized around it with fixed shapes. This is the single
 * definition used by map drawing, camera framing, and the Survey editor's Map layer rows.
 */
object EntityShape {
  /** Prefix of the bulk-generated sample entities, drawn smaller and without labels. */
  const val GENERATED_ID_PREFIX = "entity-rnd-"

  fun isGenerated(entity: GeospatialEntityItem): Boolean = entity.id.startsWith(GENERATED_ID_PREFIX)

  /**
   * Vertices of [entity]'s shape in the survey at [anchor]: one point for points, an open ring for
   * polygons, and an empty list for records without geometry.
   */
  fun vertices(entity: GeospatialEntityItem, anchor: SurveyMapAnchor): List<LatLng> =
    vertices(
      kind = EntityShapeKind.of(entity.geometryTypeLabel) ?: return emptyList(),
      normalizedX = entity.normalizedX.toDouble(),
      normalizedY = entity.normalizedY.toDouble(),
      anchor = anchor,
      small = isGenerated(entity),
    )

  /** Vertices of a [kind] shape centered on normalized survey coordinates. */
  fun vertices(
    kind: EntityShapeKind,
    normalizedX: Double,
    normalizedY: Double,
    anchor: SurveyMapAnchor,
    small: Boolean = false,
  ): List<LatLng> {
    fun at(dx: Double, dy: Double): LatLng = anchor.toLatLng(normalizedX + dx, normalizedY + dy)
    return when (kind) {
      EntityShapeKind.POLYGON -> {
        val (w, h) = if (small) 0.022 to 0.015 else 0.22 to 0.11
        listOf(
          at(-w * 0.48, -h * 0.42),
          at(w * 0.45, -h * 0.5),
          at(w * 0.52, h * 0.38),
          at(-w * 0.4, h * 0.48),
        )
      }
      EntityShapeKind.LINE -> {
        val w = 0.24
        val h = 0.12
        listOf(
          at(-w * 0.52, h * 0.44),
          at(-w * 0.16, -h * 0.18),
          at(w * 0.18, h * 0.22),
          at(w * 0.52, -h * 0.42),
        )
      }
      EntityShapeKind.POINT -> listOf(at(0.0, 0.0))
    }
  }
}
