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
package org.groundplatform.v2.web.ui.formeditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key as eventKey
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.groundplatform.v2.app.domain.model.DeviceFormFactor
import org.groundplatform.v2.app.domain.model.DeviceOrientation
import org.groundplatform.v2.app.domain.model.LibraryConcept
import org.groundplatform.v2.app.domain.model.editor.ChoiceColors
import org.groundplatform.v2.app.domain.model.editor.ChoiceSource
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.DateRule
import org.groundplatform.v2.app.domain.model.editor.EditorChoice
import org.groundplatform.v2.app.domain.model.editor.EditorChoiceImage
import org.groundplatform.v2.app.domain.model.editor.EditorDataset
import org.groundplatform.v2.app.domain.model.editor.EditorForm
import org.groundplatform.v2.app.domain.model.editor.EditorQuestion
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.EditorRelevance
import org.groundplatform.v2.app.domain.model.editor.EditorSection
import org.groundplatform.v2.app.domain.model.editor.EditorValidation
import org.groundplatform.v2.app.domain.model.editor.FlowEdge
import org.groundplatform.v2.app.domain.model.editor.FlowEdgeKind
import org.groundplatform.v2.app.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.app.domain.model.editor.FormFlowGraph
import org.groundplatform.v2.app.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.app.domain.model.editor.GeometryCapture
import org.groundplatform.v2.app.domain.model.editor.MediaSource
import org.groundplatform.v2.app.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.app.domain.model.editor.SaveToMode
import org.groundplatform.v2.app.domain.model.editor.SaveToRules
import org.groundplatform.v2.app.domain.model.editor.TextPattern
import org.groundplatform.v2.app.domain.model.editor.ValidationKind
import org.groundplatform.v2.app.domain.model.editor.ValidationRules
import org.groundplatform.v2.app.domain.model.editor.friendlyDate
import org.groundplatform.v2.app.domain.model.editor.isoDateToUtcMillis
import org.groundplatform.v2.app.domain.model.editor.slugify
import org.groundplatform.v2.app.domain.model.editor.utcMillisToIsoDate
import org.groundplatform.v2.app.domain.model.hasGeometry
import org.groundplatform.v2.app.domain.usecase.ChoiceListImporter
import org.groundplatform.v2.app.ui.common.DevicePreviewFrame
import org.groundplatform.v2.app.ui.common.DragAxis
import org.groundplatform.v2.app.ui.common.GroundFilterChip
import org.groundplatform.v2.app.ui.common.PlatformPickResult
import org.groundplatform.v2.app.ui.common.awaitDragPastSlop
import org.groundplatform.v2.app.ui.common.dragSlopFor
import org.groundplatform.v2.app.ui.common.isPlatformMediaPickerAvailable
import org.groundplatform.v2.app.ui.common.openPlatformMediaPicker
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.web.ui.common.DropdownFieldClickTarget
import org.groundplatform.v2.web.ui.common.GrabPointerIcon
import org.groundplatform.v2.web.ui.common.GrabbingPointerIcon
import org.groundplatform.v2.web.ui.common.TextFilePickResult
import org.groundplatform.v2.web.ui.common.downloadTextFile
import org.groundplatform.v2.web.ui.common.openPlatformTextFilePicker
import org.groundplatform.v2.web.ui.common.showPlatformGrabCursor
import org.groundplatform.v2.web.ui.common.showPlatformGrabbingCursor
import org.groundplatform.v2.web.ui.dashboard.SidePanelSeparator
import org.groundplatform.v2.web.ui.dashboard.SidePanelSeparatorWidth
import org.groundplatform.v2.web.ui.state.FormEditorUiState
import org.groundplatform.v2.web.ui.surveyeditor.IsoLanguages
import org.groundplatform.v2.web.ui.viewmodel.FormEditorActions
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private val CardWidth = 184.dp
private val CardHeight = 312.dp
private val StartButtonWidth = 148.dp
private val StartButtonHeight = 48.dp
private val TerminalWidth = 104.dp
private val TerminalHeight = 56.dp
private val SlotGap = 72.dp
private val CanvasPadding = 32.dp
private val ArcInset = 22.dp

private fun arcRise(span: Int): Dp = 36.dp + 24.dp * (span - 2).coerceIn(0, 6)

/**
 * Visual Form editor page: a flow canvas previewing every screen of the Form, connected by arrows
 * for each potential transition implied by display logic, plus a properties panel for the selected
 * screen and an in-browser preview that runs the generated XForms through [MobileFormRunner].
 */
@Composable
fun FormEditorPage(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
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
) {
  val linkedDataset = uiState.datasets.firstOrNull { it.isLinkedToThisForm }
  Box(
    modifier =
      modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val primaryModifier = event.isCtrlPressed || event.isMetaPressed
        if (!primaryModifier) return@onPreviewKeyEvent false
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
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      FormEditorToolbar(
        uiState = uiState,
        actions = actions,
        onCreateDataset =
          onCreateDataset.takeIf {
            uiState.form.saveTo.mode == SaveToMode.CREATE && linkedDataset == null
          },
        onDelete = onDelete,
      )
      ImportMatchesCard(
        uiState = uiState,
        actions = actions,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
      )
      Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
          FloatingAddQuestionButton(
            onAdd = actions::addQuestion,
            onAddSection = { _ ->
              val secId = actions.addSection("Section", repeatable = false)
              actions.selectSection(secId)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp),
          )
        }
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
          onCreateDataset =
            onCreateDataset.takeIf {
              uiState.form.saveTo.mode == SaveToMode.CREATE && linkedDataset == null
            },
          onUnlinkDataset = onUnlinkDataset,
          onCreateFormForDataset = onCreateFormForDataset,
          onOpenForm = onOpenForm,
          onCreateChoiceDataset = onCreateChoiceDataset,
          modifier = Modifier.width(uiState.sidePanelWidthDp.dp).fillMaxHeight(),
        )
      }
    }

    if (uiState.previewController != null || uiState.previewError != null) {
      FormPreviewOverlay(uiState, actions, isDarkTheme)
    }
    ConceptSearchDialog(uiState, actions)
  }
}

@Composable
private fun FloatingAddQuestionButton(
  onAdd: (EditorQuestionType, Int?) -> Unit,
  onAddSection: ((Int?) -> Unit)? = null,
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
      Text("Add question")
    }
    AddQuestionMenu(
      expanded = addingQuestion,
      onDismiss = { addingQuestion = false },
      onAdd = onAdd,
      onAddSection = onAddSection,
    )
  }
}

