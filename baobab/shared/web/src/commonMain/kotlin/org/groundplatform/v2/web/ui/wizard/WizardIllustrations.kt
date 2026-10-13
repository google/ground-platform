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
package org.groundplatform.v2.web.ui.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.web.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.web.domain.model.editor.WizardMapFeatureSource

/**
 * Panoramic hero illustration for Step 1 (`Overview`) of the Survey Creation Wizard.
 *
 * Depicts the end-to-end Ground 2.0 workflow: a sunlit savanna and agroforestry valley with a
 * dashed survey area boundary, a statistical sample plot grid, field verification pins, and a
 * structured survey form card.
 */
@Composable
internal fun WizardOverviewHeroGraphic(modifier: Modifier = Modifier) {
  val shape = MaterialTheme.shapes.medium
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
  Canvas(
    modifier = modifier.fillMaxWidth().height(132.dp).clip(shape).border(1.dp, borderColor, shape)
  ) {
    val w = size.width
    val h = size.height
    val s = (h / 132f).coerceIn(0.75f, 1.5f)

    val clipRect =
      Path().apply {
        addRoundRect(
          androidx.compose.ui.geometry.RoundRect(
            rect = Rect(0f, 0f, w, h),
            cornerRadius = CornerRadius(12f * s, 12f * s),
          )
        )
      }

    clipPath(clipRect) {
      // 1. Warm morning sky gradient
      drawRect(
        brush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFFD8EFE6), Color(0xFFF3F8EC), Color(0xFFFDF7E2)),
            startY = 0f,
            endY = h * 0.62f,
          )
      )

      // Soft sun glow on the upper-left horizon
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(Color(0xFFFFE082).copy(alpha = 0.65f), Color.Transparent),
            center = Offset(w * 0.16f, h * 0.24f),
            radius = 68f * s,
          ),
        radius = 68f * s,
        center = Offset(w * 0.16f, h * 0.24f),
      )

      // Distant mountains
      val farMountains =
        Path().apply {
          moveTo(0f, h * 0.52f)
          cubicTo(w * 0.15f, h * 0.34f, w * 0.30f, h * 0.38f, w * 0.46f, h * 0.46f)
          cubicTo(w * 0.64f, h * 0.30f, w * 0.82f, h * 0.34f, w, h * 0.44f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
      drawPath(farMountains, color = Color(0xFF8AAFA0))

      // Mid-ground sunlit hills
      val leftHill =
        Path().apply {
          moveTo(0f, h * 0.56f)
          cubicTo(w * 0.25f, h * 0.46f, w * 0.48f, h * 0.58f, w * 0.72f, h * 0.74f)
          lineTo(w * 0.72f, h)
          lineTo(0f, h)
          close()
        }
      drawPath(
        path = leftHill,
        brush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF6B9E62), Color(0xFF3E6F46)),
            startY = h * 0.46f,
            endY = h,
          ),
      )

      val rightHill =
        Path().apply {
          moveTo(w * 0.32f, h * 0.78f)
          cubicTo(w * 0.54f, h * 0.54f, w * 0.78f, h * 0.48f, w, h * 0.58f)
          lineTo(w, h)
          lineTo(w * 0.32f, h)
          close()
        }
      drawPath(
        path = rightHill,
        brush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF568B56), Color(0xFF285538)),
            startY = h * 0.48f,
            endY = h,
          ),
      )

      // Meandering river
      val river =
        Path().apply {
          moveTo(w * 0.46f, h * 0.46f)
          cubicTo(w * 0.42f, h * 0.60f, w * 0.52f, h * 0.74f, w * 0.40f, h * 1.02f)
        }
      drawPath(
        river,
        color = Color(0xFFD7C293),
        style = Stroke(width = 11f * s, cap = StrokeCap.Round),
      )
      drawPath(
        river,
        color = Color(0xFF3B8598),
        style = Stroke(width = 7f * s, cap = StrokeCap.Round),
      )

      // Dashed survey area (AOI) polygon on the left hill
      val aoiPts =
        listOf(
          Offset(w * 0.14f, h * 0.62f),
          Offset(w * 0.27f, h * 0.54f),
          Offset(w * 0.35f, h * 0.66f),
          Offset(w * 0.23f, h * 0.78f),
          Offset(w * 0.12f, h * 0.73f),
        )
      val aoiPath =
        Path().apply {
          aoiPts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
          close()
        }
      drawPath(aoiPath, color = Color(0xFFA7F3D0).copy(alpha = 0.24f))
      drawPath(
        path = aoiPath,
        color = Color(0xFFECFDF5),
        style =
          Stroke(
            width = 1.8f * s,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 3.5f * s), 0f),
          ),
      )
      aoiPts.forEach { pt -> drawCircle(color = Color(0xFFECFDF5), radius = 2.5f * s, center = pt) }

      // Sample plot square on the right slope
      val plotTopLeft = Offset(w * 0.55f, h * 0.58f)
      val plotSize = 26f * s
      drawRoundRect(
        color = Color(0xFFF9A825).copy(alpha = 0.22f),
        topLeft = plotTopLeft,
        size = Size(plotSize, plotSize),
        cornerRadius = CornerRadius(3f * s, 3f * s),
      )
      drawRoundRect(
        color = Color(0xFFFFE082),
        topLeft = plotTopLeft,
        size = Size(plotSize, plotSize),
        cornerRadius = CornerRadius(3f * s, 3f * s),
        style = Stroke(width = 1.7f * s),
      )
      for (r in 0..2) {
        for (c in 0..2) {
          val dotCenter =
            Offset(
              plotTopLeft.x + (c + 0.5f) * (plotSize / 3f),
              plotTopLeft.y + (r + 0.5f) * (plotSize / 3f),
            )
          drawCircle(
            color = if ((r + c) % 2 == 0) Color(0xFF66BB6A) else Color(0xFFFFCA28),
            radius = 2.0f * s,
            center = dotCenter,
          )
        }
      }

      // Savanna acacia & broadleaf trees
      drawMiniAcacia(base = Offset(w * 0.07f, h * 0.84f), scale = s * 0.95f)
      drawMiniBroadleaf(base = Offset(w * 0.68f, h * 0.64f), scale = s * 0.85f)

      // Ground squircle pin inside the AOI
      drawMiniGroundPin(
        tip = Offset(w * 0.24f, h * 0.67f),
        fillColor = Color(0xFF2E7D32),
        scale = s * 0.9f,
        checked = true,
      )

      // Floating Form / Schema card on the right
      val cardW = 112f * s
      val cardH = 74f * s
      val cardLeft = w - cardW - 22f * s
      val cardTop = (h - cardH) / 2f
      drawRoundRect(
        color = Color.Black.copy(alpha = 0.14f),
        topLeft = Offset(cardLeft + 2f * s, cardTop + 3f * s),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(8f * s, 8f * s),
      )
      drawRoundRect(
        color = Color(0xFFFFFDF9),
        topLeft = Offset(cardLeft, cardTop),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(8f * s, 8f * s),
      )
      // Card header bar
      drawRoundRect(
        color = Color(0xFF2E7D32),
        topLeft = Offset(cardLeft + 8f * s, cardTop + 8f * s),
        size = Size(cardW - 16f * s, 10f * s),
        cornerRadius = CornerRadius(3f * s, 3f * s),
      )
      // Simulated form rows with check badges
      for (row in 0..2) {
        val rowY = cardTop + 26f * s + row * 15f * s
        drawCircle(
          color = if (row < 2) Color(0xFF2E7D32) else Color(0xFFF9A825),
          radius = 4f * s,
          center = Offset(cardLeft + 14f * s, rowY),
        )
        drawRoundRect(
          color = Color(0xFFCBD5E1),
          topLeft = Offset(cardLeft + 24f * s, rowY - 3f * s),
          size = Size(cardW - 36f * s - (row * 10f * s), 6f * s),
          cornerRadius = CornerRadius(3f * s, 3f * s),
        )
      }
    }
  }
}

