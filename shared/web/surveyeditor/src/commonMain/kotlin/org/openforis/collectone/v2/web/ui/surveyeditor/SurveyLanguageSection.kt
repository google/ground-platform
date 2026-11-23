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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.InputChip
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_clear_search
import org.openforis.collectone.v2.web.resources.surveyeditor_close
import org.openforis.collectone.v2.web.resources.surveyeditor_details_remove_item
import org.openforis.collectone.v2.web.resources.surveyeditor_language_add
import org.openforis.collectone.v2.web.resources.surveyeditor_language_added
import org.openforis.collectone.v2.web.resources.surveyeditor_language_default
import org.openforis.collectone.v2.web.resources.surveyeditor_language_default_chip
import org.openforis.collectone.v2.web.resources.surveyeditor_language_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_language_matches
import org.openforis.collectone.v2.web.resources.surveyeditor_language_no_matches
import org.openforis.collectone.v2.web.resources.surveyeditor_language_none_selected
import org.openforis.collectone.v2.web.resources.surveyeditor_language_picker_title
import org.openforis.collectone.v2.web.resources.surveyeditor_language_popular
import org.openforis.collectone.v2.web.resources.surveyeditor_language_search_label
import org.openforis.collectone.v2.web.resources.surveyeditor_language_search_placeholder
import org.openforis.collectone.v2.web.resources.surveyeditor_language_supported
import org.openforis.collectone.v2.web.resources.surveyeditor_language_title
import org.openforis.collectone.v2.web.resources.surveyeditor_select
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.IsoLanguage
import org.openforis.collectone.v2.web.ui.common.IsoLanguages

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageSelectorSection(uiState: SurveyEditorUiState, actions: SurveyDetailsActions) {
  val details = uiState.details
  val supported =
    remember(details.supportedLanguages) {
      details.supportedLanguages.sortedBy { code ->
        (IsoLanguages.findByCode(code)?.name ?: code).lowercase()
      }
    }
  val defaultLang = details.defaultLanguage
  var showPicker by remember { mutableStateOf(false) }

  if (showPicker && !uiState.isReadOnly) {
    LanguagePickerDialog(
      selectedCodes = supported.toSet(),
      onAddLanguage = { lang -> actions.addSupportedLanguage(lang.code) },
      onDismiss = { showPicker = false },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_language_title),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      stringResource(Res.string.surveyeditor_language_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Supported languages chips and Add button
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        stringResource(Res.string.surveyeditor_language_supported),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        supported.forEach { code ->
          val lang = IsoLanguages.findByCode(code)
          val displayName = lang?.name ?: code
          val isDefault = code.equals(defaultLang, ignoreCase = true)
          InputChip(
            selected = isDefault,
            onClick = { actions.setDefaultLanguage(code) },
            enabled = !uiState.isReadOnly,
            label = {
              Text(
                if (supported.size > 1 && isDefault) {
                  stringResource(Res.string.surveyeditor_language_default_chip, displayName)
                } else {
                  displayName
                }
              )
            },
            modifier = Modifier.height(32.dp),
            trailingIcon =
              if (!uiState.isReadOnly && supported.size > 1) {
                {
                  IconButton(
                    onClick = { actions.removeSupportedLanguage(code) },
                    modifier = Modifier.size(18.dp),
                  ) {
                    Icon(
                      Icons.Outlined.Close,
                      contentDescription =
                        stringResource(Res.string.surveyeditor_details_remove_item, displayName),
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
              } else {
                null
              },
          )
        }
        if (!uiState.isReadOnly) {
          OutlinedButton(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.surveyeditor_language_add))
          }
        }
      }
    }

    // Default language selector (only needed when more than one language is supported)
    if (supported.size > 1) {
      val defaultLanguageObject = IsoLanguages.findByCode(defaultLang)
      val defaultLabel =
        defaultLanguageObject?.name
          ?: defaultLang.ifEmpty { stringResource(Res.string.surveyeditor_language_none_selected) }
      Box(modifier = Modifier.widthIn(max = 380.dp)) {
        DropdownSelector(
          label = stringResource(Res.string.surveyeditor_language_default),
          selectedText = defaultLabel,
          options = supported,
          optionText = { code -> IsoLanguages.findByCode(code)?.name ?: code },
          enabled = !uiState.isReadOnly,
          onSelect = { pickedCode -> actions.setDefaultLanguage(pickedCode) },
        )
      }
    }
  }
}

/**
 * Searchable dialog allowing the user to search the full catalog of ISO 639-3 languages by code or
 * name and add them to the survey.
 */
@Composable
private fun LanguagePickerDialog(
  selectedCodes: Set<String>,
  onAddLanguage: (IsoLanguage) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val searchResults = remember(searchQuery) { IsoLanguages.search(searchQuery, limit = 40) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          Icons.Outlined.Language,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(Res.string.surveyeditor_language_picker_title))
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 500.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          label = { Text(stringResource(Res.string.surveyeditor_language_search_label)) },
          placeholder = {
            Text(stringResource(Res.string.surveyeditor_language_search_placeholder))
          },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = stringResource(Res.string.surveyeditor_clear_search),
                )
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Text(
          text =
            if (searchQuery.isBlank()) {
              stringResource(Res.string.surveyeditor_language_popular)
            } else {
              pluralStringResource(
                Res.plurals.surveyeditor_language_matches,
                searchResults.size,
                searchResults.size,
              )
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          if (searchResults.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                stringResource(Res.string.surveyeditor_language_no_matches, searchQuery.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(searchResults, key = { it.id }) { lang ->
                val isAlreadySelected = selectedCodes.any {
                  it.equals(lang.code, ignoreCase = true) || it.equals(lang.id, ignoreCase = true)
                }
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable(enabled = !isAlreadySelected) {
                        onAddLanguage(lang)
                        onDismiss()
                      }
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = lang.name,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                    )
                  }
                  if (isAlreadySelected) {
                    TonalBadge(
                      text = stringResource(Res.string.surveyeditor_language_added),
                      tone = BadgeTone.PRIMARY,
                    )
                  } else {
                    OutlinedButton(
                      onClick = {
                        onAddLanguage(lang)
                        onDismiss()
                      },
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Text(stringResource(Res.string.surveyeditor_select))
                    }
                  }
                }
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_close)) }
    },
  )
}
