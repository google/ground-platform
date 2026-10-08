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
package org.groundplatform.v2.devtools.prototypeapp.client.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Structural tests for the dependency-free [PdfDocumentWriter] and its helpers. */
class PdfDocumentWriterTest {

  @Test
  fun emptyDocument_isAValidSinglePagePdf() {
    val pdf = PdfDocumentWriter().toPdfString()

    assertTrue(pdf.startsWith("%PDF-1.4\n"))
    assertTrue(pdf.endsWith("%%EOF\n"))
    assertTrue(pdf.contains("/Type /Pages /Kids [7 0 R] /Count 1"))
    assertXrefOffsetsPointAtObjects(pdf)
  }

  @Test
  fun multiPageDocument_hasConsistentPageTreeAndXref() {
    val writer = PdfDocumentWriter(title = "Report (draft)")
    repeat(3) { i -> writer.addPage().text(48f, 60f, "Page ${i + 1}") }
    val pdf = writer.toPdfString()

    assertTrue(pdf.contains("/Count 3"))
    assertEquals(3, Regex("/Type /Page /Parent").findAll(pdf).count())
    assertTrue(pdf.contains("/Title (Report \\(draft\\))"))
    assertXrefOffsetsPointAtObjects(pdf)
  }

  @Test
  fun contentStreamLength_matchesStreamBytes() {
    val writer = PdfDocumentWriter()
    writer.addPage().apply {
      text(48f, 60f, "Hello, Ground")
      circle(100f, 100f, 10f, fill = PdfColor.BLACK)
      roundedRect(48f, 120f, 100f, 20f, 10f, fill = PdfPalette.primary)
    }
    val pdf = writer.toPdfString()

    val match = Regex("<< /Length (\\d+) >>\nstream\n").find(pdf)!!
    val start = match.range.last + 1
    val end = pdf.indexOf("\nendstream", start)
    assertEquals(match.groupValues[1].toInt(), end - start)
  }

  @Test
  fun output_isSevenBitAscii() {
    val writer = PdfDocumentWriter(title = "Café • Nyeri")
    writer.addPage().text(48f, 60f, "Résumé — 25°C ± 2 • ✓ 📷 ≤10m 中文")
    val bytes = writer.toByteArray()

    assertTrue(bytes.all { it >= 0 }, "Every byte should be 7-bit ASCII")
  }

  @Test
  fun pdfLiteral_escapesDelimitersAndEncodesWinAnsiAsOctal() {
    assertEquals("(a\\(b\\)c\\\\)", pdfLiteral("a(b)c\\"))
    // é = 0xE9 = \351, • = 0x95 = \225, — = 0x97 = \227.
    assertEquals("(\\351 \\225 \\227)", pdfLiteral("é • —"))
  }

  @Test
  fun winAnsi_transliteratesCommonSymbolsAndReplacesTheRest() {
    assertEquals("<=10m -> v [photo] x", WinAnsi.normalize("≤10m → ✓ 📷 ✗"))
    assertEquals("??", WinAnsi.normalize("中文"))
    // Emoji variation selectors and combining marks are dropped.
    assertEquals("ok", WinAnsi.normalize("o\uFE0Fk\u0301"))
    assertEquals("Café ± 2°", WinAnsi.normalize("Café ± 2°"))
  }

  @Test
  fun fontMetrics_matchAdobeAfmWidths() {
    // Helvetica: "W" = 944, "i" = 222; Helvetica-Bold "i" = 278; Courier is monospaced.
    assertEquals(9.44f, PdfFont.REGULAR.measure("W", 10f), 0.001f)
    assertEquals(2.22f, PdfFont.REGULAR.measure("i", 10f), 0.001f)
    assertEquals(2.78f, PdfFont.BOLD.measure("i", 10f), 0.001f)
    assertEquals(18f, PdfFont.MONO.measure("abc", 10f), 0.001f)
    // Accented letters use the base letter's width.
    assertEquals(PdfFont.REGULAR.measure("e", 10f), PdfFont.REGULAR.measure("é", 10f))
  }

  @Test
  fun wrapText_fitsLinesAndBreaksLongWords() {
    val text = "Terraced shade coffee intercropped with Cordia and Grevillea along the stream."
    val lines = wrapText(text, PdfFont.REGULAR, 10f, 120f)

    assertTrue(lines.size > 1)
    assertTrue(lines.all { PdfFont.REGULAR.measure(it, 10f) <= 120f })
    assertEquals(text, lines.joinToString(" "))

    val id = "a".repeat(200)
    val broken = wrapText(id, PdfFont.MONO, 10f, 100f)
    assertTrue(broken.size > 1)
    assertEquals(id, broken.joinToString(""))
    assertTrue(broken.all { PdfFont.MONO.measure(it, 10f) <= 100f })

    assertEquals(
      listOf("first", "", "second"),
      wrapText("first\n\nsecond", PdfFont.REGULAR, 10f, 200f),
    )
  }

  @Test
  fun fmt_isPlatformIndependent() {
    assertEquals("0", fmt(0f))
    assertEquals("12.5", fmt(12.5f))
    assertEquals("-3.25", fmt(-3.25f))
    assertEquals("841.89", fmt(841.89f))
    assertEquals("0", fmt(-0.001f))
  }

  @Test
  fun pdfColor_parsesCssHexAndFallsBack() {
    assertEquals(PdfColor(1f, 0f, 0f), PdfColor.fromCss("#f00", PdfColor.BLACK))
    assertEquals(PdfColor.fromArgb(0xFF36693E), PdfColor.fromCss("#36693E", PdfColor.BLACK))
    assertEquals(PdfColor.WHITE, PdfColor.fromCss("green", PdfColor.WHITE))
    assertEquals(PdfColor.WHITE, PdfColor.fromCss(null, PdfColor.WHITE))
  }

  /** Every in-use xref entry must point at the start of `<n> 0 obj`. */
  private fun assertXrefOffsetsPointAtObjects(pdf: String) {
    val startXref = pdf.substringAfterLast("startxref\n").substringBefore('\n').toInt()
    assertTrue(pdf.startsWith("xref\n", startXref))
    val lines = pdf.substring(startXref).lines()
    val count = lines[1].split(' ')[1].toInt()
    for (n in 1 until count) {
      val offset = lines[2 + n].substring(0, 10).toInt()
      assertTrue(pdf.startsWith("$n 0 obj", offset), "Object $n should start at offset $offset")
    }
  }
}
