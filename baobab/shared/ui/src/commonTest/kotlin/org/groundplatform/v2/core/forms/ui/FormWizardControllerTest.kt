/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.ui

import groundplatform.v2.forms.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

class FormWizardControllerTest {

  private val sampleFormXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms"
            xmlns:h="http://www.w3.org/1999/xhtml"
            xmlns:jr="http://openrosa.org/javarosa">
      <h:head>
        <h:title>Baobab Tree Survey</h:title>
        <model>
          <instance>
            <data id="baobab_survey" version="1">
              <species/>
              <height_m/>
              <giant_notes/>
              <branches>
                <branch_length_m/>
              </branches>
            </data>
          </instance>
          <bind nodeset="/data/species" type="string" required="true()"/>
          <bind nodeset="/data/height_m" type="decimal" constraint=". > 0" jr:constraintMsg="Height must be positive"/>
          <bind nodeset="/data/giant_notes" type="string" relevant="/data/height_m >= 20"/>
          <bind nodeset="/data/branches/branch_length_m" type="decimal"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/species">
          <label>Tree Species</label>
        </input>
        <input ref="/data/height_m">
          <label>Height (meters)</label>
        </input>
        <input ref="/data/giant_notes">
          <label>Giant Baobab Notes</label>
        </input>
        <group ref="/data/branches">
          <label>Branch Measurements</label>
          <repeat nodeset="/data/branches">
            <input ref="/data/branches/branch_length_m">
              <label>Branch Length (m)</label>
            </input>
          </repeat>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  @Test
  fun singleQuestionNavigationAndDynamicRelevanceUpdatesSteps() {
    val formDef = XFormsXmlSerializer.deserializeFormDef(sampleFormXml)
    val controller = FormWizardController(formDef = formDef)

    // Initially:
    // 0: /data/species
    // 1: /data/height_m
    // (/data/giant_notes is not relevant because height_m is empty)
    // 2: RepeatHubStep for /data/branches (0 instances initially)
    // 3: SummaryStep
    assertEquals(4, controller.totalSteps)
    assertEquals(0, controller.currentStepIndex)
    val step0 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/species", step0.control.canonicalPath)

    // Attempt to advance without filling required species -> blocked by validation
    assertFalse(controller.nextStep(enforceValidation = true))
    assertTrue(controller.showCurrentStepValidationWarning)
    assertEquals(0, controller.currentStepIndex)

    // Fill species and advance to height_m
    controller.updateString("/data/species", "Adansonia digitata")
    assertTrue(controller.nextStep(enforceValidation = true))
    assertEquals(1, controller.currentStepIndex)
    val step1 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/height_m", step1.control.canonicalPath)

    // Set height_m = 25.0 -> /data/giant_notes dynamically becomes relevant!
    controller.updateDouble("/data/height_m", 25.0)
    assertEquals(5, controller.totalSteps)
    assertEquals(1, controller.currentStepIndex) // Anchored on /data/height_m

    // Advance -> now lands on the newly relevant /data/giant_notes step!
    assertTrue(controller.nextStep(enforceValidation = true))
    val step2 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/giant_notes", step2.control.canonicalPath)
  }

  @Test
  fun addRepeatInstanceNavigatesToNewRepeatQuestionAndFinalizes() {
    val formDef = XFormsXmlSerializer.deserializeFormDef(sampleFormXml)
    var latestRecordXml = ""
    val controller =
      FormWizardController(
        formDef = formDef,
        onRecordUpdated = { record, _ ->
          latestRecordXml = XFormsXmlSerializer.serializeRecordInstance(record)
        },
      )

    controller.updateString("/data/species", "Adansonia grandidieri")
    controller.updateDouble("/data/height_m", 12.5)
    controller.updateDouble("/data/branches[1]/branch_length_m", 4.2)

    // Add a second repeat instance and verify navigation jumps to /data/branches[2]/branch_length_m
    controller.addRepeatInstanceAndOpen("/data/branches")
    val current = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/branches[2]/branch_length_m", current.control.canonicalPath)
    assertEquals(2, current.repeatContext?.repeatIndex)
    assertEquals(2, current.repeatContext?.totalInstances)

    controller.updateDouble("/data/branches[2]/branch_length_m", 5.8)
    assertTrue(latestRecordXml.contains("Adansonia grandidieri"))

    val finalization = controller.finalizeForm()
    assertIs<FinalizationResult.Success>(finalization)
  }