@Composable
private fun FormEditorToolbar(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onCreateDataset: (() -> Unit)?,
  onDelete: (() -> Unit)?,
) {
  var overflowExpanded by remember { mutableStateOf(false) }
  var savingTemplate by remember { mutableStateOf(false) }
  var confirmingDeleteForm by remember { mutableStateOf(false) }
  if (savingTemplate) {
    SaveAsTemplateDialog(uiState, actions, onDismiss = { savingTemplate = false })
  }
  if (confirmingDeleteForm && onDelete != null) {
    AlertDialog(
      onDismissRequest = { confirmingDeleteForm = false },
      title = { Text("Delete form?") },
      text = {
        Text("Delete \"${uiState.form.title.ifBlank { "Untitled form" }}\"? This can't be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingDeleteForm = false
            onDelete()
          }
        ) {
          Text("Delete form", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { confirmingDeleteForm = false }) { Text("Cancel") } },
    )
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 1.dp,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val isCompact = maxWidth < 960.dp
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.widthIn(max = if (isCompact) 200.dp else 300.dp)) {
          Text(
            text = uiState.form.title.ifBlank { "Untitled form" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Design questions, display logic, and flows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = actions::undo, enabled = uiState.canUndo) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
          }
          IconButton(onClick = actions::redo, enabled = uiState.canRedo) {
            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
          }
        }
        if (!isCompact) {
          FilledTonalButton(onClick = actions::selectFormSettings) {
            Icon(
              Icons.Outlined.Settings,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Form settings", softWrap = false)
          }
        }
        Spacer(Modifier.weight(1f))
        if (uiState.supportedLanguages.size > 1) {
          FormLanguageSelector(uiState, actions)
        }
        // The linked map layer or data table is shown in Form settings, not here.
        if (!isCompact) {
          if (onCreateDataset != null) {
            OutlinedButton(onClick = onCreateDataset) {
              Icon(
                Icons.Outlined.Layers,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                if (uiState.form.hasGeometry) "Create map layer" else "Create data table",
                softWrap = false,
              )
            }
          }
        }
        PreviewTargetToggle(uiState, actions)
        Button(onClick = actions::startPreview) {
          Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Preview", softWrap = false)
        }
        Box {
          IconButton(onClick = { overflowExpanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
          }
          DropdownMenu(
            expanded = overflowExpanded,
            onDismissRequest = { overflowExpanded = false },
          ) {
            if (isCompact) {
              DropdownMenuItem(
                text = { Text("Form settings") },
                leadingIcon = {
                  Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                },
                onClick = {
                  overflowExpanded = false
                  actions.selectFormSettings()
                },
              )
              if (onCreateDataset != null) {
                DropdownMenuItem(
                  text = {
                    Text(if (uiState.form.hasGeometry) "Create map layer" else "Create data table")
                  },
                  leadingIcon = {
                    Icon(
                      Icons.Outlined.Layers,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  onClick = {
                    overflowExpanded = false
                    onCreateDataset()
                  },
                )
              }
            }
            DropdownMenuItem(
              text = { Text("Download XForms XML") },
              leadingIcon = {
                Icon(
                  Icons.Outlined.Description,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              onClick = {
                overflowExpanded = false
                val baseName =
                  slugify(uiState.form.title.ifBlank { uiState.form.formId }, fallback = "form")
                downloadTextFile(
                  fileName = "$baseName.xml",
                  mimeType = "application/xml",
                  content = uiState.xformsXml,
                )
              },
            )
            val unavailable = uiState.saveAsTemplateUnavailableReason
            DropdownMenuItem(
              text = {
                Column {
                  Text("Save as template…")
                  if (unavailable != null) {
                    Text(
                      unavailable,
                      style = MaterialTheme.typography.bodySmall,
                      modifier = Modifier.widthIn(max = 260.dp),
                    )
                  }
                }
              },
              leadingIcon = {
                Icon(
                  Icons.Outlined.BookmarkAdd,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              enabled = unavailable == null,
              onClick = {
                overflowExpanded = false
                savingTemplate = true
              },
            )
            if (onDelete != null) {
              DropdownMenuItem(
                text = { Text("Delete form", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                  Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                  )
                },
                onClick = {
                  overflowExpanded = false
                  confirmingDeleteForm = true
                },
              )
            }
          }
        }
      }
    }
  }
}

internal fun languageDisplayName(code: String): String = IsoLanguages.findByCode(code)?.name ?: code

/**
 * Dropdown selector in the preview header that lets survey designers switch the active editing and
 * preview language across all languages configured on the survey.
 */
@Composable
internal fun FormLanguageSelector(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val activeCode = uiState.activeLanguage
  val activeName = languageDisplayName(activeCode)
  Box(modifier = modifier) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier =
        Modifier.height(36.dp).semantics { contentDescription = "Form language: $activeName" },
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
      Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text(activeName, style = MaterialTheme.typography.labelMedium, softWrap = false)
      Spacer(Modifier.width(4.dp))
      Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      uiState.supportedLanguages.forEach { code ->
        val name = languageDisplayName(code)
        val isDefault = code == uiState.defaultLanguage
        val isSelected = code == activeCode
        DropdownMenuItem(
          text = { Text(if (isDefault) "$name • Default" else name) },
          leadingIcon = {
            if (isSelected) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            } else {
              Spacer(Modifier.size(18.dp))
            }
          },
          onClick = {
            expanded = false
            actions.selectLanguage(code)
          },
          modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
      }
    }
  }
}

/** Standard icon representing each [EditorQuestionType]. */
internal fun questionTypeIcon(type: EditorQuestionType): ImageVector =
  when (type) {
    EditorQuestionType.TEXT -> Icons.Outlined.Edit
    EditorQuestionType.LONG_TEXT -> Icons.Outlined.Description
    EditorQuestionType.NUMBER -> Icons.AutoMirrored.Outlined.List
    EditorQuestionType.SELECT_ONE -> Icons.Outlined.CheckCircle
    EditorQuestionType.SELECT_MULTIPLE -> Icons.Outlined.Check
    EditorQuestionType.DATE -> Icons.Outlined.DateRange
    EditorQuestionType.LOCATION -> Icons.Outlined.LocationOn
    EditorQuestionType.LINE -> Icons.Outlined.Timeline
    EditorQuestionType.POLYGON -> Icons.Outlined.Pentagon
    EditorQuestionType.PHOTO -> Icons.Outlined.AccountCircle
    EditorQuestionType.VIDEO -> Icons.Outlined.Videocam
    EditorQuestionType.AUDIO -> Icons.Outlined.Mic
    EditorQuestionType.NOTE -> Icons.Outlined.Info
  }

/** Dropdown menu listing all question types with their icons for insertion at [atIndex]. */
@Composable
internal fun AddQuestionMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  atIndex: Int? = null,
  onAddSection: ((Int?) -> Unit)? = null,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    EditorQuestionType.entries.forEach { type ->
      DropdownMenuItem(
        text = { Text(type.label) },
        leadingIcon = {
          Icon(
            questionTypeIcon(type),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        onClick = {
          onAdd(type, atIndex)
          onDismiss()
        },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
      )
    }
    if (onAddSection != null) {
      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
      DropdownMenuItem(
        text = { Text("Section") },
        leadingIcon = {
          Icon(
            Icons.Outlined.Layers,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        },
        onClick = {
          onAddSection(atIndex)
          onDismiss()
        },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Flow canvas
// ---------------------------------------------------------------------------------------------

/** Precomputed Dp geometry for every flow slot (Start, questions, Ghost card, End). */
class FlowLayout(
  val questionCount: Int,
  val edges: List<FlowEdge>,
  val form: EditorForm? = null,
) {
  val slotCount = questionCount + 2
  val endSlot = questionCount + 1
  private val maxRise: Dp = edges.filter { it.span > 1 }.maxOfOrNull { arcRise(it.span) } ?: 0.dp
  val cardTop: Dp = CanvasPadding + maxRise + 8.dp
  val midY: Dp = cardTop + CardHeight / 2

  fun width(slot: Int): Dp =
    when (slot) {
      0 -> StartButtonWidth
      endSlot -> TerminalWidth
      else -> CardWidth
    }

  fun height(slot: Int): Dp =
    when (slot) {
      0 -> StartButtonHeight
      endSlot -> TerminalHeight
      else -> CardHeight
    }

  private val slotCol = mutableMapOf<Int, Int>()
  private val slotRow = mutableMapOf<Int, Int>()
  private val sectionGhostCol = mutableMapOf<String, Int>()
  private val sectionGhostRow = mutableMapOf<String, Int>()

  val ghostCol: Int
  val endCol: Int
  val maxRow: Int

  init {
    slotCol[0] = 0
    slotRow[0] = 0

    var trunkCol = 0
    val branchRows = mutableMapOf<String, Int>()
    var nextBranchRow = 1
    val lastColInRow = mutableMapOf<Int, Int>()
    val seenSections = mutableSetOf<String>()

    for (qIdx in 0 until questionCount) {
      val slot = qIdx + 1
      val question = form?.questions?.getOrNull(qIdx)
      val sectionId = question?.sectionId.orEmpty()
      val section = question?.let { form?.section(it.sectionId) }
      val relevance = question?.relevance ?: section?.relevance

      if (sectionId.isNotBlank()) seenSections += sectionId

      if (relevance == null || form == null) {
        // Main trunk: row 0, advance column
        trunkCol++
        slotCol[slot] = trunkCol
        slotRow[slot] = 0
        lastColInRow[0] = trunkCol
      } else {
        // Conditional branch!
        val sourceKey = relevance.sourceQuestionKey
        val sourceIndex = form.indexOf(sourceKey)
        val parentSlot = if (sourceIndex >= 0) FormFlowGraph.questionSlot(sourceIndex) else 0
        val parentCol = slotCol[parentSlot] ?: 0

        val branchKey = "${sourceKey}_${relevance.operator}_${relevance.value}"
        val branchRow = branchRows.getOrPut(branchKey) { nextBranchRow++ }
        val prevColOnRow = lastColInRow[branchRow] ?: parentCol
        val col = maxOf(parentCol + 1, prevColOnRow + 1)
        slotCol[slot] = col
        slotRow[slot] = branchRow
        lastColInRow[branchRow] = col
        trunkCol = maxOf(trunkCol, col)
      }

      if (sectionId.isNotBlank()) {
        val nextQ = form?.questions?.getOrNull(qIdx + 1)
        if (nextQ == null || nextQ.sectionId != sectionId) {
          val currentRow = slotRow[slot] ?: 0
          if (currentRow == 0) {
            trunkCol++
            sectionGhostCol[sectionId] = trunkCol
            sectionGhostRow[sectionId] = 0
            lastColInRow[0] = trunkCol
          } else {
            val prevCol = lastColInRow[currentRow] ?: trunkCol
            val gCol = prevCol + 1
            sectionGhostCol[sectionId] = gCol
            sectionGhostRow[sectionId] = currentRow
            lastColInRow[currentRow] = gCol
            trunkCol = maxOf(trunkCol, gCol)
          }
        }
      }
    }

    form?.sections?.forEach { sec ->
      if (sec.id !in seenSections) {
        trunkCol++
        sectionGhostCol[sec.id] = trunkCol
        sectionGhostRow[sec.id] = 0
        lastColInRow[0] = trunkCol
      }
    }

    val maxQuestionsCol =
      maxOf(
        slotCol.values.maxOrNull() ?: trunkCol,
        sectionGhostCol.values.maxOrNull() ?: trunkCol,
      )
    ghostCol = maxOf(trunkCol + 1, maxQuestionsCol + 1)
    endCol = ghostCol + 1

    slotCol[endSlot] = endCol
    slotRow[endSlot] = 0

    maxRow =
      maxOf(
        slotRow.values.maxOrNull() ?: 0,
        sectionGhostRow.values.maxOrNull() ?: 0,
      )
  }

  fun hasSectionGhost(sectionId: String): Boolean = sectionGhostCol.containsKey(sectionId)

  fun sectionGhostX(sectionId: String): Dp = colX(sectionGhostCol[sectionId] ?: 0)

  fun sectionGhostY(sectionId: String): Dp = rowY(sectionGhostRow[sectionId] ?: 0)

  fun slotCol(slot: Int): Int = slotCol[slot] ?: 0

  fun slotRow(slot: Int): Int = slotRow[slot] ?: 0

  private fun colX(col: Int): Dp =
    if (col == 0) {
      CanvasPadding
    } else {
      CanvasPadding + StartButtonWidth + SlotGap + (CardWidth + SlotGap) * (col - 1)
    }

  val BranchGap: Dp = 48.dp

  private fun rowY(row: Int): Dp = cardTop + (CardHeight + BranchGap) * row

  fun slotX(slot: Int): Dp = colX(slotCol(slot))

  fun slotY(slot: Int): Dp = rowY(slotRow(slot)) + (CardHeight - height(slot)) / 2

  fun x(slot: Int): Dp = slotX(slot)

  fun top(slot: Int): Dp = slotY(slot)

  fun slotPosition(slot: Int): DpOffset = DpOffset(slotX(slot), slotY(slot))

  val ghostX: Dp = colX(ghostCol)
  val ghostY: Dp = rowY(0)

  val totalWidth: Dp = colX(endCol) + TerminalWidth + CanvasPadding
  val totalHeight: Dp = rowY(maxRow) + CardHeight + CanvasPadding + 32.dp
}

@Composable
private fun FlowCanvasPanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  val edges = uiState.flowEdges
  val horizontalScroll = rememberScrollState()
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      PlatformDisabledBanner(uiState, actions)
      UnavailablePreviewArea(uiState, actions, Modifier.weight(1f).fillMaxWidth()) {
        FlowCanvas(uiState, actions, edges, horizontalScroll, Modifier.fillMaxSize())
      }
      FlowHorizontalScrollBar(
        scrollState = horizontalScroll,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      )
    }
  }
}

/**
 * Floating bottom-left status and legend pill overlaid on the Form editor canvas (Mobile and Web).
 * Surfaces screen/question & path count, estimated duration & fatigue warning badge with tooltip,
 * validation issue count, and the flow edge legend.
 */
@Composable
private fun CanvasStatusOverlay(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val form = uiState.form
  val issues = uiState.issues
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    color = colors.surfaceContainerHigh.copy(alpha = 0.94f),
    contentColor = colors.onSurface,
    tonalElevation = 3.dp,
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val summaryText =
        when (uiState.previewTarget) {
          FormPreviewTarget.MOBILE ->
            "${form.questions.size} screens • ${uiState.pathCount} potential paths"
          FormPreviewTarget.WEB -> "${form.questions.size} questions • Web layout"
        }
      Text(
        text = summaryText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        softWrap = false,
      )
      DurationFatigueBadge(uiState = uiState)
      if (issues.isNotEmpty()) {
        Surface(
          onClick = {
            val firstKey = issues.first().questionKey
            if (firstKey != null) actions.select(firstKey) else actions.selectForm()
          },
          shape = RoundedCornerShape(50),
          color = colors.errorContainer,
          contentColor = colors.onErrorContainer,
          modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Outlined.Warning,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${issues.size} ${if (issues.size == 1) "issue" else "issues"}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              softWrap = false,
            )
          }
        }
      }
      FlowLegend()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationFatigueBadge(uiState: FormEditorUiState) {
  val colors = MaterialTheme.colorScheme
  val minutes = uiState.estimatedCompletionMinutes
  val longestCount = uiState.longestPathQuestionCount
  val isFatigue = uiState.isFatigueWarning
  val questionNoun = if (longestCount == 1) "question" else "questions"
  val tooltipText = buildString {
    append("Estimated at ~2 min per question along the longest path ($longestCount $questionNoun).")
    if (isFatigue) {
      append(" Surveys over 10 questions (~20 min) may cause respondent fatigue.")
    }
  }
  TooltipBox(
    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    tooltip = { PlainTooltip { Text(tooltipText) } },
    state = rememberTooltipState(),
  ) {
    Surface(
      shape = RoundedCornerShape(50),
      color = if (isFatigue) colors.tertiaryContainer else colors.surfaceContainerHighest,
      contentColor = if (isFatigue) colors.onTertiaryContainer else colors.onSurfaceVariant,
      modifier = Modifier.semantics { contentDescription = tooltipText },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = if (isFatigue) Icons.Outlined.Warning else Icons.Outlined.Schedule,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = if (isFatigue) "~$minutes min • Long survey" else "~$minutes min",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isFatigue) FontWeight.SemiBold else FontWeight.Medium,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun FlowHorizontalScrollBar(
  scrollState: androidx.compose.foundation.ScrollState,
  modifier: Modifier = Modifier,
) {
  val maxScroll = scrollState.maxValue
  if (maxScroll <= 0) return

  val coroutineScope = rememberCoroutineScope()
  val colors = MaterialTheme.colorScheme

  Box(
    modifier =
      modifier
        .height(10.dp)
        .clip(CircleShape)
        .background(colors.surfaceContainerHighest.copy(alpha = 0.5f))
  ) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val trackWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
      val totalContentPx = trackWidthPx + maxScroll.toFloat()
      val thumbWidthFraction = (trackWidthPx / totalContentPx).coerceIn(0.1f, 1f)
      val thumbWidthPx = trackWidthPx * thumbWidthFraction
      val scrollFraction =
        if (maxScroll > 0) (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f) else 0f
      val thumbOffsetPx = scrollFraction * (trackWidthPx - thumbWidthPx)
      val thumbOffsetDp = with(LocalDensity.current) { thumbOffsetPx.toDp() }
      val thumbWidthDp = with(LocalDensity.current) { thumbWidthPx.toDp() }

      var isThumbHovered by remember { mutableStateOf(false) }
      var isThumbDragging by remember { mutableStateOf(false) }
      DisposableEffect(isThumbHovered) {
        if (isThumbHovered) showPlatformGrabCursor(true)
        onDispose { if (isThumbHovered) showPlatformGrabCursor(false) }
      }
      DisposableEffect(isThumbDragging) {
        if (isThumbDragging) showPlatformGrabbingCursor(true)
        onDispose { if (isThumbDragging) showPlatformGrabbingCursor(false) }
      }

      Box(
        modifier =
          Modifier.offset(x = thumbOffsetDp)
            .width(thumbWidthDp)
            .height(10.dp)
            .clip(CircleShape)
            .background(colors.outline.copy(alpha = 0.65f))
            .pointerHoverIcon(if (isThumbDragging) GrabbingPointerIcon else GrabPointerIcon)
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  val event = awaitPointerEvent()
                  when (event.type) {
                    PointerEventType.Enter -> isThumbHovered = true
                    PointerEventType.Exit -> isThumbHovered = false
                  }
                }
              }
            }
            .pointerInput(maxScroll, trackWidthPx, thumbWidthPx) {
              detectHorizontalDragGestures(
                onDragStart = { isThumbDragging = true },
                onDragEnd = { isThumbDragging = false },
                onDragCancel = { isThumbDragging = false },
              ) { change, dragAmount ->
                change.consume()
                val scrollableTrack = trackWidthPx - thumbWidthPx
                if (scrollableTrack > 0f) {
                  val deltaScroll = (dragAmount / scrollableTrack) * maxScroll
                  scrollState.dispatchRawDelta(deltaScroll)
                }
              }
            }
      )
    }
  }
}

@Composable
private fun FlowLegend() {
  val colors = MaterialTheme.colorScheme
  Row(
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LegendItem("Always next", colors.outline, dashed = false)
    LegendItem("If condition is true", colors.primary, dashed = true)
  }
}

@Composable
private fun LegendItem(text: String, color: Color, dashed: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Canvas(modifier = Modifier.width(28.dp).height(10.dp)) {
      drawLine(
        color = color,
        start = Offset(0f, size.height / 2),
        end = Offset(size.width, size.height / 2),
        strokeWidth = 2.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
      )
    }
    Spacer(Modifier.width(6.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      softWrap = false,
    )
  }
}

@Composable
private fun FlowCanvas(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  edges: List<FlowEdge>,
  horizontalScroll: androidx.compose.foundation.ScrollState,
  modifier: Modifier,
) {
  val form = uiState.form
  val layout =
    remember(form.questions.size, form.sections, edges) {
      FlowLayout(form.questions.size, edges, form)
    }
  val density = LocalDensity.current
  val selectedSlot =
    if (uiState.selectedIndex >= 0) FormFlowGraph.questionSlot(uiState.selectedIndex) else -1

  // Keep the selected screen in view (e.g. after adding or reordering). This jumps rather than
  // animating: while an animated scroll is in progress the scroll container consumes the next
  // pointer down to stop it, which swallowed the user's next click on a screen card.
  LaunchedEffect(selectedSlot, layout.slotCount) {
    if (selectedSlot >= 0) {
      val left = with(density) { (layout.x(selectedSlot) - SlotGap).roundToPx() }
      val right = with(density) { (layout.x(selectedSlot) + CardWidth + SlotGap).roundToPx() }
      val viewportEnd = horizontalScroll.value + horizontalScroll.viewportSize
      when {
        left < horizontalScroll.value -> horizontalScroll.scrollTo(left.coerceAtLeast(0))
        right > viewportEnd && horizontalScroll.viewportSize > 0 ->
          horizontalScroll.scrollTo(right - horizontalScroll.viewportSize)
      }
    }
  }

  val colors = MaterialTheme.colorScheme
  val drag = remember { DragReorderState() }
  var isPanning by remember { mutableStateOf(false) }
  var isCanvasHovered by remember { mutableStateOf(false) }
  val isGrabbing = isPanning || drag.isDragging
  val showGrab = isCanvasHovered && uiState.isEnabledOnPreviewTarget
  DisposableEffect(showGrab) {
    if (showGrab) showPlatformGrabCursor(true)
    onDispose { if (showGrab) showPlatformGrabCursor(false) }
  }
  DisposableEffect(isGrabbing) {
    if (isGrabbing) showPlatformGrabbingCursor(true)
    onDispose { if (isGrabbing) showPlatformGrabbingCursor(false) }
  }
  val pitchPx = with(density) { (CardWidth + SlotGap).toPx() }
  val cardWidthPx = with(density) { CardWidth.toPx() }
  val edgeZonePx = with(density) { 56.dp.toPx() }
  val edgeAlpha by animateFloatAsState(if (drag.isDragging) 0.15f else 1f)
  val verticalScroll = rememberScrollState()

  val draggingSlot =
    remember(drag.draggingKey, form) {
      val idx = form.indexOf(drag.draggingKey ?: "")
      if (idx >= 0) FormFlowGraph.questionSlot(idx) else -1
    }
  val draggingSlotLeftPx =
    if (draggingSlot >= 0) with(density) { layout.x(draggingSlot).toPx() } else 0f
  val draggedCenterXDp =
    with(density) { (draggingSlotLeftPx + drag.offset + cardWidthPx / 2).toDp() }

  val hoveredSectionId: String? =
    if (drag.isDragging) {
      val targetQ = form.questions.getOrNull(drag.targetIndex)
      val sectionAtTarget = targetQ?.sectionId?.takeIf { it.isNotEmpty() }
      val sectionByFrame =
        form.sections
          .firstOrNull { section ->
            val sectionQuestions = form.questions.filter { it.sectionId == section.id }
            val slots = sectionQuestions.map { FormFlowGraph.questionSlot(form.indexOf(it.key)) }
            val hasGhost = layout.hasSectionGhost(section.id)
            if (sectionQuestions.isEmpty() && !hasGhost) return@firstOrNull false
            val minX =
              minOf(
                slots.minOfOrNull { layout.slotX(it) }
                  ?: (if (hasGhost) layout.sectionGhostX(section.id) else layout.totalWidth),
                if (hasGhost) layout.sectionGhostX(section.id) else layout.totalWidth,
              ) - 16.dp
            val maxX =
              maxOf(
                slots.maxOfOrNull { layout.slotX(it) + layout.width(it) } ?: 0.dp,
                if (hasGhost) layout.sectionGhostX(section.id) + CardWidth else 0.dp,
              ) + 16.dp
            draggedCenterXDp in minX..maxX
          }
          ?.id
      sectionByFrame ?: sectionAtTarget
    } else {
      null
    }
  Box(
    modifier =
      modifier
        .pointerHoverIcon(
          icon = if (isGrabbing) GrabbingPointerIcon else GrabPointerIcon,
          overrideDescendants = isGrabbing,
        )
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              when (event.type) {
                PointerEventType.Enter -> isCanvasHovered = true
                PointerEventType.Exit -> isCanvasHovered = false
              }
            }
          }
        }
        .horizontalScroll(horizontalScroll)
        .verticalScroll(verticalScroll)
        .pointerInput(drag.isDragging) {
          // Pans the canvas. Like screen reordering, a pan only starts past [MouseDragSlop]:
          // Compose's default mouse slop (0.125 dp) turned slightly-moving clicks on screen cards
          // into pans that cancelled the click.
          if (!drag.isDragging) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val (start, travel) =
                awaitDragPastSlop(down, dragSlopFor(down), DragAxis.HORIZONTAL)
                  ?: return@awaitEachGesture
              isPanning = true
              try {
                horizontalScroll.dispatchRawDelta(-travel.x)
                verticalScroll.dispatchRawDelta(-travel.y)
                horizontalDrag(start.id) { change ->
                  val delta = change.positionChange()
                  horizontalScroll.dispatchRawDelta(-delta.x)
                  verticalScroll.dispatchRawDelta(-delta.y)
                  change.consume()
                }
              } finally {
                isPanning = false
              }
            }
          }
        }
  ) {
    // Clicking empty canvas selects the Form itself, showing Form properties.
    Box(
      modifier =
        Modifier.width(layout.totalWidth)
          .height(layout.totalHeight)
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = actions::selectForm,
          )
    ) {
      Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = edgeAlpha }) {
        edges.forEach { edge ->
          val highlighted =
            selectedSlot >= 0 && (edge.from == selectedSlot || edge.to == selectedSlot)
          val baseColor =
            when (edge.kind) {
              FlowEdgeKind.NEXT -> colors.outline
              FlowEdgeKind.CONDITIONAL -> colors.primary
              FlowEdgeKind.SKIP -> colors.tertiary
            }
          val color =
            if (selectedSlot >= 0 && !highlighted) baseColor.copy(alpha = 0.45f) else baseColor
          drawFlowEdge(edge, layout, color, strokeWidthDp = if (highlighted) 3.dp else 2.dp)
        }
      }

      // Section visual grouping frames
      form.sections.forEach { section ->
        val sectionQuestions = form.questions.filter { it.sectionId == section.id }
        val slots = sectionQuestions.map { FormFlowGraph.questionSlot(form.indexOf(it.key)) }
        val hasGhost = layout.hasSectionGhost(section.id)
        val isSectionHovered = hoveredSectionId == section.id
        val isSectionSelected = uiState.selectedSectionId == section.id

        if (sectionQuestions.isNotEmpty() || hasGhost) {
          val minX =
            minOf(
              slots.minOfOrNull { layout.slotX(it) }
                ?: (if (hasGhost) layout.sectionGhostX(section.id) else layout.totalWidth),
              if (hasGhost) layout.sectionGhostX(section.id) else layout.totalWidth,
            ) - 16.dp
          val minY =
            minOf(
              slots.minOfOrNull { layout.slotY(it) }
                ?: (if (hasGhost) layout.sectionGhostY(section.id) else layout.totalHeight),
              if (hasGhost) layout.sectionGhostY(section.id) else layout.totalHeight,
            ) - 40.dp
          val maxX =
            maxOf(
              slots.maxOfOrNull { layout.slotX(it) + layout.width(it) } ?: 0.dp,
              if (hasGhost) layout.sectionGhostX(section.id) + CardWidth else 0.dp,
            ) + 16.dp
          val maxY =
            maxOf(
              slots.maxOfOrNull { layout.slotY(it) + layout.height(it) } ?: 0.dp,
              if (hasGhost) layout.sectionGhostY(section.id) + CardHeight else 0.dp,
            ) + 16.dp
          val frameWidth = maxX - minX
          val frameHeight = maxY - minY
          Surface(
            onClick = { actions.selectSection(section.id) },
            modifier = Modifier.offset(minX, minY).width(frameWidth).height(frameHeight),
            shape = RoundedCornerShape(20.dp),
            color =
              if (isSectionHovered) colors.primaryContainer.copy(alpha = 0.28f)
              else if (isSectionSelected) colors.primaryContainer.copy(alpha = 0.12f)
              else colors.surfaceContainerLow.copy(alpha = 0.45f),
            border =
              BorderStroke(
                if (isSectionHovered || isSectionSelected) 2.5.dp else 1.5.dp,
                if (isSectionHovered) colors.primary
                else if (isSectionSelected) colors.primary
                else if (section.repeatable) colors.primary.copy(alpha = 0.6f)
                else colors.outlineVariant.copy(alpha = 0.8f),
              ),
          ) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopStart),
              ) {
                GroundTonalBadge(
                  text =
                    if (section.repeatable)
                      "Repeated section: ${section.title.ifBlank { "Untitled" }}"
                    else "Section: ${section.title.ifBlank { "Untitled" }}",
                  tone =
                    if (section.repeatable) GroundBadgeTone.PRIMARY else GroundBadgeTone.SECONDARY,
                  modifier = Modifier.clickable { actions.selectSection(section.id) },
                )
                if (section.relevance != null) {
                  GroundTonalBadge(text = "Conditional", tone = GroundBadgeTone.TERTIARY)
                }
                if (section.repeatable && section.repeatCountExpression.isNotBlank()) {
                  Text(
                    text = "Count: ${section.repeatCountExpression}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                  )
                }
              }
            }
          }

          if (hasGhost) {
            val lastIdx =
              if (sectionQuestions.isNotEmpty()) form.indexOf(sectionQuestions.last().key)
              else form.questions.size - 1
            GhostQuestionCard(
              atIndex = lastIdx + 1,
              onAdd = { type, idx -> actions.addQuestion(type, idx, sectionId = section.id) },
              title = "Add to ${section.title.ifBlank { "section" }}",
              subtitle = "Append question in this section",
              contentDescription = "Add question inside ${section.title.ifBlank { "section" }}",
              modifier =
                Modifier.offset(
                  layout.sectionGhostX(section.id),
                  layout.sectionGhostY(section.id),
                ),
            )
          }
        }
      }

      // Condition pills on conditional flow edges
      edges
        .filter { it.kind == FlowEdgeKind.CONDITIONAL }
        .forEach { edge ->
          val targetQ = form.questions.getOrNull(edge.to - 1)
          val section = targetQ?.let { form.section(it.sectionId) }
          val relevance = targetQ?.relevance ?: section?.relevance
          if (relevance != null) {
            val pillText =
              if (relevance.value.isNotBlank()) "${relevance.operator.label} '${relevance.value}'"
              else relevance.operator.label
            val pillX =
              (layout.slotX(edge.from) + layout.width(edge.from) + layout.slotX(edge.to)) / 2 -
                32.dp
            val pillY =
              (layout.slotY(edge.from) + layout.slotY(edge.to)) / 2 + (CardHeight / 2) - 12.dp
            Surface(
              modifier = Modifier.offset(pillX, pillY),
              shape = RoundedCornerShape(12.dp),
              color = colors.surfaceContainerHighest,
              border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.7f)),
              shadowElevation = 2.dp,
            ) {
              Text(
                text = pillText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
        }

      LaunchButtonNode(
        text = form.effectiveCtaLabel.ifBlank { "Untitled form" },
        modifier = Modifier.offset(layout.x(0), layout.top(0)),
        isSelected = uiState.isFormSelected,
        onClick = actions::selectForm,
      )

      val handleAddSection: (Int?) -> Unit = { _ ->
        val secId = actions.addSection("Section", repeatable = false)
        actions.selectSection(secId)
      }

      // Hoverable "+" between Start and Q1 (or Q0)
      AddQuestionAffordance(
        centerX = layout.x(0) + layout.width(0) + SlotGap / 2,
        centerY = layout.midY,
        atIndex = 0,
        onAdd = actions::addQuestion,
        onAddSection = handleAddSection,
        alwaysVisible = false,
      )

      form.questions.forEachIndexed { index, question ->
        val slot = FormFlowGraph.questionSlot(index)
        key(question.key) {
          val isDragged = drag.draggingKey == question.key
          val shift by
            animateFloatAsState(
              targetValue = drag.shiftFor(index) * pitchPx,
              animationSpec = if (drag.isDragging) spring() else snap(),
            )
          val slotLeftPx = with(density) { layout.x(slot).toPx() }
          ScreenPreviewCard(
            index = index,
            question = question,
            form = form,
            datasets = uiState.datasets,
            isSelected = question.key == uiState.selectedKey,
            hasIssues = uiState.issuesFor(question.key).isNotEmpty(),
            isLinked = question.conceptLink != null,
            isDragged = isDragged,
            activeLanguage = uiState.activeLanguage,
            defaultLanguage = uiState.defaultLanguage,
            onClick = { actions.select(question.key) },
            modifier =
              Modifier.offset(layout.x(slot), layout.top(slot))
                .zIndex(if (isDragged) 1f else 0f)
                .graphicsLayer {
                  translationX = if (isDragged) drag.offset else shift
                  val lift = if (isDragged) 1.04f else 1f
                  scaleX = lift
                  scaleY = lift
                  shadowElevation = if (isDragged) 12.dp.toPx() else 0f
                }
                .dragToReorder(
                  state = drag,
                  key = question.key,
                  index = index,
                  count = form.questions.size,
                  pitchPx = pitchPx,
                  axis = DragAxis.HORIZONTAL,
                  onMove = { key, to ->
                    actions.moveQuestionTo(key, to, targetSectionId = hoveredSectionId ?: "")
                    actions.select(key)
                  },
                  autoScroll = {
                    val left = slotLeftPx + drag.offset - horizontalScroll.value
                    val viewport = horizontalScroll.viewportSize.toFloat()
                    val step =
                      when {
                        left < edgeZonePx -> -24f
                        left + cardWidthPx > viewport - edgeZonePx -> 24f
                        else -> 0f
                      }
                    if (step == 0f) 0f else horizontalScroll.dispatchRawDelta(step)
                  },
                ),
          )

          // Hover-only insertion affordance between question screens
          AddQuestionAffordance(
            centerX = layout.x(slot) + layout.width(slot) + SlotGap / 2,
            centerY = layout.midY,
            atIndex = index + 1,
            onAdd = actions::addQuestion,
            onAddSection = handleAddSection,
            alwaysVisible = false,
          )
        }
      }
      GhostQuestionCard(
        atIndex = form.questions.size,
        onAdd = actions::addQuestion,
        onAddSection = handleAddSection,
        modifier = Modifier.offset(layout.ghostX, layout.cardTop),
      )

      // Drop indicator between question cards while dragging
      if (
        drag.isDragging && drag.targetIndex in 0..form.questions.size && form.questions.isNotEmpty()
      ) {
        val dropSlot =
          if (drag.targetIndex < form.questions.size) {
            FormFlowGraph.questionSlot(drag.targetIndex)
          } else {
            FormFlowGraph.questionSlot(form.questions.size - 1)
          }
        val dropIndicatorX =
          if (drag.targetIndex < form.questions.size) {
            layout.x(dropSlot) - SlotGap / 2
          } else {
            layout.x(dropSlot) + CardWidth + SlotGap / 2
          }
        Surface(
          modifier =
            Modifier.offset(dropIndicatorX - 2.dp, layout.cardTop)
              .width(4.dp)
              .height(CardHeight)
              .zIndex(10f),
          color = colors.primary,
          shape = RoundedCornerShape(2.dp),
          shadowElevation = 4.dp,
        ) {}
      }

      TerminalNode(
        text = "Review & submit",
        isSelected = uiState.isFormSelected,
        onClick = actions::selectForm,
        modifier = Modifier.offset(layout.x(layout.endSlot), layout.top(layout.endSlot)),
      )
      Text(
        text = SaveToRules.outcome(form, uiState.saveTarget),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
          Modifier.offset(
              layout.x(layout.endSlot) - SlotGap / 2,
              layout.top(layout.endSlot) + TerminalHeight + 8.dp,
            )
            .width(TerminalWidth + SlotGap),
      )
    }
  }
}

