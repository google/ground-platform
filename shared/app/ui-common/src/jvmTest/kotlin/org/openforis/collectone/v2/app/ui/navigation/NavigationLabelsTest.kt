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
package org.openforis.collectone.v2.app.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * The English labels of the navigation and screen models. They say "map features", never
 * "entities", to users.
 */
class NavigationLabelsTest {

  private fun english(resources: List<StringResource>) = runBlocking {
    resources.map { getString(it) }
  }

  @Test
  fun appScreenTitles() {
    assertEquals(
      listOf("Sign In", "Terms of Service", "Download Survey", "Main Survey (Map & List)"),
      english(AppScreen.entries.map { it.titleRes }),
    )
  }

  @Test
  fun appScreenSubtitles() {
    assertEquals(
      listOf(
        "Google authentication entry point",
        "Data governance & platform terms acceptance",
        "Browse shared surveys or search by name/location",
        "Survey map, map layers, location & submission bottom sheets, searchable list & drawer",
      ),
      english(AppScreen.entries.map { it.subtitleRes }),
    )
  }

  @Test
  fun mainSurveyViewModeLabels() {
    assertEquals(listOf("Map", "List"), english(MainSurveyViewMode.entries.map { it.labelRes }))
  }
}
