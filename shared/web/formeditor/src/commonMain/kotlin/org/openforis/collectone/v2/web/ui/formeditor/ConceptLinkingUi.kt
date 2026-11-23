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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.editor.ConceptLinking
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssue
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.ui.common.displayMessage
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_label_to_dictionary
import org.openforis.collectone.v2.web.resources.formeditor_add_label_to_organization_dictionary
import org.openforis.collectone.v2.web.resources.formeditor_cancel
import org.openforis.collectone.v2.web.resources.formeditor_concept_id_version
import org.openforis.collectone.v2.web.resources.formeditor_concept_not_in_dictionary
import org.openforis.collectone.v2.web.resources.formeditor_concept_reference
import org.openforis.collectone.v2.web.resources.formeditor_concept_type_answers
import org.openforis.collectone.v2.web.resources.formeditor_concept_unit
import org.openforis.collectone.v2.web.resources.formeditor_delete
import org.openforis.collectone.v2.web.resources.formeditor_delete_linked_question_message
import org.openforis.collectone.v2.web.resources.formeditor_delete_linked_question_title
import org.openforis.collectone.v2.web.resources.formeditor_delete_question_message
import org.openforis.collectone.v2.web.resources.formeditor_delete_question_title
import org.openforis.collectone.v2.web.resources.formeditor_delete_warning_feeds_impact_report
import org.openforis.collectone.v2.web.resources.formeditor_delete_warning_linked_standard_field
import org.openforis.collectone.v2.web.resources.formeditor_dismiss
import org.openforis.collectone.v2.web.resources.formeditor_import_matches_title
import org.openforis.collectone.v2.web.resources.formeditor_label
import org.openforis.collectone.v2.web.resources.formeditor_label_in_language
import org.openforis.collectone.v2.web.resources.formeditor_link_all
import org.openforis.collectone.v2.web.resources.formeditor_link_selected
import org.openforis.collectone.v2.web.resources.formeditor_link_to_standard_field_button
import org.openforis.collectone.v2.web.resources.formeditor_link_to_standard_field_title
import org.openforis.collectone.v2.web.resources.formeditor_linked_fields_explainer
import org.openforis.collectone.v2.web.resources.formeditor_list_and
import org.openforis.collectone.v2.web.resources.formeditor_no_dictionary_yet
import org.openforis.collectone.v2.web.resources.formeditor_no_standard_fields_available
import org.openforis.collectone.v2.web.resources.formeditor_no_standard_fields_match
import org.openforis.collectone.v2.web.resources.formeditor_not_matched
import org.openforis.collectone.v2.web.resources.formeditor_not_now
import org.openforis.collectone.v2.web.resources.formeditor_note_text
import org.openforis.collectone.v2.web.resources.formeditor_note_text_in_language
import org.openforis.collectone.v2.web.resources.formeditor_search_the_dictionary
import org.openforis.collectone.v2.web.resources.formeditor_standard_fields
import org.openforis.collectone.v2.web.resources.formeditor_standard_value
import org.openforis.collectone.v2.web.resources.formeditor_template_apply_selected
import org.openforis.collectone.v2.web.resources.formeditor_template_remove_question
import org.openforis.collectone.v2.web.resources.formeditor_template_removed_questions
import org.openforis.collectone.v2.web.resources.formeditor_template_review_changes
import org.openforis.collectone.v2.web.resources.formeditor_template_review_description
import org.openforis.collectone.v2.web.resources.formeditor_template_review_title
import org.openforis.collectone.v2.web.resources.formeditor_template_suggested_additions
import org.openforis.collectone.v2.web.resources.formeditor_template_update_available
import org.openforis.collectone.v2.web.resources.formeditor_template_update_summary
import org.openforis.collectone.v2.web.resources.formeditor_unlink_standard_field
import org.openforis.collectone.v2.web.resources.formeditor_use_standard_label
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.IsoLanguages

/** How long typing must pause before the label is searched for standard fields. */
private const val SUGGESTION_DEBOUNCE_MS = 250L

/**
 * The question's **Label** field with standard-field autocomplete (see
 * `docs/technical/model/library/01-concepts.md`, "Linking Form Fields"): after the designer pauses
 * typing, up to five matching dictionary concepts appear under the field. The list never takes
 * focus or links anything by itself; Esc or clicking elsewhere dismisses it.
 */
