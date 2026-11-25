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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.FormEditorValidator
import org.openforis.collectone.v2.app.domain.model.editor.GeometryText
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.parseHexColor
import org.openforis.collectone.v2.app.domain.model.editor.slugify
import org.openforis.collectone.v2.app.ui.common.displayMessage
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.pluralRes
import org.openforis.collectone.v2.app.ui.text.singularRes
import org.openforis.collectone.v2.core.forms.ui.appMonospaceFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Slider
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.core.sync.EntityFieldMask
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.ColumnPrefixGroup
import org.openforis.collectone.v2.web.domain.model.GroupedColumnsLayout
import org.openforis.collectone.v2.web.domain.model.GroupedTableColumn
import org.openforis.collectone.v2.web.domain.model.PagedEntityTableCache
import org.openforis.collectone.v2.web.domain.model.TableCellLoadState
import org.openforis.collectone.v2.web.domain.model.groupColumnsByCommonPrefix
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_add_feature
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_add_property
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_add_row
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_advanced
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_choose
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_color
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_column_geometry
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_column_key
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_column_label
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_create_form_data_table
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_create_form_map_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_data_table
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_data_table_body
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_data_table_menu
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_data_table_title
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_map_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_map_layer_body
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_map_layer_menu
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_delete_map_layer_title
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_edit_form
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_empty_features
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_empty_rows
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_empty_sample_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_features
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_features_count
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_field_collection
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_fill_opacity
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_geometry_type
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_hide
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_id
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_id_supporting
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_issues_count
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_key_property
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_label_property
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_linked_form_data_table
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_linked_form_map_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_linked_to
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_map_style
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_more_issues
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_more_options
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_name
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_no_form_data_table
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_no_form_map_layer
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_properties
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_properties_count
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_properties_managed_by_form
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_advanced
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_hide_id
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_internal_id
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_internal_id_supporting
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_label
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_required
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_property_type
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_remove_property
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_rows
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_rows_count
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_sample_design
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_samples_count
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_samples_none
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_settings
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_show
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_stroke_width
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_unlink_form
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_untitled
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_updated_by
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_visible_by_default
import org.openforis.collectone.v2.web.resources.surveyeditor_dataset_visible_by_default_description
import org.openforis.collectone.v2.web.resources.surveyeditor_delete
import org.openforis.collectone.v2.web.resources.surveyeditor_description
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review
import org.openforis.collectone.v2.web.ui.common.CollapsedColumnGroupBodyCell
import org.openforis.collectone.v2.web.ui.common.CollapsedColumnGroupHeaderCell
import org.openforis.collectone.v2.web.ui.common.CollapsedColumnGroupWidth
import org.openforis.collectone.v2.web.ui.common.CollapsiblePropertyGroupRow
import org.openforis.collectone.v2.web.ui.common.ColumnPrefixGroupChipsRow
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.ExpandedGroupAccentStrip
import org.openforis.collectone.v2.web.ui.common.ExpandedGroupCollapseButton
import org.openforis.collectone.v2.web.ui.common.SectionLabel
import org.openforis.collectone.v2.web.ui.common.VirtualDataTable
import org.openforis.collectone.v2.web.ui.common.VirtualTableLoadingCell
import org.openforis.collectone.v2.web.ui.text.resolve

private val CellWidth = 160.dp
private val GeometryCellWidth = 280.dp
private val IndexCellWidth = 44.dp
private val CellHeight = 38.dp

private val PresetColors =
  listOf("#F37C22", "#D13135", "#7A279F", "#2278CF", "#3C8D40", "#F9BF40", "#2E7D32", "#6D4C41")

private data class DatasetTableColumn(
  val property: EntityProperty,
  val runtimeKey: String,
  val isRemoteOnly: Boolean = false,
)

private fun buildDatasetTableColumns(
  dataset: EntityDataset,
  remotePropertyKeys: List<String>,
): List<DatasetTableColumn> {
  val remoteKeySet = remotePropertyKeys.toSet()
  val declaredColumns =
    dataset.properties.map { prop ->
      val runtimeKey =
        when {
          prop.name in remoteKeySet -> prop.name
          prop.label in remoteKeySet -> prop.label
          else -> prop.name
        }
      DatasetTableColumn(property = prop, runtimeKey = runtimeKey, isRemoteOnly = false)
    }
  if (remotePropertyKeys.isEmpty()) return declaredColumns
  val covered = HashSet<String>(declaredColumns.size * 2)
  declaredColumns.forEach {
    covered += it.property.name
    covered += it.runtimeKey
    covered += it.property.label
  }
  covered += dataset.keyProperty
  covered += dataset.labelProperty
  val extras =
    remotePropertyKeys
      .filterNot { key ->
        key in SurveyEditorDerivation.NON_PROPERTY_KEYS ||
          key in covered ||
          SurveyEditorDerivation.propertyName(key) in covered ||
          (dataset.keyProperty != SurveyEditorDerivation.ID_PROPERTY &&
            key == SurveyEditorDerivation.ID_PROPERTY)
      }
      .map { key ->
        DatasetTableColumn(
          property = EntityProperty(name = key, label = key, type = PropertyType.TEXT),
          runtimeKey = key,
          isRemoteOnly = true,
        )
      }
  return declaredColumns + extras
}

@Composable
private fun rememberDatasetTableCache(
  isMap: Boolean,
  surveyId: String,
  datasetId: String,
  tableRepo: org.openforis.collectone.v2.app.domain.repository.EntityTableRepository?,
): PagedEntityTableCache? {
  val lazyRepo = tableRepo?.takeIf { isMap && it.isLazyLoadingEnabled }
  val hasIds = surveyId.isNotBlank() && datasetId.isNotBlank()
  val tableCache =
    remember(surveyId, datasetId, lazyRepo, hasIds) {
      if (lazyRepo != null && hasIds) {
        PagedEntityTableCache(surveyId = surveyId, datasetId = datasetId, repository = lazyRepo)
      } else {
        null
      }
    }
  DisposableEffect(tableCache) { onDispose { tableCache?.clear() } }
  LaunchedEffect(tableCache) { tableCache?.ensureSchemaLoaded(this) }
  return tableCache
}

/**
 * Entity editor for a Map layer (map preview + feature table + style) or a Data table (spreadsheet
 * of rows). Both share the schema (properties) and dataset settings panel.
 */
