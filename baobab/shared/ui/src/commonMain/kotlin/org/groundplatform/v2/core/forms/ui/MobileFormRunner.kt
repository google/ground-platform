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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.ValidationStatus

/**
 * Reusable Compose Multiplatform mobile form runner displaying a single question screen at a time
 * with Material 3 `TopAppBar`, progress indicator, repeat group management, language `FilterChip`s,
 * and final submission review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileFormRunner(
  controller: FormWizardController,
  modifier: Modifier = Modifier,
  onClose: (() -> Unit)? = null,
  onSubmitted: ((FinalizationResult.Success) -> Unit)? = null,
) {
  val state = controller.formState
  val currentStep = controller.currentStep
  val stepIndex = controller.currentStepIndex
  val totalSteps = controller.totalSteps.coerceAtLeast(1)
  val progress = (stepIndex + 1).toFloat() / totalSteps.toFloat()
  val formTitle =
    state.formDef.title.takeIf { it.isNotBlank() }
      ?: state.formDef.form_id.takeIf { it.isNotBlank() }
      ?: "Survey Form"
  val colors = MaterialTheme.colorScheme
  // The top bar is a saturated `primary` surface, so everything drawn on it is tinted from
  // `onPrimary` rather than the default surface roles. Deriving the de-emphasized variants by
  // alpha (instead of naming more palette entries) keeps the title / subtitle / tonal-button
  // hierarchy intact under both the light and dark schemes.
  val onAppBar = colors.onPrimary
  val onAppBarSecondary = colors.onPrimary.copy(alpha = 0.80f)
  val appBarTonalContainer = colors.onPrimary.copy(alpha = 0.16f)
  val appBarTrack = colors.onPrimary.copy(alpha = 0.24f)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = colors.surfaceContainerLow,
    topBar = {
      Surface(color = colors.primary, contentColor = onAppBar, tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
          TopAppBar(
            title = {
              Column {
                Text(
                  text = formTitle,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = onAppBar,
                )
                Text(
                  text =
                    if (currentStep is FormWizardStep.SummaryStep) {
                      "Review & Finalize (${controller.totalQuestionSteps} questions)"
                    } else {
                      "Step ${stepIndex + 1} of $totalSteps"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = onAppBarSecondary,
                )
              }
            },
            actions = {
              FilledTonalButton(
                onClick = { controller.isOverviewOpen = !controller.isOverviewOpen },
                colors =
                  ButtonDefaults.filledTonalButtonColors(
                    containerColor = appBarTonalContainer,
                    contentColor = onAppBar,
                  ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              ) {
                Text(
                  text = if (controller.isOverviewOpen) "Close List" else "☰ Steps ($totalSteps)",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
              }
              if (onClose != null) {
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                  onClick = onClose,
                  colors = ButtonDefaults.textButtonColors(contentColor = onAppBar),
                ) {
                  Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
            },
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = colors.primary,
                titleContentColor = onAppBar,
                actionIconContentColor = onAppBar,
              ),
          )

          // Multi-lingual Language Selector (if form defines multiple translations)
          if (state.availableLanguages.size > 1) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = 16.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "🌐",
                style = MaterialTheme.typography.labelMedium,
                color = onAppBarSecondary,
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
                  label = { Text(text = lang, style = MaterialTheme.typography.labelSmall) },
                )
              }
            }
          }

          LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = onAppBar,
            trackColor = appBarTrack,
          )
        }
      }
    },
    bottomBar = {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainer,
        tonalElevation = 3.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { controller.previousStep() },
            enabled = controller.canGoBack,
            modifier = Modifier.width(112.dp),
          ) {
            Text("← Back")
          }

          Text(
            text = "${stepIndex + 1} / $totalSteps",
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
              ),
          )

          if (currentStep is FormWizardStep.SummaryStep) {
            Button(
              onClick = {
                val res = controller.finalizeForm()
                if (res is FinalizationResult.Success) {
                  onSubmitted?.invoke(res)
                }
              },
              modifier = Modifier.width(132.dp),
            ) {
              Text("Submit ✓")
            }
          } else {
            val isLastQuestionBeforeSummary = stepIndex == totalSteps - 2
            Button(
              onClick = { controller.nextStep(enforceValidation = true) },
              modifier = Modifier.width(132.dp),
            ) {
              Text(if (isLastQuestionBeforeSummary) "Review →" else "Next →")
            }
          }
        }
      }
    },
  ) { innerPadding ->
    if (controller.isOverviewOpen) {
      StepOverviewPanel(
        controller = controller,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    } else {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Enclosing Group / Repeat Breadcrumb Banner
        if (currentStep.breadcrumbs.isNotEmpty()) {
          BreadcrumbBar(
            breadcrumbs = currentStep.breadcrumbs,
            repeatContext = currentStep.repeatContext,
            controller = controller,
          )
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
              text = currentStep.title,
              style =
                MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = colors.primary,
                ),
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
            RepeatHubScreenContent(repeatGroup = currentStep.repeatGroup, controller = controller)
          }
          is FormWizardStep.SummaryStep -> {
            FormSummaryScreenContent(controller = controller)
          }
        }

        // Validation blocker notice when user pressed Next on an invalid question
        if (
          controller.showCurrentStepValidationWarning &&
            controller.currentStepErrors.isNotEmpty() &&
            currentStep !is FormWizardStep.SummaryStep
        ) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Please resolve validation errors above or skip to continue.",
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    color = colors.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                  ),
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(onClick = { controller.nextStep(enforceValidation = false) }) {
                Text("Skip →", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BreadcrumbBar(
  breadcrumbs: List<String>,
  repeatContext: RepeatStepContext?,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = breadcrumbs.joinToString("  ›  "),
        style =
          MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = colors.onSecondaryContainer,
          ),
      )
      if (repeatContext != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Repeat #${repeatContext.repeatIndex} of ${repeatContext.totalInstances}",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.onSecondaryContainer),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repeatContext.canAddInstance) {
              FilledTonalButton(
                onClick = { controller.addRepeatInstanceAndOpen(repeatContext.repeatGroupPath) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              ) {
                Text(
                  text = "+ Add ${repeatContext.repeatGroupLabel}",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
            if (repeatContext.canRemoveInstance) {
              OutlinedButton(
                onClick = {
                  controller.removeRepeatInstance(
                    repeatContext.repeatGroupPath,
                    repeatContext.repeatIndex,
                  )
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              ) {
                Text(
                  text = "Delete #${repeatContext.repeatIndex}",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RepeatHubScreenContent(
  repeatGroup: ComponentState.RepeatGroupState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val groupLabel =
    repeatGroup.label?.text?.takeIf { it.isNotBlank() }
      ?: repeatGroup.canonicalPath.substringAfterLast('/')

  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLowest),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Repeat Group: $groupLabel",
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
              ),
          )
          Text(
            text = "${repeatGroup.instances.size} instance(s) recorded",
            style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
          )
        }
        if (repeatGroup.targetCount != null) {
          GroundTonalBadge(
            text = "jr:count = ${repeatGroup.targetCount}",
            tone = GroundBadgeTone.TERTIARY,
          )
        }
      }

      if (repeatGroup.instances.isEmpty()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          color = colors.surfaceContainer,
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "No repeat entries added yet.",
              style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant),
            )
          }
        }
      } else {
        repeatGroup.instances.forEach { instance ->
          val instTitle =
            instance.label?.text?.takeIf { it.isNotBlank() }
              ?: "$groupLabel #${instance.repeatIndex}"
          OutlinedCard(
            onClick = {
              val stepIdx =
                controller.steps.indexOfFirst {
                  it.repeatContext?.instancePath == instance.canonicalPath
                }
              if (stepIdx >= 0) controller.jumpToStep(stepIdx)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = instTitle,
                  style =
                    MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.SemiBold,
                      color = colors.onSurface,
                    ),
                )
                Text(
                  text = instance.canonicalPath,
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      color = colors.onSurfaceVariant,
                    ),
                )
              }
              if (repeatGroup.canRemoveInstance) {
                TextButton(
                  onClick = {
                    controller.removeRepeatInstance(repeatGroup.canonicalPath, instance.repeatIndex)
                  },
                  colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                  Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }

      if (repeatGroup.canAddInstance) {
        Button(
          onClick = { controller.addRepeatInstanceAndOpen(repeatGroup.canonicalPath) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("+ Add Another $groupLabel")
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

  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLowest),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Review Answers",
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
              ),
          )
          Text(
            text = "Tap any question below to jump back and edit its response.",
            style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
          )
        }
        GroundTonalBadge(
          text =
            if (state.isValid) {
              "✓ Ready to Submit"
            } else {
              "${state.validationErrors.size} Issue(s)"
            },
          tone = if (state.isValid) GroundBadgeTone.PRIMARY else GroundBadgeTone.ERROR,
        )
      }

      // Submission Result Feedback Banner
      if (subResult != null) {
        when (subResult) {
          is FinalizationResult.Success -> {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "✓ Form Finalized Successfully!",
                  style =
                    MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Bold,
                      color = colors.onPrimaryContainer,
                    ),
                )
                Text(
                  text =
                    "Instance ID: ${subResult.recordInstance.metadata?.instance_id ?: "(generated)"}",
                  style =
                    MaterialTheme.typography.labelSmall.copy(
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
              colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "⚠ Cannot submit: ${subResult.errors.size} validation error(s):",
                  style =
                    MaterialTheme.typography.bodySmall.copy(
                      fontWeight = FontWeight.Bold,
                      color = colors.onErrorContainer,
                    ),
                )
                subResult.errors.forEach { err ->
                  Text(
                    text = "• ${err.fieldPath}: ${err.message}",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        color = colors.onErrorContainer,
                      ),
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
              containerColor =
                if (isInvalid) colors.errorContainer.copy(alpha = 0.4f)
                else colors.surfaceContainerLow
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
                  style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
                )
              }
              Text(
                text = qStep.title,
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                  ),
              )
              Text(
                text = formatFieldValueForDisplay(fieldState.value, fieldState.dataType),
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    color = if (fieldState.isEmpty) colors.onSurfaceVariant else colors.primary,
                  ),
              )
            }
            GroundTonalBadge(
              text = if (isInvalid) "Invalid" else "Edit",
              tone = if (isInvalid) GroundBadgeTone.ERROR else GroundBadgeTone.PRIMARY,
            )
          }
        }
      }

      // Entities Preview (if declared)
      if (state.entityStates.isNotEmpty()) {
        HorizontalDivider(color = colors.outlineVariant)
        Text(
          text = "Evaluated Entities (${state.entityStates.size})",
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              color = colors.onSurface,
            ),
        )
        state.entityStates.forEach { entity ->
          OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.outlinedCardColors(containerColor = colors.secondaryContainer),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text =
                  "Dataset: ${entity.dataset} (${if (entity.shouldCreate) "CREATE" else if (entity.shouldUpdate) "UPDATE" else "INACTIVE"})",
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.onSecondaryContainer,
                  ),
              )
              Text(
                text = "ID: ${entity.entityId} | Label: ${entity.label}",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color = colors.onSecondaryContainer,
                  ),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StepOverviewPanel(controller: FormWizardController, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "All Form Steps (${controller.totalSteps})",
      style =
        MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          color = colors.onSurface,
        ),
    )
    controller.steps.forEachIndexed { idx, step ->
      val isCurrent = idx == controller.currentStepIndex
      OutlinedCard(
        onClick = { controller.jumpToStep(idx) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor =
              if (isCurrent) colors.primaryContainer else colors.surfaceContainerLowest
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
              text = "${idx + 1}. ${step.title}",
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
            GroundTonalBadge(
              text =
                when {
                  isInvalid -> "!"
                  !fs.isEmpty -> "✓"
                  fs.isRequired -> "*"
                  else -> "•"
                },
              tone =
                when {
                  isInvalid -> GroundBadgeTone.ERROR
                  !fs.isEmpty -> GroundBadgeTone.PRIMARY
                  else -> GroundBadgeTone.NEUTRAL
                },
            )
          }
        }
      }
    }
  }
}

/**
 * Wraps a [MobileFormRunner] in a realistic mobile phone bezel for embedding inside web or desktop
 * debugging tools.
 */
@Composable
fun MobilePhoneFrame(
  modifier: Modifier = Modifier,
  deviceLabel: String = "Ground Mobile Form Runner (390×720)",
  content: @Composable () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = deviceLabel,
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          color = colors.onSurfaceVariant,
        ),
    )

    // Outer Phone Bezel
    Box(
      modifier =
        Modifier.width(400.dp)
          .height(720.dp)
          .clip(RoundedCornerShape(36.dp))
          .background(colors.inverseSurface)
          .padding(10.dp)
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge).background(colors.surface)
      ) {
        // Simulated Mobile Status Bar
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(colors.surfaceContainerHigh)
              .padding(horizontal = 18.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "09:41",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
              ),
          )
          // Camera notch pill
          Box(
            modifier =
              Modifier.width(64.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.inverseSurface)
          )
          Text(
            text = "5G • 100%",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
              ),
          )
        }

        // Screen Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }

        // Bottom Gesture Home Bar
        Box(
          modifier =
            Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.width(110.dp).height(4.dp).clip(CircleShape).background(colors.outline)
          )
        }
      }
    }
  }
}
