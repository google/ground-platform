/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.app.ui.map

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LocationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SettingsRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrlRepository
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher

/** A map feature of the seeded sample survey, on the `layer-coffee-parcels` layer. */
internal const val SEEDED_ENTITY_ID = "00000007-640e-5ed7-9874-4d6b6717f21d"

/** A local place of the seeded sample survey. */
internal const val SEEDED_PLACE_ID = "place-karima-forest"

/** The "All users" organization's Earth Engine imagery source in the seeded data. */
internal const val SEEDED_EE_SOURCE_ID = "imagery-all-users-ee-s2-true-color"

/** The current [SurveyMapData] snapshot of [store] (the sample data by default). */
internal fun seededMapData(store: InMemoryLocalStore = seededStore()): SurveyMapData = runNow {
  observeSurveyMapData(
      surveyRepository = SurveyRepositoryImpl(store),
      organizationRepository = OrganizationRepositoryImpl(store),
      settingsRepository = SettingsRepositoryImpl(store),
      placeRepository = PlaceRepositoryImpl(store),
      connectivityRepository = ConnectivityRepository(),
      offlineMapRepository = null,
    )
    .first()
}

/**
 * A [SurveyMapViewModel] on the sample data, with the repositories the tests inspect.
 *
 * Built outside `runNow` on purpose: the ViewModel's immediate dispatcher runs nested coroutines
 * only after the current one yields, so a fixture built inside one would not see its own updates.
 */
internal class MapViewModelFixture(
  offlineMapRepository: OfflineMapRepository? = null,
  overlayTileUrls: OverlayTileUrlRepository? = null,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
) {
  val store = seededStore()
  val surveyRepository = SurveyRepositoryImpl(store)
  val locationRepository = LocationRepositoryImpl()
  val viewModel =
    SurveyMapViewModel(
      surveyRepository = surveyRepository,
      organizationRepository = OrganizationRepositoryImpl(store),
      settingsRepository = SettingsRepositoryImpl(store),
      locationRepository = locationRepository,
      placeRepository = PlaceRepositoryImpl(store),
      dispatcher = dispatcher,
      connectivityRepository = ConnectivityRepository(),
      clusterMapFeatures = ClusterMapFeaturesUseCase(),
      computeWayfindingNavigation = ComputeWayfindingNavigationUseCase(),
      overlayTileUrls = overlayTileUrls,
      offlineMapRepository = offlineMapRepository,
    )

  val uiState: SurveyMapUiState
    get() = viewModel.uiState.value
}
