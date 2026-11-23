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
package org.openforis.collectone.v2.web.ui.surveyeditor

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class SurveyAreaTextTest {
  private fun text(squareMeters: Double): String = runBlocking {
    surveyAreaText(squareMeters).resolveString()
  }

  @Test
  fun english_keepsTheEditorsNumbers() {
    assertEquals("8.50 ha", text(85_000.0))
    assertEquals("850 ha", text(8_500_000.0))
    assertEquals("9,999 ha", text(99_990_000.0))
    assertEquals("1,234 km²", text(1_234_000_000.0))
  }

  @Test
  fun units_areNamedInTheUsersLanguage() {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("th"))
    try {
      assertEquals("850 เฮกตาร์", text(8_500_000.0))
      assertEquals("1,234 ตร.กม.", text(1_234_000_000.0))
    } finally {
      Locale.setDefault(previous)
    }
  }
}
