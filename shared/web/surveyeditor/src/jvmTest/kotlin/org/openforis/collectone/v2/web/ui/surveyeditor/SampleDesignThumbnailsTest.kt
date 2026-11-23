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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode

/** The thumbnail pickers of the sample design panel select on click and draw every option. */
@OptIn(ExperimentalTestApi::class)
class SampleDesignThumbnailsTest {

  @Test
  fun clickingAThumbnailSelectsIt() =
    runDesktopComposeUiTest(width = 480, height = 400) {
      var method by mutableStateOf(SampleMethod.SYSTEMATIC_GRID)
      setContent {
        MaterialTheme {
          ThumbnailSelector(
            label = "Method",
            options = SampleMethod.entries,
            selected = method,
            optionText = { it.cardLabel },
            onSelect = { method = it },
            thumbnail = { m, colors -> drawSampleMethodThumbnail(m, colors) },
          )
        }
      }
      onNodeWithText("Grid").assertIsSelected()
      onNodeWithText("Cluster").assertIsNotSelected().performClick()
      waitForIdle()
      assertEquals(SampleMethod.CLUSTER, method)
      onNodeWithText("Cluster").assertIsSelected()
      onNodeWithText("Grid").assertIsNotSelected()
    }

  @Test
  fun disabledThumbnailsIgnoreClicks() =
    runDesktopComposeUiTest(width = 480, height = 400) {
      var selected = SubPlotMode.NONE
      setContent {
        MaterialTheme {
          ThumbnailSelector(
            label = "Sample points in each plot",
            options = SubPlotMode.entries,
            selected = selected,
            optionText = { it.cardLabel },
            onSelect = { selected = it },
            thumbnail = { m, colors -> drawSubPlotThumbnail(m, PlotShapeOption.POINT, colors) },
            enabled = false,
          )
        }
      }
      onNodeWithText("Grid").performClick()
      waitForIdle()
      assertEquals(SubPlotMode.NONE, selected)
    }

  /**
   * Draws every thumbnail, with every picker's options on a single row even in a narrow panel. Set
   * `SAMPLE_THUMBNAILS_PNG` to a file path to also save the rendering for a visual check.
   */
  @Test
  fun drawsEveryThumbnail() =
    runDesktopComposeUiTest(width = 300, height = 560) {
      setContent {
        MaterialTheme {
          Surface {
            Column(Modifier.width(NARROW_PANEL_WIDTH).padding(10.dp)) {
              var method by remember { mutableStateOf(SampleMethod.STRATIFIED_RANDOM) }
              var shape by remember { mutableStateOf(PlotShapeOption.CIRCLE) }
              var mode by remember { mutableStateOf(SubPlotMode.GRID) }
              ThumbnailSelector(
                label = "Method",
                options = SampleMethod.entries,
                selected = method,
                optionText = { it.cardLabel },
                onSelect = { method = it },
                thumbnail = { m, colors -> drawSampleMethodThumbnail(m, colors) },
              )
              ThumbnailSelector(
                label = "Plot shape",
                options = PlotShapeOption.entries,
                selected = shape,
                optionText = { it.cardLabel },
                onSelect = { shape = it },
                thumbnail = { s, colors -> drawPlotShapeThumbnail(s, colors) },
              )
              ThumbnailSelector(
                label = "Sample points in each plot",
                options = SubPlotMode.entries,
                selected = mode,
                optionText = { it.cardLabel },
                onSelect = { mode = it },
                thumbnail = { m, colors -> drawSubPlotThumbnail(m, shape, colors) },
              )
            }
          }
        }
      }
      // The first and last card of the Method and Sample points rows ("Grid" and "Random" appear in
      // both rows, in that order).
      val grids = onAllNodesWithText("Grid")
      val randoms = onAllNodesWithText("Random")
      for (row in 0..1) {
        val first = (if (row == 0) grids[0] else onNodeWithText("None")).getUnclippedBoundsInRoot()
        val last =
          (if (row == 0) onNodeWithText("Cluster") else randoms[1]).getUnclippedBoundsInRoot()
        assertEquals(first.top, last.top, "the last option of row $row wrapped onto another row")
      }
      val image = onRoot().captureToImage()
      System.getenv("SAMPLE_THUMBNAILS_PNG")?.let { path ->
        val png = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
        File(path).writeBytes(png!!.bytes)
      }
    }

  private companion object {
    /** Narrower than the survey editor's settings panel usually is. */
    val NARROW_PANEL_WIDTH = 280.dp
  }
}
