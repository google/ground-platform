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
package org.openforis.collectone.v2.app.client.pdf

import kotlin.math.abs
import kotlin.math.roundToLong

/** An RGB color with components in `0f..1f`. */
data class PdfColor(val red: Float, val green: Float, val blue: Float) {
  /** A lighter tint of this color, mixing in [amount] (`0f..1f`) of white. */
  fun tint(amount: Float): PdfColor =
    PdfColor(red + (1f - red) * amount, green + (1f - green) * amount, blue + (1f - blue) * amount)

  /** Relative luminance approximation, used to choose readable text on top of this color. */
  val isDark: Boolean
    get() = 0.299f * red + 0.587f * green + 0.114f * blue < 0.6f

  companion object {
    val BLACK = PdfColor(0f, 0f, 0f)
    val WHITE = PdfColor(1f, 1f, 1f)

    /** Builds a color from a Compose-style ARGB `Long` (alpha is ignored). */
    fun fromArgb(argb: Long): PdfColor =
      PdfColor(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
      )

    /** Parses `#RGB` or `#RRGGBB`, or returns [fallback] for anything else. */
    fun fromCss(css: String?, fallback: PdfColor): PdfColor {
      val hex = css?.trim()?.removePrefix("#") ?: return fallback
      val full =
        when (hex.length) {
          3 -> hex.map { "$it$it" }.joinToString("")
          6 -> hex
          else -> return fallback
        }
      val value = full.toLongOrNull(16) ?: return fallback
      return fromArgb(value)
    }
  }
}

/**
 * One page's content stream. All coordinates use a top-left origin with y growing downwards; text
 * positions refer to the baseline.
 */
class PdfPage(val width: Float, val height: Float) {
  internal val content = StringBuilder()

  private fun y(top: Float): String = fmt(height - top)

  private fun fillColor(color: PdfColor) {
    content.append(fmt(color.red)).append(' ').append(fmt(color.green)).append(' ')
    content.append(fmt(color.blue)).append(" rg\n")
  }

  private fun strokeColor(color: PdfColor) {
    content.append(fmt(color.red)).append(' ').append(fmt(color.green)).append(' ')
    content.append(fmt(color.blue)).append(" RG\n")
  }

  /** Draws [text] with its baseline starting at ([x], [baseline]). */
  fun text(
    x: Float,
    baseline: Float,
    text: String,
    font: PdfFont = PdfFont.REGULAR,
    size: Float = 10f,
    color: PdfColor = PdfColor.BLACK,
  ) {
    if (text.isEmpty()) return
    content.append("BT\n")
    fillColor(color)
    content.append('/').append(font.resourceName).append(' ').append(fmt(size)).append(" Tf\n")
    content.append(fmt(x)).append(' ').append(y(baseline)).append(" Td\n")
    content.append(pdfLiteral(text)).append(" Tj\nET\n")
  }

  /** Fills a rectangle whose top-left corner is ([x], [top]). */
  fun fillRect(x: Float, top: Float, w: Float, h: Float, color: PdfColor) {
    fillColor(color)
    content.append(fmt(x)).append(' ').append(fmt(height - top - h)).append(' ')
    content.append(fmt(w)).append(' ').append(fmt(h)).append(" re f\n")
  }

  /** Strokes a rectangle whose top-left corner is ([x], [top]). */
  fun strokeRect(
    x: Float,
    top: Float,
    w: Float,
    h: Float,
    color: PdfColor,
    lineWidth: Float = 0.75f,
  ) {
    strokeColor(color)
    content.append(fmt(lineWidth)).append(" w\n")
    content.append(fmt(x)).append(' ').append(fmt(height - top - h)).append(' ')
    content.append(fmt(w)).append(' ').append(fmt(h)).append(" re S\n")
  }

