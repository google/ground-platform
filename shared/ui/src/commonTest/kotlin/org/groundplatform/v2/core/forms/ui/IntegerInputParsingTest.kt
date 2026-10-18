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
package org.groundplatform.v2.core.forms.ui

import groundplatform.v2.forms.DataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IntegerInputParsingTest {

  @Test
  fun parse_rejectsValuesAboveInt32Range() {
    // Regression: the widget used to call `parsed.toInt()`, so 3000000000 was silently stored as
    // -1294967296 instead of being surfaced to the user as a validation error.
    val result = parseIntegerInput("3000000000", DataType.TYPE_INT32)

    assertIs<IntegerInput.Invalid>(result)
    assertEquals("Enter a value between -2147483648 and 2147483647", result.message)
  }

  @Test
  fun parse_rejectsValuesBelowInt32Range() {
    val result = parseIntegerInput("-3000000000", DataType.TYPE_INT32)

    assertIs<IntegerInput.Invalid>(result)
  }

  @Test
  fun parse_acceptsInt32Boundaries() {
    assertEquals(
      IntegerInput.Valid(2147483647L),
      parseIntegerInput("2147483647", DataType.TYPE_INT32),
    )
    assertEquals(
      IntegerInput.Valid(-2147483648L),
      parseIntegerInput("-2147483648", DataType.TYPE_INT32),
    )
  }

  @Test
  fun parse_allowsFullLongRangeForInt64Fields() {
    assertEquals(
      IntegerInput.Valid(3000000000L),
      parseIntegerInput("3000000000", DataType.TYPE_INT64),
    )
  }

  @Test
  fun parse_treatsBlankAsEmpty() {
    assertEquals(IntegerInput.Empty, parseIntegerInput("", DataType.TYPE_INT32))
    assertEquals(IntegerInput.Empty, parseIntegerInput("   ", DataType.TYPE_INT32))
  }

  @Test
  fun parse_rejectsNonNumericText() {
    val result = parseIntegerInput("12.5", DataType.TYPE_INT32)

    assertIs<IntegerInput.Invalid>(result)
    assertEquals("Enter a whole integer number", result.message)
  }

  @Test
  fun parse_toleratesSurroundingWhitespace() {
    assertEquals(IntegerInput.Valid(42L), parseIntegerInput("  42  ", DataType.TYPE_INT32))
  }
}
