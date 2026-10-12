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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import org.groundplatform.v2.app.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.app.domain.model.MapFeatureCluster
import org.groundplatform.v2.app.domain.model.MapScaleBarSpec
import org.groundplatform.v2.app.domain.model.groupClusterFeaturesByMarkerSymbol

/**
 * Domain use case performing deterministic spatial clustering of map features and computing the
 * viewport scale bar specification.
 */
class ClusterMapFeaturesUseCase {
  /**
   * True when the map is zoomed out past the clustering threshold ([CLUSTERING_ZOOM_DELTA], about
   * z12.95), causing visible map features to be grouped into spatial clusters.
   */
  fun isClusteringActive(mapZoomDelta: Float): Boolean = mapZoomDelta <= CLUSTERING_ZOOM_DELTA

  /**
   * Normalized world-space clustering radius scaled exponentially as the user zooms out past
   * [CLUSTERING_ZOOM_DELTA] (`BASE_CLUSTER_RADIUS_NORMALIZED * 2^(CLUSTERING_ZOOM_DELTA -
   * mapZoomDelta)`).
   *
   * Following standard Web Mercator clustering practice (Supercluster / MarkerClusterer), a fixed
   * screen-space cluster radius doubles in geographic / world radius with every zoom level out, so
   * clusters progressively cover a broader and broader area as the user zooms out. Returns `0f`
   * when clustering is inactive.
   */
  fun clusterRadiusNormalized(mapZoomDelta: Float): Float =
    if (!isClusteringActive(mapZoomDelta)) {
      0f
    } else {
      val zoomOutLevels = (CLUSTERING_ZOOM_DELTA - mapZoomDelta).toDouble()
      (BASE_CLUSTER_RADIUS_NORMALIZED * 2.0.pow(zoomOutLevels).toFloat()).coerceIn(
        BASE_CLUSTER_RADIUS_NORMALIZED,
        MAX_CLUSTER_RADIUS_NORMALIZED,
      )
    }

