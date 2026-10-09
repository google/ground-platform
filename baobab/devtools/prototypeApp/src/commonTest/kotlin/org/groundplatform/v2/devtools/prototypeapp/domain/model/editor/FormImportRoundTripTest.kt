/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.groundplatform.v2.devtools.prototypeapp.data.seed.FormEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.builtInFallbackXFormsXmlForForm

/** `EditorForm` → published XForms XML → [FormImport] → the same Form. */
class FormImportRoundTripTest {
  private fun roundTrip(form: EditorForm): EditorForm {
    val draft = SurveyEditorSamples.draft()
    val entry = SurveyEditorForm("f1", form)
    val xml = draft.copy(forms = listOf(entry)).publishedFormXml(entry)
    return assertNotNull(FormImport.fromXml(xml, form.formId, availability = form.availability))
      .form
  }

  private fun assertSameQuestions(expected: EditorForm, actual: EditorForm) {
    assertEquals(expected.formId, actual.formId)
    assertEquals(expected.title, actual.title)
    assertEquals(expected.questions.map { it.name }, actual.questions.map { it.name })
    assertEquals(expected.questions.map { it.label }, actual.questions.map { it.label })
    assertEquals(expected.questions.map { it.type }, actual.questions.map { it.type })
    assertEquals(expected.questions.map { it.required }, actual.questions.map { it.required })
    assertEquals(
      expected.questions.map { it.choiceDatasetId },
      actual.questions.map { it.choiceDatasetId },
    )
    assertEquals(
      expected.questions.map { it.allowAddEntity },
      actual.questions.map { it.allowAddEntity },
    )
    assertEquals(
      expected.questions.map { q -> q.choices.map { it.value to it.label } },
      actual.questions.map { q -> q.choices.map { it.value to it.label } },
    )
  }

  @Test
  fun shadeTreeVisit_survivesTheRoundTrip() {
    val expected = FormEditorSamples.shadeTreeVisit()
    val actual = roundTrip(expected)
    assertSameQuestions(expected, actual)
    // Relevance is re-linked by question name → key.
    val byName = actual.questions.associateBy { it.name }
    val count = byName.getValue("shade_tree_count")
    assertEquals(byName.getValue("has_shade_trees").key, count.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, count.relevance?.operator)
    assertEquals("yes", count.relevance?.value)
    val pestNotes = byName.getValue("pest_notes")
    assertEquals(byName.getValue("observed_issues").key, pestNotes.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.INCLUDES, pestNotes.relevance?.operator)
    assertEquals("pests", pestNotes.relevance?.value)
    assertNull(byName.getValue("visit_date").relevance)
    assertEquals("Stand near the center of the plot.", byName.getValue("farm_location").hint)
  }

  @Test
  fun parcelBoundaryCheck_survivesTheRoundTrip() {
    val expected = FormEditorSamples.parcelBoundaryCheck()
    assertSameQuestions(expected, roundTrip(expected))
  }

  @Test
  fun derivedSampleForms_areStableAcrossPublishAndImport() {
    for (survey in PrototypeFakeSurveysData.defaultSampleSurveys()) {
      for (form in PrototypeFakeSurveysData.formsForSurvey(survey.id)) {
        val xml = PrototypeFakeSurveysData.builtInFallbackXFormsXmlForForm(form)
        val imported = assertNotNull(FormImport.fromXml(xml, form.id, form.title), form.id).form
        assertSameQuestions(imported, roundTrip(imported))
      }
    }
  }

  @Test
  fun datasetBackedSelectOneAndSelectMultiple_importWithChoiceDatasetIdAndRoundTrip() {
    val draft = SurveyEditorSamples.draft()
    val base = FormEditorSamples.shadeTreeVisit()
    val withDatasetChoices =
      base.copy(
        questions =
          base.questions.map { q ->
            when (q.name) {
              "has_shade_trees" ->
                q.copy(choiceDatasetId = "farmers", allowAddEntity = true, choices = emptyList())
              "observed_issues" -> q.copy(choiceDatasetId = "tree_species", choices = emptyList())
              else -> q
            }
          }
      )
    val entry = SurveyEditorForm("f1", withDatasetChoices)
    val catalog = draft.datasets.map { it.toEditorDataset(entry.key) }
    val xml = draft.copy(forms = listOf(entry)).publishedFormXml(entry)
    val imported = assertNotNull(FormImport.fromXml(xml, withDatasetChoices.formId)).form
    val byName = imported.questions.associateBy { it.name }
    assertEquals("farmers", byName.getValue("has_shade_trees").choiceDatasetId)
    assertEquals(true, byName.getValue("has_shade_trees").allowAddEntity)
    assertEquals(
      ChoiceSource.DATA_TABLE,
      byName.getValue("has_shade_trees").effectiveChoiceSource(catalog),
    )
    assertEquals("tree_species", byName.getValue("observed_issues").choiceDatasetId)
    assertEquals(false, byName.getValue("observed_issues").allowAddEntity)
    assertEquals(
      ChoiceSource.DATA_TABLE,
      byName.getValue("observed_issues").effectiveChoiceSource(catalog),
    )
  }

