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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem

/**
 * Domain use case resolving the [FormDef] to execute for a given [FormPreviewItem] and injecting
 * the `entityref` (`/data/target_entity`) map/list selection step when launched without a
 * pre-selected entity.
 */
class ResolveFormDefForLaunchUseCase {
  operator fun invoke(
    customFormDef: FormDef?,
    form: FormPreviewItem,
    candidateEntities: List<GeospatialEntityItem> = emptyList(),
    defaultSelectedEntityId: String = "",
    includeEntityRefStep: Boolean = false,
  ): FormDef {
    val base =
      customFormDef ?: XFormsXmlSerializer.deserializeFormDef(builtInFallbackXFormsXmlForForm(form))
    return if (includeEntityRefStep && form.requiresEntity) {
      ensureEntityRefStepInFormDef(
        baseFormDef = base,
        form = form,
        candidateEntities = candidateEntities,
        defaultSelectedEntityId = defaultSelectedEntityId,
      )
    } else {
      base
    }
  }

  fun builtInFallbackXFormsXmlForForm(form: FormPreviewItem): String {
    when (form.id) {
      "form-single-point-land-use" -> return WorkbenchExampleForm.SINGLE_POINT_LAND_USE.xformsXml
      "form-sample-plots-forest" ->
        return WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT.xformsXml
      "form-commodity-perimeter-center" ->
        return WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER.xformsXml
      "form-household-past-individuals" ->
        return WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS.xformsXml
      "form-coffee-parcel" -> return WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml
    }
    val escapedTitle = form.title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val safeFormId = form.id.replace('-', '_')
    return """<h:html xmlns="http://www.w3.org/2002/xforms"
        xmlns:h="http://www.w3.org/1999/xhtml"
        xmlns:jr="http://openrosa.org/javarosa">
  <h:head>
    <h:title>$escapedTitle</h:title>
    <model>
      <instance>
        <data id="$safeFormId" version="${form.version}">
          <meta>
            <instanceID/>
          </meta>
          <organizer_action_cta>Verified in field (${form.ctaLabel})</organizer_action_cta>
          <collector_observation>Field verification complete</collector_observation>
          <canopy_or_parcel_metric>94</canopy_or_parcel_metric>
        </data>
      </instance>
      <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
      <bind nodeset="/data/organizer_action_cta" type="string" required="true()"/>
      <bind nodeset="/data/collector_observation" type="string"/>
      <bind nodeset="/data/canopy_or_parcel_metric" type="int"/>
    </model>
  </h:head>
  <h:body>
    <input ref="/data/organizer_action_cta">
      <label>Completed Form Action (${form.ctaLabel})</label>
    </input>
    <input ref="/data/collector_observation">
      <label>Field Observation Notes</label>
    </input>
    <input ref="/data/canopy_or_parcel_metric">
      <label>Measured Field Metric / Score (%)</label>
    </input>
  </h:body>
</h:html>"""
  }

  fun ensureEntityRefStepInFormDef(
    baseFormDef: FormDef,
    form: FormPreviewItem,
    candidateEntities: List<GeospatialEntityItem> = emptyList(),
    defaultSelectedEntityId: String = "",
  ): FormDef {
    if (!form.requiresEntity) return baseFormDef
    val baseXml = XFormsXmlSerializer.serializeFormDef(baseFormDef)
    if (
      baseXml.contains("/data/target_entity") ||
        baseXml.contains("/data/sample_plot_entity") ||
        baseXml.contains("/data/past_individual_id") ||
        baseXml.contains("/data/primary_respondent_id") ||
        baseXml.contains("appearance=\"map-select\"")
    ) {
      return baseFormDef
    }

    val escapedLabel =
      "Select ${form.targetSingularTypeLabel} (${form.targetDatasetName})"
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    val itemsXml =
      if (candidateEntities.isNotEmpty()) {
        candidateEntities.joinToString("\n") { ent ->
          val itemLabel =
            "${ent.label} (${ent.geoId})"
              .replace("&", "&amp;")
              .replace("<", "&lt;")
              .replace(">", "&gt;")
          """      <item>
        <label>$itemLabel</label>
        <value>${ent.id}</value>
      </item>"""
        }
      } else {
        """      <item>
        <label>Default Feature</label>
        <value>default_feature</value>
      </item>"""
      }

    val withDataNode =
      if (baseXml.contains("</meta>")) {
        baseXml.replaceFirst(
          "</meta>",
          "</meta>\n          <target_entity>$defaultSelectedEntityId</target_entity>",
        )
      } else {
        baseXml.replaceFirst(
          Regex("(<data[^>]*>)"),
          "$1\n          <target_entity>$defaultSelectedEntityId</target_entity>",
        )
      }

    val withBinding =
      withDataNode.replaceFirst(
        "</model>",
        """      <bind nodeset="/data/target_entity" type="string" required="true()"/>
    </model>""",
      )

    val selectControlXml =
      """  <h:body>
    <select1 ref="/data/target_entity" appearance="map-select">
      <label>$escapedLabel</label>
$itemsXml
    </select1>"""

    val updatedXml =
      if (withBinding.contains("<h:body>")) {
        withBinding.replaceFirst("<h:body>", selectControlXml)
      } else {
        withBinding.replaceFirst("<body>", selectControlXml.replace("<h:body>", "<body>"))
      }

    return try {
      XFormsXmlSerializer.deserializeFormDef(updatedXml)
    } catch (_: Exception) {
      baseFormDef
    }
  }
}
