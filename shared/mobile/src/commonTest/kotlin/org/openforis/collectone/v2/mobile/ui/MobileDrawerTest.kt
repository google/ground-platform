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
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** The navigation drawer: its actions, sub-views, and whether the basemap shows behind them. */
class MobileDrawerTest {
  @Test
  fun updateDrawerOpen_opensTheDrawer() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.updateDrawerOpen(true)

    assertTrue(state.isDrawerOpen)
  }

  @Test
  fun drawerManageOfflineMaps_closesTheDrawerOnTheOfflineMapsSubView() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.updateDrawerOpen(true)

    state.drawerManageOfflineMaps()

    assertFalse(state.isDrawerOpen)
    assertEquals(MobileRoute.OfflineMaps, state.currentRoute)
  }

  @Test
  fun drawerOpenSettings_closesTheDrawerOnTheSettingsSubView() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.updateDrawerOpen(true)

    state.drawerOpenSettings()

    assertFalse(state.isDrawerOpen)
    assertEquals(MobileRoute.Settings, state.currentRoute)
  }

  @Test
  fun drawerSwitchSurveys_opensTheSurveysSubView() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.drawerSwitchSurveys()

    assertEquals(MobileRoute.DownloadedSurveys, state.currentRoute)
  }

  @Test
  fun downloadedSurveys_listOnlyTheSurveysOnTheDevice() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    assertEquals(
      6,
      state.viewModels.dashboard.uiState.value.surveys.filter { it.isDownloaded }.size,
    )
    assertTrue(
      state.viewModels.dashboard.uiState.value.surveys
        .filter { it.isDownloaded }
        .all { it.isDownloaded }
    )
  }

  @Test
  fun openDownloadMoreSurveysScreen_leavesTheSurveysSubViewForDownloadSurvey() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.drawerSwitchSurveys()

    state.openDownloadMoreSurveysScreen()

    assertEquals(MobileRoute.DownloadSurvey, state.currentRoute)
  }

  @Test
  fun drawerViewTermsOfService_showsTheTermsOfServiceScreen() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.updateDrawerOpen(true)

    state.drawerViewTermsOfService()

    assertFalse(state.isDrawerOpen)
    assertEquals(MobileRoute.TermsOfService, state.currentRoute)
  }

  @Test
  fun drawerSignOut_signsOutToTheSignInScreen() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    state.viewModels.onboarding.signInWithGoogle()
    state.viewModels.onboarding.acceptTermsOfService()
    state.viewModels.onboarding.openSurvey("00000033-6b50-51cd-ac94-baea2ea06d65")
    assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)

    state.signOut()

    assertEquals(MobileRoute.SignIn, state.currentRoute)
    assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
  }

  @Test
  fun isMapShowing_falseBeforeTheMainSurveyScreen() {
    val state = MobileAppState(initialRoute = MobileRoute.SignIn, dataHolder = sampleDataHolder())

    assertFalse(state.isMapShowing())
  }

  @Test
  fun isMapShowing_trueOnTheMainSurveyMap() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    assertTrue(state.isMapShowing())
  }

  @Test
  fun isMapShowing_falseWhileADrawerSubViewCoversTheMap() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.drawerOpenSettings()

    assertFalse(state.isMapShowing())
  }

  @Test
  fun isMapShowing_trueAgainOnceTheDrawerSubViewCloses() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.drawerOpenUploads()

    state.closeDrawerSubView()

    assertTrue(state.isMapShowing())
  }

  @Test
  fun isMapShowing_staysTrueWhileAFormShowsItsEntityRefMapStep() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    // The first sample form starts on an entityref step whose picker shows the map.
    state.viewModels.dataCollection.launchFormFromFab(
      state.viewModels.dataCollection.uiState.value.mobileForms.first().id
    )

    assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertTrue(state.isMapShowing())
  }

  @Test
  fun isMainMapScreenShowing_falseOnTheOnboardingScreens() {
    val onboardingRoutes =
      listOf(MobileRoute.SignIn, MobileRoute.TermsOfService, MobileRoute.DownloadSurvey)

    for (route in onboardingRoutes) {
      val state = MobileAppState(initialRoute = route, dataHolder = sampleDataHolder())
      assertFalse(state.isMainMapScreenShowing(), "on $route")
    }
  }

  @Test
  fun isMainMapScreenShowing_trueOnTheMainSurveyMap() {
    val state = MobileAppState(initialRoute = MobileRoute.SignIn, dataHolder = sampleDataHolder())

    state.viewModels.onboarding.openSurvey("00000033-6b50-51cd-ac94-baea2ea06d65")

    assertTrue(state.isMainMapScreenShowing())
  }

  @Test
  fun isMainMapScreenShowing_falseWhileAnyDrawerSubViewIsOpen() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    val openSubViews =
      listOf(state::drawerSwitchSurveys, state::drawerManageOfflineMaps, state::drawerOpenSettings)

    for (openSubView in openSubViews) {
      openSubView()
      assertFalse(state.isMainMapScreenShowing(), "while ${state.currentRoute} is open")
      state.closeDrawerSubView()
    }
  }

  @Test
  fun isMainMapScreenShowing_trueAgainOnceTheDrawerSubViewCloses() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.drawerManageOfflineMaps()

    state.closeDrawerSubView()

    assertTrue(state.isMainMapScreenShowing())
  }

  @Test
  fun isMainMapScreenShowing_falseWhileADataCollectionFormIsOpen() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

    state.viewModels.dataCollection.launchFormFromFab(
      state.viewModels.dataCollection.uiState.value.mobileForms.first().id
    )

    assertFalse(state.isMainMapScreenShowing())
  }

  @Test
  fun isMainMapScreenShowing_trueAgainOnceTheFormCloses() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.dataCollection.launchFormFromFab(
      state.viewModels.dataCollection.uiState.value.mobileForms.first().id
    )

    state.viewModels.dataCollection.closeActiveFormRunner()

    assertTrue(state.isMainMapScreenShowing())
  }

  @Test
  fun returnToSignInIfSignedOut_leavesTheSurveyOfASignedOutUser() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.drawerOpenSettings()

    state.returnToSignInIfSignedOut()

    assertEquals(listOf<MobileRoute>(MobileRoute.SignIn), state.navigator.backStack)
  }

  @Test
  fun returnToSignInIfSignedOut_keepsASignedInUserOnTheSurvey() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    state.viewModels.onboarding.signInWithGoogle()
    state.viewModels.onboarding.acceptTermsOfService()
    state.viewModels.onboarding.openSurvey("00000033-6b50-51cd-ac94-baea2ea06d65")

    state.returnToSignInIfSignedOut()

    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
  }
}
