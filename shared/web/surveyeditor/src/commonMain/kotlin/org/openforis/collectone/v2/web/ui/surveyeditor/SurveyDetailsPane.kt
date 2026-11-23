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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.ImpactEvent
import org.openforis.collectone.v2.app.domain.model.LibraryPrograms
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.InputChip
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_danger_zone
import org.openforis.collectone.v2.web.resources.surveyeditor_danger_zone_body
import org.openforis.collectone.v2.web.resources.surveyeditor_delete_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_delete_survey_message
import org.openforis.collectone.v2.web.resources.surveyeditor_delete_survey_title
import org.openforis.collectone.v2.web.resources.surveyeditor_description
import org.openforis.collectone.v2.web.resources.surveyeditor_details_activity
import org.openforis.collectone.v2.web.resources.surveyeditor_details_activity_map_features
import org.openforis.collectone.v2.web.resources.surveyeditor_details_activity_profile
import org.openforis.collectone.v2.web.resources.surveyeditor_details_contents
import org.openforis.collectone.v2.web.resources.surveyeditor_details_data_tables
import org.openforis.collectone.v2.web.resources.surveyeditor_details_dismiss
import org.openforis.collectone.v2.web.resources.surveyeditor_details_map_layers
import org.openforis.collectone.v2.web.resources.surveyeditor_details_organization
import org.openforis.collectone.v2.web.resources.surveyeditor_details_organization_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_details_organization_missing
import org.openforis.collectone.v2.web.resources.surveyeditor_details_organization_none
import org.openforis.collectone.v2.web.resources.surveyeditor_details_organization_unknown
import org.openforis.collectone.v2.web.resources.surveyeditor_details_people
import org.openforis.collectone.v2.web.resources.surveyeditor_details_purpose_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_details_purpose_none
import org.openforis.collectone.v2.web.resources.surveyeditor_details_purpose_title
import org.openforis.collectone.v2.web.resources.surveyeditor_details_purpose_unavailable
import org.openforis.collectone.v2.web.resources.surveyeditor_details_remove_item
import org.openforis.collectone.v2.web.resources.surveyeditor_details_subtitle
import org.openforis.collectone.v2.web.resources.surveyeditor_details_survey_status
import org.openforis.collectone.v2.web.resources.surveyeditor_details_survey_title
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_publish_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_reopen_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_forms
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_survey_details
import org.openforis.collectone.v2.web.resources.surveyeditor_untitled_survey
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.PaneScaffold
import org.openforis.collectone.v2.web.ui.common.ProgramPickerSection
import org.openforis.collectone.v2.web.ui.common.PurposePackGrid
import org.openforis.collectone.v2.web.ui.common.SectionLabel
import org.openforis.collectone.v2.web.ui.text.resolve

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SurveyDetailsPane(uiState: SurveyEditorUiState, actions: SurveyEditorActions) {
  val details = uiState.details
  PaneScaffold(
    title = stringResource(Res.string.surveyeditor_nav_survey_details),
    subtitle = stringResource(Res.string.surveyeditor_details_subtitle),
  ) {
    Column(
      modifier = Modifier.widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      OutlinedTextField(
        value = details.title,
        onValueChange = { v -> actions.updateDetails { it.copy(title = v) } },
        enabled = !uiState.isReadOnly,
        label = { Text(stringResource(Res.string.surveyeditor_details_survey_title)) },
        singleLine = true,
        isError = !uiState.isReadOnly && details.title.isBlank(),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = details.description,
        onValueChange = { v -> actions.updateDetails { it.copy(description = v) } },
        enabled = !uiState.isReadOnly,
        label = { Text(stringResource(Res.string.surveyeditor_description)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      SurveyStatusSection(uiState = uiState, actions = actions)
      OrganizationSection(uiState = uiState, actions = actions)
      CostAndQuotaSection(uiState = uiState, actions = actions)
      PurposesSection(uiState = uiState, actions = actions)
      LanguageSelectorSection(uiState = uiState, actions = actions)
      SurveyAreaSection(
        uiState = uiState,
        actions = actions,
        localPlaces = uiState.localPlaces,
        searchPlaces = actions::searchPlaces,
      )
    }

    SectionLabel(stringResource(Res.string.surveyeditor_details_contents))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SummaryCard(stringResource(Res.string.surveyeditor_nav_forms), uiState.forms.size) {
        uiState.forms.firstOrNull()?.let { actions.select(SurveyEditorSection.Form(it.key)) }
      }
      SummaryCard(
        stringResource(Res.string.surveyeditor_details_map_layers),
        uiState.mapLayers.size,
      ) {
        uiState.mapLayers.firstOrNull()?.let { actions.select(SurveyEditorSection.Dataset(it.key)) }
      }
      SummaryCard(
        stringResource(Res.string.surveyeditor_details_data_tables),
        uiState.dataTables.size,
      ) {
        uiState.dataTables.firstOrNull()?.let {
          actions.select(SurveyEditorSection.Dataset(it.key))
        }
      }
      SummaryCard(
        stringResource(Res.string.surveyeditor_details_people),
        uiState.sharing.collaborators.size + 1,
      ) {
        actions.select(SurveyEditorSection.Sharing)
      }
    }

    if (uiState.activity.isNotEmpty()) ActivitySection(uiState.activity)
    if (uiState.canManageSurvey) {
      DangerZoneSection(
        surveyTitle = details.title,
        isSaving = uiState.isSaving,
        onDelete = actions::deleteSurvey,
      )
    }
  }
}

/**
 * "Danger zone" card for people who can manage the survey, with a confirmed **Delete survey**
 * action. It stays available while the survey is closed or archived, which are otherwise read-only.
 */
@Composable
private fun DangerZoneSection(surveyTitle: String, isSaving: Boolean, onDelete: () -> Unit) {
  var isConfirming by remember { mutableStateOf(false) }
  if (isConfirming) {
    AlertDialog(
      onDismissRequest = { isConfirming = false },
      title = {
        Text(
          stringResource(
            Res.string.surveyeditor_delete_survey_title,
            surveyTitle.ifBlank { stringResource(Res.string.surveyeditor_untitled_survey) },
          )
        )
      },
      text = { Text(stringResource(Res.string.surveyeditor_delete_survey_message)) },
      confirmButton = {
        Button(
          onClick = {
            isConfirming = false
            onDelete()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
          Text(stringResource(Res.string.surveyeditor_delete_survey))
        }
      },
      dismissButton = {
        TextButton(onClick = { isConfirming = false }) {
          Text(stringResource(Res.string.surveyeditor_cancel))
        }
      },
    )
  }

  SectionLabel(stringResource(Res.string.surveyeditor_danger_zone))
  OutlinedCard(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        stringResource(Res.string.surveyeditor_danger_zone_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(
        onClick = { isConfirming = true },
        enabled = !isSaving,
        colors =
          ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(
          stringResource(Res.string.surveyeditor_delete_survey),
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

/**
 * Read-only list of the survey's "data was used" events (exports, receipts, closing): what
 * happened, when, how many map features and how much area it covered, and the export profile.
 */
@Composable
private fun ActivitySection(events: List<ImpactEvent>) {
  SectionLabel(stringResource(Res.string.surveyeditor_details_activity))
  OutlinedCard(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
    Column {
      events.forEachIndexed { index, event ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(event.type.labelRes), style = MaterialTheme.typography.bodyMedium)
            Text(
              activitySummary(event),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            event.occurredAt.take(10),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/** "12 map features · 3.4 ha · Profile: eudr", leaving out what the event didn't record. */
@Composable
internal fun activitySummary(event: ImpactEvent): String {
  val n = event.featureCount
  val features = pluralStringResource(Res.plurals.surveyeditor_details_activity_map_features, n, n)
  val profile =
    event.exportProfileId?.let {
      stringResource(Res.string.surveyeditor_details_activity_profile, it)
    }
  val area =
    Measurement((event.areaHa * 10).roundToLong() / 10.0, MeasurementUnit.HECTARE, decimals = 1)
      .toUiText()
      .resolve()
  return buildList {
      add(features)
      if (event.areaHa > 0.0) add(area)
      profile?.let { add(it) }
    }
    .joinToString(" · ")
}

@Composable
private fun SummaryCard(label: String, count: Int, onClick: () -> Unit) {
  OutlinedCard(onClick = onClick, modifier = Modifier.width(160.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Shows the survey's current lifecycle state under "Survey details" with an interactive status
 * badge menu and a direct action button to publish a draft or reopen a closed/archived survey.
 */
@Composable
private fun SurveyStatusSection(uiState: SurveyEditorUiState, actions: SurveyLifecycleActions) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_details_survey_status),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SurveyStatusBadgeMenu(
              lifecycleState = uiState.lifecycleState,
              canManageSurvey = uiState.canManageSurvey,
              hasUnpublishedChanges = uiState.hasUnpublishedChanges,
              onSelectState = actions::setLifecycleState,
            )
          }
          Text(
            text = uiState.lifecycleState.statusDescription(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (uiState.canManageSurvey) {
          if (uiState.isDraft) {
            Button(
              onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) },
              enabled = uiState.issueCount == 0 && !uiState.isSaving,
            ) {
              Text(stringResource(Res.string.surveyeditor_lifecycle_publish_survey))
            }
          } else if (uiState.isReadOnly) {
            OutlinedButton(
              onClick = { actions.setLifecycleState(SurveyLifecycleState.PUBLISHED) }
            ) {
              Text(stringResource(Res.string.surveyeditor_lifecycle_reopen_survey))
            }
          }
        }
      }
    }
  }
}

/**
 * Sentinel for the "None (personal survey)" entry of the organization dropdown; its label comes
 * from resources.
 */
private val NoOrganization = Organization(id = "", name = "")

/**
 * Lets the owner move the survey into one of the organizations they belong to, or keep it personal.
 * An organization the survey already belongs to stays selectable even if the signed-in user isn't a
 * member, so opening the editor never silently changes it.
 */
@Composable
private fun OrganizationSection(uiState: SurveyEditorUiState, actions: SurveyDetailsActions) {
  val selectedId = uiState.details.organizationId
  val current = uiState.organization(selectedId)
  val options =
    remember(uiState.signedInUserOrganizations, current) {
      buildList {
        add(NoOrganization)
        addAll(uiState.signedInUserOrganizations)
        if (current != null && none { it.id == current.id }) add(current)
      }
    }
  val noOrganizationLabel = stringResource(Res.string.surveyeditor_details_organization_none)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_details_organization),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      stringResource(Res.string.surveyeditor_details_organization_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(modifier = Modifier.widthIn(max = 380.dp)) {
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_details_organization),
        selectedText =
          current?.name
            ?: selectedId?.let {
              stringResource(Res.string.surveyeditor_details_organization_unknown, it)
            }
            ?: noOrganizationLabel,
        options = options,
        optionText = { if (it === NoOrganization) noOrganizationLabel else it.name },
        enabled = !uiState.isReadOnly,
        onSelect = { actions.setOrganization(it.id.ifBlank { null }) },
      )
    }
    if (selectedId != null && current == null) {
      Text(
        stringResource(Res.string.surveyeditor_details_organization_missing),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    uiState.organizationNotice?.let { notice ->
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(
            notice.resolve(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
          )
          IconButton(onClick = actions::dismissOrganizationNotice) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.surveyeditor_details_dismiss),
            )
          }
        }
      }
    }
  }
}

/**
 * The survey's purposes and programs (`SurveyDef.purpose_ids` / `program_ids`), chosen at creation
 * and editable here with the same cards and chips. Changing them doesn't add or remove Forms.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposesSection(uiState: SurveyEditorUiState, actions: PurposeActions) {
  val context = uiState.library
  val packs = context.library.pickablePurposePacks
  val details = uiState.details
  val unavailable =
    details.purposeIds.filter { id ->
      id != SurveyWizardRules.OTHER_PURPOSE_ID && packs.none { it.id == id }
    }
  val offeredPrograms =
    LibraryPrograms.offeredFor(packs.filter { it.id in details.purposeIds }, details.programIds)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_details_purpose_title),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      stringResource(Res.string.surveyeditor_details_purpose_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (packs.isEmpty()) {
      Text(
        stringResource(Res.string.surveyeditor_details_purpose_none),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      PurposePackGrid(
        packs = packs,
        library = context.library,
        organizationName = context.organizationName,
        language = context.language,
        selectedIds = details.purposeIds,
        onToggle = actions::togglePurpose,
        otherPurposeText = details.otherPurpose,
        onOtherPurposeTextChange = actions::updateOtherPurpose,
        enabled = !uiState.isReadOnly,
      )
    }
    if (unavailable.isNotEmpty()) {
      Text(
        stringResource(Res.string.surveyeditor_details_purpose_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        unavailable.forEach { id ->
          InputChip(
            selected = false,
            onClick = { actions.togglePurpose(id) },
            enabled = !uiState.isReadOnly,
            label = { Text(id) },
            trailingIcon =
              if (uiState.isReadOnly) null
              else {
                {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription =
                      stringResource(Res.string.surveyeditor_details_remove_item, id),
                    modifier = Modifier.size(18.dp),
                  )
                }
              },
          )
        }
      }
    }
    ProgramPickerSection(
      programIds = offeredPrograms,
      selectedIds = details.programIds,
      onToggle = actions::toggleProgram,
      enabled = !uiState.isReadOnly,
    )
  }
}
