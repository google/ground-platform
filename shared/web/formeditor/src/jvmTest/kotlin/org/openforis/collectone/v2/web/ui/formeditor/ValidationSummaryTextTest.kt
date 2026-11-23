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
package org.openforis.collectone.v2.web.ui.formeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.editor.DateRule
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.TextPattern

/** The English validation summaries the Form editor shows under a question's rules. */
class ValidationSummaryTextTest {
  private fun english(type: EditorQuestionType, validation: EditorValidation): String? =
    runBlocking {
      validationSummaryText(type, validation)?.resolveString()
    }

  @Test
  fun numberRange() {
    val number = EditorQuestionType.NUMBER
    assertEquals("Must be between 0 and 120.", english(number, EditorValidation("0", "120")))
    assertEquals("Must be at least 2.5.", english(number, EditorValidation(min = "2.5")))
    assertEquals("Must be at most -1.", english(number, EditorValidation(max = "-1")))
  }

  @Test
  fun textLength() {
    val text = EditorQuestionType.TEXT
    assertEquals("Must be 3–40 characters long.", english(text, EditorValidation("3", "40")))
    assertEquals("Must be at least 1 character long.", english(text, EditorValidation(min = "1")))
    assertEquals(
      "Must be at least 5 characters long.",
      english(text, EditorValidation(min = "5")),
    )
    assertEquals("Must be at most 1 character long.", english(text, EditorValidation(max = "1")))
    assertEquals("Must be at most 80 characters long.", english(text, EditorValidation(max = "80")))
  }

  @Test
  fun textPatterns() {
    val text = EditorQuestionType.TEXT
    assertEquals(
      "Must be an email address.",
      english(text, EditorValidation(pattern = TextPattern.EMAIL)),
    )
    assertEquals(
      "Must contain only digits.",
      english(text, EditorValidation(pattern = TextPattern.DIGITS)),
    )
    assertEquals(
      "Must contain only letters A–Z.",
      english(text, EditorValidation(pattern = TextPattern.LETTERS)),
    )
    assertEquals(
      "Must match the pattern.",
      english(text, EditorValidation(pattern = TextPattern.CUSTOM, customPattern = "^[A-Z]{2}$")),
    )
  }

  @Test
  fun textLengthAndPattern_areJoined() {
    assertEquals(
      "Must be 3–40 characters long and contain only digits.",
      english(
        EditorQuestionType.TEXT,
        EditorValidation(min = "3", max = "40", pattern = TextPattern.DIGITS),
      ),
    )
  }

  @Test
  fun dates() {
    val date = EditorQuestionType.DATE
    assertEquals(
      "Must not be in the future.",
      english(date, EditorValidation(dateRule = DateRule.NOT_IN_FUTURE)),
    )
    assertEquals(
      "Must not be in the past.",
      english(date, EditorValidation(dateRule = DateRule.NOT_IN_PAST)),
    )
    assertEquals(
      "Must be on or after Jan 1, 2026 and be on or before Dec 31, 2026.",
      english(
        date,
        EditorValidation(dateRule = DateRule.BETWEEN, min = "2026-01-01", max = "2026-12-31"),
      ),
    )
  }

  @Test
  fun selectionCount() {
    val multi = EditorQuestionType.SELECT_MULTIPLE
    assertEquals("Select between 1 and 3 options.", english(multi, EditorValidation("1", "3")))
    assertEquals("Select at least 1 option.", english(multi, EditorValidation(min = "1")))
    assertEquals("Select at least 2 options.", english(multi, EditorValidation(min = "2")))
    assertEquals("Select at most 1 option.", english(multi, EditorValidation(max = "1")))
    assertEquals("Select at most 4 options.", english(multi, EditorValidation(max = "4")))
  }

  @Test
  fun noRule_hasNoSummary() {
    assertNull(english(EditorQuestionType.NUMBER, EditorValidation()))
    assertNull(english(EditorQuestionType.PHOTO, EditorValidation(min = "1")))
  }
}
