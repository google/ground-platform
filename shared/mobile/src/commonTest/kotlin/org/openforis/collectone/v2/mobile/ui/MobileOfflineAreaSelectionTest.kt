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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.client.offline.SimulatedOfflineRegionClient
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * The area selection mode in the mobile app shell: it opens on the main map (closing the drawer and
 * any drawer page, so the map shows), closes back to where it started, and doesn't open on devices
 * that can't save map areas.
 */
class MobileOfflineAreaSelectionTest {
  private fun simulatedState(initialRoute: MobileRoute = MobileRoute.MainSurvey) =
    MobileAppState(
      initialRoute = initialRoute,
      dataHolder = sampleDataHolder(offlineRegionClient = { SimulatedOfflineRegionClient(it) }),
    )

  private val MobileAppState.isSelectingArea: Boolean
    get() = offlineAreaSelection.uiState.value.isActive

  @Test
  fun start_fromTheDrawer_closesItAndShowsTheMap() {
    val state = simulatedState()
    state.updateDrawerOpen(true)

    state.startOfflineAreaSelection()

    assertTrue(state.isSelectingArea)
    assertFalse(state.isDrawerOpen)
    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
    assertTrue(state.isMapShowing())
    assertTrue(state.viewModels.surveyMap.uiState.value.isSelectingOfflineArea)
  }

  @Test
  fun start_fromTheLayersSheet_closesIt() {
    val state = simulatedState()
    state.viewModels.surveyMap.updateLayersSheetOpen(true)

    state.startOfflineAreaSelection()

    assertTrue(state.isSelectingArea)
    assertFalse(state.viewModels.surveyMap.uiState.value.isLayersSheetOpen)
  }

  @Test
  fun close_returnsToTheMap_withTheControlsBack() {
    val state = simulatedState()
    state.startOfflineAreaSelection()

    state.closeOfflineAreaSelection()

    assertFalse(state.isSelectingArea)
    assertFalse(state.viewModels.surveyMap.uiState.value.isSelectingOfflineArea)
    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
  }

  @Test
  fun startedFromOfflineMaps_showsTheMap_andClosesBackToOfflineMaps() {
    val state = simulatedState()
    state.drawerManageOfflineMaps()
    assertEquals(MobileRoute.OfflineMaps, state.currentRoute)

    state.startOfflineAreaSelection()
    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
    assertTrue(state.isMapShowing())

    state.closeOfflineAreaSelection()
    assertEquals(MobileRoute.OfflineMaps, state.currentRoute)
  }

  @Test
  fun leavingTheMap_endsTheMode() {
    val state = simulatedState()
    state.startOfflineAreaSelection()

    state.drawerOpenSettings()

    assertFalse(state.isSelectingArea)
    assertFalse(state.viewModels.surveyMap.uiState.value.isSelectingOfflineArea)
  }

  @Test
  fun aDeviceThatCantSaveAreas_hidesTheWaysIn() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.startOfflineAreaSelection()

    assertFalse(state.isSelectingArea)
    assertFalse(state.viewModels.surveyMap.uiState.value.canSaveMapAreas)
  }

  @Test
  fun showOnMap_framesTheAreaOnTheMainMap() {
    val state = simulatedState()
    state.drawerManageOfflineMaps()
    val bounds = LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40)

    state.showOfflineAreaOnMap(bounds)

    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
    assertEquals(bounds, state.viewModels.surveyMap.uiState.value.framedBounds)
  }
}
