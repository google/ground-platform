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
import kotlin.test.assertIs
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review_and_submit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_this_question

/** The titles [FormWizardController] gives steps and questions are translatable [FormUiText]. */
class FormWizardControllerTextTest {

  private val formXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Site Visit</h:title>
        <model>
          <instance>
            <data id="site" version="1">
              <species/><unlabeled_note/><site><soil/><slope/></site>
            </data>
          </instance>
          <bind nodeset="/data/species" type="string"/>
          <bind nodeset="/data/unlabeled_note" type="string"/>
          <bind nodeset="/data/site/soil" type="string"/>
          <bind nodeset="/data/site/slope" type="string"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/species"><label>Tree species</label></input>
        <input ref="/data/unlabeled_note"/>
        <group ref="/data/site" appearance="field-list">
          <label>Site</label>
          <input ref="/data/site/soil"><label>Soil</label></input>
          <input ref="/data/site/slope"/>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun controller() =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(formXml))

  @Test
  fun questionStep_titleIsTheQuestionLabel() {
    val step = assertIs<FormWizardStep.QuestionStep>(controller().steps[0])

    assertEquals(FormUiText.Verbatim("Tree species"), step.title)
  }

  @Test
  fun questionStep_withoutALabel_isTitledWithItsDataName() {
    val step = assertIs<FormWizardStep.QuestionStep>(controller().steps[1])

    assertEquals(FormUiText.Verbatim("unlabeled_note"), step.title)
  }

  @Test
  fun fieldListGroupStep_titleIsTheGroupLabel() {
    val step = assertIs<FormWizardStep.FieldListGroupStep>(controller().steps[2])

    assertEquals(FormUiText.Verbatim("Site"), step.title)
  }

  @Test
  fun summaryStep_titleIsLocalized() {
    val step = assertIs<FormWizardStep.SummaryStep>(controller().steps.last())

    assertEquals(FormUiText.Resource(Res.string.forms_review_and_submit), step.title)
  }

  @Test
  fun questionTitleFor_aLabeledQuestion_isItsLabel() {
    assertEquals(
      FormUiText.Verbatim("Tree species"),
      controller().questionTitleFor("/data/species"),
    )
  }

  @Test
  fun questionTitleFor_anUnlabeledQuestionInAFieldList_isTheGroupLabel() {
    assertEquals(FormUiText.Verbatim("Site"), controller().questionTitleFor("/data/site/slope"))
  }

  @Test
  fun questionTitleFor_anUnknownPath_isTheLocalizedStandIn() {
    assertEquals(
      FormUiText.Resource(Res.string.forms_this_question),
      controller().questionTitleFor("/data/missing"),
    )
  }
}
