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

import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.appScreen

/**
 * How the mobile app launches: the route it starts at ([startRoute]), and which routes shown before
 * the launch it may show again ([mayRestore]): the route the browser history names on a reload, or
 * the back stack Android restores after process death or a configuration change.
 */
internal class MobileLaunch(
  val startRoute: MobileRoute,
  /** Whether the user has a survey open, reopened at launch or opened since. */
  private val isSurveyOpen: () -> Boolean,
) {
  /**
   * Whether [route] may be shown again. The Main Survey, its pages, and its form show the active
   * survey, so they may only while the user has a survey open; otherwise they would show another
   * survey, and the app starts over at [startRoute] instead.
   */
  fun mayRestore(route: MobileRoute): Boolean =
    route.appScreen != AppScreen.MAIN_SURVEY || isSurveyOpen()

  companion object {
    /**
     * A launch at [route], chosen by the host (tests and previews), restoring whatever it saved.
     */
    fun at(route: MobileRoute) = MobileLaunch(route, isSurveyOpen = { true })
  }
}

/**
 * Where the mobile app starts for [session], decided from what is on the device alone, so the first
 * screen never waits for the network:
 * - Sign-in while signed out, then the Terms of Service until the user accepts them.
 * - The Main Survey when [isSurveyOpen]: the survey the user last opened is still on the device.
 * - Otherwise the surveys on the device, when [hasSurveysOnDevice].
 * - Otherwise Download survey, to download one.
 */
internal fun launchRoute(
  session: AuthSession,
  isSurveyOpen: Boolean,
  hasSurveysOnDevice: Boolean,
): MobileRoute =
  when {
    !session.isSignedIn -> MobileRoute.SignIn
    !session.hasAcceptedTerms -> MobileRoute.TermsOfService
    isSurveyOpen -> MobileRoute.MainSurvey
    hasSurveysOnDevice -> MobileRoute.DownloadedSurveys
    else -> MobileRoute.DownloadSurvey
  }

/**
 * Whether the mobile app may resume at the route the browser history names (on a reload) for the
 * [session] restored at launch: only past onboarding, signed in with the Terms of Service accepted,
 * so a reload can't skip signing in or the terms.
 */
fun resumesAtHistoryRoute(session: AuthSession): Boolean =
  session.isSignedIn && session.hasAcceptedTerms
