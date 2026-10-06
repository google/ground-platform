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
package org.groundplatform.v2.core.forms.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.ValidationStatus

/**
 * Completion state of one question in the compact (web) form layout, shown as the icon at the left
 * of its collapsible card.
 */
enum class CompactQuestionStatus {
  /** The question has a value (or is calculated / read-only and so needs no input). */
  ANSWERED,

  /** The collector explicitly skipped this optional question without answering it. */
  SKIPPED,

  /** The question has no value yet. */
  PENDING,

  /** The question has a value that fails validation, or is required and was left empty. */
  INVALID,
}

/**
 * Layout state of a [CompactFormRunner]: which question cards are expanded and which optional
 * questions were explicitly skipped. Neither is part of the form data, so it lives beside the
 * [FormWizardController] rather than in it. Create one per form session with
 * [rememberCompactFormLayoutState].
 *
 * Every card starts expanded (see [expandAllByDefault]). With [autoCollapseOnAnswer], the runner
 * reports each card's [CompactQuestionStatus] through [onStatusChanged] and a card collapses by
 * itself the moment it goes from not answered to [CompactQuestionStatus.ANSWERED]; that happens
 * once per such transition, so a collector who re-opens the card afterwards keeps it open.
 */
class CompactFormLayoutState(
  initiallyExpandAll: Boolean = true,
  /** Whether a card collapses on its own once its question becomes answered. */
  val autoCollapseOnAnswer: Boolean = false,
) {
  private val expanded = mutableStateMapOf<String, Boolean>()
  private val skipped = mutableStateMapOf<String, Boolean>()

  /** Last status reported for each card via [onStatusChanged]; not observed by the UI. */
  private val lastStatus = mutableMapOf<String, CompactQuestionStatus>()

  /**
   * Whether cards that haven't been toggled yet (by the collector, a skip, or an auto-collapse) are
   * expanded. True for both the read-only designer preview and interactive sessions, which then
   * rely on [autoCollapseOnAnswer] to tidy answered cards away.
   */
  var expandAllByDefault: Boolean by mutableStateOf(initiallyExpandAll)

  fun isExpanded(path: String): Boolean = expanded[path] ?: expandAllByDefault

  fun setExpanded(path: String, value: Boolean) {
    expanded[path] = value
  }

  fun toggle(path: String) = setExpanded(path, !isExpanded(path))

  /**
   * Records the latest [status] of the card at [path]. Returns true when this call auto-collapsed
   * the card, i.e. [autoCollapseOnAnswer] is on and the status just changed from a previously seen
   * non-answered status to [CompactQuestionStatus.ANSWERED]. The first status seen for a path never
   * collapses it, so questions that are already answered when the form opens (defaults, calculated
   * values) stay expanded like the rest; nor does a status that stays answered, so manual expands
   * after the collapse are respected.
   */
  fun onStatusChanged(path: String, status: CompactQuestionStatus): Boolean {
    val previous = lastStatus.put(path, status)
    val shouldCollapse =
      autoCollapseOnAnswer &&
        previous != null &&
        previous != CompactQuestionStatus.ANSWERED &&
        status == CompactQuestionStatus.ANSWERED
    if (shouldCollapse) expanded[path] = false
    return shouldCollapse
  }

  fun isSkipped(path: String): Boolean = skipped[path] == true

  /** Marks [path] as skipped and collapses its card. */
  fun skip(path: String) {
    skipped[path] = true
    expanded[path] = false
  }

  /** Clears the skipped mark on [path], e.g. once it receives a value. */
  fun unskip(path: String) {
    skipped.remove(path)
  }

  fun expandAll(paths: List<String>) = paths.forEach { expanded[it] = true }

  fun collapseAll(paths: List<String>) = paths.forEach { expanded[it] = false }
}

/** Remembers a [CompactFormLayoutState] for the lifetime of [controller]. */
@Composable
fun rememberCompactFormLayoutState(
  controller: FormWizardController,
  initiallyExpandAll: Boolean = true,
  autoCollapseOnAnswer: Boolean = false,
): CompactFormLayoutState =
  remember(controller) {
    CompactFormLayoutState(
      initiallyExpandAll = initiallyExpandAll,
      autoCollapseOnAnswer = autoCollapseOnAnswer,
    )
  }

/**
 * Resolves the [CompactQuestionStatus] of [control]. A value always wins over a stale skip mark;
 * read-only and calculated questions count as answered since the collector can't act on them.
 */
