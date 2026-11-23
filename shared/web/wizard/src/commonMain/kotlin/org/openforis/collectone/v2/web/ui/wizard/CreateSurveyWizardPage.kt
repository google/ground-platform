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
package org.openforis.collectone.v2.web.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_back
import org.openforis.collectone.v2.web.resources.wizard_cancel
import org.openforis.collectone.v2.web.resources.wizard_cancel_survey_creation
import org.openforis.collectone.v2.web.resources.wizard_create_survey
import org.openforis.collectone.v2.web.resources.wizard_discard
import org.openforis.collectone.v2.web.resources.wizard_discard_body
import org.openforis.collectone.v2.web.resources.wizard_discard_title
import org.openforis.collectone.v2.web.resources.wizard_keep_editing
import org.openforis.collectone.v2.web.resources.wizard_next
import org.openforis.collectone.v2.web.resources.wizard_required_field_missing
import org.openforis.collectone.v2.web.resources.wizard_saving_survey
import org.openforis.collectone.v2.web.resources.wizard_skip_for_now
import org.openforis.collectone.v2.web.resources.wizard_starter_template_cancel
import org.openforis.collectone.v2.web.resources.wizard_starter_template_keep_forms
import org.openforis.collectone.v2.web.resources.wizard_starter_template_replace_body
import org.openforis.collectone.v2.web.resources.wizard_starter_template_replace_forms
import org.openforis.collectone.v2.web.resources.wizard_starter_template_replace_title
import org.openforis.collectone.v2.web.resources.wizard_working_copy_continue
import org.openforis.collectone.v2.web.resources.wizard_working_copy_discard
import org.openforis.collectone.v2.web.resources.wizard_working_copy_prompt_body
import org.openforis.collectone.v2.web.resources.wizard_working_copy_prompt_title
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorActions
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareCardPosition
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.wizard.steps.FormQuestionsStepContent
import org.openforis.collectone.v2.web.ui.wizard.steps.MapFeaturesStepContent
import org.openforis.collectone.v2.web.ui.wizard.steps.ModeAndPurposeStepContent
import org.openforis.collectone.v2.web.ui.wizard.steps.OverviewStepContent
import org.openforis.collectone.v2.web.ui.wizard.steps.SharingStepContent
import org.openforis.collectone.v2.web.ui.wizard.steps.SurveyAreaStepContent
import org.openforis.collectone.v2.web.ui.wizard.steps.WizardCareStickyChip

private val WizardContentMaxWidth = 860.dp

private val WizardWideContentMaxWidth = 1160.dp

/**
 * Bounds of the height of the Map features step's map preview and of its settings, which scroll on
 * their own; between them it follows the window's height.
 */
private val WizardMapPreviewMinHeight = 420.dp

private val WizardMapPreviewMaxHeight = 900.dp

/**
 * Full-page 6-step Survey Creation Wizard (`#create-survey`) combining Ground 1.0's field survey
 * wizard and Collect Earth Online's visual classification & sample design wizard, aligned with
 * Collect One's `SurveyEditorPage` and the MAP Impact Measurement framework.
 */
