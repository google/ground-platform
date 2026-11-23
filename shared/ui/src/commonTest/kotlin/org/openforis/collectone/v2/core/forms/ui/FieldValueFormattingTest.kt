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

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.TypedValueList

class FieldValueFormattingTest {

  private fun scalar(value: TypedValue) = FieldValue(scalar_value = value)

  private fun format(value: FieldValue?, type: DataType) = formatFieldValueForDisplay(value, type)

  @Test
  fun missingOrEmptyValues_areUnanswered() {
    assertEquals("(Unanswered)", format(null, DataType.TYPE_STRING))
    assertEquals("(Unanswered)", format(FieldValue(), DataType.TYPE_STRING))
    assertEquals(
      "(Unanswered)",
      format(FieldValue(list_value = TypedValueList()), DataType.TYPE_STRING),
    )
    assertEquals("(Unanswered)", format(scalar(TypedValue()), DataType.TYPE_BOOLEAN))
  }

  @Test
  fun lists_joinTheirChoiceCodes() {
    val value =
      FieldValue(
        list_value =
          TypedValueList(
            values = listOf(TypedValue(string_value = "oak"), TypedValue(string_value = "pine"))
          )
      )

    assertEquals("oak, pine", format(value, DataType.TYPE_STRING))
  }

  @Test
  fun integers_acceptEitherWidth() {
    assertEquals("7", format(scalar(TypedValue(int64_value = 7L)), DataType.TYPE_INT32))
    assertEquals("9", format(scalar(TypedValue(int32_value = 9)), DataType.TYPE_INT64))
  }

  @Test
  fun dates_arePaddedAndAZeroYearIsUnanswered() {
    assertEquals(
      "0987-03-05",
      format(
        scalar(TypedValue(date_value = Date(year = 987, month = 3, day = 5))),
        DataType.TYPE_DATE,
      ),
    )
    assertEquals(
      "(Unanswered)",
      format(
        scalar(TypedValue(date_value = Date(year = 0, month = 1, day = 1))),
        DataType.TYPE_DATE,
      ),
    )
  }

  @Test
  fun times_arePadded() {
    assertEquals(
      "07:05:09",
      format(
        scalar(TypedValue(time_value = TimeOfDay(hours = 7, minutes = 5, seconds = 9))),
        DataType.TYPE_TIME,
      ),
    )
  }

  @Test
  fun timestamps_areIsoUtcOnEveryPlatform() {
    // Wire's Instant has no ISO toString() off the JVM, so the web showed "Instant@…" instead.
    assertEquals(
      "2026-11-09T16:32:00Z",
      format(
        scalar(TypedValue(timestamp_value = ofEpochSecond(1_794_241_920L, 0L))),
        DataType.TYPE_DATETIME,
      ),
    )
    assertEquals(
      "2026-11-09T16:32:00.250Z",
      format(
        scalar(TypedValue(timestamp_value = ofEpochSecond(1_794_241_920L, 250_000_000L))),
        DataType.TYPE_DATETIME,
      ),
    )
  }

  @Test
  fun booleansAndGeometry_useWords() {
    assertEquals("Yes", format(scalar(TypedValue(bool_value = true)), DataType.TYPE_BOOLEAN))
    assertEquals("No", format(scalar(TypedValue(bool_value = false)), DataType.TYPE_BOOLEAN))
    val points = List(3) { GeoPoint(latitude = it.toDouble(), longitude = 0.0) }
    assertEquals(
      "3 trace points",
      format(
        scalar(TypedValue(geotrace_value = GeoTrace(points = points))),
        DataType.TYPE_GEOTRACE,
      ),
    )
    assertEquals(
      "3 polygon vertices",
      format(
        scalar(TypedValue(geoshape_value = GeoShape(points = points))),
        DataType.TYPE_GEOSHAPE,
      ),
    )
    assertEquals(
      "1.5, 2.5 (±4.0m)",
      format(
        scalar(
          TypedValue(
            geopoint_value = GeoPoint(latitude = 1.5, longitude = 2.5, accuracy_meters = 4.0)
          )
        ),
        DataType.TYPE_GEOPOINT,
      ),
    )
  }

  @Test
  fun otherTypes_showWhicheverValueIsSet() {
    assertEquals("abc", format(scalar(TypedValue(string_value = "abc")), DataType.TYPE_STRING))
    assertEquals("12", format(scalar(TypedValue(int64_value = 12L)), DataType.TYPE_STRING))
    assertEquals(
      "(Unanswered)",
      format(scalar(TypedValue(string_value = "")), DataType.TYPE_STRING),
    )
    assertEquals("Yes", format(scalar(TypedValue(bool_value = true)), DataType.TYPE_STRING))
  }
}