fun compactQuestionStatus(
  control: ComponentState.ControlState,
  isSkipped: Boolean,
): CompactQuestionStatus {
  val fs = control.fieldState
  return when {
    fs.validationStatus is ValidationStatus.Invalid && !fs.isEmpty -> CompactQuestionStatus.INVALID
    !fs.isEmpty -> CompactQuestionStatus.ANSWERED
    fs.isReadOnly || fs.isCalculated -> CompactQuestionStatus.ANSWERED
    isSkipped && !fs.isRequired -> CompactQuestionStatus.SKIPPED
    else -> CompactQuestionStatus.PENDING
  }
}

/** One entry in the vertical stack rendered by [CompactFormRunner]. */
sealed interface CompactFormItem {
  /** A single question card. */
  data class Question(val step: FormWizardStep.QuestionStep) : CompactFormItem

  /** A section heading for a `field-list` group, followed by its questions. */
  data class GroupHeading(val step: FormWizardStep.FieldListGroupStep) : CompactFormItem

  /** A repeat group's add/remove controls. */
  data class RepeatHub(val step: FormWizardStep.RepeatHubStep) : CompactFormItem
}

/**
 * Flattens [steps] into the compact layout's vertical stack: every relevant question becomes a card
 * (questions of a `field-list` group are preceded by a heading) and repeat hubs stay as-is. The
 * wizard's final "Review & Submit" step ([FormWizardStep.SummaryStep]) is never an entry: the
 * compact runner's footer owns submission and validation feedback, so the step is left to the
 * controller only. Progress tallies ([compactFormProgress]) therefore exclude it too.
 */
fun buildCompactFormItems(steps: List<FormWizardStep>): List<CompactFormItem> =
  steps.flatMap { step ->
    when (step) {
      is FormWizardStep.QuestionStep -> listOf(CompactFormItem.Question(step))
      is FormWizardStep.FieldListGroupStep ->
        listOf(CompactFormItem.GroupHeading(step)) +
          step.controls.map { control ->
            CompactFormItem.Question(
              FormWizardStep.QuestionStep(
                stepKey = control.canonicalPath,
                title =
                  control.label?.text?.takeIf { it.isNotBlank() }
                    ?: control.canonicalPath.substringAfterLast('/'),
                breadcrumbs = step.breadcrumbs + step.title,
                repeatContext = step.repeatContext,
                control = control,
              )
            )
          }
      is FormWizardStep.RepeatHubStep -> listOf(CompactFormItem.RepeatHub(step))
      is FormWizardStep.SummaryStep -> emptyList()
    }
  }

/** Progress of a compact form: how many question cards are settled (answered or skipped). */
data class CompactFormProgress(
  val answered: Int,
  val skipped: Int,
  val pending: Int,
  val invalid: Int,
) {
  val total: Int
    get() = answered + skipped + pending + invalid

  val settled: Int
    get() = answered + skipped

  val fraction: Float
    get() = if (total == 0) 0f else settled.toFloat() / total.toFloat()
}

/** Tallies the statuses of every question card in [items]. */
fun compactFormProgress(
  items: List<CompactFormItem>,
  isSkipped: (String) -> Boolean,
): CompactFormProgress {
  var answered = 0
  var skipped = 0
  var pending = 0
  var invalid = 0
  items.filterIsInstance<CompactFormItem.Question>().forEach { item ->
    val control = item.step.control
    when (compactQuestionStatus(control, isSkipped(control.canonicalPath))) {
      CompactQuestionStatus.ANSWERED -> answered++
      CompactQuestionStatus.SKIPPED -> skipped++
      CompactQuestionStatus.PENDING -> pending++
      CompactQuestionStatus.INVALID -> invalid++
    }
  }
  return CompactFormProgress(answered, skipped, pending, invalid)
}

