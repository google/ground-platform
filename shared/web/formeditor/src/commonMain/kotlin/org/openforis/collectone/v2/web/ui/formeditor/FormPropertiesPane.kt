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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssue
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.common.displayMessage
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_advanced
import org.openforis.collectone.v2.web.resources.formeditor_availability
import org.openforis.collectone.v2.web.resources.formeditor_button_label
import org.openforis.collectone.v2.web.resources.formeditor_button_label_help
import org.openforis.collectone.v2.web.resources.formeditor_collapse_section
import org.openforis.collectone.v2.web.resources.formeditor_create_data_table_for_form
import org.openforis.collectone.v2.web.resources.formeditor_create_map_layer_for_form
import org.openforis.collectone.v2.web.resources.formeditor_edit_data_table
import org.openforis.collectone.v2.web.resources.formeditor_edit_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_expand_section
import org.openforis.collectone.v2.web.resources.formeditor_form_no_questions_yet
import org.openforis.collectone.v2.web.resources.formeditor_form_properties
import org.openforis.collectone.v2.web.resources.formeditor_form_properties_counts
import org.openforis.collectone.v2.web.resources.formeditor_form_properties_path_count
import org.openforis.collectone.v2.web.resources.formeditor_form_properties_question_count
import org.openforis.collectone.v2.web.resources.formeditor_form_title
import org.openforis.collectone.v2.web.resources.formeditor_linked_data_table_description
import org.openforis.collectone.v2.web.resources.formeditor_linked_map_layer_description
import org.openforis.collectone.v2.web.resources.formeditor_linked_to_dataset
import org.openforis.collectone.v2.web.resources.formeditor_open_dataset
import org.openforis.collectone.v2.web.resources.formeditor_submissions
import org.openforis.collectone.v2.web.resources.formeditor_template_adds_data_table
import org.openforis.collectone.v2.web.resources.formeditor_template_adds_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_template_section
import org.openforis.collectone.v2.web.resources.formeditor_template_title
import org.openforis.collectone.v2.web.resources.formeditor_unlink_data_table
import org.openforis.collectone.v2.web.resources.formeditor_unlink_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_untitled_form
import org.openforis.collectone.v2.web.ui.common.SectionLabel

/**
 * Whether "Advanced" sections are expanded, shared by every Advanced section in the editor.
 *
 * Sections start collapsed (unless their settings are customized or have issues). Expanding or
 * collapsing one does the same to all of them for the rest of the session.
 */
object AdvancedDisclosure {
  /** Explicit choice, or `null` if the user hasn't toggled any Advanced section yet. */
  var expanded: Boolean? by mutableStateOf(null)

  /** Whether a section is expanded; [autoExpand] applies until the user toggles one. */
  fun isExpanded(autoExpand: Boolean): Boolean = expanded ?: autoExpand

  fun toggle(autoExpand: Boolean) {
    expanded = !isExpanded(autoExpand)
  }
}

