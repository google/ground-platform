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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.core.forms.ui.formatFieldValueForDisplay

/**
 * Canonical XForms `entityref` nodeset path (`/data/target_entity`) representing the geospatial
 * entity reference step (`select_one_from_file <dataset>.csv` / `appearance="map-select"`) when a
 * form requires a target geospatial entity.
 */
const val ENTITY_REF_FIELD_PATH: String = "/data/target_entity"

/**
 * Default rich XForms `<h:html>` definition used in `devtools/prototypeApp` for EUDR / Shade-Tree /
 * Field Survey data collection and live XForms FormDef testing in the UX Chrome panel.
 *
 * Defaults to [WorkbenchExampleForm.ALL_FIELD_TYPES], showcasing all 20+ ProtoForms / XForms field
 * types while preserving `/data/dominant_shade_species` and `/data/surviving_saplings_count`.
 */
val DEFAULT_PROTOTYPE_XFORMS_XML: String = WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml

/**
 * Secondary XForms sample preset for Baobab / Shade Tree Biometrics testing in the Chrome panel.
 */
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

/** Parses the initial default XForms XML into a [FormDef] via [XFormsXmlSerializer]. */
fun parseDefaultPrototypeFormDef(): FormDef =
  XFormsXmlSerializer.deserializeFormDef(DEFAULT_PROTOTYPE_XFORMS_XML)

/**
 * Ensures that when [form] requires a geospatial entity (`form.requiresEntity == true`), the
 * [FormDef] contains an `entityref` step (`[ENTITY_REF_FIELD_PATH] = "/data/target_entity"`,
 * `appearance = "map-select"`, `required = "true()"`) bound to the eligible candidate entities in
 * `form.targetDatasetId`.
 */
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

/**
 * Resolves the [FormDef] to execute in [FormWizardController] for a given [form]: uses
 * [customFormDef] when present and valid, otherwise parses the built-in fallback XForms XML for
 * [form]. When [includeEntityRefStep] is `true` and [form] requires a geospatial entity, prepends
 * the required `entityref` (`/data/target_entity`) step so the user can select the target entity on
 * the Map or List during data collection.
 */