@Composable
fun EntityDatasetEditor(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
) {
  var selectedRow by remember { mutableStateOf<String?>(null) }
  val generationError = uiState.generationError(dataset.key)?.resolve()
  val issues =
    uiState.datasetIssues(dataset).map { DatasetIssueText(it.displayMessage(), it.rowKey) } +
      listOfNotNull(generationError?.let { DatasetIssueText(it, rowKey = null) })
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  val surveyId = uiState.surveyId.ifBlank { uiState.details.surveyId }
  val tableCache =
    rememberDatasetTableCache(
      isMap = isMap,
      surveyId = surveyId,
      datasetId = dataset.id,
      tableRepo = actions.tableRepository(),
    )
  val remoteSchema by (tableCache?.schema?.collectAsState() ?: remember { mutableStateOf(null) })
  val tableColumns =
    remember(dataset.properties, dataset.keyProperty, dataset.labelProperty, remoteSchema) {
      buildDatasetTableColumns(dataset, remoteSchema?.propertyKeys.orEmpty())
    }
  val extraRemoteColumns =
    remember(tableColumns) { tableColumns.filter { it.isRemoteOnly }.map { it.property } }

  Column(modifier = Modifier.fillMaxSize()) {
    DatasetHeader(uiState, actions, dataset, issues.size, totalPropertyCount = tableColumns.size)
    Row(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (isMap) {
          InteractiveLayerMapCard(
            editor = actions,
            dataset = dataset,
            selectedRow = selectedRow,
            onSelectRow = { selectedRow = it },
            modifier = Modifier.fillMaxWidth().weight(1.4f),
            backdropPolygons = uiState.details.surveyArea?.parts.orEmpty(),
            geometryEditable = !dataset.isGenerated && !uiState.isReadOnly,
          )
        }
        RowsTableCard(
          uiState = uiState,
          actions = actions,
          dataset = dataset,
          tableColumns = tableColumns,
          tableCache = tableCache,
          issues = issues,
          selectedRow = selectedRow,
          onSelectRow = { selectedRow = it },
          modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
      DatasetSettingsPanel(
        uiState = uiState,
        actions = actions,
        dataset = dataset,
        extraRemoteProperties = extraRemoteColumns,
        modifier = Modifier.width(380.dp).fillMaxHeight(),
      )
    }
  }
}

@Composable
private fun DatasetHeader(
  uiState: SurveyEditorUiState,
  actions: DatasetActions,
  dataset: EntityDataset,
  issueCount: Int,
  totalPropertyCount: Int = dataset.properties.size,
) {
  var confirmDelete by remember { mutableStateOf(false) }

  if (confirmDelete) {
    ConfirmDeleteDatasetDialog(
      dataset = dataset,
      onConfirm = {
        confirmDelete = false
        actions.deleteDataset(dataset.key)
      },
      onDismiss = { confirmDelete = false },
    )
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DatasetHeaderTitleColumn(
        dataset = dataset,
        totalPropertyCount = totalPropertyCount,
        modifier = Modifier.weight(1f),
      )
      if (issueCount > 0) {
        DatasetHeaderIssuesBadge(issueCount = issueCount)
      }
      if (!uiState.isReadOnly) {
        DatasetHeaderOverflowMenu(dataset = dataset, onRequestDelete = { confirmDelete = true })
      }
    }
  }
}

@Composable
private fun DatasetHeaderTitleColumn(
  dataset: EntityDataset,
  totalPropertyCount: Int,
  modifier: Modifier = Modifier,
) {
  val itemsCount =
    pluralStringResource(
      if (dataset.kind == DatasetKind.MAP_LAYER) Res.plurals.surveyeditor_dataset_features_count
      else Res.plurals.surveyeditor_dataset_rows_count,
      dataset.rows.size,
      dataset.rows.size,
    )
  val propertiesCount =
    pluralStringResource(
      Res.plurals.surveyeditor_dataset_properties_count,
      totalPropertyCount,
      totalPropertyCount,
    )
  Column(modifier = modifier) {
    Text(
      text = stringResource(dataset.kind.singularRes),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        dataset.displayName.ifBlank { stringResource(Res.string.surveyeditor_dataset_untitled) },
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text =
        buildString {
          append("$itemsCount • $propertiesCount")
          if (dataset.kind == DatasetKind.MAP_LAYER) {
            append(" • ${stringResource(dataset.geometryType.pluralRes).lowercase()}")
          }
        },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DatasetHeaderIssuesBadge(issueCount: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      Icons.Outlined.Warning,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(4.dp))
    Text(
      pluralStringResource(Res.plurals.surveyeditor_dataset_issues_count, issueCount, issueCount),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.error,
    )
  }
}

@Composable
private fun DatasetHeaderOverflowMenu(dataset: EntityDataset, onRequestDelete: () -> Unit) {
  var menuOpen by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { menuOpen = true }) {
      Icon(
        Icons.Outlined.MoreVert,
        contentDescription =
          stringResource(
            Res.string.surveyeditor_dataset_more_options,
            dataset.displayName.ifBlank { stringResource(dataset.kind.singularRes) },
          ),
      )
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = {
          Text(deleteDatasetMenuLabel(dataset.kind), color = MaterialTheme.colorScheme.error)
        },
        leadingIcon = {
          Icon(
            Icons.Outlined.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        },
        onClick = {
          menuOpen = false
          onRequestDelete()
        },
      )
    }
  }
}

@Composable
private fun ConfirmDeleteDatasetDialog(
  dataset: EntityDataset,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  val name =
    dataset.displayName.ifBlank { stringResource(Res.string.surveyeditor_dataset_untitled) }
  val count = dataset.rows.size
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          if (isMap) Res.string.surveyeditor_dataset_delete_map_layer_title
          else Res.string.surveyeditor_dataset_delete_data_table_title
        )
      )
    },
    text = {
      Text(
        pluralStringResource(
          if (isMap) Res.plurals.surveyeditor_dataset_delete_map_layer_body
          else Res.plurals.surveyeditor_dataset_delete_data_table_body,
          count,
          name,
          count,
        )
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(
          stringResource(
            if (isMap) Res.string.surveyeditor_dataset_delete_map_layer
            else Res.string.surveyeditor_dataset_delete_data_table
          )
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
    },
  )
}

// ---------------------------------------------------------------------------------------------
// Rows / features table
// ---------------------------------------------------------------------------------------------

/** A dataset issue in the UI language, optionally tied to the row with [rowKey]. */
private data class DatasetIssueText(val text: String, val rowKey: String?)

private const val GEOMETRY_COLUMN_KEY = "__geometry__"
private const val HORIZONTAL_COLUMN_OVERSCAN = 2

/**
 * Indices of fixed-width columns intersecting the horizontally scrolled viewport, expanded by
 * [overscan] columns on each side so rapid scrolling doesn't flash blank cells. Returns
 * [IntRange.EMPTY] when [columnCount] is 0.
 */
internal fun visibleColumnRange(
  columnCount: Int,
  scrollPx: Int,
  viewportWidthPx: Int,
  leadingWidthPx: Float,
  columnWidthPx: Float,
  overscan: Int = HORIZONTAL_COLUMN_OVERSCAN,
): IntRange {
  if (columnCount <= 0) return IntRange.EMPTY
  if (viewportWidthPx <= 0 || columnWidthPx <= 0f) return 0 until columnCount
  val startPx = (scrollPx - leadingWidthPx).coerceAtLeast(0f)
  val endPx = (scrollPx + viewportWidthPx - leadingWidthPx).coerceAtLeast(0f)
  val first = ((startPx / columnWidthPx).toInt() - overscan).coerceIn(0, columnCount - 1)
  val last = ((endPx / columnWidthPx).toInt() + overscan).coerceIn(first, columnCount - 1)
  return first..last
}

