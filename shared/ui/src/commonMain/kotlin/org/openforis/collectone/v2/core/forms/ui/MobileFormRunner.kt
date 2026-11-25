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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.ValidationStatus
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.FilterChip
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedIconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_all_entries
import org.openforis.collectone.v2.core.forms.ui.resources.forms_all_form_steps
import org.openforis.collectone.v2.core.forms.ui.resources.forms_back
import org.openforis.collectone.v2.core.forms.ui.resources.forms_blocker_location
import org.openforis.collectone.v2.core.forms.ui.resources.forms_blocker_optional
import org.openforis.collectone.v2.core.forms.ui.resources.forms_blocker_required
import org.openforis.collectone.v2.core.forms.ui.resources.forms_cannot_submit_errors
import org.openforis.collectone.v2.core.forms.ui.resources.forms_close_form
import org.openforis.collectone.v2.core.forms.ui.resources.forms_close_step_list
import org.openforis.collectone.v2.core.forms.ui.resources.forms_edit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_form_finalized
import org.openforis.collectone.v2.core.forms.ui.resources.forms_invalid_badge
import org.openforis.collectone.v2.core.forms.ui.resources.forms_issue_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_language
import org.openforis.collectone.v2.core.forms.ui.resources.forms_next
import org.openforis.collectone.v2.core.forms.ui.resources.forms_ready_to_submit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_entry_position
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_next_entry
import org.openforis.collectone.v2.core.forms.ui.resources.forms_repeat_previous_entry
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review_and_finalize_summary
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review_answers
import org.openforis.collectone.v2.core.forms.ui.resources.forms_review_answers_hint
import org.openforis.collectone.v2.core.forms.ui.resources.forms_skip
import org.openforis.collectone.v2.core.forms.ui.resources.forms_step_counter
import org.openforis.collectone.v2.core.forms.ui.resources.forms_step_list_item
import org.openforis.collectone.v2.core.forms.ui.resources.forms_step_of_total
import org.openforis.collectone.v2.core.forms.ui.resources.forms_steps_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_submit
import org.openforis.collectone.v2.core.forms.ui.resources.forms_untitled_survey_form
import org.openforis.collectone.v2.core.forms.ui.resources.forms_validation_error_item

/**
 * Reusable Compose Multiplatform mobile form runner displaying a single question screen at a time
 * with a primary-colored banner ([MobileFormTopBar]), progress indicator, repeat group management,
 * language `FilterChip`s, and final submission review.
 *
 * The banner holds the form title and a single close button ([onClose]); under the title it shows
 * [headerContext] (for example, the map feature the form collects data for) or, without one, the
 * step position. The bottom bar moves between steps, and its step counter chip opens a bottom sheet
 * listing every step to jump to.
 */
