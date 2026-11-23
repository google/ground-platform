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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_basemap_type_map
import org.openforis.collectone.v2.app.resources.app_basemap_type_satellite
import org.openforis.collectone.v2.app.resources.app_geometry_line
import org.openforis.collectone.v2.app.resources.app_geometry_none
import org.openforis.collectone.v2.app.resources.app_geometry_point
import org.openforis.collectone.v2.app.resources.app_geometry_polygon
import org.openforis.collectone.v2.app.resources.app_imagery_type_earth_engine
import org.openforis.collectone.v2.app.resources.app_imagery_type_xyz_tiles
import org.openforis.collectone.v2.map.GeometryType

// Localized display labels of domain enums shown in the shared UI.

/** Localized display label of this geometry type (e.g. `Polygon`), or `No geometry` for none. */
@Composable
fun GeometryType?.displayLabel(): String =
  stringResource(
    when (this) {
      GeometryType.POINT -> Res.string.app_geometry_point
      GeometryType.LINE_STRING -> Res.string.app_geometry_line
      GeometryType.POLYGON -> Res.string.app_geometry_polygon
      null -> Res.string.app_geometry_none
    }
  )

/** Localized display label of this basemap type (`Map` or `Satellite`). */
@Composable
fun BasemapType.displayLabel(): String =
  stringResource(
    when (this) {
      BasemapType.NORMAL -> Res.string.app_basemap_type_map
      BasemapType.SATELLITE -> Res.string.app_basemap_type_satellite
    }
  )

/** Localized display label of this imagery source type (e.g. `XYZ tile URL`). */
@Composable
fun ImagerySourceType.displayLabel(): String =
  stringResource(
    when (this) {
      ImagerySourceType.XYZ_TILES -> Res.string.app_imagery_type_xyz_tiles
      ImagerySourceType.EARTH_ENGINE -> Res.string.app_imagery_type_earth_engine
    }
  )
