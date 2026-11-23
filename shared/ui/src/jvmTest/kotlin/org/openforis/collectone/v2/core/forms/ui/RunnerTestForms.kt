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

/** Forms shared by the runner UI tests. */
internal object RunnerTestForms {

  /** A required species question followed by an optional notes question. */
  fun speciesAndNotes(): FormWizardController =
    formController(
      title = "Tree Survey",
      model =
        """
        <instance><data id="trees" version="1"><species/><notes/></data></instance>
        <bind nodeset="/data/species" type="string" required="true()"
              jr:requiredMsg="Species is required"/>
        <bind nodeset="/data/notes" type="string"/>
        """,
      body =
        """
        <input ref="/data/species"><label>Tree species</label></input>
        <input ref="/data/notes"><label>Notes</label></input>
        """,
    )

  /** An open-ended repeat of branch measurements. */
  fun branchRepeat(): FormWizardController =
    formController(
      title = "Branch Survey",
      model =
        """
        <instance>
          <data id="branches" version="1"><branch><length_m/></branch></data>
        </instance>
        <bind nodeset="/data/branch/length_m" type="decimal"/>
        """,
      body =
        """
        <group ref="/data/branch">
          <label>Branch</label>
          <repeat nodeset="/data/branch">
            <input ref="/data/branch/length_m"><label>Branch length (m)</label></input>
          </repeat>
        </group>
        """,
    )

  /**
   * A cascading select matching the Form Designer's published shape (`parent_question_key` +
   * filtered `itemset`): Country (`Kenya` / `Uganda`) → Region (`Nairobi` / `Kampala` / `Other`).
   */
  fun cascadingCountryRegion(): FormWizardController {
    val base =
      org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer.deserializeFormDef(
        """
        <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
          <h:head>
            <h:title>Location Survey</h:title>
            <model>
              <instance>
                <data id="locations" version="1"><country/><region/></data>
              </instance>
              <instance id="region">
                <root>
                  <item><name>nairobi</name><label>Nairobi</label><parent_value>kenya</parent_value></item>
                  <item><name>kampala</name><label>Kampala</label><parent_value>uganda</parent_value></item>
                  <item><name>other</name><label>Other</label></item>
                </root>
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
              <itemset nodeset="instance('region')/root/item[(string-length(parent_value) = 0 or selected(/data/country, parent_value))]">
                <value ref="name"/>
                <label ref="label"/>
              </itemset>
            </select1>
          </h:body>
        </h:html>
        """
          .trimIndent()
      )
    val components =
      base.view!!.components.map { comp ->
        val ctrl = comp.control
        if (ctrl?.field_ref == "region") {
          comp.copy(control = ctrl.copy(parent_question_key = "country"))
        } else {
          comp
        }
      }
    return FormWizardController(
      formDef = base.copy(view = base.view!!.copy(components = components))
    )
  }
}
