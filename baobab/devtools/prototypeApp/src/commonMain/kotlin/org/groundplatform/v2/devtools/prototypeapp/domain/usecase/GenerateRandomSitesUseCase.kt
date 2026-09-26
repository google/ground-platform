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

import kotlin.math.roundToInt
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository

/** Result of generating random map features via [GenerateRandomSitesUseCase]. */
data class GenerateRandomSitesResult(
  val addedCount: Int,
  val totalEntityCount: Int,
  val updatedEntities: List<GeospatialEntityItem>,
  val noticeMessage: String,
)

/**
 * Domain use case that generates deterministic Halton-sequence / LCG polygon [GeospatialEntityItem]
 * features across the active survey region and updates [SurveyRepository].
 */
class GenerateRandomSitesUseCase(private val surveyRepository: SurveyRepository) {
  operator fun invoke(count: Int = 5_000): GenerateRandomSitesResult? {
    if (count <= 0) return null
    val existingEntities = surveyRepository.getEntities()
    val existingSize = existingEntities.size
    val batchSeed = existingSize * 1103515245L + 12345L
    val newFeatures = ArrayList<GeospatialEntityItem>(count)

    for (i in 0 until count) {
      val idx = existingSize + i + 1
      val h1 = ((batchSeed + i * 2654435761L) ushr 16) and 0xFFFFL
      val h2 = ((batchSeed + i * 1597334677L) ushr 16) and 0xFFFFL
      val u = (h1.toFloat() / 65535f)
      val v = (h2.toFloat() / 65535f)

      val isInner = (i % 10) < 3
      val nx = if (isInner) -0.15f + u * 1.30f else -2.20f + u * 5.40f
      val ny = if (isInner) -0.15f + v * 1.30f else -2.20f + v * 5.40f

      val statusBucket = i % 4
      val (status, symbol, hexStr, hexLong) =
        when (statusBucket) {
          0 -> listOf("Completed", "✓", "#1E8E3E", 0xFF1E8E3EL)
          1 -> listOf("In progress", "◐", "#F9AB00", 0xFFF9AB00L)
          else -> listOf("Pending", "○", "#E65100", 0xFFE65100L)
        }
      val statusLabel = status as String
      val markerSymbol = symbol as String
      val colorCss = hexStr as String
      val colorHex = hexLong as Long

      val areaHa = ((0.4 + (u * 2.4)) * 100.0).roundToInt() / 100.0
      val perimeterM = (220 + (v * 420)).roundToInt()
      val lat = ((0.4198 - (ny - 0.5f) * 0.018) * 10000.0).roundToInt() / 10000.0
      val lng = ((36.9512 + (nx - 0.5f) * 0.014) * 10000.0).roundToInt() / 10000.0

      newFeatures.add(
        GeospatialEntityItem(
          id = "entity-rnd-$idx",
          label = "Plot RND-$idx • Parcel #$idx",
          datasetId = "coffee_parcels",
          datasetName = "Smallholder Coffee Parcels",
          layerId = "layer-coffee-parcels",
          geoId = "S2-rnd-${idx.toString(16)}",
          geometryTypeLabel = "Polygon",
          areaHectares = areaHa,
          perimeterMeters = perimeterM,
          coordinatesLabel = "${lat}°S, ${lng}°E",
          normalizedX = nx,
          normalizedY = ny,
          colorHex = colorHex,
          properties =
            mapOf(
              "status" to statusLabel,
              "marker-symbol" to markerSymbol,
              "marker-color" to colorCss,
              "stroke" to colorCss,
              "fill" to colorCss,
              "Cooperative" to "Othaya Farmers Co-op",
              "Primary Cultivar" to if (i % 2 == 0) "SL28" else "Ruiru 11",
            ),
          submissions = emptyList(),
          singularTypeLabel = "Coffee Parcel",
          syncStatus = SyncStatus.SYNCED,
        )
      )
    }

    val updatedEntities = existingEntities + newFeatures
    surveyRepository.setEntities(updatedEntities)
    val activeSurveyId = surveyRepository.getActiveSurveyId()
    surveyRepository.setSurveys(
      surveyRepository.getSurveys().map { s ->
        if (s.id == activeSurveyId) s.copy(entityCount = updatedEntities.size) else s
      }
    )
    return GenerateRandomSitesResult(
      addedCount = count,
      totalEntityCount = updatedEntities.size,
      updatedEntities = updatedEntities,
      noticeMessage =
        "Added $count random polygon features (${updatedEntities.size} total map features)",
    )
  }
}