@Composable
fun MobileFormRunner(
  controller: FormWizardController,
  modifier: Modifier = Modifier,
  onClose: (() -> Unit)? = null,
  onSubmitted: ((FinalizationResult.Success) -> Unit)? = null,
  headerContext: (@Composable () -> Unit)? = null,
) {
  val state = controller.formState
  val currentStep = controller.currentStep
  val stepIndex = controller.currentStepIndex
  val totalSteps = controller.totalSteps.coerceAtLeast(1)
  val progress = (stepIndex + 1).toFloat() / totalSteps.toFloat()
  val formTitle = formDisplayTitle(state.formDef, untitled = Res.string.forms_untitled_survey_form)
  val colors = MaterialTheme.colorScheme
  // Everything drawn on the banner is tinted from `onPrimary` (see [MobileFormTopBar]).
  val onAppBar = colors.onPrimary
  val onAppBarSecondary = colors.onPrimary.copy(alpha = 0.80f)
  val appBarTonalContainer = colors.onPrimary.copy(alpha = 0.16f)

  Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = colors.surface,
      topBar = {
        MobileFormTopBar(
          title = formTitle,
          progress = progress,
          onClose = onClose,
          subtitle =
            headerContext
              ?: {
                Text(
                  text =
                    if (currentStep is FormWizardStep.SummaryStep) {
                      pluralStringResource(
                        Res.plurals.forms_review_and_finalize_summary,
                        controller.totalQuestionSteps,
                        controller.totalQuestionSteps,
                      )
                    } else {
                      stringResource(Res.string.forms_step_of_total, stepIndex + 1, totalSteps)
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
        ) {
          if (state.availableLanguages.size > 1) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = 16.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = LanguageIcon,
                contentDescription = stringResource(Res.string.forms_language),
                tint = onAppBarSecondary,
                modifier = Modifier.size(18.dp),
              )
              state.availableLanguages.forEach { lang ->
                val isActive = lang == state.activeLanguage
                FilterChip(
                  selected = isActive,
                  onClick = { controller.setLanguage(lang) },
                  colors =
                    androidx.compose.material3.FilterChipDefaults.filterChipColors(
                      containerColor = appBarTonalContainer,
                      labelColor = onAppBar,
                      // Selected chips invert: the on-color becomes the fill, so the label has to
                      // switch back to the bar's own color to stay legible.
                      selectedContainerColor = onAppBar,
                      selectedLabelColor = colors.primary,
                    ),
                  elevation =
                    androidx.compose.material3.FilterChipDefaults.filterChipElevation(
                      elevation = 0.dp,
                      pressedElevation = 0.dp,
                      focusedElevation = 0.dp,
                      hoveredElevation = 0.dp,
                      draggedElevation = 0.dp,
                      disabledElevation = 0.dp,
                    ),
                  label = {
                    Text(
                      text = lang,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      softWrap = false,
                    )
                  },
                )
              }
            }
          }
        }
      },
      bottomBar = {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = colors.surfaceContainer,
          contentColor = colors.onSurface,
          tonalElevation = 3.dp,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Icon-only, so the step chip and the Skip / Next buttons fit on narrow screens.
            OutlinedIconButton(
              onClick = { controller.previousStep() },
              enabled = controller.canGoBack,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.forms_back),
                modifier = Modifier.size(18.dp),
              )
            }

            // Step position; opens the list of all steps in a bottom sheet.
            AssistChip(
              onClick = { controller.openOverview() },
              label = {
                Text(
                  text = stringResource(Res.string.forms_step_counter, stepIndex + 1, totalSteps),
                  maxLines = 1,
                  softWrap = false,
                )
              },
              trailingIcon = {
                Icon(
                  imageVector = Icons.Default.KeyboardArrowUp,
                  contentDescription = stringResource(Res.string.forms_steps_count, totalSteps),
                  modifier = Modifier.size(18.dp),
                )
              },
            )

            // The step's actions take the rest of the bar, at its end.
            Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (currentStep is FormWizardStep.SummaryStep) {
                Button(
                  onClick = {
                    val res = controller.finalizeForm()
                    if (res is FinalizationResult.Success) {
                      onSubmitted?.invoke(res)
                    }
                  }
                ) {
                  Text(
                    stringResource(Res.string.forms_submit),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                  )
                }
              } else {
                val isLastQuestionBeforeSummary = stepIndex == totalSteps - 2
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (controller.isCurrentStepOptional) {
                    OutlinedButton(onClick = { controller.nextStep(enforceValidation = false) }) {
                      Text(
                        stringResource(Res.string.forms_skip),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                      )
                    }
                  }

                  Button(
                    onClick = { controller.nextStep(enforceValidation = true) },
                    enabled = !controller.isCurrentStepWaitingForLocationCapture,
                  ) {
                    Text(
                      stringResource(
                        if (isLastQuestionBeforeSummary) Res.string.forms_review
                        else Res.string.forms_next
                      ),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      softWrap = false,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Where the step sits: the repeat entry being filled in (with its own actions), else the
        // enclosing groups.
        val repeatContext = currentStep.repeatContext
        if (repeatContext != null) {
          RepeatEntryBar(
            breadcrumbs = currentStep.breadcrumbs,
            repeatContext = repeatContext,
            controller = controller,
          )
        } else if (currentStep.breadcrumbs.isNotEmpty()) {
          BreadcrumbBar(breadcrumbs = currentStep.breadcrumbs)
        }

        when (currentStep) {
          is FormWizardStep.QuestionStep -> {
            QuestionControlCard(
              control = currentStep.control,
              controller = controller,
              showValidationErrors = controller.showCurrentStepValidationWarning,
            )
          }
          is FormWizardStep.FieldListGroupStep -> {
            Text(
              text = currentStep.title.resolve(),
              style =
                MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = colors.primary,
                ),
              modifier = Modifier.semantics { heading() },
            )
            currentStep.controls.forEach { control ->
              QuestionControlCard(
                control = control,
                controller = controller,
                showValidationErrors = controller.showCurrentStepValidationWarning,
              )
            }
          }
          is FormWizardStep.RepeatHubStep -> {
            RepeatHub(
              step = currentStep,
              onOpenEntry = controller::openRepeatInstance,
              onRemoveEntry = { index ->
                controller.removeRepeatInstance(currentStep.repeatGroup.canonicalPath, index)
              },
              onAddEntry = {
                controller.addRepeatInstanceAndOpen(currentStep.repeatGroup.canonicalPath)
              },
            )
          }
          is FormWizardStep.SummaryStep -> {
            FormSummaryScreenContent(controller = controller)
          }
        }

        // Validation blocker notice when user pressed Next on an invalid non-single-question step
        // or while waiting for location capture (single QuestionStep already renders its error
        // card)
        if (
          controller.showCurrentStepValidationWarning &&
            (controller.isCurrentStepWaitingForLocationCapture ||
              (controller.currentStepErrors.isNotEmpty() &&
                currentStep !is FormWizardStep.QuestionStep)) &&
            currentStep !is FormWizardStep.SummaryStep
        ) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
              CardDefaults.cardColors(
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
              ),
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  stringResource(
                    stepValidationBlockerMessage(
                      isWaitingForLocationCapture =
                        controller.isCurrentStepWaitingForLocationCapture,
                      isStepOptional = controller.isCurrentStepOptional,
                    )
                  ),
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    color = colors.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                  ),
                modifier = Modifier.weight(1f),
              )
              if (controller.isCurrentStepOptional) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                  onClick = { controller.nextStep(enforceValidation = false) },
                  colors =
                    ButtonDefaults.outlinedButtonColors(contentColor = colors.onErrorContainer),
                  border = BorderStroke(1.dp, colors.onErrorContainer.copy(alpha = 0.5f)),
                ) {
                  Text(
                    stringResource(Res.string.forms_skip),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
    if (controller.isOverviewOpen) {
      ModalBottomSheetOverlay(onDismissRequest = { controller.closeOverview() }) {
        StepListSheetContent(controller = controller)
      }
    }
  }
}

