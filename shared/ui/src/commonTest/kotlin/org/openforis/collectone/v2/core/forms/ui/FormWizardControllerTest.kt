/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.core.forms.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

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

  @Test
  fun booleanInputOptionsAndDisplay_usesYesAndNoWithoutTrueOrFalse() {
    assertEquals(listOf(true to "Yes", false to "No"), BooleanInputOptions)
    assertEquals(
      "Yes",
      formatFieldValueForDisplay(
        FieldValue(scalar_value = TypedValue(bool_value = true)),
        DataType.TYPE_BOOLEAN,
      ),
    )
    assertEquals(
      "No",
      formatFieldValueForDisplay(
        FieldValue(scalar_value = TypedValue(bool_value = false)),
        DataType.TYPE_BOOLEAN,
      ),
    )
    assertEquals("(Unanswered)", formatFieldValueForDisplay(null, DataType.TYPE_BOOLEAN))
  }

  @Test
  fun stepValidationBlockerMessage_omitsSkipPromptForRequiredQuestions() {
    val formDef = XFormsXmlSerializer.deserializeFormDef(sampleFormXml)
    val controller = FormWizardController(formDef = formDef)

    // Step 0 (/data/species) is required -> cannot skip, message must not mention skipping
    assertFalse(controller.isCurrentStepOptional)
    assertFalse(controller.nextStep(enforceValidation = true))
    assertTrue(controller.showCurrentStepValidationWarning)
    assertEquals(
      "Please resolve validation errors above to continue.",
      stepValidationBlockerMessage(
        isWaitingForLocationCapture = controller.isCurrentStepWaitingForLocationCapture,
        isStepOptional = controller.isCurrentStepOptional,
      ),
    )

    // Answer step 0 and advance to step 1 (/data/height_m), which is optional with a constraint
    controller.updateString("/data/species", "Adansonia digitata")
    assertTrue(controller.nextStep(enforceValidation = true))
    assertTrue(controller.isCurrentStepOptional)

    // Enter invalid value (-5.0 violates . > 0) -> optional question mentions skipping
    controller.updateDouble("/data/height_m", -5.0)
    assertFalse(controller.nextStep(enforceValidation = true))
    assertTrue(controller.showCurrentStepValidationWarning)
    assertEquals(
      "Please resolve validation errors above or skip to continue.",
      stepValidationBlockerMessage(
        isWaitingForLocationCapture = controller.isCurrentStepWaitingForLocationCapture,
        isStepOptional = controller.isCurrentStepOptional,
      ),
    )

    // Waiting for location capture takes precedence
    assertEquals(
      "Please capture a location or drop a pin before proceeding.",
      stepValidationBlockerMessage(isWaitingForLocationCapture = true, isStepOptional = false),
    )
    assertEquals(
      "Please capture a location or drop a pin before proceeding.",
      stepValidationBlockerMessage(isWaitingForLocationCapture = true, isStepOptional = true),
    )
  }

  @Test
  fun repeatGroupQuestionSteps_bindAndRetainAnswersAcrossInstances() {
    val formDef = WorkbenchExampleForm.ALL_FIELD_TYPES.formDefinition.proto
    val controller = FormWizardController(formDef = formDef)

    // Repeat #1 steps must carry [1] in their canonicalPath and "#1" in instanceLabel
    val repeat1Steps =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().filter {
        it.repeatContext?.repeatIndex == 1
      }
    assertTrue(repeat1Steps.isNotEmpty())
    assertTrue(repeat1Steps.all { it.control.canonicalPath.contains("/sampled_trees[1]/") })
    assertEquals(
      "Individual Tree Measurements (Repeat Group) #1",
      repeat1Steps.first().repeatContext?.instanceLabel,
    )

    // Update Repeat #1 answers via controller and verify the recomputed QuestionSteps retain them
    controller.updateString("/data/sampled_trees[1]/tree_tag", "T-101")
    controller.updateDouble("/data/sampled_trees[1]/stem_dbh_cm", 36.4)

    // Add Repeat #2 and populate its answers
    controller.addRepeatInstanceAndOpen("/data/sampled_trees")
    controller.updateString("/data/sampled_trees[2]/tree_tag", "T-102")
    controller.updateDouble("/data/sampled_trees[2]/stem_dbh_cm", 22.0)

    val stepsByPath =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().associateBy {
        it.control.canonicalPath
      }

    assertEquals(
      "T-101",
      stepsByPath["/data/sampled_trees[1]/tree_tag"]
        ?.control
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.string_value,
    )
    assertEquals(
      36.4,
      stepsByPath["/data/sampled_trees[1]/stem_dbh_cm"]
        ?.control
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.double_value,
    )
    assertEquals(
      "T-102",
      stepsByPath["/data/sampled_trees[2]/tree_tag"]
        ?.control
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.string_value,
    )
    assertEquals(
      22.0,
      stepsByPath["/data/sampled_trees[2]/stem_dbh_cm"]
        ?.control
        ?.fieldState
        ?.value
        ?.scalar_value
        ?.double_value,
    )
  }

  @Test
  fun repeatHub_buildsPreviewSummaries_andSupportsOpenRepeatHubNavigation() {
    val formDef = WorkbenchExampleForm.ALL_FIELD_TYPES.formDefinition.proto
    val controller = FormWizardController(formDef = formDef)

    // Populate instance 1
    controller.updateString("/data/sampled_trees[1]/tree_tag", "Oak-42")
    controller.updateDouble("/data/sampled_trees[1]/stem_dbh_cm", 45.0)

    // Add instance 2 and populate
    controller.addRepeatInstanceAndOpen("/data/sampled_trees")
    controller.updateString("/data/sampled_trees[2]/tree_tag", "Pine-17")

    // Open repeat hub via controller
    controller.openRepeatHub("/data/sampled_trees")
    val current = controller.currentStep
    assertTrue(current is FormWizardStep.RepeatHubStep)
    assertEquals("/data/sampled_trees", current.repeatGroup.canonicalPath)
    assertEquals(2, current.summaries.size)

    val summary1 = current.summaries[0]
    assertEquals(1, summary1.repeatIndex)
    assertTrue(summary1.previewText.contains("Oak-42") || summary1.previewText.contains("45"))

    val summary2 = current.summaries[1]
    assertEquals(2, summary2.repeatIndex)
    assertTrue(summary2.previewText.contains("Pine-17"))
  }

  @Test
  fun cascadingSelects_evaluatesParentInfoAndPrunesInvalidChildAnswers() {
    val cascadeXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Cascade Test</h:title>
          <model>
            <instance>
              <data id="cascade_test">
                <country/>
                <region/>
              </data>
            </instance>
            <bind nodeset="/data/country" type="string"/>
            <bind nodeset="/data/region" type="string"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/country">
            <label>Country</label>
            <item><label>Kenya</label><value>kenya</value></item>
            <item><label>Uganda</label><value>uganda</value></item>
          </select1>
          <select1 ref="/data/region">
            <label>Region</label>
            <item><label>Nairobi</label><value>nairobi</value></item>
            <item><label>Mombasa</label><value>mombasa</value></item>
            <item><label>Kampala</label><value>kampala</value></item>
            <item><label>All</label><value>all_region</value></item>
          </select1>
        </h:body>
      </h:html>
      """
        .trimIndent()
    val baseDef = XFormsXmlSerializer.deserializeFormDef(cascadeXml)
    val updatedComponents =
      baseDef.view!!.components.map { comp ->
        val ctrl = comp.control ?: return@map comp
        if (ctrl.field_ref == "region") {
          comp.copy(
            control =
              ctrl.copy(
                parent_question_key = "country",
                choices =
                  ctrl.choices.map { choice ->
                    when (choice.value_) {
                      "nairobi",
                      "mombasa" -> choice.copy(parent_value = "kenya")
                      "kampala" -> choice.copy(parent_value = "uganda")
                      else -> choice
                    }
                  },
              )
          )
        } else comp
      }
    val formDef = baseDef.copy(view = baseDef.view!!.copy(components = updatedComponents))
    val controller = FormWizardController(formDef = formDef)
    val regionStep =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().first {
        it.control.canonicalPath == "/data/region"
      }

    // Initially parent is unanswered:
    val initialInfo = controller.parentQuestionInfoFor(regionStep.control)
    assertNotNull(initialInfo)
    assertFalse(initialInfo.isAnswered)
    assertEquals("Country", initialInfo.label)

    // Answer country = "kenya":
    controller.updateString("/data/country", "kenya")
    val kenyaInfo = controller.parentQuestionInfoFor(regionStep.control)
    assertNotNull(kenyaInfo)
    assertTrue(kenyaInfo.isAnswered)
    assertEquals(listOf("kenya"), kenyaInfo.selectedValues)

    // Answer region = "nairobi":
    controller.updateString("/data/region", "nairobi")
    assertEquals(
      "nairobi",
      controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value,
    )

    // Change country to "uganda" -> "nairobi" is invalid for uganda, so region must be cleared:
    controller.updateString("/data/country", "uganda")
    val ugandaInfo = controller.parentQuestionInfoFor(regionStep.control)
    assertNotNull(ugandaInfo)
    assertTrue(ugandaInfo.isAnswered)
    assertEquals(listOf("uganda"), ugandaInfo.selectedValues)
    assertTrue(
      controller.formState
        .findFieldState("/data/region")
        ?.value
        ?.scalar_value
        ?.string_value
        .isNullOrBlank()
    )

    // Answer region = "kampala" (valid for uganda):
    controller.updateString("/data/region", "kampala")
    assertEquals(
      "kampala",
      controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value,
    )

    // Clear country -> region must be cleared because parent is unanswered:
    controller.clearField("/data/country")
    val clearedInfo = controller.parentQuestionInfoFor(regionStep.control)
    assertNotNull(clearedInfo)
    assertFalse(clearedInfo.isAnswered)
    assertTrue(
      controller.formState
        .findFieldState("/data/region")
        ?.value
        ?.scalar_value
        ?.string_value
        .isNullOrBlank()
    )

    // Set choice with empty parent_value ("all_region"):
    controller.updateString("/data/country", "kenya")
    controller.updateString("/data/region", "all_region")
    assertEquals(
      "all_region",
      controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value,
    )
    // Switch to uganda: "all_region" has empty parent_value, so it stays valid!
    controller.updateString("/data/country", "uganda")
    assertEquals(
      "all_region",
      controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value,
    )
  }
}
