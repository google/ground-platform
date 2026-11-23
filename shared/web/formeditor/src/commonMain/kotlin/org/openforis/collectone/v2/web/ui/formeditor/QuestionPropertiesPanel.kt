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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.DateRule
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorValidation
import org.openforis.collectone.v2.app.domain.model.editor.FormEditorValidator
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.TextPattern
import org.openforis.collectone.v2.app.domain.model.editor.ValidationKind
import org.openforis.collectone.v2.app.domain.model.editor.ValidationRules
import org.openforis.collectone.v2.app.domain.model.editor.isoDateToUtcMillis
import org.openforis.collectone.v2.app.domain.model.editor.utcMillisToIsoDate
import org.openforis.collectone.v2.app.ui.common.displayMessage
import org.openforis.collectone.v2.app.ui.text.descriptionRes
import org.openforis.collectone.v2.app.ui.text.isoDateText
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_advanced
import org.openforis.collectone.v2.web.resources.formeditor_advanced_has_errors
import org.openforis.collectone.v2.web.resources.formeditor_allowed_dates
import org.openforis.collectone.v2.web.resources.formeditor_any_date
import org.openforis.collectone.v2.web.resources.formeditor_cancel
import org.openforis.collectone.v2.web.resources.formeditor_capture
import org.openforis.collectone.v2.web.resources.formeditor_capture_walk_boundary
import org.openforis.collectone.v2.web.resources.formeditor_capture_walk_boundary_description
import org.openforis.collectone.v2.web.resources.formeditor_capture_walk_path
import org.openforis.collectone.v2.web.resources.formeditor_capture_walk_path_description
import org.openforis.collectone.v2.web.resources.formeditor_clear_field
import org.openforis.collectone.v2.web.resources.formeditor_collapse_advanced
import org.openforis.collectone.v2.web.resources.formeditor_decimal_places
import org.openforis.collectone.v2.web.resources.formeditor_decimal_places_up_to
import org.openforis.collectone.v2.web.resources.formeditor_decimal_places_zero
import org.openforis.collectone.v2.web.resources.formeditor_delete
import org.openforis.collectone.v2.web.resources.formeditor_delete_question
import org.openforis.collectone.v2.web.resources.formeditor_duplicate
import org.openforis.collectone.v2.web.resources.formeditor_duplicate_branch
import org.openforis.collectone.v2.web.resources.formeditor_earliest_date
import org.openforis.collectone.v2.web.resources.formeditor_error_message_optional
import org.openforis.collectone.v2.web.resources.formeditor_expand_advanced
import org.openforis.collectone.v2.web.resources.formeditor_hint_in_language_optional
import org.openforis.collectone.v2.web.resources.formeditor_hint_optional
import org.openforis.collectone.v2.web.resources.formeditor_latest_date
import org.openforis.collectone.v2.web.resources.formeditor_max_length
import org.openforis.collectone.v2.web.resources.formeditor_maximum
import org.openforis.collectone.v2.web.resources.formeditor_maximum_selections
import org.openforis.collectone.v2.web.resources.formeditor_media_source
import org.openforis.collectone.v2.web.resources.formeditor_min_length
import org.openforis.collectone.v2.web.resources.formeditor_minimum
import org.openforis.collectone.v2.web.resources.formeditor_minimum_selections
import org.openforis.collectone.v2.web.resources.formeditor_name
import org.openforis.collectone.v2.web.resources.formeditor_name_supporting_text
import org.openforis.collectone.v2.web.resources.formeditor_next
import org.openforis.collectone.v2.web.resources.formeditor_no_validation_rules
import org.openforis.collectone.v2.web.resources.formeditor_none
import org.openforis.collectone.v2.web.resources.formeditor_not_set
import org.openforis.collectone.v2.web.resources.formeditor_notes_cant_be_required
import org.openforis.collectone.v2.web.resources.formeditor_ok
import org.openforis.collectone.v2.web.resources.formeditor_pattern
import org.openforis.collectone.v2.web.resources.formeditor_pick_field
import org.openforis.collectone.v2.web.resources.formeditor_previous
import org.openforis.collectone.v2.web.resources.formeditor_question_options
import org.openforis.collectone.v2.web.resources.formeditor_question_position
import org.openforis.collectone.v2.web.resources.formeditor_question_type
import org.openforis.collectone.v2.web.resources.formeditor_regular_expression
import org.openforis.collectone.v2.web.resources.formeditor_required
import org.openforis.collectone.v2.web.resources.formeditor_required_description
import org.openforis.collectone.v2.web.resources.formeditor_validation
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.SectionLabel

