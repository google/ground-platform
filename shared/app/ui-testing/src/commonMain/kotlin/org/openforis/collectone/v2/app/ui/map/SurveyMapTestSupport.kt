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
package org.openforis.collectone.v2.app.ui.map

import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.datacollection.main

/**
 * The main survey map as the mobile map view and web dashboard draw it from [this] session's map,
 * data collection, and pending uploads state.
 */
fun AppViewModels.mainSurveyMap(showNavigation: Boolean): SurveyMap =
  SurveyMapContent.main(
    uiState = surveyMap.uiState.value,
    dataCollection = dataCollection.uiState.value,
    pendingIds = dashboard.uiState.value.pendingUploadEntityIds,
    showNavigation = showNavigation,
  )
