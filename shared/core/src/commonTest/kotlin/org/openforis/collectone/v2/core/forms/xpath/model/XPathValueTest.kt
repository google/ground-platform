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
package org.openforis.collectone.v2.core.forms.xpath.model

import com.google.type.Date
import com.google.type.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.TypedValueList
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue.Bool
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue.Number
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue.Str

/** Conversions between evaluated XPath values and stored answers (calculate and setvalue). */
class XPathValueTest {

  @Test
  fun emptyResults_storeNothingExceptForStringTargets() {
    assertNull(Str("").toTypedValue(DataType.TYPE_INT32))
    assertNull(Number(Double.NaN).toTypedValue(DataType.TYPE_DOUBLE))
    assertEquals(TypedValue(string_value = ""), Str("").toTypedValue(DataType.TYPE_STRING))
  }

  @Test
  fun integerTargets_roundHalfUp() {
    assertEquals(TypedValue(int32_value = 3), Number(2.5).toTypedValue(DataType.TYPE_INT32))
    assertEquals(TypedValue(int64_value = -2L), Number(-2.5).toTypedValue(DataType.TYPE_INT64))
    // A non-numeric string is NaN, which stores nothing.
    assertNull(Str("abc").toTypedValue(DataType.TYPE_INT32))
  }

  @Test
  fun dateTargets_acceptDatesTimestampsStringsAndEpochDays() {
    val date = Date(year = 2024, month = 2, day = 29)

    assertEquals(
      TypedValue(date_value = date),
      XPathValue.DateVal(date).toTypedValue(DataType.TYPE_DATE),
    )
    assertEquals(TypedValue(date_value = date), Str("2024-02-29").toTypedValue(DataType.TYPE_DATE))
    // XForms dates are days since the Unix epoch when used as numbers.
    assertEquals(
      TypedValue(date_value = Date(year = 1970, month = 1, day = 2)),
      Number(1.0).toTypedValue(DataType.TYPE_DATE),
    )
    assertNull(Str("not a date").toTypedValue(DataType.TYPE_DATE))
  }

  @Test
  fun dateTimeTargets_convertDatesToMidnightUtc() {
    val fromDate =
      XPathValue.DateVal(Date(year = 1970, month = 1, day = 2)).toTypedValue(DataType.TYPE_DATETIME)

    assertEquals(86_400L, fromDate?.timestamp_value?.getEpochSecond())
    assertNull(Str("garbage").toTypedValue(DataType.TYPE_DATETIME))
  }

  @Test
  fun timeTargets_parseStrings() {
    assertEquals(
      TypedValue(time_value = TimeOfDay(hours = 13, minutes = 45, seconds = 0)),
      Str("13:45:00").toTypedValue(DataType.TYPE_TIME),
    )
    assertNull(Str("later").toTypedValue(DataType.TYPE_TIME))
  }

  @Test
  fun geoTargets_parseOdkSpaceAndSemicolonSeparatedText() {
    val point =
      GeoPoint(latitude = 1.5, longitude = 2.5, altitude_meters = 0.0, accuracy_meters = 0.0)

    assertEquals(
      TypedValue(geopoint_value = point),
      Str("1.5 2.5").toTypedValue(DataType.TYPE_GEOPOINT),
    )
    assertEquals(
      TypedValue(geotrace_value = GeoTrace(points = listOf(point, point))),
      Str("1.5 2.5 0 0; 1.5 2.5").toTypedValue(DataType.TYPE_GEOTRACE),
    )
    assertEquals(
      TypedValue(geoshape_value = GeoShape(points = listOf(point))),
      Str("1.5 2.5;bad").toTypedValue(DataType.TYPE_GEOSHAPE),
    )
    assertNull(Str("1.5").toTypedValue(DataType.TYPE_GEOPOINT))
    assertNull(Str("x y").toTypedValue(DataType.TYPE_GEOTRACE))
  }

