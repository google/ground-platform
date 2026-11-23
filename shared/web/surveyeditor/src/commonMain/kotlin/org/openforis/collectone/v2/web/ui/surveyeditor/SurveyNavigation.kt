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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.ui.common.DragAxis
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_add_data_table
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_add_form
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_add_map_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_forms
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_move_down
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_move_up
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_no_data_tables
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_no_forms
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_no_map_layers
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_sharing
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_survey_details
import org.openforis.collectone.v2.web.resources.surveyeditor_untitled
import org.openforis.collectone.v2.web.resources.surveyeditor_untitled_form
import org.openforis.collectone.v2.web.ui.common.NavItem
import org.openforis.collectone.v2.web.ui.formeditor.DragReorderState
import org.openforis.collectone.v2.web.ui.formeditor.dragToReorder

@Composable
internal fun SurveyNavigation(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  modifier: Modifier = Modifier,
) {
  var showAddForm by remember { mutableStateOf(false) }
  if (showAddForm) {
    AddFormDialog(actions, onDismiss = { showAddForm = false }, library = uiState.library)
  }
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      NavItem(
        label = stringResource(Res.string.surveyeditor_nav_survey_details),
        icon = Icons.Outlined.Info,
        selected = uiState.section == SurveyEditorSection.Details,
        onClick = { actions.select(SurveyEditorSection.Details) },
        hasIssues = uiState.detailsIssues.isNotEmpty(),
      )
      NavItem(
        label = stringResource(Res.string.surveyeditor_nav_sharing),
        icon = Icons.Outlined.Person,
        selected = uiState.section == SurveyEditorSection.Sharing,
        onClick = { actions.select(SurveyEditorSection.Sharing) },
        trailing = "${uiState.sharing.collaborators.size + 1}",
        hasIssues = uiState.sharingIssues.isNotEmpty(),
      )

      NavHeading(
        stringResource(Res.string.surveyeditor_nav_forms),
        addDescription = stringResource(Res.string.surveyeditor_nav_add_form),
        onAdd =
          if (uiState.isReadOnly) null
          else {
            { showAddForm = true }
          },
      )
      if (uiState.forms.isEmpty()) NavEmpty(stringResource(Res.string.surveyeditor_nav_no_forms))
      ReorderableNavList(
        items = uiState.forms,
        keyOf = { it.key },
        onMove = actions::moveForm,
        readOnly = uiState.isReadOnly,
      ) { entry, showHandle ->
        val form = entry.form
        NavItem(
          label = form.title.ifBlank { stringResource(Res.string.surveyeditor_untitled_form) },
          icon = if (showHandle) Icons.Outlined.DragIndicator else Icons.Outlined.Description,
          selected = uiState.section == SurveyEditorSection.Form(entry.key),
          onClick = { actions.select(SurveyEditorSection.Form(entry.key)) },
          trailing = "${form.questions.size}",
          hasIssues = uiState.formIssues(entry).isNotEmpty(),
          nested = true,
        )
      }

      DatasetNavGroup(
        uiState,
        actions,
        DatasetKind.MAP_LAYER,
        uiState.mapLayers,
        Icons.Outlined.Layers,
      )
      DatasetNavGroup(
        uiState,
        actions,
        DatasetKind.DATA_TABLE,
        uiState.dataTables,
        Icons.AutoMirrored.Outlined.List,
      )
    }
  }
}

