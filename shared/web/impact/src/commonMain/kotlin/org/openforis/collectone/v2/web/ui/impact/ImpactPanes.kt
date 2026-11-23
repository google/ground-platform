/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.impact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionLevel
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.domain.model.ImpactIndicator
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.domain.model.ImpactSummaryScope
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_impact_adding_up
import org.openforis.collectone.v2.web.resources.wizard_impact_all_countries
import org.openforis.collectone.v2.web.resources.wizard_impact_all_of_collect_one
import org.openforis.collectone.v2.web.resources.wizard_impact_all_surveys
import org.openforis.collectone.v2.web.resources.wizard_impact_by_country_and_map_area
import org.openforis.collectone.v2.web.resources.wizard_impact_by_country_and_map_area_supporting
import org.openforis.collectone.v2.web.resources.wizard_impact_closed_not_answered_yet
import org.openforis.collectone.v2.web.resources.wizard_impact_closed_surveys_not_answered
import org.openforis.collectone.v2.web.resources.wizard_impact_countries
import org.openforis.collectone.v2.web.resources.wizard_impact_detail_covering
import org.openforis.collectone.v2.web.resources.wizard_impact_download_pdf
import org.openforis.collectone.v2.web.resources.wizard_impact_duplicates_counted_once
import org.openforis.collectone.v2.web.resources.wizard_impact_fewer_than_10
import org.openforis.collectone.v2.web.resources.wizard_impact_filter_country
import org.openforis.collectone.v2.web.resources.wizard_impact_filter_survey
import org.openforis.collectone.v2.web.resources.wizard_impact_global_purpose_by_level
import org.openforis.collectone.v2.web.resources.wizard_impact_global_subtitle_full
import org.openforis.collectone.v2.web.resources.wizard_impact_global_subtitle_published
import org.openforis.collectone.v2.web.resources.wizard_impact_hide
import org.openforis.collectone.v2.web.resources.wizard_impact_hints_title
import org.openforis.collectone.v2.web.resources.wizard_impact_hints_title_empty
import org.openforis.collectone.v2.web.resources.wizard_impact_how_data_was_used
import org.openforis.collectone.v2.web.resources.wizard_impact_how_directly_contributed
import org.openforis.collectone.v2.web.resources.wizard_impact_level_of_five
import org.openforis.collectone.v2.web.resources.wizard_impact_linked_fields_not_counted
import org.openforis.collectone.v2.web.resources.wizard_impact_map_areas_kept_private
import org.openforis.collectone.v2.web.resources.wizard_impact_map_areas_with_numbers
import org.openforis.collectone.v2.web.resources.wizard_impact_map_features
import org.openforis.collectone.v2.web.resources.wizard_impact_map_features_in_private_areas
import org.openforis.collectone.v2.web.resources.wizard_impact_no_countries
import org.openforis.collectone.v2.web.resources.wizard_impact_no_global_purpose_surveys
import org.openforis.collectone.v2.web.resources.wizard_impact_no_numbers_for_this
import org.openforis.collectone.v2.web.resources.wizard_impact_no_plot_mapped_twice
import org.openforis.collectone.v2.web.resources.wizard_impact_no_surveys
import org.openforis.collectone.v2.web.resources.wizard_impact_not_enough_data_body
import org.openforis.collectone.v2.web.resources.wizard_impact_not_enough_data_title
import org.openforis.collectone.v2.web.resources.wizard_impact_not_used_yet
import org.openforis.collectone.v2.web.resources.wizard_impact_organization_subtitle
import org.openforis.collectone.v2.web.resources.wizard_impact_organizations_not_included
import org.openforis.collectone.v2.web.resources.wizard_impact_organizations_not_included_detail
import org.openforis.collectone.v2.web.resources.wizard_impact_outcome_yes
import org.openforis.collectone.v2.web.resources.wizard_impact_outcomes_empty
import org.openforis.collectone.v2.web.resources.wizard_impact_outcomes_empty_survey
import org.openforis.collectone.v2.web.resources.wizard_impact_own_indicators
import org.openforis.collectone.v2.web.resources.wizard_impact_own_indicators_supporting
import org.openforis.collectone.v2.web.resources.wizard_impact_plots_and_map_features
import org.openforis.collectone.v2.web.resources.wizard_impact_producers_registered
import org.openforis.collectone.v2.web.resources.wizard_impact_producers_registered_detail
import org.openforis.collectone.v2.web.resources.wizard_impact_suggested_indicators
import org.openforis.collectone.v2.web.resources.wizard_impact_suggested_indicators_supporting
import org.openforis.collectone.v2.web.resources.wizard_impact_survey_subtitle
import org.openforis.collectone.v2.web.resources.wizard_impact_surveys
import org.openforis.collectone.v2.web.resources.wizard_impact_title
import org.openforis.collectone.v2.web.resources.wizard_impact_unique_hectares_mapped
import org.openforis.collectone.v2.web.resources.wizard_impact_unique_hectares_mapped_detail
import org.openforis.collectone.v2.web.resources.wizard_impact_what_happened_with_data
import org.openforis.collectone.v2.web.resources.wizard_impact_why
import org.openforis.collectone.v2.web.resources.wizard_impact_your_indicators
import org.openforis.collectone.v2.web.resources.wizard_impact_your_indicators_supporting
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.text.PUBLICATION_MIN_FEATURES
import org.openforis.collectone.v2.web.ui.text.activityLabelRes
import org.openforis.collectone.v2.web.ui.text.explanationRes
import org.openforis.collectone.v2.web.ui.text.impactMeasurementText
import org.openforis.collectone.v2.web.ui.text.labelRes
import org.openforis.collectone.v2.web.ui.text.messageRes
import org.openforis.collectone.v2.web.ui.text.resolve

