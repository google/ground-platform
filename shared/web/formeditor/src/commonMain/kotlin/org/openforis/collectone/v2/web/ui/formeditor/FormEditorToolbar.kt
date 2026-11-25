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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.slugify
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_cancel
import org.openforis.collectone.v2.web.resources.formeditor_create_data_table
import org.openforis.collectone.v2.web.resources.formeditor_create_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_delete_form
import org.openforis.collectone.v2.web.resources.formeditor_delete_form_message
import org.openforis.collectone.v2.web.resources.formeditor_delete_form_title
import org.openforis.collectone.v2.web.resources.formeditor_download_xforms_xml
import org.openforis.collectone.v2.web.resources.formeditor_editor_subtitle
import org.openforis.collectone.v2.web.resources.formeditor_form_language_description
import org.openforis.collectone.v2.web.resources.formeditor_form_settings
import org.openforis.collectone.v2.web.resources.formeditor_language_default
import org.openforis.collectone.v2.web.resources.formeditor_more_options
import org.openforis.collectone.v2.web.resources.formeditor_preview
import org.openforis.collectone.v2.web.resources.formeditor_redo
import org.openforis.collectone.v2.web.resources.formeditor_save_as_template_menu
import org.openforis.collectone.v2.web.resources.formeditor_template_subtitle
import org.openforis.collectone.v2.web.resources.formeditor_undo
import org.openforis.collectone.v2.web.resources.formeditor_untitled_form
import org.openforis.collectone.v2.web.ui.common.IsoLanguages
import org.openforis.collectone.v2.web.ui.common.downloadTextFile

