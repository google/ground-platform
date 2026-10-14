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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.groundplatform.v2.app.domain.model.editor.EditorDataset
import org.groundplatform.v2.app.domain.model.editor.EditorForm
import org.groundplatform.v2.app.domain.model.editor.EditorQuestion
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.FlowEdge
import org.groundplatform.v2.app.domain.model.editor.FlowEdgeKind
import org.groundplatform.v2.app.domain.model.editor.FormFlowGraph
import org.groundplatform.v2.app.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.app.domain.model.editor.GeometryCapture
import org.groundplatform.v2.core.forms.ui.CompactFormRunner
import org.groundplatform.v2.core.forms.ui.CompactGeometryInput
import org.groundplatform.v2.core.forms.ui.CompactMapDrawingHost
import org.groundplatform.v2.core.forms.ui.FitScreenIcon
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.core.forms.ui.addMapDrawingVertex
import org.groundplatform.v2.web.ui.datacollection.WebFormPanelChrome
import org.groundplatform.v2.web.ui.datacollection.WebFormPanelWidth
import org.groundplatform.v2.web.ui.state.FormEditorUiState
import org.groundplatform.v2.web.ui.viewmodel.FormEditorActions

private val WebWireframeCardHeight = 82.dp
private val WebWireframeCardGap = 10.dp
private val WebWireframeGhostHeight = 56.dp
private val WebSkipRailWidth = 68.dp
private val WebPanelHeaderOffset = 68.dp

/** Icon of each [FormPreviewTarget] in the editor's toggle. */
internal fun previewTargetIcon(target: FormPreviewTarget): ImageVector =
  when (target) {
    FormPreviewTarget.MOBILE -> Icons.Outlined.Smartphone
    FormPreviewTarget.WEB -> Icons.Outlined.Computer
  }

/** Segmented Mobile / Web toggle for the Form editor toolbar. */
@Composable
internal fun PreviewTargetToggle(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier.height(36.dp)) {
    FormPreviewTarget.entries.forEachIndexed { index, target ->
      SegmentedButton(
        selected = uiState.previewTarget == target,
        onClick = { actions.selectPreviewTarget(target) },
        shape =
          SegmentedButtonDefaults.itemShape(index = index, count = FormPreviewTarget.entries.size),
        icon = {
          Icon(
            imageVector = previewTargetIcon(target),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = { Text(target.label, style = MaterialTheme.typography.labelMedium) },
      )
    }
  }
}

/** Short name of the platform a [FormPreviewTarget] stands for, for use in sentences. */
internal fun FormPreviewTarget.sentenceName(): String =
  when (this) {
    FormPreviewTarget.MOBILE -> "the mobile app"
    FormPreviewTarget.WEB -> "the web dashboard"
  }

/**
 * Banner shown above the canvas when the Form is switched off for the platform being previewed.
 * **Enable** turns it on for that platform; the same switches live in Form settings.
 */
@Composable
internal fun PlatformDisabledBanner(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  if (uiState.isEnabledOnPreviewTarget) return
  val target = uiState.previewTarget
  val colors = MaterialTheme.colorScheme
  CanvasBanner(
    icon = Icons.Outlined.VisibilityOff,
    title = "Not available on ${target.label.lowercase()}",
    body =
      "Collectors won't find this form in ${target.sentenceName()}. Enable it to offer it " +
        "there (also in Form settings).",
    actionLabel = "Enable",
    onAction = actions::enableOnPreviewTarget,
    containerColor = colors.tertiaryContainer,
    contentColor = colors.onTertiaryContainer,
    modifier = modifier,
  )
}

/**
 * Error banner shown above the web canvas when the Form is on for web but has GPS-only geometry
 * questions, which the web dashboard can't capture. **Fix all** switches them to
 * [GeometryCapture.GPS_OR_MAP]. Only shown while the Form is enabled on web, after
 * [PlatformDisabledBanner]'s check.
 */
@Composable
internal fun WebIncompatibleGeometryBanner(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  if (uiState.previewTarget != FormPreviewTarget.WEB || !uiState.isEnabledOnPreviewTarget) return
  val incompatible = uiState.webIncompatibleGeometryQuestions
  if (incompatible.isEmpty()) return
  val colors = MaterialTheme.colorScheme
  CanvasBanner(
    icon = Icons.Outlined.ErrorOutline,
    title = "Can't be used on web yet",
    body =
      "This form can't be used on web until its GPS-only questions allow drawing on the map. " +
        webIncompatibleSummary(incompatible.size),
    actionLabel = "Fix all",
    onAction = actions::makeGeometryQuestionsWebCompatible,
    containerColor = colors.errorContainer,
    contentColor = colors.onErrorContainer,
    modifier = modifier,
  )
}

/** Full-width notice above a canvas preview: icon, title, body, and one text action. */
@Composable
private fun CanvasBanner(
  icon: ImageVector,
  title: String,
  body: String,
  actionLabel: String,
  onAction: () -> Unit,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxWidth(), color = containerColor) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = contentColor,
        )
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = contentColor)
      }
      TextButton(onClick = onAction) { Text(actionLabel, color = contentColor) }
    }
  }
}