  /** Fills (and optionally strokes) a rectangle with rounded corners of [radius]. */
  fun roundedRect(
    x: Float,
    top: Float,
    w: Float,
    h: Float,
    radius: Float,
    fill: PdfColor?,
    stroke: PdfColor? = null,
    lineWidth: Float = 0.75f,
  ) {
    val r = radius.coerceAtMost(minOf(w, h) / 2f)
    val k = r * (1f - BEZIER_CIRCLE)
    val right = x + w
    val bottom = top + h
    moveTo(x + r, top)
    lineTo(right - r, top)
    curveTo(right - k, top, right, top + k, right, top + r)
    lineTo(right, bottom - r)
    curveTo(right, bottom - k, right - k, bottom, right - r, bottom)
    lineTo(x + r, bottom)
    curveTo(x + k, bottom, x, bottom - k, x, bottom - r)
    lineTo(x, top + r)
    curveTo(x, top + k, x + k, top, x + r, top)
    paint(fill, stroke, lineWidth, close = true)
  }

  /** Draws a straight line. */
  fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: PdfColor, lineWidth: Float = 0.75f) {
    moveTo(x1, y1)
    lineTo(x2, y2)
    paint(fill = null, stroke = color, lineWidth = lineWidth, close = false)
  }

  /** Draws a polyline through [points] (x, y pairs), closing and filling it when [closed]. */
  fun polyline(
    points: List<Pair<Float, Float>>,
    closed: Boolean,
    stroke: PdfColor?,
    fill: PdfColor? = null,
    lineWidth: Float = 1f,
  ) {
    if (points.isEmpty()) return
    moveTo(points.first().first, points.first().second)
    points.drop(1).forEach { (px, py) -> lineTo(px, py) }
    paint(fill = if (closed) fill else null, stroke = stroke, lineWidth = lineWidth, close = closed)
  }

  /** Draws a circle centered on ([cx], [cy]). */
  fun circle(
    cx: Float,
    cy: Float,
    r: Float,
    fill: PdfColor?,
    stroke: PdfColor? = null,
    lineWidth: Float = 0.75f,
  ) {
    val k = r * BEZIER_CIRCLE
    moveTo(cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    paint(fill, stroke, lineWidth, close = true)
  }

  /** Fills the left half of a circle centered on ([cx], [cy]). */
  fun leftHalfCircle(cx: Float, cy: Float, r: Float, fill: PdfColor) {
    val k = r * BEZIER_CIRCLE
    moveTo(cx, cy - r)
    curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy)
    curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r)
    paint(fill = fill, stroke = null, lineWidth = 0f, close = true)
  }

  private fun moveTo(x: Float, top: Float) {
    content.append(fmt(x)).append(' ').append(y(top)).append(" m\n")
  }

  private fun lineTo(x: Float, top: Float) {
    content.append(fmt(x)).append(' ').append(y(top)).append(" l\n")
  }

  private fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    content.append(fmt(x1)).append(' ').append(y(y1)).append(' ')
    content.append(fmt(x2)).append(' ').append(y(y2)).append(' ')
    content.append(fmt(x3)).append(' ').append(y(y3)).append(" c\n")
  }

  private fun paint(fill: PdfColor?, stroke: PdfColor?, lineWidth: Float, close: Boolean) {
    if (fill != null) fillColor(fill)
    if (stroke != null) {
      strokeColor(stroke)
      content.append(fmt(lineWidth)).append(" w 1 j 1 J\n")
    }
    val op =
      when {
        fill != null && stroke != null -> if (close) "b" else "B"
        fill != null -> "f"
        stroke != null -> if (close) "s" else "S"
        else -> "n"
      }
    content.append(op).append('\n')
  }

  private companion object {
    /** Control point distance for approximating a quarter circle with a cubic Bézier curve. */
    const val BEZIER_CIRCLE = 0.5523f
  }
}

/**
 * Formats a number for a content stream with at most two decimals and no exponent, independent of
 * the platform's `Float.toString()` (which differs between the JVM, JS, and Wasm).
 */
internal fun fmt(value: Float): String {
  val scaled = (value * 100.0).roundToLong()
  val negative = scaled < 0
  val magnitude = abs(scaled)
  val whole = magnitude / 100
  val fraction = (magnitude % 100).toInt()
  val sign = if (negative && magnitude != 0L) "-" else ""
  return when {
    fraction == 0 -> "$sign$whole"
    fraction % 10 == 0 -> "$sign$whole.${fraction / 10}"
    else -> "$sign$whole.${fraction.toString().padStart(2, '0')}"
  }
}
