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
package org.openforis.collectone.v2.web.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_add_another_form
import org.openforis.collectone.v2.web.resources.wizard_add_form
import org.openforis.collectone.v2.web.resources.wizard_hide_forms_and_layers
import org.openforis.collectone.v2.web.resources.wizard_show_forms_and_layers
import org.openforis.collectone.v2.web.resources.wizard_step_forms
import org.openforis.collectone.v2.web.resources.wizard_untitled
import org.openforis.collectone.v2.web.resources.wizard_untitled_form
import org.openforis.collectone.v2.web.ui.common.NavItem
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorPage
import org.openforis.collectone.v2.web.ui.surveyeditor.AddFormDialog
import org.openforis.collectone.v2.web.ui.surveyeditor.ChoiceDatasetCreationDialogs
import org.openforis.collectone.v2.web.ui.surveyeditor.EntityDatasetEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorActions
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.rememberChoiceDatasetCreationState
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

// ---------------------------------------------------------------------------------------------
// Step 5: Forms
// ---------------------------------------------------------------------------------------------

@Composable
internal fun FormQuestionsStepContent(
  uiState: CreateSurveyWizardUiState,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  isDarkTheme: Boolean,
) {
  val forms = editorUiState.forms
  val primaryForm = uiState.primaryForm ?: forms.firstOrNull()
  val section = editorUiState.section
  val selectedDataset =
    (section as? SurveyEditorSection.Dataset)?.let { s ->
      editorUiState.datasets.firstOrNull { it.key == s.key }
    }
  val selectedForm =
    if (selectedDataset != null) {
      null
    } else {
      (section as? SurveyEditorSection.Form)?.let { s -> forms.firstOrNull { it.key == s.key } }
        ?: primaryForm
    }
  // Progressive disclosure: the outline panel is collapsed by default and can be toggled open
  // or expands automatically when a choice question references a Map layer or Data table.
  val hasDatasetLinkedChoices = forms.any { entry ->
    entry.form.questions.any { it.choiceSource != ChoiceSource.MANUAL }
  }
  var showOutline by remember { mutableStateOf(false) }
  LaunchedEffect(hasDatasetLinkedChoices) {
    if (hasDatasetLinkedChoices) showOutline = true
  }
  var showAddForm by remember { mutableStateOf(false) }
  var showAddMapLayer by remember { mutableStateOf(false) }
  var showAddDataTable by remember { mutableStateOf(false) }
  val choiceDatasetCreation = rememberChoiceDatasetCreationState()
  ChoiceDatasetCreationDialogs(choiceDatasetCreation, editorUiState, editorActions)
  if (showAddForm) {
    AddFormDialog(
      actions = editorActions,
      onDismiss = { showAddForm = false },
      library = editorUiState.library,
    )
  }
  if (showAddMapLayer) {
    org.openforis.collectone.v2.web.ui.surveyeditor.AddMapLayerDialog(
      uiState = editorUiState,
      actions = editorActions,
      onDismiss = { showAddMapLayer = false },
      offerTemplates = true,
    )
  }
  if (showAddDataTable) {
    org.openforis.collectone.v2.web.ui.surveyeditor.AddDataTableDialog(
      actions = editorActions,
      onDismiss = { showAddDataTable = false },
      library = editorUiState.library,
      onAddFromTemplate = { editorActions.addFormFromTemplate(it, openDataset = true) },
    )
  }

  Row(modifier = Modifier.fillMaxSize()) {
    if (showOutline) {
      WizardSurveyOutline(
        editorUiState = editorUiState,
        selectedFormKey = selectedForm?.key,
        selectedDatasetKey = selectedDataset?.key,
        onSelect = editorActions::select,
        onAddForm = { showAddForm = true },
        onAddMapLayer = { showAddMapLayer = true },
        onAddDataTable = { showAddDataTable = true },
        onCollapse = { showOutline = false },
        modifier = Modifier.width(264.dp).fillMaxHeight(),
      )
      VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
      if (!showOutline) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TextButton(onClick = { showOutline = true }) {
            Icon(
              Icons.Outlined.ChevronRight,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.wizard_show_forms_and_layers))
          }
          TextButton(onClick = { showAddForm = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.wizard_add_another_form))
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
          selectedDataset != null ->
            key(selectedDataset.key) {
              EntityDatasetEditor(editorUiState, editorActions, selectedDataset)
            }
          selectedForm != null ->
            key(selectedForm.key) {
              val formEditor =
                remember(selectedForm.key) { editorActions.formEditor(selectedForm.key) }
              val formState by formEditor.uiState.collectAsState()
              val previewController by formEditor.previewController.collectAsState()
              FormEditorPage(
                uiState = formState,
                actions = formEditor,
                previewController = previewController,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.fillMaxSize(),
                onCreateDataset = { editorActions.createDatasetForForm(selectedForm.key) },
                // The primary Form is wired to the primary Map layer, so only others can be
                // deleted.
                onDelete =
                  if (selectedForm.key != primaryForm?.key) {
                    { editorActions.deleteForm(selectedForm.key) }
                  } else {
                    null
                  },
                onSaveToModeChange = { editorActions.setFormSaveToMode(selectedForm.key, it) },
                onOpenDataset = { id ->
                  editorUiState.datasets
                    .firstOrNull { it.id == id }
                    ?.let { editorActions.select(SurveyEditorSection.Dataset(it.key)) }
                },
                onOpenForm = { formKey -> editorActions.select(SurveyEditorSection.Form(formKey)) },
                onCreateChoiceDataset = { kind, questionKey ->
                  showOutline = true
                  choiceDatasetCreation.request(selectedForm.key, questionKey, kind)
                },
              )
            }
          else ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Button(onClick = { editorActions.addForm() }) {
                Text(stringResource(Res.string.wizard_add_form))
              }
            }
        }
      }
    }
  }
}