/**
 * Illustration for each [SurveyCollectionMode] card on Step 2 (`Mode & purpose`):
 * - [SurveyCollectionMode.FIELD_SURVEY]: GPS walking polygon, mobile accuracy halo, and field pin.
 * - [SurveyCollectionMode.VISUAL_CLASSIFICATION]: Satellite mosaic tiles, CEO square plot, and 3×3
 *   sub-plot interpretation grid.
 * - [SurveyCollectionMode.BOTH]: Combined satellite sample plot grid + ground-truth field pin and
 *   GPS track.
 */
@Composable
internal fun CollectionModeCardGraphic(
  mode: SurveyCollectionMode,
  modifier: Modifier = Modifier,
) {
  val shape = MaterialTheme.shapes.small
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
  Canvas(
    modifier = modifier.fillMaxWidth().height(92.dp).clip(shape).border(1.dp, borderColor, shape)
  ) {
    when (mode) {
      SurveyCollectionMode.FIELD_SURVEY -> drawFieldSurveyGraphic()
      SurveyCollectionMode.VISUAL_CLASSIFICATION -> drawVisualClassificationGraphic()
      SurveyCollectionMode.BOTH -> drawHybridModeGraphic()
    }
  }
}

private fun DrawScope.drawFieldSurveyGraphic() {
  val w = size.width
  val h = size.height
  val s = (h / 92f).coerceIn(0.75f, 1.4f)

  // Terrain background
  drawRect(
    brush =
      Brush.verticalGradient(
        colors = listOf(Color(0xFF3E7548), Color(0xFF244D32)),
        startY = 0f,
        endY = h,
      )
  )

  // Subtle topographic contour lines
  val contour1 =
    Path().apply {
      moveTo(0f, h * 0.32f)
      cubicTo(w * 0.35f, h * 0.18f, w * 0.65f, h * 0.45f, w, h * 0.26f)
    }
  val contour2 =
    Path().apply {
      moveTo(0f, h * 0.74f)
      cubicTo(w * 0.30f, h * 0.58f, w * 0.70f, h * 0.85f, w, h * 0.64f)
    }
  drawPath(contour1, color = Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.4f * s))
  drawPath(contour2, color = Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.4f * s))

  // Walked GPS farm boundary polygon
  val polyPts =
    listOf(
      Offset(w * 0.16f, h * 0.30f),
      Offset(w * 0.52f, h * 0.20f),
      Offset(w * 0.64f, h * 0.62f),
      Offset(w * 0.26f, h * 0.76f),
    )
  val polyPath =
    Path().apply {
      polyPts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
      close()
    }
  drawPath(polyPath, color = Color(0xFF66BB6A).copy(alpha = 0.28f))
  drawPath(
    path = polyPath,
    color = Color(0xFFA7F3D0),
    style =
      Stroke(
        width = 2.0f * s,
        join = StrokeJoin.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 3.5f * s), 0f),
      ),
  )
  polyPts.forEach { pt -> drawCircle(color = Color.White, radius = 2.8f * s, center = pt) }

  // Blue GPS accuracy halo + live location dot at the bottom-right vertex
  val gpsCenter = polyPts[2]
  drawCircle(
    color = Color(0xFF38BDF8).copy(alpha = 0.28f),
    radius = 12f * s,
    center = gpsCenter,
  )
  drawCircle(color = Color.White, radius = 5.2f * s, center = gpsCenter)
  drawCircle(color = Color(0xFF0284C7), radius = 3.6f * s, center = gpsCenter)

  // Ground squircle check pin inside the polygon
  drawMiniGroundPin(
    tip = Offset(w * 0.38f, h * 0.54f),
    fillColor = Color(0xFF2E7D32),
    scale = s * 0.88f,
    checked = true,
  )

  // Stylized mobile handset badge on the right
  val phoneW = 26f * s
  val phoneH = 44f * s
  val phoneTopLeft = Offset(w * 0.76f, (h - phoneH) / 2f)
  drawRoundRect(
    color = Color(0xFF0F172A),
    topLeft = phoneTopLeft,
    size = Size(phoneW, phoneH),
    cornerRadius = CornerRadius(5f * s, 5f * s),
  )
  drawRoundRect(
    color = Color(0xFFECFDF5),
    topLeft = Offset(phoneTopLeft.x + 2.5f * s, phoneTopLeft.y + 4.5f * s),
    size = Size(phoneW - 5f * s, phoneH - 9f * s),
    cornerRadius = CornerRadius(3f * s, 3f * s),
  )
  drawRoundRect(
    color = Color(0xFF2E7D32),
    topLeft = Offset(phoneTopLeft.x + 5.5f * s, phoneTopLeft.y + 9f * s),
    size = Size(phoneW - 11f * s, 5f * s),
    cornerRadius = CornerRadius(2f * s, 2f * s),
  )
  drawRoundRect(
    color = Color(0xFF94A3B8),
    topLeft = Offset(phoneTopLeft.x + 5.5f * s, phoneTopLeft.y + 18f * s),
    size = Size(phoneW - 11f * s, 4f * s),
    cornerRadius = CornerRadius(2f * s, 2f * s),
  )
  drawRoundRect(
    color = Color(0xFF94A3B8),
    topLeft = Offset(phoneTopLeft.x + 5.5f * s, phoneTopLeft.y + 25f * s),
    size = Size(phoneW - 15f * s, 4f * s),
    cornerRadius = CornerRadius(2f * s, 2f * s),
  )
}