@Composable
private fun DatasetNavGroup(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  kind: DatasetKind,
  datasets: List<EntityDataset>,
  icon: ImageVector,
) {
  var showAddMapLayer by remember { mutableStateOf(false) }
  var showAddDataTable by remember { mutableStateOf(false) }
  if (showAddMapLayer) {
    AddMapLayerDialog(
      uiState,
      actions,
      onDismiss = { showAddMapLayer = false },
      offerTemplates = true,
    )
  }
  if (showAddDataTable) {
    AddDataTableDialog(
      actions,
      onDismiss = { showAddDataTable = false },
      library = uiState.library,
      onAddFromTemplate = { actions.addFormFromTemplate(it, openDataset = true) },
    )
  }
  NavHeading(
    stringResource(kind.pluralRes),
    addDescription =
      when (kind) {
        DatasetKind.MAP_LAYER -> stringResource(Res.string.surveyeditor_nav_add_map_layer)
        DatasetKind.DATA_TABLE -> stringResource(Res.string.surveyeditor_nav_add_data_table)
      },
    onAdd =
      if (uiState.isReadOnly) null
      else {
        {
          when (kind) {
            DatasetKind.MAP_LAYER -> showAddMapLayer = true
            DatasetKind.DATA_TABLE -> showAddDataTable = true
          }
        }
      },
  )
  if (datasets.isEmpty()) {
    NavEmpty(
      when (kind) {
        DatasetKind.MAP_LAYER -> stringResource(Res.string.surveyeditor_nav_no_map_layers)
        DatasetKind.DATA_TABLE -> stringResource(Res.string.surveyeditor_nav_no_data_tables)
      }
    )
  }
  ReorderableNavList(
    items = datasets,
    keyOf = { it.key },
    onMove = actions::moveDataset,
    readOnly = uiState.isReadOnly,
  ) { dataset, showHandle ->
    NavItem(
      label = dataset.displayName.ifBlank { stringResource(Res.string.surveyeditor_untitled) },
      icon = if (showHandle) Icons.Outlined.DragIndicator else icon,
      selected = uiState.section == SurveyEditorSection.Dataset(dataset.key),
      onClick = { actions.select(SurveyEditorSection.Dataset(dataset.key)) },
      trailing = "${dataset.rows.size}",
      hasIssues = uiState.datasetIssueCount(dataset) > 0,
      nested = true,
    )
  }
}

private val NavItemHeight = 44.dp
private val NavItemSpacing = 2.dp

/**
 * A vertical list of navigation items that can be reordered by dragging. [itemContent] receives
 * whether to show a drag handle (while hovered or dragged).
 */
@Composable
private fun <T> ReorderableNavList(
  items: List<T>,
  keyOf: (T) -> String,
  onMove: (key: String, toIndex: Int) -> Unit,
  readOnly: Boolean = false,
  itemContent: @Composable (item: T, showHandle: Boolean) -> Unit,
) {
  val drag = remember { DragReorderState() }
  val pitchPx = with(LocalDensity.current) { (NavItemHeight + NavItemSpacing).toPx() }
  val moveUpLabel = stringResource(Res.string.surveyeditor_nav_move_up)
  val moveDownLabel = stringResource(Res.string.surveyeditor_nav_move_down)
  Column(verticalArrangement = Arrangement.spacedBy(NavItemSpacing)) {
    items.forEachIndexed { index, item ->
      val itemKey = keyOf(item)
      key(itemKey) {
        val isDragged = !readOnly && drag.draggingKey == itemKey
        val shift by
          animateFloatAsState(
            targetValue = if (readOnly) 0f else drag.shiftFor(index) * pitchPx,
            animationSpec = if (drag.isDragging) spring() else snap(),
          )
        val hover = remember { MutableInteractionSource() }
        val isHovered by hover.collectIsHoveredAsState()
        val reorderModifier =
          if (readOnly) {
            Modifier
          } else {
            Modifier.dragToReorder(
                state = drag,
                key = itemKey,
                index = index,
                count = items.size,
                pitchPx = pitchPx,
                axis = DragAxis.VERTICAL,
                onMove = onMove,
              )
              .semantics {
                customActions = buildList {
                  if (index > 0) {
                    add(
                      CustomAccessibilityAction(moveUpLabel) {
                        onMove(itemKey, index - 1)
                        true
                      }
                    )
                  }
                  if (index < items.lastIndex) {
                    add(
                      CustomAccessibilityAction(moveDownLabel) {
                        onMove(itemKey, index + 1)
                        true
                      }
                    )
                  }
                }
              }
          }
        Surface(
          color =
            if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
          shape = MaterialTheme.shapes.extraLarge,
          modifier =
            Modifier.zIndex(if (isDragged) 1f else 0f)
              .graphicsLayer {
                translationY = if (isDragged) drag.offset else shift
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                shape = RoundedCornerShape(28.dp)
                clip = isDragged
              }
              .clip(MaterialTheme.shapes.extraLarge)
              .hoverable(hover)
              .then(reorderModifier),
        ) {
          itemContent(item, !readOnly && (isHovered || isDragged))
        }
      }
    }
  }
}

@Composable
private fun NavHeading(text: String, addDescription: String, onAdd: (() -> Unit)? = null) {
  Column {
    HorizontalDivider(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(
            start = 16.dp,
            top = if (onAdd == null) 8.dp else 0.dp,
            bottom = if (onAdd == null) 8.dp else 0.dp,
          ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      if (onAdd != null) {
        IconButton(onClick = onAdd) {
          Icon(Icons.Outlined.Add, contentDescription = addDescription)
        }
      }
    }
  }
}

@Composable
private fun NavEmpty(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 8.dp),
  )
}