@Composable
private fun AddQuestionAffordance(
  centerX: Dp,
  centerY: Dp,
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  alwaysVisible: Boolean = false,
  onAddSection: ((Int?) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme

  val buttonSize = 32.dp
  val slotWidth = SlotGap
  val slotHeight = 64.dp

  Box(
    modifier =
      modifier
        .offset(x = centerX - slotWidth / 2, y = centerY - slotHeight / 2)
        .size(slotWidth, slotHeight)
        .hoverable(interactionSource),
    contentAlignment = Alignment.Center,
  ) {
    // The button is always composed and hit-testable, only transparent until hovered. Touch input
    // has no hover, so composing it on hover alone meant the first tap merely revealed it.
    val visible = alwaysVisible || isHovered || menuExpanded
    Surface(
      onClick = { menuExpanded = true },
      shape = CircleShape,
      color = if (alwaysVisible && !isHovered) colors.secondaryContainer else colors.secondary,
      contentColor =
        if (alwaysVisible && !isHovered) colors.onSecondaryContainer else colors.onSecondary,
      shadowElevation = if (isHovered || menuExpanded) 4.dp else if (visible) 1.dp else 0.dp,
      modifier =
        Modifier.size(buttonSize)
          .graphicsLayer { alpha = if (visible) 1f else 0f }
          .pointerHoverIcon(if (visible) PointerIcon.Hand else GrabPointerIcon),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          Icons.Outlined.Add,
          contentDescription = "Add question here",
          modifier = Modifier.size(18.dp),
        )
      }
    }
    AddQuestionMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onAdd = onAdd,
      atIndex = atIndex,
      onAddSection = onAddSection,
    )
  }
}

