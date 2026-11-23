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
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.domain.model.Countries
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_country_with_code

/** Country names and their order in the user's language, with English unchanged. */
class CountryTextTest {
  private val kenya = checkNotNull(Countries.byCode("KE"))
  private val ivoryCoast = checkNotNull(Countries.byCode("CI"))
  private val germany = checkNotNull(Countries.byCode("DE"))

  @Test
  fun english_keepsTheListedNames() {
    assertEquals("Kenya", kenya.localizedName("en"))
    assertEquals("Côte d'Ivoire", ivoryCoast.localizedName("en-US"))
  }

  @Test
  fun otherLanguages_useThePlatformNames() {
    assertEquals("Allemagne", germany.localizedName("fr"))
    assertEquals("Alemania", germany.localizedName("es"))
  }

  @Test
  fun label_isTheNameThenTheCode() {
    assertEquals(
      "Kenya (KE)",
      runBlocking { getString(Res.string.org_country_with_code, kenya.localizedName("en"), "KE") },
    )
  }

  @Test
  fun search_matchesEnglishAndLocalizedNames() {
    assertEquals("KE", searchCountries("ken", "fr").first().code)
    assertEquals(listOf("DE"), searchCountries("Allemagne", "fr").map { it.code })
  }

  @Test
  fun english_keepsTheListedOrder() {
    assertEquals(Countries.all, countriesInDisplayOrder("en"))
    assertEquals(Countries.all, countriesInDisplayOrder("en-GB"))
    assertEquals(Countries.all, searchCountries("", "en"))
    assertEquals(Countries.search("guinea"), searchCountries("guinea", "en"))
  }

  @Test
  fun otherLanguages_listCountriesByTheirLocalizedName() {
    val french = countriesInDisplayOrder("fr").map { it.code }

    // Afrique du Sud and Allemagne are listed with the A's, not as South Africa and Germany.
    assertTrue(french.indexOf("ZA") < french.indexOf("AL"), "Afrique du Sud before Albanie")
    assertTrue(french.indexOf("DE") < french.indexOf("AD"), "Allemagne before Andorre")
    // Accented names sort with their letter: Danemark, Égypte, Espagne.
    assertTrue(french.indexOf("DK") < french.indexOf("EG"), "Danemark before Égypte")
    assertTrue(french.indexOf("EG") < french.indexOf("ES"), "Égypte before Espagne")
    assertEquals(Countries.all.size, french.size)
    assertEquals(countriesInDisplayOrder("fr"), searchCountries("", "fr"))
  }

  @Test
  fun otherLanguages_rankTheCodeThenLocalizedNamePrefixes_inDisplayOrder() {
    // Albanie by its code, then Algérie and Allemagne, whose names start with "al".
    assertEquals(listOf("AL", "DZ", "DE"), searchCountries("al", "fr").take(3).map { it.code })
  }

  @Test
  fun french_matchesNamesIgnoringAccentsAndCase() {
    assertEquals("EG", searchCountries("egypte", "fr").first().code, "egypte finds Égypte")
    assertEquals("EG", searchCountries("EGYPTE", "fr").first().code, "EGYPTE finds Égypte")
    assertEquals("US", searchCountries("etats", "fr").first().code, "etats finds États-Unis")
    assertEquals(
      "US",
      searchCountries("ÉTATS-U", "fr").first().code,
      "accented queries still match",
    )
  }

  @Test
  fun french_accentInsensitiveMatchesKeepTheRanks() {
    val startingWithE = searchCountries("e", "fr").map { it.code }
    // Names starting with "e" (accented or not) come first, in display order; Kenya only contains
    // an "e", so it comes later.
    assertTrue(startingWithE.indexOf("EG") < startingWithE.indexOf("ES"), "Égypte before Espagne")
    assertTrue(startingWithE.indexOf("ES") < startingWithE.indexOf("KE"), "Espagne before Kenya")
    // Word prefixes in display order: Émirats arabes unis, then États-Unis (then Tunisia, by its
    // English name).
    assertEquals(listOf("AE", "US"), searchCountries("unis", "fr").take(2).map { it.code })
  }

  @Test
  fun vietnamese_matchesNamesIgnoringToneMarksAndDStroke() {
    assertEquals("Đức", germany.localizedName("vi"))
    assertEquals("DE", searchCountries("duc", "vi").first().code, "duc finds Đức")
    assertEquals("VN", searchCountries("viet", "vi").first().code, "viet finds Việt Nam")
    assertEquals("US", searchCountries("hoa ky", "vi").first().code, "hoa ky finds Hoa Kỳ")
  }
}
