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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import org.openforis.collectone.v2.app.domain.model.CostTrackingCalculator
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_cost_budget_alerts
import org.openforis.collectone.v2.web.resources.org_cost_by_collector
import org.openforis.collectone.v2.web.resources.org_cost_by_organization
import org.openforis.collectone.v2.web.resources.org_cost_by_survey
import org.openforis.collectone.v2.web.resources.org_cost_controls_save
import org.openforis.collectone.v2.web.resources.org_cost_controls_saved
import org.openforis.collectone.v2.web.resources.org_cost_exceeded
import org.openforis.collectone.v2.web.resources.org_cost_photos
import org.openforis.collectone.v2.web.resources.org_cost_submissions
import org.openforis.collectone.v2.web.resources.org_cost_total_entities
import org.openforis.collectone.v2.web.resources.org_cost_tracked_surveys
import org.openforis.collectone.v2.web.resources.org_cost_within
import org.openforis.collectone.v2.web.resources.org_count_active_in_surveys
import org.openforis.collectone.v2.web.resources.org_count_entities
import org.openforis.collectone.v2.web.resources.org_count_generated
import org.openforis.collectone.v2.web.resources.org_count_photos
import org.openforis.collectone.v2.web.resources.org_count_predefined
import org.openforis.collectone.v2.web.resources.org_count_submissions
import org.openforis.collectone.v2.web.resources.org_count_surveys
import org.openforis.collectone.v2.web.resources.org_limit_acl_collaborators
import org.openforis.collectone.v2.web.resources.org_limit_active_surveys_per_user
import org.openforis.collectone.v2.web.resources.org_limit_adhoc_features_per_user
import org.openforis.collectone.v2.web.resources.org_limit_boundary_vertices
import org.openforis.collectone.v2.web.resources.org_limit_forms_per_survey
import org.openforis.collectone.v2.web.resources.org_limit_photo_questions_per_form
import org.openforis.collectone.v2.web.resources.org_limit_predefined_entities
import org.openforis.collectone.v2.web.resources.org_limit_questions_per_form
import org.openforis.collectone.v2.web.resources.org_limit_sample_plots
import org.openforis.collectone.v2.web.resources.org_limit_submissions_per_feature
import org.openforis.collectone.v2.web.resources.org_limit_surveys_per_owner
import org.openforis.collectone.v2.web.resources.org_limits_collector_title
import org.openforis.collectone.v2.web.resources.org_limits_survey_title
import org.openforis.collectone.v2.web.resources.org_quota_tier_basic
import org.openforis.collectone.v2.web.resources.org_quota_tier_body
import org.openforis.collectone.v2.web.resources.org_quota_tier_inherit
import org.openforis.collectone.v2.web.resources.org_quota_tier_sponsored
import org.openforis.collectone.v2.web.resources.org_quota_tier_title
import org.openforis.collectone.v2.web.ui.common.SectionLabel

