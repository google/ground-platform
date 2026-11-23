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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaSource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_collect_earth_grid
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_coordinates
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_drawn
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_place_search
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_selected_boundary
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_uploaded

/** How the survey area was defined, in the user's language (e.g. `"Uploaded: plots.geojson"`). */
@Composable
internal fun SurveyAreaSource.text(): String =
  when (this) {
    SurveyAreaSource.SelectedBoundary ->
      stringResource(Res.string.surveyeditor_area_source_selected_boundary)
    SurveyAreaSource.Drawn -> stringResource(Res.string.surveyeditor_area_source_drawn)
    is SurveyAreaSource.Uploaded ->
      stringResource(Res.string.surveyeditor_area_source_uploaded, fileName)
    SurveyAreaSource.PlaceSearch -> stringResource(Res.string.surveyeditor_area_source_place_search)
    SurveyAreaSource.Coordinates -> stringResource(Res.string.surveyeditor_area_source_coordinates)
    is SurveyAreaSource.CollectEarthGrid ->
      pluralStringResource(
        Res.plurals.surveyeditor_area_source_collect_earth_grid,
        plotCount,
        plotCount,
      )
  }
