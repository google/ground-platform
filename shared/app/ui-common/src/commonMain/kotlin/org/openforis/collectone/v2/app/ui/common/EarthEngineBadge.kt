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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_powered_by_google_earth_engine
import org.openforis.collectone.v2.core.forms.ui.controls.Surface

/** Test tag on the floating "Powered by Google Earth Engine" badge overlaid on the map. */
const val EARTH_ENGINE_MAP_BADGE_TAG = "earth_engine_map_badge"

/** Test tag on the "Powered by Google Earth Engine" badge inside the Time series panel. */
const val EARTH_ENGINE_PANEL_BADGE_TAG = "earth_engine_panel_badge"

/**
 * The 2026 Google Earth Engine product logo (isometric rounded hexagon cube framing a wavy blue and
 * periwinkle globe), drawn as a vector so it stays sharp at any DPI without raster assets across
 * web, desktop, and mobile.
 */
val EarthEngineLogo: ImageVector by lazy {
  ImageVector.Builder(
      name = "GoogleEarthEngine2026",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 192f,
      viewportHeight = 192f,
    )
    .apply {
      // Cube bottom face (#BBE2FF)
      addPath(
        fill = SolidColor(Color(0xFFBBE2FF)),
        pathData =
          addPathNodes(
            "M 18.91 140.25 c 3.17 5.45 7.75 10.09 13.42 13.35 l 45.33 26.02 " +
              "c 11.35 6.51 25.32 6.51 36.67 0 l 45.33 -26.02 " +
              "c 5.67 -3.26 10.26 -7.89 13.42 -13.35 l -77.09 -44.25 -77.09 44.25 Z"
          ),
      )
      // Cube right face (#76BBFF)
      addPath(
        fill = SolidColor(Color(0xFF76BBFF)),
        pathData =
          addPathNodes(
            "M 173.09 140.25 c 3.17 -5.45 4.91 -11.72 4.91 -18.23 v -52.04 " +
              "c 0 -13.03 -6.99 -25.06 -18.34 -31.58 l -45.33 -26.02 " +
              "c -5.67 -3.26 -12.01 -4.88 -18.34 -4.88 v 88.5 l 77.09 44.25 Z"
          ),
      )
      // Cube left face (#9DD2FF)
      addPath(
        fill = SolidColor(Color(0xFF9DD2FF)),
        pathData =
          addPathNodes(
            "M 32.34 38.4 c -11.35 6.51 -18.34 18.55 -18.34 31.58 v 52.04 " +
              "c 0 6.51 1.75 12.78 4.91 18.23 l 77.09 -44.25 v -88.5 " +
              "c -6.33 0 -12.66 1.63 -18.33 4.88 l -45.33 26.02 Z"
          ),
      )
      // Globe band 4 — bottom deep blue wave (#2051DA)
      addPath(
        fill = SolidColor(Color(0xFF2051DA)),
        pathData =
          addPathNodes(
            "M 61.23 110.56 c 8.75 0 13.43 5.85 18.44 13.55 " +
              "5 7.7 13.28 20.14 29.82 25.02 -2.82 0.71 -5.73 1.21 -8.71 1.47 " +
              "-1.57 0.14 -3.16 0.21 -4.77 0.21 -22.33 0 -41.54 -13.34 -50.09 -32.48 " +
              "3.45 -4.71 9.03 -7.77 15.32 -7.77 Z"
          ),
      )
      // Globe band 3 — middle blue-to-periwinkle gradient wave
      addPath(
        fill =
          Brush.linearGradient(
            0.06f to Color(0xFF3186FF),
            0.20f to Color(0xFF5A92FF),
            0.59f to Color(0xFFA9A8FF),
            1.0f to Color(0xFFA9A8FF),
            start = Offset(116.13f, 109.36f),
            end = Offset(5.24f, 109.36f),
          ),
        pathData =
          addPathNodes(
            "M 63.8 69.59 c 31.83 0 31.83 52.83 63.67 52.83 " +
              "9.13 0 15.64 -4.35 21.04 -10.55 -5.53 18.3 -20.36 32.56 -39.01 37.27 " +
              "-0.3 -0.09 -0.6 -0.18 -0.9 -0.28 -15.95 -5.06 -24.02 -17.19 -28.93 -24.75 " +
              "-5 -7.7 -9.68 -13.55 -18.44 -13.55 -6.29 0 -11.86 3.06 -15.32 7.77 " +
              "-3.05 -6.82 -4.75 -14.38 -4.75 -22.33 0 -5.98 0.96 -11.73 2.73 -17.12 " +
              "5.17 -5.52 11.4 -9.29 19.91 -9.29 Z"
          ),
      )
      // Globe band 2 — upper-middle sky-blue gradient wave
      addPath(
        fill =
          Brush.linearGradient(
            0.0f to Color(0xFF76BBFF),
            1.0f to Color(0xFF4FA0FF),
            start = Offset(87.23f, 82.67f),
            end = Offset(155.77f, 82.67f),
          ),
        pathData =
          addPathNodes(
            "M 63.8 69.58 c 31.83 0 31.83 52.83 63.67 52.83 " +
              "9.13 0 15.64 -4.35 21.04 -10.55 1.52 -5.02 2.33 -10.34 2.33 -15.86 " +
              "0 -10.17 -2.77 -19.69 -7.6 -27.85 -3.89 5.96 -8.38 10.94 -15.86 10.94 " +
              "-15.91 0 -16.4 -27.48 -45.18 -36.16 -18.07 4.68 -32.52 18.34 -38.31 35.94 " +
              "5.17 -5.52 11.4 -9.29 19.91 -9.29 Z"
          ),
      )
      // Globe band 1 — top light-blue cap (#BBE2FF)
      addPath(
        fill = SolidColor(Color(0xFFBBE2FF)),
        pathData =
          addPathNodes(
            "M 127.38 79.09 c 7.48 0 11.97 -4.97 15.86 -10.94 " +
              "-9.54 -16.14 -27.13 -26.97 -47.24 -26.97 -4.77 0 -9.39 0.61 -13.8 1.75 " +
              "28.78 8.66 29.27 36.16 45.18 36.16 Z"
          ),
      )
    }
    .build()
}

/** The 2026 Google Earth Engine logo at [size]. */
@Composable
fun EarthEngineLogoIcon(size: Dp = 16.dp, modifier: Modifier = Modifier) {
  Image(imageVector = EarthEngineLogo, contentDescription = null, modifier = modifier.size(size))
}

/**
 * Attribution pill displaying the 2026 Google Earth Engine logo and localized *"Powered by Google
 * Earth Engine"* label.
 *
 * When [isMapOverlay] is `true`, uses an opaque elevated pill so it stays legible over both map and
 * satellite/Earth Engine raster tiles (and avoids Skiko/Wasm translucent drop-shadow artifacts).
 * When `false`, renders a flat tonal badge suited to panels such as the Time series panel.
 */
@Composable
fun PoweredByEarthEngineBadge(modifier: Modifier = Modifier, isMapOverlay: Boolean = false) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color =
      if (isMapOverlay) MaterialTheme.colorScheme.surfaceContainerLow
      else MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation = if (isMapOverlay) 3.dp else 0.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.padding(
          horizontal = if (isMapOverlay) 10.dp else 8.dp,
          vertical = if (isMapOverlay) 4.dp else 3.dp,
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      EarthEngineLogoIcon(size = if (isMapOverlay) 16.dp else 15.dp)
      Text(
        text = stringResource(Res.string.app_powered_by_google_earth_engine),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color =
          if (isMapOverlay) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}
