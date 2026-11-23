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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceColors
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusBadge
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusRule
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.domain.model.editor.StatusMarkerSymbol
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_status_rule
import org.openforis.collectone.v2.web.resources.formeditor_at_least_submissions
import org.openforis.collectone.v2.web.resources.formeditor_choose
import org.openforis.collectone.v2.web.resources.formeditor_condition
import org.openforis.collectone.v2.web.resources.formeditor_default_status_color
import org.openforis.collectone.v2.web.resources.formeditor_feature_property
import org.openforis.collectone.v2.web.resources.formeditor_marker_color
import org.openforis.collectone.v2.web.resources.formeditor_matches_min_submissions_feature
import org.openforis.collectone.v2.web.resources.formeditor_matches_min_submissions_map_feature
import org.openforis.collectone.v2.web.resources.formeditor_matches_min_submissions_table_row
import org.openforis.collectone.v2.web.resources.formeditor_matches_when_submitted
import org.openforis.collectone.v2.web.resources.formeditor_move_status_rule_down
import org.openforis.collectone.v2.web.resources.formeditor_move_status_rule_up
import org.openforis.collectone.v2.web.resources.formeditor_otherwise
import org.openforis.collectone.v2.web.resources.formeditor_otherwise_help
import org.openforis.collectone.v2.web.resources.formeditor_pick_a_choice
import org.openforis.collectone.v2.web.resources.formeditor_question
import org.openforis.collectone.v2.web.resources.formeditor_question_answer
import org.openforis.collectone.v2.web.resources.formeditor_remove_status_rule
import org.openforis.collectone.v2.web.resources.formeditor_row_property
import org.openforis.collectone.v2.web.resources.formeditor_set_status_marker
import org.openforis.collectone.v2.web.resources.formeditor_status_color_for_rule
import org.openforis.collectone.v2.web.resources.formeditor_status_label
import org.openforis.collectone.v2.web.resources.formeditor_status_marker
import org.openforis.collectone.v2.web.resources.formeditor_status_marker_off_map_features
import org.openforis.collectone.v2.web.resources.formeditor_status_marker_off_table_rows
import org.openforis.collectone.v2.web.resources.formeditor_status_marker_on_map_feature
import org.openforis.collectone.v2.web.resources.formeditor_status_marker_on_table_row
import org.openforis.collectone.v2.web.resources.formeditor_status_placeholder
import org.openforis.collectone.v2.web.resources.formeditor_status_rule_title
import org.openforis.collectone.v2.web.resources.formeditor_status_rule_when
import org.openforis.collectone.v2.web.resources.formeditor_status_rules_help
import org.openforis.collectone.v2.web.resources.formeditor_submission_count
import org.openforis.collectone.v2.web.resources.formeditor_symbol
import org.openforis.collectone.v2.web.resources.formeditor_value
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.SectionLabel

/**
 * Conditional status marker and label rules, configured in Form properties → Advanced.
 *
 * Off by default. When turned on, defaults to marking the feature as `✓ Surveyed` when this form is
 * submitted and `○ Pending` otherwise; organizers can add or reorder rules based on submission
 * count, question answers, or feature properties.
 */
@Composable
internal fun StatusMarkerEditor(uiState: FormEditorUiState, actions: StatusRuleActions) {
  val form = uiState.form
  val status = form.saveTo.status
  val target = uiState.saveTarget
  val isMapFeature = target?.isMapLayer ?: form.hasGeometry
  val colors = MaterialTheme.colorScheme

  SectionLabel(stringResource(Res.string.formeditor_status_marker))
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .toggleable(
            value = status.enabled,
            enabled = !uiState.isReadOnly,
            role = Role.Switch,
            onValueChange = actions::setStatusEnabled,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Flag,
        contentDescription = null,
        tint = if (status.enabled) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(Res.string.formeditor_set_status_marker),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text =
            stringResource(
              when {
                status.enabled && isMapFeature -> Res.string.formeditor_status_marker_on_map_feature
                status.enabled -> Res.string.formeditor_status_marker_on_table_row
                isMapFeature -> Res.string.formeditor_status_marker_off_map_features
                else -> Res.string.formeditor_status_marker_off_table_rows
              }
            ),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      Switch(checked = status.enabled, onCheckedChange = null, enabled = !uiState.isReadOnly)
    }
  }

  if (!status.enabled) return

  HelperText(stringResource(Res.string.formeditor_status_rules_help))

  status.rules.forEachIndexed { index, rule ->
    StatusRuleCard(
      uiState = uiState,
      actions = actions,
      index = index,
      totalRules = status.rules.size,
      rule = rule,
      target = target,
    )
  }

  if (!uiState.isReadOnly) {
    TextButton(onClick = actions::addStatusRule) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(4.dp))
      Text(stringResource(Res.string.formeditor_add_status_rule))
    }
  }

  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(Res.string.formeditor_otherwise),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(Res.string.formeditor_otherwise_help),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
        }
        StatusBadgeChip(status.defaultBadge)
      }
      StatusBadgeEditor(
        badge = status.defaultBadge,
        colorDescription = stringResource(Res.string.formeditor_default_status_color),
        onUpdate = actions::updateDefaultStatusBadge,
        enabled = !uiState.isReadOnly,
      )
    }
  }
}

