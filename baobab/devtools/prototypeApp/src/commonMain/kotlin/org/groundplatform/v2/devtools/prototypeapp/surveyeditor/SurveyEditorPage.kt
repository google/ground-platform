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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.WebAppHeader
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderContext
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderSupportingText
import org.groundplatform.v2.devtools.prototypeapp.WebMobilePrototypeButton
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.remote.MapboxPlacesDataSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DragAxis
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DragReorderState
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorPage
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.formeditor.SectionLabel
import org.groundplatform.v2.devtools.prototypeapp.formeditor.dragToReorder
import org.groundplatform.v2.devtools.prototypeapp.map.SurveyBasemaps
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.FeatureFilter
import org.groundplatform.v2.map.GeoJsonSource
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.GeometryType
import org.groundplatform.v2.map.GroundMap
import org.groundplatform.v2.map.LngLatBounds
import org.groundplatform.v2.map.MapCameraState
import org.groundplatform.v2.map.MapContent
import org.groundplatform.v2.map.MapFeature
import org.groundplatform.v2.map.MapInsets
import org.groundplatform.v2.map.MapLayer
import org.groundplatform.v2.map.StyleValue

/**
 * Survey editor page: the shared [WebAppHeader] with publishing controls, a left-hand navigation
 * list (Survey details, Sharing, Forms, Map layers, Data tables), and a content pane showing the
 * editor for the selected item.
 *
 * Edits are kept as an unpublished draft in [state]. [onPublish] commits them; [onClose] throws
 * them away (after the user confirms, if there are any). Both are expected to leave the editor.
 */
