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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.GeometryKind
import org.openforis.collectone.v2.app.domain.model.editor.LatLng
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.geometryKind
import org.openforis.collectone.v2.app.ui.state.SurveyMapUiState
import org.openforis.collectone.v2.core.forms.ui.GroundBadgeTone
import org.openforis.collectone.v2.core.forms.ui.GroundTonalBadge
import org.openforis.collectone.v2.web.domain.model.editor.CareGovernanceCriterion
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.WebPage
import org.openforis.collectone.v2.web.ui.common.ProgramChips
import org.openforis.collectone.v2.web.ui.common.PurposePackGrid
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.ValidationErrorBanner
import org.openforis.collectone.v2.web.ui.common.WebAppHeader
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.formeditor.DropdownSelector
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorPage
import org.openforis.collectone.v2.web.ui.state.CreateSurveyWizardUiState
import org.openforis.collectone.v2.web.ui.state.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.state.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.AddFormDialog
import org.openforis.collectone.v2.web.ui.surveyeditor.ChoiceDatasetCreationDialogs
import org.openforis.collectone.v2.web.ui.surveyeditor.EntityDatasetEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.GEOMETRY_FILE_ACCEPT
import org.openforis.collectone.v2.web.ui.surveyeditor.InteractiveLayerMapCard
import org.openforis.collectone.v2.web.ui.surveyeditor.IssueList
import org.openforis.collectone.v2.web.ui.surveyeditor.LanguageSelectorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.MapLayerImportPreview
import org.openforis.collectone.v2.web.ui.surveyeditor.NavItem
import org.openforis.collectone.v2.web.ui.surveyeditor.SamplingDesignPanel
import org.openforis.collectone.v2.web.ui.surveyeditor.SharingContent
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyAreaSection
import org.openforis.collectone.v2.web.ui.surveyeditor.rememberChoiceDatasetCreationState
import org.openforis.collectone.v2.web.ui.viewmodel.CreateSurveyWizardActions
import org.openforis.collectone.v2.web.ui.viewmodel.SurveyEditorActions

private const val WIZARD_IMPORT_FILE_ACCEPT = ".xml,.xhtml,.cep,.zip,.ced"

private val WizardContentMaxWidth = 860.dp
private val WizardWideContentMaxWidth = 1160.dp

/**
 * Bounds of the height of the Map features step's map preview and of its settings, which scroll on
 * their own; between them it follows the window's height.
 */
private val WizardMapPreviewMinHeight = 420.dp
private val WizardMapPreviewMaxHeight = 900.dp

/** Sentinel for the "None (personal survey)" option in the Organization selector. */
private val NoOrganizationOption = Organization(id = "", name = "None (personal survey)")

/**
 * Full-page 6-step Survey Creation Wizard (`#create-survey`) combining Ground 1.0's field survey
 * wizard and Collect Earth Online's visual classification & sample design wizard, aligned with
 * Collect One's `SurveyEditorPage` and the MAP Impact Measurement framework.
 */
@Composable
fun CreateSurveyWizardPage(
  state: WebAppState,
  isDarkTheme: Boolean = state.isDarkTheme,
  onSignOut: () -> Unit = { state.signOut() },
  modifier: Modifier = Modifier,
) {
  val wizardState by state.createSurveyWizard.uiState.collectAsState()
  val editorState by state.surveyEditor.uiState.collectAsState()
  val mapState by state.surveyMap.uiState.collectAsState()

  CreateSurveyWizardPage(
    uiState = wizardState,
    actions = state.createSurveyWizard,
    editorUiState = editorState,
    editorActions = state.surveyEditor,
    mapUiState = mapState,
    onToggleImagerySource = state.surveyMap::toggleImagerySource,
    isDarkTheme = isDarkTheme,
    modifier = modifier,
    header = { navigationIcon, context ->
      WebAppHeader(
        state = state,
        onSignOut = onSignOut,
        onLogoClick = {
          state.createSurveyWizard.cancelWizard()
          state.selectPage(WebPage.WEB_SURVEYS)
        },
        navigationIcon = navigationIcon,
        context = context,
        actions = null,
      )
    },
  )
}

