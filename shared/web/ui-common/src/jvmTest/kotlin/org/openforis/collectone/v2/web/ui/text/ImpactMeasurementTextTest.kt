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

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.domain.model.ImpactMessage
import org.openforis.collectone.v2.web.domain.model.ImpactText

class ImpactMeasurementTextTest {
  private fun hectares(value: Double): String = runBlocking {
    impactMeasurementText(ImpactFormat.hectares(value)).resolveString()
  }

  @Test
  fun english_writesHectaresLikeOtherImpactNumbers() {
    assertEquals("9.12 ha", hectares(9.12))
    assertEquals("9.8 ha", hectares(9.80))
    assertEquals("12.5 ha", hectares(12.46))
    assertEquals("1,234.6 ha", hectares(1234.56))
  }

  @Test
  fun measuredText_isResolvedInsideMessages() {
    val detail =
      ImpactText.of(ImpactMessage.DETAIL_COVERING, ImpactText.Measured(ImpactFormat.hectares(5.85)))

    assertTrue(runBlocking { detail.load() }.contains("5.85 ha"))
  }

  @Test
  fun units_areNamedInTheUsersLanguage() {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("sw"))
    try {
      assertEquals("1,234.6 hekta", hectares(1234.56))
    } finally {
      Locale.setDefault(previous)
    }
  }
}