private fun DrawScope.drawFlowEdge(
  edge: FlowEdge,
  layout: FlowLayout,
  color: Color,
  strokeWidthDp: Dp,
) {
  val strokeWidth = strokeWidthDp.toPx()
  val dash =
    if (edge.kind == FlowEdgeKind.NEXT) null else PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

  val fromRow = layout.slotRow(edge.from)
  val toRow = layout.slotRow(edge.to)

  if (fromRow != toRow) {
    // Cross-row branching connector: smooth cubic bezier S-curve
    val startX = (layout.slotX(edge.from) + layout.width(edge.from)).toPx()
    val startY = (layout.slotY(edge.from) + layout.height(edge.from) / 2).toPx()
    val endX = (layout.slotX(edge.to) - 2.dp).toPx()
    val endY = (layout.slotY(edge.to) + layout.height(edge.to) / 2).toPx()
    val dx = (endX - startX).coerceAtLeast(40f)
    val c1 = Offset(startX + dx * 0.5f, startY)
    val c2 = Offset(endX - dx * 0.5f, endY)
    val path =
      Path().apply {
        moveTo(startX, startY)
        cubicTo(c1.x, c1.y, c2.x, c2.y, endX, endY)
      }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = dash))
    drawArrowHead(tip = Offset(endX, endY), from = Offset(endX - 10f, endY), color = color)
  } else if (edge.span == 1) {
    val startY = (layout.slotY(edge.from) + layout.height(edge.from) / 2).toPx()
    val endY = (layout.slotY(edge.to) + layout.height(edge.to) / 2).toPx()
    val start = Offset((layout.slotX(edge.from) + layout.width(edge.from)).toPx(), startY)
    val end = Offset((layout.slotX(edge.to) - 2.dp).toPx(), endY)
    if (edge.to == layout.endSlot) {
      val ghostLeft = Offset((layout.ghostX - 2.dp).toPx(), (layout.ghostY + CardHeight / 2).toPx())
      val ghostRight =
        Offset((layout.ghostX + CardWidth + 2.dp).toPx(), (layout.ghostY + CardHeight / 2).toPx())
      drawLine(color, start, ghostLeft, strokeWidth = strokeWidth, pathEffect = dash)
      drawLine(color, ghostRight, end, strokeWidth = strokeWidth, pathEffect = dash)
      drawArrowHead(tip = end, from = ghostRight, color = color)
    } else {
      drawLine(color, start, end, strokeWidth = strokeWidth, pathEffect = dash)
      drawArrowHead(tip = end, from = start, color = color)
    }
  } else {
    val sx = (layout.slotX(edge.from) + layout.width(edge.from) - ArcInset).toPx()
    val sy = layout.slotY(edge.from).toPx()
    val ex = (layout.slotX(edge.to) + ArcInset).toPx()
    val ey = (layout.slotY(edge.to) - 2.dp).toPx()
    val apexY = (layout.slotY(edge.from) - arcRise(edge.span)).toPx()
    val path =
      Path().apply {
        moveTo(sx, sy)
        cubicTo(sx, apexY, ex, apexY, ex, ey)
      }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = dash))
    drawArrowHead(tip = Offset(ex, ey), from = Offset(ex, apexY), color = color)
  }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
  val angle = atan2(tip.y - from.y, tip.x - from.x)
  val length = 10.dp.toPx()
  val spread = 0.45f
  val path =
    Path().apply {
      moveTo(tip.x, tip.y)
      lineTo(tip.x - length * cos(angle - spread), tip.y - length * sin(angle - spread))
      lineTo(tip.x - length * cos(angle + spread), tip.y - length * sin(angle + spread))
      close()
    }
  drawPath(path, color)
}

/**
 * Start node of the flow: renders the data-collection launch button shown in the app using the
 * user-specified button label (or form title when blank). Selecting it shows Form properties.
 */
@Composable
private fun LaunchButtonNode(
  text: String,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val sized =
    modifier
      .width(StartButtonWidth)
      .height(StartButtonHeight)
      .pointerHoverIcon(PointerIcon.Hand)
      .semantics { contentDescription = "Start: $text" }
  val border = if (isSelected) BorderStroke(2.dp, colors.onPrimaryContainer) else null
  Surface(
    onClick = onClick,
    modifier = sized,
    shape = MaterialTheme.shapes.small,
    color = colors.primary,
    contentColor = colors.onPrimary,
    border = border,
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Description,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** End node of the flow; selecting it shows Form properties. */
@Composable
private fun TerminalNode(
  text: String,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val content: @Composable () -> Unit = {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
      )
    }
  }
  val sized = modifier.width(TerminalWidth).height(TerminalHeight)
  val shape = RoundedCornerShape(50)
  val border = if (isSelected) BorderStroke(2.dp, colors.secondary) else null
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = sized.pointerHoverIcon(PointerIcon.Hand),
      shape = shape,
      color = colors.secondaryContainer,
      contentColor = colors.onSecondaryContainer,
      border = border,
      content = content,
    )
  } else {
    Surface(
      modifier = sized,
      shape = shape,
      color = colors.secondaryContainer,
      contentColor = colors.onSecondaryContainer,
      border = border,
      content = content,
    )
  }
}

/**
 * Dashed "ghost" screen card at the end of the flow (before Review & submit) or inside a section
 * container that lets authors append a new question screen directly on the canvas.
 */
@Composable
internal fun GhostQuestionCard(
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  modifier: Modifier = Modifier,
  title: String = "Add a question",
  subtitle: String = "Click to append a screen before Review & submit",
  contentDescription: String = "Add a question at end of flow",
  onAddSection: ((Int?) -> Unit)? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val highlighted = isHovered || menuExpanded
  val borderColor = if (highlighted) colors.primary else colors.outlineVariant
  val containerColor =
    if (highlighted) colors.secondaryContainer.copy(alpha = 0.25f)
    else colors.surfaceContainerLow.copy(alpha = 0.7f)

  Box(
    modifier =
      modifier
        .width(CardWidth)
        .height(CardHeight)
        .clip(MaterialTheme.shapes.medium)
        .background(containerColor)
        .pointerHoverIcon(PointerIcon.Hand)
        .hoverable(interactionSource)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = { menuExpanded = true },
        )
        .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = (if (highlighted) 2.dp else 1.5.dp).toPx()
      val cornerPx = 12.dp.toPx()
      drawRoundRect(
        color = borderColor,
        topLeft = Offset(strokePx / 2f, strokePx / 2f),
        size =
          androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx,
          ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
        style =
          Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
          ),
      )
    }
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = if (highlighted) colors.primary else colors.secondaryContainer,
        contentColor = if (highlighted) colors.onPrimary else colors.onSecondaryContainer,
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (highlighted) colors.primary else colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    AddQuestionMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onAdd = onAdd,
      atIndex = atIndex,
      onAddSection = onAddSection,
    )
  }
}

@Composable
private fun ScreenPreviewCard(
  index: Int,
  question: EditorQuestion,
  form: EditorForm,
  datasets: List<EditorDataset>,
  isSelected: Boolean,
  hasIssues: Boolean,
  isDragged: Boolean,
  onClick: () -> Unit,
  isLinked: Boolean = false,
  activeLanguage: String = "en",
  defaultLanguage: String = "en",
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var isHovered by remember { mutableStateOf(false) }
  val isHoverHighlighted = isHovered && !isSelected && !isDragged
  // Subtle selection using GroundTheme's muted secondary roles (the same treatment as selected
  // list rows elsewhere in the app) rather than the bright primaryContainer accent.
  // Hovering the card body highlights the entire card and shows a pointer cursor (click to select),
  // while hovering the drag handle icon shows an open-hand grab cursor (drag to reorder); active
  // dragging switches the whole canvas to a closed-hand grabbing cursor.
  OutlinedCard(
    onClick = onClick,
    modifier =
      modifier.width(CardWidth).height(CardHeight).pointerHoverIcon(PointerIcon.Hand).pointerInput(
        Unit
      ) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
              PointerEventType.Enter -> isHovered = true
              PointerEventType.Exit -> isHovered = false
            }
          }
        }
      },
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          when {
            isSelected -> colors.secondaryContainer
            isHoverHighlighted -> colors.secondaryContainer.copy(alpha = 0.28f)
            else -> colors.surface
          },
        contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = if (isSelected || isDragged || isHoverHighlighted) 2.dp else 1.dp,
        color =
          when {
            isDragged -> colors.outline
            hasIssues -> colors.error
            isSelected -> colors.secondary
            isHoverHighlighted -> colors.primary.copy(alpha = 0.65f)
            else -> colors.outlineVariant
          },
      ),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          Icons.Outlined.DragIndicator,
          contentDescription = "Drag to reorder",
          tint =
            when {
              isSelected -> colors.onSecondaryContainer
              isDragged -> colors.onSurface
              else -> colors.onSurfaceVariant
            },
          modifier = Modifier.size(16.dp).pointerHoverIcon(GrabPointerIcon),
        )
        GroundTonalBadge(text = "Q${index + 1}", tone = GroundBadgeTone.NEUTRAL)
        val section =
          if (question.sectionId.isNotBlank()) form.section(question.sectionId) else null
        if (section != null) {
          GroundTonalBadge(
            text = if (section.repeatable) "↻ ${section.title}" else "▤ ${section.title}",
            tone = if (section.repeatable) GroundBadgeTone.PRIMARY else GroundBadgeTone.SECONDARY,
          )
        }
        Icon(
          questionTypeIcon(question.type),
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
        Text(
          text = question.type.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (isLinked) {
          Icon(
            Icons.Outlined.Link,
            contentDescription = "Linked to a standard field",
            tint = if (isSelected) colors.onSecondaryContainer else colors.primary,
            modifier = Modifier.size(14.dp),
          )
        }
        if (hasIssues) {
          Icon(
            Icons.Outlined.Warning,
            contentDescription = "Has issues",
            tint = colors.error,
            modifier = Modifier.size(14.dp),
          )
        }
      }
      Text(
        text = form.describeRelevance(question, datasets) ?: "Always shown",
        style = MaterialTheme.typography.labelSmall,
        color =
          when {
            isSelected -> colors.onSecondaryContainer
            question.isConditional -> colors.primary
            else -> colors.onSurfaceVariant
          },
        fontWeight = if (question.isConditional) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
      )

      MiniScreen(
        question = question,
        formTitle = form.title,
        datasets = datasets,
        activeLanguage = activeLanguage,
        defaultLanguage = defaultLanguage,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )

      Text(
        text = question.type.label + if (question.required) " • Required" else "",
        style = MaterialTheme.typography.labelSmall,
        color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      )
    }
  }
}

/** Static, scaled-down mock of how the question screen looks on a device. */
@Composable
private fun MiniScreen(
  question: EditorQuestion,
  formTitle: String,
  datasets: List<EditorDataset>,
  activeLanguage: String = "en",
  defaultLanguage: String = "en",
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val displayLabel = question.displayLabel(activeLanguage, defaultLanguage)
  val displayHint = question.displayHint(activeLanguage, defaultLanguage)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = colors.surfaceContainerLowest,
    contentColor = colors.onSurface,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(colors.primary)
            .padding(horizontal = 8.dp, vertical = 5.dp)
      ) {
        Text(
          text = formTitle.ifBlank { "Untitled form" },
          style = MaterialTheme.typography.labelSmall,
          color = colors.onPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = displayLabel.ifBlank { "(no label)" } + if (question.required) " *" else "",
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurface,
          fontWeight = FontWeight.Bold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        if (displayHint.isNotBlank()) {
          Text(
            text = displayHint,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.height(2.dp))
        MiniWidget(question, datasets, activeLanguage, defaultLanguage)
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        MiniButton("Back", filled = false)
        MiniButton("Next", filled = true)
      }
    }
  }
}

@Composable
private fun MiniButton(text: String, filled: Boolean) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(if (filled) colors.primary else Color.Transparent)
        .border(1.dp, if (filled) colors.primary else colors.outline, RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 2.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = if (filled) colors.onPrimary else colors.primary,
    )
  }
}

