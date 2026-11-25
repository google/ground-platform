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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_FOOTER_TEXT
import org.openforis.collectone.v2.app.ui.common.DEV_TOOLS_FOOTER_BUTTON_TAG
import org.openforis.collectone.v2.app.ui.common.DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION
import org.openforis.collectone.v2.app.ui.common.LocalAppEnvironment
import org.openforis.collectone.v2.app.ui.common.formatAppVersionBadge
import org.openforis.collectone.v2.web.WebConsoleApp
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState

@OptIn(ExperimentalTestApi::class)
class WebDevToolsFooterTest {
  private val expectedVersionLabel = formatAppVersionBadge(AppBuildConfig.APP_VERSION)

  @AfterTest
  fun tearDown() {
    lastOpenedExternalUrlForTest = null
  }

  private fun webStateWithBackendUrl(serviceUrl: String?): WebAppState =
    WebAppState(
      dataHolder =
        AppDataHolder(
          localStore = seededStore(),
          userDataServiceUrl = serviceUrl,
          grpcClient = SeedUserDataService().client(),
          enableSyncEngine = false,
        )
    )

  @Test
  fun webFooterRendersInDevAndStagingAndHidesInProd() =
    runDesktopComposeUiTest(width = 1280, height = 800) {
      val state = webStateWithBackendUrl("https://staging-backend.example.org")
      var environment by mutableStateOf(AppEnvironment.DEV)

      setContent {
        CompositionLocalProvider(LocalAppEnvironment provides environment) {
          WebConsoleApp(
            state = state,
            browserHistory = MemoryBrowserHistory(initialPath = "/surveys"),
          )
        }
      }
      waitForIdle()

      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertExists()
      onNodeWithText("Dev tools").assertDoesNotExist()
      onNodeWithText(expectedVersionLabel).assertExists()
      onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertExists()
      onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertExists()

      runOnIdle { environment = AppEnvironment.STAGING }
      waitForIdle()
      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertExists()
      onNodeWithText(expectedVersionLabel).assertExists()
      onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertExists()

      runOnIdle { environment = AppEnvironment.PROD }
      waitForIdle()
      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()
      onNodeWithText(expectedVersionLabel).assertDoesNotExist()
      onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertDoesNotExist()
      onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()
    }

  @Test
  fun devToolsDialogOpensLinksToConfiguredBackendRoutesAndTogglesDarkMode() =
    runDesktopComposeUiTest(width = 1280, height = 800) {
      val backendUrl = "https://staging-backend.example.org/"
      val state = webStateWithBackendUrl(backendUrl)

      setContent {
        CompositionLocalProvider(LocalAppEnvironment provides AppEnvironment.STAGING) {
          WebConsoleApp(
            state = state,
            browserHistory = MemoryBrowserHistory(initialPath = "/surveys"),
          )
        }
      }
      waitForIdle()

      assertFalse(state.viewModels.settings.uiState.value.isDarkTheme)
      onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).performClick()
      waitForIdle()

      onNodeWithText(WEB_DEV_TOOLS_DIALOG_TITLE).assertExists()
      onNodeWithText("STAGING").assertExists()
      onNodeWithText("https://staging-backend.example.org").assertExists()

      // Toggle light -> dark -> light via the Dev Tools switch.
      onNodeWithTag(WEB_DEV_TOOLS_DARK_MODE_TOGGLE_TAG).assertIsOff().performClick()
      waitForIdle()
      assertTrue(state.viewModels.settings.uiState.value.isDarkTheme)
      onNodeWithTag(WEB_DEV_TOOLS_DARK_MODE_TOGGLE_TAG).assertIsOn()
      onNodeWithText("Dark theme active").assertExists()

      onNodeWithTag(WEB_DEV_TOOLS_DARK_MODE_TOGGLE_TAG).performClick()
      waitForIdle()
      assertFalse(state.viewModels.settings.uiState.value.isDarkTheme)
      onNodeWithTag(WEB_DEV_TOOLS_DARK_MODE_TOGGLE_TAG).assertIsOff()

      // Every active backend debug console route links to the configured backend server URL.
      onNodeWithText("RPC Snapshot (JSON)").assertDoesNotExist()
      val expectedRoutes =
        listOf(
          "RPC Inspector" to "https://staging-backend.example.org/debug",
          "Spanner Inspector" to "https://staging-backend.example.org/debug/spanner",
          "Message Inspector" to "https://staging-backend.example.org/debug/messages",
          "Server Health" to "https://staging-backend.example.org/health",
        )
      for ((title, expectedUrl) in expectedRoutes) {
        lastOpenedExternalUrlForTest = null
        onNodeWithText(title).performScrollTo().performClick()
        waitForIdle()
        assertEquals(expectedUrl, lastOpenedExternalUrlForTest, "Route click for $title")
      }

      onNodeWithText("Close").performClick()
      waitForIdle()
      onNodeWithText(WEB_DEV_TOOLS_DIALOG_TITLE).assertDoesNotExist()
    }

  @Test
  fun devToolsDialogShowsUnconfiguredBackendNoticeWhenNoServiceUrlSet() =
    runDesktopComposeUiTest(width = 1280, height = 800) {
      lastOpenedExternalUrlForTest = null
      val state = webStateWithBackendUrl(null)

      setContent {
        CompositionLocalProvider(LocalAppEnvironment provides AppEnvironment.DEV) {
          WebConsoleApp(
            state = state,
            browserHistory = MemoryBrowserHistory(initialPath = "/surveys"),
          )
        }
      }
      waitForIdle()

      onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).performClick()
      waitForIdle()

      onNodeWithText("No backend server URL configured (USER_DATA_SERVICE_URL)").assertExists()
      onNodeWithText("RPC Inspector").performClick()
      waitForIdle()
      assertNull(lastOpenedExternalUrlForTest)
    }
}
