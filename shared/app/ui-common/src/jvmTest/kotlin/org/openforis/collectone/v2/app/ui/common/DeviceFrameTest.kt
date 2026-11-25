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

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.core.forms.ui.AppTheme

@OptIn(ExperimentalTestApi::class)
class DeviceFrameTest {
  @Test
  fun copyApkAndIosLinkChips_renderAndInvokeCallbacksIndependently() =
    runDesktopComposeUiTest(width = 900, height = 900) {
      var apkCopies = 0
      var iosCopies = 0
      setContent {
        AppTheme {
          DevicePreviewFrame(
            deviceTitle = "Collect One Mobile UI",
            isDarkTheme = false,
            onCopyApkLink = { apkCopies++ },
            onCopyIosLink = { iosCopies++ },
          ) {
            Text("Screen")
          }
        }
      }

      onNodeWithText("Copy APK Link").assertExists()
      onNodeWithText("Copy iOS Link").assertExists()

      // Clicking iOS copies only the iOS link and flips only the iOS chip to "Copied Link".
      mainClock.autoAdvance = false
      onNodeWithText("Copy iOS Link").performClick()
      mainClock.advanceTimeByFrame()
      assertEquals(0, apkCopies)
      assertEquals(1, iosCopies)
      onNodeWithText("Copied Link").assertExists()
      onNodeWithText("Copy APK Link").assertExists()
      onNodeWithContentDescription("Copy iOS Link").assertExists()

      // After the 2s feedback window, the label reverts to "Copy iOS Link".
      mainClock.advanceTimeBy(2500L)
      mainClock.advanceTimeByFrame()
      onNodeWithText("Copy iOS Link").assertExists()

      // Clicking APK copies only the APK link and leaves "Copy iOS Link" intact.
      onNodeWithText("Copy APK Link").performClick()
      mainClock.advanceTimeByFrame()
      assertEquals(1, apkCopies)
      assertEquals(1, iosCopies)
      onNodeWithText("Copied Link").assertExists()
      onNodeWithText("Copy iOS Link").assertExists()
    }

  @Test
  fun copyLinkChips_omittedWhenCallbacksAreNull() =
    runDesktopComposeUiTest(width = 900, height = 900) {
      setContent {
        AppTheme {
          DevicePreviewFrame(deviceTitle = "Form Preview", isDarkTheme = false) { Text("Screen") }
        }
      }

      onNodeWithText("Copy APK Link").assertDoesNotExist()
      onNodeWithText("Copy iOS Link").assertDoesNotExist()
    }

  @Test
  fun devicePreviewFrame_wiresFormFactorAndSimulationCallbacks() =
    runDesktopComposeUiTest(width = 900, height = 900) {
      var selectedFactor: DeviceFormFactor? = null
      var rotations = 0
      var themeToggles = 0
      var airplaneToggles = 0
      var walkCloserClicks = 0

      setContent {
        AppTheme {
          DevicePreviewFrame(
            deviceTitle = "Collect One Mobile UI",
            isDarkTheme = false,
            onSelectFormFactor = { selectedFactor = it },
            onRotateDevice = { rotations++ },
            onToggleDarkTheme = { themeToggles++ },
            isAirplaneMode = false,
            onToggleAirplaneMode = { airplaneToggles++ },
            onWalkCloser = { walkCloserClicks++ },
          ) {
            Text("Screen")
          }
        }
      }

      onNodeWithText("Tablet").performClick()
      onNodeWithText("Rotate").performClick()
      onNodeWithText("Light").performClick()
      onNodeWithText("Airplane mode").performClick()
      onNodeWithText("Walk closer").performClick()

      assertEquals(DeviceFormFactor.TABLET, selectedFactor)
      assertEquals(1, rotations)
      assertEquals(1, themeToggles)
      assertEquals(1, airplaneToggles)
      assertEquals(1, walkCloserClicks)
    }
}
