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
package org.openforis.collectone.v2.mobile.ui.drawer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_FOOTER_TEXT
import org.openforis.collectone.v2.app.ui.common.DEV_TOOLS_FOOTER_BUTTON_TAG
import org.openforis.collectone.v2.app.ui.common.LocalAppEnvironment
import org.openforis.collectone.v2.app.ui.common.formatAppVersionBadge
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.mobileSampleDataHolder
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.runMobileNavigationTest

/**
 * Where the mobile app shows its version and attribution: in the navigation drawer's About footer,
 * not in a bar under every screen.
 */
@OptIn(ExperimentalTestApi::class)
class MobileDrawerAboutTest {
  @Test
  fun mobileApp_hasNoAttributionBarUnderItsScreensInAnyEnvironment() = runComposeUiTest {
    val dataHolder = mobileSampleDataHolder()
    var environment by mutableStateOf(AppEnvironment.DEV)

    setContent {
      CompositionLocalProvider(LocalAppEnvironment provides environment) {
        MobileAppSession(dataHolder, MobileRoute.SignIn) { state -> MobileApp(state) }
      }
    }

    for (next in AppEnvironment.entries) {
      runOnIdle { environment = next }
      waitForIdle()
      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()
      onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()
    }
  }

  @Test
  fun drawer_showsTheVersionAndTheContributorsWithoutATagline() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.updateDrawerOpen(true) }
    it.waitForIdle()

    it.onNodeWithTag(DRAWER_ABOUT_FOOTER_TAG).assertIsDisplayed()
    val version = formatAppVersionBadge(AppBuildConfig.APP_VERSION)
    it.onNodeWithText("Open Foris Collect One $version").assertIsDisplayed()
    it.onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertIsDisplayed()
    it.onNodeWithText("Offline-First Core", substring = true).assertDoesNotExist()
  }
}
