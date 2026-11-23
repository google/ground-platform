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
package org.openforis.collectone.v2.app.ui.text

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.openforis.collectone.v2.app.domain.model.editor.friendlyDate

class DateTextTest {
  private fun resolve(text: UiText): String = runBlocking { text.resolveString() }

  private fun <T> inLocale(locale: Locale, block: () -> T): T {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    return try {
      block()
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun english_matchesTheExportedEnglishDate() {
    (1..12).forEach { month ->
      val iso = "2026-${month.toString().padStart(2, '0')}-05"
      assertEquals(friendlyDate(iso), resolve(isoDateText(iso)))
    }
  }

  @Test
  fun english_dayIsUnpadded() {
    assertEquals("Mar 5, 2026", resolve(mediumDateText(LocalDate(2026, 3, 5))))
    assertEquals("Dec 31, 1999", resolve(mediumDateText(LocalDate(1999, 12, 31))))
  }

  @Test
  fun otherLanguages_useTheirMonthNamesAndOrder() {
    assertEquals(
      "5 mars 2026",
      inLocale(Locale.FRENCH) { resolve(mediumDateText(LocalDate(2026, 3, 5))) },
    )
    assertEquals(
      "5 de mar de 2026",
      inLocale(Locale.forLanguageTag("pt")) { resolve(mediumDateText(LocalDate(2026, 3, 5))) },
    )
  }

  @Test
  fun invalidDate_isShownAsIs() {
    assertEquals("2026-02-30", resolve(isoDateText("2026-02-30")))
  }
}
