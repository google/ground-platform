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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.ValidationStatus
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_cancel
import org.openforis.collectone.v2.core.forms.ui.resources.forms_collapse_all
import org.openforis.collectone.v2.core.forms.ui.resources.forms_collapse_question
import org.openforis.collectone.v2.core.forms.ui.resources.forms_expand_all
import org.openforis.collectone.v2.core.forms.ui.resources.forms_expand_question
import org.openforis.collectone.v2.core.forms.ui.resources.forms_progress_answered_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_progress_answered_of_total
import org.openforis.collectone.v2.core.forms.ui.resources.forms_progress_pending_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_progress_skipped_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_questions_need_attention
import org.openforis.collectone.v2.core.forms.ui.resources.forms_required_label
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review_and_submit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_skip
import org.openforis.collectone.v2.core.forms.ui.resources.forms_status_answered
import org.openforis.collectone.v2.core.forms.ui.resources.forms_status_needs_attention
import org.openforis.collectone.v2.core.forms.ui.resources.forms_status_not_answered
import org.openforis.collectone.v2.core.forms.ui.resources.forms_status_pending
import org.openforis.collectone.v2.core.forms.ui.resources.forms_status_skipped
import org.openforis.collectone.v2.core.forms.ui.resources.forms_submit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_untitled_form

/** How long a card stays highlighted after a [CompactFocusRequest] brought it into view. */
internal const val FOCUS_FLASH_MILLIS = 1500L

/**
 * Compact form runner for web: every relevant question of [controller]'s form, vertically stacked
 * as collapsible cards ([CompactQuestionCard]) so a whole form fits a side panel, with a progress
 * footer and a Submit button. Questions use the same widgets as the mobile one-question-per-screen
 * runner ([ControlWidget]); only the chrome differs. The widgets render at [FormDensity.COMPACT]
 * (provided through [LocalFormDensity] around the whole stack, in both interactive and [readOnly]
 * mode), which hides the numeric stepper buttons and makes free-text fields slightly shorter; the
 * mobile runner keeps the default [FormDensity.COMFORTABLE].
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
 * - [geometryInput] chooses how `geopoint` / `geotrace` / `geoshape` questions are answered: the
 *   device GPS widgets, or a request to draw on the host's map ([CompactGeometryInput.MapDrawing],
 *   what the web dashboard uses). A card whose geometry is being drawn doesn't auto-collapse until
 *   the collector finishes drawing.
 * - [focusRequest] brings a question into view on the host's behalf (e.g. after its geometry was
 *   clicked on the web dashboard's map): the card expands, scrolls into view through the enclosing
 *   scroll container, and is highlighted for a moment. A new [CompactFocusRequest.token] re-fires
 *   the same path.
 */
