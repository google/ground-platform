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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureCluster
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapScaleBarSpec
import org.groundplatform.v2.devtools.prototypeapp.domain.model.groupClusterFeaturesByMarkerSymbol

/**
 * Domain use case performing deterministic spatial clustering of map features and computing the
 * viewport scale bar specification.
 */
class ClusterMapFeaturesUseCase {
  operator fun invoke(
    features: List<MapClusterFeatureItem>,
    radiusNormalized: Float,
  ): List<MapFeatureCluster> {
    if (features.isEmpty() || radiusNormalized <= 0f) return emptyList()

    val sepX = (radiusNormalized * 2.85f).coerceAtLeast(radiusNormalized)
    val sepY = (radiusNormalized * 1.30f).coerceAtLeast(radiusNormalized)

    class MutableWorkingCluster(
      val members: MutableList<MapClusterFeatureItem>,
      var sumX: Float,
      var sumY: Float,
    ) {
      val cx: Float
        get() = sumX / members.size

      val cy: Float
        get() = sumY / members.size
    }

    val working: MutableList<MutableWorkingCluster> =
      if (features.size > 64) {
        val cellX = (sepX * 0.75f).coerceAtLeast(0.15f)
        val cellY = (sepY * 0.75f).coerceAtLeast(0.15f)
        val buckets = LinkedHashMap<Long, MutableWorkingCluster>()
        for (feat in features) {
          val gx = floor(feat.normalizedX / cellX).toInt()
          val gy = floor(feat.normalizedY / cellY).toInt()
          val key = (gx.toLong() shl 32) or (gy.toLong() and 0xFFFFFFFFL)
          val existing = buckets[key]
          if (existing != null) {
            existing.members.add(feat)
            existing.sumX += feat.normalizedX
            existing.sumY += feat.normalizedY
          } else {
            val list = ArrayList<MapClusterFeatureItem>()
            list.add(feat)
            buckets[key] = MutableWorkingCluster(list, feat.normalizedX, feat.normalizedY)
          }
        }
        buckets.values.toMutableList()
      } else {
        features
          .map { feat ->
            MutableWorkingCluster(mutableListOf(feat), feat.normalizedX, feat.normalizedY)
          }
          .toMutableList()
      }

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
          val radialRatio = hypot(dx, dy) / radiusNormalized
          val balloonOverlapRatio = maxOf(dx / sepX, dy / sepY)
          val metric = minOf(radialRatio, balloonOverlapRatio)
          if (metric <= 1.0f && metric < bestMetric) {
            bestMetric = metric
            bestI = i
            bestJ = j
          }
        }
      }

      if (bestI < 0 || bestJ < 0) break

      val target = working[bestI]
      val absorbed = working.removeAt(bestJ)
      target.members.addAll(absorbed.members)
      target.sumX += absorbed.sumX
      target.sumY += absorbed.sumY
    }

    return working
      .sortedWith(compareBy<MutableWorkingCluster> { (it.cy * 10f).toInt() }.thenBy { it.cx })
      .mapIndexed { index, cluster ->
        MapFeatureCluster(
          id = "cluster-${index + 1}",
          normalizedX = cluster.cx,
          normalizedY = cluster.cy,
          features = cluster.members,
          symbolGroups = groupClusterFeaturesByMarkerSymbol(cluster.members),
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
    val effectiveZoom = (baseZoom + mapZoomDelta).coerceIn(10.0f, 19.0f).toDouble()
    val metersPerDp =
      (40075016.686 * cos(latitudeDeg * PI / 180.0)) / (512.0 * 2.0.pow(effectiveZoom))
    val candidateMeters =
      listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000)
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
}
