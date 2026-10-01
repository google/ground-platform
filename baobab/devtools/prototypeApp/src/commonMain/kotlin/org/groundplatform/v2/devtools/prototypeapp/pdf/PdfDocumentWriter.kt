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

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Minimal, dependency-free PDF 1.4 writer in pure Kotlin `commonMain`, so the same code generates
 * PDFs offline on every target (web, Android, iOS, and the JVM).
 *
 * Scope is deliberately small: vector shapes, and text in three of the standard 14 fonts
 * (Helvetica, Helvetica-Bold, Courier) with `WinAnsiEncoding`. Standard fonts need no embedding
 * (ISO 32000-1 section 9.6.2.2), which keeps files small and the writer simple. Characters outside
 * WinAnsi (Windows-1252) are transliterated or replaced by [WinAnsi.encode].
 *
 * Coordinates passed to [PdfPage] use a top-left origin with y growing downwards, in PDF points
 * (1/72 inch); the page flips them into PDF user space. Content streams are left uncompressed and
 * every byte of the file is 7-bit ASCII, so cross-reference offsets equal string offsets.
 */
internal class PdfDocumentWriter(
  private val title: String = "",
  private val author: String = "",
  private val subject: String = "",
  private val creator: String = "Ground",
) {
  private val pages = mutableListOf<PdfPage>()

  /** Number of pages added so far. */
  val pageCount: Int
    get() = pages.size

  /** Pages added so far, in order. */
  val allPages: List<PdfPage>
    get() = pages

  /** Appends a new blank page of the given size (default A4 portrait) and returns it. */
  fun addPage(width: Float = A4_WIDTH, height: Float = A4_HEIGHT): PdfPage =
    PdfPage(width, height).also { pages += it }

  /** Serializes the document. A document without pages gets one blank A4 page. */
  fun toByteArray(): ByteArray = toPdfString().encodeToByteArray()

  internal fun toPdfString(): String {
    if (pages.isEmpty()) addPage()
    val fonts = PdfFont.entries
    // Object numbers: 1 catalog, 2 page tree, 3 info, then fonts, then (page, content) pairs.
    val firstFontObject = 4
    val firstPageObject = firstFontObject + fonts.size
    val objectCount = firstPageObject + pages.size * 2 - 1
    val offsets = IntArray(objectCount + 1)
    val out = StringBuilder()
    out.append("%PDF-1.4\n")

    fun writeObject(number: Int, body: String) {
      offsets[number] = out.length
      out.append(number).append(" 0 obj\n").append(body).append("\nendobj\n")
    }

    writeObject(1, "<< /Type /Catalog /Pages 2 0 R >>")
    val kids = pages.indices.joinToString(" ") { "${firstPageObject + it * 2} 0 R" }
    writeObject(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
    writeObject(
      3,
      buildString {
        append("<< /Producer ").append(pdfLiteral("Ground PDF writer"))
        append(" /Creator ").append(pdfLiteral(creator))
        if (title.isNotBlank()) append(" /Title ").append(pdfLiteral(title))
        if (author.isNotBlank()) append(" /Author ").append(pdfLiteral(author))
        if (subject.isNotBlank()) append(" /Subject ").append(pdfLiteral(subject))
        append(" >>")
      },
    )
    val fontResources = fonts.mapIndexed { i, font ->
      "/${font.resourceName} ${firstFontObject + i} 0 R"
    }
    fonts.forEachIndexed { i, font ->
      writeObject(
        firstFontObject + i,
        "<< /Type /Font /Subtype /Type1 /BaseFont /${font.baseFont}" +
          " /Encoding /WinAnsiEncoding >>",
      )
    }
    pages.forEachIndexed { i, page ->
      val pageObject = firstPageObject + i * 2
      val contentObject = pageObject + 1
      writeObject(
        pageObject,
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(page.width)} ${fmt(page.height)}]" +
          " /Resources << /Font << ${fontResources.joinToString(" ")} >> >>" +
          " /Contents $contentObject 0 R >>",
      )
      val stream = page.content.toString()
      writeObject(contentObject, "<< /Length ${stream.length} >>\nstream\n${stream}\nendstream")
    }

    val xrefOffset = out.length
    out.append("xref\n0 ").append(objectCount + 1).append('\n')
    out.append("0000000000 65535 f \n")
    for (n in 1..objectCount) {
      out.append(offsets[n].toString().padStart(10, '0')).append(" 00000 n \n")
    }
    out.append("trailer\n<< /Size ").append(objectCount + 1).append(" /Root 1 0 R /Info 3 0 R >>\n")
    out.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")
    return out.toString()
  }

  companion object {
    /** ISO 216 A4 portrait, in points. */
    const val A4_WIDTH = 595.28f
    const val A4_HEIGHT = 841.89f
  }
}

