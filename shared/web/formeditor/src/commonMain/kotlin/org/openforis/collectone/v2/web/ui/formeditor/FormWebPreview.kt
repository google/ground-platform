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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.FlowEdge
import org.openforis.collectone.v2.app.domain.model.editor.FlowEdgeKind
import org.openforis.collectone.v2.app.domain.model.editor.FormFlowGraph
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.ChoiceColorPill
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.FitScreenIcon
import org.openforis.collectone.v2.core.forms.ui.LocalInteractivePointerIcon
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_a_question
import org.openforis.collectone.v2.web.resources.formeditor_add_question_at_end_of_web_form
import org.openforis.collectone.v2.web.resources.formeditor_add_question_here
import org.openforis.collectone.v2.web.resources.formeditor_adjust_view
import org.openforis.collectone.v2.web.resources.formeditor_always_shown
import org.openforis.collectone.v2.web.resources.formeditor_answered_progress
import org.openforis.collectone.v2.web.resources.formeditor_collect_data
import org.openforis.collectone.v2.web.resources.formeditor_decimal_input
import org.openforis.collectone.v2.web.resources.formeditor_has_issues
import org.openforis.collectone.v2.web.resources.formeditor_integer_input
import org.openforis.collectone.v2.web.resources.formeditor_linked_to_standard_field
import org.openforis.collectone.v2.web.resources.formeditor_no_label
import org.openforis.collectone.v2.web.resources.formeditor_question_number_badge
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_badge
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_badge_untitled
import org.openforis.collectone.v2.web.resources.formeditor_reset_view
import org.openforis.collectone.v2.web.resources.formeditor_review_and_submit
import org.openforis.collectone.v2.web.resources.formeditor_section_badge
import org.openforis.collectone.v2.web.resources.formeditor_section_badge_untitled
import org.openforis.collectone.v2.web.resources.formeditor_untitled_form
import org.openforis.collectone.v2.web.ui.common.WebFormPanelChrome
import org.openforis.collectone.v2.web.ui.common.WebFormPanelWidth

const val WEB_DASHBOARD_WIREFRAME_TAG = "web_dashboard_wireframe_backdrop"

private val WebWireframeCardHeight = 82.dp
private val WebWireframeCardGap = 10.dp
private val WebWireframeGhostHeight = 56.dp
private val WebSkipRailWidth = 68.dp
private val WebPanelHeaderOffset = 68.dp

