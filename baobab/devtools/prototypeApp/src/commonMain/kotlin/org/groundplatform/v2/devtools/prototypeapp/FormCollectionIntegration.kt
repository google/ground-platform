/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState
import org.groundplatform.v2.core.forms.serialization.ProtoJsonSerializer
import org.groundplatform.v2.core.forms.serialization.TextProtoSerializer
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.core.forms.ui.formatFieldValueForDisplay

/**
 * Default rich ODK XForms `<h:html>` definition used in `devtools/prototypeApp` for EUDR /
 * Shade-Tree / Field Survey data collection and live XForms FormDef testing in the UX Chrome panel.
 */
const val DEFAULT_PROTOTYPE_XFORMS_XML: String =
  """<h:html xmlns="http://www.w3.org/2002/xforms"
        xmlns:h="http://www.w3.org/1999/xhtml"
        xmlns:jr="http://openrosa.org/javarosa">
  <h:head>
    <h:title>EUDR &amp; Shade-Tree Field Survey</h:title>
    <model>
      <instance>
        <data id="eudr_shade_field_survey" version="2026091901">
          <meta>
            <instanceID/>
          </meta>
          <deforestation_free_since_2020>yes</deforestation_free_since_2020>
          <dominant_shade_species>Grevillea robusta</dominant_shade_species>
          <surviving_saplings_count>188</surviving_saplings_count>
          <canopy_shade_pct>42.5</canopy_shade_pct>
          <soil_moisture_status>moist_mulch</soil_moisture_status>
          <field_notes>Walked perimeter verified on-site with high-precision GNSS</field_notes>
        </data>
      </instance>
      <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
      <bind nodeset="/data/deforestation_free_since_2020" type="string" required="true()"/>
      <bind nodeset="/data/dominant_shade_species" type="string" required="true()"/>
      <bind nodeset="/data/surviving_saplings_count" type="int"/>
      <bind nodeset="/data/canopy_shade_pct" type="decimal"/>
      <bind nodeset="/data/soil_moisture_status" type="string"/>
      <bind nodeset="/data/field_notes" type="string"/>
    </model>
  </h:head>
  <h:body>
    <select1 ref="/data/deforestation_free_since_2020">
      <label>Deforestation-free since Dec 2020 (EUDR)</label>
      <item>
        <label>Yes — Verified via perimeter walk &amp; canopy history</label>
        <value>yes</value>
      </item>
      <item>
        <label>Requires follow-up verification</label>
        <value>follow_up</value>
      </item>
      <item>
        <label>No — Recent clearing observed</label>
        <value>no</value>
      </item>
    </select1>
    <input ref="/data/dominant_shade_species">
      <label>Dominant Intercropped Shade Species</label>
    </input>
    <input ref="/data/surviving_saplings_count">
      <label>Surviving Indigenous Saplings Count</label>
    </input>
    <input ref="/data/canopy_shade_pct">
      <label>Canopy Shade Cover (%)</label>
    </input>
    <select1 ref="/data/soil_moisture_status">
      <label>Topsoil Moisture &amp; Mulch Cover</label>
      <item>
        <label>Moist — Heavy leaf litter mulch intact</label>
        <value>moist_mulch</value>
      </item>
      <item>
        <label>Moderate — Partial organic cover</label>
        <value>moderate</value>
      </item>
      <item>
        <label>Dry — Exposed topsoil</label>
        <value>dry</value>
      </item>
    </select1>
    <input ref="/data/field_notes">
      <label>Collector Field Notes &amp; Observations</label>
    </input>
  </h:body>
</h:html>"""

/** Secondary XForms sample preset for Baobab / Shade Tree Biometrics testing in the Chrome panel. */
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

/**
 * Built-in fallback XForms `<h:html>` XML definitions keyed by [FormPreviewItem.id], used when
 * [PrototypeAppState.customXFormsXml] is cleared/blank.
 */
