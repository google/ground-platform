/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.model.PlaceKind
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_place_coordinates_category
import org.openforis.collectone.v2.app.resources.app_place_coordinates_name
import org.openforis.collectone.v2.app.resources.app_place_coordinates_region

/** The place's name: its own, or `Coordinates (0.4210°S, 36.9505°E)` for typed coordinates. */
fun SurveyPlaceItem.nameText(): UiText =
  when (kind) {
    PlaceKind.NAMED -> UiText.Plain(name)
    PlaceKind.COORDINATES ->
      Res.string.app_place_coordinates_name.toUiText(coordinatesText(position))
  }

/** The place's category: its own, or `GPS Coordinates` for typed coordinates. */
fun SurveyPlaceItem.categoryText(): UiText =
  when (kind) {
    PlaceKind.NAMED -> UiText.Plain(categoryLabel)
    PlaceKind.COORDINATES -> Res.string.app_place_coordinates_category.toUiText()
  }

/** The place's region: its own, or the survey's location for typed coordinates. */
fun SurveyPlaceItem.regionText(): UiText =
  when (kind) {
    PlaceKind.NAMED -> UiText.Plain(regionSubtitle)
    PlaceKind.COORDINATES -> Res.string.app_place_coordinates_region.toUiText(regionSubtitle)
  }
