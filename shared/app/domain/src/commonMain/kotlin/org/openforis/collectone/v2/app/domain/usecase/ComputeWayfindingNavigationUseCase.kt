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
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.StraightLineNavigationState
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.position
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel

/**
 * Straight-line wayfinding toward a map feature, submission, or place: distance, bearing, and
 * walking time, resolving submission geometries to a target, and simulated GPS stepping toward it.
 *
 * Positions are in the survey map's normalized frame ([SurveyMapAnchor]: x east, y south, each 0..1
 * across the anchor's span), not latitude / longitude, and distances are planar within that frame.
 */
class ComputeWayfindingNavigationUseCase {
  /**
   * The straight line from one normalized position to another. The frame spans
   * [SurveyMapAnchor.SPAN_LONGITUDE] × [SurveyMapAnchor.SPAN_LATITUDE] degrees, converted at the
   * equator (1558.48 m east-west, 2000.38 m north-south), so distances are approximate away from
   * it. The bearing is clockwise from north, rounded to whole degrees and to one of 8 compass
   * points; walking time assumes 65 m/min (about 3.9 km/h); within 8 m the collector has arrived.
   */
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
      distance = unitSystem.distance(distMeters),
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
    anchor: SurveyMapAnchor,
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    val geom = submissionGeometries.firstOrNull { it.submissionId == sub.id }
    if (geom != null) {
      val (x, y) = anchor.positionOf(geom.geometry.representativePoint)
      return Triple(x, y, geom)
    }
    if (sub.location != null) {
      val (x, y) = anchor.positionOf(sub.location)
      return Triple(x.coerceIn(0.08f, 0.92f), y.coerceIn(0.08f, 0.92f), null)
    }
    val parentEntity = entities.firstOrNull { it.id == sub.entityId } ?: return null
    val (parentX, parentY) = anchor.positionOf(parentEntity.position ?: return null)
    val subIndex = parentEntity.submissions.indexOfFirst { it.id == sub.id }.coerceAtLeast(0)
    val offsetX = (subIndex * 0.014f)
    val offsetY = (subIndex * 0.012f)
    return Triple(
      (parentX + offsetX).coerceIn(0.08f, 0.92f),
      (parentY + offsetY).coerceIn(0.08f, 0.92f),
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
    entities: List<GeospatialEntityItem>,
    allSubmissions: List<SubmissionPreviewItem>,
    submissionGeometries: List<SubmissionGeometryPolygon>,
    findPlaceById: (String) -> SurveyPlaceItem?,
    anchor: SurveyMapAnchor,
  ): StraightLineNavigationState? {
    val kind = navigationTargetKind ?: return null
    val targetId = navigationTargetId ?: return null
    return when (kind) {
      NavigationTargetKind.ENTITY -> {
        val entity = entities.firstOrNull { it.id == targetId } ?: return null
        val (targetX, targetY) = anchor.positionOf(entity.position ?: return null)
        val vector =
          invoke(
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = targetX,
            targetNormalizedY = targetY,
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
            anchor = anchor,
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
              "${sub.entityLabel} • ${sub.collectorName} (${sub.updatedAtLabel})"
            } else {
              "Standalone submission • ${sub.collectorName} (${sub.updatedAtLabel})"
            },
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
