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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryPrograms
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_badge_hidden
import org.openforis.collectone.v2.web.resources.org_badge_hidden_with_templates
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_delete_item_title
import org.openforis.collectone.v2.web.resources.org_field_description_english
import org.openforis.collectone.v2.web.resources.org_field_title_english
import org.openforis.collectone.v2.web.resources.org_library_global_body_member
import org.openforis.collectone.v2.web.resources.org_none
import org.openforis.collectone.v2.web.resources.org_purpose_associated_templates
import org.openforis.collectone.v2.web.resources.org_purpose_delete
import org.openforis.collectone.v2.web.resources.org_purpose_delete_body
import org.openforis.collectone.v2.web.resources.org_purpose_edit_title
import org.openforis.collectone.v2.web.resources.org_purpose_exports
import org.openforis.collectone.v2.web.resources.org_purpose_new_button
import org.openforis.collectone.v2.web.resources.org_purpose_new_title
import org.openforis.collectone.v2.web.resources.org_purpose_pillar
import org.openforis.collectone.v2.web.resources.org_purpose_programs
import org.openforis.collectone.v2.web.resources.org_purpose_templates_label
import org.openforis.collectone.v2.web.resources.org_purposes_empty
import org.openforis.collectone.v2.web.resources.org_purposes_global_body_admins
import org.openforis.collectone.v2.web.resources.org_purposes_global_body_manager
import org.openforis.collectone.v2.web.resources.org_purposes_global_empty
import org.openforis.collectone.v2.web.resources.org_purposes_global_title
import org.openforis.collectone.v2.web.resources.org_purposes_org_title
import org.openforis.collectone.v2.web.resources.org_save
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

/** The organization's Purpose Packs, then global ones with a Hide toggle. */
@Composable
internal fun PurposesPane(
  library: OrganizationLibraryUiState,
  actions: OrganizationActions,
  organization: Organization,
  isLoading: Boolean = false,
) {
  var creating by remember(organization.id) { mutableStateOf(false) }
  var editing by remember(organization.id) { mutableStateOf<PurposePack?>(null) }
  var deleting by remember(organization.id) { mutableStateOf<PurposePack?>(null) }
  val hiddenTemplateIds =
    library.formTemplates.filter { it.isHidden }.mapTo(mutableSetOf()) { it.entry.id }
  val availableTemplates =
    remember(library.formTemplates) { library.formTemplates.map { it.entry } }

  if (creating) {
    PurposeDialog(
      title = stringResource(Res.string.org_purpose_new_title),
      initialTitle = "",
      initialDescription = "",
      initialPillar = null,
      initialTemplateIds = emptyList(),
      availableTemplates = availableTemplates,
      onSave = { title, description, pillar, templateIds ->
        actions
          .createPurposePack(
            organizationId = organization.id,
            title = title,
            description = description,
            pillar = pillar,
            formTemplateIds = templateIds,
          )
          .also {
            if (it == null) creating = false
          }
      },
      onDismiss = { creating = false },
    )
  }
  editing?.let { pack ->
    PurposeDialog(
      title = stringResource(Res.string.org_purpose_edit_title),
      initialTitle = pack.title.text,
      initialDescription = pack.description.text,
      initialPillar = pack.pillar,
      initialTemplateIds = pack.formTemplateIds,
      availableTemplates = availableTemplates,
      onSave = { title, description, pillar, templateIds ->
        actions
          .updatePurposePack(
            organizationId = pack.organizationId,
            packId = pack.id,
            title = title,
            description = description,
            icon = pack.icon,
            pillar = pillar,
            status = pack.status,
            formTemplateIds = templateIds,
          )
          .also {
            if (it == null) editing = null
          }
      },
      onDismiss = { editing = null },
    )
  }
  deleting?.let { pack ->
    ConfirmDeleteDialog(
      title = stringResource(Res.string.org_delete_item_title, pack.title.text),
      body = stringResource(Res.string.org_purpose_delete_body),
      confirmLabel = stringResource(Res.string.org_purpose_delete),
      onConfirm = {
        actions.deletePurposePack(pack.organizationId, pack.id)
        deleting = null
      },
      onDismiss = { deleting = null },
    )
  }

  val (global, own) = library.purposePacks.partition { it.isGlobal }
  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (!library.isGlobalLibrary) {
      DetailsCard(stringResource(Res.string.org_purposes_org_title, organization.name)) {
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
              stringResource(Res.string.org_purposes_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        own.forEach { row ->
          key(row.entry.id) {
            PurposeRow(
              row,
              library,
              hiddenTemplateIds,
              { editing = row.entry },
              { deleting = row.entry },
            ) {
              actions.setGlobalEntryHidden(organization.id, row.entry.id, it)
            }
          }
        }
        if (library.canEdit) {
          OutlinedButton(onClick = { creating = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.org_purpose_new_button))
          }
        }
      }
    }
    DetailsCard(stringResource(Res.string.org_purposes_global_title)) {
      Text(
        if (library.isGlobalLibrary) {
          stringResource(Res.string.org_purposes_global_body_admins)
        } else if (library.canEdit) {
          stringResource(Res.string.org_purposes_global_body_manager)
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
            stringResource(Res.string.org_purposes_global_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      global.forEach { row ->
        key(row.entry.id) {
          PurposeRow(
            row,
            library,
            hiddenTemplateIds,
            { editing = row.entry },
            { deleting = row.entry },
          ) {
            actions.setGlobalEntryHidden(organization.id, row.entry.id, it)
          }
        }
      }
      if (library.isGlobalLibrary && library.canEdit) {
        OutlinedButton(onClick = { creating = true }) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.org_purpose_new_button))
        }
      }
    }
  }
}

