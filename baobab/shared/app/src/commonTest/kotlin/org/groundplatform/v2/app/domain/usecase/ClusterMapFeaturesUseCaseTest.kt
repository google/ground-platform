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
package org.groundplatform.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.app.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.app.domain.model.MapFeatureKind

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
    val cluster = clusters.first()
    assertEquals(2, cluster.siteCount)
    assertEquals(0.305f, cluster.normalizedX, 1e-4f)
    assertEquals(0.305f, cluster.normalizedY, 1e-4f)
    assertEquals(0.30f, cluster.minNormalizedX, 1e-4f)
    assertEquals(0.31f, cluster.maxNormalizedX, 1e-4f)
    assertEquals(0.30f, cluster.minNormalizedY, 1e-4f)
    assertEquals(0.31f, cluster.maxNormalizedY, 1e-4f)
    assertTrue(cluster.diameterNormalized > 0f)
  }

  @Test
  fun clusterRadiusNormalized_doublesForEachZoomLevelOutFromClusteringThreshold() {
    val onsetRadius =
      useCase.clusterRadiusNormalized(ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA)
    val oneLevelOut =
      useCase.clusterRadiusNormalized(ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA - 1.0f)
    val twoLevelsOut =
      useCase.clusterRadiusNormalized(ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA - 2.0f)

    assertEquals(ClusterMapFeaturesUseCase.BASE_CLUSTER_RADIUS_NORMALIZED, onsetRadius, 1e-4f)
    assertEquals(onsetRadius * 2.0f, oneLevelOut, 1e-4f)
    assertEquals(onsetRadius * 4.0f, twoLevelsOut, 1e-4f)
  }

  @Test
  fun invoke_progressivelyCoversBroaderAreaAndReducesClusterCountAsUserZoomsOut() {
    // Build a multi-scale distribution: 4 regional quadrants across [-1.5, 2.5] x [-1.5, 2.5],
    // each containing 2 sub-villages of 3 nearby plots.
    val regionCenters =
      listOf(
        -1.0f to -1.0f,
        2.0f to -1.0f,
        -1.0f to 2.0f,
        2.0f to 2.0f,
      )
    val villageOffsets = listOf(-0.28f to -0.20f, 0.28f to 0.20f)
    val plotOffsets = listOf(-0.04f to -0.03f, 0.0f to 0.02f, 0.04f to -0.02f)

    var nextId = 1
    val features = buildList {
      for ((rx, ry) in regionCenters) {
        for ((vx, vy) in villageOffsets) {
          for ((px, py) in plotOffsets) {
            add(
              MapClusterFeatureItem(
                id = "f-${nextId++}",
                kind = MapFeatureKind.ENTITY,
                label = "Plot $nextId",
                colorHex = 0xFF1E8E3EL,
                colorCss = "#1E8E3E",
                markerSymbol = "✓",
                normalizedX = rx + vx + px,
                normalizedY = ry + vy + py,
              )
            )
          }
        }
      }
    }

    val zoomLevels = listOf(-2.35f, -3.00f, -3.75f, -4.50f, -5.25f, -6.00f, -6.75f)
    val clustersByZoom = zoomLevels.map { zoomDelta ->
      useCase(features, useCase.clusterRadiusNormalized(zoomDelta))
    }

    // Every zoom level preserves total feature count
    clustersByZoom.forEach { clusters ->
      assertEquals(features.size, clusters.sumOf { it.totalCount })
    }

    // As the user zooms out, cluster count monotonically decreases while max cluster spatial span
    // monotonically increases across 4 hierarchy levels:
    // - At -2.35f: 8 village clusters (3 plots each)
    // - At -4.50f: 4 regional clusters (6 plots each)
    // - At -6.00f: 2 macro-region clusters (12 plots each)
    // - At -6.75f: 1 global cluster covering all 24 plots
    assertEquals(8, clustersByZoom[0].size)
    assertEquals(4, clustersByZoom[3].size)
    assertEquals(2, clustersByZoom[5].size)
    assertEquals(1, clustersByZoom[6].size)
    for (i in 0 until clustersByZoom.lastIndex) {
      val finer = clustersByZoom[i]
      val coarser = clustersByZoom[i + 1]
      assertTrue(
        coarser.size <= finer.size,
        "Expected cluster count to decrease or stay equal when zooming out: ${finer.size} -> ${coarser.size}",
      )
      val finerMaxDiameter = finer.maxOf { it.diameterNormalized }
      val coarserMaxDiameter = coarser.maxOf { it.diameterNormalized }
      assertTrue(
        coarserMaxDiameter >= finerMaxDiameter - 1e-4f,
        "Expected cluster spatial coverage to grow or stay equal when zooming out: $finerMaxDiameter -> $coarserMaxDiameter",
      )
    }
  }

  @Test
  fun invoke_preventsUnboundedChainingAcrossUniformGrid() {
    // 11 x 11 uniform grid across [0.0, 2.0] x [0.0, 2.0] (spacing = 0.20, total span = 2.0).
    // At radius = 0.22, adjacent points are within 1 radius, but bounded-diameter clustering must
    // partition the grid into multiple local clusters rather than chaining all 121 points into 1.
    var id = 1
    val gridFeatures = buildList {
      for (ix in 0..10) {
        for (iy in 0..10) {
          add(
            MapClusterFeatureItem(
              id = "grid-${id++}",
              kind = MapFeatureKind.ENTITY,
              label = "Grid $id",
              colorHex = 0xFF1E8E3EL,
              colorCss = "#1E8E3E",
              markerSymbol = "✓",
              normalizedX = ix * 0.20f,
              normalizedY = iy * 0.20f,
            )
          )
        }
      }
    }

    val radius = 0.22f
    val clusters = useCase(gridFeatures, radiusNormalized = radius)
    assertTrue(clusters.size > 4, "Uniform grid should not snowball into a single cluster")
    assertEquals(gridFeatures.size, clusters.sumOf { it.totalCount })
    val maxAllowedSpanX =
      radius *
        ClusterMapFeaturesUseCase.BALLOON_ASPECT_X *
        ClusterMapFeaturesUseCase.MAX_CLUSTER_DIAMETER_FACTOR + 1e-3f
    val maxAllowedSpanY = radius * ClusterMapFeaturesUseCase.MAX_CLUSTER_DIAMETER_FACTOR + 1e-3f
    clusters.forEach { cluster ->
      assertTrue(cluster.spanNormalizedX <= maxAllowedSpanX)
      assertTrue(cluster.spanNormalizedY <= maxAllowedSpanY)
    }
  }

  @Test
  fun computeScaleBarSpec_formatsMetricDistanceForSurvey() {
    val scaleBar = useCase.computeScaleBarSpec("00000033-6b50-51cd-ac94-baea2ea06d65", 0f)
    assertTrue(scaleBar.label.endsWith("m"))

    val regionalScaleBar =
      useCase.computeScaleBarSpec("00000033-6b50-51cd-ac94-baea2ea06d65", -6.0f)
    assertTrue(regionalScaleBar.label.endsWith("km"))
    assertTrue(regionalScaleBar.distanceMeters > scaleBar.distanceMeters)
  }

  @Test
  fun expansionZoomDelta_returnsZoomDeltaSufficientToSplitOrUncluster() {
    val features =
      listOf(
        MapClusterFeatureItem(
          id = "f1",
          kind = MapFeatureKind.ENTITY,
          label = "Plot 1",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.20f,
          normalizedY = 0.20f,
        ),
        MapClusterFeatureItem(
          id = "f2",
          kind = MapFeatureKind.ENTITY,
          label = "Plot 2",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.35f,
          normalizedY = 0.30f,
        ),
      )
    val cluster = useCase(features, radiusNormalized = 0.30f).first()
    assertEquals(0.20f, cluster.minNormalizedX, 1e-4f)
    assertEquals(0.35f, cluster.maxNormalizedX, 1e-4f)
    assertEquals(0.20f, cluster.minNormalizedY, 1e-4f)
    assertEquals(0.30f, cluster.maxNormalizedY, 1e-4f)

    val currentZoomDelta = -3.75f
    val expansionZoom = cluster.expansionZoomDelta(currentZoomDelta)
    assertTrue(expansionZoom >= currentZoomDelta + 1.25f)

    // Coincident points advance past clustering onset to dissolve into individual features
    val coincident =
      listOf(
        MapClusterFeatureItem(
          id = "c1",
          kind = MapFeatureKind.ENTITY,
          label = "Point 1",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.20f,
          normalizedY = 0.20f,
        ),
        MapClusterFeatureItem(
          id = "c2",
          kind = MapFeatureKind.ENTITY,
          label = "Point 2",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.20f,
          normalizedY = 0.20f,
        ),
      )
    val coincidentCluster = useCase(coincident, radiusNormalized = 0.30f).first()
    val unclusterZoom = coincidentCluster.expansionZoomDelta(-3.0f)
    assertTrue(unclusterZoom >= ClusterMapFeaturesUseCase.CLUSTERING_ZOOM_DELTA + 0.15f)
  }

  @Test
  fun invoke_isDeterministicRegardlessOfInputOrder() {
    val features = (1..20).map { i ->
      MapClusterFeatureItem(
        id = "f-$i",
        kind = MapFeatureKind.ENTITY,
        label = "Plot $i",
        colorHex = 0xFF1E8E3EL,
        colorCss = "#1E8E3E",
        markerSymbol = "✓",
        normalizedX = (i % 5) * 0.05f,
        normalizedY = (i / 5) * 0.05f,
      )
    }
    val clustersForward = useCase(features, radiusNormalized = 0.12f)
    val clustersReversed = useCase(features.reversed(), radiusNormalized = 0.12f)
    val clustersShuffled = useCase(features.shuffled(), radiusNormalized = 0.12f)

    assertEquals(clustersForward.size, clustersReversed.size)
    assertEquals(clustersForward.size, clustersShuffled.size)
    for (i in clustersForward.indices) {
      assertEquals(clustersForward[i].id, clustersReversed[i].id)
      assertEquals(clustersForward[i].id, clustersShuffled[i].id)
      assertEquals(clustersForward[i].features.map { it.id }, clustersReversed[i].features.map { it.id })
      assertEquals(clustersForward[i].features.map { it.id }, clustersShuffled[i].features.map { it.id })
    }
  }
}
