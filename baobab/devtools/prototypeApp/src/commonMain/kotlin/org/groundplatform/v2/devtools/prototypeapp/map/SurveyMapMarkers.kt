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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.devtools.prototypeapp.EntityGeometryKind

private val MapInk = Color(0xFF0E2219)
private val MapMint = Color(0xFF8BD6B1)

/** The Compose UI for a [SurveyMarker]; positioned on the map by `GroundMap`. */
@Composable
internal fun SurveyMarkerView(marker: SurveyMarker) {
  when (marker) {
    is SurveyMarker.GeometryPill -> GeometryPill(marker)
    is SurveyMarker.PinLabel -> PinLabel(marker)
    is SurveyMarker.ClusterBalloon -> ClusterBalloon(marker)
    is SurveyMarker.UserChip ->
      Chip(
        text = marker.text,
        background = Color(0xEB1A73E8),
        border = Color.White,
        borderWidth = 1.dp,
        textColor = Color.White,
        fontSize = 8.5.sp,
        radius = 10.dp,
        padding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
      )
    is SurveyMarker.NavigationPill ->
      Chip(
        text = marker.text,
        background = Color(0xEB071812),
        border = Color(0xFF00E5FF),
        borderWidth = 1.5.dp,
        textColor = Color(0xFFE0F7FA),
        fontSize = 9.sp,
        radius = 12.dp,
        padding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
      )
    is SurveyMarker.Place -> PlaceChip(marker)
  }
}

@Composable
private fun GeometryPill(pill: SurveyMarker.GeometryPill) {
  val onLayerColor = Color(contentColorOnArgb(pill.color.toArgbLong()))
  val prefix =
    when (pill.kind) {
      EntityGeometryKind.POLYGON -> "▱ "
      EntityGeometryKind.LINE -> "╱ "
      else -> "● "
    }
  Row(
    Modifier.layerChipSurface(pill.color, onLayerColor, pill.selected, pill.pending, 16.dp, 3.dp)
      .padding(
        start = 8.dp,
        top = 3.dp,
        end = if (pill.status != null) 4.dp else 8.dp,
        bottom = 3.dp,
      ),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MarkerText(prefix + pill.label, onLayerColor, 10.sp, FontWeight.Bold)
    pill.status?.let { StatusBadge(it, outline = onLayerColor) }
  }
}

@Composable
private fun PinLabel(label: SurveyMarker.PinLabel) {
  val onLayerColor = Color(contentColorOnArgb(label.color.toArgbLong()))
  Row(
    Modifier.widthIn(max = 160.dp)
      .layerChipSurface(label.color, onLayerColor, label.selected, label.pending, 8.dp, 1.dp)
      .padding(
        start = 8.dp,
        top = 2.dp,
        end = if (label.status != null) 3.dp else 8.dp,
        bottom = 2.dp,
      ),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.weight(1f, fill = false)) {
      MarkerText(
        label.label,
        onLayerColor,
        11.sp,
        if (label.selected) FontWeight.Bold else FontWeight.Medium,
      )
    }
    label.status?.let { StatusBadge(it, outline = onLayerColor) }
  }
}

/**
 * Surface of a feature's on-map chip: filled with its layer [color]. Selected chips get a thicker
 * border in the chip's [onColor]; pending (unsynced) chips a dashed one; others a white rim.
 */
private fun Modifier.layerChipSurface(
  color: Color,
  onColor: Color,
  selected: Boolean,
  pending: Boolean,
  radius: Dp,
  elevation: Dp,
): Modifier =
  markerSurface(
    background = color,
    border = if (selected || pending) onColor else Color.White,
    borderWidth = if (selected) 2.dp else 1.dp,
    radius = radius,
    dashed = pending,
    elevation = if (selected) elevation + 2.dp else elevation,
  )

/**
 * A feature's workflow status symbol on a disc of its status color, with a thin [outline] (the
 * chip's on-color) so it stays visible when the status and layer colors are similar.
 */
