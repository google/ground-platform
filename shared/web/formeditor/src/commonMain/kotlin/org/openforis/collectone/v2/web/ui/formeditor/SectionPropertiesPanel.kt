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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.EditorSection
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.ui.common.displayMessage
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_close_properties
import org.openforis.collectone.v2.web.resources.formeditor_condition
import org.openforis.collectone.v2.web.resources.formeditor_delete_section
import org.openforis.collectone.v2.web.resources.formeditor_depends_on_question
import org.openforis.collectone.v2.web.resources.formeditor_duplicate
import org.openforis.collectone.v2.web.resources.formeditor_missing
import org.openforis.collectone.v2.web.resources.formeditor_move_section_down
import org.openforis.collectone.v2.web.resources.formeditor_move_section_up
import org.openforis.collectone.v2.web.resources.formeditor_no_section
import org.openforis.collectone.v2.web.resources.formeditor_repeat_count_optional
import org.openforis.collectone.v2.web.resources.formeditor_repeat_count_placeholder
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_badge
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_badge_untitled
import org.openforis.collectone.v2.web.resources.formeditor_repeated_section_description
import org.openforis.collectone.v2.web.resources.formeditor_section
import org.openforis.collectone.v2.web.resources.formeditor_section_badge
import org.openforis.collectone.v2.web.resources.formeditor_section_badge_untitled
import org.openforis.collectone.v2.web.resources.formeditor_section_condition
import org.openforis.collectone.v2.web.resources.formeditor_section_condition_no_sources
import org.openforis.collectone.v2.web.resources.formeditor_section_condition_off
import org.openforis.collectone.v2.web.resources.formeditor_section_condition_on
import org.openforis.collectone.v2.web.resources.formeditor_section_description
import org.openforis.collectone.v2.web.resources.formeditor_section_title
import org.openforis.collectone.v2.web.resources.formeditor_select_value
import org.openforis.collectone.v2.web.resources.formeditor_single_section
import org.openforis.collectone.v2.web.resources.formeditor_single_section_description
import org.openforis.collectone.v2.web.resources.formeditor_value
import org.openforis.collectone.v2.web.ui.common.DropdownSelector

/**
 * Editor for configuring a selected [EditorSection]'s title, repeatability, count expression, and
 * display logic, which shows or hides all its questions together, and for moving it as a whole.
 */
