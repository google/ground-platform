/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.ValidationStatus

/**
 * Reusable Compose Multiplatform mobile form runner displaying a single question screen at a time
 * with top progress navigation, repeat group management, language switching, and final submission
 * review.
 */
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

  Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF3F6F4)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 1. Mobile Top App Bar
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFF1B5E20))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = formTitle,
              style =
                MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                ),
            )
            Text(
              text =
                if (currentStep is FormWizardStep.SummaryStep) {
                  "Review & Finalize (${controller.totalQuestionSteps} questions)"
                } else {
                  "Step ${stepIndex + 1} of $totalSteps"
                },
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC8E6C9)),
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Overview toggle button
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFF2E7D32))
                  .clickable { controller.isOverviewOpen = !controller.isOverviewOpen }
                  .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = if (controller.isOverviewOpen) "Close List" else "☰ Steps ($totalSteps)",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                  ),
              )
            }

            if (onClose != null) {
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF144718))
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = "✕",
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    ),
                )
              }
            }
          }
        }

        // Multi-lingual Language Selector (if form defines multiple translations)
        if (state.availableLanguages.size > 1) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = "🌐", style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
            state.availableLanguages.forEach { lang ->
              val isActive = lang == state.activeLanguage
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) Color.White else Color(0xFF2E7D32))
                    .clickable { controller.setLanguage(lang) }
                    .padding(horizontal = 10.dp, vertical = 3.dp)
              ) {
                Text(
                  text = lang,
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                      color = if (isActive) Color(0xFF1B5E20) else Color.White,
                    ),
                )
              }
            }
          }
        }

        LinearProgressIndicator(
          progress = { progress.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
          color = Color(0xFF81C784),
          trackColor = Color(0xFF0D3B10),
        )
      }

      // 2. Optional Question Jump Overview Drawer
      if (controller.isOverviewOpen) {
        StepOverviewPanel(controller = controller, modifier = Modifier.weight(1f))
      } else {
        // 3. Main Single-Question / Step Viewport
        Column(
          modifier =
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
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
                    color = Color(0xFF1B5E20),
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
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F6)),
              shape = RoundedCornerShape(12.dp),
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
                      color = Color(0xFFB91C1C),
                      fontWeight = FontWeight.Medium,
                    ),
                  modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                  onClick = { controller.nextStep(enforceValidation = false) },
                  modifier = Modifier.height(32.dp),
                ) {
                  Text("Skip →", fontSize = 11.sp)
                }
              }
            }
          }
        }
      }

      // 4. Bottom Navigation Action Bar
      Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 8.dp) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { controller.previousStep() },
            enabled = controller.canGoBack,
            modifier = Modifier.width(110.dp),
          ) {
            Text("← Back")
          }

          Text(
            text = "${stepIndex + 1} / $totalSteps",
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4B5563),
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
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
              modifier = Modifier.width(130.dp),
            ) {
              Text("Submit ✓")
            }
          } else {
            val isLastQuestionBeforeSummary = stepIndex == totalSteps - 2
            Button(
              onClick = { controller.nextStep(enforceValidation = true) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
              modifier = Modifier.width(130.dp),
            ) {
              Text(if (isLastQuestionBeforeSummary) "Review →" else "Next →")
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
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = breadcrumbs.joinToString("  ›  "),
        style =
          MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1B5E20),
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
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2E7D32)),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (repeatContext.canAddInstance) {
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1B5E20))
                    .clickable {
                      controller.addRepeatInstanceAndOpen(repeatContext.repeatGroupPath)
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(
                  text = "+ Add ${repeatContext.repeatGroupLabel}",
                  style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                )
              }
            }
            if (repeatContext.canRemoveInstance) {
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFEE2E2))
                    .clickable {
                      controller.removeRepeatInstance(
                        repeatContext.repeatGroupPath,
                        repeatContext.repeatIndex,
                      )
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(
                  text = "Delete #${repeatContext.repeatIndex}",
                  style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFB91C1C)),
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
  val groupLabel =
    repeatGroup.label?.text?.takeIf { it.isNotBlank() }
      ?: repeatGroup.canonicalPath.substringAfterLast('/')

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
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
                color = Color(0xFF111827),
              ),
          )
          Text(
            text = "${repeatGroup.instances.size} instance(s) recorded",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
          )
        }
        if (repeatGroup.targetCount != null) {
          BadgePill(
            text = "jr:count = ${repeatGroup.targetCount}",
            bgColor = Color(0xFFE0F2FE),
            textColor = Color(0xFF0369A1),
          )
        }
      }

      if (repeatGroup.instances.isEmpty()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp))
              .padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No repeat entries added yet.",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6B7280)),
          )
        }
      } else {
        repeatGroup.instances.forEach { instance ->
          val instTitle =
            instance.label?.text?.takeIf { it.isNotBlank() }
              ?: "$groupLabel #${instance.repeatIndex}"
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                .clickable {
                  val stepIdx =
                    controller.steps.indexOfFirst {
                      it.repeatContext?.instancePath == instance.canonicalPath
                    }
                  if (stepIdx >= 0) controller.jumpToStep(stepIdx)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = instTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text = instance.canonicalPath,
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF6B7280),
                  ),
              )
            }
            if (repeatGroup.canRemoveInstance) {
              OutlinedButton(
                onClick = {
                  controller.removeRepeatInstance(repeatGroup.canonicalPath, instance.repeatIndex)
                },
                modifier = Modifier.height(32.dp),
              ) {
                Text("Remove", fontSize = 11.sp, color = Color(0xFFB91C1C))
              }
            }
          }
        }
      }

      if (repeatGroup.canAddInstance) {
        Button(
          onClick = { controller.addRepeatInstanceAndOpen(repeatGroup.canonicalPath) },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
        ) {
          Text("+ Add Another $groupLabel")
        }
      }
    }
  }
}

