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
package org.openforis.collectone.v2.app.ui.text

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_display_logic_shown_if
import org.openforis.collectone.v2.app.resources.domain_quota_max_forms
import org.openforis.collectone.v2.app.resources.domain_survey_lifecycle_state_draft

/** [UiText] resolves the same in composition ([UiText.resolve]) and outside it. */
@OptIn(ExperimentalTestApi::class)
class UiTextTest {
  /** A resource whose arguments are another [UiText], a string resource, and user data. */
  private val nested =
    Res.string.domain_display_logic_shown_if.toUiText(
      UiText.Plain("Tree count"),
      Res.string.domain_survey_lifecycle_state_draft,
      42,
    )
  private val plural = Res.plurals.domain_quota_max_forms.toUiText(2, 2, 1, "Free")

  @Test
  fun resolveString_resolvesNestedTextAndResourceArguments_andPassesUserDataThrough() {
    assertEquals("Shown only if \"Tree count\" Draft 42.", runBlocking { nested.resolveString() })
  }

  @Test
  fun resolveString_picksThePluralFormForTheQuantity() {
    assertEquals(
      "Survey contains 2 forms, exceeding the limit of 1 for Free tier.",
      runBlocking { plural.resolveString() },
    )
  }

  @Test
  fun lowercase_lowersTheResolvedText() {
    assertEquals(
      "shown only if \"tree count\" draft 42.",
      runBlocking { UiText.Lowercase(nested).resolveString() },
    )
  }

  @Test
  fun resolve_inComposition_matchesResolveString() = runDesktopComposeUiTest {
    val texts = listOf(UiText.Plain("Maya"), nested, plural, UiText.Lowercase(nested))
    var resolved = emptyList<String>()

    setContent { resolved = texts.map { it.resolve() } }
    waitForIdle()

    assertEquals(runBlocking { texts.map { it.resolveString() } }, resolved)
  }
}
