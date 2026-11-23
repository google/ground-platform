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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ViewComponent
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.ResolvedChoiceOption
import org.openforis.collectone.v2.core.forms.model.ResolvedLabel
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

class CascadingSelectsTest {

  /**
   * Country (select one) → regions (select multiple) → district (select one), with parent values
   * patched onto the choices the way the form editor stores them.
   */
  private fun cascadeForm(
    parentKeys: Map<String, String> = mapOf("regions" to "country", "district" to "regions")
  ): FormDef {
    val xml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Cascade</h:title>
          <model>
            <instance>
              <data id="cascade"><country/><regions/><district/></data>
            </instance>
            <bind nodeset="/data/country" type="string"/>
            <bind nodeset="/data/regions" type="string"/>
            <bind nodeset="/data/district" type="string"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/country">
            <label>Country</label>
            <item><label>Kenya</label><value>kenya</value></item>
            <item><label>Uganda</label><value>uganda</value></item>
          </select1>
          <select ref="/data/regions">
            <label>Regions</label>
            <item><label>Nairobi</label><value>nairobi</value></item>
            <item><label>Coast</label><value>coast</value></item>
            <item><label>Central</label><value>central</value></item>
            <item><label>Anywhere</label><value>anywhere</value></item>
          </select>
          <select1 ref="/data/district">
            <label>District</label>
            <item><label>Westlands</label><value>westlands</value></item>
            <item><label>Kilifi</label><value>kilifi</value></item>
          </select1>
        </h:body>
      </h:html>
      """
        .trimIndent()
    val parentValues =
      mapOf(
        "nairobi" to "kenya",
        "coast" to "kenya",
        "central" to "uganda",
        "westlands" to "nairobi",
        "kilifi" to "coast",
      )
    val base = XFormsXmlSerializer.deserializeFormDef(xml)
    val components =
      base.view!!.components.map { component ->
        val control = component.control ?: return@map component
        val parentKey = parentKeys[control.field_ref] ?: return@map component
        component.copy(
          control =
            control.copy(
              parent_question_key = parentKey,
              choices =
                control.choices.map { choice ->
                  parentValues[choice.value_]?.let { choice.copy(parent_value = it) } ?: choice
                },
            )
        )
      }
    return base.copy(view = base.view!!.copy(components = components))
  }

  private fun FormWizardController.values(path: String): List<String> =
    CascadingSelects.selectedValues(formState.findFieldState(path))

  @Test
  fun selectedValues_splitsSpaceSeparatedSelectAnswers() {
    val controller = FormWizardController(formDef = cascadeForm(parentKeys = emptyMap()))

    controller.updateString("/data/regions", " nairobi  coast ")

    assertEquals(listOf("nairobi", "coast"), controller.values("/data/regions"))
  }

  @Test
  fun selectedValues_isEmptyForUnansweredOrMissingFields() {
    val controller = FormWizardController(formDef = cascadeForm())

    assertEquals(emptyList(), controller.values("/data/regions"))
    assertEquals(emptyList(), CascadingSelects.selectedValues(null))
  }

  @Test
  fun multiSelectChild_keepsOnlyChoicesTheNewParentAllows() {
    val controller = FormWizardController(formDef = cascadeForm())
    controller.updateString("/data/country", "kenya")
    controller.updateMultiSelect("/data/regions", listOf("nairobi", "central", "anywhere"))

    controller.updateString("/data/country", "uganda")

    // "nairobi" belongs to Kenya; "anywhere" has no parent value, so it always stays.
    assertEquals(listOf("central", "anywhere"), controller.values("/data/regions"))
  }

  @Test
  fun multiSelectChild_isClearedWhenNoChoiceSurvives() {
    val controller = FormWizardController(formDef = cascadeForm())
    controller.updateString("/data/country", "kenya")
    controller.updateMultiSelect("/data/regions", listOf("nairobi", "coast"))

    controller.updateString("/data/country", "uganda")

    assertEquals(emptyList(), controller.values("/data/regions"))
  }

  @Test
  fun pruning_cascadesToGrandchildren() {
    val controller = FormWizardController(formDef = cascadeForm())
    controller.updateString("/data/country", "kenya")
    controller.updateMultiSelect("/data/regions", listOf("nairobi"))
    controller.updateString("/data/district", "westlands")

    // Uganda drops Nairobi, so Westlands (a Nairobi district) must go too.
    controller.updateString("/data/country", "uganda")

    assertEquals(emptyList(), controller.values("/data/regions"))
    assertEquals(emptyList(), controller.values("/data/district"))
  }

  @Test
  fun pruning_leavesAnswersThatAreNotInTheChildsOptions() {
    val controller = FormWizardController(formDef = cascadeForm())
    controller.updateString("/data/country", "kenya")
    // A value with no matching option (for example from an older form version) is kept.
    controller.updateString("/data/district", "legacy_code")

    controller.updateMultiSelect("/data/regions", listOf("coast"))

    assertEquals(listOf("legacy_code"), controller.values("/data/district"))
  }

  @Test
  fun pruning_stopsOnCyclicParentKeys() {
    // Misconfigured: country and regions name each other as parents.
    val controller =
      FormWizardController(
        formDef = cascadeForm(parentKeys = mapOf("regions" to "country", "country" to "regions"))
      )
    controller.updateString("/data/country", "kenya")
    controller.updateMultiSelect("/data/regions", listOf("nairobi"))

    // Uganda drops Nairobi; the emptied regions then clear country, whose pruning is not
    // re-entered.
    controller.updateString("/data/country", "uganda")

    assertEquals(emptyList(), controller.values("/data/regions"))
    assertEquals(emptyList(), controller.values("/data/country"))
  }

  @Test
  fun parentQuestionInfo_reportsMultiSelectParentSelections() {
    val controller = FormWizardController(formDef = cascadeForm())
    val district =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().first {
        it.control.canonicalPath == "/data/district"
      }
    controller.updateString("/data/country", "kenya")
    controller.updateMultiSelect("/data/regions", listOf("nairobi", "coast"))

    val info = controller.parentQuestionInfoFor(district.control)

    assertEquals("/data/regions", info?.path)
    assertEquals("Regions", info?.label)
    assertEquals(listOf("nairobi", "coast"), info?.selectedValues)
    assertTrue(info?.isAnswered == true)
  }

  @Test
  fun parentQuestionInfo_isNullForQuestionsWithoutAParent() {
    val controller = FormWizardController(formDef = cascadeForm())
    val country =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().first {
        it.control.canonicalPath == "/data/country"
      }

    assertNull(controller.parentQuestionInfoFor(country.control))
  }

  @Test
  fun allowedOptions_keepsUnfilteredChoicesAndChoicesOfTheSelectedParents() {
    val options =
      listOf(option("nairobi", "kenya"), option("kampala", "uganda"), option("anywhere", ""))

    assertEquals(
      listOf("nairobi", "anywhere"),
      CascadingSelects.allowedOptions(options, listOf("kenya")).map { it.value },
    )
    assertEquals(
      listOf("nairobi", "kampala", "anywhere"),
      CascadingSelects.allowedOptions(options, listOf("kenya", "uganda")).map { it.value },
    )
  }

  @Test
  fun allowedOptions_listsEveryOptionWhenTheQuestionHasNoParent() {
    val options = listOf(option("nairobi", "kenya"), option("kampala", "uganda"))

    assertEquals(options, CascadingSelects.allowedOptions(options, null))
  }

  /**
   * XLSForm `choice_filter` cascade: the engine filters the city itemset by the country answer, and
   * the city question has no parent key.
   */
  private fun choiceFilterForm(cityAppearance: String = ""): FormDef =
    XFormsXmlSerializer.deserializeFormDef(
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Choice filter</h:title>
          <model>
            <instance>
              <data id="choice_filter"><country/><city/><street/></data>
            </instance>
            <instance id="cities">
              <root>
                <item><name>nairobi</name><label>Nairobi</label><country>kenya</country></item>
                <item><name>mombasa</name><label>Mombasa</label><country>kenya</country></item>
                <item><name>kampala</name><label>Kampala</label><country>uganda</country></item>
                <item><name>border</name><label>Border post</label><country>any</country></item>
              </root>
            </instance>
            <instance id="streets">
              <root>
                <item><name>moi_avenue</name><label>Moi Avenue</label><city>nairobi</city></item>
                <item><name>kampala_road</name><label>Kampala Road</label><city>kampala</city></item>
              </root>
            </instance>
            <bind nodeset="/data/country" type="string"/>
            <bind nodeset="/data/city" type="string"/>
            <bind nodeset="/data/street" type="string"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/country">
            <label>Country</label>
            <item><label>Kenya</label><value>kenya</value></item>
            <item><label>Uganda</label><value>uganda</value></item>
          </select1>
          <select1 ref="/data/city" appearance="$cityAppearance">
            <label>City</label>
            <itemset nodeset="instance('cities')/root/item[country = /data/country or country = 'any']">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <select1 ref="/data/street">
            <label>Street</label>
            <itemset nodeset="instance('streets')/root/item[city = /data/city]">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
        </h:body>
      </h:html>
      """
        .trimIndent()
    )

