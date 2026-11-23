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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import openforis.collectone.v1.forms.GeoPoint
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.GeoLocationFix
import org.openforis.collectone.v2.core.forms.ui.GeoLocationSource
import org.openforis.collectone.v2.core.forms.ui.LocalGeoLocationSource
import org.openforis.collectone.v2.core.forms.ui.QuestionControlCard

/** Location questions record only real fixes from the host's [GeoLocationSource]. */
@OptIn(ExperimentalTestApi::class)
class LocationQuestionUiTest {

  private val geoPointFormXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Plot Survey</h:title>
        <model>
          <instance>
            <data id="plot_survey" version="1"><plot_location/></data>
          </instance>
          <bind nodeset="/data/plot_location" type="geopoint"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/plot_location"><label>Plot location</label></input>
      </h:body>
    </h:html>
    """

  private class FakeGeoLocationSource(fix: GeoLocationFix?) : GeoLocationSource {
    override val currentFix: StateFlow<GeoLocationFix?> = MutableStateFlow(fix)
  }

  private fun FormWizardController.capturedPoint(): GeoPoint? =
    (currentStep as FormWizardStep.QuestionStep)
      .control
      .fieldState
      .value
      ?.scalar_value
      ?.geopoint_value

  private fun runQuestionTest(
    source: GeoLocationSource?,
    block: androidx.compose.ui.test.ComposeUiTest.(FormWizardController) -> Unit,
  ) = runComposeUiTest {
    val controller =
      FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(geoPointFormXml))
    val step = controller.currentStep as FormWizardStep.QuestionStep
    setContent {
      AppTheme {
        if (source == null) {
          QuestionControlCard(step.control, controller, showValidationErrors = false)
        } else {
          CompositionLocalProvider(LocalGeoLocationSource provides source) {
            QuestionControlCard(step.control, controller, showValidationErrors = false)
          }
        }
      }
    }
    block(controller)
  }

  @Test
  fun withoutAHostSource_waitsForLocationAndCannotCapture() =
    runQuestionTest(source = null) { controller ->
      onNodeWithText("Waiting for GPS location…").assertExists()
      onNodeWithText("Capture location").assertIsNotEnabled()

      assertNull(controller.capturedPoint())
    }

  @Test
  fun geoShapeWithoutAHostSource_cannotWalkOrAddPoints() = runComposeUiTest {
    val controller =
      FormWizardController(
        formDef =
          XFormsXmlSerializer.deserializeFormDef(
            geoPointFormXml.replace("type=\"geopoint\"", "type=\"geoshape\"")
          )
      )
    val step = controller.currentStep as FormWizardStep.QuestionStep
    setContent {
      AppTheme { QuestionControlCard(step.control, controller, showValidationErrors = false) }
    }

    onNodeWithText("Waiting for GPS location…").assertExists()
    onNodeWithText("Start walking").assertIsNotEnabled()
    onNodeWithText("Add point").assertIsNotEnabled()
  }

  @Test
  fun withAFix_capturesTheReportedLocation() =
    runQuestionTest(
      source =
        FakeGeoLocationSource(
          GeoLocationFix(
            latitude = 48.858370,
            longitude = 2.294481,
            altitudeMeters = 35.0,
            accuracyMeters = 4.0,
          )
        )
    ) { controller ->
      onNodeWithText("Waiting for GPS location…").assertDoesNotExist()
      onNodeWithText("Capture location").assertIsEnabled().performClick()

      assertEquals(
        GeoPoint(
          latitude = 48.858370,
          longitude = 2.294481,
          altitude_meters = 35.0,
          accuracy_meters = 4.0,
        ),
        controller.capturedPoint(),
      )
    }
}
