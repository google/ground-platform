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
package org.openforis.collectone.v2.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionViewModel
import org.openforis.collectone.v2.app.ui.listsearch.ListSearchViewModel
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingViewModel

/** UI state the ViewModels keep in their [SavedStateHandle] across Android process death. */
class SavedUiStateTest {
  private val holder = sampleDataHolder()

  @Test
  fun onboarding_restoresAndSavesTheSurveySearchQuery() {
    val savedState = SavedStateHandle(mapOf("searchQuery" to "forest"))
    val viewModel = onboarding(savedState)

    assertEquals("forest", viewModel.uiState.value.searchQuery)

    viewModel.updateSearchQuery("mangrove")

    assertEquals("mangrove", savedState["searchQuery"])
  }

  @Test
  fun listSearch_restoresAndSavesTheQuery() {
    val savedState = SavedStateHandle(mapOf("listSearchQuery" to "plot 7"))
    val viewModel = listSearch(savedState)

    assertEquals("plot 7", viewModel.uiState.value.query)

    viewModel.clearQuery()

    assertEquals("", savedState["listSearchQuery"])
  }

  @Test
  fun listSearch_restoresAndSavesTheStatusFilter() {
    val savedState = SavedStateHandle(mapOf("listSearchStatusFilters" to "coffee_parcels\tPending"))
    val viewModel = listSearch(savedState)

    assertEquals(mapOf("coffee_parcels" to "Pending"), viewModel.uiState.value.selectedStatuses)

    viewModel.clearStatusFilter()

    assertNull(savedState["listSearchStatusFilters"])
  }

  @Test
  fun dataCollection_restoresAndSavesTheSelectedMapFeature() {
    val savedState = SavedStateHandle(mapOf("selectedEntityId" to "entity-1"))
    val viewModel = dataCollection(savedState)

    assertEquals("entity-1", viewModel.uiState.value.selectedEntityId)

    viewModel.onEntitySelected(null)

    assertNull(savedState["selectedEntityId"])
  }

  @Test
  fun withoutSavedState_startsEmpty() {
    assertEquals("", onboarding(SavedStateHandle()).uiState.value.searchQuery)
    assertEquals("", listSearch(SavedStateHandle()).uiState.value.query)
    assertNull(dataCollection(SavedStateHandle()).uiState.value.selectedEntityId)
  }

  private fun onboarding(savedState: SavedStateHandle) =
    OnboardingViewModel(
      authRepository = holder.authRepository,
      surveyRepository = holder.surveyRepository,
      organizationRepository = holder.organizationRepository,
      connectivityRepository = holder.connectivityRepository,
      hasCompletedInitialSync = holder.hasCompletedInitialSync,
      savedState = savedState,
    )

  private fun listSearch(savedState: SavedStateHandle) =
    ListSearchViewModel(
      surveyRepository = holder.surveyRepository,
      placeRepository = holder.placeRepository,
      connectivityRepository = holder.connectivityRepository,
      searchPlaces = holder.searchPlacesUseCase,
      savedState = savedState,
    )

  private fun dataCollection(savedState: SavedStateHandle) =
    DataCollectionViewModel(
      surveyRepository = holder.surveyRepository,
      settingsRepository = holder.settingsRepository,
      authRepository = holder.authRepository,
      locationRepository = holder.locationRepository,
      completeFormSubmission = holder.completeFormSubmissionUseCase,
      launchForm = holder.launchFormUseCase,
      connectivityRepository = holder.connectivityRepository,
      savedState = savedState,
    )
}
