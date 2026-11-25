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
package org.openforis.collectone.v2.mobile.ui.drawer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.ui.common.ABOUT_CONTENT_TAG
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_FOOTER_TEXT
import org.openforis.collectone.v2.app.ui.common.DEV_TOOLS_FOOTER_BUTTON_TAG
import org.openforis.collectone.v2.app.ui.common.LocalAppEnvironment
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.mobileSampleDataHolder
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.runMobileNavigationTest

/**
 * Where the mobile app shows its version and attribution: on the About screen the navigation drawer
 * opens, not in the drawer itself or in a bar under every screen.
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
  fun drawer_hasAnAboutItemInsteadOfTheVersionAndTheContributors() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.updateDrawerOpen(true) }
    it.waitForIdle()

    it.onNodeWithText("About").assertIsDisplayed()
    it.onNodeWithText(AppBuildConfig.APP_VERSION, substring = true).assertDoesNotExist()
    it.onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()
  }

  @Test
  fun aboutItem_opensTheAboutScreen_andBackReturnsToTheSurvey() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.updateDrawerOpen(true) }
    it.waitForIdle()

    it.onNodeWithText("About").performClick()
    it.waitForIdle()

    assertFalse(state.isDrawerOpen)
    assertEquals(listOf(MobileRoute.MainSurvey, MobileRoute.About), state.navigator.backStack)
    it.onNodeWithTag(ABOUT_CONTENT_TAG).assertIsDisplayed()

    pressBack()

    assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), state.navigator.backStack)
    it.onNodeWithTag(ABOUT_CONTENT_TAG).assertDoesNotExist()
    assertEquals(0, systemBackCount)
  }

  @Test
  fun aboutScreen_backArrowReturnsToTheSurvey() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.drawerOpenAbout() }
    it.waitForIdle()

    it.onNodeWithContentDescription("Back").performClick()
    it.waitForIdle()

    assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), state.navigator.backStack)
  }

  @Test
  fun aboutScreen_showsTheNameTheVersionThePurposeAndTheContributors() = runMobileNavigationTest {
    val state = show()
    it.runOnIdle { state.drawerOpenAbout() }
    it.waitForIdle()

    it.onNodeWithText("Open Foris Collect One").assertIsDisplayed()
    it.onNodeWithText("Version ${AppBuildConfig.APP_VERSION.trim()}").assertIsDisplayed()
    it.onNodeWithText("Collect One is an open-source tool", substring = true).assertIsDisplayed()
    it.onNodeWithText("Our mission is", substring = true).assertIsDisplayed()
    it.onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertIsDisplayed()
  }
}
