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

import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.COMPARISON
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.flipOperator
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.unquote

/**
 * Reads an XForms `relevant` expression back into the editor's single-condition display logic
 * ([EditorRelevance]), the inverse of [EditorXFormsGenerator.relevantExpression]. Besides what the
 * generator writes, it accepts the common XLSForm spellings of the same conditions:
 * `not(selected(…))`, `count-selected(…) > 0`, `string-length(…) != 0`, comparisons with the
 * question on either side, and a bare reference (true when answered).
 *
 * The result must also be one the editor offers for the source question's type
 * ([RelevanceOperator.availableFor]); otherwise the expression counts as unsupported.
 */
internal object RelevanceImport {
  /**
   * Display logic equivalent to [expression] (outer parentheses already stripped), or `null` when
   * the editor can't represent it. [source] resolves a node reference in the expression to the
   * question it may depend on, or `null` when it names no earlier question.
   */
  fun parse(expression: String, source: (String) -> EditorQuestion?): EditorRelevance? =
    selection(expression, source)
      ?: answeredCheck(expression, source)
      ?: COMPARISON.matchEntire(expression)?.let { m ->
        val lhs = m.groupValues[1].trim()
        val op = m.groupValues[2]
        val rhs = m.groupValues[3].trim()
        source(lhs)?.let { comparison(it, op, rhs) }
          ?: source(rhs)?.let { comparison(it, flipOperator(op), lhs) }
      }
      ?: source(expression)?.let(::answered)

  /** `selected(ref, value)` or `not(selected(ref, value))`. */
  private fun selection(expression: String, source: (String) -> EditorQuestion?): EditorRelevance? =
    NOT_SELECTED.matchEntire(expression)?.let { m ->
      source(m.groupValues[1])?.let { selected(it, m.groupValues[2], negated = true) }
    }
      ?: SELECTED.matchEntire(expression)?.let { m ->
        source(m.groupValues[1])?.let { selected(it, m.groupValues[2], negated = false) }
      }

  /** `string-length(ref) > 0` or `count-selected(ref) > 0` (or `!= 0`): "is answered". */
  private fun answeredCheck(
    expression: String,
    source: (String) -> EditorQuestion?,
  ): EditorRelevance? =
    (ANSWERED.matchEntire(expression) ?: COUNT_SELECTED_ANSWERED.matchEntire(expression))?.let { m
      ->
      source(m.groupValues[1])?.let(::answered)
    }

  private fun answered(src: EditorQuestion): EditorRelevance? =
    if (RelevanceOperator.IS_ANSWERED in RelevanceOperator.availableFor(src.type)) {
      EditorRelevance(src.key, RelevanceOperator.IS_ANSWERED)
    } else {
      null
    }

  /**
   * `selected(src, literal)` (ODK XForms `selected()`), or its negation. An empty literal tests for
   * an empty answer. On a Select multiple it means "includes"; on single-valued questions,
   * equality.
   */
  private fun selected(src: EditorQuestion, literal: String, negated: Boolean): EditorRelevance? {
    val value = unquote(literal)
    val available = RelevanceOperator.availableFor(src.type)
    if (value.isEmpty()) {
      val op = if (negated) RelevanceOperator.IS_ANSWERED else RelevanceOperator.EQUALS
      return if (op in available) EditorRelevance(src.key, op, "") else null
    }
    val op =
      if (negated) {
        RelevanceOperator.NOT_EQUALS.takeIf { it in available }
      } else {
        when {
          src.type == EditorQuestionType.SELECT_MULTIPLE &&
            RelevanceOperator.INCLUDES in available -> RelevanceOperator.INCLUDES
          RelevanceOperator.EQUALS in available -> RelevanceOperator.EQUALS
          RelevanceOperator.INCLUDES in available -> RelevanceOperator.INCLUDES
          else -> null
        }
      }
    return op?.let { EditorRelevance(src.key, it, value) }
  }

  /**
   * `src <opToken> literal`. The editor has no "at least" / "at most", so `>=` and `<=` import as
   * `>` and `<`; `!= ''` means "answered".
   */
  private fun comparison(src: EditorQuestion, opToken: String, literal: String): EditorRelevance? {
    val value = unquote(literal)
    val available = RelevanceOperator.availableFor(src.type)
    if (value.isEmpty() && opToken == "!=") {
      return if (RelevanceOperator.IS_ANSWERED in available) {
        EditorRelevance(src.key, RelevanceOperator.IS_ANSWERED)
      } else {
        null
      }
    }
    val operator =
      when (opToken) {
        "=" -> RelevanceOperator.EQUALS
        "!=" -> RelevanceOperator.NOT_EQUALS
        ">",
        ">=" -> RelevanceOperator.GREATER_THAN
        "<",
        "<=" -> RelevanceOperator.LESS_THAN
        else -> return null
      }
    return if (operator in available) EditorRelevance(src.key, operator, value) else null
  }

  private val SELECTED = Regex("""^selected\(\s*([^,]+?)\s*,\s*(.+?)\s*\)$""")
  private val NOT_SELECTED = Regex("""^not\(\s*selected\(\s*([^,]+?)\s*,\s*(.+?)\s*\)\s*\)$""")
  private val ANSWERED = Regex("""^string-length\(\s*(.+?)\s*\)\s*(?:>|!=)\s*0$""")
  private val COUNT_SELECTED_ANSWERED = Regex("""^count-selected\(\s*(.+?)\s*\)\s*(?:>|!=)\s*0$""")
}
