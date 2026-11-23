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
import org.openforis.collectone.v2.app.client.offline.SimulatedOfflineRegionClient
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * The shell's own state: switching the Main Survey between map and list, and resetting the shell
 * when the workbench resets the store.
 */
class MobileAppStateShellTest {
  private fun state(initialRoute: MobileRoute = MobileRoute.MainSurvey) =
    MobileAppState(
      initialRoute = initialRoute,
      dataHolder = sampleDataHolder(offlineRegionClient = { SimulatedOfflineRegionClient(it) }),
    )

  @Test
  fun mapMode_fromADrawerPage_returnsToTheMap_andCollapsesTheSheet() {
    val state = state()
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
    state.drawerOpenSettings()

    state.setMainSurveyViewMode(MainSurveyViewMode.MAP)

    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun reset_returnsToSignIn_withTheShellAtItsDefaults() {
    val state = state()
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
    state.updateUserProfileOpen(true)
    state.drawerManageOfflineMaps()
    state.startOfflineAreaSelection()

    state.reset()

    assertEquals(MobileRoute.SignIn, state.currentRoute)
    assertEquals(listOf<MobileRoute>(MobileRoute.SignIn), state.navigator.backStack)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertFalse(state.isDrawerOpen)
    assertFalse(state.isUserProfileOpen)
    assertFalse(state.offlineAreaSelection.uiState.value.isActive)
    assertFalse(state.viewModels.surveyMap.uiState.value.isSelectingOfflineArea)
  }

  @Test
  fun closingTheAreaSelection_afterAReset_staysOnSignIn() {
    // The reset forgets that the mode was started from Offline maps.
    val state = state()
    state.drawerManageOfflineMaps()
    state.startOfflineAreaSelection()
    state.reset()

    state.closeOfflineAreaSelection()

    assertEquals(MobileRoute.SignIn, state.currentRoute)
  }
}
