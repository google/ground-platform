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

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.map.LatLng

/** The searchable list's search as connectivity changes and the session resets. */
class ListSearchViewModelTest {
  private val holder = sampleDataHolder()
  private val viewModel =
    ListSearchViewModel(
      surveyRepository = holder.surveyRepository,
      placeRepository = holder.placeRepository,
      connectivityRepository = holder.connectivityRepository,
      searchPlaces = holder.searchPlacesUseCase,
    )

  private val uiState
    get() = viewModel.uiState.value

  @Test
  fun goingOffline_dropsTheGeocoderResults() = runTest {
    searchedWithGeocoderResults("Aberdare")

    holder.connectivityRepository.setOnline(false)

    assertTrue(uiState.geocoderPlaces.isEmpty())
    assertTrue(uiState.places.isEmpty())
  }

  @Test
  fun comingBackOnline_searchesTheQueryAgain() {
    viewModel.updateQuery("Karima")
    holder.connectivityRepository.setOnline(false)

    holder.connectivityRepository.setOnline(true)

    assertEquals(listOf("Karima Hill Forest Reserve"), uiState.places.map { it.name })
  }

  @Test
  fun alwaysOnlineClient_searchesPlacesWhileTheDeviceIsOffline() {
    val webViewModel =
      ListSearchViewModel(
        surveyRepository = holder.surveyRepository,
        placeRepository = holder.placeRepository,
        connectivityRepository = holder.connectivityRepository,
        searchPlaces = holder.searchPlacesUseCase,
        isAlwaysOnline = true,
      )
    holder.connectivityRepository.setOnline(false)

    webViewModel.updateQuery("Karima")

    assertEquals(
      listOf("Karima Hill Forest Reserve"),
      webViewModel.uiState.value.places.map { it.name },
    )
  }

  @Test
  fun reset_clearsTheQueryAndTheGeocoderResults() = runTest {
    searchedWithGeocoderResults("Aberdare")

    viewModel.reset()

    assertEquals("", uiState.query)
    assertTrue(uiState.geocoderPlaces.isEmpty())
  }

  @Test
  fun resultsForAnEarlierQuery_areDropped() = runTest {
    viewModel.updateQuery("Aberdare")
    viewModel.awaitPlacesSearchIdle()
    viewModel.updateQuery("Karima")
    viewModel.awaitPlacesSearchIdle()

    viewModel.onPlacesSearchResults("Aberdare", listOf(ABERDARE))

    assertTrue(uiState.geocoderPlaces.none { it.id == ABERDARE.id })
    assertFalse(uiState.isSearchingPlaces)
  }

  @Test
  fun resultsArrivingOffline_areDropped() = runTest {
    viewModel.updateQuery("Aberdare")
    viewModel.awaitPlacesSearchIdle()
    holder.connectivityRepository.setOnline(false)

    viewModel.onPlacesSearchResults("Aberdare", listOf(ABERDARE))

    assertTrue(uiState.geocoderPlaces.isEmpty())
  }

  @Test
  fun queriesWhileOffline_matchMapFeaturesWithoutSearchingPlaces() {
    holder.connectivityRepository.setOnline(false)
    val feature = uiState.entities.first()

    viewModel.updateQuery(feature.geoId)

    assertFalse(uiState.isSearchingPlaces)
    assertTrue(uiState.entities.any { it.id == feature.id })
    assertTrue(uiState.entityGroups.flatMap { it.entities }.any { it.id == feature.id })
  }

  @Test
  fun clearQuery_listsEveryMapFeatureAgain() {
    val all = uiState.entities
    viewModel.updateQuery("no-such-feature-xyz")
    assertTrue(uiState.entities.isEmpty())

    viewModel.clearQuery()

    assertEquals(all, uiState.entities)
    assertTrue(uiState.geocoderPlaces.isEmpty())
  }

