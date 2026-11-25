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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [AppMap] reports what the mouse pointer is over through `onHover`: the topmost feature, a marker
 * in preference to the features beneath it, and `null` over the background or off the map.
 */
@OptIn(ExperimentalTestApi::class)
class AppMapHoverTest {
  private val center = LatLng(0.0, 0.0)

  /** A circle feature at the map's center, with a marker well to its east. */
  private val content =
    MapContent(
      basemap = Basemap.None(),
      sources = listOf(GeoJsonSource("points", listOf(MapFeature("pin", Geometry.Point(center))))),
      layers =
        listOf(MapLayer.Circle(id = "pins", sourceId = "points", color = StyleValue.Constant(RED))),
      markers = listOf(MapMarker(id = "label", position = LatLng(0.0, 0.02))),
    )

  @Test
  fun hover_reportsTheFeatureUnderThePointer_andNullOverTheBackground() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val hovered = mutableListOf<String?>()
      setContent {
        AppMap(
          content = content,
          cameraState = MapCameraState(CameraPosition(center = center, zoom = 12.0)),
          onEvent = {},
          modifier = Modifier.size(400.dp).testTag("map"),
          onHover = { hovered += it },
        )
      }
      waitForIdle()

      onNodeWithTag("map").performMouseInput { moveTo(this.center) }
      waitForIdle()
      assertEquals("pin", hovered.last())

      onNodeWithTag("map").performMouseInput { moveTo(Offset(20f, 20f)) }
      waitForIdle()
      assertNull(hovered.last())

      onNodeWithTag("map").performMouseInput {
        moveTo(this.center)
        exit()
      }
      waitForIdle()
      assertNull(hovered.last())
    }

  @Test
  fun hover_overAMarker_reportsTheMarker() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val hovered = mutableListOf<String?>()
      setContent {
        AppMap(
          content = content,
          cameraState = MapCameraState(CameraPosition(center = center, zoom = 12.0)),
          onEvent = {},
          modifier = Modifier.size(400.dp),
          onHover = { hovered += it },
        ) { marker ->
          Box(Modifier.size(24.dp).testTag("marker-${marker.id}")) { BasicText(marker.id) }
        }
      }
      waitForIdle()

      onNodeWithTag("marker-label").performMouseInput { moveTo(this.center) }
      waitForIdle()
      assertEquals("label", hovered.last())
    }

  @Test
  fun hover_stopsBeingReported_whenTheAppStopsListening() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val hovered = mutableListOf<String?>()
      var listening by mutableStateOf(true)
      setContent {
        AppMap(
          content = content,
          cameraState = MapCameraState(CameraPosition(center = center, zoom = 12.0)),
          onEvent = {},
          modifier = Modifier.size(400.dp).testTag("map"),
          onHover = if (listening) ({ hovered += it }) else null,
        )
      }
      waitForIdle()
      onNodeWithTag("map").performMouseInput { moveTo(this.center) }
      waitForIdle()
      assertEquals("pin", hovered.last())

      listening = false
      waitForIdle()
      val reports = hovered.size
      onNodeWithTag("map").performMouseInput { moveTo(Offset(20f, 20f)) }
      waitForIdle()
      assertEquals(reports, hovered.size)
    }

  private companion object {
    val RED = Color(0xFFD32F2F)
  }
}