@Composable
private fun RowsTableCard(
  uiState: SurveyEditorUiState,
  actions: DatasetActions,
  dataset: EntityDataset,
  tableColumns: List<DatasetTableColumn>,
  tableCache: PagedEntityTableCache?,
  issues: List<DatasetIssueText>,
  selectedRow: String?,
  onSelectRow: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  val colors = MaterialTheme.colorScheme
  val scope = rememberCoroutineScope()
  val rowIssues = remember(issues) { issues.mapNotNull { it.rowKey }.toSet() }
  val listState = remember(dataset.key) { LazyListState() }
  val horizontalScroll = rememberScrollState()
  var editingCell by remember(dataset.key) { mutableStateOf<Pair<String, String>?>(null) }
  val groupState = rememberDatasetColumnGroupState(dataset, tableColumns)
  val displaySlots = groupState.groupedLayout.displaySlots
  val columnWidths = remember(displaySlots) { computeDatasetColumnWidths(displaySlots) }
  ScrollToExpandedDatasetGroupEffect(
    scrollToGroupId = groupState.scrollToGroupId,
    groupedLayout = groupState.groupedLayout,
    columnWidths = columnWidths,
    horizontalScroll = horizontalScroll,
    onClearScrollTarget = groupState.clearScrollTarget,
  )
  val surveyId = uiState.surveyId.ifBlank { uiState.details.surveyId }
  val rowEntityIds = rememberDatasetRowEntityIds(dataset, surveyId)
  val slotRuntimeKeys = remember(displaySlots) { datasetSlotRuntimeKeys(displaySlots) }
  val cacheRevision by (tableCache?.revision?.collectAsState() ?: remember { mutableStateOf(0) })

  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      RowsTableToolbar(
        isMap = isMap,
        canAddRow = !dataset.isGenerated && !uiState.isReadOnly,
        groups = groupState.groupedLayout.groups,
        expandedGroupIds = groupState.expandedGroupIds,
        onToggleGroup = { group -> groupState.toggle(group.id, true) },
        onAddRow = { onSelectRow(actions.addRow(dataset.key)) },
      )
      if (issues.isNotEmpty()) {
        DatasetIssuesBanner(issues = issues, onSelectRow = onSelectRow)
        Spacer(Modifier.height(8.dp))
      }
      DatasetVirtualTable(
        uiState = uiState,
        actions = actions,
        dataset = dataset,
        isMap = isMap,
        displaySlots = displaySlots,
        columnWidths = columnWidths,
        listState = listState,
        horizontalScroll = horizontalScroll,
        selectedRow = selectedRow,
        rowIssues = rowIssues,
        rowEntityIds = rowEntityIds,
        tableCache = tableCache,
        cacheRevision = cacheRevision,
        editingCell = editingCell,
        onEditingCellChange = { editingCell = it },
        onToggleGroup = { groupId -> groupState.toggle(groupId, false) },
        onSelectRow = onSelectRow,
        onViewportChanged = { rowRange, colRange ->
          tableCache?.onViewportChanged(
            scope = scope,
            rowRange = rowRange,
            colRange = colRange,
            rowEntityIds = rowEntityIds,
            columnKeys = slotRuntimeKeys,
          )
        },
      )
    }
  }
}

private class DatasetColumnGroupState(
  val groupedLayout: GroupedColumnsLayout<DatasetTableColumn>,
  val expandedGroupIds: Set<String>,
  val scrollToGroupId: String?,
  val toggle: (groupId: String, scrollOnExpand: Boolean) -> Unit,
  val clearScrollTarget: () -> Unit,
)

@Composable
private fun rememberDatasetColumnGroupState(
  dataset: EntityDataset,
  tableColumns: List<DatasetTableColumn>,
): DatasetColumnGroupState {
  var expandedGroupIds by remember(dataset.key) { mutableStateOf(emptySet<String>()) }
  var scrollToGroupId by remember(dataset.key) { mutableStateOf<String?>(null) }
  val groupedLayout =
    remember(tableColumns, expandedGroupIds, dataset.keyProperty, dataset.labelProperty) {
      groupColumnsByCommonPrefix(
        columns = tableColumns,
        keyOf = { it.runtimeKey },
        expandedGroupIds = expandedGroupIds,
        isGroupable = { col ->
          col.property.name != dataset.keyProperty &&
            col.property.name != dataset.labelProperty &&
            col.property.name != SurveyEditorDerivation.ID_PROPERTY &&
            col.property.name !in SamplePlotProperties.all
        },
      )
    }
  return DatasetColumnGroupState(
    groupedLayout = groupedLayout,
    expandedGroupIds = expandedGroupIds,
    scrollToGroupId = scrollToGroupId,
    toggle = { groupId, scrollOnExpand ->
      if (groupId in expandedGroupIds) {
        expandedGroupIds = expandedGroupIds - groupId
        scrollToGroupId = null
      } else {
        expandedGroupIds = expandedGroupIds + groupId
        if (scrollOnExpand) scrollToGroupId = groupId
      }
    },
    clearScrollTarget = { scrollToGroupId = null },
  )
}

private fun computeDatasetColumnWidths(
  displaySlots: List<GroupedTableColumn<DatasetTableColumn>>
): List<Dp> = displaySlots.map { slot ->
  when (slot) {
    is GroupedTableColumn.CollapsedGroup -> CollapsedColumnGroupWidth
    is GroupedTableColumn.Single -> {
      val headerChars = maxOf(slot.item.property.label.length, slot.item.property.name.length)
      val extra = if (slot.isGroupStart) 24f else 0f
      (headerChars * 7.5f + 28f + extra).dp.coerceIn(CellWidth, 260.dp)
    }
  }
}

@Composable
private fun rememberDatasetRowEntityIds(dataset: EntityDataset, surveyId: String): List<String> =
  remember(dataset.rows, dataset.keyProperty, dataset.id, dataset.kind, surveyId) {
    dataset.rows.map { row ->
      if (RecordIds.isUuid(row.key)) row.key
      else SurveyEditorDerivation.entityIdOf(dataset, row, surveyId)
    }
  }

// Placeholder slots map to "" so PagedEntityTableCache skips fetching collapsed group columns.
private fun datasetSlotRuntimeKeys(
  displaySlots: List<GroupedTableColumn<DatasetTableColumn>>
): List<String> = displaySlots.map { slot ->
  when (slot) {
    is GroupedTableColumn.Single -> slot.item.runtimeKey
    is GroupedTableColumn.CollapsedGroup -> ""
  }
}

@Composable
private fun ScrollToExpandedDatasetGroupEffect(
  scrollToGroupId: String?,
  groupedLayout: GroupedColumnsLayout<DatasetTableColumn>,
  columnWidths: List<Dp>,
  horizontalScroll: ScrollState,
  onClearScrollTarget: () -> Unit,
) {
  val density = LocalDensity.current
  LaunchedEffect(scrollToGroupId, groupedLayout.displaySlots) {
    val targetId = scrollToGroupId ?: return@LaunchedEffect
    val slotIndex = groupedLayout.firstExpandedSlotIndex(targetId)
    if (slotIndex >= 0) {
      val leadingDp = columnWidths.take(slotIndex).fold(IndexCellWidth) { acc, w -> acc + w }
      val targetPx = with(density) { (leadingDp - 16.dp).coerceAtLeast(0.dp).toPx() }.toInt()
      withFrameNanos {}
      if (horizontalScroll.maxValue < targetPx) {
        withFrameNanos {}
      }
      horizontalScroll.animateScrollTo(targetPx.coerceIn(0, horizontalScroll.maxValue))
      onClearScrollTarget()
    }
  }
}

