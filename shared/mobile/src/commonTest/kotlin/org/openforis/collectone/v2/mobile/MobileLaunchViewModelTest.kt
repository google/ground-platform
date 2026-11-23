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
package org.openforis.collectone.v2.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemoryActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * [MobileLaunchViewModel] decides where the mobile app starts from the data on the device, as soon
 * as the session is restored and the signed-in user's stored data is in the local store.
 */
class MobileLaunchViewModelTest {
  private val profile = AuthProfile(displayName = "Maya Lin", email = "maya@example.org", "")
  private val signedOut = AuthSession(isSignedIn = false, profile)
  private val pastOnboarding = AuthSession(isSignedIn = true, profile, hasAcceptedTerms = true)

  private val restoredSession = MutableStateFlow<AuthSession?>(null)
  private val hasLoadedLocalData = MutableStateFlow(false)
  private val storedActiveSurvey = InMemoryActiveSurveyStore()
  private val surveyRepository = SurveyRepositoryImpl(InMemoryLocalStore(), storedActiveSurvey)

  private fun launchViewModel() =
    MobileLaunchViewModel(restoredSession, hasLoadedLocalData, surveyRepository)

  /** Stores survey [id] on the device as the initial sync does, downloaded or only listed. */
  private fun storeSurvey(id: String, isDownloaded: Boolean) = runNow {
    surveyRepository.setSurveys(
      listOf(
        SurveyPreviewItem(
          id = id,
          title = "Survey $id",
          description = "",
          location = "",
          isDownloaded = isDownloaded,
          entityCount = 0,
        )
      )
    )
  }

  /** Reopens the survey last opened on this device, as the initial sync does from local data. */
  private fun reopenSurveyLastOpened(id: String) = runNow {
    storedActiveSurvey.save(id)
    surveyRepository.restoreActiveSurvey()
  }

  /** Restores [session] with the user's stored data in the local store. */
  private fun restore(session: AuthSession) {
    restoredSession.value = session
    hasLoadedLocalData.value = true
  }

  @Test
  fun beforeTheSessionIsRestored_theLaunchIsNotKnown() {
    hasLoadedLocalData.value = true

    assertNull(launchViewModel().launch.value)
  }

  @Test
  fun signedOut_startsOnSignIn() {
    restore(signedOut)

    assertEquals(MobileRoute.SignIn, launchViewModel().launch.value?.startRoute)
  }

  @Test
  fun aSessionRestoredSignedOutThenSignedIn_startsFromTheSessionOnceTheDataIsLoaded() {
    // The restored session can trail the account the auth client restored.
    restoredSession.value = signedOut
    val viewModel = launchViewModel()

    restore(pastOnboarding)

    assertEquals(MobileRoute.DownloadSurvey, viewModel.launch.value?.startRoute)
  }

  @Test
  fun pastOnboarding_waitsForTheStoredDataBeforeChoosing() {
    storeSurvey("survey-1", isDownloaded = true)
    restoredSession.value = pastOnboarding
    val viewModel = launchViewModel()
    assertNull(viewModel.launch.value)

    hasLoadedLocalData.value = true

    assertEquals(MobileRoute.DownloadedSurveys, viewModel.launch.value?.startRoute)
  }

  @Test
  fun withTheSurveyLastOpenedOnTheDevice_startsOnTheMainSurvey() {
    storeSurvey("survey-1", isDownloaded = true)
    reopenSurveyLastOpened("survey-1")

    restore(pastOnboarding)

    assertEquals(MobileRoute.MainSurvey, launchViewModel().launch.value?.startRoute)
  }

  @Test
  fun withSurveysOnTheDeviceButNoneOpen_startsOnTheSurveysOnTheDevice() {
    storeSurvey("survey-1", isDownloaded = true)

    restore(pastOnboarding)

    assertEquals(MobileRoute.DownloadedSurveys, launchViewModel().launch.value?.startRoute)
  }

  @Test
  fun withSurveysOnlyListedNotDownloaded_startsOnDownloadSurvey() {
    storeSurvey("survey-1", isDownloaded = false)

    restore(pastOnboarding)

    assertEquals(MobileRoute.DownloadSurvey, launchViewModel().launch.value?.startRoute)
  }

  @Test
  fun withNothingOnTheDevice_startsOnDownloadSurvey() {
    restore(pastOnboarding)

    assertEquals(MobileRoute.DownloadSurvey, launchViewModel().launch.value?.startRoute)
  }

  @Test
  fun aSurveyOpenedAfterTheLaunch_keepsTheStartRouteButLetsTheMainSurveyBeRestored() {
    storeSurvey("survey-1", isDownloaded = true)
    restore(pastOnboarding)
    val launch = checkNotNull(launchViewModel().launch.value)

    runNow { surveyRepository.setActiveSurveyId("survey-1") }

    assertEquals(MobileRoute.DownloadedSurveys, launch.startRoute)
    assertTrue(launch.mayRestore(MobileRoute.MainSurvey))
  }
}