private fun DrawScope.drawVisualClassificationGraphic() {
  val w = size.width
  val h = size.height
  val s = (h / 92f).coerceIn(0.75f, 1.4f)

  // Deep satellite earth-observation backdrop
  drawRect(
    brush =
      Brush.linearGradient(
        colors = listOf(Color(0xFF1B3B2B), Color(0xFF1E3A4C), Color(0xFF2B4C3B)),
        start = Offset.Zero,
        end = Offset(w, h),
      )
  )

  // Subtle satellite tile grid lines
  val cols = 6
  val rows = 3
  for (c in 1 until cols) {
    val x = w * (c.toFloat() / cols)
    drawLine(
      color = Color.White.copy(alpha = 0.08f),
      start = Offset(x, 0f),
      end = Offset(x, h),
      strokeWidth = 1f,
    )
  }
  for (r in 1 until rows) {
    val y = h * (r.toFloat() / rows)
    drawLine(
      color = Color.White.copy(alpha = 0.08f),
      start = Offset(0f, y),
      end = Offset(w, y),
      strokeWidth = 1f,
    )
  }

  // Natural canopy & clearing patches visible in the imagery
  val forestPatch =
    Path().apply {
      moveTo(0f, 0f)
      lineTo(w * 0.62f, 0f)
      cubicTo(w * 0.56f, h * 0.42f, w * 0.44f, h * 0.68f, w * 0.34f, h)
      lineTo(0f, h)
      close()
    }
  drawPath(forestPatch, color = Color(0xFF2E6B3E).copy(alpha = 0.55f))

  // CEO square sample plot with 3x3 internal sub-plot grid in the center
  val plotSize = 54f * s
  val plotTopLeft = Offset((w - plotSize) / 2f, (h - plotSize) / 2f)
  drawRoundRect(
    color = Color(0xFFF9A825).copy(alpha = 0.16f),
    topLeft = plotTopLeft,
    size = Size(plotSize, plotSize),
    cornerRadius = CornerRadius(4f * s, 4f * s),
  )
  drawRoundRect(
    color = Color(0xFFFFCA28),
    topLeft = plotTopLeft,
    size = Size(plotSize, plotSize),
    cornerRadius = CornerRadius(4f * s, 4f * s),
    style = Stroke(width = 2.0f * s),
  )

  // 3x3 classified sub-plot points (green = Forest, orange = Non-forest)
  for (r in 0..2) {
    for (c in 0..2) {
      val pt =
        Offset(
          plotTopLeft.x + (c + 0.5f) * (plotSize / 3f),
          plotTopLeft.y + (r + 0.5f) * (plotSize / 3f),
        )
      val isForest = c == 0 || (c == 1 && r <= 1)
      drawCircle(color = Color.White, radius = 4.2f * s, center = pt)
      drawCircle(
        color = if (isForest) Color(0xFF43A047) else Color(0xFFF57C00),
        radius = 3.0f * s,
        center = pt,
      )
    }
  }
}

