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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ViewComponent
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.messageKey
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FormState

/**
 * Cascading selects in the Form editor: authoring helpers, validation, the XForms the editor
 * publishes (and how the form engine filters it), and the round trip back into the editor.
 */
class CascadingSelectsEditorTest {
  private val country =
    EditorQuestion(
      key = "q1",
      name = "country",
      label = "Country",
      type = EditorQuestionType.SELECT_ONE,
      choices = listOf(EditorChoice("kenya", "Kenya"), EditorChoice("uganda", "Uganda")),
    )

  private val region =
    EditorQuestion(
      key = "q2",
      name = "region",
      label = "Region",
      type = EditorQuestionType.SELECT_ONE,
      parentQuestionKey = "q1",
      choices =
        listOf(
          EditorChoice("nairobi", "Nairobi", parentValue = "kenya"),
          EditorChoice("coast", "Coast", parentValue = "kenya"),
          EditorChoice("central", "Central", parentValue = "uganda"),
          EditorChoice("other", "Other"),
        ),
    )

  private fun form(vararg questions: EditorQuestion, sections: List<EditorSection> = emptyList()) =
    EditorForm(
      formId = "cascade",
      title = "Cascade",
      questions = questions.toList(),
      sections = sections,
    )

  private fun issues(form: EditorForm) =
    FormEditorValidator.validate(form).filter { it.questionKey == region.key }

  private fun session(form: EditorForm) = FormSession(EditorXFormsGenerator.compile(form).proto)

  private fun FormState.controls(): List<ComponentState.ControlState> {
    fun walk(list: List<ComponentState>): List<ComponentState.ControlState> = list.flatMap { item ->
      when (item) {
        is ComponentState.ControlState -> listOf(item)
        is ComponentState.GroupState -> walk(item.children)
        is ComponentState.RepeatGroupState -> item.instances.flatMap { walk(it.children) }
      }
    }
    return walk(rootComponents)
  }

  private fun FormState.optionValues(path: String): List<String> =
    assertNotNull(controls().firstOrNull { it.canonicalPath == path }, path).options.map {
      it.value
    }

  private fun ViewComponent.allControls(): List<ControlDef> =
    listOfNotNull(control) +
      group?.components.orEmpty().flatMap { it.allControls() } +
      repeat?.components.orEmpty().flatMap { it.allControls() }

  private fun compiledControl(form: EditorForm, name: String): ControlDef =
    EditorXFormsGenerator.compile(form)
      .proto
      .view!!
      .components
      .flatMap { it.allControls() }
      .first { it.field_ref.substringAfterLast('/') == name }

  // Authoring helpers

  @Test
  fun eligibleCascadeParents_areTheEarlierChoiceQuestions() {
    val notes =
      EditorQuestion(key = "q0", name = "notes", label = "Notes", type = EditorQuestionType.TEXT)
    val district = region.copy(key = "q3", name = "district", parentQuestionKey = "q2")

    val parents = form(notes, country, region, district).eligibleCascadeParents("q3")

    assertEquals(listOf("q1", "q2"), parents.map { it.key })
    assertEquals(emptyList(), form(country, region).eligibleCascadeParents("q1"))
  }

  @Test
  fun cascadeParentOf_resolvesKeysAndImportedNamesButNotNonChoiceQuestions() {
    val byName = region.copy(parentQuestionKey = "country")
    val notes =
      EditorQuestion(key = "q9", name = "notes", label = "Notes", type = EditorQuestionType.TEXT)

    assertEquals(country, form(country, region).cascadeParentOf(region))
    assertEquals(country, form(country, byName).cascadeParentOf(byName))
    assertEquals(null, form(notes, region).cascadeParentOf(region.copy(parentQuestionKey = "q9")))
    assertEquals(null, form(region).cascadeParentOf(region))
  }

  @Test
  fun cascadeChildrenOf_listsTheQuestionsFilteredByTheParent() {
    val unrelated = country.copy(key = "q3", name = "language")

    assertEquals(
      listOf("q2"),
      form(country, region, unrelated).cascadeChildrenOf("q1").map { it.key },
    )
    assertEquals(emptyList(), form(country, region).cascadeChildrenOf("q2"))
  }

  // Validation

  @Test
  fun validCascade_hasNoIssues() {
    assertEquals(emptyList(), issues(form(country, region)))
  }

  @Test
  fun cascadeOnADeletedParent_isAnError() {
    val messages = issues(form(region)).map { it.message.messageKey to it.isWarning }

    assertEquals(listOf(EditorIssueMessage.CASCADE_PARENT_DELETED to false), messages)
  }

  @Test
  fun cascadeOnALaterParent_isAnError() {
    val messages = issues(form(region, country)).map { it.message.messageKey }

    assertEquals(listOf(EditorIssueMessage.CASCADE_PARENT_LATER), messages)
  }

  @Test
  fun cascadeOnANonChoiceParent_isAnError() {
    val messages =
      issues(form(country.copy(type = EditorQuestionType.TEXT, choices = emptyList()), region))
        .map { it.message.messageKey }

    assertEquals(listOf(EditorIssueMessage.CASCADE_PARENT_NOT_CHOICE), messages)
  }

