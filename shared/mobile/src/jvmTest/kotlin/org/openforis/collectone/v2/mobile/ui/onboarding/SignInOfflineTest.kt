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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.runMobileNavigationTest

/** Sign in shows an internet-required message while offline, and clears it once back online. */
@OptIn(ExperimentalTestApi::class)
class SignInOfflineTest {
  private val offlineText = "You're offline. An internet connection is required to sign in."

  @Test
  fun online_showsNoNotice() = runMobileNavigationTest {
    show(MobileRoute.SignIn)

    it.onNodeWithTag(SIGN_IN_NOTICE_TAG).assertDoesNotExist()
  }

  @Test
  fun goingOffline_showsSignInOfflineMessageWithoutPlacesApiText() = runMobileNavigationTest {
    val state = show(MobileRoute.SignIn)

    it.runOnIdle { state.updateDeviceConnectivity(isOnline = false) }
    it.waitForIdle()

    it
      .onNodeWithTag(SIGN_IN_NOTICE_TAG)
      .assertIsDisplayed()
      .assert(hasText(offlineText))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    it.onNodeWithText("Places", substring = true).assertDoesNotExist()
  }

  @Test
  fun comingBackOnline_clearsOfflineMessageCleanly() = runMobileNavigationTest {
    val state = show(MobileRoute.SignIn)
    it.runOnIdle { state.updateDeviceConnectivity(isOnline = false) }
    it.waitForIdle()
    it.onNodeWithTag(SIGN_IN_NOTICE_TAG).assertIsDisplayed()

    it.runOnIdle { state.updateDeviceConnectivity(isOnline = true) }
    it.waitForIdle()

    it.onNodeWithTag(SIGN_IN_NOTICE_TAG).assertDoesNotExist()
    it.onNodeWithText("Places", substring = true).assertDoesNotExist()
  }

  @Test
  fun tappingSignInOffline_thenComingBackOnline_clearsNoticeCleanly() = runMobileNavigationTest {
    val state = show(MobileRoute.SignIn)
    it.runOnIdle { state.updateDeviceConnectivity(isOnline = false) }
    it.waitForIdle()

    it.onNodeWithText("Sign in with Google").performClick()
    it.waitForIdle()
    it.onNodeWithTag(SIGN_IN_NOTICE_TAG).assertIsDisplayed().assert(hasText(offlineText))

    it.runOnIdle { state.updateDeviceConnectivity(isOnline = true) }
    it.waitForIdle()

    it.onNodeWithTag(SIGN_IN_NOTICE_TAG).assertDoesNotExist()
    it.onNodeWithText("Places", substring = true).assertDoesNotExist()
  }
}