/**
 * The primary-colored banner at the top of the mobile form: the form [title] (at most two lines, so
 * a long title can't push the rest of the screen around), a [subtitle] line, a single close button
 * ([onClose], hidden when `null`), any [extraContent] (the language selector), and the [progress]
 * through the form.
 *
 * [subtitle] and [extraContent] are drawn on `primary`: their default text style is `labelSmall` in
 * a de-emphasized `onPrimary`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileFormTopBar(
  title: String,
  progress: Float,
  modifier: Modifier = Modifier,
  onClose: (() -> Unit)? = null,
  subtitle: @Composable () -> Unit = {},
  extraContent: @Composable () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  // The bar is a saturated `primary` surface, so everything drawn on it is tinted from `onPrimary`
  // rather than the default surface roles. Deriving the de-emphasized variants by alpha (instead of
  // naming more palette entries) keeps the title / subtitle hierarchy intact under both the light
  // and dark schemes.
  val onAppBar = colors.onPrimary
  val onAppBarSecondary = colors.onPrimary.copy(alpha = 0.80f)
  val appBarTrack = colors.onPrimary.copy(alpha = 0.24f)
  Surface(
    modifier = modifier,
    color = colors.primary,
    contentColor = onAppBar,
    tonalElevation = 2.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      TopAppBar(
        title = {
          FormHeaderTitle(title = title, titleColor = onAppBar) {
            CompositionLocalProvider(LocalContentColor provides onAppBarSecondary) {
              ProvideTextStyle(MaterialTheme.typography.labelSmall, subtitle)
            }
          }
        },
        actions = {
          if (onClose != null) {
            IconButton(
              onClick = onClose,
              colors = IconButtonDefaults.iconButtonColors(contentColor = onAppBar),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.forms_close_form),
              )
            }
          }
        },
        // Fixed, and tall enough for a two-line title over the subtitle.
        expandedHeight = MobileFormTopBarHeight,
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = colors.primary,
            titleContentColor = onAppBar,
            actionIconContentColor = onAppBar,
          ),
      )

      CompositionLocalProvider(LocalContentColor provides onAppBarSecondary) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall, extraContent)
      }

      LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(5.dp),
        color = onAppBar,
        trackColor = appBarTrack,
      )
    }
  }
}

private val MobileFormTopBarHeight = 72.dp

/** The groups enclosing a step outside any repeat entry. */
@Composable
private fun BreadcrumbBar(breadcrumbs: List<String>) {
  val colors = MaterialTheme.colorScheme
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
      ),
  ) {
    Text(
      text = breadcrumbs.joinToString("  ›  "),
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          color = colors.onSecondaryContainer,
        ),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    )
  }
}