@Composable
internal fun ConceptLabelField(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val key = question.key
  val isNote = question.type == EditorQuestionType.NOTE
  val activeLang = uiState.activeLanguage
  val defaultLang = uiState.defaultLanguage
  val isDefaultLang = uiState.isDefaultLanguageSelected
  val currentLabel = question.labelIn(activeLang, defaultLang)
  val activeLangName = IsoLanguages.findByCode(activeLang)?.name ?: activeLang
  // Only the designer's typing searches; selecting a question with a label doesn't.
  var edited by remember(key, activeLang) { mutableStateOf(false) }
  LaunchedEffect(key, activeLang, currentLabel, edited, uiState.isReadOnly) {
    if (!edited || isNote || uiState.isReadOnly) return@LaunchedEffect
    delay(SUGGESTION_DEBOUNCE_MS)
    actions.requestConceptSuggestions(key, currentLabel)
  }
  val suggestions =
    uiState.conceptSuggestions?.takeIf {
      it.questionKey == key && !it.isExplicitSearch && !uiState.isReadOnly
    }
  Box {
    OutlinedTextField(
      value = currentLabel,
      onValueChange = { v ->
        edited = true
        actions.updateQuestionLabel(key, v)
      },
      enabled = !uiState.isReadOnly,
      label = {
        Text(
          when {
            isDefaultLang ->
              if (isNote) stringResource(Res.string.formeditor_note_text)
              else stringResource(Res.string.formeditor_label)
            else ->
              if (isNote)
                stringResource(Res.string.formeditor_note_text_in_language, activeLangName)
              else stringResource(Res.string.formeditor_label_in_language, activeLangName)
          }
        )
      },
      placeholder =
        if (!isDefaultLang && question.label.isNotBlank()) {
          { Text(question.label) }
        } else {
          null
        },
      minLines = 2,
      modifier =
        Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
          if (
            suggestions != null && event.type == KeyEventType.KeyDown && event.key == Key.Escape
          ) {
            actions.dismissConceptSuggestions()
            true
          } else {
            false
          }
        },
    )
    DropdownMenu(
      expanded = suggestions != null,
      onDismissRequest = actions::dismissConceptSuggestions,
      // Non-focusable, so typing continues in the Label field while the list is open.
      properties = PopupProperties(focusable = false),
      modifier = Modifier.widthIn(min = 280.dp, max = 420.dp),
    ) {
      if (suggestions != null) {
        Text(
          text = stringResource(Res.string.formeditor_standard_fields),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        SuggestionItems(suggestions, uiState, actions)
      }
    }
  }
}

