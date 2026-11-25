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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.model.AppEnvironment

@OptIn(ExperimentalTestApi::class)
class DemoBrandingTest {
  @Test
  fun formatAppVersionBadgeAddsSinglePrefixAndTrimsBlank() {
    assertEquals("v2.0.0-SNAPSHOT", formatAppVersionBadge("2.0.0-SNAPSHOT"))
    assertEquals("v2.0.0-SNAPSHOT", formatAppVersionBadge("  2.0.0-SNAPSHOT  "))
    assertEquals("v2.1.0", formatAppVersionBadge("v2.1.0"))
    assertEquals("V2.1.0", formatAppVersionBadge("V2.1.0"))
    assertEquals("", formatAppVersionBadge(""))
    assertEquals("", formatAppVersionBadge("   "))
  }

  @Test
  fun demoBadgeAndDevToolsFooterRenderInStaging() = runDesktopComposeUiTest {
    assertEquals("DEMO", DEMO_BADGE_TEXT)
    assertEquals("Admin only", ADMIN_ONLY_BADGE_TEXT)
    assertEquals(2026, DEMO_ATTRIBUTION_COPYRIGHT_YEAR)
    assertEquals("The Collect One Contributors", DEMO_ATTRIBUTION_CONTRIBUTORS_LABEL)
    assertEquals(
      "https://github.com/google/ground-platform/graphs/contributors?all=1",
      DEMO_ATTRIBUTION_CONTRIBUTORS_URL,
    )
    assertEquals("© 2026 The Collect One Contributors", DEMO_ATTRIBUTION_FOOTER_TEXT)
    assertEquals("Debugging tools", DEV_TOOLS_FOOTER_LABEL)
    assertTrue(AppBuildConfig.APP_VERSION.isNotBlank())

    var devToolsClicks = 0
    setContent {
      MaterialTheme {
        Column {
          DemoBadge()
          AdminOnlyBadge()
          DevToolsFooterBar(
            onOpenDevTools = { devToolsClicks++ },
            environment = AppEnvironment.STAGING,
          )
        }
      }
    }

    onNodeWithText("DEMO").assertExists()
    onNodeWithContentDescription("Admin only").assertExists()
    onNodeWithText("Dev tools").assertDoesNotExist()
    onNodeWithText(DEV_TOOLS_FOOTER_LABEL).assertExists()
    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertExists().performClick()
    assertEquals(1, devToolsClicks)
  }

  /** The version and the attribution moved from the footer to the About screen. */
  @Test
  fun devToolsFooterShowsNoVersionOrAttribution() = runDesktopComposeUiTest {
    val version = formatAppVersionBadge(AppBuildConfig.APP_VERSION)
    setContent {
      MaterialTheme { DevToolsFooterBar(onOpenDevTools = {}, environment = AppEnvironment.DEV) }
    }

    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertExists()
    onNodeWithText(version, substring = true).assertDoesNotExist()
    onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertDoesNotExist()
  }

  @Test
  fun devToolsFooterIsHiddenInProdEnvironment() = runDesktopComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalAppEnvironment provides AppEnvironment.PROD) {
        MaterialTheme {
          Column {
            DevToolsFooterBar(onOpenDevTools = {})
          }
        }
      }
    }

    onNodeWithTag(DEV_TOOLS_FOOTER_BUTTON_TAG).assertDoesNotExist()
    onNodeWithText(DEV_TOOLS_FOOTER_LABEL).assertDoesNotExist()
  }

  @Test
  fun contributorsAttributionTextShowsTheAttributionLine() = runDesktopComposeUiTest {
    setContent { MaterialTheme { ContributorsAttributionText() } }

    onNodeWithText("© 2026 The Collect One Contributors").assertExists()
  }
}
