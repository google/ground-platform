/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.impact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImpactUnitsTest {
  @Test
  fun convert_supportedPairs() {
    val cases =
      listOf(
        Triple("har", "m2", 10_000.0),
        Triple("m2", "har", 0.0001),
        Triple("km2", "har", 100.0),
        Triple("har", "km2", 0.01),
        Triple("km2", "m2", 1_000_000.0),
        Triple("t", "kg", 1_000.0),
        Triple("kg", "t", 0.001),
        Triple("g", "kg", 0.001),
        Triple("t", "g", 1_000_000.0),
        Triple("km", "m", 1_000.0),
        Triple("m", "km", 0.001),
        Triple("cm", "m", 0.01),
        Triple("m3", "L", 1_000.0),
        Triple("L", "m3", 0.001),
        Triple("l", "L", 1.0),
        Triple("l", "m3", 0.001),
      )
    for ((from, to, factor) in cases) {
      assertNear(factor, ImpactUnits.convert(1.0, from, to)!!, factor * 1e-12, "$from→$to")
    }
  }

  @Test
  fun convert_isExactForWholeFactors() {
    assertEquals(1_500.0, ImpactUnits.convert(1.5, "t", "kg"))
    assertEquals(0.5, ImpactUnits.convert(5_000.0, "m2", "har"))
    assertEquals(0.5, ImpactUnits.convert(500.0, "g", "kg"))
  }

  @Test
  fun convert_identity() {
    for (unit in listOf("har", "m2", "kg", "t", "L", "m3", "%", "{tree}")) {
      assertEquals(42.0, ImpactUnits.convert(42.0, unit, unit), unit)
    }
    assertEquals(3.0, ImpactUnits.convert(3.0, "", ""))
    assertEquals(3.0, ImpactUnits.convert(3.0, " ", ""))
  }

  @Test
  fun convert_differentDimensions_isNull() {
    assertNull(ImpactUnits.convert(1.0, "har", "kg"))
    assertNull(ImpactUnits.convert(1.0, "kg", "m3"))
    assertNull(ImpactUnits.convert(1.0, "m", "m2"))
    assertNull(ImpactUnits.convert(1.0, "L", "kg"))
  }

  @Test
  fun convert_unknownOrNonUcumUnits_isNull() {
    // UCUM `ha` is not the hectare, and codes are case-sensitive (except `L`/`l`).
    assertNull(ImpactUnits.convert(1.0, "ha", "m2"))
    assertNull(ImpactUnits.convert(1.0, "KG", "kg"))
    assertNull(ImpactUnits.convert(1.0, "M2", "har"))
    assertNull(ImpactUnits.convert(1.0, "m²", "har"))
    assertNull(ImpactUnits.convert(1.0, "acre", "har"))
    assertNull(ImpactUnits.convert(1.0, "%", "{tree}"))
  }

  @Test
  fun convert_blankOnlyConvertsToBlank() {
    assertNull(ImpactUnits.convert(1.0, "", "kg"))
    assertNull(ImpactUnits.convert(1.0, "kg", ""))
    assertNull(ImpactUnits.convert(1.0, "%", ""))
  }
}
