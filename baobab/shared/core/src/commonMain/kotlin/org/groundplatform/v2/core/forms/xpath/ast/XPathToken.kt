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

/** Lexical token types for XPath 1.0 and ODK XForms expressions. */
enum class TokenType {
  SLASH, // /
  DOUBLE_SLASH, // //
  DOT, // .
  DOUBLE_DOT, // ..
  LPAREN, // (
  RPAREN, // )
  LBRACKET, // [
  RBRACKET, // ]
  COMMA, // ,
  PIPE, // |
  PLUS, // +
  MINUS, // -
  STAR, // * (Wildcard or Multiply depending on lexical context)
  DIV, // div
  MOD, // mod
  AND, // and or &&
  OR, // or or ||
  EQ, // =
  NEQ, // !=
  LT, // <
  LTE, // <=
  GT, // >
  GTE, // >=
  DOUBLE_COLON, // ::
  VARIABLE_REF, // ${var} or $var
  IDENTIFIER, // QName or NCName (e.g., person, jr:itext, count-non-empty)
  STRING_LITERAL, // 'abc' or "abc"
  NUMBER_LITERAL, // 123 or 45.67
  EOF,
}

/** A single lexical token produced by [XPathLexer]. */
data class XPathToken(val type: TokenType, val text: String, val position: Int)
