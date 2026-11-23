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
package org.openforis.collectone.v2.app.ui.map

import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.position
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase

/**
 * Straight-line distance and bearing from the user's GPS position to map features, submissions,
 * their geometry questions, and places, for the wayfinding badges and navigation notices.
 *
 * Stateless: every call reads the user's position from [session] and the targets from [data], so
 * the answers always match the snapshot the caller has. Distances are in
 * [SurveyMapData.unitSystem].
 */
internal class SurveyMapWayfinding(
  private val computeWayfindingNavigation: ComputeWayfindingNavigationUseCase
) {
  fun vectorTo(
    data: SurveyMapData,
    session: SurveyMapSession,
    targetNormalizedX: Float,
    targetNormalizedY: Float,
  ): StraightLineVector =
    computeWayfindingNavigation(
      fromNormalizedX = session.userGpsNormalizedX,
      fromNormalizedY = session.userGpsNormalizedY,
      targetNormalizedX = targetNormalizedX,
      targetNormalizedY = targetNormalizedY,
      unitSystem = data.unitSystem,
    )

  /** Normalized target coordinates and optional geometry polygon of [submissionId]. */
  fun submissionTargetGeometry(
    data: SurveyMapData,
    submissionId: String,
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? =
    computeWayfindingNavigation.resolveSubmissionTargetGeometry(
      submissionId = submissionId,
      allSubmissions = data.allSubmissions,
      submissionGeometries = data.submissionGeometries,
      entities = data.entities,
      anchor = data.anchor,
    )

  /** `null` for an unknown feature or one without a position. */
  fun toEntity(
    data: SurveyMapData,
    session: SurveyMapSession,
    entityId: String,
  ): StraightLineVector? {
    val entity = data.entities.firstOrNull { it.id == entityId } ?: return null
    val (x, y) = data.anchor.positionOf(entity.position ?: return null)
    return vectorTo(data, session, x, y)
  }

  fun toSubmission(
    data: SurveyMapData,
    session: SurveyMapSession,
    submissionId: String,
  ): StraightLineVector? {
    val (tx, ty, _) = submissionTargetGeometry(data, submissionId) ?: return null
    return vectorTo(data, session, tx, ty)
  }

  fun toPlace(
    data: SurveyMapData,
    session: SurveyMapSession,
    place: SurveyPlaceItem,
  ): StraightLineVector = vectorTo(data, session, place.normalizedX, place.normalizedY)

  fun isGeometryField(
    data: SurveyMapData,
    submissionId: String,
    field: SubmissionFieldEntry,
  ): Boolean =
    computeWayfindingNavigation.isSubmissionFieldGeometry(
      submissionId = submissionId,
      field = field,
      submissionGeometries = data.submissionGeometries,
    )

  /**
   * To the representative point of geometry question [field] of [submissionId]. The geometry is
   * matched by field path or, for older submissions without paths, by question label; when the
   * submission has no stored geometry for it, falls back to the submission's own target. `null` for
   * a field that isn't a geometry question.
   */
  fun toSubmissionField(
    data: SurveyMapData,
    session: SurveyMapSession,
    submissionId: String,
    field: SubmissionFieldEntry,
  ): StraightLineVector? {
    if (!isGeometryField(data, submissionId, field)) return null
    val geom =
      data.submissionGeometries.firstOrNull {
        it.submissionId == submissionId &&
          (it.fieldPath == field.questionName || it.questionLabel == field.questionLabel)
      } ?: return toSubmission(data, session, submissionId)
    val (x, y) = data.anchor.positionOf(geom.geometry.representativePoint)
    return vectorTo(data, session, x, y)
  }
}