/**
 * Wraps a canvas preview area so that, while the Form is off for the previewed platform, it is
 * greyed out and none of its controls respond: pointer events are consumed before they reach the
 * content, mirroring the fact that collectors can't interact with the Form there.
 */
@Composable
internal fun UnavailablePreviewArea(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val enabled = uiState.isEnabledOnPreviewTarget
  Box(modifier = modifier) {
    Box(modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.4f)) { content() }
    if (!enabled) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
              }
            }
      )
    }
  }
}

/**
 * The editor canvas in [FormPreviewTarget.WEB] mode: renders a mock web dashboard backdrop (header,
 * left feature list panel, and map with an interactive "Adjust view" control) behind the right-hand
 * [WebFormPanelChrome]. Inside the panel, questions are shown as compact wireframe cards with
 * external curved conditional skip arcs along the left margin and a dashed ghost card at the
 * bottom.
 */
@Composable
internal fun WebLayoutCanvasPanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
  val form = uiState.form
  val colors = MaterialTheme.colorScheme
  var isZoomedIn by remember { mutableStateOf(false) }

  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      PlatformDisabledBanner(uiState, actions)
      WebIncompatibleGeometryBanner(uiState, actions)
      UnavailablePreviewArea(uiState, actions, Modifier.weight(1f).fillMaxWidth()) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(colors.surfaceContainer)
              .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = actions::selectForm,
              )
        ) {
          MockWebDashboardBackdrop(
            uiState = uiState,
            isZoomedIn = isZoomedIn,
            onToggleZoom = { isZoomedIn = !isZoomedIn },
            modifier = Modifier.fillMaxSize(),
          )

          Row(
            modifier =
              Modifier.align(Alignment.TopEnd)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, end = 20.dp, bottom = 72.dp),
            verticalAlignment = Alignment.Top,
          ) {
            WebFlowSkipRail(
              questionCount = form.questions.size,
              edges = uiState.flowEdges,
              selectedIndex = uiState.selectedIndex,
            )
            WebFormPanelChrome(
              title = form.title.ifBlank { "Untitled form" },
              subtitle = "Collect data",
              modifier = Modifier.width(WebFormPanelWidth),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(WebWireframeCardGap),
              ) {
                form.questions.forEachIndexed { index, question ->
                  key(question.key) {
                    WebWireframeQuestionCard(
                      index = index,
                      question = question,
                      form = form,
                      datasets = uiState.datasets,
                      isSelected = question.key == uiState.selectedKey,
                      hasIssues = uiState.issuesFor(question.key).isNotEmpty(),
                      isLinked = question.conceptLink != null,
                      activeLanguage = uiState.activeLanguage,
                      defaultLanguage = uiState.defaultLanguage,
                      isZoomedIn = isZoomedIn,
                      onToggleZoom = { isZoomedIn = !isZoomedIn },
                      onClick = { actions.select(question.key) },
                    )
                  }
                }
                WebGhostQuestionCard(
                  atIndex = form.questions.size,
                  onAdd = actions::addQuestion,
                )
                WebWireframeSubmitFooter(
                  questionCount = form.questions.size,
                  isSelected = uiState.isFormSelected,
                  onClick = actions::selectForm,
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Curved vertical flow & conditional skip lines drawn along the outside left edge of the web form
 * panel so authors can see sequential transitions and conditional skips in Web layout mode.
 */
@Composable
private fun WebFlowSkipRail(
  questionCount: Int,
  edges: List<FlowEdge>,
  selectedIndex: Int,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val selectedSlot = if (selectedIndex >= 0) FormFlowGraph.questionSlot(selectedIndex) else -1
  val stepPitch = WebWireframeCardHeight + WebWireframeCardGap
  val totalCardsHeight =
    WebPanelHeaderOffset +
      stepPitch * questionCount +
      WebWireframeGhostHeight +
      WebWireframeCardGap +
      48.dp

  Canvas(modifier = modifier.width(WebSkipRailWidth).height(totalCardsHeight)) {
    val railRight = size.width - 4.dp.toPx()
    fun slotY(slot: Int): Float =
      when {
        slot <= 0 -> (WebPanelHeaderOffset - 18.dp).toPx()
        slot in 1..questionCount ->
          (WebPanelHeaderOffset + stepPitch * (slot - 1) + WebWireframeCardHeight / 2).toPx()
        else ->
          (WebPanelHeaderOffset +
              stepPitch * questionCount +
              WebWireframeGhostHeight +
              WebWireframeCardGap +
              20.dp)
            .toPx()
      }

    edges.forEach { edge ->
      val highlighted = selectedSlot >= 0 && (edge.from == selectedSlot || edge.to == selectedSlot)
      val baseColor =
        when (edge.kind) {
          FlowEdgeKind.NEXT -> colors.outline
          FlowEdgeKind.CONDITIONAL -> colors.primary
          FlowEdgeKind.SKIP -> colors.tertiary
        }
      val color =
        if (selectedSlot >= 0 && !highlighted) baseColor.copy(alpha = 0.45f) else baseColor
      val strokePx = (if (highlighted) 2.5.dp else 1.75.dp).toPx()
      val dash =
        if (edge.kind == FlowEdgeKind.NEXT) null
        else PathEffect.dashPathEffect(floatArrayOf(10f, 6f))

      val sy = slotY(edge.from)
      val ey = slotY(edge.to)
      if (edge.span <= 1) {
        val x = railRight - 8.dp.toPx()
        val start = Offset(x, sy + 14.dp.toPx())
        val end = Offset(x, ey - 14.dp.toPx())
        if (end.y > start.y) {
          drawLine(color, start, end, strokeWidth = strokePx, pathEffect = dash)
          drawWebArrowHead(tip = end, from = start, color = color)
        }
      } else {
        val depth = (18.dp + 10.dp * (edge.span - 2).coerceIn(0, 4)).toPx()
        val apexX = (railRight - depth).coerceAtLeast(6.dp.toPx())
        val path =
          Path().apply {
            moveTo(railRight, sy)
            cubicTo(apexX, sy, apexX, ey, railRight, ey)
          }
        drawPath(path, color, style = Stroke(width = strokePx, pathEffect = dash))
        drawWebArrowHead(tip = Offset(railRight, ey), from = Offset(apexX, ey), color = color)
      }
    }
  }
}

private fun DrawScope.drawWebArrowHead(tip: Offset, from: Offset, color: Color) {
  val angle = atan2(tip.y - from.y, tip.x - from.x)
  val length = 7.dp.toPx()
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
 * Compact 2-line wireframe question card inside the web layout canvas. Highlights on hover (`F5`),
 * selects the question on click, and offers an interactive "Adjust view" button (`M5`) on geometry
 * questions.
 */
@Composable
private fun WebWireframeQuestionCard(
  index: Int,
  question: EditorQuestion,
  form: EditorForm,
  datasets: List<EditorDataset>,
  isSelected: Boolean,
  hasIssues: Boolean,
  isLinked: Boolean,
  activeLanguage: String,
  defaultLanguage: String,
  isZoomedIn: Boolean,
  onToggleZoom: () -> Unit,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  var isHovered by remember { mutableStateOf(false) }
  val isHoverHighlighted = isHovered && !isSelected
  val displayLabel = question.displayLabel(activeLanguage, defaultLanguage).ifBlank { "(no label)" }
  val relevanceText = form.describeRelevance(question, datasets) ?: "Always shown"

  OutlinedCard(
    onClick = onClick,
    modifier =
      Modifier.fillMaxWidth()
        .height(WebWireframeCardHeight)
        .pointerHoverIcon(PointerIcon.Hand)
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
        width = if (isSelected || isHoverHighlighted) 2.dp else 1.dp,
        color =
          when {
            hasIssues -> colors.error
            isSelected -> colors.secondary
            isHoverHighlighted -> colors.primary.copy(alpha = 0.65f)
            else -> colors.outlineVariant
          },
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        GroundTonalBadge(text = "Q${index + 1}", tone = GroundBadgeTone.NEUTRAL)
        Icon(
          imageVector = questionTypeIcon(question.type),
          contentDescription = null,
          modifier = Modifier.size(15.dp),
          tint = if (isSelected) colors.onSecondaryContainer else colors.primary,
        )
        Text(
          text = displayLabel + if (question.required) " *" else "",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
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

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = relevanceText,
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
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (question.type.isGeometry) {
          Surface(
            onClick = onToggleZoom,
            shape = RoundedCornerShape(50),
            color = if (isZoomedIn) colors.primaryContainer else colors.surfaceContainerHighest,
            contentColor = if (isZoomedIn) colors.onPrimaryContainer else colors.onSurfaceVariant,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = FitScreenIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = if (isZoomedIn) "Reset view" else "Adjust view",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        } else {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = colors.surfaceContainerLowest,
            border = BorderStroke(1.dp, colors.outlineVariant),
          ) {
            Text(
              text = wireframeWidgetSummary(question, datasets, activeLanguage, defaultLanguage),
              style = MaterialTheme.typography.labelSmall,
              color = colors.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
      }
    }
  }
}

private fun wireframeWidgetSummary(
  question: EditorQuestion,
  datasets: List<EditorDataset>,
  activeLanguage: String,
  defaultLanguage: String,
): String =
  when (question.type) {
    EditorQuestionType.SELECT_ONE,
    EditorQuestionType.SELECT_MULTIPLE -> {
      val choices = question.resolvedChoices(datasets)
      if (choices.isEmpty()) question.type.label
      else
        choices.take(2).joinToString(" · ") {
          it.displayLabel(activeLanguage, defaultLanguage).ifBlank { it.value }
        } + if (choices.size > 2) " (+${choices.size - 2})" else ""
    }
    EditorQuestionType.NUMBER ->
      if (question.decimalPlaces <= 0) "Integer input" else "Decimal (${question.decimalPlaces} dp)"
    else -> question.type.label
  }

/**
 * Compact dashed ghost card at the bottom of the web question stack (`F6`), opening
 * [AddQuestionMenu] when clicked.
 */
@Composable
private fun WebGhostQuestionCard(
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
) {
  var isHovered by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val highlighted = isHovered || menuExpanded
  val borderColor = if (highlighted) colors.primary else colors.outlineVariant
  val containerColor =
    if (highlighted) colors.secondaryContainer.copy(alpha = 0.25f)
    else colors.surfaceContainerLow.copy(alpha = 0.7f)

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(WebWireframeGhostHeight)
        .clip(MaterialTheme.shapes.medium)
        .background(containerColor)
        .pointerHoverIcon(PointerIcon.Hand)
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
        }
        .clickable { menuExpanded = true }
        .semantics { contentDescription = "Add a question at end of web form" },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = (if (highlighted) 2.dp else 1.5.dp).toPx()
      val cornerPx = 12.dp.toPx()
      drawRoundRect(
        color = borderColor,
        topLeft = Offset(strokePx / 2f, strokePx / 2f),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style =
          Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
          ),
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Outlined.Add,
        contentDescription = null,
        tint = if (highlighted) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = "Add a question",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (highlighted) colors.primary else colors.onSurfaceVariant,
      )
    }
    AddQuestionMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onAdd = onAdd,
      atIndex = atIndex,
    )
  }
}

@Composable
private fun WebWireframeSubmitFooter(
  questionCount: Int,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.small,
    color = if (isSelected) colors.secondaryContainer else colors.surfaceContainerLow,
    border =
      BorderStroke(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) colors.secondary else colors.outlineVariant,
      ),
    modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "0 of $questionCount answered",
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
      )
      Surface(
        shape = RoundedCornerShape(50),
        color = colors.primary,
        contentColor = colors.onPrimary,
      ) {
        Text(
          text = "Review & submit",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
      }
    }
  }
}

