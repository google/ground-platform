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
package org.openforis.collectone.v2.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.ui.listsearch.awaitPlacesSearchIdle
import org.openforis.collectone.v2.app.ui.session.TestAppSession
import org.openforis.collectone.v2.map.LatLng

/**
 * The searchable list's search: map features filtered by the query, and places matched from the
 * survey's gazetteer and the live geocoder.
 */
class SearchableListSearchTest {
  private val dataHolder = sampleDataHolder()
  private val session = TestAppSession(dataHolder)

  @Test
  fun blankQuery_listsEveryMapFeatureGroupedByDataset_andNoPlaces() {
    assertEquals(5, session.viewModels.listSearch.uiState.value.entities.size)
    assertEquals(
      listOf(
        "Smallholder Coffee Parcels",
        "Shade Tree Monitoring Plots",
        "Cooperative Washing Stations",
      ),
      session.viewModels.listSearch.uiState.value.entityGroups.map { it.datasetName },
    )
    assertTrue(session.viewModels.listSearch.uiState.value.places.isEmpty())
  }

  @Test
  fun query_matchesMapFeaturesByPropertyValue() {
    session.searchableListActions.updateListSearchQuery("Kamau")

    assertEquals(
      listOf("Plot NYR-104 • Kamau Family Parcel"),
      session.viewModels.listSearch.uiState.value.entities.map { it.label },
    )
  }

  @Test
  fun query_matchesTheSurveysPlaces() {
    session.searchableListActions.updateListSearchQuery("Karima Hill")

    assertEquals(
      listOf("Karima Hill Forest Reserve"),
      session.viewModels.listSearch.uiState.value.places.map { it.name },
    )
    assertTrue(session.viewModels.listSearch.uiState.value.entities.isEmpty())
  }

  @Test
  fun geocoderResults_forTheCurrentQuery_mergeIntoThePlaces() = runTest {
    session.searchableListActions.updateListSearchQuery("Aberdare")
    session.viewModels.listSearch.awaitPlacesSearchIdle()

    session.viewModels.listSearch.onPlacesSearchResults("Aberdare", listOf(ABERDARE))

    assertEquals(
      listOf(ABERDARE.id),
      session.viewModels.listSearch.uiState.value.places.map { it.id },
    )
    assertFalse(session.viewModels.listSearch.uiState.value.isSearchingPlaces)
  }

  @Test
  fun geocoderResults_forAnOlderQuery_areIgnored() = runTest {
    session.searchableListActions.updateListSearchQuery("Aberdare")
    session.viewModels.listSearch.awaitPlacesSearchIdle()

    session.viewModels.listSearch.onPlacesSearchResults("Aberd", listOf(ABERDARE))

    assertTrue(session.viewModels.listSearch.uiState.value.places.isEmpty())
  }

  @Test
  fun clearListSearchQuery_dropsTheGeocoderResults() = runTest {
    session.searchableListActions.updateListSearchQuery("Aberdare")
    session.viewModels.listSearch.awaitPlacesSearchIdle()
    session.viewModels.listSearch.onPlacesSearchResults("Aberdare", listOf(ABERDARE))

    session.searchableListActions.clearListSearchQuery()

    assertEquals("", session.viewModels.listSearch.uiState.value.query)
    assertTrue(session.viewModels.listSearch.uiState.value.geocoderPlaces.isEmpty())
    assertTrue(session.viewModels.listSearch.uiState.value.places.isEmpty())
  }

  @Test
  fun selectPlace_findsAGeocoderResult() = runTest {
    session.searchableListActions.updateListSearchQuery("Aberdare")
    session.viewModels.listSearch.awaitPlacesSearchIdle()
    session.viewModels.listSearch.onPlacesSearchResults("Aberdare", listOf(ABERDARE))

    session.searchableListActions.selectPlace(ABERDARE.id)

    assertEquals(ABERDARE.id, session.viewModels.surveyMap.uiState.value.selectedPlace?.id)
  }

  @Test
  fun offline_searchesMapFeaturesButNoPlaces() {
    dataHolder.connectivityRepository.setOnline(false)

    session.searchableListActions.updateListSearchQuery("Karima")

    assertTrue(session.viewModels.listSearch.uiState.value.places.isEmpty())
    session.searchableListActions.updateListSearchQuery("Kamau")
    assertEquals(1, session.viewModels.listSearch.uiState.value.entities.size)
  }

  @Test
  fun statusFilter_filtersTargetDatasetAndHidesOptionsWhenSurveyDoesNotUseStatus() {
    assertTrue(session.viewModels.listSearch.uiState.value.usesEntityStatus)
    assertEquals(
      listOf("Pending", "In progress", "Completed"),
      session.viewModels.listSearch.uiState.value.statusOptionsForDataset("coffee_parcels").map {
        it.status
      },
    )

    session.searchableListActions.toggleListStatusFilter("coffee_parcels", "Completed")
    assertEquals(
      "Completed",
      session.viewModels.listSearch.uiState.value.selectedStatusForDataset("coffee_parcels"),
    )
    val parcelsGroup =
      session.viewModels.listSearch.uiState.value.entityGroups.first {
        it.datasetId == "coffee_parcels"
      }
    assertEquals(1, parcelsGroup.entities.size)
    assertEquals("Completed", parcelsGroup.entities.single().workflowStatus)
    // Other datasets remain unfiltered.
    assertEquals(3, session.viewModels.listSearch.uiState.value.entities.size)

    session.searchableListActions.clearListStatusFilter("coffee_parcels")
    assertEquals(
      null,
      session.viewModels.listSearch.uiState.value.selectedStatusForDataset("coffee_parcels"),
    )
    assertEquals(5, session.viewModels.listSearch.uiState.value.entities.size)

    // Switch to Simple Point & Land Use Survey, where entities do not use workflow status.
    session.viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
    assertFalse(session.viewModels.listSearch.uiState.value.usesEntityStatus)
    assertTrue(session.viewModels.listSearch.uiState.value.selectedStatuses.isEmpty())
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