@Composable
private fun DatasetVirtualTable(
  uiState: SurveyEditorUiState,
  actions: DatasetActions,
  dataset: EntityDataset,
  isMap: Boolean,
  displaySlots: List<GroupedTableColumn<DatasetTableColumn>>,
  columnWidths: List<Dp>,
  listState: LazyListState,
  horizontalScroll: ScrollState,
  selectedRow: String?,
  rowIssues: Set<String>,
  rowEntityIds: List<String>,
  tableCache: PagedEntityTableCache?,
  cacheRevision: Int,
  editingCell: Pair<String, String>?,
  onEditingCellChange: (Pair<String, String>?) -> Unit,
  onToggleGroup: (String) -> Unit,
  onSelectRow: (String?) -> Unit,
  onViewportChanged: (IntRange, IntRange) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val selectedIndex =
    remember(dataset.rows, selectedRow) {
      if (selectedRow == null) -1 else dataset.rows.indexOfFirst { it.key == selectedRow }
    }
  VirtualDataTable(
    rowCount = dataset.rows.size,
    rowKey = { dataset.rows[it].key },
    columnWidths = columnWidths,
    listState = listState,
    horizontalScroll = horizontalScroll,
    leadingWidth = IndexCellWidth,
    trailingWidth = (if (isMap) GeometryCellWidth else 0.dp) + IndexCellWidth,
    scrollToRowIndex = selectedIndex,
    onVisibleWindowChanged = if (tableCache != null) onViewportChanged else null,
    modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    emptyContent = { DatasetTableEmptyState(dataset, isMap) },
    leadingHeader = { HeaderCell("#", IndexCellWidth) },
    headerCell = { colIndex, width ->
      DatasetTableHeaderSlot(dataset, displaySlots[colIndex], width, onToggleGroup)
    },
    trailingHeader = { DatasetTableTrailingHeader(isMap) },
    rowModifier = { rowIndex ->
      val selected = dataset.rows[rowIndex].key == selectedRow
      Modifier.background(if (selected) colors.secondaryContainer else Color.Transparent)
    },
    leadingCell = { rowIndex ->
      val row = dataset.rows[rowIndex]
      val selected = row.key == selectedRow
      RowIndexCell(
        index = rowIndex,
        selected = selected,
        hasIssue = row.key in rowIssues,
        onClick = { onSelectRow(if (selected) null else row.key) },
      )
    },
    cell = { rowIndex, colIndex, width ->
      DatasetTableCellSlot(
        uiState = uiState,
        actions = actions,
        dataset = dataset,
        rowIndex = rowIndex,
        colIndex = colIndex,
        slot = displaySlots[colIndex],
        entityId = rowEntityIds[rowIndex],
        width = width,
        selectedRow = selectedRow,
        editingCell = editingCell,
        onEditingCellChange = onEditingCellChange,
        onSelectRow = onSelectRow,
        tableCache = tableCache,
        cacheRevision = cacheRevision,
        onToggleGroup = onToggleGroup,
      )
    },
    trailingCell = { rowIndex ->
      DatasetTableTrailingSlot(
        dataset = dataset,
        row = dataset.rows[rowIndex],
        isMap = isMap,
        isReadOnly = uiState.isReadOnly,
        selectedRow = selectedRow,
        editingCell = editingCell,
        onEditingCellChange = onEditingCellChange,
        onSelectRow = onSelectRow,
        actions = actions,
      )
    },
  )
}

@Composable
private fun DatasetTableTrailingHeader(isMap: Boolean) {
  if (isMap) {
    HeaderCell(stringResource(Res.string.surveyeditor_dataset_column_geometry), GeometryCellWidth)
  }
  HeaderCell("", IndexCellWidth)
}

@Composable
private fun DatasetTableTrailingSlot(
  dataset: EntityDataset,
  row: EntityRow,
  isMap: Boolean,
  isReadOnly: Boolean,
  selectedRow: String?,
  editingCell: Pair<String, String>?,
  onEditingCellChange: (Pair<String, String>?) -> Unit,
  onSelectRow: (String?) -> Unit,
  actions: DatasetActions,
) {
  val colors = MaterialTheme.colorScheme
  val selected = row.key == selectedRow
  RowTrailingCells(
    dataset = dataset,
    row = row,
    isMap = isMap,
    isReadOnly = isReadOnly,
    selected = selected,
    textColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    editingColumn = editingCell?.takeIf { it.first == row.key }?.second,
    onSelectRow = { onSelectRow(row.key) },
    onStartEditingGeometry = {
      onSelectRow(row.key)
      onEditingCellChange(row.key to GEOMETRY_COLUMN_KEY)
    },
    onStopEditing = { col -> if (editingCell == row.key to col) onEditingCellChange(null) },
    actions = actions,
  )
}

@Composable
private fun DatasetTableHeaderSlot(
  dataset: EntityDataset,
  slot: GroupedTableColumn<DatasetTableColumn>,
  width: Dp,
  onToggleGroup: (String) -> Unit,
) {
  when (slot) {
    is GroupedTableColumn.CollapsedGroup ->
      CollapsedColumnGroupHeaderCell(
        groupId = slot.group.id,
        wildcardLabel = slot.group.wildcardLabel,
        count = slot.group.size,
        width = width,
        height = CellHeight,
        onExpand = { onToggleGroup(slot.group.id) },
      )
    is GroupedTableColumn.Single ->
      DatasetTableHeaderCell(
        dataset = dataset,
        slot = slot,
        width = width,
        onCollapseGroup = onToggleGroup,
      )
  }
}

@Composable
private fun DatasetTableCellSlot(
  uiState: SurveyEditorUiState,
  actions: DatasetActions,
  dataset: EntityDataset,
  rowIndex: Int,
  colIndex: Int,
  slot: GroupedTableColumn<DatasetTableColumn>,
  entityId: String,
  width: Dp,
  selectedRow: String?,
  editingCell: Pair<String, String>?,
  onEditingCellChange: (Pair<String, String>?) -> Unit,
  onSelectRow: (String?) -> Unit,
  tableCache: PagedEntityTableCache?,
  cacheRevision: Int,
  onToggleGroup: (String) -> Unit,
) {
  when (slot) {
    is GroupedTableColumn.CollapsedGroup ->
      CollapsedColumnGroupBodyCell(
        width = width,
        height = CellHeight,
        onExpand = { onToggleGroup(slot.group.id) },
      )
    is GroupedTableColumn.Single -> {
      // Read cacheRevision before resolving loadState so tile loads invalidate visible
      // cells and pass immutable TableCellLoadState directly into DatasetTableBodyCell.
      val row = dataset.rows[rowIndex]
      val col = slot.item
      val loadState =
        resolveEditorCellLoadState(
          dataset = dataset,
          row = row,
          column = col,
          entityId = entityId,
          rowIndex = rowIndex,
          colIndex = colIndex,
          tableCache = tableCache,
          cacheRevision = cacheRevision,
        )
      DatasetTableBodyCell(
        uiState = uiState,
        actions = actions,
        dataset = dataset,
        row = row,
        column = col,
        entityId = entityId,
        loadState = loadState,
        width = width,
        selected = row.key == selectedRow,
        editingCell = editingCell,
        onEditingCellChange = onEditingCellChange,
        onSelectRow = onSelectRow,
        tableCache = tableCache,
      )
    }
  }
}

@Composable
private fun DatasetTableEmptyState(dataset: EntityDataset, isMap: Boolean) {
  Text(
    stringResource(
      when {
        dataset.isGenerated -> Res.string.surveyeditor_dataset_empty_sample_plots
        isMap -> Res.string.surveyeditor_dataset_empty_features
        else -> Res.string.surveyeditor_dataset_empty_rows
      }
    ),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(12.dp),
  )
}

