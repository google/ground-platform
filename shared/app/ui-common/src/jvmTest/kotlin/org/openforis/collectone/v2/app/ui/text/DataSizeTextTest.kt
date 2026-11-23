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
import org.openforis.collectone.v2.app.domain.model.DataSize

class DataSizeTextTest {
  private fun text(bytes: Long): String = runBlocking { DataSize(bytes).toUiText().resolveString() }

  private fun <T> inLocale(languageTag: String, block: () -> T): T {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag(languageTag))
    return try {
      block()
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun amountsBelowTen_haveOneDecimal() {
    assertEquals("4.2 MB", text(4_200_000))
    assertEquals("1.0 GB", text(1_000_000_000))
    assertEquals("9.9 MB", text(9_940_000))
  }

  @Test
  fun amountsFromTen_areWhole() {
    assertEquals("48 MB", text(48_000_000))
    assertEquals("640 kB", text(640_000))
    assertEquals("10 MB", text(9_960_000)) // 9.96 rounds to 10, which is whole.
  }

  @Test
  fun gigabytesAndTerabytesBelowAHundred_haveOneDecimal() {
    assertEquals("12.4 GB", text(12_400_000_000))
    assertEquals("99.9 GB", text(99_940_000_000))
    assertEquals("100 GB", text(99_960_000_000)) // 99.96 rounds to 100, which is whole.
    assertEquals("120 GB", text(120_400_000_000))
    assertEquals("12.5 TB", text(12_500_000_000_000))
  }

  @Test
  fun units_areDecimal() {
    assertEquals("1.0 kB", text(1000))
    assertEquals("1.0 MB", text(1_000_000))
    assertEquals("977 kB", text(976_563)) // Not "0.9 MB", as a 1,024-based unit would give.
  }

  @Test
  fun roundingUpToAThousand_movesToTheNextUnit() {
    assertEquals("1.0 MB", text(999_600))
    assertEquals("1.0 GB", text(999_700_000))
  }

  @Test
  fun smallAndHugeSizes_stayInTheSmallestAndLargestUnit() {
    assertEquals("0 kB", text(0))
    assertEquals("0.4 kB", text(400))
    assertEquals("1.2 TB", text(1_200_000_000_000))
    assertEquals("1500 TB", text(1_500_000_000_000_000))
  }

  @Test
  fun units_areWrittenInTheUsersLanguage() {
    assertEquals("48 Mo", inLocale("fr") { text(48_000_000) })
    assertEquals("4.2 Go", inLocale("fr") { text(4_200_000_000) })
    assertEquals("48 MB", inLocale("es") { text(48_000_000) })
  }
}