// ---------------------------------------------------------------------------------------------
// Properties panel
// ---------------------------------------------------------------------------------------------

/** Properties of the selected question, or of the Form itself when no question is selected. */
@Composable
internal fun QuestionPropertiesPanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onSaveToModeChange: (SaveToMode) -> Unit,
  onOpenDataset: ((String) -> Unit)?,
  onCreateDataset: (() -> Unit)? = null,
  onUnlinkDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
  templateDetails: (@Composable () -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    val question = uiState.selectedQuestion
    val selectedSection = uiState.selectedSection
    if (question == null) {
      if (selectedSection != null) {
        SectionProperties(
          uiState = uiState,
          actions = actions,
          section = selectedSection,
        )
        return@ElevatedCard
      }
      FormProperties(
        uiState = uiState,
        actions = actions,
        onSaveToModeChange = onSaveToModeChange,
        onOpenDataset = onOpenDataset,
        onCreateDataset = onCreateDataset,
        onUnlinkDataset = onUnlinkDataset,
        templateDetails = templateDetails,
      )
      return@ElevatedCard
    }
    key(question.key) {
      QuestionProperties(
        uiState = uiState,
        actions = actions,
        question = question,
        onOpenDataset = onOpenDataset,
        onCreateFormForDataset = onCreateFormForDataset,
        onOpenForm = onOpenForm,
        onCreateChoiceDataset = onCreateChoiceDataset,
      )
    }
  }
}