private fun DrawScope.drawHybridModeGraphic() {
  val w = size.width
  val h = size.height
  val s = (h / 92f).coerceIn(0.75f, 1.4f)

  // Split / blended satellite-to-field gradient
  drawRect(
    brush =
      Brush.horizontalGradient(
        colors = listOf(Color(0xFF1D3E3A), Color(0xFF2B5B3C), Color(0xFF3A6E45)),
        startX = 0f,
        endX = w,
      )
  )

  // Left side: CEO sample plot with sub-plot points
  val plotSize = 46f * s
  val plotTopLeft = Offset(w * 0.12f, (h - plotSize) / 2f)
  drawRoundRect(
    color = Color(0xFFF9A825).copy(alpha = 0.18f),
    topLeft = plotTopLeft,
    size = Size(plotSize, plotSize),
    cornerRadius = CornerRadius(4f * s, 4f * s),
  )
  drawRoundRect(
    color = Color(0xFFFFCA28),
    topLeft = plotTopLeft,
    size = Size(plotSize, plotSize),
    cornerRadius = CornerRadius(4f * s, 4f * s),
    style = Stroke(width = 1.8f * s),
  )
  for (r in 0..1) {
    for (c in 0..1) {
      val pt =
        Offset(
          plotTopLeft.x + (c + 0.5f) * (plotSize / 2f),
          plotTopLeft.y + (r + 0.5f) * (plotSize / 2f),
        )
      drawCircle(color = Color.White, radius = 3.6f * s, center = pt)
      drawCircle(
        color = if (r == 0 && c == 0) Color(0xFF43A047) else Color(0xFFF9A825),
        radius = 2.5f * s,
        center = pt,
      )
    }
  }

  // Dashed connecting path from the sample plot to the field verification pin on the right
  val linkPath =
    Path().apply {
      moveTo(plotTopLeft.x + plotSize + 4f * s, h * 0.55f)
      cubicTo(w * 0.48f, h * 0.35f, w * 0.60f, h * 0.72f, w * 0.74f, h * 0.58f)
    }
  drawPath(
    path = linkPath,
    color = Color(0xFFA7F3D0),
    style =
      Stroke(
        width = 2.0f * s,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 3.5f * s), 0f),
      ),
  )

  // Right side: Ground squircle field verification pin + GPS halo
  val pinTip = Offset(w * 0.76f, h * 0.62f)
  drawCircle(
    color = Color(0xFF38BDF8).copy(alpha = 0.25f),
    radius = 14f * s,
    center = pinTip,
  )
  drawMiniGroundPin(
    tip = pinTip,
    fillColor = Color(0xFF2E7D32),
    scale = s * 0.92f,
    checked = true,
  )
}

