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

class CoordinateDegreesTest {
  @Test
  fun positiveDegrees_areNotNegative() {
    assertEquals(CoordinateDegrees("36.9470", isNegative = false), CoordinateDegrees.of(36.947))
  }

  @Test
  fun negativeDegrees_keepTheirMagnitudeAndAreNegative() {
    assertEquals(CoordinateDegrees("0.4160", isNegative = true), CoordinateDegrees.of(-0.416))
  }

  @Test
  fun trailingZeros_areKept() {
    assertEquals("36.7000", CoordinateDegrees.of(36.7).magnitude)
    assertEquals("12.0000", CoordinateDegrees.of(12.0).magnitude)
  }

  @Test
  fun degrees_roundToFourDecimals() {
    assertEquals("0.4183", CoordinateDegrees.of(-0.41826).magnitude)
    assertEquals("0.4182", CoordinateDegrees.of(-0.41824).magnitude)
  }

  @Test
  fun rounding_carriesIntoWholeDegrees() {
    assertEquals("37.0000", CoordinateDegrees.of(36.99996).magnitude)
  }

  @Test
  fun valuesThatRoundToZero_areNotNegative() {
    assertEquals(CoordinateDegrees("0.0000", isNegative = false), CoordinateDegrees.of(-0.00001))
  }

  @Test
  fun extremes_keepAllDigits() {
    assertEquals(CoordinateDegrees("180.0000", isNegative = true), CoordinateDegrees.of(-180.0))
    assertEquals(CoordinateDegrees("90.0000", isNegative = false), CoordinateDegrees.of(90.0))
  }
}
