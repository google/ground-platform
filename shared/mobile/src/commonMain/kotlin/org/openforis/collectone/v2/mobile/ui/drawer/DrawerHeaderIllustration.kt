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
package org.openforis.collectone.v2.mobile.ui.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Naturalistic panoramic Ground landscape background image for the mobile navigation drawer header.
 *
 * Depicts a sunlit watershed valley with misty highland mountain ridges, terraced agroforestry
 * slopes, a meandering valley river, drifting clouds, distant birds, and a signature savanna acacia
 * and broadleaf forest grove framed toward the right horizon, overlaid with a soft directional
 * tonal veil so user profile and organization text on the left maintains high-contrast legibility
 * in both Light and Dark themes.
 */
@Composable
internal fun DrawerHeaderBackground(
  isDarkTheme: Boolean,
  scrimColor: Color,
  modifier: Modifier = Modifier,
) {
  val colors = DrawerHeaderPalette.forTheme(isDarkTheme)
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val s = (h / 136f).coerceAtLeast(0.6f)

    // 1. High-key pastel morning sky gradient (light mode) or deep emerald twilight (dark mode)
    drawRect(brush = Brush.verticalGradient(colors = colors.skyGradient))

    // 2. Warm morning sun & soft concentric glow over the right valley pass
    val sunCenter = Offset(w * 0.73f, h * 0.28f)
    val sunRadius = h * 0.17f
    drawCircle(
      color = colors.sunOuterGlow,
      radius = sunRadius * 2.15f,
      center = sunCenter,
    )
    drawCircle(
      color = colors.sunInnerGlow,
      radius = sunRadius * 1.42f,
      center = sunCenter,
    )
    drawCircle(
      color = colors.sunDisc,
      radius = sunRadius,
      center = sunCenter,
    )

    // Soft clouds & distant soaring birds in the upper valley sky
    val cloudColor = colors.cloud
    drawHeaderCloud(center = Offset(w * 0.50f, h * 0.18f), scale = s * 0.85f, color = cloudColor)
    drawHeaderCloud(center = Offset(w * 0.88f, h * 0.16f), scale = s * 0.72f, color = cloudColor)

    val birdColor = colors.bird
    drawHeaderBird(center = Offset(w * 0.59f, h * 0.24f), scale = s * 0.82f, color = birdColor)
    drawHeaderBird(center = Offset(w * 0.63f, h * 0.19f), scale = s * 0.64f, color = birdColor)

    // 3. Distant misty mountain ridges in soft pastel mint/sage
    val farRange =
      Path().apply {
        moveTo(0f, h * 0.56f)
        cubicTo(w * 0.16f, h * 0.44f, w * 0.30f, h * 0.42f, w * 0.44f, h * 0.49f)
        cubicTo(w * 0.56f, h * 0.54f, w * 0.68f, h * 0.34f, w * 0.82f, h * 0.36f)
        cubicTo(w * 0.90f, h * 0.37f, w * 0.96f, h * 0.41f, w, h * 0.44f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = farRange,
      color = colors.farRange,
    )

    val midRidge =
      Path().apply {
        moveTo(0f, h * 0.64f)
        cubicTo(w * 0.18f, h * 0.52f, w * 0.36f, h * 0.56f, w * 0.54f, h * 0.59f)
        cubicTo(w * 0.70f, h * 0.61f, w * 0.84f, h * 0.44f, w, h * 0.50f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = midRidge,
      color = colors.midRidge,
    )

    // 4. Mid-ground terraced agroforestry & savanna hills in soft pastel sage
    val leftHill =
      Path().apply {
        moveTo(0f, h * 0.68f)
        cubicTo(w * 0.22f, h * 0.58f, w * 0.44f, h * 0.66f, w * 0.64f, h * 0.80f)
        lineTo(w * 0.64f, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = leftHill,
      brush =
        Brush.verticalGradient(
          colors = colors.leftHillGradient,
          startY = h * 0.58f,
          endY = h,
        ),
    )

    // Subtle cultivated terrace contour lines on the left slope
    val terraceColor = colors.terrace
    val terrace1 =
      Path().apply {
        moveTo(0f, h * 0.75f)
        cubicTo(w * 0.18f, h * 0.66f, w * 0.34f, h * 0.72f, w * 0.50f, h * 0.80f)
      }
    val terrace2 =
      Path().apply {
        moveTo(0f, h * 0.83f)
        cubicTo(w * 0.16f, h * 0.74f, w * 0.32f, h * 0.80f, w * 0.46f, h * 0.88f)
      }
    drawPath(
      path = terrace1,
      color = terraceColor,
      style = Stroke(width = 1.8f * s, cap = StrokeCap.Round),
    )
    drawPath(
      path = terrace2,
      color = terraceColor.copy(alpha = terraceColor.alpha * 0.8f),
      style = Stroke(width = 1.6f * s, cap = StrokeCap.Round),
    )

    // Right hillside in pastel meadow tones
    val rightHill =
      Path().apply {
        moveTo(w * 0.36f, h * 0.84f)
        cubicTo(w * 0.56f, h * 0.64f, w * 0.78f, h * 0.54f, w, h * 0.62f)
        lineTo(w, h)
        lineTo(w * 0.36f, h)
        close()
      }
    drawPath(
      path = rightHill,
      brush =
        Brush.verticalGradient(
          colors = colors.rightHillGradient,
          startY = h * 0.54f,
          endY = h,
        ),
    )

    // 5. Meandering valley river in soft pastel aqua & sand
    val riverPath =
      Path().apply {
        moveTo(w * 0.58f, h * 0.59f)
        cubicTo(w * 0.52f, h * 0.69f, w * 0.62f, h * 0.80f, w * 0.52f, h * 0.91f)
        cubicTo(w * 0.48f, h * 0.95f, w * 0.43f, h * 0.99f, w * 0.38f, h * 1.03f)
      }
    drawPath(
      path = riverPath,
      color = colors.riverBank,
      style = Stroke(width = 10.5f * s, cap = StrokeCap.Round),
    )
    drawPath(
      path = riverPath,
      color = colors.riverWater,
      style = Stroke(width = 7.0f * s, cap = StrokeCap.Round),
    )
    drawPath(
      path = riverPath,
      color = colors.riverHighlight,
      style = Stroke(width = 2.2f * s, cap = StrokeCap.Round),
    )

    // 6. Foreground botanical hills in soft sage (avoiding dark greens behind text)
    val foreLeft =
      Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.20f, h * 0.80f, w * 0.36f, h * 0.88f, w * 0.52f, h)
        lineTo(0f, h)
        close()
      }
    drawPath(
      path = foreLeft,
      color = colors.foregroundLeftHill,
    )

    val foreRight =
      Path().apply {
        moveTo(w * 0.46f, h)
        cubicTo(w * 0.64f, h * 0.83f, w * 0.82f, h * 0.76f, w, h * 0.82f)
        lineTo(w, h)
        close()
      }
    drawPath(
      path = foreRight,
      color = colors.foregroundRightHill,
    )

    // 7. Signature Savanna Acacia & Broadleaf trees rendered in soft pastel sage tones
    drawHeaderBroadleafTree(
      base = Offset(w * 0.67f, h * 0.69f),
      scale = s * 0.76f,
      colors = colors.leftBroadleafTree,
    )
    drawHeaderAcaciaTree(
      base = Offset(w * 0.83f, h * 0.82f),
      scale = s * 1.05f,
      colors = colors.acaciaTree,
    )
    drawHeaderBroadleafTree(
      base = Offset(w * 0.94f, h * 0.76f),
      scale = s * 0.82f,
      colors = colors.rightBroadleafTree,
    )

    val tuftColor = colors.grassTuft
    drawHeaderGrassTuft(base = Offset(w * 0.74f, h * 0.95f), scale = s * 0.85f, color = tuftColor)
    drawHeaderGrassTuft(base = Offset(w * 0.90f, h * 0.93f), scale = s * 0.92f, color = tuftColor)

    // 8. Stronger two-stage legibility scrim:
    //    (a) Horizontal veil keeping the left & center text zone high-contrast
    val baseVeil = if (isDarkTheme) scrimColor else DrawerHeaderPalette.LightThemeVeil
    drawRect(
      brush =
        Brush.horizontalGradient(
          0.0f to baseVeil.copy(alpha = if (isDarkTheme) 0.86f else 0.84f),
          0.55f to baseVeil.copy(alpha = if (isDarkTheme) 0.72f else 0.68f),
          0.82f to baseVeil.copy(alpha = if (isDarkTheme) 0.48f else 0.42f),
          1.0f to baseVeil.copy(alpha = if (isDarkTheme) 0.28f else 0.22f),
        )
    )
    //    (b) Vertical bottom-up veil behind the user profile rows (name, email, organizations)
    drawRect(
      brush =
        Brush.verticalGradient(
          0.0f to Color.Transparent,
          0.36f to baseVeil.copy(alpha = if (isDarkTheme) 0.18f else 0.15f),
          0.72f to baseVeil.copy(alpha = if (isDarkTheme) 0.52f else 0.48f),
          1.0f to baseVeil.copy(alpha = if (isDarkTheme) 0.62f else 0.56f),
        )
    )
  }
}

