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
  private val creator: String = "Open Foris Collect One",
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
        append("<< /Producer ").append(pdfLiteral("Open Foris Collect One PDF writer"))
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