/**
 * Header of a step inside a repeat entry: a top row with the groups above the entry, the entry's
 * full-width title, and Delete; and a bottom row with previous/next entry arrows (when more than
 * one entry exists), its position ("Entry #2 of 3", plus any group within the entry), and **All
 * entries** back to the repeat's list. Delete asks for confirmation when the entry holds answers
 * and then returns to the list, since the entry's screens are gone. Repeats whose entry count is
 * fixed by the form have no list or Delete, but still keep previous/next entry navigation.
 */
@Composable
private fun RepeatEntryBar(
  breadcrumbs: List<String>,
  repeatContext: RepeatStepContext,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val entryCrumb = breadcrumbs.lastIndexOf(repeatContext.instanceLabel)
  val above = if (entryCrumb >= 0) breadcrumbs.take(entryCrumb) else emptyList()
  val within = breadcrumbsWithinEntry(breadcrumbs, repeatContext)
  val hasList = repeatContext.canAddInstance || repeatContext.canRemoveInstance
  val prevPath = controller.previousRepeatInstancePath(repeatContext)
  val nextPath = controller.nextRepeatInstancePath(repeatContext)
  val showEntryStepper = repeatContext.totalInstances > 1 || prevPath != null || nextPath != null
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
      ),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (above.isNotEmpty()) {
            Text(
              text = above.joinToString(" › "),
              style = MaterialTheme.typography.labelSmall,
              color = colors.onSecondaryContainer.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = repeatContext.instanceLabel,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (repeatContext.canRemoveInstance) {
          RepeatEntryDeleteButton(
            entryTitle = repeatContext.instanceLabel,
            // Without a summary, err on the side of asking.
            answeredCount = controller.repeatEntrySummary(repeatContext)?.answeredCount ?: 1,
            onDelete = {
              controller.removeRepeatInstance(
                repeatContext.repeatGroupPath,
                repeatContext.repeatIndex,
              )
              controller.openRepeatHub(repeatContext.repeatGroupPath)
            },
            buttonSize = 36.dp,
            iconSize = 18.dp,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (showEntryStepper) {
            IconButton(
              onClick = {
                prevPath?.let {
                  controller.openSiblingRepeatInstance(repeatContext.instancePath, it)
                }
              },
              enabled = prevPath != null,
              modifier = Modifier.size(28.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.forms_repeat_previous_entry),
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Text(
            text =
              (listOf(
                  stringResource(
                    Res.string.forms_repeat_entry_position,
                    repeatContext.repeatIndex,
                    repeatContext.totalInstances,
                  )
                ) + within)
                .joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSecondaryContainer.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (showEntryStepper) {
            IconButton(
              onClick = {
                nextPath?.let {
                  controller.openSiblingRepeatInstance(repeatContext.instancePath, it)
                }
              },
              enabled = nextPath != null,
              modifier = Modifier.size(28.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.forms_repeat_next_entry),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
        if (hasList) {
          TextButton(
            onClick = { controller.openRepeatHub(repeatContext.repeatGroupPath) },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSecondaryContainer),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.List,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(Res.string.forms_all_entries),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FormSummaryScreenContent(controller: FormWizardController) {
  val colors = MaterialTheme.colorScheme
  val state = controller.formState
  val questionSteps = controller.steps.filterIsInstance<FormWizardStep.QuestionStep>()
  val subResult = controller.submissionResult

  // The review sits directly on the page background, without a container of its own.
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(Res.string.forms_review_answers),
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = colors.onSurface,
            ),
        )
        Text(
          text = stringResource(Res.string.forms_review_answers_hint),
          style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
        )
      }
      TonalBadge(
        text =
          if (state.isValid) {
            stringResource(Res.string.forms_ready_to_submit)
          } else {
            pluralStringResource(
              Res.plurals.forms_issue_count,
              state.validationErrors.size,
              state.validationErrors.size,
            )
          },
        tone = if (state.isValid) BadgeTone.PRIMARY else BadgeTone.ERROR,
      )
    }

    // Submission Result Feedback Banner
    if (subResult != null) {
      when (subResult) {
        is FinalizationResult.Success -> {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors =
              CardDefaults.cardColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
              ),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = stringResource(Res.string.forms_form_finalized),
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                  ),
              )
            }
          }
        }
        is FinalizationResult.ValidationFailure -> {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors =
              CardDefaults.cardColors(
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
              ),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text =
                  pluralStringResource(
                    Res.plurals.forms_cannot_submit_errors,
                    subResult.errors.size,
                    subResult.errors.size,
                  ),
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.onErrorContainer,
                  ),
              )
              subResult.errors.forEach { err ->
                Text(
                  text =
                    stringResource(
                      Res.string.forms_validation_error_item,
                      controller.questionTitleFor(err.fieldPath).resolve(),
                      err.message,
                    ),
                  style = MaterialTheme.typography.labelSmall.copy(color = colors.onErrorContainer),
                  modifier = Modifier.clickable { controller.jumpToField(err.fieldPath) },
                )
              }
            }
          }
        }
      }
    }

    // All Relevant Questions Summary List
    questionSteps.forEach { qStep ->
      val control = qStep.control
      val fieldState = control.fieldState
      val isInvalid = fieldState.validationStatus is ValidationStatus.Invalid
      OutlinedCard(
        onClick = { controller.jumpToField(control.canonicalPath) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = if (isInvalid) colors.errorContainer else colors.surfaceContainerLow,
            contentColor = if (isInvalid) colors.onErrorContainer else colors.onSurface,
          ),
        border =
          BorderStroke(
            width = 1.dp,
            color = if (isInvalid) colors.error else colors.outlineVariant,
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            if (qStep.breadcrumbs.isNotEmpty()) {
              Text(
                text = qStep.breadcrumbs.joinToString(" › "),
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color =
                      if (isInvalid) colors.onErrorContainer.copy(alpha = 0.85f)
                      else colors.onSurfaceVariant
                  ),
              )
            }
            Text(
              text = qStep.questionLabel,
              style =
                MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = if (isInvalid) colors.onErrorContainer else colors.onSurface,
                ),
            )
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
              if (!isInvalid && !fieldState.isEmpty) {
                ChoiceImageRegistry.resolveSelectedOptionColors(control.options, selectedValues)
              } else {
                emptyList()
              }
            if (coloredSelections.isNotEmpty()) {
              ChoiceColorPillsRow(
                items = coloredSelections,
                compact = true,
                modifier = Modifier.padding(top = 4.dp),
              )
            } else {
              Text(
                text = fieldValueDisplayText(fieldState.value, fieldState.dataType),
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    color =
                      when {
                        isInvalid -> colors.onErrorContainer.copy(alpha = 0.85f)
                        fieldState.isEmpty -> colors.onSurfaceVariant
                        else -> colors.primary
                      }
                  ),
              )
            }
          }
          if (isInvalid) {
            TonalBadge(
              text = stringResource(Res.string.forms_invalid_badge),
              tone = BadgeTone.ERROR,
            )
          }
          IconButton(onClick = { controller.jumpToField(control.canonicalPath) }) {
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = stringResource(Res.string.forms_edit),
              tint = if (isInvalid) colors.onErrorContainer else colors.primary,
            )
          }
        }
      }
    }
  }
}

