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
package org.openforis.collectone.v2.web.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollapseContent
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.ui.common.GeoIdText
import org.openforis.collectone.v2.app.ui.common.PlatformMediaCaptureHandler
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityGeometryIcon
import org.openforis.collectone.v2.app.ui.datacollection.PublicDataInFormBanner
import org.openforis.collectone.v2.app.ui.datacollection.isWizardStepEntityRef
import org.openforis.collectone.v2.app.ui.text.targetTypeText
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.CompactFocusRequest
import org.openforis.collectone.v2.core.forms.ui.CompactFormRunner
import org.openforis.collectone.v2.core.forms.ui.CompactGeometryInput
import org.openforis.collectone.v2.core.forms.ui.LocalMediaCaptureHandler
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_clear_search
import org.openforis.collectone.v2.web.resources.web_collapse_form
import org.openforis.collectone.v2.web.resources.web_collect_data
import org.openforis.collectone.v2.web.resources.web_completed
import org.openforis.collectone.v2.web.resources.web_download_survey_kml
import org.openforis.collectone.v2.web.resources.web_entity_ref_empty
import org.openforis.collectone.v2.web.resources.web_entity_ref_filter_placeholder
import org.openforis.collectone.v2.web.resources.web_entity_ref_no_matches
import org.openforis.collectone.v2.web.resources.web_entity_ref_picker_instructions
import org.openforis.collectone.v2.web.resources.web_form_menu_summary_new_record
import org.openforis.collectone.v2.web.resources.web_form_menu_summary_remaining
import org.openforis.collectone.v2.web.resources.web_form_question_count
import org.openforis.collectone.v2.web.resources.web_manage_survey
import org.openforis.collectone.v2.web.resources.web_new_record_at_location
import org.openforis.collectone.v2.web.resources.web_no_forms_available_on_web
import org.openforis.collectone.v2.web.resources.web_search_map_features
import org.openforis.collectone.v2.web.resources.web_select_target_to_begin
import org.openforis.collectone.v2.web.resources.web_start_a_form
import org.openforis.collectone.v2.web.resources.web_survey_has_no_forms
import org.openforis.collectone.v2.web.ui.common.WebFormPanelChrome
import org.openforis.collectone.v2.web.ui.common.WebHeaderButton

/**
 * Web dashboard's data collection panel: the floating right-hand card that hosts the active form (
 * [DataCollectionUiState.activeFormWizardController]) in the compact stacked layout
 * ([CompactFormRunner]) instead of mobile's one-question-per-screen runner.
 *
 * Web adaptation of the mobile entry points:
 * - Forms opened from the **Collect data** toolbar menu ([WebCollectDataMenuButton], the
 *   counterpart of mobile's bottom FAB) with no target feature first show a picker
 *   ([WebEntityRefPicker]): clicking a matching feature on the map, in the left-hand list, or in
 *   the picker's list fills the form's `entityref` and reveals the questions. That step is then
 *   left out of the stack. A matching feature already selected when the form opens fills it right
 *   away, so the picker is skipped.
 * - Forms opened from a map feature's card (**Collect data** buttons) go straight to the questions.
 * - Geometry questions (`geopoint`, `geotrace`, `geoshape`) are drawn on the main map through
 *   [DataCollectionUiState.webMapDrawing] instead of captured from a device GPS: **Draw on map**
 *   routes map clicks to the question until the point is placed or the collector clicks **Done**.
 *   All of the form's geometry answers stay on the map in the in-flow style
 *   ([DataCollectionUiState.webFormGeometries]); clicking one scrolls to and highlights its card
 *   ([DataCollectionUiState.webFormFocusRequest]).
 *
 * Submitting runs the same [DataCollectionActions.completeActiveFormSubmission] as mobile, which
 * adds the submission to the feature's history and closes the panel.
 *
 * [userGpsCoordinatesLabel] is the collector's current GPS position, shown in the subtitle of a
 * standalone form (one that needs no target feature).
 */
/**
 * Display title of the active web data collection form, shared by the card and its collapsed pill.
 */
@Composable
internal fun webDataCollectionFormTitle(uiState: DataCollectionUiState): String =
  uiState.activeFormWizardController?.formState?.formDef?.title?.takeIf { it.isNotBlank() }
    ?: uiState.activeDataCollectionForm?.title
    ?: stringResource(Res.string.web_collect_data)

