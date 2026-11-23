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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_draft_badge
import org.openforis.collectone.v2.web.resources.wizard_draft_mode_body
import org.openforis.collectone.v2.web.resources.wizard_draft_mode_title
import org.openforis.collectone.v2.web.resources.wizard_readiness_export_profiles
import org.openforis.collectone.v2.web.resources.wizard_readiness_fields_linked
import org.openforis.collectone.v2.web.resources.wizard_readiness_goals
import org.openforis.collectone.v2.web.resources.wizard_readiness_mapped
import org.openforis.collectone.v2.web.resources.wizard_readiness_mapped_ready
import org.openforis.collectone.v2.web.resources.wizard_readiness_mode
import org.openforis.collectone.v2.web.resources.wizard_readiness_programs
import org.openforis.collectone.v2.web.resources.wizard_readiness_purposes
import org.openforis.collectone.v2.web.resources.wizard_readiness_supporting
import org.openforis.collectone.v2.web.resources.wizard_readiness_title
import org.openforis.collectone.v2.web.resources.wizard_sharing_subtitle
import org.openforis.collectone.v2.web.resources.wizard_step_sharing
import org.openforis.collectone.v2.web.ui.common.ValidationErrorBanner
import org.openforis.collectone.v2.web.ui.surveyeditor.SharingContent
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorActions
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareCardPosition
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.text.resolve
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardActions
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

// ---------------------------------------------------------------------------------------------
// Step 6: Sharing & Impact Readiness
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SharingStepContent(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  editorActions: SurveyEditorActions,
  careCardPosition: CareCardPosition,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = stringResource(Res.string.wizard_step_sharing),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(Res.string.wizard_sharing_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  uiState.importErrorMessage?.let { errorMsg ->
    ValidationErrorBanner(message = errorMsg.resolve())
  }

  DraftModeNoticeBanner()

  ImpactReadinessSummaryCard(uiState = uiState)

  // CARE Principles & FAO Indigenous / Community Data Sovereignty Card
  WizardCareCard(
    uiState = uiState,
    actions = actions,
    editorUiState = editorUiState,
    position = careCardPosition,
  )

  // Standard Collect One SharingContent (without PaneScaffold's nested verticalScroll)
  SharingContent(
    uiState = editorUiState,
    actions = editorActions,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun DraftModeNoticeBanner() {
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
            text = stringResource(Res.string.wizard_draft_mode_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          TonalBadge(
            text = stringResource(Res.string.wizard_draft_badge),
            tone = BadgeTone.WARNING,
          )
        }
        Text(
          text = stringResource(Res.string.wizard_draft_mode_body),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImpactReadinessSummaryCard(uiState: CreateSurveyWizardUiState) {
  val readiness = uiState.impactReadiness
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
          text = stringResource(Res.string.wizard_readiness_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = stringResource(Res.string.wizard_readiness_supporting),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ImpactReadinessBadgesRow(uiState = uiState)
      if (readiness.exportPlans.isNotEmpty()) {
        ImpactReadinessExportPlansSection(uiState = uiState)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImpactReadinessBadgesRow(uiState: CreateSurveyWizardUiState) {
  val readiness = uiState.impactReadiness
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TonalBadge(
      text =
        stringResource(
          Res.string.wizard_readiness_mode,
          stringResource(uiState.collectionMode.labelRes),
        ),
      tone = BadgeTone.PRIMARY,
    )
    TonalBadge(
      text =
        stringResource(
          Res.string.wizard_readiness_fields_linked,
          readiness.linkedConceptCount,
          readiness.totalQuestionCount,
        ),
      tone = if (readiness.linkedConceptCount > 0) BadgeTone.PRIMARY else BadgeTone.NEUTRAL,
    )
    if (readiness.selectedPackTitles.isNotEmpty()) {
      TonalBadge(
        text =
          stringResource(
            Res.string.wizard_readiness_purposes,
            readiness.selectedPackTitles.map { it.resolve() }.joinToString(", "),
          ),
        tone = BadgeTone.TERTIARY,
      )
    }
    if (readiness.selectedProgramLabels.isNotEmpty()) {
      TonalBadge(
        text =
          stringResource(
            Res.string.wizard_readiness_programs,
            readiness.selectedProgramLabels.joinToString(", "),
          ),
        tone = BadgeTone.NEUTRAL,
      )
    }
    if (readiness.goalTitles.isNotEmpty()) {
      TonalBadge(
        text =
          stringResource(
            Res.string.wizard_readiness_goals,
            readiness.goalTitles.map { it.resolve() }.joinToString(", "),
          ),
        tone = BadgeTone.PRIMARY,
      )
    }
  }
}

@Composable
private fun ImpactReadinessExportPlansSection(uiState: CreateSurveyWizardUiState) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = stringResource(Res.string.wizard_readiness_export_profiles),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
    uiState.impactReadiness.exportPlans.forEach { plan ->
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
              stringResource(
                if (plan.unmappedFields.isEmpty()) Res.string.wizard_readiness_mapped_ready
                else Res.string.wizard_readiness_mapped,
                mapped,
                total,
              ),
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