/**
 * The editor canvas in [FormPreviewTarget.WEB] mode: renders a minimalist skeleton-style wireframe
 * of the web dashboard behind the right-hand [WebFormPanelChrome]. Inside the panel, questions are
 * shown as compact wireframe cards with external curved conditional skip arcs along the left margin
 * and a dashed ghost card at the bottom.
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

  androidx.compose.runtime.CompositionLocalProvider(
    LocalDisabledQuestionTypes provides uiState.disabledQuestionTypes
  ) {
    Column(modifier = modifier.fillMaxSize()) {
      PlatformDisabledBanner(uiState, actions)
      GoogleEarthIncompatibleQuestionsBanner(uiState)
      UnavailablePreviewArea(uiState, Modifier.weight(1f).fillMaxWidth()) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(colors.surfaceContainer)
              .backdropClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = actions::selectForm,
              )
        ) {
          WebDashboardWireframeBackdrop(
            isZoomedIn = isZoomedIn,
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
              includeGhost = !uiState.isReadOnly,
            )
            WebFormPanelChrome(
              title = form.title.ifBlank { stringResource(Res.string.formeditor_untitled_form) },
              subtitle = stringResource(Res.string.formeditor_collect_data),
              modifier = Modifier.width(WebFormPanelWidth),
            ) {
              Box(modifier = Modifier.fillMaxWidth()) {
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
                        onSelectSection = actions::selectSection,
                      )
                    }
                  }
                  form.sections
                    .filter { form.questionsIn(it.id).isEmpty() }
                    .forEach { emptySection ->
                      WebEmptySectionCard(
                        section = emptySection,
                        form = form,
                        datasets = uiState.datasets,
                        isSelected = uiState.selectedSectionId == emptySection.id,
                        hasIssues = uiState.issuesForSection(emptySection.id).isNotEmpty(),
                        onClick = { actions.selectSection(emptySection.id) },
                      )
                    }
                  if (!uiState.isReadOnly) {
                    val lastEndSectionId = form.sections.lastOrNull()?.id
                    WebGhostQuestionCard(
                      atIndex = form.questions.size,
                      onAdd = { type, idx ->
                        actions.addQuestion(
                          type,
                          idx,
                          sectionId = "",
                          afterSectionId = lastEndSectionId,
                        )
                      },
                      onAddSection = { idx ->
                        val secId =
                          actions.addSection(
                            "Section",
                            repeatable = false,
                            atIndex = idx,
                            afterSectionId = lastEndSectionId,
                          )
                        actions.selectSection(secId)
                      },
                      onAddRepeatedSection = { idx ->
                        val secId =
                          actions.addSection(
                            "Section",
                            repeatable = true,
                            atIndex = idx,
                            afterSectionId = lastEndSectionId,
                          )
                        actions.selectSection(secId)
                      },
                    )
                  }
                  WebWireframeSubmitFooter(
                    questionCount = form.questions.size,
                    isSelected = uiState.isFormSelected,
                    onClick = actions::selectForm,
                  )
                }
                WebStepInsertOverlay(
                  form = form,
                  isReadOnly = uiState.isReadOnly,
                  actions = actions,
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
  includeGhost: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val selectedSlot = if (selectedIndex >= 0) FormFlowGraph.questionSlot(selectedIndex) else -1
  val stepPitch = WebWireframeCardHeight + WebWireframeCardGap
  val ghostExtra = if (includeGhost) WebWireframeGhostHeight + WebWireframeCardGap else 0.dp
  val totalCardsHeight = WebPanelHeaderOffset + stepPitch * questionCount + ghostExtra + 48.dp

  Canvas(modifier = modifier.width(WebSkipRailWidth).height(totalCardsHeight)) {
    val railRight = size.width - 4.dp.toPx()
    fun slotY(slot: Int): Float =
      when {
        slot <= 0 -> (WebPanelHeaderOffset - 18.dp).toPx()
        slot in 1..questionCount ->
          (WebPanelHeaderOffset + stepPitch * (slot - 1) + WebWireframeCardHeight / 2).toPx()
        else -> (WebPanelHeaderOffset + stepPitch * questionCount + ghostExtra + 20.dp).toPx()
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
  onSelectSection: (String) -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  var isHovered by remember { mutableStateOf(false) }
  val isHoverHighlighted = isHovered && !isSelected
  val noLabel = stringResource(Res.string.formeditor_no_label)
  val displayLabel = question.displayLabel(activeLanguage, defaultLanguage).ifBlank { noLabel }
  val section = form.sectionOf(question)
  val isConditionallyShown = form.isShownConditionally(question)
  val relevanceText =
    form.describeRelevance(question, datasets) ?: stringResource(Res.string.formeditor_always_shown)

  OutlinedCard(
    onClick = onClick,
    modifier =
      Modifier.fillMaxWidth().height(WebWireframeCardHeight).pointerInput(Unit) {
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
      WebWireframeCardHeader(
        index = index,
        question = question,
        section = section,
        displayLabel = displayLabel,
        isSelected = isSelected,
        isLinked = isLinked,
        hasIssues = hasIssues,
        onSelectSection = onSelectSection,
      )
      WebWireframeCardFooter(
        question = question,
        datasets = datasets,
        activeLanguage = activeLanguage,
        defaultLanguage = defaultLanguage,
        relevanceText = relevanceText,
        isConditionallyShown = isConditionallyShown,
        isSelected = isSelected,
        isZoomedIn = isZoomedIn,
        onToggleZoom = onToggleZoom,
      )
    }
  }
}

@Composable
private fun WebWireframeCardHeader(
  index: Int,
  question: EditorQuestion,
  section: EditorSection?,
  displayLabel: String,
  isSelected: Boolean,
  isLinked: Boolean,
  hasIssues: Boolean,
  onSelectSection: (String) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val iconTint = if (isSelected) colors.onSecondaryContainer else colors.primary
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TonalBadge(
      text = stringResource(Res.string.formeditor_question_number_badge, index + 1),
      tone = BadgeTone.NEUTRAL,
    )
    if (section != null) {
      TonalBadge(
        text = if (section.repeatable) "↻ ${section.title}" else "▤ ${section.title}",
        tone = if (section.repeatable) BadgeTone.PRIMARY else BadgeTone.SECONDARY,
        modifier = Modifier.clip(CircleShape).clickable { onSelectSection(section.id) },
      )
    }
    Icon(
      imageVector = questionTypeIcon(question.type),
      contentDescription = null,
      modifier = Modifier.size(15.dp),
      tint = iconTint,
    )
    Text(
      text = if (question.required) "$displayLabel *" else displayLabel,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (isLinked) {
      Icon(
        Icons.Outlined.Link,
        contentDescription = stringResource(Res.string.formeditor_linked_to_standard_field),
        tint = iconTint,
        modifier = Modifier.size(14.dp),
      )
    }
    if (hasIssues) {
      Icon(
        Icons.Outlined.Warning,
        contentDescription = stringResource(Res.string.formeditor_has_issues),
        tint = colors.error,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

@Composable
private fun WebWireframeCardFooter(
  question: EditorQuestion,
  datasets: List<EditorDataset>,
  activeLanguage: String,
  defaultLanguage: String,
  relevanceText: String,
  isConditionallyShown: Boolean,
  isSelected: Boolean,
  isZoomedIn: Boolean,
  onToggleZoom: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
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
          isConditionallyShown -> colors.primary
          else -> colors.onSurfaceVariant
        },
      fontWeight = if (isConditionallyShown) FontWeight.Bold else FontWeight.Normal,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(8.dp))
    if (question.type.isGeometry) {
      WireframeGeometryZoomChip(isZoomedIn = isZoomedIn, onToggleZoom = onToggleZoom)
    } else {
      WireframeQuestionSummaryBadge(
        question = question,
        datasets = datasets,
        activeLanguage = activeLanguage,
        defaultLanguage = defaultLanguage,
      )
    }
  }
}

@Composable
private fun WireframeGeometryZoomChip(isZoomedIn: Boolean, onToggleZoom: () -> Unit) {
  val colors = MaterialTheme.colorScheme
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
        text =
          if (isZoomedIn) stringResource(Res.string.formeditor_reset_view)
          else stringResource(Res.string.formeditor_adjust_view),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
private fun WireframeQuestionSummaryBadge(
  question: EditorQuestion,
  datasets: List<EditorDataset>,
  activeLanguage: String,
  defaultLanguage: String,
) {
  val colors = MaterialTheme.colorScheme
  val resolvedChoices =
    if (
      question.type == EditorQuestionType.SELECT_ONE ||
        question.type == EditorQuestionType.SELECT_MULTIPLE
    ) {
      question.resolvedChoices(datasets)
    } else {
      emptyList()
    }
  val coloredChoices = resolvedChoices.mapNotNull { choice ->
    ChoiceImageRegistry.parseChoiceColor(choice.colorHex)?.let { color ->
      choice.displayLabel(activeLanguage, defaultLanguage).ifBlank { choice.value } to color
    }
  }
  if (coloredChoices.isNotEmpty()) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      coloredChoices.take(2).forEach { (label, color) ->
        ChoiceColorPill(label = label, color = color, compact = true)
      }
      if (resolvedChoices.size > 2) {
        Text(
          text = "+${resolvedChoices.size - 2}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
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

@Composable
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
      if (choices.isEmpty()) stringResource(question.type.labelRes)
      else
        choices.take(2).joinToString(" · ") {
          it.displayLabel(activeLanguage, defaultLanguage).ifBlank { it.value }
        } + if (choices.size > 2) " (+${choices.size - 2})" else ""
    }
    EditorQuestionType.NUMBER ->
      if (question.decimalPlaces <= 0) stringResource(Res.string.formeditor_integer_input)
      else stringResource(Res.string.formeditor_decimal_input, question.decimalPlaces)
    else -> stringResource(question.type.labelRes)
  }

/**
 * Compact card representing a newly added section that has no questions yet in Web layout mode, so
 * authors can see and select it immediately.
 */
