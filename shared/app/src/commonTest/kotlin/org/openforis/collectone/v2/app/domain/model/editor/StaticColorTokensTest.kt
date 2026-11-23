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
package org.openforis.collectone.v2.app.domain.model.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.core.forms.ui.AppStaticColors

/**
 * The domain stores colors as `#RRGGBB` strings, so it can't reference the Compose theme. These
 * tests keep the stored presets equal to the app's static color tokens they stand for.
 */
class StaticColorTokensTest {
  private fun hexOf(color: Color): String =
    "#" + (color.toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

  private val tokens =
    mapOf(
      "Orange" to AppStaticColors.Orange,
      "Red" to AppStaticColors.Red,
      "Purple" to AppStaticColors.Purple,
      "Blue" to AppStaticColors.Blue,
      "Green" to AppStaticColors.Green,
      "Yellow" to AppStaticColors.Yellow,
    )

  @Test
  fun choicePresets_matchTheStaticColorTokens() {
    val presets = ChoiceColors.palette.toMap()
    tokens.forEach { (name, color) -> assertEquals(hexOf(color), presets[name], name) }
  }

  @Test
  fun surveyedBadge_isTheStaticGreen() {
    assertEquals(hexOf(AppStaticColors.Green), EditorStatusBadge.SURVEYED.colorHex)
  }

  @Test
  fun inProgressBadge_isTheStaticYellow() {
    assertEquals(hexOf(AppStaticColors.Yellow), EditorStatusBadge.IN_PROGRESS.colorHex)
  }

  @Test
  fun needsReviewBadge_isTheStaticOrange() {
    assertEquals(hexOf(AppStaticColors.Orange), EditorStatusBadge.NEEDS_REVIEW.colorHex)
  }

  @Test
  fun flaggedBadge_isTheStaticRed() {
    assertEquals(hexOf(AppStaticColors.Red), EditorStatusBadge.FLAGGED.colorHex)
  }
}
