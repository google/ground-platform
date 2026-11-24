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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorRelevance
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_question_2
import org.openforis.collectone.v2.web.resources.formeditor_always_show
import org.openforis.collectone.v2.web.resources.formeditor_condition
import org.openforis.collectone.v2.web.resources.formeditor_display_logic
import org.openforis.collectone.v2.web.resources.formeditor_first_screen_display_logic_notice
import org.openforis.collectone.v2.web.resources.formeditor_first_screen_in_form
import org.openforis.collectone.v2.web.resources.formeditor_in_conditional_section
import org.openforis.collectone.v2.web.resources.formeditor_missing_question
import org.openforis.collectone.v2.web.resources.formeditor_move_question_down
import org.openforis.collectone.v2.web.resources.formeditor_pick_a_choice
import org.openforis.collectone.v2.web.resources.formeditor_question
import org.openforis.collectone.v2.web.resources.formeditor_select_question_2
import org.openforis.collectone.v2.web.resources.formeditor_show_only_if
import org.openforis.collectone.v2.web.resources.formeditor_value
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.SectionLabel

@Composable
internal fun DisplayLogicEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val form = uiState.form
  val key = question.key
  val sources = form.eligibleRelevanceSources(key)
  val relevance = question.relevance
  var showFirstQuestionNotice by
    remember(key, sources.size, relevance != null) {
      mutableStateOf(false)
    }
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionLabel(stringResource(Res.string.formeditor_display_logic))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      SegmentedButton(
        selected = relevance == null && !showFirstQuestionNotice,
        onClick = {
          showFirstQuestionNotice = false
          actions.disableRelevance(key)
        },
        enabled = !uiState.isReadOnly,
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        label = { Text(stringResource(Res.string.formeditor_always_show)) },
      )
      SegmentedButton(
        selected = relevance != null || showFirstQuestionNotice,
        onClick = {
          if (sources.isNotEmpty()) {
            showFirstQuestionNotice = false
            if (relevance == null) actions.enableRelevance(key)
          } else if (relevance == null) {
            showFirstQuestionNotice = true
          }
        },
        enabled = !uiState.isReadOnly,
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        label = { Text(stringResource(Res.string.formeditor_show_only_if)) },
      )
    }
    if (sources.isEmpty() && relevance == null) {
      Surface(
        color =
          if (showFirstQuestionNotice) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor =
          if (showFirstQuestionNotice) colors.onSecondaryContainer else colors.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        border =
          BorderStroke(
            1.dp,
            if (showFirstQuestionNotice) colors.secondary else colors.outlineVariant,
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Outlined.Info,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stringResource(Res.string.formeditor_first_screen_in_form),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text = stringResource(Res.string.formeditor_first_screen_display_logic_notice),
            style = MaterialTheme.typography.bodySmall,
          )
          if (form.questions.size > 1 || !uiState.isReadOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (form.questions.size > 1) {
                TextButton(
                  onClick = {
                    showFirstQuestionNotice = false
                    actions.select(form.questions[1].key)
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                  Text(stringResource(Res.string.formeditor_select_question_2))
                }
                if (!uiState.isReadOnly) {
                  TextButton(
                    onClick = {
                      showFirstQuestionNotice = false
                      actions.moveQuestion(key, 1)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  ) {
                    Text(stringResource(Res.string.formeditor_move_question_down))
                  }
                }
              } else if (!uiState.isReadOnly) {
                TextButton(
                  onClick = {
                    showFirstQuestionNotice = false
                    actions.addQuestion(EditorQuestionType.TEXT, 1)
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                  Text(stringResource(Res.string.formeditor_add_question_2))
                }
              }
            }
          }
        }
      }
    }
    if (relevance != null) {
      ConditionRulePickers(
        uiState = uiState,
        sources = sources,
        condition = relevance,
        enabled = !uiState.isReadOnly,
        onChange = { updated -> actions.updateQuestion(key) { it.copy(relevance = updated) } },
      )
      form.relevanceSummary(question, uiState.datasets)?.let { summary ->
        Text(
          text = summary.toUiText().resolve(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    // Section display logic applies on top of the question's own (relevance is inherited).
    form
      .sectionOf(question)
      ?.takeIf { it.isConditional }
      ?.let { section ->
        TextButton(
          onClick = { actions.selectSection(section.id) },
          contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) {
          Text(
            text =
              stringResource(
                Res.string.formeditor_in_conditional_section,
                section.title.ifBlank { section.id },
              ),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
  }
}

/** Default [EditorRelevance] seeded from the first eligible source question. */
internal fun defaultConditionFor(
  source: EditorQuestion,
  datasets: List<org.openforis.collectone.v2.app.domain.model.editor.EditorDataset>,
): EditorRelevance {
  val operator = RelevanceOperator.availableFor(source.type).first()
  val value =
    if (operator.needsValue) source.resolvedChoices(datasets).firstOrNull()?.value.orEmpty() else ""
  return EditorRelevance(source.key, operator, value)
}

/**
 * Reusable Question / Operator / Value pickers for any [EditorRelevance] predicate (display logic,
 * conditional requiredness, or conditional validation).
 */
@Composable
internal fun ConditionRulePickers(
  uiState: FormEditorUiState,
  sources: List<EditorQuestion>,
  condition: EditorRelevance,
  enabled: Boolean,
  onChange: (EditorRelevance) -> Unit,
) {
  val form = uiState.form
  val source = form.find(condition.sourceQuestionKey)
  DropdownSelector(
    label = stringResource(Res.string.formeditor_question),
    selectedText =
      source?.let { it.label.ifBlank { it.name } }
        ?: stringResource(Res.string.formeditor_missing_question),
    options = sources,
    optionText = { it.label.ifBlank { it.name } },
    enabled = enabled,
    onSelect = { picked ->
      val operator =
        condition.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
          ?: RelevanceOperator.availableFor(picked.type).first()
      val value = picked.resolvedChoices(uiState.datasets).firstOrNull()?.value.orEmpty()
      onChange(EditorRelevance(picked.key, operator, value))
    },
  )
  if (source != null) {
    DropdownSelector(
      label = stringResource(Res.string.formeditor_condition),
      selectedText = stringResource(condition.operator.labelRes),
      options = RelevanceOperator.availableFor(source.type),
      optionText = { stringResource(it.labelRes) },
      enabled = enabled,
      onSelect = { op -> onChange(condition.copy(operator = op)) },
    )
    if (condition.operator.needsValue) {
      val sourceChoices = source.resolvedChoices(uiState.datasets)
      if (source.type.hasChoices && sourceChoices.isNotEmpty()) {
        DropdownSelector(
          label = stringResource(Res.string.formeditor_value),
          selectedText =
            sourceChoices.firstOrNull { it.value == condition.value }?.label
              ?: condition.value.ifBlank { stringResource(Res.string.formeditor_pick_a_choice) },
          options = sourceChoices,
          optionText = { it.label },
          enabled = enabled,
          onSelect = { c -> onChange(condition.copy(value = c.value)) },
        )
      } else {
        OutlinedTextField(
          value = condition.value,
          onValueChange = { v -> onChange(condition.copy(value = v)) },
          enabled = enabled,
          label = { Text(stringResource(Res.string.formeditor_value)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
