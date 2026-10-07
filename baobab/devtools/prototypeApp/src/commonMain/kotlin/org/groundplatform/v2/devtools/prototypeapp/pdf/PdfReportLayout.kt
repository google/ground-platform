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
package org.groundplatform.v2.devtools.prototypeapp.pdf

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** A generated PDF file, ready to save, preview, or share. */
internal class GeneratedPdf(val fileName: String, val bytes: ByteArray, val pageCount: Int) {
  /** File size for display, e.g. `"14 KB"`. */
  val sizeLabel: String
    get() =
      when {
        bytes.size < 1024 -> "${bytes.size} B"
        bytes.size < 1024 * 1024 -> "${(bytes.size + 512) / 1024} KB"
        else -> "${((bytes.size * 10L) / (1024 * 1024)) / 10.0} MB"
      }

  /** Short summary for display, e.g. `"2 pages • 14 KB"`. */
  val summaryLabel: String
    get() = "$pageCount ${if (pageCount == 1) "page" else "pages"} • $sizeLabel"
}

/** Palette used by Ground reports, aligned with `GroundTheme`'s light color scheme. */
internal object PdfPalette {
  val primary = PdfColor.fromArgb(0xFF36693E)
  val onSurface = PdfColor.fromArgb(0xFF181D18)
  val onSurfaceVariant = PdfColor.fromArgb(0xFF424940)
  val outline = PdfColor.fromArgb(0xFF727970)
  val outlineVariant = PdfColor.fromArgb(0xFFC1C9BE)
  val surfaceContainer = PdfColor.fromArgb(0xFFEBEFE7)
  val error = PdfColor.fromArgb(0xFFBA1A1A)
  val tertiary = PdfColor.fromArgb(0xFF39656C)
}

/** Lines of [text] wrapped to [maxWidth] points; blank input lines are kept as empty lines. */
internal fun wrapText(text: String, font: PdfFont, size: Float, maxWidth: Float): List<String> {
  val lines = mutableListOf<String>()
  text.replace("\r\n", "\n").split('\n').forEach { paragraph ->
    var line = ""
    for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
      val candidate = if (line.isEmpty()) word else "$line $word"
      if (font.measure(candidate, size) <= maxWidth) {
        line = candidate
        continue
      }
      if (line.isNotEmpty()) lines += line
      // Break words longer than a full line (IDs, URLs, coordinates) at character boundaries.
      var rest = word
      while (rest.length > 1 && font.measure(rest, size) > maxWidth) {
        var take = rest.length - 1
        while (take > 1 && font.measure(rest.substring(0, take), size) > maxWidth) take--
        lines += rest.substring(0, take)
        rest = rest.substring(take)
      }
      line = rest
    }
    lines += line
  }
  return lines
}

/**
 * Flowing, top-to-bottom layout of a report on A4 pages: headings, label/value rows, paragraphs,
 * status chips, and a geometry figure, with automatic page breaks plus a running header and a `Page
 * n of N` footer on every page.
 */
