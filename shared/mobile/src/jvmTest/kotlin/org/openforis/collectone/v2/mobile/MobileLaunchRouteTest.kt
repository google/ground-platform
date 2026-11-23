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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * Where the mobile app starts for the session restored at launch, whether a reload may resume at
 * the route the browser history names, and which routes from before the launch it may show again.
 */
class MobileLaunchRouteTest {
  private val profile = AuthProfile(displayName = "Maya Lin", email = "maya@example.org", "")

  private val pastOnboarding = AuthSession(isSignedIn = true, profile, hasAcceptedTerms = true)

  @Test
  fun signedOut_startsOnSignIn() {
    val session = AuthSession(isSignedIn = false, profile)

    assertEquals(
      MobileRoute.SignIn,
      launchRoute(session, isSurveyOpen = true, hasSurveysOnDevice = true),
    )
  }

  @Test
  fun signedInWithoutTheTerms_startsOnTheTerms() {
    val session = AuthSession(isSignedIn = true, profile, hasAcceptedTerms = false)

    assertEquals(
      MobileRoute.TermsOfService,
      launchRoute(session, isSurveyOpen = true, hasSurveysOnDevice = true),
    )
  }

  @Test
  fun withTheSurveyLastOpenedOnTheDevice_startsOnTheMainSurvey() {
    assertEquals(
      MobileRoute.MainSurvey,
      launchRoute(pastOnboarding, isSurveyOpen = true, hasSurveysOnDevice = true),
    )
  }

  @Test
  fun withSurveysOnTheDeviceButNoneOpen_startsOnTheSurveysOnTheDevice() {
    assertEquals(
      MobileRoute.DownloadedSurveys,
      launchRoute(pastOnboarding, isSurveyOpen = false, hasSurveysOnDevice = true),
    )
  }

  @Test
  fun withNoSurveyOnTheDevice_startsOnDownloadSurvey() {
    assertEquals(
      MobileRoute.DownloadSurvey,
      launchRoute(pastOnboarding, isSurveyOpen = false, hasSurveysOnDevice = false),
    )
  }

  @Test
  fun signedInWithTheTermsAccepted_resumesAtTheHistoryRoute() {
    assertTrue(resumesAtHistoryRoute(pastOnboarding))
  }

  @Test
  fun signedInWithoutTheTerms_startsOverInsteadOfResuming() {
    val session = AuthSession(isSignedIn = true, profile, hasAcceptedTerms = false)

    assertFalse(resumesAtHistoryRoute(session))
  }

  @Test
  fun signedOut_startsOverInsteadOfResuming() {
    assertFalse(resumesAtHistoryRoute(AuthSession(isSignedIn = false, profile)))
  }

  @Test
  fun withASurveyOpen_theMainSurveyAndItsPagesMayBeRestored() {
    val launch = MobileLaunch(MobileRoute.DownloadSurvey, isSurveyOpen = { true })

    assertTrue(launch.mayRestore(MobileRoute.MainSurvey))
    assertTrue(launch.mayRestore(MobileRoute.Settings))
    assertTrue(launch.mayRestore(MobileRoute.DataCollection))
  }

  @Test
  fun withoutASurveyOpen_theMainSurveyAndItsPagesAreNotRestored() {
    val launch = MobileLaunch(MobileRoute.DownloadSurvey, isSurveyOpen = { false })

    assertFalse(launch.mayRestore(MobileRoute.MainSurvey))
    assertFalse(launch.mayRestore(MobileRoute.Uploads))
    assertFalse(launch.mayRestore(MobileRoute.DataCollection))
  }

  @Test
  fun withoutASurveyOpen_theSurveyListMayBeRestored() {
    val launch = MobileLaunch(MobileRoute.DownloadSurvey, isSurveyOpen = { false })

    assertTrue(launch.mayRestore(MobileRoute.DownloadSurvey))
  }

  @Test
  fun aSurveyOpenedAfterTheLaunch_letsTheMainSurveyBeRestored() {
    // For example after an Android configuration change, which restores the back stack again.
    var isSurveyOpen = false
    val launch = MobileLaunch(MobileRoute.DownloadSurvey, isSurveyOpen = { isSurveyOpen })

    isSurveyOpen = true

    assertTrue(launch.mayRestore(MobileRoute.MainSurvey))
  }

  @Test
  fun aLaunchChosenByTheHost_restoresEveryRoute() {
    assertTrue(MobileLaunch.at(MobileRoute.SignIn).mayRestore(MobileRoute.MainSurvey))
  }
}
