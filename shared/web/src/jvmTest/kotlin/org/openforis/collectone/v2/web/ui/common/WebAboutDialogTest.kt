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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.ui.common.ABOUT_CONTENT_TAG
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_FOOTER_TEXT
import org.openforis.collectone.v2.app.ui.common.LocalAppEnvironment
import org.openforis.collectone.v2.web.WebConsoleApp
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState

/**
 * The web console's About dialog: opened from the account menu, in production builds too, with the
 * product name, version, purpose, and contributors that used to be in the footer.
 */
@OptIn(ExperimentalTestApi::class)
class WebAboutDialogTest {
  private fun signedInState(): WebAppState =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
    }

  @Test
  fun accountMenuAboutItemOpensTheAboutDialogAndCloseDismissesIt() =
    runDesktopComposeUiTest(width = 1280, height = 800) {
      val state = signedInState()
      setContent {
        CompositionLocalProvider(LocalAppEnvironment provides AppEnvironment.PROD) {
          WebConsoleApp(
            state = state,
            browserHistory = MemoryBrowserHistory(initialPath = "/surveys"),
          )
        }
      }
      waitForIdle()
      onNodeWithTag(WEB_ABOUT_DIALOG_TAG).assertDoesNotExist()
      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()

      onNode(hasContentDescription("Account menu", substring = true)).performClick()
      waitForIdle()
      onNodeWithText("About").performClick()
      waitForIdle()

      onNodeWithTag(WEB_ABOUT_DIALOG_TAG).assertExists()
      onNodeWithTag(ABOUT_CONTENT_TAG).assertExists()
      onNodeWithText("Open Foris Collect One").assertExists()
      onNodeWithText("Version ${AppBuildConfig.APP_VERSION.trim()}").assertExists()
      onNodeWithText("Collect One is an open-source tool", substring = true).assertExists()
      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertExists()

      onNodeWithText("Close").performClick()
      waitForIdle()

      onNodeWithTag(WEB_ABOUT_DIALOG_TAG).assertDoesNotExist()
      onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()
    }
}
