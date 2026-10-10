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
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeEntitiesData

class ImpactCoverageTest {
  private val template = PrototypeFakeEntitiesData.defaultGeospatialEntities().first()

  private fun plot(id: String, geoId: String, areaHa: Double) =
    template.copy(id = id, geoId = geoId, areaHectares = areaHa)

  @Test
  fun featuresSharingAGeoId_countOnce() {
    val coverage =
      ImpactCoverage.of(
        listOf(plot("a", "S2-1", 2.0), plot("b", "S2-1", 2.0), plot("c", "S2-2", 1.5))
      )
    assertEquals(ImpactCoverage(featureCount = 2, areaHa = 3.5), coverage)
  }

  @Test
  fun featuresWithoutAGeoId_countIndividually_andAreasAreSummed() {
    val coverage =
      ImpactCoverage.of(
        listOf(plot("a", "", 1.0), plot("b", "  ", 2.0), plot("c", "S2-3", 0.5), plot("d", "", 1.0))
      )
    assertEquals(4, coverage.featureCount)
    assertEquals(4.5, coverage.areaHa)
  }

  @Test
  fun aSingleFeature_coversItsArea_andNoFeatures_coverNothing() {
    assertEquals(ImpactCoverage(1, 1.84), ImpactCoverage.of(listOf(plot("a", "S2-1", 1.84))))
    assertEquals(ImpactCoverage(), ImpactCoverage.of(emptyList()))
  }
}