@Composable
private fun QuestionProperties(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
  onOpenDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
) {
  val form = uiState.form
  val index = uiState.selectedIndex
  val key = question.key
  val issues = uiState.issuesFor(key)
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    if (confirmingDelete) {
      DeleteQuestionDialog(
        warning = uiState.deleteWarningFor(question),
        onConfirm = {
          confirmingDelete = false
          actions.deleteQuestion(key)
        },
        onDismiss = { confirmingDelete = false },
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text =
          stringResource(Res.string.formeditor_question_position, index + 1, form.questions.size),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.weight(1f))
      TextButton(
        onClick = { actions.select(form.questions.getOrNull(index - 1)?.key) },
        enabled = index > 0,
      ) {
        Icon(
          Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(stringResource(Res.string.formeditor_previous))
      }
      TextButton(
        onClick = { actions.select(form.questions.getOrNull(index + 1)?.key) },
        enabled = index < form.questions.lastIndex,
      ) {
        Text(stringResource(Res.string.formeditor_next))
        Spacer(Modifier.width(4.dp))
        Icon(
          Icons.AutoMirrored.Outlined.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      }
      if (!uiState.isReadOnly) {
        Box {
          IconButton(onClick = { menuExpanded = true }) {
            Icon(
              Icons.Outlined.MoreVert,
              contentDescription = stringResource(Res.string.formeditor_question_options),
            )
          }
          val hasDependents =
            form.questions.any {
              it.relevance?.sourceQuestionKey == key || it.parentQuestionKey == key
            }
          DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(Res.string.formeditor_duplicate)) },
              leadingIcon = {
                Icon(
                  Icons.Outlined.ContentCopy,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              onClick = {
                menuExpanded = false
                actions.duplicateQuestion(key)
              },
            )
            if (hasDependents) {
              DropdownMenuItem(
                text = { Text(stringResource(Res.string.formeditor_duplicate_branch)) },
                leadingIcon = {
                  Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                },
                onClick = {
                  menuExpanded = false
                  actions.duplicateQuestionBranch(key)
                },
              )
            }
            DropdownMenuItem(
              text = {
                Text(
                  stringResource(Res.string.formeditor_delete),
                  color = MaterialTheme.colorScheme.error,
                )
              },
              leadingIcon = {
                Icon(
                  Icons.Outlined.Delete,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp),
                )
              },
              onClick = {
                menuExpanded = false
                confirmingDelete = true
              },
            )
          }
        }
      }
    }

    if (issues.isNotEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
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
    ConceptWarnings(uiState.warningsFor(key))

    DropdownSelector(
      label = stringResource(Res.string.formeditor_question_type),
      selectedText = stringResource(question.type.labelRes),
      options = EditorQuestionType.entries,
      optionText = { stringResource(it.labelRes) },
      optionIcon = { questionTypeIcon(it) },
      optionEnabled = uiState::isQuestionTypeAvailable,
      selectedIcon = questionTypeIcon(question.type),
      enabled = !uiState.isReadOnly,
      onSelect = { actions.changeType(key, it) },
    )

    if (form.sections.isNotEmpty()) {
      QuestionSectionSelector(uiState, actions, question)
    }

    if (question.type.isGeometry) {
      GeometryCaptureSelector(uiState, actions, question)
    }
    if (question.type.isMedia) {
      MediaSourceSelector(uiState, actions, question)
    }

    ConceptLabelField(uiState, actions, question)
    ConceptLinkChip(uiState, actions, question)
    val activeLang = uiState.activeLanguage
    val defaultLang = uiState.defaultLanguage
    val isDefaultLang = uiState.isDefaultLanguageSelected
    val activeLangName = languageDisplayName(activeLang)
    OutlinedTextField(
      value = question.hintIn(activeLang, defaultLang),
      onValueChange = { v -> actions.updateQuestionHint(key, v) },
      enabled = !uiState.isReadOnly,
      label = {
        Text(
          if (isDefaultLang) stringResource(Res.string.formeditor_hint_optional)
          else stringResource(Res.string.formeditor_hint_in_language_optional, activeLangName)
        )
      },
      placeholder =
        if (!isDefaultLang && question.hint.isNotBlank()) ({ Text(question.hint) }) else null,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.formeditor_required),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(
          text =
            if (question.type.isReadOnly)
              stringResource(Res.string.formeditor_notes_cant_be_required)
            else stringResource(Res.string.formeditor_required_description),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = question.required,
        enabled = !uiState.isReadOnly && !question.type.isReadOnly,
        onCheckedChange = { v -> actions.updateQuestion(key) { it.copy(required = v) } },
      )
    }

    if (question.type.hasChoices) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      ChoicesEditor(
        uiState = uiState,
        actions = actions,
        question = question,
        onOpenDataset = onOpenDataset,
        onCreateFormForDataset = onCreateFormForDataset,
        onOpenForm = onOpenForm,
        onCreateChoiceDataset = onCreateChoiceDataset,
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    DisplayLogicEditor(uiState, actions, question)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AdvancedSection(uiState, actions, question)

    if (!uiState.isReadOnly) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      OutlinedButton(
        onClick = { confirmingDelete = true },
        colors =
          ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.formeditor_delete_question))
      }
    }
  }
}

/**
 * **Decimal places** setting of a [EditorQuestionType.NUMBER] question: `0` for whole numbers
 * (integer), or `1`..[EditorQuestion.MAX_DECIMAL_PLACES] for decimal numbers.
 */