/** The standard 14 fonts used by Ground PDFs; none need embedding. */
internal enum class PdfFont(val resourceName: String, val baseFont: String) {
  REGULAR("F1", "Helvetica"),
  BOLD("F2", "Helvetica-Bold"),
  MONO("F3", "Courier");

  /** Advance width of [text] at [size] points, after WinAnsi encoding. */
  fun measure(text: String, size: Float): Float {
    var total = 0
    for (code in WinAnsi.encode(text)) total += widthOf(code.toInt() and 0xFF)
    return total * size / 1000f
  }

  /** Advance width of a WinAnsi [code] in 1/1000 em, from the Adobe Font Metrics (AFM) files. */
  internal fun widthOf(code: Int): Int {
    if (this == MONO) return 600
    val ascii = if (this == BOLD) HELVETICA_BOLD_ASCII else HELVETICA_ASCII
    return when {
      code in 32..126 -> ascii[code - 32]
      code >= 0xC0 -> {
        val base = LATIN1_LETTER_BASES[code - 0xC0]
        if (base != '*') ascii[base.code - 32] else latin1Special(code)
      }
      else -> highPunctuationWidth(code)
    }
  }

  private fun latin1Special(code: Int): Int =
    when (code) {
      0xC6 -> 1000 // Æ
      0xDF -> 611 // ß
      0xE6 -> 889 // æ
      else -> 584 // × and ÷
    }

  private fun highPunctuationWidth(code: Int): Int =
    when (code) {
      0x85,
      0x89,
      0x8C,
      0x97,
      0x99 -> 1000 // … ‰ Œ — ™
      0x9C -> 944 // œ
      0x91,
      0x92 -> if (this == BOLD) 278 else 222 // ‘ ’
      0x93,
      0x94 -> if (this == BOLD) 500 else 333 // “ ”
      0x95 -> 350 // •
      0xA0 -> 278 // no-break space
      0xB0 -> 400 // °
      0xB7 -> 278 // ·
      0xA9,
      0xAE -> 737 // © ®
      0xA6 -> if (this == BOLD) 280 else 260 // ¦
      0xA8,
      0xAF,
      0xB4,
      0xB8,
      0xB2,
      0xB3,
      0xB9 -> 333
      0xBC,
      0xBD,
      0xBE -> 834 // ¼ ½ ¾
      0x8A,
      0x9F -> 667 // Š Ÿ
      0x8E -> 611 // Ž
      0x9A,
      0x9E -> 500 // š ž
      else -> 556
    }