private val ImpactContentMaxWidth = 1040.dp
private val IndicatorCardWidth = 320.dp

/**
 * The survey Impact tab of the web dashboard: what the survey's data adds up to, grouped by goal,
 * how it was used, and a PDF summary download.
 */
@Composable
internal fun SurveyImpactPane(
  summary: ImpactSummary?,
  isLoading: Boolean,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ImpactPage(
    title = stringResource(Res.string.wizard_impact_title),
    subtitle = stringResource(Res.string.wizard_impact_survey_subtitle),
    isLoading = isLoading,
    summary = summary,
    onDownload = onDownload,
    modifier = modifier,
  )
}

/**
 * One organization's numbers on the Impact page: its members' full view (filterable by survey and
 * country) or its published view (filterable by country only, since survey names aren't published),
 * with a PDF summary download.
 *
 * @param banner shown under the title, e.g. what the organization publishes.
 * @param headerActions extra buttons next to the download button.
 */
@Composable
internal fun OrganizationImpactPane(
  state: OrganizationImpactUiState?,
  isLoading: Boolean,
  onSurveyFilterChange: (String?) -> Unit,
  onCountryFilterChange: (String?) -> Unit,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
  title: String = stringResource(Res.string.wizard_impact_title),
  subtitle: String = stringResource(Res.string.wizard_impact_organization_subtitle),
  banner: @Composable () -> Unit = {},
  headerActions: @Composable () -> Unit = {},
) {
  ImpactPage(
    title = title,
    subtitle = subtitle,
    isLoading = isLoading,
    summary = state?.summary,
    onDownload = onDownload,
    modifier = modifier,
    banner = banner,
    headerActions = headerActions,
    filters = {
      if (state != null && (state.surveys.isNotEmpty() || state.countries.size > 1)) {
        val allSurveys = stringResource(Res.string.wizard_impact_all_surveys)
        val allCountries = stringResource(Res.string.wizard_impact_all_countries)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (state.surveys.isNotEmpty()) {
            DropdownSelector(
              label = stringResource(Res.string.wizard_impact_filter_survey),
              selectedText =
                state.surveys.firstOrNull { it.id == state.filter.surveyId }?.title ?: allSurveys,
              options = listOf(null) + state.surveys.map { it.id },
              optionText = { id ->
                state.surveys.firstOrNull { it.id == id }?.title ?: allSurveys
              },
              onSelect = onSurveyFilterChange,
              modifier = Modifier.width(320.dp),
            )
          }
          DropdownSelector(
            label = stringResource(Res.string.wizard_impact_filter_country),
            selectedText =
              state.countries.firstOrNull { it.code == state.filter.countryCode }?.name
                ?: allCountries,
            options = listOf(null) + state.countries.map { it.code },
            optionText = { code ->
              state.countries.firstOrNull { it.code == code }?.name ?: allCountries
            },
            onSelect = onCountryFilterChange,
            modifier = Modifier.width(220.dp),
          )
        }
      }
    },
  )
}

/**
 * Numbers across all of Collect One: main goal totals from global standard fields,
 * organization-suggested indicators kept separate, countries, and how many map areas are kept
 * private. Everyone sees the published view; Managers of `"All users"` see every standard field.
 */