@Composable
fun SurveyEditorPage(
  state: SurveyEditorState,
  appState: PrototypeAppState,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  onPublish: () -> Unit = state::markPublished,
  onClose: () -> Unit = state::discardChanges,
) {
  Column(modifier = modifier.fillMaxSize()) {
    SurveyEditorTopBar(state, onPublish, onClose, appState)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      SurveyNavigation(state = state, modifier = Modifier.width(280.dp).fillMaxHeight())
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        when (val section = state.section) {
          SurveyEditorSection.Details -> SurveyDetailsPane(state, appState.places)
          SurveyEditorSection.Sharing -> SharingPane(state)
          is SurveyEditorSection.Form -> {
            val entry = state.selectedForm
            if (entry != null) {
              LaunchedEffect(entry.editor.form.questions) { state.syncDatasetsLinkedToForm(entry) }
              key(entry.key) {
                FormEditorPage(
                  state = entry.editor,
                  isDarkTheme = isDarkTheme,
                  onCreateDataset = { state.createDatasetForForm(entry.key) },
                  onDelete = { state.deleteForm(entry.key) },
                )
              }
            }
          }
          is SurveyEditorSection.Dataset -> {
            val dataset = state.selectedDataset
            if (dataset != null) {
              key(section.key) { EntityDatasetEditor(state, dataset) }
            }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

/**
 * Close button and survey title on the left; draft status, Discard, and Publish changes on the
 * right. Close and Discard both run [onClose], asking for confirmation first if there are
 * unpublished changes.
 */
@Composable
private fun SurveyEditorTopBar(
  state: SurveyEditorState,
  onPublish: () -> Unit,
  onClose: () -> Unit,
  appState: PrototypeAppState,
) {
  val hasChanges = state.hasUnpublishedChanges
  val issueCount = state.issueCount
  var isConfirmingDiscard by remember { mutableStateOf(false) }

  WebAppHeader(
    state = appState,
    onSignOut = null,
    navigationIcon = {
      IconButton(onClick = { if (hasChanges) isConfirmingDiscard = true else onClose() }) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = "Close survey editor",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    context = {
      WebHeaderContext(title = state.details.title.ifBlank { "Untitled survey" }) {
        WebHeaderSupportingText("Survey editor", color = MaterialTheme.colorScheme.primary)
        WebHeaderSupportingText("·")
        when {
          issueCount > 0 ->
            WebHeaderSupportingText(
              "Fix $issueCount ${if (issueCount == 1) "issue" else "issues"} to publish",
              color = MaterialTheme.colorScheme.error,
            )
          hasChanges -> WebHeaderSupportingText("Unpublished changes")
          else -> WebHeaderSupportingText("All changes published")
        }
      }
    },
    actions = {
      WebMobilePrototypeButton(appState)
      TextButton(onClick = { isConfirmingDiscard = true }, enabled = hasChanges) { Text("Discard") }
      Button(onClick = onPublish, enabled = state.canPublish) { Text("Publish changes") }
    },
  )

  if (isConfirmingDiscard) {
    AlertDialog(
      onDismissRequest = { isConfirmingDiscard = false },
      title = { Text("Discard unpublished changes?") },
      text = {
        Text(
          "Changes you've made since this survey was last published will be lost. Data " +
            "collectors will keep seeing the published version."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            isConfirmingDiscard = false
            onClose()
          }
        ) {
          Text("Discard")
        }
      },
      dismissButton = {
        TextButton(onClick = { isConfirmingDiscard = false }) { Text("Keep editing") }
      },
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------

@Composable
private fun SurveyNavigation(state: SurveyEditorState, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      NavItem(
        label = "Survey details",
        icon = Icons.Outlined.Info,
        selected = state.section == SurveyEditorSection.Details,
        onClick = { state.select(SurveyEditorSection.Details) },
      )
      NavItem(
        label = "Sharing",
        icon = Icons.Outlined.Person,
        selected = state.section == SurveyEditorSection.Sharing,
        onClick = { state.select(SurveyEditorSection.Sharing) },
        trailing = "${state.sharing.collaborators.size + 1}",
      )

      NavHeading("Forms", addDescription = "Add form", onAdd = state::addForm)
      if (state.forms.isEmpty()) NavEmpty("No forms yet")
      ReorderableNavList(items = state.forms, keyOf = { it.key }, onMove = state::moveForm) {
        entry,
        showHandle ->
        val form = entry.editor.form
        NavItem(
          label = form.title.ifBlank { "Untitled form" },
          icon = if (showHandle) Icons.Outlined.DragIndicator else Icons.Outlined.Description,
          selected = state.section == SurveyEditorSection.Form(entry.key),
          onClick = { state.select(SurveyEditorSection.Form(entry.key)) },
          trailing = "${form.questions.size}",
          hasIssues = entry.editor.issues.isNotEmpty(),
          nested = true,
        )
      }

      DatasetNavGroup(state, DatasetKind.MAP_LAYER, state.mapLayers, Icons.Outlined.Layers)
      DatasetNavGroup(
        state,
        DatasetKind.DATA_TABLE,
        state.dataTables,
        Icons.AutoMirrored.Outlined.List,
      )
    }
  }
}

@Composable
private fun DatasetNavGroup(
  state: SurveyEditorState,
  kind: DatasetKind,
  datasets: List<EntityDataset>,
  icon: ImageVector,
) {
  NavHeading(
    kind.plural,
    addDescription = "Add ${kind.singular.lowercase()}",
    onAdd = { state.addDataset(kind) },
  )
  if (datasets.isEmpty()) NavEmpty("No ${kind.plural.lowercase()} yet")
  ReorderableNavList(items = datasets, keyOf = { it.key }, onMove = state::moveDataset) {
    dataset,
    showHandle ->
    NavItem(
      label = dataset.displayName.ifBlank { "Untitled" },
      icon = if (showHandle) Icons.Outlined.DragIndicator else icon,
      selected = state.section == SurveyEditorSection.Dataset(dataset.key),
      onClick = { state.select(SurveyEditorSection.Dataset(dataset.key)) },
      trailing = "${dataset.rows.size}",
      hasIssues = state.datasetIssues(dataset).isNotEmpty(),
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
  itemContent: @Composable (item: T, showHandle: Boolean) -> Unit,
) {
  val drag = remember { DragReorderState() }
  val pitchPx = with(LocalDensity.current) { (NavItemHeight + NavItemSpacing).toPx() }
  Column(verticalArrangement = Arrangement.spacedBy(NavItemSpacing)) {
    items.forEachIndexed { index, item ->
      val itemKey = keyOf(item)
      key(itemKey) {
        val isDragged = drag.draggingKey == itemKey
        val shift by
          animateFloatAsState(
            targetValue = drag.shiftFor(index) * pitchPx,
            animationSpec = if (drag.isDragging) spring() else snap(),
          )
        val hover = remember { MutableInteractionSource() }
        val isHovered by hover.collectIsHoveredAsState()
        Surface(
          color =
            if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.extraLarge,
          modifier =
            Modifier.zIndex(if (isDragged) 1f else 0f)
              .graphicsLayer {
                translationY = if (isDragged) drag.offset else shift
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                shape = RoundedCornerShape(28.dp)
                clip = isDragged
              }
              .hoverable(hover)
              .dragToReorder(
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
                      CustomAccessibilityAction("Move up") {
                        onMove(itemKey, index - 1)
                        true
                      }
                    )
                  }
                  if (index < items.lastIndex) {
                    add(
                      CustomAccessibilityAction("Move down") {
                        onMove(itemKey, index + 1)
                        true
                      }
                    )
                  }
                }
              },
        ) {
          itemContent(item, isHovered || isDragged)
        }
      }
    }
  }
}

@Composable
private fun NavHeading(text: String, addDescription: String, onAdd: () -> Unit) {
  Column {
    HorizontalDivider(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = addDescription) }
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

@Composable
private fun NavItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  trailing: String? = null,
  hasIssues: Boolean = false,
  nested: Boolean = false,
) {
  NavigationDrawerItem(
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    selected = selected,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
    badge = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasIssues) {
          Icon(
            Icons.Outlined.Warning,
            contentDescription = "Has issues",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium)
      }
    },
    colors =
      NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
    modifier = Modifier.height(44.dp).padding(start = if (nested) 12.dp else 0.dp),
  )
}

// ---------------------------------------------------------------------------------------------
// Survey details
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PaneScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Column(modifier = Modifier.widthIn(max = 760.dp)) {
      Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    content()
  }
}

@Composable
private fun SurveyDetailsPane(state: SurveyEditorState, localPlaces: List<SurveyPlaceItem>) {
  val details = state.details
  PaneScaffold(
    title = "Survey details",
    subtitle = "Basic information shown to data collectors when they open the survey.",
  ) {
    Column(
      modifier = Modifier.widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      OutlinedTextField(
        value = details.title,
        onValueChange = { v -> state.updateDetails { it.copy(title = v) } },
        label = { Text("Survey title") },
        singleLine = true,
        isError = details.title.isBlank(),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = details.description,
        onValueChange = { v -> state.updateDetails { it.copy(description = v) } },
        label = { Text("Description") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = details.surveyId,
        onValueChange = { v -> state.updateDetails { it.copy(surveyId = v.trim()) } },
        label = { Text("Survey ID") },
        singleLine = true,
        isError = !FormEditorValidator.isValidName(details.surveyId),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )
      LanguageSelectorSection(state = state)
      SurveyAreaSection(state = state, localPlaces = localPlaces)
    }

    SectionLabel("Contents")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      SummaryCard("Forms", state.forms.size) {
        state.forms.firstOrNull()?.let { state.select(SurveyEditorSection.Form(it.key)) }
      }
      SummaryCard("Map layers", state.mapLayers.size) {
        state.mapLayers.firstOrNull()?.let { state.select(SurveyEditorSection.Dataset(it.key)) }
      }
      SummaryCard("Data tables", state.dataTables.size) {
        state.dataTables.firstOrNull()?.let { state.select(SurveyEditorSection.Dataset(it.key)) }
      }
      SummaryCard("People", state.sharing.collaborators.size + 1) {
        state.select(SurveyEditorSection.Sharing)
      }
    }
  }
}

@Composable
private fun SummaryCard(label: String, count: Int, onClick: () -> Unit) {
  OutlinedCard(onClick = onClick, modifier = Modifier.width(160.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelectorSection(state: SurveyEditorState) {
  val details = state.details
  val supported = details.supportedLanguages
  val defaultLang = details.defaultLanguage
  var showPicker by remember { mutableStateOf(false) }

  if (showPicker) {
    LanguagePickerDialog(
      selectedCodes = supported.toSet(),
      onAddLanguage = { lang -> state.addSupportedLanguage(lang.code) },
      onDismiss = { showPicker = false },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Languages", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
      "Choose the languages in which forms and survey questions can be authored and collected. The default language is shown when a specific translation is not available.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Supported languages chips and Add button
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        "Supported languages",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        supported.forEach { code ->
          val lang = IsoLanguages.findByCode(code)
          val displayName = lang?.name ?: code
          val isDefault = code.equals(defaultLang, ignoreCase = true)
          InputChip(
            selected = isDefault,
            onClick = { state.setDefaultLanguage(code) },
            label = { Text(if (isDefault) "$displayName • Default" else displayName) },
            trailingIcon = {
              if (supported.size > 1) {
                IconButton(
                  onClick = { state.removeSupportedLanguage(code) },
                  modifier = Modifier.size(18.dp),
                ) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove $displayName",
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            },
          )
        }
        OutlinedButton(onClick = { showPicker = true }, shape = RoundedCornerShape(8.dp)) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Add language")
        }
      }
    }

    // Default language selector
    if (supported.isNotEmpty()) {
      val defaultLanguageObject = IsoLanguages.findByCode(defaultLang)
      val defaultLabel = defaultLanguageObject?.name ?: defaultLang.ifEmpty { "None selected" }
      Box(modifier = Modifier.widthIn(max = 380.dp)) {
        DropdownSelector(
          label = "Default language",
          selectedText = defaultLabel,
          options = supported,
          optionText = { code -> IsoLanguages.findByCode(code)?.name ?: code },
          onSelect = { pickedCode -> state.setDefaultLanguage(pickedCode) },
        )
      }
    }
  }
}

@Composable
private fun SurveyAreaSection(state: SurveyEditorState, localPlaces: List<SurveyPlaceItem>) {
  val area = state.details.surveyArea
  var showSearchDialog by remember { mutableStateOf(false) }

  if (showSearchDialog) {
    SurveyAreaPickerDialog(
      surveyId = state.details.surveyId,
      surveyLocationLabel = state.details.title.ifBlank { "Survey" },
      surveyCenter = area?.center ?: LatLng(-0.4198, 36.9512),
      currentAreaName = area?.name,
      localPlaces = localPlaces,
      onSelectArea = { newArea -> state.setSurveyArea(newArea) },
      onDismiss = { showSearchDialog = false },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          "Survey area",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          "Geographic coverage and boundary limits for data collection in this survey.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (area != null) {
      OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Small static, non-pan/zoomable map thumbnail preview
          SurveyAreaThumbnail(
            area = area,
            modifier = Modifier.size(width = 160.dp, height = 120.dp),
          )

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = area.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text =
                "Center: ${formatFixed(area.center.lat, 4)}°, ${formatFixed(area.center.lng, 4)}° • ${area.boundaries.size} boundary vertices",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(top = 4.dp),
            ) {
              OutlinedButton(
                onClick = { showSearchDialog = true },
                shape = RoundedCornerShape(8.dp),
              ) {
                Icon(
                  Icons.Outlined.Search,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Change area")
              }
              OutlinedButton(
                onClick = { state.setSurveyArea(null) },
                shape = RoundedCornerShape(8.dp),
              ) {
                Icon(
                  Icons.Outlined.Delete,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Remove")
              }
            }
          }
        }
      }
    } else {
      OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(width = 160.dp, height = 100.dp)
                .background(
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Outlined.Map,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(36.dp),
            )
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "No survey area set",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "Search for a location, town, region, or enter coordinates to set the survey boundary.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = { showSearchDialog = true },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.padding(top = 4.dp),
            ) {
              Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text("Search and set area")
            }
          }
        }
      }
    }
  }
}