@Composable
private fun DatasetTableHeaderCell(
  dataset: EntityDataset,
  slot: GroupedTableColumn.Single<DatasetTableColumn>,
  width: Dp,
  onCollapseGroup: (String) -> Unit,
) {
  val property = slot.item.property
  val base = property.label.ifBlank { property.name } + (if (property.required) " *" else "")
  val label =
    when (property.name) {
      dataset.keyProperty -> stringResource(Res.string.surveyeditor_dataset_column_key, base)
      dataset.labelProperty -> stringResource(Res.string.surveyeditor_dataset_column_label, base)
      else -> base
    }
  val groupId = slot.groupId
  if (groupId == null) {
    HeaderCell(label, width)
  } else {
    Box(
      modifier =
        Modifier.width(width)
          .height(CellHeight)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
      ExpandedGroupAccentStrip(modifier = Modifier.align(Alignment.TopStart))
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (slot.isGroupStart) {
          ExpandedGroupCollapseButton(
            groupId = groupId,
            wildcardLabel = slot.groupLabel,
            count = slot.groupSize,
            onCollapse = { onCollapseGroup(groupId) },
          )
        }
        Text(
          label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun DatasetTableBodyCell(
  uiState: SurveyEditorUiState,
  actions: DatasetActions,
  dataset: EntityDataset,
  row: EntityRow,
  column: DatasetTableColumn,
  entityId: String,
  loadState: TableCellLoadState,
  width: Dp,
  selected: Boolean,
  editingCell: Pair<String, String>?,
  onEditingCellChange: (Pair<String, String>?) -> Unit,
  onSelectRow: (String?) -> Unit,
  tableCache: PagedEntityTableCache?,
) {
  val colors = MaterialTheme.colorScheme
  val cellTextColor = if (selected) colors.onSecondaryContainer else colors.onSurface
  when (loadState) {
    TableCellLoadState.Loading -> VirtualTableLoadingCell(width = width, height = CellHeight)
    is TableCellLoadState.Loaded ->
      DatasetPropertyCell(
        dataset = dataset,
        property = column.property,
        value = loadState.value,
        width = width,
        isReadOnly = uiState.isReadOnly || column.isRemoteOnly,
        editing = !column.isRemoteOnly && editingCell == row.key to column.property.name,
        textColor = cellTextColor,
        onSelectRow = { onSelectRow(row.key) },
        onStartEditing = {
          onSelectRow(row.key)
          onEditingCellChange(row.key to column.property.name)
        },
        onStopEditing = {
          if (editingCell == row.key to column.property.name) onEditingCellChange(null)
        },
        onValueChange = { newValue ->
          tableCache?.putLocalValue(entityId, column.runtimeKey, newValue)
          actions.updateCell(dataset.key, row.key, column.property.name, newValue)
        },
      )
  }
}

private fun resolveEditorCellLoadState(
  dataset: EntityDataset,
  row: EntityRow,
  column: DatasetTableColumn,
  entityId: String,
  rowIndex: Int,
  colIndex: Int,
  tableCache: PagedEntityTableCache?,
  @Suppress("UNUSED_PARAMETER") cacheRevision: Int = 0,
): TableCellLoadState {
  val localValue = row.values[column.property.name]
  if (tableCache == null) return TableCellLoadState.Loaded(localValue.orEmpty())
  val hasLocal =
    localValue != null ||
      column.property.name == dataset.keyProperty ||
      column.property.name == dataset.labelProperty ||
      column.runtimeKey in EntityFieldMask.CORE_PROPERTY_KEYS
  return tableCache.cellState(
    entityId = entityId,
    rowIndex = rowIndex,
    colIndex = colIndex,
    columnKey = column.runtimeKey,
    hasLocalValue = hasLocal,
    localValue = localValue.orEmpty(),
  )
}

@Composable
private fun RowsTableToolbar(
  isMap: Boolean,
  canAddRow: Boolean,
  groups: List<ColumnPrefixGroup<DatasetTableColumn>>,
  expandedGroupIds: Set<String>,
  onToggleGroup: (ColumnPrefixGroup<DatasetTableColumn>) -> Unit,
  onAddRow: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      stringResource(
        if (isMap) Res.string.surveyeditor_dataset_features
        else Res.string.surveyeditor_dataset_rows
      ),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
    )
    ColumnPrefixGroupChipsRow(
      groups = groups,
      expandedGroupIds = expandedGroupIds,
      onToggle = onToggleGroup,
      modifier = Modifier.weight(1f),
    )
    if (groups.isEmpty()) Spacer(Modifier.weight(1f))
    if (canAddRow) {
      TextButton(onClick = onAddRow) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(
            if (isMap) Res.string.surveyeditor_dataset_add_feature
            else Res.string.surveyeditor_dataset_add_row
          )
        )
      }
    }
  }
}

