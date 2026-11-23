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
package org.openforis.collectone.v2.app.ui.listsearch

import org.openforis.collectone.v2.app.domain.model.EntityDatasetFeaturesGroup
import org.openforis.collectone.v2.app.domain.model.EntityStatusFilterOption
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem

/**
 * State of the searchable list's search (see `ListSearchViewModel`): the query, any per-dataset
 * workflow status filters, the active survey's map features and data table records they match, and
 * the places [query] matches in the survey's gazetteer and the live geocoder.
 */
data class ListSearchUiState(
  val query: String = "",
  /**
   * Currently selected workflow status filter per entity dataset (`datasetId -> status`), e.g.
   * `"coffee_parcels" to "Pending"`. Empty when all statuses are shown across every dataset.
   */
  val selectedStatuses: Map<String, String> = emptyMap(),
  /** Whether the device is online; places are only searched online. */
  val isOnline: Boolean = true,
  /** Whether a live geocoder search for [query] is in flight. */
  val isSearchingPlaces: Boolean = false,
  /** The live geocoder's results for [query]. */
  val geocoderPlaces: List<SurveyPlaceItem> = emptyList(),
  /** Entities matching [query] and [selectedStatuses]: every one while both are unset. */
  val entities: List<GeospatialEntityItem> = emptyList(),
  /**
   * [entities] grouped by their entity dataset (map layer or data table), each carrying its own
   * [EntityDatasetFeaturesGroup.statusFilterOptions] and
   * [EntityDatasetFeaturesGroup.selectedStatusFilter].
   */
  val entityGroups: List<EntityDatasetFeaturesGroup> = emptyList(),
  /**
   * Places matching [query]: geocoder results, then gazetteer matches, led by a place for the
   * coordinates [query] names. Empty while [query] is blank or the device is offline.
   */
  val places: List<SurveyPlaceItem> = emptyList(),
) {
  /**
   * Whether at least one entity dataset in the active survey exposes workflow status filter
   * options.
   */
  val usesEntityStatus: Boolean
    get() = entityGroups.any { it.statusFilterOptions.isNotEmpty() }

  /** Selected status filter for [datasetId], or `null` when none is active on that dataset. */
  fun selectedStatusForDataset(datasetId: String): String? = selectedStatuses[datasetId]

  /** Available status filter options for [datasetId], or empty when that dataset has none. */
  fun statusOptionsForDataset(datasetId: String): List<EntityStatusFilterOption> =
    entityGroups.firstOrNull { it.datasetId == datasetId }?.statusFilterOptions.orEmpty()

  /** The place with [placeId] among [geocoderPlaces] and [places], or `null`. */
  fun findPlace(placeId: String): SurveyPlaceItem? =
    geocoderPlaces.firstOrNull { it.id == placeId } ?: places.firstOrNull { it.id == placeId }
}
