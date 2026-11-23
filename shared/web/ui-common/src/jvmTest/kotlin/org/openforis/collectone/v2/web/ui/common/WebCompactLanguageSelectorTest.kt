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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WebCompactLanguageSelectorTest {
  @Test
  fun showsCurrentLanguageCode_andSelectingALanguageReportsItsCode() = runDesktopComposeUiTest {
    var selected: String? = null
    setContent {
      MaterialTheme {
        WebCompactLanguageSelector(currentLanguageCode = "fr", onLanguageChange = { selected = it })
      }
    }

    // Minimal representation: just the language code, not the language name.
    onNodeWithText("FR").assertExists()
    onNodeWithText("Français").assertDoesNotExist()

    onNodeWithTag(WEB_COMPACT_LANGUAGE_SELECTOR_TAG).performClick()
    onNodeWithText("Español").performClick()

    assertEquals("es", selected)
  }
}
