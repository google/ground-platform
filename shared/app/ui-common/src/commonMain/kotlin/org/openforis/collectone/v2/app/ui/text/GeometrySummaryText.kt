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
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_entity_geometry_kind_none
import org.openforis.collectone.v2.app.resources.domain_geometry_summary_line
import org.openforis.collectone.v2.app.resources.domain_geometry_summary_point
import org.openforis.collectone.v2.app.resources.domain_geometry_summary_polygon
import org.openforis.collectone.v2.map.GeometryType

/** Localized one-line summary of [entity]'s geometry, e.g. `Line • 120 m`. */
fun entityGeometrySummaryText(
  entity: GeospatialEntityItem,
  unitSystem: MeasurementUnitSystem,
): UiText {
  val length = unitSystem.length(entity.perimeterMeters).toUiText()
  return when (entity.geometryType) {
    GeometryType.POLYGON ->
      Res.string.domain_geometry_summary_polygon.toUiText(
        unitSystem.area(entity.areaHectares).toUiText(),
        length,
      )
    GeometryType.LINE_STRING -> Res.string.domain_geometry_summary_line.toUiText(length)
    GeometryType.POINT ->
      Res.string.domain_geometry_summary_point.toUiText(entityCoordinatesText(entity) ?: "")
    null -> Res.string.domain_entity_geometry_kind_none.toUiText()
  }
}
