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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

class LikertScaleWidgetTest {

  @Test
  fun formatLikertOptionLabel_stripsMatchingOrdinalPrefixes() {
    assertEquals("Severe Dieback", formatLikertOptionLabel("1 - Severe Dieback", stepNumber = 1))
    assertEquals("Moderate Stress", formatLikertOptionLabel("2 – Moderate Stress", stepNumber = 2))
    assertEquals("Acceptable", formatLikertOptionLabel("3 — Acceptable", stepNumber = 3))
    assertEquals("Vigorous Crown", formatLikertOptionLabel("4. Vigorous Crown", stepNumber = 4))
    assertEquals(
      "Optimal Closed Canopy",
      formatLikertOptionLabel("5: Optimal Closed Canopy", stepNumber = 5),
    )
    assertEquals("Strongly Agree", formatLikertOptionLabel("5) Strongly Agree", stepNumber = 5))
    assertEquals("Strongly Agree", formatLikertOptionLabel("(5) Strongly Agree", stepNumber = 5))
  }

  @Test
  fun formatLikertOptionLabel_preservesUnprefixedRangesAndMismatchedNumbers() {
    assertEquals(
      "Strongly Disagree",
      formatLikertOptionLabel("Strongly Disagree", stepNumber = 1),
    )
    assertEquals("1-5 saplings", formatLikertOptionLabel("1-5 saplings", stepNumber = 1))
    assertEquals("1", formatLikertOptionLabel("1", stepNumber = 1))
    assertEquals(
      "2 - Moderate Stress",
      formatLikertOptionLabel("2 - Moderate Stress", stepNumber = 1),
    )
    assertEquals("10 - Ten", formatLikertOptionLabel("10 - Ten", stepNumber = 1))
  }

  @Test
  fun likertSelectOneControl_updatesSelectionAndFormatsExampleCatalogLabels() {
    val formDef = WorkbenchExampleForm.ALL_FIELD_TYPES.formDefinition.proto
    val controller = FormWizardController(formDef = formDef)
    val likertStep =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().first {
        it.control.canonicalPath == "/data/canopy_health_rating"
      }

    assertTrue(likertStep.control.appearance.split(' ').contains("likert"))
    val formattedLabels =
      likertStep.control.options.mapIndexed { index, option ->
        formatLikertOptionLabel(option.label.text, index + 1)
      }
    assertEquals(
      listOf(
        "Severe Dieback",
        "Moderate Stress",
        "Acceptable",
        "Vigorous Crown",
        "Optimal Closed Canopy",
      ),
      formattedLabels,
    )

    controller.updateString("/data/canopy_health_rating", "excellent")
    val updatedStep =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().first {
        it.control.canonicalPath == "/data/canopy_health_rating"
      }
    assertEquals("excellent", updatedStep.control.fieldState.value?.scalar_value?.string_value)
  }

  @Test
  fun likertQuickAppearance_advancesToNextStepOnSelect() {
    val xml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Likert Form</h:title>
          <model>
            <instance>
              <data id="likert_form">
                <rating/>
                <follow_up/>
              </data>
            </instance>
            <bind nodeset="/data/rating" type="select1"/>
            <bind nodeset="/data/follow_up" type="string"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/rating" appearance="likert quick">
            <label>Rate canopy health</label>
            <item><label>1 - Poor</label><value>poor</value></item>
            <item><label>2 - Good</label><value>good</value></item>
          </select1>
          <input ref="/data/follow_up">
            <label>Follow-up note</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val controller = FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(xml))
    assertEquals(0, controller.currentStepIndex)
    controller.updateString("/data/rating", "good")
    controller.nextStep()
    assertEquals(1, controller.currentStepIndex)
  }
}
