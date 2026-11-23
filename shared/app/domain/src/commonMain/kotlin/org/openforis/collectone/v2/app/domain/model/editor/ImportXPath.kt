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
package org.openforis.collectone.v2.app.domain.model.editor

/**
 * Text-level helpers [FormImport] uses to recognize the XPath expressions the editor writes
 * (constraints, `relevant`, itemset filters) without a full XPath parser. They work on the
 * expression text, so they only need to understand quoting and parentheses.
 */
internal object ImportXPath {
  private const val QUOTES = "'\""

  /** A node reference: a relative or absolute path of XML names. */
  val REF = Regex("""^/?([A-Za-z_][A-Za-z0-9_.\-/]*)$""")

  /** `<lhs> <op> <rhs>` with one of the XPath 1.0 comparison operators (section 3.4). */
  val COMPARISON = Regex("""^(.+?)\s*(!=|>=|<=|=|>|<)\s*(.+)$""")

  /**
   * `selected(<ref>, <column>)` with a bare name as its second argument, as an itemset predicate
   * that keeps the rows whose `<column>` is among the selections at `<ref>` (cascading selects).
   */
  val SELECTED_CALL = Regex("""^selected\(\s*([^,]+?)\s*,\s*([A-Za-z_][A-Za-z0-9_.\-]*)\s*\)$""")

  /** The XPath `or` operator between two clauses. */
  val OR_OPERATOR = Regex("""\s+or\s+""")

  /** `jr:itext(<id>)`, the JavaRosa function that looks up a translated string by its ID. */
  val JR_ITEXT = Regex("""^jr:itext\(\s*(.+?)\s*\)$""")

  /**
   * [expr] without parentheses that wrap all of it, repeatedly: `((a = 1))` becomes `a = 1`, while
   * `(a) or (b)` is left alone. Parentheses inside string literals are ignored.
   */
  fun stripOuterParens(expr: String): String {
    var s = expr.trim()
    while (isWrapped(s)) {
      s = s.substring(1, s.length - 1).trim()
    }
    return s
  }

  /**
   * Whether [s] starts with a `(` that its last character closes, rather than one closed earlier as
   * in `(a) or (b)`.
   */
  private fun isWrapped(s: String): Boolean {
    if (s.length < 2 || !s.startsWith('(') || !s.endsWith(')')) return false
    var depth = 0
    var quote: Char? = null
    for (i in 0 until s.length - 1) {
      val c = s[i]
      when {
        quote != null -> if (c == quote) quote = null
        c == '\'' || c == '"' -> quote = c
        c == '(' -> depth++
        c == ')' -> {
          depth--
          if (depth == 0) return false
        }
      }
    }
    return depth == 1
  }

  /** The comparison operator to use when the operands of [op] are swapped (`5 < .` is `. > 5`). */
  fun flipOperator(op: String): String =
    when (op) {
      ">" -> "<"
      ">=" -> "<="
      "<" -> ">"
      "<=" -> ">="
      else -> op
    }

  /**
   * Whether [expression] is a constant true, as XLSForm writes `required` and `relevant` (`true()`,
   * `true`, or `1`). Anything else, including other expressions that happen to be true, is false.
   */
  fun isTrue(expression: String?): Boolean =
    when (expression?.trim()?.lowercase()) {
      "true()",
      "true",
      "1" -> true
      else -> false
    }

  /**
   * The content of the XPath string literal [literal] (XPath 1.0 section 3.7: delimited by `'` or
   * `"`, with no escapes), or [literal] trimmed when it isn't quoted.
   */
  fun unquote(literal: String): String {
    val v = literal.trim()
    return if (v.length >= 2 && v.first() in QUOTES && v.last() == v.first()) {
      v.substring(1, v.length - 1)
    } else {
      v
    }
  }
}