  @Test
  fun choiceFilterChild_isClearedWhenTheParentFiltersItsAnswerOut() {
    val controller = FormWizardController(formDef = choiceFilterForm())
    controller.updateString("/data/country", "kenya")
    controller.updateString("/data/city", "nairobi")
    controller.updateString("/data/street", "moi_avenue")

    controller.updateString("/data/country", "uganda")

    // Nairobi isn't a Ugandan city, and Moi Avenue (a Nairobi street) goes with it.
    assertEquals(emptyList(), controller.values("/data/city"))
    assertEquals(emptyList(), controller.values("/data/street"))
  }

  @Test
  fun choiceFilterChild_keepsAnAnswerTheNewParentStillAllows() {
    val controller = FormWizardController(formDef = choiceFilterForm())
    controller.updateString("/data/country", "kenya")
    controller.updateString("/data/city", "border")

    controller.updateString("/data/country", "uganda")

    assertEquals(listOf("border"), controller.values("/data/city"))
  }

  @Test
  fun choiceFilterChild_thatAllowsAddingChoices_isNotPruned() {
    val controller = FormWizardController(formDef = choiceFilterForm(cityAppearance = "add-entity"))
    controller.updateString("/data/country", "kenya")
    controller.updateString("/data/city", "nairobi")

    controller.updateString("/data/country", "uganda")

    assertEquals(listOf("nairobi"), controller.values("/data/city"))
  }

