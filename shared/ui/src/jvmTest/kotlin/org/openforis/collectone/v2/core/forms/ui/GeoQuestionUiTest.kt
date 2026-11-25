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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import openforis.collectone.v1.forms.GeoPoint

/** Capturing, panning, zooming, and undoing on the built-in geopoint / geotrace / geoshape maps. */
@OptIn(ExperimentalTestApi::class)
class GeoQuestionUiTest {

  private fun geoForm(type: String, appearance: String = "") =
    formController(
      title = "Plot Survey",
      model =
        """
        <instance><data id="plots" version="1"><plot/></data></instance>
        <bind nodeset="/data/plot" type="$type"/>
        """,
      body = """<input ref="/data/plot" appearance="$appearance"><label>Plot</label></input>""",
    )

  private fun ComposeUiTest.showQuestion(controller: FormWizardController) = setContent {
    AppTheme {
      CompositionLocalProvider(LocalGeoLocationSource provides FixedGeoLocationSource(PARIS_FIX)) {
        ControlWidget(controller.currentQuestion(), controller)
      }
    }
  }

  private fun FormWizardController.point(): GeoPoint? =
    currentQuestion().fieldState.value?.scalar_value?.geopoint_value

  private fun FormWizardController.shapeVertexCount(): Int =
    currentQuestion().fieldState.value?.scalar_value?.geoshape_value?.points?.size ?: 0

  private fun FormWizardController.traceVertexCount(): Int =
    currentQuestion().fieldState.value?.scalar_value?.geotrace_value?.points?.size ?: 0

  private val parisPoint =
    GeoPoint(latitude = 48.858370, longitude = 2.294481, accuracy_meters = 4.0)

  @Test
  fun geopoint_captureLocation_recordsTheFix() = runComposeUiTest {
    val controller = geoForm("geopoint")
    showQuestion(controller)

    onNodeWithText("Capture location").performClick()

    assertEquals(parisPoint, controller.point())
  }

  @Test
  fun geopoint_undo_clearsTheCapturedPoint() = runComposeUiTest {
    val controller = geoForm("geopoint")
    showQuestion(controller)
    onNodeWithText("Capture location").performClick()

    onNodeWithContentDescription("Undo").performClick()

    assertNull(controller.point())
  }

  @Test
  fun geopoint_redo_restoresTheUndonePoint() = runComposeUiTest {
    val controller = geoForm("geopoint")
    showQuestion(controller)
    onNodeWithText("Capture location").performClick()
    onNodeWithContentDescription("Undo").performClick()

    onNodeWithContentDescription("Redo").performClick()

    assertEquals(parisPoint, controller.point())
  }

  @Test
  fun geopoint_redo_isDisabledUntilSomethingIsUndone() = runComposeUiTest {
    val controller = geoForm("geopoint")
    showQuestion(controller)
    onNodeWithText("Capture location").performClick()

    onNodeWithContentDescription("Redo").assertIsNotEnabled()
  }

  @Test
  fun geopoint_zoomIn_updatesTheZoomReadout() = runComposeUiTest {
    val controller = geoForm("geopoint")
    showQuestion(controller)
    onNodeWithText("GPS ±4.0 m • 17.5z").assertExists()

    onNodeWithContentDescription("Zoom in").performClick()

    onNodeWithText("GPS ±4.0 m • 18.3z").assertExists()
  }

  @Test
  fun geopoint_zoomAndUndoHistory_surviveLeavingTheQuestion() = runComposeUiTest {
    val controller = geoForm("geopoint")
    var showQuestion by mutableStateOf(true)
    setContent {
      AppTheme {
        CompositionLocalProvider(
          LocalGeoLocationSource provides FixedGeoLocationSource(PARIS_FIX)
        ) {
          if (showQuestion) ControlWidget(controller.currentQuestion(), controller)
        }
      }
    }
    onNodeWithContentDescription("Zoom in").performClick()
    onNodeWithText("Capture location").performClick()
    onNodeWithContentDescription("Undo").performClick()

    showQuestion = false
    waitForIdle()
    showQuestion = true

    onNodeWithText("GPS ±4.0 m • 18.3z").assertExists()
    onNodeWithContentDescription("Redo").assertIsEnabled()
  }

  @Test
  fun pannableGeopoint_dragging_capturesTheCrosshairInsteadOfTheFix() = runComposeUiTest {
    val controller = geoForm("geopoint", appearance = "placement-map")
    showQuestion(controller)

    onRoot().performTouchInput {
      swipe(start = Offset(centerX, 120f), end = Offset(centerX - 120f, 120f))
    }
    onNodeWithText("Panned • Crosshair active").assertExists()
    onNodeWithText("Add point").performClick()

    assertNotEquals(PARIS_FIX.longitude, controller.point()?.longitude)
  }

  @Test
  fun pannableGeopoint_recenter_returnsToTheFix() = runComposeUiTest {
    val controller = geoForm("geopoint", appearance = "placement-map")
    showQuestion(controller)
    onRoot().performTouchInput {
      swipe(start = Offset(centerX, 120f), end = Offset(centerX - 120f, 120f))
    }

    onNodeWithContentDescription("Recenter on GPS").performClick()
    onNodeWithText("Capture location").performClick()

    assertEquals(parisPoint, controller.point())
  }

  @Test
  fun pannableGeoshape_addPoint_appendsAVertexEachTime() = runComposeUiTest {
    val controller = geoForm("geoshape", appearance = "placement-map")
    showQuestion(controller)

    repeat(3) { onNodeWithText("Capture vertex").performClick() }

    assertEquals(3, controller.shapeVertexCount())
  }

  @Test
  fun pannableGeoshape_undo_removesTheLastVertex() = runComposeUiTest {
    val controller = geoForm("geoshape", appearance = "placement-map")
    showQuestion(controller)
    repeat(3) { onNodeWithText("Capture vertex").performClick() }

    onNodeWithContentDescription("Undo").performClick()

    assertEquals(2, controller.shapeVertexCount())
  }

  @Test
  fun pannableGeoshape_redo_restoresTheRemovedVertex() = runComposeUiTest {
    val controller = geoForm("geoshape", appearance = "placement-map")
    showQuestion(controller)
    repeat(3) { onNodeWithText("Capture vertex").performClick() }
    onNodeWithContentDescription("Undo").performClick()

    onNodeWithContentDescription("Redo").performClick()

    assertEquals(3, controller.shapeVertexCount())
  }

  @Test
  fun pannableGeoshape_clear_removesEveryVertexAndCanBeUndone() = runComposeUiTest {
    val controller = geoForm("geoshape", appearance = "placement-map")
    showQuestion(controller)
    repeat(3) { onNodeWithText("Capture vertex").performClick() }

    onNodeWithContentDescription("Clear all vertices").performClick()
    assertEquals(0, controller.shapeVertexCount())
    onNodeWithContentDescription("Undo").performClick()

    assertEquals(3, controller.shapeVertexCount())
  }

  @Test
  fun gpsGeotrace_addPoint_recordsTheFixAsAVertex() = runComposeUiTest {
    val controller = geoForm("geotrace")
    showQuestion(controller)
    onNodeWithText("Start walking").assertIsEnabled()

    onNodeWithText("Add point").performClick()

    assertEquals(1, controller.traceVertexCount())
  }
}
