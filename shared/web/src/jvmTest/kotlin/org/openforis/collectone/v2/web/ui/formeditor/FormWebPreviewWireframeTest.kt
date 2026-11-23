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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.FormEditorSamples
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.web.SurveyEditorDestination
import org.openforis.collectone.v2.web.navigation.RecordingPageNavigation
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.surveyEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.formEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

@OptIn(ExperimentalTestApi::class)
class FormWebPreviewWireframeTest {

  private fun ComposeUiTest.maybeWriteScreenshot(suffix: String) {
    val basePath = System.getenv("FORM_WIREFRAME_SCREENSHOT_PNG") ?: return
    val outFile = File(basePath.removeSuffix(".png") + "_$suffix.png")
    outFile.parentFile?.mkdirs()
    val bitmap = onRoot().captureToImage().asSkiaBitmap()
    val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)
    outFile.writeBytes(png!!.bytes)
  }

  @Test
  fun webLayoutCanvasRendersMinimalistSkeletonBackdropWithoutMockPreviewDetails() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state =
        formEditorViewModel(
          form = FormEditorSamples.shadeTreeVisit(),
          datasets =
            listOf(
              EditorDataset(
                id = "ds-parcels",
                displayName = "Smallholder Coffee Parcels",
                isMapLayer = true,
                keyProperty = "plot_id",
                labelProperty = "plot_name",
                properties = emptyList(),
                isLinkedToThisForm = true,
              )
            ),
        )
      state.selectPreviewTarget(FormPreviewTarget.WEB)
      if (!state.ui.isEnabledOnPreviewTarget) {
        state.enableOnPreviewTarget()
      }
      state.select("q1")

      setContent {
        AppTheme(darkTheme = false) {
          val uiState by state.uiState.collectAsState()
          FormEditorPage(
            uiState = uiState,
            actions = state,
            previewController = state.previewController.collectAsState().value,
            isDarkTheme = false,
          )
        }
      }
      waitForIdle()

      onNodeWithTag(WEB_DASHBOARD_WIREFRAME_TAG, useUnmergedTree = true).assertExists()

      // Detailed mock dashboard text and controls must not appear in the background wireframe.
      onAllNodesWithText("Smallholder Coffee Parcels", useUnmergedTree = true).assertCountEquals(0)
      onAllNodesWithText("3 features", useUnmergedTree = true).assertCountEquals(0)
      onAllNodesWithText("Search features", substring = true, useUnmergedTree = true)
        .assertCountEquals(0)
      onAllNodesWithText("Plot #101", substring = true, useUnmergedTree = true).assertCountEquals(0)
      onAllNodesWithText("Survey map", substring = true, useUnmergedTree = true)
        .assertCountEquals(0)

      maybeWriteScreenshot("wide")

      // Clicking the exposed left side of the skeleton backdrop selects the form itself.
      onNodeWithTag(WEB_DASHBOARD_WIREFRAME_TAG, useUnmergedTree = true).performMouseInput {
        click(Offset(60f, 120f))
      }
      waitForIdle()
      assertTrue(state.ui.isFormSelected)
    }

  @Test
  fun surveyEditorWebLayoutRendersCleanWireframeAtStandardDesktopWidth() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val firstFormKey = state.surveyEditor.uiState.value.forms.first().key
      state.surveyEditor.select(SurveyEditorSection.Form(firstFormKey))
      val formEditor = state.surveyEditor.formEditor(firstFormKey)
      formEditor.selectPreviewTarget(FormPreviewTarget.WEB)
      if (!formEditor.uiState.value.isEnabledOnPreviewTarget) {
        formEditor.enableOnPreviewTarget()
      }

      setContent {
        AppTheme(darkTheme = false) {
          ProvideWebSession(state) {
            SurveyEditorDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      onNodeWithTag(WEB_DASHBOARD_WIREFRAME_TAG, useUnmergedTree = true).assertExists()
      onAllNodesWithText("Plot #101", substring = true, useUnmergedTree = true).assertCountEquals(0)
      onAllNodesWithText("3 features", useUnmergedTree = true).assertCountEquals(0)

      maybeWriteScreenshot("survey_editor")
    }
}
