/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.serialization.textproto

/** Recursive-descent parser for Protocol Buffer Text Format (`textproto` / `txtpb`). */
internal class TextProtoParser(private val input: String) {
  private var pos = 0

  fun parseMessage(): TextProtoMessage {
    val fields = mutableListOf<TextProtoField>()
    skipWhitespaceAndComments()
    while (pos < input.length && peek() != '}' && peek() != '>') {
      val field = parseField()
      fields.add(field)
      skipWhitespaceAndComments()
      if (pos < input.length && (peek() == ',' || peek() == ';')) {
        pos++
        skipWhitespaceAndComments()
      }
    }
    return TextProtoMessage(fields)
  }

  private fun parseField(): TextProtoField {
    skipWhitespaceAndComments()
    val name = parseIdentifier()
    if (name.isEmpty()) {
      throw IllegalArgumentException(
        "Expected field name at line ${currentLine()}, col ${currentCol()} near '${snippet()}'"
      )
    }
    skipWhitespaceAndComments()
    val hasColon =
      if (pos < input.length && peek() == ':') {
        pos++
        skipWhitespaceAndComments()
        true
      } else {
        false
      }

    if (pos >= input.length) {
      throw IllegalArgumentException("Unexpected end of input after field '$name'")
    }

    val c = peek()
    val value: TextProtoValue =
      when {
        c == '{' || c == '<' -> parseMessageBlock()
        c == '[' -> parseList()
        else -> {
          if (!hasColon) {
            throw IllegalArgumentException(
              "Expected ':' or '{' after field name '$name' at line ${currentLine()}, col ${currentCol()}"
            )
          }
          parseScalarValue()
        }
      }
    return TextProtoField(name, value)
  }

  private fun parseMessageBlock(): TextProtoValue.MessageVal {
    val open = peek()
    val close = if (open == '{') '}' else '>'
    pos++ // consume '{' or '<'
    val msg = parseMessage()
    skipWhitespaceAndComments()
    if (pos >= input.length || peek() != close) {
      throw IllegalArgumentException(
        "Expected '$close' to close message block at line ${currentLine()}, col ${currentCol()}"
      )
    }
    pos++ // consume '}' or '>'
    return TextProtoValue.MessageVal(msg)
  }

  private fun parseList(): TextProtoValue.ListVal {
    pos++ // consume '['
    val elements = mutableListOf<TextProtoValue>()
    skipWhitespaceAndComments()
    while (pos < input.length && peek() != ']') {
      val elem =
        if (peek() == '{' || peek() == '<') {
          parseMessageBlock()
        } else {
          parseScalarValue()
        }
      elements.add(elem)
      skipWhitespaceAndComments()
      if (pos < input.length && (peek() == ',' || peek() == ';')) {
        pos++
        skipWhitespaceAndComments()
      }
    }
    if (pos >= input.length || peek() != ']') {
      throw IllegalArgumentException(
        "Expected ']' to close list at line ${currentLine()}, col ${currentCol()}"
      )
    }
    pos++ // consume ']'
    return TextProtoValue.ListVal(elements)
  }

  private fun parseScalarValue(): TextProtoValue {
    skipWhitespaceAndComments()
    if (pos >= input.length) {
      throw IllegalArgumentException("Unexpected end of input while parsing value")
    }
    val c = peek()
    if (c == '"' || c == '\'') {
      val sb = StringBuilder()
      while (pos < input.length && (peek() == '"' || peek() == '\'')) {
        sb.append(parseQuotedString())
        skipWhitespaceAndComments()
      }
      return TextProtoValue.StringVal(sb.toString())
    }
    val token = parseRawToken()
    if (token.isEmpty()) {
      throw IllegalArgumentException(
        "Unexpected character '${peek()}' at line ${currentLine()}, col ${currentCol()}"
      )
    }
    // Check if token is numeric
    val firstChar = token[0]
    if (
      firstChar.isDigit() ||
        ((firstChar == '-' || firstChar == '+' || firstChar == '.') &&
          token.length > 1 &&
          (token[1].isDigit() || token.lowercase() == "-inf" || token.lowercase() == "-infinity"))
    ) {
      return TextProtoValue.NumberVal(token)
    }
    return TextProtoValue.IdentifierVal(token)
  }

  private fun parseQuotedString(): String {
    val quote = input[pos++]
    val sb = StringBuilder()
    while (pos < input.length) {
      val c = input[pos++]
      if (c == quote) {
        return sb.toString()
      }
      if (c == '\\') {
        if (pos >= input.length) {
          throw IllegalArgumentException("Unterminated escape sequence in string")
        }
        when (val esc = input[pos++]) {
          'n' -> sb.append('\n')
          'r' -> sb.append('\r')
          't' -> sb.append('\t')
          '\\' -> sb.append('\\')
          '"' -> sb.append('"')
          '\'' -> sb.append('\'')
          'b' -> sb.append('\b')
          'f' -> sb.append('\u000C')
          'x',
          'X' -> {
            val hex = StringBuilder()
            repeat(2) {
              if (pos < input.length && input[pos].isHexDigit()) {
                hex.append(input[pos++])
              }
            }
            if (hex.isNotEmpty()) {
              sb.append(hex.toString().toInt(16).toChar())
            }
          }
          in '0'..'7' -> {
            val oct = StringBuilder().append(esc)
            repeat(2) {
              if (pos < input.length && input[pos] in '0'..'7') {
                oct.append(input[pos++])
              }
            }
            sb.append(oct.toString().toInt(8).toChar())
          }
          else -> sb.append(esc)
        }
      } else {
        sb.append(c)
      }
    }
    throw IllegalArgumentException("Unterminated string literal starting with $quote")
  }

  private fun parseIdentifier(): String {
    val start = pos
    while (pos < input.length) {
      val c = input[pos]
      if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '/' || c == '.') {
        pos++
      } else {
        break
      }
    }
    return input.substring(start, pos)
  }

  private fun parseRawToken(): String {
    val start = pos
    while (pos < input.length) {
      val c = input[pos]
      if (
        c.isWhitespace() ||
          c == '#' ||
          c == '{' ||
          c == '}' ||
          c == '<' ||
          c == '>' ||
          c == '[' ||
          c == ']' ||
          c == ':' ||
          c == ',' ||
          c == ';'
      ) {
        break
      }
      pos++
    }
    return input.substring(start, pos)
  }

  private fun skipWhitespaceAndComments() {
    while (pos < input.length) {
      val c = input[pos]
      if (c.isWhitespace()) {
        pos++
      } else if (c == '#') {
        while (pos < input.length && input[pos] != '\n') {
          pos++
        }
      } else {
        break
      }
    }
  }

  private fun peek(): Char = input[pos]

  private fun currentLine(): Int = input.substring(0, pos).count { it == '\n' } + 1

  private fun currentCol(): Int {
    val lastNewline = input.lastIndexOf('\n', pos - 1)
    return if (lastNewline == -1) pos + 1 else pos - lastNewline
  }

  private fun snippet(): String {
    val end = (pos + 20).coerceAtMost(input.length)
    return input.substring(pos, end).replace("\n", "\\n")
  }

  private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

  companion object {
    fun parse(input: String): TextProtoMessage = TextProtoParser(input).parseMessage()
  }
}
