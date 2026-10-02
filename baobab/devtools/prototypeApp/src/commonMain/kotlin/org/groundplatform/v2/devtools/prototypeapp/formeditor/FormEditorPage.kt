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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.devtools.prototypeapp.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.MobileDevicePreviewFrame

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
  state: FormEditorState,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  onCreateDataset: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      FormEditorToolbar(state, onCreateDataset, onDelete)
      Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        FlowCanvasPanel(state, Modifier.weight(1f).fillMaxHeight())
        QuestionPropertiesPanel(state, Modifier.width(380.dp).fillMaxHeight())
      }
    }

    if (state.previewController != null || state.previewError != null) {
      FormPreviewOverlay(state, isDarkTheme)
    }
    if (state.isXmlViewerOpen) {
      XFormsXmlOverlay(state)
    }
  }
}

@Composable
private fun FormEditorToolbar(
  state: FormEditorState,
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
      Column(modifier = Modifier.width(200.dp)) {
        Text(
          text = "Form",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Design questions, display logic, and flows. Generates XForms.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedTextField(
        value = state.form.title,
        onValueChange = state::updateTitle,
        label = { Text("Form title") },
        singleLine = true,
        modifier = Modifier.width(280.dp),
      )
      Spacer(Modifier.weight(1f))
      if (onCreateDataset != null) {
        OutlinedButton(onClick = onCreateDataset) {
          Icon(Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Create layer")
        }
      }
      Button(onClick = state::startPreview) {
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
              state.isXmlViewerOpen = true
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
    EditorQuestionType.PHOTO -> Icons.Outlined.AccountCircle
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
private fun FlowCanvasPanel(state: FormEditorState, modifier: Modifier = Modifier) {
  val form = state.form
  val edges = state.flowEdges
  val issues = state.issues
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
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${form.questions.size} screens • ${state.pathCount} potential paths",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        if (issues.isNotEmpty()) {
          AssistChip(
            onClick = {
              issues.firstOrNull { it.questionKey != null }?.let { state.select(it.questionKey) }
            },
            label = { Text("${issues.size} ${if (issues.size == 1) "issue" else "issues"}") },
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
        Spacer(Modifier.weight(1f))
        FlowLegend()
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      FlowCanvas(state, edges, horizontalScroll, Modifier.weight(1f).fillMaxWidth())
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
  Row(
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
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
    )
  }
}

@Composable
private fun FlowCanvas(
  state: FormEditorState,
  edges: List<FlowEdge>,
  horizontalScroll: androidx.compose.foundation.ScrollState,
  modifier: Modifier,
) {
  val form = state.form
  val layout = remember(form.questions.size, edges) { FlowLayout(form.questions.size, edges) }
  val density = LocalDensity.current
  val selectedSlot =
    if (state.selectedIndex >= 0) FormFlowGraph.questionSlot(state.selectedIndex) else -1

  // Keep the selected screen in view (e.g. after adding or reordering).
  LaunchedEffect(selectedSlot, layout.slotCount) {
    if (selectedSlot >= 0) {
      val left = with(density) { (layout.x(selectedSlot) - SlotGap).roundToPx() }
      val right = with(density) { (layout.x(selectedSlot) + CardWidth + SlotGap).roundToPx() }
      val viewportEnd = horizontalScroll.value + horizontalScroll.viewportSize
      when {
        left < horizontalScroll.value -> horizontalScroll.animateScrollTo(left.coerceAtLeast(0))
        right > viewportEnd && horizontalScroll.viewportSize > 0 ->
          horizontalScroll.animateScrollTo(right - horizontalScroll.viewportSize)
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
          if (!drag.isDragging) {
            detectHorizontalDragGestures { change, dragAmount ->
              change.consume()
              horizontalScroll.dispatchRawDelta(-dragAmount)
            }
          }
        }
  ) {
    Box(modifier = Modifier.width(layout.totalWidth).height(layout.totalHeight)) {
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

      TerminalNode(text = "Start", modifier = Modifier.offset(layout.x(0), layout.top(0)))

      // Hoverable "+" between Start and Q1 (or Q0)
      AddQuestionAffordance(
        centerX = layout.x(0) + layout.width(0) + SlotGap / 2,
        centerY = layout.midY,
        atIndex = 0,
        onAdd = state::addQuestion,
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
            isSelected = question.key == state.selectedKey,
            hasIssues = state.issuesFor(question.key).isNotEmpty(),
            isDragged = isDragged,
            onClick = { state.select(question.key) },
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
                    state.moveQuestionTo(key, to)
                    state.select(key)
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
            onAdd = state::addQuestion,
            alwaysVisible = isLastQuestion,
          )
        }
      }
      TerminalNode(
        text = "Review & submit",
        modifier = Modifier.offset(layout.x(layout.endSlot), layout.top(layout.endSlot)),
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
    val visible = alwaysVisible || isHovered || menuExpanded
    if (visible) {
      Surface(
        onClick = { menuExpanded = true },
        shape = CircleShape,
        color = if (alwaysVisible && !isHovered) colors.primaryContainer else colors.primary,
        contentColor =
          if (alwaysVisible && !isHovered) colors.onPrimaryContainer else colors.onPrimary,
        shadowElevation = if (isHovered || menuExpanded) 4.dp else 1.dp,
        modifier = Modifier.size(buttonSize),
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

@Composable
private fun TerminalNode(text: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.width(TerminalWidth).height(TerminalHeight),
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
      )
    }
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
  OutlinedCard(
    onClick = onClick,
    modifier = modifier.width(CardWidth).height(CardHeight),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (isSelected) colors.primaryContainer else colors.surface
      ),
    border =
      BorderStroke(
        width = if (isSelected || isDragged) 2.dp else 1.dp,
        color =
          when {
            isDragged -> colors.primary
            hasIssues -> colors.error
            isSelected -> colors.primary
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
          tint = if (isDragged) colors.primary else colors.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
        GroundTonalBadge(text = "Q${index + 1}", tone = GroundBadgeTone.PRIMARY)
        Icon(
          questionTypeIcon(question.type),
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = colors.onSurfaceVariant,
        )
        Text(
          text = question.type.label,
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
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
        color = if (question.isConditional) colors.primary else colors.onSurfaceVariant,
        fontWeight = if (question.isConditional) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
      )

      MiniScreen(question, form.title, Modifier.weight(1f).fillMaxWidth())

      Text(
        text = question.name + if (question.required) " *" else "",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = colors.onSurfaceVariant,
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
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(10.dp))
        .background(colors.surfaceContainerLowest)
        .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
  ) {
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
    EditorQuestionType.LOCATION -> MiniPlaceholder("Capture location", Icons.Outlined.LocationOn)
    EditorQuestionType.PHOTO -> MiniPlaceholder("Take photo", Icons.Outlined.Add)
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

@Composable
private fun QuestionPropertiesPanel(state: FormEditorState, modifier: Modifier = Modifier) {
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    val question = state.selectedQuestion
    if (question == null) {
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
          text =
            if (state.form.questions.isEmpty()) {
              "This form has no questions yet. Use \"Add question\" to create one."
            } else {
              "Select a screen to edit its properties."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      return@ElevatedCard
    }
    key(question.key) { QuestionProperties(state, question) }
  }
}

@Composable
private fun QuestionProperties(state: FormEditorState, question: EditorQuestion) {
  val form = state.form
  val index = state.selectedIndex
  val key = question.key
  val issues = state.issuesFor(key)
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
      Text(
        text = "/data/${question.name}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { state.moveQuestion(key, -1) }, enabled = index > 0) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move earlier")
      }
      IconButton(
        onClick = { state.moveQuestion(key, 1) },
        enabled = index < form.questions.lastIndex,
      ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move later")
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { state.duplicateQuestion(key) }) { Text("Duplicate") }
      TextButton(onClick = { state.deleteQuestion(key) }) {
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
      onSelect = { state.changeType(key, it) },
    )

    OutlinedTextField(
      value = question.label,
      onValueChange = { v -> state.updateQuestion(key) { it.copy(label = v) } },
      label = { Text(if (question.type == EditorQuestionType.NOTE) "Note text" else "Label") },
      minLines = 2,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = question.hint,
      onValueChange = { v -> state.updateQuestion(key) { it.copy(hint = v) } },
      label = { Text("Hint (optional)") },
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = question.name,
      onValueChange = { v -> state.updateQuestion(key) { it.copy(name = v.trim()) } },
      label = { Text("Name") },
      singleLine = true,
      isError = !FormEditorValidator.isValidName(question.name),
      supportingText = { Text("Data column name. Letters, digits, _ . -") },
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
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
        onCheckedChange = { v -> state.updateQuestion(key) { it.copy(required = v) } },
      )
    }

    if (question.type.hasChoices) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      ChoicesEditor(state, question)
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    DisplayLogicEditor(state, question)
  }
}

@Composable
private fun ChoicesEditor(state: FormEditorState, question: EditorQuestion) {
  val key = question.key
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel("Choices")
    question.choices.forEachIndexed { i, choice ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedTextField(
          value = choice.label,
          onValueChange = { state.updateChoiceLabel(key, i, it) },
          label = { Text("Label") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = choice.value,
          onValueChange = { state.updateChoice(key, i, choice.copy(value = it.trim())) },
          label = { Text("Value") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.width(110.dp),
        )
        IconButton(onClick = { state.removeChoice(key, i) }, modifier = Modifier.size(32.dp)) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = "Remove choice",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
    TextButton(onClick = { state.addChoice(key) }) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(4.dp))
      Text("Add choice")
    }
  }
}

@Composable
private fun DisplayLogicEditor(state: FormEditorState, question: EditorQuestion) {
  val form = state.form
  val key = question.key
  val sources = form.eligibleRelevanceSources(key)
  val relevance = question.relevance
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionLabel("Display logic")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      SegmentedButton(
        selected = relevance == null,
        onClick = { state.disableRelevance(key) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        label = { Text("Always show") },
      )
      SegmentedButton(
        selected = relevance != null,
        onClick = { if (relevance == null) state.enableRelevance(key) },
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
          state.updateQuestion(key) {
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
            state.updateQuestion(key) { it.copy(relevance = relevance.copy(operator = op)) }
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
                state.updateQuestion(key) { it.copy(relevance = relevance.copy(value = c.value)) }
              },
            )
          } else {
            OutlinedTextField(
              value = relevance.value,
              onValueChange = { v ->
                state.updateQuestion(key) { it.copy(relevance = relevance.copy(value = v)) }
              },
              label = { Text("Value") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      EditorXFormsGenerator.relevantExpression(form, question)?.let { xpath ->
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "relevant=\"$xpath\"",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(8.dp),
          )
        }
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
  Box(modifier = modifier.fillMaxWidth()) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraSmall,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = selectedText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun FormPreviewOverlay(state: FormEditorState, isDarkTheme: Boolean) {
  val controller = state.previewController
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
      if (controller != null) {
        MobileDevicePreviewFrame(
          deviceTitle = "Preview • ${state.form.title}",
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
              onClose = state::closePreview,
              onSubmitted = { state.markPreviewSubmitted() },
            )
          }
        }
      }
      PreviewSidePanel(state)
    }
  }
}

@Composable
private fun PreviewSidePanel(state: FormEditorState) {
  val controller = state.previewController
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
        IconButton(onClick = state::closePreview) {
          Icon(Icons.Outlined.Close, contentDescription = "Close preview")
        }
      }
      val error = state.previewError
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
            "Screens update live as you answer: conditional questions appear or disappear based on display logic.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
        if (state.previewSubmitted) {
          Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.small) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // Filled: indicates the validation-passed state.
              Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary)
              Spacer(Modifier.width(8.dp))
              Text("Submission passed validation.", style = MaterialTheme.typography.bodyMedium)
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
              color = colors.primary,
              modifier = Modifier.width(24.dp),
            )
            Text(
              text = step.title,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = state::restartPreview) {
          Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Restart")
        }
        Button(onClick = state::closePreview) { Text("Back to editor") }
      }
    }
  }
}

@Composable
private fun XFormsXmlOverlay(state: FormEditorState) {
  val xml = state.xformsXml
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
          IconButton(onClick = { state.isXmlViewerOpen = false }) {
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