/**
 * Small, static (not pan-able or zoom-able) map thumbnail showing a satellite basemap with the
 * survey area's boundary polygon and center point on top.
 */
@Composable
private fun SurveyAreaThumbnail(area: SurveyArea, modifier: Modifier = Modifier) {
  val boundaryColor = MaterialTheme.colorScheme.primary
  val cameraState =
    remember(area) { MapCameraState(CameraPosition(area.center.toMapLatLng(), area.zoom)) }
  val viewport = cameraState.viewportSize
  val viewportReady = viewport.width > 4.dp && viewport.height > 4.dp
  LaunchedEffect(area, viewportReady) {
    if (!viewportReady) return@LaunchedEffect
    val pts = (area.boundaries.ifEmpty { listOf(area.center) }).map { it.toMapLatLng() }
    val lngSpan = pts.maxOf { it.longitude } - pts.minOf { it.longitude }
    // Bounds spanning > 180° longitude (e.g. across the antimeridian) would center on the wrong
    // side of the world, so anchor on the area's own center and zoom instead.
    val zoom =
      if (lngSpan >= 180.0) {
        area.zoom
      } else {
        val padding = MapInsets(16.dp, 16.dp, 16.dp, 16.dp)
        cameraState.fitBounds(LngLatBounds.of(pts), padding, durationMs = 0).zoom
      }
    // Keep the fitted zoom but center directly over the surveyed place.
    cameraState.move(CameraPosition(area.center.toMapLatLng(), zoom))
  }
  val content = remember(area, boundaryColor) { surveyAreaThumbnailContent(area, boundaryColor) }

  Surface(
    modifier = modifier.clip(RoundedCornerShape(8.dp)),
    shape = RoundedCornerShape(8.dp),
    color = Color.Transparent,
    shadowElevation = 1.dp,
  ) {
    GroundMap(
      content = content,
      cameraState = cameraState,
      onEvent = {},
      modifier = Modifier.fillMaxSize(),
      gesturesEnabled = false,
    )
  }
}

