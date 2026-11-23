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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.core.forms.ui.AppTheme

@OptIn(ExperimentalTestApi::class)
class EarthEngineBadgeTest {
  private val screenshotPath: String? = System.getenv("EARTH_ENGINE_BADGE_PNG")

  @Test
  fun poweredByEarthEngineBadge_rendersNewLogoAndAttributionInLightAndDarkThemes() =
    runDesktopComposeUiTest(width = 760, height = 340) {
      assertEquals(192f, EarthEngineLogo.viewportWidth)
      assertEquals(192f, EarthEngineLogo.viewportHeight)

      setContent {
        Row(
          modifier = Modifier.background(Color(0xFF1E293B)).padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.background(Color.White).padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            EarthEngineLogoIcon(size = 160.dp)
          }
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTheme(darkTheme = false) {
              Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  PoweredByEarthEngineBadge(
                    isMapOverlay = false,
                    modifier = Modifier.testTag(EARTH_ENGINE_PANEL_BADGE_TAG),
                  )
                  PoweredByEarthEngineBadge(
                    isMapOverlay = true,
                    modifier = Modifier.testTag(EARTH_ENGINE_MAP_BADGE_TAG),
                  )
                }
              }
            }
            AppTheme(darkTheme = true) {
              Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  PoweredByEarthEngineBadge(isMapOverlay = false)
                  PoweredByEarthEngineBadge(isMapOverlay = true)
                }
              }
            }
          }
        }
      }
      waitForIdle()

      onNodeWithTag(EARTH_ENGINE_PANEL_BADGE_TAG).assertExists()
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertExists()
      onAllNodesWithText("Powered by Google Earth Engine").assertCountEquals(4)

      screenshotPath?.let { path ->
        val bitmap = onRoot().captureToImage().asSkiaBitmap()
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)
        File(path).writeBytes(png!!.bytes)
      }
    }
}
