/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LayerStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PropertyType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.parseHexColor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.SectionLabel
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyEditorActions

private val CellWidth = 160.dp
private val GeometryCellWidth = 280.dp
private val IndexCellWidth = 44.dp
private val CellHeight = 38.dp

private val PresetColors =
  listOf("#F37C22", "#D13135", "#7A279F", "#2278CF", "#3C8D40", "#F9BF40", "#2E7D32", "#6D4C41")

/**
 * Entity editor for a Map layer (map preview + feature table + style) or a Data table (spreadsheet
 * of rows). Both share the schema (properties) and dataset settings panel.
 */
@Composable
internal fun EntityDatasetEditor(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
) {
  var selectedRow by remember { mutableStateOf<String?>(null) }
  val issues = uiState.datasetIssues(dataset)
  val isMap = dataset.kind == DatasetKind.MAP_LAYER

  Column(modifier = Modifier.fillMaxSize()) {
    DatasetHeader(uiState, actions, dataset, issues.size)
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
          )
        }
        RowsTableCard(
          uiState = uiState,
          actions = actions,
          dataset = dataset,
          issues = issues,
          selectedRow = selectedRow,
          onSelectRow = { selectedRow = it },
          modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
      DatasetSettingsPanel(uiState, actions, dataset, Modifier.width(380.dp).fillMaxHeight())
    }
  }
}

