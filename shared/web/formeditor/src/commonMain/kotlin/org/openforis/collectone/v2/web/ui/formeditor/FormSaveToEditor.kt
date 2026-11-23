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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorDatasetProperty
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EntityIdSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.RadioButton
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.selectable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_choose
import org.openforis.collectone.v2.web.resources.formeditor_dataset_kind_data_table
import org.openforis.collectone.v2.web.resources.formeditor_dataset_kind_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_dont_update
import org.openforis.collectone.v2.web.resources.formeditor_fields_to_update
import org.openforis.collectone.v2.web.resources.formeditor_fields_to_update_empty
import org.openforis.collectone.v2.web.resources.formeditor_find_by_selected_map_feature_help
import org.openforis.collectone.v2.web.resources.formeditor_find_by_selected_table_row_help
import org.openforis.collectone.v2.web.resources.formeditor_find_map_feature_by
import org.openforis.collectone.v2.web.resources.formeditor_find_table_row_by
import org.openforis.collectone.v2.web.resources.formeditor_geometry_location
import org.openforis.collectone.v2.web.resources.formeditor_matches_property
import org.openforis.collectone.v2.web.resources.formeditor_picked_from_list
import org.openforis.collectone.v2.web.resources.formeditor_property_already_updated
import org.openforis.collectone.v2.web.resources.formeditor_question
import org.openforis.collectone.v2.web.resources.formeditor_question_answer
import org.openforis.collectone.v2.web.resources.formeditor_save_to_create_data_table_description
import org.openforis.collectone.v2.web.resources.formeditor_save_to_create_linked_description
import org.openforis.collectone.v2.web.resources.formeditor_save_to_create_map_layer_description
import org.openforis.collectone.v2.web.resources.formeditor_save_to_summary_update_in
import org.openforis.collectone.v2.web.resources.formeditor_save_to_summary_with_status
import org.openforis.collectone.v2.web.resources.formeditor_save_to_update_description
import org.openforis.collectone.v2.web.resources.formeditor_save_to_update_unavailable
import org.openforis.collectone.v2.web.resources.formeditor_selected_on_map
import org.openforis.collectone.v2.web.resources.formeditor_submission_adds_map_feature_to
import org.openforis.collectone.v2.web.resources.formeditor_submission_adds_table_row_to
import org.openforis.collectone.v2.web.resources.formeditor_submission_saved_without_dataset
import org.openforis.collectone.v2.web.resources.formeditor_submission_updates_map_feature_in
import org.openforis.collectone.v2.web.resources.formeditor_submission_updates_table_row_in
import org.openforis.collectone.v2.web.resources.formeditor_submission_updates_unchosen
import org.openforis.collectone.v2.web.resources.formeditor_submissions_always_kept
import org.openforis.collectone.v2.web.resources.formeditor_update_target_label
import org.openforis.collectone.v2.web.resources.formeditor_updates
import org.openforis.collectone.v2.web.resources.formeditor_when_form_submitted
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.SectionLabel

/** Chooses between adding new features and updating existing ones, and how updates work. */
@Composable
internal fun SaveToEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
) {
  val form = uiState.form
  val saveTo = form.saveTo
  val targets = uiState.updateTargets
  val linked = uiState.datasets.firstOrNull { it.isLinkedToThisForm }
  val updateTarget = if (saveTo.mode == SaveToMode.UPDATE) uiState.saveTarget else null

  SectionLabel(stringResource(Res.string.formeditor_when_form_submitted))
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SaveToOption(
      selected = saveTo.mode == SaveToMode.CREATE,
      title = SaveToRules.createLabel(form.hasGeometry),
      description =
        linked?.let {
          stringResource(Res.string.formeditor_save_to_create_linked_description, it.displayName)
        }
          ?: stringResource(
            if (form.hasGeometry) Res.string.formeditor_save_to_create_map_layer_description
            else Res.string.formeditor_save_to_create_data_table_description
          ),
      enabled = !uiState.isReadOnly,
      onClick = { onSaveToModeChange(SaveToMode.CREATE) },
    )
    SaveToOption(
      selected = saveTo.mode == SaveToMode.UPDATE,
      title = SaveToRules.updateLabel(updateTarget),
      description =
        if (targets.isEmpty() && saveTo.mode != SaveToMode.UPDATE) {
          stringResource(Res.string.formeditor_save_to_update_unavailable)
        } else {
          stringResource(Res.string.formeditor_save_to_update_description)
        },
      enabled = !uiState.isReadOnly && (targets.isNotEmpty() || saveTo.mode == SaveToMode.UPDATE),
      onClick = { onSaveToModeChange(SaveToMode.UPDATE) },
    )
  }
  if (saveTo.mode == SaveToMode.UPDATE) UpdateSettings(uiState, actions, targets)
  Text(
    text = stringResource(Res.string.formeditor_submissions_always_kept),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  StatusMarkerEditor(uiState, actions)
}