fun resolveFormDefForLaunch(
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

/**
 * Returns `true` if [step] represents an `entityref` question step (e.g. bound to
 * [ENTITY_REF_FIELD_PATH] `/data/target_entity`, `/data/entity_id`, or configured with
 * `appearance="map-select"` / `"entityref"`).
 */
fun isWizardStepEntityRef(step: FormWizardStep?): Boolean {
  if (step == null) return false
  val controls =
    when (step) {
      is FormWizardStep.QuestionStep -> listOf(step.control)
      is FormWizardStep.FieldListGroupStep -> step.controls
      else -> emptyList()
    }
  return controls.any { ctrl ->
    ctrl.canonicalPath == ENTITY_REF_FIELD_PATH ||
      ctrl.canonicalPath.endsWith("/target_entity") ||
      ctrl.canonicalPath.endsWith("/entity_id") ||
      ctrl.canonicalPath.endsWith("/sample_plot_entity") ||
      ctrl.canonicalPath.endsWith("/past_individual_id") ||
      ctrl.canonicalPath.endsWith("/primary_respondent_id") ||
      ctrl.appearance.contains("map-select", ignoreCase = true) ||
      ctrl.appearance.contains("entityref", ignoreCase = true)
  }
}

/**
 * Extracts all answered fields from [recordInstance] (and [controller]'s [FormState]) into a list
 * of [SubmissionFieldEntry] items using [formatFieldValueForDisplay] from
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
    val isMetaOrEntityRefField =
      fs.relativePath == "meta/instanceID" ||
        fs.relativePath.startsWith("meta/") ||
        fs.canonicalPath.endsWith("/meta/instanceID") ||
        fs.canonicalPath == ENTITY_REF_FIELD_PATH ||
        fs.relativePath == "target_entity"
    if (fs.isRelevant && !fs.isEmpty && !isMetaOrEntityRefField) {
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
        answerValue = "Verified in field ($gnssBadge)",
      )
    )
  }

  return entries
}

/**
 * Embedded Data Collection Form overlay rendered inside the mobile/tablet device frame when
 * [PrototypeAppState.isDataCollectionFormOpen] is `true`.
 *
 * Uses the same [MobileFormRunner] and [FormWizardController] components as
 * `devtools/formdebugger`, preceded by a compact context banner displaying the target Geospatial
 * Entity name, GeoID, and active form title.
 *
 * When the form was launched from the bottom-centered FAB without a pre-selected entity and the
 * current wizard step requires an `entityref` (`[PrototypeAppState.isCurrentFormStepEntityRef]`),
 * presents an interactive **Map or List** entity selector right inside the data collection step so
 * the collector can choose the target geospatial entity.
 */
@Composable
fun PrototypeDataCollectionFormScreen(state: PrototypeAppState) {
  val controller = state.activeFormWizardController ?: return
  val entity = state.activeDataCollectionEntity
  val form = state.activeDataCollectionForm
  val resolvedTitle =
    controller.formState.formDef.title.takeIf { it.isNotBlank() }
      ?: form?.title
      ?: "Data Collection Form"

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Compact target entity & form context banner at the top of the device screen
    Surface(
      color = MaterialTheme.colorScheme.inverseSurface,
      contentColor = MaterialTheme.colorScheme.inverseOnSurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          IconButton(onClick = { state.closeActiveFormRunner() }, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to Survey",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF8BD6B1),
                modifier = Modifier.size(14.dp),
              )
              Text(
                text =
                  if (entity != null) {
                    "${entity.label} • ${entity.geoId}"
                  } else if (form != null && !form.requiresEntity) {
                    "Standalone Field Log • ${state.userGpsCoordinatesLabel}"
                  } else {
                    "Select ${form?.targetSingularTypeLabel ?: "Feature"} (${form?.targetDatasetName ?: "Required"})"
                  },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8BD6B1),
                fontWeight = FontWeight.Bold,
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
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.88f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        AssistChip(
          onClick = { state.closeActiveFormRunner() },
          colors =
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
              containerColor = Color(0xFF1E563D),
              labelColor = Color.White,
              leadingIconContentColor = Color.White,
            ),
          border =
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(
              enabled = true,
              borderColor = Color(0xFF386B52),
            ),
          label = { Text("Map", style = MaterialTheme.typography.labelSmall) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Cancel Form",
              modifier = Modifier.size(14.dp),
            )
          },
          modifier = Modifier.height(32.dp),
        )
      }
    }

    if (state.isCurrentFormStepEntityRef && form != null) {
      EntityRefStepMapOrListSelector(
        state = state,
        form = form,
        controller = controller,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    } else {
      // Shared MobileFormRunner component from org.groundplatform.v2.core.forms.ui
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        MobileFormRunner(
          controller = controller,
          onClose = { state.closeActiveFormRunner() },
          onSubmitted = { result: FinalizationResult.Success ->
            state.completeActiveFormSubmission(result.recordInstance, result.entityStates)
          },
        )
      }
    }
  }
}

/**
 * Interactive **Map or List** selector presented at the `entityref` step (`/data/target_entity`) of
 * a data collection form when the form was launched without a pre-selected geospatial entity from
 * the map (e.g., via the bottom-centered FAB).
 */
