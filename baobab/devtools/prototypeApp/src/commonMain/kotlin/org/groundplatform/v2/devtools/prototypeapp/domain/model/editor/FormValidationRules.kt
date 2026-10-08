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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

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
        EditorQuestionType.INTEGER,
        EditorQuestionType.DECIMAL -> NUMBER_RANGE
        EditorQuestionType.TEXT,
        EditorQuestionType.LONG_TEXT -> TEXT
        EditorQuestionType.DATE -> DATE
        EditorQuestionType.SELECT_MULTIPLE -> SELECTION_COUNT
        else -> null
      }
  }
}

/** Which dates a date question accepts. */
enum class DateRule(val label: String) {
  NOT_IN_FUTURE("Not in the future"),
  NOT_IN_PAST("Not in the past"),
  BETWEEN("Between two dates"),
}

/** Preset text patterns, compiled to the XForms `regex()` function. */
enum class TextPattern(val label: String, val regex: String?, val summary: String) {
  EMAIL("Email address", "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", "be an email address"),
  DIGITS("Digits only", "^[0-9]+$", "contain only digits"),
  LETTERS("Letters only", "^[A-Za-z]+$", "contain only letters A–Z"),
  CUSTOM("Custom pattern", null, "match the pattern"),
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

/** Compiles [EditorValidation] to XForms and describes it in plain language. */
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

  /** Plain-language description, e.g. "Must be between 0 and 120.", or `null` if no rule. */
  fun summary(type: EditorQuestionType, validation: EditorValidation?): String? {
    if (validation == null) return null
    val phrases = parts(type, validation).map { it.phrase }
    if (phrases.isEmpty()) return null
    return if (ValidationKind.of(type) == ValidationKind.SELECTION_COUNT) {
      phrases.joinToString(" and ").replaceFirstChar { it.uppercase() } + "."
    } else {
      "Must " + phrases.joinToString(" and ") + "."
    }
  }

  /** The `jr:constraintMsg` to export: the custom message, or the generated summary. */
  fun message(type: EditorQuestionType, validation: EditorValidation?): String? =
    validation?.message?.trim()?.ifEmpty { null } ?: summary(type, validation)

  /** Drops [validation] if it doesn't fit a question of [newType]. */
  fun adaptToType(
    oldType: EditorQuestionType,
    newType: EditorQuestionType,
    validation: EditorValidation?,
  ): EditorValidation? {
    val kind = ValidationKind.of(newType) ?: return null
    return validation?.takeIf { ValidationKind.of(oldType) == kind }
  }

  /** Problems that prevent [validation] from compiling to a meaningful constraint. */
  fun issues(question: EditorQuestion): List<String> {
    val validation = question.validation ?: return emptyList()
    val kind = ValidationKind.of(question.type) ?: return emptyList()
    val issues = mutableListOf<String>()
    val min = validation.min.trim()
    val max = validation.max.trim()
    when (kind) {
      ValidationKind.NUMBER_RANGE -> {
        val lo = min.takeIf(::isValidNumber)?.toDoubleOrNull()
        val hi = max.takeIf(::isValidNumber)?.toDoubleOrNull()
        if (min.isNotEmpty() && lo == null) issues += "Minimum must be a number."
        if (max.isNotEmpty() && hi == null) issues += "Maximum must be a number."
        if (lo != null && hi != null && lo > hi) issues += "Minimum can't be more than maximum."
      }
      ValidationKind.TEXT,
      ValidationKind.SELECTION_COUNT -> {
        val noun = if (kind == ValidationKind.TEXT) "length" else "number of selections"
        val lo = min.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val hi = max.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (min.isNotEmpty() && (lo == null || lo < 0)) {
          issues += "Minimum $noun must be a whole number of 0 or more."
        }
        if (max.isNotEmpty() && (hi == null || hi < 0)) {
          issues += "Maximum $noun must be a whole number of 0 or more."
        }
        if (lo != null && hi != null && lo > hi) {
          issues += "Minimum $noun can't be more than maximum."
        }
        if (kind == ValidationKind.SELECTION_COUNT && lo != null && lo > question.choices.size) {
          issues += "Minimum number of selections is more than the number of choices."
        }
        if (kind == ValidationKind.TEXT && validation.pattern == TextPattern.CUSTOM) {
          val pattern = validation.customPattern
          when {
            pattern.isBlank() -> issues += "Enter a custom pattern."
            runCatching { Regex(pattern) }.isFailure -> issues += "Custom pattern isn't valid."
            '\'' in pattern && '"' in pattern ->
              issues += "Custom pattern can't contain both ' and \"."
          }
        }
      }
      ValidationKind.DATE ->
        // Dates are picked with a date picker, so they're always ISO `YYYY-MM-DD`.
        if (validation.dateRule == DateRule.BETWEEN) {
          if (min.isEmpty() && max.isEmpty()) issues += "Pick an earliest or latest date."
          if (isValidDate(min) && isValidDate(max) && min > max) {
            issues += "Earliest date can't be after latest date."
          }
        }
    }
    return issues
  }

  fun isValidDate(text: String): Boolean = isoDateToUtcMillis(text) != null

  private class Part(val xpath: String, val phrase: String)