@Composable
private fun MiniWidget(
  question: EditorQuestion,
  datasets: List<EditorDataset>,
  activeLanguage: String = "en",
  defaultLanguage: String = "en",
) {
  val colors = MaterialTheme.colorScheme
  when (question.type) {
    EditorQuestionType.TEXT -> MiniField("Answer", 26.dp)
    EditorQuestionType.LONG_TEXT -> MiniField("Answer", 52.dp)
    EditorQuestionType.NUMBER -> MiniField(if (question.decimalPlaces <= 0) "123" else "1.5", 26.dp)
    EditorQuestionType.DATE -> MiniField("YYYY-MM-DD", 26.dp, Icons.Outlined.DateRange)
    EditorQuestionType.SELECT_ONE,
    EditorQuestionType.SELECT_MULTIPLE -> {
      val round = question.type == EditorQuestionType.SELECT_ONE
      val effectiveSource = question.effectiveChoiceSource(datasets)
      val datasetId = question.choiceDatasetId
      val dataset = datasetId?.let { id -> datasets.firstOrNull { it.id == id } }
      if (question.usesDatasetChoices) {
        val fallbackLabel =
          if (effectiveSource == ChoiceSource.DATA_TABLE) "Choose data table"
          else "Choose map layer"
        val defaultIcon =
          if (effectiveSource == ChoiceSource.DATA_TABLE) Icons.Outlined.TableChart
          else Icons.Outlined.Layers
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            dataset?.let(::datasetIcon) ?: defaultIcon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = dataset?.displayName ?: datasetId.orEmpty().ifBlank { fallbackLabel },
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      val previewChoices = question.resolvedChoices(datasets)
      val maxItems = if (question.usesDatasetChoices) 3 else 4
      previewChoices.take(maxItems).forEach { choice ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .border(
                  1.5.dp,
                  colors.primary,
                  if (round) CircleShape else RoundedCornerShape(2.dp),
                )
          )
          Spacer(Modifier.width(6.dp))
          if (choice.colorHex != null) {
            ColorDot(colorHex = choice.colorHex, contentDescription = null, size = 8.dp)
            Spacer(Modifier.width(4.dp))
          }
          choice.image?.let { image ->
            rememberChoiceImageBitmap(image)?.let { bitmap ->
              Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)),
              )
              Spacer(Modifier.width(4.dp))
            }
          }
          Text(
            text = choice.displayLabel(activeLanguage, defaultLanguage).ifBlank { choice.value },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (previewChoices.size > maxItems) {
        Text(
          text = "+${previewChoices.size - maxItems} more",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
      if (question.usesDatasetChoices && question.allowAddEntity) {
        Text(
          text = "+ Add new ${dataset?.featureNoun ?: "item"}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.primary,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    EditorQuestionType.LOCATION ->
      MiniPlaceholder(geometryPlaceholderText(question, "Capture point"), Icons.Outlined.LocationOn)
    EditorQuestionType.LINE ->
      MiniPlaceholder(geometryPlaceholderText(question, "Walk line"), Icons.Outlined.Timeline)
    EditorQuestionType.POLYGON ->
      MiniPlaceholder(geometryPlaceholderText(question, "Walk polygon"), Icons.Outlined.Pentagon)
    EditorQuestionType.PHOTO ->
      MiniPlaceholder(mediaPlaceholderText(question, "Take photo"), Icons.Outlined.Add)
    EditorQuestionType.VIDEO ->
      MiniPlaceholder(mediaPlaceholderText(question, "Record video"), Icons.Outlined.Videocam)
    EditorQuestionType.AUDIO ->
      MiniPlaceholder(mediaPlaceholderText(question, "Record audio"), Icons.Outlined.Mic)
    EditorQuestionType.NOTE ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Outlined.Info,
          contentDescription = null,
          tint = colors.onSurfaceVariant,
          modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          text = "Read-only note",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
  }
}

@Composable
private fun MiniField(
  placeholder: String,
  height: Dp,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(height)
        .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = placeholder,
      style = MaterialTheme.typography.labelSmall,
      color = colors.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    if (icon != null) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = colors.onSurfaceVariant,
      )
    }
  }
}

/**
 * Caption of a geometry question's mini widget: [gpsAction] when locked to GPS, or the map
 * alternative when collectors may also draw on the map.
 */
private fun geometryPlaceholderText(question: EditorQuestion, gpsAction: String): String =
  when (question.capture) {
    GeometryCapture.GPS_ONLY -> "$gpsAction (GPS)"
    GeometryCapture.GPS_OR_MAP -> "$gpsAction or draw on map"
  }

/**
 * Caption of a media question's mini widget: [captureAction] alone when collectors must capture a
 * fresh file, or the upload alternative when they may also pick one from their device.
 */
private fun mediaPlaceholderText(question: EditorQuestion, captureAction: String): String =
  when (question.mediaSource) {
    MediaSource.CAPTURE_ONLY -> captureAction
    MediaSource.CAPTURE_OR_UPLOAD -> "$captureAction or upload"
  }

@Composable
private fun MiniPlaceholder(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .height(64.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(colors.surfaceVariant),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = colors.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
  }
}

// ---------------------------------------------------------------------------------------------
// Properties panel
// ---------------------------------------------------------------------------------------------

/** Properties of the selected question, or of the Form itself when no question is selected. */
@Composable
private fun QuestionPropertiesPanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
  onOpenDataset: ((String) -> Unit)?,
  onCreateDataset: (() -> Unit)? = null,
  onUnlinkDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    val question = uiState.selectedQuestion
    val selectedSection = uiState.selectedSection
    if (question == null) {
      if (selectedSection != null) {
        SectionProperties(
          uiState = uiState,
          actions = actions,
          section = selectedSection,
        )
        return@ElevatedCard
      }
      FormProperties(
        uiState = uiState,
        actions = actions,
        onSaveToModeChange = onSaveToModeChange,
        onOpenDataset = onOpenDataset,
        onCreateDataset = onCreateDataset,
        onUnlinkDataset = onUnlinkDataset,
      )
      return@ElevatedCard
    }
    key(question.key) {
      QuestionProperties(
        uiState = uiState,
        actions = actions,
        question = question,
        onOpenDataset = onOpenDataset,
        onCreateFormForDataset = onCreateFormForDataset,
        onOpenForm = onOpenForm,
        onCreateChoiceDataset = onCreateChoiceDataset,
      )
    }
  }
}

@Composable
private fun QuestionProperties(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
  onOpenDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
) {
  val form = uiState.form
  val index = uiState.selectedIndex
  val key = question.key
  val issues = uiState.issuesFor(key)
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    var menuExpanded by remember { mutableStateOf(false) }
    var deleteWarning by remember { mutableStateOf<String?>(null) }
    deleteWarning?.let { message ->
      DeleteLinkedQuestionDialog(
        message = message,
        onConfirm = {
          deleteWarning = null
          actions.deleteQuestion(key)
        },
        onDismiss = { deleteWarning = null },
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Question ${index + 1} of ${form.questions.size}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.weight(1f))
      TextButton(
        onClick = { actions.select(form.questions.getOrNull(index - 1)?.key) },
        enabled = index > 0,
      ) {
        Icon(
          Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Previous")
      }
      TextButton(
        onClick = { actions.select(form.questions.getOrNull(index + 1)?.key) },
        enabled = index < form.questions.lastIndex,
      ) {
        Text("Next")
        Spacer(Modifier.width(4.dp))
        Icon(
          Icons.AutoMirrored.Outlined.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      }
      Box {
        IconButton(onClick = { menuExpanded = true }) {
          Icon(Icons.Outlined.MoreVert, contentDescription = "Question options")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
            text = { Text("Duplicate") },
            leadingIcon = {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            onClick = {
              menuExpanded = false
              actions.duplicateQuestion(key)
            },
          )
          DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
              Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
            },
            onClick = {
              menuExpanded = false
              // Linked questions feed reports: confirm first (softly; never blocked).
              val warning = uiState.deleteWarningFor(question)
              if (warning == null) actions.deleteQuestion(key) else deleteWarning = warning
            },
          )
        }
      }
    }

    if (issues.isNotEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          issues.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }
        }
      }
    }
    ConceptWarnings(uiState.warningsFor(key))

    DropdownSelector(
      label = "Question type",
      selectedText = question.type.label,
      options = EditorQuestionType.entries,
      optionText = { it.label },
      optionIcon = { questionTypeIcon(it) },
      selectedIcon = questionTypeIcon(question.type),
      onSelect = { actions.changeType(key, it) },
    )

    if (question.type.isGeometry) {
      GeometryCaptureSelector(uiState, actions, question)
    }
    if (question.type.isMedia) {
      MediaSourceSelector(uiState, actions, question)
    }

    ConceptLabelField(uiState, actions, question)
    ConceptLinkChip(uiState, actions, question)
    val activeLang = uiState.activeLanguage
    val defaultLang = uiState.defaultLanguage
    val isDefaultLang = uiState.isDefaultLanguageSelected
    val activeLangName = languageDisplayName(activeLang)
    OutlinedTextField(
      value = question.hintIn(activeLang, defaultLang),
      onValueChange = { v -> actions.updateQuestionHint(key, v) },
      label = {
        Text(if (isDefaultLang) "Hint (optional)" else "Hint ($activeLangName, optional)")
      },
      placeholder =
        if (!isDefaultLang && question.hint.isNotBlank()) ({ Text(question.hint) }) else null,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Required", style = MaterialTheme.typography.bodyLarge)
        Text(
          text =
            if (question.type.isReadOnly) "Notes can't be required."
            else "Collectors must answer before continuing.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = question.required,
        enabled = !question.type.isReadOnly,
        onCheckedChange = { v -> actions.updateQuestion(key) { it.copy(required = v) } },
      )
    }

    if (question.type.hasChoices) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      ChoicesEditor(
        uiState = uiState,
        actions = actions,
        question = question,
        onOpenDataset = onOpenDataset,
        onCreateFormForDataset = onCreateFormForDataset,
        onOpenForm = onOpenForm,
        onCreateChoiceDataset = onCreateChoiceDataset,
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    DisplayLogicEditor(uiState, actions, question)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AdvancedSection(uiState, actions, question)
  }
}

/**
 * Editor for configuring a selected [EditorSection]'s title, repeatability, count expression, and
 * conditional relevance.
 */
