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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Distinguishes whether straight-line navigation is targeting a Geospatial Entity, a Submission, or
 * a searched Place.
 */
enum class NavigationTargetKind(val badgeLabel: String) {
  ENTITY("ENTITY"),
  SUBMISSION("SUBMISSION"),
  PLACE("PLACE"),
}

/**
 * Computed straight-line geodesic vector from the collector's current GPS position
 * (`userGpsNormalizedX`, `userGpsNormalizedY`) to a target entity or submission.
 */
data class StraightLineVector(
  val fromNormalizedX: Float,
  val fromNormalizedY: Float,
  val toNormalizedX: Float,
  val toNormalizedY: Float,
  val distanceMeters: Int,
  val formattedDistance: String,
  val bearingDegrees: Int,
  val cardinalDirection: String,
  val estimatedWalkMinutes: Int,
  val hasArrived: Boolean,
) {
  val isArrived: Boolean
    get() = hasArrived

  val formattedBearing: String
    get() = "${bearingDegrees}° $cardinalDirection"

  /** Formatted distance & compass bearing badge (e.g. `"495 m • 319° NW"`). */
  val formattedBadge: String
    get() = "$formattedDistance • $formattedBearing"
}

/**
 * Active straight-line wayfinding navigation state guiding the collector from their current GPS
 * position to either a [GeospatialEntityItem] or a [SubmissionPreviewItem].
 */
data class StraightLineNavigationState(
  val targetKind: NavigationTargetKind,
  val targetId: String,
  val entityId: String,
  val submissionId: String?,
  val geometryId: String?,
  val targetTitle: String,
  val targetSubtitle: String,
  val targetCoordinatesLabel: String,
  val colorHex: Long,
  val vector: StraightLineVector,
) {
  val formattedDistance: String
    get() = vector.formattedDistance
}