/**
 * Collapsible outline of the survey's Forms, Map layers, and Data tables for the Forms step, shown
 * when the survey has more than one Form or dataset. Map layer and Data table groups only appear
 * when they have entries, keeping the outline short.
 */
@Composable
private fun WizardSurveyOutline(
  editorUiState: SurveyEditorUiState,
  selectedFormKey: String?,
  selectedDatasetKey: String?,
  onSelect: (SurveyEditorSection) -> Unit,
  onAddForm: () -> Unit,
  onAddMapLayer: () -> Unit,
  onAddDataTable: () -> Unit,
  onCollapse: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(Res.string.wizard_step_forms),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAddForm) {
          Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.wizard_add_form))
        }
        IconButton(onClick = onCollapse) {
          Icon(
            Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(Res.string.wizard_hide_forms_and_layers),
          )
        }
      }
      editorUiState.forms.forEach { entry ->
        NavItem(
          label = entry.form.title.ifBlank { stringResource(Res.string.wizard_untitled_form) },
          icon = Icons.Outlined.Description,
          selected = entry.key == selectedFormKey,
          onClick = { onSelect(SurveyEditorSection.Form(entry.key)) },
          trailing = "${entry.form.questions.size}",
          hasIssues = editorUiState.formIssues(entry).isNotEmpty(),
        )
      }
      OutlineDatasetGroup(
        title = stringResource(DatasetKind.MAP_LAYER.pluralRes),
        datasets = editorUiState.mapLayers,
        icon = Icons.Outlined.Layers,
        editorUiState = editorUiState,
        selectedDatasetKey = selectedDatasetKey,
        onSelect = onSelect,
        onAdd = onAddMapLayer,
      )
      OutlineDatasetGroup(
        title = stringResource(DatasetKind.DATA_TABLE.pluralRes),
        datasets = editorUiState.dataTables,
        icon = Icons.AutoMirrored.Outlined.List,
        editorUiState = editorUiState,
        selectedDatasetKey = selectedDatasetKey,
        onSelect = onSelect,
        onAdd = onAddDataTable,
      )
    }
  }
}

@Composable
private fun OutlineDatasetGroup(
  title: String,
  datasets: List<EntityDataset>,
  icon: ImageVector,
  editorUiState: SurveyEditorUiState,
  selectedDatasetKey: String?,
  onSelect: (SurveyEditorSection) -> Unit,
  onAdd: () -> Unit,
) {
  HorizontalDivider(
    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onAdd) {
      Icon(Icons.Outlined.Add, contentDescription = null)
    }
  }
  datasets.forEach { dataset ->
    NavItem(
      label = dataset.displayName.ifBlank { stringResource(Res.string.wizard_untitled) },
      icon = icon,
      selected = dataset.key == selectedDatasetKey,
      onClick = { onSelect(SurveyEditorSection.Dataset(dataset.key)) },
      hasIssues = editorUiState.datasetIssueCount(dataset) > 0,
    )
  }
}
