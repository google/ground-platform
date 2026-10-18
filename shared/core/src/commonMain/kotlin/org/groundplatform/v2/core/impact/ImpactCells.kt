/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.impact

import kotlin.math.min
import org.groundplatform.v2.core.geo.s2.s2CellToken

/*
 * Grid cells and thresholding (docs/technical/backend/impact-aggregation.md, "Grid Cells and
 * Thresholding").
 */

/** CELL rows plus what was suppressed, for [ImpactDiagnostics]. */
internal class CellAggregation(
  val rows: List<ImpactAggregateRow>,
  val suppressedCellCount: Int,
  val suppressedCellFeatureCount: Long,
)

private class CellFeature(val ref: FeatureRef, val fineToken: String, val coarseToken: String)

/**
 * CELL rows (FEATURES only) for the deduplicated, platform-eligible [winners] of the global scope.
 *
 * Each winner with a [representativePoint] falls in one S2 cell at [ImpactConfig.fineCellLevel]. A
 * fine cell is published when it has at least [ImpactConfig.cellMinFeatures] features from at least
 * [ImpactConfig.cellMinOrganizations] distinct organizations (by the winner's organization;
 * personal surveys share one key). Features of unpublished fine cells merge into their ancestor at
 * [ImpactConfig.coarseCellLevel] (clamped to the fine level), which is published under the same
 * thresholds or else emitted as one suppressed row with zeroed counts. So the published feature
 * counts plus [CellAggregation.suppressedCellFeatureCount] always equal the features with a point.
 */
internal fun cellAggregation(winners: List<FeatureRef>, config: ImpactConfig): CellAggregation {
  val fineLevel = config.fineCellLevel
  val coarseLevel = min(config.coarseCellLevel, fineLevel)
  val fineCells = HashMap<String, MutableList<CellFeature>>()
  for (ref in winners) {
    val point = representativePoint(ref.feature.geometry) ?: continue
    val feature =
      CellFeature(
        ref,
        fineToken = s2CellToken(point.lat, point.lng, fineLevel),
        coarseToken = s2CellToken(point.lat, point.lng, coarseLevel),
      )
    fineCells.getOrPut(feature.fineToken) { mutableListOf() } += feature
  }

  fun publishable(features: List<CellFeature>) =
    features.size >= config.cellMinFeatures &&
      features.mapTo(HashSet()) { it.ref.survey.organizationKey }.size >=
        config.cellMinOrganizations

  fun row(token: String, features: List<CellFeature>?) =
    ImpactAggregateRow(
      scopeType = ImpactScopeType.CELL,
      scopeId = token,
      metric = ImpactMetric.FEATURES,
      featureCount = features?.size?.toLong() ?: 0L,
      areaHa = features?.sumOf { it.ref.areaHa } ?: 0.0,
      suppressed = features == null,
    )

  val rows = mutableListOf<ImpactAggregateRow>()
  val coarseCells = HashMap<String, MutableList<CellFeature>>()
  for (token in fineCells.keys.sorted()) {
    val features = fineCells.getValue(token)
    if (publishable(features)) rows += row(token, features)
    else features.groupByTo(coarseCells) { it.coarseToken }
  }
  var suppressedCells = 0
  var suppressedFeatures = 0L
  for (token in coarseCells.keys.sorted()) {
    val features = coarseCells.getValue(token)
    if (publishable(features)) {
      rows += row(token, features)
    } else {
      rows += row(token, null)
      suppressedCells++
      suppressedFeatures += features.size
    }
  }
  return CellAggregation(rows.sortedBy { it.scopeId }, suppressedCells, suppressedFeatures)
}
