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
import org.openforis.collectone.v2.core.forms.xpath.XPathEngine

/**
 * Reads an XForms `constraint` back into the editor's [EditorValidation], the inverse of what
 * [ValidationRules] writes. Recognizes structured rules (number bounds, text length/pattern, date
 * rules, selection counts, cross-field comparisons, spatial distance checks, and conditional
 * `not(<cond>) or (<rule>)` wrappers) and falls back to [EditorValidation.customExpression] for any
 * other valid XPath constraint.
 */
internal object ConstraintImport {
  /**
   * The editor rule equivalent to [constraint] (outer parentheses already stripped) on a question
   * of [type], with [message] as its error message, or `null` when [constraint] cannot be parsed as
   * XPath.
   */
  fun parse(
    type: EditorQuestionType,
    constraint: String,
    message: String,
    resolveQuestion: (String) -> EditorQuestion? = { null },
    resolveEarlierQuestion: (String) -> EditorQuestion? = resolveQuestion,
  ): EditorValidation? {
    if (type.isReadOnly) return null
    val trimmed = stripOuterParens(constraint)
    if (trimmed.isEmpty()) return null

    // Recognize conditional validation wrapper: `not(<cond>) or (<rule>)`.
    val condMatch = CONDITIONAL_WRAPPER.matchEntire(trimmed)
    if (condMatch != null) {
      val condExpr = stripOuterParens(condMatch.groupValues[1])
      val innerExpr = stripOuterParens(condMatch.groupValues[2])
      val parsedCond = RelevanceImport.parse(condExpr, resolveEarlierQuestion)
      if (parsedCond != null) {
        val inner =
          parseUnconditional(type, innerExpr, message, resolveQuestion)
            ?: fallbackCustom(innerExpr, message)
        if (inner != null) return inner.copy(condition = parsedCond)
      }
    }

    return parseUnconditional(type, trimmed, message, resolveQuestion)
      ?: fallbackCustom(trimmed, message)
  }

  private fun parseUnconditional(
    type: EditorQuestionType,
    constraint: String,
    message: String,
    resolveQuestion: (String) -> EditorQuestion?,
  ): EditorValidation? {
    val clauses =
      constraint.split(AND_SPLIT).map { stripOuterParens(it) }.filter { it.isNotEmpty() }
    return when (ValidationKind.of(type)) {
      ValidationKind.NUMBER_RANGE -> numberRange(type, clauses, message, resolveQuestion)
      ValidationKind.TEXT -> text(type, clauses, message, resolveQuestion)
      ValidationKind.DATE -> date(type, clauses, message, resolveQuestion)
      ValidationKind.SELECTION_COUNT -> selectionCount(clauses, message)
      ValidationKind.LOCATION -> distance(clauses, message, resolveQuestion)
      null -> null
    }
  }

  private fun fallbackCustom(constraint: String, message: String): EditorValidation? =
    if (runCatching { XPathEngine.compile(constraint) }.isSuccess) {
      EditorValidation(customExpression = constraint, message = message)
    } else {
      null
    }

  private class RangeBounds {
    var min = ""
    var max = ""
    var minExclusive = false
    var maxExclusive = false

    val hasAny: Boolean
      get() = min.isNotEmpty() || max.isNotEmpty()

    fun apply(op: String?, value: String): Boolean =
      when (op) {
        ">=" -> setMin(value, exclusive = false)
        ">" -> setMin(value, exclusive = true)
        "<=" -> setMax(value, exclusive = false)
        "<" -> setMax(value, exclusive = true)
        "=" -> {
          min = value
          max = value
          minExclusive = false
          maxExclusive = false
          true
        }
        else -> false
      }

    private fun setMin(value: String, exclusive: Boolean): Boolean {
      min = value
      minExclusive = exclusive
      return true
    }

    private fun setMax(value: String, exclusive: Boolean): Boolean {
      max = value
      maxExclusive = exclusive
      return true
    }
  }

