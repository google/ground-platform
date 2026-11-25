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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.pow
import org.openforis.collectone.v2.app.domain.model.MapClusterFeatureItem
import org.openforis.collectone.v2.app.domain.model.MapFeatureCluster
import org.openforis.collectone.v2.app.domain.model.MapScaleBarSpec
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.groupClusterFeaturesByMarkerSymbol
import org.openforis.collectone.v2.app.domain.model.scaleBarLength

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
   * [CLUSTERING_ZOOM_DELTA] (`BASE_CLUSTER_RADIUS_NORMALIZED *
   * densityRadiusMultiplier(featureCount)
   * * 2^(CLUSTERING_ZOOM_DELTA - mapZoomDelta)`).
   *
   * Following standard Web Mercator clustering practice (Supercluster / MarkerClusterer), a fixed
   * screen-space cluster radius doubles in geographic / world radius with every zoom level out, so
   * clusters progressively cover a broader and broader area as the user zooms out. For dense
   * surveys with many features (`featureCount > DENSE_SURVEY_FEATURE_THRESHOLD`), the base screen
   * radius scales up logarithmically so cluster badges stay well-spaced on screen. Returns `0f`
   * when clustering is inactive.
   */
  fun clusterRadiusNormalized(mapZoomDelta: Float, featureCount: Int = 0): Float =
    if (!isClusteringActive(mapZoomDelta)) {
      0f
    } else {
      val baseRadius = BASE_CLUSTER_RADIUS_NORMALIZED * densityRadiusMultiplier(featureCount)
      val zoomOutLevels = (CLUSTERING_ZOOM_DELTA - mapZoomDelta).toDouble()
      (baseRadius * 2.0.pow(zoomOutLevels).toFloat()).coerceIn(
        baseRadius,
        MAX_CLUSTER_RADIUS_NORMALIZED,
      )
    }

  /**
   * Multiplier `[1.0, MAX_DENSE_RADIUS_MULTIPLIER]` applied to the base cluster radius when a
   * survey has more than [DENSE_SURVEY_FEATURE_THRESHOLD] visible features, reducing visual marker
   * crowding on large sample-plot grids.
   */
  fun densityRadiusMultiplier(featureCount: Int): Float =
    if (featureCount <= DENSE_SURVEY_FEATURE_THRESHOLD) {
      1.0f
    } else {
      val octaves = log2(featureCount.toDouble() / DENSE_SURVEY_FEATURE_THRESHOLD).toFloat()
      (1.0f + DENSE_RADIUS_GROWTH_PER_OCTAVE * octaves).coerceIn(
        1.0f,
        MAX_DENSE_RADIUS_MULTIPLIER,
      )
    }

  private class WorkingCluster(
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

  operator fun invoke(
    features: List<MapClusterFeatureItem>,
    radiusNormalized: Float,
  ): List<MapFeatureCluster> {
    if (features.isEmpty() || radiusNormalized <= 0f) return emptyList()

    // 1. For large datasets (>64 features), pre-aggregate points into a fine sub-radius spatial
    // hash grid (cell <= 0.25 * radiusNormalized) so points within the same micro-cell are
    // combined in O(N) time without introducing coarse grid-boundary artifacts.
    val sortedFeatures = features.sortedBy { it.id }
    val fineCell = (radiusNormalized * 0.25f).coerceAtLeast(0.02f)
    var working: MutableList<WorkingCluster> =
      if (sortedFeatures.size > 64) {
        val buckets = LinkedHashMap<Long, WorkingCluster>()
        for (feat in sortedFeatures) {
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
        buckets.values
          .sortedWith(
            compareByDescending<WorkingCluster> { it.members.size }
              .thenBy { it.members.minOf { m -> m.id } }
          )
          .toMutableList()
      } else {
        sortedFeatures
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

    for ((levelIndex, levelRadius) in pyramidRadii.withIndex()) {
      if (working.size <= 1) break
      val isFinalLevel = levelIndex == pyramidRadii.lastIndex
      val diameterFactor =
        if (isFinalLevel) MAX_CLUSTER_DIAMETER_FACTOR else INTERMEDIATE_CLUSTER_DIAMETER_FACTOR
      val rx = levelRadius * BALLOON_ASPECT_X
      val ry = levelRadius
      val maxSpanX = rx * diameterFactor
      val maxSpanY = ry * diameterFactor

      // Pass A: Density-seeded sphere-of-influence clustering (prevents centroid-drift chaining).
      working.sortWith(
        compareByDescending<WorkingCluster> { it.members.size }
          .thenBy { it.members.minOf { m -> m.id } }
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
          if (dx <= rx && dy <= ry) {
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
            val gapX = maxOf(0f, maxOf(ci.minX, cj.minX) - minOf(ci.maxX, cj.maxX))
            val gapY = maxOf(0f, maxOf(ci.minY, cj.minY) - minOf(ci.maxY, cj.maxY))
            val reachFactor =
              if (
                gapX <= rx * ADJACENT_BLOCK_GAP_FACTOR && gapY <= ry * ADJACENT_BLOCK_GAP_FACTOR
              ) {
                ADJACENT_BLOCK_CENTROID_FACTOR
              } else {
                1.0f
              }
            val dx = abs(ci.cx - cj.cx)
            val dy = abs(ci.cy - cj.cy)
            if (dx > rx * reachFactor || dy > ry * reachFactor) continue
            val metric = hypot(dx / rx, dy / ry)
            if (metric < bestMetric) {
              val combinedMinX = minOf(ci.minX, cj.minX)
              val combinedMaxX = maxOf(ci.maxX, cj.maxX)
              val combinedMinY = minOf(ci.minY, cj.minY)
              val combinedMaxY = maxOf(ci.maxY, cj.maxY)
              val combinedSpanX = combinedMaxX - combinedMinX
              val combinedSpanY = combinedMaxY - combinedMinY
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

    // 3. Pass C: On dense multi-cluster surveys, absorb small interstitial remnants (singletons and
    // pairs) that were left between grid clusters into their nearest neighbor within
    // SINGLETON_CAPTURE_FACTOR, as long as the combined cluster remains within maxSpanX / maxSpanY.
    if (features.size > DENSE_SURVEY_FEATURE_THRESHOLD && working.size > 4) {
      val captureRx = radiusNormalized * BALLOON_ASPECT_X * SINGLETON_CAPTURE_FACTOR
      val captureRy = radiusNormalized * SINGLETON_CAPTURE_FACTOR
      val maxSpanX = radiusNormalized * BALLOON_ASPECT_X * MAX_CLUSTER_DIAMETER_FACTOR
      val maxSpanY = radiusNormalized * MAX_CLUSTER_DIAMETER_FACTOR
      var mergedRemnant = true
      while (mergedRemnant && working.size > 1) {
        mergedRemnant = false
        for (i in working.indices.reversed()) {
          val remnant = working[i]
          if (remnant.members.size <= SMALL_REMNANT_MAX_SIZE) {
            val bestIdx =
              findBestRemnantMergeTarget(
                working = working,
                remnantIndex = i,
                captureRx = captureRx,
                captureRy = captureRy,
                maxSpanX = maxSpanX,
                maxSpanY = maxSpanY,
              )
            if (bestIdx >= 0) {
              val target = working[bestIdx]
              working.removeAt(i)
              target.absorb(remnant)
              mergedRemnant = true
              break
            }
          }
        }
      }
    }

    return working
      .sortedWith(
        compareByDescending<WorkingCluster> { it.members.size }
          .thenBy { it.members.minOf { m -> m.id } }
      )
      .mapIndexed { index, cluster ->
        MapFeatureCluster(
          id = "cluster-${index + 1}",
          normalizedX = cluster.cx,
          normalizedY = cluster.cy,
          features = cluster.members.sortedBy { it.id },
          symbolGroups = groupClusterFeaturesByMarkerSymbol(cluster.members),
          minNormalizedX = cluster.minX,
          maxNormalizedX = cluster.maxX,
          minNormalizedY = cluster.minY,
          maxNormalizedY = cluster.maxY,
        )
      }
  }

  private fun findBestRemnantMergeTarget(
    working: List<WorkingCluster>,
    remnantIndex: Int,
    captureRx: Float,
    captureRy: Float,
    maxSpanX: Float,
    maxSpanY: Float,
  ): Int {
    val remnant = working[remnantIndex]
    var bestIdx = -1
    var bestMetric = Float.MAX_VALUE
    for (j in working.indices) {
      if (j != remnantIndex) {
        val candidate = working[j]
        val dx = abs(remnant.cx - candidate.cx)
        val dy = abs(remnant.cy - candidate.cy)
        if (dx <= captureRx && dy <= captureRy) {
          val metric = hypot(dx / captureRx, dy / captureRy)
          val combinedSpanX =
            maxOf(remnant.maxX, candidate.maxX) - minOf(remnant.minX, candidate.minX)
          val combinedSpanY =
            maxOf(remnant.maxY, candidate.maxY) - minOf(remnant.minY, candidate.minY)
          if (metric < bestMetric && combinedSpanX <= maxSpanX && combinedSpanY <= maxSpanY) {
            bestMetric = metric
            bestIdx = j
          }
        }
      }
    }
    return bestIdx
  }

  /**
   * Helper computing the expansion zoom delta at which [cluster] splits or disaggregates into
   * individual features.
   */
  fun expansionZoomDelta(
    cluster: MapFeatureCluster,
    currentZoomDelta: Float,
    featureCount: Int = cluster.totalCount,
  ): Float =
    cluster.expansionZoomDelta(
      currentZoomDelta = currentZoomDelta,
      baseClusterRadius = BASE_CLUSTER_RADIUS_NORMALIZED * densityRadiusMultiplier(featureCount),
    )

  fun computeScaleBarSpec(activeSurveyId: String, mapZoomDelta: Float): MapScaleBarSpec {
    val anchor = SurveyMapAnchor.forSurvey(activeSurveyId)
    return computeScaleBarSpec(
      baseZoom = anchor.zoom,
      latitudeDeg = anchor.center.latitude,
      mapZoomDelta = mapZoomDelta,
    )
  }

  fun computeScaleBarSpec(
    baseZoom: Double,
    latitudeDeg: Double,
    mapZoomDelta: Float,
  ): MapScaleBarSpec {
    val effectiveZoom = (baseZoom + mapZoomDelta).coerceIn(2.0, 19.0)
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
    return MapScaleBarSpec(
      length = scaleBarLength(chosenMeters.toDouble()),
      distanceMeters = chosenMeters,
      barWidthDp = barWidthDp,
    )
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
    const val MAX_CLUSTER_RADIUS_NORMALIZED = 1024.0f

    /** Horizontal-to-vertical aspect ratio matching rendered cluster balloons on screen. */
    const val BALLOON_ASPECT_X = 1.25f

    /** Maximum member bounding span of a cluster relative to its directional radius. */
    const val MAX_CLUSTER_DIAMETER_FACTOR = 2.15f

    /**
     * Diameter cap at intermediate pyramid levels (`1.85f`) so adjacent sub-clusters from level
     * `k - 1` have combined span `<= MAX_CLUSTER_DIAMETER_FACTOR` at level `k` without jamming.
     */
    private const val INTERMEDIATE_CLUSTER_DIAMETER_FACTOR = 1.85f

    /** Feature count above which the screen-space cluster radius scales up for dense surveys. */
    const val DENSE_SURVEY_FEATURE_THRESHOLD = 32

    /** Additional base cluster radius multiplier per doubling of feature count above 32. */
    private const val DENSE_RADIUS_GROWTH_PER_OCTAVE = 0.34f

    /** Maximum density multiplier applied to [BASE_CLUSTER_RADIUS_NORMALIZED] on large surveys. */
    const val MAX_DENSE_RADIUS_MULTIPLIER = 2.35f

    /** Search radius multiplier for absorbing stranded 1–3 feature remnants into neighbors. */
    private const val SINGLETON_CAPTURE_FACTOR = 1.65f

    /** Maximum boundary gap factor (`0.65 * r`) identifying adjacent sub-grid blocks in Pass B. */
    private const val ADJACENT_BLOCK_GAP_FACTOR = 0.65f

    /** Centroid reach factor (`1.40 * r`) for merging adjacent sub-grid blocks within maxSpan. */
    private const val ADJACENT_BLOCK_CENTROID_FACTOR = 1.40f

    /** Maximum size of a remnant cluster eligible for Pass C neighbor absorption. */
    private const val SMALL_REMNANT_MAX_SIZE = 3

    /**
     * Radius decay per pyramid step (`< 0.5f` so each step more than doubles the radius, allowing
     * pairs of sub-clusters from the previous step to merge without exceeding max span).
     */
    private const val ZOOM_STEP_RADIUS_DECAY = 0.45f
  }
}