fun builtInFallbackXFormsXmlForForm(form: FormPreviewItem): String {
  val escapedTitle =
    form.title
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
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
          <organizer_action_cta>Verified on-site (${form.ctaLabel})</organizer_action_cta>
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

/** Parses the initial default XForms XML into a [FormDef] via [XFormsXmlSerializer]. */
fun parseDefaultPrototypeFormDef(): FormDef =
  XFormsXmlSerializer.deserializeFormDef(DEFAULT_PROTOTYPE_XFORMS_XML)

/**
 * Resolves the [FormDef] to execute in [FormWizardController] for a given [form]:
 * uses [customFormDef] when present and valid, otherwise parses the built-in fallback XForms XML
 * for [form].
 */
fun resolveFormDefForLaunch(customFormDef: FormDef?, form: FormPreviewItem): FormDef =
  customFormDef ?: XFormsXmlSerializer.deserializeFormDef(builtInFallbackXFormsXmlForForm(form))

/**
 * Extracts all answered fields from [recordInstance] (and [controller]'s [FormState]) into a
 * list of [SubmissionFieldEntry] items using [formatFieldValueForDisplay] from
 * `org.groundplatform.v2.core.forms.ui`.
 */
fun extractSubmissionFieldsFromRecord(
  recordInstance: RecordInstance,
  controller: FormWizardController?,
  resolvedFormDef: FormDef,
  form: FormPreviewItem,
  gnssBadge: String,
): List<SubmissionFieldEntry> {
  val evaluatedState: FormState =
    if (controller != null && controller.formState.recordInstance == recordInstance) {
      controller.formState
    } else {
      FormSession(
          formDef = controller?.formState?.formDef ?: resolvedFormDef,
          existingRecord = recordInstance,
          isFirstLoad = false,
        )
        .state
    }

  val steps = controller?.steps ?: FormWizardController.buildSteps(evaluatedState)
  val controlLabelsByPath = mutableMapOf<String, String>()
  steps.forEach { step ->
    when (step) {
      is FormWizardStep.QuestionStep -> {
        controlLabelsByPath[step.control.canonicalPath] = step.title
      }
      is FormWizardStep.FieldListGroupStep -> {
        step.controls.forEach { ctrl ->
          val label =
            ctrl.label?.text?.takeIf { it.isNotBlank() }
              ?: ctrl.canonicalPath.substringAfterLast('/')
          controlLabelsByPath[ctrl.canonicalPath] = label
        }
      }
      else -> {}
    }
  }

  val entries = mutableListOf<SubmissionFieldEntry>()
  evaluatedState.fieldStates.values.forEach { fs ->
    val isMetaField =
      fs.relativePath == "meta/instanceID" ||
        fs.relativePath.startsWith("meta/") ||
        fs.canonicalPath.endsWith("/meta/instanceID")
    if (fs.isRelevant && !fs.isEmpty && !isMetaField) {
      val questionName = fs.relativePath.ifBlank { fs.canonicalPath.trimStart('/') }
      val questionLabel =
        controlLabelsByPath[fs.canonicalPath]
          ?: fs.canonicalPath.substringAfterLast('/').replace('_', ' ')
      val formattedValue = formatFieldValueForDisplay(fs.value, fs.dataType)
      entries.add(
        SubmissionFieldEntry(
          questionName = questionName,
          questionLabel = questionLabel,
          answerValue = formattedValue,
        )
      )
    }
  }

  if (entries.isEmpty()) {
    entries.add(
      SubmissionFieldEntry(
        questionName = "organizer_action_cta",
        questionLabel = "Completed Form Action (${form.ctaLabel})",
        answerValue = "Verified on-site ($gnssBadge)",
      )
    )
  }

  return entries
}

/**
 * Embedded Data Collection Form overlay rendered inside the mobile/tablet device frame when
 * [PrototypeAppState.isDataCollectionFormOpen] is `true`.
 *
 * Uses the same [MobileFormRunner] and [FormWizardController] components as `devtools/formdebugger`,
 * preceded by a compact context banner displaying the target Geospatial Entity name, GeoID, and
 * active form title.
 */
@Composable
fun PrototypeDataCollectionFormScreen(state: PrototypeAppState) {
  val controller = state.activeFormWizardController ?: return
  val entity = state.activeDataCollectionEntity ?: state.selectedEntity
  val form = state.activeDataCollectionForm
  val resolvedTitle =
    controller.formState.formDef.title.takeIf { it.isNotBlank() }
      ?: form?.title
      ?: "Data Collection Form"

  Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF3F6F4))) {
    // Compact target entity & form context banner at the top of the device screen
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(Color(0xFF0F3826))
          .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier =
            Modifier.size(26.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(Color(0xFF1E6F50))
              .clickable { state.closeActiveFormRunner() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Survey",
            tint = Color.White,
            modifier = Modifier.size(15.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFF8BD6B1),
              modifier = Modifier.size(12.dp),
            )
            Text(
              text =
                if (entity != null) {
                  "${entity.label} • ${entity.geoId}"
                } else {
                  "Site Data Collection"
                },
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFF8BD6B1),
                  fontWeight = FontWeight.Bold,
                ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text =
              if (form != null) {
                "${form.ctaLabel} — $resolvedTitle"
              } else {
                resolvedTitle
              },
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
              ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Return to map/list pill button
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E563D))
            .clickable { state.closeActiveFormRunner() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Cancel Form",
          tint = Color.White,
          modifier = Modifier.size(12.dp),
        )
        Text(
          text = "Map",
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            ),
        )
      }
    }

    // Shared MobileFormRunner component from org.groundplatform.v2.core.forms.ui
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      MobileFormRunner(
        controller = controller,
        onClose = { state.closeActiveFormRunner() },
        onSubmitted = { result: FinalizationResult.Success ->
          state.completeActiveFormSubmission(result.recordInstance)
        },
      )
    }
  }
}

