/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.ui

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
}
