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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mobile app's back stack in its `NavHost`: what the system Back gesture closes or goes back
 * from (innermost surface first), and how the routes stack up.
 */
@OptIn(ExperimentalTestApi::class)
class MobileBackStackTest {
  @Test
  fun onSignIn_backIsLeftToTheSystem() = runMobileNavigationTest {
    show(MobileRoute.SignIn)

    pressBack()

    assertEquals(1, systemBackCount)
  }

  @Test
  fun onTheMainSurveyMapWithNothingOpen_backIsLeftToTheSystem() = runMobileNavigationTest {
    show()

    pressBack()

    assertEquals(1, systemBackCount)
  }

  @Test
  fun onTheSurveysOnTheDeviceAtLaunch_backIsLeftToTheSystem() = runMobileNavigationTest {
    val state = show(MobileRoute.DownloadedSurveys)

    pressBack()

    assertEquals(1, systemBackCount)
    assertEquals(listOf<MobileRoute>(MobileRoute.DownloadedSurveys), state.navigator.backStack)
  }

  @Test
  fun onTheSurveysOnTheDeviceAtLaunch_thereIsNoBackButton() = runMobileNavigationTest {
    show(MobileRoute.DownloadedSurveys)

    it.onAllNodesWithText("Back").assertCountEquals(0)
  }

  @Test
  fun fromTheSurveysOnTheDeviceAtLaunch_backFromDownloadSurveyReturnsToThem() =
    runMobileNavigationTest {
      val state = show(MobileRoute.DownloadedSurveys)
      it.runOnIdle { state.openDownloadMoreSurveysScreen() }

      pressBack()

      assertEquals(listOf<MobileRoute>(MobileRoute.DownloadedSurveys), state.navigator.backStack)
    }

  @Test
  fun openingASurveyFromTheSurveysOnTheDeviceAtLaunch_showsItsMainSurvey() =
    runMobileNavigationTest {
      val state = show(MobileRoute.DownloadedSurveys)

      it.runOnIdle {
        state.viewModels.onboarding.openSurvey(
          state.viewModels.dashboard.uiState.value.surveys.filter { it.isDownloaded }.first().id
        )
      }
      it.waitForIdle()

      assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), state.navigator.backStack)
    }

  @Test
  fun onTermsOfService_backDeclinesTheTermsAndSignsOut() = runMobileNavigationTest {
    val state = show(MobileRoute.SignIn)
    it.runOnIdle { state.viewModels.onboarding.signInWithGoogle() }

    pressBack()

    assertEquals(listOf<MobileRoute>(MobileRoute.SignIn), state.navigator.backStack)
    assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
    assertEquals(0, systemBackCount)
  }

  @Test
  fun onDownloadSurveyAfterTerms_backAsksBeforeSigningOut() = runMobileNavigationTest {
    val state = show(MobileRoute.SignIn)
    it.runOnIdle {
      state.viewModels.onboarding.signInWithGoogle()
      state.viewModels.onboarding.acceptTermsOfService()
    }

    pressBack()

    assertEquals(MobileRoute.DownloadSurvey, state.currentRoute)
    assertTrue(state.viewModels.onboarding.uiState.value.isSignOutPromptOpen)
  }

  @Test
  fun onDownloadSurveyFromTheSurveyList_backReturnsToTheList() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle {
      state.drawerSwitchSurveys()
      state.openDownloadMoreSurveysScreen()
    }
    assertEquals(
      listOf(MobileRoute.MainSurvey, MobileRoute.DownloadedSurveys, MobileRoute.DownloadSurvey),
      state.navigator.backStack,
    )

    pressBack()

    assertEquals(
      listOf(MobileRoute.MainSurvey, MobileRoute.DownloadedSurveys),
      state.navigator.backStack,
    )
  }

  @Test
  fun onADrawerPage_backReturnsToTheMap() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.drawerOpenSettings() }
    assertEquals(listOf(MobileRoute.MainSurvey, MobileRoute.Settings), state.navigator.backStack)

    pressBack()

    assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), state.navigator.backStack)
    pressBack()
    assertEquals(1, systemBackCount)
  }

  @Test
  fun withTheDrawerOpen_backClosesIt() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.updateDrawerOpen(true) }

    pressBack()

    assertFalse(state.isDrawerOpen)
    assertEquals(0, systemBackCount)
  }

  @Test
  fun withAMapFeatureSelected_backClearsTheSelection() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.viewModels.surveyMap.selectEntity(NYR_104) }

    pressBack()

    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertEquals(0, systemBackCount)
  }

  @Test
  fun withASubmissionOpen_backClosesTheSubmissionBeforeItsMapFeature() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.viewModels.dataCollection.selectSubmissionDetail(SAPLING_SUBMISSION) }
    val entityId = state.viewModels.dataCollection.uiState.value.selectedEntityId

    pressBack()

    assertNull(state.viewModels.dataCollection.uiState.value.selectedSubmissionId)
    assertEquals(entityId, state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun openingAForm_showsItOverTheMainSurvey() = runMobileNavigationTest {
    val state = show()

    it.runOnIdle {
      state.viewModels.dataCollection.launchFormFromFab(
        state.viewModels.dataCollection.uiState.value.mobileForms.first().id
      )
    }
    it.waitForIdle()

    assertEquals(
      listOf(MobileRoute.MainSurvey, MobileRoute.DataCollection),
      state.navigator.backStack,
    )
  }

  @Test
  fun withADataCollectionFormOpen_backClosesTheForm() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle {
      state.viewModels.dataCollection.launchFormFromFab(
        state.viewModels.dataCollection.uiState.value.mobileForms.first().id
      )
    }
    it.waitForIdle()

    pressBack()

    assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), state.navigator.backStack)
  }

  @Test
  fun poppingTheFormRoute_closesTheForm() = runMobileNavigationTest {
    // As the browser's Back button does on mobile web: the back stack pops without the app's back
    // handlers.
    val state = show()
    it.runOnIdle {
      state.viewModels.dataCollection.launchFormFromFab(
        state.viewModels.dataCollection.uiState.value.mobileForms.first().id
      )
    }
    it.waitForIdle()

    it.runOnIdle { navController.popBackStack() }
    it.waitForIdle()

    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
    assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
  }

  @Test
  fun aFormRouteWithoutAnOpenForm_returnsToTheMainSurvey() = runMobileNavigationTest {
    val state = show()

    it.runOnIdle { navController.navigate(MobileRoute.DataCollection) }
    it.waitForIdle()

    assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), state.navigator.backStack)
  }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val SAPLING_SUBMISSION = "00000026-cca2-5594-b83f-02266f40048e"
  }
}
