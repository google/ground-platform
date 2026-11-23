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

import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.stripOuterParens
import org.openforis.collectone.v2.app.domain.model.editor.ImportXPath.unquote

/**
 * Reads an XForms `constraint` back into the editor's [EditorValidation], the inverse of what
 * [ValidationRules] writes. Only the shapes the editor itself writes are recognized: `and`-joined
 * comparisons of `.` (the current answer), `string-length(.)`, `count-selected(.)`, `regex(., …)`,
 * `today()`, and `date(…)` against literals. Anything else is unsupported, so the caller can report
 * it instead of silently dropping or misreading a rule.
 */
internal object ConstraintImport {
  /**
   * The editor rule equivalent to [constraint] (outer parentheses already stripped) on a question
   * of [type], with [message] as its error message, or `null` when the editor can't represent it. A
   * constraint is only recognized when every clause is.
   */
  fun parse(type: EditorQuestionType, constraint: String, message: String): EditorValidation? {
    val clauses =
      constraint.split(AND_SPLIT).map { stripOuterParens(it) }.filter { it.isNotEmpty() }
    return when (ValidationKind.of(type)) {
      ValidationKind.NUMBER_RANGE -> numberRange(clauses, message)
      ValidationKind.TEXT -> text(clauses, message)
      ValidationKind.DATE -> date(clauses, message)
      ValidationKind.SELECTION_COUNT -> selectionCount(clauses, message)
      null -> null
    }
  }