  @Test
  fun choicesTiedToUnknownParentValues_areAWarning() {
    val stale =
      region.copy(
        choices = region.choices + EditorChoice("dodoma", "Dodoma", parentValue = "tanzania")
      )

    val issue = issues(form(country, stale)).single()

    assertEquals(EditorIssueMessage.CASCADE_CHOICE_PARENT_VALUE_UNKNOWN, issue.message.messageKey)
    assertTrue(issue.isWarning)
  }

  // Published XForms and the form engine

  @Test
  fun publishedChild_filtersItsItemsetOnTheParentSelections() {
    val control = compiledControl(form(country, region), "region")

    assertEquals("country", control.parent_question_key)
    assertEquals(
      "(string-length(parent_value) = 0 or selected(/data/country, parent_value))",
      control.itemset?.nodeset_filter,
    )
  }

  @Test
  fun publishedChild_listsTheChoicesOfTheSelectedParent() {
    val session = session(form(country, region))

    session.updateString("/data/country", "uganda")

    assertEquals(listOf("central", "other"), session.state.optionValues("/data/region"))
  }

  @Test
  fun publishedChildOfAMultiSelectParent_listsTheChoicesOfEverySelectedParent() {
    val countries = country.copy(type = EditorQuestionType.SELECT_MULTIPLE)
    val session = session(form(countries, region))

    session.updateMultiSelect("/data/country", listOf("kenya", "uganda"))

    assertEquals(
      listOf("nairobi", "coast", "central", "other"),
      session.state.optionValues("/data/region"),
    )
  }

  @Test
  fun publishedChildWithABrokenParent_isNotFiltered() {
    val control = compiledControl(form(region), "region")

    assertEquals("", control.parent_question_key)
    assertEquals("", control.itemset?.nodeset_filter.orEmpty())
  }

  @Test
  fun publishedChildInARepeat_filtersOnItsOwnInstancesParent() {
    val plots = EditorSection(id = "plots", title = "Plots", repeatable = true)
    val form =
      form(
        country.copy(sectionId = "plots"),
        region.copy(sectionId = "plots"),
        sections = listOf(plots),
      )
    assertEquals(
      "(string-length(parent_value) = 0 or selected(current()/../country, parent_value))",
      compiledControl(form, "region").itemset?.nodeset_filter,
    )
    val session = session(form)
    val repeat = session.state.rootComponents.filterIsInstance<ComponentState.RepeatGroupState>()
    val repeatPath = repeat.single().canonicalPath
    if (session.state.controls().none { it.canonicalPath.endsWith("[1]/region") }) {
      session.addRepeatInstance(repeatPath)
    }
    session.addRepeatInstance(repeatPath)

    session.updateString("$repeatPath[1]/country", "kenya")
    session.updateString("$repeatPath[2]/country", "uganda")

    assertEquals(
      listOf("nairobi", "coast", "other"),
      session.state.optionValues("$repeatPath[1]/region"),
    )
    assertEquals(listOf("central", "other"), session.state.optionValues("$repeatPath[2]/region"))
  }

  // Round trip

  private fun roundTrip(form: EditorForm): EditorForm {
    val entry = SurveyEditorForm("f1", form)
    val xml = SurveyEditorSamples.draft().copy(forms = listOf(entry)).publishedFormXml(entry)
    return assertNotNull(FormImport.fromXml(xml, form.formId)).form
  }

  @Test
  fun cascade_survivesPublishAndImport() {
    val actual = roundTrip(form(country, region))

    val byName = actual.questions.associateBy { it.name }
    val importedRegion = byName.getValue("region")
    assertEquals(byName.getValue("country").key, importedRegion.parentQuestionKey)
    assertEquals(
      region.choices.map { it.value to it.parentValue },
      importedRegion.choices.map { it.value to it.parentValue },
    )
    assertEquals(emptyList(), FormEditorValidator.validate(actual).filter { !it.isWarning })
  }

  @Test
  fun cascadeOnAMultiSelectParent_survivesPublishAndImport() {
    val countries = country.copy(type = EditorQuestionType.SELECT_MULTIPLE)

    val actual = roundTrip(form(countries, region))

    val byName = actual.questions.associateBy { it.name }
    assertEquals(EditorQuestionType.SELECT_MULTIPLE, byName.getValue("country").type)
    assertEquals(byName.getValue("country").key, byName.getValue("region").parentQuestionKey)
  }

  @Test
  fun cascadeInARepeat_survivesPublishAndImport() {
    val plots = EditorSection(id = "plots", title = "Plots", repeatable = true)

    val actual =
      roundTrip(
        form(
          country.copy(sectionId = "plots"),
          region.copy(sectionId = "plots"),
          sections = listOf(plots),
        )
      )

    val byName = actual.questions.associateBy { it.name }
    assertEquals(byName.getValue("country").key, byName.getValue("region").parentQuestionKey)
  }