  @Test
  fun savedState_restoresAndUpdatesQuery() {
    val savedState = SavedStateHandle()
    val feature = uiState.entities.first()
    val first =
      ListSearchViewModel(
        surveyRepository = holder.surveyRepository,
        placeRepository = holder.placeRepository,
        connectivityRepository = holder.connectivityRepository,
        searchPlaces = holder.searchPlacesUseCase,
        savedState = savedState,
      )
    first.updateQuery(feature.geoId)
    assertEquals(feature.geoId, savedState["listSearchQuery"])

    val restored =
      ListSearchViewModel(
        surveyRepository = holder.surveyRepository,
        placeRepository = holder.placeRepository,
        connectivityRepository = holder.connectivityRepository,
        searchPlaces = holder.searchPlacesUseCase,
        savedState = savedState,
      )
    assertEquals(feature.geoId, restored.uiState.value.query)
    assertTrue(restored.uiState.value.entities.any { it.id == feature.id })
  }

  @Test
  fun findPlace_prefersGeocoderPlaces_thenFallsBackToGazetteerPlaces_orNull() = runTest {
    searchedWithGeocoderResults("Karima")
    val gazetteerPlace = uiState.places.first { it.id != ABERDARE.id }

    assertEquals(ABERDARE, uiState.findPlace(ABERDARE.id))
    assertEquals(gazetteerPlace, uiState.findPlace(gazetteerPlace.id))
    assertNull(uiState.findPlace("place.missing"))
  }

  @Test
  fun statusFilter_filtersOnlyTargetDatasetAndCombinesWithSearchQuery() {
    assertTrue(uiState.usesEntityStatus)
    assertEquals(
      listOf("○ Pending", "◐ In progress", "✓ Completed"),
      uiState.statusOptionsForDataset("coffee_parcels").map { it.badgeText },
    )
    // Single-entity homogeneous layers do not expose redundant 1-chip filters.
    assertTrue(uiState.statusOptionsForDataset("shade_monitoring_plots").isEmpty())

    viewModel.toggleStatusFilter("coffee_parcels", "Pending")
    assertEquals("Pending", uiState.selectedStatusForDataset("coffee_parcels"))
    // coffee_parcels narrows to its 1 Pending parcel while the other 2 datasets remain listed.
    assertEquals(3, uiState.entities.size)
    val parcelsGroup = uiState.entityGroups.first { it.datasetId == "coffee_parcels" }
    assertEquals(1, parcelsGroup.entities.size)
    assertEquals("Pending", parcelsGroup.entities.single().workflowStatus)

    // Toggling the same status again clears the dataset filter.
    viewModel.toggleStatusFilter("coffee_parcels", "Pending")
    assertNull(uiState.selectedStatusForDataset("coffee_parcels"))
    assertEquals(5, uiState.entities.size)

    // Combining dataset status filter with search query:
    viewModel.toggleStatusFilter("coffee_parcels", "In progress")
    assertEquals(3, uiState.entities.size)
    viewModel.updateQuery("Njeri")
    assertEquals(1, uiState.entities.size)
    assertTrue(uiState.entities.single().label.contains("Njeri"))
  }

  @Test
  fun surveyWithoutEntityStatus_hidesStatusOptionsAndClearsStatusFilter() = runTest {
    viewModel.toggleStatusFilter("coffee_parcels", "Pending")
    assertEquals("Pending", uiState.selectedStatusForDataset("coffee_parcels"))

    holder.surveyRepository.setActiveSurveyId("00000036-1a1c-5c51-997f-28b67d813aa3")

    assertFalse(uiState.usesEntityStatus)
    assertTrue(uiState.selectedStatuses.isEmpty())
    assertTrue(uiState.entityGroups.all { it.statusFilterOptions.isEmpty() })
    assertEquals(3, uiState.entities.size)
  }

  private suspend fun searchedWithGeocoderResults(query: String) {
    viewModel.updateQuery(query)
    viewModel.awaitPlacesSearchIdle()
    viewModel.onPlacesSearchResults(query, listOf(ABERDARE))
    assertEquals(listOf(ABERDARE), uiState.geocoderPlaces)
  }

  private companion object {
    val ABERDARE =
      SurveyPlaceItem(
        id = "place.aberdare.901",
        name = "Aberdare National Park",
        categoryLabel = "National Park",
        regionSubtitle = "Nyeri County, Kenya",
        normalizedX = 0.05f,
        normalizedY = 0.05f,
        position = LatLng(-0.3833, 36.7),
      )
  }
}