@Composable
private fun DatasetIssuesBanner(issues: List<DatasetIssueText>, onSelectRow: (String?) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(
    color = colors.errorContainer,
    contentColor = colors.onErrorContainer,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      issues.take(6).forEach { issue ->
        Text(
          "• ${issue.text}",
          style = MaterialTheme.typography.bodySmall,
          modifier =
            if (issue.rowKey != null) Modifier.clickable { onSelectRow(issue.rowKey) }
            else Modifier,
        )
      }
      if (issues.size > 6) {
        Text(
          pluralStringResource(
            Res.plurals.surveyeditor_dataset_more_issues,
            issues.size - 6,
            issues.size - 6,
          ),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun RowIndexCell(
  index: Int,
  selected: Boolean,
  hasIssue: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = Modifier.width(IndexCellWidth).height(CellHeight).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      "${index + 1}",
      style = MaterialTheme.typography.labelMedium,
      color =
        when {
          hasIssue -> colors.error
          selected -> colors.onSecondaryContainer
          else -> colors.onSurfaceVariant
        },
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

@Composable
private fun RowTrailingCells(
  dataset: EntityDataset,
  row: EntityRow,
  isMap: Boolean,
  isReadOnly: Boolean,
  selected: Boolean,
  textColor: Color,
  editingColumn: String?,
  onSelectRow: () -> Unit,
  onStartEditingGeometry: () -> Unit,
  onStopEditing: (String) -> Unit,
  actions: DatasetActions,
) {
  val colors = MaterialTheme.colorScheme
  val locked = dataset.isGenerated || isReadOnly
  if (isMap && locked) {
    ReadOnlyCell(
      GeometryText.format(row.geometry),
      width = GeometryCellWidth,
      monospace = true,
      onClick = onSelectRow,
    )
  } else if (isMap) {
    GeometryCell(
      dataset = dataset,
      row = row,
      editing = editingColumn == GEOMETRY_COLUMN_KEY,
      onStartEditing = onStartEditingGeometry,
      onStopEditing = { onStopEditing(GEOMETRY_COLUMN_KEY) },
      textColor = textColor,
    ) {
      actions.updateGeometry(dataset.key, row.key, it)
    }
  }
  if (locked) {
    Spacer(Modifier.width(IndexCellWidth))
  } else {
    IconButton(
      onClick = {
        if (editingColumn != null) onStopEditing(editingColumn)
        actions.removeRow(dataset.key, row.key)
      },
      modifier = Modifier.size(IndexCellWidth),
    ) {
      Icon(
        Icons.Outlined.Close,
        contentDescription = stringResource(Res.string.surveyeditor_delete),
        tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
private fun DatasetPropertyCell(
  dataset: EntityDataset,
  property: EntityProperty,
  value: String,
  width: Dp = CellWidth,
  isReadOnly: Boolean,
  editing: Boolean,
  textColor: Color,
  onSelectRow: () -> Unit,
  onStartEditing: () -> Unit,
  onStopEditing: () -> Unit,
  onValueChange: (String) -> Unit,
) {
  val reserved = dataset.isGenerated && property.name in SamplePlotProperties.all
  if (reserved || (isReadOnly && property.type != PropertyType.BOOLEAN)) {
    ReadOnlyCell(
      if (property.name == SamplePlotProperties.SAMPLES) samplesSummary(value) else value,
      width = width,
      onClick = onSelectRow,
    )
  } else if (property.type == PropertyType.BOOLEAN) {
    BooleanCell(value, width = width, enabled = !isReadOnly, onValueChange = onValueChange)
  } else {
    val isError =
      !property.type.accepts(value) ||
        (value.isBlank() && (property.required || property.name == dataset.keyProperty))
    TextCell(
      value = value,
      editing = editing,
      onStartEditing = onStartEditing,
      onStopEditing = onStopEditing,
      onValueChange = onValueChange,
      width = width,
      isError = isError,
      textColor = textColor,
    )
  }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
  Box(
    modifier =
      Modifier.width(width)
        .height(CellHeight)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 8.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * An editable table cell that renders a lightweight [Text] until clicked and mounts a
 * [BasicTextField] only while [editing] is true, keeping large spreadsheets responsive in Wasm.
 */
@Composable
private fun TextCell(
  value: String,
  editing: Boolean,
  onStartEditing: () -> Unit,
  onStopEditing: () -> Unit,
  onValueChange: (String) -> Unit,
  width: Dp,
  isError: Boolean,
  monospace: Boolean = false,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  val colors = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodySmall.copy(color = textColor)
  val textStyle = if (monospace) style.copy(fontFamily = appMonospaceFontFamily()) else style
  val frameModifier =
    Modifier.width(width)
      .height(CellHeight)
      .border(
        if (isError) 1.5.dp else 0.5.dp,
        if (isError) colors.error else colors.outlineVariant,
      )
  if (!editing) {
    Box(
      modifier = frameModifier.clickable(onClick = onStartEditing).padding(horizontal = 8.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      Text(
        text = value,
        style = textStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    return
  }
  val focusRequester = remember { FocusRequester() }
  var hadFocus by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  @Suppress("DEPRECATION")
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = textStyle,
    cursorBrush = SolidColor(colors.primary),
    modifier =
      frameModifier
        .focusRequester(focusRequester)
        .onFocusChanged {
          if (it.isFocused) {
            hadFocus = true
          } else if (hadFocus) {
            onStopEditing()
          }
        }
        .padding(horizontal = 8.dp, vertical = 10.dp),
  )
}

/** A table cell that shows [text] without letting it be edited (generated values). */
@Composable
private fun ReadOnlyCell(
  text: String,
  width: Dp = CellWidth,
  monospace: Boolean = false,
  onClick: () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier =
      Modifier.width(width)
        .height(CellHeight)
        .background(colors.surfaceContainerLow)
        .border(0.5.dp, colors.outlineVariant)
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text,
      style =
        MaterialTheme.typography.bodySmall.let {
          if (monospace) it.copy(fontFamily = appMonospaceFontFamily()) else it
        },
      color = colors.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** "25 points", for a plot's `samples` geotrace. */
@Composable
private fun samplesSummary(geotrace: String): String =
  when (val n = SamplePlotProperties.geotraceSize(geotrace)) {
    0 -> stringResource(Res.string.surveyeditor_dataset_samples_none)
    else -> pluralStringResource(Res.plurals.surveyeditor_dataset_samples_count, n, n)
  }

/** "Delete map layer…" or "Delete data table…". */
@Composable
private fun deleteDatasetMenuLabel(kind: DatasetKind): String =
  stringResource(
    when (kind) {
      DatasetKind.MAP_LAYER -> Res.string.surveyeditor_dataset_delete_map_layer_menu
      DatasetKind.DATA_TABLE -> Res.string.surveyeditor_dataset_delete_data_table_menu
    }
  )

@Composable
private fun BooleanCell(
  value: String,
  width: Dp = CellWidth,
  enabled: Boolean = true,
  onValueChange: (String) -> Unit,
) {
  val checked = value.lowercase() in setOf("yes", "true")
  Box(
    modifier =
      Modifier.width(width)
        .height(CellHeight)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    contentAlignment = Alignment.CenterStart,
  ) {
    Checkbox(
      checked = checked,
      enabled = enabled,
      onCheckedChange = { onValueChange(if (it) "yes" else "no") },
    )
  }
}

@Composable
private fun GeometryCell(
  dataset: EntityDataset,
  row: EntityRow,
  editing: Boolean,
  onStartEditing: () -> Unit,
  onStopEditing: () -> Unit,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  onChange: (List<LatLng>) -> Unit,
) {
  var text by remember(row.key, row.geometry) { mutableStateOf(GeometryText.format(row.geometry)) }
  val isError =
    if (editing) {
      val parsed = GeometryText.parse(text)
      parsed == null || parsed.size < dataset.geometryType.minVertices
    } else {
      row.geometry.size < dataset.geometryType.minVertices
    }
  TextCell(
    value = text,
    editing = editing,
    onStartEditing = onStartEditing,
    onStopEditing = onStopEditing,
    onValueChange = {
      text = it
      GeometryText.parse(it)?.let(onChange)
    },
    width = GeometryCellWidth,
    isError = isError,
    monospace = true,
    textColor = textColor,
  )
}

// ---------------------------------------------------------------------------------------------
// Settings, style & schema
// ---------------------------------------------------------------------------------------------

@Composable
private fun DatasetSettingsPanel(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
  extraRemoteProperties: List<EntityProperty> = emptyList(),
  modifier: Modifier = Modifier,
) {
  val key = dataset.key
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  val listState = remember(key) { LazyListState() }
  var showAdvanced by remember(key) { mutableStateOf(!FormEditorValidator.isValidName(dataset.id)) }
  var confirmDelete by remember(key) { mutableStateOf(false) }
  var expandedRemotePropertyGroups by remember(key) { mutableStateOf(emptySet<String>()) }

  if (confirmDelete) {
    ConfirmDeleteDatasetDialog(
      dataset = dataset,
      onConfirm = {
        confirmDelete = false
        actions.deleteDataset(key)
      },
      onDismiss = { confirmDelete = false },
    )
  }

  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      datasetIdentityItems(uiState, actions, dataset, isMap)
      datasetSamplingAndStyleItems(uiState, actions, dataset, isMap)
      datasetPropertiesItems(uiState, actions, dataset)
      if (extraRemoteProperties.isNotEmpty()) {
        datasetRemotePropertiesItems(
          extraRemoteProperties = extraRemoteProperties,
          expandedGroups = expandedRemotePropertyGroups,
          onToggleGroup = { groupId ->
            expandedRemotePropertyGroups =
              if (groupId in expandedRemotePropertyGroups) expandedRemotePropertyGroups - groupId
              else expandedRemotePropertyGroups + groupId
          },
        )
      }
      if (!dataset.isLinkedToForm && !uiState.isReadOnly) {
        item(key = "add-property") {
          TextButton(onClick = { actions.addProperty(key) }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.surveyeditor_dataset_add_property))
          }
        }
      }
      item(key = "advanced") {
        DatasetAdvancedSettingsSection(
          dataset = dataset,
          isReadOnly = uiState.isReadOnly,
          showAdvanced = showAdvanced,
          onToggleAdvanced = { showAdvanced = !showAdvanced },
          onUpdateId = { v -> actions.updateDataset(key) { it.copy(id = v.trim()) } },
          onRequestDelete = { confirmDelete = true },
        )
      }
    }
  }
}

private fun LazyListScope.datasetIdentityItems(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
  isMap: Boolean,
) {
  val key = dataset.key
  item(key = "settings-header") {
    SectionLabel(stringResource(Res.string.surveyeditor_dataset_settings))
  }
  item(key = "name") {
    OutlinedTextField(
      value = dataset.displayName,
      onValueChange = { v -> actions.updateDataset(key) { it.copy(displayName = v) } },
      enabled = !uiState.isReadOnly,
      label = { Text(stringResource(Res.string.surveyeditor_dataset_name)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
  }
  item(key = "description") {
    OutlinedTextField(
      value = dataset.description,
      onValueChange = { v -> actions.updateDataset(key) { it.copy(description = v) } },
      enabled = !uiState.isReadOnly,
      label = { Text(stringResource(Res.string.surveyeditor_description)) },
      minLines = 2,
      modifier = Modifier.fillMaxWidth(),
    )
  }
  if (isMap && !dataset.isGenerated) {
    item(key = "geometry-type") {
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_dataset_geometry_type),
        selectedText = stringResource(dataset.geometryType.pluralRes),
        options = GeometryType.entries,
        optionText = { stringResource(it.pluralRes) },
        enabled = !uiState.isReadOnly,
        onSelect = { g -> actions.updateDataset(key) { it.copy(geometryType = g) } },
      )
    }
  }
}

private fun LazyListScope.datasetSamplingAndStyleItems(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
  isMap: Boolean,
) {
  val key = dataset.key
  if (dataset.generator != null) {
    item(key = "sample-design") {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(stringResource(Res.string.surveyeditor_dataset_sample_design))
        SamplingDesignPanel(uiState, actions, dataset)
      }
    }
    item(key = "plot-review") {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(stringResource(Res.string.surveyeditor_plot_review))
        PlotReviewPanel(uiState, actions, dataset)
      }
    }
  }
  item(key = "key-label-properties") {
    val choose = stringResource(Res.string.surveyeditor_dataset_choose)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_dataset_key_property),
        selectedText = dataset.property(dataset.keyProperty)?.label ?: choose,
        options = dataset.properties,
        optionText = { it.label },
        enabled = !uiState.isReadOnly,
        onSelect = { p -> actions.updateDataset(key) { it.copy(keyProperty = p.name) } },
      )
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_dataset_label_property),
        selectedText = dataset.property(dataset.labelProperty)?.label ?: choose,
        options = dataset.properties,
        optionText = { it.label },
        enabled = !uiState.isReadOnly,
        onSelect = { p -> actions.updateDataset(key) { it.copy(labelProperty = p.name) } },
      )
    }
  }
  item(key = "field-collection") {
    FieldCollectionSettingsSection(uiState = uiState, actions = actions, dataset = dataset)
  }
  if (isMap) {
    item(key = "map-style") {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(stringResource(Res.string.surveyeditor_dataset_map_style))
        LayerStyleEditor(dataset, enabled = !uiState.isReadOnly) { transform ->
          actions.updateDataset(key) { it.copy(style = transform(it.style)) }
        }
      }
    }
  }
}

private fun LazyListScope.datasetPropertiesItems(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
) {
  val key = dataset.key
  item(key = "properties-header") {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      SectionLabel(stringResource(Res.string.surveyeditor_dataset_properties))
      if (dataset.isLinkedToForm) {
        DatasetPropertiesLinkedToFormNotice()
      }
    }
  }
  itemsIndexed(dataset.properties, key = { index, _ -> "property-$index" }) { index, property ->
    val reserved = dataset.isGenerated && property.name in SamplePlotProperties.all
    PropertyEditor(
      property = property,
      readOnly = dataset.isLinkedToForm || reserved || uiState.isReadOnly,
      canRemove =
        !uiState.isReadOnly && !dataset.isLinkedToForm && !reserved && dataset.properties.size > 1,
      onChange = { actions.updateProperty(key, index, it) },
      onRemove = { actions.removeProperty(key, index) },
    )
  }
}

@Composable
private fun DatasetPropertiesLinkedToFormNotice() {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Outlined.Link,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        stringResource(Res.string.surveyeditor_dataset_properties_managed_by_form),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun LazyListScope.datasetRemotePropertiesItems(
  extraRemoteProperties: List<EntityProperty>,
  expandedGroups: Set<String>,
  onToggleGroup: (String) -> Unit,
) {
  val groupedRemote =
    groupColumnsByCommonPrefix(columns = extraRemoteProperties, keyOf = { it.name })
  groupedRemote.groups.forEach { group ->
    val expanded = group.id in expandedGroups
    item(key = "remote-group-header-${group.id}") {
      CollapsiblePropertyGroupRow(
        groupId = group.id,
        wildcardLabel = group.wildcardLabel,
        count = group.size,
        expanded = expanded,
        onToggle = { onToggleGroup(group.id) },
      )
    }
    if (expanded) {
      items(group.items, key = { prop -> "remote-property-${prop.name}" }) { prop ->
        RemotePropertyChip(prop = prop, indented = true)
      }
    }
  }
  val ungroupedRemote =
    groupedRemote.displaySlots.filterIsInstance<GroupedTableColumn.Single<EntityProperty>>()
  items(ungroupedRemote, key = { slot -> "remote-property-${slot.item.name}" }) { slot ->
    RemotePropertyChip(prop = slot.item, indented = false)
  }
}

@Composable
private fun RemotePropertyChip(prop: EntityProperty, indented: Boolean) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().padding(start = if (indented) 14.dp else 0.dp),
  ) {
    Text(
      text = prop.label.ifBlank { prop.name },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
  }
}

@Composable
private fun FieldCollectionSettingsSection(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    SectionLabel(stringResource(Res.string.surveyeditor_dataset_field_collection))
    val linkedForm = uiState.forms.firstOrNull { it.key == dataset.linkedFormKey }
    if (linkedForm != null) {
      LinkedFormCard(
        dataset = dataset,
        formTitle = linkedForm.form.title,
        isReadOnly = uiState.isReadOnly,
        onEditForm = { actions.select(SurveyEditorSection.Form(linkedForm.key)) },
        onUnlink = { actions.unlinkDataset(dataset.key) },
      )
    } else {
      UnlinkedFormCard(
        datasetKind = dataset.kind,
        isReadOnly = uiState.isReadOnly,
        onCreateForm = { actions.createFormForDataset(dataset.key) },
      )
    }
    val updatingForms = uiState.formsUpdating(dataset)
    if (updatingForms.isNotEmpty()) {
      Text(
        stringResource(Res.string.surveyeditor_dataset_updated_by),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      updatingForms.forEach { form ->
        AssistChip(
          onClick = { actions.select(SurveyEditorSection.Form(form.key)) },
          label = { Text(form.form.title) },
          leadingIcon = {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          },
        )
      }
    }
  }
}

@Composable
private fun LinkedFormCard(
  dataset: EntityDataset,
  formTitle: String,
  isReadOnly: Boolean,
  onEditForm: () -> Unit,
  onUnlink: () -> Unit,
) {
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  // The same neutral surface as the card shown when no form is linked: a linked form is ordinary
  // layer configuration, not a status to call out.
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
          stringResource(Res.string.surveyeditor_dataset_linked_to, formTitle),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Text(
        stringResource(
          if (isMap) Res.string.surveyeditor_dataset_linked_form_map_layer
          else Res.string.surveyeditor_dataset_linked_form_data_table
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onEditForm) {
          Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(stringResource(Res.string.surveyeditor_dataset_edit_form))
        }
        if (!isReadOnly) {
          OutlinedButton(onClick = onUnlink) {
            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.surveyeditor_dataset_unlink_form))
          }
        }
      }
    }
  }
}

