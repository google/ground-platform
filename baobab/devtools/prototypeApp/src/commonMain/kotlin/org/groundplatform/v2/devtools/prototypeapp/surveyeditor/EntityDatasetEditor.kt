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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.formeditor.SectionLabel

private val CellWidth = 160.dp
private val GeometryCellWidth = 280.dp
private val IndexCellWidth = 44.dp
private val CellHeight = 38.dp

private val PresetColors =
  listOf("#2E7D32", "#1565C0", "#6D4C41", "#C62828", "#F9A825", "#6A1B9A", "#00838F", "#37474F")

/**
 * Entity editor for a Map layer (map preview + feature table + style) or a Data table (spreadsheet
 * of rows). Both share the schema (properties) and dataset settings panel.
 */
@Composable
internal fun EntityDatasetEditor(state: SurveyEditorState, dataset: EntityDataset) {
  var selectedRow by remember { mutableStateOf<String?>(null) }
  val issues = state.datasetIssues(dataset)
  val isMap = dataset.kind == DatasetKind.MAP_LAYER

  Column(modifier = Modifier.fillMaxSize()) {
    DatasetHeader(state, dataset, issues.size)
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
            state = state,
            dataset = dataset,
            selectedRow = selectedRow,
            onSelectRow = { selectedRow = it },
            modifier = Modifier.fillMaxWidth().weight(1.4f),
          )
        }
        RowsTableCard(
          state = state,
          dataset = dataset,
          issues = issues,
          selectedRow = selectedRow,
          onSelectRow = { selectedRow = it },
          modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
      DatasetSettingsPanel(state, dataset, Modifier.width(380.dp).fillMaxHeight())
    }
  }
}

@Composable
private fun DatasetHeader(state: SurveyEditorState, dataset: EntityDataset, issueCount: Int) {
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
            Icons.Default.Warning,
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
      TextButton(onClick = { state.deleteDataset(dataset.key) }) {
        Icon(
          Icons.Default.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          "Delete ${dataset.kind.singular.lowercase()}",
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Rows / features table
// ---------------------------------------------------------------------------------------------

@Composable
private fun RowsTableCard(
  state: SurveyEditorState,
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
        TextButton(onClick = { onSelectRow(state.addRow(dataset.key)) }) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text(if (isMap) "Add feature" else "Add row")
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
              if (isMap) "No features yet. Add one here or on the map." else "No rows yet.",
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
                    color = if (row.key in rowIssues) colors.error else colors.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                  )
                }
                dataset.properties.forEach { p ->
                  val value = row.values[p.name].orEmpty()
                  if (p.type == PropertyType.BOOLEAN) {
                    BooleanCell(value) { state.updateCell(dataset.key, row.key, p.name, it) }
                  } else {
                    TextCell(
                      value = value,
                      onValueChange = { state.updateCell(dataset.key, row.key, p.name, it) },
                      width = CellWidth,
                      isError =
                        !p.type.accepts(value) ||
                          (value.isBlank() && (p.required || p.name == dataset.keyProperty)),
                      onFocus = { onSelectRow(row.key) },
                    )
                  }
                }
                if (isMap) {
                  GeometryCell(dataset, row) { state.updateGeometry(dataset.key, row.key, it) }
                }
                IconButton(
                  onClick = { state.removeRow(dataset.key, row.key) },
                  modifier = Modifier.size(IndexCellWidth),
                ) {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
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
  onFocus: () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface)
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
private fun GeometryCell(dataset: EntityDataset, row: EntityRow, onChange: (List<LatLng>) -> Unit) {
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
  )
}

// ---------------------------------------------------------------------------------------------
// Settings, style & schema
// ---------------------------------------------------------------------------------------------

@Composable
private fun DatasetSettingsPanel(
  state: SurveyEditorState,
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
        onValueChange = { v -> state.updateDataset(key) { it.copy(displayName = v) } },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = dataset.id,
        onValueChange = { v -> state.updateDataset(key) { it.copy(id = v.trim()) } },
        label = { Text("ID") },
        singleLine = true,
        supportingText = { Text("Used in Forms, e.g. instance('${dataset.id}')") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = dataset.description,
        onValueChange = { v -> state.updateDataset(key) { it.copy(description = v) } },
        label = { Text("Description") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
      )
      if (isMap) {
        DropdownSelector(
          label = "Geometry type",
          selectedText = dataset.geometryKind.label,
          options = GeometryKind.entries,
          optionText = { it.label },
          onSelect = { g -> state.updateDataset(key) { it.copy(geometryKind = g) } },
        )
      }
      DropdownSelector(
        label = "Key property (unique ID)",
        selectedText = dataset.property(dataset.keyProperty)?.label ?: "Choose…",
        options = dataset.properties,
        optionText = { "${it.label} (${it.name})" },
        onSelect = { p -> state.updateDataset(key) { it.copy(keyProperty = p.name) } },
      )
      DropdownSelector(
        label = "Label property (shown in lists)",
        selectedText = dataset.property(dataset.labelProperty)?.label ?: "Choose…",
        options = dataset.properties,
        optionText = { "${it.label} (${it.name})" },
        onSelect = { p -> state.updateDataset(key) { it.copy(labelProperty = p.name) } },
      )
      SwitchRow(
        title = "Allow adding in the field",
        description =
          "Data collectors can add new ${if (isMap) "map features" else "rows"} from the mobile app.",
        checked = dataset.fieldCreationEnabled,
        onCheckedChange = { v -> state.updateDataset(key) { it.copy(fieldCreationEnabled = v) } },
      )

      if (isMap) {
        SectionLabel("Map style")
        LayerStyleEditor(dataset) { transform ->
          state.updateDataset(key) { it.copy(style = transform(it.style)) }
        }
      }

      SectionLabel("Properties")
      dataset.properties.forEachIndexed { index, property ->
        PropertyEditor(
          property = property,
          canRemove = dataset.properties.size > 1,
          onChange = { state.updateProperty(key, index, it) },
          onRemove = { state.removeProperty(key, index) },
        )
      }
      TextButton(onClick = { state.addProperty(key) }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add property")
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
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
      modifier = Modifier.fillMaxWidth(),
    )
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
  canRemove: Boolean,
  onChange: (EntityProperty) -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
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
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = property.name,
          onValueChange = { onChange(property.copy(name = it.trim())) },
          label = { Text("Name") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.width(120.dp),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
          DropdownSelector(
            label = "Type",
            selectedText = property.type.label,
            options = PropertyType.entries,
            optionText = { it.label },
            onSelect = { onChange(property.copy(type = it)) },
          )
        }
        Checkbox(
          checked = property.required,
          onCheckedChange = { onChange(property.copy(required = it)) },
        )
        Text("Required", style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = onRemove, enabled = canRemove) {
          Icon(Icons.Default.Close, contentDescription = "Remove property")
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
