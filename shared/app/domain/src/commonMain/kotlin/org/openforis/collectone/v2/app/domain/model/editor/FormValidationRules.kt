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

import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.message

/** Family of validation rules that applies to a question type. */
enum class ValidationKind {
  /** Integer / decimal: inclusive minimum and maximum value. */
  NUMBER_RANGE,
  /** Text: minimum / maximum length and an optional pattern. */
  TEXT,
  /** Date: not in the future / past, or between two dates. */
  DATE,
  /** Select multiple: minimum / maximum number of selected choices. */
  SELECTION_COUNT;

  companion object {
    /** Rule family for [type], or `null` if the type has no basic validation rules. */
    fun of(type: EditorQuestionType): ValidationKind? =
      when (type) {
        EditorQuestionType.NUMBER -> NUMBER_RANGE
        EditorQuestionType.TEXT,
        EditorQuestionType.LONG_TEXT -> TEXT
        EditorQuestionType.DATE -> DATE
        EditorQuestionType.SELECT_MULTIPLE -> SELECTION_COUNT
        else -> null
      }
  }
}

/** Which dates a date question accepts. */
enum class DateRule {
  NOT_IN_FUTURE,
  NOT_IN_PAST,
  BETWEEN,
}

/** Preset text patterns, compiled to the XForms `regex()` function. */
enum class TextPattern(val regex: String?) {
  EMAIL("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"),
  DIGITS("^[0-9]+$"),
  LETTERS("^[A-Za-z]+$"),
  CUSTOM(null),
}

/**
 * One condition a valid answer meets, read from a question's [EditorValidation] by
 * [ValidationRules.conditions]. The editor describes each in the user's language; numbers are kept
 * as the organizer typed them, and dates are ISO `YYYY-MM-DD`.
 */
sealed interface ValidationCondition {
  data class NumberBetween(val min: String, val max: String) : ValidationCondition

  data class NumberAtLeast(val min: String) : ValidationCondition

  data class NumberAtMost(val max: String) : ValidationCondition

  data class LengthBetween(val min: Int, val max: Int) : ValidationCondition

  data class LengthAtLeast(val min: Int) : ValidationCondition

  data class LengthAtMost(val max: Int) : ValidationCondition

  data class MatchesPattern(val pattern: TextPattern) : ValidationCondition

  data object NotInFuture : ValidationCondition

  data object NotInPast : ValidationCondition

  data class OnOrAfter(val date: String) : ValidationCondition

  data class OnOrBefore(val date: String) : ValidationCondition

  data class SelectionsBetween(val min: Int, val max: Int) : ValidationCondition

  data class SelectionsAtLeast(val min: Int) : ValidationCondition

  data class SelectionsAtMost(val max: Int) : ValidationCondition
}

/**
 * Structured validation for one question. Which fields are used depends on the question's
 * [ValidationKind]:
 * - [ValidationKind.NUMBER_RANGE]: [min] / [max] are numbers.
 * - [ValidationKind.TEXT]: [min] / [max] are lengths; [pattern] / [customPattern].
 * - [ValidationKind.DATE]: [dateRule]; for [DateRule.BETWEEN], [min] / [max] are `YYYY-MM-DD`.
 * - [ValidationKind.SELECTION_COUNT]: [min] / [max] are selection counts.
 *
 * [message] is the optional error shown to data collectors (`jr:constraintMsg`); when blank, a
 * message is generated from the rule.
 */
data class EditorValidation(
  val min: String = "",
  val max: String = "",
  val dateRule: DateRule? = null,
  val pattern: TextPattern? = null,
  val customPattern: String = "",
  val message: String = "",
)

/**
 * Compiles [EditorValidation] to XForms, reads it as [ValidationCondition]s for the editor to
 * describe, and writes the default `jr:constraintMsg` exported with the form.
 */
object ValidationRules {
  // XPath 1.0 section 3.7 `Number` literal, with an optional leading minus (no exponents).
  private val NUMBER_PATTERN = Regex("^-?(\\d+(\\.\\d*)?|\\.\\d+)$")

  /** Whether [text] is a plain decimal number that can be written as an XPath literal. */
  fun isValidNumber(text: String): Boolean = NUMBER_PATTERN.matches(text)