private fun DrawScope.drawMiniAcacia(base: Offset, scale: Float) {
  drawRoundRect(
    color = Color(0xFF4A3222),
    topLeft = Offset(base.x - 1.5f * scale, base.y - 16f * scale),
    size = Size(3f * scale, 16f * scale),
    cornerRadius = CornerRadius(1f * scale, 1f * scale),
  )
  drawRoundRect(
    color = Color(0xFF1D462B),
    topLeft = Offset(base.x - 14f * scale, base.y - 20f * scale),
    size = Size(28f * scale, 6f * scale),
    cornerRadius = CornerRadius(3f * scale, 3f * scale),
  )
  drawRoundRect(
    color = Color(0xFF4E8953),
    topLeft = Offset(base.x - 11f * scale, base.y - 22.5f * scale),
    size = Size(22f * scale, 4.5f * scale),
    cornerRadius = CornerRadius(2.5f * scale, 2.5f * scale),
  )
}

private fun DrawScope.drawMiniBroadleaf(base: Offset, scale: Float) {
  drawRoundRect(
    color = Color(0xFF4A3323),
    topLeft = Offset(base.x - 1.5f * scale, base.y - 10f * scale),
    size = Size(3f * scale, 10f * scale),
    cornerRadius = CornerRadius(1f * scale, 1f * scale),
  )
  drawCircle(
    color = Color(0xFF234B30),
    radius = 7.5f * scale,
    center = Offset(base.x - 3f * scale, base.y - 12f * scale),
  )
  drawCircle(
    color = Color(0xFF386D44),
    radius = 8.5f * scale,
    center = Offset(base.x + 1f * scale, base.y - 15f * scale),
  )
  drawCircle(
    color = Color(0xFF6B9F62),
    radius = 5.5f * scale,
    center = Offset(base.x + 2f * scale, base.y - 16.5f * scale),
  )
}

