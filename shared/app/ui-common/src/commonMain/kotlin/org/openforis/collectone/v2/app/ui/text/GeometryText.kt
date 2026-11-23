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

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_geometry_line
import org.openforis.collectone.v2.app.resources.domain_geometry_point
import org.openforis.collectone.v2.app.resources.domain_geometry_polygon
import org.openforis.collectone.v2.map.GeometryType

/** Display name of a geometry type, e.g. `Polygon`; resolve with `stringResource`. */
val GeometryType.labelRes: StringResource
  get() =
    when (this) {
      GeometryType.POINT -> Res.string.domain_geometry_point
      GeometryType.LINE_STRING -> Res.string.domain_geometry_line
      GeometryType.POLYGON -> Res.string.domain_geometry_polygon
    }

/**
 * [entity]'s localized coordinates, e.g. `0.4182°S, 36.9491°E` (see [coordinatesText]), or `null`
 * for a record without geometry.
 */
fun entityCoordinatesText(entity: GeospatialEntityItem): UiText? =
  entity.geometry?.representativePoint?.let(::coordinatesText)