@Composable
private fun SectionProperties(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  section: EditorSection,
) {
  val form = uiState.form
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (section.repeatable) "Repeated section" else "Section",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Group of questions in the form flow",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { actions.deleteSection(section.id) }) {
          Icon(
            Icons.Outlined.Delete,
            contentDescription = "Delete section",
            tint = colors.error,
          )
        }
        IconButton(onClick = { actions.selectSection(null) }) {
          Icon(Icons.Outlined.Close, contentDescription = "Close properties")
        }
      }
    }

    HorizontalDivider(color = colors.outlineVariant)

    OutlinedTextField(
      value = section.title,
      onValueChange = { newTitle ->
        actions.updateSection(section.id) { it.copy(title = newTitle) }
      },
      label = { Text("Section title") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (section.repeatable) "Repeated section" else "Single section",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          text =
            if (section.repeatable) "Collectors can add multiple entries."
            else "Questions are visually and logically grouped.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Switch(
        checked = section.repeatable,
        onCheckedChange = { isRep ->
          actions.updateSection(section.id) { it.copy(repeatable = isRep) }
        },
      )
    }

    if (section.repeatable) {
      OutlinedTextField(
        value = section.repeatCountExpression,
        onValueChange = { expr ->
          actions.updateSection(section.id) { it.copy(repeatCountExpression = expr) }
        },
        label = { Text("Repeat count (optional expression)") },
        placeholder = { Text("e.g. 3 or /data/number_of_plots") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    HorizontalDivider(color = colors.outlineVariant)

    // Section Relevance (Group condition)
    val eligibleSources = form.questions.filter { it.sectionId != section.id }
    val relevance = section.relevance
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Section condition",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          text =
            if (relevance != null) "Section only shown when condition met."
            else "Section is always shown.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Switch(
        checked = relevance != null,
        onCheckedChange = { enabled ->
          if (enabled) {
            val firstSource = eligibleSources.firstOrNull()
            if (firstSource != null) {
              val op = RelevanceOperator.availableFor(firstSource.type).first()
              val defaultVal =
                firstSource.resolvedChoices(uiState.datasets).firstOrNull()?.value.orEmpty()
              actions.updateSection(section.id) {
                it.copy(
                  relevance =
                    EditorRelevance(
                      sourceQuestionKey = firstSource.key,
                      operator = op,
                      value = defaultVal,
                    )
                )
              }
            }
          } else {
            actions.updateSection(section.id) { it.copy(relevance = null) }
          }
        },
      )
    }

    if (relevance != null && eligibleSources.isNotEmpty()) {
      val sourceQ = form.questions.firstOrNull { it.key == relevance.sourceQuestionKey }
      DropdownSelector(
        label = "Depends on question",
        selectedText = sourceQ?.label?.ifBlank { sourceQ.name } ?: "(missing)",
        options = eligibleSources,
        optionText = { it.label.ifBlank { it.name } },
        onSelect = { picked ->
          val op =
            relevance.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
              ?: RelevanceOperator.availableFor(picked.type).first()
          actions.updateSection(section.id) {
            it.copy(relevance = EditorRelevance(picked.key, op, relevance.value))
          }
        },
      )
      if (sourceQ != null) {
        DropdownSelector(
          label = "Condition",
          selectedText = relevance.operator.label,
          options = RelevanceOperator.availableFor(sourceQ.type),
          optionText = { it.label },
          onSelect = { op ->
            actions.updateSection(section.id) {
              it.copy(relevance = relevance.copy(operator = op))
            }
          },
        )
        if (relevance.operator.needsValue) {
          val choices = sourceQ.resolvedChoices(uiState.datasets)
          if (choices.isNotEmpty()) {
            DropdownSelector(
              label = "Value",
              selectedText =
                choices
                  .firstOrNull { it.value == relevance.value }
                  ?.label
                  ?.ifBlank { relevance.value } ?: relevance.value.ifBlank { "(select value)" },
              options = choices,
              optionText = { it.label.ifBlank { it.value } },
              onSelect = { choice ->
                actions.updateSection(section.id) {
                  it.copy(relevance = relevance.copy(value = choice.value))
                }
              },
            )
          } else {
            OutlinedTextField(
              value = relevance.value,
              onValueChange = { v ->
                actions.updateSection(section.id) {
                  it.copy(relevance = relevance.copy(value = v))
                }
              },
              label = { Text("Value") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

/**
 * **Decimal places** setting of a [EditorQuestionType.NUMBER] question: `0` for whole numbers
 * (integer), or `1`..[EditorQuestion.MAX_DECIMAL_PLACES] for decimal numbers.
 */
@Composable
private fun DecimalPlacesSelector(actions: FormEditorActions, question: EditorQuestion) {
  OutlinedTextField(
    value = question.decimalPlaces.toString(),
    onValueChange = { v ->
      val parsed =
        if (v.isBlank()) 0
        else v.trim().toIntOrNull()?.coerceIn(0, EditorQuestion.MAX_DECIMAL_PLACES)
      if (parsed != null) {
        actions.updateDecimalPlaces(question.key, parsed)
      }
    },
    label = { Text("Decimal places") },
    supportingText = {
      Text(
        if (question.decimalPlaces <= 0) "0 for whole numbers (integer)."
        else
          "Up to ${question.decimalPlaces} decimal place${if (question.decimalPlaces == 1) "" else "s"}."
      )
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
}

private fun geometryCaptureLabel(type: EditorQuestionType, capture: GeometryCapture): String =
  when (capture) {
    GeometryCapture.GPS_ONLY ->
      when (type) {
        EditorQuestionType.POLYGON -> "Walk boundary (GPS every 5s)"
        EditorQuestionType.LINE -> "Walk path (GPS every 5s)"
        else -> GeometryCapture.GPS_ONLY.label
      }
    GeometryCapture.GPS_OR_MAP -> GeometryCapture.GPS_OR_MAP.label
  }

private fun geometryCaptureDescription(
  type: EditorQuestionType,
  capture: GeometryCapture,
): String =
  when (capture) {
    GeometryCapture.GPS_ONLY ->
      when (type) {
        EditorQuestionType.POLYGON ->
          "Collectors walk the perimeter on site; GPS vertices are recorded every 5s."
        EditorQuestionType.LINE ->
          "Collectors walk the path on site; GPS vertices are recorded every 5s."
        else -> GeometryCapture.GPS_ONLY.description
      }
    GeometryCapture.GPS_OR_MAP -> GeometryCapture.GPS_OR_MAP.description
  }

/**
 * **Capture** setting of a geometry question: GPS only, or GPS or draw on the map. Shows the chosen
 * mode's description and, when GPS only would leave the Form unanswerable in the web dashboard, the
 * incompatibility error (also listed in the question's issues).
 */
@Composable
private fun GeometryCaptureSelector(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    DropdownSelector(
      label = "Capture",
      selectedText = geometryCaptureLabel(question.type, question.capture),
      options = GeometryCapture.entries,
      optionText = { geometryCaptureLabel(question.type, it) },
      onSelect = { actions.updateCapture(question.key, it) },
    )
    Text(
      text = geometryCaptureDescription(question.type, question.capture),
      style = MaterialTheme.typography.bodySmall,
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (question.isWebIncompatible && uiState.form.availability.includesWeb) {
      Text(
        text = FormEditorValidator.webIncompatibleMessage(question),
        style = MaterialTheme.typography.bodySmall,
        color = colors.error,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }
  }
}

/**
 * **Media source** setting of a photo, video, or audio question: capture or upload (the ODK
 * default), or capture only (the `new` appearance). Shows the chosen mode's description.
 */
@Composable
private fun MediaSourceSelector(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    DropdownSelector(
      label = "Media source",
      selectedText = question.mediaSource.label,
      options = MediaSource.entries,
      optionText = { it.label },
      onSelect = { actions.updateMediaSource(question.key, it) },
    )
    Text(
      text = question.mediaSource.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
  }
}

/**
 * Collapsible section for settings most form authors don't need: the data column name and
 * validation rules. Collapsed by default; expands automatically when it contains an error.
 */
@Composable
private fun AdvancedSection(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val key = question.key
  val nameInvalid = !FormEditorValidator.isValidName(question.name)
  val hasError = nameInvalid || ValidationRules.issues(question).isNotEmpty()
  // Open/closed lives in the editor state so it persists across question selection.
  val expanded = uiState.isAdvancedExpanded
  LaunchedEffect(key, hasError) { if (hasError) actions.setAdvancedExpanded(true) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .clickable { actions.setAdvancedExpanded(!expanded) }
          .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SectionLabel("Advanced")
      if (hasError) {
        Icon(
          Icons.Outlined.Warning,
          contentDescription = "Advanced settings have errors",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp),
        )
      }
      Spacer(Modifier.weight(1f))
      Icon(
        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) "Collapse advanced" else "Expand advanced",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (expanded) {
      LinkToStandardFieldButton(actions, question)
      OutlinedTextField(
        value = question.name,
        onValueChange = { v -> actions.updateQuestion(key) { it.copy(name = v.trim()) } },
        label = { Text("Name") },
        singleLine = true,
        isError = nameInvalid,
        supportingText = { Text("Data column name. Letters, digits, _ . -") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )
      if (question.type.isNumeric) {
        DecimalPlacesSelector(actions, question)
      }
      ValidationEditor(uiState, actions, question)
    }
  }
}

/**
 * Structured validation rules, compiled to the XForms bind `constraint` and `jr:constraintMsg` (ODK
 * XForms spec, "Bindings"). Only rules that apply to the question type are offered.
 */
@Composable
private fun ValidationEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val key = question.key
  val type = question.type
  val validation = question.validation ?: EditorValidation()
  fun update(transform: (EditorValidation) -> EditorValidation) =
    actions.updateValidation(key, transform)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Validation", style = MaterialTheme.typography.titleSmall)
    when (ValidationKind.of(type)) {
      null ->
        Text(
          text = "No validation rules for this question type.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      ValidationKind.NUMBER_RANGE -> MinMaxFields("Minimum", "Maximum", validation, ::update)
      ValidationKind.TEXT -> {
        MinMaxFields("Min length", "Max length", validation, ::update)
        DropdownSelector(
          label = "Pattern",
          selectedText = validation.pattern?.label ?: "None",
          options = listOf<TextPattern?>(null) + TextPattern.entries,
          optionText = { it?.label ?: "None" },
          onSelect = { p -> update { it.copy(pattern = p) } },
          modifier = Modifier.fillMaxWidth(),
        )
        if (validation.pattern == TextPattern.CUSTOM) {
          OutlinedTextField(
            value = validation.customPattern,
            onValueChange = { v -> update { it.copy(customPattern = v) } },
            label = { Text("Regular expression") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      ValidationKind.DATE -> {
        DropdownSelector(
          label = "Allowed dates",
          selectedText = validation.dateRule?.label ?: "Any date",
          options = listOf<DateRule?>(null) + DateRule.entries,
          optionText = { it?.label ?: "Any date" },
          onSelect = { r ->
            update {
              if (r == DateRule.BETWEEN) it.copy(dateRule = r)
              else it.copy(dateRule = r, min = "", max = "")
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        if (validation.dateRule == DateRule.BETWEEN) {
          DateRangeFields(validation, ::update)
        }
      }
      ValidationKind.SELECTION_COUNT ->
        MinMaxFields("Minimum selections", "Maximum selections", validation, ::update)
    }
    if (ValidationRules.isActive(type, question.validation)) {
      val summary = ValidationRules.summary(type, question.validation).orEmpty()
      Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedTextField(
        value = validation.message,
        onValueChange = { v -> update { it.copy(message = v) } },
        label = { Text("Error message (optional)") },
        placeholder = { Text(summary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/**
 * Earliest / latest date for [DateRule.BETWEEN], chosen with M3 date pickers. Values are stored as
 * ISO `YYYY-MM-DD` (what XForms `date()` expects) and shown in a friendly format.
 */
@Composable
private fun DateRangeFields(
  validation: EditorValidation,
  update: ((EditorValidation) -> EditorValidation) -> Unit,
) {
  val minMillis = isoDateToUtcMillis(validation.min)
  val maxMillis = isoDateToUtcMillis(validation.max)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    DatePickerField(
      label = "Earliest date",
      iso = validation.min,
      // Keep earliest ≤ latest.
      isSelectable = { maxMillis == null || it <= maxMillis },
      onPick = { v -> update { it.copy(min = v) } },
    )
    DatePickerField(
      label = "Latest date",
      iso = validation.max,
      isSelectable = { minMillis == null || it >= minMillis },
      onPick = { v -> update { it.copy(max = v) } },
    )
  }
}

/**
 * Read-only field showing [iso] as e.g. "Mar 5, 2026"; the calendar icon opens a [DatePickerDialog]
 * and the clear icon unsets the date. [onPick] receives ISO `YYYY-MM-DD`, or "" when cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
  label: String,
  iso: String,
  isSelectable: (utcMillis: Long) -> Boolean,
  onPick: (String) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  OutlinedTextField(
    value = if (iso.isEmpty()) "" else friendlyDate(iso),
    onValueChange = {},
    readOnly = true,
    singleLine = true,
    label = { Text(label) },
    placeholder = { Text("Not set") },
    trailingIcon = {
      Row {
        if (iso.isNotEmpty()) {
          IconButton(onClick = { onPick("") }) {
            Icon(Icons.Outlined.Close, contentDescription = "Clear $label")
          }
        }
        IconButton(onClick = { open = true }) {
          Icon(Icons.Outlined.DateRange, contentDescription = "Pick $label")
        }
      }
    },
    modifier = Modifier.fillMaxWidth(),
  )
  if (open) {
    val pickerState =
      rememberDatePickerState(
        initialSelectedDateMillis = isoDateToUtcMillis(iso),
        selectableDates =
          object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = isSelectable(utcTimeMillis)
          },
      )
    DatePickerDialog(
      onDismissRequest = { open = false },
      confirmButton = {
        TextButton(
          onClick = {
            // DatePicker reports the selection as UTC midnight of the chosen day.
            pickerState.selectedDateMillis?.let { onPick(utcMillisToIsoDate(it)) }
            open = false
          },
          enabled = pickerState.selectedDateMillis != null,
        ) {
          Text("OK")
        }
      },
      dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    ) {
      DatePicker(state = pickerState)
    }
  }
}

/** A pair of side-by-side fields editing [EditorValidation.min] and [EditorValidation.max]. */
@Composable
private fun MinMaxFields(
  minLabel: String,
  maxLabel: String,
  validation: EditorValidation,
  update: ((EditorValidation) -> EditorValidation) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = validation.min,
      onValueChange = { v -> update { it.copy(min = v.trim()) } },
      label = { Text(minLabel) },
      singleLine = true,
      modifier = Modifier.weight(1f),
    )
    OutlinedTextField(
      value = validation.max,
      onValueChange = { v -> update { it.copy(max = v.trim()) } },
      label = { Text(maxLabel) },
      singleLine = true,
      modifier = Modifier.weight(1f),
    )
  }
}

private enum class ChoiceVisualOption(val label: String) {
  NONE("Text only"),
  COLOR("Colors"),
  IMAGE("Images"),
}

private fun choiceSourceIcon(source: ChoiceSource): ImageVector =
  when (source) {
    ChoiceSource.MANUAL -> Icons.AutoMirrored.Outlined.List
    ChoiceSource.MAP_LAYER -> Icons.Outlined.Layers
    ChoiceSource.DATA_TABLE -> Icons.Outlined.TableChart
  }

private fun choiceVisualIcon(visual: ChoiceVisualOption): ImageVector =
  when (visual) {
    ChoiceVisualOption.NONE -> Icons.Outlined.TextFields
    ChoiceVisualOption.COLOR -> Icons.Outlined.Palette
    ChoiceVisualOption.IMAGE -> Icons.Outlined.Image
  }

@Composable
private fun ChoicesEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
  onOpenDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
) {
  val key = question.key
  val colors = MaterialTheme.colorScheme
  val effectiveSource = uiState.choiceSourceFor(question)
  val questionIndex = uiState.form.questions.indexOfFirst { it.key == question.key }
  var imageError by remember { mutableStateOf<String?>(null) }
  var importDialogChoices by remember(key) { mutableStateOf<List<EditorChoice>?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("Choices")
      if (effectiveSource == ChoiceSource.MANUAL) {
        TextButton(
          onClick = {
            openPlatformTextFilePicker(".csv,.tsv,.txt,text/csv,text/plain") { result ->
              if (result is TextFilePickResult.Picked) {
                val parsed = ChoiceListImporter.parseChoices(result.text, result.fileName)
                if (parsed.isNotEmpty()) {
                  val isPlaceholder =
                    question.choices.size == 2 &&
                      question.choices[0].value == "option_1" &&
                      question.choices[1].value == "option_2"
                  if (question.choices.isEmpty() || isPlaceholder) {
                    actions.importQuestionChoices(key, parsed, append = false)
                  } else {
                    importDialogChoices = parsed
                  }
                }
              }
            }
          }
        ) {
          Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Import (.csv / .txt)")
        }
      }
    }

    importDialogChoices?.let { imported ->
      AlertDialog(
        onDismissRequest = { importDialogChoices = null },
        title = { Text("Import choices") },
        text = {
          Text(
            "Found ${imported.size} choices in file. Would you like to replace your current choices (${question.choices.size}) or append to them?"
          )
        },
        confirmButton = {
          Button(
            onClick = {
              actions.importQuestionChoices(key, imported, append = false)
              importDialogChoices = null
            }
          ) {
            Text("Replace")
          }
        },
        dismissButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importDialogChoices = null }) {
              Text("Cancel")
            }
            FilledTonalButton(
              onClick = {
                actions.importQuestionChoices(key, imported, append = true)
                importDialogChoices = null
              }
            ) {
              Text("Append")
            }
          }
        },
      )
    }

    DropdownSelector(
      label = "Choices",
      selectedText = effectiveSource.label,
      options = ChoiceSource.entries,
      optionText = { it.label },
      optionIcon = ::choiceSourceIcon,
      selectedIcon = choiceSourceIcon(effectiveSource),
      onSelect = { source ->
        actions.setChoiceSource(key, source)
        // With no map layers or data tables of that kind yet, go straight to creating one (the
        // same "start empty or import" dialog as the survey editor).
        if (source.isDataset && uiState.choiceDatasetsFor(source).isEmpty()) {
          val kind =
            if (source == ChoiceSource.MAP_LAYER) DatasetKind.MAP_LAYER else DatasetKind.DATA_TABLE
          if (onCreateChoiceDataset != null) {
            onCreateChoiceDataset(kind, key)
          } else if (questionIndex >= 0) {
            actions.createDatasetForChoiceQuestion(questionIndex, kind)
          }
        }
      },
    )
    when (effectiveSource) {
      ChoiceSource.MANUAL -> {
        val hasImages = question.choices.any { it.image != null }
        val hasColors = question.choices.any { it.colorHex != null }
        var visualOverride by remember(question.key) { mutableStateOf<ChoiceVisualOption?>(null) }
        val currentVisual =
          visualOverride
            ?: when {
              hasImages -> ChoiceVisualOption.IMAGE
              hasColors -> ChoiceVisualOption.COLOR
              else -> ChoiceVisualOption.NONE
            }

        // In the Color style every choice has a color, including ones added or imported later.
        if (currentVisual == ChoiceVisualOption.COLOR) {
          val missingColors = question.choices.count { it.colorHex == null }
          LaunchedEffect(key, missingColors) {
            if (missingColors > 0) {
              actions.updateQuestion(key) { q ->
                q.copy(
                  choices =
                    q.choices.mapIndexed { idx, c ->
                      if (c.colorHex != null) c
                      else
                        c.copy(
                          colorHex = ChoiceColors.palette[idx % ChoiceColors.palette.size].second,
                          image = null,
                        )
                    }
                )
              }
            }
          }
        }

        DropdownSelector(
          label = "Visual style",
          selectedText = currentVisual.label,
          options = ChoiceVisualOption.entries,
          optionText = { it.label },
          optionIcon = ::choiceVisualIcon,
          selectedIcon = choiceVisualIcon(currentVisual),
          onSelect = { visual ->
            visualOverride = visual
            when (visual) {
              ChoiceVisualOption.NONE -> {
                actions.updateQuestion(key) { q ->
                  q.copy(choices = q.choices.map { it.copy(colorHex = null, image = null) })
                }
              }
              ChoiceVisualOption.COLOR -> {
                actions.updateQuestion(key) { q ->
                  q.copy(
                    choices =
                      q.choices.mapIndexed { idx, c ->
                        val color =
                          c.colorHex ?: ChoiceColors.palette[idx % ChoiceColors.palette.size].second
                        c.copy(colorHex = color, image = null)
                      }
                  )
                }
              }
              ChoiceVisualOption.IMAGE -> {
                actions.updateQuestion(key) { q ->
                  q.copy(choices = q.choices.map { it.copy(colorHex = null) })
                }
              }
            }
          },
        )

        val precedingChoiceQuestions =
          if (questionIndex > 0) {
            uiState.form.questions.take(questionIndex).filter { it.type.hasChoices }
          } else {
            emptyList()
          }

        var parentChoiceFilter by
          remember(key, question.parentQuestionKey) {
            mutableStateOf<String?>(null)
          }

        val selectedParent = precedingChoiceQuestions.firstOrNull {
          it.key == question.parentQuestionKey || it.name == question.parentQuestionKey
        }

        if (precedingChoiceQuestions.isNotEmpty()) {
          DropdownSelector(
            label = "Cascading filter (parent question)",
            selectedText = selectedParent?.label?.ifBlank { selectedParent.name } ?: "None",
            options = listOf(null) + precedingChoiceQuestions,
            optionText = { it?.label?.ifBlank { it.name } ?: "None" },
            onSelect = { selected ->
              actions.updateQuestionParentKey(key, selected?.key ?: "")
              parentChoiceFilter = null
            },
          )
        }

        if (selectedParent != null) {
          val parentChoices = selectedParent.resolvedChoices(uiState.datasets)
          if (parentChoices.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Filter:",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
              )
              GroundFilterChip(
                selected = parentChoiceFilter == null,
                onClick = { parentChoiceFilter = null },
                label = { Text("All") },
              )
              parentChoices.forEach { pChoice ->
                GroundFilterChip(
                  selected = parentChoiceFilter == pChoice.value,
                  onClick = {
                    parentChoiceFilter =
                      if (parentChoiceFilter == pChoice.value) null else pChoice.value
                  },
                  label = { Text(pChoice.label.ifBlank { pChoice.value }) },
                )
              }
            }
          }
        }

        // A linked question maps each choice to a value of the concept's code list.
        val concept = uiState.conceptOf(question)?.takeIf { it.codeList.isNotEmpty() }
        val activeLang = uiState.activeLanguage
        val defaultLang = uiState.defaultLanguage
        val isDefaultLang = uiState.isDefaultLanguageSelected
        val activeLangName = languageDisplayName(activeLang)

        val indexedChoices = question.choices.mapIndexed { idx, c -> idx to c }
        val visibleChoices =
          if (selectedParent != null && parentChoiceFilter != null) {
            indexedChoices.filter { (_, c) -> c.parentValue == parentChoiceFilter }
          } else {
            indexedChoices
          }

        visibleChoices.forEach { (i, choice) ->
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(modifier = Modifier.padding(top = 12.dp)) {
              when (currentVisual) {
                ChoiceVisualOption.NONE -> ChoiceTextStyleIndicator()
                ChoiceVisualOption.COLOR ->
                  ChoiceColorButton(
                    colorHex = choice.colorHex,
                    onSelect = { actions.setChoiceColor(key, i, it) },
                  )
                ChoiceVisualOption.IMAGE ->
                  ChoiceImageButton(
                    image = choice.image,
                    onPicked = { image ->
                      imageError =
                        if (actions.setChoiceImage(key, i, image)) null
                        else "Choose an image file under ${EditorChoiceImage.MAX_BYTES / 1024} KB."
                    },
                    onRemove = { actions.setChoiceImage(key, i, null) },
                    onError = { imageError = it },
                  )
              }
            }
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              OutlinedTextField(
                value = choice.labelIn(activeLang, defaultLang),
                onValueChange = { actions.updateChoiceLabel(key, i, it) },
                label = { Text(if (isDefaultLang) "Label" else "Label ($activeLangName)") },
                placeholder =
                  if (!isDefaultLang && choice.label.isNotBlank()) ({ Text(choice.label) })
                  else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              if (selectedParent != null) {
                val parentChoices = selectedParent.resolvedChoices(uiState.datasets)
                if (parentChoices.isNotEmpty()) {
                  DropdownSelector(
                    label =
                      "Parent choice (${selectedParent.label.ifBlank { selectedParent.name }})",
                    selectedText =
                      parentChoices.firstOrNull { it.value == choice.parentValue }?.label
                        ?: choice.parentValue.ifBlank { "Any (unfiltered)" },
                    options = listOf(null) + parentChoices,
                    optionText = { it?.label ?: "Any (unfiltered)" },
                    onSelect = { selected ->
                      actions.updateChoiceParentValue(key, i, selected?.value ?: "")
                    },
                  )
                } else {
                  OutlinedTextField(
                    value = choice.parentValue,
                    onValueChange = { actions.updateChoiceParentValue(key, i, it) },
                    label = {
                      Text("Parent value (${selectedParent.label.ifBlank { selectedParent.name }})")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
              if (concept != null) {
                ChoiceConceptLinkChip(
                  concept = concept,
                  choice = choice,
                  language = uiState.activeLanguage,
                  onSelect = { actions.setChoiceCode(key, i, it) },
                )
              }
            }
            IconButton(
              onClick = { actions.removeChoice(key, i) },
              modifier = Modifier.padding(top = 12.dp).size(32.dp),
            ) {
              Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove choice",
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
        imageError?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
          )
        }
        TextButton(
          onClick = {
            actions.addChoice(key)
            val assignedParentVal = parentChoiceFilter
            if (assignedParentVal != null) {
              actions.updateChoiceParentValue(key, question.choices.size, assignedParentVal)
            }
            if (currentVisual == ChoiceVisualOption.COLOR) {
              actions.updateQuestion(key) { q ->
                val lastIdx = q.choices.lastIndex
                if (lastIdx >= 0) {
                  val color = ChoiceColors.palette[lastIdx % ChoiceColors.palette.size].second
                  q.copy(
                    choices =
                      q.choices.mapIndexed { idx, c ->
                        if (idx == lastIdx && c.colorHex == null)
                          c.copy(colorHex = color, image = null)
                        else c
                      }
                  )
                } else {
                  q
                }
              }
            }
          }
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add choice")
        }
      }
      ChoiceSource.MAP_LAYER,
      ChoiceSource.DATA_TABLE -> {
        val availableDatasets = uiState.choiceDatasetsFor(effectiveSource)
        val selectedDataset =
          uiState.choiceDatasetFor(question)?.takeIf {
            it.isMapLayer == (effectiveSource == ChoiceSource.MAP_LAYER)
          }
        val pluralKind =
          if (effectiveSource == ChoiceSource.MAP_LAYER) "map layers" else "data tables"
        val singularKind =
          if (effectiveSource == ChoiceSource.MAP_LAYER) "map layer" else "data table"
        val targetKind =
          if (effectiveSource == ChoiceSource.MAP_LAYER) DatasetKind.MAP_LAYER
          else DatasetKind.DATA_TABLE

        if (availableDatasets.isEmpty() && question.choiceDatasetId.isNullOrBlank()) {
          Text(
            text = "No $pluralKind are available in this survey yet.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = {
              if (onCreateChoiceDataset != null) {
                onCreateChoiceDataset(targetKind, key)
              } else if (questionIndex >= 0) {
                actions.createDatasetForChoiceQuestion(questionIndex, targetKind)
              }
            }
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New $singularKind")
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (availableDatasets.isNotEmpty()) {
              Box(modifier = Modifier.weight(1f)) {
                DropdownSelector(
                  label = effectiveSource.label,
                  selectedText =
                    selectedDataset?.displayName
                      ?: question.choiceDatasetId?.takeIf { it.isNotBlank() }
                      ?: "Choose",
                  options = availableDatasets,
                  optionText = { it.displayName },
                  optionIcon = ::datasetIcon,
                  onSelect = { actions.setChoiceDataset(key, it.id) },
                )
              }
            }
            OutlinedButton(
              onClick = {
                if (onCreateChoiceDataset != null) {
                  onCreateChoiceDataset(targetKind, key)
                } else if (questionIndex >= 0) {
                  actions.createDatasetForChoiceQuestion(questionIndex, targetKind)
                }
              }
            ) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("New $singularKind")
            }
          }
          if (selectedDataset != null) {
            val count = selectedDataset.rows.size
            val noun =
              if (count == 1) selectedDataset.featureNoun else selectedDataset.featureNounPlural
            Text(
              text = "Data collectors choose from $count $noun in ${selectedDataset.displayName}.",
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
            )
            if (onOpenDataset != null) {
              AssistChip(
                onClick = { onOpenDataset(selectedDataset.id) },
                label = { Text(selectedDataset.displayName) },
                leadingIcon = {
                  Icon(
                    datasetIcon(selectedDataset),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                },
              )
            }
            AllowAddEntityControl(
              question = question,
              dataset = selectedDataset,
              actions = actions,
              onCreateFormForDataset = onCreateFormForDataset,
              onOpenForm = onOpenForm,
            )
          } else if (!question.choiceDatasetId.isNullOrBlank()) {
            Text(
              text = "Choices are loaded from external list \"${question.choiceDatasetId}\".",
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

/**
 * Lets the form designer allow collectors to add a new map feature or table row inline while
 * selecting from [dataset]. Only datasets with a creation Form (or for which one can be generated)
 * support adding items during collection; generated sample plots cannot be added by collectors.
 */
@Composable
private fun AllowAddEntityControl(
  question: EditorQuestion,
  dataset: EditorDataset,
  actions: FormEditorActions,
  onCreateFormForDataset: ((String) -> Unit)?,
  onOpenForm: ((String) -> Unit)?,
) {
  val colors = MaterialTheme.colorScheme
  val key = question.key
  when {
    dataset.isGenerated -> {
      Text(
        text = "Sample plots are generated from a sample design and can't be added by collectors.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
      )
    }
    dataset.hasCreationForm || (dataset.key.isEmpty() && dataset.linkedFormKey == null) -> {
      val formLabel = dataset.linkedFormTitle?.takeIf { it.isNotBlank() }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Allow collectors to add new ${dataset.featureNounPlural}",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text =
              if (formLabel != null) {
                "Uses $formLabel to add new ${dataset.featureNounPlural}."
              } else {
                "Collectors can add a new ${dataset.featureNoun} while selecting."
              },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
        }
        Switch(
          checked = question.allowAddEntity,
          onCheckedChange = { actions.setAllowAddEntity(key, it) },
        )
      }
      val linkedFormKey = dataset.linkedFormKey
      if (onOpenForm != null && linkedFormKey != null) {
        AssistChip(
          onClick = { onOpenForm(linkedFormKey) },
          label = { Text(formLabel ?: "Open form") },
          leadingIcon = {
            Icon(
              Icons.Outlined.Description,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
        )
      }
    }
    dataset.canCreateForm -> {
      Surface(
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Allow collectors to add new ${dataset.featureNounPlural}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          Text(
            text =
              "${dataset.displayName} doesn't have a form for adding new ${dataset.featureNounPlural} yet.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
          if (onCreateFormForDataset != null && dataset.key.isNotEmpty()) {
            OutlinedButton(
              onClick = {
                onCreateFormForDataset(dataset.key)
                actions.setAllowAddEntity(key, true)
              }
            ) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Create form for ${dataset.displayName}")
            }
          }
        }
      }
    }
  }
}

/**
 * Non-interactive marker shown beside each choice when the question's visual style is text only.
 * Colors are picked only in the "Color" visual style.
 */
@Composable
private fun ChoiceTextStyleIndicator() {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color = colors.surfaceContainerHigh,
    border = BorderStroke(1.dp, colors.outlineVariant),
    modifier = Modifier.size(32.dp).semantics { contentDescription = "Text choice" },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        Icons.Outlined.TextFields,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

/**
 * Compact linked-concept chip rendered directly underneath each choice's label field when the
 * parent select question is linked to a standard dictionary concept with a code list.
 */
@Composable
private fun ChoiceConceptLinkChip(
  concept: LibraryConcept,
  choice: EditorChoice,
  language: String,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val matched = concept.codeList.firstOrNull { it.code == choice.code }
  val isLinked = choice.code != null
  val chipText =
    when {
      matched != null -> "${matched.label.get(language)} (${matched.code})"
      choice.code != null -> "Code: ${choice.code}"
      else -> "Link to standard choice…"
    }

  Box(modifier = modifier) {
    Surface(
      onClick = { expanded = true },
      shape = RoundedCornerShape(8.dp),
      color =
        if (isLinked) colors.secondaryContainer.copy(alpha = 0.65f) else colors.surfaceContainerLow,
      contentColor = if (isLinked) colors.onSecondaryContainer else colors.onSurfaceVariant,
      border =
        BorderStroke(
          1.dp,
          if (isLinked) colors.secondary.copy(alpha = 0.4f) else colors.outlineVariant,
        ),
      modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Outlined.Link,
          contentDescription = null,
          tint = if (isLinked) colors.primary else colors.onSurfaceVariant,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = chipText,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isLinked) FontWeight.Medium else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          Icons.Outlined.ExpandMore,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text("Not linked") },
        onClick = {
          onSelect(null)
          expanded = false
        },
      )
      concept.codeList.forEach { item ->
        val itemLabel = "${item.label.get(language)} (${item.code})"
        val isSelected = item.code == choice.code
        DropdownMenuItem(
          text = { Text(itemLabel) },
          leadingIcon = {
            if (isSelected) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
              )
            } else {
              Icon(
                Icons.Outlined.Link,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
          },
          onClick = {
            onSelect(item.code)
            expanded = false
          },
        )
      }
    }
  }
}

/** Color dot for a choice; opens a compact palette. Every choice has a color in this style. */
@Composable
private fun ChoiceColorButton(colorHex: String?, onSelect: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  Box {
    IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
      ColorDot(
        colorHex = colorHex,
        contentDescription = "Choice color: ${ChoiceColors.nameOf(colorHex) ?: colorHex ?: "none"}",
      )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      Text(
        text = "Choice color",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
      ChoiceColors.palette.chunked(5).forEach { row ->
        Row(
          modifier = Modifier.padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          row.forEach { (name, hex) ->
            IconButton(
              onClick = {
                onSelect(hex)
                expanded = false
              },
              modifier = Modifier.size(36.dp),
            ) {
              ColorDot(
                colorHex = hex,
                contentDescription = name,
                isSelected = hex.equals(colorHex, ignoreCase = true),
              )
            }
          }
        }
      }
    }
  }
}

/** Filled swatch for [colorHex], or an outlined empty circle when there is no color. */
@Composable
internal fun ColorDot(
  colorHex: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  size: Dp = 20.dp,
) {
  val colors = MaterialTheme.colorScheme
  val fill = ChoiceColors.argb(colorHex)?.let { Color(it) }
  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(fill ?: Color.Transparent)
        .border(
          width = if (isSelected) 2.dp else 1.dp,
          color = if (isSelected) colors.onSurface else colors.outline,
          shape = CircleShape,
        )
        .semantics { contentDescription?.let { this.contentDescription = it } },
    contentAlignment = Alignment.Center,
  ) {
    if (isSelected) {
      val checkTint =
        when {
          fill == null -> colors.onSurface
          fill.luminance() > 0.6f -> Color(0xFF181D18)
          else -> Color.White
        }
      Icon(
        Icons.Outlined.Check,
        contentDescription = null,
        tint = checkTint,
        modifier = Modifier.size(size * 0.7f),
      )
    }
  }
}

/**
 * Attaches a small image to a choice using the platform picker (downscaled to
 * [EditorChoiceImage.MAX_PIXELS] on the long edge). Shows a thumbnail with Replace / Remove once
 * set.
 */
@Composable
private fun ChoiceImageButton(
  image: EditorChoiceImage?,
  onPicked: (EditorChoiceImage) -> Unit,
  onRemove: () -> Unit,
  onError: (String) -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val pick = {
    openPlatformMediaPicker(
      accept = "image/*",
      capture = "",
      maxPixels = EditorChoiceImage.MAX_PIXELS,
    ) { result ->
      when (result) {
        is PlatformPickResult.Picked -> {
          val file = result.file
          val mimeType =
            file.mimeType.ifBlank { MediaCapture.mimeTypeForFileName(file.fileName).orEmpty() }
          onPicked(EditorChoiceImage(mimeType, file.base64))
        }
        is PlatformPickResult.Failed -> onError(result.message)
        PlatformPickResult.Cancelled -> Unit
      }
    }
  }
  if (image == null) {
    IconButton(
      onClick = pick,
      enabled = isPlatformMediaPickerAvailable,
      modifier = Modifier.size(32.dp),
    ) {
      Icon(
        Icons.Outlined.AddPhotoAlternate,
        contentDescription = "Add choice image",
        modifier = Modifier.size(20.dp),
      )
    }
    return
  }
  Box {
    val bitmap = rememberChoiceImageBitmap(image)
    Surface(
      onClick = { menuExpanded = true },
      shape = MaterialTheme.shapes.extraSmall,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.size(32.dp),
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = "Choice image",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.Photo,
            contentDescription = "Choice image",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
      DropdownMenuItem(
        text = { Text("Replace image") },
        leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
        enabled = isPlatformMediaPickerAvailable,
        onClick = {
          menuExpanded = false
          pick()
        },
      )
      DropdownMenuItem(
        text = { Text("Remove image") },
        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        onClick = {
          menuExpanded = false
          onRemove()
        },
      )
    }
  }
}

/** Decodes [image] for display, or `null` if the bytes aren't a decodable bitmap. */
@OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
@Composable
internal fun rememberChoiceImageBitmap(image: EditorChoiceImage): ImageBitmap? =
  remember(image) { runCatching { Base64.decode(image.base64).decodeToImageBitmap() }.getOrNull() }

@Composable
private fun DisplayLogicEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val form = uiState.form
  val key = question.key
  val sources = form.eligibleRelevanceSources(key)
  val relevance = question.relevance
  var showFirstQuestionNotice by
    remember(key, sources.size, relevance != null) {
      mutableStateOf(false)
    }
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionLabel("Display logic")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      SegmentedButton(
        selected = relevance == null && !showFirstQuestionNotice,
        onClick = {
          showFirstQuestionNotice = false
          actions.disableRelevance(key)
        },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        label = { Text("Always show") },
      )
      SegmentedButton(
        selected = relevance != null || showFirstQuestionNotice,
        onClick = {
          if (sources.isNotEmpty()) {
            showFirstQuestionNotice = false
            if (relevance == null) actions.enableRelevance(key)
          } else if (relevance == null) {
            showFirstQuestionNotice = true
          }
        },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        label = { Text("Show only if…") },
      )
    }
    if (sources.isEmpty() && relevance == null) {
      Surface(
        color =
          if (showFirstQuestionNotice) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor =
          if (showFirstQuestionNotice) colors.onSecondaryContainer else colors.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        border =
          BorderStroke(
            1.dp,
            if (showFirstQuestionNotice) colors.secondary else colors.outlineVariant,
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Outlined.Info,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "First screen in the form",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text =
              "Display logic can depend on earlier questions. Move this question later or select a later question to configure conditional visibility.",
            style = MaterialTheme.typography.bodySmall,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (form.questions.size > 1) {
              TextButton(
                onClick = {
                  showFirstQuestionNotice = false
                  actions.select(form.questions[1].key)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Text("Select Question 2 →")
              }
              TextButton(
                onClick = {
                  showFirstQuestionNotice = false
                  actions.moveQuestion(key, 1)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Text("Move question down")
              }
            } else {
              TextButton(
                onClick = {
                  showFirstQuestionNotice = false
                  actions.addQuestion(EditorQuestionType.TEXT, 1)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Text("Add Question 2")
              }
            }
          }
        }
      }
    }
    if (relevance != null) {
      val source = form.find(relevance.sourceQuestionKey)
      DropdownSelector(
        label = "Question",
        selectedText = source?.let { it.label.ifBlank { it.name } } ?: "(missing question)",
        options = sources,
        optionText = { it.label.ifBlank { it.name } },
        onSelect = { picked ->
          val operator =
            relevance.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
              ?: RelevanceOperator.availableFor(picked.type).first()
          val value = picked.resolvedChoices(uiState.datasets).firstOrNull()?.value.orEmpty()
          actions.updateQuestion(key) {
            it.copy(relevance = EditorRelevance(picked.key, operator, value))
          }
        },
      )
      if (source != null) {
        DropdownSelector(
          label = "Condition",
          selectedText = relevance.operator.label,
          options = RelevanceOperator.availableFor(source.type),
          optionText = { it.label },
          onSelect = { op ->
            actions.updateQuestion(key) { it.copy(relevance = relevance.copy(operator = op)) }
          },
        )
        if (relevance.operator.needsValue) {
          val sourceChoices = source.resolvedChoices(uiState.datasets)
          if (source.type.hasChoices && sourceChoices.isNotEmpty()) {
            DropdownSelector(
              label = "Value",
              selectedText =
                sourceChoices.firstOrNull { it.value == relevance.value }?.label
                  ?: relevance.value.ifBlank { "Pick a choice" },
              options = sourceChoices,
              optionText = { it.label },
              onSelect = { c ->
                actions.updateQuestion(key) { it.copy(relevance = relevance.copy(value = c.value)) }
              },
            )
          } else {
            OutlinedTextField(
              value = relevance.value,
              onValueChange = { v ->
                actions.updateQuestion(key) { it.copy(relevance = relevance.copy(value = v)) }
              },
              label = { Text("Value") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      form.relevanceSummary(question, uiState.datasets)?.let { summary ->
        Text(
          text = summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
internal fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownSelector(
  label: String,
  selectedText: String,
  options: List<T>,
  optionText: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  optionIcon: ((T) -> ImageVector)? = null,
  enabled: Boolean = true,
  supportingText: String? = null,
  selectedIcon: ImageVector? = null,
) {
  var expanded by remember { mutableStateOf(false) }
  // Standard M3 exposed dropdown: a read-only OutlinedTextField, so the label and value use the
  // same typography as every other input in the editors.
  ExposedDropdownMenuBox(
    expanded = expanded && enabled,
    onExpandedChange = { if (enabled) expanded = it },
    modifier = modifier.fillMaxWidth(),
  ) {
    Box {
      OutlinedTextField(
        value = selectedText,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        leadingIcon =
          selectedIcon?.let { icon ->
            {
              Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          },
        supportingText = supportingText?.let { text -> { Text(text) } },
        trailingIcon = {
          ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
        },
        modifier =
          Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
      )
      // Opens the menu on the first click, even if the mouse moves slightly during it.
      DropdownFieldClickTarget(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it },
        enabled = enabled,
      )
    }
    ExposedDropdownMenu(
      expanded = expanded && enabled,
      onDismissRequest = { expanded = false },
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(optionText(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon =
            optionIcon?.invoke(option)?.let { icon ->
              {
                Icon(
                  icon,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
          onClick = {
            onSelect(option)
            expanded = false
          },
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Overlays: flow preview
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModalScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
          ) {},
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}

@Composable
private fun FormPreviewOverlay(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  isDarkTheme: Boolean,
) {
  val controller = uiState.previewController
  var formFactor by remember { mutableStateOf(DeviceFormFactor.MOBILE) }
  var orientation by remember { mutableStateOf(DeviceFormFactor.MOBILE.defaultOrientation) }
  var previewDarkTheme by remember(isDarkTheme) { mutableStateOf(isDarkTheme) }
  ModalScrim(onDismiss = actions::closePreview) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val maxPanelHeight = (maxHeight - 48.dp).coerceAtLeast(320.dp)
      Row(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
      ) {
        if (controller != null && uiState.previewTarget == FormPreviewTarget.WEB) {
          WebPreviewBrowserFrame(uiState = uiState, actions = actions, controller = controller)
        } else if (controller != null) {
          DevicePreviewFrame(
            deviceTitle = "Preview • ${uiState.form.title}",
            isDarkTheme = previewDarkTheme,
            formFactor = formFactor,
            orientation = orientation,
            onSelectFormFactor = {
              formFactor = it
              orientation = it.defaultOrientation
            },
            onRotateDevice = {
              orientation =
                if (orientation == DeviceOrientation.PORTRAIT) DeviceOrientation.LANDSCAPE
                else DeviceOrientation.PORTRAIT
            },
            onToggleDarkTheme = { previewDarkTheme = !previewDarkTheme },
          ) {
            key(controller) {
              MobileFormRunner(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                onClose = actions::closePreview,
                onSubmitted = { actions.markPreviewSubmitted() },
              )
            }
          }
        }
        PreviewSidePanel(
          uiState = uiState,
          actions = actions,
          modifier = Modifier.heightIn(max = maxPanelHeight),
        )
      }
    }
  }
}

@Composable
private fun PreviewSidePanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  val controller = uiState.previewController
  val colors = MaterialTheme.colorScheme
  ElevatedCard(modifier = modifier.width(340.dp)) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "Flow preview",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions::closePreview) {
          Icon(Icons.Outlined.Close, contentDescription = "Close preview")
        }
      }
      Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (uiState.supportedLanguages.size > 1) {
          DropdownSelector(
            label = "Language",
            selectedText = languageDisplayName(uiState.activeLanguage),
            options = uiState.supportedLanguages,
            optionText = { code ->
              val name = languageDisplayName(code)
              if (code == uiState.defaultLanguage) "$name • Default" else name
            },
            onSelect = { actions.selectLanguage(it) },
          )
        }
        val error = uiState.previewError
        if (error != null) {
          Text(
            text = "The generated XForms couldn't be loaded:\n$error",
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
          )
        }
        if (controller != null) {
          Text(
            text =
              if (uiState.previewTarget == FormPreviewTarget.WEB) {
                "Questions update live as you answer: conditional cards appear or disappear based on display logic."
              } else {
                "Screens update live as you answer: conditional questions appear or disappear based on display logic."
              },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
          if (uiState.previewSubmitted) {
            Surface(
              color = colors.primaryContainer,
              contentColor = colors.onPrimaryContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Filled: indicates the validation-passed uiState.
                Icon(
                  Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = colors.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  "Submission passed validation.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.onPrimaryContainer,
                )
              }
            }
          }
          SectionLabel("Current path")
          controller.steps.forEachIndexed { i, step ->
            val isCurrent = i == controller.currentStepIndex
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.small)
                  .background(if (isCurrent) colors.secondaryContainer else Color.Transparent)
                  .clickable { controller.jumpToStep(i) }
                  .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (step is FormWizardStep.SummaryStep) "✓" else "${i + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) colors.onSecondaryContainer else colors.primary,
                modifier = Modifier.width(24.dp),
              )
              Text(
                text = step.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) colors.onSecondaryContainer else colors.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions::restartPreview) {
          Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Restart")
        }
        Button(onClick = actions::closePreview) { Text("Back to editor") }
      }
    }
  }
}
