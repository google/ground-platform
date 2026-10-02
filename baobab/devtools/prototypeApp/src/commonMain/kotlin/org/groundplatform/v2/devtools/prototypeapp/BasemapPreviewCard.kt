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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of the white frame around the [BasemapPreviewCard] thumbnail. */
private val BasemapPreviewFrameWidth = 3.dp

/**
 * Small square card over the map that opens the basemap selector (the layers sheet on mobile, a
 * modal dialog on the web dashboard). Like the Google Maps layers card, its thumbnail previews the
 * basemap the user can switch to: a map thumbnail while satellite imagery is shown, and vice versa.
 */
@Composable
internal fun BasemapPreviewCard(
  selectedBasemapType: BasemapType,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 56.dp,
) {
  val previewType =
    if (selectedBasemapType == BasemapType.SATELLITE) BasemapType.NORMAL else BasemapType.SATELLITE
  val cornerRadius = 12.dp
  Surface(
    onClick = onClick,
    modifier = modifier.size(size).semantics { contentDescription = "Layers" },
    shape = RoundedCornerShape(cornerRadius),
    color = Color.White,
    shadowElevation = 3.dp,
  ) {
    BasemapThumbnail(
      type = previewType,
      modifier =
        Modifier.padding(BasemapPreviewFrameWidth)
          .clip(RoundedCornerShape(cornerRadius - BasemapPreviewFrameWidth))
          .fillMaxSize(),
    )
  }
}

/** Order of the basemap options in the selector: satellite imagery first, as in Google Earth. */
internal val BasemapOptionOrder = listOf(BasemapType.SATELLITE, BasemapType.NORMAL)

/**
 * One option of the basemap selector, modeled on Google Earth's basemap settings: a thumbnail of
 * the basemap and its name. The selected option gets a primary-colored frame around its thumbnail,
 * a primary label, and a light primary tint across the row.
 */
@Composable
internal fun BasemapOptionRow(
  type: BasemapType,
  isSelected: Boolean,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val thumbnailShape = RoundedCornerShape(12.dp)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(
          if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
          } else {
            Color.Transparent
          }
        )
        .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    BasemapThumbnail(
      type = type,
      modifier =
        Modifier.size(52.dp)
          .border(
            width = if (isSelected) 3.dp else 1.dp,
            color =
              if (isSelected) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.outlineVariant
              },
            shape = thumbnailShape,
          )
          .padding(if (isSelected) 3.dp else 1.dp)
          .clip(RoundedCornerShape(if (isSelected) 9.dp else 11.dp)),
    )
    Text(
      text = type.label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
      color =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * Stylized, offline thumbnail of a basemap [type]: a bay, a park, and a few roads, drawn in the
 * colors of the street map or of satellite imagery.
 */
@Composable
internal fun BasemapThumbnail(type: BasemapType, modifier: Modifier = Modifier) {
  val palette = if (type == BasemapType.SATELLITE) SatellitePalette else StreetPalette
  Canvas(modifier = modifier) {
    drawRect(color = palette.land)
    drawPath(path = parkPath(), color = palette.park)
    drawPath(path = waterPath(), color = palette.water)
    drawRoads(palette)
  }
}

/** Colors of a [BasemapThumbnail]. */
private data class BasemapThumbnailPalette(
  val land: Color,
  val park: Color,
  val water: Color,
  val road: Color,
  val roadCasing: Color,
  val highway: Color,
)

private val StreetPalette =
  BasemapThumbnailPalette(
    land = Color(0xFFF1F3EE),
    park = Color(0xFFC6E8C4),
    water = Color(0xFF9CD8F0),
    road = Color.White,
    roadCasing = Color(0xFFD5D8DC),
    highway = Color(0xFF8AA4C8),
  )

private val SatellitePalette =
  BasemapThumbnailPalette(
    land = Color(0xFF55603F),
    park = Color(0xFF2F4A2A),
    water = Color(0xFF16304D),
    road = Color(0xFFB9AE8E),
    roadCasing = Color(0x66000000),
    highway = Color(0xFFD9CFB0),
  )

/** Path in coordinates relative to the canvas size, from points given as fractions of it. */
private fun DrawScope.relativePath(vararg points: Pair<Float, Float>): Path =
  Path().apply {
    points.forEachIndexed { index, (x, y) ->
      if (index == 0) {
        moveTo(x * size.width, y * size.height)
      } else {
        lineTo(x * size.width, y * size.height)
      }
    }
    close()
  }

/** Bay on the left edge, with a curved shoreline. */
private fun DrawScope.waterPath(): Path {
  val w = size.width
  val h = size.height
  return Path().apply {
    moveTo(0f, 0.30f * h)
    cubicTo(0.30f * w, 0.32f * h, 0.38f * w, 0.55f * h, 0.30f * w, 0.70f * h)
    cubicTo(0.24f * w, 0.82f * h, 0.36f * w, 0.92f * h, 0.42f * w, h)
    lineTo(0f, h)
    close()
  }
}

/** Park along the shore, upper left. */
private fun DrawScope.parkPath(): Path =
  relativePath(
    0f to 0f,
    0.55f to 0f,
    0.62f to 0.30f,
    0.48f to 0.62f,
    0.38f to 0.85f,
    0f to 0.85f,
  )

/** A diagonal highway and a small grid of streets on the right. */
private fun DrawScope.drawRoads(palette: BasemapThumbnailPalette) {
  val w = size.width
  val h = size.height
  val streetWidth = 0.045f * w
  val casingWidth = streetWidth + 0.02f * w
  val streets =
    listOf(
      Offset(0.62f * w, 0.42f * h) to Offset(w, 0.36f * h),
      Offset(0.56f * w, 0.66f * h) to Offset(w, 0.60f * h),
      Offset(0.72f * w, 0.20f * h) to Offset(0.80f * w, h),
      Offset(0.50f * w, 0.88f * h) to Offset(w, 0.84f * h),
    )
  streets.forEach { (start, end) ->
    drawLine(palette.roadCasing, start, end, strokeWidth = casingWidth, cap = StrokeCap.Round)
  }
  streets.forEach { (start, end) ->
    drawLine(palette.road, start, end, strokeWidth = streetWidth, cap = StrokeCap.Round)
  }
  drawLine(
    color = palette.highway,
    start = Offset(0.66f * w, 0f),
    end = Offset(w, 0.30f * h),
    strokeWidth = 0.06f * w,
    cap = StrokeCap.Round,
  )
}