@Composable
private fun StatusBadge(badge: SurveyMarker.StatusBadge, outline: Color) {
  Box(
    Modifier.size(16.dp).background(badge.color, CircleShape).border(1.dp, outline, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    MarkerText(
      badge.symbol,
      Color(contentColorOnArgb(badge.color.toArgbLong())),
      9.sp,
      FontWeight.ExtraBold,
    )
  }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun ClusterBalloon(balloon: SurveyMarker.ClusterBalloon) {
  val selected = balloon.selected
  val accent = if (selected) Color(0xFF00E676) else MapMint
  val textColor = if (selected) MapInk else Color.White
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Column(
      Modifier.markerSurface(
          background = if (selected) Color.White else Color(0xF50E2219),
          border = accent,
          borderWidth = if (selected) 2.dp else 1.5.dp,
          radius = 14.dp,
          elevation = 4.dp,
        )
        .padding(start = 9.dp, top = 5.dp, end = 9.dp, bottom = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      MarkerText(
        balloon.header,
        if (selected) Color(0xFF1B5E20) else MapMint,
        9.5.sp,
        FontWeight.ExtraBold,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        balloon.chips.forEach { chip ->
          Row(
            Modifier.background(
                if (selected) Color(0x1A0E2219) else Color(0x21FFFFFF),
                RoundedCornerShape(12.dp),
              )
              .padding(start = 4.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (chip.symbol.isEmpty()) {
              Box(
                Modifier.size(13.dp)
                  .background(Color(0x4766BB6A), CircleShape)
                  .border(1.5.dp, chip.color, CircleShape)
              )
            } else {
              Box(
                Modifier.size(15.dp).background(chip.color, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                MarkerText(chip.symbol, Color.White, 9.sp, FontWeight.ExtraBold)
              }
            }
            MarkerText(chip.count.toString(), textColor, 10.5.sp, FontWeight.ExtraBold)
          }
        }
      }
    }
    // Tail pointing at the anchor dot.
    Canvas(Modifier.size(width = 12.dp, height = 6.dp)) {
      val tail =
        Path().apply {
          moveTo(0f, 0f)
          lineTo(size.width, 0f)
          lineTo(size.width / 2, size.height)
          close()
        }
      drawPath(tail, accent)
    }
    Box(
      Modifier.padding(top = 1.dp)
        .size(7.dp)
        .background(MapMint, CircleShape)
        .border(1.5.dp, MapInk, CircleShape)
    )
  }
}

@Composable
private fun PlaceChip(place: SurveyMarker.Place) {
  Row(
    Modifier.markerSurface(MapInk, Color(0xFF80DEEA), 2.dp, 16.dp)
      .padding(start = 5.dp, top = 3.dp, end = 8.dp, bottom = 3.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(16.dp).background(Color(0xFF00ACC1), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      MarkerText("★", Color.White, 9.5.sp, FontWeight.ExtraBold)
    }
    MarkerText(place.name, Color.White, 10.sp, FontWeight.Bold)
    MarkerText("×", Color.White.copy(alpha = 0.82f), 12.sp, FontWeight.Bold)
  }
}

@Composable
private fun Chip(
  text: String,
  background: Color,
  border: Color,
  borderWidth: Dp,
  textColor: Color,
  fontSize: TextUnit,
  radius: Dp,
  padding: PaddingValues,
) {
  Box(Modifier.markerSurface(background, border, borderWidth, radius).padding(padding)) {
    MarkerText(text, textColor, fontSize, FontWeight.Bold)
  }
}

@Composable
private fun MarkerText(text: String, color: Color, fontSize: TextUnit, weight: FontWeight) {
  Text(
    text,
    color = color,
    fontSize = fontSize,
    fontWeight = weight,
    lineHeight = fontSize * 1.25f,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Ellipsis,
  )
}

/** Shadow, rounded background, and a solid or dashed border: the look of map annotations. */
private fun Modifier.markerSurface(
  background: Color,
  border: Color,
  borderWidth: Dp,
  radius: Dp,
  dashed: Boolean = false,
  elevation: Dp = 3.dp,
): Modifier {
  val shape = RoundedCornerShape(radius)
  return shadow(elevation, shape, clip = false)
    .background(background, shape)
    .outline(border, borderWidth, radius, dashed)
}

private fun Modifier.outline(color: Color, width: Dp, radius: Dp, dashed: Boolean): Modifier =
  if (!dashed) {
    border(width, color, RoundedCornerShape(radius))
  } else {
    drawBehind {
      val w = width.toPx()
      drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style =
          Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
          ),
      )
    }
  }