  operator fun invoke(
    features: List<MapClusterFeatureItem>,
    radiusNormalized: Float,
  ): List<MapFeatureCluster> {
    if (features.isEmpty() || radiusNormalized <= 0f) return emptyList()

    class WorkingCluster(
      val members: MutableList<MapClusterFeatureItem>,
      var sumX: Float,
      var sumY: Float,
      var minX: Float,
      var maxX: Float,
      var minY: Float,
      var maxY: Float,
    ) {
      val cx: Float
        get() = sumX / members.size

      val cy: Float
        get() = sumY / members.size

      fun absorb(other: WorkingCluster) {
        members.addAll(other.members)
        sumX += other.sumX
        sumY += other.sumY
        if (other.minX < minX) minX = other.minX
        if (other.maxX > maxX) maxX = other.maxX
        if (other.minY < minY) minY = other.minY
        if (other.maxY > maxY) maxY = other.maxY
      }
    }

    // 1. For large datasets (>64 features), pre-aggregate points into a fine sub-radius spatial
    // hash grid (cell <= 0.25 * radiusNormalized) so points within the same micro-cell are
    // combined in O(N) time without introducing coarse grid-boundary artifacts.
    val fineCell = (radiusNormalized * 0.25f).coerceAtLeast(0.02f)
    var working: MutableList<WorkingCluster> =
      if (features.size > 64) {
        val buckets = LinkedHashMap<Long, WorkingCluster>()
        for (feat in features) {
          val gx = floor(feat.normalizedX / fineCell).toInt()
          val gy = floor(feat.normalizedY / fineCell).toInt()
          val key = (gx.toLong() shl 32) or (gy.toLong() and 0xFFFFFFFFL)
          val existing = buckets[key]
          if (existing != null) {
            existing.members.add(feat)
            existing.sumX += feat.normalizedX
            existing.sumY += feat.normalizedY
            if (feat.normalizedX < existing.minX) existing.minX = feat.normalizedX
            if (feat.normalizedX > existing.maxX) existing.maxX = feat.normalizedX
            if (feat.normalizedY < existing.minY) existing.minY = feat.normalizedY
            if (feat.normalizedY > existing.maxY) existing.maxY = feat.normalizedY
          } else {
            val list = ArrayList<MapClusterFeatureItem>()
            list.add(feat)
            buckets[key] =
              WorkingCluster(
                members = list,
                sumX = feat.normalizedX,
                sumY = feat.normalizedY,
                minX = feat.normalizedX,
                maxX = feat.normalizedX,
                minY = feat.normalizedY,
                maxY = feat.normalizedY,
              )
          }
        }
        buckets.values.toMutableList()
      } else {
        features
          .map { feat ->
            WorkingCluster(
              members = mutableListOf(feat),
              sumX = feat.normalizedX,
              sumY = feat.normalizedY,
              minX = feat.normalizedX,
              maxX = feat.normalizedX,
              minY = feat.normalizedY,
              maxY = feat.normalizedY,
            )
          }
          .toMutableList()
      }

    // 2. Build bottom-up hierarchical pyramid radii (stepping by 0.75 zoom levels = 2^0.75x) ending
    // at radiusNormalized, matching Supercluster's fine-to-coarse zoom hierarchy.
    val pyramidRadii = ArrayList<Float>()
    var stepR = radiusNormalized
    val minPyramidR = maxOf(BASE_CLUSTER_RADIUS_NORMALIZED, fineCell * 1.5f)
    while (stepR > minPyramidR * 1.05f) {
      pyramidRadii.add(stepR)
      stepR *= ZOOM_STEP_RADIUS_DECAY
    }
    pyramidRadii.add(stepR.coerceAtMost(radiusNormalized))
    pyramidRadii.reverse()

    for (levelRadius in pyramidRadii) {
      if (working.size <= 1) break
      val rx = levelRadius * BALLOON_ASPECT_X
      val ry = levelRadius
      val maxSpanX = rx * MAX_CLUSTER_DIAMETER_FACTOR
      val maxSpanY = ry * MAX_CLUSTER_DIAMETER_FACTOR

      // Pass A: Density-seeded sphere-of-influence clustering (prevents centroid-drift chaining).
      working.sortWith(
        compareByDescending<WorkingCluster> { it.members.size }
          .thenBy { (it.cy * 100f).toInt() }
          .thenBy { (it.cx * 100f).toInt() }
          .thenBy { it.members.first().id }
      )
      val absorbed = BooleanArray(working.size)
      val seeded = ArrayList<WorkingCluster>(working.size)
      for (i in 0 until working.size) {
        if (absorbed[i]) continue
        val seed = working[i]
        val seedX = seed.cx
        val seedY = seed.cy
        for (j in i + 1 until working.size) {
          if (absorbed[j]) continue
          val candidate = working[j]
          val dx = abs(candidate.cx - seedX)
          val dy = abs(candidate.cy - seedY)
          if (dx <= rx && dy <= ry && hypot(dx / rx, dy / ry) <= 1.0f) {
            val combinedSpanX = maxOf(seed.maxX, candidate.maxX) - minOf(seed.minX, candidate.minX)
            val combinedSpanY = maxOf(seed.maxY, candidate.maxY) - minOf(seed.minY, candidate.minY)
            if (combinedSpanX <= maxSpanX && combinedSpanY <= maxSpanY) {
              seed.absorb(candidate)
              absorbed[j] = true
            }
          }
        }
        seeded.add(seed)
      }
      working = seeded

      // Pass B: Bounded-diameter closest-pair consolidation (resolves close boundary pairs while
      // enforcing maxSpanX / maxSpanY so clusters never snowball across the map).
      while (working.size > 1) {
        var bestI = -1
        var bestJ = -1
        var bestMetric = Float.MAX_VALUE

        for (i in 0 until working.size) {
          val ci = working[i]
          for (j in i + 1 until working.size) {
            val cj = working[j]
            val dx = abs(ci.cx - cj.cx)
            val dy = abs(ci.cy - cj.cy)
            if (dx > rx || dy > ry) continue
            val metric = hypot(dx / rx, dy / ry)
            if (metric <= 1.0f && metric < bestMetric) {
              val combinedSpanX = maxOf(ci.maxX, cj.maxX) - minOf(ci.minX, cj.minX)
              val combinedSpanY = maxOf(ci.maxY, cj.maxY) - minOf(ci.minY, cj.minY)
              if (combinedSpanX <= maxSpanX && combinedSpanY <= maxSpanY) {
                bestMetric = metric
                bestI = i
                bestJ = j
              }
            }
          }
        }

        if (bestI < 0 || bestJ < 0) break

        val target = working[bestI]
        val merged = working.removeAt(bestJ)
        target.absorb(merged)
      }
    }

    return working
      .sortedWith(compareBy<WorkingCluster> { (it.cy * 10f).toInt() }.thenBy { it.cx })
      .mapIndexed { index, cluster ->
        MapFeatureCluster(
          id = "cluster-${index + 1}",
          normalizedX = cluster.cx,
          normalizedY = cluster.cy,
          features = cluster.members,
          symbolGroups = groupClusterFeaturesByMarkerSymbol(cluster.members),
          minNormalizedX = cluster.minX,
          maxNormalizedX = cluster.maxX,
          minNormalizedY = cluster.minY,
          maxNormalizedY = cluster.maxY,
        )
      }
  }