@Composable
internal fun GlobalImpactPane(
  summary: ImpactSummary?,
  isLoading: Boolean,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
  banner: @Composable () -> Unit = {},
) {
  ImpactPage(
    title = stringResource(Res.string.wizard_impact_all_of_collect_one),
    subtitle =
      stringResource(
        if (summary?.isPublished == false) Res.string.wizard_impact_global_subtitle_full
        else Res.string.wizard_impact_global_subtitle_published
      ),
    isLoading = isLoading,
    summary = summary,
    onDownload = onDownload,
    modifier = modifier,
    banner = banner,
  )
}

@Composable
private fun ImpactPage(
  title: String,
  subtitle: String,
  isLoading: Boolean,
  summary: ImpactSummary?,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
  banner: @Composable () -> Unit = {},
  headerActions: @Composable () -> Unit = {},
  filters: @Composable () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Row(
      modifier = Modifier.widthIn(max = ImpactContentMaxWidth).fillMaxWidth(),
      verticalAlignment = Alignment.Top,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(16.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        headerActions()
        if (summary != null && !summary.isBelowPublicationThreshold) {
          OutlinedButton(onClick = onDownload) {
            Icon(
              Icons.Outlined.Download,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.wizard_impact_download_pdf))
          }
        }
      }
    }
    Box(modifier = Modifier.widthIn(max = ImpactContentMaxWidth)) { banner() }
    filters()
    when {
      summary != null -> ImpactSummaryContent(summary)
      isLoading ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Text(
            stringResource(Res.string.wizard_impact_adding_up),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      else ->
        Text(
          stringResource(Res.string.wizard_impact_no_numbers_for_this),
          style = MaterialTheme.typography.bodyMedium,
        )
    }
  }
}

