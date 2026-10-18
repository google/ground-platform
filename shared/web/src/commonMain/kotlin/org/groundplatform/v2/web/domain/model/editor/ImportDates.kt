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
package org.groundplatform.v2.web.domain.model.editor

/** How to read numeric dates like `03/04/2025` whose day and month could be either way round. */
enum class DateOrder(val label: String, val example: String) {
  DAY_FIRST("Day first", "DD/MM/YYYY"),
  MONTH_FIRST("Month first", "MM/DD/YYYY");

  companion object {
    /** Regions (ISO 3166 codes) that write month first: the US, its territories, and a few more. */
    private val MONTH_FIRST_REGIONS =
      setOf("US", "PR", "GU", "VI", "AS", "MP", "UM", "PH", "FM", "MH", "PW")

    /**
     * The usual order for [region], an ISO 3166 code such as `US` (blank or unknown: day first).
     */
    fun forRegion(region: String?): DateOrder =
      if (region?.uppercase() in MONTH_FIRST_REGIONS) MONTH_FIRST else DAY_FIRST
  }
}

/** A column read as dates: the ISO `YYYY-MM-DD` value of each cell and how they were read. */
data class DateColumn(
  val isoValues: List<String>,
  /** True if day and month could be either way round, so [DateOrder] decided. */
  val ambiguous: Boolean,
)

/**
 * Recognizes the date formats commonly found in spreadsheets and converts them to ISO dates: year
 * first (`2025-04-03`, `2025/4/3`), day or month first with a 4-digit year (`03/04/2025`,
 * `3.4.2025`) or a 2-digit year with `/` or `-` (`3/4/25`), and English month names (`3 Apr 2025`,
 * `03-Apr-2025`, `April 3, 2025`). Values with a time of day aren't treated as dates.
 */
object ImportDates {

  private val YEAR_FIRST = Regex("""^(\d{4})([-/.])(\d{1,2})\2(\d{1,2})$""")
  private val YEAR_LAST = Regex("""^(\d{1,2})([-/.])(\d{1,2})\2(\d{4}|\d{2})$""")
  private val DAY_MONTH_NAME = Regex("""^(\d{1,2})[-\s/.]+([A-Za-z]{3,9})\.?,?[-\s/.]+(\d{4})$""")
  private val MONTH_NAME_DAY =
    Regex("""^([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})$""")

  private val MONTHS =
    listOf(
      "january",
      "february",
      "march",
      "april",
      "may",
      "june",
      "july",
      "august",
      "september",
      "october",
      "november",
      "december",
    )

  /** A value's date parts; [first] and [second] are day and month in an order not yet known. */
  private sealed interface Shape {
    data class Exact(val year: Int, val month: Int, val day: Int) : Shape

    data class Numeric(val first: Int, val second: Int, val year: Int) : Shape
  }

  /**
   * Reads every non-blank value in [values] as a date, or returns `null` if any isn't one. Day and
   * month order is taken from the data when a value settles it (e.g. `25/12/2025`) and from
   * [preferred] when no value does.
   */
  fun column(values: List<String>, preferred: DateOrder): DateColumn? {
    val shapes = values.map { v -> if (v.isBlank()) null else shape(v.trim()) ?: return null }
    if (shapes.all { it == null }) return null
    val numeric = shapes.filterIsInstance<Shape.Numeric>()
    val dayFirstOk = numeric.all { isValid(it.year, it.second, it.first) }
    val monthFirstOk = numeric.all { isValid(it.year, it.first, it.second) }
    val order =
      when {
        dayFirstOk && monthFirstOk -> preferred
        dayFirstOk -> DateOrder.DAY_FIRST
        monthFirstOk -> DateOrder.MONTH_FIRST
        else -> return null
      }
    val ambiguous =
      numeric.isNotEmpty() &&
        dayFirstOk &&
        monthFirstOk &&
        numeric.any {
          it.first != it.second
        }
    val iso = shapes.map { s ->
      when (s) {
        null -> ""
        is Shape.Exact -> iso(s.year, s.month, s.day)
        is Shape.Numeric ->
          if (order == DateOrder.DAY_FIRST) iso(s.year, s.second, s.first)
          else iso(s.year, s.first, s.second)
      }
    }
    return DateColumn(iso, ambiguous)
  }

  private fun shape(v: String): Shape? {
    YEAR_FIRST.matchEntire(v)?.let { m ->
      val (y, _, mo, d) = m.destructured
      return exact(y.toInt(), mo.toInt(), d.toInt())
    }
    YEAR_LAST.matchEntire(v)?.let { m ->
      val (a, sep, b, y) = m.destructured
      if (y.length == 2 && sep == ".") return null // Too much like a version number.
      val year = if (y.length == 2) expandYear(y.toInt()) else y.toInt()
      return Shape.Numeric(a.toInt(), b.toInt(), year)
    }
    DAY_MONTH_NAME.matchEntire(v)?.let { m ->
      val (d, name, y) = m.destructured
      return exact(y.toInt(), month(name) ?: return null, d.toInt())
    }
    MONTH_NAME_DAY.matchEntire(v)?.let { m ->
      val (name, d, y) = m.destructured
      return exact(y.toInt(), month(name) ?: return null, d.toInt())
    }
    return null
  }

  private fun exact(year: Int, month: Int, day: Int): Shape? =
    if (isValid(year, month, day)) Shape.Exact(year, month, day) else null

  /** Month number for an English month name or its 3-letter (or `Sept`) abbreviation. */
  private fun month(name: String): Int? {
    val n = name.lowercase()
    if (n == "sept") return 9
    val i = MONTHS.indexOfFirst { full -> n == full || (n.length == 3 && full.startsWith(n)) }
    return if (i < 0) null else i + 1
  }

  /** 2-digit years: `00`–`49` are 2000–2049 and `50`–`99` are 1950–1999. */
  private fun expandYear(yy: Int): Int = if (yy < 50) 2000 + yy else 1900 + yy

  private fun isValid(year: Int, month: Int, day: Int): Boolean =
    year in 1000..9999 && month in 1..12 && day in 1..daysIn(year, month)

  private fun daysIn(year: Int, month: Int): Int =
    when (month) {
      2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
      4,
      6,
      9,
      11 -> 30
      else -> 31
    }

  private fun iso(year: Int, month: Int, day: Int): String =
    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
