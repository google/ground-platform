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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_retry

/** [ProvideAppLocale] switches Compose resource resolution in place and restores the default. */
@OptIn(ExperimentalTestApi::class)
class AppLocaleTest {
  private val initialLocale: Locale = Locale.getDefault()

  @AfterTest
  fun restoreJvmLocale() {
    Locale.setDefault(initialLocale)
  }

  @Test
  fun switchesComposeResourcesToLanguageTagAndRestoresDefaultWhenBlankOrNull() =
    runDesktopComposeUiTest {
      var languageCode by mutableStateOf<String?>("fr")
      var currentTag = ""
      var retryLabel = ""

      setContent {
        ProvideAppLocale(languageCode) {
          currentTag = LocalAppLocale.current
          retryLabel = stringResource(Res.string.app_retry)
        }
      }
      waitForIdle()
      assertEquals("fr", currentTag)
      assertEquals("Réessayer", retryLabel)

      languageCode = ""
      waitForIdle()
      assertEquals(initialLocale.toLanguageTag(), currentTag)
      assertEquals("Retry", retryLabel)
    }
}
