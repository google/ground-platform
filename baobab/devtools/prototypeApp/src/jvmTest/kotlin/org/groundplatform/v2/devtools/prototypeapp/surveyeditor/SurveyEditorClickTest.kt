/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples

/** Every item in the survey editor's left-hand navigation must select on a single click. */
@OptIn(ExperimentalTestApi::class)
class SurveyEditorClickTest {

  private fun withNavigation(block: ComposeUiTest.(SurveyEditorState) -> Unit) =
    runDesktopComposeUiTest(width = 600, height = 1000) {
      val state = SurveyEditorState(SurveyEditorSamples.draft())
      setContent {
        MaterialTheme {
          SurveyNavigation(state = state, modifier = Modifier.width(280.dp).fillMaxHeight())
        }
      }
      block(state)
    }

  @Test
  fun clickWithSlightMouseMovementSelectsForm() = withNavigation { state ->
    val entry = state.forms.first()
    // A real click rarely keeps the mouse perfectly still between press and release.
    onAllNodesWithText(entry.editor.form.title, useUnmergedTree = true)
      .onFirst()
      .performMouseInput {
        moveTo(center)
        press()
        moveBy(Offset(2f, 1f))
        release()
      }
    waitForIdle()
    assertEquals(SurveyEditorSection.Form(entry.key), state.section)
  }

  @Test
  fun clickWithSlightMouseMovementSelectsDataset() = withNavigation { state ->
    val dataset = (state.mapLayers + state.dataTables).first()
    onAllNodesWithText(dataset.displayName, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(1f, 3f))
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Dataset(dataset.key), state.section)
  }

  @Test
  fun dragPastSlopStillReorders() = withNavigation { state ->
    val first = state.forms.first()
    if (state.forms.size < 2) return@withNavigation
    onAllNodesWithText(first.editor.form.title, useUnmergedTree = true)
      .onFirst()
      .performMouseInput {
        moveTo(center)
        press()
        repeat(10) { moveBy(Offset(0f, 12f)) }
        release()
      }
    waitForIdle()
    assertEquals(SurveyEditorSection.Details, state.section)
    assertEquals(first.key, state.forms[1].key)
  }
}
