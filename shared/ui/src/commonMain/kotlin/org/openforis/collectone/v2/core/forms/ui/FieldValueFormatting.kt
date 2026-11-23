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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldValue
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.media.MediaCapture
import org.openforis.collectone.v2.core.forms.temporal.formatIsoInstant
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_no
import org.openforis.collectone.v2.core.forms.ui.resources.forms_polygon_vertex_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_trace_point_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_unanswered
import org.openforis.collectone.v2.core.forms.ui.resources.forms_yes

/** The words [formatFieldValue] needs, so the same formatting serves English data and the UI. */
private class FieldValueWords(
  val unanswered: String,
  val yes: String,
  val no: String,
  val tracePoints: (Int) -> String,
  val polygonVertices: (Int) -> String,
)

/** English words, for answer summaries stored as data (e.g. a submission's field entries). */
private val EnglishFieldValueWords =
  FieldValueWords(
    unanswered = "(Unanswered)",
    yes = "Yes",
    no = "No",
    tracePoints = { "$it trace points" },
    polygonVertices = { "$it polygon vertices" },
  )

/**
 * Formats a [FieldValue] into a human-readable English summary string. Hosts use it to store answer
 * summaries as data; the form UI shows the localized [fieldValueDisplayText] instead.
 */
fun formatFieldValueForDisplay(value: FieldValue?, dataType: DataType): String =
  formatFieldValue(value, dataType, EnglishFieldValueWords)

/**
 * Formats a [FieldValue] into a human-readable summary in the current locale, for read-only
 * displays and review cards.
 */
@Composable
fun fieldValueDisplayText(value: FieldValue?, dataType: DataType): String {
  // Plurals need their count up front; a value holds at most one geometry, so the resolved
  // phrases always match the count formatFieldValue asks for.
  val traceCount = value?.scalar_value?.geotrace_value?.points?.size ?: 0
  val shapeCount = value?.scalar_value?.geoshape_value?.points?.size ?: 0
  val tracePoints =
    pluralStringResource(Res.plurals.forms_trace_point_count, traceCount, traceCount)
  val polygonVertices =
    pluralStringResource(Res.plurals.forms_polygon_vertex_count, shapeCount, shapeCount)
  val words =
    FieldValueWords(
      unanswered = stringResource(Res.string.forms_unanswered),
      yes = stringResource(Res.string.forms_yes),
      no = stringResource(Res.string.forms_no),
      tracePoints = { tracePoints },
      polygonVertices = { polygonVertices },
    )
  return formatFieldValue(value, dataType, words)
}

private fun formatFieldValue(
  value: FieldValue?,
  dataType: DataType,
  words: FieldValueWords,
): String {
  val unanswered = words.unanswered
  if (value == null) return unanswered
  value.list_value?.let { list ->
    if (list.values.isEmpty()) return unanswered
    return list.values.mapNotNull { it.string_value }.joinToString(", ")
  }
  val scalar = value.scalar_value ?: return unanswered
  return when (dataType) {
    DataType.TYPE_BOOLEAN ->
      scalar.bool_value?.let { if (it) words.yes else words.no } ?: unanswered
    DataType.TYPE_INT32 ->
      (scalar.int32_value ?: scalar.int64_value?.toInt())?.toString() ?: unanswered
    DataType.TYPE_INT64 ->
      (scalar.int64_value ?: scalar.int32_value?.toLong())?.toString() ?: unanswered
    DataType.TYPE_DOUBLE -> scalar.double_value?.toString() ?: unanswered
    DataType.TYPE_DATE ->
      scalar.date_value?.let { d ->
        if (d.year > 0) {
          "${d.year.toString().padStart(4, '0')}-${d.month.toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
        } else {
          unanswered
        }
      } ?: unanswered
    DataType.TYPE_TIME ->
      scalar.time_value?.let { t ->
        "${t.hours.toString().padStart(2, '0')}:${t.minutes.toString().padStart(2, '0')}:${t.seconds.toString().padStart(2, '0')}"
      } ?: unanswered
    DataType.TYPE_DATETIME -> scalar.timestamp_value?.let(::formatIsoInstant) ?: unanswered
    DataType.TYPE_GEOPOINT ->
      scalar.geopoint_value?.let { gp ->
        "${gp.latitude}, ${gp.longitude} (±${gp.accuracy_meters}m)"
      } ?: unanswered
    DataType.TYPE_GEOTRACE ->
      scalar.geotrace_value?.let { gt -> words.tracePoints(gt.points.size) } ?: unanswered
    DataType.TYPE_GEOSHAPE ->
      scalar.geoshape_value?.let { gs -> words.polygonVertices(gs.points.size) } ?: unanswered
    DataType.TYPE_BINARY -> MediaCapture.attachmentFileName(value) ?: unanswered
    else ->
      scalar.string_value?.takeIf { it.isNotEmpty() }
        ?: scalar.int64_value?.toString()
        ?: scalar.int32_value?.toString()
        ?: scalar.double_value?.toString()
        ?: scalar.bool_value?.let { if (it) words.yes else words.no }
        ?: unanswered
  }
}