  @Test
  fun binaryTargets_encodeTextAsUtf8() {
    assertEquals(
      TypedValue(binary_value = "photo.jpg".encodeUtf8()),
      Str("photo.jpg").toTypedValue(DataType.TYPE_BINARY),
    )
  }

  @Test
  fun unspecifiedTargets_inferTheTypeFromTheValue() {
    assertEquals(TypedValue(int32_value = 4), Number(4.0).toTypedValue())
    assertEquals(TypedValue(double_value = 4.5), Number(4.5).toTypedValue())
    // Whole numbers beyond Int range stay doubles rather than overflowing.
    assertEquals(TypedValue(double_value = 3e10), Number(3e10).toTypedValue())
    assertEquals(TypedValue(bool_value = false), Bool(false).toTypedValue())
    assertEquals(
      TypedValue(string_value = "a b"),
      XPathValue.ValueList(listOf(Str("a"), Str("b"))).toTypedValue(),
    )
  }

  @Test
  fun multiSelectTargets_splitOnWhitespace() {
    assertEquals(
      FieldValue(
        list_value =
          TypedValueList(
            values = listOf(TypedValue(string_value = "a"), TypedValue(string_value = "b"))
          )
      ),
      Str("  a\tb ").toFieldValue(DataType.TYPE_SELECT_MULTIPLE),
    )
  }

  @Test
  fun fieldValues_roundTripThroughXPathValues() {
    val list =
      FieldValue(
        list_value =
          TypedValueList(
            values = listOf(TypedValue(string_value = "x"), TypedValue(int32_value = 2))
          )
      )

    assertEquals(
      XPathValue.ValueList(listOf(Str("x"), Number(2.0))),
      XPathValue.fromFieldValue(list),
    )
    assertEquals(Str(""), XPathValue.fromFieldValue(null))
    assertEquals(Str(""), XPathValue.fromFieldValue(FieldValue()))
    assertEquals(Str(""), XPathValue.fromTypedValue(TypedValue()))
    assertEquals(Number(7.0), XPathValue.fromTypedValue(TypedValue(int64_value = 7L)))
  }

  @Test
  fun truthiness_followsXPathRulesPlusGeometry() {
    assertFalse(Number(Double.NaN).toBoolean())
    assertFalse(Number(0.0).toBoolean())
    assertTrue(Str("false").toBoolean())
    assertFalse(XPathValue.GeoTraceVal(GeoTrace()).toBoolean())
    assertFalse(XPathValue.BinaryVal("".encodeUtf8()).toBoolean())
    assertTrue(XPathValue.ValueList(listOf(Str(""))).toBoolean())
  }

  @Test
  fun stringsConvertToNumbersOrEpochDays() {
    assertEquals(12.5, Str(" 12.5 ").toNumber())
    assertEquals(1.0, Str("1970-01-02").toNumber())
    assertTrue(Str("   ").toNumber().isNaN())
    assertTrue(Str("twelve").toNumber().isNaN())
  }

  @Test
  fun numberFormatting_followsXPathSection4_2() {
    assertEquals("NaN", XPathValue.formatXPathNumber(Double.NaN))
    assertEquals("Infinity", XPathValue.formatXPathNumber(Double.POSITIVE_INFINITY))
    assertEquals("-Infinity", XPathValue.formatXPathNumber(Double.NEGATIVE_INFINITY))
    assertEquals("-0", XPathValue.formatXPathNumber(-0.0))
    assertEquals("3", XPathValue.formatXPathNumber(3.0))
    assertEquals("0.25", XPathValue.formatXPathNumber(0.25))
  }

  @Test
  fun geoValues_formatAsOdkGeopointText() {
    val point = GeoPoint(latitude = 1.0, longitude = 2.5, accuracy_meters = 3.0)

    assertEquals("1 2.5 0 3", XPathValue.GeoPointVal(point).toXPathString())
    assertEquals(
      "1 2.5 0 3; 1 2.5 0 3",
      XPathValue.GeoShapeVal(GeoShape(points = listOf(point, point))).toXPathString(),
    )
  }
}