private fun DrawScope.drawMiniGroundPin(
  tip: Offset,
  fillColor: Color,
  scale: Float,
  checked: Boolean,
) {
  val bodyW = 18f * scale
  val bodyH = 18f * scale
  val corner = 6f * scale
  val pointerH = 4.5f * scale
  val topLeft = Offset(tip.x - bodyW / 2f, tip.y - bodyH - pointerH)
  val center = Offset(tip.x, topLeft.y + bodyH / 2f)

  drawOval(
    color = Color.Black.copy(alpha = 0.22f),
    topLeft = Offset(tip.x - 5f * scale, tip.y - 1.8f * scale),
    size = Size(10f * scale, 3.5f * scale),
  )
  val pointer =
    Path().apply {
      moveTo(tip.x - 4f * scale, topLeft.y + bodyH - 1f)
      lineTo(tip.x, tip.y)
      lineTo(tip.x + 4f * scale, topLeft.y + bodyH - 1f)
      close()
    }
  drawPath(pointer, color = Color.White)
  drawRoundRect(
    color = Color.White,
    topLeft = topLeft,
    size = Size(bodyW, bodyH),
    cornerRadius = CornerRadius(corner, corner),
  )
  val inset = 1.6f * scale
  drawRoundRect(
    color = fillColor,
    topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
    size = Size(bodyW - inset * 2f, bodyH - inset * 2f),
    cornerRadius = CornerRadius(corner - inset * 0.5f, corner - inset * 0.5f),
  )
  if (checked) {
    val check =
      Path().apply {
        moveTo(center.x - 3.6f * scale, center.y + 0.2f * scale)
        lineTo(center.x - 1.0f * scale, center.y + 2.8f * scale)
        lineTo(center.x + 3.8f * scale, center.y - 2.4f * scale)
      }
    drawPath(
      path = check,
      color = Color.White,
      style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  } else {
    drawCircle(color = Color.White, radius = 2.6f * scale, center = center)
  }
}

/**
 * Illustration for each [WizardMapFeatureSource] card on Step 4 (`Map features`):
 * - [WizardMapFeatureSource.FIELD_FREEFORM]: Data collectors capturing points and walked polygons
 *   in the field.
 * - [WizardMapFeatureSource.SAMPLE_PLOTS]: Systematic sample plot grid inside a survey area.
 * - [WizardMapFeatureSource.IMPORT_FILE]: Pre-defined parcel polygons imported from GeoJSON / KML.
 */
@Composable
internal fun MapFeatureSourceCardGraphic(
  source: WizardMapFeatureSource,
  modifier: Modifier = Modifier,
) {
  val shape = MaterialTheme.shapes.small
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
  Canvas(
    modifier = modifier.fillMaxWidth().height(84.dp).clip(shape).border(1.dp, borderColor, shape)
  ) {
    when (source) {
      WizardMapFeatureSource.FIELD_FREEFORM -> drawFieldFreeformSourceGraphic()
      WizardMapFeatureSource.SAMPLE_PLOTS -> drawSamplePlotsSourceGraphic()
      WizardMapFeatureSource.IMPORT_FILE -> drawImportFileSourceGraphic()
    }
  }
}

private fun DrawScope.drawFieldFreeformSourceGraphic() {
  val w = size.width
  val h = size.height
  val s = (h / 84f).coerceIn(0.75f, 1.4f)

  drawRect(
    brush =
      Brush.verticalGradient(
        colors = listOf(Color(0xFF2E6B47), Color(0xFF1E4631)),
        startY = 0f,
        endY = h,
      )
  )

  // Walked polygon + GPS pins
  val polyPts =
    listOf(
      Offset(w * 0.18f, h * 0.28f),
      Offset(w * 0.48f, h * 0.22f),
      Offset(w * 0.54f, h * 0.68f),
      Offset(w * 0.22f, h * 0.74f),
    )
  val polyPath =
    Path().apply {
      polyPts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
      close()
    }
  drawPath(polyPath, color = Color(0xFF66BB6A).copy(alpha = 0.28f))
  drawPath(
    path = polyPath,
    color = Color(0xFFA7F3D0),
    style =
      Stroke(
        width = 1.8f * s,
        join = StrokeJoin.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 3.5f * s), 0f),
      ),
  )
  polyPts.forEach { pt -> drawCircle(color = Color.White, radius = 2.6f * s, center = pt) }

  drawMiniGroundPin(
    tip = Offset(w * 0.36f, h * 0.54f),
    fillColor = Color(0xFF2E7D32),
    scale = s * 0.85f,
    checked = true,
  )
  drawMiniGroundPin(
    tip = Offset(w * 0.76f, h * 0.64f),
    fillColor = Color(0xFF0284C7),
    scale = s * 0.85f,
    checked = false,
  )
}