@Composable
private fun EntityRefStepMapOrListSelector(
  state: PrototypeAppState,
  form: FormPreviewItem,
  controller: FormWizardController,
  modifier: Modifier = Modifier,
) {
  val selectedEntity = state.activeDataCollectionEntity
  val allDatasetCandidates = state.allDatasetEntitiesForForm(form)
  val filteredCandidates = state.filteredEntityRefCandidates
  val currentStepNum = controller.currentStepIndex + 1
  val totalSteps = controller.steps.size.coerceAtLeast(1)

  Column(
    modifier = modifier.background(MaterialTheme.colorScheme.surface),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(
      modifier =
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Step Header Card
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            GroundTonalBadge(
              text = "STEP $currentStepNum OF $totalSteps • REQUIRED",
              tone = GroundBadgeTone.PRIMARY,
            )
            GroundTonalBadge(text = form.targetDatasetName, tone = GroundBadgeTone.SECONDARY)
          }
          Text(
            text = "Select ${form.targetSingularTypeLabel}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              "No ${form.targetSingularTypeLabel.lowercase()} was pre-selected on the map. Choose a target ${form.targetSingularTypeLabel.lowercase()} below using the Map or List to continue data collection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          // Segmented Toggle: Map vs List selector
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MainSurveyViewMode.entries.forEachIndexed { idx, mode ->
              val isSelected = state.entityRefSelectorViewMode == mode
              SegmentedButton(
                selected = isSelected,
                onClick = { state.updateEntityRefSelectorViewMode(mode) },
                shape =
                  SegmentedButtonDefaults.itemShape(
                    index = idx,
                    count = MainSurveyViewMode.entries.size,
                  ),
                icon = {
                  Icon(
                    imageVector =
                      if (mode == MainSurveyViewMode.MAP) {
                        Icons.Default.Map
                      } else {
                        Icons.AutoMirrored.Filled.List
                      },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text =
                      if (mode == MainSurveyViewMode.MAP) {
                        "Select on Map"
                      } else {
                        "Select from List (${allDatasetCandidates.size})"
                      },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                },
              )
            }
          }
        }
      }

      // Currently selected entity summary banner (if chosen)
      if (selectedEntity != null) {
        ElevatedCard(
          modifier =
            Modifier.fillMaxWidth()
              .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium,
              ),
          colors =
            CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = "Selected: ${selectedEntity.label}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    "GeoID: ${selectedEntity.geoId} • ${state.formattedWayfindingBadgeForEntity(selectedEntity.id)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            GroundTonalBadge(text = "Ready", tone = GroundBadgeTone.PRIMARY)
          }
        }
      }

      // MAP MODE vs LIST MODE
      if (state.entityRefSelectorViewMode == MainSurveyViewMode.MAP) {
        // Interactive Map Picker Canvas + Tappable Feature Chips for the Target Dataset
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF12281E))) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                // Subtle survey terrain grid lines
                val gridStepX = size.width / 6f
                val gridStepY = size.height / 4f
                for (i in 1..5) {
                  drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(i * gridStepX, 0f),
                    end = Offset(i * gridStepX, size.height),
                    strokeWidth = 1f,
                  )
                }
                for (j in 1..3) {
                  drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, j * gridStepY),
                    end = Offset(size.width, j * gridStepY),
                    strokeWidth = 1f,
                  )
                }

                // Draw user GPS dot
                val gpsCenter =
                  Offset(
                    x = state.userGpsNormalizedX * size.width,
                    y = state.userGpsNormalizedY * size.height,
                  )
                drawCircle(
                  color = Color(0xFF42A5F5).copy(alpha = 0.25f),
                  radius = 16.dp.toPx(),
                  center = gpsCenter,
                )
                drawCircle(color = Color(0xFF2196F3), radius = 6.dp.toPx(), center = gpsCenter)

                // Draw candidate entities in the target dataset (Point, LineString, Polygon)
                allDatasetCandidates.take(300).forEach { candidate ->
                  val isSelected = selectedEntity?.id == candidate.id
                  val isPending = candidate.isPending
                  val cx = candidate.normalizedX * size.width
                  val cy = candidate.normalizedY * size.height
                  val strokeColor =
                    if (isSelected) {
                      Color(0xFF00E676)
                    } else {
                      Color(candidate.markerColorHex)
                    }
                  when {
                    candidate.geometryTypeLabel.equals("Point", ignoreCase = true) -> {
                      drawCircle(
                        color =
                          strokeColor.copy(
                            alpha =
                              when {
                                isSelected -> 0.45f
                                isPending -> 0.14f
                                else -> 0.28f
                              }
                          ),
                        radius = if (isSelected) 22.dp.toPx() else 16.dp.toPx(),
                        center = Offset(cx, cy),
                      )
                      drawCircle(color = strokeColor, radius = 8.dp.toPx(), center = Offset(cx, cy))
                    }
                    candidate.geometryTypeLabel.equals("LineString", ignoreCase = true) -> {
                      val lineW = (0.24f * size.width).coerceAtLeast(48.dp.toPx())
                      val lineH = (0.14f * size.height).coerceAtLeast(28.dp.toPx())
                      val p1 = Offset(cx - lineW / 2f, cy + lineH / 2f)
                      val p2 = Offset(cx, cy - lineH / 3f)
                      val p3 = Offset(cx + lineW / 2f, cy - lineH / 2f)
                      drawLine(
                        color = strokeColor,
                        start = p1,
                        end = p2,
                        strokeWidth = if (isSelected) 3.5.dp.toPx() else 2.5.dp.toPx(),
                      )
                      drawLine(
                        color = strokeColor,
                        start = p2,
                        end = p3,
                        strokeWidth = if (isSelected) 3.5.dp.toPx() else 2.5.dp.toPx(),
                      )
                      drawCircle(color = strokeColor, radius = 5.dp.toPx(), center = p2)
                    }
                    else -> {
                      val boxW = (0.24f * size.width).coerceAtLeast(48.dp.toPx())
                      val boxH = (0.16f * size.height).coerceAtLeast(34.dp.toPx())
                      val topLeft = Offset(cx - boxW / 2f, cy - boxH / 2f)
                      drawRect(
                        color =
                          strokeColor.copy(
                            alpha =
                              when {
                                isSelected -> 0.35f
                                isPending -> 0.10f
                                else -> 0.22f
                              }
                          ),
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                      )
                      drawRect(
                        color = strokeColor,
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                        style =
                          Stroke(
                            width = if (isSelected) 3.dp.toPx() else 2.dp.toPx(),
                            pathEffect =
                              if (isPending) {
                                PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                              } else {
                                null
                              },
                          ),
                      )
                    }
                  }
                }
              }

              // Top-left map badge
              Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = Color(0xFF0E2219).copy(alpha = 0.90f),
              ) {
                Text(
                  text =
                    "Tap a ${form.targetSingularTypeLabel.lowercase()} on map or below to select",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF8BD6B1),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            // Interactive map feature chips for quick one-tap selection on map
            Column(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "${form.targetDatasetName.uppercase()} ON MAP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              allDatasetCandidates.take(40).forEach { candidate ->
                EntityRefCandidateOptionCard(
                  state = state,
                  form = form,
                  candidate = candidate,
                  isSelected = selectedEntity?.id == candidate.id,
                  onSelect = { state.selectEntityRefForActiveForm(candidate.id) },
                )
              }
            }
          }
        }
      } else {
        // LIST MODE: Searchable list of candidate entities for the target dataset
        OutlinedTextField(
          value = state.entityRefSearchQuery,
          onValueChange = { state.updateEntityRefSearchQuery(it) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search map features",
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (state.entityRefSearchQuery.isNotEmpty()) {
              IconButton(onClick = { state.clearEntityRefSearchQuery() }) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear search",
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          },
          placeholder = {
            Text(
              text = "Filter ${form.targetDatasetName.lowercase()} by name or GeoID...",
              style = MaterialTheme.typography.bodySmall,
            )
          },
        )

        if (filteredCandidates.isEmpty()) {
          OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "No matching ${form.targetDatasetName.lowercase()} found.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        } else {
          filteredCandidates.take(40).forEach { candidate ->
            EntityRefCandidateOptionCard(
              state = state,
              form = form,
              candidate = candidate,
              isSelected = selectedEntity?.id == candidate.id,
              onSelect = { state.selectEntityRefForActiveForm(candidate.id) },
            )
          }
        }
      }
    }

    // Bottom Step Action Bar (Cancel vs Continue to Form Questions)
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { state.closeActiveFormRunner() }) { Text("Cancel") }

        Button(onClick = { controller.nextStep() }, enabled = selectedEntity != null) {
          Text(
            text =
              if (selectedEntity != null) {
                "Continue with ${selectedEntity.label.substringBefore(" •")}"
              } else {
                "Select a ${form.targetSingularTypeLabel.lowercase()} to continue"
              },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun EntityRefCandidateOptionCard(
  state: PrototypeAppState,
  form: FormPreviewItem,
  candidate: GeospatialEntityItem,
  isSelected: Boolean,
  onSelect: () -> Unit,
) {
  val isEligible = state.isFormButtonEnabled(candidate, form)
  val wayfindingBadge = state.formattedWayfindingBadgeForEntity(candidate.id)
  OutlinedCard(
    onClick = { if (isEligible) onSelect() },
    enabled = isEligible,
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (isSelected) {
            Modifier.border(
              width = 1.5.dp,
              color = MaterialTheme.colorScheme.primary,
              shape = MaterialTheme.shapes.small,
            )
          } else {
            Modifier
          }
        ),
    shape = MaterialTheme.shapes.small,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
          } else {
            MaterialTheme.colorScheme.surface
          }
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "${candidate.markerSymbol} ${candidate.label}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          GroundTonalBadge(
            text = candidate.mapStatusSummaryBadge,
            tone =
              when (candidate.markerSymbol) {
                "✓" -> GroundBadgeTone.PRIMARY
                "◐" -> GroundBadgeTone.TERTIARY
                else -> GroundBadgeTone.WARNING
              },
          )
          SyncStatusIndicatorBadge(syncStatus = candidate.syncStatus)
        }
        Text(
          text = "GeoID: ${candidate.geoId} • $wayfindingBadge",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text =
            "${candidate.geometryTypeLabel} • ${candidate.submissionCount} ${if (candidate.submissionCount == 1) "submission" else "submissions"}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (isSelected) {
        GroundTonalBadge(text = "Selected ✓", tone = GroundBadgeTone.PRIMARY)
      } else if (isEligible) {
        AssistChip(
          onClick = onSelect,
          label = {
            Text(
              text = "Select",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
            )
          },
        )
      } else {
        GroundTonalBadge(text = "Completed", tone = GroundBadgeTone.NEUTRAL)
      }
    }
  }
}