@OptIn(ExperimentalFoundationApi::class)
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
  geometryInput: CompactGeometryInput = CompactGeometryInput.Device,
  focusRequest: CompactFocusRequest? = null,
  variant: FormVariant = FormVariant.WEB,
) {
  remember(controller, variant) {
    controller.updateVariant(variant)
    variant
  }
  val state = controller.formState
  val drawingHost = (geometryInput as? CompactGeometryInput.MapDrawing)?.host
  val items =
    buildCompactFormItems(controller.steps).filter {
      it !is CompactFormItem.Question || questionFilter(it.step)
    }
  val blocks = buildCompactFormBlocks(items)
  val questionNumbers = compactQuestionNumbers(blocks)
  val questionPaths = items.filterIsInstance<CompactFormItem.Question>().map { it.step.stepKey }
  val progress = compactFormProgress(items, layoutState::isSkipped, variant)
  var submitAttempted by remember(controller) { mutableStateOf(false) }
  val submissionResult = controller.submissionResult
  val formTitle = formDisplayTitle(state.formDef, untitled = Res.string.forms_untitled_form)

  // Both the live web form and the designer's read-only preview render at compact density (no
  // numeric steppers, slightly shorter text fields), so the preview matches what collectors get.
  CompositionLocalProvider(
    LocalFormDensity provides FormDensity.COMPACT,
    LocalFormVariant provides variant,
  ) {
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

      // Each item renders the same wherever it sits; repeat entries only add a frame around theirs.
      val renderItem: @Composable (CompactFormItem) -> Unit = { item ->
        when (item) {
          is CompactFormItem.GroupHeading ->
            CompactSectionHeading(
              text = item.step.title.resolve(),
              breadcrumbs = breadcrumbsWithinEntry(item.step.breadcrumbs, item.step.repeatContext),
            )
          // Hubs are folded into their CompactFormBlock.RepeatSection.
          is CompactFormItem.RepeatHub -> Unit
          is CompactFormItem.Question -> {
            val path = item.step.stepKey
            val control = item.step.control
            val status = compactQuestionStatus(control, layoutState.isSkipped(path), variant)
            val isIncompatible = incompatibleQuestionTypeForVariant(control, variant) != null
            key(path) {
              // Whether an input inside the card has keyboard focus. Status changes are reported
              // to the layout state only while the card is not being edited, so a text answer
              // auto-collapses once the collector moves on rather than at the first keystroke. A
              // geometry being drawn on the host's map counts as being edited too.
              var hasFocus by remember { mutableStateOf(false) }
              var hasBlurred by remember { mutableStateOf(false) }
              val isDrawing = drawingHost?.activeDrawingPath == path
              LaunchedEffect(status, hasFocus, isDrawing) {
                if (!hasFocus && !isDrawing) layoutState.onStatusChanged(path, status)
              }
              // A host focus request for this card: expand, scroll into view, flash the border.
              val bringIntoView = remember { BringIntoViewRequester() }
              var flashToken by remember { mutableStateOf<Long?>(null) }
              LaunchedEffect(focusRequest) {
                val request = focusRequest?.takeIf { it.targets(path) } ?: return@LaunchedEffect
                layoutState.setExpanded(path, true)
                flashToken = request.token
                // Let the expanded card lay out before measuring where to scroll to.
                withFrameNanos {}
                withFrameNanos {}
                bringIntoView.bringIntoView()
                delay(FOCUS_FLASH_MILLIS)
                if (flashToken == request.token) flashToken = null
              }
              CompactQuestionCard(
                step = item.step,
                controller = controller,
                status = status,
                expanded = layoutState.isExpanded(path),
                onToggle = { layoutState.toggle(path) },
                modifier =
                  Modifier.bringIntoViewRequester(bringIntoView)
                    .onFocusChanged {
                      if (hasFocus && !it.hasFocus) hasBlurred = true
                      hasFocus = it.hasFocus
                    }
                    .focusGroup(),
                questionNumber = questionNumbers[path],
                // With a selection host, a header click selects the question instead of
                // collapsing it.
                onHeaderClick = {
                  if (onSelectQuestion != null) onSelectQuestion(path) else layoutState.toggle(path)
                },
                onSkip =
                  if (
                    !readOnly &&
                      !isIncompatible &&
                      isStepOptional(item.step) &&
                      control.fieldState.isEmpty
                  ) {
                    { layoutState.skip(path) }
                  } else {
                    null
                  },
                readOnly = readOnly,
                isSelected = selectedPath == path || flashToken != null,
                showValidationErrors =
                  submitAttempted || (hasBlurred && !hasFocus && !control.fieldState.isEmpty),
                geometryInput = geometryInput,
                variant = variant,
              )
            }
          }
        }
      }

      CompactBlocks(
        blocks = blocks,
        controller = controller,
        layoutState = layoutState,
        readOnly = readOnly,
        renderItem = renderItem,
      )

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
      FormHeaderTitle(
        title = title,
        subtitle =
          stringResource(
            Res.string.forms_progress_answered_of_total,
            progress.settled,
            progress.total,
          ),
        titleColor = colors.onSurface,
        subtitleColor = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onExpandAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(
          stringResource(Res.string.forms_expand_all),
          style = MaterialTheme.typography.labelSmall,
          softWrap = false,
        )
      }
      TextButton(onClick = onCollapseAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(
          stringResource(Res.string.forms_collapse_all),
          style = MaterialTheme.typography.labelSmall,
          softWrap = false,
        )
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

/**
 * Renders [blocks] in order: lone items through [renderItem], repeat groups as a
 * [CompactRepeatSection] whose entries recurse into their own blocks (nested repeats included).
 * Deleting an entry also forgets the expand / collapse state of its cards, since later entries
 * shift into its paths.
 */
@Composable
private fun CompactBlocks(
  blocks: List<CompactFormBlock>,
  controller: FormWizardController,
  layoutState: CompactFormLayoutState,
  readOnly: Boolean,
  renderItem: @Composable (CompactFormItem) -> Unit,
) {
  blocks.forEach { block ->
    when (block) {
      is CompactFormBlock.Single -> renderItem(block.item)
      is CompactFormBlock.RepeatSection ->
        key(block.repeatPath) {
          CompactRepeatSection(
            section = block,
            onAddEntry = { controller.addRepeatInstanceAndOpen(block.repeatPath) },
            onRemoveEntry = { index ->
              controller.removeRepeatInstance(block.repeatPath, index)
              layoutState.forgetRepeatEntries(block.repeatPath)
            },
            readOnly = readOnly,
          ) { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              CompactBlocks(entry.blocks, controller, layoutState, readOnly, renderItem)
            }
          }
        }
    }
  }
}

/**
 * [breadcrumbs] below the repeat entry [repeatContext] points at: the entry's frame already shows
 * its title (and everything above it), so cards inside only need the groups within the entry.
 */
internal fun breadcrumbsWithinEntry(
  breadcrumbs: List<String>,
  repeatContext: RepeatStepContext?,
): List<String> {
  if (repeatContext == null) return breadcrumbs
  val entryCrumb = breadcrumbs.lastIndexOf(repeatContext.instanceLabel)
  return if (entryCrumb >= 0) breadcrumbs.drop(entryCrumb + 1) else breadcrumbs
}

@Composable
private fun CompactSectionHeading(text: String, breadcrumbs: List<String>) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }.padding(top = 6.dp)
  ) {
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
      modifier = Modifier.semantics { heading() },
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
 * Both the header row and its chevron button are excluded from keyboard `Tab` / `Shift+Tab`
 * traversal (`canFocus = false`) so collectors step directly between answer inputs while retaining
 * pointer click, hover cursor, and accessibility semantics.
 */
@Composable
fun CompactQuestionCard(
  step: FormWizardStep.QuestionStep,
  controller: FormWizardController,
  status: CompactQuestionStatus,
  expanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  questionNumber: Int? = null,
  onHeaderClick: () -> Unit = onToggle,
  onSkip: (() -> Unit)? = null,
  readOnly: Boolean = false,
  isSelected: Boolean = false,
  showValidationErrors: Boolean = false,
  geometryInput: CompactGeometryInput = CompactGeometryInput.Device,
  variant: FormVariant = LocalFormVariant.current,
) {
  val colors = MaterialTheme.colorScheme
  val control = step.control
  val fieldState = control.fieldState
  val incompatibleType = incompatibleQuestionTypeForVariant(control, variant)
  val hintText = control.hint?.text?.takeIf { it.isNotBlank() }
  val mapDrawing =
    (geometryInput as? CompactGeometryInput.MapDrawing)?.takeIf {
      !fieldState.isReadOnly && mapDrawingKindOf(fieldState.dataType) != null
    }
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
      // Skip the question card heading row (and its collapse/expand chevron below) during Tab /
      // Shift+Tab keyboard traversal so collectors move directly between answer inputs. Pointer
      // clicks, hover cursor styling, and accessibility click semantics remain intact.
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .focusProperties { canFocus = false }
            .clickable(onClick = onHeaderClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CompactStatusIcon(status = status, questionNumber = questionNumber)
        Column(modifier = Modifier.weight(1f)) {
          // Inside a repeat entry the entry's frame names it, so only a group within the entry
          // (if any) is worth repeating on the card.
          val contextLabel =
            breadcrumbsWithinEntry(step.breadcrumbs, step.repeatContext).lastOrNull()
          if (contextLabel != null) {
            Text(
              text = contextLabel,
              style = MaterialTheme.typography.labelSmall,
              color = colors.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text =
              if (fieldState.isRequired && incompatibleType == null) {
                stringResource(Res.string.forms_required_label, step.questionLabel)
              } else {
                step.questionLabel
              },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
          )
          // Collapsed cards summarize the answer on one line (or as color-coded pills when the
          // selected choice options carry option colors).
          if (!expanded) {
            val selectedValues =
              fieldState.value
                ?.list_value
                ?.values
                ?.mapNotNull { it.string_value }
                ?.filter { it.isNotBlank() }
                ?: fieldState.value?.scalar_value?.string_value?.split(' ')?.filter {
                  it.isNotBlank()
                }
                ?: emptyList()
            val coloredSelections =
              if (status == CompactQuestionStatus.ANSWERED) {
                ChoiceImageRegistry.resolveSelectedOptionColors(control.options, selectedValues)
              } else {
                emptyList()
              }
            if (coloredSelections.isNotEmpty()) {
              ChoiceColorPillsRow(
                items = coloredSelections,
                compact = true,
                modifier = Modifier.padding(top = 2.dp),
              )
            } else {
              val summary =
                when (status) {
                  CompactQuestionStatus.SKIPPED -> stringResource(Res.string.forms_status_skipped)
                  CompactQuestionStatus.PENDING ->
                    stringResource(Res.string.forms_status_not_answered)
                  else -> fieldValueDisplayText(fieldState.value, fieldState.dataType)
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
        }
        IconButton(
          onClick = onToggle,
          modifier = Modifier.size(32.dp).focusProperties { canFocus = false },
        ) {
          Icon(
            imageVector =
              if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription =
              stringResource(
                if (expanded) Res.string.forms_collapse_question
                else Res.string.forms_expand_question
              ),
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
          when {
            incompatibleType != null ->
              VariantIncompatibleQuestionWarning(
                incompatibleType = incompatibleType,
                variant = variant,
                fieldPath = control.canonicalPath,
              )
            mapDrawing != null ->
              MapDrawingRequestWidget(
                control = control,
                controller = controller,
                host = mapDrawing.host,
                readOnly = readOnly,
              )
            readOnly -> ReadOnlyInputs { ControlWidget(control = control, controller = controller) }
            else -> ControlWidget(control = control, controller = controller)
          }
          val validation = fieldState.validationStatus
          if (validation is ValidationStatus.Invalid && showValidationErrors) {
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
                Text(
                  stringResource(Res.string.forms_skip),
                  style = MaterialTheme.typography.labelMedium,
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

/**
 * Status icon at the left of a [CompactQuestionCard]. Unanswered (`PENDING`) cards render a filled
 * neutral circle displaying the 1-based [questionNumber] (when supplied) so it reads as a step
 * badge instead of an unchecked radio button; answered cards keep the filled checkmark circle.
 */
@Composable
fun CompactStatusIcon(
  status: CompactQuestionStatus,
  size: Dp = 20.dp,
  questionNumber: Int? = null,
) {
  val colors = MaterialTheme.colorScheme
  val description =
    when (status) {
      CompactQuestionStatus.ANSWERED -> stringResource(Res.string.forms_status_answered)
      CompactQuestionStatus.SKIPPED -> stringResource(Res.string.forms_status_skipped)
      CompactQuestionStatus.PENDING -> stringResource(Res.string.forms_status_pending)
      CompactQuestionStatus.INVALID -> stringResource(Res.string.forms_status_needs_attention)
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
    CompactQuestionStatus.PENDING ->
      if (questionNumber != null) {
        QuestionNumberBadge(number = questionNumber, size = size, description = description)
      } else {
        StatusRing(colors.outline, size, description, slashed = false)
      }
  }
}

/** Filled grey circle displaying the centered 1-based question [number] for unanswered cards. */
@Composable
private fun QuestionNumberBadge(number: Int, size: Dp, description: String) {
  val colors = MaterialTheme.colorScheme
  val label = number.toString()
  val fontSize =
    when {
      label.length >= 3 -> 8.5.sp
      label.length == 2 -> 9.5.sp
      else -> 11.sp
    }
  val horizontalPadding = if (label.length >= 3) 4.dp else 0.dp
  Box(
    modifier =
      Modifier.height(size)
        .widthIn(min = size)
        .background(color = colors.surfaceContainerHighest, shape = CircleShape)
        .padding(horizontal = horizontalPadding)
        .testTag("compact_question_number_$number")
        .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontSize = fontSize,
          lineHeight = fontSize,
          fontWeight = FontWeight.Bold,
        ),
      color = colors.onSurfaceVariant,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false,
    )
  }
}

/** Ring for skipped questions (diagonal bar) and fallback when no question number is given. */
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
            pluralStringResource(
              Res.plurals.forms_questions_need_attention,
              submissionResult.errors.size,
              submissionResult.errors.size,
            ),
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
          buildList {
              add(stringResource(Res.string.forms_progress_answered_count, progress.answered))
              if (progress.skipped > 0) {
                add(stringResource(Res.string.forms_progress_skipped_count, progress.skipped))
              }
              if (progress.pending > 0) {
                add(stringResource(Res.string.forms_progress_pending_count, progress.pending))
              }
            }
            .joinToString(" • "),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f),
        maxLines = 2,
      )
      if (onCancel != null) {
        OutlinedButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 14.dp)) {
          Text(stringResource(Res.string.forms_cancel), softWrap = false)
        }
      }
      Button(onClick = onSubmit, contentPadding = PaddingValues(horizontal = 18.dp)) {
        Text(
          stringResource(
            if (isValid) Res.string.forms_submit else Res.string.forms_review_and_submit
          ),
          softWrap = false,
        )
      }
    }
  }
}
