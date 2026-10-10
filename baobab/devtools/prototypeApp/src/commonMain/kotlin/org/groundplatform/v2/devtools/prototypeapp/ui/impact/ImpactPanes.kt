/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.impact

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactAttributionLevel
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactFormat
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactIndicator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummaryScope
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationImpactUiState

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
    title = "Impact",
    subtitle =
      "What this survey's data adds up to, and how it was used. Plots mapped more than once " +
        "count once.",
    isLoading = isLoading,
    summary = summary,
    onDownload = onDownload,
    modifier = modifier,
  )
}

/**
 * The organization Impact tab: the organization's numbers across its surveys, filterable by survey
 * and country, its own indicators, and a PDF summary download.
 */
@Composable
internal fun OrganizationImpactPane(
  state: OrganizationImpactUiState?,
  isLoading: Boolean,
  onSurveyFilterChange: (String?) -> Unit,
  onCountryFilterChange: (String?) -> Unit,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ImpactPage(
    title = "Impact",
    subtitle =
      "What your surveys' data adds up to, and how it was used. Plots mapped in more than one " +
        "survey count once.",
    isLoading = isLoading,
    summary = state?.summary,
    onDownload = onDownload,
    modifier = modifier,
    filters = {
      if (state != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DropdownSelector(
            label = "Survey",
            selectedText =
              state.surveys.firstOrNull { it.id == state.filter.surveyId }?.title ?: ALL_SURVEYS,
            options = listOf(null) + state.surveys.map { it.id },
            optionText = { id -> state.surveys.firstOrNull { it.id == id }?.title ?: ALL_SURVEYS },
            onSelect = onSurveyFilterChange,
            modifier = Modifier.width(320.dp),
          )
          DropdownSelector(
            label = "Country",
            selectedText =
              state.countries.firstOrNull { it.code == state.filter.countryCode }?.name
                ?: ALL_COUNTRIES,
            options = listOf(null) + state.countries.map { it.code },
            optionText = { code ->
              state.countries.firstOrNull { it.code == code }?.name ?: ALL_COUNTRIES
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
 * Platform-wide numbers for Managers of `"All users"`: main goal totals from global standard
 * fields, organization-suggested indicators kept separate, countries, and how many map areas are
 * kept private.
 */
@Composable
internal fun GlobalImpactPane(
  summary: ImpactSummary?,
  isLoading: Boolean,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ImpactPage(
    title = "Platform impact",
    subtitle =
      "What everyone's data adds up to, from global standard fields only. Organizations that " +
        "opted out aren't included, and small groups are kept private. Only Managers of All " +
        "users see this.",
    isLoading = isLoading,
    summary = summary,
    onDownload = onDownload,
    modifier = modifier,
  )
}

private const val ALL_SURVEYS = "All surveys"
private const val ALL_COUNTRIES = "All countries"

@Composable
private fun ImpactPage(
  title: String,
  subtitle: String,
  isLoading: Boolean,
  summary: ImpactSummary?,
  onDownload: () -> Unit,
  modifier: Modifier = Modifier,
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
      if (summary != null) {
        Spacer(Modifier.width(16.dp))
        OutlinedButton(onClick = onDownload) {
          Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Download PDF summary")
        }
      }
    }
    filters()
    when {
      summary != null -> ImpactSummaryContent(summary)
      isLoading ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Text("Adding up the numbers…", style = MaterialTheme.typography.bodyMedium)
        }
      else -> Text("No numbers for this yet.", style = MaterialTheme.typography.bodyMedium)
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
    if (summary.hints.isNotEmpty()) HintsCard(summary)
    Totals(summary)
    summary.goals.forEach { goal ->
      ImpactSection(goal.title) { IndicatorRow(goal.indicators) }
    }
    if (summary.ownIndicators.isNotEmpty()) {
      ImpactSection(
        "Your indicators",
        "Your organization's own standard fields. They stay in your dashboards.",
      ) {
        IndicatorRow(summary.ownIndicators)
      }
    }
    if (summary.suggestedIndicators.isNotEmpty()) {
      ImpactSection(
        "Organization-suggested indicators",
        "Organizations' own fields they suggest count toward a goal. Shown separately and never " +
          "added to the totals above.",
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          summary.suggestedIndicators.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                "${group.goalTitle} · ${group.organizationName}",
                style = MaterialTheme.typography.titleSmall,
              )
              IndicatorRow(group.indicators)
            }
          }
        }
      }
    }
    ImpactSection("How the data was used") {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        summary.activity.forEach { item ->
          StatCard(
            label = item.label,
            value = ImpactFormat.count(item.count),
            detail =
              if (item.featureCount > 0) {
                "Covering ${ImpactFormat.count(item.featureCount, "map feature")}"
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
      label = "Unique hectares mapped",
      value = ImpactFormat.hectares(features.areaHa),
      detail = "Area of the unique map features",
      emphasized = true,
      modifier = Modifier.width(240.dp),
    )
    StatCard(
      label = "Plots and map features",
      value = ImpactFormat.count(features.uniqueCount),
      detail =
        if (features.duplicateCount > 0) {
          "${ImpactFormat.count(features.duplicateCount, "duplicate")} counted once " +
            "(same GeoID mapped again)"
        } else {
          "No plot mapped twice"
        },
      emphasized = true,
      modifier = Modifier.width(240.dp),
    )
    summary.producerCount?.let { producers ->
      StatCard(
        label = "Producers registered",
        value = ImpactFormat.count(producers),
        detail = "Different producers named on map features",
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
      Text(indicator.valueText, style = MaterialTheme.typography.headlineSmall)
      if (indicator.detail.isNotBlank()) {
        Text(
          indicator.detail,
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
                item.valueText,
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
        "What happened with the data",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (outcomes.isEmpty) {
        Text(
          if (summary.scope == ImpactSummaryScope.SURVEY) {
            "You'll be asked when the survey is closed."
          } else {
            "No answers yet. Organizers are asked when they close a survey."
          },
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      outcomes.uses.forEach { (kind, count) ->
        LabeledCount(
          kind.label,
          if (summary.scope == ImpactSummaryScope.SURVEY) "Yes" else ImpactFormat.count(count),
        )
      }
      if (outcomes.notYetCount > 0) {
        LabeledCount(
          "Not used yet",
          if (summary.scope == ImpactSummaryScope.SURVEY) "Yes"
          else ImpactFormat.count(outcomes.notYetCount),
        )
      }
      if (outcomes.unansweredCount > 0) {
        LabeledCount(
          if (summary.scope == ImpactSummaryScope.SURVEY) "Closed, not answered yet"
          else "Closed surveys not answered",
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
        "How directly the data contributed",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val level = attribution.level
      if (level != null) {
        Text("${level.score} of 5 · ${level.label}", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
          progress = { level.score / 5f },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        Text(
          level.explanation,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else if (attribution.distribution.isEmpty()) {
        Text(
          if (summary.scope == ImpactSummaryScope.GLOBAL) {
            "No surveys with a global purpose yet."
          } else {
            "No surveys yet."
          },
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        val total = attribution.distribution.values.sum()
        if (summary.scope == ImpactSummaryScope.GLOBAL) {
          Text(
            "Surveys with a global purpose, by level",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        ImpactAttributionLevel.entries.forEach { lvl ->
          val count = attribution.distribution[lvl] ?: 0
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
              Text(
                "${lvl.score} · ${lvl.label}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              Text(
                ImpactFormat.count(count, "survey"),
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
    "By country and map area",
    "Countries need at least 10 map features, and map areas at least 10 map features from 2 " +
      "organizations, before their numbers are shown.",
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
            "Countries",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (summary.countries.isEmpty()) {
            Text("No countries yet.", style = MaterialTheme.typography.bodyMedium)
          }
          summary.countries.forEach { country ->
            LabeledCount(
              country.countryName,
              if (country.isSuppressed) {
                "Fewer than 10"
              } else {
                "${ImpactFormat.count(country.featureCount)} · ${ImpactFormat.hectares(country.areaHa)}"
              },
            )
          }
        }
      }
      summary.cells?.let { cells ->
        StatCard(
          label = "Map areas kept private",
          value = ImpactFormat.count(cells.suppressedCellCount.toLong()),
          detail =
            "${ImpactFormat.count(cells.publishedCellCount.toLong(), "map area")} with numbers · " +
              "${ImpactFormat.count(cells.suppressedFeatureCount, "map feature")} in private areas",
          modifier = Modifier.width(IndicatorCardWidth),
        )
      }
      if (summary.excludedOrganizationCount > 0) {
        StatCard(
          label = "Organizations not included",
          value = ImpactFormat.count(summary.excludedOrganizationCount.toLong()),
          detail = "They keep their data out of platform-wide numbers",
          modifier = Modifier.width(IndicatorCardWidth),
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
          if (summary.isEmpty) "How to get numbers here" else "Get more out of your data",
          style = MaterialTheme.typography.titleSmall,
        )
        summary.hints.forEach { Text(it.message, style = MaterialTheme.typography.bodyMedium) }
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
        "${ImpactFormat.count(count.toLong(), "linked field")} couldn't be counted",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = { isExpanded = !isExpanded }) {
        Text(if (isExpanded) "Hide" else "Why?")
      }
    }
    if (isExpanded) {
      Box(modifier = Modifier.padding(start = 24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          summary.diagnostics.forEach {
            Text(
              "• ${it.message}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