@Composable
internal fun WebDataCollectionCard(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  userGpsCoordinatesLabel: String,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
  onRequestClose: () -> Unit = { actions.closeActiveFormRunner() },
) {
  val controller = uiState.activeFormWizardController ?: return
  val form = uiState.activeDataCollectionForm
  val entity = uiState.activeDataCollectionEntity
  val needsEntity = form != null && form.requiresEntity && entity == null
  val title = webDataCollectionFormTitle(uiState)

  // The selected map feature answers the `entityref` step, the way the mobile Map or List selector
  // does: one already selected when the form opens links the form to it right away, and picking
  // one anywhere in the dashboard while the form waits for its target does the same.
  val selectionTarget = uiState.entityRefTargetFromSelection
  LaunchedEffect(controller, selectionTarget?.id) {
    selectionTarget?.let { actions.selectEntityRefForActiveForm(it.id) }
  }

  WebFormPanelChrome(
    title = title,
    subtitle =
      when {
        entity != null -> "${entity.label} • ${entity.datasetName}"
        form != null && !form.requiresEntity ->
          stringResource(Res.string.web_new_record_at_location, userGpsCoordinatesLabel)
        form != null ->
          stringResource(
            Res.string.web_select_target_to_begin,
            form.targetTypeText.resolve().lowercase(),
          )
        else -> null
      },
    onClose = onRequestClose,
    headerAccessory =
      onCollapse?.let { collapse ->
        {
          IconButton(onClick = collapse, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Outlined.CollapseContent,
              contentDescription = stringResource(Res.string.web_collapse_form),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      },
    modifier = modifier,
  ) {
    if (uiState.isActiveSurveyPublicData) {
      PublicDataInFormBanner()
    }
    Column(
      modifier =
        Modifier.weight(1f, fill = false)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(16.dp)
    ) {
      if (needsEntity && form != null) {
        WebEntityRefPicker(uiState = uiState, actions = actions, form = form)
      } else {
        val mediaCaptureHandler = remember { PlatformMediaCaptureHandler() }
        CompositionLocalProvider(LocalMediaCaptureHandler provides mediaCaptureHandler) {
          CompactFormRunner(
            controller = controller,
            onCancel = onRequestClose,
            onSubmitted = { result: FinalizationResult.Success ->
              actions.completeActiveFormSubmission(result.recordInstance, result.entityStates)
            },
            // The target feature is picked on the map or in the lists, not in a card.
            questionFilter = { step -> !isWizardStepEntityRef(step) },
            // No field GPS in a browser: every geometry question is drawn on the main map.
            geometryInput = CompactGeometryInput.MapDrawing(host = uiState.webMapDrawing),
            // Clicking one of the form's geometries on the map jumps to its question. The request
            // keeps its token, so the runner handles each click once without it being consumed.
            focusRequest =
              uiState.webFormFocusRequest?.let { CompactFocusRequest(it.path, it.token) },
          )
        }
      }
    }
  }
}

/**
 * Web counterpart of the mobile `entityref` Map or List selector: an instruction to click a
 * matching feature on the map or in the left-hand list, plus a searchable list of candidates.
 */
@Composable
private fun WebEntityRefPicker(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  form: FormPreviewItem,
) {
  val candidates = uiState.filteredEntityRefCandidates
  val total = uiState.allDatasetEntitiesForForm(form).size
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.TouchApp,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text =
            stringResource(
              Res.string.web_entity_ref_picker_instructions,
              form.targetTypeText.resolve().lowercase(),
            ),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    OutlinedTextField(
      value = uiState.entityRefSearchQuery,
      onValueChange = { actions.updateEntityRefSearchQuery(it) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = stringResource(Res.string.web_search_map_features),
          modifier = Modifier.size(18.dp),
        )
      },
      trailingIcon = {
        if (uiState.entityRefSearchQuery.isNotEmpty()) {
          IconButton(onClick = { actions.clearEntityRefSearchQuery() }) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.web_clear_search),
              modifier = Modifier.size(16.dp),
            )
          }
        }
      },
      placeholder = {
        Text(
          text =
            stringResource(
              Res.string.web_entity_ref_filter_placeholder,
              form.targetDatasetName.lowercase(),
              total,
            ),
          style = MaterialTheme.typography.bodySmall,
        )
      },
    )
    if (candidates.isEmpty()) {
      Text(
        text =
          stringResource(
            if (uiState.entityRefSearchQuery.isBlank()) {
              Res.string.web_entity_ref_empty
            } else {
              Res.string.web_entity_ref_no_matches
            },
            form.targetDatasetName.lowercase(),
          ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    candidates.take(40).forEach { candidate ->
      val isEligible = uiState.isFormButtonEnabled(candidate, form)
      OutlinedCard(
        onClick = { actions.selectEntityRefForActiveForm(candidate.id) },
        enabled = isEligible,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors =
          CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          EntityGeometryIcon(entity = candidate, size = 18.dp)
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = candidate.label,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            GeoIdText(
              entity = candidate,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (!isEligible || candidate.hasWorkflowStatus) {
            TonalBadge(
              text =
                if (isEligible) candidate.mapStatusSummaryBadge
                else stringResource(Res.string.web_completed),
              tone =
                when {
                  !isEligible -> BadgeTone.NEUTRAL
                  candidate.markerSymbol == "✓" -> BadgeTone.PRIMARY
                  candidate.markerSymbol == "◐" -> BadgeTone.TERTIARY
                  else -> BadgeTone.WARNING
                },
            )
          }
        }
      }
    }
  }
}

/**
 * **Collect data** action for the web dashboard's toolbar: the counterpart of mobile's bottom FAB
 * and its Available Forms sheet. Opens a menu of the survey's forms; choosing one starts it in the
 * right-hand panel via [DataCollectionActions.launchFormFromFab]. Forms whose features are all
 * completed are listed but disabled. When [onDownloadSurveyKml] is provided, also offers
 * downloading the survey's KML balloon forms for Google Earth.
 */
@Composable
internal fun WebCollectDataMenuButton(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  onManageForms: (() -> Unit)? = null,
  onDownloadSurveyKml: (() -> Unit)? = null,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    WebHeaderButton(
      text = stringResource(Res.string.web_collect_data),
      icon = Icons.Outlined.Add,
      onClick = { expanded = true },
      tonal = true,
    )
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
    ) {
      Text(
        text = stringResource(Res.string.web_start_a_form),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )
      uiState.webForms.forEach { form ->
        CollectDataFormMenuItem(
          form = form,
          eligibleCount = uiState.eligibleEntitiesForForm(form).size,
          onClick = {
            expanded = false
            actions.launchFormFromFab(form.id)
          },
        )
      }
      if (uiState.webForms.isEmpty()) {
        CollectDataEmptyFormsSection(
          hasAnyForms = uiState.forms.isNotEmpty(),
          onManageForms = onManageForms,
          onDismiss = { expanded = false },
        )
      }
      CollectDataGoogleEarthMenuItems(
        onDownloadSurveyKml = onDownloadSurveyKml,
        onDismiss = { expanded = false },
      )
    }
  }
}