  /** Valid, applicable pieces of [validation]; invalid inputs are skipped. */
  private fun parts(type: EditorQuestionType, validation: EditorValidation): List<Part> {
    val min = validation.min.trim()
    val max = validation.max.trim()
    return when (ValidationKind.of(type)) {
      null -> emptyList()
      ValidationKind.NUMBER_RANGE -> {
        val lo = min.takeIf(::isValidNumber)
        val hi = max.takeIf(::isValidNumber)
        when {
          lo != null && hi != null ->
            listOf(Part(". >= $lo and . <= $hi", "be between $lo and $hi"))
          lo != null -> listOf(Part(". >= $lo", "be at least $lo"))
          hi != null -> listOf(Part(". <= $hi", "be at most $hi"))
          else -> emptyList()
        }
      }
      ValidationKind.TEXT ->
        buildList {
          val lo = min.toIntOrNull()?.takeIf { it >= 0 }
          val hi = max.toIntOrNull()?.takeIf { it >= 0 }
          when {
            lo != null && hi != null ->
              add(
                Part(
                  "string-length(.) >= $lo and string-length(.) <= $hi",
                  "be $lo–$hi characters long",
                )
              )
            lo != null ->
              add(Part("string-length(.) >= $lo", "be at least ${count(lo, "character")} long"))
            hi != null ->
              add(Part("string-length(.) <= $hi", "be at most ${count(hi, "character")} long"))
          }
          val pattern = validation.pattern
          val regex =
            if (pattern == TextPattern.CUSTOM) validation.customPattern else pattern?.regex
          if (
            pattern != null &&
              !regex.isNullOrBlank() &&
              runCatching { Regex(regex) }.isSuccess &&
              !('\'' in regex && '"' in regex)
          ) {
            // ODK XForms spec, "XPath functions": regex(string value, string expression).
            add(Part("regex(., ${xpathStringLiteral(regex)})", pattern.summary))
          }
        }
      ValidationKind.DATE ->
        when (validation.dateRule) {
          null -> emptyList()
          DateRule.NOT_IN_FUTURE -> listOf(Part(". <= today()", "not be in the future"))
          DateRule.NOT_IN_PAST -> listOf(Part(". >= today()", "not be in the past"))
          DateRule.BETWEEN -> {
            val lo = min.takeIf(::isValidDate)
            val hi = max.takeIf(::isValidDate)
            buildList {
              lo?.let { add(Part(". >= date('$it')", "be on or after ${friendlyDate(it)}")) }
              hi?.let { add(Part(". <= date('$it')", "be on or before ${friendlyDate(it)}")) }
            }
          }
        }
      ValidationKind.SELECTION_COUNT -> {
        val lo = min.toIntOrNull()?.takeIf { it >= 0 }
        val hi = max.toIntOrNull()?.takeIf { it >= 0 }
        // ODK XForms spec, "XPath functions": count-selected(node) for `select` answers.
        when {
          lo != null && hi != null ->
            listOf(
              Part(
                "count-selected(.) >= $lo and count-selected(.) <= $hi",
                "select between $lo and $hi options",
              )
            )
          lo != null ->
            listOf(Part("count-selected(.) >= $lo", "select at least ${count(lo, "option")}"))
          hi != null ->
            listOf(Part("count-selected(.) <= $hi", "select at most ${count(hi, "option")}"))
          else -> emptyList()
        }
      }
    }
  }

  private fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"

  /** XPath 1.0 section 3.7: literals may be delimited by either `'` or `"`. */
  private fun xpathStringLiteral(value: String): String =
    if (value.contains('\'')) "\"$value\"" else "'$value'"
}

/**
 * Conversions between ISO `YYYY-MM-DD` dates and the UTC-midnight milliseconds used by the M3
 * `DatePicker`. Pure calendar arithmetic (proleptic Gregorian, H. Hinnant's `days_from_civil`), so
 * no time zone is involved and there is no off-by-one around midnight.
 */
private const val MILLIS_PER_DAY = 86_400_000L
private val ISO_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
private val MONTH_ABBREVIATIONS =
  listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** UTC midnight of [iso] in milliseconds, or `null` if [iso] isn't a real `YYYY-MM-DD` date. */
fun isoDateToUtcMillis(iso: String): Long? {
  val match = ISO_DATE.matchEntire(iso) ?: return null
  val (y, m, d) = match.destructured.toList().map { it.toInt() }
  if (m !in 1..12 || d !in 1..daysInMonth(y, m)) return null
  return daysFromCivil(y, m, d) * MILLIS_PER_DAY
}

/** ISO `YYYY-MM-DD` of the UTC calendar day containing [utcMillis]. */
fun utcMillisToIsoDate(utcMillis: Long): String {
  val (y, m, d) = civilFromDays(utcMillis.floorDiv(MILLIS_PER_DAY))
  return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-" +
    d.toString().padStart(2, '0')
}

/** Friendly form of an ISO date, e.g. "Mar 5, 2026"; returns [iso] unchanged if invalid. */
fun friendlyDate(iso: String): String {
  if (isoDateToUtcMillis(iso) == null) return iso
  val (y, m, d) = iso.split("-").map { it.toInt() }
  return "${MONTH_ABBREVIATIONS[m - 1]} $d, $y"
}

private fun daysInMonth(year: Int, month: Int): Int =
  when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4,
    6,
    9,
    11 -> 30
    else -> 31
  }

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
  val y = (if (month <= 2) year - 1 else year).toLong()
  val era = y.floorDiv(400L)
  val yoe = y - era * 400
  val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
  val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
  return era * 146_097 + doe - 719_468
}

private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
  val z = days + 719_468
  val era = z.floorDiv(146_097L)
  val doe = z - era * 146_097
  val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
  val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
  val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
  return Triple(y, m, d)
}