/**
 * Static mock of the web dashboard behind the right-hand data collection panel (`F7` & `M5`):
 * includes a mock top dashboard bar, a mock left-hand feature list panel, and a stylized map
 * viewport with parcels, grid lines, and an interactive "Adjust view" zoom toggle.
 */
@Composable
private fun MockWebDashboardBackdrop(
  uiState: FormEditorUiState,
  isZoomedIn: Boolean,
  onToggleZoom: () -> Unit,
  modifier: Modifier = Modifier,
  onMapClick: ((Double, Double) -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val datasetName = uiState.saveTarget?.displayName ?: "Sample plots"

  Column(modifier = modifier) {
    // Mock top dashboard header
    Surface(
      color = colors.surface,
      tonalElevation = 1.dp,
      modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          Icons.Outlined.Layers,
          contentDescription = null,
          tint = colors.primary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = datasetName,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = colors.onSurface,
        )
        GroundTonalBadge(text = "3 features", tone = GroundBadgeTone.NEUTRAL)
        Spacer(Modifier.weight(1f))
        OutlinedButton(
          onClick = onToggleZoom,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.height(30.dp),
        ) {
          Icon(
            imageVector = FitScreenIcon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (isZoomedIn) "Reset view" else "Adjust view",
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }

    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      // Mock left-hand feature list sidebar
      Surface(
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(204.dp).fillMaxHeight(),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.surfaceContainerHighest.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().height(30.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Search features…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
              )
            }
          }
          MockFeatureRow(
            title = "Plot #101 • East parcel",
            subtitle = if (isZoomedIn) "Framed in map (1.8 ha)" else "Selected • 1.8 ha",
            selected = true,
            isPolygon = true,
          )
          MockFeatureRow(
            title = "Plot #102 • Creek buffer",
            subtitle = "Surveyed • 2.4 ha",
            selected = false,
            isPolygon = true,
          )
          MockFeatureRow(
            title = "Plot #103 • North ridge",
            subtitle = "Pending • Point",
            selected = false,
            isPolygon = false,
          )
        }
      }

      // Mock terrain / parcel map canvas
      Box(
        modifier =
          Modifier.weight(1f).fillMaxHeight().background(Color(0xFFDCE6D5)).let { base ->
            if (onMapClick != null) {
              base.clickable { onMapClick(0.342, 32.581) }
            } else {
              base
            }
          }
      ) {
        val primaryColor = colors.primary
        val tertiaryColor = colors.tertiary
        Canvas(modifier = Modifier.fillMaxSize()) {
          val zoom = if (isZoomedIn) 1.55f else 1f
          val gridStep = 44.dp.toPx() * zoom
          val gridColor = Color(0xFFB8C9AE).copy(alpha = 0.55f)
          var gx = 0f
          while (gx < size.width) {
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
            gx += gridStep
          }
          var gy = 0f
          while (gy < size.height) {
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            gy += gridStep
          }

          // River / stream path across the mock map
          val river =
            Path().apply {
              moveTo(0f, size.height * 0.72f)
              cubicTo(
                size.width * 0.25f,
                size.height * 0.58f,
                size.width * 0.45f,
                size.height * 0.80f,
                size.width,
                size.height * 0.38f,
              )
            }
          drawPath(
            path = river,
            color = Color(0xFF8AB4CE).copy(alpha = 0.65f),
            style = Stroke(width = 10.dp.toPx() * zoom),
          )

          // Active feature polygon (Plot #101) framed in the left-center of the map
          val cx = size.width * 0.30f
          val cy = size.height * 0.44f
          val rx = 68.dp.toPx() * zoom
          val ry = 50.dp.toPx() * zoom
          val vertices =
            listOf(
              Offset(cx - rx * 0.85f, cy - ry * 0.65f),
              Offset(cx + rx * 0.75f, cy - ry * 0.80f),
              Offset(cx + rx * 0.95f, cy + ry * 0.45f),
              Offset(cx - rx * 0.35f, cy + ry * 0.85f),
            )
          val polyPath =
            Path().apply {
              moveTo(vertices[0].x, vertices[0].y)
              for (i in 1 until vertices.size) {
                lineTo(vertices[i].x, vertices[i].y)
              }
              close()
            }
          drawPath(polyPath, primaryColor.copy(alpha = if (isZoomedIn) 0.28f else 0.18f))
          drawPath(
            polyPath,
            primaryColor,
            style = Stroke(width = (if (isZoomedIn) 3.dp else 2.dp).toPx()),
          )
          vertices.forEach { v ->
            drawCircle(Color.White, radius = 5.dp.toPx(), center = v)
            drawCircle(primaryColor, radius = 3.5.dp.toPx(), center = v)
          }

          // Secondary sample marker (Plot #103)
          if (!isZoomedIn) {
            val p2 = Offset(size.width * 0.16f, size.height * 0.22f)
            drawCircle(Color.White, radius = 7.dp.toPx(), center = p2)
            drawCircle(tertiaryColor, radius = 5.dp.toPx(), center = p2)
          }
        }

        Surface(
          shape = RoundedCornerShape(50),
          color = colors.surface.copy(alpha = 0.9f),
          tonalElevation = 2.dp,
          modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = FitScreenIcon,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text =
                if (isZoomedIn) "Framed: Plot #101 • Scale 1:1,200"
                else "Survey map • Scale 1:2,500",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = colors.onSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MockFeatureRow(
  title: String,
  subtitle: String,
  selected: Boolean,
  isPolygon: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.small,
    color = if (selected) colors.secondaryContainer else colors.surface,
    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    border =
      BorderStroke(
        1.dp,
        if (selected) colors.secondary else colors.outlineVariant.copy(alpha = 0.6f),
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = if (isPolygon) Icons.Outlined.Pentagon else Icons.Outlined.LocationOn,
        contentDescription = null,
        tint = if (selected) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Interactive web preview shown by **Preview** in [FormPreviewTarget.WEB] mode: a browser-like
 * frame around a realistic mock web dashboard ([MockWebDashboardBackdrop]) with the live
 * [CompactFormRunner] in the right-hand panel and an interactive [CompactMapDrawingHost] supporting
 * map drawing and "Adjust view" / "Zoom to fit" (`M5`).
 */
@Composable
internal fun WebPreviewBrowserFrame(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  controller: FormWizardController,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var isZoomedIn by remember { mutableStateOf(false) }
  var activeDrawingPath by remember { mutableStateOf<String?>(null) }
  var activeDrawingKind by remember { mutableStateOf<MapDrawingKind?>(null) }
  var vertexCounter by remember { mutableStateOf(0) }

  val mapHost =
    remember(controller, activeDrawingPath, isZoomedIn) {
      object : CompactMapDrawingHost {
        override val activeDrawingPath: String? = activeDrawingPath

        override fun startDrawing(path: String, kind: MapDrawingKind) {
          activeDrawingPath = path
          activeDrawingKind = kind
          // Seed initial sample vertices so collectors immediately see a drawable shape in preview
          val sampleOffsets =
            when (kind) {
              MapDrawingKind.POINT -> listOf(0.3420 to 32.5810)
              MapDrawingKind.LINE -> listOf(0.3420 to 32.5810, 0.3425 to 32.5818)
              MapDrawingKind.POLYGON ->
                listOf(0.3420 to 32.5810, 0.3426 to 32.5818, 0.3418 to 32.5822)
            }
          sampleOffsets.forEach { (lat, lng) ->
            controller.addMapDrawingVertex(path, kind, lat, lng)
          }
        }

        override fun stopDrawing() {
          activeDrawingPath = null
          activeDrawingKind = null
        }

        override val canFrameGeometry: Boolean = true

        override fun frameGeometry(path: String, kind: MapDrawingKind) {
          isZoomedIn = !isZoomedIn
        }
      }
    }

  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    ElevatedCard(
      modifier = Modifier.width(1040.dp).padding(bottom = 12.dp),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Preview • ${uiState.form.title} • Web",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onSurface,
        )
        TextButton(onClick = { isZoomedIn = !isZoomedIn }) {
          Icon(
            imageVector = FitScreenIcon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(if (isZoomedIn) "Reset map view" else "Adjust view")
        }
      }
    }
    Surface(
      modifier = Modifier.width(1040.dp).height(720.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = colors.surface,
      shadowElevation = 8.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Browser chrome: traffic lights and an address pill.
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(colors.surfaceContainerHigh)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          repeat(3) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.outlineVariant))
          }
          Spacer(Modifier.width(8.dp))
          Surface(
            shape = CircleShape,
            color = colors.surfaceContainerLowest,
            modifier = Modifier.weight(1f).height(26.dp),
          ) {
            Box(contentAlignment = Alignment.CenterStart) {
              Text(
                text = "ground.app/#dashboard",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
              )
            }
          }
        }
        // Mock dashboard behind the right-hand data collection panel.
        Box(modifier = Modifier.fillMaxSize().background(colors.surfaceContainerHighest)) {
          MockWebDashboardBackdrop(
            uiState = uiState,
            isZoomedIn = isZoomedIn,
            onToggleZoom = { isZoomedIn = !isZoomedIn },
            onMapClick = { baseLat, baseLng ->
              val path = activeDrawingPath
              val kind = activeDrawingKind
              if (path != null && kind != null) {
                vertexCounter += 1
                val delta = vertexCounter * 0.0004
                val updated =
                  controller.addMapDrawingVertex(path, kind, baseLat + delta, baseLng + delta)
                if (kind == MapDrawingKind.POINT && updated.isNotEmpty()) {
                  activeDrawingPath = null
                  activeDrawingKind = null
                }
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
          WebFormPanelChrome(
            title = uiState.form.title.ifBlank { "Untitled form" },
            subtitle = "Collect data",
            onClose = actions::closePreview,
            modifier =
              Modifier.align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 14.dp, bottom = 14.dp)
                .width(WebFormPanelWidth)
                .heightIn(max = 720.dp - 46.dp - 70.dp),
          ) {
            Column(
              modifier =
                Modifier.weight(1f, fill = false)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(16.dp)
            ) {
              key(controller) {
                CompactFormRunner(
                  controller = controller,
                  onCancel = actions::closePreview,
                  onSubmitted = { actions.markPreviewSubmitted() },
                  geometryInput = CompactGeometryInput.MapDrawing(host = mapHost),
                )
              }
            }
          }
        }
      }
    }
  }
}