  private companion object {
    // Helvetica.afm widths for codes 32..126.
    val HELVETICA_ASCII =
      intArrayOf(
        278,
        278,
        355,
        556,
        556,
        889,
        667,
        191,
        333,
        333,
        389,
        584,
        278,
        333,
        278,
        278, // 32-47
        556,
        556,
        556,
        556,
        556,
        556,
        556,
        556,
        556,
        556, // 0-9
        278,
        278,
        584,
        584,
        584,
        556,
        1015, // 58-64
        667,
        667,
        722,
        722,
        667,
        611,
        778,
        722,
        278,
        500,
        667,
        556,
        833, // A-M
        722,
        778,
        667,
        778,
        722,
        667,
        611,
        722,
        667,
        944,
        667,
        667,
        611, // N-Z
        278,
        278,
        278,
        469,
        556,
        333, // 91-96
        556,
        556,
        500,
        556,
        556,
        278,
        556,
        556,
        222,
        222,
        500,
        222,
        833, // a-m
        556,
        556,
        556,
        556,
        333,
        500,
        278,
        556,
        500,
        722,
        500,
        500,
        500, // n-z
        334,
        260,
        334,
        584, // 123-126
      )

    // Helvetica-Bold.afm widths for codes 32..126.
    val HELVETICA_BOLD_ASCII =
      intArrayOf(
        278,
        333,
        474,
        556,
        556,
        889,
        722,
        238,
        333,
        333,
        389,
        584,
        278,
        333,
        278,
        278, // 32-47
        556,
        556,
        556,
        556,
        556,
        556,
        556,
        556,
        556,
        556, // 0-9
        333,
        333,
        584,
        584,
        584,
        611,
        975, // 58-64
        722,
        722,
        722,
        722,
        667,
        611,
        778,
        722,
        278,
        556,
        722,
        611,
        833, // A-M
        722,
        778,
        667,
        778,
        722,
        667,
        611,
        722,
        667,
        944,
        667,
        667,
        611, // N-Z
        333,
        278,
        333,
        584,
        556,
        333, // 91-96
        556,
        611,
        556,
        611,
        556,
        333,
        611,
        611,
        278,
        278,
        556,
        278,
        889, // a-m
        611,
        611,
        611,
        611,
        389,
        556,
        333,
        611,
        556,
        778,
        556,
        556,
        500, // n-z
        389,
        280,
        389,
        584, // 123-126
      )

    /**
     * For Latin-1 codes 0xC0..0xFF, the unaccented ASCII letter with the same advance width, or `*`
     * for the few glyphs with their own width ([latin1Special]).
     */
    const val LATIN1_LETTER_BASES =
      "AAAAAA*CEEEEIIIIDNOOOOO*OUUUUYP*aaaaaa*ceeeeiiiionooooo*ouuuuypy"
  }
}

/** An RGB color with components in `0f..1f`. */
internal data class PdfColor(val red: Float, val green: Float, val blue: Float) {
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
internal class PdfPage(val width: Float, val height: Float) {
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
 * Windows-1252 (`WinAnsiEncoding`, ISO 32000-1 Annex D) encoder for the standard fonts, with a
 * small transliteration table for symbols common in Ground data that have no WinAnsi glyph.
 */
internal object WinAnsi {
  /** Unicode → WinAnsi for the 0x80..0x9F range, where Windows-1252 differs from Latin-1. */
  private val HIGH_PUNCTUATION: Map<Char, Int> =
    mapOf(
      '€' to 0x80,
      '‚' to 0x82,
      'ƒ' to 0x83,
      '„' to 0x84,
      '…' to 0x85,
      '†' to 0x86,
      '‡' to 0x87,
      'ˆ' to 0x88,
      '‰' to 0x89,
      'Š' to 0x8A,
      '‹' to 0x8B,
      'Œ' to 0x8C,
      'Ž' to 0x8E,
      '‘' to 0x91,
      '’' to 0x92,
      '“' to 0x93,
      '”' to 0x94,
      '•' to 0x95,
      '–' to 0x96,
      '—' to 0x97,
      '˜' to 0x98,
      '™' to 0x99,
      'š' to 0x9A,
      '›' to 0x9B,
      'œ' to 0x9C,
      'ž' to 0x9E,
      'Ÿ' to 0x9F,
    )