private fun DrawScope.drawSamplePlotsSourceGraphic() {
  val w = size.width
  val h = size.height
  val s = (h / 84f).coerceIn(0.75f, 1.4f)

  drawRect(
    brush =
      Brush.linearGradient(
        colors = listOf(Color(0xFF1A382C), Color(0xFF24493C)),
        start = Offset.Zero,
        end = Offset(w, h),
      )
  )

  // Survey boundary box
  val padX = w * 0.14f
  val padY = h * 0.14f
  drawRoundRect(
    color = Color(0xFFA7F3D0).copy(alpha = 0.5f),
    topLeft = Offset(padX, padY),
    size = Size(w - 2 * padX, h - 2 * padY),
    cornerRadius = CornerRadius(6f * s, 6f * s),
    style =
      Stroke(
        width = 1.5f * s,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 3.5f * s), 0f),
      ),
  )

  // 2x3 grid of sample plots inside AOI
  val plotSize = 16f * s
  for (r in 0..1) {
    for (c in 0..2) {
      val cx = w * (0.28f + c * 0.22f)
      val cy = h * (0.36f + r * 0.30f)
      val tl = Offset(cx - plotSize / 2f, cy - plotSize / 2f)
      drawRoundRect(
        color = Color(0xFFF9A825).copy(alpha = 0.24f),
        topLeft = tl,
        size = Size(plotSize, plotSize),
        cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
      )
      drawRoundRect(
        color = Color(0xFFFFCA28),
        topLeft = tl,
        size = Size(plotSize, plotSize),
        cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
        style = Stroke(width = 1.5f * s),
      )
      drawCircle(color = Color.White, radius = 2.0f * s, center = Offset(cx, cy))
    }
  }
}

private fun DrawScope.drawImportFileSourceGraphic() {
  val w = size.width
  val h = size.height
  val s = (h / 84f).coerceIn(0.75f, 1.4f)

  drawRect(
    brush =
      Brush.linearGradient(
        colors = listOf(Color(0xFF1E3A4C), Color(0xFF284B3C)),
        start = Offset.Zero,
        end = Offset(w, h),
      )
  )

  // Pre-defined parcel polygons on the left
  val parcel1 =
    Path().apply {
      moveTo(w * 0.14f, h * 0.22f)
      lineTo(w * 0.38f, h * 0.18f)
      lineTo(w * 0.42f, h * 0.50f)
      lineTo(w * 0.16f, h * 0.52f)
      close()
    }
  val parcel2 =
    Path().apply {
      moveTo(w * 0.18f, h * 0.56f)
      lineTo(w * 0.44f, h * 0.54f)
      lineTo(w * 0.48f, h * 0.82f)
      lineTo(w * 0.20f, h * 0.84f)
      close()
    }
  drawPath(parcel1, color = Color(0xFF38BDF8).copy(alpha = 0.26f))
  drawPath(parcel1, color = Color(0xFF7DD3FC), style = Stroke(width = 1.8f * s))
  drawPath(parcel2, color = Color(0xFF66BB6A).copy(alpha = 0.26f))
  drawPath(parcel2, color = Color(0xFFA7F3D0), style = Stroke(width = 1.8f * s))

  // File card badge on the right
  val docW = 36f * s
  val docH = 44f * s
  val docTopLeft = Offset(w * 0.64f, (h - docH) / 2f)
  drawRoundRect(
    color = Color(0xFFFFFDF9),
    topLeft = docTopLeft,
    size = Size(docW, docH),
    cornerRadius = CornerRadius(5f * s, 5f * s),
  )
  drawRoundRect(
    color = Color(0xFF0284C7),
    topLeft = Offset(docTopLeft.x + 6f * s, docTopLeft.y + 8f * s),
    size = Size(docW - 12f * s, 6f * s),
    cornerRadius = CornerRadius(2f * s, 2f * s),
  )
  drawRoundRect(
    color = Color(0xFF94A3B8),
    topLeft = Offset(docTopLeft.x + 6f * s, docTopLeft.y + 19f * s),
    size = Size(docW - 12f * s, 4f * s),
    cornerRadius = CornerRadius(2f * s, 2f * s),
  )
  drawRoundRect(
    color = Color(0xFF94A3B8),
    topLeft = Offset(docTopLeft.x + 6f * s, docTopLeft.y + 27f * s),
    size = Size(docW - 16f * s, 4f * s),
    cornerRadius = CornerRadius(2f * s, 2f * s),
  )
}
