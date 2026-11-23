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
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem

/**
 * Distances and bearings from the user's GPS position (see `SurveyMapViewModel`), which the UI
 * shows as wayfinding badges (`wayfindingBadgeText`).
 */
interface WayfindingBadges {
  /** To the map feature with [entityId], or `null` if it has no location. */
  fun distanceAndBearingToEntity(entityId: String): StraightLineVector?

  /** To [place]. */
  fun distanceAndBearingToPlace(place: SurveyPlaceItem): StraightLineVector

  /** To the geometry [field] of [submissionId], or `null` if it isn't a geometry. */
  fun distanceAndBearingToSubmissionField(
    submissionId: String,
    field: SubmissionFieldEntry,
  ): StraightLineVector?
}