  /** Inclusive bounds such as `. >= 0 and . <= 10`, in either operand order (`0 <= .`). */
  private fun numberRange(clauses: List<String>, message: String): EditorValidation? {
    var min = ""
    var max = ""
    var recognized = true
    clauses.forEach { part ->
      val bound = BOUND.matchEntire(part)?.let { it.groupValues[1] to it.groupValues[2] }
      // `5 <= .` bounds the answer like `. >= 5`.
      val reversed =
        if (bound == null) {
          REVERSED_BOUND.matchEntire(part)?.let {
            ImportXPath.flipOperator(it.groupValues[2]) to it.groupValues[1]
          }
        } else {
          null
        }
      val (op, value) = bound ?: reversed ?: (null to "")
      when (op) {
        ">=",
        ">" -> min = value
        "<=",
        "<" -> max = value
        "=" -> {
          min = value
          max = value
        }
        else -> recognized = false
      }
    }
    return if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
      EditorValidation(min = min, max = max, message = message)
    } else {
      null
    }
  }

  /** Length bounds on `string-length(.)` and a `regex(., …)` pattern (preset or custom). */
  private fun text(clauses: List<String>, message: String): EditorValidation? {
    var min = ""
    var max = ""
    var pattern: TextPattern? = null
    var customPattern = ""
    var recognized = true
    clauses.forEach { part ->
      val lenMatch = STRING_LENGTH_BOUND.matchEntire(part)
      val regexMatch = if (lenMatch == null) REGEX_CALL.matchEntire(part) else null
      when {
        lenMatch != null -> {
          val bounds = integerBounds(lenMatch.groupValues[1], lenMatch.groupValues[2])
          if (bounds == null) {
            recognized = false
          } else {
            bounds.first?.let { min = it }
            bounds.second?.let { max = it }
          }
        }
        regexMatch != null -> {
          val rawRegex = unquote(regexMatch.groupValues[1])
          val preset = TextPattern.entries.firstOrNull { it.regex == rawRegex }
          pattern = preset ?: TextPattern.CUSTOM
          customPattern = if (preset != null) "" else rawRegex
        }
        else -> recognized = false
      }
    }
    val hasRule = min.isNotEmpty() || max.isNotEmpty() || pattern != null
    return if (recognized && hasRule) {
      EditorValidation(
        min = min,
        max = max,
        pattern = pattern,
        customPattern = customPattern,
        message = message,
      )
    } else {
      null
    }
  }

  /**
   * `. <= today()` / `. >= today()` as "not in the future / past", else ISO 8601 date bounds such
   * as `. >= date('2024-01-01')`.
   */
  private fun date(clauses: List<String>, message: String): EditorValidation? {
    relativeToToday(clauses)?.let { rule ->
      return EditorValidation(dateRule = rule, message = message)
    }
    var min = ""
    var max = ""
    var recognized = true
    clauses.forEach { part ->
      val m = DATE_BOUND.matchEntire(part)
      val iso = m?.let { unquote(it.groupValues[2]) }
      when {
        m == null || iso == null || !ValidationRules.isValidDate(iso) -> recognized = false
        m.groupValues[1] == ">=" || m.groupValues[1] == ">" -> min = iso
        m.groupValues[1] == "<=" || m.groupValues[1] == "<" -> max = iso
        m.groupValues[1] == "=" -> {
          min = iso
          max = iso
        }
        else -> recognized = false
      }
    }
    return if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
      EditorValidation(min = min, max = max, dateRule = DateRule.BETWEEN, message = message)
    } else {
      null
    }
  }

  private fun relativeToToday(clauses: List<String>): DateRule? {
    val op = clauses.singleOrNull()?.let { DATE_TODAY.matchEntire(it) }?.groupValues?.get(1)
    return when (op) {
      "<=",
      "<" -> DateRule.NOT_IN_FUTURE
      ">=",
      ">" -> DateRule.NOT_IN_PAST
      else -> null
    }
  }

  /** Bounds on `count-selected(.)`, the number of choices picked in a Select multiple. */
  private fun selectionCount(clauses: List<String>, message: String): EditorValidation? {
    var min = ""
    var max = ""
    var recognized = true
    clauses.forEach { part ->
      val m = COUNT_SELECTED_BOUND.matchEntire(part)
      val bounds = m?.let { integerBounds(it.groupValues[1], it.groupValues[2]) }
      if (bounds == null) {
        recognized = false
      } else {
        bounds.first?.let { min = it }
        bounds.second?.let { max = it }
      }
    }
    return if (recognized && (min.isNotEmpty() || max.isNotEmpty())) {
      EditorValidation(min = min, max = max, message = message)
    } else {
      null
    }
  }

  /**
   * The inclusive (minimum, maximum) a whole-number comparison `<count> <op> <n>` sets; either side
   * is `null` when the comparison doesn't set it. Strict bounds become inclusive ones (`> 2` is a
   * minimum of 3). `null` when [rawN] isn't a number or the bound can't be met (`< 0`).
   */
  private fun integerBounds(op: String, rawN: String): Pair<String?, String?>? {
    val n = rawN.toIntOrNull() ?: return null
    return when (op) {
      ">=" -> n.toString() to null
      ">" -> (n + 1).toString() to null
      "<=" -> null to n.toString()
      "<" -> if (n > 0) null to (n - 1).toString() else null
      "=" -> n.toString() to n.toString()
      else -> null
    }
  }

  private val AND_SPLIT = Regex("""\s+and\s+""")
  private val BOUND = Regex("""^\.\s*(>=|<=|>|<|=)\s*(-?(?:\d+(?:\.\d*)?|\.\d+))$""")
  private val REVERSED_BOUND = Regex("""^(-?(?:\d+(?:\.\d*)?|\.\d+))\s*(>=|<=|>|<|=)\s*\.$""")
  private val STRING_LENGTH_BOUND = Regex("""^string-length\(\s*\.\s*\)\s*(>=|<=|>|<|=)\s*(\d+)$""")
  private val REGEX_CALL = Regex("""^regex\(\s*\.\s*,\s*(.+?)\s*\)$""")
  private val DATE_TODAY = Regex("""^\.\s*(<=|<|>=|>)\s*today\(\s*\)$""")
  private val DATE_BOUND = Regex("""^\.\s*(>=|<=|>|<|=)\s*date\(\s*(.+?)\s*\)$""")
  private val COUNT_SELECTED_BOUND =
    Regex("""^count-selected\(\s*\.\s*\)\s*(>=|<=|>|<|=)\s*(\d+)$""")
}
