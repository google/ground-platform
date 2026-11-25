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
import org.openforis.collectone.v2.core.forms.xpath.XPathEngine

/** Family of structured validation rules that applies to a question type. */
enum class ValidationKind {
  /** Integer / decimal: minimum and maximum value (inclusive or exclusive) and field comparison. */
  NUMBER_RANGE,
  /** Text: minimum / maximum length, optional pattern, and field comparison. */
  TEXT,
  /** Date: not in the future / past, between two dates, and field comparison. */
  DATE,
  /** Select multiple: minimum / maximum number of selected choices. */
  SELECTION_COUNT,
  /** Location (geopoint): minimum / maximum distance in meters from another location question. */
  LOCATION;

  companion object {
    /** Rule family for [type], or `null` if the type has no structured validation rules. */
    fun of(type: EditorQuestionType): ValidationKind? =
      when (type) {
        EditorQuestionType.NUMBER -> NUMBER_RANGE
        EditorQuestionType.TEXT,
        EditorQuestionType.LONG_TEXT -> TEXT
        EditorQuestionType.DATE -> DATE
        EditorQuestionType.SELECT_MULTIPLE -> SELECTION_COUNT
        EditorQuestionType.LOCATION -> LOCATION
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

/** Operator for comparing a question's answer against another question's answer. */
enum class ComparisonOperator(val xpath: String) {
  EQUALS("="),
  NOT_EQUALS("!="),
  GREATER_THAN(">"),
  GREATER_THAN_OR_EQUAL(">="),
  LESS_THAN("<"),
  LESS_THAN_OR_EQUAL("<=");

  companion object {
    fun fromXpath(op: String): ComparisonOperator? = entries.firstOrNull { it.xpath == op }

    fun supportsType(type: EditorQuestionType): Boolean = availableFor(type).isNotEmpty()

    fun availableFor(type: EditorQuestionType): List<ComparisonOperator> =
      when (type) {
        EditorQuestionType.NUMBER,
        EditorQuestionType.DATE -> entries
        EditorQuestionType.TEXT,
        EditorQuestionType.LONG_TEXT -> listOf(EQUALS, NOT_EQUALS)
        else -> emptyList()
      }

    fun areCompatible(left: EditorQuestionType, right: EditorQuestionType): Boolean =
      when (left) {
        EditorQuestionType.NUMBER -> right == EditorQuestionType.NUMBER
        EditorQuestionType.DATE -> right == EditorQuestionType.DATE
        EditorQuestionType.TEXT,
        EditorQuestionType.LONG_TEXT ->
          right == EditorQuestionType.TEXT || right == EditorQuestionType.LONG_TEXT
        else -> false
      }
  }
}

/**
 * Cross-field comparison rule (`. <operator> <target>`), e.g. `end_date >= start_date` or
 * `harvested_area <= total_area`. The target is referenced by [EditorQuestion.key] so renaming a
 * question keeps the rule intact.
 */
data class EditorFieldComparison(
  val operator: ComparisonOperator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
  val targetQuestionKey: String = "",
)

/**
 * One condition a valid answer meets, read from a question's [EditorValidation] by
 * [ValidationRules.conditions]. The editor describes each in the user's language; numbers are kept
 * as the organizer typed them, and dates are ISO `YYYY-MM-DD`.
 */
sealed interface ValidationCondition {
  data class NumberBetween(val min: String, val max: String) : ValidationCondition

  data class NumberAtLeast(val min: String) : ValidationCondition

  data class NumberGreaterThan(val min: String) : ValidationCondition

  data class NumberAtMost(val max: String) : ValidationCondition

  data class NumberLessThan(val max: String) : ValidationCondition

  data class LengthBetween(val min: Int, val max: Int) : ValidationCondition

  data class LengthAtLeast(val min: Int) : ValidationCondition

  data class LengthAtMost(val max: Int) : ValidationCondition

  data class MatchesPattern(val pattern: TextPattern) : ValidationCondition

  data object NotInFuture : ValidationCondition

  data object NotInPast : ValidationCondition

  data class OnOrAfter(val date: String) : ValidationCondition

  data class AfterDate(val date: String) : ValidationCondition

  data class OnOrBefore(val date: String) : ValidationCondition

  data class BeforeDate(val date: String) : ValidationCondition

  data class SelectionsBetween(val min: Int, val max: Int) : ValidationCondition

  data class SelectionsAtLeast(val min: Int) : ValidationCondition

  data class SelectionsAtMost(val max: Int) : ValidationCondition

  data class CompareToQuestion(val operator: ComparisonOperator, val targetName: String) :
    ValidationCondition

  data class DistanceBetween(
    val minMeters: String,
    val maxMeters: String,
    val referenceName: String,
  ) : ValidationCondition

  data class DistanceAtLeast(val minMeters: String, val referenceName: String) : ValidationCondition

  data class DistanceAtMost(val maxMeters: String, val referenceName: String) : ValidationCondition

  data object CustomExpression : ValidationCondition
}

/**
 * Structured validation for one question. Which fields are used depends on the question's
 * [ValidationKind]:
 * - [ValidationKind.NUMBER_RANGE]: [min] / [max] are numbers ([minExclusive] / [maxExclusive] for
 *   strict `>` / `<`); optional [comparison] against another number question.
 * - [ValidationKind.TEXT]: [min] / [max] are lengths; [pattern] / [customPattern]; optional
 *   [comparison] (`=` / `!=`) against another text question.
 * - [ValidationKind.DATE]: [dateRule]; for [DateRule.BETWEEN], [min] / [max] are `YYYY-MM-DD`
 *   ([minExclusive] / [maxExclusive] for strict `>` / `<`); optional [comparison] against another
 *   date question.
 * - [ValidationKind.SELECTION_COUNT]: [min] / [max] are selection counts.
 * - [ValidationKind.LOCATION]: [distanceReferenceQuestionKey] plus [min] / [max] distance in
 *   meters.
 *
 * Any non-read-only question also supports [customExpression] (arbitrary XPath constraint) and
 * [condition] (apply validation only when the condition holds: `not(<cond>) or (<rule>)`).
 *
 * [message] is the optional error shown to data collectors (`jr:constraintMsg`); when blank, a
 * message is generated from the rule.
 */
data class EditorValidation(
  val min: String = "",
  val max: String = "",
  val minExclusive: Boolean = false,
  val maxExclusive: Boolean = false,
  val dateRule: DateRule? = null,
  val pattern: TextPattern? = null,
  val customPattern: String = "",
  val comparison: EditorFieldComparison? = null,
  val distanceReferenceQuestionKey: String = "",
  val customExpression: String = "",
  val condition: EditorRelevance? = null,
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
  fun isActive(
    type: EditorQuestionType,
    validation: EditorValidation?,
    form: EditorForm? = null,
    questionKey: String? = null,
  ): Boolean = validation != null && parts(type, validation, form, questionKey).isNotEmpty()

  /**
   * XPath `constraint` for a question of [type] (ODK XForms spec, "Bindings": `constraint` is
   * evaluated with `.` as the question's value), or `null` when no rule is set.
   */
  fun constraintExpression(
    type: EditorQuestionType,
    validation: EditorValidation?,
    form: EditorForm? = null,
    questionKey: String? = null,
  ): String? {
    if (validation == null) return null
    val activeParts = parts(type, validation, form, questionKey)
    if (activeParts.isEmpty()) return null
    val core =
      if (activeParts.size == 1) {
        activeParts.single().xpath
      } else {
        activeParts.joinToString(" and ") { part ->
          if (part.needsParensInConjunction) "(${part.xpath})" else part.xpath
        }
      }
    val condExpr =
      if (validation.condition != null && form != null && questionKey != null) {
        val question = form.find(questionKey)
        if (question != null) {
          EditorXFormsGenerator.relevantExpression(
            form = form,
            relevance = validation.condition,
            targetQuestionIndex = form.indexOf(questionKey),
            contextSectionId = question.sectionId,
          )
        } else {
          null
        }
      } else {
        null
      }
    return if (condExpr != null) "not($condExpr) or ($core)" else core
  }

  /** The conditions [validation] sets for a question of [type]; invalid inputs are skipped. */
  fun conditions(
    type: EditorQuestionType,
    validation: EditorValidation?,
    form: EditorForm? = null,
    questionKey: String? = null,
  ): List<ValidationCondition> =
    validation?.let { parts(type, it, form, questionKey).map(Part::condition) }.orEmpty()

  /**
   * The `jr:constraintMsg` to export: the organizer's message, or a default generated from the rule
   * (e.g. "Must be between 0 and 120."). The default is form content, not editor text: it is
   * written in English, the forms' default language, whatever the editor's language.
   */
  fun message(
    type: EditorQuestionType,
    validation: EditorValidation?,
    form: EditorForm? = null,
    questionKey: String? = null,
  ): String? =
    validation?.message?.trim()?.ifEmpty { null }
      ?: DefaultConstraintMessage.of(conditions(type, validation, form, questionKey))

  /**
   * Adapts [validation] when a question changes from [oldType] to [newType]. Type-specific rules
   * are kept only when the [ValidationKind] matches; type-independent rules
   * ([EditorValidation.customExpression], [EditorValidation.condition], [EditorValidation.message])
   * are preserved for any non-read-only type.
   */
  fun adaptToType(
    oldType: EditorQuestionType,
    newType: EditorQuestionType,
    validation: EditorValidation?,
  ): EditorValidation? {
    if (validation == null || newType.isReadOnly) return null
    val oldKind = ValidationKind.of(oldType)
    val newKind = ValidationKind.of(newType)
    if (oldKind != null && oldKind == newKind) return validation
    val kept =
      EditorValidation(
        customExpression = validation.customExpression,
        condition = validation.condition,
        message = validation.message,
      )
    return kept.takeIf { it != EditorValidation() }
  }

  /** Problems that prevent the question's validation from compiling to a meaningful constraint. */
  fun issues(question: EditorQuestion, form: EditorForm? = null): List<DomainMessage> {
    val validation = question.validation ?: return emptyList()
    if (question.type.isReadOnly) return emptyList()
    val min = validation.min.trim()
    val max = validation.max.trim()
    val typeIssues =
      when (ValidationKind.of(question.type)) {
        null -> emptyList()
        ValidationKind.NUMBER_RANGE ->
          numberRangeIssues(min, max, validation.minExclusive, validation.maxExclusive)
        ValidationKind.TEXT ->
          countIssues(isText = true, min, max) + customPatternIssues(validation)
        ValidationKind.SELECTION_COUNT ->
          countIssues(isText = false, min, max) + selectionsMoreThanChoicesIssues(question, min)
        ValidationKind.DATE ->
          dateIssues(validation, min, max, validation.minExclusive, validation.maxExclusive)
        ValidationKind.LOCATION -> distanceIssues(question, validation, min, max, form)
      }
    return typeIssues +
      comparisonIssues(question, validation, form) +
      customExpressionIssues(validation)
  }

  private fun <T : Comparable<T>> isInvertedRange(
    lo: T?,
    hi: T?,
    minExclusive: Boolean,
    maxExclusive: Boolean,
  ): Boolean {
    if (lo == null || hi == null) return false
    return lo > hi || (lo == hi && (minExclusive || maxExclusive))
  }

  private fun numberRangeIssues(
    min: String,
    max: String,
    minExclusive: Boolean,
    maxExclusive: Boolean,
  ): List<DomainMessage> = buildList {
    val lo = min.takeIf(::isValidNumber)?.toDoubleOrNull()
    val hi = max.takeIf(::isValidNumber)?.toDoubleOrNull()
    if (min.isNotEmpty() && lo == null) add(EditorIssueMessage.MIN_NOT_NUMBER.message())
    if (max.isNotEmpty() && hi == null) add(EditorIssueMessage.MAX_NOT_NUMBER.message())
    if (isInvertedRange(lo, hi, minExclusive, maxExclusive)) {
      add(EditorIssueMessage.MIN_MORE_THAN_MAX.message())
    }
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
    minExclusive: Boolean,
    maxExclusive: Boolean,
  ): List<DomainMessage> = buildList {
    // Dates are picked with a date picker, so they're always ISO `YYYY-MM-DD`.
    if (validation.dateRule != DateRule.BETWEEN) return@buildList
    if (min.isEmpty() && max.isEmpty()) add(EditorIssueMessage.DATE_RANGE_MISSING.message())
    val lo = min.takeIf(::isValidDate)
    val hi = max.takeIf(::isValidDate)
    if (isInvertedRange(lo, hi, minExclusive, maxExclusive)) {
      add(EditorIssueMessage.DATE_RANGE_REVERSED.message())
    }
  }

  private fun comparisonIssues(
    question: EditorQuestion,
    validation: EditorValidation,
    form: EditorForm?,
  ): List<DomainMessage> {
    val cmp = validation.comparison ?: return emptyList()
    val availableOps = ComparisonOperator.availableFor(question.type)
    if (availableOps.isEmpty()) return emptyList()
    return buildList {
      if (cmp.operator !in availableOps) {
        add(
          EditorIssueMessage.COMPARISON_OPERATOR_NOT_APPLICABLE.message(
            DomainMessage.Label(cmp.operator),
            DomainMessage.Label(question.type),
          )
        )
      }
      if (cmp.targetQuestionKey.isBlank()) {
        add(EditorIssueMessage.COMPARISON_TARGET_MISSING.message())
      } else if (form != null) {
        val target = form.find(cmp.targetQuestionKey)
        when {
          target == null || target.key == question.key ->
            add(EditorIssueMessage.COMPARISON_TARGET_DELETED.message())
          !form.canReferTo(target, question.sectionId) &&
            form.sectionOf(target)?.repeatable == true ->
            add(EditorIssueMessage.COMPARISON_TARGET_IN_REPEAT.message(target.name))
          !ComparisonOperator.areCompatible(question.type, target.type) ->
            add(EditorIssueMessage.COMPARISON_TARGET_WRONG_TYPE.message(target.name))
        }
      }
    }
  }

  private fun distanceReferenceIssue(
    question: EditorQuestion,
    refKey: String,
    form: EditorForm?,
  ): DomainMessage? =
    when {
      refKey.isEmpty() -> EditorIssueMessage.DISTANCE_REFERENCE_MISSING.message()
      form == null -> null
      else -> {
        val ref = form.find(refKey)
        when {
          ref == null || ref.key == question.key ->
            EditorIssueMessage.DISTANCE_REFERENCE_DELETED.message()
          !form.canReferTo(ref, question.sectionId) && form.sectionOf(ref)?.repeatable == true ->
            EditorIssueMessage.DISTANCE_REFERENCE_IN_REPEAT.message(ref.name)
          ref.type != EditorQuestionType.LOCATION ->
            EditorIssueMessage.DISTANCE_REFERENCE_NOT_LOCATION.message(ref.name)
          else -> null
        }
      }
    }

  private fun distanceBoundsIssues(min: String, max: String): List<DomainMessage> = buildList {
    val lo = min.takeIf(::isValidNumber)?.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val hi = max.takeIf(::isValidNumber)?.toDoubleOrNull()?.takeIf { it >= 0.0 }
    if (min.isNotEmpty() && lo == null) add(EditorIssueMessage.DISTANCE_MIN_INVALID.message())
    if (max.isNotEmpty() && hi == null) add(EditorIssueMessage.DISTANCE_MAX_INVALID.message())
    if (lo != null && hi != null && lo > hi) {
      add(EditorIssueMessage.DISTANCE_MIN_MORE_THAN_MAX.message())
    }
  }

  private fun distanceIssues(
    question: EditorQuestion,
    validation: EditorValidation,
    min: String,
    max: String,
    form: EditorForm?,
  ): List<DomainMessage> {
    val refKey = validation.distanceReferenceQuestionKey.trim()
    if (refKey.isEmpty() && min.isEmpty() && max.isEmpty()) return emptyList()
    return buildList {
      distanceReferenceIssue(question, refKey, form)?.let(::add)
      addAll(distanceBoundsIssues(min, max))
    }
  }

  private fun customExpressionIssues(validation: EditorValidation): List<DomainMessage> {
    val expr = validation.customExpression.trim()
    if (expr.isEmpty()) return emptyList()
    return if (runCatching { XPathEngine.compile(expr) }.isFailure) {
      listOf(EditorIssueMessage.CUSTOM_EXPRESSION_INVALID.message())
    } else {
      emptyList()
    }
  }

  fun isValidDate(text: String): Boolean = isoDateToUtcMillis(text) != null

  private class Part(
    val xpath: String,
    val condition: ValidationCondition,
    val needsParensInConjunction: Boolean = false,
  )

  /** Valid, applicable pieces of [validation]; invalid inputs are skipped. */
  private fun parts(
    type: EditorQuestionType,
    validation: EditorValidation,
    form: EditorForm?,
    questionKey: String?,
  ): List<Part> {
    if (type.isReadOnly) return emptyList()
    val min = validation.min.trim()
    val max = validation.max.trim()
    val structured =
      when (ValidationKind.of(type)) {
        null -> emptyList()
        ValidationKind.NUMBER_RANGE ->
          numberRangeParts(min, max, validation.minExclusive, validation.maxExclusive)
        ValidationKind.TEXT -> lengthParts(min, max) + patternParts(validation)
        ValidationKind.DATE ->
          dateParts(validation, min, max, validation.minExclusive, validation.maxExclusive)
        ValidationKind.SELECTION_COUNT -> selectionCountParts(min, max)
        ValidationKind.LOCATION -> distanceParts(validation, min, max, form, questionKey)
      }
    return structured +
      comparisonParts(type, validation, form, questionKey) +
      customExpressionParts(validation)
  }

  private fun numberRangeParts(
    min: String,
    max: String,
    minExclusive: Boolean,
    maxExclusive: Boolean,
  ): List<Part> {
    val lo = min.takeIf(::isValidNumber)
    val hi = max.takeIf(::isValidNumber)
    val minOp = if (minExclusive) ">" else ">="
    val maxOp = if (maxExclusive) "<" else "<="
    return when {
      lo != null && hi != null && !minExclusive && !maxExclusive ->
        listOf(Part(". >= $lo and . <= $hi", ValidationCondition.NumberBetween(lo, hi)))
      else ->
        buildList {
          lo?.let {
            val cond =
              if (minExclusive) ValidationCondition.NumberGreaterThan(it)
              else ValidationCondition.NumberAtLeast(it)
            add(Part(". $minOp $it", cond))
          }
          hi?.let {
            val cond =
              if (maxExclusive) ValidationCondition.NumberLessThan(it)
              else ValidationCondition.NumberAtMost(it)
            add(Part(". $maxOp $it", cond))
          }
        }
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

  private fun dateParts(
    validation: EditorValidation,
    min: String,
    max: String,
    minExclusive: Boolean,
    maxExclusive: Boolean,
  ): List<Part> =
    when (validation.dateRule) {
      null -> emptyList()
      DateRule.NOT_IN_FUTURE -> listOf(Part(". <= today()", ValidationCondition.NotInFuture))
      DateRule.NOT_IN_PAST -> listOf(Part(". >= today()", ValidationCondition.NotInPast))
      DateRule.BETWEEN ->
        buildList {
          min.takeIf(::isValidDate)?.let {
            val op = if (minExclusive) ">" else ">="
            val cond =
              if (minExclusive) ValidationCondition.AfterDate(it)
              else ValidationCondition.OnOrAfter(it)
            add(Part(". $op date('$it')", cond))
          }
          max.takeIf(::isValidDate)?.let {
            val op = if (maxExclusive) "<" else "<="
            val cond =
              if (maxExclusive) ValidationCondition.BeforeDate(it)
              else ValidationCondition.OnOrBefore(it)
            add(Part(". $op date('$it')", cond))
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

  private fun comparisonParts(
    type: EditorQuestionType,
    validation: EditorValidation,
    form: EditorForm?,
    questionKey: String?,
  ): List<Part> {
    val cmp = validation.comparison ?: return emptyList()
    if (cmp.operator !in ComparisonOperator.availableFor(type)) return emptyList()
    if (cmp.targetQuestionKey.isBlank() || form == null) return emptyList()
    val target = form.find(cmp.targetQuestionKey) ?: return emptyList()
    if (target.key == questionKey) return emptyList()
    if (!ComparisonOperator.areCompatible(type, target.type)) return emptyList()
    val contextSectionId = questionKey?.let { form.find(it)?.sectionId }.orEmpty()
    if (!form.canReferTo(target, contextSectionId)) return emptyList()
    val ref = xpathQuestionRef(form, target, contextSectionId)
    val targetName = target.label.ifBlank { target.name }
    return listOf(
      Part(
        ". ${cmp.operator.xpath} $ref",
        ValidationCondition.CompareToQuestion(cmp.operator, targetName),
      )
    )
  }

  private fun validDistanceRef(
    form: EditorForm?,
    refKey: String,
    questionKey: String?,
    contextSectionId: String,
  ): EditorQuestion? =
    form?.find(refKey)?.takeIf {
      refKey.isNotEmpty() &&
        it.key != questionKey &&
        it.type == EditorQuestionType.LOCATION &&
        form.canReferTo(it, contextSectionId)
    }

  private fun distanceParts(
    validation: EditorValidation,
    min: String,
    max: String,
    form: EditorForm?,
    questionKey: String?,
  ): List<Part> {
    val refKey = validation.distanceReferenceQuestionKey.trim()
    val contextSectionId = questionKey?.let { form?.find(it)?.sectionId }.orEmpty()
    val refQuestion = validDistanceRef(form, refKey, questionKey, contextSectionId)
    val lo = min.takeIf(::isValidNumber)?.takeIf { (it.toDoubleOrNull() ?: -1.0) >= 0.0 }
    val hi = max.takeIf(::isValidNumber)?.takeIf { (it.toDoubleOrNull() ?: -1.0) >= 0.0 }
    val hasRef = form != null && refQuestion != null
    val hasBounds = lo != null || hi != null
    if (!hasRef || !hasBounds) return emptyList()
    val refPath = xpathQuestionRef(form, refQuestion, contextSectionId)
    val refName = refQuestion.label.ifBlank { refQuestion.name }
    val distCall = "distance(., $refPath)"
    return when {
      lo != null && hi != null ->
        listOf(
          Part(
            "$distCall >= $lo and $distCall <= $hi",
            ValidationCondition.DistanceBetween(lo, hi, refName),
          )
        )
      lo != null ->
        listOf(Part("$distCall >= $lo", ValidationCondition.DistanceAtLeast(lo, refName)))
      else -> listOf(Part("$distCall <= $hi", ValidationCondition.DistanceAtMost(hi!!, refName)))
    }
  }

  private fun customExpressionParts(validation: EditorValidation): List<Part> {
    val expr = validation.customExpression.trim()
    if (expr.isEmpty()) return emptyList()
    if (runCatching { XPathEngine.compile(expr) }.isFailure) return emptyList()
    return listOf(Part(expr, ValidationCondition.CustomExpression, needsParensInConjunction = true))
  }

  private fun xpathQuestionRef(
    form: EditorForm,
    target: EditorQuestion,
    contextSectionId: String,
  ): String {
    val targetSection = form.sectionOf(target)
    return if (targetSection?.repeatable == true && targetSection.id == contextSectionId) {
      "../${target.name}"
    } else {
      form.instancePath(target)
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
      is ValidationCondition.NumberGreaterThan -> "be greater than ${condition.min}"
      is ValidationCondition.NumberAtMost -> "be at most ${condition.max}"
      is ValidationCondition.NumberLessThan -> "be less than ${condition.max}"
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
      is ValidationCondition.AfterDate -> "be after ${friendlyDate(condition.date)}"
      is ValidationCondition.OnOrBefore -> "be on or before ${friendlyDate(condition.date)}"
      is ValidationCondition.BeforeDate -> "be before ${friendlyDate(condition.date)}"
      is ValidationCondition.SelectionsBetween ->
        "select between ${condition.min} and ${condition.max} options"
      is ValidationCondition.SelectionsAtLeast ->
        "select at least ${count(condition.min, "option")}"
      is ValidationCondition.SelectionsAtMost -> "select at most ${count(condition.max, "option")}"
      is ValidationCondition.CompareToQuestion -> {
        val opPhrase =
          when (condition.operator) {
            ComparisonOperator.EQUALS -> "equal"
            ComparisonOperator.NOT_EQUALS -> "not equal"
            ComparisonOperator.GREATER_THAN -> "be greater than"
            ComparisonOperator.GREATER_THAN_OR_EQUAL -> "be at least"
            ComparisonOperator.LESS_THAN -> "be less than"
            ComparisonOperator.LESS_THAN_OR_EQUAL -> "be at most"
          }
        "$opPhrase ${condition.targetName}"
      }
      is ValidationCondition.DistanceBetween ->
        "be between ${condition.minMeters} and ${condition.maxMeters} meters from ${condition.referenceName}"
      is ValidationCondition.DistanceAtLeast ->
        "be at least ${condition.minMeters} meters from ${condition.referenceName}"
      is ValidationCondition.DistanceAtMost ->
        "be within ${condition.maxMeters} meters of ${condition.referenceName}"
      ValidationCondition.CustomExpression -> "satisfy the validation rule"
    }

  private fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"
}