/**
 * Prominent section inside `UxDesignerInspectorPanel` (the Prototype App page Chrome) allowing an
 * ODK XForms `FormDef` (`<h:html>...</h:html>`) to be pasted or edited for live testing in the
 * prototype app's data collection flow.
 */
@Composable
fun XFormsFormDefChromeSection(state: PrototypeAppState) {
  val parsedFormDef = state.customFormDef
  val xmlError = state.xformsXmlError
  val fieldCount = parsedFormDef?.model?.bindings?.size ?: 0
  var showProtoPreview by remember { mutableStateOf(false) }
  var previewAsJson by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFF4F8F5))
        .border(1.dp, Color(0xFFC5DEC9), RoundedCornerShape(12.dp))
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Section Header + Live Status Badge
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "XFORMS FORMDEF XML TESTER (DATA COLLECTION)",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF1E6F50),
              letterSpacing = 0.6.sp,
            ),
        )
        Text(
          text =
            "Paste an ODK XForms <h:html> FormDef below. Launching any entity form runs this FormDef in MobileFormRunner.",
          style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF4B5563)),
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Live status badge (Valid FormDef • N fields vs XML Parse Error)
      val isError = xmlError != null
      val badgeBg =
        when {
          isError -> Color(0xFFFDECEA)
          parsedFormDef != null -> Color(0xFFE8F5E9)
          else -> Color(0xFFE5E7EB)
        }
      val badgeTextColor =
        when {
          isError -> Color(0xFFB3261E)
          parsedFormDef != null -> Color(0xFF1B5E20)
          else -> Color(0xFF374151)
        }
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(16.dp))
            .background(badgeBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (!isError && parsedFormDef != null) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = badgeTextColor,
            modifier = Modifier.size(12.dp),
          )
        }
        Text(
          text =
            when {
              isError -> "XML Parse Error"
              parsedFormDef != null -> "Valid FormDef • $fieldCount fields"
              else -> "Using Built-In FormDef"
            },
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = badgeTextColor,
            ),
        )
      }
    }

    // Preset / Action Row: Load Sample XForms, Biometrics Preset, Clear, and Launch Form Now
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        onClick = {
          if (state.isDataCollectionFormOpen) {
            state.closeActiveFormRunner()
          } else {
            state.launchActiveOrDefaultFormForTesting()
          }
        },
        enabled = xmlError == null,
        colors =
          ButtonDefaults.buttonColors(
            containerColor =
              if (state.isDataCollectionFormOpen) Color(0xFFB3261E) else Color(0xFF1B5E20)
          ),
        modifier = Modifier.height(34.dp),
      ) {
        Text(
          text =
            if (state.isDataCollectionFormOpen) {
              "■ Close Active Form Runner"
            } else {
              "▶ Test / Launch Form Now"
            },
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
      }

      OutlinedButton(
        onClick = { state.resetDefaultXFormsXml() },
        modifier = Modifier.height(34.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = null,
          modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Load Sample XForms",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
      }

      OutlinedButton(
        onClick = { state.updateCustomXFormsXml(BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML) },
        modifier = Modifier.height(34.dp),
      ) {
        Text(
          text = "Baobab Preset",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
      }

      OutlinedButton(
        onClick = { state.updateCustomXFormsXml("") },
        modifier = Modifier.height(34.dp),
      ) {
        Text(
          text = "Clear",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
      }
    }

    // Multi-line Monospace OutlinedTextField for pasting / editing XForms FormDef XML
    OutlinedTextField(
      value = state.customXFormsXml,
      onValueChange = { state.updateCustomXFormsXml(it) },
      modifier = Modifier.fillMaxWidth().height(195.dp),
      placeholder = {
        Text(
          text = "Paste ODK XForms <h:html>...</h:html> XML here to test in MobileFormRunner...",
          style =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = Color(0xFF9CA3AF),
            ),
        )
      },
      textStyle =
        TextStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          lineHeight = 16.sp,
        ),
      isError = xmlError != null,
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White,
          errorContainerColor = Color(0xFFFFF8F8),
        ),
    )

    // Error details banner if XML is invalid
    if (xmlError != null) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFDECEA))
            .border(1.dp, Color(0xFFF5C2C0), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
      ) {
        Text(
          text = "XML Parse Error: $xmlError",
          style =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = Color(0xFFB3261E),
            ),
        )
      }
    }

    // Optional collapsible ProtoForms TextProto / JSON inspector (using TextProtoSerializer & ProtoJsonSerializer)
    if (parsedFormDef != null && xmlError == null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "Parsed FormDef: \"${parsedFormDef.title.ifBlank { parsedFormDef.form_id }}\" (v${parsedFormDef.version.ifBlank { "1" }})",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              color = Color(0xFF1B5E20),
              fontWeight = FontWeight.SemiBold,
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = if (showProtoPreview) "Hide Proto" else "Inspect TextProto / JSON",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF1E6F50),
                fontWeight = FontWeight.Bold,
              ),
            modifier = Modifier.clickable { showProtoPreview = !showProtoPreview },
          )
        }
      }

      if (showProtoPreview) {
        val serializedProto =
          remember(parsedFormDef, previewAsJson) {
            if (previewAsJson) {
              ProtoJsonSerializer.serializeFormDef(parsedFormDef, prettyPrint = true)
            } else {
              TextProtoSerializer.serializeFormDef(parsedFormDef)
            }
          }
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { previewAsJson = false },
            modifier = Modifier.height(28.dp),
          ) {
            Text("TextProto", fontSize = 11.sp)
          }
          OutlinedButton(
            onClick = { previewAsJson = true },
            modifier = Modifier.height(28.dp),
          ) {
            Text("JSON", fontSize = 11.sp)
          }
        }
        OutlinedTextField(
          value = serializedProto,
          onValueChange = {},
          readOnly = true,
          modifier = Modifier.fillMaxWidth().height(120.dp),
          textStyle =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              lineHeight = 15.sp,
            ),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFFF9FAFB),
              unfocusedContainerColor = Color(0xFFF9FAFB),
            ),
        )
      }
    }
  }
}
