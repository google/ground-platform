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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import kotlin.test.Test
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale

/** The organization page's "What's published" card states the publication rules in its language. */
@OptIn(ExperimentalTestApi::class)
class PublishedNumbersCardTest {
  private val organization = Organization(id = "org-1", name = "Kenya Forest Service")

  @Test
  fun english_statesThePublicationRules() {
    assertCardShows(
      languageCode = "en",
      text =
        "Submissions, map features, locations, and answers are never published, nor are " +
          "standard fields kept for the organization only or marked sensitive.",
    )
  }

  @Test
  fun french_statesThePublicationRulesInFrench() {
    assertCardShows(
      languageCode = "fr",
      text =
        "Les soumissions, entités cartographiques, emplacements et réponses ne sont jamais " +
          "publiées",
    )
  }

  private fun assertCardShows(languageCode: String, text: String) {
    // On the JVM the app locale is the default locale, which the other tests expect in English.
    val defaultLocale = Locale.getDefault()
    try {
      runDesktopComposeUiTest {
        setContent {
          ProvideAppLocale(languageCode) {
            PublishedNumbersCard(organization = organization, isManager = false, onOpenImpact = {})
          }
        }

        onNodeWithText(text, substring = true).assertExists()
      }
    } finally {
      Locale.setDefault(defaultLocale)
    }
  }
}