/**
 * Prominent section inside `UxDesignerInspectorPanel` (the Prototype App page Chrome) allowing an
 * XForms `FormDef` (`<h:html>...</h:html>`) to be pasted or edited for live testing in the
 * prototype app's data collection flow.
 */
@Composable
fun XFormsFormDefChromeSection(state: PrototypeAppState) {
  val parsedFormDef = state.customFormDef
  val xmlError = state.xformsXmlError
  val fieldCount = parsedFormDef?.model?.bindings?.size ?: 0
  var showRawXmlEditor by remember { mutableStateOf(false) }
  var showProtoPreview by remember { mutableStateOf(false) }
  var previewAsJson by remember { mutableStateOf(false) }

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
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
            text = "EXAMPLE SURVEYS & XFORMS WORKBENCH",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
          Text(
            text =
              "Select an example survey below to open it with its preloaded entities, submissions, and form.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val isError = xmlError != null
        GroundTonalBadge(
          text =
            when {
              isError -> "XML Parse Error"
              parsedFormDef != null -> "Valid FormDef • $fieldCount fields"
              else -> "Using Built-In FormDef"
            },
          tone =
            when {
              isError -> GroundBadgeTone.ERROR
              parsedFormDef != null -> GroundBadgeTone.PRIMARY
              else -> GroundBadgeTone.NEUTRAL
            },
        )
      }

      // Swappable Example Surveys Selector (5 Workbench Example Surveys with preloaded entities & submissions)
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "EXAMPLE SURVEYS (CLICK TO OPEN SURVEY)",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 0.5.sp,
            ),
        )
        WorkbenchExampleForm.entries.forEach { example ->
          val surveyId = PrototypeAppState.surveyIdForExampleForm(example)
          val isSelected =
            state.activeSurveyId == surveyId || state.selectedWorkbenchExampleForm == example
          val preloadedEntityCount = PrototypeAppState.preloadedEntityCountForSurvey(surveyId)
          val preloadedSubmissionCount =
            PrototypeAppState.preloadedSubmissionCountForSurvey(surveyId)
          OutlinedCard(
            onClick = { state.selectWorkbenchExampleForm(example, launchImmediately = false) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors =
              CardDefaults.outlinedCardColors(
                containerColor =
                  if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                  } else {
                    MaterialTheme.colorScheme.surface
                  }
              ),
            border =
              BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color =
                  if (isSelected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.outlineVariant
                  },
              ),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = example.shortLabel,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isSelected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                GroundTonalBadge(
                  text = if (isSelected) "✓ Active Survey" else "Open Survey",
                  tone = if (isSelected) GroundBadgeTone.PRIMARY else GroundBadgeTone.SECONDARY,
                )
              }

              Text(
                text = example.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )

              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                GroundTonalBadge(
                  text = example.badgeText,
                  tone = if (isSelected) GroundBadgeTone.PRIMARY else GroundBadgeTone.SECONDARY,
                )
                GroundTonalBadge(
                  text = "$preloadedEntityCount entities • $preloadedSubmissionCount submissions",
                  tone = GroundBadgeTone.NEUTRAL,
                )
              }
            }
          }
        }
      }

      // Preset / Action Row: Launch Active Form, Reset, and Toggle Raw XML Editor
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
                if (state.isDataCollectionFormOpen) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.primary
                }
            ),
        ) {
          Text(
            text =
              if (state.isDataCollectionFormOpen) {
                "■ Close Active Form Runner"
              } else {
                "▶ Test / Launch Form Now"
              },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(onClick = { showRawXmlEditor = !showRawXmlEditor }) {
          Text(
            text = if (showRawXmlEditor) "Hide XForms XML" else "Edit XForms XML",
            style = MaterialTheme.typography.labelMedium,
          )
        }

        OutlinedButton(onClick = { state.resetDefaultXFormsXml() }) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Reset Default", style = MaterialTheme.typography.labelMedium)
        }
      }

      // Collapsible Multi-line Monospace OutlinedTextField for pasting / editing XForms FormDef XML
      if (showRawXmlEditor || xmlError != null) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { state.updateCustomXFormsXml(BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML) }
          ) {
            Text(text = "Baobab Preset", style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(onClick = { state.updateCustomXFormsXml("") }) {
            Text(text = "Clear XML", style = MaterialTheme.typography.labelSmall)
          }
        }

        androidx.compose.runtime.key(state.activeSurveyId, state.selectedWorkbenchExampleForm) {
          OutlinedTextField(
            value = state.customXFormsXml,
            onValueChange = { state.updateCustomXFormsXml(it) },
            modifier = Modifier.fillMaxWidth().height(195.dp),
            placeholder = {
              Text(
                text = "Paste XForms <h:html>...</h:html> XML here to test in MobileFormRunner...",
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
              )
            },
            textStyle =
              MaterialTheme.typography.bodySmall.copy(
                lineHeight = 16.sp,
              ),
            isError = xmlError != null,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
              ),
          )
        }
      }

      // Error details banner if XML is invalid
      if (xmlError != null) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "XML Parse Error: $xmlError",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onErrorContainer,
              ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
      }

      // Optional collapsible ProtoForms TextProto / JSON inspector (using TextProtoSerializer &
      // ProtoJsonSerializer)
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
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

          TextButton(onClick = { showProtoPreview = !showProtoPreview }) {
            Text(
              text = if (showProtoPreview) "Hide Proto" else "Inspect TextProto / JSON",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
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
            FilterChip(
              selected = !previewAsJson,
              onClick = { previewAsJson = false },
              label = { Text("TextProto", style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
              selected = previewAsJson,
              onClick = { previewAsJson = true },
              label = { Text("JSON", style = MaterialTheme.typography.labelSmall) },
            )
          }
          OutlinedTextField(
            value = serializedProto,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle =
              MaterialTheme.typography.labelSmall.copy(
                lineHeight = 15.sp,
              ),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              ),
          )
        }
      }
    }
  }
}
