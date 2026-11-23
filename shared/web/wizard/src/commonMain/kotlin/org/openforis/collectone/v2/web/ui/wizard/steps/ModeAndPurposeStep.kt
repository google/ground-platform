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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_purpose_question
import org.openforis.collectone.v2.web.resources.wizard_purpose_question_supporting
import org.openforis.collectone.v2.web.resources.wizard_purpose_subtitle
import org.openforis.collectone.v2.web.resources.wizard_starter_template_supporting
import org.openforis.collectone.v2.web.resources.wizard_starter_template_title
import org.openforis.collectone.v2.web.resources.wizard_step_purpose
import org.openforis.collectone.v2.web.ui.common.ProgramPickerSection
import org.openforis.collectone.v2.web.ui.common.PurposePackGrid
import org.openforis.collectone.v2.web.ui.common.StarterTemplatePicker
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardActions
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

// ---------------------------------------------------------------------------------------------
// Step 2: Purpose
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ModeAndPurposeStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  scrollState: ScrollState,
  isLibraryLoading: Boolean = false,
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
      text = stringResource(Res.string.wizard_step_purpose),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(Res.string.wizard_purpose_subtitle),
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
      val language = editorUiState.details.defaultLanguage.ifBlank { "en" }
      // All purposes (organization and global) in one alphabetical list; organization purposes
      // carry a badge with the organization's name.
      val packs =
        remember(uiState.sortedPurposePacks, language) {
          uiState.sortedPurposePacks.sortedBy { it.title.get(language).lowercase() }
        }

      WizardSectionTitle(stringResource(Res.string.wizard_purpose_question))
      Text(
        text = stringResource(Res.string.wizard_purpose_question_supporting),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      PurposePackGrid(
        packs = packs,
        library = uiState.library,
        organizationName = uiState.selectedOrganization?.name,
        language = language,
        selectedIds = editorUiState.details.purposeIds,
        onToggle = actions::togglePurposePack,
        otherPurposeText = editorUiState.details.otherPurpose,
        onOtherPurposeTextChange = actions::updateOtherPurpose,
        isError = uiState.isPurposeInvalid,
        onOtherInputPositioned = { otherInputY = it },
        isLoading = isLibraryLoading,
      )

      uiState.selectedPurposePack?.let { pack ->
        Spacer(Modifier.height(4.dp))
        WizardSectionTitle(stringResource(Res.string.wizard_starter_template_title))
        Text(
          text = stringResource(Res.string.wizard_starter_template_supporting),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StarterTemplatePicker(
          pack = pack,
          library = uiState.library,
          language = language,
          selectedId = uiState.starterTemplateId,
          onSelect = actions::selectStarterTemplate,
        )
      }
      if (uiState.offeredProgramIds.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        ProgramPickerSection(
          programIds = uiState.offeredProgramIds,
          selectedIds = editorUiState.details.programIds,
          onToggle = actions::toggleProgram,
        )
      }
    }
  }
}