@Composable
private fun SaveToOption(
  selected: Boolean,
  title: String,
  description: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(
          selected = selected,
          enabled = enabled,
          role = Role.RadioButton,
          onClick = onClick,
        )
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    RadioButton(selected = selected, onClick = null, enabled = enabled)
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      val contentAlpha = if (enabled) 1f else 0.38f
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
      )
    }
  }
}

/** Target dataset, feature lookup, and field mapping for [SaveToMode.UPDATE]. */
@Composable
private fun UpdateSettings(
  uiState: FormEditorUiState,
  actions: SaveToActions,
  targets: List<EditorDataset>,
) {
  val form = uiState.form
  val saveTo = form.saveTo
  val target = uiState.saveTarget
  val choose = stringResource(Res.string.formeditor_choose)
  val mapLayerKind = stringResource(Res.string.formeditor_dataset_kind_map_layer)
  val dataTableKind = stringResource(Res.string.formeditor_dataset_kind_data_table)
  DropdownSelector(
    label = stringResource(Res.string.formeditor_update_target_label),
    selectedText = target?.displayName ?: choose,
    options = targets,
    optionText = { "${it.displayName} (${if (it.isMapLayer) mapLayerKind else dataTableKind})" },
    optionIcon = { datasetIcon(it) },
    onSelect = { actions.setTargetDataset(it.id) },
    enabled = !uiState.isReadOnly,
  )
  if (target == null) return

  SectionLabel(
    stringResource(
      if (target.isMapLayer) Res.string.formeditor_find_map_feature_by
      else Res.string.formeditor_find_table_row_by
    )
  )
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    SegmentedButton(
      selected = saveTo.idSource == EntityIdSource.SELECTED_FEATURE,
      onClick = { actions.setIdSource(EntityIdSource.SELECTED_FEATURE) },
      enabled = !uiState.isReadOnly,
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
      label = {
        Text(
          stringResource(
            if (target.isMapLayer) Res.string.formeditor_selected_on_map
            else Res.string.formeditor_picked_from_list
          )
        )
      },
    )
    SegmentedButton(
      selected = saveTo.idSource == EntityIdSource.QUESTION,
      onClick = { actions.setIdSource(EntityIdSource.QUESTION) },
      enabled = !uiState.isReadOnly,
      shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
      label = { Text(stringResource(Res.string.formeditor_question_answer)) },
    )
  }
  when (saveTo.idSource) {
    EntityIdSource.SELECTED_FEATURE ->
      HelperText(
        if (target.isMapLayer) {
          stringResource(Res.string.formeditor_find_by_selected_map_feature_help)
        } else {
          stringResource(Res.string.formeditor_find_by_selected_table_row_help, target.displayName)
        }
      )
    EntityIdSource.QUESTION -> {
      val question = form.find(saveTo.idQuestionKey)
      DropdownSelector(
        label = stringResource(Res.string.formeditor_question),
        selectedText = question?.let { "${it.name} — ${it.label}" } ?: choose,
        options = SaveToRules.savableQuestions(form),
        optionText = { "${it.name} — ${it.label}" },
        optionIcon = { questionTypeIcon(it.type) },
        onSelect = { actions.setIdQuestion(it.key) },
        enabled = !uiState.isReadOnly,
      )
      DropdownSelector(
        label = stringResource(Res.string.formeditor_matches_property),
        selectedText = target.property(saveTo.idMatchProperty)?.let(::propertyText) ?: choose,
        options = target.matchableProperties,
        optionText = { propertyText(it) },
        onSelect = { actions.setIdMatchProperty(it.name) },
        enabled = !uiState.isReadOnly,
      )
    }
  }
  SaveToRules.entityIdExpression(form, target)?.let { xpath ->
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "id=\"$xpath\"",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.padding(8.dp),
      )
    }
  }

  SectionLabel(stringResource(Res.string.formeditor_fields_to_update))
  val questions =
    SaveToRules.savableQuestions(form).filterNot {
      saveTo.idSource == EntityIdSource.QUESTION && it.key == saveTo.idQuestionKey
    }
  if (questions.isEmpty()) {
    HelperText(stringResource(Res.string.formeditor_fields_to_update_empty))
  }
  questions.forEach { question -> FieldMappingRow(uiState, actions, question, target) }
}