@Composable
private fun FormSummaryScreenContent(controller: FormWizardController) {
  val state = controller.formState
  val questionSteps = controller.steps.filterIsInstance<FormWizardStep.QuestionStep>()
  val subResult = controller.submissionResult

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Review Answers",
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
              ),
          )
          Text(
            text = "Tap any question below to jump back and edit its response.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
          )
        }
        BadgePill(
          text =
            if (state.isValid) {
              "✓ Ready to Submit"
            } else {
              "${state.validationErrors.size} Issue(s)"
            },
          bgColor = if (state.isValid) Color(0xFFE8F5E9) else Color(0xFFFEE2E2),
          textColor = if (state.isValid) Color(0xFF1B5E20) else Color(0xFFB91C1C),
        )
      }

      // Submission Result Feedback Banner
      if (subResult != null) {
        when (subResult) {
          is FinalizationResult.Success -> {
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                  .border(1.dp, Color(0xFF81C784), RoundedCornerShape(10.dp))
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "✓ Form Finalized Successfully!",
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20),
                  ),
              )
              Text(
                text =
                  "Instance ID: ${subResult.recordInstance.metadata?.instance_id ?: "(generated)"}",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF2E7D32),
                  ),
              )
            }
          }
          is FinalizationResult.ValidationFailure -> {
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .background(Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
                  .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
                  .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "⚠ Cannot submit: ${subResult.errors.size} validation error(s):",
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB91C1C),
                  ),
              )
              subResult.errors.forEach { err ->
                Text(
                  text = "• ${err.fieldPath}: ${err.message}",
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      color = Color(0xFFB91C1C),
                      fontFamily = FontFamily.Monospace,
                    ),
                  modifier = Modifier.clickable { controller.jumpToField(err.fieldPath) },
                )
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
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(if (isInvalid) Color(0xFFFEF2F2) else Color(0xFFF9FAFB))
              .border(
                width = 1.dp,
                color = if (isInvalid) Color(0xFFFECACA) else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(10.dp),
              )
              .clickable { controller.jumpToField(control.canonicalPath) }
              .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            if (qStep.breadcrumbs.isNotEmpty()) {
              Text(
                text = qStep.breadcrumbs.joinToString(" › "),
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
              )
            }
            Text(
              text = qStep.title,
              style =
                MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF111827),
                ),
            )
            Text(
              text = formatFieldValueForDisplay(fieldState.value, fieldState.dataType),
              style =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  color = if (fieldState.isEmpty) Color(0xFF9CA3AF) else Color(0xFF1B5E20),
                ),
            )
          }
          BadgePill(
            text = if (isInvalid) "Invalid" else "Edit",
            bgColor = if (isInvalid) Color(0xFFFEE2E2) else Color(0xFFE8F5E9),
            textColor = if (isInvalid) Color(0xFFB91C1C) else Color(0xFF1B5E20),
          )
        }
      }

      // ODK Entities Preview (if declared)
      if (state.entityStates.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Evaluated ODK Entities (${state.entityStates.size})",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        state.entityStates.forEach { entity ->
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text =
                "Dataset: ${entity.dataset} (${if (entity.shouldCreate) "CREATE" else if (entity.shouldUpdate) "UPDATE" else "INACTIVE"})",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = "ID: ${entity.entityId} | Label: ${entity.label}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StepOverviewPanel(controller: FormWizardController, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "All Form Steps (${controller.totalSteps})",
      style =
        MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          color = Color(0xFF111827),
        ),
    )
    controller.steps.forEachIndexed { idx, step ->
      val isCurrent = idx == controller.currentStepIndex
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) Color(0xFFE8F5E9) else Color.White)
            .border(
              width = if (isCurrent) 2.dp else 1.dp,
              color = if (isCurrent) Color(0xFF1B5E20) else Color(0xFFE5E7EB),
              shape = RoundedCornerShape(10.dp),
            )
            .clickable { controller.jumpToStep(idx) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (step.breadcrumbs.isNotEmpty()) {
            Text(
              text = step.breadcrumbs.joinToString(" › "),
              style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
            )
          }
          Text(
            text = "${idx + 1}. ${step.title}",
            style =
              MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) Color(0xFF1B5E20) else Color(0xFF111827),
              ),
          )
        }
        if (step is FormWizardStep.QuestionStep) {
          val fs = step.control.fieldState
          val isInvalid = fs.validationStatus is ValidationStatus.Invalid
          BadgePill(
            text =
              when {
                isInvalid -> "!"
                !fs.isEmpty -> "✓"
                fs.isRequired -> "*"
                else -> "•"
              },
            bgColor =
              when {
                isInvalid -> Color(0xFFFEE2E2)
                !fs.isEmpty -> Color(0xFFE8F5E9)
                else -> Color(0xFFF3F4F6)
              },
            textColor =
              when {
                isInvalid -> Color(0xFFB91C1C)
                !fs.isEmpty -> Color(0xFF1B5E20)
                else -> Color(0xFF6B7280)
              },
          )
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
          color = Color(0xFF374151),
        ),
    )

    // Outer Phone Bezel
    Box(
      modifier =
        Modifier.width(400.dp)
          .height(720.dp)
          .clip(RoundedCornerShape(36.dp))
          .background(Color(0xFF1F2937))
          .padding(10.dp)
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0xFFF3F6F4))
      ) {
        // Simulated Mobile Status Bar
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(Color(0xFF0D3B10))
              .padding(horizontal = 18.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "09:41",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
              ),
          )
          // Camera notch pill
          Box(
            modifier =
              Modifier.width(64.dp).height(10.dp).clip(CircleShape).background(Color(0xFF051A06))
          )
          Text(
            text = "5G • 100%",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
              ),
          )
        }

        // Screen Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }

        // Bottom Gesture Home Bar
        Box(
          modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.width(110.dp).height(4.dp).clip(CircleShape).background(Color(0xFF9CA3AF))
          )
        }
      }
    }
  }
}