internal class PdfReportLayout(
  title: String,
  subject: String,
  private val runningHeader: String,
  private val footerNote: String,
) {
  private val writer = PdfDocumentWriter(title = title, subject = subject)
  val pageWidth = PdfDocumentWriter.A4_WIDTH
  val pageHeight = PdfDocumentWriter.A4_HEIGHT
  val left = MARGIN
  val right = pageWidth - MARGIN
  val contentWidth = right - left
  private val contentTop = MARGIN + 26f
  private val contentBottom = pageHeight - MARGIN - 20f

  /** The page being laid out. */
  lateinit var page: PdfPage
    private set

  /** Top of the next element on [page]. */
  var y: Float = 0f
    private set

  init {
    newPage()
  }

  /** Starts a new page. */
  fun newPage() {
    page = writer.addPage(pageWidth, pageHeight)
    y = contentTop
  }

  /** Breaks to a new page unless [height] points still fit on the current one. */
  fun ensureSpace(height: Float) {
    if (y + height > contentBottom && y > contentTop) newPage()
  }

  fun spacer(height: Float) {
    y += height
  }

  /** Large bold document title. */
  fun title(text: String) {
    val lines = wrapText(text, PdfFont.BOLD, 20f, contentWidth)
    lines.forEach { line ->
      ensureSpace(24f)
      page.text(left, y + 18f, line, PdfFont.BOLD, 20f, PdfPalette.onSurface)
      y += 24f
    }
  }

  /** Muted line(s) below the title. */
  fun subtitle(text: String) {
    paragraph(text, size = 11f, color = PdfPalette.onSurfaceVariant)
  }

  /** Wrapped text block. */
  fun paragraph(
    text: String,
    font: PdfFont = PdfFont.REGULAR,
    size: Float = 10f,
    color: PdfColor = PdfPalette.onSurface,
    indent: Float = 0f,
  ) {
    val leading = size * 1.35f
    wrapText(text, font, size, contentWidth - indent).forEach { line ->
      ensureSpace(leading)
      page.text(left + indent, y + size, line, font, size, color)
      y += leading
    }
  }

  /** Uppercase section heading with a rule below; never left alone at the bottom of a page. */
  fun sectionHeading(text: String) {
    ensureSpace(64f)
    y += 14f
    page.text(left, y + 9f, text.uppercase(), PdfFont.BOLD, 9.5f, PdfPalette.primary)
    y += 14f
    page.line(left, y, right, y, PdfPalette.primary, 0.9f)
    y += 6f
  }

  /** Bold sub-heading (e.g. one submission inside an entity report), with optional muted meta. */
  fun subheading(text: String, meta: String = "") {
    ensureSpace(48f)
    y += 6f
    paragraph(text, PdfFont.BOLD, 11f, PdfPalette.onSurface)
    if (meta.isNotBlank()) paragraph(meta, size = 8.5f, color = PdfPalette.onSurfaceVariant)
    y += 2f
  }

  /** Small muted group label used for XForms groups within a list of fields. */
  fun groupLabel(text: String) {
    ensureSpace(36f)
    y += 4f
    page.text(left, y + 8.5f, text, PdfFont.BOLD, 8.5f, PdfPalette.tertiary)
    y += 13f
  }

  /**
   * Two-column label/value row with a hairline separator. Long values wrap and may continue on the
   * next page.
   */
  fun field(label: String, value: String, mono: Boolean = false) {
    val labelWidth = contentWidth * 0.34f
    val valueX = left + labelWidth + 12f
    val valueWidth = right - valueX
    val labelLines = wrapText(label, PdfFont.REGULAR, 9f, labelWidth)
    val valueFont = if (mono) PdfFont.MONO else PdfFont.REGULAR
    val valueSize = if (mono) 9f else 10f
    val valueLines = wrapText(value.ifBlank { "—" }, valueFont, valueSize, valueWidth)
    val leading = 13.5f
    val rows = max(labelLines.size, valueLines.size)
    ensureSpace(minOf(rows, 3) * leading + 8f)
    y += 4f
    for (i in 0 until rows) {
      ensureSpace(leading)
      labelLines.getOrNull(i)?.let {
        page.text(left, y + 9.5f, it, PdfFont.REGULAR, 9f, PdfPalette.onSurfaceVariant)
      }
      valueLines.getOrNull(i)?.let {
        val color = if (value.isBlank()) PdfPalette.outline else PdfPalette.onSurface
        page.text(valueX, y + 9.5f, it, valueFont, valueSize, color)
      }
      y += leading
    }
    y += 3f
    page.line(left, y, right, y, PdfPalette.outlineVariant, 0.5f)
  }

  /**
   * Workflow status chip filled with [statusColor], showing the `simplestyle-spec` [markerSymbol]
   * as a vector glyph followed by [statusText].
   */
  fun statusChip(markerSymbol: String, statusText: String, statusColor: PdfColor) {
    ensureSpace(28f)
    val h = 18f
    val top = y + 4f
    val onStatus = if (statusColor.isDark) PdfColor.WHITE else PdfPalette.onSurface
    val hasGlyph = markerSymbol.isNotBlank()
    val textWidth = PdfFont.BOLD.measure(statusText, 9f)
    val statusWidth = textWidth + 16f + (if (hasGlyph) 14f else 0f)
    page.roundedRect(left, top, statusWidth, h, h / 2f, fill = statusColor)
    var textX = left + 8f
    if (hasGlyph) {
      drawMarkerGlyph(markerSymbol, left + 14f, top + h / 2f, 4.5f, onStatus)
      textX += 14f
    }
    page.text(textX, top + 12.5f, statusText, PdfFont.BOLD, 9f, onStatus)
    y = top + h + 6f
  }

  /** Draws Ground's workflow marker symbols (`○`, `◐`, `✓`) as vector shapes. */
  private fun drawMarkerGlyph(symbol: String, cx: Float, cy: Float, r: Float, color: PdfColor) {
    when (symbol.trim()) {
      "○",
      "◯" -> page.circle(cx, cy, r, fill = null, stroke = color, lineWidth = 1.2f)
      "◐" -> {
        page.leftHalfCircle(cx, cy, r, color)
        page.circle(cx, cy, r, fill = null, stroke = color, lineWidth = 1.2f)
      }
      "●" -> page.circle(cx, cy, r, fill = color)
      "✓",
      "✔" ->
        page.polyline(
          listOf(cx - r to cy, cx - r * 0.3f to cy + r * 0.75f, cx + r to cy - r * 0.8f),
          closed = false,
          stroke = color,
          lineWidth = 1.6f,
        )
      else -> {
        val text = WinAnsi.normalize(symbol).take(2)
        val w = PdfFont.BOLD.measure(text, 9f)
        page.text(cx - w / 2f, cy + 3.2f, text, PdfFont.BOLD, 9f, color)
      }
    }
  }

  /**
   * Draws a map-style figure of a geometry: [paths] of (longitude, latitude) vertices, filled and
   * closed when [closed] (polygons), or a single-vertex path drawn as a point marker. Includes a
   * north arrow, a scale bar, and the bounding coordinates.
   */
  fun geometryFigure(
    paths: List<List<Pair<Double, Double>>>,
    closed: Boolean,
    color: PdfColor,
    height: Float = 190f,
  ) {
    val all = paths.flatten()
    if (all.isEmpty()) return
    ensureSpace(height + 26f)
    y += 6f
    val boxTop = y
    page.fillRect(left, boxTop, contentWidth, height, PdfPalette.surfaceContainer)
    page.strokeRect(left, boxTop, contentWidth, height, PdfPalette.outlineVariant, 0.6f)

    val minLng = all.minOf { it.first }
    val maxLng = all.maxOf { it.first }
    val minLat = all.minOf { it.second }
    val maxLat = all.maxOf { it.second }
    val midLat = (minLat + maxLat) / 2
    val lngScale = cos(midLat * PI / 180).coerceAtLeast(0.01)
    // Equirectangular projection: x = lng * cos(midLat), y = lat (degrees). A minimum span keeps
    // points and tiny features from being scaled up without limit.
    val spanX = max((maxLng - minLng) * lngScale, MIN_SPAN_DEGREES)
    val spanY = max(maxLat - minLat, MIN_SPAN_DEGREES)
    val pad = 22f
    val scale = minOf((contentWidth - 2 * pad) / spanX, (height - 2 * pad) / spanY)
    val centerX = (minLng + maxLng) / 2 * lngScale
    val centerY = (minLat + maxLat) / 2
    val boxCenterX = left + contentWidth / 2f
    val boxCenterY = boxTop + height / 2f
    fun project(p: Pair<Double, Double>): Pair<Float, Float> =
      (boxCenterX + ((p.first * lngScale - centerX) * scale).toFloat()) to
        (boxCenterY - ((p.second - centerY) * scale).toFloat())

    paths.forEach { path ->
      val points = path.map(::project)
      if (points.size == 1) {
        val (px, py) = points.first()
        page.circle(px, py, 9f, fill = color.tint(0.6f), stroke = null)
        page.circle(px, py, 4.5f, fill = color, stroke = PdfColor.WHITE, lineWidth = 1.2f)
      } else {
        page.polyline(
          points,
          closed = closed,
          stroke = color,
          fill = if (closed) color.tint(0.7f) else null,
          lineWidth = if (closed) 1.4f else 2.2f,
        )
        points.forEach { (px, py) ->
          page.circle(px, py, 2.2f, fill = PdfColor.WHITE, stroke = color, lineWidth = 0.9f)
        }
      }
    }

    // North arrow (upper right).
    val nx = right - 16f
    val ny = boxTop + 12f
    page.polyline(
      listOf(nx to ny, nx + 5f to ny + 14f, nx to ny + 10f, nx - 5f to ny + 14f),
      closed = true,
      stroke = null,
      fill = PdfPalette.onSurfaceVariant,
    )
    page.text(nx - 3f, ny + 25f, "N", PdfFont.BOLD, 8f, PdfPalette.onSurfaceVariant)

    // Scale bar (lower left): a "nice" length close to a quarter of the box width.
    val metersPerPoint = METERS_PER_DEGREE / scale
    val targetMeters = metersPerPoint * contentWidth / 4
    val magnitude = 10.0.pow(floor(log10(targetMeters)))
    val niceMeters =
      listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.last { it <= targetMeters * 1.0001 }
    val barWidth = (niceMeters / metersPerPoint).toFloat()
    val bx = left + 12f
    val by = boxTop + height - 12f
    page.fillRect(bx, by - 3f, barWidth, 3f, PdfPalette.onSurfaceVariant)
    val scaleLabel =
      if (niceMeters >= 1000) "${trimNumber(niceMeters / 1000)} km"
      else "${trimNumber(niceMeters)} m"
    page.text(bx, by - 6f, scaleLabel, PdfFont.REGULAR, 7.5f, PdfPalette.onSurfaceVariant)

    y = boxTop + height + 4f
    val bounds =
      if (all.size == 1) {
        "Location ${formatCoordinate(all.first().second)}, ${formatCoordinate(all.first().first)}"
      } else {
        "Bounds: lat ${formatCoordinate(minLat)} to ${formatCoordinate(maxLat)}, " +
          "lng ${formatCoordinate(minLng)} to ${formatCoordinate(maxLng)} (WGS 84)"
      }
    paragraph(bounds, size = 8f, color = PdfPalette.onSurfaceVariant)
  }

  /** Lays out every page's running header and `Page n of N` footer, then serializes the file. */
  fun finish(fileName: String): GeneratedPdf {
    val pages = writer.allPages
    pages.forEachIndexed { index, p ->
      p.text(left, MARGIN + 8f, runningHeader, PdfFont.BOLD, 8.5f, PdfPalette.primary)
      p.line(left, MARGIN + 14f, right, MARGIN + 14f, PdfPalette.outlineVariant, 0.5f)
      val footerY = pageHeight - MARGIN + 4f
      p.line(left, footerY - 12f, right, footerY - 12f, PdfPalette.outlineVariant, 0.5f)
      p.text(left, footerY, footerNote, PdfFont.REGULAR, 7.5f, PdfPalette.onSurfaceVariant)
      val pageLabel = "Page ${index + 1} of ${pages.size}"
      val w = PdfFont.REGULAR.measure(pageLabel, 7.5f)
      p.text(right - w, footerY, pageLabel, PdfFont.REGULAR, 7.5f, PdfPalette.onSurfaceVariant)
    }
    return GeneratedPdf(fileName, writer.toByteArray(), writer.pageCount)
  }

  private companion object {
    const val MARGIN = 48f
    const val METERS_PER_DEGREE = 111_320.0
    const val MIN_SPAN_DEGREES = 0.0015
  }
}

/** Formats a coordinate in degrees with six decimals (about 0.1 m), without an exponent. */
internal fun formatCoordinate(degrees: Double): String {
  val scaled = kotlin.math.round(kotlin.math.abs(degrees) * 1_000_000).toLong()
  val sign = if (degrees < 0 && scaled != 0L) "-" else ""
  return "$sign${scaled / 1_000_000}.${(scaled % 1_000_000).toString().padStart(6, '0')}"
}

private fun trimNumber(value: Double): String {
  val rounded = kotlin.math.round(value * 10) / 10
  return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
}
