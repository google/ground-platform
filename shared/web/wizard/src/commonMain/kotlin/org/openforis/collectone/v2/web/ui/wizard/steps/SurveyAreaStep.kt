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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_imagery_added_by
import org.openforis.collectone.v2.web.resources.wizard_imagery_built_in
import org.openforis.collectone.v2.web.resources.wizard_imagery_default_only
import org.openforis.collectone.v2.web.resources.wizard_imagery_offline_available
import org.openforis.collectone.v2.web.resources.wizard_imagery_online_only
import org.openforis.collectone.v2.web.resources.wizard_imagery_source_all_users
import org.openforis.collectone.v2.web.resources.wizard_imagery_supporting_field
import org.openforis.collectone.v2.web.resources.wizard_imagery_supporting_visual
import org.openforis.collectone.v2.web.resources.wizard_imagery_title
import org.openforis.collectone.v2.web.resources.wizard_purpose_source_organization
import org.openforis.collectone.v2.web.resources.wizard_step_survey_area
import org.openforis.collectone.v2.web.resources.wizard_survey_area_subtitle_field
import org.openforis.collectone.v2.web.resources.wizard_survey_area_subtitle_visual
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyAreaSection
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorActions
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

// ---------------------------------------------------------------------------------------------
// Step 3: Survey area
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SurveyAreaStepContent(
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
      text = stringResource(Res.string.wizard_step_survey_area),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        stringResource(
          if (uiState.collectionMode == SurveyCollectionMode.FIELD_SURVEY) {
            Res.string.wizard_survey_area_subtitle_field
          } else {
            Res.string.wizard_survey_area_subtitle_visual
          }
        ),
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
      WizardSectionTitle(stringResource(Res.string.wizard_imagery_title))
      Text(
        text =
          stringResource(
            if (uiState.collectionMode == SurveyCollectionMode.FIELD_SURVEY) {
              Res.string.wizard_imagery_supporting_field
            } else {
              Res.string.wizard_imagery_supporting_visual
            }
          ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (allSources.isEmpty()) {
        Text(
          text = stringResource(Res.string.wizard_imagery_default_only),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        allSources.forEach { source ->
          val isOrgSource = orgImagery.any { it.id == source.id }
          ImagerySourceToggleRow(
            source = source,
            sourceBadge =
              if (isOrgSource) {
                uiState.selectedOrganization?.name
                  ?: stringResource(Res.string.wizard_purpose_source_organization)
              } else {
                stringResource(Res.string.wizard_imagery_source_all_users)
              },
            isBuiltIn = !isOrgSource,
            enabled = mapUiState.isImagerySourceEnabled(source.id),
            onToggle = { onToggleImagerySource(source.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun ImagerySourceToggleRow(
  source: ImagerySource,
  sourceBadge: String,
  /** Whether [source] is built-in platform imagery (available to all users). */
  isBuiltIn: Boolean,
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
          TonalBadge(
            text = sourceBadge,
            tone = if (isBuiltIn) BadgeTone.NEUTRAL else BadgeTone.TERTIARY,
          )
        }
        val addedBy =
          if (isBuiltIn) stringResource(Res.string.wizard_imagery_built_in)
          else stringResource(Res.string.wizard_imagery_added_by, sourceBadge)
        val offlineNote =
          stringResource(
            if (source.allowOfflineDownload) Res.string.wizard_imagery_offline_available
            else Res.string.wizard_imagery_online_only
          )
        Text(
          text = "$addedBy · $offlineNote",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