/** The survey area's boundary (fill, outline, vertices) and center over a satellite basemap. */
internal fun surveyAreaThumbnailContent(area: SurveyArea, boundaryColor: Color): MapContent {
  val src = "survey-area"
  val boundary = area.boundaries.map { it.toMapLatLng() }
  val features = buildList {
    if (boundary.size >= 3) {
      add(MapFeature("boundary", Geometry.Polygon(listOf(boundary))))
      boundary.forEachIndexed { i, p ->
        add(MapFeature("vertex-$i", Geometry.Point(p), mapOf("kind" to "vertex")))
      }
    }
    add(MapFeature("center", Geometry.Point(area.center.toMapLatLng()), mapOf("kind" to "center")))
  }
  return MapContent(
    basemap = SurveyBasemaps.Satellite,
    sources = listOf(GeoJsonSource(src, features)),
    layers =
      listOf(
        MapLayer.Fill(
          id = "survey-area-fill",
          sourceId = src,
          color = StyleValue.Constant(boundaryColor),
          opacity = StyleValue.Constant(0.28f),
        ),
        MapLayer.Line(
          id = "survey-area-line",
          sourceId = src,
          filter = FeatureFilter.GeometryTypeIs(GeometryType.POLYGON),
          color = StyleValue.Constant(boundaryColor),
          width = StyleValue.Constant(2.5.dp),
        ),
        MapLayer.Circle(
          id = "survey-area-vertices",
          sourceId = src,
          filter = FeatureFilter.Equals("kind", "vertex"),
          color = StyleValue.Constant(Color.White),
          radius = StyleValue.Constant(3.5.dp),
          strokeColor = StyleValue.Constant(boundaryColor),
          strokeWidth = 1.5.dp,
        ),
        MapLayer.Circle(
          id = "survey-area-center",
          sourceId = src,
          filter = FeatureFilter.Equals("kind", "center"),
          color = StyleValue.Constant(boundaryColor),
          radius = StyleValue.Constant(4.dp),
          strokeColor = StyleValue.Constant(Color.White),
          strokeWidth = 1.dp,
        ),
      ),
  )
}