@Composable
private fun PurposeRow(
  row: LibraryEntryRow<PurposePack>,
  library: OrganizationLibraryUiState,
  hiddenTemplateIds: Set<String>,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSetHidden: (Boolean) -> Unit,
) {
  val pack = row.entry
  val hiddenByTemplates =
    row.isGlobal &&
      !row.isHidden &&
      pack.formTemplateIds.isNotEmpty() &&
      pack.formTemplateIds.all { it in hiddenTemplateIds }
  EntryRowLayout(
    title = pack.title.text,
    description = pack.description.text,
    badges =
      listOfNotNull(
        pack.pillar?.labelRes?.let { stringResource(it) },
        stringResource(Res.string.org_badge_hidden).takeIf { row.isHidden },
        stringResource(Res.string.org_badge_hidden_with_templates).takeIf { hiddenByTemplates },
        statusBadge(pack.status),
      ),
    summary =
      listOfNotNull(
          pack.programIds
            .takeIf { it.isNotEmpty() }
            ?.let {
              stringResource(
                Res.string.org_purpose_programs,
                it.joinToString { id -> LibraryPrograms.label(id) },
              )
            },
          pack.exportProfileIds
            .takeIf { it.isNotEmpty() }
            ?.let { stringResource(Res.string.org_purpose_exports, it.joinToString()) },
        )
        .joinToString(" · "),
    dimmed = row.isHidden || hiddenByTemplates,
    row = row,
    entryLabel = pack.title.text,
    onRename = onEdit,
    onDelete = onDelete,
    onSetHidden = onSetHidden,
    extraContent =
      if (pack.formTemplateIds.isNotEmpty()) {
        {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              stringResource(Res.string.org_purpose_templates_label),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.align(Alignment.CenterVertically),
            )
            pack.formTemplateIds.forEach { templateId ->
              val templateTitle = library.templateTitles[templateId] ?: templateId
              Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.CenterVertically),
              ) {
                Text(
                  templateTitle,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
        }
      } else null,
  )
}

/** Edits the title, description, MAP pillar, and associated form templates of a Purpose Pack. */
@Composable
private fun PurposeDialog(
  title: String,
  initialTitle: String,
  initialDescription: String,
  initialPillar: ImpactPillar?,
  initialTemplateIds: List<String>,
  availableTemplates: List<FormTemplate>,
  onSave:
    (
      title: String,
      description: String,
      pillar: ImpactPillar?,
      templateIds: List<String>,
    ) -> OrganizationMessage?,
  onDismiss: () -> Unit,
) {
  var entryTitle by remember { mutableStateOf(initialTitle) }
  var description by remember { mutableStateOf(initialDescription) }
  var pillar by remember { mutableStateOf(initialPillar) }
  val none = stringResource(Res.string.org_none)
  val selectedTemplates = remember {
    mutableStateListOf<String>().apply { addAll(initialTemplateIds) }
  }
  var error by remember { mutableStateOf<OrganizationMessage?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = entryTitle,
          onValueChange = {
            entryTitle = it
            error = null
          },
          label = { Text(stringResource(Res.string.org_field_title_english)) },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it.asString()) } },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(Res.string.org_field_description_english)) },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        val pillarOptions = listOf<ImpactPillar?>(null) + ImpactPillar.entries
        DropdownSelector(
          label = stringResource(Res.string.org_purpose_pillar),
          selectedText = pillar?.labelRes?.let { stringResource(it) } ?: none,
          options = pillarOptions,
          optionText = { option -> option?.labelRes?.let { stringResource(it) } ?: none },
          onSelect = { pillar = it },
        )
        if (availableTemplates.isNotEmpty()) {
          Text(
            stringResource(Res.string.org_purpose_associated_templates),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            availableTemplates.forEach { template ->
              val isChecked = template.id in selectedTemplates
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      if (isChecked) selectedTemplates.remove(template.id)
                      else selectedTemplates.add(template.id)
                    }
                    .padding(vertical = 2.dp),
              ) {
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { checked ->
                    if (checked) selectedTemplates.add(template.id)
                    else selectedTemplates.remove(template.id)
                  },
                )
                Spacer(Modifier.width(8.dp))
                Text(template.title.text, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { error = onSave(entryTitle, description, pillar, selectedTemplates.toList()) },
        enabled = entryTitle.isNotBlank(),
      ) {
        Text(stringResource(Res.string.org_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.org_cancel)) }
    },
  )
}