@Composable
private fun DatasetHeader(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
  issueCount: Int,
) {
  val itemsLabel = if (dataset.kind == DatasetKind.MAP_LAYER) "features" else "rows"
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
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = dataset.kind.singular,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = dataset.displayName.ifBlank { "Untitled" },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            buildString {
              append("${dataset.rows.size} $itemsLabel • ${dataset.properties.size} properties")
              if (dataset.kind == DatasetKind.MAP_LAYER) {
                append(" • ${dataset.geometryKind.label.lowercase()}")
              }
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (issueCount > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            "$issueCount ${if (issueCount == 1) "issue" else "issues"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      TextButton(onClick = { actions.deleteDataset(dataset.key) }) {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Delete ${dataset.kind.singular.lowercase()}", color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Rows / features table
// ---------------------------------------------------------------------------------------------

@Composable
private fun RowsTableCard(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
  issues: List<DatasetIssue>,
  selectedRow: String?,
  onSelectRow: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  val colors = MaterialTheme.colorScheme
  val rowIssues = issues.mapNotNull { it.rowKey }.toSet()
  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          if (isMap) "Features" else "Rows",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        if (!dataset.isGenerated) {
          TextButton(onClick = { onSelectRow(actions.addRow(dataset.key)) }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isMap) "Add feature" else "Add row")
          }
        }
      }
      if (issues.isNotEmpty()) {
        Surface(
          color = colors.errorContainer,
          contentColor = colors.onErrorContainer,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            issues.take(6).forEach { issue ->
              Text(
                "• ${issue.message}",
                style = MaterialTheme.typography.bodySmall,
                modifier =
                  if (issue.rowKey != null) Modifier.clickable { onSelectRow(issue.rowKey) }
                  else Modifier,
              )
            }
            if (issues.size > 6) {
              Text("…and ${issues.size - 6} more", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
        Spacer(Modifier.height(8.dp))
      }
      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .horizontalScroll(rememberScrollState())
      ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          // Header
          Row {
            HeaderCell("#", IndexCellWidth)
            dataset.properties.forEach { p ->
              val tag =
                when (p.name) {
                  dataset.keyProperty -> " (key)"
                  dataset.labelProperty -> " (label)"
                  else -> ""
                }
              HeaderCell(
                p.label.ifBlank { p.name } + (if (p.required) " *" else "") + tag,
                CellWidth,
              )
            }
            if (isMap) HeaderCell("Geometry (lat, lng; …)", GeometryCellWidth)
            HeaderCell("", IndexCellWidth)
          }
          if (dataset.rows.isEmpty()) {
            Text(
              when {
                dataset.isGenerated -> "No sample plots yet. Generate them from the sample design."
                isMap -> "No features yet. Add one here or on the map."
                else -> "No rows yet."
              },
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
            )
          }
          dataset.rows.forEachIndexed { index, row ->
            key(row.key) {
              val selected = row.key == selectedRow
              Row(
                modifier =
                  Modifier.background(
                    if (selected) colors.secondaryContainer else Color.Transparent
                  ),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.width(IndexCellWidth).height(CellHeight).clickable {
                      onSelectRow(if (selected) null else row.key)
                    },
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      when {
                        row.key in rowIssues -> colors.error
                        selected -> colors.onSecondaryContainer
                        else -> colors.onSurfaceVariant
                      },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                  )
                }
                val cellTextColor = if (selected) colors.onSecondaryContainer else colors.onSurface
                dataset.properties.forEach { p ->
                  val value = row.values[p.name].orEmpty()
                  val reserved = dataset.isGenerated && p.name in SamplePlotProperties.all
                  if (reserved) {
                    ReadOnlyCell(
                      if (p.name == SamplePlotProperties.SAMPLES) samplesSummary(value) else value,
                      onClick = { onSelectRow(row.key) },
                    )
                  } else if (p.type == PropertyType.BOOLEAN) {
                    BooleanCell(value) { actions.updateCell(dataset.key, row.key, p.name, it) }
                  } else {
                    TextCell(
                      value = value,
                      onValueChange = { actions.updateCell(dataset.key, row.key, p.name, it) },
                      width = CellWidth,
                      isError =
                        !p.type.accepts(value) ||
                          (value.isBlank() && (p.required || p.name == dataset.keyProperty)),
                      textColor = cellTextColor,
                      onFocus = { onSelectRow(row.key) },
                    )
                  }
                }
                if (isMap && dataset.isGenerated) {
                  ReadOnlyCell(
                    GeometryText.format(row.geometry),
                    width = GeometryCellWidth,
                    monospace = true,
                    onClick = { onSelectRow(row.key) },
                  )
                } else if (isMap) {
                  GeometryCell(dataset, row, textColor = cellTextColor) {
                    actions.updateGeometry(dataset.key, row.key, it)
                  }
                }
                if (dataset.isGenerated) {
                  Spacer(Modifier.width(IndexCellWidth))
                } else {
                  IconButton(
                    onClick = { actions.removeRow(dataset.key, row.key) },
                    modifier = Modifier.size(IndexCellWidth),
                  ) {
                    Icon(
                      Icons.Outlined.Close,
                      contentDescription = "Delete",
                      tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
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

@Composable
private fun TextCell(
  value: String,
  onValueChange: (String) -> Unit,
  width: Dp,
  isError: Boolean,
  monospace: Boolean = false,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  onFocus: () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodySmall.copy(color = textColor)
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = if (monospace) style.copy(fontFamily = FontFamily.Monospace) else style,
    cursorBrush = SolidColor(colors.primary),
    modifier =
      Modifier.width(width)
        .height(CellHeight)
        .border(
          if (isError) 1.5.dp else 0.5.dp,
          if (isError) colors.error else colors.outlineVariant,
        )
        .onFocusChanged { if (it.isFocused) onFocus() }
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
          if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it
        },
      color = colors.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** "25 points", for a plot's `samples` geotrace. */
private fun samplesSummary(geotrace: String): String =
  when (val n = SamplePlotProperties.geotraceSize(geotrace)) {
    0 -> "None"
    1 -> "1 point"
    else -> "$n points"
  }

@Composable
private fun BooleanCell(value: String, onValueChange: (String) -> Unit) {
  val checked = value.lowercase() in setOf("yes", "true")
  Box(
    modifier =
      Modifier.width(CellWidth)
        .height(CellHeight)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    contentAlignment = Alignment.CenterStart,
  ) {
    Checkbox(checked = checked, onCheckedChange = { onValueChange(if (it) "yes" else "no") })
  }
}

@Composable
private fun GeometryCell(
  dataset: EntityDataset,
  row: EntityRow,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  onChange: (List<LatLng>) -> Unit,
) {
  var text by remember(row.key, row.geometry) { mutableStateOf(GeometryText.format(row.geometry)) }
  val parsed = GeometryText.parse(text)
  TextCell(
    value = text,
    onValueChange = {
      text = it
      GeometryText.parse(it)?.let(onChange)
    },
    width = GeometryCellWidth,
    isError = parsed == null || parsed.size < dataset.geometryKind.minVertices,
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
  modifier: Modifier = Modifier,
) {
  val key = dataset.key
  val isMap = dataset.kind == DatasetKind.MAP_LAYER
  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SectionLabel("Settings")
      OutlinedTextField(
        value = dataset.displayName,
        onValueChange = { v -> actions.updateDataset(key) { it.copy(displayName = v) } },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = dataset.id,
        onValueChange = { v -> actions.updateDataset(key) { it.copy(id = v.trim()) } },
        label = { Text("ID") },
        singleLine = true,
        supportingText = { Text("Forms use this ID to look up records from this dataset.") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = dataset.description,
        onValueChange = { v -> actions.updateDataset(key) { it.copy(description = v) } },
        label = { Text("Description") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
      )
      if (isMap && !dataset.isGenerated) {
        DropdownSelector(
          label = "Geometry type",
          selectedText = dataset.geometryKind.label,
          options = GeometryKind.entries,
          optionText = { it.label },
          onSelect = { g -> actions.updateDataset(key) { it.copy(geometryKind = g) } },
        )
      }
      if (dataset.generator != null) {
        SectionLabel("Sample design")
        SamplingDesignPanel(uiState, actions, dataset)
      }
      DropdownSelector(
        label = "Key property (unique ID)",
        selectedText = dataset.property(dataset.keyProperty)?.label ?: "Choose…",
        options = dataset.properties,
        optionText = { "${it.label} (${it.name})" },
        onSelect = { p -> actions.updateDataset(key) { it.copy(keyProperty = p.name) } },
      )
      DropdownSelector(
        label = "Label property (shown in lists)",
        selectedText = dataset.property(dataset.labelProperty)?.label ?: "Choose…",
        options = dataset.properties,
        optionText = { "${it.label} (${it.name})" },
        onSelect = { p -> actions.updateDataset(key) { it.copy(labelProperty = p.name) } },
      )
      SectionLabel("Field collection & Form")
      val linkedForm = uiState.forms.firstOrNull { it.key == dataset.linkedFormKey }
      if (linkedForm != null) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Outlined.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                "Linked to ${linkedForm.form.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              "Data collectors can add new ${if (isMap) "map features" else "rows"} using this form. Schema properties stay in sync with form questions.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = { actions.select(SurveyEditorSection.Form(linkedForm.key)) }) {
                Icon(
                  Icons.Outlined.Edit,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Edit form")
              }
              OutlinedButton(
                onClick = { actions.unlinkDataset(key) },
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                  ),
                border =
                  BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                  ),
              ) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Unlink form")
              }
            }
          }
        }
      } else {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          contentColor = MaterialTheme.colorScheme.onSurface,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              "No form is linked to this ${dataset.kind.singular.lowercase()}. Data collectors can only view existing items.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { actions.createFormForDataset(key) }) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Create form for ${dataset.kind.singular.lowercase()}")
            }
          }
        }
      }

      val updatingForms = uiState.formsUpdating(dataset)
      if (updatingForms.isNotEmpty()) {
        Text(
          "Updated by",
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

      if (isMap) {
        SectionLabel("Map style")
        LayerStyleEditor(dataset) { transform ->
          actions.updateDataset(key) { it.copy(style = transform(it.style)) }
        }
      }

      SectionLabel("Properties")
      if (dataset.isLinkedToForm) {
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
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              "Properties are managed by the linked form. To edit, add, or delete properties, edit the form.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      dataset.properties.forEachIndexed { index, property ->
        val reserved = dataset.isGenerated && property.name in SamplePlotProperties.all
        PropertyEditor(
          property = property,
          readOnly = dataset.isLinkedToForm || reserved,
          canRemove = !dataset.isLinkedToForm && !reserved && dataset.properties.size > 1,
          onChange = { actions.updateProperty(key, index, it) },
          onRemove = { actions.removeProperty(key, index) },
        )
      }
      if (!dataset.isLinkedToForm) {
        TextButton(onClick = { actions.addProperty(key) }) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add property")
        }
      }
    }
  }
}

