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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key as eventKey
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_question
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparator
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparatorWidth

/**
 * Visual Form editor page: a flow canvas previewing every screen of the Form, connected by arrows
 * for each potential transition implied by display logic, plus a properties panel for the selected
 * screen and an in-browser preview that runs the generated XForms through [MobileFormRunner].
 *
 * The same page edits a survey's Forms and an organization's library templates
 * ([FormEditorUiState.isTemplate]); for a template, the host passes its metadata fields as
 * [templateDetails] and no dataset callbacks.
 */
@Composable
fun FormEditorPage(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  /** Runner of the open preview ([FormEditorViewModel.previewController]). */
  previewController: FormWizardController?,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  onCreateDataset: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
  onSaveToModeChange: ((SaveToMode) -> Unit)? = null,
  onOpenDataset: ((String) -> Unit)? = null,
  onUnlinkDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
  /**
   * Template metadata fields (description, purposes) that the host of a template's Form editor
   * ([FormEditorUiState.isTemplate]) shows in Form properties, in place of the survey's submission
   * settings.
   */
  templateDetails: (@Composable () -> Unit)? = null,
) {
  val linkedDataset = uiState.datasets.firstOrNull { it.isLinkedToThisForm }
  val createDatasetAction = onCreateDataset.takeIf {
    !uiState.isReadOnly && uiState.form.saveTo.mode == SaveToMode.CREATE && linkedDataset == null
  }
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .undoRedoShortcuts(uiState, actions)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      FormEditorToolbar(
        uiState = uiState,
        actions = actions,
        onCreateDataset = createDatasetAction,
        onDelete = onDelete.takeIf { !uiState.isReadOnly },
      )
      ImportMatchesCard(
        uiState = uiState,
        actions = actions,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
      )
      TemplateUpdateReviewCard(
        uiState = uiState,
        actions = actions,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
      )
      Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FormCanvasBox(uiState, actions, Modifier.weight(1f).fillMaxHeight())
        SidePanelSeparator(
          widthDp = uiState.sidePanelWidthDp,
          onWidthChange = actions::updateSidePanelWidth,
          panelOnRight = true,
          modifier =
            Modifier.width(SidePanelSeparatorWidth)
              .fillMaxHeight()
              .clip(MaterialTheme.shapes.extraSmall),
        )
        QuestionPropertiesPanel(
          uiState = uiState,
          actions = actions,
          onSaveToModeChange = onSaveToModeChange ?: actions::setSaveToMode,
          onOpenDataset = onOpenDataset,
          onCreateDataset = createDatasetAction,
          onUnlinkDataset = onUnlinkDataset.takeIf { !uiState.isReadOnly },
          onCreateFormForDataset = onCreateFormForDataset.takeIf { !uiState.isReadOnly },
          onOpenForm = onOpenForm,
          onCreateChoiceDataset = onCreateChoiceDataset.takeIf { !uiState.isReadOnly },
          templateDetails = templateDetails,
          modifier = Modifier.width(uiState.sidePanelWidthDp.dp).fillMaxHeight(),
        )
      }
    }

    if (uiState.isPreviewOpen) {
      FormPreviewOverlay(uiState, actions, previewController, isDarkTheme)
    }
    ConceptSearchDialog(uiState, actions)
  }
}

private fun Modifier.undoRedoShortcuts(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
): Modifier = onPreviewKeyEvent { event ->
  if (event.type != KeyEventType.KeyDown || (!event.isCtrlPressed && !event.isMetaPressed)) {
    return@onPreviewKeyEvent false
  }
  when {
    event.eventKey == Key.Z && event.isShiftPressed -> {
      if (uiState.canRedo) actions.redo()
      true
    }
    event.eventKey == Key.Z && !event.isShiftPressed -> {
      if (uiState.canUndo) actions.undo()
      true
    }
    event.eventKey == Key.Y && !event.isShiftPressed -> {
      if (uiState.canRedo) actions.redo()
      true
    }
    else -> false
  }
}

@Composable
private fun FormCanvasBox(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  CompositionLocalProvider(
    LocalDisabledQuestionTypes provides uiState.disabledQuestionTypes,
    LocalQuestionTypeAvailability provides uiState::isQuestionTypeAvailable,
  ) {
    Box(modifier = modifier) {
      when (uiState.previewTarget) {
        FormPreviewTarget.MOBILE -> FlowCanvasPanel(uiState, actions, Modifier.fillMaxSize())
        FormPreviewTarget.WEB -> WebLayoutCanvasPanel(uiState, actions, Modifier.fillMaxSize())
      }
      CanvasStatusOverlay(
        uiState = uiState,
        actions = actions,
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(start = 20.dp, bottom = 24.dp, end = 220.dp),
      )
      if (!uiState.isReadOnly) {
        FloatingAddQuestionButton(
          onAdd = { type, idx -> actions.addQuestion(type, idx) },
          onAddSection = { idx ->
            val secId = actions.addSection("Section", repeatable = false, atIndex = idx)
            actions.selectSection(secId)
          },
          onAddRepeatedSection = { idx ->
            val secId = actions.addSection("Section", repeatable = true, atIndex = idx)
            actions.selectSection(secId)
          },
          modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp),
        )
      }
    }
  }
}

@Composable
private fun FloatingAddQuestionButton(
  onAdd: (EditorQuestionType, Int?) -> Unit,
  onAddSection: ((Int?) -> Unit)? = null,
  onAddRepeatedSection: ((Int?) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  var addingQuestion by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    FilledTonalButton(
      onClick = { addingQuestion = true },
      shape = MaterialTheme.shapes.large,
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
      colors =
        ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
      elevation =
        ButtonDefaults.filledTonalButtonElevation(defaultElevation = 6.dp, hoveredElevation = 8.dp),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(stringResource(Res.string.formeditor_add_question))
    }
    AddQuestionMenu(
      expanded = addingQuestion,
      onDismiss = { addingQuestion = false },
      onAdd = onAdd,
      onAddSection = onAddSection,
      onAddRepeatedSection = onAddRepeatedSection,
    )
  }
}
