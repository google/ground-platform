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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit

class SurveyAreaDisplayTest {
  @Test
  fun smallAreas_areHectaresToTwoDecimals() {
    assertEquals(
      Measurement(8.5, MeasurementUnit.HECTARE, decimals = 2),
      SurveyAreaGeometry.displayArea(85_000.0),
    )
  }

  @Test
  fun mediumAreas_areWholeHectares() {
    assertEquals(
      Measurement(850.0, MeasurementUnit.HECTARE, decimals = 0),
      SurveyAreaGeometry.displayArea(8_500_000.0),
    )
  }

  @Test
  fun largeAreas_areWholeSquareKilometers() {
    assertEquals(
      Measurement(1234.0, MeasurementUnit.SQUARE_KILOMETER, decimals = 0),
      SurveyAreaGeometry.displayArea(1_234_000_000.0),
    )
  }
}