  /** Form editor cascade inside a repeat: each plot's region is filtered by that plot's country. */
  private fun repeatCascadeForm(): FormDef {
    val base =
      XFormsXmlSerializer.deserializeFormDef(
        """
        <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
          <h:head>
            <h:title>Plots</h:title>
            <model>
              <instance>
                <data id="plots"><plots><country/><region/></plots></data>
              </instance>
              <bind nodeset="/data/plots/country" type="string"/>
              <bind nodeset="/data/plots/region" type="string"/>
            </model>
          </h:head>
          <h:body>
            <group ref="/data/plots">
              <label>Plots</label>
              <repeat nodeset="/data/plots">
                <select1 ref="/data/plots/country">
                  <label>Country</label>
                  <item><label>Kenya</label><value>kenya</value></item>
                  <item><label>Uganda</label><value>uganda</value></item>
                </select1>
                <select1 ref="/data/plots/region">
                  <label>Region</label>
                  <item><label>Nairobi</label><value>nairobi</value></item>
                  <item><label>Kampala</label><value>kampala</value></item>
                </select1>
              </repeat>
            </group>
          </h:body>
        </h:html>
        """
          .trimIndent()
      )
    val parentValues = mapOf("nairobi" to "kenya", "kampala" to "uganda")
    fun patch(component: ViewComponent): ViewComponent {
      val control = component.control
      return when {
        control != null && control.field_ref.endsWith("region") ->
          component.copy(
            control =
              control.copy(
                parent_question_key = "country",
                choices =
                  control.choices.map { it.copy(parent_value = parentValues[it.value_].orEmpty()) },
              )
          )
        component.group != null ->
          component.copy(
            group = component.group!!.copy(components = component.group!!.components.map(::patch))
          )
        component.repeat != null ->
          component.copy(
            repeat =
              component.repeat!!.copy(components = component.repeat!!.components.map(::patch))
          )
        else -> component
      }
    }
    return base.copy(view = base.view!!.copy(components = base.view!!.components.map(::patch)))
  }

  private fun FormWizardController.controlAt(path: String): ComponentState.ControlState {
    fun walk(list: List<ComponentState>): ComponentState.ControlState? =
      list.firstNotNullOfOrNull { item ->
        when (item) {
          is ComponentState.ControlState -> item.takeIf { it.canonicalPath == path }
          is ComponentState.GroupState -> walk(item.children)
          is ComponentState.RepeatGroupState ->
            item.instances.firstNotNullOfOrNull { walk(it.children) }
        }
      }
    return assertNotNull(walk(formState.rootComponents), path)
  }