/** Stateless overload of the 6-step Survey Creation Wizard. */
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
            contentDescription = "Cancel survey creation",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      {
        WebHeaderContext(title = uiState.title.ifBlank { "Create survey" }) {
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
        CreateSurveyWizardStep.SHARING ->
          WizardStepContainer {
            SharingStepContent(
              uiState = uiState,
              actions = actions,
              editorUiState = editorUiState,
              editorActions = editorActions,
            )
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
      title = { Text("Discard survey?") },
      text = { Text("If you leave now, this survey and any changes you've made will be lost.") },
      confirmButton = {
        TextButton(
          onClick = {
            isConfirmingDiscard = false
            actions.cancelWizard()
          }
        ) {
          Text("Discard")
        }
      },
      dismissButton = {
        TextButton(onClick = { isConfirmingDiscard = false }) { Text("Keep editing") }
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
      Surface(
        shape = RoundedCornerShape(18.dp),
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
        modifier = Modifier.clickable(enabled = isClickable) { onSelectStep(step) },
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
            text = step.label,
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
            Text("Back")
          }
        } else {
          TextButton(onClick = onCancelClick) { Text("Cancel") }
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
                text = "Required field missing — see highlighted section",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
        if (uiState.canSkipCurrentStep) {
          TextButton(onClick = actions::skipStep) { Text("Skip for now") }
        }
        if (isLast) {
          Button(
            onClick = actions::finishWizard,
            enabled = uiState.canProceedFromCurrentStep,
          ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create survey")
          }
        } else {
          Button(onClick = actions::nextStep) {
            Text("Next")
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

@Composable
private fun WizardSectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

// ---------------------------------------------------------------------------------------------
// Step 1: Overview
// ---------------------------------------------------------------------------------------------

@Composable
private fun OverviewStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  pickTextFile: TextFilePicker,
  scrollState: ScrollState,
) {
  var titleHadFocus by remember { mutableStateOf(false) }
  var titleBlurredAfterFocus by remember { mutableStateOf(false) }
  val showTitleError = (uiState.isTitleInvalid || titleBlurredAfterFocus) && uiState.title.isBlank()
  val showStartMethodError =
    uiState.showOverviewValidationErrors &&
      uiState.title.isNotBlank() &&
      !uiState.canProceedFromCurrentStep
  var identityCardY by remember { mutableStateOf(0) }
  var startMethodCardY by remember { mutableStateOf(0) }
  val titleFocusRequester = remember { FocusRequester() }
  // Set while the title holds the focus it was given when the step opened, so leaving the field
  // without typing doesn't flag it as missing before the organizer has touched it.
  var titleAutoFocused by remember { mutableStateOf(false) }

  // Start typing the survey's name straight away when the designer opens on a new survey.
  LaunchedEffect(Unit) {
    if (uiState.title.isBlank()) {
      titleAutoFocused = true
      runCatching { titleFocusRequester.requestFocus() }
      withFrameNanos {}
      runCatching { titleFocusRequester.requestFocus() }.onFailure { titleAutoFocused = false }
    }
  }

  LaunchedEffect(uiState.validationAttemptCount) {
    if (uiState.validationAttemptCount > 0 && !uiState.canProceedFromCurrentStep) {
      if (uiState.title.isBlank()) {
        scrollState.animateScrollTo(identityCardY)
        runCatching { titleFocusRequester.requestFocus() }
      } else {
        scrollState.animateScrollTo(startMethodCardY)
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Overview",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        "Name your survey, choose the organization running it, configure languages, and pick how you'd like to start.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  WizardOverviewHeroGraphic()

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier =
      Modifier.fillMaxWidth()
        .onGloballyPositioned { identityCardY = it.positionInParent().y.toInt() }
        .then(
          if (showTitleError) {
            Modifier.border(
              1.5.dp,
              MaterialTheme.colorScheme.error,
              MaterialTheme.shapes.medium,
            )
          } else {
            Modifier
          }
        ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      WizardSectionTitle("Survey identity")
      if (showTitleError) {
        ValidationErrorBanner(message = "Enter a survey title to continue.")
      }
      OutlinedTextField(
        value = uiState.title,
        onValueChange = actions::updateTitle,
        label = { Text("Survey title *") },
        placeholder = { Text("e.g. Nyeri Smallholder Coffee Plot Mapping") },
        singleLine = true,
        isError = showTitleError,
        modifier =
          Modifier.fillMaxWidth().focusRequester(titleFocusRequester).onFocusChanged { focusState ->
            if (focusState.isFocused) {
              if (!titleAutoFocused) titleHadFocus = true
            } else {
              titleAutoFocused = false
              if (titleHadFocus) titleBlurredAfterFocus = true
            }
          },
      )
      OutlinedTextField(
        value = uiState.description,
        onValueChange = actions::updateDescription,
        label = { Text("Description (optional)") },
        placeholder = { Text("Describe the goals and instructions for data collectors.") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )

      val orgOptions =
        remember(uiState.memberOrganizations, uiState.selectedOrganization) {
          buildList {
            add(NoOrganizationOption)
            addAll(uiState.memberOrganizations)
            val sel = uiState.selectedOrganization
            if (sel != null && none { it.id == sel.id }) add(sel)
          }
        }
      DropdownSelector(
        label = "Organization",
        selectedText = uiState.selectedOrganization?.name ?: NoOrganizationOption.name,
        options = orgOptions,
        optionText = { it.name },
        onSelect = { picked -> actions.selectOrganization(picked.id.ifBlank { null }) },
      )
      Text(
        text =
          "Running a survey under an organization unlocks its shared Purpose Packs, Dictionary concepts, Form templates, and custom basemap imagery.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LanguageSelectorSection(uiState = editorUiState, actions = editorActions)
    }
  }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier =
      Modifier.fillMaxWidth()
        .onGloballyPositioned { startMethodCardY = it.positionInParent().y.toInt() }
        .then(
          if (showStartMethodError) {
            Modifier.border(
              1.5.dp,
              MaterialTheme.colorScheme.error,
              MaterialTheme.shapes.medium,
            )
          } else {
            Modifier
          }
        ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      WizardSectionTitle("How would you like to start?")
      if (showStartMethodError) {
        uiState.currentStepValidationMessage?.let { msg -> ValidationErrorBanner(message = msg) }
      }
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        WizardStartMethod.entries.forEach { method ->
          val icon =
            when (method) {
              WizardStartMethod.FROM_SCRATCH -> Icons.Outlined.AutoAwesome
              WizardStartMethod.COPY_EXISTING -> Icons.Outlined.ContentCopy
              WizardStartMethod.IMPORT_XFORMS -> Icons.Outlined.UploadFile
            }
          SelectableChoiceCard(
            title = method.label,
            description = method.description,
            icon = icon,
            selected = uiState.startMethod == method,
            onClick = { actions.selectStartMethod(method) },
            modifier = Modifier.weight(1f),
          )
        }
      }

      when (uiState.startMethod) {
        WizardStartMethod.FROM_SCRATCH -> Unit
        WizardStartMethod.COPY_EXISTING -> {
          if (uiState.existingSurveys.isEmpty()) {
            Text(
              text = "No existing surveys available to copy.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          } else {
            val selectedSurvey =
              uiState.existingSurveys.firstOrNull { it.id == uiState.copySourceSurveyId }
                ?: uiState.existingSurveys.first()
            DropdownSelector(
              label = "Source survey to copy",
              selectedText = "${selectedSurvey.title} (${selectedSurvey.location})",
              options = uiState.existingSurveys,
              optionText = { "${it.title} (${it.location})" },
              onSelect = { actions.selectCopySourceSurvey(it.id) },
            )
            Text(
              text =
                "Copies Forms, Map layer schemas, sample plot designs, and Purposes without copying previously collected records.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        WizardStartMethod.IMPORT_XFORMS -> {
          Text(
            text = "Supported formats: ODK XForms (.xml), Collect Earth (.cep, .zip)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedButton(
              onClick = {
                pickTextFile(WIZARD_IMPORT_FILE_ACCEPT) { result ->
                  when (result) {
                    is TextFilePickResult.Picked ->
                      actions.importXFormsXml(result.fileName, result.text)
                    else -> Unit
                  }
                }
              }
            ) {
              Icon(
                Icons.Outlined.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                if (uiState.importedXForms != null) "Choose another file (.xml, .cep)"
                else "Choose file (.xml, .cep)"
              )
            }
            uiState.importedXForms?.let { imported ->
              val plotCount = uiState.selectedCepProject?.plotDataset?.rows?.size ?: 0
              val summaryPrefix = buildString {
                append("${imported.form.questions.size} questions")
                if (plotCount > 0) append(" · $plotCount sample plots")
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${uiState.importedXFormsFileName}: ${imported.form.title}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "$summaryPrefix parsed · Ready to import into Step 5",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              IconButton(onClick = actions::clearImportedXForms) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear imported file")
              }
            }
          }
          if (uiState.importedCepProjects.size > 1) {
            val selectedCep = uiState.selectedCepProject ?: uiState.importedCepProjects.first()
            DropdownSelector(
              label = "Collect Earth survey in archive (${uiState.importedCepProjects.size} found)",
              selectedText = "${selectedCep.projectTitle} (${selectedCep.fileName})",
              options = uiState.importedCepProjects,
              optionText = { "${it.projectTitle} (${it.fileName})" },
              onSelect = { actions.selectImportedCepProject(it.fileName) },
            )
          }
          uiState.importErrorMessage?.let { errorMsg -> ValidationErrorBanner(message = errorMsg) }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Step 2: Purpose
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModeAndPurposeStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  scrollState: ScrollState,
) {
  var purposeCardY by remember { mutableStateOf(0) }
  var otherInputY by remember { mutableStateOf(0) }

  LaunchedEffect(uiState.validationAttemptCount) {
    if (uiState.validationAttemptCount > 0 && uiState.isPurposeInvalid) {
      val targetY =
        if (
          SurveyWizardRules.OTHER_PURPOSE_ID in editorUiState.details.purposeIds &&
            editorUiState.details.otherPurpose.isBlank() &&
            otherInputY > 0
        ) {
          (purposeCardY + otherInputY - 48).coerceAtLeast(purposeCardY)
        } else {
          purposeCardY
        }
      scrollState.animateScrollTo(targetY)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Purpose",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        "Choose what outcomes or reporting standards this survey supports to pre-configure its map layer and starting form.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier =
      Modifier.fillMaxWidth()
        .onGloballyPositioned { purposeCardY = it.positionInParent().y.toInt() }
        .then(
          if (uiState.isPurposeInvalid) {
            Modifier.border(
              1.5.dp,
              MaterialTheme.colorScheme.error,
              MaterialTheme.shapes.medium,
            )
          } else {
            Modifier
          }
        ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      val (compliancePacks, generalPacks) =
        remember(uiState.sortedPurposePacks) {
          uiState.sortedPurposePacks.partition(SurveyWizardRules::isComplianceFrameworkPack)
        }
      WizardSectionTitle("What will this data be used for? *")
      Text(
        text =
          "Select the primary purpose so Collect One can tailor your survey's map layer defaults, starting form, standard library concepts, data governance guardrails (such as EUDR or CARE sovereignty), and one-click export profiles. Choose \"Other\" if your goal isn't listed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (compliancePacks.isEmpty()) {
        PurposePackGrid(
          packs = generalPacks,
          library = uiState.library,
          organizationName = uiState.selectedOrganization?.name,
          language = editorUiState.details.defaultLanguage.ifBlank { "en" },
          selectedIds = editorUiState.details.purposeIds,
          onToggle = actions::togglePurposePack,
          otherPurposeText = editorUiState.details.otherPurpose,
          onOtherPurposeTextChange = actions::updateOtherPurpose,
          isError = uiState.isPurposeInvalid,
          onOtherInputPositioned = { otherInputY = it },
        )
      } else {
        PurposePackGrid(
          packs = generalPacks,
          library = uiState.library,
          organizationName = uiState.selectedOrganization?.name,
          language = editorUiState.details.defaultLanguage.ifBlank { "en" },
          selectedIds = editorUiState.details.purposeIds,
          onToggle = actions::togglePurposePack,
        )
        Spacer(Modifier.height(4.dp))
        WizardSectionTitle("Compliance with deforestation & reporting regulations")
        Text(
          text =
            "Select a supply-chain, legality, or certification framework if this survey must satisfy one (pre-configures required geolocation guardrails and one-click export profiles):",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PurposePackGrid(
          packs = compliancePacks,
          library = uiState.library,
          organizationName = uiState.selectedOrganization?.name,
          language = editorUiState.details.defaultLanguage.ifBlank { "en" },
          selectedIds = editorUiState.details.purposeIds,
          onToggle = actions::togglePurposePack,
          otherPurposeText = editorUiState.details.otherPurpose,
          onOtherPurposeTextChange = actions::updateOtherPurpose,
          isError = uiState.isPurposeInvalid,
          onOtherInputPositioned = { otherInputY = it },
        )
      }

      if (uiState.offeredProgramIds.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        WizardSectionTitle("Reporting programs & frameworks")
        Text(
          text = "Tag this survey with the programs its data contributes to:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProgramChips(
          programIds = uiState.offeredProgramIds,
          selectedIds = editorUiState.details.programIds,
          onToggle = actions::toggleProgram,
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Step 3: Survey area
// ---------------------------------------------------------------------------------------------

@Composable
private fun SurveyAreaStepContent(
  uiState: CreateSurveyWizardUiState,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  mapUiState: SurveyMapUiState,
  onToggleImagerySource: (String) -> Unit,
  scrollState: ScrollState,
) {
  var surveyAreaCardY by remember { mutableStateOf(0) }

  LaunchedEffect(uiState.validationAttemptCount) {
    if (uiState.validationAttemptCount > 0 && uiState.isSurveyAreaInvalid) {
      scrollState.animateScrollTo(surveyAreaCardY)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Survey area",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        if (uiState.collectionMode == SurveyCollectionMode.FIELD_SURVEY) {
          "Define the geographic boundary to frame the survey map and pre-stage offline basemap imagery for field collectors."
        } else {
          "Define the area of interest (AOI) where sample plots will be generated and interpreted, and verify the satellite basemap imagery."
        },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier =
      Modifier.fillMaxWidth()
        .onGloballyPositioned { surveyAreaCardY = it.positionInParent().y.toInt() }
        .then(
          if (uiState.isSurveyAreaInvalid) {
            Modifier.border(
              1.5.dp,
              MaterialTheme.colorScheme.error,
              MaterialTheme.shapes.medium,
            )
          } else {
            Modifier
          }
        ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SurveyAreaSection(
        uiState = editorUiState,
        actions = editorActions,
        localPlaces = editorUiState.localPlaces,
        searchPlaces = editorActions::searchPlaces,
        isError = uiState.isSurveyAreaInvalid,
      )
    }
  }

  val orgImagery = uiState.selectedOrganization?.imagerySources.orEmpty()
  val globalImagery = mapUiState.allUsersImagerySources
  val allSources = remember(globalImagery, orgImagery) { globalImagery + orgImagery }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      WizardSectionTitle("Basemap & reference imagery")
      Text(
        text =
          if (uiState.collectionMode == SurveyCollectionMode.FIELD_SURVEY) {
            "Choose which satellite and reference imagery layers are enabled alongside the default satellite basemap."
          } else {
            "Select the high-resolution or temporal imagery layers (such as NICFI Planet or Sentinel-2 mosaics) that photo-interpreters will inspect when classifying plots."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (allSources.isEmpty()) {
        Text(
          text =
            "Default Mapbox Satellite basemap is enabled. No custom imagery layers configured.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        allSources.forEach { source ->
          val isOrgSource = orgImagery.any { it.id == source.id }
          ImagerySourceRow(
            source = source,
            sourceBadge =
              if (isOrgSource) uiState.selectedOrganization?.name ?: "Organization"
              else "All users",
            enabled = mapUiState.isImagerySourceEnabled(source.id),
            onToggle = { onToggleImagerySource(source.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun ImagerySourceRow(
  source: ImagerySource,
  sourceBadge: String,
  enabled: Boolean,
  onToggle: () -> Unit,
) {
  OutlinedCard(
    onClick = onToggle,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Checkbox(checked = enabled, onCheckedChange = { onToggle() })
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = source.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          GroundTonalBadge(
            text = sourceBadge,
            tone =
              if (sourceBadge == "All users") GroundBadgeTone.NEUTRAL else GroundBadgeTone.TERTIARY,
          )
        }
        val addedBy =
          if (sourceBadge == "All users") "Built-in platform imagery" else "Added by $sourceBadge"
        val offlineNote =
          if (source.allowOfflineDownload) {
            "Available for offline field download and web mapping"
          } else {
            "Online satellite & reference imagery layer"
          }
        Text(
          text = "$addedBy · $offlineNote",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Step 4: Map features
// ---------------------------------------------------------------------------------------------

@Composable
private fun MapFeaturesStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  pickTextFile: TextFilePicker,
  sourceCollapsed: Boolean,
  onSourceCollapsedChange: (Boolean) -> Unit,
  sourcePreselected: Boolean,
  mapPreviewHeight: Dp,
) {
  val primaryLayer = uiState.primaryMapLayer
  val requiresEudrGuardrail =
    SurveyWizardRules.requiresEudrPolygonGuardrail(editorUiState.details.purposeIds)
  var importPreview by remember { mutableStateOf<MapLayerImportPreview?>(null) }
  var importError by remember { mutableStateOf<String?>(null) }
  var selectedPreviewRow by remember { mutableStateOf<String?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Map features",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        "Configure the primary Map layer where data will be recorded: free-form field collection, a statistical sample plot grid, or imported GeoJSON / KML features.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.animateContentSize(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      WizardSectionTitle("How are map features created?")
      fun iconOf(source: WizardMapFeatureSource) =
        when (source) {
          WizardMapFeatureSource.FIELD_FREEFORM -> Icons.Outlined.AddLocationAlt
          WizardMapFeatureSource.SAMPLE_PLOTS -> Icons.Outlined.GridOn
          WizardMapFeatureSource.IMPORT_FILE -> Icons.Outlined.UploadFile
        }
      if (sourceCollapsed) {
        // Once chosen (or preselected), the choice collapses to a one-line summary so the
        // configuration for it is right below; "Change" brings the cards back.
        val source = uiState.mapFeatureSource
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            iconOf(source),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              source.label,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              if (sourcePreselected) {
                "${source.description} Preselected for the purpose and collection mode you chose."
              } else {
                source.description
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(onClick = { onSourceCollapsedChange(false) }) { Text("Change") }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          WizardMapFeatureSource.entries.forEach { source ->
            SelectableChoiceCard(
              title = source.label,
              description = source.description,
              icon = iconOf(source),
              tags = source.tags,
              selected = uiState.mapFeatureSource == source,
              onClick = {
                actions.selectMapFeatureSource(source)
                onSourceCollapsedChange(true)
              },
              modifier = Modifier.weight(1f),
              graphic = { MapFeatureSourceCardGraphic(source = source) },
            )
          }
        }
      }
    }
  }

  if (requiresEudrGuardrail && uiState.mapFeatureSource != WizardMapFeatureSource.SAMPLE_PLOTS) {
    Surface(
      color = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(20.dp))
          Text(
            text = "EUDR Article 9 geolocation guardrail",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text =
            "EU Deforestation Regulation (EUDR) requires polygon boundaries for all production plots larger than 4 hectares. Polygon geometry is selected by default.",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Checkbox(
            checked = uiState.eudrPlotsUnderFourHaConfirmed,
            onCheckedChange = actions::confirmEudrPlotsUnderFourHa,
          )
          Text(
            text = "All production plots in this survey are ≤ 4 ha (allow single-point locations)",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }

  if (primaryLayer != null) {
    val surveyArea = editorUiState.details.surveyArea
    val mapDataset =
      remember(primaryLayer, uiState.mapFeatureSource, surveyArea) {
        if (
          uiState.mapFeatureSource == WizardMapFeatureSource.FIELD_FREEFORM &&
            primaryLayer.rows.isEmpty()
        ) {
          seedPreviewFeaturesForFreeformLayer(primaryLayer, surveyArea)
        } else {
          primaryLayer
        }
      }
    // The map and its settings share a fixed height, and the settings scroll on their own (as in
    // the
    // survey editor), so the map stays in view while scrolling through long settings such as a
    // sample design.
    Row(
      modifier = Modifier.fillMaxWidth().height(mapPreviewHeight),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        key(uiState.mapFeatureSource, primaryLayer.geometryKind) {
          InteractiveLayerMapCard(
            editor = editorActions,
            dataset = mapDataset,
            selectedRow = selectedPreviewRow,
            onSelectRow = { selectedPreviewRow = it },
            modifier = Modifier.fillMaxSize(),
            geometryEditable =
              !primaryLayer.isGenerated &&
                uiState.mapFeatureSource != WizardMapFeatureSource.FIELD_FREEFORM,
            showAddButtonInHeader = false,
            backdropPolygons = surveyArea?.parts.orEmpty(),
          )
        }
      }

      ElevatedCard(
        modifier = Modifier.width(400.dp).fillMaxHeight(),
        colors =
          CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          WizardSectionTitle("Primary map layer settings")
          OutlinedTextField(
            value = primaryLayer.displayName,
            onValueChange = actions::updatePrimaryLayerDisplayName,
            label = { Text("Map layer name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          when (uiState.mapFeatureSource) {
            WizardMapFeatureSource.FIELD_FREEFORM -> {
              val allowedGeometries =
                if (requiresEudrGuardrail && !uiState.eudrPlotsUnderFourHaConfirmed) {
                  listOf(GeometryKind.POLYGON)
                } else {
                  GeometryKind.entries
                }
              DropdownSelector(
                label = "Geometry type",
                selectedText = primaryLayer.geometryKind.label,
                options = allowedGeometries,
                optionText = { it.label },
                onSelect = actions::updatePrimaryLayerGeometryKind,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Allow collectors to add new map features in the field",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  Text(
                    text =
                      "When enabled, data collectors can drop pins or walk plot perimeters to create new map features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = primaryLayer.fieldCreationEnabled,
                  onCheckedChange = actions::updatePrimaryLayerFieldCreation,
                )
              }
            }

            WizardMapFeatureSource.SAMPLE_PLOTS -> {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                WizardSectionTitle("Statistical sample design & sub-plot points")
                GroundTonalBadge(
                  text = "Collect Earth Online",
                  tone = GroundBadgeTone.PRIMARY,
                )
              }
              Text(
                text =
                  "Configure Systematic grid, Simple random, or Stratified random plots, plot shape (Point, Square, Circle), and internal sub-plot sample point grids.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              SamplingDesignPanel(
                uiState = editorUiState,
                actions = editorActions,
                dataset = primaryLayer,
              )
            }

            WizardMapFeatureSource.IMPORT_FILE -> {
              WizardSectionTitle("Import map features from GeoJSON or KML")
              Text(
                text =
                  "Upload existing farm boundaries, monitoring plots, or points of interest. Attributes in the file become Map layer properties.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                OutlinedButton(
                  onClick = {
                    importError = null
                    pickTextFile(GEOMETRY_FILE_ACCEPT) { result ->
                      when (result) {
                        is TextFilePickResult.Picked -> {
                          val preview = MapLayerImportPreview.of(result.fileName, result.text)
                          importPreview = preview
                          preview.plan?.let(actions::importPrimaryMapLayer)
                        }
                        is TextFilePickResult.Failed -> importError = result.message
                        TextFilePickResult.Cancelled -> Unit
                      }
                    }
                  }
                ) {
                  Icon(
                    Icons.Outlined.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text("Choose GeoJSON or KML file")
                }
                if (primaryLayer.rows.isNotEmpty()) {
                  GroundTonalBadge(
                    text =
                      "${primaryLayer.rows.size} ${primaryLayer.geometryKind.label.lowercase()} features loaded",
                    tone = GroundBadgeTone.PRIMARY,
                  )
                }
              }
              importError?.let { msg ->
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              importPreview?.let { preview -> IssueList(preview.result.issues) }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Also allow collectors to add new map features in the field",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    text =
                      "Keep off for fixed monitoring plots; turn on if data collectors can also add new sites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = primaryLayer.fieldCreationEnabled,
                  onCheckedChange = actions::updatePrimaryLayerFieldCreation,
                )
              }
            }
          }
        }
      }
    }
  }

  val otherDatasetCount = editorUiState.datasets.count { it.key != primaryLayer?.key }
  if (otherDatasetCount > 0) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Outlined.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text =
          "This survey also has $otherDatasetCount more " +
            "${if (otherDatasetCount == 1) "map layer or data table" else "map layers and data tables"}" +
            ", linked to its other forms. You can review them in the next step.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun seedPreviewFeaturesForFreeformLayer(
  dataset: EntityDataset,
  surveyArea: SurveyArea?,
): EntityDataset {
  val areaPoints = surveyArea?.parts?.flatten().orEmpty()
  val centerLat: Double
  val centerLng: Double
  val latSpan: Double
  val lngSpan: Double
  if (areaPoints.isNotEmpty()) {
    val minLat = areaPoints.minOf { it.lat }
    val maxLat = areaPoints.maxOf { it.lat }
    val minLng = areaPoints.minOf { it.lng }
    val maxLng = areaPoints.maxOf { it.lng }
    centerLat = (minLat + maxLat) / 2.0
    centerLng = (minLng + maxLng) / 2.0
    latSpan = ((maxLat - minLat).coerceAtLeast(0.008)) * 0.22
    lngSpan = ((maxLng - minLng).coerceAtLeast(0.008)) * 0.22
  } else {
    centerLat = -0.4167
    centerLng = 36.95
    latSpan = 0.012
    lngSpan = 0.012
  }
  val offsets = listOf(-0.6 to -0.45, 0.08 to 0.45, 0.55 to -0.2)
  val previewRows = offsets.mapIndexed { idx, (dLatFactor, dLngFactor) ->
    val lat = centerLat + latSpan * dLatFactor
    val lng = centerLng + lngSpan * dLngFactor
    val rLat = latSpan * 0.28
    val rLng = lngSpan * 0.28
    val geometry =
      when (dataset.geometryKind) {
        GeometryKind.POINT -> listOf(LatLng(lat, lng))
        GeometryKind.LINE ->
          listOf(
            LatLng(lat - rLat * 0.7, lng - rLng * 0.8),
            LatLng(lat + rLat * 0.2, lng),
            LatLng(lat - rLat * 0.3, lng + rLng * 0.85),
          )
        GeometryKind.POLYGON ->
          listOf(
            LatLng(lat - rLat, lng - rLng),
            LatLng(lat + rLat, lng - rLng * 0.85),
            LatLng(lat + rLat * 0.9, lng + rLng),
            LatLng(lat - rLat * 0.85, lng + rLng * 0.9),
          )
      }
    EntityRow(
      key = "${dataset.key}-example-${idx + 1}",
      values =
        mapOf(
          dataset.keyProperty to "example_${idx + 1}",
          dataset.labelProperty to "Example ${idx + 1}",
        ),
      geometry = geometry,
    )
  }
  return dataset.copy(
    key = "${dataset.key}-preview-${dataset.geometryKind.name}",
    rows = previewRows,
  )
}

// ---------------------------------------------------------------------------------------------
// Step 5: Forms
// ---------------------------------------------------------------------------------------------

@Composable
private fun FormQuestionsStepContent(
  uiState: CreateSurveyWizardUiState,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  isDarkTheme: Boolean,
) {
  val forms = editorUiState.forms
  val primaryForm = uiState.primaryForm ?: forms.firstOrNull()
  val section = editorUiState.section
  val selectedDataset =
    (section as? SurveyEditorSection.Dataset)?.let { s ->
      editorUiState.datasets.firstOrNull { it.key == s.key }
    }
  val selectedForm =
    if (selectedDataset != null) {
      null
    } else {
      (section as? SurveyEditorSection.Form)?.let { s -> forms.firstOrNull { it.key == s.key } }
        ?: primaryForm
    }
  // Progressive disclosure: the outline panel is collapsed by default and can be toggled open
  // when the survey has several Forms or datasets.
  val hasSeveralParts = forms.size > 1 || editorUiState.datasets.size > 1
  var showOutline by remember { mutableStateOf(false) }
  var showAddForm by remember { mutableStateOf(false) }
  val choiceDatasetCreation = rememberChoiceDatasetCreationState()
  ChoiceDatasetCreationDialogs(choiceDatasetCreation, editorUiState, editorActions)
  if (showAddForm) {
    AddFormDialog(
      actions = editorActions,
      onDismiss = { showAddForm = false },
      library = editorUiState.library,
    )
  }

  Row(modifier = Modifier.fillMaxSize()) {
    if (showOutline) {
      WizardSurveyOutline(
        editorUiState = editorUiState,
        selectedFormKey = selectedForm?.key,
        selectedDatasetKey = selectedDataset?.key,
        onSelect = editorActions::select,
        onAddForm = { showAddForm = true },
        onCollapse = { showOutline = false },
        modifier = Modifier.width(264.dp).fillMaxHeight(),
      )
      VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
      if (!showOutline) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (hasSeveralParts) {
            TextButton(onClick = { showOutline = true }) {
              Icon(
                Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text("Show all forms & layers")
            }
          } else {
            TextButton(onClick = { showAddForm = true }) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Add another form")
            }
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
          selectedDataset != null ->
            key(selectedDataset.key) {
              EntityDatasetEditor(editorUiState, editorActions, selectedDataset)
            }
          selectedForm != null ->
            key(selectedForm.key) {
              val formEditor =
                remember(selectedForm.key) { editorActions.formEditor(selectedForm.key) }
              val formState by formEditor.uiState.collectAsState()
              FormEditorPage(
                uiState = formState,
                actions = formEditor,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.fillMaxSize(),
                onCreateDataset = { editorActions.createDatasetForForm(selectedForm.key) },
                // The primary Form is wired to the primary Map layer, so only others can be
                // deleted.
                onDelete =
                  if (selectedForm.key != primaryForm?.key) {
                    { editorActions.deleteForm(selectedForm.key) }
                  } else {
                    null
                  },
                onSaveToModeChange = { editorActions.setFormSaveToMode(selectedForm.key, it) },
                onOpenDataset = { id ->
                  editorUiState.datasets
                    .firstOrNull { it.id == id }
                    ?.let { editorActions.select(SurveyEditorSection.Dataset(it.key)) }
                },
                onOpenForm = { formKey -> editorActions.select(SurveyEditorSection.Form(formKey)) },
                onCreateChoiceDataset = { kind, questionKey ->
                  choiceDatasetCreation.request(selectedForm.key, questionKey, kind)
                },
              )
            }
          else ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Button(onClick = { editorActions.addForm() }) { Text("Add form") }
            }
        }
      }
    }
  }
}

/**
 * Collapsible outline of the survey's Forms, Map layers, and Data tables for the Forms step, shown
 * when the survey has more than one Form or dataset. Map layer and Data table groups only appear
 * when they have entries, keeping the outline short.
 */
@Composable
private fun WizardSurveyOutline(
  editorUiState: SurveyEditorUiState,
  selectedFormKey: String?,
  selectedDatasetKey: String?,
  onSelect: (SurveyEditorSection) -> Unit,
  onAddForm: () -> Unit,
  onCollapse: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Forms",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAddForm) {
          Icon(Icons.Outlined.Add, contentDescription = "Add form")
        }
        IconButton(onClick = onCollapse) {
          Icon(Icons.Outlined.ChevronLeft, contentDescription = "Hide forms & layers")
        }
      }
      editorUiState.forms.forEach { entry ->
        NavItem(
          label = entry.form.title.ifBlank { "Untitled form" },
          icon = Icons.Outlined.Description,
          selected = entry.key == selectedFormKey,
          onClick = { onSelect(SurveyEditorSection.Form(entry.key)) },
          trailing = "${entry.form.questions.size}",
          hasIssues = editorUiState.formIssues(entry).isNotEmpty(),
        )
      }
      OutlineDatasetGroup(
        title = DatasetKind.MAP_LAYER.plural,
        datasets = editorUiState.mapLayers,
        icon = Icons.Outlined.Layers,
        editorUiState = editorUiState,
        selectedDatasetKey = selectedDatasetKey,
        onSelect = onSelect,
      )
      OutlineDatasetGroup(
        title = DatasetKind.DATA_TABLE.plural,
        datasets = editorUiState.dataTables,
        icon = Icons.AutoMirrored.Outlined.List,
        editorUiState = editorUiState,
        selectedDatasetKey = selectedDatasetKey,
        onSelect = onSelect,
      )
    }
  }
}

@Composable
private fun OutlineDatasetGroup(
  title: String,
  datasets: List<EntityDataset>,
  icon: ImageVector,
  editorUiState: SurveyEditorUiState,
  selectedDatasetKey: String?,
  onSelect: (SurveyEditorSection) -> Unit,
) {
  if (datasets.isEmpty()) return
  HorizontalDivider(
    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
  )
  datasets.forEach { dataset ->
    NavItem(
      label = dataset.displayName.ifBlank { "Untitled" },
      icon = icon,
      selected = dataset.key == selectedDatasetKey,
      onClick = { onSelect(SurveyEditorSection.Dataset(dataset.key)) },
      hasIssues = editorUiState.datasetIssues(dataset).isNotEmpty(),
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Step 6: Sharing & Impact Readiness
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharingStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
) {
  val readiness = uiState.impactReadiness

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Sharing",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        "Review your survey's impact and export readiness, configure data sovereignty defaults, and invite collaborators.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  uiState.importErrorMessage?.let { errorMsg ->
    ValidationErrorBanner(message = errorMsg)
  }

  // Draft Mode & How to Publish Notice
  Surface(
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(22.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Starts in Draft mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          GroundTonalBadge(text = "Draft", tone = GroundBadgeTone.WARNING)
        }
        Text(
          text =
            "Clicking Create survey opens the survey on the main map in Draft mode so you can inspect your study area and layers or open the Survey editor before data collectors see it. When you're ready to go live, open the Survey editor, click the Draft status badge next to the survey name, and select Publish survey.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }

  // Impact & Export Readiness Summary Card
  ElevatedCard(
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      )
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          Icons.Outlined.Verified,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Text(
          text = "Impact & export readiness",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text =
          "How this survey's schema connects to your organization's impact rollup, FAO/UN environmental indicators, and one-click export profiles.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        GroundTonalBadge(
          text = "Mode: ${uiState.collectionMode.label}",
          tone = GroundBadgeTone.PRIMARY,
        )
        GroundTonalBadge(
          text =
            "${readiness.linkedConceptCount}/${readiness.totalQuestionCount} standard fields linked",
          tone =
            if (readiness.linkedConceptCount > 0) GroundBadgeTone.PRIMARY
            else GroundBadgeTone.NEUTRAL,
        )
        if (readiness.selectedPackTitles.isNotEmpty()) {
          GroundTonalBadge(
            text = "Purposes: ${readiness.selectedPackTitles.joinToString(", ")}",
            tone = GroundBadgeTone.TERTIARY,
          )
        }
        if (readiness.selectedProgramLabels.isNotEmpty()) {
          GroundTonalBadge(
            text = "Programs: ${readiness.selectedProgramLabels.joinToString(", ")}",
            tone = GroundBadgeTone.NEUTRAL,
          )
        }
        if (readiness.goalTitles.isNotEmpty()) {
          GroundTonalBadge(
            text = "Impact goals: ${readiness.goalTitles.joinToString(", ")}",
            tone = GroundBadgeTone.PRIMARY,
          )
        }
      }

      if (readiness.exportPlans.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "One-click export profiles",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          readiness.exportPlans.forEach { plan ->
            val mapped = plan.fields.count { it.isMapped }
            val total = plan.fields.size
            val fraction = if (total > 0) mapped.toFloat() / total.toFloat() else 1f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = plan.profile.title.get("en"),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                )
                Text(
                  text =
                    if (plan.unmappedFields.isEmpty()) "$mapped/$total mapped (Ready)"
                    else "$mapped/$total mapped",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (plan.unmappedFields.isEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
              }
              LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
              )
            }
          }
        }
      }
    }
  }

  // CARE Principles & FAO Indigenous / Community Data Sovereignty Card
  val careCriteria =
    remember(
      editorUiState.sharing.policy,
      editorUiState.sharing.peerDataVisibility,
      uiState.collectorConsentRequired,
      uiState.collectorConsentText,
      readiness.linkedConceptCount,
      readiness.selectedPackTitles,
    ) {
      listOf(
        CareGovernanceCriterion(
          code = "C",
          title = "Collective Benefit",
          standard = "CARE & FAO Indigenous Data Policy",
          description =
            "Ecosystem and community indicators use shared library concepts for community-owned evidence.",
          isSatisfied =
            readiness.linkedConceptCount > 0 || readiness.selectedPackTitles.isNotEmpty(),
        ),
        CareGovernanceCriterion(
          code = "A",
          title = "Authority to Control",
          standard = "CARE & FAO VGGT Tenure Governance",
          description =
            "Access is Restricted so community stewards control who can view or collect survey records.",
          isSatisfied = editorUiState.sharing.policy == SharingPolicy.RESTRICTED,
        ),
        CareGovernanceCriterion(
          code = "R",
          title = "Responsibility",
          standard = "CARE Principles",
          description =
            "Peer visibility is limited to each contributor's own submissions plus survey organizers.",
          isSatisfied = editorUiState.sharing.peerDataVisibility == PeerDataVisibility.OWN_ONLY,
        ),
        CareGovernanceCriterion(
          code = "E",
          title = "Ethics & Aggregate Protection",
          standard = "CARE Principles",
          description =
            "Individual tenure and household records are never exposed in public platform rollups.",
          isSatisfied = true,
        ),
        CareGovernanceCriterion(
          code = "FPIC",
          title = "Free, Prior and Informed Consent",
          standard = "FAO FPIC Operational Guidelines",
          description =
            "Data collectors must review and accept explicit community consent terms before collecting data.",
          isSatisfied =
            uiState.collectorConsentRequired && uiState.collectorConsentText.isNotBlank(),
        ),
      )
    }

  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "CARE Principles & Indigenous / community data sovereignty",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (readiness.careSovereigntyApplied) {
                "Restricted access, 'Only their own submissions' peer visibility, and FAO Free, Prior and Informed Consent (FPIC) collector terms are active to protect community and tenure data."
              } else {
                "Working with Indigenous Peoples, local communities, or sensitive land-tenure data? Apply CARE & FAO FPIC sovereignty defaults (Restricted access + Collectors see only their own submissions + FPIC consent terms)."
              },
            style = MaterialTheme.typography.bodySmall,
          )
          if (readiness.excludeFromPlatformAggregates) {
            Text(
              text =
                "Note: ${uiState.selectedOrganization?.name} is excluded from platform-wide aggregate totals.",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
        if (readiness.careSovereigntyApplied) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "✓ CARE defaults applied",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        } else {
          OutlinedButton(onClick = actions::applyCareSovereigntyDefaults) {
            Text("Apply CARE defaults")
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        careCriteria.forEach { criterion ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color =
              if (criterion.isSatisfied) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
              } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
              },
            contentColor = MaterialTheme.colorScheme.onSurface,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector =
                  if (criterion.isSatisfied) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                contentDescription = null,
                tint =
                  if (criterion.isSatisfied) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Column {
                Text(
                  text = "${criterion.code} · ${criterion.title}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = criterion.standard,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }

  // Collector Consent / Data Governance Terms Card
  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          WizardSectionTitle("Data collector consent & governance terms")
          Text(
            text =
              "Require data collectors to review and accept custom Free, Prior and Informed Consent (FPIC) or project terms before opening the survey.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = uiState.collectorConsentRequired,
          onCheckedChange = actions::updateCollectorConsentRequired,
        )
      }
      if (uiState.collectorConsentRequired) {
        OutlinedTextField(
          value = uiState.collectorConsentText,
          onValueChange = actions::updateCollectorConsentText,
          label = { Text("Consent terms shown to data collectors") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  // Standard Collect One SharingContent (without PaneScaffold's nested verticalScroll)
  SharingContent(
    uiState = editorUiState,
    actions = editorActions,
    modifier = Modifier.fillMaxWidth(),
  )
}

// ---------------------------------------------------------------------------------------------
// Shared Wizard Card Component
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectableChoiceCard(
  title: String,
  description: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tags: List<String> = emptyList(),
  badgeText: String? = null,
  graphic: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedCard(
    onClick = onClick,
    modifier =
      modifier.fillMaxHeight().heightIn(min = 160.dp).semantics {
        role = Role.RadioButton
        this.selected = selected
      },
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) colors.secondary else colors.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (graphic != null) {
        graphic()
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (selected) colors.onSecondaryContainer else colors.primary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Selected",
            tint = colors.secondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
      )
      if (tags.isNotEmpty() || badgeText != null) {
        Spacer(Modifier.weight(1f))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          tags.forEach { tag ->
            GroundTonalBadge(
              text = tag,
              tone = GroundBadgeTone.PRIMARY,
            )
          }
          if (badgeText != null) {
            GroundTonalBadge(
              text = badgeText,
              tone = GroundBadgeTone.NEUTRAL,
            )
          }
        }
      }
    }
  }
}
