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
package org.openforis.collectone.v2.app.ui.text

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit

class MeasurementTextTest {
  private fun resolve(text: UiText): String = runBlocking { text.resolveString() }

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
  fun wholeNumbers_haveNoFraction() {
    assertEquals("120 m", resolve(Measurement(120.0, MeasurementUnit.METER, 0).toUiText()))
    assertEquals("10 km", resolve(Measurement(10.0, MeasurementUnit.KILOMETER, 0).toUiText()))
  }

  @Test
  fun roundedOrMeasuredNumbers_keepTheirFraction() {
    assertEquals("2.97 acres", resolve(Measurement(2.97, MeasurementUnit.ACRE, 2).toUiText()))
    assertEquals("2.0 km", resolve(Measurement(2.0, MeasurementUnit.KILOMETER, 1).toUiText()))
    assertEquals("2.0 ha", resolve(Measurement(2.0, MeasurementUnit.HECTARE, null).toUiText()))
  }

  @Test
  fun writtenAmounts_areFollowedByTheUnit() {
    assertEquals(
      "1,234 km²",
      resolve(measurementText("1,234", MeasurementUnit.SQUARE_KILOMETER)),
    )
  }

  @Test
  fun gnssAccuracy_isPlusOrMinusALength() {
    assertEquals(
      "±3.0 m",
      resolve(gnssAccuracyText(Measurement(3.0, MeasurementUnit.METER, null))),
    )
  }

  @Test
  fun imperialGnssAccuracy_isPlusOrMinusTenthsOfFeet() {
    assertEquals(
      "±6.8 ft",
      resolve(gnssAccuracyText(Measurement(6.8, MeasurementUnit.FOOT, 1))),
    )
  }

  @Test
  fun everyUnit_hasAnEnglishNameMatchingItsSymbol() {
    MeasurementUnit.entries.forEach { unit ->
      assertEquals(unit.symbol, resolve(UiText.Resource(unit.nameRes)))
    }
  }

  @Test
  fun units_areNamedInTheUsersLanguage() {
    val feet = Measurement(394.0, MeasurementUnit.FOOT, 0)
    val hectares = Measurement(1.42, MeasurementUnit.HECTARE, null)

    assertEquals("394 pies", inLocale("es") { resolve(feet.toUiText()) })
    assertEquals("1.42 hekta", inLocale("sw") { resolve(hectares.toUiText()) })
    assertEquals("1.42 เฮกตาร์", inLocale("th") { resolve(hectares.toUiText()) })
  }
}