  /** Whether [validation] constrains anything for a question of [type]. */
  fun isActive(type: EditorQuestionType, validation: EditorValidation?): Boolean =
    validation != null && parts(type, validation).isNotEmpty()

  /**
   * XPath `constraint` for a question of [type] (ODK XForms spec, "Bindings": `constraint` is
   * evaluated with `.` as the question's value), or `null` when no rule is set.
   */
  fun constraintExpression(type: EditorQuestionType, validation: EditorValidation?): String? {
    if (validation == null) return null
    return parts(type, validation).takeIf { it.isNotEmpty() }?.joinToString(" and ") { it.xpath }
  }

  /** The conditions [validation] sets for a question of [type]; invalid inputs are skipped. */
  fun conditions(
    type: EditorQuestionType,
    validation: EditorValidation?,
  ): List<ValidationCondition> = validation?.let { parts(type, it).map(Part::condition) }.orEmpty()

  /**
   * The `jr:constraintMsg` to export: the organizer's message, or a default generated from the rule
   * (e.g. "Must be between 0 and 120."). The default is form content, not editor text: it is
   * written in English, the forms' default language, whatever the editor's language.
   */
  fun message(type: EditorQuestionType, validation: EditorValidation?): String? =
    validation?.message?.trim()?.ifEmpty { null }
      ?: DefaultConstraintMessage.of(conditions(type, validation))

  /** Drops [validation] if it doesn't fit a question of [newType]. */
  fun adaptToType(
    oldType: EditorQuestionType,
    newType: EditorQuestionType,
    validation: EditorValidation?,
  ): EditorValidation? {
    val kind = ValidationKind.of(newType) ?: return null
    return validation?.takeIf { ValidationKind.of(oldType) == kind }
  }

  /** Problems that prevent the question's validation from compiling to a meaningful constraint. */
  fun issues(question: EditorQuestion): List<DomainMessage> {
    val validation = question.validation ?: return emptyList()
    val min = validation.min.trim()
    val max = validation.max.trim()
    return when (ValidationKind.of(question.type)) {
      null -> emptyList()
      ValidationKind.NUMBER_RANGE -> numberRangeIssues(min, max)
      ValidationKind.TEXT -> countIssues(isText = true, min, max) + customPatternIssues(validation)
      ValidationKind.SELECTION_COUNT ->
        countIssues(isText = false, min, max) + selectionsMoreThanChoicesIssues(question, min)
      ValidationKind.DATE -> dateIssues(validation, min, max)
    }
  }

  private fun numberRangeIssues(min: String, max: String): List<DomainMessage> = buildList {
    val lo = min.takeIf(::isValidNumber)?.toDoubleOrNull()
    val hi = max.takeIf(::isValidNumber)?.toDoubleOrNull()
    if (min.isNotEmpty() && lo == null) add(EditorIssueMessage.MIN_NOT_NUMBER.message())
    if (max.isNotEmpty() && hi == null) add(EditorIssueMessage.MAX_NOT_NUMBER.message())
    if (lo != null && hi != null && lo > hi) add(EditorIssueMessage.MIN_MORE_THAN_MAX.message())
  }

  /** Issues of a length ([isText]) or selection count range. */
  private fun countIssues(isText: Boolean, min: String, max: String): List<DomainMessage> =
    buildList {
      val lo = min.takeIf { it.isNotEmpty() }?.toIntOrNull()
      val hi = max.takeIf { it.isNotEmpty() }?.toIntOrNull()
      if (min.isNotEmpty() && (lo == null || lo < 0)) {
        add(
          if (isText) {
            EditorIssueMessage.MIN_LENGTH_INVALID.message()
          } else {
            EditorIssueMessage.MIN_SELECTIONS_INVALID.message()
          }
        )
      }
      if (max.isNotEmpty() && (hi == null || hi < 0)) {
        add(
          if (isText) {
            EditorIssueMessage.MAX_LENGTH_INVALID.message()
          } else {
            EditorIssueMessage.MAX_SELECTIONS_INVALID.message()
          }
        )
      }
      if (lo != null && hi != null && lo > hi) {
        add(
          if (isText) {
            EditorIssueMessage.MIN_LENGTH_MORE_THAN_MAX.message()
          } else {
            EditorIssueMessage.MIN_SELECTIONS_MORE_THAN_MAX.message()
          }
        )
      }
    }