private fun DrawScope.drawHeaderCloud(center: Offset, scale: Float, color: Color) {
  val w = 44f * scale
  val h = 11f * scale
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
    size = Size(w, h),
    cornerRadius = CornerRadius(h / 2f, h / 2f),
  )
  drawCircle(
    color = color,
    radius = 8.2f * scale,
    center = Offset(center.x - 8.5f * scale, center.y - 2.8f * scale),
  )
  drawCircle(
    color = color,
    radius = 10.5f * scale,
    center = Offset(center.x + 2.8f * scale, center.y - 4.6f * scale),
  )
  drawCircle(
    color = color,
    radius = 7.0f * scale,
    center = Offset(center.x + 13f * scale, center.y - 1.8f * scale),
  )
}

private fun DrawScope.drawHeaderBird(center: Offset, scale: Float, color: Color) {
  val bird =
    Path().apply {
      moveTo(center.x - 5.2f * scale, center.y + 1.1f * scale)
      cubicTo(
        center.x - 2.8f * scale,
        center.y - 2.1f * scale,
        center.x - 0.9f * scale,
        center.y - 1.4f * scale,
        center.x,
        center.y + 0.5f * scale,
      )
      cubicTo(
        center.x + 0.9f * scale,
        center.y - 1.4f * scale,
        center.x + 2.8f * scale,
        center.y - 2.1f * scale,
        center.x + 5.2f * scale,
        center.y + 1.1f * scale,
      )
    }
  drawPath(
    path = bird,
    color = color,
    style = Stroke(width = 1.25f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

private fun DrawScope.drawHeaderAcaciaTree(base: Offset, scale: Float, colors: AcaciaTreeColors) {
  val trunk =
    Path().apply {
      moveTo(base.x - 3.2f * scale, base.y)
      cubicTo(
        base.x - 1.8f * scale,
        base.y - 7.5f * scale,
        base.x - 1.4f * scale,
        base.y - 13f * scale,
        base.x - 2.0f * scale,
        base.y - 19f * scale,
      )
      lineTo(base.x - 12f * scale, base.y - 26.5f * scale)
      lineTo(base.x - 9.8f * scale, base.y - 27.5f * scale)
      lineTo(base.x - 0.9f * scale, base.y - 20.8f * scale)
      lineTo(base.x + 1.4f * scale, base.y - 29.5f * scale)
      lineTo(base.x + 3.2f * scale, base.y - 29.5f * scale)
      lineTo(base.x + 1.4f * scale, base.y - 20f * scale)
      lineTo(base.x + 11.2f * scale, base.y - 25.5f * scale)
      lineTo(base.x + 13f * scale, base.y - 24.5f * scale)
      lineTo(base.x + 1.8f * scale, base.y - 17f * scale)
      cubicTo(
        base.x + 1.6f * scale,
        base.y - 11f * scale,
        base.x + 2.0f * scale,
        base.y - 5.5f * scale,
        base.x + 3.6f * scale,
        base.y,
      )
      close()
    }
  drawPath(path = trunk, color = colors.bark)

  drawHeaderCanopyPad(
    center = Offset(base.x - 10.5f * scale, base.y - 27.5f * scale),
    width = 22f * scale,
    height = 7.0f * scale,
    color = colors.shadowCanopy,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 10.5f * scale, base.y - 25.5f * scale),
    width = 22f * scale,
    height = 7.0f * scale,
    color = colors.shadowCanopy,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 1f * scale, base.y - 31.2f * scale),
    width = 30f * scale,
    height = 8.0f * scale,
    color = colors.midCanopy,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x - 9.5f * scale, base.y - 29.2f * scale),
    width = 18.5f * scale,
    height = 4.6f * scale,
    color = colors.lightCanopyLeft,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 11.2f * scale, base.y - 27.2f * scale),
    width = 18.5f * scale,
    height = 4.6f * scale,
    color = colors.lightCanopyRight,
  )
  drawHeaderCanopyPad(
    center = Offset(base.x + 1.8f * scale, base.y - 33.2f * scale),
    width = 24f * scale,
    height = 5.2f * scale,
    color = colors.topCanopyHighlight,
  )
}