@Composable
private fun UnlinkedFormCard(
  datasetKind: DatasetKind,
  isReadOnly: Boolean,
  onCreateForm: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(
          when (datasetKind) {
            DatasetKind.MAP_LAYER -> Res.string.surveyeditor_dataset_no_form_map_layer
            DatasetKind.DATA_TABLE -> Res.string.surveyeditor_dataset_no_form_data_table
          }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!isReadOnly) {
        Button(onClick = onCreateForm) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            stringResource(
              when (datasetKind) {
                DatasetKind.MAP_LAYER -> Res.string.surveyeditor_dataset_create_form_map_layer
                DatasetKind.DATA_TABLE -> Res.string.surveyeditor_dataset_create_form_data_table
              }
            )
          )
        }
      }
    }
  }
}

/**
 * Whether the Advanced section offers [dataset]'s ID for editing. IDs people chose or that were
 * derived from the name (`farm_plots`) are shown, since forms refer to them. An ID the system
 * generated (a UUID) means nothing to a survey organizer, who knows the map layer or data table by
 * its name, so it stays out of the settings.
 */
internal fun showsDatasetIdField(dataset: EntityDataset): Boolean = !RecordIds.isUuid(dataset.id)

@Composable
private fun DatasetAdvancedSettingsSection(
  dataset: EntityDataset,
  isReadOnly: Boolean,
  showAdvanced: Boolean,
  onToggleAdvanced: () -> Unit,
  onUpdateId: (String) -> Unit,
  onRequestDelete: () -> Unit,
) {
  val showsIdField = showsDatasetIdField(dataset)
  // Nothing to show: the ID is internal and a read-only survey has no Delete button.
  if (!showsIdField && isReadOnly) return
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleAdvanced),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel(stringResource(Res.string.surveyeditor_dataset_advanced))
      TextButton(onClick = onToggleAdvanced) {
        Text(
          stringResource(
            if (showAdvanced) Res.string.surveyeditor_dataset_hide
            else Res.string.surveyeditor_dataset_show
          )
        )
      }
    }
    if (showAdvanced) {
      if (showsIdField) {
        OutlinedTextField(
          value = dataset.id,
          onValueChange = onUpdateId,
          enabled = !isReadOnly,
          label = { Text(stringResource(Res.string.surveyeditor_dataset_id)) },
          singleLine = true,
          supportingText = { Text(stringResource(Res.string.surveyeditor_dataset_id_supporting)) },
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(fontFamily = appMonospaceFontFamily()),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      if (!isReadOnly) {
        OutlinedButton(
          onClick = onRequestDelete,
          colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(deleteDatasetMenuLabel(dataset.kind))
        }
      }
    }
  }
}