/** Everything in [summary], from totals to how the data was used and what couldn't be counted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImpactSummaryContent(summary: ImpactSummary) {
  Column(
    modifier = Modifier.widthIn(max = ImpactContentMaxWidth),
    verticalArrangement = Arrangement.spacedBy(28.dp),
  ) {
    if (summary.isBelowPublicationThreshold) {
      BelowThresholdCard()
      return@Column
    }
    if (summary.hints.isNotEmpty()) HintsCard(summary)
    Totals(summary)
    summary.goals.forEach { goal ->
      ImpactSection(goal.title.resolve()) { IndicatorRow(goal.indicators) }
    }
    if (summary.ownIndicators.isNotEmpty()) {
      ImpactSection(
        stringResource(
          if (summary.isPublished) Res.string.wizard_impact_own_indicators
          else Res.string.wizard_impact_your_indicators
        ),
        stringResource(
          if (summary.isPublished) Res.string.wizard_impact_own_indicators_supporting
          else Res.string.wizard_impact_your_indicators_supporting
        ),
      ) {
        IndicatorRow(summary.ownIndicators)
      }
    }
    if (summary.suggestedIndicators.isNotEmpty()) {
      ImpactSection(
        stringResource(Res.string.wizard_impact_suggested_indicators),
        stringResource(Res.string.wizard_impact_suggested_indicators_supporting),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          summary.suggestedIndicators.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                "${group.goalTitle.resolve()} · ${group.organizationName}",
                style = MaterialTheme.typography.titleSmall,
              )
              IndicatorRow(group.indicators)
            }
          }
        }
      }
    }
    ImpactSection(stringResource(Res.string.wizard_impact_how_data_was_used)) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        summary.activity.forEach { item ->
          StatCard(
            label = stringResource(item.type.activityLabelRes),
            value = ImpactFormat.count(item.count),
            detail =
              if (item.featureCount > 0) {
                stringResource(
                  Res.string.wizard_impact_detail_covering,
                  mapFeatures(item.featureCount),
                )
              } else {
                null
              },
            modifier = Modifier.width(200.dp),
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutcomesCard(summary)
        AttributionCard(summary)
      }
    }
    if (summary.scope == ImpactSummaryScope.GLOBAL) GlobalPrivacy(summary)
    if (summary.diagnostics.isNotEmpty()) Diagnostics(summary)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Totals(summary: ImpactSummary) {
  val features = summary.features
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    StatCard(
      label = stringResource(Res.string.wizard_impact_unique_hectares_mapped),
      value = impactMeasurementText(ImpactFormat.hectares(features.areaHa)).resolve(),
      detail = stringResource(Res.string.wizard_impact_unique_hectares_mapped_detail),
      emphasized = true,
      modifier = Modifier.width(240.dp),
    )
    StatCard(
      label = stringResource(Res.string.wizard_impact_plots_and_map_features),
      value = ImpactFormat.count(features.uniqueCount),
      detail =
        if (features.duplicateCount > 0) {
          pluralStringResource(
            Res.plurals.wizard_impact_duplicates_counted_once,
            features.duplicateCount.toQuantity(),
            ImpactFormat.count(features.duplicateCount),
          )
        } else {
          stringResource(Res.string.wizard_impact_no_plot_mapped_twice)
        },
      emphasized = true,
      modifier = Modifier.width(240.dp),
    )
    summary.producerCount?.let { producers ->
      StatCard(
        label = stringResource(Res.string.wizard_impact_producers_registered),
        value = ImpactFormat.count(producers),
        detail = stringResource(Res.string.wizard_impact_producers_registered_detail),
        emphasized = true,
        modifier = Modifier.width(240.dp),
      )
    }
  }
}

@Composable
private fun ImpactSection(
  title: String,
  supporting: String? = null,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column {
      Text(title, style = MaterialTheme.typography.titleLarge)
      supporting?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    content()
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndicatorRow(indicators: List<ImpactIndicator>) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    indicators.forEach { IndicatorCard(it) }
  }
}

@Composable
private fun IndicatorCard(indicator: ImpactIndicator) {
  OutlinedCard(modifier = Modifier.width(IndicatorCardWidth)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        indicator.label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(indicator.valueText.resolve(), style = MaterialTheme.typography.headlineSmall)
      if (!indicator.detail.isBlank) {
        Text(
          indicator.detail.resolve(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (indicator.breakdown.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        indicator.breakdown.forEach { item ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
              Text(
                item.label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              Text(
                item.valueText.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LinearProgressIndicator(
              progress = { item.fraction.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatCard(
  label: String,
  value: String,
  detail: String?,
  modifier: Modifier = Modifier,
  emphasized: Boolean = false,
) {
  ElevatedCard(
    modifier = modifier,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor =
          if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerLow
          }
      ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(label, style = MaterialTheme.typography.labelLarge)
      Text(
        value,
        style =
          if (emphasized) {
            MaterialTheme.typography.headlineMedium
          } else {
            MaterialTheme.typography.headlineSmall
          },
      )
      detail?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun OutcomesCard(summary: ImpactSummary) {
  val outcomes = summary.outcomes
  OutlinedCard(modifier = Modifier.width(IndicatorCardWidth)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        stringResource(Res.string.wizard_impact_what_happened_with_data),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (outcomes.isEmpty) {
        Text(
          stringResource(
            if (summary.scope == ImpactSummaryScope.SURVEY) {
              Res.string.wizard_impact_outcomes_empty_survey
            } else {
              Res.string.wizard_impact_outcomes_empty
            }
          ),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      val yes = stringResource(Res.string.wizard_impact_outcome_yes)
      outcomes.uses.forEach { (kind, count) ->
        LabeledCount(
          stringResource(kind.labelRes),
          if (summary.scope == ImpactSummaryScope.SURVEY) yes else ImpactFormat.count(count),
        )
      }
      if (outcomes.notYetCount > 0) {
        LabeledCount(
          stringResource(Res.string.wizard_impact_not_used_yet),
          if (summary.scope == ImpactSummaryScope.SURVEY) yes
          else ImpactFormat.count(outcomes.notYetCount),
        )
      }
      if (outcomes.unansweredCount > 0) {
        LabeledCount(
          stringResource(
            if (summary.scope == ImpactSummaryScope.SURVEY) {
              Res.string.wizard_impact_closed_not_answered_yet
            } else {
              Res.string.wizard_impact_closed_surveys_not_answered
            }
          ),
          if (summary.scope == ImpactSummaryScope.SURVEY) ""
          else ImpactFormat.count(outcomes.unansweredCount),
        )
      }
    }
  }
}

@Composable
private fun LabeledCount(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    if (value.isNotBlank()) {
      Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AttributionCard(summary: ImpactSummary) {
  val attribution = summary.attribution
  OutlinedCard(modifier = Modifier.width(IndicatorCardWidth)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        stringResource(Res.string.wizard_impact_how_directly_contributed),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val level = attribution.level
      if (level != null) {
        Text(
          "${stringResource(Res.string.wizard_impact_level_of_five, level.score.toString())} · " +
            stringResource(level.labelRes),
          style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
          progress = { level.score / 5f },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        Text(
          stringResource(level.explanationRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else if (attribution.distribution.isEmpty()) {
        Text(
          stringResource(
            if (summary.scope == ImpactSummaryScope.GLOBAL) {
              Res.string.wizard_impact_no_global_purpose_surveys
            } else {
              Res.string.wizard_impact_no_surveys
            }
          ),
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        val total = attribution.distribution.values.sum()
        if (summary.scope == ImpactSummaryScope.GLOBAL) {
          Text(
            stringResource(Res.string.wizard_impact_global_purpose_by_level),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        ImpactAttributionLevel.entries.forEach { lvl ->
          val count = attribution.distribution[lvl] ?: 0
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
              Text(
                "${lvl.score} · ${stringResource(lvl.labelRes)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              Text(
                pluralStringResource(
                  Res.plurals.wizard_impact_surveys,
                  count.toQuantity(),
                  ImpactFormat.count(count),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LinearProgressIndicator(
              progress = { if (total > 0) count.toFloat() / total else 0f },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlobalPrivacy(summary: ImpactSummary) {
  ImpactSection(
    stringResource(Res.string.wizard_impact_by_country_and_map_area),
    stringResource(Res.string.wizard_impact_by_country_and_map_area_supporting),
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedCard(modifier = Modifier.width(IndicatorCardWidth)) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            stringResource(Res.string.wizard_impact_countries),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (summary.countries.isEmpty()) {
            Text(
              stringResource(Res.string.wizard_impact_no_countries),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          summary.countries.forEach { country ->
            LabeledCount(
              country.countryName,
              if (country.isSuppressed) {
                stringResource(Res.string.wizard_impact_fewer_than_10)
              } else {
                val area = impactMeasurementText(ImpactFormat.hectares(country.areaHa)).resolve()
                "${ImpactFormat.count(country.featureCount)} · $area"
              },
            )
          }
        }
      }
      summary.cells?.let { cells ->
        StatCard(
          label = stringResource(Res.string.wizard_impact_map_areas_kept_private),
          value = ImpactFormat.count(cells.suppressedCellCount.toLong()),
          detail =
            pluralStringResource(
              Res.plurals.wizard_impact_map_areas_with_numbers,
              cells.publishedCellCount,
              ImpactFormat.count(cells.publishedCellCount.toLong()),
            ) +
              " · " +
              pluralStringResource(
                Res.plurals.wizard_impact_map_features_in_private_areas,
                cells.suppressedFeatureCount.toQuantity(),
                ImpactFormat.count(cells.suppressedFeatureCount),
              ),
          modifier = Modifier.width(IndicatorCardWidth),
        )
      }
      if (summary.excludedOrganizationCount > 0) {
        StatCard(
          label = stringResource(Res.string.wizard_impact_organizations_not_included),
          value = ImpactFormat.count(summary.excludedOrganizationCount.toLong()),
          detail = stringResource(Res.string.wizard_impact_organizations_not_included_detail),
          modifier = Modifier.width(IndicatorCardWidth),
        )
      }
    }
  }
}

/** Shown instead of published numbers while an organization has too few map features. */
@Composable
private fun BelowThresholdCard() {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.Outlined.Info, contentDescription = null)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          stringResource(Res.string.wizard_impact_not_enough_data_title),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          stringResource(Res.string.wizard_impact_not_enough_data_body, PUBLICATION_MIN_FEATURES),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

