/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyOutcomeCardState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyOutcomeActions

private const val OUTCOME_TITLE = "What happened with this data?"
private const val OUTCOME_SUPPORTING_TEXT =
  "Choose all that apply. Your answer helps show how survey data gets used."

/**
 * Inline "What happened with this data?" card, shown in the Survey editor after the survey is
 * closed. It doesn't block anything: the survey is already closed, and Skip is always available.
 */
@Composable
internal fun SurveyOutcomeCard(
  card: SurveyOutcomeCardState,
  actions: SurveyOutcomeActions,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    modifier = modifier,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(OUTCOME_TITLE, style = MaterialTheme.typography.titleMedium)
      SurveyOutcomeChoices(card, actions)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        TextButton(onClick = actions::skipOutcome) { Text("Skip") }
        Button(onClick = actions::saveOutcome, enabled = card.canSave) { Text("Save") }
      }
    }
  }
}

/**
 * "What happened with this data?" as a dialog, opened from the "Was this data used?" badge on the
 * web surveys page. Dismissing it is the same as Skip.
 */
@Composable
internal fun SurveyOutcomeDialog(card: SurveyOutcomeCardState, actions: SurveyOutcomeActions) {
  AlertDialog(
    onDismissRequest = actions::skipOutcome,
    title = { Text(OUTCOME_TITLE) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          card.surveyTitle,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        SurveyOutcomeChoices(card, actions)
      }
    },
    confirmButton = {
      Button(onClick = actions::saveOutcome, enabled = card.canSave) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = actions::skipOutcome) { Text("Skip") } },
  )
}

/** The outcome chips and the optional time and cost comparison. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveyOutcomeChoices(card: SurveyOutcomeCardState, actions: SurveyOutcomeActions) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      OUTCOME_SUPPORTING_TEXT,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      SurveyOutcomeKind.entries.forEach { kind ->
        val selected = kind in card.outcomes
        GroundFilterChip(
          selected = selected,
          onClick = { actions.toggleOutcome(kind) },
          label = { Text(kind.label) },
          leadingIcon = if (selected) ({ CheckIcon() }) else null,
        )
      }
    }
    Text(
      "Compared with your previous method, this took… (optional)",
      style = MaterialTheme.typography.labelLarge,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      EffortComparison.entries.forEach { comparison ->
        val selected = card.effortComparison == comparison
        GroundFilterChip(
          selected = selected,
          onClick = { actions.setEffortComparison(if (selected) null else comparison) },
          label = { Text(comparison.chipLabel) },
          leadingIcon = if (selected) ({ CheckIcon() }) else null,
        )
      }
    }
  }
}

@Composable
private fun CheckIcon() {
  Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
}

/** Chip label completing "this took…". */
private val EffortComparison.chipLabel: String
  get() =
    when (this) {
      EffortComparison.LESS -> "Less time and cost"
      EffortComparison.SAME -> "About the same"
      EffortComparison.MORE -> "More time and cost"
    }