  /** Readable ASCII stand-ins for common symbols that WinAnsi can't represent. */
  private val TRANSLITERATIONS: Map<String, String> =
    mapOf(
      "✓" to "v",
      "✔" to "v",
      "☑" to "[x]",
      "☐" to "[ ]",
      "✗" to "x",
      "✘" to "x",
      "○" to "o",
      "◯" to "o",
      "●" to "*",
      "◐" to "*",
      "◑" to "*",
      "◉" to "*",
      "≤" to "<=",
      "≥" to ">=",
      "≠" to "!=",
      "≈" to "~",
      "→" to "->",
      "←" to "<-",
      "↔" to "<->",
      "↑" to "^",
      "↓" to "v",
      "➤" to ">",
      "▶" to ">",
      "▲" to "^",
      "▼" to "v",
      "−" to "-",
      "‐" to "-",
      "‑" to "-",
      "′" to "'",
      "″" to "\"",
      "\u2009" to " ",
      "\u202F" to " ",
      "\u2007" to " ",
      "\uD83D\uDCF7" to "[photo]", // 📷
      "\uD83D\uDCF8" to "[photo]", // 📸
      "\uD83C\uDFA4" to "[audio]", // 🎤
      "\uD83C\uDF99" to "[audio]", // 🎙
      "\uD83C\uDFA5" to "[video]", // 🎥
      "\uD83D\uDCF9" to "[video]", // 📹
      "\uD83D\uDCCD" to "[location]", // 📍
      "\uD83D\uDCCE" to "[file]", // 📎
    )

  /** Characters that are silently dropped (joiners, variation selectors, and BOMs). */
  private fun isIgnorable(c: Char): Boolean =
    c == '\u200B' ||
      c == '\u200C' ||
      c == '\u200D' ||
      c == '\uFEFF' ||
      c in '\uFE00'..'\uFE0F' ||
      c in '\u0300'..'\u036F' // combining diacritics

  /** Encodes [text] as WinAnsi bytes, replacing unsupported characters. */
  fun encode(text: String): ByteArray {
    val out = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
      val c = text[i]
      val isPair = c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
      val unit = if (isPair) text.substring(i, i + 2) else c.toString()
      i += unit.length
      val code = c.code
      when {
        c == '\t' -> repeat(4) { out += ' '.code.toByte() }
        c == '\n' || c == '\r' -> out += ' '.code.toByte()
        !isPair && (code in 0x20..0x7E || code in 0xA0..0xFF) -> out += code.toByte()
        !isPair && c in HIGH_PUNCTUATION -> out += HIGH_PUNCTUATION.getValue(c).toByte()
        !isPair && isIgnorable(c) -> Unit
        unit in TRANSLITERATIONS ->
          TRANSLITERATIONS.getValue(unit).forEach { out += it.code.toByte() }
        else -> out += '?'.code.toByte()
      }
    }
    return out.toByteArray()
  }

  /** [text] as it will render, i.e. after transliteration (decoded back to Unicode). */
  fun normalize(text: String): String {
    val reverse = HIGH_PUNCTUATION.entries.associate { (k, v) -> v to k }
    return encode(text)
      .map { byte ->
        val code = byte.toInt() and 0xFF
        reverse[code] ?: code.toChar()
      }
      .joinToString("")
  }
}

/** Encodes [text] as a PDF literal string with 7-bit ASCII escapes (ISO 32000-1 7.3.4.2). */
internal fun pdfLiteral(text: String): String {
  val bytes = WinAnsi.encode(text)
  val sb = StringBuilder(bytes.size + 2)
  sb.append('(')
  for (b in bytes) {
    val code = b.toInt() and 0xFF
    when {
      code == '('.code || code == ')'.code || code == '\\'.code ->
        sb.append('\\').append(code.toChar())
      code in 0x20..0x7E -> sb.append(code.toChar())
      else -> sb.append('\\').append(code.toString(8).padStart(3, '0'))
    }
  }
  sb.append(')')
  return sb.toString()
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
