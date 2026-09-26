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
package org.groundplatform.v2.devtools.prototypeapp.data.mapper

import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.inferTargetZoomForPlace
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates

/**
 * Data layer mapper (`data/mapper`) translating raw Mapbox Places API JSON payloads into pure
 * domain [SurveyPlaceItem] instances per Section 3 of `docs/technical/client/architecture.md`.
 */
class PlaceJsonMapper {
  fun mapJsonToPlaces(
    json: String,
    defaultRegionSubtitle: String,
    surveyLng: Double,
    surveyLat: Double,
  ): List<SurveyPlaceItem> {
    val trimmed = json.trim()
    if (!trimmed.startsWith("[") || trimmed == "[]") return emptyList()
    val objRegex = Regex("\\{([^{}]*)\\}")
    fun extractStr(body: String, key: String): String {
      val m = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(body) ?: return ""
      return m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }
    fun extractDoubleOrNull(body: String, key: String): Double? {
      val m = Regex("\"$key\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").find(body) ?: return null
      return m.groupValues[1].toDoubleOrNull()
    }
    return objRegex
      .findAll(trimmed)
      .mapNotNull { match ->
        val body = match.groupValues[1]
        val id = extractStr(body, "id").ifEmpty { return@mapNotNull null }
        val name = extractStr(body, "name").ifEmpty { return@mapNotNull null }
        val categoryLabel = extractStr(body, "categoryLabel").ifEmpty { "Place" }
        val regionSubtitle =
          extractStr(body, "regionSubtitle")
            .ifEmpty { extractStr(body, "subtitle") }
            .ifEmpty { defaultRegionSubtitle }
        val coordinatesLabel = extractStr(body, "coordinatesLabel")
        val parsedCoords = parsePlaceCoordinates(coordinatesLabel)
        val lng =
          extractDoubleOrNull(body, "lng")
            ?: extractDoubleOrNull(body, "longitude")
            ?: parsedCoords?.second
            ?: surveyLng
        val lat =
          extractDoubleOrNull(body, "lat")
            ?: extractDoubleOrNull(body, "latitude")
            ?: parsedCoords?.first
            ?: surveyLat
        val bboxMinLng = extractDoubleOrNull(body, "bboxMinLng")
        val bboxMinLat = extractDoubleOrNull(body, "bboxMinLat")
        val bboxMaxLng = extractDoubleOrNull(body, "bboxMaxLng")
        val bboxMaxLat = extractDoubleOrNull(body, "bboxMaxLat")
        val targetZoom =
          extractDoubleOrNull(body, "targetZoom")?.toFloat()
            ?: inferTargetZoomForPlace(
              categoryLabel = categoryLabel,
              bboxMinLng = bboxMinLng,
              bboxMinLat = bboxMinLat,
              bboxMaxLng = bboxMaxLng,
              bboxMaxLat = bboxMaxLat,
              fallbackZoomDelta = 0.85f,
            )
        val computedNx = (0.50f + ((lng - surveyLng) / 0.014).toFloat())
        val computedNy = (0.50f + ((surveyLat - lat) / 0.018).toFloat())
        val nx = extractDoubleOrNull(body, "nx")?.toFloat() ?: computedNx
        val ny = extractDoubleOrNull(body, "ny")?.toFloat() ?: computedNy
        SurveyPlaceItem(
          id = id,
          name = name,
          categoryLabel = categoryLabel,
          regionSubtitle = regionSubtitle,
          coordinatesLabel = coordinatesLabel,
          normalizedX = nx,
          normalizedY = ny,
          zoomDelta = (targetZoom - 15.3f).coerceIn(-13.0f, 3.2f),
          longitude = lng,
          latitude = lat,
          bboxMinLng = bboxMinLng,
          bboxMinLat = bboxMinLat,
          bboxMaxLng = bboxMaxLng,
          bboxMaxLat = bboxMaxLat,
          targetZoom = targetZoom,
          mapboxPlaceId = id,
          sourceLabel = "Places API",
        )
      }
      .toList()
  }
}
