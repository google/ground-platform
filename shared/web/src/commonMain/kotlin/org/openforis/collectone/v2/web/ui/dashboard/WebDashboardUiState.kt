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
package org.openforis.collectone.v2.web.ui.dashboard

import org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesActions
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesUiState

/**
 * What the [WebDashboardPage] shows: its own layout ([dashboard]), the selected record and open
 * form ([dataCollection]), the survey map ([map]), and the dedicated "Time series & imagery" bottom
 * sheet ([timeSeries]).
 */
internal data class WebDashboardUiState(
  val dashboard: DashboardUiState,
  val dataCollection: DataCollectionUiState,
  val map: SurveyMapUiState,
  val timeSeries: TimeSeriesUiState,
  /** Confirmation of an impact summary download, shown in a snackbar until dismissed. */
  val impactMessage: String? = null,
  val hasCompletedInitialSync: Boolean = true,
) {
  /** Whether the survey's map features are still loading, so tables show a spinner. */
  val isLoading: Boolean
    get() = !hasCompletedInitialSync && dashboard.entities.isEmpty()
}

/**
 * The feature intents the [WebDashboardPage] sends, one interface per part of
 * [WebDashboardUiState].
 */
internal class WebDashboardActions(
  val dashboard: DashboardActions,
  val dataCollection: DataCollectionActions,
  val map: SurveyMapActions,
  val timeSeries: TimeSeriesActions,
  val dismissImpactMessage: () -> Unit,
  val requestCloseForm: () -> Unit = { dataCollection.closeActiveFormRunner() },
)
