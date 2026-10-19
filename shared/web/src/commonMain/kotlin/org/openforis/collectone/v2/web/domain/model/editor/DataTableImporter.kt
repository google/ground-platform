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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType

/**
 * A Data table ready to be created from an uploaded CSV file: a property per column and one row
 * (without editor keys yet) per record.
 */
data class DataTableImportPlan(
  val name: String,
  val properties: List<EntityProperty>,
  val keyProperty: String,
  val labelProperty: String,
  val rows: List<Map<String, String>>,
  /** Labels of Date columns whose day and month order came from [dateOrder], not the data. */
  val ambiguousDateColumns: List<String> = emptyList(),
  /** How ambiguous numeric dates were read. */
  val dateOrder: DateOrder = DateOrder.DAY_FIRST,
)

/** The outcome of reading a CSV file: a plan (`null` if nothing can be imported) and any notes. */
data class DataTableImportResult(val plan: DataTableImportPlan?, val notes: List<String>)

/** A column's guessed type, with its cell values converted for that type. */
internal data class ColumnGuess(
  val type: PropertyType,
  val values: List<String>,
  val ambiguousDates: Boolean = false,
)

/** Turns a CSV file into a [DataTableImportPlan]. */
object DataTableImporter {

  /**
   * Plans a Data table for CSV [text]. The first record is the header; each column becomes a
   * property, typed as Integer, Decimal, Yes / No or Date when every value in it fits (see
   * [guessColumn]). Dates are stored as `YYYY-MM-DD`; numeric dates whose day and month could be
   * either way round are read in [dateOrder]. An `id` column with unique, non-blank values is the
   * key; otherwise a `row_id` key is generated. A `name` column, if any, is the label.
   */
  fun plan(
    fileName: String,
    text: String,
    dateOrder: DateOrder = DateOrder.DAY_FIRST,
  ): DataTableImportResult {
    val notes = mutableListOf<String>()
    val records = Csv.parse(text).filterNot { r -> r.all { it.isBlank() } }
    val header = records.firstOrNull()
    if (header == null) return DataTableImportResult(null, listOf("The file is empty."))
    val body = records.drop(1)
    if (body.isEmpty()) {
      return DataTableImportResult(null, listOf("The file has a header row but no data rows."))
    }

    // One property per column, with names made valid and unique.
    val names = mutableListOf<String>()
    header.forEach { column ->
      names += MapLayerImporter.uniqueName(MapLayerImporter.propertyName(column), names)
    }
    val width = names.size
    val ragged = body.count { it.size != width }
    if (ragged > 0) {
      notes +=
        "$ragged ${if (ragged == 1) "row has" else "rows have"} a different number of columns " +
          "than the header. Missing cells are left blank and extra cells are dropped."
    }
    val cells: List<List<String>> = body.map { r ->
      List(width) { i -> r.getOrNull(i)?.trim() ?: "" }
    }

    val idIndex = names.indexOf("id")
    val idValues = if (idIndex >= 0) cells.map { it[idIndex] } else emptyList()
    val idIsUsableKey =
      idIndex >= 0 && idValues.none { it.isEmpty() } && idValues.toSet().size == idValues.size
    if (idIndex >= 0 && !idIsUsableKey) {
      notes += "The id column has blank or repeated values, so a new Row ID column is added."
    }
    val keyProperty = if (idIsUsableKey) "id" else MapLayerImporter.uniqueName("row_id", names)

    val guesses = names.mapIndexed { i, name ->
      val column = cells.map { it[i] }
      if (name == keyProperty) ColumnGuess(PropertyType.TEXT, column)
      else guessColumn(column, dateOrder)
    }
    val labels = header.map { it.trim() }
    val properties = buildList {
      if (!idIsUsableKey) add(EntityProperty(keyProperty, "Row ID", required = true))
      names.forEachIndexed { i, name ->
        add(
          EntityProperty(
            name = name,
            label = labels[i].ifBlank { name },
            type = guesses[i].type,
            required = name == keyProperty,
          )
        )
      }
    }
    val ambiguousDateColumns =
      names.indices.filter { guesses[it].ambiguousDates }.map { labels[it].ifBlank { names[it] } }
    if (ambiguousDateColumns.isNotEmpty()) {
      notes +=
        "Dates in ${ambiguousDateColumns.joinToString { "\"$it\"" }} could be day or month " +
          "first. They're read as ${dateOrder.example}."
    }
    val labelProperty = names.firstOrNull { it == "name" } ?: keyProperty
    val rows =
      cells.indices.map { r ->
        buildMap {
          if (!idIsUsableKey) put(keyProperty, "R-${(r + 1).toString().padStart(4, '0')}")
          names.forEachIndexed { i, name ->
            val value = guesses[i].values[r]
            if (value.isNotEmpty()) put(name, value)
          }
        }
      }
    return DataTableImportResult(
      DataTableImportPlan(
        name = fileName.substringBeforeLast('.').ifBlank { "Imported data table" },
        properties = properties,
        keyProperty = keyProperty,
        labelProperty = labelProperty,
        rows = rows,
        ambiguousDateColumns = ambiguousDateColumns,
        dateOrder = dateOrder,
      ),
      notes,
    )
  }

  /**
   * Guesses the most specific type accepting every non-blank value in [values]: Integer, Decimal,
   * Yes / No, Date (see [ImportDates]), else Text. Values with leading zeros (codes like `007`)
   * aren't treated as numbers so they aren't changed by numeric handling.
   */
  internal fun guessColumn(values: List<String>, dateOrder: DateOrder): ColumnGuess {
    val present = values.filter { it.isNotBlank() }
    if (present.isEmpty()) return ColumnGuess(PropertyType.TEXT, values)
    val hasLeadingZero = present.any { it.length > 1 && it.startsWith("0") && it[1] != '.' }
    if (!hasLeadingZero) {
      if (present.all { PropertyType.INTEGER.accepts(it) }) {
        return ColumnGuess(PropertyType.INTEGER, values)
      }
      if (present.all { PropertyType.DECIMAL.accepts(it) }) {
        return ColumnGuess(PropertyType.DECIMAL, values)
      }
    }
    if (present.all { PropertyType.BOOLEAN.accepts(it) }) {
      return ColumnGuess(PropertyType.BOOLEAN, values)
    }
    ImportDates.column(values, dateOrder)?.let {
      return ColumnGuess(PropertyType.DATE, it.isoValues, it.ambiguous)
    }
    return ColumnGuess(PropertyType.TEXT, values)
  }
}

/** A small RFC 4180 CSV reader: quoted fields, escaped quotes, embedded newlines, CRLF and BOM. */
internal object Csv {

  /**
   * Parses [text] into records of fields. The delimiter is detected from the first line: a comma,
   * or a semicolon or tab if one of those is more common there.
   */
  fun parse(text: String): List<List<String>> {
    val input = text.removePrefix("\uFEFF")
    val delimiter = detectDelimiter(input)
    val records = mutableListOf<List<String>>()
    var record = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < input.length) {
      val c = input[i]
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < input.length && input[i + 1] == '"') {
            field.append('"')
            i++
          } else {
            inQuotes = false
          }
        } else {
          field.append(c)
        }
      } else {
        when (c) {
          '"' -> inQuotes = true
          delimiter -> {
            record += field.toString()
            field.clear()
          }
          '\r',
          '\n' -> {
            if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
            record += field.toString()
            field.clear()
            records += record
            record = mutableListOf()
          }
          else -> field.append(c)
        }
      }
      i++
    }
    if (field.isNotEmpty() || record.isNotEmpty()) {
      record += field.toString()
      records += record
    }
    return records
  }

  private fun detectDelimiter(text: String): Char {
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    return listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
  }
}