  @Test
  fun forestStructureDisplayLogic_importsRelativeSelectedOnSelectOneWithoutWarnings() {
    val xml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa">
        <h:head>
          <h:title>Forest Structure Form</h:title>
          <model>
            <itext>
              <translation lang="English">
                <text id="habitat"><value>Habitat</value></text>
                <text id="other_describe"><value>Other (describe)</value></text>
                <text id="other_habitat_description"><value>Describe other habitat</value></text>
                <text id="evidence_of_logging"><value>Evidence of logging</value></text>
                <text id="evidence_of_logging_description"><value>Describe logging evidence</value></text>
                <text id="evidence_of_fire"><value>Evidence of fire/burning</value></text>
                <text id="evidence_of_fire_description"><value>Describe fire/burning evidence</value></text>
                <text id="evidence_of_grazing"><value>Evidence of grazing</value></text>
                <text id="evidence_of_grazing_description"><value>Describe grazing evidence</value></text>
                <text id="yes"><value>Yes</value></text>
                <text id="no"><value>No</value></text>
              </translation>
            </itext>
            <instance>
              <Data id="ForestStructure">
                <Habitat/>
                <OtherHabitatDescription/>
                <EvidenceOfLogging/>
                <EvidenceOfLoggingDescription/>
                <EvidenceOfFire/>
                <EvidenceOfFireDescription/>
                <EvidenceOfGrazing/>
                <EvidenceOfGrazingDescription/>
              </Data>
            </instance>
            <bind nodeset="Habitat" type="select1" required="false()"/>
            <bind nodeset="OtherHabitatDescription" type="string" required="false()" relevant="selected(../Habitat, 'OTR')"/>
            <bind nodeset="EvidenceOfLogging" type="select1" required="false()"/>
            <bind nodeset="EvidenceOfLoggingDescription" type="string" required="false()" relevant="selected(../EvidenceOfLogging, 'Y')"/>
            <bind nodeset="EvidenceOfFire" type="select1" required="false()"/>
            <bind nodeset="EvidenceOfFireDescription" type="string" required="false()" relevant="selected(../EvidenceOfFire, 'Y')"/>
            <bind nodeset="EvidenceOfGrazing" type="select1" required="false()"/>
            <bind nodeset="EvidenceOfGrazingDescription" type="string" required="false()" relevant="selected(../EvidenceOfGrazing, 'Y')"/>
          </model>
        </h:head>
        <h:body>
          <group>
            <select1 ref="Habitat">
              <label ref="jr:itext('habitat')"/>
              <item><label ref="jr:itext('other_describe')"/><value>OTR</value></item>
            </select1>
            <input ref="OtherHabitatDescription">
              <label ref="jr:itext('other_habitat_description')"/>
            </input>
          </group>
          <select1 ref="EvidenceOfLogging">
            <label ref="jr:itext('evidence_of_logging')"/>
            <item><label ref="jr:itext('yes')"/><value>Y</value></item>
            <item><label ref="jr:itext('no')"/><value>N</value></item>
          </select1>
          <input ref="EvidenceOfLoggingDescription">
            <label ref="jr:itext('evidence_of_logging_description')"/>
          </input>
          <select1 ref="EvidenceOfFire">
            <label ref="jr:itext('evidence_of_fire')"/>
            <item><label ref="jr:itext('yes')"/><value>Y</value></item>
            <item><label ref="jr:itext('no')"/><value>N</value></item>
          </select1>
          <input ref="EvidenceOfFireDescription">
            <label ref="jr:itext('evidence_of_fire_description')"/>
          </input>
          <select1 ref="EvidenceOfGrazing">
            <label ref="jr:itext('evidence_of_grazing')"/>
            <item><label ref="jr:itext('yes')"/><value>Y</value></item>
            <item><label ref="jr:itext('no')"/><value>N</value></item>
          </select1>
          <input ref="EvidenceOfGrazingDescription">
            <label ref="jr:itext('evidence_of_grazing_description')"/>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val imported = assertNotNull(FormImport.fromXml(xml))
    assertEquals(emptyList(), imported.notes)
    assertEquals(emptyList(), FormEditorValidator.validate(imported.form))
    val byName = imported.form.questions.associateBy { it.name }

    val otherHabitat = byName.getValue("OtherHabitatDescription")
    assertEquals(byName.getValue("Habitat").key, otherHabitat.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, otherHabitat.relevance?.operator)
    assertEquals("OTR", otherHabitat.relevance?.value)

    val loggingDesc = byName.getValue("EvidenceOfLoggingDescription")
    assertEquals(byName.getValue("EvidenceOfLogging").key, loggingDesc.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, loggingDesc.relevance?.operator)
    assertEquals("Y", loggingDesc.relevance?.value)

    val fireDesc = byName.getValue("EvidenceOfFireDescription")
    assertEquals(byName.getValue("EvidenceOfFire").key, fireDesc.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, fireDesc.relevance?.operator)
    assertEquals("Y", fireDesc.relevance?.value)

    val grazingDesc = byName.getValue("EvidenceOfGrazingDescription")
    assertEquals(byName.getValue("EvidenceOfGrazing").key, grazingDesc.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, grazingDesc.relevance?.operator)
    assertEquals("Y", grazingDesc.relevance?.value)
  }

