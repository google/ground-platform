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
package org.openforis.collectone.v2.app.data.mapper

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.survey.LayerDef
import org.openforis.collectone.v2.app.domain.model.EntityShape
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType

/**
 * Reads the geometry of rows stored before entities and layers had a canonical geometry
 * (`docs/technical/model/data/05-geometry.md`, "Migration"). Those rows name their geometry kind
 * only in a display label (`"Point"`, `"LineString"`, `"Polygon"`) and place entities by a
 * normalized survey position, which the app drew as a fixed shape around that position.
 *
 * The labels' and positions' fields are reserved now; this reads them with [ReservedFields].
 */
internal object LegacyEntityGeometry {
  /** Reserved field number of `EntityRecord.geometry_type_label`. */
  const val ENTITY_GEOMETRY_TYPE_LABEL = 21

  /** Reserved field numbers of `EntityRecord.normalized_x` and `normalized_y`. */
  const val ENTITY_NORMALIZED_X = 25
  const val ENTITY_NORMALIZED_Y = 26

  /** Reserved field number of `LayerDef.geometry_type_label`. */
  const val LAYER_GEOMETRY_TYPE_LABEL = 8

  /**
   * The shape the app drew for a legacy [record]: its labeled kind at its normalized position in
   * its survey. `null` when the record names no kind (a row without geometry).
   */
  fun of(record: EntityRecord): Geometry? {
    val label = ReservedFields.string(record.unknownFields, ENTITY_GEOMETRY_TYPE_LABEL)
    val type = typeNamed(label) ?: return null
    val anchor = SurveyMapAnchor.forSurvey(record.survey_id)
    return EntityShape.placeholder(
      type = type,
      center =
        anchor.toLatLng(
          (ReservedFields.float(record.unknownFields, ENTITY_NORMALIZED_X) ?: 0f).toDouble(),
          (ReservedFields.float(record.unknownFields, ENTITY_NORMALIZED_Y) ?: 0f).toDouble(),
        ),
    )
  }

  /** The geometry type a legacy [layer] names in its label, if any. */
  fun typeOf(layer: LayerDef): GeometryType? =
    typeNamed(ReservedFields.string(layer.unknownFields, LAYER_GEOMETRY_TYPE_LABEL))

  /** The type named by a legacy geometry label, matched like the app matched it. */
  private fun typeNamed(label: String): GeometryType? =
    when {
      label.contains("polygon", ignoreCase = true) -> GeometryType.POLYGON
      label.contains("line", ignoreCase = true) -> GeometryType.LINE_STRING
      label.contains("point", ignoreCase = true) -> GeometryType.POINT
      else -> null
    }
}
