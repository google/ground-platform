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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_badge_hidden
import org.openforis.collectone.v2.web.resources.org_delete_item
import org.openforis.collectone.v2.web.resources.org_delete_item_title
import org.openforis.collectone.v2.web.resources.org_edit_item
import org.openforis.collectone.v2.web.resources.org_hide
import org.openforis.collectone.v2.web.resources.org_library_global_body_member
import org.openforis.collectone.v2.web.resources.org_show
import org.openforis.collectone.v2.web.resources.org_template_delete
import org.openforis.collectone.v2.web.resources.org_template_delete_body
import org.openforis.collectone.v2.web.resources.org_template_edit_title
import org.openforis.collectone.v2.web.resources.org_template_new_button
import org.openforis.collectone.v2.web.resources.org_template_purposes_label
import org.openforis.collectone.v2.web.resources.org_template_summary
import org.openforis.collectone.v2.web.resources.org_templates_empty
import org.openforis.collectone.v2.web.resources.org_templates_global_body_admins
import org.openforis.collectone.v2.web.resources.org_templates_global_body_manager
import org.openforis.collectone.v2.web.resources.org_templates_global_empty
import org.openforis.collectone.v2.web.resources.org_templates_global_title
import org.openforis.collectone.v2.web.resources.org_templates_org_title

