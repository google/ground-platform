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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_customize_limits
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_forms
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_hide_limits
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_acl_collaborators
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_area_vertices
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_forms
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_photo_questions_per_form
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_predefined_entities
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_questions_per_form
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_max_sample_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_predefined
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_questions
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_sample_plots
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_tier_basic
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_tier_inherit
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_tier_override
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_tier_sponsored
import org.openforis.collectone.v2.web.resources.surveyeditor_details_cost_title

/** Configures survey-level quota overrides, with live readout of resource counts. */
@Composable
internal fun CostAndQuotaSection(uiState: SurveyEditorUiState, actions: SurveyDetailsActions) {
  val details = uiState.details
  val limits = details.quotaLimits
  var expanded by remember { mutableStateOf(false) }

  val predefinedEntitiesCount = uiState.datasets.filter { !it.isGenerated }.sumOf { it.rows.size }
  val generatedEntitiesCount = uiState.datasets.filter { it.isGenerated }.sumOf { it.rows.size }
  val formCount = uiState.forms.size
  val questionCount = uiState.forms.sumOf { it.form.questions.size }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_details_cost_title),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          listOf(
              pluralStringResource(
                Res.plurals.surveyeditor_details_cost_forms,
                formCount,
                formCount,
              ),
              pluralStringResource(
                Res.plurals.surveyeditor_details_cost_questions,
                questionCount,
                questionCount,
              ),
              pluralStringResource(
                Res.plurals.surveyeditor_details_cost_predefined,
                predefinedEntitiesCount,
                predefinedEntitiesCount,
              ),
              pluralStringResource(
                Res.plurals.surveyeditor_details_cost_sample_plots,
                generatedEntitiesCount,
                generatedEntitiesCount,
              ),
            )
            .joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            stringResource(Res.string.surveyeditor_details_cost_tier_override),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          FlatFilterChip(
            selected = limits.tier == QuotaTier.UNSPECIFIED,
            onClick = { actions.updateQuotaLimits { it.copy(tier = QuotaTier.UNSPECIFIED) } },
            enabled = !uiState.isReadOnly,
            label = { Text(stringResource(Res.string.surveyeditor_details_cost_tier_inherit)) },
          )
          FlatFilterChip(
            selected = limits.tier == QuotaTier.BASIC,
            onClick = { actions.updateQuotaLimits { QuotaLimitsConfig.BASIC } },
            enabled = !uiState.isReadOnly,
            label = { Text(stringResource(Res.string.surveyeditor_details_cost_tier_basic)) },
          )
          FlatFilterChip(
            selected = limits.tier == QuotaTier.SPONSORED,
            onClick = { actions.updateQuotaLimits { QuotaLimitsConfig.SPONSORED } },
            enabled = !uiState.isReadOnly,
            label = { Text(stringResource(Res.string.surveyeditor_details_cost_tier_sponsored)) },
          )
        }

        TextButton(onClick = { expanded = !expanded }) {
          Text(
            if (expanded) stringResource(Res.string.surveyeditor_details_cost_hide_limits)
            else stringResource(Res.string.surveyeditor_details_cost_customize_limits)
          )
        }

        if (expanded) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              OutlinedTextField(
                value = limits.maxFormsPerSurvey.toString(),
                onValueChange = { str ->
                  actions.updateQuotaLimits { it.copy(maxFormsPerSurvey = str.toIntOrNull() ?: 0) }
                },
                enabled = !uiState.isReadOnly,
                label = { Text(stringResource(Res.string.surveyeditor_details_cost_max_forms)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = limits.maxQuestionsPerForm.toString(),
                onValueChange = { str ->
                  actions.updateQuotaLimits {
                    it.copy(maxQuestionsPerForm = str.toIntOrNull() ?: 0)
                  }
                },
                enabled = !uiState.isReadOnly,
                label = {
                  Text(stringResource(Res.string.surveyeditor_details_cost_max_questions_per_form))
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              OutlinedTextField(
                value = limits.maxPhotoQuestionsPerForm.toString(),
                onValueChange = { str ->
                  actions.updateQuotaLimits {
                    it.copy(maxPhotoQuestionsPerForm = str.toIntOrNull() ?: 0)
                  }
                },
                enabled = !uiState.isReadOnly,
                label = {
                  Text(
                    stringResource(
                      Res.string.surveyeditor_details_cost_max_photo_questions_per_form
                    )
                  )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = limits.maxPredefinedEntities.toString(),
                onValueChange = { str ->
                  actions.updateQuotaLimits {
                    it.copy(maxPredefinedEntities = str.toIntOrNull() ?: 0)
                  }
                },
                enabled = !uiState.isReadOnly,
                label = {
                  Text(stringResource(Res.string.surveyeditor_details_cost_max_predefined_entities))
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              OutlinedTextField(
                value = limits.maxGeneratedEntities.toString(),
                onValueChange = { str ->
                  actions.updateQuotaLimits {
                    it.copy(maxGeneratedEntities = str.toIntOrNull() ?: 0)
                  }
                },
                enabled = !uiState.isReadOnly,
                label = {
                  Text(stringResource(Res.string.surveyeditor_details_cost_max_sample_plots))
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = limits.maxAreaVertices.toString(),
                onValueChange = { str ->
                  actions.updateQuotaLimits { it.copy(maxAreaVertices = str.toIntOrNull() ?: 0) }
                },
                enabled = !uiState.isReadOnly,
                label = {
                  Text(stringResource(Res.string.surveyeditor_details_cost_max_area_vertices))
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            OutlinedTextField(
              value = limits.maxAclUsers.toString(),
              onValueChange = { str ->
                actions.updateQuotaLimits { it.copy(maxAclUsers = str.toIntOrNull() ?: 0) }
              },
              enabled = !uiState.isReadOnly,
              label = {
                Text(stringResource(Res.string.surveyeditor_details_cost_max_acl_collaborators))
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}
