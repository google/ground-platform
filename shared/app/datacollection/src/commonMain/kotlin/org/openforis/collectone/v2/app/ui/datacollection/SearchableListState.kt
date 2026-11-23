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
package org.openforis.collectone.v2.app.ui.datacollection

import org.openforis.collectone.v2.app.domain.model.EntityDatasetFeaturesGroup
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.ui.map.MapFeatureNoun

/**
 * A place in the searchable list's `Places` section.
 * - [isNavigating] and [wayfinding]: straight-line navigation to [place] and the distance and
 *   bearing to it (shown as a badge, e.g. `340 m • 142° SE`).
 */
data class PlaceListItem(
  val place: SurveyPlaceItem,
  val isSelected: Boolean = false,
  val isNavigating: Boolean = false,
  val wayfinding: StraightLineVector? = null,
)

/**
 * State of the searchable list of map features and places ([BottomSheetSearchableListContent]),
 * shown in the mobile bottom sheet and the web dashboard's left-hand panel.
 * - [surveyTitle] and [surveyLocation]: the mobile sheet's peek header.
 * - [isOnline]: places are only searched online; offline the mobile sheet explains that search is
 *   limited to local map features.
 * - [isSheetExpanded]: whether the mobile bottom sheet is expanded (typing in the web search field
 *   expands it too).
 * - [entitiesCountNoun]: what the map features are called (a layer's noun or generic ones).
 * - [hasMapLayers]: whether the survey has any entity-dataset map layers configured, distinguishing
 *   an empty survey ("No map layers yet.") from an empty map layer ("No map features yet.").
 * - [entityGroups]: the map features and data table records matching [query] and
 *   [activeStatusFilters], grouped by dataset, with each dataset carrying its own status filter
 *   options and selected status filter.
 * - [activeStatusFilters]: currently active workflow status filters keyed by `datasetId`.
 * - [collapsedDatasetIds]: datasets whose rows are hidden in the web panel's list (none while
 *   searching, and never a dataset whose status filter is currently active).
 * - [places]: the places matching [query].
 */
data class SearchableListUiState(
  val surveyTitle: String = "",
  val surveyLocation: String = "",
  val query: String = "",
  val isOnline: Boolean = true,
  val isSheetExpanded: Boolean = false,
  val entitiesCountNoun: MapFeatureNoun = MapFeatureNoun.Generic,
  val hasMapLayers: Boolean = false,
  val entityGroups: List<EntityDatasetFeaturesGroup> = emptyList(),
  val activeStatusFilters: Map<String, String> = emptyMap(),
  val collapsedDatasetIds: Set<String> = emptySet(),
  val selectedEntityId: String? = null,
  val selectedLayerDatasetId: String? = null,
  val places: List<PlaceListItem> = emptyList(),
) {
  /** Whether any entity dataset in the active survey exposes status filter options. */
  val usesEntityStatus: Boolean
    get() = entityGroups.any { it.statusFilterOptions.isNotEmpty() }
}

/** User intents of the searchable list of map features, data tables, and places. */
interface SearchableListActions {
  /** Searches map features (and places, when online) for [query]. */
  fun updateListSearchQuery(query: String)

  fun clearListSearchQuery()

  /** Toggles filtering entities in [datasetId] by workflow [status]. */
  fun toggleListStatusFilter(datasetId: String, status: String) {}

  /**
   * Clears the active workflow status filter on [datasetId], or across every dataset when
   * [datasetId] is `null`.
   */
  fun clearListStatusFilter(datasetId: String? = null) {}

  fun updateEntityBottomSheetExpanded(expanded: Boolean)

  /**
   * Selects the map layer of the dataset with [datasetId] (web), opening its details card, or
   * clears the layer selection when `null`.
   */
  fun selectLayer(datasetId: String?)

  fun toggleListDatasetCollapsed(datasetId: String)

  fun toggleLayerVisibility(layerId: String)

  /** Selects a map feature from the list, framing it on the map and showing its details. */
  fun selectEntityFromList(entityId: String)

  /** Flies the map to the place with [placeId]. */
  fun selectPlace(placeId: String)

  fun toggleNavigationToPlace(placeId: String)
}

/**
 * What the mobile survey's persistent bottom sheet ([SurveyPersistentBottomSheetContent]) shows.
 */
sealed interface SurveyBottomSheetUiState {
  /** No record is selected: the searchable list of map features and places. */
  data class SearchableList(val list: SearchableListUiState) : SurveyBottomSheetUiState

  /** A map feature is selected: its details. */
  data class EntityDetails(val details: EntityDetailsUiState) : SurveyBottomSheetUiState

  /** A standalone submission (one without a map feature) is selected: its details. */
  data class SubmissionDetails(val details: SubmissionDetailsUiState) : SurveyBottomSheetUiState
}
