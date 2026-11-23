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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.Test
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.runMobileNavigationTest

/** Download survey shows a banner while the device is offline, and hides it once it's back. */
@OptIn(ExperimentalTestApi::class)
class DownloadSurveyOfflineBannerTest {
  private val bannerText =
    "You're offline. Connect to the internet to download surveys for offline use."

  @Test
  fun online_showsNoBanner() = runMobileNavigationTest {
    show(MobileRoute.DownloadSurvey)

    it.onNodeWithTag(DOWNLOAD_SURVEY_OFFLINE_BANNER_TAG).assertDoesNotExist()
  }

  @Test
  fun goingOffline_showsTheBanner() = runMobileNavigationTest {
    val state = show(MobileRoute.DownloadSurvey)

    it.runOnIdle { state.updateDeviceConnectivity(isOnline = false) }

    it
      .onNodeWithTag(DOWNLOAD_SURVEY_OFFLINE_BANNER_TAG)
      .assertIsDisplayed()
      .assert(hasText(bannerText))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
  }

  @Test
  fun comingBackOnline_hidesTheBanner() = runMobileNavigationTest {
    val state = show(MobileRoute.DownloadSurvey)
    it.runOnIdle { state.updateDeviceConnectivity(isOnline = false) }

    it.runOnIdle { state.updateDeviceConnectivity(isOnline = true) }

    it.onNodeWithTag(DOWNLOAD_SURVEY_OFFLINE_BANNER_TAG).assertDoesNotExist()
  }
}