@Composable
private fun CollectDataEmptyFormsSection(
  hasAnyForms: Boolean,
  onManageForms: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  Text(
    text =
      stringResource(
        if (hasAnyForms) Res.string.web_no_forms_available_on_web
        else Res.string.web_survey_has_no_forms
      ),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
  if (onManageForms != null) {
    DropdownMenuItem(
      text = {
        Text(
          text = stringResource(Res.string.web_manage_survey),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      },
      onClick = {
        onDismiss()
        onManageForms()
      },
    )
  }
}

@Composable
private fun CollectDataFormMenuItem(
  form: FormPreviewItem,
  eligibleCount: Int,
  onClick: () -> Unit,
) {
  val canLaunch = !form.requiresEntity || eligibleCount > 0
  DropdownMenuItem(
    enabled = canLaunch,
    text = {
      Column {
        Text(
          text = form.ctaLabel.ifBlank { form.title },
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            pluralStringResource(
                Res.plurals.web_form_question_count,
                form.questionCount,
                form.questionCount,
              )
              .let { questions ->
                if (form.requiresEntity) {
                  stringResource(
                    Res.string.web_form_menu_summary_remaining,
                    questions,
                    eligibleCount,
                    form.targetDatasetName.lowercase(),
                  )
                } else {
                  stringResource(Res.string.web_form_menu_summary_new_record, questions)
                }
              },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Outlined.Description,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
    },
    onClick = onClick,
  )
}

@Composable
private fun CollectDataGoogleEarthMenuItems(
  onDownloadSurveyKml: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  if (onDownloadSurveyKml == null) return
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  DropdownMenuItem(
    text = {
      Text(
        text = stringResource(Res.string.web_download_survey_kml),
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Outlined.Download,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
    },
    onClick = {
      onDismiss()
      onDownloadSurveyKml()
    },
  )
}