  private fun selectionsMoreThanChoicesIssues(
    question: EditorQuestion,
    min: String,
  ): List<DomainMessage> {
    val lo = min.takeIf { it.isNotEmpty() }?.toIntOrNull()
    val tooMany = !question.usesDatasetChoices && lo != null && lo > question.choices.size
    return if (tooMany) listOf(EditorIssueMessage.MIN_SELECTIONS_MORE_THAN_CHOICES.message())
    else emptyList()
  }

  private fun customPatternIssues(validation: EditorValidation): List<DomainMessage> {
    if (validation.pattern != TextPattern.CUSTOM) return emptyList()
    val pattern = validation.customPattern
    val issue =
      when {
        pattern.isBlank() -> EditorIssueMessage.CUSTOM_PATTERN_MISSING
        runCatching { Regex(pattern) }.isFailure -> EditorIssueMessage.CUSTOM_PATTERN_INVALID
        '\'' in pattern && '"' in pattern -> EditorIssueMessage.CUSTOM_PATTERN_QUOTES
        else -> null
      }
    return listOfNotNull(issue?.message())
  }

  private fun dateIssues(
    validation: EditorValidation,
    min: String,
    max: String,
  ): List<DomainMessage> = buildList {
    // Dates are picked with a date picker, so they're always ISO `YYYY-MM-DD`.
    if (validation.dateRule != DateRule.BETWEEN) return@buildList
    if (min.isEmpty() && max.isEmpty()) add(EditorIssueMessage.DATE_RANGE_MISSING.message())
    if (isValidDate(min) && isValidDate(max) && min > max) {
      add(EditorIssueMessage.DATE_RANGE_REVERSED.message())
    }
  }

  fun isValidDate(text: String): Boolean = isoDateToUtcMillis(text) != null

  private class Part(val xpath: String, val condition: ValidationCondition)

  /** Valid, applicable pieces of [validation]; invalid inputs are skipped. */
  private fun parts(type: EditorQuestionType, validation: EditorValidation): List<Part> {
    val min = validation.min.trim()
    val max = validation.max.trim()
    return when (ValidationKind.of(type)) {
      null -> emptyList()
      ValidationKind.NUMBER_RANGE -> numberRangeParts(min, max)
      ValidationKind.TEXT -> lengthParts(min, max) + patternParts(validation)
      ValidationKind.DATE -> dateParts(validation, min, max)
      ValidationKind.SELECTION_COUNT -> selectionCountParts(min, max)
    }
  }

  private fun numberRangeParts(min: String, max: String): List<Part> {
    val lo = min.takeIf(::isValidNumber)
    val hi = max.takeIf(::isValidNumber)
    return when {
      lo != null && hi != null ->
        listOf(Part(". >= $lo and . <= $hi", ValidationCondition.NumberBetween(lo, hi)))
      lo != null -> listOf(Part(". >= $lo", ValidationCondition.NumberAtLeast(lo)))
      hi != null -> listOf(Part(". <= $hi", ValidationCondition.NumberAtMost(hi)))
      else -> emptyList()
    }
  }

  private fun lengthParts(min: String, max: String): List<Part> {
    val lo = min.toIntOrNull()?.takeIf { it >= 0 }
    val hi = max.toIntOrNull()?.takeIf { it >= 0 }
    return when {
      lo != null && hi != null ->
        listOf(
          Part(
            "string-length(.) >= $lo and string-length(.) <= $hi",
            ValidationCondition.LengthBetween(lo, hi),
          )
        )
      lo != null -> listOf(Part("string-length(.) >= $lo", ValidationCondition.LengthAtLeast(lo)))
      hi != null -> listOf(Part("string-length(.) <= $hi", ValidationCondition.LengthAtMost(hi)))
      else -> emptyList()
    }
  }

  private fun patternParts(validation: EditorValidation): List<Part> {
    val pattern = validation.pattern ?: return emptyList()
    val regex =
      (if (pattern == TextPattern.CUSTOM) validation.customPattern else pattern.regex)?.takeIf(
        ::isUsableRegex
      ) ?: return emptyList()
    // ODK XForms spec, "XPath functions": regex(string value, string expression).
    return listOf(
      Part("regex(., ${xpathStringLiteral(regex)})", ValidationCondition.MatchesPattern(pattern))
    )
  }

