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
package org.openforis.collectone.v2.web.ui.surveyoutcome

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_outcome_effort_less
import org.openforis.collectone.v2.web.resources.wizard_outcome_effort_more
import org.openforis.collectone.v2.web.resources.wizard_outcome_effort_question
import org.openforis.collectone.v2.web.resources.wizard_outcome_effort_same
import org.openforis.collectone.v2.web.resources.wizard_outcome_save
import org.openforis.collectone.v2.web.resources.wizard_outcome_skip
import org.openforis.collectone.v2.web.resources.wizard_outcome_supporting
import org.openforis.collectone.v2.web.resources.wizard_outcome_title

/**
 * Inline "What happened with this data?" card, shown in the Survey editor after the survey is
 * closed. It doesn't block anything: the survey is already closed, and Skip is always available.
 */
@Composable
fun SurveyOutcomeCard(
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
      Text(
        stringResource(Res.string.wizard_outcome_title),
        style = MaterialTheme.typography.titleMedium,
      )
      SurveyOutcomeChoices(card, actions)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        TextButton(onClick = actions::skipOutcome) {
          Text(stringResource(Res.string.wizard_outcome_skip))
        }
        Button(onClick = actions::saveOutcome, enabled = card.canSave) {
          Text(stringResource(Res.string.wizard_outcome_save))
        }
      }
    }
  }
}

/**
 * "What happened with this data?" as a dialog, opened from the "Was this data used?" badge on the
 * web surveys page. Dismissing it is the same as Skip.
 */
@Composable
fun SurveyOutcomeDialog(card: SurveyOutcomeCardState, actions: SurveyOutcomeActions) {
  AlertDialog(
    onDismissRequest = actions::skipOutcome,
    title = { Text(stringResource(Res.string.wizard_outcome_title)) },
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
      Button(onClick = actions::saveOutcome, enabled = card.canSave) {
        Text(stringResource(Res.string.wizard_outcome_save))
      }
    },
    dismissButton = {
      TextButton(onClick = actions::skipOutcome) {
        Text(stringResource(Res.string.wizard_outcome_skip))
      }
    },
  )
}

/** The outcome chips and the optional time and cost comparison. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveyOutcomeChoices(card: SurveyOutcomeCardState, actions: SurveyOutcomeActions) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.wizard_outcome_supporting),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      SurveyOutcomeKind.entries.forEach { kind ->
        val selected = kind in card.outcomes
        FlatFilterChip(
          selected = selected,
          onClick = { actions.toggleOutcome(kind) },
          label = { Text(stringResource(kind.labelRes)) },
          leadingIcon = if (selected) ({ CheckIcon() }) else null,
        )
      }
    }
    Text(
      stringResource(Res.string.wizard_outcome_effort_question),
      style = MaterialTheme.typography.labelLarge,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      EffortComparison.entries.forEach { comparison ->
        val selected = card.effortComparison == comparison
        FlatFilterChip(
          selected = selected,
          onClick = { actions.setEffortComparison(if (selected) null else comparison) },
          label = { Text(stringResource(comparison.chipLabel)) },
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
private val EffortComparison.chipLabel: StringResource
  get() =
    when (this) {
      EffortComparison.LESS -> Res.string.wizard_outcome_effort_less
      EffortComparison.SAME -> Res.string.wizard_outcome_effort_same
      EffortComparison.MORE -> Res.string.wizard_outcome_effort_more
    }
