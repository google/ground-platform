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
package org.groundplatform.v2.core.forms.xpath.ast

import org.groundplatform.v2.core.forms.xpath.XPathSyntaxException

/**
 * Lexical analyzer for XPath 1.0 and ODK XForms expressions.
 *
 * Implements the XPath 1.0 lexical disambiguation rules (Section 3.7):
 * - If a token follows a value-producing token (`IDENTIFIER`, `NUMBER_LITERAL`, `STRING_LITERAL`,
 *   `RPAREN`, `RBRACKET`, `DOT`, `DOUBLE_DOT`, `VARIABLE_REF`), then `*` is treated as a
 *   multiplication operator (`STAR`) and `and`, `or`, `div`, `mod` are treated as operators.
 * - Otherwise, `*` is treated as a node wildcard test and `and`, `or`, `div`, `mod` are treated as
 *   identifiers (element names).
 */
class XPathLexer(private val input: String) {

  fun tokenize(): List<XPathToken> {
    val tokens = mutableListOf<XPathToken>()
    var i = 0
    val len = input.length

    fun isOperatorContext(): Boolean {
      val prev = tokens.lastOrNull() ?: return false
      return when (prev.type) {
        TokenType.IDENTIFIER,
        TokenType.NUMBER_LITERAL,
        TokenType.STRING_LITERAL,
        TokenType.RPAREN,
        TokenType.RBRACKET,
        TokenType.DOT,
        TokenType.DOUBLE_DOT,
        TokenType.VARIABLE_REF -> true
        else -> false
      }
    }

    while (i < len) {
      val c = input[i]
      if (c.isWhitespace()) {
        i++
        continue
      }

      val startPos = i
      when (c) {
        '/' -> {
          if (i + 1 < len && input[i + 1] == '/') {
            tokens.add(XPathToken(TokenType.DOUBLE_SLASH, "//", startPos))
            i += 2
          } else {
            tokens.add(XPathToken(TokenType.SLASH, "/", startPos))
            i++
          }
        }
        '.' -> {
          if (i + 1 < len && input[i + 1] == '.') {
            tokens.add(XPathToken(TokenType.DOUBLE_DOT, "..", startPos))
            i += 2
          } else if (i + 1 < len && input[i + 1].isDigit()) {
            // Leading dot number literal e.g. .5
            i++
            while (i < len && input[i].isDigit()) {
              i++
            }
            if (i < len && (input[i] == 'e' || input[i] == 'E')) {
              i++
              if (i < len && (input[i] == '+' || input[i] == '-')) i++
              while (i < len && input[i].isDigit()) i++
            }
            tokens.add(XPathToken(TokenType.NUMBER_LITERAL, input.substring(startPos, i), startPos))
          } else {
            tokens.add(XPathToken(TokenType.DOT, ".", startPos))
            i++
          }
        }
        '(' -> {
          tokens.add(XPathToken(TokenType.LPAREN, "(", startPos))
          i++
        }
        ')' -> {
          tokens.add(XPathToken(TokenType.RPAREN, ")", startPos))
          i++
        }
        '[' -> {
          tokens.add(XPathToken(TokenType.LBRACKET, "[", startPos))
          i++
        }
        ']' -> {
          tokens.add(XPathToken(TokenType.RBRACKET, "]", startPos))
          i++
        }
        ',' -> {
          tokens.add(XPathToken(TokenType.COMMA, ",", startPos))
          i++
        }
        '|' -> {
          if (i + 1 < len && input[i + 1] == '|') {
            tokens.add(XPathToken(TokenType.OR, "||", startPos))
            i += 2
          } else {
            tokens.add(XPathToken(TokenType.PIPE, "|", startPos))
            i++
          }
        }
        '&' -> {
          if (i + 1 < len && input[i + 1] == '&') {
            tokens.add(XPathToken(TokenType.AND, "&&", startPos))
            i += 2
          } else {
            throw XPathSyntaxException(
              "Unexpected character '&' (did you mean '&&' or 'and'?)",
              startPos,
            )
          }
        }
        '+' -> {
          tokens.add(XPathToken(TokenType.PLUS, "+", startPos))
          i++
        }
        '-' -> {
          tokens.add(XPathToken(TokenType.MINUS, "-", startPos))
          i++
        }
        '*' -> {
          tokens.add(XPathToken(TokenType.STAR, "*", startPos))
          i++
        }
        '=' -> {
          tokens.add(XPathToken(TokenType.EQ, "=", startPos))
          i++
        }
        '!' -> {
          if (i + 1 < len && input[i + 1] == '=') {
            tokens.add(XPathToken(TokenType.NEQ, "!=", startPos))
            i += 2
          } else {
            throw XPathSyntaxException("Expected '=' after '!'", startPos)
          }
        }
        '<' -> {
          if (i + 1 < len && input[i + 1] == '=') {
            tokens.add(XPathToken(TokenType.LTE, "<=", startPos))
            i += 2
          } else {
            tokens.add(XPathToken(TokenType.LT, "<", startPos))
            i++
          }
        }
        '>' -> {
          if (i + 1 < len && input[i + 1] == '=') {
            tokens.add(XPathToken(TokenType.GTE, ">=", startPos))
            i += 2
          } else {
            tokens.add(XPathToken(TokenType.GT, ">", startPos))
            i++
          }
        }
        ':' -> {
          if (i + 1 < len && input[i + 1] == ':') {
            tokens.add(XPathToken(TokenType.DOUBLE_COLON, "::", startPos))
            i += 2
          } else {
            throw XPathSyntaxException("Unexpected single ':' outside identifier", startPos)
          }
        }
        '$' -> {
          // Support both ${var_name} (XLSForm/ProtoForms style) and $var_name (standard XPath
          // variable)
          if (i + 1 < len && input[i + 1] == '{') {
            i += 2
            val varStart = i
            while (i < len && input[i] != '}') {
              i++
            }
            if (i >= len) {
              throw XPathSyntaxException("Unterminated variable reference '\${...}'", startPos)
            }
            val varName = input.substring(varStart, i).trim()
            i++ // skip '}'
            tokens.add(XPathToken(TokenType.VARIABLE_REF, varName, startPos))
          } else {
            i++
            val varStart = i
            while (i < len && isIdentifierPart(input[i])) {
              i++
            }
            val varName = input.substring(varStart, i)
            if (varName.isEmpty()) {
              throw XPathSyntaxException("Expected variable name after '$'", startPos)
            }
            tokens.add(XPathToken(TokenType.VARIABLE_REF, varName, startPos))
          }
        }
        '\'',
        '"' -> {
          val quote = c
          i++
          val sb = StringBuilder()
          while (i < len && input[i] != quote) {
            sb.append(input[i])
            i++
          }
          if (i >= len) {
            throw XPathSyntaxException("Unterminated string literal starting with $quote", startPos)
          }
          i++ // skip closing quote
          tokens.add(XPathToken(TokenType.STRING_LITERAL, sb.toString(), startPos))
        }
        else -> {
          if (c.isDigit()) {
            while (i < len && input[i].isDigit()) {
              i++
            }
            if (i < len && input[i] == '.') {
              i++
              while (i < len && input[i].isDigit()) {
                i++
              }
            }
            if (i < len && (input[i] == 'e' || input[i] == 'E')) {
              i++
              if (i < len && (input[i] == '+' || input[i] == '-')) i++
              while (i < len && input[i].isDigit()) i++
            }
            tokens.add(XPathToken(TokenType.NUMBER_LITERAL, input.substring(startPos, i), startPos))
          } else if (isIdentifierStart(c)) {
            while (i < len && isIdentifierPart(input[i])) {
              // Stop before '::' so axis names like 'child::node' tokenize cleanly
              if (input[i] == ':' && i + 1 < len && input[i + 1] == ':') {
                break
              }
              i++
            }
            val text = input.substring(startPos, i)
            val opContext = isOperatorContext()
            val tokenType =
              if (opContext) {
                when (text) {
                  "and" -> TokenType.AND
                  "or" -> TokenType.OR
                  "div" -> TokenType.DIV
                  "mod" -> TokenType.MOD
                  else -> TokenType.IDENTIFIER
                }
              } else {
                TokenType.IDENTIFIER
              }
            tokens.add(XPathToken(tokenType, text, startPos))
          } else {
            throw XPathSyntaxException("Unexpected character '$c'", startPos)
          }
        }
      }
    }

    tokens.add(XPathToken(TokenType.EOF, "", len))
    return tokens
  }

  private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'

  private fun isIdentifierPart(c: Char): Boolean =
    c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == ':'
}
