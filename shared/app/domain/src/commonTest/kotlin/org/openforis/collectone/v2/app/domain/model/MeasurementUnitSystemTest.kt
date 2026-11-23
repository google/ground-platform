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

class MeasurementUnitSystemTest {
  @Test
  fun metricGnssAccuracy_isMetersAsMeasured() {
    assertEquals(
      Measurement(2.1, MeasurementUnit.METER, decimals = null),
      MeasurementUnitSystem.METRIC.gnssAccuracy(2.1),
    )
  }

  @Test
  fun imperialGnssAccuracy_isFeetCutToTenths() {
    assertEquals(
      Measurement(6.8, MeasurementUnit.FOOT, decimals = 1),
      MeasurementUnitSystem.IMPERIAL.gnssAccuracy(2.1),
    )
  }

  @Test
  fun metricLengthAndArea_areWholeMetersAndStoredHectares() {
    assertEquals(
      Measurement(480.0, MeasurementUnit.METER, decimals = 0),
      MeasurementUnitSystem.METRIC.length(480),
    )
    assertEquals(
      Measurement(1.42, MeasurementUnit.HECTARE, decimals = null),
      MeasurementUnitSystem.METRIC.area(1.42),
    )
  }

  @Test
  fun imperialLengthAndArea_areWholeFeetAndAcresToHundredths() {
    assertEquals(
      Measurement(1575.0, MeasurementUnit.FOOT, decimals = 0),
      MeasurementUnitSystem.IMPERIAL.length(480),
    )
    assertEquals(
      Measurement(3.51, MeasurementUnit.ACRE, decimals = 2),
      MeasurementUnitSystem.IMPERIAL.area(1.42),
    )
  }

  @Test
  fun areaUnit_isHectaresOrAcres() {
    assertEquals(MeasurementUnit.HECTARE, MeasurementUnitSystem.METRIC.areaUnit)
    assertEquals(MeasurementUnit.ACRE, MeasurementUnitSystem.IMPERIAL.areaUnit)
  }
}
