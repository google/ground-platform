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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test

/** What the About screen and dialog show ([AboutContent]). */
@OptIn(ExperimentalTestApi::class)
class AboutContentTest {
  @Test
  fun showsTheProductNameTheVersionThePurposeAndTheContributors() = runDesktopComposeUiTest {
    setContent { MaterialTheme { AboutContent(appVersion = " 2.1.0 ") } }

    onNodeWithTag(ABOUT_CONTENT_TAG).assertExists()
    onNodeWithText("Open Foris Collect One").assertExists()
    onNodeWithText("Version 2.1.0").assertExists()
    onNodeWithText("Collect One is an open-source tool", substring = true).assertExists()
    onNodeWithText("Our mission is", substring = true).assertExists()
    onNodeWithText(DEMO_ATTRIBUTION_FOOTER_TEXT).assertExists()
  }

  @Test
  fun hidesTheVersionWhenItIsBlank() = runDesktopComposeUiTest {
    setContent { MaterialTheme { AboutContent(appVersion = "  ") } }

    onNodeWithText("Open Foris Collect One").assertExists()
    onNodeWithText("Version", substring = true).assertDoesNotExist()
  }
}
