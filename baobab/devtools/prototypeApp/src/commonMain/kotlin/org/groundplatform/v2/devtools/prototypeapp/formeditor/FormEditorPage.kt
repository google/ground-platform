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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerEventType
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.devtools.prototypeapp.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.MobileDevicePreviewFrame
import org.groundplatform.v2.devtools.prototypeapp.PlatformPickResult
import org.groundplatform.v2.devtools.prototypeapp.SidePanelSeparator
import org.groundplatform.v2.devtools.prototypeapp.SidePanelSeparatorWidth
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceColors
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DateRule
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoiceImage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorRelevance
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorValidation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FlowEdge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FlowEdgeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormFlowGraph
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryCapture
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.MediaSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.TextPattern
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ValidationKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ValidationRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.friendlyDate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoDateToUtcMillis
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.utcMillisToIsoDate
import org.groundplatform.v2.devtools.prototypeapp.isPlatformMediaPickerAvailable
import org.groundplatform.v2.devtools.prototypeapp.openPlatformMediaPicker
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.FormEditorActions
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private val CardWidth = 184.dp
private val CardHeight = 312.dp
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
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      FormEditorToolbar(
        uiState,
        actions,
        onCreateDataset.takeIf { uiState.form.saveTo.mode == SaveToMode.CREATE },
        onDelete,
      )
      Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        when (uiState.previewTarget) {
          FormPreviewTarget.MOBILE ->
            FlowCanvasPanel(uiState, actions, Modifier.weight(1f).fillMaxHeight())
          FormPreviewTarget.WEB ->
            WebLayoutCanvasPanel(uiState, actions, Modifier.weight(1f).fillMaxHeight())
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
          modifier = Modifier.width(uiState.sidePanelWidthDp.dp).fillMaxHeight(),
        )
      }
    }

    if (uiState.previewController != null || uiState.previewError != null) {
      FormPreviewOverlay(uiState, actions, isDarkTheme)
    }
    if (uiState.isXmlViewerOpen) {
      XFormsXmlOverlay(uiState, actions)
    }
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
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.widthIn(max = 320.dp)) {
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
        )
      }
      FilledTonalButton(onClick = actions::selectFormSettings) {
        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Form settings")
      }
      PreviewTargetToggle(uiState, actions)
      Spacer(Modifier.weight(1f))
      if (onCreateDataset != null) {
        OutlinedButton(onClick = onCreateDataset) {
          Icon(Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (uiState.form.hasGeometry) "Create map layer" else "Create data table")
        }
      }
      Button(onClick = actions::startPreview) {
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Preview")
      }
      Box {
        IconButton(onClick = { overflowExpanded = true }) {
          Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
          DropdownMenuItem(
            text = { Text("Export XForms XML") },
            leadingIcon = {
              Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            onClick = {
              overflowExpanded = false
              actions.setXmlViewerOpen(true)
            },
          )
        }
      }
      if (onDelete != null) {
        TextButton(onClick = onDelete) {
          Icon(
            Icons.Outlined.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Delete form", color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
}

/** Standard icon representing each [EditorQuestionType]. */
internal fun questionTypeIcon(type: EditorQuestionType): ImageVector =
  when (type) {
    EditorQuestionType.TEXT -> Icons.Outlined.Edit
    EditorQuestionType.LONG_TEXT -> Icons.Outlined.Description
    EditorQuestionType.INTEGER -> Icons.AutoMirrored.Outlined.List
    EditorQuestionType.DECIMAL -> Icons.AutoMirrored.Outlined.List
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
private fun AddQuestionMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  atIndex: Int? = null,
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
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Flow canvas
// ---------------------------------------------------------------------------------------------

/** Precomputed Dp geometry for every flow slot (Start, questions, End). */
private class FlowLayout(questionCount: Int, edges: List<FlowEdge>) {
  val slotCount = questionCount + 2
  val endSlot = questionCount + 1
  private val maxRise: Dp = edges.filter { it.span > 1 }.maxOfOrNull { arcRise(it.span) } ?: 0.dp
  val cardTop: Dp = CanvasPadding + maxRise + 8.dp
  val midY: Dp = cardTop + CardHeight / 2

  fun isTerminal(slot: Int) = slot == 0 || slot == endSlot

  fun width(slot: Int): Dp = if (isTerminal(slot)) TerminalWidth else CardWidth

  private val xs: List<Dp> = buildList {
    var x = CanvasPadding
    for (slot in 0 until slotCount) {
      add(x)
      x += width(slot) + SlotGap
    }
  }

  fun x(slot: Int): Dp = xs[slot]

  fun top(slot: Int): Dp =
    if (isTerminal(slot)) cardTop + (CardHeight - TerminalHeight) / 2 else cardTop

  val totalWidth: Dp = x(endSlot) + TerminalWidth + CanvasPadding
  val totalHeight: Dp = cardTop + CardHeight + CanvasPadding
}

@Composable
private fun FlowCanvasPanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  val form = uiState.form
  val edges = uiState.flowEdges
  val issues = uiState.issues
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
      // FlowRow so the legend moves to its own line when the header is too narrow (e.g. when the
      // issues chip is shown) rather than squeezing labels into one-character-wide columns.
      FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${form.questions.size} screens • ${uiState.pathCount} potential paths",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            softWrap = false,
          )
          if (issues.isNotEmpty()) {
            AssistChip(
              onClick = {
                val firstKey = issues.first().questionKey
                if (firstKey != null) actions.select(firstKey) else actions.selectForm()
              },
              label = {
                Text(
                  "${issues.size} ${if (issues.size == 1) "issue" else "issues"}",
                  softWrap = false,
                )
              },
              leadingIcon = {
                Icon(
                  Icons.Outlined.Warning,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              colors =
                AssistChipDefaults.assistChipColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  labelColor = MaterialTheme.colorScheme.onErrorContainer,
                  leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
              border = null,
            )
          }
        }
        Spacer(Modifier.weight(1f))
        FlowLegend()
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

      Box(
        modifier =
          Modifier.offset(x = thumbOffsetDp)
            .width(thumbWidthDp)
            .height(10.dp)
            .clip(CircleShape)
            .background(colors.outline.copy(alpha = 0.65f))
            .pointerInput(maxScroll, trackWidthPx, thumbWidthPx) {
              detectHorizontalDragGestures { change, dragAmount ->
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
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    LegendItem("Always next", colors.outline, dashed = false)
    LegendItem("If condition is true", colors.primary, dashed = true)
    LegendItem("If skipped", colors.tertiary, dashed = true)
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
  val layout = remember(form.questions.size, edges) { FlowLayout(form.questions.size, edges) }
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
  val pitchPx = with(density) { (CardWidth + SlotGap).toPx() }
  val cardWidthPx = with(density) { CardWidth.toPx() }
  val edgeZonePx = with(density) { 56.dp.toPx() }
  val edgeAlpha by animateFloatAsState(if (drag.isDragging) 0.15f else 1f)
  Box(
    modifier =
      modifier
        .horizontalScroll(horizontalScroll)
        .verticalScroll(rememberScrollState())
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
              horizontalScroll.dispatchRawDelta(-travel.x)
              horizontalDrag(start.id) { change ->
                horizontalScroll.dispatchRawDelta(-change.positionChange().x)
                change.consume()
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

      TerminalNode(
        text = "Start",
        modifier = Modifier.offset(layout.x(0), layout.top(0)),
        isSelected = uiState.isFormSelected,
        onClick = actions::selectForm,
      )

      // Hoverable "+" between Start and Q1 (or Q0)
      AddQuestionAffordance(
        centerX = layout.x(0) + layout.width(0) + SlotGap / 2,
        centerY = layout.midY,
        atIndex = 0,
        onAdd = actions::addQuestion,
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
            isSelected = question.key == uiState.selectedKey,
            hasIssues = uiState.issuesFor(question.key).isNotEmpty(),
            isDragged = isDragged,
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
                    actions.moveQuestionTo(key, to)
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

          // Insertion affordance between question screens (hoverable),
          // or permanent at the very end of questions (after last question)
          val isLastQuestion = index == form.questions.lastIndex
          AddQuestionAffordance(
            centerX = layout.x(slot) + layout.width(slot) + SlotGap / 2,
            centerY = layout.midY,
            atIndex = index + 1,
            onAdd = actions::addQuestion,
            alwaysVisible = isLastQuestion,
          )
        }
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
  modifier: Modifier = Modifier,
) {
  var isHovered by remember { mutableStateOf(false) }
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
        .pointerInput(Unit) {
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
      modifier = Modifier.size(buttonSize).graphicsLayer { alpha = if (visible) 1f else 0f },
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
  if (edge.span == 1) {
    val start = Offset((layout.x(edge.from) + layout.width(edge.from)).toPx(), layout.midY.toPx())
    val end = Offset((layout.x(edge.to) - 2.dp).toPx(), layout.midY.toPx())
    drawLine(color, start, end, strokeWidth = strokeWidth, pathEffect = dash)
    drawArrowHead(tip = end, from = start, color = color)
  } else {
    val sx = (layout.x(edge.from) + layout.width(edge.from) - ArcInset).toPx()
    val sy = layout.top(edge.from).toPx()
    val ex = (layout.x(edge.to) + ArcInset).toPx()
    val ey = (layout.top(edge.to) - 2.dp).toPx()
    val apexY = (layout.cardTop - arcRise(edge.span)).toPx()
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

/** Start or end node of the flow; selecting either shows Form properties. */
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
      modifier = sized,
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

@Composable
private fun ScreenPreviewCard(
  index: Int,
  question: EditorQuestion,
  form: EditorForm,
  isSelected: Boolean,
  hasIssues: Boolean,
  isDragged: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  // Subtle selection using GroundTheme's muted secondary roles (the same treatment as selected
  // list rows elsewhere in the app) rather than the bright primaryContainer accent.
  OutlinedCard(
    onClick = onClick,
    modifier = modifier.width(CardWidth).height(CardHeight),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (isSelected) colors.secondaryContainer else colors.surface,
        contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = if (isSelected || isDragged) 2.dp else 1.dp,
        color =
          when {
            isDragged -> colors.outline
            hasIssues -> colors.error
            isSelected -> colors.secondary
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
          modifier = Modifier.size(16.dp),
        )
        GroundTonalBadge(text = "Q${index + 1}", tone = GroundBadgeTone.NEUTRAL)
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
        text = form.describeRelevance(question) ?: "Always shown",
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

      MiniScreen(question, form.title, Modifier.weight(1f).fillMaxWidth())

      Text(
        text = question.name + if (question.required) " *" else "",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
private fun MiniScreen(question: EditorQuestion, formTitle: String, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
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
          text = question.label.ifBlank { "(no label)" } + if (question.required) " *" else "",
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurface,
          fontWeight = FontWeight.Bold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        if (question.hint.isNotBlank()) {
          Text(
            text = question.hint,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.height(2.dp))
        MiniWidget(question)
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
private fun MiniWidget(question: EditorQuestion) {
  val colors = MaterialTheme.colorScheme
  when (question.type) {
    EditorQuestionType.TEXT -> MiniField("Answer", 26.dp)
    EditorQuestionType.LONG_TEXT -> MiniField("Answer", 52.dp)
    EditorQuestionType.INTEGER -> MiniField("123", 26.dp)
    EditorQuestionType.DECIMAL -> MiniField("1.5", 26.dp)
    EditorQuestionType.DATE -> MiniField("YYYY-MM-DD", 26.dp, Icons.Outlined.DateRange)
    EditorQuestionType.SELECT_ONE,
    EditorQuestionType.SELECT_MULTIPLE -> {
      val round = question.type == EditorQuestionType.SELECT_ONE
      question.choices.take(4).forEach { choice ->
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
            text = choice.label.ifBlank { choice.value },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (question.choices.size > 4) {
        Text(
          text = "+${question.choices.size - 4} more",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
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
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    val question = uiState.selectedQuestion
    if (question == null) {
      FormProperties(uiState, actions, onSaveToModeChange, onOpenDataset)
      return@ElevatedCard
    }
    key(question.key) { QuestionProperties(uiState, actions, question) }
  }
}

@Composable
private fun QuestionProperties(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val form = uiState.form
  val index = uiState.selectedIndex
  val key = question.key
  val issues = uiState.issuesFor(key)
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column {
      Text(
        text = "Question ${index + 1} of ${form.questions.size}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { actions.moveQuestion(key, -1) }, enabled = index > 0) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move earlier")
      }
      IconButton(
        onClick = { actions.moveQuestion(key, 1) },
        enabled = index < form.questions.lastIndex,
      ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move later")
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { actions.duplicateQuestion(key) }) { Text("Duplicate") }
      TextButton(onClick = { actions.deleteQuestion(key) }) {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Delete", color = MaterialTheme.colorScheme.error)
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

    DropdownSelector(
      label = "Question type",
      selectedText = question.type.label,
      options = EditorQuestionType.entries,
      optionText = { it.label },
      optionIcon = { questionTypeIcon(it) },
      onSelect = { actions.changeType(key, it) },
    )

    if (question.type.isGeometry) {
      GeometryCaptureSelector(uiState, actions, question)
    }
    if (question.type.isMedia) {
      MediaSourceSelector(uiState, actions, question)
    }

    OutlinedTextField(
      value = question.label,
      onValueChange = { v -> actions.updateQuestion(key) { it.copy(label = v) } },
      label = { Text(if (question.type == EditorQuestionType.NOTE) "Note text" else "Label") },
      minLines = 2,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = question.hint,
      onValueChange = { v -> actions.updateQuestion(key) { it.copy(hint = v) } },
      label = { Text("Hint (optional)") },
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
      ChoicesEditor(uiState, actions, question)
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    DisplayLogicEditor(uiState, actions, question)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AdvancedSection(uiState, actions, question)
  }
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
      selectedText = question.capture.label,
      options = GeometryCapture.entries,
      optionText = { it.label },
      onSelect = { actions.updateCapture(question.key, it) },
    )
    Text(
      text = question.capture.description,
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

@Composable
private fun ChoicesEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val key = question.key
  var imageError by remember { mutableStateOf<String?>(null) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel("Choices")
    question.choices.forEachIndexed { i, choice ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        ChoiceColorButton(
          colorHex = choice.colorHex,
          onSelect = { actions.setChoiceColor(key, i, it) },
        )
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
        OutlinedTextField(
          value = choice.label,
          onValueChange = { actions.updateChoiceLabel(key, i, it) },
          label = { Text("Label") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = choice.value,
          onValueChange = { actions.updateChoice(key, i, choice.copy(value = it.trim())) },
          label = { Text("Value") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.width(96.dp),
        )
        IconButton(onClick = { actions.removeChoice(key, i) }, modifier = Modifier.size(32.dp)) {
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
        color = MaterialTheme.colorScheme.error,
      )
    }
    TextButton(onClick = { actions.addChoice(key) }) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(4.dp))
      Text("Add choice")
    }
  }
}

/** Color dot for a choice; opens a compact palette with a "No color" option. */
@Composable
private fun ChoiceColorButton(colorHex: String?, onSelect: (String?) -> Unit) {
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
      DropdownMenuItem(
        text = { Text("No color") },
        leadingIcon = { Icon(Icons.Outlined.FormatColorReset, contentDescription = null) },
        enabled = colorHex != null,
        onClick = {
          onSelect(null)
          expanded = false
        },
      )
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
  remember(image) {
    runCatching { Base64.decode(image.base64).decodeToImageBitmap() }.getOrNull()
  }

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
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionLabel("Display logic")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      SegmentedButton(
        selected = relevance == null,
        onClick = { actions.disableRelevance(key) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        label = { Text("Always show") },
      )
      SegmentedButton(
        selected = relevance != null,
        onClick = { if (relevance == null) actions.enableRelevance(key) },
        enabled = sources.isNotEmpty() || relevance != null,
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        label = { Text("Show only if…") },
      )
    }
    if (sources.isEmpty() && relevance == null) {
      Text(
        text = "Display logic can depend on earlier questions. Move this question later to use it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (relevance != null) {
      val source = form.find(relevance.sourceQuestionKey)
      DropdownSelector(
        label = "Question",
        selectedText = source?.let { "${it.name} — ${it.label}" } ?: "(missing question)",
        options = sources,
        optionText = { "${it.name} — ${it.label}" },
        onSelect = { picked ->
          val operator =
            relevance.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
              ?: RelevanceOperator.availableFor(picked.type).first()
          val value = picked.choices.firstOrNull()?.value.orEmpty()
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
          if (source.type.hasChoices) {
            DropdownSelector(
              label = "Value",
              selectedText =
                source.choices.firstOrNull { it.value == relevance.value }?.label
                  ?: relevance.value.ifBlank { "Pick a choice" },
              options = source.choices,
              optionText = { "${it.label} (${it.value})" },
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
      form.relevanceSummary(question)?.let { summary ->
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
    text = text.uppercase(),
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
) {
  var expanded by remember { mutableStateOf(false) }
  // Standard M3 exposed dropdown: a read-only OutlinedTextField, so the label and value use the
  // same typography as every other input in the editors.
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      value = selectedText,
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier =
        Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
// Overlays: flow preview and XForms XML
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModalScrim(content: @Composable () -> Unit) {
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

@Composable
private fun FormPreviewOverlay(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  isDarkTheme: Boolean,
) {
  val controller = uiState.previewController
  var formFactor by remember { mutableStateOf(DeviceFormFactor.MOBILE) }
  var orientation by remember { mutableStateOf(DeviceFormFactor.MOBILE.defaultOrientation) }
  ModalScrim {
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
        MobileDevicePreviewFrame(
          deviceTitle = "Preview • ${uiState.form.title}",
          isDarkTheme = isDarkTheme,
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
      PreviewSidePanel(uiState, actions)
    }
  }
}

@Composable
private fun PreviewSidePanel(uiState: FormEditorUiState, actions: FormEditorActions) {
  val controller = uiState.previewController
  val colors = MaterialTheme.colorScheme
  ElevatedCard(modifier = Modifier.width(340.dp)) {
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

@Composable
private fun XFormsXmlOverlay(uiState: FormEditorUiState, actions: FormEditorActions) {
  val xml = uiState.xformsXml
  val parseError =
    remember(xml) {
      try {
        XFormsXmlSerializer.deserializeFormDef(xml)
        null
      } catch (e: Exception) {
        e.message ?: e.toString()
      }
    }
  ModalScrim {
    ElevatedCard(modifier = Modifier.width(820.dp).fillMaxHeight(0.85f)) {
      Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Generated XForms",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                parseError?.let { "Parse error: $it" }
                  ?: "Parses cleanly with the Ground form engine.",
              style = MaterialTheme.typography.bodySmall,
              color =
                if (parseError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { actions.setXmlViewerOpen(false) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Close")
          }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxSize(),
        ) {
          SelectionContainer {
            Text(
              text = xml,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              modifier =
                Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .horizontalScroll(rememberScrollState())
                  .padding(12.dp),
            )
          }
        }
      }
    }
  }
}
