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
package org.groundplatform.v2.app.domain.usecase

import org.groundplatform.v2.app.domain.model.editor.EditorChoice
import org.groundplatform.v2.app.domain.model.editor.slugify

/**
 * Parses choice option lists from plain text (.txt), comma-separated (.csv), or tab-separated
 * (.tsv) inputs for single-choice and multiple-choice questions (including cascading select lists).
 */
object ChoiceListImporter {

  private val LIST_NAME_HEADER_NAMES =
    setOf(
      "list_name",
      "listname",
      "list",
      "choice_list",
      "choices_list",
      "choices",
      "list_id",
    )
  private val VALUE_HEADER_NAMES =
    setOf("value", "name", "code", "id", "val", "choice_value", "item_name")
  private val LABEL_HEADER_NAMES =
    setOf("label", "title", "text", "name_en", "display_name", "choice_label", "item_label")
  private val PARENT_HEADER_NAMES =
    setOf(
      "parent",
      "parent_value",
      "parent_val",
      "parent_name",
      "parent_code",
      "parent_id",
      "cascade",
      "filter",
      "filter_value",
      "parentvalue",
      "parent_choice",
      "category",
    )

  /**
   * Parses choice options from [content].
   *
   * Formats supported:
   * - 1 column (text or single CSV column): each row is a label, value is auto-slugified.
   * - 2 columns: column 0 is value, column 1 is label.
   * - 3+ columns: column 0 is parentValue, column 1 is value, column 2 is label (unless a header
   *   specifies roles).
   * - Optional header rows matching standard column names (e.g. `value,label`,
   *   `parent,value,label`, `name,label`, `cascade,value,label`).
   */
  fun parseChoices(content: String, fileName: String = ""): List<EditorChoice> {
    val trimmedContent = content.trim()
    if (trimmedContent.isEmpty()) return emptyList()

    val lines = splitCsvLines(trimmedContent)
    if (lines.isEmpty()) return emptyList()

    val isTsv =
      fileName.endsWith(".tsv", ignoreCase = true) ||
        (trimmedContent.contains('\t') && !trimmedContent.contains(','))
    val delimiter = if (isTsv) '\t' else ','

    val parsedRows =
      lines
        .map { parseDelimitedRow(it, delimiter) }
        .filter { row -> row.isNotEmpty() && !row.all { it.isBlank() } }
    if (parsedRows.isEmpty()) return emptyList()

    val firstRow = parsedRows.first()
    val headerIndices = detectHeader(firstRow)

    val (dataRows, valIdx, lblIdx, parentIdx) =
      if (headerIndices != null) {
        val (v, l, p) = headerIndices
        Quadruple(parsedRows.drop(1), v, l, p)
      } else {
        // Without header: determine roles by column count
        val colCount = parsedRows.maxOf { it.size }
        when {
          colCount <= 1 -> Quadruple(parsedRows, -1, 0, -1)
          colCount == 2 -> Quadruple(parsedRows, 0, 1, -1)
          colCount == 3 -> {
            // Check if column 0 has identical value across all rows (indicates XLSForm list_name,
            // not a parent)
            val firstColValues =
              parsedRows.mapNotNull { it.getOrNull(0)?.trim() }.filter { it.isNotEmpty() }
            val isConstantListColumn =
              firstColValues.size > 1 && firstColValues.distinct().size == 1
            if (isConstantListColumn) {
              // Ignore column 0 (list_name), map column 1 -> value, column 2 -> label
              Quadruple(parsedRows, 1, 2, -1)
            } else {
              Quadruple(parsedRows, 1, 2, 0) // parentValue, value, label
            }
          }
          else -> Quadruple(parsedRows, 1, 2, 0) // parentValue, value, label
        }
      }

    val usedValues = mutableSetOf<String>()
    val result = mutableListOf<EditorChoice>()

    for (row in dataRows) {
      val rawLabel = if (lblIdx in row.indices) row[lblIdx].trim() else ""
      val rawVal = if (valIdx in row.indices) row[valIdx].trim() else ""
      val rawParent = if (parentIdx in row.indices) row[parentIdx].trim() else ""

      if (rawLabel.isEmpty() && rawVal.isEmpty()) continue

      val label = rawLabel.ifEmpty { rawVal }
      var value = rawVal.ifEmpty { slugify(label) }

      // Ensure choice value is unique
      if (value in usedValues) {
        var n = 2
        while ("${value}_$n" in usedValues) {
          n++
        }
        value = "${value}_$n"
      }
      usedValues += value

      result += EditorChoice(value = value, label = label, parentValue = rawParent)
    }

    return result
  }

  private fun detectHeader(row: List<String>): Triple<Int, Int, Int>? {
    var valIdx = -1
    var lblIdx = -1
    var parentIdx = -1
    var listNameIdx = -1

    for ((index, col) in row.withIndex()) {
      val clean = col.trim().lowercase().replace(" ", "_").replace("-", "_")
      when {
        clean in LIST_NAME_HEADER_NAMES && listNameIdx == -1 -> listNameIdx = index
        clean in VALUE_HEADER_NAMES && valIdx == -1 -> valIdx = index
        clean in LABEL_HEADER_NAMES && lblIdx == -1 -> lblIdx = index
        clean in PARENT_HEADER_NAMES && parentIdx == -1 -> parentIdx = index
      }
    }

    // A valid header must match at least label or value, or list_name with another column
    if (valIdx != -1 || lblIdx != -1 || (listNameIdx != -1 && row.size >= 2)) {
      val effectiveLabel =
        when {
          lblIdx != -1 -> lblIdx
          valIdx != -1 -> valIdx
          else -> if (listNameIdx == 0) 1 else 0
        }
      val effectiveVal =
        when {
          valIdx != -1 -> valIdx
          else -> -1
        }
      return Triple(effectiveVal, effectiveLabel, parentIdx)
    }

    return null
  }

  /** Splits a CSV string into logical lines, preserving embedded newlines inside quoted fields. */
  private fun splitCsvLines(content: String): List<String> {
    val lines = mutableListOf<String>()
    val currentLine = StringBuilder()
    var inQuotes = false

    var i = 0
    while (i < content.length) {
      val c = content[i]
      when {
        c == '"' -> {
          inQuotes = !inQuotes
          currentLine.append(c)
        }
        (c == '\r' || c == '\n') && !inQuotes -> {
          if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
            i++
          }
          val line = currentLine.toString().trim()
          if (line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
            lines += line
          }
          currentLine.clear()
        }
        else -> currentLine.append(c)
      }
      i++
    }

    val finalLine = currentLine.toString().trim()
    if (finalLine.isNotEmpty() && !finalLine.startsWith("#") && !finalLine.startsWith("//")) {
      lines += finalLine
    }

    return lines
  }

  /** Parses a single row into cells, taking quotes into account. */
  private fun parseDelimitedRow(line: String, delimiter: Char): List<String> {
    val cells = mutableListOf<String>()
    val currentCell = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
      val c = line[i]
      when {
        c == '"' -> {
          if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
            // Escaped quote
            currentCell.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        c == delimiter && !inQuotes -> {
          cells += currentCell.toString().trim()
          currentCell.clear()
        }
        else -> currentCell.append(c)
      }
      i++
    }
    cells += currentCell.toString().trim()

    return cells
  }

  private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
  )
}