/** The organization's Form templates, then global ones with a Hide toggle. */
@Composable
internal fun TemplatesPane(
  library: OrganizationLibraryUiState,
  actions: OrganizationActions,
  organization: Organization,
  isLoading: Boolean = false,
) {
  // The template open in the Form editor, if any.
  var editing by remember(organization.id) { mutableStateOf<TemplateEditTarget?>(null) }
  var deleting by remember(organization.id) { mutableStateOf<FormTemplate?>(null) }

  editing?.let { target ->
    val session =
      remember(target) { TemplateEditorSession.of(target.template, library, organization.name) }
    TemplateEditorDialog(
      session = session,
      availablePurposes = library.ownPurposePacks,
      onSave = { session.save(actions, organization.id) },
      onDismiss = { editing = null },
    )
  }
  deleting?.let { template ->
    ConfirmDeleteDialog(
      title = stringResource(Res.string.org_delete_item_title, template.title.text),
      body = stringResource(Res.string.org_template_delete_body),
      confirmLabel = stringResource(Res.string.org_template_delete),
      onConfirm = {
        actions.deleteTemplate(template.organizationId, template.id)
        deleting = null
      },
      onDismiss = { deleting = null },
    )
  }

  val (global, own) = library.formTemplates.partition { it.isGlobal }
  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (!library.isGlobalLibrary) {
      DetailsCard(stringResource(Res.string.org_templates_org_title, organization.name)) {
        if (own.isEmpty()) {
          if (isLoading) {
            Box(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            Text(
              stringResource(Res.string.org_templates_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        val conceptLabels =
          remember(library.resolvedLibrary) {
            library.resolvedLibrary.concepts.associate { it.id to it.label.text }
          }
        own.forEach { row ->
          key(row.entry.id) {
            val associatedPurposes =
              remember(library.purposePacks, row.entry.id) {
                library.purposePacks.map { it.entry }.filter { row.entry.id in it.formTemplateIds }
              }
            TemplateRow(
              row = row,
              associatedPurposes = associatedPurposes,
              conceptLabels = conceptLabels,
              onEdit = { editing = TemplateEditTarget(row.entry) },
              onDelete = { deleting = row.entry },
            ) {
              actions.setGlobalEntryHidden(organization.id, row.entry.id, it)
            }
          }
        }
        if (library.canEdit) {
          OutlinedButton(onClick = { editing = TemplateEditTarget(null) }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.org_template_new_button))
          }
        }
      }
    }
    DetailsCard(stringResource(Res.string.org_templates_global_title)) {
      Text(
        if (library.isGlobalLibrary) {
          stringResource(Res.string.org_templates_global_body_admins)
        } else if (library.canEdit) {
          stringResource(Res.string.org_templates_global_body_manager, organization.name)
        } else {
          stringResource(Res.string.org_library_global_body_member, organization.name)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (global.isEmpty()) {
        if (isLoading) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        } else {
          Text(
            stringResource(Res.string.org_templates_global_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      val globalConceptLabels =
        remember(library.resolvedLibrary) {
          library.resolvedLibrary.concepts.associate { it.id to it.label.text }
        }
      global.forEach { row ->
        key(row.entry.id) {
          val associatedPurposes =
            remember(library.purposePacks, row.entry.id) {
              library.purposePacks.map { it.entry }.filter { row.entry.id in it.formTemplateIds }
            }
          TemplateRow(
            row = row,
            associatedPurposes = associatedPurposes,
            conceptLabels = globalConceptLabels,
            onEdit = { editing = TemplateEditTarget(row.entry) },
            onDelete = { deleting = row.entry },
          ) {
            actions.setGlobalEntryHidden(organization.id, row.entry.id, it)
          }
        }
      }
      if (library.isGlobalLibrary && library.canEdit) {
        OutlinedButton(onClick = { editing = TemplateEditTarget(null) }) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.org_template_new_button))
        }
      }
    }
  }
}

@Composable
private fun TemplateRow(
  row: LibraryEntryRow<FormTemplate>,
  associatedPurposes: List<PurposePack>,
  conceptLabels: Map<String, String> = emptyMap(),
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSetHidden: (Boolean) -> Unit,
) {
  val template = row.entry
  val questionCount = template.form.questions.size
  val badges =
    listOfNotNull(
      stringResource(Res.string.org_badge_hidden).takeIf { row.isHidden },
      statusBadge(template.status),
    )
  val summary =
    pluralStringResource(
      Res.plurals.org_template_summary,
      questionCount,
      questionCount,
      template.linkedQuestionCount,
    )

  LibraryEntryCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EntryTitleRow(title = template.title.text, badges = badges, dimmed = row.isHidden)
          if (template.description.text.isNotBlank()) {
            Text(template.description.text, style = MaterialTheme.typography.bodySmall)
          }
          Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TemplateRowActions(row, template.title.text, onEdit, onDelete, onSetHidden)
      }
      if (associatedPurposes.isNotEmpty()) {
        TemplatePurposeChips(associatedPurposes)
      }
      if (template.form.questions.isNotEmpty()) {
        TemplateQuestionSummaryList(template.form.questions, conceptLabels)
      }
    }
  }
}

@Composable
private fun TemplateRowActions(
  row: LibraryEntryRow<FormTemplate>,
  title: String,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSetHidden: (Boolean) -> Unit,
) {
  if (row.canEdit) {
    TextButton(onClick = onEdit) {
      Icon(
        Icons.Outlined.Edit,
        contentDescription = stringResource(Res.string.org_edit_item, title),
      )
      Spacer(Modifier.width(4.dp))
      Text(stringResource(Res.string.org_template_edit_title))
    }
    IconButton(onClick = onDelete) {
      Icon(
        Icons.Outlined.Delete,
        contentDescription = stringResource(Res.string.org_delete_item, title),
      )
    }
  }
  if (row.canHide) {
    TextButton(onClick = { onSetHidden(!row.isHidden) }) {
      Icon(
        if (row.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
        contentDescription = null,
      )
      Spacer(Modifier.width(6.dp))
      Text(
        if (row.isHidden) stringResource(Res.string.org_show)
        else stringResource(Res.string.org_hide)
      )
    }
  }
}

@Composable
private fun TemplatePurposeChips(associatedPurposes: List<PurposePack>) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      stringResource(Res.string.org_template_purposes_label),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.CenterVertically),
    )
    associatedPurposes.forEach { purpose ->
      Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.align(Alignment.CenterVertically),
      ) {
        Text(
          purpose.title.text,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun TemplateQuestionSummaryList(
  questions: List<EditorQuestion>,
  conceptLabels: Map<String, String>,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    questions.forEachIndexed { idx, q ->
      val conceptSuffix =
        q.conceptLink?.conceptId?.let { id ->
          " · → ${conceptLabels[id] ?: LibraryIds.nameOf(id)}"
        } ?: ""
      val reqSuffix = if (q.required) " *" else ""
      Text(
        text =
          "${idx + 1}. ${q.label}$reqSuffix (${stringResource(q.type.labelRes)}$conceptSuffix)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The template opened in the Form editor: [template], or a new one when it's `null`. */
private class TemplateEditTarget(val template: FormTemplate?)
