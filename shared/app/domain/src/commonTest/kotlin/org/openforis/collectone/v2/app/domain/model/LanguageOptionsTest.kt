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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The app languages, as every language picker (mobile and web) lists them. */
class LanguageOptionsTest {
  @Test
  fun languages_areListedByNativeName() {
    assertEquals(
      listOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "sw" to "Kiswahili",
        "pt" to "Português",
        "vi" to "Tiếng Việt",
        "th" to "ไทย",
        "lo" to "ພາສາລາວ",
        "km" to "ភាសាខ្មែរ",
      ),
      LANGUAGE_OPTIONS.map { it.code to it.label },
    )
  }

  @Test
  fun nativeNameOrder_ignoresCase_andBreaksTiesByCode() {
    val options =
      listOf(
        LanguageOption(code = "b", label = "ไทย"),
        LanguageOption(code = "z", label = "alpha"),
        LanguageOption(code = "a", label = "Alpha"),
        LanguageOption(code = "c", label = "Beta"),
      )

    assertEquals(
      listOf("a", "z", "c", "b"),
      options.sortedWith(LanguageOption.NATIVE_NAME_ORDER).map { it.code },
    )
  }
}
