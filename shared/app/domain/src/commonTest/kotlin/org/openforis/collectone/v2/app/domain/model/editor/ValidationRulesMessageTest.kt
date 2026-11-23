/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The default `jr:constraintMsg` exported for a question's validation when the organizer wrote no
 * message of their own: one English sentence per rule family.
 */
class ValidationRulesMessageTest {
  private fun message(type: EditorQuestionType, validation: EditorValidation): String? =
    ValidationRules.message(type, validation)

  @Test
  fun numberRange() {
    val number = EditorQuestionType.NUMBER
    assertEquals("Must be between 0 and 120.", message(number, EditorValidation("0", "120")))
    assertEquals("Must be at least 2.5.", message(number, EditorValidation(min = "2.5")))
    assertEquals("Must be at most -1.", message(number, EditorValidation(max = "-1")))
  }

  @Test
  fun textLength() {
    val text = EditorQuestionType.TEXT
    assertEquals("Must be 3–40 characters long.", message(text, EditorValidation("3", "40")))
    assertEquals("Must be at least 1 character long.", message(text, EditorValidation(min = "1")))
    assertEquals(
      "Must be at least 5 characters long.",
      message(text, EditorValidation(min = "5")),
    )
    assertEquals("Must be at most 1 character long.", message(text, EditorValidation(max = "1")))
    assertEquals(
      "Must be at most 80 characters long.",
      message(EditorQuestionType.LONG_TEXT, EditorValidation(max = "80")),
    )
  }

  @Test
  fun textPatterns() {
    val text = EditorQuestionType.TEXT
    assertEquals(
      "Must be an email address.",
      message(text, EditorValidation(pattern = TextPattern.EMAIL)),
    )
    assertEquals(
      "Must contain only digits.",
      message(text, EditorValidation(pattern = TextPattern.DIGITS)),
    )
    assertEquals(
      "Must contain only letters A–Z.",
      message(text, EditorValidation(pattern = TextPattern.LETTERS)),
    )
    assertEquals(
      "Must match the pattern.",
      message(text, EditorValidation(pattern = TextPattern.CUSTOM, customPattern = "^[A-Z]{2}$")),
    )
  }

  @Test
  fun textLengthAndPattern_areJoined() {
    assertEquals(
      "Must be at least 2 characters long and contain only letters A–Z.",
      message(EditorQuestionType.TEXT, EditorValidation(min = "2", pattern = TextPattern.LETTERS)),
    )
  }

  @Test
  fun dates() {
    val date = EditorQuestionType.DATE
    assertEquals(
      "Must not be in the future.",
      message(date, EditorValidation(dateRule = DateRule.NOT_IN_FUTURE)),
    )
    assertEquals(
      "Must not be in the past.",
      message(date, EditorValidation(dateRule = DateRule.NOT_IN_PAST)),
    )
    assertEquals(
      "Must be on or after Jan 1, 2026 and be on or before Dec 31, 2026.",
      message(
        date,
        EditorValidation(dateRule = DateRule.BETWEEN, min = "2026-01-01", max = "2026-12-31"),
      ),
    )
    assertEquals(
      "Must be on or before Dec 31, 2026.",
      message(date, EditorValidation(dateRule = DateRule.BETWEEN, max = "2026-12-31")),
    )
  }

  @Test
  fun selectionCount() {
    val multi = EditorQuestionType.SELECT_MULTIPLE
    assertEquals("Select between 1 and 3 options.", message(multi, EditorValidation("1", "3")))
    assertEquals("Select at least 1 option.", message(multi, EditorValidation(min = "1")))
    assertEquals("Select at least 2 options.", message(multi, EditorValidation(min = "2")))
    assertEquals("Select at most 1 option.", message(multi, EditorValidation(max = "1")))
    assertEquals("Select at most 4 options.", message(multi, EditorValidation(max = "4")))
  }

  @Test
  fun customMessage_replacesTheGeneratedOne() {
    assertEquals(
      "Too many trees",
      message(EditorQuestionType.NUMBER, EditorValidation(max = "9", message = " Too many trees ")),
    )
  }

  @Test
  fun noRule_hasNoMessage() {
    assertNull(message(EditorQuestionType.NUMBER, EditorValidation()))
    assertNull(message(EditorQuestionType.PHOTO, EditorValidation(min = "1")))
  }
}
