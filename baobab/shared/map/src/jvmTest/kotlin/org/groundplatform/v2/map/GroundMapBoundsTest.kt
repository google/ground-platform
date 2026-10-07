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
package org.groundplatform.v2.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Markers rendered in [GroundMap] must be clipped to the map's bounds and must not appear or accept
 * taps outside the map viewport (e.g. over an adjacent side panel).
 */
@OptIn(ExperimentalTestApi::class)
class GroundMapBoundsTest {

  @Test
  fun markersOutsideMapViewport_areNotDisplayedOrTappable() =
    runDesktopComposeUiTest(width = 800, height = 400) {
      val center = LatLng(0.0, 0.0)
      val offscreenWest = LatLng(0.0, -5.0)
      val camera = MapCameraState(CameraPosition(center = center, zoom = 12.0))
      val content =
        MapContent(
          markers =
            listOf(
              MapMarker(id = "inside", position = center),
              MapMarker(id = "outside", position = offscreenWest),
            )
        )
      val tappedIds = mutableListOf<String>()

      setContent {
        Row(Modifier.size(800.dp, 400.dp)) {
          // Left side panel sibling
          Box(Modifier.width(300.dp).fillMaxHeight())
          // Map occupying the right 500x400 area
          GroundMap(
            content = content,
            cameraState = camera,
            onEvent = { event ->
              if (event is MapEvent.MarkerTapped) {
                tappedIds += event.markerId
              }
            },
            modifier = Modifier.width(500.dp).fillMaxHeight(),
          ) { marker ->
            BasicText(marker.id, Modifier.testTag("marker-${marker.id}"))
          }
        }
      }
      waitForIdle()

      onNodeWithTag("marker-inside").assertIsDisplayed()
      onNodeWithTag("marker-outside").assertIsNotDisplayed()

      onNodeWithTag("marker-inside").performClick()
      assertEquals(listOf("inside"), tappedIds)
    }
}
