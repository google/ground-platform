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

/** Search folding removes case and Latin accents, and nothing else. */
class SearchFoldingTest {
  @Test
  fun removesCaseAndLatinAccents() {
    assertEquals("egypte", foldedForSearch("Égypte"))
    assertEquals("etats-unis", foldedForSearch("États-Unis"))
    assertEquals("cote d'ivoire", foldedForSearch("Côte d'Ivoire"))
    assertEquals("sao tome-et-principe", foldedForSearch("Sao Tomé-et-Principe"))
  }

  @Test
  fun removesVietnameseToneMarksAndTheDStroke() {
    assertEquals("viet nam", foldedForSearch("Việt Nam"))
    assertEquals("duc", foldedForSearch("Đức"))
    assertEquals("hoa ky", foldedForSearch("Hoa Kỳ"))
  }

  @Test
  fun keepsTheMarksOfOtherScripts() {
    // Thai vowel and tone signs are part of the word: ไทย (Thailand) and กัมพูชา (Cambodia).
    assertEquals("ไทย", foldedForSearch("ไทย"))
    assertEquals("กัมพูชา", foldedForSearch("กัมพูชา"))
  }
}
