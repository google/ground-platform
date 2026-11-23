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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository

/** The local gazetteer the survey area picker searches, with and without a live geocoder. */
class SurveyEditorLocalPlacesTest {
  private val store = seededStore()
  private val seededPlaces = runNow { PlaceRepositoryImpl(store).getLocalPlaces() }

  @Test
  fun withoutALiveGeocoder_thePickerSearchesTheLocalGazetteer() {
    val viewModel = viewModel(store, PlacesWithGeocoder(store, hasLiveGeocoder = false))

    assertTrue(seededPlaces.isNotEmpty())
    assertEquals(seededPlaces, viewModel.ui.localPlaces)
  }

  @Test
  fun withALiveGeocoder_thePickerLeavesTheLocalGazetteerOut() {
    val viewModel = viewModel(store, PlacesWithGeocoder(store, hasLiveGeocoder = true))

    assertEquals(emptyList(), viewModel.ui.localPlaces)
  }

  /** The store's places, reporting [hasLiveGeocoder] instead of the platform's configuration. */
  private class PlacesWithGeocoder(store: LocalStore, override val hasLiveGeocoder: Boolean) :
    PlaceRepository by PlaceRepositoryImpl(store)

  private fun viewModel(store: LocalStore, places: PlaceRepository) =
    SurveyEditorViewModel(
      surveyRepository = SurveyRepositoryImpl(store),
      surveyEditorRepository = SurveyEditorRepositoryImpl(store),
      organizationRepository = OrganizationRepositoryImpl(store),
      authRepository = AuthRepositoryImpl(),
      placeRepository = places,
    )
}