  /** Whether [regex] compiles and can be written as one XPath string literal. */
  private fun isUsableRegex(regex: String): Boolean =
    regex.isNotBlank() && runCatching { Regex(regex) }.isSuccess && !('\'' in regex && '"' in regex)

  private fun dateParts(validation: EditorValidation, min: String, max: String): List<Part> =
    when (validation.dateRule) {
      null -> emptyList()
      DateRule.NOT_IN_FUTURE -> listOf(Part(". <= today()", ValidationCondition.NotInFuture))
      DateRule.NOT_IN_PAST -> listOf(Part(". >= today()", ValidationCondition.NotInPast))
      DateRule.BETWEEN ->
        buildList {
          min.takeIf(::isValidDate)?.let {
            add(Part(". >= date('$it')", ValidationCondition.OnOrAfter(it)))
          }
          max.takeIf(::isValidDate)?.let {
            add(Part(". <= date('$it')", ValidationCondition.OnOrBefore(it)))
          }
        }
    }

  private fun selectionCountParts(min: String, max: String): List<Part> {
    val lo = min.toIntOrNull()?.takeIf { it >= 0 }
    val hi = max.toIntOrNull()?.takeIf { it >= 0 }
    // ODK XForms spec, "XPath functions": count-selected(node) for `select` answers.
    return when {
      lo != null && hi != null ->
        listOf(
          Part(
            "count-selected(.) >= $lo and count-selected(.) <= $hi",
            ValidationCondition.SelectionsBetween(lo, hi),
          )
        )
      lo != null ->
        listOf(Part("count-selected(.) >= $lo", ValidationCondition.SelectionsAtLeast(lo)))
      hi != null ->
        listOf(Part("count-selected(.) <= $hi", ValidationCondition.SelectionsAtMost(hi)))
      else -> emptyList()
    }
  }
}

/**
 * The default `jr:constraintMsg` of [ValidationRules.message], in English: "Must " and the
 * conditions joined by "and" (e.g. "Must be 3–40 characters long and contain only digits."), or the
 * selection count as a sentence ("Select at least 2 options.").
 */
private object DefaultConstraintMessage {
  fun of(conditions: List<ValidationCondition>): String? {
    if (conditions.isEmpty()) return null
    val phrases = conditions.joinToString(" and ") { phrase(it) }
    return if (conditions.all(::isSelectionCount)) {
      phrases.replaceFirstChar { it.uppercase() } + "."
    } else {
      "Must $phrases."
    }
  }

  private fun isSelectionCount(condition: ValidationCondition): Boolean =
    condition is ValidationCondition.SelectionsBetween ||
      condition is ValidationCondition.SelectionsAtLeast ||
      condition is ValidationCondition.SelectionsAtMost

  private fun phrase(condition: ValidationCondition): String =
    when (condition) {
      is ValidationCondition.NumberBetween -> "be between ${condition.min} and ${condition.max}"
      is ValidationCondition.NumberAtLeast -> "be at least ${condition.min}"
      is ValidationCondition.NumberAtMost -> "be at most ${condition.max}"
      is ValidationCondition.LengthBetween -> "be ${condition.min}–${condition.max} characters long"
      is ValidationCondition.LengthAtLeast ->
        "be at least ${count(condition.min, "character")} long"
      is ValidationCondition.LengthAtMost -> "be at most ${count(condition.max, "character")} long"
      is ValidationCondition.MatchesPattern ->
        when (condition.pattern) {
          TextPattern.EMAIL -> "be an email address"
          TextPattern.DIGITS -> "contain only digits"
          TextPattern.LETTERS -> "contain only letters A–Z"
          TextPattern.CUSTOM -> "match the pattern"
        }
      ValidationCondition.NotInFuture -> "not be in the future"
      ValidationCondition.NotInPast -> "not be in the past"
      is ValidationCondition.OnOrAfter -> "be on or after ${friendlyDate(condition.date)}"
      is ValidationCondition.OnOrBefore -> "be on or before ${friendlyDate(condition.date)}"
      is ValidationCondition.SelectionsBetween ->
        "select between ${condition.min} and ${condition.max} options"
      is ValidationCondition.SelectionsAtLeast ->
        "select at least ${count(condition.min, "option")}"
      is ValidationCondition.SelectionsAtMost -> "select at most ${count(condition.max, "option")}"
    }

  private fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"
}