/**
 * Compact form runner for web: every relevant question of [controller]'s form, vertically stacked
 * as collapsible cards ([CompactQuestionCard]) so a whole form fits a side panel, with a progress
 * footer and a Submit button. Questions use the same widgets as the mobile one-question-per-screen
 * runner ([ControlWidget]); only the chrome differs.
 *
 * - Each card shows its [CompactQuestionStatus] icon at the left and expands or collapses on click.
 * - Every card starts expanded. In interactive mode a card collapses on its own once its question
 *   becomes answered (see [CompactFormLayoutState.onStatusChanged]); while the collector is still
 *   typing in it, the collapse waits until focus leaves the card. Manual toggles, **Expand all**
 *   and **Collapse all** always win afterwards.
 * - Optional questions have a **Skip** action that marks them [CompactQuestionStatus.SKIPPED] and
 *   collapses the card.
 * - The wizard's final "Review & Submit" step is not rendered as a card; the footer's Submit button
 *   and validation banner cover it, and the header's `n of m answered` counts questions only.
 * - With [readOnly] (used by the Form designer's web preview), inputs are shown but don't react,
 *   all cards stay expanded (no auto-collapse), and the footer is hidden.
 * - [onSelectQuestion] is called with the question's canonical path when a card header is clicked,
 *   so hosts can sync a selection (e.g. the Form designer's properties panel); [selectedPath]
 *   highlights that card.
 * - [questionFilter] drops questions the host answers elsewhere (e.g. the web dashboard fills the
 *   target map feature from a map click rather than a card).
 */