@Composable
private fun StatusRuleCard(
  uiState: FormEditorUiState,
  actions: StatusRuleActions,
  index: Int,
  totalRules: Int,
  rule: EditorStatusRule,
  target: EditorDataset?,
) {
  val form = uiState.form
  val savableQuestions = SaveToRules.savableQuestions(form)
  val propertyNoun =
    stringResource(
      if (target?.isMapLayer == false) Res.string.formeditor_row_property
      else Res.string.formeditor_feature_property
    )
  val submissionCountLabel = stringResource(Res.string.formeditor_submission_count)
  val questionAnswerLabel = stringResource(Res.string.formeditor_question_answer)
  val choose = stringResource(Res.string.formeditor_choose)
  val subjectOptions = buildList {
    add(StatusConditionSubject.SUBMISSIONS)
    if (savableQuestions.isNotEmpty() || rule.subject == StatusConditionSubject.QUESTION) {
      add(StatusConditionSubject.QUESTION)
    }
    if (target != null || rule.subject == StatusConditionSubject.ENTITY_PROPERTY) {
      add(StatusConditionSubject.ENTITY_PROPERTY)
    }
  }
  fun subjectLabel(subject: StatusConditionSubject): String =
    when (subject) {
      StatusConditionSubject.SUBMISSIONS -> submissionCountLabel
      StatusConditionSubject.QUESTION -> questionAnswerLabel
      StatusConditionSubject.ENTITY_PROPERTY -> propertyNoun
    }

  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = stringResource(Res.string.formeditor_status_rule_title, index + 1),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        StatusBadgeChip(rule.badge)
        if (!uiState.isReadOnly && totalRules > 1) {
          IconButton(
            onClick = { actions.moveStatusRule(index, -1) },
            enabled = index > 0,
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowUp,
              contentDescription =
                stringResource(Res.string.formeditor_move_status_rule_up, index + 1),
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = { actions.moveStatusRule(index, 1) },
            enabled = index < totalRules - 1,
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription =
                stringResource(Res.string.formeditor_move_status_rule_down, index + 1),
              modifier = Modifier.size(18.dp),
            )
          }
        }
        if (!uiState.isReadOnly) {
          IconButton(
            onClick = { actions.removeStatusRule(index) },
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              Icons.Outlined.Close,
              contentDescription =
                stringResource(Res.string.formeditor_remove_status_rule, index + 1),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      DropdownSelector(
        label = stringResource(Res.string.formeditor_status_rule_when),
        selectedText = subjectLabel(rule.subject),
        options = subjectOptions,
        optionText = { subjectLabel(it) },
        enabled = !uiState.isReadOnly,
        onSelect = { picked ->
          actions.updateStatusRule(index) { current ->
            when (picked) {
              StatusConditionSubject.SUBMISSIONS ->
                current.copy(
                  subject = picked,
                  minSubmissions = current.minSubmissions.coerceAtLeast(1),
                )
              StatusConditionSubject.QUESTION -> {
                val q =
                  form.find(current.questionKey)
                    ?: savableQuestions.firstOrNull { it.type.hasChoices }
                    ?: savableQuestions.firstOrNull()
                val op =
                  q?.let { RelevanceOperator.availableFor(it.type).first() }
                    ?: RelevanceOperator.EQUALS
                val v = q?.resolvedChoices(uiState.datasets)?.firstOrNull()?.value.orEmpty()
                current.copy(
                  subject = picked,
                  questionKey = q?.key,
                  operator = op,
                  value = v,
                )
              }
              StatusConditionSubject.ENTITY_PROPERTY -> {
                val prop =
                  target?.property(current.property) ?: target?.matchableProperties?.firstOrNull()
                val op =
                  prop?.let { SaveToRules.operatorsForProperty(it.kind).first() }
                    ?: RelevanceOperator.EQUALS
                current.copy(
                  subject = picked,
                  property = prop?.name,
                  operator = op,
                )
              }
            }
          }
        },
      )

      when (rule.subject) {
        StatusConditionSubject.SUBMISSIONS -> {
          OutlinedTextField(
            value = rule.minSubmissions.toString(),
            onValueChange = { text ->
              val parsed = text.trim().toIntOrNull()
              if (parsed != null) {
                actions.updateStatusRule(index) { it.copy(minSubmissions = parsed) }
              } else if (text.isEmpty()) {
                actions.updateStatusRule(index) { it.copy(minSubmissions = 1) }
              }
            },
            enabled = !uiState.isReadOnly,
            label = { Text(stringResource(Res.string.formeditor_at_least_submissions)) },
            supportingText = {
              Text(
                if (rule.minSubmissions <= 1) {
                  stringResource(Res.string.formeditor_matches_when_submitted)
                } else {
                  pluralStringResource(
                    when (target?.isMapLayer) {
                      null -> Res.plurals.formeditor_matches_min_submissions_feature
                      true -> Res.plurals.formeditor_matches_min_submissions_map_feature
                      false -> Res.plurals.formeditor_matches_min_submissions_table_row
                    },
                    rule.minSubmissions,
                    rule.minSubmissions,
                  )
                }
              )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        StatusConditionSubject.QUESTION -> {
          val question = form.find(rule.questionKey)
          DropdownSelector(
            label = stringResource(Res.string.formeditor_question),
            selectedText = question?.let { "${it.name} — ${it.label}" } ?: choose,
            options = savableQuestions,
            optionText = { "${it.name} — ${it.label}" },
            optionIcon = { questionTypeIcon(it.type) },
            enabled = !uiState.isReadOnly,
            onSelect = { picked ->
              val op =
                rule.operator.takeIf { it in RelevanceOperator.availableFor(picked.type) }
                  ?: RelevanceOperator.availableFor(picked.type).first()
              val v = picked.resolvedChoices(uiState.datasets).firstOrNull()?.value.orEmpty()
              actions.updateStatusRule(index) {
                it.copy(questionKey = picked.key, operator = op, value = v)
              }
            },
          )
          if (question != null) {
            DropdownSelector(
              label = stringResource(Res.string.formeditor_condition),
              selectedText = stringResource(rule.operator.labelRes),
              options = RelevanceOperator.availableFor(question.type),
              optionText = { stringResource(it.labelRes) },
              enabled = !uiState.isReadOnly,
              onSelect = { op -> actions.updateStatusRule(index) { it.copy(operator = op) } },
            )
            if (rule.operator.needsValue) {
              val questionChoices = question.resolvedChoices(uiState.datasets)
              if (question.type.hasChoices && questionChoices.isNotEmpty()) {
                DropdownSelector(
                  label = stringResource(Res.string.formeditor_value),
                  selectedText =
                    questionChoices.firstOrNull { it.value == rule.value }?.label
                      ?: rule.value.ifBlank { stringResource(Res.string.formeditor_pick_a_choice) },
                  options = questionChoices,
                  optionText = { "${it.label} (${it.value})" },
                  enabled = !uiState.isReadOnly,
                  onSelect = { c -> actions.updateStatusRule(index) { it.copy(value = c.value) } },
                )
              } else {
                OutlinedTextField(
                  value = rule.value,
                  onValueChange = { v -> actions.updateStatusRule(index) { it.copy(value = v) } },
                  enabled = !uiState.isReadOnly,
                  label = { Text(stringResource(Res.string.formeditor_value)) },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }
        StatusConditionSubject.ENTITY_PROPERTY -> {
          val prop = target?.property(rule.property)
          val properties = target?.matchableProperties.orEmpty()
          DropdownSelector(
            label = propertyNoun,
            selectedText = prop?.let(::propertyText) ?: choose,
            options = properties,
            optionText = { propertyText(it) },
            enabled = !uiState.isReadOnly,
            onSelect = { picked ->
              val op =
                rule.operator.takeIf { it in SaveToRules.operatorsForProperty(picked.kind) }
                  ?: SaveToRules.operatorsForProperty(picked.kind).first()
              actions.updateStatusRule(index) { it.copy(property = picked.name, operator = op) }
            },
          )
          if (prop != null) {
            DropdownSelector(
              label = stringResource(Res.string.formeditor_condition),
              selectedText = stringResource(rule.operator.labelRes),
              options = SaveToRules.operatorsForProperty(prop.kind),
              optionText = { stringResource(it.labelRes) },
              enabled = !uiState.isReadOnly,
              onSelect = { op -> actions.updateStatusRule(index) { it.copy(operator = op) } },
            )
            if (rule.operator.needsValue) {
              OutlinedTextField(
                value = rule.value,
                onValueChange = { v -> actions.updateStatusRule(index) { it.copy(value = v) } },
                enabled = !uiState.isReadOnly,
                label = { Text(stringResource(Res.string.formeditor_value)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }

      StatusBadgeEditor(
        badge = rule.badge,
        colorDescription = stringResource(Res.string.formeditor_status_color_for_rule, index + 1),
        onUpdate = { transform ->
          actions.updateStatusRule(index) { it.copy(badge = transform(it.badge)) }
        },
        enabled = !uiState.isReadOnly,
      )
    }
  }
}

/** Edits a status outcome's color swatch, marker symbol, and status label. */
@Composable
private fun StatusBadgeEditor(
  badge: EditorStatusBadge,
  colorDescription: String,
  onUpdate: ((EditorStatusBadge) -> EditorStatusBadge) -> Unit,
  enabled: Boolean = true,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    StatusColorButton(
      colorHex = badge.colorHex,
      contentDescription = colorDescription,
      onSelect = { hex -> onUpdate { it.copy(colorHex = hex) } },
      enabled = enabled,
    )
    DropdownSelector(
      label = stringResource(Res.string.formeditor_symbol),
      selectedText = "${badge.symbol.symbol} ${stringResource(badge.symbol.labelRes)}",
      options = StatusMarkerSymbol.entries,
      optionText = { "${it.symbol} ${stringResource(it.labelRes)}" },
      onSelect = { sym -> onUpdate { it.copy(symbol = sym) } },
      enabled = enabled,
      modifier = Modifier.weight(0.45f),
    )
    OutlinedTextField(
      value = badge.label,
      onValueChange = { text -> onUpdate { it.copy(label = text) } },
      enabled = enabled,
      label = { Text(stringResource(Res.string.formeditor_status_label)) },
      singleLine = true,
      modifier = Modifier.weight(0.55f),
    )
  }
}

/** Color swatch button for a status badge; opens [ChoiceColors.palette]. */
@Composable
private fun StatusColorButton(
  colorHex: String,
  contentDescription: String,
  onSelect: (String) -> Unit,
  enabled: Boolean = true,
) {
  var expanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  Box {
    IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(36.dp)) {
      ColorDot(
        colorHex = colorHex,
        contentDescription = "$contentDescription: ${ChoiceColors.nameOf(colorHex) ?: colorHex}",
        size = 24.dp,
      )
    }
    DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      Text(
        text = stringResource(Res.string.formeditor_marker_color),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
      ChoiceColors.palette.chunked(5).forEach { row ->
        Row(
          modifier = Modifier.padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          row.forEach { (name, hex) ->
            IconButton(
              onClick = {
                onSelect(hex)
                expanded = false
              },
              modifier = Modifier.size(36.dp),
            ) {
              ColorDot(
                colorHex = hex,
                contentDescription = name,
                isSelected = hex.equals(colorHex, ignoreCase = true),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Live status pill preview matching `EntityStatusChip`: filled with [EditorStatusBadge.colorHex]
 * and showing `[symbol] [label]` in a contrasting foreground color.
 */
@Composable
fun StatusBadgeChip(badge: EditorStatusBadge, modifier: Modifier = Modifier) {
  val bg = ChoiceColors.argb(badge.colorHex)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
  val fg = if (bg.luminance() > 0.6f) Color(0xFF181D18) else Color.White
  val placeholder = stringResource(Res.string.formeditor_status_placeholder)
  val text =
    listOf(badge.symbol.symbol, badge.label.trim().ifEmpty { placeholder }).joinToString(" ")
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = bg,
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = fg,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
  }
}