/** Suggestion rows, then the "Add to dictionary" row when the user may add concepts. */
@Composable
private fun SuggestionItems(
  suggestions: ConceptSuggestionsState,
  uiState: FormEditorUiState,
  actions: ConceptLinkActions,
  onError: (FormEditorMessage) -> Unit = {},
) {
  suggestions.suggestions.forEach { suggestion ->
    DropdownMenuItem(
      text = { SuggestionRow(suggestion) },
      leadingIcon = {
        Icon(
          questionTypeIcon(ConceptLinking.questionTypeFor(suggestion.concept.dataType)),
          contentDescription = stringResource(suggestion.concept.dataType.labelRes),
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      onClick = { actions.linkConcept(suggestions.questionKey, suggestion.concept.id) },
    )
  }
  suggestions.addToDictionaryLabel?.let { label ->
    if (suggestions.suggestions.isNotEmpty()) HorizontalDivider()
    val organization = uiState.library.organizationName
    val addText =
      if (organization != null) {
        stringResource(Res.string.formeditor_add_label_to_dictionary, label, organization)
      } else {
        stringResource(Res.string.formeditor_add_label_to_organization_dictionary, label)
      }
    DropdownMenuItem(
      text = {
        Text(
          text = addText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
      onClick = {
        actions.addLabelToDictionary(suggestions.questionKey)?.let(onError)
        actions.dismissConceptSuggestions()
      },
    )
  }
}

@Composable
private fun SuggestionRow(suggestion: ConceptSuggestion) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = suggestion.label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      TonalBadge(text = suggestion.sourceLabel, tone = BadgeTone.SECONDARY)
    }
    val details =
      listOfNotNull(
          suggestion.description.takeIf { it.isNotBlank() },
          stringResource(
              Res.string.formeditor_concept_type_answers,
              stringResource(suggestion.concept.dataType.labelRes),
            )
            .takeUnless { suggestion.isTypeCompatible },
        )
        .joinToString(" · ")
    if (details.isNotEmpty()) {
      Text(
        text = details,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * The linked standard field of [question]: a chip `<Source> · <Label>` whose tooltip shows the
 * concept's definition and references, with **Unlink** and, when the question's label differs,
 * **Use standard label**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConceptLinkChip(
  uiState: FormEditorUiState,
  actions: ConceptLinkActions,
  question: EditorQuestion,
) {
  val link = question.conceptLink ?: return
  val concept = uiState.conceptOf(question)
  val language = uiState.activeLanguage
  val standardLabel = concept?.label?.get(language)
  val currentQuestionLabel = question.displayLabel(uiState.activeLanguage, uiState.defaultLanguage)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TooltipBox(
        positionProvider =
          TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(conceptTooltip(concept, language, link.conceptId)) } },
        state = rememberTooltipState(),
        modifier = Modifier.weight(1f, fill = false),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
              text =
                if (concept == null) link.conceptId
                else "${uiState.library.sourceLabel(concept)} · $standardLabel",
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      if (!uiState.isReadOnly) {
        IconButton(onClick = { actions.unlinkConcept(question.key) }) {
          Icon(
            Icons.Outlined.LinkOff,
            contentDescription = stringResource(Res.string.formeditor_unlink_standard_field),
          )
        }
      }
    }
    if (
      !uiState.isReadOnly &&
        standardLabel != null &&
        standardLabel.isNotBlank() &&
        standardLabel != currentQuestionLabel
    ) {
      TextButton(onClick = { actions.useStandardLabel(question.key) }) {
        Text(
          stringResource(Res.string.formeditor_use_standard_label, standardLabel),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun conceptTooltip(concept: LibraryConcept?, language: String, conceptId: String): String {
  if (concept == null)
    return stringResource(Res.string.formeditor_concept_not_in_dictionary, conceptId)
  val unit = ConceptLinking.unitLabel(concept.unit)
  return listOfNotNull(
      concept.description.get(language).takeIf { it.isNotBlank() },
      if (unit.isNotBlank()) stringResource(Res.string.formeditor_concept_unit, unit) else null,
      if (concept.references.isNotEmpty()) {
        stringResource(Res.string.formeditor_concept_reference, concept.references.joinToString())
      } else {
        null
      },
      stringResource(
        Res.string.formeditor_concept_id_version,
        if (concept.id.startsWith("org.")) concept.id.substringAfterLast('.') else concept.id,
        concept.version.toString(),
      ),
    )
    .joinToString("\n")
}

/** **Link to standard field…**, which opens [ConceptSearchDialog] for [question]. */
@Composable
internal fun LinkToStandardFieldButton(
  actions: ConceptLinkActions,
  question: EditorQuestion,
  enabled: Boolean = true,
) {
  if (!enabled || question.conceptLink != null || question.type == EditorQuestionType.NOTE) return
  OutlinedButton(onClick = { actions.openConceptSearch(question.key) }) {
    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(stringResource(Res.string.formeditor_link_to_standard_field_button))
  }
}

/** Searching the dictionary explicitly, from [LinkToStandardFieldButton]. */
@Composable
internal fun ConceptSearchDialog(uiState: FormEditorUiState, actions: ConceptLinkActions) {
  val search = uiState.conceptSuggestions?.takeIf { it.isExplicitSearch } ?: return
  var error by remember { mutableStateOf<FormEditorMessage?>(null) }
  AlertDialog(
    onDismissRequest = actions::dismissConceptSuggestions,
    title = { Text(stringResource(Res.string.formeditor_link_to_standard_field_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = search.query,
          onValueChange = actions::updateConceptSearchQuery,
          label = { Text(stringResource(Res.string.formeditor_search_the_dictionary)) },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
          if (search.suggestions.isEmpty()) {
            val trimmedQuery = search.query.trim()
            Text(
              text =
                when {
                  uiState.library.library.concepts.isEmpty() ->
                    stringResource(Res.string.formeditor_no_dictionary_yet)
                  trimmedQuery.isEmpty() ->
                    stringResource(Res.string.formeditor_no_standard_fields_available)
                  else ->
                    stringResource(Res.string.formeditor_no_standard_fields_match, trimmedQuery)
                },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }
          SuggestionItems(search, uiState, actions, onError = { error = it })
        }
        error?.let {
          Text(
            it.text(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = actions::dismissConceptSuggestions) {
        Text(stringResource(Res.string.formeditor_cancel))
      }
    },
  )
}

/** Concept link warnings for one question. They're advice and never block publishing. */
@Composable
internal fun ConceptWarnings(warnings: List<EditorIssue>) {
  if (warnings.isEmpty()) return
  Surface(
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        warnings.forEach { Text(it.displayMessage(), style = MaterialTheme.typography.bodySmall) }
      }
    }
  }
}

/**
 * Which code-list value of [concept] manual [choice] means (`collectone_code`), so answers can be
 * added up across Forms.
 */
@Composable
internal fun ChoiceCodeSelector(
  concept: LibraryConcept,
  choice: EditorChoice,
  language: String,
  onSelect: (String?) -> Unit,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val selected = concept.codeList.firstOrNull { it.code == choice.code }
  val notMatched = stringResource(Res.string.formeditor_not_matched)
  DropdownSelector(
    label = stringResource(Res.string.formeditor_standard_value),
    selectedText = selected?.label?.get(language) ?: choice.code ?: notMatched,
    options = listOf(null) + concept.codeList,
    optionText = { item -> item?.let { "${it.label.get(language)} (${it.code})" } ?: notMatched },
    onSelect = { item -> onSelect(item?.code) },
    enabled = enabled,
    modifier = modifier,
  )
}

/**
 * Confirmation before soft-deleting a question: explains that the question will be flagged as
 * deleted, hidden from collectors once devices sync, and that collected data is preserved, plus
 * [warning] when the question is linked to a standard field or impact report.
 */
@Composable
internal fun DeleteQuestionDialog(
  warning: LinkedQuestionDeleteWarning? = null,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          if (warning != null) Res.string.formeditor_delete_linked_question_title
          else Res.string.formeditor_delete_question_title
        )
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.formeditor_delete_question_message))
        if (warning != null) {
          Text(stringResource(Res.string.formeditor_delete_linked_question_message, warning.text()))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(Res.string.formeditor_delete), color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.formeditor_cancel)) }
    },
  )
}

/** Soft confirmation before deleting a linked question; [warning] says what it feeds. */
@Composable
internal fun DeleteLinkedQuestionDialog(
  warning: LinkedQuestionDeleteWarning,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  DeleteQuestionDialog(warning = warning, onConfirm = onConfirm, onDismiss = onDismiss)
}

/**
 * After an import: "We found N fields that match standard definitions", with strong matches
 * pre-checked. Linking is always the designer's choice.
 */
@Composable
internal fun ImportMatchesCard(
  uiState: FormEditorUiState,
  actions: ConceptLinkActions,
  modifier: Modifier = Modifier,
) {
  if (uiState.isReadOnly) return
  val matches = uiState.importMatches
  if (matches.isEmpty()) return
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text =
            pluralStringResource(
              Res.plurals.formeditor_import_matches_title,
              matches.size,
              matches.size,
            ),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions::dismissImportMatches) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.formeditor_dismiss),
          )
        }
      }
      Text(
        text = stringResource(Res.string.formeditor_linked_fields_explainer),
        style = MaterialTheme.typography.bodySmall,
      )
      Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        matches.forEach { match ->
          Row(
            modifier =
              Modifier.fillMaxWidth().clickable {
                actions.setImportMatchChecked(match.questionKey, !match.isChecked)
              },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = match.isChecked,
              onCheckedChange = { actions.setImportMatchChecked(match.questionKey, it) },
            )
            Text(
              text = match.questionLabel,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = " → ${match.concept.label.get(uiState.library.language)}",
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            TonalBadge(text = match.sourceLabel, tone = BadgeTone.NEUTRAL)
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val checked = matches.count { it.isChecked }
        FilledTonalButton(onClick = actions::linkImportMatches, enabled = checked > 0) {
          Text(
            if (checked == matches.size) stringResource(Res.string.formeditor_link_all)
            else stringResource(Res.string.formeditor_link_selected, checked)
          )
        }
        TextButton(onClick = actions::dismissImportMatches) {
          Text(stringResource(Res.string.formeditor_not_now))
        }
      }
    }
  }
}