@Composable
private fun HintsCard(summary: ImpactSummary) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.Outlined.Lightbulb, contentDescription = null)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          stringResource(
            if (summary.isEmpty) Res.string.wizard_impact_hints_title_empty
            else Res.string.wizard_impact_hints_title
          ),
          style = MaterialTheme.typography.titleSmall,
        )
        summary.hints.forEach {
          Text(stringResource(it.messageRes), style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}

/** A discreet line saying what couldn't be counted, expanding to say why. */
@Composable
private fun Diagnostics(summary: ImpactSummary) {
  var isExpanded by remember { mutableStateOf(false) }
  val count = summary.diagnostics.size
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
      modifier =
        Modifier.clip(MaterialTheme.shapes.small)
          .clickable(role = Role.Button) { isExpanded = !isExpanded }
          .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Outlined.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Text(
        pluralStringResource(
          Res.plurals.wizard_impact_linked_fields_not_counted,
          count,
          ImpactFormat.count(count.toLong()),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = { isExpanded = !isExpanded }) {
        Text(
          stringResource(
            if (isExpanded) Res.string.wizard_impact_hide else Res.string.wizard_impact_why
          )
        )
      }
    }
    if (isExpanded) {
      Box(modifier = Modifier.padding(start = 24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          summary.diagnostics.forEach {
            Text(
              "• ${it.message.resolve()}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

/** "N map features", for [count]. */
@Composable
private fun mapFeatures(count: Long): String =
  pluralStringResource(
    Res.plurals.wizard_impact_map_features,
    count.toQuantity(),
    ImpactFormat.count(count),
  )

private fun Long.toQuantity(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