private fun DrawScope.drawHeaderCanopyPad(
  center: Offset,
  width: Float,
  height: Float,
  color: Color,
) {
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
    size = Size(width, height),
    cornerRadius = CornerRadius(height / 2f, height / 2f),
  )
}

private fun DrawScope.drawHeaderBroadleafTree(
  base: Offset,
  scale: Float,
  colors: BroadleafTreeColors,
) {
  drawRoundRect(
    color = colors.trunk,
    topLeft = Offset(base.x - 1.6f * scale, base.y - 11f * scale),
    size = Size(3.2f * scale, 11.5f * scale),
    cornerRadius = CornerRadius(1.1f * scale, 1.1f * scale),
  )
  drawCircle(
    color = colors.shadow,
    radius = 8.0f * scale,
    center = Offset(base.x - 5.0f * scale, base.y - 13f * scale),
  )
  drawCircle(
    color = colors.shadow,
    radius = 7.5f * scale,
    center = Offset(base.x + 5.0f * scale, base.y - 12.5f * scale),
  )
  drawCircle(
    color = colors.mid,
    radius = 9.4f * scale,
    center = Offset(base.x, base.y - 17.2f * scale),
  )
  drawCircle(
    color = colors.mid,
    radius = 6.8f * scale,
    center = Offset(base.x - 4.2f * scale, base.y - 14.8f * scale),
  )
  drawCircle(
    color = colors.highlight,
    radius = 6.2f * scale,
    center = Offset(base.x + 2.0f * scale, base.y - 19.0f * scale),
  )
}

private fun DrawScope.drawHeaderGrassTuft(base: Offset, scale: Float, color: Color) {
  val tuft =
    Path().apply {
      moveTo(base.x, base.y)
      quadraticTo(
        base.x - 3.6f * scale,
        base.y - 6.2f * scale,
        base.x - 8.2f * scale,
        base.y - 8.2f * scale,
      )
      quadraticTo(base.x - 1.8f * scale, base.y - 4.5f * scale, base.x, base.y)
      moveTo(base.x, base.y)
      quadraticTo(
        base.x - 0.9f * scale,
        base.y - 7.2f * scale,
        base.x - 1.8f * scale,
        base.y - 10.8f * scale,
      )
      quadraticTo(base.x + 0.9f * scale, base.y - 5.4f * scale, base.x, base.y)
      moveTo(base.x, base.y)
      quadraticTo(
        base.x + 3.6f * scale,
        base.y - 6.2f * scale,
        base.x + 7.2f * scale,
        base.y - 9.0f * scale,
      )
      quadraticTo(base.x + 1.8f * scale, base.y - 3.6f * scale, base.x, base.y)
    }
  drawPath(
    path = tuft,
    color = color,
    style = Stroke(width = 1.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}
