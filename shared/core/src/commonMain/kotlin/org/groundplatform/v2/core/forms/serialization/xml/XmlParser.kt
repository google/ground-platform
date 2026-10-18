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
package org.groundplatform.v2.core.forms.serialization.xml

/**
 * Pure-Kotlin Multiplatform XML parser. Parses XML documents into an [XmlElement] tree, preserving
 * mixed content, attributes, and namespaces.
 */
internal object XmlParser {

  /** Parses an XML document string and returns its root [XmlElement]. */
  fun parse(xml: String): XmlElement = XmlTokenizer(xml).parseRoot()

  internal fun unescapeXml(input: String): String {
    if ('&' !in input) return input
    return buildString(input.length) {
      var i = 0
      while (i < input.length) {
        val c = input[i]
        if (c == '&') {
          // Bound the lookahead. An unbounded search for ';' lets a bare '&' in the text consume
          // everything up to the terminator of the *next* entity, so "a & b &lt; c" would leave
          // "&lt;" undecoded. Entity names are short, so cap the scan instead.
          val limit = minOf(input.length, i + 1 + MAX_ENTITY_NAME_LENGTH)
          val semi = input.indexOf(';', i + 1)
          if (semi in (i + 1) until limit) {
            val decoded = decodeEntity(input.substring(i + 1, semi))
            if (decoded != null) {
              append(decoded)
              i = semi + 1
              continue
            }
          }
        }
        append(c)
        i++
      }
    }
  }

  /** Upper bound on the characters scanned when looking for an entity reference terminator. */
  private const val MAX_ENTITY_NAME_LENGTH = 32

  /**
   * Decodes the body of an entity reference (the text between `&` and `;`), or returns `null` if it
   * is not a reference this parser recognizes, in which case the `&` is treated as literal text.
   */
  private fun decodeEntity(entity: String): String? =
    when {
      entity == "lt" -> "<"
      entity == "gt" -> ">"
      entity == "amp" -> "&"
      entity == "quot" -> "\""
      entity == "apos" -> "'"
      entity.startsWith("#x") || entity.startsWith("#X") ->
        codePointToString(entity.substring(2).toIntOrNull(16))
      entity.startsWith("#") -> codePointToString(entity.substring(1).toIntOrNull(10))
      else -> null
    }

  /**
   * Converts a Unicode code point to a string, encoding supplementary-plane code points (above
   * `U+FFFF`) as a surrogate pair. `Int.toChar()` truncates to the low 16 bits, which silently
   * corrupts emoji and other astral characters into unrelated BMP characters.
   */
  private fun codePointToString(codePoint: Int?): String? {
    if (codePoint == null || codePoint < 0 || codePoint > MAX_CODE_POINT) return null
    // Unpaired surrogate halves are not legal XML characters.
    if (codePoint in 0xD800..0xDFFF) return null
    if (codePoint <= 0xFFFF) return codePoint.toChar().toString()
    val offset = codePoint - 0x10000
    val high = (0xD800 + (offset shr 10)).toChar()
    val low = (0xDC00 + (offset and 0x3FF)).toChar()
    return "$high$low"
  }

  private const val MAX_CODE_POINT = 0x10FFFF
}

private class XmlTokenizer(private val src: String) {
  private var pos = 0
  private val len = src.length

  init {
    if (pos < len && src[pos] == '\uFEFF') {
      pos++
    }
  }

  fun parseRoot(): XmlElement {
    skipPrologAndComments()
    return parseElement()
  }

  private fun skipPrologAndComments() {
    while (pos < len) {
      skipWhitespace()
      if (startsWith("<?")) {
        val end = src.indexOf("?>", pos + 2)
        pos = if (end != -1) end + 2 else len
      } else if (startsWith("<!--")) {
        val end = src.indexOf("-->", pos + 4)
        pos = if (end != -1) end + 3 else len
      } else if (startsWith("<!DOCTYPE") || startsWith("<!doctype")) {
        skipDoctype()
      } else {
        break
      }
    }
  }

  private fun skipDoctype() {
    var depth = 0
    while (pos < len) {
      when (src[pos++]) {
        '<' -> depth++
        '>' -> {
          depth--
          if (depth <= 0) break
        }
      }
    }
  }