@Composable
internal fun FormEditorToolbar(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  onCreateDataset: (() -> Unit)?,
  onDelete: (() -> Unit)?,
) {
  var overflowExpanded by remember { mutableStateOf(false) }
  var savingTemplate by remember { mutableStateOf(false) }
  var confirmingDeleteForm by remember { mutableStateOf(false) }
  if (savingTemplate) {
    SaveAsTemplateDialog(uiState, actions, onDismiss = { savingTemplate = false })
  }
  if (confirmingDeleteForm && onDelete != null) {
    AlertDialog(
      onDismissRequest = { confirmingDeleteForm = false },
      title = { Text(stringResource(Res.string.formeditor_delete_form_title)) },
      text = {
        Text(
          stringResource(
            Res.string.formeditor_delete_form_message,
            uiState.form.title.ifBlank { stringResource(Res.string.formeditor_untitled_form) },
          )
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingDeleteForm = false
            onDelete()
          }
        ) {
          Text(
            stringResource(Res.string.formeditor_delete_form),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmingDeleteForm = false }) {
          Text(stringResource(Res.string.formeditor_cancel))
        }
      },
    )
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 1.dp,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val isCompact = maxWidth < 960.dp
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.widthIn(max = if (isCompact) 200.dp else 300.dp)) {
          Text(
            text =
              uiState.form.title.ifBlank { stringResource(Res.string.formeditor_untitled_form) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              stringResource(
                if (uiState.isTemplate) Res.string.formeditor_template_subtitle
                else Res.string.formeditor_editor_subtitle
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (!uiState.isReadOnly) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::undo, enabled = uiState.canUndo) {
              Icon(
                Icons.AutoMirrored.Outlined.Undo,
                contentDescription = stringResource(Res.string.formeditor_undo),
              )
            }
            IconButton(onClick = actions::redo, enabled = uiState.canRedo) {
              Icon(
                Icons.AutoMirrored.Outlined.Redo,
                contentDescription = stringResource(Res.string.formeditor_redo),
              )
            }
          }
        }
        Spacer(Modifier.weight(1f))
        if (uiState.supportedLanguages.size > 1) {
          FormLanguageSelector(uiState, actions)
        }
        // The linked map layer or data table is shown in Form settings, not here.
        if (!isCompact) {
          if (onCreateDataset != null) {
            OutlinedButton(onClick = onCreateDataset) {
              Icon(
                Icons.Outlined.Layers,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                stringResource(
                  if (uiState.form.hasGeometry) Res.string.formeditor_create_map_layer
                  else Res.string.formeditor_create_data_table
                ),
                softWrap = false,
              )
            }
          }
        }
        // Every form action sits in this strip, in one button family: the platform toggle, then
        // Preview (tonal), then Form settings (filled) as the trailing, primary action.
        PreviewTargetToggle(uiState, actions)
        FilledTonalButton(onClick = actions::startPreview) {
          Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.formeditor_preview), softWrap = false)
        }
        if (!isCompact) {
          Button(onClick = actions::selectFormSettings) {
            Icon(
              Icons.Outlined.Settings,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.formeditor_form_settings), softWrap = false)
          }
        }
        Box {
          IconButton(onClick = { overflowExpanded = true }) {
            Icon(
              Icons.Outlined.MoreVert,
              contentDescription = stringResource(Res.string.formeditor_more_options),
            )
          }
          DropdownMenu(
            expanded = overflowExpanded,
            onDismissRequest = { overflowExpanded = false },
          ) {
            if (isCompact) {
              DropdownMenuItem(
                text = { Text(stringResource(Res.string.formeditor_form_settings)) },
                leadingIcon = {
                  Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                },
                onClick = {
                  overflowExpanded = false
                  actions.selectFormSettings()
                },
              )
              if (onCreateDataset != null) {
                DropdownMenuItem(
                  text = {
                    Text(
                      stringResource(
                        if (uiState.form.hasGeometry) Res.string.formeditor_create_map_layer
                        else Res.string.formeditor_create_data_table
                      )
                    )
                  },
                  leadingIcon = {
                    Icon(
                      Icons.Outlined.Layers,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  onClick = {
                    overflowExpanded = false
                    onCreateDataset()
                  },
                )
              }
            }
            DropdownMenuItem(
              text = { Text(stringResource(Res.string.formeditor_download_xforms_xml)) },
              leadingIcon = {
                Icon(
                  Icons.Outlined.Description,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              onClick = {
                overflowExpanded = false
                val baseName =
                  slugify(uiState.form.title.ifBlank { uiState.form.formId }, fallback = "form")
                downloadTextFile(
                  fileName = "$baseName.xml",
                  mimeType = "application/xml",
                  content = uiState.xformsXml,
                )
              },
            )
            // A template is already in the library.
            if (!uiState.isTemplate) {
              SaveAsTemplateMenuItem(uiState) {
                overflowExpanded = false
                savingTemplate = true
              }
            }
            if (onDelete != null) {
              DropdownMenuItem(
                text = {
                  Text(
                    stringResource(Res.string.formeditor_delete_form),
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
                  overflowExpanded = false
                  confirmingDeleteForm = true
                },
              )
            }
          }
        }
      }
    }
  }
}

/** The overflow menu's **Save as template…** item, disabled with the reason when unavailable. */
@Composable
private fun SaveAsTemplateMenuItem(uiState: FormEditorUiState, onClick: () -> Unit) {
  val unavailable = uiState.saveAsTemplateUnavailableReason?.text()
  DropdownMenuItem(
    text = {
      Column {
        Text(stringResource(Res.string.formeditor_save_as_template_menu))
        if (unavailable != null) {
          Text(
            unavailable,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.widthIn(max = 260.dp),
          )
        }
      }
    },
    leadingIcon = {
      Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
    },
    enabled = unavailable == null,
    onClick = onClick,
  )
}

internal fun languageDisplayName(code: String): String = IsoLanguages.findByCode(code)?.name ?: code

/**
 * Dropdown selector in the preview header that lets survey designers switch the active editing and
 * preview language across all languages configured on the survey.
 */
@Composable
internal fun FormLanguageSelector(
  uiState: FormEditorUiState,
  actions: FormSelectionActions,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val activeCode = uiState.activeLanguage
  val activeName = languageDisplayName(activeCode)
  val languageDescription =
    stringResource(Res.string.formeditor_form_language_description, activeName)
  Box(modifier = modifier) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier = Modifier.height(36.dp).semantics { contentDescription = languageDescription },
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
      Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text(activeName, style = MaterialTheme.typography.labelMedium, softWrap = false)
      Spacer(Modifier.width(4.dp))
      Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      uiState.supportedLanguages.forEach { code ->
        val name = languageDisplayName(code)
        val isDefault = code == uiState.defaultLanguage
        val isSelected = code == activeCode
        DropdownMenuItem(
          text = {
            Text(
              if (isDefault) stringResource(Res.string.formeditor_language_default, name) else name
            )
          },
          leadingIcon = {
            if (isSelected) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
            } else {
              Spacer(Modifier.size(18.dp))
            }
          },
          onClick = {
            expanded = false
            actions.selectLanguage(code)
          },
        )
      }
    }
  }
}
