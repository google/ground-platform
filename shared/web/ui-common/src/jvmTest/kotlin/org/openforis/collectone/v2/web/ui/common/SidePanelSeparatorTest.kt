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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.skia.Bitmap

private const val SEPARATOR_TAG = "side_panel_separator"
private val TestSurfaceColor = Color(0xFFF7FBF2)
private val TestSurfaceContainerLowColor = Color(0xFFF1F5EC)
private val TestPrimaryColor = Color(0xFF36693E)
private val TestBackdropColor = Color(0xFFFFFFFF)

private val TestColorScheme =
  lightColorScheme(
    primary = TestPrimaryColor,
    surface = TestSurfaceColor,
    surfaceContainerLow = TestSurfaceContainerLowColor,
    outlineVariant = Color(0xFFC1C9BE),
  )

private fun Bitmap.pixelAtDp(xDp: Float, yDp: Float, densityScale: Float): Int =
  getColor((xDp * densityScale).roundToInt(), (yDp * densityScale).roundToInt())

@OptIn(ExperimentalTestApi::class)
class SidePanelSeparatorTest {

  @Test
  fun atRest_matchesPanelSurfaceWithoutVerticalLine_andTintsEntireAreaOnHover() =
    runDesktopComposeUiTest(width = 120, height = 80) {
      setContent {
        MaterialTheme(colorScheme = TestColorScheme) {
          Row(modifier = Modifier.fillMaxSize().background(TestBackdropColor)) {
            Box(modifier = Modifier.width(40.dp).fillMaxHeight().background(TestSurfaceColor))
            SidePanelSeparator(
              widthDp = 40f,
              onWidthChange = {},
              modifier =
                Modifier.testTag(SEPARATOR_TAG).width(SidePanelSeparatorWidth).fillMaxHeight(),
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(TestBackdropColor))
          }
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val sepNode = onNodeWithTag(SEPARATOR_TAG)
      val sepBounds = sepNode.getUnclippedBoundsInRoot()
      val restingBitmap = onRoot().captureToImage().asSkiaBitmap()
      val scale = restingBitmap.width / rootBounds.width.value
      val sampleY = rootBounds.height.value / 2f

      // Every pixel across the 8dp drag area matches the panel surface with no resting line.
      val expectedRestingArgb = TestSurfaceColor.toArgb()
      for (offsetDp in listOf(0f, 2f, 4f, 6f, 7f)) {
        assertEquals(
          expectedRestingArgb,
          restingBitmap.pixelAtDp(sepBounds.left.value + offsetDp, sampleY, scale),
          "Resting pixel at +${offsetDp}dp should match left panel surface color",
        )
      }

      // Hovering tints the entire draggable area uniformly.
      sepNode.performMouseInput { enter(center) }
      waitForIdle()
      val hoveredBitmap = onRoot().captureToImage().asSkiaBitmap()
      val expectedHoverArgb =
        TestPrimaryColor.copy(alpha = 0.16f).compositeOver(TestSurfaceColor).toArgb()
      assertNotEquals(expectedRestingArgb, expectedHoverArgb)
      for (offsetDp in listOf(0f, 2f, 4f, 6f, 7f)) {
        assertEquals(
          expectedHoverArgb,
          hoveredBitmap.pixelAtDp(sepBounds.left.value + offsetDp, sampleY, scale),
          "Hovered pixel at +${offsetDp}dp should use hover tint across entire drag area",
        )
      }

      // Leaving hover restores the panel surface color.
      sepNode.performMouseInput { exit() }
      waitForIdle()
      val exitedBitmap = onRoot().captureToImage().asSkiaBitmap()
      assertEquals(
        expectedRestingArgb,
        exitedBitmap.pixelAtDp(sepBounds.left.value + 4f, sampleY, scale),
        "Exiting hover should restore panel surface color",
      )
    }

  @Test
  fun customContainerColor_isUsedAtRestAndTintedOnHover() =
    runDesktopComposeUiTest(width = 120, height = 80) {
      setContent {
        MaterialTheme(colorScheme = TestColorScheme) {
          Row(modifier = Modifier.fillMaxSize().background(TestBackdropColor)) {
            Box(
              modifier =
                Modifier.width(40.dp).fillMaxHeight().background(TestSurfaceContainerLowColor)
            )
            SidePanelSeparator(
              widthDp = 40f,
              onWidthChange = {},
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier =
                Modifier.testTag(SEPARATOR_TAG).width(SidePanelSeparatorWidth).fillMaxHeight(),
            )
          }
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val sepNode = onNodeWithTag(SEPARATOR_TAG)
      val sepBounds = sepNode.getUnclippedBoundsInRoot()
      val restingBitmap = onRoot().captureToImage().asSkiaBitmap()
      val scale = restingBitmap.width / rootBounds.width.value
      val sampleY = rootBounds.height.value / 2f

      assertEquals(
        TestSurfaceContainerLowColor.toArgb(),
        restingBitmap.pixelAtDp(sepBounds.left.value + 4f, sampleY, scale),
      )

      sepNode.performMouseInput { enter(center) }
      waitForIdle()
      val hoveredBitmap = onRoot().captureToImage().asSkiaBitmap()
      assertEquals(
        TestPrimaryColor.copy(alpha = 0.16f).compositeOver(TestSurfaceContainerLowColor).toArgb(),
        hoveredBitmap.pixelAtDp(sepBounds.left.value + 4f, sampleY, scale),
      )
    }

  @Test
  fun disabledSeparator_doesNotTintOnHover() =
    runDesktopComposeUiTest(width = 120, height = 80) {
      setContent {
        MaterialTheme(colorScheme = TestColorScheme) {
          Row(modifier = Modifier.fillMaxSize().background(TestBackdropColor)) {
            SidePanelSeparator(
              widthDp = 40f,
              onWidthChange = {},
              enabled = false,
              modifier =
                Modifier.testTag(SEPARATOR_TAG).width(SidePanelSeparatorWidth).fillMaxHeight(),
            )
          }
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val sepNode = onNodeWithTag(SEPARATOR_TAG)
      val sepBounds = sepNode.getUnclippedBoundsInRoot()
      sepNode.performMouseInput { enter(center) }
      waitForIdle()
      val bitmap = onRoot().captureToImage().asSkiaBitmap()
      val scale = bitmap.width / rootBounds.width.value
      assertEquals(
        TestSurfaceColor.toArgb(),
        bitmap.pixelAtDp(sepBounds.left.value + 4f, rootBounds.height.value / 2f, scale),
      )
    }

  @Test
  fun draggingSeparator_updatesPanelWidth() =
    runDesktopComposeUiTest(width = 200, height = 80) {
      var reportedWidth = 100f
      setContent {
        MaterialTheme(colorScheme = TestColorScheme) {
          Row(modifier = Modifier.fillMaxSize()) {
            SidePanelSeparator(
              widthDp = reportedWidth,
              onWidthChange = { reportedWidth = it },
              modifier =
                Modifier.testTag(SEPARATOR_TAG).width(SidePanelSeparatorWidth).fillMaxHeight(),
            )
          }
        }
      }
      waitForIdle()

      onNodeWithTag(SEPARATOR_TAG).performMouseInput {
        moveTo(center)
        press()
        moveBy(Offset(25f, 0f))
        release()
      }
      waitForIdle()

      assertEquals(125f, reportedWidth, 1f)
    }
}
