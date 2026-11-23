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
 * Windows-1252 (`WinAnsiEncoding`, ISO 32000-1 Annex D) encoder for the standard fonts, with a
 * small transliteration table for symbols common in Collect One data that have no WinAnsi glyph.
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
fun pdfLiteral(text: String): String {
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
