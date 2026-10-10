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
package org.groundplatform.v2.devtools.prototypeapp.ui.formeditor

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ConceptLinking
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ConceptSuggestion
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ConceptSuggestionsState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.FormEditorUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.FormEditorActions

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
  // Only the designer's typing searches; selecting a question with a label doesn't.
  var edited by remember(key) { mutableStateOf(false) }
  LaunchedEffect(key, question.label, edited) {
    if (!edited || isNote) return@LaunchedEffect
    delay(SUGGESTION_DEBOUNCE_MS)
    actions.requestConceptSuggestions(key, question.label)
  }
  val suggestions =
    uiState.conceptSuggestions?.takeIf { it.questionKey == key && !it.isExplicitSearch }
  Box {
    OutlinedTextField(
      value = question.label,
      onValueChange = { v ->
        edited = true
        actions.updateQuestion(key) { it.copy(label = v) }
      },
      label = { Text(if (isNote) "Note text" else "Label") },
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
          text = "Standard fields",
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
  actions: FormEditorActions,
  onError: (String) -> Unit = {},
) {
  suggestions.suggestions.forEach { suggestion ->
    DropdownMenuItem(
      text = { SuggestionRow(suggestion) },
      leadingIcon = {
        Icon(
          questionTypeIcon(ConceptLinking.questionTypeFor(suggestion.concept.dataType)),
          contentDescription = suggestion.concept.dataType.label,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      onClick = { actions.linkConcept(suggestions.questionKey, suggestion.concept.id) },
    )
  }
  suggestions.addToDictionaryLabel?.let { label ->
    if (suggestions.suggestions.isNotEmpty()) HorizontalDivider()
    val organization = uiState.library.organizationName ?: "organization"
    DropdownMenuItem(
      text = {
        Text(
          text = "Add \"$label\" to $organization dictionary",
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
      GroundTonalBadge(text = suggestion.sourceLabel, tone = GroundBadgeTone.SECONDARY)
    }
    val details =
      listOfNotNull(
          suggestion.description.takeIf { it.isNotBlank() },
          "${suggestion.concept.dataType.label} answers".takeUnless { suggestion.isTypeCompatible },
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
  actions: FormEditorActions,
  question: EditorQuestion,
) {
  val link = question.conceptLink ?: return
  val concept = uiState.conceptOf(question)
  val language = uiState.library.language
  val standardLabel = concept?.label?.get(language)
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
      IconButton(onClick = { actions.unlinkConcept(question.key) }) {
        Icon(Icons.Outlined.LinkOff, contentDescription = "Unlink standard field")
      }
    }
    if (standardLabel != null && standardLabel.isNotBlank() && standardLabel != question.label) {
      TextButton(onClick = { actions.useStandardLabel(question.key) }) {
        Text(
          "Use standard label \"$standardLabel\"",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun conceptTooltip(concept: LibraryConcept?, language: String, conceptId: String): String {
  if (concept == null) return "$conceptId isn't in this survey's dictionary."
  return listOfNotNull(
      concept.description.get(language).takeIf { it.isNotBlank() },
      ConceptLinking.unitLabel(concept.unit).takeIf { it.isNotBlank() }?.let { "Unit: $it" },
      concept.references.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Reference: "),
      "${concept.id} · version ${concept.version}",
    )
    .joinToString("\n")
}

/** **Link to standard field…**, which opens [ConceptSearchDialog] for [question]. */
@Composable
internal fun LinkToStandardFieldButton(actions: FormEditorActions, question: EditorQuestion) {
  if (question.conceptLink != null || question.type == EditorQuestionType.NOTE) return
  OutlinedButton(onClick = { actions.openConceptSearch(question.key) }) {
    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text("Link to standard field…")
  }
}

/** Searching the dictionary explicitly, from [LinkToStandardFieldButton]. */
@Composable
internal fun ConceptSearchDialog(uiState: FormEditorUiState, actions: FormEditorActions) {
  val search = uiState.conceptSuggestions?.takeIf { it.isExplicitSearch } ?: return
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = actions::dismissConceptSuggestions,
    title = { Text("Link to standard field") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = search.query,
          onValueChange = actions::updateConceptSearchQuery,
          label = { Text("Search the dictionary") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
          if (search.suggestions.isEmpty()) {
            Text(
              text =
                if (uiState.library.library.concepts.isEmpty()) {
                  "This survey has no dictionary yet."
                } else {
                  "No standard fields match \"${search.query.trim()}\"."
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
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = actions::dismissConceptSuggestions) { Text("Cancel") } },
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
        warnings.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
      }
    }
  }
}

/**
 * Which code-list value of [concept] manual [choice] means (`ground_code`), so answers can be added
 * up across Forms.
 */
@Composable
internal fun ChoiceCodeSelector(
  concept: LibraryConcept,
  choice: EditorChoice,
  language: String,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selected = concept.codeList.firstOrNull { it.code == choice.code }
  DropdownSelector(
    label = "Standard value",
    selectedText = selected?.label?.get(language) ?: choice.code ?: "Not matched",
    options = listOf(null) + concept.codeList,
    optionText = { item ->
      item?.let { "${it.label.get(language)} (${it.code})" } ?: "Not matched"
    },
    onSelect = { item -> onSelect(item?.code) },
    modifier = modifier,
  )
}

/** Soft confirmation before deleting a linked question; [message] says what it feeds. */
@Composable
internal fun DeleteLinkedQuestionDialog(
  message: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete linked question?") },
    text = { Text("$message Deleting it removes these answers from reports.") },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/**
 * After an import: "We found N fields that match standard definitions", with strong matches
 * pre-checked. Linking is always the designer's choice.
 */
@Composable
internal fun ImportMatchesCard(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  modifier: Modifier = Modifier,
) {
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
            "We found ${matches.size} ${if (matches.size == 1) "field" else "fields"} that match " +
              "standard definitions",
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions::dismissImportMatches) {
          Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
        }
      }
      Text(
        text = "Linked fields can be compared and added up across surveys.",
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
            GroundTonalBadge(text = match.sourceLabel, tone = GroundBadgeTone.NEUTRAL)
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val checked = matches.count { it.isChecked }
        FilledTonalButton(onClick = actions::linkImportMatches, enabled = checked > 0) {
          Text(if (checked == matches.size) "Link all" else "Link $checked selected")
        }
        TextButton(onClick = actions::dismissImportMatches) { Text("Not now") }
      }
    }
  }
}