/**
 * The list of all form steps shown in the bottom sheet the step counter chip opens: a heading with
 * a close button, and one row per step that jumps to it (closing the sheet) when tapped.
 */
@Composable
private fun StepListSheetContent(controller: FormWizardController) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(Res.string.forms_all_form_steps, controller.totalSteps),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      IconButton(onClick = { controller.closeOverview() }) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(Res.string.forms_close_step_list),
        )
      }
    }
    Column(
      modifier =
        Modifier.weight(1f, fill = false)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      controller.steps.forEachIndexed { idx, step ->
        val isCurrent = idx == controller.currentStepIndex
        OutlinedCard(
          onClick = { controller.jumpToStep(idx) },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor =
                if (isCurrent) colors.primaryContainer else colors.surfaceContainerLowest,
              contentColor = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
            ),
          border =
            BorderStroke(
              width = if (isCurrent) 2.dp else 1.dp,
              color = if (isCurrent) colors.primary else colors.outlineVariant,
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              if (step.breadcrumbs.isNotEmpty()) {
                Text(
                  text = step.breadcrumbs.joinToString(" › "),
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      color = if (isCurrent) colors.onPrimaryContainer else colors.onSurfaceVariant
                    ),
                )
              }
              Text(
                text =
                  stringResource(Res.string.forms_step_list_item, idx + 1, step.title.resolve()),
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
                  ),
              )
            }
            if (step is FormWizardStep.QuestionStep) {
              val fs = step.control.fieldState
              val isInvalid = fs.validationStatus is ValidationStatus.Invalid
              TonalBadge(
                text =
                  when {
                    isInvalid -> "!"
                    !fs.isEmpty -> "✓"
                    fs.isRequired -> "*"
                    else -> "•"
                  },
                tone =
                  when {
                    isInvalid -> BadgeTone.ERROR
                    !fs.isEmpty -> BadgeTone.PRIMARY
                    else -> BadgeTone.NEUTRAL
                  },
              )
            }
          }
        }
      }
    }
  }
}

private val LanguageIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "App.Language",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .addPath(
      pathData =
        PathParser()
          .parsePathString(
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
              "M18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56z" +
              "M12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96z" +
              "M4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2" +
              " 0,0.68 0.06,1.34 0.14,2H4.26zM5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56" +
              " -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zM8.03,8H5.08c0.96,-1.66 2.49,-2.93 4.33,-3.56" +
              "C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82" +
              "c-0.43,1.43 -1.08,2.76 -1.91,3.96zM14.34,14H9.66c-0.09,-0.66 -0.16,-1.32 -0.16,-2" +
              " 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2z" +
              "M14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56z" +
              "M16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38" +
              "c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z"
          )
          .toNodes(),
      fill = SolidColor(Color.Black),
    )
    .build()
}

/**
 * Message displayed in the validation blocker banner when the collector taps Next on an incomplete
 * or invalid step. Only optional steps mention skipping.
 */
internal fun stepValidationBlockerMessage(
  isWaitingForLocationCapture: Boolean,
  isStepOptional: Boolean,
): StringResource =
  when {
    isWaitingForLocationCapture -> Res.string.forms_blocker_location
    isStepOptional -> Res.string.forms_blocker_optional
    else -> Res.string.forms_blocker_required
  }