// ---------------------------------------------------------------------------------------------
// Cost Controls & Quotas
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CostControlsPane(
  organization: Organization,
  isManager: Boolean,
  actions: OrganizationActions,
) {
  var limits by
    remember(organization.id, organization.quotaLimits) {
      mutableStateOf(organization.quotaLimits)
    }
  var isSaved by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth).padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SectionLabel(stringResource(Res.string.org_quota_tier_title))
        Text(
          stringResource(Res.string.org_quota_tier_body),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!organization.isSynthetic) {
            FlatFilterChip(
              selected = limits.tier == QuotaTier.UNSPECIFIED,
              onClick = {
                if (isManager) limits = limits.copy(tier = QuotaTier.UNSPECIFIED)
              },
              label = { Text(stringResource(Res.string.org_quota_tier_inherit)) },
            )
          }
          FlatFilterChip(
            selected = limits.tier == QuotaTier.BASIC,
            onClick = {
              if (isManager) {
                limits = QuotaLimitsConfig.BASIC
              }
            },
            label = { Text(stringResource(Res.string.org_quota_tier_basic)) },
          )
          FlatFilterChip(
            selected = limits.tier == QuotaTier.SPONSORED,
            onClick = {
              if (isManager) {
                limits = QuotaLimitsConfig.SPONSORED
              }
            },
            label = { Text(stringResource(Res.string.org_quota_tier_sponsored)) },
          )
        }
      }
    }

    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SectionLabel(stringResource(Res.string.org_limits_survey_title))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = limits.maxSurveysPerOwner.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxSurveysPerOwner = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_surveys_per_owner)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = limits.maxFormsPerSurvey.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxFormsPerSurvey = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_forms_per_survey)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = limits.maxQuestionsPerForm.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxQuestionsPerForm = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_questions_per_form)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = limits.maxPhotoQuestionsPerForm.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxPhotoQuestionsPerForm = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_photo_questions_per_form)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = limits.maxPredefinedEntities.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxPredefinedEntities = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_predefined_entities)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = limits.maxGeneratedEntities.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxGeneratedEntities = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_sample_plots)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = limits.maxAreaVertices.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxAreaVertices = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_boundary_vertices)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = limits.maxAclUsers.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxAclUsers = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_acl_collaborators)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SectionLabel(stringResource(Res.string.org_limits_collector_title))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = limits.maxSurveysPerUser.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxSurveysPerUser = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_active_surveys_per_user)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = limits.maxAdhocEntitiesPerUser.toString(),
            onValueChange = { str ->
              if (isManager) limits = limits.copy(maxAdhocEntitiesPerUser = str.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(Res.string.org_limit_adhoc_features_per_user)) },
            singleLine = true,
            enabled = isManager,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = limits.maxSubmissionsPerEntityPerUser.toString(),
          onValueChange = { str ->
            if (isManager) {
              limits = limits.copy(maxSubmissionsPerEntityPerUser = str.toIntOrNull() ?: 0)
            }
          },
          label = { Text(stringResource(Res.string.org_limit_submissions_per_feature)) },
          singleLine = true,
          enabled = isManager,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    if (isManager) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = {
            actions.updateOrganizationQuotaLimits(organization.id, limits)
            isSaved = true
          }
        ) {
          Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.org_cost_controls_save))
        }
        if (isSaved) {
          Text(
            stringResource(Res.string.org_cost_controls_saved),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Cost Tracking
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CostTrackingPane(uiState: OrganizationUiState, isLoading: Boolean = false) {
  if (isLoading && uiState.surveys.isEmpty()) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }
  val report =
    remember(uiState.surveys, uiState.organizations) {
      CostTrackingCalculator.calculatePlatformReport(
        organizations = uiState.organizations,
        surveys = uiState.surveys,
      )
    }
  val quotaViolations =
    remember(report) {
      report.surveys.flatMap { s -> s.violations.map { "${s.surveyTitle}: $it" } }
    }
  var selectedTab by remember { mutableStateOf(0) }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth).padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CostSummaryCard(
        label = stringResource(Res.string.org_cost_tracked_surveys),
        value = "${report.totalSurveysCount}",
      )
      CostSummaryCard(
        label = stringResource(Res.string.org_cost_total_entities),
        value = "${report.totalEntitiesCount}",
      )
      CostSummaryCard(
        label = stringResource(Res.string.org_cost_submissions),
        value = "${report.totalSubmissionsCount}",
      )
      CostSummaryCard(
        label = stringResource(Res.string.org_cost_photos),
        value = "${report.totalPhotosCount}",
      )
    }

    if (quotaViolations.isNotEmpty()) {
      ElevatedCard(
        colors =
          CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            stringResource(Res.string.org_cost_budget_alerts, quotaViolations.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
          quotaViolations.forEach { alert ->
            Text(
              "• $alert",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FlatFilterChip(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        label = {
          Text(stringResource(Res.string.org_cost_by_organization, report.organizations.size))
        },
      )
      FlatFilterChip(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        label = { Text(stringResource(Res.string.org_cost_by_survey, report.surveys.size)) },
      )
      FlatFilterChip(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        label = { Text(stringResource(Res.string.org_cost_by_collector, report.users.size)) },
      )
    }

    when (selectedTab) {
      0 -> {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          report.organizations.forEach { item ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    item.organizationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    costCountsText(
                      pluralStringResource(
                        Res.plurals.org_count_surveys,
                        item.surveyCount,
                        item.surveyCount,
                      ),
                      (item.predefinedEntityCount + item.generatedEntityCount).let {
                        pluralStringResource(Res.plurals.org_count_entities, it, it)
                      },
                      submissionCount = item.submissionCount,
                      photoCount = item.photoCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Column(
                  horizontalAlignment = Alignment.End,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    if (item.tier == QuotaTier.SPONSORED)
                      stringResource(Res.string.org_quota_tier_sponsored)
                    else stringResource(Res.string.org_quota_tier_basic),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (item.hasViolations) {
                    TonalBadge(
                      text = stringResource(Res.string.org_cost_exceeded),
                      tone = BadgeTone.ERROR,
                    )
                  } else {
                    TonalBadge(
                      text = stringResource(Res.string.org_cost_within),
                      tone = BadgeTone.NEUTRAL,
                    )
                  }
                }
              }
            }
          }
        }
      }
      1 -> {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          report.surveys.forEach { item ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    item.surveyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    costCountsText(
                      stringResource(Res.string.org_count_predefined, item.predefinedEntityCount),
                      stringResource(Res.string.org_count_generated, item.generatedEntityCount),
                      submissionCount = item.submissionCount,
                      photoCount = item.photoCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Column(
                  horizontalAlignment = Alignment.End,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    if (item.effectiveQuotaLimits.tier == QuotaTier.SPONSORED)
                      stringResource(Res.string.org_quota_tier_sponsored)
                    else stringResource(Res.string.org_quota_tier_basic),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (item.hasViolations) {
                    TonalBadge(
                      text = stringResource(Res.string.org_cost_exceeded),
                      tone = BadgeTone.ERROR,
                    )
                  } else {
                    TonalBadge(
                      text = stringResource(Res.string.org_cost_within),
                      tone = BadgeTone.NEUTRAL,
                    )
                  }
                }
              }
            }
          }
        }
      }
      2 -> {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          report.users.forEach { item ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    item.userEmail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    costCountsText(
                      pluralStringResource(
                        Res.plurals.org_count_active_in_surveys,
                        item.activeSurveyCount,
                        item.activeSurveyCount,
                      ),
                      submissionCount = item.submissionCount,
                      photoCount = item.photoCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (item.hasViolations) {
                  TonalBadge(
                    text = stringResource(Res.string.org_cost_exceeded),
                    tone = BadgeTone.ERROR,
                  )
                } else {
                  TonalBadge(
                    text = stringResource(Res.string.org_cost_within),
                    tone = BadgeTone.NEUTRAL,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * A cost-tracking row's counts, such as `2 surveys · 10 entities · 5 submissions · 3 photos`: the
 * [leading] counts, then the submission and photo counts.
 */
@Composable
private fun costCountsText(vararg leading: String, submissionCount: Int, photoCount: Int): String =
  (leading.toList() +
      pluralStringResource(Res.plurals.org_count_submissions, submissionCount, submissionCount) +
      pluralStringResource(Res.plurals.org_count_photos, photoCount, photoCount))
    .joinToString(" · ")

@Composable
private fun CostSummaryCard(label: String, value: String) {
  OutlinedCard(modifier = Modifier.width(180.dp)) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