@Composable
fun CompactFormRunner(
  controller: FormWizardController,
  modifier: Modifier = Modifier,
  readOnly: Boolean = false,
  layoutState: CompactFormLayoutState =
    rememberCompactFormLayoutState(controller, autoCollapseOnAnswer = !readOnly),
  showHeader: Boolean = true,
  showFooter: Boolean = !readOnly,
  selectedPath: String? = null,
  onSelectQuestion: ((String) -> Unit)? = null,
  onCancel: (() -> Unit)? = null,
  onSubmitted: ((FinalizationResult.Success) -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  questionFilter: (FormWizardStep.QuestionStep) -> Boolean = { true },
) {
  val state = controller.formState
  val items =
    buildCompactFormItems(controller.steps).filter {
      it !is CompactFormItem.Question || questionFilter(it.step)
    }
  val questionPaths = items.filterIsInstance<CompactFormItem.Question>().map { it.step.stepKey }
  val progress = compactFormProgress(items, layoutState::isSkipped)
  var submitAttempted by remember(controller) { mutableStateOf(false) }
  val submissionResult = controller.submissionResult
  val formTitle =
    state.formDef.title.takeIf { it.isNotBlank() }
      ?: state.formDef.form_id.takeIf { it.isNotBlank() }
      ?: "Form"

  Column(
    modifier = modifier.padding(contentPadding),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (showHeader) {
      CompactFormHeader(
        title = formTitle,
        progress = progress,
        languages = state.availableLanguages,
        activeLanguage = state.activeLanguage,
        onLanguage = if (readOnly) null else controller::setLanguage,
        onExpandAll = { layoutState.expandAll(questionPaths) },
        onCollapseAll = { layoutState.collapseAll(questionPaths) },
      )
    }

    items.forEach { item ->
      when (item) {
        is CompactFormItem.GroupHeading ->
          CompactSectionHeading(text = item.step.title, breadcrumbs = item.step.breadcrumbs)
        is CompactFormItem.RepeatHub ->
          CompactRepeatHub(step = item.step, controller = controller, readOnly = readOnly)
        is CompactFormItem.Question -> {
          val path = item.step.stepKey
          val control = item.step.control
          val status = compactQuestionStatus(control, layoutState.isSkipped(path))
          key(path) {
            // Whether an input inside the card has keyboard focus. Status changes are reported to
            // the layout state only while the card is not being edited, so a text answer
            // auto-collapses once the collector moves on rather than at the first keystroke.
            var hasFocus by remember { mutableStateOf(false) }
            LaunchedEffect(status, hasFocus) {
              if (!hasFocus) layoutState.onStatusChanged(path, status)
            }
            CompactQuestionCard(
              step = item.step,
              controller = controller,
              status = status,
              expanded = layoutState.isExpanded(path),
              onToggle = { layoutState.toggle(path) },
              modifier = Modifier.onFocusChanged { hasFocus = it.hasFocus }.focusGroup(),
              // With a selection host, a header click selects the question instead of collapsing
              // it.
              onHeaderClick = {
                if (onSelectQuestion != null) onSelectQuestion(path) else layoutState.toggle(path)
              },
              onSkip =
                if (!readOnly && isStepOptional(item.step) && control.fieldState.isEmpty) {
                  { layoutState.skip(path) }
                } else {
                  null
                },
              readOnly = readOnly,
              isSelected = selectedPath == path,
              showValidationErrors = submitAttempted,
            )
          }
        }
      }
    }

    if (showFooter) {
      CompactFormFooter(
        progress = progress,
        isValid = state.isValid,
        submissionResult = submissionResult,
        onCancel = onCancel,
        onSubmit = {
          submitAttempted = true
          val result = controller.finalizeForm()
          if (result is FinalizationResult.Success) {
            onSubmitted?.invoke(result)
          } else if (result is FinalizationResult.ValidationFailure) {
            // Open the offending cards so the errors are visible.
            result.errors.forEach { layoutState.setExpanded(it.fieldPath, true) }
          }
        },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactFormHeader(
  title: String,
  progress: CompactFormProgress,
  languages: List<String>,
  activeLanguage: String,
  onLanguage: ((String) -> Unit)?,
  onExpandAll: () -> Unit,
  onCollapseAll: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = colors.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${progress.settled} of ${progress.total} answered",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
      TextButton(onClick = onExpandAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text("Expand all", style = MaterialTheme.typography.labelSmall, softWrap = false)
      }
      TextButton(onClick = onCollapseAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text("Collapse all", style = MaterialTheme.typography.labelSmall, softWrap = false)
      }
    }
    LinearProgressIndicator(
      progress = { progress.fraction.coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth().height(4.dp),
      color = colors.primary,
      trackColor = colors.surfaceContainerHighest,
    )
    if (languages.size > 1 && onLanguage != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        languages.forEach { lang ->
          val active = lang == activeLanguage
          Surface(
            onClick = { onLanguage(lang) },
            shape = CircleShape,
            color = if (active) colors.primary else colors.surfaceContainerHigh,
            contentColor = if (active) colors.onPrimary else colors.onSurfaceVariant,
          ) {
            Text(
              text = lang,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CompactSectionHeading(text: String, breadcrumbs: List<String>) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
    if (breadcrumbs.isNotEmpty()) {
      Text(
        text = breadcrumbs.joinToString(" › "),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      color = colors.primary,
    )
    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(top = 4.dp))
  }
}

/**
 * One collapsible question card of the compact layout. The header row holds the status icon, the
 * question label (with `*` when required), and a chevron; the body holds the hint, the input widget
 * ([ControlWidget]), validation errors, and the optional **Skip** action.
 *
 * Clicking the header runs [onHeaderClick] (by default [onToggle]); the chevron always toggles.
 * Hosts that use a click to select the question (the Form designer) pass their own [onHeaderClick].
 */
@Composable
fun CompactQuestionCard(
  step: FormWizardStep.QuestionStep,
  controller: FormWizardController,
  status: CompactQuestionStatus,
  expanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  onHeaderClick: () -> Unit = onToggle,
  onSkip: (() -> Unit)? = null,
  readOnly: Boolean = false,
  isSelected: Boolean = false,
  showValidationErrors: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val control = step.control
  val fieldState = control.fieldState
  val hintText = control.hint?.text?.takeIf { it.isNotBlank() }
  val borderColor =
    when {
      isSelected -> colors.primary
      status == CompactQuestionStatus.INVALID -> colors.error
      else -> colors.outlineVariant
    }

  OutlinedCard(
    modifier = modifier.fillMaxWidth().animateContentSize(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLowest),
    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CompactStatusIcon(status = status)
        Column(modifier = Modifier.weight(1f)) {
          if (step.breadcrumbs.isNotEmpty() && step.repeatContext != null) {
            Text(
              text = step.breadcrumbs.last(),
              style = MaterialTheme.typography.labelSmall,
              color = colors.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = if (fieldState.isRequired) "${step.title} *" else step.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
          )
          // Collapsed cards summarize the answer on one line.
          if (!expanded) {
            val summary =
              when (status) {
                CompactQuestionStatus.SKIPPED -> "Skipped"
                CompactQuestionStatus.PENDING -> "Not answered"
                else -> formatFieldValueForDisplay(fieldState.value, fieldState.dataType)
              }
            Text(
              text = summary,
              style = MaterialTheme.typography.bodySmall,
              color =
                when (status) {
                  CompactQuestionStatus.ANSWERED -> colors.primary
                  CompactQuestionStatus.INVALID -> colors.error
                  else -> colors.onSurfaceVariant
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector =
              if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse question" else "Expand question",
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      if (expanded) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (hintText != null) {
            Text(
              text = hintText,
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
            )
          }
          if (readOnly) {
            ReadOnlyInputs { ControlWidget(control = control, controller = controller) }
          } else {
            ControlWidget(control = control, controller = controller)
          }
          val validation = fieldState.validationStatus
          if (
            validation is ValidationStatus.Invalid && (showValidationErrors || !fieldState.isEmpty)
          ) {
            validation.errors.forEach { err ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = null,
                  tint = colors.error,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = err.message,
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.error,
                )
              }
            }
          }
          if (onSkip != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Skip", style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Shows [content] dimmed and blocks every pointer event before it reaches the inputs, so a live
 * widget can be used as a faithful read-only preview.
 */
@Composable
private fun ReadOnlyInputs(content: @Composable () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.alpha(0.78f)) { content() }
    Box(
      modifier =
        Modifier.matchParentSize().pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
          }
        }
    )
  }
}

/** Status icon at the left of a [CompactQuestionCard]. */
@Composable
fun CompactStatusIcon(status: CompactQuestionStatus, size: Dp = 20.dp) {
  val colors = MaterialTheme.colorScheme
  val description =
    when (status) {
      CompactQuestionStatus.ANSWERED -> "Answered"
      CompactQuestionStatus.SKIPPED -> "Skipped"
      CompactQuestionStatus.PENDING -> "Pending"
      CompactQuestionStatus.INVALID -> "Needs attention"
    }
  when (status) {
    CompactQuestionStatus.ANSWERED ->
      Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = description,
        tint = colors.primary,
        modifier = Modifier.size(size),
      )
    CompactQuestionStatus.INVALID ->
      Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = description,
        tint = colors.error,
        modifier = Modifier.size(size),
      )
    CompactQuestionStatus.SKIPPED -> StatusRing(colors.outline, size, description, slashed = true)
    CompactQuestionStatus.PENDING -> StatusRing(colors.outline, size, description, slashed = false)
  }
}

/** Hollow ring for pending questions; with [slashed], a diagonal bar marks a skipped one. */
@Composable
private fun StatusRing(color: Color, size: Dp, description: String, slashed: Boolean) {
  Canvas(modifier = Modifier.size(size).semantics { contentDescription = description }) {
    val stroke = 2.dp.toPx()
    val radius = (this.size.minDimension - stroke) / 2f
    drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
    if (slashed) {
      val inset = radius * 0.42f
      drawLine(
        color = color,
        start = Offset(center.x - inset, center.y + inset),
        end = Offset(center.x + inset, center.y - inset),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
      )
    }
  }
}

@Composable
private fun CompactRepeatHub(
  step: FormWizardStep.RepeatHubStep,
  controller: FormWizardController,
  readOnly: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val group = step.repeatGroup
  val label =
    group.label?.text?.takeIf { it.isNotBlank() } ?: group.canonicalPath.substringAfterLast('/')
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = colors.surfaceContainer,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = colors.onSurface,
        )
        Text(
          text = "${group.instances.size} ${if (group.instances.size == 1) "entry" else "entries"}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
      if (group.canRemoveInstance && group.instances.isNotEmpty()) {
        TextButton(
          onClick = {
            controller.removeRepeatInstance(group.canonicalPath, group.instances.last().repeatIndex)
          },
          enabled = !readOnly,
          colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
          contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
          Text("Remove last", style = MaterialTheme.typography.labelSmall, softWrap = false)
        }
      }
      if (group.canAddInstance) {
        FilledTonalButton(
          onClick = { controller.addRepeatInstanceAndOpen(group.canonicalPath) },
          enabled = !readOnly,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
          Text("+ Add", style = MaterialTheme.typography.labelSmall, softWrap = false)
        }
      }
    }
  }
}

@Composable
private fun CompactFormFooter(
  progress: CompactFormProgress,
  isValid: Boolean,
  submissionResult: FinalizationResult?,
  onCancel: (() -> Unit)?,
  onSubmit: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    HorizontalDivider(color = colors.outlineVariant)
    if (submissionResult is FinalizationResult.ValidationFailure) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
      ) {
        Text(
          text =
            "${submissionResult.errors.size} ${if (submissionResult.errors.size == 1) "question needs" else "questions need"} attention before submitting.",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text =
          buildString {
            append("${progress.answered} answered")
            if (progress.skipped > 0) append(" • ${progress.skipped} skipped")
            if (progress.pending > 0) append(" • ${progress.pending} pending")
          },
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f),
        maxLines = 2,
      )
      if (onCancel != null) {
        OutlinedButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 14.dp)) {
          Text("Cancel", softWrap = false)
        }
      }
      Button(onClick = onSubmit, contentPadding = PaddingValues(horizontal = 18.dp)) {
        Text(if (isValid) "Submit" else "Review & submit", softWrap = false)
      }
    }
  }
}
