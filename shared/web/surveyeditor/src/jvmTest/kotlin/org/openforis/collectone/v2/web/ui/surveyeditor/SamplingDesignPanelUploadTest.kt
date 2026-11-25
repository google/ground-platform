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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.geo.io.GeoReadResult
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_file_read_failed
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult

/** The strata file upload of a stratified sample design: what it shows for unusable files. */
@OptIn(ExperimentalTestApi::class)
class SamplingDesignPanelUploadTest {
  private val uploadButton = "Upload strata file (GeoJSON / KML)"

  private val pointsGeoJson =
    """
    {"type": "FeatureCollection", "features": [
      {"type": "Feature", "properties": {"name": "A"},
       "geometry": {"type": "Point", "coordinates": [36.95, -0.42]}}
    ]}
    """
      .trimIndent()

  private fun ComposeUiTest.showStratifiedDesign(pick: TextFilePickResult) {
    val editor = surveyEditorViewModel()
    val key = editor.addSamplePlotsLayer()
    editor.updateSampleDesign(key) { it.copy(method = SampleMethod.STRATIFIED_RANDOM) }
    setContent {
      MaterialTheme {
        val uiState by editor.uiState.collectAsState()
        SamplingDesignPanel(
          uiState = uiState,
          actions = editor,
          dataset = uiState.datasets.first { it.key == key },
          pickTextFile = { _, onResult -> onResult(pick) },
        )
      }
    }
    waitForIdle()
  }

  @Test
  fun fileWithoutPolygons_asksForPolygonFeatures() =
    runDesktopComposeUiTest(width = 900, height = 2400) {
      showStratifiedDesign(TextFilePickResult.Picked("plots.geojson", pointsGeoJson))

      onNodeWithText(uploadButton).performClick()
      waitForIdle()

      onNodeWithText("Strata file must contain polygon features.").assertExists()
    }

  @Test
  fun fileWithNothingToImportAndNoReadIssue_saysItHasNoPolygons() {
    val preview = MapLayerImportPreview("empty.geojson", GeoReadResult(emptyList()), plan = null)

    val check = assertIs<StrataFileCheck.Unusable>(checkStrataFile(preview))

    assertEquals(
      "No polygon features found in empty.geojson.",
      runBlocking { check.reason.resolveString() },
    )
  }

  @Test
  fun unreadableFile_showsWhyItFailed() =
    runDesktopComposeUiTest(width = 900, height = 2400) {
      showStratifiedDesign(
        TextFilePickResult.Failed(Res.string.web_file_read_failed.toUiText("strata.kml"))
      )

      onNodeWithText(uploadButton).performClick()
      waitForIdle()

      onNodeWithText("Couldn't read strata.kml. Try again.").assertExists()
    }
}
