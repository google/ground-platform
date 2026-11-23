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
package org.openforis.collectone.v2.web.ui.wizard.steps

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_copy_source_label
import org.openforis.collectone.v2.web.resources.wizard_copy_source_supporting
import org.openforis.collectone.v2.web.resources.wizard_description_label
import org.openforis.collectone.v2.web.resources.wizard_description_placeholder
import org.openforis.collectone.v2.web.resources.wizard_import_cep_archive_label
import org.openforis.collectone.v2.web.resources.wizard_import_choose_another_file
import org.openforis.collectone.v2.web.resources.wizard_import_choose_file
import org.openforis.collectone.v2.web.resources.wizard_import_clear_file
import org.openforis.collectone.v2.web.resources.wizard_import_parsed
import org.openforis.collectone.v2.web.resources.wizard_import_parsed_with_plots
import org.openforis.collectone.v2.web.resources.wizard_import_questions
import org.openforis.collectone.v2.web.resources.wizard_import_sample_plots
import org.openforis.collectone.v2.web.resources.wizard_import_supported_formats
import org.openforis.collectone.v2.web.resources.wizard_no_organization
import org.openforis.collectone.v2.web.resources.wizard_no_surveys_to_copy
import org.openforis.collectone.v2.web.resources.wizard_organization
import org.openforis.collectone.v2.web.resources.wizard_organization_supporting
import org.openforis.collectone.v2.web.resources.wizard_overview_subtitle
import org.openforis.collectone.v2.web.resources.wizard_start_method_title
import org.openforis.collectone.v2.web.resources.wizard_step_overview
import org.openforis.collectone.v2.web.resources.wizard_survey_identity
import org.openforis.collectone.v2.web.resources.wizard_survey_title_label
import org.openforis.collectone.v2.web.resources.wizard_survey_title_placeholder
import org.openforis.collectone.v2.web.resources.wizard_validation_title_required
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.ValidationErrorBanner
import org.openforis.collectone.v2.web.ui.common.WizardOverviewHeroGraphic
import org.openforis.collectone.v2.web.ui.surveyeditor.LanguageSelectorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorActions
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.text.descriptionRes
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardActions
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

private const val WIZARD_IMPORT_FILE_ACCEPT = ".xml,.xhtml,.cep,.zip,.ced"

/**
 * Sentinel for the "None (personal survey)" option in the Organization selector; its name is
 * `wizard_no_organization`.
 */
private val NoOrganizationOption = Organization(id = "", name = "")

// ---------------------------------------------------------------------------------------------
// Step 1: Overview
// ---------------------------------------------------------------------------------------------

@Composable
internal fun OverviewStepContent(
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
      text = stringResource(Res.string.wizard_step_overview),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(Res.string.wizard_overview_subtitle),
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
      WizardSectionTitle(stringResource(Res.string.wizard_survey_identity))
      if (showTitleError) {
        ValidationErrorBanner(message = stringResource(Res.string.wizard_validation_title_required))
      }
      OutlinedTextField(
        value = uiState.title,
        onValueChange = actions::updateTitle,
        label = { Text(stringResource(Res.string.wizard_survey_title_label)) },
        placeholder = { Text(stringResource(Res.string.wizard_survey_title_placeholder)) },
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
        label = { Text(stringResource(Res.string.wizard_description_label)) },
        placeholder = { Text(stringResource(Res.string.wizard_description_placeholder)) },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )

      // Organizers who aren't in any organization can only create personal surveys, so there's
      // nothing to choose.
      if (uiState.memberOrganizations.isNotEmpty() || uiState.selectedOrganization != null) {
        val orgOptions =
          remember(uiState.memberOrganizations, uiState.selectedOrganization) {
            buildList {
              add(NoOrganizationOption)
              addAll(uiState.memberOrganizations)
              val sel = uiState.selectedOrganization
              if (sel != null && none { it.id == sel.id }) add(sel)
            }
          }
        val noOrganization = stringResource(Res.string.wizard_no_organization)
        DropdownSelector(
          label = stringResource(Res.string.wizard_organization),
          selectedText = uiState.selectedOrganization?.name ?: noOrganization,
          options = orgOptions,
          optionText = { if (it.id.isEmpty()) noOrganization else it.name },
          onSelect = { picked -> actions.selectOrganization(picked.id.ifBlank { null }) },
        )
        Text(
          text = stringResource(Res.string.wizard_organization_supporting),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
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
      WizardSectionTitle(stringResource(Res.string.wizard_start_method_title))
      if (showStartMethodError) {
        uiState.currentStepValidationMessage?.let { msg ->
          ValidationErrorBanner(message = stringResource(msg))
        }
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
            title = stringResource(method.labelRes),
            description = stringResource(method.descriptionRes),
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
              text = stringResource(Res.string.wizard_no_surveys_to_copy),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          } else {
            val selectedSurvey =
              uiState.existingSurveys.firstOrNull { it.id == uiState.copySourceSurveyId }
                ?: uiState.existingSurveys.first()
            DropdownSelector(
              label = stringResource(Res.string.wizard_copy_source_label),
              selectedText = "${selectedSurvey.title} (${selectedSurvey.location})",
              options = uiState.existingSurveys,
              optionText = { "${it.title} (${it.location})" },
              onSelect = { actions.selectCopySourceSurvey(it.id) },
            )
            Text(
              text = stringResource(Res.string.wizard_copy_source_supporting),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        WizardStartMethod.IMPORT_XFORMS -> {
          Text(
            text = stringResource(Res.string.wizard_import_supported_formats),
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
                stringResource(
                  if (uiState.importedXForms != null) Res.string.wizard_import_choose_another_file
                  else Res.string.wizard_import_choose_file
                )
              )
            }
            uiState.importedXForms?.let { imported ->
              val plotCount = uiState.selectedCepProject?.plotDataset?.rows?.size ?: 0
              val questionCount = imported.form.questions.size
              val questions =
                pluralStringResource(
                  Res.plurals.wizard_import_questions,
                  questionCount,
                  questionCount,
                )
              val parsedSummary =
                if (plotCount > 0) {
                  stringResource(
                    Res.string.wizard_import_parsed_with_plots,
                    questions,
                    pluralStringResource(
                      Res.plurals.wizard_import_sample_plots,
                      plotCount,
                      plotCount,
                    ),
                  )
                } else {
                  stringResource(Res.string.wizard_import_parsed, questions)
                }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${uiState.importedXFormsFileName}: ${imported.form.title}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = parsedSummary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              IconButton(onClick = actions::clearImportedXForms) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = stringResource(Res.string.wizard_import_clear_file),
                )
              }
            }
          }
          if (uiState.importedCepProjects.size > 1) {
            val selectedCep = uiState.selectedCepProject ?: uiState.importedCepProjects.first()
            DropdownSelector(
              label =
                stringResource(
                  Res.string.wizard_import_cep_archive_label,
                  uiState.importedCepProjects.size,
                ),
              selectedText = "${selectedCep.projectTitle} (${selectedCep.fileName})",
              options = uiState.importedCepProjects,
              optionText = { "${it.projectTitle} (${it.fileName})" },
              onSelect = { actions.selectImportedCepProject(it.fileName) },
            )
          }
          uiState.importErrorMessage?.let { errorMsg ->
            ValidationErrorBanner(message = errorMsg.resolve())
          }
        }
      }
    }
  }
}
