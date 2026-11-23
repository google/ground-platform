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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_sign_in

/**
 * Picking a language in the header menu switches the page's language and leaves no trace of the
 * menu in the semantics tree, so screen readers and role-based queries see the page in its new
 * language.
 *
 * The browser mirrors this tree into its accessibility tree; the end-to-end test
 * `webConsoleLanguage.spec.ts` checks that mirror, which this desktop test can't reach.
 */
@OptIn(ExperimentalTestApi::class)
class WebLanguageMenuSemanticsTest {
  private val defaultLocale = Locale.getDefault()

  @AfterTest
  fun restoreDefaultLocale() {
    Locale.setDefault(defaultLocale)
  }

  @Test
  fun pickingALanguage_removesTheMenuFromTheSemanticsTree() = runDesktopComposeUiTest {
    setContent {
      var languageCode by remember { mutableStateOf("en") }
      ProvideAppLocale(languageCode) {
        MaterialTheme {
          Column {
            WebCompactLanguageSelector(
              currentLanguageCode = languageCode,
              onLanguageChange = { languageCode = it },
            )
            Button(onClick = {}) { Text(stringResource(Res.string.web_sign_in)) }
          }
        }
      }
    }
    onNodeWithTag(WEB_COMPACT_LANGUAGE_SELECTOR_TAG).performClick()
    onNodeWithText("Français").assertExists()

    onNodeWithText("Français").performClick()
    waitForIdle()

    onAllNodes(isPopup()).assertCountEquals(0)
    onAllNodesWithText("Español").assertCountEquals(0)
    onAllNodesWithText("English").assertCountEquals(0)
    onAllNodes(isButton() and hasText("Se connecter")).assertCountEquals(1)
  }

  private fun isButton(): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
}
