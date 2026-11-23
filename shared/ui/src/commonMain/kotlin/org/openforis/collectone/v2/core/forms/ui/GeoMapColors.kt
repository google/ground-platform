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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.ui.graphics.Color

/**
 * Fixed dark palette for the built-in geo question maps ([GeoPointInputWidget] and
 * [GeoGeometryDrawingWidget]). The map is always dark, whatever the app theme, so these colors do
 * not follow the Material color scheme.
 */
internal object GeoMapColors {
  /** Map canvas background. */
  val Background = Color(0xFF10261C)
  /** Field parcels painted on the placeholder basemap. */
  val FieldParcelLight = Color(0xFF173828)
  val FieldParcelLighter = Color(0xFF1C422F)
  val FieldParcelDark = Color(0xFF153324)
  /** Stream drawn across the placeholder basemap. */
  val Stream = Color(0xFF4FC3F7)

  /** Map frame and overlay chip borders. */
  val Border = Color(0xFF2D5944)
  /** Background of the status and zoom chips drawn over the map. */
  val OverlaySurface = Color(0xFF0A1F16)
  /** Background of the "recenter on GPS" pill. */
  val RecenterSurface = Color(0xFF0E2219)
  /** Content of the recenter pill and the reticle while the map follows GPS. */
  val Accent = Color(0xFF8BD6B1)
  /** Reticle and pending segment while the map is panned away from GPS. */
  val PannedAccent = Color(0xFFFFD54F)
  /** Dark outline under the reticle and vertices. */
  val Shadow = Color(0xFF091812)
  /** Reticle center dot while the map follows GPS. */
  val CenterDot = Color(0xFF00E676)
  /** Reticle center dot while the map is panned away from GPS. */
  val PannedCenterDot = Color(0xFFFFB300)
  /** Status chip text while the map follows GPS. */
  val StatusText = AppFixedColors.primaryFixed
  /** Status chip text while panned or walking. */
  val ActiveStatusText = Color(0xFFFFE082)

  /** Saved geopoint marker and its halo. */
  val SavedPoint = Color(0xFF00C853)
  val SavedPointHalo = Color(0xFF00E676)

  /** Collector's live GPS position: accuracy halo, ring and dot. */
  val GpsAccuracyHalo = Color(0xFF42A5F5)
  val GpsAccuracyRing = Color(0xFF64B5F6)
  val GpsDot = Color(0xFF1E88E5)

  /** Geotrace line and geoshape polygon. */
  val TraceLine = Color(0xFF29B6F6)
  val PolygonFill = Color(0xFF4CAF50)
  val PolygonOutline = Color(0xFF81C784)
  /** Vertex markers: the first, the last, and every one in between. */
  val FirstVertex = Color(0xFF00C853)
  val LastVertex = Color(0xFFFF9100)
  val Vertex = Color(0xFF0288D1)
}
