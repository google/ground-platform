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
package org.openforis.collectone.v2.app.ui.session

import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListActions

/**
 * Intents of the searchable list of map features and places, applied to the session's [viewModels]:
 * the search to its `ListSearchViewModel`, selection and navigation to the map, and layers to the
 * dashboard.
 */
class SessionSearchableListActions(private val viewModels: AppViewModels) : SearchableListActions {
  private val surveyMap = viewModels.surveyMap

  override fun updateListSearchQuery(query: String) = viewModels.listSearch.updateQuery(query)

  override fun clearListSearchQuery() = viewModels.listSearch.clearQuery()

  override fun toggleListStatusFilter(datasetId: String, status: String) =
    viewModels.listSearch.toggleStatusFilter(datasetId, status)

  override fun clearListStatusFilter(datasetId: String?) =
    viewModels.listSearch.clearStatusFilter(datasetId)

  override fun updateEntityBottomSheetExpanded(expanded: Boolean) =
    surveyMap.updateEntityBottomSheetExpanded(expanded)

  /** Selecting a layer clears any selected map feature, submission, or place. */
  override fun selectLayer(datasetId: String?) {
    viewModels.dashboard.selectLayer(datasetId)
    if (datasetId != null) {
      surveyMap.clearSelectedPlace()
      viewModels.dataCollection.clearSelection()
    }
  }

  override fun toggleListDatasetCollapsed(datasetId: String) =
    viewModels.dashboard.toggleListDatasetCollapsed(datasetId)

  override fun toggleLayerVisibility(layerId: String) = surveyMap.toggleLayerVisibility(layerId)

  /**
   * The map pans and zooms to the map feature, and the sheet settles at its peek height showing its
   * details, so both are visible.
   */
  override fun selectEntityFromList(entityId: String) = surveyMap.selectEntity(entityId)

  override fun selectPlace(placeId: String) {
    findPlace(placeId)?.let(surveyMap::selectPlace)
  }

  override fun toggleNavigationToPlace(placeId: String) {
    if (surveyMap.uiState.value.isNavigatingToPlace(placeId)) {
      surveyMap.stopNavigation()
    } else {
      findPlace(placeId)?.let(surveyMap::startNavigationToPlace)
    }
  }

  /**
   * The place with [placeId] among the list's matches and the geocoder's results, the survey's
   * places, and the place selected or navigated to on the map.
   */
  private fun findPlace(placeId: String): SurveyPlaceItem? =
    viewModels.listSearch.uiState.value.findPlace(placeId) ?: surveyMap.findPlaceById(placeId)
}
