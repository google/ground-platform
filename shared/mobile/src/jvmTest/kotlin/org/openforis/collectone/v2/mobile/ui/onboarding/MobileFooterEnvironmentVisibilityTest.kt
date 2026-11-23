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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.ui.common.DEMO_ATTRIBUTION_FOOTER_TEXT
import org.openforis.collectone.v2.app.ui.common.DEV_TOOLS_FOOTER_BUTTON_TAG
import org.openforis.collectone.v2.app.ui.common.DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION
import org.openforis.collectone.v2.app.ui.common.LocalAppEnvironment
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.mobileSampleDataHolder
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

@OptIn(ExperimentalTestApi::class)
class MobileFooterEnvironmentVisibilityTest {
  @Test
  fun mobileAppShowsAttributionFooterInDevAndStagingAndHidesItInProd() = runComposeUiTest {
    val dataHolder = mobileSampleDataHolder()
    var environment by mutableStateOf(AppEnvironment.DEV)

    setContent {
      CompositionLocalProvider(LocalAppEnvironment provides environment) {
        MobileAppSession(dataHolder, MobileRoute.SignIn) { state -> MobileApp(state) }
      }
    }
    waitForIdle()

    onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertExists()
    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()
    onNodeWithContentDescription(DEV_TOOLS_FOOTER_CONTENT_DESCRIPTION).assertDoesNotExist()

    runOnIdle { environment = AppEnvironment.STAGING }
    waitForIdle()
    onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertExists()
    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()

    runOnIdle { environment = AppEnvironment.PROD }
    waitForIdle()
    onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()
    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()
  }
}
