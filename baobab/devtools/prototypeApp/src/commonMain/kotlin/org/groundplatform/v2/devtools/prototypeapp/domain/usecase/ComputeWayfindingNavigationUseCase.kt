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
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineNavigationState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineVector
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem

/**
 * Domain use case encapsulating geodesic straight-line wayfinding calculations, submission geometry
 * target resolution, and simulated GPS stepping toward an active navigation target.
 */
class ComputeWayfindingNavigationUseCase {
  operator fun invoke(
    fromNormalizedX: Float,
    fromNormalizedY: Float,
    targetNormalizedX: Float,
    targetNormalizedY: Float,
    unitSystem: MeasurementUnitSystem,
  ): StraightLineVector {
    val eastMeters = (targetNormalizedX - fromNormalizedX) * 1558.48
    val northMeters = (fromNormalizedY - targetNormalizedY) * 2000.38
    val distMeters = hypot(eastMeters, northMeters).roundToInt().coerceAtLeast(0)
    val rawBearingDeg =
      if (distMeters == 0) {
        0.0
      } else {
        atan2(eastMeters, northMeters) * (180.0 / PI)
      }
    val bearingDeg = (((rawBearingDeg.roundToInt()) % 360) + 360) % 360
    val cardinalDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val cardinalIdx = (((bearingDeg + 22.5) / 45.0).toInt()) % 8
    val cardinal = cardinalDirections[cardinalIdx]
    val formattedDist =
      if (unitSystem == MeasurementUnitSystem.METRIC) {
        if (distMeters >= 1000) {
          val km = ((distMeters / 100.0).roundToInt()) / 10.0
          "$km km"
        } else {
          "$distMeters m"
        }
      } else {
        val feet = (distMeters * 3.28084).roundToInt()
        if (feet >= 5280) {
          val miles = ((feet / 528.0).roundToInt()) / 10.0
          "$miles mi"
        } else {
          "$feet ft"
        }
      }
    val walkMinutes =
      if (distMeters <= 8) {
        0
      } else {
        (distMeters / 65.0).roundToInt().coerceAtLeast(1)
      }
    return StraightLineVector(
      fromNormalizedX = fromNormalizedX,
      fromNormalizedY = fromNormalizedY,
      toNormalizedX = targetNormalizedX,
      toNormalizedY = targetNormalizedY,
      distanceMeters = distMeters,
      formattedDistance = formattedDist,
      bearingDegrees = bearingDeg,
      cardinalDirection = cardinal,
      estimatedWalkMinutes = walkMinutes,
      hasArrived = distMeters <= 8,
    )
  }

  fun resolveSubmissionTargetGeometry(
    submissionId: String,
    allSubmissions: List<SubmissionPreviewItem>,
    submissionGeometries: List<SubmissionGeometryPolygon>,
    entities: List<GeospatialEntityItem>,
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    val geom = submissionGeometries.firstOrNull { it.submissionId == sub.id }
    if (geom != null) {
      return Triple(geom.normalizedX, geom.normalizedY, geom)
    }
    if (sub.normalizedX != null && sub.normalizedY != null) {
      return Triple(
        sub.normalizedX.coerceIn(0.08f, 0.92f),
        sub.normalizedY.coerceIn(0.08f, 0.92f),
        null,
      )
    }
    val parentEntity = entities.firstOrNull { it.id == sub.entityId } ?: return null
    val subIndex = parentEntity.submissions.indexOfFirst { it.id == sub.id }.coerceAtLeast(0)
    val offsetX = (subIndex * 0.014f)
    val offsetY = (subIndex * 0.012f)
    return Triple(
      (parentEntity.normalizedX + offsetX).coerceIn(0.08f, 0.92f),
      (parentEntity.normalizedY + offsetY).coerceIn(0.08f, 0.92f),
      null,
    )
  }

  fun isSubmissionFieldGeometry(
    submissionId: String,
    field: SubmissionFieldEntry,
    submissionGeometries: List<SubmissionGeometryPolygon>,
  ): Boolean {
    if (
      submissionGeometries.any {
        it.submissionId == submissionId &&
          (it.fieldPath == field.questionName || it.questionLabel == field.questionLabel)
      }
    ) {
      return true
    }
    val labelLower = field.questionLabel.lowercase()
    val nameLower = field.questionName.lowercase()
    val valTrimmed = field.answerValue.trim()
    return labelLower.contains("geoshape") ||
      labelLower.contains("geotrace") ||
      labelLower.contains("geopoint") ||
      nameLower.contains("geoshape") ||
      nameLower.contains("geotrace") ||
      nameLower.contains("geopoint") ||
      valTrimmed.startsWith("Polygon (", ignoreCase = true) ||
      valTrimmed.startsWith("LineString (", ignoreCase = true) ||
      valTrimmed.startsWith("Point (", ignoreCase = true)
  }

