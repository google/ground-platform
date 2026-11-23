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

/** The standard 14 fonts used by Collect One PDFs; none need embedding. */
enum class PdfFont(val resourceName: String, val baseFont: String) {
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