/** One question and the property its answer updates. */
@Composable
private fun FieldMappingRow(
  uiState: FormEditorUiState,
  actions: SaveToActions,
  question: EditorQuestion,
  target: EditorDataset,
) {
  val saveTo = uiState.form.saveTo
  val property = saveTo.propertyFor(question.key)
  val options: List<String?> = listOf(null) + SaveToRules.propertyOptions(question, target)
  val isDuplicate =
    property != null &&
      saveTo.mappings.count {
        it.property == property && uiState.form.find(it.questionKey) != null
      } > 1
  val dontUpdate = stringResource(Res.string.formeditor_dont_update)
  val geometryLabel = stringResource(Res.string.formeditor_geometry_location)
  val warning =
    when {
      isDuplicate ->
        stringResource(Res.string.formeditor_property_already_updated, property.orEmpty())
      property != null ->
        SaveToRules.compatibilityWarning(question, property, target)?.toUiText()?.resolve()
      else -> null
    }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        questionTypeIcon(question.type),
        contentDescription = stringResource(question.type.labelRes),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(0.45f)) {
        Text(
          text = question.label.ifBlank { question.name },
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = question.name,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      DropdownSelector(
        label = stringResource(Res.string.formeditor_updates),
        selectedText = property?.let { mappingText(it, target, geometryLabel) } ?: dontUpdate,
        options = options,
        optionText = { option ->
          option?.let { mappingText(it, target, geometryLabel) } ?: dontUpdate
        },
        onSelect = { actions.setMapping(question.key, it) },
        enabled = !uiState.isReadOnly,
        modifier = Modifier.weight(0.55f),
      )
    }
    if (warning != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Outlined.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          text = warning,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}

internal fun datasetIcon(dataset: EditorDataset) =
  if (dataset.isMapLayer) Icons.Outlined.Layers else Icons.Outlined.TableChart

internal fun propertyText(property: EditorDatasetProperty): String =
  if (property.label.isBlank() || property.label == property.name) property.name
  else "${property.label} (${property.name})"

private fun mappingText(property: String, target: EditorDataset, geometryLabel: String): String =
  if (property == SaveToRules.GEOMETRY_PROPERTY) geometryLabel
  else target.property(property)?.let(::propertyText) ?: property

@Composable
internal fun submissionDescription(form: EditorForm, target: EditorDataset?): String =
  stringResource(
    when {
      form.saveTo.mode == SaveToMode.UPDATE ->
        when {
          target == null -> Res.string.formeditor_submission_updates_unchosen
          target.isMapLayer -> Res.string.formeditor_submission_updates_map_feature_in
          else -> Res.string.formeditor_submission_updates_table_row_in
        }
      target == null -> Res.string.formeditor_submission_saved_without_dataset
      target.isMapLayer -> Res.string.formeditor_submission_adds_map_feature_to
      else -> Res.string.formeditor_submission_adds_table_row_to
    }
  )

@Composable
internal fun saveToSummary(form: EditorForm, target: EditorDataset?): String {
  val base =
    when (form.saveTo.mode) {
      SaveToMode.CREATE -> SaveToRules.createLabel(form.hasGeometry)
      SaveToMode.UPDATE ->
        target?.let {
          stringResource(
            Res.string.formeditor_save_to_summary_update_in,
            SaveToRules.updateLabel(it),
            it.displayName,
          )
        } ?: SaveToRules.updateLabel(null)
    }
  val status = form.saveTo.status
  if (!status.enabled) return base
  val badges =
    (status.rules.map { "${it.badge.symbol.symbol} ${it.badge.label}" } +
        "${status.defaultBadge.symbol.symbol} ${status.defaultBadge.label}")
      .distinct()
      .joinToString(" / ")
  return stringResource(Res.string.formeditor_save_to_summary_with_status, base, badges)
}