@Composable
private fun WebEmptySectionCard(
  section: EditorSection,
  form: EditorForm,
  datasets: List<EditorDataset>,
  isSelected: Boolean,
  hasIssues: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val badgeText =
    when {
      section.repeatable && section.title.isBlank() ->
        stringResource(Res.string.formeditor_repeated_section_badge_untitled)
      section.repeatable ->
        stringResource(Res.string.formeditor_repeated_section_badge, section.title)
      section.title.isBlank() -> stringResource(Res.string.formeditor_section_badge_untitled)
      else -> stringResource(Res.string.formeditor_section_badge, section.title)
    }
  val relevanceText =
    form.describeRelevance(section, datasets) ?: stringResource(Res.string.formeditor_always_shown)

  OutlinedCard(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (isSelected) colors.secondaryContainer else colors.surfaceContainerLow,
        contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = if (isSelected) 2.dp else 1.dp,
        color =
          when {
            hasIssues -> colors.error
            isSelected -> colors.secondary
            section.repeatable -> colors.primary.copy(alpha = 0.6f)
            else -> colors.outlineVariant
          },
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TonalBadge(
        text = badgeText,
        tone = if (section.repeatable) BadgeTone.PRIMARY else BadgeTone.SECONDARY,
      )
      Text(
        text = relevanceText,
        style = MaterialTheme.typography.labelSmall,
        color = if (section.isConditional) colors.primary else colors.onSurfaceVariant,
        fontWeight = if (section.isConditional) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (hasIssues) {
        Icon(
          Icons.Outlined.Warning,
          contentDescription = stringResource(Res.string.formeditor_has_issues),
          tint = colors.error,
          modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

/**
 * Hover-revealed `+` insertion buttons overlaid at the top and between stacked wireframe cards in
 * [WebLayoutCanvasPanel] without altering vertical layout metrics when idle.
 */
@Composable
private fun WebStepInsertOverlay(
  form: EditorForm,
  isReadOnly: Boolean,
  actions: FormEditorActions,
) {
  if (isReadOnly) return
  val handleAddSection: (Int?) -> Unit = { idx ->
    val secId = actions.addSection("Section", repeatable = false, atIndex = idx)
    actions.selectSection(secId)
  }
  val handleAddRepeatedSection: (Int?) -> Unit = { idx ->
    val secId = actions.addSection("Section", repeatable = true, atIndex = idx)
    actions.selectSection(secId)
  }
  val cardStep = WebWireframeCardHeight + WebWireframeCardGap
  Box(modifier = Modifier.fillMaxWidth().zIndex(2f)) {
    for (insertIndex in 0..form.questions.size) {
      val centerY =
        if (insertIndex == 0) {
          7.dp
        } else {
          14.dp + cardStep * insertIndex - WebWireframeCardGap / 2
        }
      val inheritedSectionId =
        when {
          insertIndex == 0 -> ""
          insertIndex in form.questions.indices &&
            form.questions[insertIndex - 1].sectionId == form.questions[insertIndex].sectionId ->
            form.questions[insertIndex].sectionId
          else -> ""
        }
      WebInsertStepAffordance(
        centerY = centerY,
        atIndex = insertIndex,
        onAdd = { type, idx -> actions.addQuestion(type, idx, sectionId = inheritedSectionId) },
        onAddSection = handleAddSection,
        onAddRepeatedSection = handleAddRepeatedSection,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

@Composable
private fun WebInsertStepAffordance(
  centerY: Dp,
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  onAddSection: (Int?) -> Unit,
  onAddRepeatedSection: ((Int?) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val visible = isHovered || menuExpanded
  val hitHeight = 20.dp
  val buttonSize = 22.dp

  Box(
    modifier =
      modifier
        .offset(y = (centerY - hitHeight / 2).coerceAtLeast(0.dp))
        .size(width = 72.dp, height = hitHeight)
        .hoverable(interactionSource),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(
      LocalInteractivePointerIcon provides
        if (visible) LocalInteractivePointerIcon.current else PointerIcon.Default
    ) {
      Surface(
        onClick = { menuExpanded = true },
        shape = CircleShape,
        color = colors.secondary,
        contentColor = colors.onSecondary,
        shadowElevation = if (visible) 3.dp else 0.dp,
        modifier = Modifier.size(buttonSize).graphicsLayer { alpha = if (visible) 1f else 0f },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.Add,
            contentDescription = stringResource(Res.string.formeditor_add_question_here),
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
    AddQuestionMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onAdd = onAdd,
      atIndex = atIndex,
      onAddSection = onAddSection,
      onAddRepeatedSection = onAddRepeatedSection,
    )
  }
}

/**
 * Compact dashed ghost card at the bottom of the web question stack (`F6`), opening
 * [AddQuestionMenu] when clicked.
 */
@Composable
private fun WebGhostQuestionCard(
  atIndex: Int,
  onAdd: (EditorQuestionType, Int?) -> Unit,
  onAddSection: ((Int?) -> Unit)? = null,
  onAddRepeatedSection: ((Int?) -> Unit)? = null,
) {
  var isHovered by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val addQuestionDescription = stringResource(Res.string.formeditor_add_question_at_end_of_web_form)
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
        .semantics { contentDescription = addQuestionDescription },
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
        text = stringResource(Res.string.formeditor_add_a_question),
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
      onAddSection = onAddSection,
      onAddRepeatedSection = onAddRepeatedSection,
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
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(Res.string.formeditor_answered_progress, questionCount),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
      )
      Surface(
        shape = RoundedCornerShape(50),
        color = colors.primary,
        contentColor = colors.onPrimary,
      ) {
        Text(
          text = stringResource(Res.string.formeditor_review_and_submit),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
      }
    }
  }
}

/**
 * Minimalist skeleton-style wireframe of the web dashboard behind the right-hand data collection
 * panel: uses neutral geometric shapes (header bar placeholders, left sidebar skeleton rows, and a
 * muted spatial wireframe) without labels, icons, or realistic map details.
 */
@Composable
internal fun WebDashboardWireframeBackdrop(
  isZoomedIn: Boolean,
  modifier: Modifier = Modifier,
  onMapClick: ((Double, Double) -> Unit)? = null,
) {
  val palette = WireframePalette(MaterialTheme.colorScheme)
  Column(modifier = modifier.testTag(WEB_DASHBOARD_WIREFRAME_TAG)) {
    WebDashboardTopBarSkeleton(palette)
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      WebDashboardSidebarSkeleton(palette)
      WebDashboardMapWireframe(
        palette = palette,
        isZoomedIn = isZoomedIn,
        onMapClick = onMapClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
    }
  }
}

private class WireframePalette(colors: ColorScheme) {
  val chromeSurface: Color = colors.surfaceContainerLowest
  val canvasSurface: Color = colors.surfaceContainerLow
  val hairlineColor: Color = colors.outlineVariant.copy(alpha = 0.45f)
  val skeletonStrong: Color = colors.onSurface.copy(alpha = 0.10f)
  val skeletonMid: Color = colors.onSurface.copy(alpha = 0.065f)
  val skeletonSoft: Color = colors.onSurface.copy(alpha = 0.04f)
  val wireframeStroke: Color = colors.outlineVariant.copy(alpha = 0.55f)
}

@Composable
private fun WebDashboardTopBarSkeleton(palette: WireframePalette) {
  Column(modifier = Modifier.fillMaxWidth().background(palette.chromeSurface)) {
    Row(
      modifier = Modifier.fillMaxWidth().height(43.dp).padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SkeletonBlock(18.dp, 18.dp, RoundedCornerShape(5.dp), palette.skeletonStrong)
      SkeletonBlock(116.dp, 10.dp, CircleShape, palette.skeletonStrong)
      SkeletonBlock(46.dp, 18.dp, CircleShape, palette.skeletonSoft)
      Spacer(Modifier.weight(1f))
      SkeletonBlock(72.dp, 22.dp, CircleShape, palette.skeletonSoft)
    }
    HorizontalDivider(thickness = 1.dp, color = palette.hairlineColor)
  }
}

@Composable
private fun WebDashboardSidebarSkeleton(palette: WireframePalette) {
  Row(modifier = Modifier.width(204.dp).fillMaxHeight()) {
    Column(
      modifier =
        Modifier.weight(1f).fillMaxHeight().background(palette.chromeSurface).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.skeletonSoft)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SkeletonBlock(11.dp, 11.dp, CircleShape, palette.skeletonMid)
        SkeletonBlock(84.dp, 8.dp, CircleShape, palette.skeletonMid)
      }
      WireframeFeatureRowSkeleton(0.78f, 0.46f, RoundedCornerShape(4.dp), true, palette)
      WireframeFeatureRowSkeleton(0.64f, 0.40f, RoundedCornerShape(4.dp), false, palette)
      WireframeFeatureRowSkeleton(0.72f, 0.50f, CircleShape, false, palette)
      WireframeFeatureRowSkeleton(0.56f, 0.34f, RoundedCornerShape(4.dp), false, palette)
    }
    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(palette.hairlineColor))
  }
}

@Composable
private fun WebDashboardMapWireframe(
  palette: WireframePalette,
  isZoomedIn: Boolean,
  onMapClick: ((Double, Double) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier.background(palette.canvasSurface).let { base ->
        if (onMapClick != null) base.clickable { onMapClick(0.342, 32.581) } else base
      }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) { drawWireframeMapShapes(palette, isZoomedIn) }
    Column(
      modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      repeat(2) {
        Box(
          modifier =
            Modifier.size(26.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(palette.chromeSurface.copy(alpha = 0.85f))
              .border(1.dp, palette.hairlineColor, RoundedCornerShape(6.dp)),
          contentAlignment = Alignment.Center,
        ) {
          SkeletonBlock(10.dp, 2.dp, CircleShape, palette.skeletonStrong)
        }
      }
    }
  }
}

private fun DrawScope.drawWireframeMapShapes(palette: WireframePalette, isZoomedIn: Boolean) {
  val obscuredRight = (WebFormPanelWidth + WebSkipRailWidth + 24.dp).toPx()
  val openWidth = size.width - obscuredRight
  if (openWidth < 140.dp.toPx()) return
  val zoom = if (isZoomedIn) 1.3f else 1f
  val maxRadiusX = (openWidth * 0.28f).coerceAtMost(58.dp.toPx()) * zoom
  val maxRadiusY = (maxRadiusX * 0.74f).coerceAtMost(44.dp.toPx() * zoom)
  val cx = openWidth * 0.54f
  val cy = size.height * 0.44f
  val vertices =
    listOf(
      Offset(cx - maxRadiusX * 0.85f, cy - maxRadiusY * 0.65f),
      Offset(cx + maxRadiusX * 0.75f, cy - maxRadiusY * 0.80f),
      Offset(cx + maxRadiusX * 0.95f, cy + maxRadiusY * 0.45f),
      Offset(cx - maxRadiusX * 0.35f, cy + maxRadiusY * 0.85f),
    )
  val polyPath =
    Path().apply {
      moveTo(vertices[0].x, vertices[0].y)
      for (i in 1 until vertices.size) lineTo(vertices[i].x, vertices[i].y)
      close()
    }
  drawPath(polyPath, palette.skeletonSoft)
  drawPath(polyPath, palette.wireframeStroke, style = Stroke(width = 1.5.dp.toPx()))
  vertices.forEach { v ->
    drawCircle(palette.canvasSurface, radius = 3.5.dp.toPx(), center = v)
    drawCircle(
      palette.wireframeStroke,
      radius = 3.5.dp.toPx(),
      center = v,
      style = Stroke(width = 1.5.dp.toPx()),
    )
  }
  if (!isZoomedIn && openWidth >= 200.dp.toPx()) {
    drawCircle(
      palette.wireframeStroke,
      radius = 4.5.dp.toPx(),
      center = Offset(openWidth * 0.28f, size.height * 0.24f),
      style = Stroke(width = 1.5.dp.toPx()),
    )
  }
}

@Composable
internal fun SkeletonBlock(
  width: Dp,
  height: Dp,
  shape: Shape,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.width(width).height(height).clip(shape).background(color))
}

@Composable
private fun WireframeFeatureRowSkeleton(
  titleFraction: Float,
  subtitleFraction: Float,
  leadingShape: Shape,
  highlighted: Boolean,
  palette: WireframePalette,
) {
  val borderColor =
    if (highlighted) palette.hairlineColor else palette.hairlineColor.copy(alpha = 0.3f)
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .background(if (highlighted) palette.skeletonSoft else Color.Transparent)
        .border(1.dp, borderColor, MaterialTheme.shapes.small)
        .padding(horizontal = 10.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    SkeletonBlock(
      width = 15.dp,
      height = 15.dp,
      shape = leadingShape,
      color = if (highlighted) palette.skeletonStrong else palette.skeletonMid,
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(
        modifier =
          Modifier.fillMaxWidth(titleFraction)
            .height(8.dp)
            .clip(CircleShape)
            .background(if (highlighted) palette.skeletonStrong else palette.skeletonMid)
      )
      Box(
        modifier =
          Modifier.fillMaxWidth(subtitleFraction)
            .height(6.dp)
            .clip(CircleShape)
            .background(if (highlighted) palette.skeletonMid else palette.skeletonSoft)
      )
    }
  }
}