  /**
   * Numeric bounds (`. >= 0`, `. > 0`, `0 <= .`) and/or a cross-field comparison (`. <=
   * /data/total_area`).
   */
  private fun numberRange(
    type: EditorQuestionType,
    clauses: List<String>,
    message: String,
    resolveQuestion: (String) -> EditorQuestion?,
  ): EditorValidation? {
    val bounds = RangeBounds()
    var comparison: EditorFieldComparison? = null
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
      if (!bounds.apply(op, value)) {
        val cmp = parseFieldComparison(type, part, resolveQuestion)
        if (cmp != null && comparison == null) comparison = cmp else recognized = false
      }
    }
    val hasRule = bounds.hasAny || comparison != null
    return if (recognized && hasRule) {
      EditorValidation(
        min = bounds.min,
        max = bounds.max,
        minExclusive = bounds.minExclusive,
        maxExclusive = bounds.maxExclusive,
        comparison = comparison,
        message = message,
      )
    } else {
      null
    }
  }

  private class TextBounds {
    var min = ""
    var max = ""
    var pattern: TextPattern? = null
    var customPattern = ""
    var comparison: EditorFieldComparison? = null

    val hasAny: Boolean
      get() = min.isNotEmpty() || max.isNotEmpty() || pattern != null || comparison != null

    fun apply(
      type: EditorQuestionType,
      part: String,
      resolveQuestion: (String) -> EditorQuestion?,
    ): Boolean {
      val lenMatch = STRING_LENGTH_BOUND.matchEntire(part)
      val regexMatch = if (lenMatch == null) REGEX_CALL.matchEntire(part) else null
      val cmp =
        if (lenMatch == null && regexMatch == null) {
          parseFieldComparison(type, part, resolveQuestion)
        } else {
          null
        }
      return when {
        lenMatch != null -> applyLength(lenMatch.groupValues[1], lenMatch.groupValues[2])
        regexMatch != null -> applyRegex(unquote(regexMatch.groupValues[1]))
        cmp != null && comparison == null -> {
          comparison = cmp
          true
        }
        else -> false
      }
    }

    private fun applyLength(op: String, rawN: String): Boolean {
      val bounds = integerBounds(op, rawN)
      if (bounds != null) {
        bounds.first?.let { min = it }
        bounds.second?.let { max = it }
      }
      return bounds != null
    }

    private fun applyRegex(rawRegex: String): Boolean {
      val preset = TextPattern.entries.firstOrNull { it.regex == rawRegex }
      pattern = preset ?: TextPattern.CUSTOM
      customPattern = if (preset != null) "" else rawRegex
      return true
    }
  }

  /** Length bounds on `string-length(.)`, `regex(., …)`, and/or text cross-field comparison. */
  private fun text(
    type: EditorQuestionType,
    clauses: List<String>,
    message: String,
    resolveQuestion: (String) -> EditorQuestion?,
  ): EditorValidation? {
    val state = TextBounds()
    val recognized = clauses.all { state.apply(type, it, resolveQuestion) }
    return if (recognized && state.hasAny) {
      EditorValidation(
        min = state.min,
        max = state.max,
        pattern = state.pattern,
        customPattern = state.customPattern,
        comparison = state.comparison,
        message = message,
      )
    } else {
      null
    }
  }

  private class DateState {
    var dateRule: DateRule? = null
    val bounds = RangeBounds()
    var comparison: EditorFieldComparison? = null

    val hasAny: Boolean
      get() = dateRule != null || comparison != null

    fun apply(
      type: EditorQuestionType,
      part: String,
      resolveQuestion: (String) -> EditorQuestion?,
    ): Boolean {
      val todayMatch = DATE_TODAY.matchEntire(part)
      val boundMatch = if (todayMatch == null) DATE_BOUND.matchEntire(part) else null
      val cmp =
        if (todayMatch == null && boundMatch == null) {
          parseFieldComparison(type, part, resolveQuestion)
        } else {
          null
        }
      return when {
        todayMatch != null && dateRule == null -> {
          dateRule = todayDateRule(todayMatch.groupValues[1])
          dateRule != null
        }
        boundMatch != null -> {
          val iso = unquote(boundMatch.groupValues[2])
          val validBound =
            ValidationRules.isValidDate(iso) && bounds.apply(boundMatch.groupValues[1], iso)
          if (validBound) dateRule = DateRule.BETWEEN
          validBound
        }
        cmp != null && comparison == null -> {
          comparison = cmp
          true
        }
        else -> false
      }
    }

    private fun todayDateRule(op: String): DateRule? =
      when (op) {
        "<=",
        "<" -> DateRule.NOT_IN_FUTURE
        ">=",
        ">" -> DateRule.NOT_IN_PAST
        else -> null
      }
  }

  /**
   * `. <= today()` / `. >= today()` as "not in the future / past", ISO 8601 date bounds such as
   * `. >= date('2024-01-01')`, and/or cross-field date comparisons (`. >= /data/start_date`).
   */
  private fun date(
    type: EditorQuestionType,
    clauses: List<String>,
    message: String,
    resolveQuestion: (String) -> EditorQuestion?,
  ): EditorValidation? {
    val state = DateState()
    val recognized = clauses.all { state.apply(type, it, resolveQuestion) }
    return if (recognized && state.hasAny) {
      EditorValidation(
        min = state.bounds.min,
        max = state.bounds.max,
        minExclusive = state.bounds.minExclusive,
        maxExclusive = state.bounds.maxExclusive,
        dateRule = state.dateRule,
        comparison = state.comparison,
        message = message,
      )
    } else {
      null
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

  /** Spatial distance bounds: `distance(., /data/plot_center) <= 50`. */
  private fun distance(
    clauses: List<String>,
    message: String,
    resolveQuestion: (String) -> EditorQuestion?,
  ): EditorValidation? {
    var refKey = ""
    var min = ""
    var max = ""
    var recognized = true
    clauses.forEach { part ->
      val m = DISTANCE_BOUND.matchEntire(part)
      if (m == null) {
        recognized = false
      } else {
        val rawRef = m.groupValues[1].trim()
        val op = m.groupValues[2]
        val value = m.groupValues[3]
        val refQuestion = resolveQuestion(rawRef)
        if (refQuestion == null || refQuestion.type != EditorQuestionType.LOCATION) {
          recognized = false
        } else if (refKey.isNotEmpty() && refKey != refQuestion.key) {
          recognized = false
        } else {
          refKey = refQuestion.key
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
      }
    }
    val hasBounds = min.isNotEmpty() || max.isNotEmpty()
    return if (recognized && refKey.isNotEmpty() && hasBounds) {
      EditorValidation(
        min = min,
        max = max,
        distanceReferenceQuestionKey = refKey,
        message = message,
      )
    } else {
      null
    }
  }

  private fun parseFieldComparison(
    type: EditorQuestionType,
    clause: String,
    resolveQuestion: (String) -> EditorQuestion?,
  ): EditorFieldComparison? {
    val forward = FIELD_COMPARE.matchEntire(clause)
    val reversed = if (forward == null) REVERSED_FIELD_COMPARE.matchEntire(clause) else null
    val opAndRef =
      when {
        forward != null -> forward.groupValues[1] to forward.groupValues[2].trim()
        reversed != null ->
          ImportXPath.flipOperator(reversed.groupValues[2]) to reversed.groupValues[1].trim()
        else -> null
      }
    val op = opAndRef?.let { ComparisonOperator.fromXpath(it.first) }
    val target = opAndRef?.let { resolveQuestion(it.second) }
    val opValid = op != null && op in ComparisonOperator.availableFor(type)
    val targetValid = target != null && ComparisonOperator.areCompatible(type, target.type)
    return if (opValid && targetValid) {
      EditorFieldComparison(operator = op, targetQuestionKey = target.key)
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

  private val CONDITIONAL_WRAPPER = Regex("""^not\(\s*(.+?)\s*\)\s+or\s+\(\s*(.+)\s*\)$""")
  private val AND_SPLIT = Regex("""\s+and\s+""")
  private val BOUND = Regex("""^\.\s*(>=|<=|>|<|=)\s*(-?(?:\d+(?:\.\d*)?|\.\d+))$""")
  private val REVERSED_BOUND = Regex("""^(-?(?:\d+(?:\.\d*)?|\.\d+))\s*(>=|<=|>|<|=)\s*\.$""")
  private val FIELD_COMPARE = Regex("""^\.\s*(>=|<=|!=|>|<|=)\s*([^\s'"]+)$""")
  private val REVERSED_FIELD_COMPARE = Regex("""^([^\s'"]+)\s*(>=|<=|!=|>|<|=)\s*\.$""")
  private val STRING_LENGTH_BOUND = Regex("""^string-length\(\s*\.\s*\)\s*(>=|<=|>|<|=)\s*(\d+)$""")
  private val REGEX_CALL = Regex("""^regex\(\s*\.\s*,\s*(.+?)\s*\)$""")
  private val DATE_TODAY = Regex("""^\.\s*(<=|<|>=|>)\s*today\(\s*\)$""")
  private val DATE_BOUND = Regex("""^\.\s*(>=|<=|>|<|=)\s*date\(\s*(.+?)\s*\)$""")
  private val COUNT_SELECTED_BOUND =
    Regex("""^count-selected\(\s*\.\s*\)\s*(>=|<=|>|<|=)\s*(\d+)$""")
  private val DISTANCE_BOUND =
    Regex("""^distance\(\s*\.\s*,\s*([^)]+?)\s*\)\s*(>=|<=|>|<|=)\s*(\d+(?:\.\d*)?|\.\d+)$""")
}
