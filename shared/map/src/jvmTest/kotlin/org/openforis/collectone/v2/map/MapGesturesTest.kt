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
package org.openforis.collectone.v2.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MapGesturesTest {
  @Test
  fun pinchOut_zoomsIn_and_pinchIn_zoomsOut() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val initialZoom = 10.0
      val cameraState =
        MapCameraState(CameraPosition(center = LatLng(0.0, 0.0), zoom = initialZoom)).apply {
          viewportSize = DpSize(400.dp, 400.dp)
        }

      setContent {
        Box(modifier = Modifier.size(400.dp).testTag("map").mapGestures(cameraState) {})
      }
      waitForIdle()

      onNodeWithTag("map").performTouchInput {
        pinch(
          start0 = Offset(180f, 200f),
          end0 = Offset(80f, 200f),
          start1 = Offset(220f, 200f),
          end1 = Offset(320f, 200f),
        )
      }
      waitForIdle()
      val zoomedIn = cameraState.position.zoom
      assertTrue(zoomedIn > initialZoom, "Expected stretch to zoom in, got $zoomedIn")

      onNodeWithTag("map").performTouchInput {
        pinch(
          start0 = Offset(80f, 200f),
          end0 = Offset(180f, 200f),
          start1 = Offset(320f, 200f),
          end1 = Offset(220f, 200f),
        )
      }
      waitForIdle()
      val zoomedOut = cameraState.position.zoom
      assertTrue(zoomedOut < zoomedIn, "Expected pinch to zoom out, got $zoomedOut vs $zoomedIn")
    }
}
