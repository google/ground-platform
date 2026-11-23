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

class ScaleBarLengthTest {
  @Test
  fun lengthsBelowAKilometer_areWholeMeters() {
    assertEquals(Measurement(200.0, MeasurementUnit.METER, decimals = 0), scaleBarLength(200.0))
  }

  @Test
  fun lengthsFromAKilometerOn_areKilometers() {
    assertEquals(Measurement(5.0, MeasurementUnit.KILOMETER, decimals = 0), scaleBarLength(5000.0))
    assertEquals(Measurement(1.5, MeasurementUnit.KILOMETER, decimals = 1), scaleBarLength(1500.0))
  }

  @Test
  fun fractionsOfAMeter_keepTheirTenths() {
    assertEquals(Measurement(0.5, MeasurementUnit.METER, decimals = 1), scaleBarLength(0.5))
  }
}