  private fun parseElement(): XmlElement {
    expect('<')
    val name = readName()
    val attributes = mutableMapOf<String, String>()

    while (pos < len) {
      skipWhitespace()
      if (pos >= len) break
      val c = src[pos]
      if (c == '/' || c == '>') break
      val attrName = readName()
      skipWhitespace()
      if (pos < len && src[pos] == '=') {
        pos++
        skipWhitespace()
        attributes[attrName] = readAttributeValue()
      } else {
        attributes[attrName] = ""
      }
    }

    if (pos < len && src[pos] == '/') {
      pos++
      expect('>')
      return XmlElement(name = name, attributes = attributes, children = emptyList())
    }

    expect('>')

    val rawChildren = mutableListOf<XmlNode>()
    while (pos < len) {
      if (startsWith("</")) {
        pos += 2
        val closeName = readName()
        skipWhitespace()
        expect('>')
        if (closeName != name) {
          throw IllegalArgumentException(
            "Mismatched XML closing tag: expected </$name> but found </$closeName>"
          )
        }
        break
      } else if (startsWith("<!--")) {
        val end = src.indexOf("-->", pos + 4)
        pos = if (end != -1) end + 3 else len
      } else if (startsWith("<![CDATA[")) {
        val end = src.indexOf("]]>", pos + 9)
        if (end != -1) {
          val cdata = src.substring(pos + 9, end)
          rawChildren.add(XmlText(cdata))
          pos = end + 3
        } else {
          rawChildren.add(XmlText(src.substring(pos + 9)))
          pos = len
        }
      } else if (startsWith("<?")) {
        val end = src.indexOf("?>", pos + 2)
        pos = if (end != -1) end + 2 else len
      } else if (src[pos] == '<') {
        rawChildren.add(parseElement())
      } else {
        val text = readText()
        if (text.isNotEmpty()) {
          rawChildren.add(XmlText(text))
        }
      }
    }

    // Normalize children:
    // If there are child elements AND all text nodes are purely whitespace, discard pure-whitespace
    // text nodes so formatting indentation doesn't pollute element-only containers.
    // If any text node has non-whitespace characters (mixed content or pure text element), preserve
    // text nodes.
    val hasElements = rawChildren.any { it is XmlElement }
    val hasNonWhitespaceText = rawChildren.any { it is XmlText && it.text.isNotBlank() }

    val finalChildren =
      if (hasElements && !hasNonWhitespaceText) {
        rawChildren.filterIsInstance<XmlElement>()
      } else if (!hasElements && rawChildren.size > 1) {
        listOf(XmlText(rawChildren.filterIsInstance<XmlText>().joinToString("") { it.text }))
      } else {
        rawChildren
      }

    return XmlElement(name = name, attributes = attributes, children = finalChildren)
  }

  private fun readName(): String {
    val start = pos
    while (pos < len) {
      val c = src[pos]
      if (c.isWhitespace() || c == '=' || c == '/' || c == '>' || c == '<') break
      pos++
    }
    if (pos == start) {
      throw IllegalArgumentException(
        "Expected XML name at position $pos near '${src.substring(pos, minOf(len, pos + 20))}'"
      )
    }
    return src.substring(start, pos)
  }

  private fun readAttributeValue(): String {
    if (pos >= len) return ""
    val quote = src[pos]
    return if (quote == '"' || quote == '\'') {
      pos++
      val start = pos
      while (pos < len && src[pos] != quote) {
        pos++
      }
      val raw = src.substring(start, pos)
      if (pos < len && src[pos] == quote) {
        pos++
      }
      XmlParser.unescapeXml(raw)
    } else {
      val start = pos
      while (pos < len && !src[pos].isWhitespace() && src[pos] != '>' && src[pos] != '/') {
        pos++
      }
      XmlParser.unescapeXml(src.substring(start, pos))
    }
  }

  private fun readText(): String {
    val start = pos
    while (pos < len && src[pos] != '<') {
      pos++
    }
    return XmlParser.unescapeXml(src.substring(start, pos))
  }

  private fun skipWhitespace() {
    while (pos < len && src[pos].isWhitespace()) {
      pos++
    }
  }

  private fun startsWith(prefix: String): Boolean = src.regionMatches(pos, prefix, 0, prefix.length)

  private fun expect(expected: Char) {
    if (pos >= len || src[pos] != expected) {
      val found = if (pos < len) "'${src[pos]}'" else "EOF"
      throw IllegalArgumentException("Expected '$expected' at position $pos but found $found")
    }
    pos++
  }
}