  fun computeScaleBarSpec(activeSurveyId: String, mapZoomDelta: Float): MapScaleBarSpec {
    val (baseZoom, latitudeDeg) =
      when (activeSurveyId) {
        "survey-amazon-bio" -> 14.8f to -3.1190
        "survey-tanzania-water" -> 15.1f to -6.1659
        "survey-california-fire" -> 14.9f to 38.5449
        else -> 15.3f to -0.4198
      }
    val effectiveZoom = (baseZoom + mapZoomDelta).coerceIn(2.0f, 19.0f).toDouble()
    val metersPerDp =
      (40075016.686 * cos(latitudeDeg * PI / 180.0)) / (512.0 * 2.0.pow(effectiveZoom))
    val candidateMeters =
      listOf(
        5,
        10,
        20,
        50,
        100,
        200,
        500,
        1000,
        2000,
        5000,
        10000,
        20000,
        50000,
        100000,
        200000,
        500000,
      )
    val targetWidthDp = 72.0
    val chosenMeters =
      candidateMeters.minByOrNull { dist ->
        val widthDp = dist / metersPerDp
        val outOfRangePenalty =
          when {
            widthDp < 44.0 -> (44.0 - widthDp) * 4.0
            widthDp > 112.0 -> (widthDp - 112.0) * 4.0
            else -> 0.0
          }
        abs(widthDp - targetWidthDp) + outOfRangePenalty
      } ?: 100
    val barWidthDp = (chosenMeters / metersPerDp).toFloat().coerceIn(44f, 114f)
    val label =
      if (chosenMeters >= 1000) {
        "${chosenMeters / 1000} km"
      } else {
        "$chosenMeters m"
      }
    return MapScaleBarSpec(label = label, distanceMeters = chosenMeters, barWidthDp = barWidthDp)
  }

  companion object {
    /**
     * `mapZoomDelta` (relative to the survey's default `15.3z`) at or below which map features are
     * clustered: about `12.95z`.
     */
    const val CLUSTERING_ZOOM_DELTA = -2.35f

    /**
     * Normalized world-space cluster radius at the onset of clustering ([CLUSTERING_ZOOM_DELTA]).
     * Doubles with every full zoom level zoomed out (`2^(CLUSTERING_ZOOM_DELTA - mapZoomDelta)`).
     */
    const val BASE_CLUSTER_RADIUS_NORMALIZED = 0.215f

    /** Upper bound on normalized cluster radius at deep regional/continental zoom-out. */
    const val MAX_CLUSTER_RADIUS_NORMALIZED = 512.0f

    /** Horizontal-to-vertical aspect ratio matching rendered cluster balloons on screen. */
    const val BALLOON_ASPECT_X = 1.25f

    /** Maximum member bounding span of a cluster relative to its directional radius. */
    const val MAX_CLUSTER_DIAMETER_FACTOR = 2.15f

    /** Radius multiplier per `0.75` zoom step (`2^(-0.75)`). */
    private const val ZOOM_STEP_RADIUS_DECAY = 0.5946035f
  }
}