@Composable
private fun LayerStyleEditor(
  dataset: EntityDataset,
  enabled: Boolean = true,
  update: ((LayerStyle) -> LayerStyle) -> Unit,
) {
  val style = dataset.style
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      PresetColors.forEach { hex ->
        val selected = hex.equals(style.colorHex, ignoreCase = true)
        Box(
          modifier =
            Modifier.size(28.dp)
              .clip(CircleShape)
              .background(Color(parseHexColor(hex)!!))
              .border(
                if (selected) 3.dp else 0.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                CircleShape,
              )
              .clickable(enabled = enabled) { update { it.copy(colorHex = hex) } }
        )
      }
    }
    OutlinedTextField(
      value = style.colorHex,
      onValueChange = { v -> update { it.copy(colorHex = v.trim()) } },
      enabled = enabled,
      label = { Text(stringResource(Res.string.surveyeditor_dataset_color)) },
      singleLine = true,
      isError = enabled && parseHexColor(style.colorHex) == null,
      textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = appMonospaceFontFamily()),
      modifier = Modifier.fillMaxWidth(),
    )
    if (dataset.geometryType == GeometryType.POINT) {
      LayerIconPicker(
        selectedIconName = style.iconName,
        layerColor =
          parseHexColor(style.colorHex)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
        enabled = enabled,
        onSelect = { name -> update { it.copy(iconName = name) } },
      )
    }
    if (dataset.geometryType != GeometryType.POINT) {
      Text(
        stringResource(Res.string.surveyeditor_dataset_stroke_width, style.strokeWidth.toInt()),
        style = MaterialTheme.typography.bodySmall,
      )
      Slider(
        value = style.strokeWidth.toFloat(),
        onValueChange = { v -> update { it.copy(strokeWidth = v.toDouble()) } },
        enabled = enabled,
        valueRange = 1f..8f,
        steps = 6,
      )
    }
    if (dataset.geometryType == GeometryType.POLYGON) {
      Text(
        stringResource(
          Res.string.surveyeditor_dataset_fill_opacity,
          (style.fillOpacity * 100).toInt(),
        ),
        style = MaterialTheme.typography.bodySmall,
      )
      Slider(
        value = style.fillOpacity.toFloat(),
        onValueChange = { v -> update { it.copy(fillOpacity = v.toDouble()) } },
        enabled = enabled,
        valueRange = 0f..1f,
      )
    }
    SwitchRow(
      title = stringResource(Res.string.surveyeditor_dataset_visible_by_default),
      description = stringResource(Res.string.surveyeditor_dataset_visible_by_default_description),
      checked = style.visibleByDefault,
      enabled = enabled,
      onCheckedChange = { v -> update { it.copy(visibleByDefault = v) } },
    )
  }
}

@Composable
private fun PropertyEditor(
  property: EntityProperty,
  readOnly: Boolean = false,
  canRemove: Boolean,
  onChange: (EntityProperty) -> Unit,
  onRemove: () -> Unit,
) {
  val hasInvalidName =
    !FormEditorValidator.isValidName(property.name) || property.name == "geometry"
  var showAdvanced by remember { mutableStateOf(hasInvalidName) }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = property.label,
          onValueChange = { newLabel ->
            val currentAutoSlug = slugify(property.label, fallback = property.name)
            val shouldSyncName =
              !showAdvanced &&
                (property.name == currentAutoSlug || property.name.startsWith("property"))
            val updatedName =
              if (shouldSyncName) slugify(newLabel, fallback = property.name) else property.name
            onChange(property.copy(label = newLabel, name = updatedName))
          },
          label = { Text(stringResource(Res.string.surveyeditor_dataset_property_label)) },
          singleLine = true,
          enabled = !readOnly,
          modifier = Modifier.weight(1f),
        )
        if (!readOnly) {
          TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(
              text =
                stringResource(
                  if (showAdvanced) Res.string.surveyeditor_dataset_property_hide_id
                  else Res.string.surveyeditor_dataset_property_advanced
                ),
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }
      if (showAdvanced || hasInvalidName) {
        OutlinedTextField(
          value = property.name,
          onValueChange = { onChange(property.copy(name = it.trim())) },
          label = { Text(stringResource(Res.string.surveyeditor_dataset_property_internal_id)) },
          singleLine = true,
          enabled = !readOnly,
          isError = hasInvalidName,
          supportingText = {
            Text(stringResource(Res.string.surveyeditor_dataset_property_internal_id_supporting))
          },
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(fontFamily = appMonospaceFontFamily()),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
          if (readOnly) {
            OutlinedTextField(
              value = stringResource(property.type.labelRes),
              onValueChange = {},
              label = { Text(stringResource(Res.string.surveyeditor_dataset_property_type)) },
              readOnly = true,
              enabled = false,
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            DropdownSelector(
              label = stringResource(Res.string.surveyeditor_dataset_property_type),
              selectedText = stringResource(property.type.labelRes),
              options = PropertyType.entries,
              optionText = { stringResource(it.labelRes) },
              onSelect = { onChange(property.copy(type = it)) },
            )
          }
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
          checked = property.required,
          enabled = !readOnly,
          onCheckedChange = { onChange(property.copy(required = it)) },
        )
        Text(
          stringResource(Res.string.surveyeditor_dataset_property_required),
          style = MaterialTheme.typography.bodySmall,
        )
        if (!readOnly) {
          IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.surveyeditor_dataset_remove_property),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
  }
}