@Composable
private fun DecimalPlacesSelector(
  actions: QuestionActions,
  question: EditorQuestion,
  enabled: Boolean = true,
) {
  OutlinedTextField(
    value = question.decimalPlaces.toString(),
    onValueChange = { v ->
      val parsed =
        if (v.isBlank()) 0
        else v.trim().toIntOrNull()?.coerceIn(0, EditorQuestion.MAX_DECIMAL_PLACES)
      if (parsed != null) {
        actions.updateDecimalPlaces(question.key, parsed)
      }
    },
    enabled = enabled,
    label = { Text(stringResource(Res.string.formeditor_decimal_places)) },
    supportingText = {
      Text(
        if (question.decimalPlaces <= 0) stringResource(Res.string.formeditor_decimal_places_zero)
        else
          pluralStringResource(
            Res.plurals.formeditor_decimal_places_up_to,
            question.decimalPlaces,
            question.decimalPlaces,
          )
      )
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun geometryCaptureLabel(type: EditorQuestionType, capture: GeometryCapture): String =
  when (capture) {
    GeometryCapture.GPS_ONLY ->
      when (type) {
        EditorQuestionType.POLYGON -> stringResource(Res.string.formeditor_capture_walk_boundary)
        EditorQuestionType.LINE -> stringResource(Res.string.formeditor_capture_walk_path)
        else -> stringResource(GeometryCapture.GPS_ONLY.labelRes)
      }
    GeometryCapture.GPS_OR_MAP -> stringResource(GeometryCapture.GPS_OR_MAP.labelRes)
  }

@Composable
private fun geometryCaptureDescription(
  type: EditorQuestionType,
  capture: GeometryCapture,
): String =
  when (capture) {
    GeometryCapture.GPS_ONLY ->
      when (type) {
        EditorQuestionType.POLYGON ->
          stringResource(Res.string.formeditor_capture_walk_boundary_description)
        EditorQuestionType.LINE ->
          stringResource(Res.string.formeditor_capture_walk_path_description)
        else -> stringResource(GeometryCapture.GPS_ONLY.descriptionRes)
      }
    GeometryCapture.GPS_OR_MAP -> stringResource(GeometryCapture.GPS_OR_MAP.descriptionRes)
  }

/**
 * **Capture** setting of a geometry question: GPS only, or GPS or draw on the map. Shows the chosen
 * mode's description and, when GPS only would leave the Form unanswerable in the web dashboard, the
 * incompatibility error (also listed in the question's issues).
 */
@Composable
private fun GeometryCaptureSelector(
  uiState: FormEditorUiState,
  actions: QuestionActions,
  question: EditorQuestion,
) {
  val colors = MaterialTheme.colorScheme
  val captureLabels =
    GeometryCapture.entries.associateWith { geometryCaptureLabel(question.type, it) }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    DropdownSelector(
      label = stringResource(Res.string.formeditor_capture),
      selectedText = captureLabels.getValue(question.capture),
      options = GeometryCapture.entries,
      optionText = { captureLabels.getValue(it) },
      enabled = !uiState.isReadOnly,
      onSelect = { actions.updateCapture(question.key, it) },
    )
    Text(
      text = geometryCaptureDescription(question.type, question.capture),
      style = MaterialTheme.typography.bodySmall,
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
  }
}

/**
 * **Media source** setting of a photo, video, or audio question: capture or upload (the ODK
 * default), or capture only (the `new` appearance). Shows the chosen mode's description.
 */
@Composable
private fun MediaSourceSelector(
  uiState: FormEditorUiState,
  actions: QuestionActions,
  question: EditorQuestion,
) {
  val sourceLabels = MediaSource.entries.associateWith { stringResource(it.labelRes) }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    DropdownSelector(
      label = stringResource(Res.string.formeditor_media_source),
      selectedText = sourceLabels.getValue(question.mediaSource),
      options = MediaSource.entries,
      optionText = { sourceLabels.getValue(it) },
      enabled = !uiState.isReadOnly,
      onSelect = { actions.updateMediaSource(question.key, it) },
    )
    Text(
      text = stringResource(question.mediaSource.descriptionRes),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
  }
}

/**
 * Collapsible section for settings most form authors don't need: the data column name and
 * validation rules. Collapsed by default; expands automatically when it contains an error.
 */
@Composable
private fun AdvancedSection(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val key = question.key
  val nameInvalid = !FormEditorValidator.isValidName(question.name)
  val hasError = nameInvalid || ValidationRules.issues(question).isNotEmpty()
  // Open/closed lives in the editor state so it persists across question selection.
  val expanded = uiState.isAdvancedExpanded
  LaunchedEffect(key, hasError) { if (hasError) actions.setAdvancedExpanded(true) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .clickable { actions.setAdvancedExpanded(!expanded) }
          .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SectionLabel(stringResource(Res.string.formeditor_advanced))
      if (hasError) {
        Icon(
          Icons.Outlined.Warning,
          contentDescription = stringResource(Res.string.formeditor_advanced_has_errors),
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp),
        )
      }
      Spacer(Modifier.weight(1f))
      Icon(
        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription =
          if (expanded) stringResource(Res.string.formeditor_collapse_advanced)
          else stringResource(Res.string.formeditor_expand_advanced),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (expanded) {
      LinkToStandardFieldButton(actions, question, enabled = !uiState.isReadOnly)
      OutlinedTextField(
        value = question.name,
        onValueChange = { v -> actions.updateQuestion(key) { it.copy(name = v.trim()) } },
        enabled = !uiState.isReadOnly,
        label = { Text(stringResource(Res.string.formeditor_name)) },
        singleLine = true,
        isError = !uiState.isReadOnly && nameInvalid,
        supportingText = { Text(stringResource(Res.string.formeditor_name_supporting_text)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )
      if (question.type.isNumeric) {
        DecimalPlacesSelector(actions, question, enabled = !uiState.isReadOnly)
      }
      ValidationEditor(uiState, actions, question)
    }
  }
}

/** The localized [label], or [none] when the setting has no value. */
@Composable
private fun labelOrNone(label: StringResource?, none: String): String =
  label?.let { stringResource(it) } ?: none

/**
 * Structured validation rules, compiled to the XForms bind `constraint` and `jr:constraintMsg` (ODK
 * XForms spec, "Bindings"). Only rules that apply to the question type are offered.
 */
@Composable
private fun ValidationEditor(
  uiState: FormEditorUiState,
  actions: QuestionActions,
  question: EditorQuestion,
) {
  val key = question.key
  val type = question.type
  val validation = question.validation ?: EditorValidation()
  fun update(transform: (EditorValidation) -> EditorValidation) =
    actions.updateValidation(key, transform)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(Res.string.formeditor_validation),
      style = MaterialTheme.typography.titleSmall,
    )
    when (ValidationKind.of(type)) {
      null ->
        Text(
          text = stringResource(Res.string.formeditor_no_validation_rules),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      ValidationKind.NUMBER_RANGE ->
        MinMaxFields(
          stringResource(Res.string.formeditor_minimum),
          stringResource(Res.string.formeditor_maximum),
          validation,
          ::update,
          enabled = !uiState.isReadOnly,
        )
      ValidationKind.TEXT -> {
        MinMaxFields(
          stringResource(Res.string.formeditor_min_length),
          stringResource(Res.string.formeditor_max_length),
          validation,
          ::update,
          enabled = !uiState.isReadOnly,
        )
        val noneText = stringResource(Res.string.formeditor_none)
        DropdownSelector(
          label = stringResource(Res.string.formeditor_pattern),
          selectedText = labelOrNone(validation.pattern?.labelRes, noneText),
          options = listOf<TextPattern?>(null) + TextPattern.entries,
          optionText = { labelOrNone(it?.labelRes, noneText) },
          onSelect = { p -> update { it.copy(pattern = p) } },
          enabled = !uiState.isReadOnly,
          modifier = Modifier.fillMaxWidth(),
        )
        if (validation.pattern == TextPattern.CUSTOM) {
          OutlinedTextField(
            value = validation.customPattern,
            onValueChange = { v -> update { it.copy(customPattern = v) } },
            enabled = !uiState.isReadOnly,
            label = { Text(stringResource(Res.string.formeditor_regular_expression)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      ValidationKind.DATE -> {
        val anyDateText = stringResource(Res.string.formeditor_any_date)
        DropdownSelector(
          label = stringResource(Res.string.formeditor_allowed_dates),
          selectedText = labelOrNone(validation.dateRule?.labelRes, anyDateText),
          options = listOf<DateRule?>(null) + DateRule.entries,
          optionText = { labelOrNone(it?.labelRes, anyDateText) },
          onSelect = { r ->
            update {
              if (r == DateRule.BETWEEN) it.copy(dateRule = r)
              else it.copy(dateRule = r, min = "", max = "")
            }
          },
          enabled = !uiState.isReadOnly,
          modifier = Modifier.fillMaxWidth(),
        )
        if (validation.dateRule == DateRule.BETWEEN) {
          DateRangeFields(validation, ::update, enabled = !uiState.isReadOnly)
        }
      }
      ValidationKind.SELECTION_COUNT ->
        MinMaxFields(
          stringResource(Res.string.formeditor_minimum_selections),
          stringResource(Res.string.formeditor_maximum_selections),
          validation,
          ::update,
          enabled = !uiState.isReadOnly,
        )
    }
    if (ValidationRules.isActive(type, question.validation)) {
      val summary = validationSummaryText(type, question.validation)?.resolve().orEmpty()
      Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedTextField(
        value = validation.message,
        onValueChange = { v -> update { it.copy(message = v) } },
        enabled = !uiState.isReadOnly,
        label = { Text(stringResource(Res.string.formeditor_error_message_optional)) },
        placeholder = { Text(summary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/**
 * Earliest / latest date for [DateRule.BETWEEN], chosen with M3 date pickers. Values are stored as
 * ISO `YYYY-MM-DD` (what XForms `date()` expects) and shown in a friendly format.
 */
@Composable
private fun DateRangeFields(
  validation: EditorValidation,
  update: ((EditorValidation) -> EditorValidation) -> Unit,
  enabled: Boolean = true,
) {
  val minMillis = isoDateToUtcMillis(validation.min)
  val maxMillis = isoDateToUtcMillis(validation.max)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    DatePickerField(
      label = stringResource(Res.string.formeditor_earliest_date),
      iso = validation.min,
      // Keep earliest ≤ latest.
      isSelectable = { maxMillis == null || it <= maxMillis },
      onPick = { v -> update { it.copy(min = v) } },
      enabled = enabled,
    )
    DatePickerField(
      label = stringResource(Res.string.formeditor_latest_date),
      iso = validation.max,
      isSelectable = { minMillis == null || it >= minMillis },
      onPick = { v -> update { it.copy(max = v) } },
      enabled = enabled,
    )
  }
}

/**
 * Read-only field showing [iso] in the user's language, e.g. "Mar 5, 2026"; the calendar icon opens
 * a [DatePickerDialog] and the clear icon unsets the date. [onPick] receives ISO `YYYY-MM-DD`, or
 * "" when cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
  label: String,
  iso: String,
  isSelectable: (utcMillis: Long) -> Boolean,
  onPick: (String) -> Unit,
  enabled: Boolean = true,
) {
  var open by remember { mutableStateOf(false) }
  OutlinedTextField(
    value = if (iso.isEmpty()) "" else isoDateText(iso).resolve(),
    onValueChange = {},
    readOnly = true,
    enabled = enabled,
    singleLine = true,
    label = { Text(label) },
    placeholder = { Text(stringResource(Res.string.formeditor_not_set)) },
    trailingIcon =
      if (enabled) {
        {
          Row {
            if (iso.isNotEmpty()) {
              IconButton(onClick = { onPick("") }) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = stringResource(Res.string.formeditor_clear_field, label),
                )
              }
            }
            IconButton(onClick = { open = true }) {
              Icon(
                Icons.Outlined.DateRange,
                contentDescription = stringResource(Res.string.formeditor_pick_field, label),
              )
            }
          }
        }
      } else {
        null
      },
    modifier = Modifier.fillMaxWidth(),
  )
  if (open && enabled) {
    val pickerState =
      rememberDatePickerState(
        initialSelectedDateMillis = isoDateToUtcMillis(iso),
        selectableDates =
          object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = isSelectable(utcTimeMillis)
          },
      )
    DatePickerDialog(
      onDismissRequest = { open = false },
      confirmButton = {
        TextButton(
          onClick = {
            // DatePicker reports the selection as UTC midnight of the chosen day.
            pickerState.selectedDateMillis?.let { onPick(utcMillisToIsoDate(it)) }
            open = false
          },
          enabled = pickerState.selectedDateMillis != null,
        ) {
          Text(stringResource(Res.string.formeditor_ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { open = false }) {
          Text(stringResource(Res.string.formeditor_cancel))
        }
      },
    ) {
      DatePicker(state = pickerState)
    }
  }
}

/** A pair of side-by-side fields editing [EditorValidation.min] and [EditorValidation.max]. */
@Composable
private fun MinMaxFields(
  minLabel: String,
  maxLabel: String,
  validation: EditorValidation,
  update: ((EditorValidation) -> EditorValidation) -> Unit,
  enabled: Boolean = true,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = validation.min,
      onValueChange = { v -> update { it.copy(min = v.trim()) } },
      enabled = enabled,
      label = { Text(minLabel) },
      singleLine = true,
      modifier = Modifier.weight(1f),
    )
    OutlinedTextField(
      value = validation.max,
      onValueChange = { v -> update { it.copy(max = v.trim()) } },
      enabled = enabled,
      label = { Text(maxLabel) },
      singleLine = true,
      modifier = Modifier.weight(1f),
    )
  }
}