  @Test
  fun repeatChild_filtersOnTheParentAnswerOfItsOwnInstance() {
    val controller = FormWizardController(formDef = repeatCascadeForm())
    controller.updateString("/data/plots[1]/country", "kenya")
    controller.addRepeatInstanceAndOpen("/data/plots")
    controller.updateString("/data/plots[2]/country", "uganda")

    val first = controller.parentQuestionInfoFor(controller.controlAt("/data/plots[1]/region"))
    val second = controller.parentQuestionInfoFor(controller.controlAt("/data/plots[2]/region"))

    assertEquals("/data/plots[1]/country", first?.path)
    assertEquals(listOf("kenya"), first?.selectedValues)
    assertEquals("/data/plots[2]/country", second?.path)
    assertEquals(listOf("uganda"), second?.selectedValues)
  }

  @Test
  fun repeatChild_isPrunedOnlyInTheInstanceWhoseParentChanged() {
    val controller = FormWizardController(formDef = repeatCascadeForm())
    controller.updateString("/data/plots[1]/country", "kenya")
    controller.updateString("/data/plots[1]/region", "nairobi")
    controller.addRepeatInstanceAndOpen("/data/plots")
    controller.updateString("/data/plots[2]/country", "kenya")
    controller.updateString("/data/plots[2]/region", "nairobi")

    controller.updateString("/data/plots[2]/country", "uganda")

    assertEquals(listOf("nairobi"), controller.values("/data/plots[1]/region"))
    assertEquals(emptyList(), controller.values("/data/plots[2]/region"))
  }

  /**
   * Form Designer published cascade: `ControlDef.parent_question_key` AND an `itemset` filtered by
   * `string-length(parent_value) = 0 or selected(<parent>, parent_value)`, across three levels
   * (`country` -> multi-select `regions` -> `district`).
   */
  @Test
  fun itemsetBackedEditorChild_prunesFilteredOutChoicesAndKeepsUnfilteredOnes() {
    val base =
      XFormsXmlSerializer.deserializeFormDef(
        """
        <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
          <h:head>
            <h:title>Published Cascade</h:title>
            <model>
              <instance>
                <data id="published_cascade"><country/><regions/><district/></data>
              </instance>
              <instance id="regions">
                <root>
                  <item><name>nairobi</name><label>Nairobi</label><parent_value>kenya</parent_value></item>
                  <item><name>central</name><label>Central</label><parent_value>uganda</parent_value></item>
                  <item><name>other</name><label>Other</label></item>
                </root>
              </instance>
              <instance id="district">
                <root>
                  <item><name>westlands</name><label>Westlands</label><parent_value>nairobi</parent_value></item>
                  <item><name>wakiso</name><label>Wakiso</label><parent_value>central</parent_value></item>
                </root>
              </instance>
              <bind nodeset="/data/country" type="string"/>
              <bind nodeset="/data/regions" type="string"/>
              <bind nodeset="/data/district" type="string"/>
            </model>
          </h:head>
          <h:body>
            <select1 ref="/data/country">
              <label>Country</label>
              <item><label>Kenya</label><value>kenya</value></item>
              <item><label>Uganda</label><value>uganda</value></item>
            </select1>
            <select ref="/data/regions">
              <label>Regions</label>
              <itemset nodeset="instance('regions')/root/item[(string-length(parent_value) = 0 or selected(/data/country, parent_value))]">
                <value ref="name"/>
                <label ref="label"/>
              </itemset>
            </select>
            <select1 ref="/data/district">
              <label>District</label>
              <itemset nodeset="instance('district')/root/item[(string-length(parent_value) = 0 or selected(/data/regions, parent_value))]">
                <value ref="name"/>
                <label ref="label"/>
              </itemset>
            </select1>
          </h:body>
        </h:html>
        """
          .trimIndent()
      )
    val parentKeys = mapOf("regions" to "country", "district" to "regions")
    val components =
      base.view!!.components.map { component ->
        val control = component.control ?: return@map component
        val parentKey = parentKeys[control.field_ref] ?: return@map component
        component.copy(control = control.copy(parent_question_key = parentKey))
      }
    val controller =
      FormWizardController(formDef = base.copy(view = base.view!!.copy(components = components)))

    controller.updateString("/data/country", "kenya")
    controller.updateMultiSelect("/data/regions", listOf("nairobi", "other"))
    controller.updateString("/data/district", "westlands")

    // Switching to Uganda removes "nairobi" from the itemset options, pruning it and cascading to
    // clear "westlands", while keeping the unfiltered "other" choice.
    controller.updateString("/data/country", "uganda")

    assertEquals(listOf("other"), controller.values("/data/regions"))
    assertEquals(emptyList(), controller.values("/data/district"))
  }

  private fun option(value: String, parentValue: String) =
    ResolvedChoiceOption(
      value = value,
      label = ResolvedLabel(text = value),
      properties = emptyMap(),
      parentValue = parentValue,
    )
}
