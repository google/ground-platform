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
import kotlin.test.assertNull
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_yes

class FormWizardStepsTest {

  private val formXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Plot</h:title>
        <model>
          <instance>
            <data id="plot">
              <site><slope/><aspect/></site>
              <trees><species/><dbh/><alive/><notes/></trees>
            </data>
          </instance>
          <bind nodeset="/data/site/slope" type="decimal"/>
          <bind nodeset="/data/site/aspect" type="string"/>
          <bind nodeset="/data/trees/species" type="string"/>
          <bind nodeset="/data/trees/dbh" type="decimal"/>
          <bind nodeset="/data/trees/alive" type="boolean"/>
          <bind nodeset="/data/trees/notes" type="string"/>
        </model>
      </h:head>
      <h:body>
        <group ref="/data/site" appearance="field-list">
          <label>Site</label>
          <input ref="/data/site/slope"><label>Slope</label></input>
          <input ref="/data/site/aspect"><label>Aspect</label></input>
        </group>
        <group ref="/data/trees">
          <label>Trees</label>
          <repeat nodeset="/data/trees">
            <select1 ref="/data/trees/species">
              <label>Species</label>
              <item><label>Baobab</label><value>baobab</value></item>
            </select1>
            <input ref="/data/trees/dbh"><label>DBH</label></input>
            <input ref="/data/trees/alive"><label>Alive</label></input>
            <input ref="/data/trees/notes"><label>Notes</label></input>
          </repeat>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun controller() =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(formXml))

  @Test
  fun fieldListGroup_becomesOneStepWithAllItsQuestions() {
    val steps = controller().steps

    val site = assertIs<FormWizardStep.FieldListGroupStep>(steps.first())
    assertEquals("group:/data/site", site.stepKey)
    assertEquals("Site", site.groupLabel)
    assertEquals(
      listOf("/data/site/slope", "/data/site/aspect"),
      site.controls.map { it.canonicalPath },
    )
  }

  @Test
  fun stepsAlwaysEndWithTheSummary() {
    assertIs<FormWizardStep.SummaryStep>(controller().steps.last())
  }

  @Test
  fun repeatEntries_comeBeforeTheirHubAndCarryTheirContext() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/trees")
    controller.addRepeatInstanceAndOpen("/data/trees")

    val steps = controller.steps
    val hubIndex = steps.indexOfFirst { it is FormWizardStep.RepeatHubStep }
    val entrySteps = steps.filter { it.repeatContext != null }
    assertEquals(8, entrySteps.size)
    assertEquals(true, entrySteps.all { steps.indexOf(it) < hubIndex })
    val secondEntry = entrySteps.last().repeatContext!!
    assertEquals(2, secondEntry.repeatIndex)
    assertEquals(2, secondEntry.totalInstances)
    assertEquals("Trees #2", secondEntry.instanceLabel)
    assertEquals(listOf("Trees #2"), entrySteps.last().breadcrumbs)
    val hub = assertIs<FormWizardStep.RepeatHubStep>(steps[hubIndex])
    assertEquals(FormUiText.Verbatim("Trees (2)"), hub.title)
  }

  @Test
  fun repeatHubSummaries_previewUpToThreeAnswers() {
    val controller = controller()
    controller.addRepeatInstanceAndOpen("/data/trees")
    val entry = controller.steps.first { it.repeatContext != null }.repeatContext!!.instancePath
    controller.updateString("$entry/species", "baobab")
    controller.updateDouble("$entry/dbh", 42.0)
    controller.updateBoolean("$entry/alive", true)
    controller.updateString("$entry/notes", "hollow trunk")

    val hub = controller.steps.filterIsInstance<FormWizardStep.RepeatHubStep>().single()
    val summary = hub.summaries.single()

    assertEquals(4, summary.answeredCount)
    // Choice codes show their labels, whole decimals drop ".0", and booleans use a translated word.
    assertEquals(
      listOf(
        FormUiText.Verbatim("Baobab"),
        FormUiText.Verbatim("42"),
        FormUiText.Resource(Res.string.forms_yes),
      ),
      summary.previewParts,
    )
  }

  @Test
  fun emptyRepeat_stillHasAHub() {
    val hub = controller().steps.filterIsInstance<FormWizardStep.RepeatHubStep>().single()

    assertEquals("repeat-hub:/data/trees", hub.stepKey)
    assertEquals(emptyList(), hub.summaries)
  }

  @Test
  fun formatControlPreview_formatsTemporalValuesAsIsoAndSkipsYearZeroSentinel() {
    val temporalXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Visit</h:title>
          <model>
            <instance><data id="visit"><d/><t/><dt/></data></instance>
            <bind nodeset="/data/d" type="date"/>
            <bind nodeset="/data/t" type="time"/>
            <bind nodeset="/data/dt" type="dateTime"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/d"><label>Date</label></input>
          <input ref="/data/t"><label>Time</label></input>
          <input ref="/data/dt"><label>Recorded</label></input>
        </h:body>
      </h:html>
      """
        .trimIndent()
    val controller =
      FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(temporalXml))
    fun previewAt(path: String) =
      FormWizardSteps.formatControlPreview(
        controller.steps
          .filterIsInstance<FormWizardStep.QuestionStep>()
          .first { it.control.canonicalPath == path }
          .control
      )

    controller.updateDate("/data/d", 987, 3, 5)
    assertEquals(FormUiText.Verbatim("0987-03-05"), previewAt("/data/d"))

    controller.updateDate("/data/d", 0, 1, 1)
    assertNull(previewAt("/data/d"))

    controller.updateTime("/data/t", 7, 5, 9)
    assertEquals(FormUiText.Verbatim("07:05:09"), previewAt("/data/t"))

    controller.updateTimestamp("/data/dt", 1_794_241_920L)
    assertEquals(FormUiText.Verbatim("2026-11-09T16:32:00Z"), previewAt("/data/dt"))
  }
}
