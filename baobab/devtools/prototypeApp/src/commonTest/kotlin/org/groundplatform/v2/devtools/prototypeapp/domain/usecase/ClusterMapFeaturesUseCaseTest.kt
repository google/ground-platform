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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureKind

class ClusterMapFeaturesUseCaseTest {
  private val useCase = ClusterMapFeaturesUseCase()

  @Test
  fun invoke_groupsNearbyFeaturesIntoSingleCluster() {
    val features =
      listOf(
        MapClusterFeatureItem(
          id = "f1",
          kind = MapFeatureKind.ENTITY,
          label = "Plot 1",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.30f,
          normalizedY = 0.30f,
        ),
        MapClusterFeatureItem(
          id = "f2",
          kind = MapFeatureKind.ENTITY,
          label = "Plot 2",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.31f,
          normalizedY = 0.31f,
        ),
      )
    val clusters = useCase(features, radiusNormalized = 0.10f)
    assertEquals(1, clusters.size)
    assertEquals(2, clusters.first().siteCount)
  }

  @Test
  fun computeScaleBarSpec_formatsMetricDistanceForSurvey() {
    val scaleBar = useCase.computeScaleBarSpec("survey-kenya-coffee", 0f)
    assertTrue(scaleBar.label.endsWith("m"))
  }
}