  /** An XForm written outside the editor, with the given `choice_filter` predicate on `city`. */
  private fun externalForm(cityFilter: String, countryTag: String = "select1") =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>External</h:title>
        <model>
          <instance><data id="external"><country/><city/></data></instance>
          <instance id="cities">
            <root>
              <item><name>nairobi</name><label>Nairobi</label><country>kenya</country></item>
              <item><name>kampala</name><label>Kampala</label><country>uganda</country></item>
            </root>
          </instance>
          <bind nodeset="/data/country" type="string"/>
          <bind nodeset="/data/city" type="string"/>
        </model>
      </h:head>
      <h:body>
        <$countryTag ref="/data/country">
          <label>Country</label>
          <item><label>Kenya</label><value>kenya</value></item>
          <item><label>Uganda</label><value>uganda</value></item>
        </$countryTag>
        <select1 ref="/data/city">
          <label>City</label>
          <itemset nodeset="instance('cities')/root/item[$cityFilter]">
            <value ref="name"/>
            <label ref="label"/>
          </itemset>
        </select1>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun importedCity(xml: String): Pair<EditorQuestion, EditorQuestion> {
    val imported = assertNotNull(FormImport.fromXml(xml, "external")).form
    val byName = imported.questions.associateBy { it.name }
    return byName.getValue("country") to byName.getValue("city")
  }

  @Test
  fun xlsFormEqualsChoiceFilter_importsAsACascade() {
    val (countryQ, city) = importedCity(externalForm("country = /data/country"))

    assertEquals(countryQ.key, city.parentQuestionKey)
    assertEquals(
      listOf("nairobi" to "kenya", "kampala" to "uganda"),
      city.choices.map { it.value to it.parentValue },
    )
  }

  @Test
  fun xlsFormSelectedChoiceFilter_importsAsACascade() {
    val (countryQ, city) =
      importedCity(externalForm("selected(/data/country, country)", countryTag = "select"))

    assertEquals(countryQ.key, city.parentQuestionKey)
    assertEquals(listOf("kenya", "uganda"), city.choices.map { it.parentValue })
  }

  @Test
  fun legacyEditorChoiceFilter_stillImportsAsACascade() {
    val (countryQ, city) =
      importedCity(externalForm("parent_value = /data/country or parent_value = ''"))

    assertEquals(countryQ.key, city.parentQuestionKey)
  }

  @Test
  fun choiceFilterAgainstALiteral_isNotACascade() {
    val (_, city) = importedCity(externalForm("country = 'kenya'"))

    assertEquals("", city.parentQuestionKey)
  }

  @Test
  fun datasetBackedCascade_filtersByMatchingParentColumnAndRoundTrips() {
    val villages =
      EditorDataset(
        id = "villages",
        displayName = "Villages",
        isMapLayer = false,
        keyProperty = "code",
        labelProperty = "name",
        properties =
          listOf(
            EditorDatasetProperty("code", "Code", EditorPropertyKind.TEXT),
            EditorDatasetProperty("name", "Name", EditorPropertyKind.TEXT),
            EditorDatasetProperty("country", "Country", EditorPropertyKind.TEXT),
          ),
        rows =
          listOf(
            EditorDatasetRow(
              name = "karen",
              label = "Karen",
              values = mapOf("code" to "karen", "name" to "Karen", "country" to "kenya"),
            ),
            EditorDatasetRow(
              name = "entebbe",
              label = "Entebbe",
              values = mapOf("code" to "entebbe", "name" to "Entebbe", "country" to "uganda"),
            ),
            EditorDatasetRow(
              name = "border",
              label = "Border Market",
              values = mapOf("code" to "border", "name" to "Border Market"),
            ),
          ),
      )
    val village =
      EditorQuestion(
        key = "q2",
        name = "village",
        label = "Village",
        type = EditorQuestionType.SELECT_ONE,
        choiceSource = ChoiceSource.DATA_TABLE,
        choiceDatasetId = "villages",
        parentQuestionKey = "q1",
      )
    val cascadeForm = form(country, village)
    assertEquals(emptyList(), FormEditorValidator.validate(cascadeForm, listOf(villages)))

    val compiled =
      EditorXFormsGenerator.compile(cascadeForm, inlineRows = true, datasets = listOf(villages))
        .proto
    val villageControl =
      compiled.view!!.components.flatMap { it.allControls() }.first { it.field_ref == "village" }
    assertEquals("country", villageControl.parent_question_key)
    assertEquals(
      "(string-length(country) = 0 or selected(/data/country, country))",
      villageControl.itemset?.nodeset_filter,
    )

    val session = FormSession(compiled)
    session.updateString("/data/country", "kenya")
    assertEquals(listOf("karen", "border"), session.state.optionValues("/data/village"))
    session.updateString("/data/country", "uganda")
    assertEquals(listOf("entebbe", "border"), session.state.optionValues("/data/village"))

    val xml =
      org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer.serialize(
        compiled,
        prettyPrint = true,
      )
    val reimported = assertNotNull(FormImport.fromXml(xml, cascadeForm.formId)).form
    val byName = reimported.questions.associateBy { it.name }
    assertEquals("villages", byName.getValue("village").choiceDatasetId)
    assertEquals(byName.getValue("country").key, byName.getValue("village").parentQuestionKey)
  }
}