  @Test
  fun validationAndCustomRootRelevance_importCleanlyAndRoundTrip() {
    val xml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa">
        <h:head>
          <h:title>Household Survey</h:title>
          <model>
            <instance>
              <HouseholdSurvey id="HouseholdSurvey1">
                <SurveyorCode/>
                <SurveyorID/>
                <HeadOfHouseholdAge/>
                <ChildBirthdate/>
                <ChildColors/>
                <Email/>
              </HouseholdSurvey>
            </instance>
            <bind nodeset="/HouseholdSurvey/SurveyorCode" type="string" constraint="string-length(.) &gt;= 2 and string-length(.) &lt;= 8 and regex(., '[A-Za-z]{2}[0-9]{2}')" jr:constraintMsg="Invalid code"/>
            <bind nodeset="/HouseholdSurvey/SurveyorID" type="string" relevant="selected(/HouseholdSurvey/SurveyorCode, '')"/>
            <bind nodeset="/HouseholdSurvey/HeadOfHouseholdAge" type="int" constraint=". &gt;= 0 and . &lt; 120" jr:constraintMsg="Age must be between 0 and 120."/>
            <bind nodeset="/HouseholdSurvey/ChildBirthdate" type="date" constraint=". &lt;= today()" jr:constraintMsg="No future dates"/>
            <bind nodeset="/HouseholdSurvey/ChildColors" type="select" constraint="count-selected(.) = 2" jr:constraintMsg="Pick 2 colors"/>
            <bind nodeset="/HouseholdSurvey/Email" type="string" constraint="regex(., '^[^@\s]+@[^@\s]+\.[^@\s]+$')"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/HouseholdSurvey/SurveyorCode"><label>Surveyor Code</label></input>
          <input ref="/HouseholdSurvey/SurveyorID"><label>Surveyor ID</label></input>
          <input ref="/HouseholdSurvey/HeadOfHouseholdAge"><label>Age</label></input>
          <input ref="/HouseholdSurvey/ChildBirthdate"><label>Birthdate</label></input>
          <select ref="/HouseholdSurvey/ChildColors">
            <label>Colors</label>
            <item><label>Red</label><value>red</value></item>
            <item><label>Blue</label><value>blue</value></item>
            <item><label>Green</label><value>green</value></item>
          </select>
          <input ref="/HouseholdSurvey/Email"><label>Email</label></input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val imported = assertNotNull(FormImport.fromXml(xml))
    assertEquals(emptyList(), imported.notes)
    val byName = imported.form.questions.associateBy { it.name }

    val codeVal = assertNotNull(byName.getValue("SurveyorCode").validation)
    assertEquals("2", codeVal.min)
    assertEquals("8", codeVal.max)
    assertEquals(TextPattern.CUSTOM, codeVal.pattern)
    assertEquals("[A-Za-z]{2}[0-9]{2}", codeVal.customPattern)
    assertEquals("Invalid code", codeVal.message)

    val idRel = assertNotNull(byName.getValue("SurveyorID").relevance)
    assertEquals(byName.getValue("SurveyorCode").key, idRel.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, idRel.operator)
    assertEquals("", idRel.value)

    val ageVal = assertNotNull(byName.getValue("HeadOfHouseholdAge").validation)
    assertEquals("0", ageVal.min)
    assertEquals("120", ageVal.max)
    assertEquals("Age must be between 0 and 120.", ageVal.message)

    val dateVal = assertNotNull(byName.getValue("ChildBirthdate").validation)
    assertEquals(DateRule.NOT_IN_FUTURE, dateVal.dateRule)
    assertEquals("No future dates", dateVal.message)

    val colorsVal = assertNotNull(byName.getValue("ChildColors").validation)
    assertEquals("2", colorsVal.min)
    assertEquals("2", colorsVal.max)
    assertEquals("Pick 2 colors", colorsVal.message)

    val emailVal = assertNotNull(byName.getValue("Email").validation)
    assertEquals(TextPattern.EMAIL, emailVal.pattern)
  }
}