@Composable
internal fun SectionProperties(
  uiState: FormEditorUiState,
  actions: SectionActions,
  section: EditorSection,
) {
  val form = uiState.form
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            if (section.repeatable) stringResource(Res.string.formeditor_repeated_section)
            else stringResource(Res.string.formeditor_section),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = stringResource(Res.string.formeditor_section_description),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!uiState.isReadOnly) {
          IconButton(
            onClick = { actions.moveSection(section.id, -1) },
            enabled = form.movingSection(section.id, -1) != form,
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowUp,
              contentDescription = stringResource(Res.string.formeditor_move_section_up),
            )
          }
          IconButton(
            onClick = { actions.moveSection(section.id, 1) },
            enabled = form.movingSection(section.id, 1) != form,
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription = stringResource(Res.string.formeditor_move_section_down),
            )
          }
          IconButton(onClick = { actions.duplicateSection(section.id) }) {
            Icon(
              Icons.Outlined.ContentCopy,
              contentDescription = stringResource(Res.string.formeditor_duplicate),
            )
          }
          IconButton(onClick = { actions.deleteSection(section.id) }) {
            Icon(
              Icons.Outlined.Delete,
              contentDescription = stringResource(Res.string.formeditor_delete_section),
              tint = colors.error,
            )
          }
        }
        IconButton(onClick = { actions.selectSection(null) }) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.formeditor_close_properties),
          )
        }
      }
    }

    HorizontalDivider(color = colors.outlineVariant)

    val issues = uiState.issuesForSection(section.id)
    if (issues.isNotEmpty()) {
      Surface(
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          issues.forEach {
            Text("• ${it.displayMessage()}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    OutlinedTextField(
      value = section.title,
      onValueChange = { newTitle ->
        actions.updateSection(section.id) { it.copy(title = newTitle) }
      },
      enabled = !uiState.isReadOnly,
      label = { Text(stringResource(Res.string.formeditor_section_title)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            if (section.repeatable) stringResource(Res.string.formeditor_repeated_section)
            else stringResource(Res.string.formeditor_single_section),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          text =
            if (section.repeatable) {
              stringResource(Res.string.formeditor_repeated_section_description)
            } else {
              stringResource(Res.string.formeditor_single_section_description)
            },
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Switch(
        checked = section.repeatable,
        enabled = !uiState.isReadOnly,
        onCheckedChange = { isRep ->
          actions.updateSection(section.id) { it.copy(repeatable = isRep) }
        },
      )
    }

    if (section.repeatable) {
      OutlinedTextField(
        value = section.repeatCountExpression,
        onValueChange = { expr ->
          actions.updateSection(section.id) { it.copy(repeatCountExpression = expr) }
        },
        enabled = !uiState.isReadOnly,
        label = { Text(stringResource(Res.string.formeditor_repeat_count_optional)) },
        placeholder = { Text(stringResource(Res.string.formeditor_repeat_count_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    HorizontalDivider(color = colors.outlineVariant)

    SectionConditionEditor(uiState, actions, section)
  }
}

/**
 * The section's display logic: a toggle, then the question, comparison, and value it tests, and a
 * plain-language summary. Only questions asked before the section (outside repeated sections) can
 * drive it; when there are none, a hint says so.
 */
@Composable
private fun SectionConditionEditor(
  uiState: FormEditorUiState,
  actions: SectionActions,
  section: EditorSection,
) {
  val form = uiState.form
  val eligibleSources = form.eligibleSectionRelevanceSources(section.id)
  val relevance = section.relevance
  SectionConditionToggleRow(
    isReadOnly = uiState.isReadOnly,
    hasRelevance = relevance != null,
    hasEligibleSources = eligibleSources.isNotEmpty(),
    onCheckedChange = { enabled ->
      if (enabled) actions.enableSectionRelevance(section.id)
      else actions.disableSectionRelevance(section.id)
    },
  )
  if (relevance != null) {
    SectionConditionFields(uiState, actions, section, relevance, eligibleSources)
  }
}

@Composable
private fun SectionConditionToggleRow(
  isReadOnly: Boolean,
  hasRelevance: Boolean,
  hasEligibleSources: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(Res.string.formeditor_section_condition),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      )
      Text(
        text =
          when {
            hasRelevance -> stringResource(Res.string.formeditor_section_condition_on)
            !hasEligibleSources ->
              stringResource(Res.string.formeditor_section_condition_no_sources)
            else -> stringResource(Res.string.formeditor_section_condition_off)
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = hasRelevance,
      enabled = !isReadOnly && (hasRelevance || hasEligibleSources),
      onCheckedChange = onCheckedChange,
    )
  }
}

@Composable
private fun SectionConditionFields(
  uiState: FormEditorUiState,
  actions: SectionActions,
  section: EditorSection,
  relevance: EditorRelevance,
  eligibleSources: List<EditorQuestion>,
) {
  val form = uiState.form
  val sourceQ = form.find(relevance.sourceQuestionKey)
  DropdownSelector(
    label = stringResource(Res.string.formeditor_depends_on_question),
    selectedText =
      sourceQ?.label?.ifBlank { sourceQ.name } ?: stringResource(Res.string.formeditor_missing),
    options = eligibleSources,
    optionText = { it.label.ifBlank { it.name } },
    enabled = !uiState.isReadOnly && eligibleSources.isNotEmpty(),
    onSelect = { picked ->
      val op =
        relevance.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
          ?: RelevanceOperator.availableFor(picked.type).first()
      val value =
        if (op.needsValue) picked.resolvedChoices(uiState.datasets).firstOrNull()?.value.orEmpty()
        else ""
      actions.updateSection(section.id) {
        it.copy(relevance = EditorRelevance(picked.key, op, value))
      }
    },
  )
  if (sourceQ == null) return
  DropdownSelector(
    label = stringResource(Res.string.formeditor_condition),
    selectedText = stringResource(relevance.operator.labelRes),
    options = RelevanceOperator.availableFor(sourceQ.type),
    optionText = { stringResource(it.labelRes) },
    enabled = !uiState.isReadOnly,
    onSelect = { op ->
      actions.updateSection(section.id) { it.copy(relevance = relevance.copy(operator = op)) }
    },
  )
  if (relevance.operator.needsValue) {
    SectionConditionValueField(uiState, actions, section.id, sourceQ, relevance)
  }
  form.relevanceSummary(section, uiState.datasets)?.let { summary ->
    Text(
      text = summary.toUiText().resolve(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SectionConditionValueField(
  uiState: FormEditorUiState,
  actions: SectionActions,
  sectionId: String,
  sourceQ: EditorQuestion,
  relevance: EditorRelevance,
) {
  val choices = sourceQ.resolvedChoices(uiState.datasets)
  if (sourceQ.type.hasChoices && choices.isNotEmpty()) {
    DropdownSelector(
      label = stringResource(Res.string.formeditor_value),
      selectedText =
        choices.firstOrNull { it.value == relevance.value }?.label?.ifBlank { relevance.value }
          ?: relevance.value.ifBlank { stringResource(Res.string.formeditor_select_value) },
      options = choices,
      optionText = { it.label.ifBlank { it.value } },
      enabled = !uiState.isReadOnly,
      onSelect = { choice ->
        actions.updateSection(sectionId) {
          it.copy(relevance = relevance.copy(value = choice.value))
        }
      },
    )
  } else {
    OutlinedTextField(
      value = relevance.value,
      onValueChange = { v ->
        actions.updateSection(sectionId) { it.copy(relevance = relevance.copy(value = v)) }
      },
      enabled = !uiState.isReadOnly,
      label = { Text(stringResource(Res.string.formeditor_value)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/**
 * Picks the section [question] belongs to (or none). Moving it into a section places it next to the
 * section's other questions, so sections stay contiguous.
 */
@Composable
internal fun QuestionSectionSelector(
  uiState: FormEditorUiState,
  actions: SectionActions,
  question: EditorQuestion,
) {
  val form = uiState.form
  val options: List<EditorSection?> = listOf(null) + form.sections
  DropdownSelector(
    label = stringResource(Res.string.formeditor_section),
    selectedText = sectionOptionText(form.sectionOf(question)),
    options = options,
    optionText = { sectionOptionText(it) },
    enabled = !uiState.isReadOnly,
    onSelect = { picked -> actions.setQuestionSection(question.key, picked?.id.orEmpty()) },
  )
}

/** How [section] is named in pickers: as on its canvas badge, or "No section" for `null`. */
@Composable
private fun sectionOptionText(section: EditorSection?): String =
  when {
    section == null -> stringResource(Res.string.formeditor_no_section)
    section.repeatable && section.title.isBlank() ->
      stringResource(Res.string.formeditor_repeated_section_badge_untitled)
    section.repeatable ->
      stringResource(Res.string.formeditor_repeated_section_badge, section.title)
    section.title.isBlank() -> stringResource(Res.string.formeditor_section_badge_untitled)
    else -> stringResource(Res.string.formeditor_section_badge, section.title)
  }