@Composable
fun CreateSurveyWizardPage(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  mapUiState: SurveyMapUiState = SurveyMapUiState(),
  onToggleImagerySource: (String) -> Unit = {},
  isDarkTheme: Boolean = false,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
  /** Whether the purpose library is still loading (Step 2 shows a spinner instead of cards). */
  isLibraryLoading: Boolean = false,
  modifier: Modifier = Modifier,
  header:
    @Composable
    (navigationIcon: @Composable () -> Unit, context: @Composable () -> Unit) -> Unit,
) {
  var isConfirmingDiscard by remember { mutableStateOf(false) }
  // Whether Step 4's "How are map features created?" choice shows all options (`true`) or a summary
  // of the chosen one (`false`): collapsed once the organizer picks one, expanded by "Change", and
  // until then collapsed only if the Purpose Packs or collection mode already decided it. Kept here
  // so it survives moving between steps.
  var mapFeatureSourceExpanded by
    remember(uiState.workingSurveyId) { mutableStateOf<Boolean?>(null) }

  Column(modifier = modifier.fillMaxSize()) {
    header(
      {
        IconButton(onClick = { isConfirmingDiscard = true }) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.wizard_cancel_survey_creation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      {
        WebHeaderContext(
          title = uiState.title.ifBlank { stringResource(Res.string.wizard_create_survey) }
        ) {
          uiState.selectedOrganization?.let { org -> WebHeaderSupportingText(org.name) }
        }
      },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (uiState.currentStep) {
        CreateSurveyWizardStep.OVERVIEW -> {
          val scrollState = rememberScrollState()
          WizardStepContainer(scrollState = scrollState) {
            OverviewStepContent(
              uiState = uiState,
              actions = actions,
              editorUiState = editorUiState,
              editorActions = editorActions,
              pickTextFile = pickTextFile,
              scrollState = scrollState,
            )
          }
        }
        CreateSurveyWizardStep.MODE_AND_PURPOSE -> {
          val scrollState = rememberScrollState()
          WizardStepContainer(scrollState = scrollState) {
            ModeAndPurposeStepContent(
              uiState = uiState,
              actions = actions,
              editorUiState = editorUiState,
              scrollState = scrollState,
              isLibraryLoading = isLibraryLoading,
            )
          }
        }
        CreateSurveyWizardStep.SURVEY_AREA -> {
          val scrollState = rememberScrollState()
          WizardStepContainer(maxWidth = WizardWideContentMaxWidth, scrollState = scrollState) {
            SurveyAreaStepContent(
              uiState = uiState,
              editorUiState = editorUiState,
              editorActions = editorActions,
              mapUiState = mapUiState,
              onToggleImagerySource = onToggleImagerySource,
              scrollState = scrollState,
            )
          }
        }
        CreateSurveyWizardStep.MAP_FEATURES ->
          BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The map and its settings fill the step's visible height (less the container's
            // padding), so once scrolled into view both fit on screen together.
            val mapPreviewHeight =
              (maxHeight - 64.dp).coerceIn(WizardMapPreviewMinHeight, WizardMapPreviewMaxHeight)
            WizardStepContainer(maxWidth = WizardWideContentMaxWidth) {
              MapFeaturesStepContent(
                uiState = uiState,
                actions = actions,
                editorUiState = editorUiState,
                editorActions = editorActions,
                pickTextFile = pickTextFile,
                sourceCollapsed =
                  mapFeatureSourceExpanded?.not() ?: uiState.mapFeatureSourcePreselected,
                onSourceCollapsedChange = { mapFeatureSourceExpanded = !it },
                sourcePreselected =
                  mapFeatureSourceExpanded == null && uiState.mapFeatureSourcePreselected,
                mapPreviewHeight = mapPreviewHeight,
              )
            }
          }
        CreateSurveyWizardStep.FORM_QUESTIONS ->
          FormQuestionsStepContent(
            uiState = uiState,
            editorUiState = editorUiState,
            editorActions = editorActions,
            isDarkTheme = isDarkTheme,
          )
        CreateSurveyWizardStep.SHARING -> {
          val scrollState = rememberScrollState()
          val careCardPosition = remember { CareCardPosition() }
          Box(
            modifier =
              Modifier.fillMaxSize().onGloballyPositioned {
                careCardPosition.viewportTop = it.positionInRoot().y
              }
          ) {
            WizardStepContainer(scrollState = scrollState) {
              SharingStepContent(
                uiState = uiState,
                actions = actions,
                editorUiState = editorUiState,
                editorActions = editorActions,
                careCardPosition = careCardPosition,
              )
            }
            // Keeps CARE conformity in view while the organizer edits the Sharing settings below.
            WizardCareStickyChip(
              uiState = uiState,
              actions = actions,
              editorUiState = editorUiState,
              position = careCardPosition,
              scrollState = scrollState,
              modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    WizardBottomBar(
      uiState = uiState,
      actions = actions,
      onCancelClick = { isConfirmingDiscard = true },
    )
  }

  if (isConfirmingDiscard) {
    AlertDialog(
      onDismissRequest = { isConfirmingDiscard = false },
      title = { Text(stringResource(Res.string.wizard_discard_title)) },
      text = { Text(stringResource(Res.string.wizard_discard_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            isConfirmingDiscard = false
            actions.cancelWizard()
          }
        ) {
          Text(stringResource(Res.string.wizard_discard))
        }
      },
      dismissButton = {
        TextButton(onClick = { isConfirmingDiscard = false }) {
          Text(stringResource(Res.string.wizard_keep_editing))
        }
      },
    )
  }

  if (uiState.isPromptingUnsavedDraft) {
    AlertDialog(
      onDismissRequest = actions::continueUnsavedDraft,
      title = { Text(stringResource(Res.string.wizard_working_copy_prompt_title)) },
      text = { Text(stringResource(Res.string.wizard_working_copy_prompt_body)) },
      confirmButton = {
        TextButton(onClick = actions::continueUnsavedDraft) {
          Text(stringResource(Res.string.wizard_working_copy_continue))
        }
      },
      dismissButton = {
        TextButton(onClick = actions::discardUnsavedDraft) {
          Text(stringResource(Res.string.wizard_working_copy_discard))
        }
      },
    )
  }

  uiState.pendingStarterTemplateId?.let { pending ->
    val pendingTitle =
      uiState.selectedPurposePack
        ?.starterTemplate(pending)
        ?.title
        ?.get(editorUiState.details.defaultLanguage.ifBlank { "en" })
        .orEmpty()
    AlertDialog(
      onDismissRequest = { actions.resolveStarterTemplateChange(null) },
      title = { Text(stringResource(Res.string.wizard_starter_template_replace_title)) },
      text = {
        Text(stringResource(Res.string.wizard_starter_template_replace_body, pendingTitle))
      },
      confirmButton = {
        TextButton(onClick = { actions.resolveStarterTemplateChange(true) }) {
          Text(stringResource(Res.string.wizard_starter_template_replace_forms))
        }
      },
      dismissButton = {
        Row {
          TextButton(onClick = { actions.resolveStarterTemplateChange(null) }) {
            Text(stringResource(Res.string.wizard_starter_template_cancel))
          }
          TextButton(onClick = { actions.resolveStarterTemplateChange(false) }) {
            Text(stringResource(Res.string.wizard_starter_template_keep_forms))
          }
        }
      },
    )
  }
}

@Composable
private fun WizardInlineStepper(
  currentStep: CreateSurveyWizardStep,
  canAdvanceFromCurrent: Boolean,
  onSelectStep: (CreateSurveyWizardStep) -> Unit,
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CreateSurveyWizardStep.entries.forEachIndexed { index, step ->
      val isCurrent = step == currentStep
      val isCompleted = step.ordinal < currentStep.ordinal
      val isNext = step.ordinal == currentStep.ordinal + 1
      val isClickable = isCompleted || isNext
      val stepChipShape = RoundedCornerShape(18.dp)
      Surface(
        shape = stepChipShape,
        color =
          when {
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            isCompleted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surfaceContainer
          },
        contentColor =
          when {
            isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
            isCompleted -> MaterialTheme.colorScheme.onSecondaryContainer
            isNext && canAdvanceFromCurrent -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier =
          Modifier.clip(stepChipShape).clickable(enabled = isClickable) { onSelectStep(step) },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Box(
            modifier =
              Modifier.size(18.dp)
                .background(
                  color =
                    when {
                      isCurrent -> MaterialTheme.colorScheme.primary
                      isCompleted -> MaterialTheme.colorScheme.secondary
                      else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                  shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
          ) {
            if (isCompleted) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(12.dp),
              )
            } else {
              Text(
                text = "${step.stepNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                  if (isCurrent) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Text(
            text = stringResource(step.labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (index < CreateSurveyWizardStep.entries.lastIndex) {
        Text(
          text = "→",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun WizardBottomBar(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  onCancelClick: () -> Unit,
) {
  val step = uiState.currentStep
  val isFirst = step == CreateSurveyWizardStep.OVERVIEW
  val isLast = step == CreateSurveyWizardStep.SHARING
  val hasActiveValidationError = uiState.currentStepValidationMessage != null
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isFirst) {
          OutlinedButton(onClick = actions::previousStep) {
            Icon(
              Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.wizard_back))
          }
        } else {
          TextButton(onClick = onCancelClick) { Text(stringResource(Res.string.wizard_cancel)) }
        }
      }
      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        WizardInlineStepper(
          currentStep = uiState.currentStep,
          canAdvanceFromCurrent = uiState.canProceedFromCurrentStep,
          onSelectStep = actions::selectStep,
        )
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (hasActiveValidationError) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = stringResource(Res.string.wizard_required_field_missing),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
        if (uiState.canSkipCurrentStep) {
          TextButton(onClick = actions::skipStep) {
            Text(stringResource(Res.string.wizard_skip_for_now))
          }
        }
        if (isLast) {
          Button(
            onClick = actions::finishWizard,
            enabled = uiState.canProceedFromCurrentStep && !uiState.isFinishing,
          ) {
            if (uiState.isFinishing) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
              )
              Spacer(Modifier.width(8.dp))
              Text(stringResource(Res.string.wizard_saving_survey))
            } else {
              Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(Res.string.wizard_create_survey))
            }
          }
        } else {
          Button(onClick = actions::nextStep) {
            Text(stringResource(Res.string.wizard_next))
            Spacer(Modifier.width(6.dp))
            Icon(
              Icons.AutoMirrored.Outlined.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WizardStepContainer(
  maxWidth: Dp = WizardContentMaxWidth,
  scrollState: ScrollState = rememberScrollState(),
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(32.dp),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      content()
    }
  }
}
