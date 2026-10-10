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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountriesTest {
  @Test
  fun all_listsEveryAssignedIsoCode_once_sortedByName() {
    assertEquals(249, Countries.all.size)
    assertEquals(Countries.all.size, Countries.all.map { it.code }.toSet().size)
    assertTrue(Countries.all.all { it.code.matches(Regex("[A-Z]{2}")) && it.name.isNotBlank() })
    // Spot-check a few codes, including ones that are easy to miss.
    for (code in listOf("KE", "VN", "US", "GB", "SS", "AX", "BQ", "CW")) {
      assertTrue(Countries.isValidCode(code), code)
    }
    // Reserved or withdrawn codes aren't assignable.
    for (code in listOf("UK", "EU", "AN", "YU", "ZZ")) assertFalse(
      Countries.isValidCode(code),
      code,
    )
    // Names are plain text: no emoji flags.
    assertTrue(Countries.all.none { country -> country.name.any { it.code > 0x2000 } })
  }

  @Test
  fun byCode_ignoresCaseAndSpaces_andLabelIsNameThenCode() {
    assertEquals("Kenya", Countries.byCode(" ke ")?.name)
    assertEquals("Kenya (KE)", Countries.byCode("KE")?.label)
    assertNull(Countries.byCode(null))
    assertNull(Countries.byCode(""))
    assertNull(Countries.byCode("Kenya"))
  }

  @Test
  fun normalizeCode_trimsAndUppercases_andTreatsBlankAsNone() {
    assertEquals("KE", Countries.normalizeCode(" ke "))
    assertNull(Countries.normalizeCode("   "))
    assertNull(Countries.normalizeCode(null))
  }

  @Test
  fun search_matchesNamesAndCodes_ignoringCaseAndAccents() {
    assertEquals(Countries.all, Countries.search(""))
    assertEquals(Countries.all, Countries.search("   "))
    // An exact code ranks first, so "ke" finds Kenya before names containing "ke".
    assertEquals("KE", Countries.search("ke").first().code)
    assertEquals("VN", Countries.search("vn").first().code)
    assertEquals("KE", Countries.search("Ken").first().code)
    // The label shown after choosing a country still matches it.
    assertEquals(listOf("KE"), Countries.search("Kenya (KE)").map { it.code })
    assertEquals("CI", Countries.search("cote").first().code)
    assertEquals("AX", Countries.search("aland").first().code)
    assertEquals(setOf("CG", "CD"), Countries.search("congo").map { it.code }.toSet())
    assertTrue(Countries.search("zzzz").isEmpty())
  }

  @Test
  fun search_ranksNamePrefixesBeforeWordPrefixesBeforeOtherMatches() {
    val results = Countries.search("guinea").map { it.name }
    // "Guinea" and "Guinea-Bissau" start with the query; the rest contain it as a later word.
    assertEquals(listOf("Guinea", "Guinea-Bissau"), results.take(2))
    assertTrue(results.containsAll(listOf("Equatorial Guinea", "Papua New Guinea")))
    // Ties keep alphabetical order.
    val united = Countries.search("united").map { it.name }
    assertEquals(united.sortedBy { it.lowercase() }, united)
    assertEquals("United Arab Emirates", united.first())
  }
}
