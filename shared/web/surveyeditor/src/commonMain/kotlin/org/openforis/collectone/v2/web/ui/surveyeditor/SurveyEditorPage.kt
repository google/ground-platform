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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparator
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparatorWidth
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorPage
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCard

/**
 * Survey editor page: the app header with publishing controls, a resizable left-hand navigation
 * list (Survey details, Sharing, Forms, Map layers, Data tables; drag its right border to resize it
 * via [SidePanelSeparator]), and a content pane showing the editor for the selected item.
 *
 * Edits are kept as an unpublished draft in [uiState]. [SurveyEditorActions.publish] commits them;
 * [SurveyEditorActions.close] throws them away (after the user confirms, if there are any). Both
 * are expected to leave the editor. [header] renders the page header around the editor's navigation
 * icon, title context, and publishing actions.
 */
@Composable
fun SurveyEditorPage(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  isLoading: Boolean = false,
  header:
    @Composable
    (
      navigationIcon: @Composable () -> Unit,
      context: @Composable () -> Unit,
      headerActions: @Composable () -> Unit,
    ) -> Unit,
) {
  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.fillMaxSize()) {
      SurveyEditorTopBar(uiState, actions, header)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      uiState.readOnlyMessage?.let { message ->
        ReadOnlyBanner(stringResource(message), uiState, actions)
      }
      uiState.outcomeCard?.let { card ->
        SurveyOutcomeCard(
          card = card,
          actions = actions,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).widthIn(max = 760.dp),
        )
      }
      val choiceDatasetCreation = rememberChoiceDatasetCreationState()
      ChoiceDatasetCreationDialogs(choiceDatasetCreation, uiState, actions)

      if (isLoading && uiState.surveyId.isBlank()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
          SurveyNavigation(
            uiState = uiState,
            actions = actions,
            modifier = Modifier.width(uiState.sidePanelWidthDp.dp).fillMaxHeight(),
          )
          SidePanelSeparator(
            widthDp = uiState.sidePanelWidthDp,
            onWidthChange = actions::updateSidePanelWidth,
            modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
          )
          Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
          ) {
            when (val section = uiState.section) {
              SurveyEditorSection.Details -> SurveyDetailsPane(uiState, actions)
              SurveyEditorSection.Sharing -> SharingPane(uiState, actions)
              is SurveyEditorSection.Form -> {
                val entry = uiState.selectedForm
                if (entry != null) {
                  key(entry.key) {
                    val formEditor = remember(entry.key) { actions.formEditor(entry.key) }
                    val formState by formEditor.uiState.collectAsState()
                    val previewController by formEditor.previewController.collectAsState()
                    FormEditorPage(
                      uiState = formState,
                      actions = formEditor,
                      previewController = previewController,
                      isDarkTheme = isDarkTheme,
                      onCreateDataset =
                        if (uiState.isReadOnly) null
                        else {
                          { actions.createDatasetForForm(entry.key) }
                        },
                      onDelete =
                        if (uiState.isReadOnly) null
                        else {
                          { actions.deleteForm(entry.key) }
                        },
                      onSaveToModeChange = { actions.setFormSaveToMode(entry.key, it) },
                      onOpenDataset = { id ->
                        uiState.datasets
                          .firstOrNull { it.id == id }
                          ?.let { actions.select(SurveyEditorSection.Dataset(it.key)) }
                      },
                      onUnlinkDataset =
                        if (uiState.isReadOnly) null
                        else { datasetKey -> actions.unlinkDataset(datasetKey) },
                      onCreateFormForDataset =
                        if (uiState.isReadOnly) null
                        else
                          { datasetKey -> actions.createFormForDataset(datasetKey, open = false) },
                      onOpenForm = { formKey -> actions.select(SurveyEditorSection.Form(formKey)) },
                      onCreateChoiceDataset =
                        if (uiState.isReadOnly) null
                        else
                          { kind, questionKey ->
                            choiceDatasetCreation.request(entry.key, questionKey, kind)
                          },
                    )
                  }
                }
              }
              is SurveyEditorSection.Dataset -> {
                val dataset = uiState.selectedDataset
                if (dataset != null) {
                  key(section.key) { EntityDatasetEditor(uiState, actions, dataset) }
                }
              }
            }
          }
        }
      }
    }
  }
}