/** Form-level properties, shown when the Form itself (or no question) is selected. */
@Composable
internal fun FormProperties(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
  onOpenDataset: ((String) -> Unit)?,
  onCreateDataset: (() -> Unit)? = null,
  onUnlinkDataset: ((String) -> Unit)? = null,
  templateDetails: (@Composable () -> Unit)? = null,
) {
  val form = uiState.form
  val formIssues = uiState.formIssues
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    FormPropertiesHeader(form.questions.size, uiState.pathCount)
    OutlinedTextField(
      value = form.title,
      onValueChange = actions::updateTitle,
      enabled = !uiState.isReadOnly,
      label = {
        Text(
          stringResource(
            if (uiState.isTemplate) Res.string.formeditor_template_title
            else Res.string.formeditor_form_title
          )
        )
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = form.ctaLabel,
      onValueChange = actions::updateCtaLabel,
      enabled = !uiState.isReadOnly,
      label = { Text(stringResource(Res.string.formeditor_button_label)) },
      placeholder = {
        Text(form.title.ifBlank { stringResource(Res.string.formeditor_untitled_form) })
      },
      supportingText = { Text(stringResource(Res.string.formeditor_button_label_help)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (form.questions.isEmpty()) {
      Text(
        text = stringResource(Res.string.formeditor_form_no_questions_yet),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (formIssues.isNotEmpty()) IssueList(formIssues)

    SectionLabel(stringResource(Res.string.formeditor_availability))
    AvailabilityToggles(uiState, actions)

    if (uiState.isTemplate) {
      TemplateProperties(uiState, templateDetails)
    } else {
      SurveySubmissionsSection(
        uiState = uiState,
        actions = actions,
        onSaveToModeChange = onSaveToModeChange,
        onOpenDataset = onOpenDataset,
        onCreateDataset = onCreateDataset,
        onUnlinkDataset = onUnlinkDataset,
      )
    }
  }
}

@Composable
private fun FormPropertiesHeader(questionCount: Int, pathCount: Long) {
  Column {
    Text(
      text = stringResource(Res.string.formeditor_form_properties),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        stringResource(
          Res.string.formeditor_form_properties_counts,
          pluralStringResource(
            Res.plurals.formeditor_form_properties_question_count,
            questionCount,
            questionCount,
          ),
          pluralStringResource(
            Res.plurals.formeditor_form_properties_path_count,
            pathCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            pathCount,
          ),
        ),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SurveySubmissionsSection(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
  onOpenDataset: ((String) -> Unit)?,
  onCreateDataset: (() -> Unit)?,
  onUnlinkDataset: ((String) -> Unit)?,
) {
  val form = uiState.form
  val target = uiState.saveTarget
  val linkedDataset = uiState.datasets.firstOrNull { it.isLinkedToThisForm }
  SectionLabel(stringResource(Res.string.formeditor_submissions))
  when {
    form.saveTo.mode == SaveToMode.CREATE && linkedDataset != null ->
      LinkedDatasetCard(uiState.isReadOnly, linkedDataset, onOpenDataset, onUnlinkDataset)
    !uiState.isReadOnly && form.saveTo.mode == SaveToMode.CREATE && onCreateDataset != null ->
      CreateLinkedDatasetCard(
        form.hasGeometry,
        submissionDescription(form, target),
        onCreateDataset,
      )
    else -> TargetDatasetSummary(submissionDescription(form, target), target, onOpenDataset)
  }
  AdvancedSection(
    summary = saveToSummary(form, target),
    autoExpand = form.saveTo.isCustomized || uiState.formIssues.isNotEmpty(),
  ) {
    SaveToEditor(uiState, actions, onSaveToModeChange)
  }
}

@Composable
private fun LinkedDatasetCard(
  isReadOnly: Boolean,
  linkedDataset: EditorDataset,
  onOpenDataset: ((String) -> Unit)?,
  onUnlinkDataset: ((String) -> Unit)?,
) {
  val isMapLayer = linkedDataset.isMapLayer
  val canUnlink = !isReadOnly && onUnlinkDataset != null && linkedDataset.key.isNotBlank()
  // The same neutral surface as the card shown before a map layer or data table is linked: the
  // link is ordinary form configuration, not a status to call out.
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Outlined.Link,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          stringResource(Res.string.formeditor_linked_to_dataset, linkedDataset.displayName),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Text(
        stringResource(
          if (isMapLayer) Res.string.formeditor_linked_map_layer_description
          else Res.string.formeditor_linked_data_table_description
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (onOpenDataset != null || canUnlink) {
        LinkedDatasetActionsRow(
          linkedDataset = linkedDataset,
          onOpenDataset = onOpenDataset,
          onUnlinkDataset = onUnlinkDataset.takeIf { canUnlink },
        )
      }
    }
  }
}

@Composable
private fun LinkedDatasetActionsRow(
  linkedDataset: EditorDataset,
  onOpenDataset: ((String) -> Unit)?,
  onUnlinkDataset: ((String) -> Unit)?,
) {
  val isMapLayer = linkedDataset.isMapLayer
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (onOpenDataset != null) {
      Button(onClick = { onOpenDataset(linkedDataset.id) }) {
        Icon(datasetIcon(linkedDataset), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(
            if (isMapLayer) Res.string.formeditor_edit_map_layer
            else Res.string.formeditor_edit_data_table
          )
        )
      }
    }
    if (onUnlinkDataset != null) {
      OutlinedButton(onClick = { onUnlinkDataset(linkedDataset.key) }) {
        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(
            if (isMapLayer) Res.string.formeditor_unlink_map_layer
            else Res.string.formeditor_unlink_data_table
          )
        )
      }
    }
  }
}

@Composable
private fun CreateLinkedDatasetCard(
  hasGeometry: Boolean,
  description: String,
  onCreateDataset: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onCreateDataset) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(
            if (hasGeometry) Res.string.formeditor_create_map_layer_for_form
            else Res.string.formeditor_create_data_table_for_form
          )
        )
      }
    }
  }
}

@Composable
private fun TargetDatasetSummary(
  description: String,
  target: EditorDataset?,
  onOpenDataset: ((String) -> Unit)?,
) {
  Text(text = description, style = MaterialTheme.typography.bodyMedium)
  if (target != null) {
    AssistChip(
      onClick = { onOpenDataset?.invoke(target.id) },
      enabled = onOpenDataset != null,
      label = { Text(target.displayName) },
      leadingIcon = {
        Icon(datasetIcon(target), contentDescription = null, modifier = Modifier.size(16.dp))
      },
      trailingIcon =
        if (onOpenDataset != null) {
          {
            Icon(
              Icons.AutoMirrored.Outlined.OpenInNew,
              contentDescription =
                stringResource(Res.string.formeditor_open_dataset, target.displayName),
              modifier = Modifier.size(16.dp),
            )
          }
        } else {
          null
        },
    )
  }
}

/**
 * Form properties of a library template ([FormEditorUiState.isTemplate]): the host's
 * [templateDetails] (description, purposes), then what a survey adding the template gets for its
 * submissions, which follows the Form's questions (a Map layer of the primary geometry question's
 * kind, else a Data table).
 */
@Composable
private fun TemplateProperties(
  uiState: FormEditorUiState,
  templateDetails: (@Composable () -> Unit)?,
) {
  SectionLabel(stringResource(Res.string.formeditor_template_section))
  templateDetails?.invoke()
  val (kind, geometryType) = uiState.templateDataset
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        if (kind == DatasetKind.MAP_LAYER) Icons.Outlined.Layers else Icons.Outlined.TableChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Text(
        if (kind == DatasetKind.MAP_LAYER) {
          stringResource(
            Res.string.formeditor_template_adds_map_layer,
            stringResource(geometryType.pluralRes).lowercase(),
          )
        } else {
          stringResource(Res.string.formeditor_template_adds_data_table)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Collapsible "Advanced" section whose expanded state is shared via [AdvancedDisclosure]. */
@Composable
internal fun AdvancedSection(
  summary: String,
  autoExpand: Boolean,
  content: @Composable () -> Unit,
) {
  val expanded = AdvancedDisclosure.isExpanded(autoExpand)
  val clickLabel =
    stringResource(
      if (expanded) Res.string.formeditor_collapse_section else Res.string.formeditor_expand_section
    )
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(
            onClickLabel = clickLabel,
            role = Role.Button,
          ) {
            AdvancedDisclosure.toggle(autoExpand)
          }
          .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Outlined.Tune,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.formeditor_advanced),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        if (!expanded) {
          Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Icon(
        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
        contentDescription = null,
      )
    }
    if (expanded) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        content()
      }
    }
  }
}

@Composable
private fun IssueList(issues: List<EditorIssue>) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      issues.forEach {
        Text("• ${it.displayMessage()}", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
internal fun HelperText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
