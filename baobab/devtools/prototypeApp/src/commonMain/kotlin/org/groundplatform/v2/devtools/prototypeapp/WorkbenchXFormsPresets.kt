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
package org.groundplatform.v2.devtools.prototypeapp

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm

// XForms presets and parsing helpers for the XForms workbench panel. These are developer tooling
// inputs, not survey data: survey forms live in the LocalStore.

/** Default XForms XML loaded into the workbench (the `All field types` example form). */
val DEFAULT_PROTOTYPE_XFORMS_XML: String = WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml

/** Sample XForms XML for the workbench's `Baobab biometrics` preset. */
const val BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML: String =
  """<h:html xmlns="http://www.w3.org/2002/xforms"
      xmlns:h="http://www.w3.org/1999/xhtml"
      xmlns:jr="http://openrosa.org/javarosa">
  <h:head>
  <h:title>Shade Tree &amp; Baobab Biometrics</h:title>
  <model>
    <instance>
      <data id="baobab_biometrics" version="2026091901">
        <meta>
          <instanceID/>
        </meta>
        <species>Adansonia digitata</species>
        <height_m>18.5</height_m>
        <circumference_m>12.2</circumference_m>
        <health_status>healthy</health_status>
      </data>
    </instance>
    <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
    <bind nodeset="/data/species" type="string" required="true()"/>
    <bind nodeset="/data/height_m" type="decimal"/>
    <bind nodeset="/data/circumference_m" type="decimal"/>
    <bind nodeset="/data/health_status" type="string"/>
  </model>
  </h:head>
  <h:body>
  <input ref="/data/species">
    <label>Tree Species</label>
  </input>
  <input ref="/data/height_m">
    <label>Canopy Height (meters)</label>
  </input>
  <input ref="/data/circumference_m">
    <label>Trunk Circumference (meters)</label>
  </input>
  <select1 ref="/data/health_status">
    <label>Crown &amp; Bark Health Status</label>
    <item>
      <label>Healthy</label>
      <value>healthy</value>
    </item>
    <item>
      <label>Stressed</label>
      <value>stressed</value>
    </item>
  </select1>
  </h:body>
</h:html>"""

/** Parses XForms XML into [FormDef]s, parsing each distinct XML string at most once. */
object XFormsParseCache {
  private val cache = mutableMapOf<String, FormDef?>()

  /** Returns the parsed [FormDef] for [xml], or null if it doesn't parse. */
  fun formDef(xml: String): FormDef? =
    cache.getOrPut(xml) {
      try {
        XFormsXmlSerializer.deserializeFormDef(xml)
      } catch (_: Exception) {
        null
      }
    }

  /** Returns the parsed [FormDef] for [example], or null if it doesn't parse. */
  fun formDef(example: WorkbenchExampleForm): FormDef? = formDef(example.xformsXml)
}
