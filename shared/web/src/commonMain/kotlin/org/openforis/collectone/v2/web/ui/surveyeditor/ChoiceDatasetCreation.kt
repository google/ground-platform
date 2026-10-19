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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.web.ui.state.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.state.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.viewmodel.SurveyEditorActions

/** A request to create a map layer or data table for the choices of question [questionKey]. */
internal data class ChoiceDatasetRequest(
  val formKey: String,
  val questionKey: String,
  val kind: DatasetKind,
)

/**
 * State of [ChoiceDatasetCreationDialogs]: the request being handled and the dataset being edited.
 */
@Stable
internal class ChoiceDatasetCreationState {
  var pending: ChoiceDatasetRequest? by mutableStateOf(null)
    private set

  internal var editing: Pair<ChoiceDatasetRequest, String>? by mutableStateOf(null)

  /** Starts creating a map layer or data table for the choices of a question. */
  fun request(formKey: String, questionKey: String, kind: DatasetKind) {
    if (pending == null && editing == null) {
      pending = ChoiceDatasetRequest(formKey, questionKey, kind)
    }
  }

  internal fun clearPending() {
    pending = null
  }
}

@Composable
internal fun rememberChoiceDatasetCreationState(): ChoiceDatasetCreationState = remember {
  ChoiceDatasetCreationState()
}

/**
 * Creating a map layer or data table from a multiple choice question: first the same dialog the
 * survey editor uses to add one (start empty or import a file), then the new dataset's editor in a
 * modal. Closing the editor binds the question's choices to the new dataset and returns to its
 * form. Cancelling the first dialog leaves the question unchanged.
 */
@Composable
internal fun ChoiceDatasetCreationDialogs(
  state: ChoiceDatasetCreationState,
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
) {
  val pending = state.pending
  if (pending != null && state.editing == null) {
    val initialDatasetKeys = remember(pending) { uiState.datasets.map { it.key }.toSet() }
    val onDismiss = {
      val created = uiState.datasets.lastOrNull { it.key !in initialDatasetKeys }
      if (created != null) state.editing = pending to created.key
      state.clearPending()
    }
    when (pending.kind) {
      DatasetKind.MAP_LAYER -> AddMapLayerDialog(uiState, actions, onDismiss = onDismiss)
      DatasetKind.DATA_TABLE -> AddDataTableDialog(actions, onDismiss = onDismiss)
    }
  }

  val editing = state.editing ?: return
  val (request, datasetKey) = editing
  val dataset = uiState.datasets.firstOrNull { it.key == datasetKey }
  if (dataset == null) {
    state.editing = null
    return
  }
  val finish = {
    val formEditor = actions.formEditor(request.formKey)
    val source =
      if (request.kind == DatasetKind.MAP_LAYER) ChoiceSource.MAP_LAYER else ChoiceSource.DATA_TABLE
    formEditor.setChoiceSource(request.questionKey, source)
    formEditor.setChoiceDataset(request.questionKey, dataset.id)
    actions.select(SurveyEditorSection.Form(request.formKey))
    state.editing = null
  }
  AlertDialog(
    onDismissRequest = finish,
    title = null,
    text = {
      Box(modifier = Modifier.fillMaxWidth().height(650.dp)) {
        EntityDatasetEditor(uiState, actions, dataset)
      }
    },
    confirmButton = { Button(onClick = finish) { Text("Done") } },
    dismissButton = null,
    modifier = Modifier.widthIn(min = 900.dp, max = 1200.dp),
  )
}
