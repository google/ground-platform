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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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
import org.openforis.collectone.v2.map.LatLng

/**
 * Straight-line wayfinding toward a map feature, submission, or place: geodesic great-circle
 * distance, initial spherical bearing, and walking time, resolving submission geometries to a
 * target, and simulated GPS stepping toward it.
 */
class ComputeWayfindingNavigationUseCase {
  /**
   * Geodesic straight-line vector from [from] to [to] using the Haversine formula (WGS 84 mean
   * Earth radius) and initial spherical bearing clockwise from true north. Walking time assumes 65
   * m/min (about 3.9 km/h); within 8 m the collector has arrived.
   */
  operator fun invoke(
    from: LatLng,
    to: LatLng,
    unitSystem: MeasurementUnitSystem,
    fromNormalizedX: Float = 0f,
    fromNormalizedY: Float = 0f,
    targetNormalizedX: Float = 0f,
    targetNormalizedY: Float = 0f,
  ): StraightLineVector {
    val lat1 = from.latitude * DEG_TO_RAD
    val lat2 = to.latitude * DEG_TO_RAD
    val dLat = (to.latitude - from.latitude) * DEG_TO_RAD
    val dLon = (to.longitude - from.longitude) * DEG_TO_RAD
    val sinDLat = sin(dLat / 2.0)
    val sinDLon = sin(dLon / 2.0)
    val h = (sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon).coerceIn(0.0, 1.0)
    val distMeters = (2.0 * EARTH_MEAN_RADIUS_M * asin(sqrt(h))).roundToInt().coerceAtLeast(0)
    val rawBearingDeg =
      if (distMeters == 0) {
        0.0
      } else {
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        atan2(y, x) * RAD_TO_DEG
      }
    val bearingDeg = (((rawBearingDeg.roundToInt()) % 360) + 360) % 360
    val cardinalIdx = (((bearingDeg + 22.5) / 45.0).toInt()) % 8
    val cardinal = CARDINAL_DIRECTIONS[cardinalIdx]
    val walkMinutes =
      if (distMeters <= ARRIVAL_RADIUS_M) {
        0
      } else {
        (distMeters / WALK_METERS_PER_MIN).roundToInt().coerceAtLeast(1)
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
      hasArrived = distMeters <= ARRIVAL_RADIUS_M,
    )
  }

  /**
   * Straight-line vector between normalized positions in [anchor]'s frame, converted to WGS 84
   * coordinates before computing geodesic distance and bearing.
   */
  operator fun invoke(
    fromNormalizedX: Float,
    fromNormalizedY: Float,
    targetNormalizedX: Float,
    targetNormalizedY: Float,
    unitSystem: MeasurementUnitSystem,
    anchor: SurveyMapAnchor = SurveyMapAnchor.forSurvey(SurveyMapAnchor.DEFAULT_SURVEY_ID),
  ): StraightLineVector =
    invoke(
      from = anchor.toLatLng(fromNormalizedX.toDouble(), fromNormalizedY.toDouble()),
      to = anchor.toLatLng(targetNormalizedX.toDouble(), targetNormalizedY.toDouble()),
      unitSystem = unitSystem,
      fromNormalizedX = fromNormalizedX,
      fromNormalizedY = fromNormalizedY,
      targetNormalizedX = targetNormalizedX,
      targetNormalizedY = targetNormalizedY,
    )

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
    fromLatLng: LatLng? = null,
  ): StraightLineNavigationState? {
    val kind = navigationTargetKind ?: return null
    val targetId = navigationTargetId ?: return null
    val origin =
      fromLatLng ?: anchor.toLatLng(fromNormalizedX.toDouble(), fromNormalizedY.toDouble())
    return when (kind) {
      NavigationTargetKind.ENTITY -> {
        val entity = entities.firstOrNull { it.id == targetId } ?: return null
        val targetPos = entity.position ?: return null
        val (targetX, targetY) = anchor.positionOf(targetPos)
        val vector =
          invoke(
            from = origin,
            to = targetPos,
            unitSystem = unitSystem,
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = targetX,
            targetNormalizedY = targetY,
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
        val targetPos =
          geom?.geometry?.representativePoint
            ?: sub.location
            ?: anchor.toLatLng(tx.toDouble(), ty.toDouble())
        val vector =
          invoke(
            from = origin,
            to = targetPos,
            unitSystem = unitSystem,
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = tx,
            targetNormalizedY = ty,
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
            from = origin,
            to = place.position,
            unitSystem = unitSystem,
            fromNormalizedX = fromNormalizedX,
            fromNormalizedY = fromNormalizedY,
            targetNormalizedX = place.normalizedX,
            targetNormalizedY = place.normalizedY,
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

  private companion object {
    const val EARTH_MEAN_RADIUS_M = 6_371_008.8
    const val DEG_TO_RAD = PI / 180.0
    const val RAD_TO_DEG = 180.0 / PI
    const val ARRIVAL_RADIUS_M = 8
    const val WALK_METERS_PER_MIN = 65.0
    val CARDINAL_DIRECTIONS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
  }
}