/**
 * Dialog allowing the user to search the Mapbox Places API (or enter raw GPS coordinates) to define
 * the survey area boundary.
 */
@Composable
private fun SurveyAreaPickerDialog(
  surveyId: String,
  surveyLocationLabel: String,
  surveyCenter: LatLng,
  currentAreaName: String?,
  localPlaces: List<SurveyPlaceItem>,
  onSelectArea: (SurveyArea) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var remotePlaces by remember { mutableStateOf<List<SurveyPlaceItem>>(emptyList()) }
  val placesDataSource = remember { MapboxPlacesDataSource() }

  // Query live Mapbox Places API when user types a query (with debounce)
  LaunchedEffect(searchQuery, surveyId) {
    val q = searchQuery.trim()
    if (q.isBlank()) {
      remotePlaces = emptyList()
      isSearching = false
      return@LaunchedEffect
    }
    isSearching = true
    delay(250) // Debounce rapid keystrokes
    placesDataSource.searchPlaces(
      surveyId = surveyId,
      query = q,
      isAirplaneMode = false,
      defaultRegionSubtitle = surveyLocationLabel,
      centerLongitude = surveyCenter.lng,
      centerLatitude = surveyCenter.lat,
    ) { results ->
      remotePlaces = results
      isSearching = false
    }
  }

  val matchingPlaces =
    remember(searchQuery, remotePlaces, localPlaces) {
      val q = searchQuery.trim()
      val directCoords = parsePlaceCoordinates(q)
      val customPlace =
        if (directCoords != null) {
          val (lat, lng) = directCoords
          SurveyPlaceItem(
            id = "custom-coords",
            name = "GPS: ${formatFixed(lat, 4)}°, ${formatFixed(lng, 4)}°",
            categoryLabel = "Custom Coordinates",
            regionSubtitle = "Direct coordinates input",
            coordinatesLabel = "${formatFixed(lat, 4)}, ${formatFixed(lng, 4)}",
            normalizedX = 0.5f,
            normalizedY = 0.5f,
            latitude = lat,
            longitude = lng,
            sourceLabel = "Coordinates",
          )
        } else {
          null
        }

      if (q.isEmpty()) {
        localPlaces
      } else {
        // Prioritize remote Mapbox Places API results, merged with matching local gazetteer entries
        val combined = LinkedHashMap<String, SurveyPlaceItem>()
        if (customPlace != null) {
          combined[customPlace.id] = customPlace
        }
        for (apiPlace in remotePlaces) {
          combined[apiPlace.id] = apiPlace
        }
        val filteredLocal = localPlaces.filter { place ->
          place.name.contains(q, ignoreCase = true) ||
            place.categoryLabel.contains(q, ignoreCase = true) ||
            place.regionSubtitle.contains(q, ignoreCase = true) ||
            place.coordinatesLabel.contains(q, ignoreCase = true)
        }
        for (localPlace in filteredLocal) {
          if (combined.values.none { it.name.equals(localPlace.name, ignoreCase = true) }) {
            combined[localPlace.id] = localPlace
          }
        }
        combined.values.toList()
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          Icons.Outlined.Map,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text("Select Survey Area")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          label = { Text("Search places or enter coordinates") },
          placeholder = { Text("e.g. Nairobi, Othaya, Chinga Dam, or -0.419, 36.950") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            if (isSearching) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (searchQuery.isBlank()) "Suggested survey locations:"
              else "${matchingPlaces.size} locations found:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          if (matchingPlaces.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                if (isSearching) "Searching places..." else "No places matching “$searchQuery”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(matchingPlaces, key = { it.id }) { place ->
                val isSelected =
                  currentAreaName != null && place.name.equals(currentAreaName, ignoreCase = true)
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable(enabled = !isSelected) {
                        val area = placeToSurveyArea(place)
                        onSelectArea(area)
                        onDismiss()
                      }
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint =
                      if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = place.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                      )
                      val cleanSource =
                        if (place.sourceLabel.equals("Places API", ignoreCase = true)) "Online"
                        else place.sourceLabel
                      if (cleanSource.isNotBlank()) {
                        Text(
                          text = "• $cleanSource",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.primary,
                        )
                      }
                    }
                    Text(
                      text = "${place.categoryLabel} • ${place.regionSubtitle}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = place.coordinatesLabel,
                      style =
                        MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                  }
                  if (isSelected) {
                    GroundTonalBadge(text = "Selected", tone = GroundBadgeTone.PRIMARY)
                  } else {
                    OutlinedButton(
                      onClick = {
                        val area = placeToSurveyArea(place)
                        onSelectArea(area)
                        onDismiss()
                      },
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Text("Select")
                    }
                  }
                }
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** Converts a [SurveyPlaceItem] to a [SurveyArea] with appropriate polygon boundary vertices. */
private fun placeToSurveyArea(place: SurveyPlaceItem): SurveyArea {
  val lat = place.latitude
  val lng = place.longitude
  val boundaries =
    if (
      place.bboxMinLat != null &&
        place.bboxMinLng != null &&
        place.bboxMaxLat != null &&
        place.bboxMaxLng != null
    ) {
      val minLng = place.bboxMinLng
      val maxLng = place.bboxMaxLng
      val minLat = place.bboxMinLat
      val maxLat = place.bboxMaxLat
      // If a country bounding box spans global bounds [-180, 180], restrict the boundary
      // polygon to reasonable geographic extent centered around the place center
      // so it does not wrap the whole globe into an invalid full-world box.
      if (maxLng - minLng >= 350.0) {
        val spanLat = (maxLat - minLat).coerceAtLeast(10.0)
        val halfLng = (spanLat * 1.4).coerceAtMost(35.0)
        listOf(
          LatLng(maxLat, lng - halfLng),
          LatLng(maxLat, lng + halfLng),
          LatLng(minLat, lng + halfLng),
          LatLng(minLat, lng - halfLng),
        )
      } else {
        listOf(
          LatLng(maxLat, minLng),
          LatLng(maxLat, maxLng),
          LatLng(minLat, maxLng),
          LatLng(minLat, minLng),
        )
      }
    } else {
      val delta = 0.015
      listOf(
        LatLng(lat + delta, lng - delta),
        LatLng(lat + delta, lng + delta),
        LatLng(lat - delta, lng + delta),
        LatLng(lat - delta, lng - delta),
      )
    }
  return SurveyArea(
    name = place.name,
    boundaries = boundaries,
    center = LatLng(lat, lng),
    zoom = place.targetZoom.toDouble(),
    sourceLabel = place.sourceLabel,
  )
}

/**
 * Searchable dialog allowing the user to search the full catalog of ISO 639-3 languages by code or
 * name and add them to the survey.
 */
@Composable
private fun LanguagePickerDialog(
  selectedCodes: Set<String>,
  onAddLanguage: (IsoLanguage) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val searchResults = remember(searchQuery) { IsoLanguages.search(searchQuery, limit = 40) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          Icons.Outlined.Language,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text("Select Language")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 500.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          label = { Text("Search by language name") },
          placeholder = { Text("e.g. Swahili, English, Spanish, Amharic...") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Text(
          text =
            if (searchQuery.isBlank()) "Popular languages & catalog preview:"
            else "${searchResults.size} matches found:",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          if (searchResults.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                "No languages matching “$searchQuery”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(searchResults, key = { it.id }) { lang ->
                val isAlreadySelected = selectedCodes.any {
                  it.equals(lang.code, ignoreCase = true) || it.equals(lang.id, ignoreCase = true)
                }
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable(enabled = !isAlreadySelected) {
                        onAddLanguage(lang)
                        onDismiss()
                      }
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = lang.name,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                    )
                  }
                  if (isAlreadySelected) {
                    GroundTonalBadge(text = "Added", tone = GroundBadgeTone.PRIMARY)
                  } else {
                    OutlinedButton(
                      onClick = {
                        onAddLanguage(lang)
                        onDismiss()
                      },
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Text("Select")
                    }
                  }
                }
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

// ---------------------------------------------------------------------------------------------
// Sharing
// ---------------------------------------------------------------------------------------------

@Composable
private fun SharingPane(state: SurveyEditorState) {
  val sharing = state.sharing
  var acceptingEmail by remember { mutableStateOf<String?>(null) }
  sharing.collaborators
    .firstOrNull { it.email == acceptingEmail && it.status == InvitationStatus.PENDING }
    ?.let { invitee ->
      AcceptInviteDialog(
        surveyTitle = state.details.title,
        invitee = invitee,
        onAccept = { name, photo -> state.acceptInvite(invitee.email, name, photo) },
        onDismiss = { acceptingEmail = null },
      )
    }
  PaneScaffold(
    title = "Sharing",
    subtitle = "Choose who can open this survey and what data collectors can see.",
  ) {
    Column(
      modifier = Modifier.widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SectionLabel("People with access")
          InviteRow(state)
          PersonRow(
            displayName = sharing.ownerProfile?.displayName ?: sharing.ownerEmail,
            email = sharing.ownerEmail,
            photoUrl = sharing.ownerProfile?.photoUrl,
            detail = "Survey owner",
          ) {
            Text(
              "Owner",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 10.dp, end = 12.dp),
            )
          }
          sharing.collaborators.forEach { person ->
            key(person.email) {
              val pending = person.status == InvitationStatus.PENDING
              PersonRow(
                displayName = person.displayName,
                email = person.email,
                photoUrl = person.profile?.photoUrl,
                pending = pending,
                detail =
                  when {
                    pending -> "Waiting for them to accept their invite link"
                    person.profile?.cachedOn.isNullOrBlank() -> "Joined just now"
                    else -> "Joined ${person.profile?.cachedOn}"
                  },
                footer =
                  person.inviteToken
                    ?.takeIf { pending }
                    ?.let { token ->
                      {
                        PendingInviteLinkRow(
                          token = token,
                          onResetLink = { state.resetInviteLink(person.email) },
                          onOpenAsInvitee = { acceptingEmail = person.email },
                        )
                      }
                    },
              ) {
                Box(modifier = Modifier.width(190.dp)) {
                  DropdownSelector(
                    label = "Role",
                    selectedText = person.role.label,
                    options = CollaboratorRole.entries,
                    optionText = { it.label },
                    onSelect = { state.setCollaboratorRole(person.email, it) },
                  )
                }
                IconButton(onClick = { state.removeCollaborator(person.email) }) {
                  Icon(Icons.Outlined.Close, contentDescription = "Remove ${person.displayName}")
                }
              }
            }
          }
          Text(
            "Names and photos come from each person's account. Ground saves them when they " +
              "accept their invite link, and refreshes them when they sign in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SectionLabel("General access")
          SharingPolicy.entries.forEach { policy ->
            RadioOption(
              title = policy.label,
              description = policy.description,
              selected = sharing.policy == policy,
              onSelect = { state.updateSharing { it.copy(policy = policy) } },
            )
          }
        }
      }

      ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SectionLabel("What data collectors can see")
          PeerDataVisibility.entries.forEach { visibility ->
            RadioOption(
              title = visibility.label,
              description = visibility.description,
              selected = sharing.peerDataVisibility == visibility,
              onSelect = { state.updateSharing { it.copy(peerDataVisibility = visibility) } },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InviteRow(state: SurveyEditorState) {
  var email by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(CollaboratorRole.DATA_COLLECTOR) }
  var error by remember { mutableStateOf<String?>(null) }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    OutlinedTextField(
      value = email,
      onValueChange = {
        email = it
        error = null
      },
      label = { Text("Add people by email") },
      singleLine = true,
      isError = error != null,
      supportingText = error?.let { { Text(it) } },
      modifier = Modifier.weight(1f),
    )
    Box(modifier = Modifier.width(190.dp).padding(top = 6.dp)) {
      DropdownSelector(
        label = "Role",
        selectedText = role.label,
        options = CollaboratorRole.entries,
        optionText = { it.label },
        onSelect = { role = it },
      )
    }
    Button(
      onClick = {
        error = state.inviteCollaborator(email, role)
        if (error == null) email = ""
      },
      enabled = email.isNotBlank(),
      modifier = Modifier.padding(top = 10.dp),
    ) {
      Text("Invite")
    }
  }
}

@Composable
private fun RadioOption(
  title: String,
  description: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(Modifier.width(12.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