  fun resolveActiveNavigationState(
    navigationTargetKind: NavigationTargetKind?,
    navigationTargetId: String?,
    fromNormalizedX: Float,
    fromNormalizedY: Float,
    unitSystem: MeasurementUnitSystem,
    userGpsCoordinatesLabel: String,
    entities: List<GeospatialEntityItem>,
    allSubmissions: List<SubmissionPreviewItem>,
    submissionGeometries: List<SubmissionGeometryPolygon>,
    findPlaceById: (String) -> SurveyPlaceItem?,
  ): StraightLineNavigationState? {
    val kind = navigationTargetKind ?: return null
    val targetId = navigationTargetId ?: return null
    return when (kind) {
      NavigationTargetKind.ENTITY -> {
        val entity = entities.firstOrNull { it.id == targetId } ?: return null
        val vector =
          invoke(
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = entity.normalizedX,
            targetNormalizedY = entity.normalizedY,
            unitSystem = unitSystem,
          )
        StraightLineNavigationState(
          targetKind = NavigationTargetKind.ENTITY,
          targetId = entity.id,
          entityId = entity.id,
          submissionId = null,
          geometryId = null,
          targetTitle = entity.label,
          targetSubtitle = "${entity.datasetName} • GeoID: ${entity.geoId}",
          targetCoordinatesLabel = entity.coordinatesLabel,
          colorHex = entity.colorHex,
          vector = vector,
        )
      }
      NavigationTargetKind.SUBMISSION -> {
        val sub = allSubmissions.firstOrNull { it.id == targetId } ?: return null
        val parentEntity = entities.firstOrNull { it.id == sub.entityId }
        val (tx, ty, geom) =
          resolveSubmissionTargetGeometry(
            submissionId = sub.id,
            allSubmissions = allSubmissions,
            submissionGeometries = submissionGeometries,
            entities = entities,
          ) ?: return null
        val vector =
          invoke(
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = tx,
            targetNormalizedY = ty,
            unitSystem = unitSystem,
          )
        StraightLineNavigationState(
          targetKind = NavigationTargetKind.SUBMISSION,
          targetId = sub.id,
          entityId = parentEntity?.id.orEmpty(),
          submissionId = sub.id,
          geometryId = geom?.id,
          targetTitle =
            geom?.shortMapBadge
              ?: if (parentEntity != null) {
                "${sub.formTitle} (${parentEntity.label.substringBefore(" •")})"
              } else {
                sub.formTitle
              },
          targetSubtitle =
            if (sub.hasAttachedEntity) {
              "${sub.entityLabel} • ${sub.collectorName} (${sub.timestamp})"
            } else {
              "Standalone submission • ${sub.collectorName} (${sub.timestamp})"
            },
          targetCoordinatesLabel =
            parentEntity?.coordinatesLabel
              ?: sub.coordinatesLabel.ifBlank { userGpsCoordinatesLabel },
          colorHex = geom?.colorHex ?: parentEntity?.colorHex ?: 0xFFAB47BC,
          vector = vector,
        )
      }
      NavigationTargetKind.PLACE -> {
        val place = findPlaceById(targetId) ?: return null
        val vector =
          invoke(
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = place.normalizedX,
            targetNormalizedY = place.normalizedY,
            unitSystem = unitSystem,
          )
        StraightLineNavigationState(
          targetKind = NavigationTargetKind.PLACE,
          targetId = place.id,
          entityId = "",
          submissionId = null,
          geometryId = null,
          targetTitle = place.name,
          targetSubtitle = "${place.categoryLabel} • ${place.regionSubtitle}",
          targetCoordinatesLabel = place.coordinatesLabel,
          colorHex = 0xFF0288D1,
          vector = vector,
        )
      }
    }
  }

  fun stepTowardTarget(
    currentX: Float,
    currentY: Float,
    targetX: Float,
    targetY: Float,
    stepFraction: Float = 0.45f,
  ): Pair<Float, Float> {
    val clampedStep = stepFraction.coerceIn(0.1f, 1.0f)
    val dx = targetX - currentX
    val dy = targetY - currentY
    if (hypot(dx.toDouble(), dy.toDouble()) < 0.008) {
      return targetX to targetY
    }
    return (currentX + dx * clampedStep) to (currentY + dy * clampedStep)
  }
}