  @Test
  fun geoPointTriggerAndCoordinateFormatting_supportsCaptureLocationAndAddPointWhenPanned() {
    // 1. Trigger label switches between "Capture location" (unpanned GPS) and "Add point" (panned)
    assertEquals("Capture location", geoPointPrimaryTriggerLabel(isPanned = false))
    assertEquals("Add point", geoPointPrimaryTriggerLabel(isPanned = true))

    // 2. Map pan appearance detection
    assertTrue(isGeoPointPanAllowed("placement-map"))
    assertTrue(isGeoPointPanAllowed("map"))
    assertTrue(isGeoPointPanAllowed("maps"))
    assertFalse(isGeoPointPanAllowed(""))
    assertFalse(isGeoPointPanAllowed("minimal"))

    // 3. Coordinate and accuracy formatting for UI display
    assertEquals(
      "1.292066° S, 36.821946° E",
      formatGeoPointCoordinates(latitude = -1.292066, longitude = 36.821946),
    )
    assertEquals("±3.2 m", formatGeoPointAccuracy(3.2))
    assertEquals("±14.2 m", formatGeoPointAccuracy(14.2))
  }

  @Test
  fun geoPointCaptureGating_disablesNextUntilCaptured_andSkipWorksForOptionalQuestions() {
    val geoFormXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Plot Survey</h:title>
          <model>
            <instance>
              <data id="plot_survey" version="1">
                <plot_location/>
                <optional_notes/>
              </data>
            </instance>
            <bind nodeset="/data/plot_location" type="geopoint"/>
            <bind nodeset="/data/optional_notes" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/plot_location" appearance="placement-map">
            <label>Capture Plot Location</label>
          </input>
          <input ref="/data/optional_notes">
            <label>Optional Notes</label>
          </input>
        </h:body>
      </h:html>
      """
    val formDef = XFormsXmlSerializer.deserializeFormDef(geoFormXml)
    val controller = FormWizardController(formDef = formDef)

    // Step 0: /data/plot_location (geopoint, optional)
    val step0 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/plot_location", step0.control.canonicalPath)
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)
    assertTrue(controller.isCurrentStepOptional)

    // "Next" is disabled / blocked while location capture is pending
    assertFalse(controller.nextStep(enforceValidation = true))
    assertEquals(0, controller.currentStepIndex)
    assertTrue(controller.showCurrentStepValidationWarning)

    // "Skip" button action advances without enforcing validation
    assertTrue(controller.nextStep(enforceValidation = false))
    assertEquals(1, controller.currentStepIndex)
    val step1 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/optional_notes", step1.control.canonicalPath)
    assertFalse(controller.isCurrentStepWaitingForLocationCapture)
    assertTrue(controller.isCurrentStepOptional)

    // Go back to geopoint step
    controller.previousStep()
    assertEquals(0, controller.currentStepIndex)
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)

    // Capture location -> now Next is unblocked!
    controller.updateGeoPoint(
      path = "/data/plot_location",
      latitude = -1.292066,
      longitude = 36.821946,
      altitudeMeters = 1680.0,
      accuracyMeters = 3.2,
    )
    assertFalse(controller.isCurrentStepWaitingForLocationCapture)
    assertTrue(controller.nextStep(enforceValidation = true))
    assertEquals(1, controller.currentStepIndex)

    // Go back and Undo point selection -> Next is disabled again!
    controller.previousStep()
    assertEquals(0, controller.currentStepIndex)
    assertFalse(controller.isCurrentStepWaitingForLocationCapture)

    // Undo action clears field
    controller.clearField("/data/plot_location")
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)
    assertFalse(controller.nextStep(enforceValidation = true))
  }

  @Test
  fun geoTraceAndGeoShapeCaptureGating_requiresMinimumVertices_andSupportsUndoAndSkip() {
    val geometryFormXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Geometry Survey</h:title>
          <model>
            <instance>
              <data id="geometry_survey" version="1">
                <trail_path/>
                <field_boundary/>
                <notes/>
              </data>
            </instance>
            <bind nodeset="/data/trail_path" type="geotrace"/>
            <bind nodeset="/data/field_boundary" type="geoshape"/>
            <bind nodeset="/data/notes" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/trail_path" appearance="placement-map">
            <label>Trail Path</label>
          </input>
          <input ref="/data/field_boundary" appearance="walk-or-draw">
            <label>Field Boundary</label>
          </input>
          <input ref="/data/notes">
            <label>Notes</label>
          </input>
        </h:body>
      </h:html>
      """
    val formDef = XFormsXmlSerializer.deserializeFormDef(geometryFormXml)
    val controller = FormWizardController(formDef = formDef)

    val p1 =
      GeoPoint(
        latitude = -1.292066,
        longitude = 36.821946,
        altitude_meters = 1680.0,
        accuracy_meters = 3.2,
      )
    val p2 =
      GeoPoint(
        latitude = -1.293100,
        longitude = 36.822500,
        altitude_meters = 1682.0,
        accuracy_meters = 3.5,
      )
    val p3 =
      GeoPoint(
        latitude = -1.294200,
        longitude = 36.823800,
        altitude_meters = 1685.0,
        accuracy_meters = 4.0,
      )

    // 1. Step 0: /data/trail_path (geotrace) requires at least 2 vertices
    assertEquals(0, controller.currentStepIndex)
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)