/** Display text of a Form editor message. */
@Suppress("SpreadOperator") // stringResource takes its format arguments only as varargs.
@Composable
internal fun FormEditorMessage.text(): String =
  when (this) {
    is FormEditorMessage.Resource -> stringResource(resource, *args.toTypedArray())
    is FormEditorMessage.Text -> text
  }

/** Display text of why deleting a linked question matters. */
@Composable
internal fun LinkedQuestionDeleteWarning.text(): String =
  when (this) {
    is LinkedQuestionDeleteWarning.FeedsImpactReport -> {
      var purposeList = purposes.first()
      for (purpose in purposes.drop(1)) {
        purposeList = stringResource(Res.string.formeditor_list_and, purposeList, purpose)
      }
      stringResource(Res.string.formeditor_delete_warning_feeds_impact_report, purposeList)
    }
    is LinkedQuestionDeleteWarning.LinkedToStandardField ->
      stringResource(Res.string.formeditor_delete_warning_linked_standard_field, name)
  }

/**
 * Human-in-the-loop review banner and dialog when a regulatory or library template (such as EUDR)
 * has suggested question additions or removals for this Form.
 */
@Composable
internal fun TemplateUpdateReviewCard(
  uiState: FormEditorUiState,
  actions: TemplateActions,
  modifier: Modifier = Modifier,
) {
  if (uiState.isReadOnly) return
  val diff = uiState.templateUpdateDiff ?: return
  var dismissedForTemplate by
    remember(diff.templateId, diff.addedQuestions.size, diff.removedQuestions.size) {
      mutableStateOf(false)
    }
  if (dismissedForTemplate) return
  var showDialog by remember(diff.templateId) { mutableStateOf(false) }
  var selectedAdditions by
    remember(diff) { mutableStateOf(diff.addedQuestions.map { it.name }.toSet()) }
  var selectedRemovals by remember(diff) { mutableStateOf(emptySet<String>()) }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            stringResource(Res.string.formeditor_template_update_available, diff.templateTitle),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text =
            stringResource(
              Res.string.formeditor_template_update_summary,
              diff.addedQuestions.size,
              diff.removedQuestions.size,
            ),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      FilledTonalButton(onClick = { showDialog = true }) {
        Text(stringResource(Res.string.formeditor_template_review_changes))
      }
      IconButton(onClick = { dismissedForTemplate = true }) {
        Icon(
          Icons.Outlined.Close,
          contentDescription = stringResource(Res.string.formeditor_dismiss),
        )
      }
    }
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = {
        Text(stringResource(Res.string.formeditor_template_review_title, diff.templateTitle))
      },
      text = {
        Column(
          modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(Res.string.formeditor_template_review_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (diff.addedQuestions.isNotEmpty()) {
            Text(
              text = stringResource(Res.string.formeditor_template_suggested_additions),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
            diff.addedQuestions.forEach { q ->
              val checked = q.name in selectedAdditions
              Row(
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    selectedAdditions =
                      if (checked) selectedAdditions - q.name else selectedAdditions + q.name
                  },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { isChecked ->
                    selectedAdditions =
                      if (isChecked) selectedAdditions + q.name else selectedAdditions - q.name
                  },
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = q.label.ifBlank { q.name },
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    text = "${stringResource(q.type.labelRes)} (${q.name})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          if (diff.removedQuestions.isNotEmpty()) {
            Text(
              text = stringResource(Res.string.formeditor_template_removed_questions),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
            diff.removedQuestions.forEach { q ->
              val checked = q.key in selectedRemovals
              Row(
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    selectedRemovals =
                      if (checked) selectedRemovals - q.key else selectedRemovals + q.key
                  },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { isChecked ->
                    selectedRemovals =
                      if (isChecked) selectedRemovals + q.key else selectedRemovals - q.key
                  },
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      stringResource(
                        Res.string.formeditor_template_remove_question,
                        q.label.ifBlank { q.name },
                      ),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    text = "${stringResource(q.type.labelRes)} (${q.name})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            val additions = diff.addedQuestions.filter { it.name in selectedAdditions }
            actions.applyTemplateUpdate(additions, selectedRemovals)
            showDialog = false
            dismissedForTemplate = true
          },
          enabled = selectedAdditions.isNotEmpty() || selectedRemovals.isNotEmpty(),
        ) {
          Text(stringResource(Res.string.formeditor_template_apply_selected))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDialog = false }) {
          Text(stringResource(Res.string.formeditor_cancel))
        }
      },
    )
  }
}