@Composable
private fun LayerStyleEditor(dataset: EntityDataset, update: ((LayerStyle) -> LayerStyle) -> Unit) {
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
              .clickable { update { it.copy(colorHex = hex) } }
        )
      }
    }
    OutlinedTextField(
      value = style.colorHex,
      onValueChange = { v -> update { it.copy(colorHex = v.trim()) } },
      label = { Text("Color") },
      singleLine = true,
      isError = parseHexColor(style.colorHex) == null,
      textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
      modifier = Modifier.fillMaxWidth(),
    )
    if (dataset.geometryKind == GeometryKind.POINT) {
      LayerIconPicker(
        selectedIconName = style.iconName,
        layerColor =
          parseHexColor(style.colorHex)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
        onSelect = { name -> update { it.copy(iconName = name) } },
      )
    }
    if (dataset.geometryKind != GeometryKind.POINT) {
      Text(
        "Stroke width: ${style.strokeWidth.toInt()} px",
        style = MaterialTheme.typography.bodySmall,
      )
      Slider(
        value = style.strokeWidth.toFloat(),
        onValueChange = { v -> update { it.copy(strokeWidth = v.toDouble()) } },
        valueRange = 1f..8f,
        steps = 6,
      )
    }
    if (dataset.geometryKind == GeometryKind.POLYGON) {
      Text(
        "Fill opacity: ${(style.fillOpacity * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall,
      )
      Slider(
        value = style.fillOpacity.toFloat(),
        onValueChange = { v -> update { it.copy(fillOpacity = v.toDouble()) } },
        valueRange = 0f..1f,
      )
    }
    SwitchRow(
      title = "Visible by default",
      description = "Show this layer when the map first opens.",
      checked = style.visibleByDefault,
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
          onValueChange = { onChange(property.copy(label = it)) },
          label = { Text("Label") },
          singleLine = true,
          enabled = !readOnly,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = property.name,
          onValueChange = { onChange(property.copy(name = it.trim())) },
          label = { Text("Name") },
          singleLine = true,
          enabled = !readOnly,
          textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.width(120.dp),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
          if (readOnly) {
            OutlinedTextField(
              value = property.type.label,
              onValueChange = {},
              label = { Text("Type") },
              readOnly = true,
              enabled = false,
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            DropdownSelector(
              label = "Type",
              selectedText = property.type.label,
              options = PropertyType.entries,
              optionText = { it.label },
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
        Text("Required", style = MaterialTheme.typography.bodySmall)
        if (!readOnly) {
          IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove property")
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
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