    // Adding 1 vertex is not enough for linestring (needs >= 2)
    controller.updateGeoTrace("/data/trail_path", listOf(p1))
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)
    assertFalse(controller.nextStep(enforceValidation = true))

    // Adding 2nd vertex satisfies linestring requirement
    controller.updateGeoTrace("/data/trail_path", listOf(p1, p2))
    assertFalse(controller.isCurrentStepWaitingForLocationCapture)
    assertTrue(controller.nextStep(enforceValidation = true))
    assertEquals(1, controller.currentStepIndex)

    // 2. Step 1: /data/field_boundary (geoshape) requires at least 3 vertices
    val step1 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/field_boundary", step1.control.canonicalPath)
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)

    // Adding 2 vertices is not enough for closed polygon (needs >= 3)
    controller.updateGeoShape("/data/field_boundary", listOf(p1, p2))
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)
    assertFalse(controller.nextStep(enforceValidation = true))

    // Adding 3rd vertex satisfies polygon requirement
    controller.updateGeoShape("/data/field_boundary", listOf(p1, p2, p3))
    assertFalse(controller.isCurrentStepWaitingForLocationCapture)

    // Undo last vertex (back to 2) -> gates again!
    controller.updateGeoShape("/data/field_boundary", listOf(p1, p2))
    assertTrue(controller.isCurrentStepWaitingForLocationCapture)
    assertFalse(controller.nextStep(enforceValidation = true))

    // Skip allows advancing optional question even when incomplete
    assertTrue(controller.isCurrentStepOptional)
    assertTrue(controller.nextStep(enforceValidation = false))
    assertEquals(2, controller.currentStepIndex)
    val step2 = assertIs<FormWizardStep.QuestionStep>(controller.currentStep)
    assertEquals("/data/notes", step2.control.canonicalPath)
  }
}
