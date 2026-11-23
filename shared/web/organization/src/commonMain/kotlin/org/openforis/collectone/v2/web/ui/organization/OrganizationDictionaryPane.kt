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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.CodeListItem
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.ui.text.descriptionRes
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_clear_search
import org.openforis.collectone.v2.web.resources.org_close
import org.openforis.collectone.v2.web.resources.org_concept_add
import org.openforis.collectone.v2.web.resources.org_concept_add_value
import org.openforis.collectone.v2.web.resources.org_concept_aggregation
import org.openforis.collectone.v2.web.resources.org_concept_data_type
import org.openforis.collectone.v2.web.resources.org_concept_delete_error_title
import org.openforis.collectone.v2.web.resources.org_concept_edit_title
import org.openforis.collectone.v2.web.resources.org_concept_goals
import org.openforis.collectone.v2.web.resources.org_concept_goals_help
import org.openforis.collectone.v2.web.resources.org_concept_id
import org.openforis.collectone.v2.web.resources.org_concept_id_version
import org.openforis.collectone.v2.web.resources.org_concept_label_help
import org.openforis.collectone.v2.web.resources.org_concept_list_values
import org.openforis.collectone.v2.web.resources.org_concept_name
import org.openforis.collectone.v2.web.resources.org_concept_pillar
import org.openforis.collectone.v2.web.resources.org_concept_privacy
import org.openforis.collectone.v2.web.resources.org_concept_references
import org.openforis.collectone.v2.web.resources.org_concept_remove_value
import org.openforis.collectone.v2.web.resources.org_concept_search_count
import org.openforis.collectone.v2.web.resources.org_concept_search_label
import org.openforis.collectone.v2.web.resources.org_concept_search_placeholder
import org.openforis.collectone.v2.web.resources.org_concept_status
import org.openforis.collectone.v2.web.resources.org_concept_suggested_goals
import org.openforis.collectone.v2.web.resources.org_concept_translations
import org.openforis.collectone.v2.web.resources.org_concept_unit
import org.openforis.collectone.v2.web.resources.org_concept_unit_help
import org.openforis.collectone.v2.web.resources.org_concept_unit_label
import org.openforis.collectone.v2.web.resources.org_concept_value
import org.openforis.collectone.v2.web.resources.org_concept_values
import org.openforis.collectone.v2.web.resources.org_concept_values_truncated
import org.openforis.collectone.v2.web.resources.org_concept_version_short
import org.openforis.collectone.v2.web.resources.org_concept_vocabulary
import org.openforis.collectone.v2.web.resources.org_concepts_empty
import org.openforis.collectone.v2.web.resources.org_concepts_global_body
import org.openforis.collectone.v2.web.resources.org_concepts_global_empty
import org.openforis.collectone.v2.web.resources.org_concepts_global_shared_body
import org.openforis.collectone.v2.web.resources.org_concepts_global_title
import org.openforis.collectone.v2.web.resources.org_concepts_no_matches
import org.openforis.collectone.v2.web.resources.org_concepts_org_body
import org.openforis.collectone.v2.web.resources.org_concepts_org_title
import org.openforis.collectone.v2.web.resources.org_delete_item
import org.openforis.collectone.v2.web.resources.org_edit_item
import org.openforis.collectone.v2.web.resources.org_field_description_english
import org.openforis.collectone.v2.web.resources.org_field_label_english
import org.openforis.collectone.v2.web.resources.org_none
import org.openforis.collectone.v2.web.resources.org_ok
import org.openforis.collectone.v2.web.resources.org_save
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

/** Searchable list of concepts: the organization's own, then global ones. */
@Composable
internal fun DictionaryPane(
  library: OrganizationLibraryUiState,
  actions: OrganizationActions,
  organization: Organization,
  isLoading: Boolean = false,
) {
  var editing by remember(organization.id) { mutableStateOf<LibraryConcept?>(null) }
  var creating by remember(organization.id) { mutableStateOf(false) }
  var deleteError by remember(organization.id) { mutableStateOf<OrganizationMessage?>(null) }

  if (creating || editing != null) {
    ConceptDialog(
      library = library,
      original = editing,
      onSave = { concept, isNew ->
        actions.saveConcept(concept, isNew).also { error ->
          if (error == null) {
            creating = false
            editing = null
          }
        }
      },
      onDismiss = {
        creating = false
        editing = null
      },
    )
  }
  deleteError?.let { message ->
    AlertDialog(
      onDismissRequest = { deleteError = null },
      title = { Text(stringResource(Res.string.org_concept_delete_error_title)) },
      text = { Text(message.asString()) },
      confirmButton = {
        TextButton(onClick = { deleteError = null }) { Text(stringResource(Res.string.org_ok)) }
      },
    )
  }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    OutlinedTextField(
      value = library.dictionaryQuery,
      onValueChange = actions::setDictionaryQuery,
      label = { Text(stringResource(Res.string.org_concept_search_label)) },
      placeholder = { Text(stringResource(Res.string.org_concept_search_placeholder)) },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      trailingIcon =
        if (library.dictionaryQuery.isNotEmpty()) {
          {
            IconButton(onClick = { actions.setDictionaryQuery("") }) {
              Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.org_clear_search),
              )
            }
          }
        } else {
          null
        },
      supportingText = {
        Text(
          pluralStringResource(
            Res.plurals.org_concept_search_count,
            library.totalConceptCount,
            library.concepts.size,
            library.totalConceptCount,
          )
        )
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    val ownTitle =
      if (library.isGlobalLibrary) stringResource(Res.string.org_concepts_global_title)
      else stringResource(Res.string.org_concepts_org_title, organization.name)
    DetailsCard(ownTitle) {
      Text(
        if (library.isGlobalLibrary) {
          stringResource(Res.string.org_concepts_global_body)
        } else {
          stringResource(Res.string.org_concepts_org_body, organization.id)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ConceptRows(
        rows = library.organizationConcepts,
        emptyText =
          when {
            library.dictionaryQuery.isNotBlank() ->
              stringResource(Res.string.org_concepts_no_matches)
            library.isGlobalLibrary -> stringResource(Res.string.org_concepts_global_empty)
            else -> stringResource(Res.string.org_concepts_empty)
          },
        onEdit = { editing = it },
        onDelete = { concept -> deleteError = actions.deleteConcept(organization.id, concept.id) },
        isLoading = isLoading,
      )
      if (library.canEdit) {
        OutlinedButton(onClick = { creating = true }) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.org_concept_add))
        }
      }
    }

    if (!library.isGlobalLibrary) {
      DetailsCard(stringResource(Res.string.org_concepts_global_title)) {
        Text(
          stringResource(Res.string.org_concepts_global_shared_body),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConceptRows(
          rows = library.globalConcepts,
          emptyText =
            if (library.dictionaryQuery.isNotBlank())
              stringResource(Res.string.org_concepts_no_matches)
            else stringResource(Res.string.org_concepts_global_empty),
          onEdit = { editing = it },
          onDelete = {},
          isLoading = isLoading,
        )
      }
    }
  }
}

@Composable
private fun ConceptRows(
  rows: List<LibraryEntryRow<LibraryConcept>>,
  emptyText: String,
  onEdit: (LibraryConcept) -> Unit,
  onDelete: (LibraryConcept) -> Unit,
  isLoading: Boolean = false,
) {
  if (rows.isEmpty()) {
    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
      return
    }
    Text(
      emptyText,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    rows.forEach { row ->
      key(row.entry.id) {
        ConceptRow(row, onEdit = { onEdit(row.entry) }, onDelete = { onDelete(row.entry) })
      }
    }
  }
}

@Composable
private fun ConceptRow(
  row: LibraryEntryRow<LibraryConcept>,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val concept = row.entry
  LibraryEntryCard {
    Row(verticalAlignment = Alignment.Top) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EntryTitleRow(
          title = concept.label.text,
          badges =
            listOfNotNull(
              stringResource(concept.dataType.labelRes),
              stringResource(concept.status.labelRes).takeIf {
                concept.status != LibraryStatus.STABLE
              },
            ),
        )
        if (!concept.description.isBlank) {
          Text(concept.description.text, style = MaterialTheme.typography.bodySmall)
        }
        Text(
          conceptSummary(concept),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (row.canEdit) {
        IconButton(onClick = onEdit) {
          Icon(
            Icons.Outlined.Edit,
            contentDescription = stringResource(Res.string.org_edit_item, concept.label.text),
          )
        }
        if (concept.status == LibraryStatus.DRAFT) {
          IconButton(onClick = onDelete) {
            Icon(
              Icons.Outlined.Delete,
              contentDescription = stringResource(Res.string.org_delete_item, concept.label.text),
            )
          }
        }
      }
    }
  }
}

/** One line of a concept's unit, list values, aggregation, privacy, and languages. */
@Composable
private fun conceptSummary(concept: LibraryConcept): String =
  listOfNotNull(
      concept.unit
        .takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.org_concept_unit, it) },
      concept.codeList
        .takeIf { it.isNotEmpty() }
        ?.let { items ->
          val shown = items.take(4).joinToString { it.label.text }
          pluralStringResource(
            if (items.size > 4) Res.plurals.org_concept_values_truncated
            else Res.plurals.org_concept_values,
            items.size,
            items.size,
            shown,
          )
        },
      stringResource(concept.aggregation.labelRes),
      stringResource(concept.privacyClass.labelRes),
      concept.pillar?.labelRes?.let { stringResource(it) },
      concept.label.values.keys.sorted().joinToString(" ").uppercase(),
      stringResource(Res.string.org_concept_version_short, concept.version),
    )
    .joinToString(" · ")

/** Editable code-list value in [ConceptDialog]; [isExisting] values can't be removed or recoded. */
private data class CodeListDraft(
  val code: String,
  val label: String,
  val isExisting: Boolean,
  val original: CodeListItem? = null,
)

/**
 * Creates or edits a concept: name, English label and description, data type, unit, list values,
 * aggregation, privacy, goals, pillar, and status. Other languages are kept as they are. Read-only
 * when the row isn't editable.
 */
@Composable
private fun ConceptDialog(
  library: OrganizationLibraryUiState,
  original: LibraryConcept?,
  onSave: (concept: LibraryConcept, isNew: Boolean) -> OrganizationMessage?,
  onDismiss: () -> Unit,
) {
  val isNew = original == null
  val readOnly =
    !library.canEdit || (original != null && original.isGlobal != library.isGlobalLibrary)
  var label by remember {
    mutableStateOf(original?.label?.get(LocalizedText.DEFAULT_LANGUAGE).orEmpty())
  }
  var name by remember { mutableStateOf("") }
  var nameEdited by remember { mutableStateOf(false) }
  var vocabulary by remember { mutableStateOf("core") }
  var description by remember {
    mutableStateOf(original?.description?.get(LocalizedText.DEFAULT_LANGUAGE).orEmpty())
  }
  var dataType by remember { mutableStateOf(original?.dataType ?: ConceptDataType.TEXT) }
  var unit by remember { mutableStateOf(original?.unit.orEmpty()) }
  var aggregation by remember { mutableStateOf(original?.aggregation ?: ConceptAggregation.NONE) }
  var privacy by remember { mutableStateOf(original?.privacyClass ?: PrivacyClass.ORG_ONLY) }
  var pillar by remember { mutableStateOf(original?.pillar) }
  val none = stringResource(Res.string.org_none)
  var goals by remember { mutableStateOf(original?.goals.orEmpty().joinToString(", ")) }
  var status by remember { mutableStateOf(original?.status ?: LibraryStatus.DRAFT) }
  val codes = remember {
    mutableStateListOf<CodeListDraft>().apply {
      original?.codeList?.forEach {
        add(CodeListDraft(it.code, it.label.get(LocalizedText.DEFAULT_LANGUAGE), true, it))
      }
    }
  }
  var error by remember { mutableStateOf<OrganizationMessage?>(null) }
  val effectiveName = if (nameEdited) name else library.suggestedName(label)

  fun build(): LibraryConcept {
    val base =
      original
        ?: LibraryConcept(
          id = library.newConceptId(effectiveName, vocabulary),
          organizationId = library.organizationId,
          label = LocalizedText(),
          dataType = dataType,
        )
    return base.copy(
      label = base.label.with(LocalizedText.DEFAULT_LANGUAGE, label.trim()),
      description = base.description.with(LocalizedText.DEFAULT_LANGUAGE, description.trim()),
      dataType = dataType,
      unit = unit.trim(),
      codeList =
        if (!dataType.hasCodeList) {
          emptyList()
        } else {
          codes.map { draft ->
            val previous = draft.original
            CodeListItem(
              code = draft.code.trim(),
              label =
                (previous?.label ?: LocalizedText()).with(
                  LocalizedText.DEFAULT_LANGUAGE,
                  draft.label.trim(),
                ),
              externalIds = previous?.externalIds.orEmpty(),
            )
          }
        },
      aggregation = aggregation,
      privacyClass = privacy,
      goals = goals.split(',').map { it.trim() }.filter { it.isNotEmpty() },
      pillar = pillar,
      status = status,
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        when {
          isNew -> stringResource(Res.string.org_concept_add)
          readOnly -> original?.label?.text.orEmpty()
          else -> stringResource(Res.string.org_concept_edit_title)
        }
      )
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = label,
          onValueChange = {
            label = it
            error = null
          },
          label = { Text(stringResource(Res.string.org_field_label_english)) },
          supportingText = { Text(stringResource(Res.string.org_concept_label_help)) },
          singleLine = true,
          readOnly = readOnly,
          modifier = Modifier.fillMaxWidth(),
        )
        if (isNew) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (library.isGlobalLibrary) {
              OutlinedTextField(
                value = vocabulary,
                onValueChange = {
                  vocabulary = it
                  error = null
                },
                label = { Text(stringResource(Res.string.org_concept_vocabulary)) },
                singleLine = true,
                modifier = Modifier.width(140.dp),
              )
            }
            OutlinedTextField(
              value = effectiveName,
              onValueChange = {
                name = it
                nameEdited = true
                error = null
              },
              label = { Text(stringResource(Res.string.org_concept_name)) },
              supportingText = {
                val rawId = library.newConceptId(effectiveName, vocabulary)
                val cleanId = if (rawId.startsWith("org.")) LibraryIds.nameOf(rawId) else rawId
                Text(stringResource(Res.string.org_concept_id, cleanId))
              },
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
              modifier = Modifier.weight(1f),
            )
          }
        } else {
          original?.let {
            val cleanId = if (it.id.startsWith("org.")) LibraryIds.nameOf(it.id) else it.id
            MonospaceId(stringResource(Res.string.org_concept_id_version, cleanId, it.version))
          }
          if (original != null && original.label.values.size > 1) {
            Text(
              stringResource(
                Res.string.org_concept_translations,
                original.label.values.entries
                  .filter { it.key != LocalizedText.DEFAULT_LANGUAGE }
                  .joinToString { "${it.key} ${it.value}" },
              ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(Res.string.org_field_description_english)) },
          minLines = 2,
          readOnly = readOnly,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(modifier = Modifier.weight(1f)) {
            DropdownSelectorOrText(
              label = stringResource(Res.string.org_concept_data_type),
              selectedText = stringResource(dataType.labelRes),
              options = ConceptDataType.entries,
              optionText = { stringResource(it.labelRes) },
              readOnly = readOnly,
              onSelect = {
                dataType = it
                error = null
              },
            )
          }
          OutlinedTextField(
            value = unit,
            onValueChange = { unit = it },
            label = { Text(stringResource(Res.string.org_concept_unit_label)) },
            placeholder = { Text("kg") },
            supportingText = { Text(stringResource(Res.string.org_concept_unit_help)) },
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.width(150.dp),
          )
        }
        if (dataType.hasCodeList) {
          Text(
            stringResource(Res.string.org_concept_list_values),
            style = MaterialTheme.typography.titleSmall,
          )
          codes.forEachIndexed { index, draft ->
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = draft.code,
                onValueChange = {
                  codes[index] = draft.copy(code = it)
                  error = null
                },
                label = { Text(stringResource(Res.string.org_concept_value)) },
                singleLine = true,
                readOnly = readOnly || draft.isExisting,
                textStyle =
                  MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(160.dp),
              )
              OutlinedTextField(
                value = draft.label,
                onValueChange = {
                  codes[index] = draft.copy(label = it)
                  error = null
                },
                label = { Text(stringResource(Res.string.org_field_label_english)) },
                singleLine = true,
                readOnly = readOnly,
                modifier = Modifier.weight(1f),
              )
              if (!readOnly && !draft.isExisting) {
                IconButton(onClick = { codes.removeAt(index) }) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.org_concept_remove_value),
                  )
                }
              }
            }
          }
          if (!readOnly) {
            TextButton(onClick = { codes.add(CodeListDraft("", "", isExisting = false)) }) {
              Icon(Icons.Outlined.Add, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text(stringResource(Res.string.org_concept_add_value))
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(modifier = Modifier.weight(1f)) {
            DropdownSelectorOrText(
              label = stringResource(Res.string.org_concept_aggregation),
              selectedText = stringResource(aggregation.labelRes),
              options = ConceptAggregation.entries,
              optionText = { stringResource(it.labelRes) },
              readOnly = readOnly,
              onSelect = { aggregation = it },
            )
          }
          Box(modifier = Modifier.weight(1f)) {
            DropdownSelectorOrText(
              label = stringResource(Res.string.org_concept_privacy),
              selectedText = stringResource(privacy.labelRes),
              options = PrivacyClass.entries,
              optionText = { stringResource(it.labelRes) },
              readOnly = readOnly,
              onSelect = { privacy = it },
            )
          }
        }
        Text(
          "${stringResource(aggregation.descriptionRes)} ${stringResource(privacy.descriptionRes)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = goals,
            onValueChange = { goals = it },
            label = {
              Text(
                if (library.isGlobalLibrary) stringResource(Res.string.org_concept_goals)
                else stringResource(Res.string.org_concept_suggested_goals)
              )
            },
            supportingText = { Text(stringResource(Res.string.org_concept_goals_help)) },
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.weight(1f),
          )
          Box(modifier = Modifier.width(170.dp)) {
            DropdownSelectorOrText(
              label = stringResource(Res.string.org_concept_pillar),
              selectedText = pillar?.labelRes?.let { stringResource(it) } ?: none,
              options = listOf<ImpactPillar?>(null) + ImpactPillar.entries,
              optionText = { option -> option?.labelRes?.let { stringResource(it) } ?: none },
              readOnly = readOnly,
              onSelect = { pillar = it },
            )
          }
        }
        Box(modifier = Modifier.widthIn(max = 220.dp)) {
          DropdownSelectorOrText(
            label = stringResource(Res.string.org_concept_status),
            selectedText = stringResource(status.labelRes),
            options = LibraryStatus.entries,
            optionText = { stringResource(it.labelRes) },
            readOnly = readOnly,
            onSelect = { status = it },
          )
        }
        if (original?.references?.isNotEmpty() == true) {
          Text(
            stringResource(Res.string.org_concept_references, original.references.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        error?.let {
          Text(
            it.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      if (!readOnly) {
        Button(
          onClick = { error = onSave(build(), isNew) },
          enabled = label.isNotBlank() && (!isNew || effectiveName.isNotBlank()),
        ) {
          Text(
            if (isNew) stringResource(Res.string.org_concept_add)
            else stringResource(Res.string.org_save)
          )
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(
          if (readOnly) stringResource(Res.string.org_close)
          else stringResource(Res.string.org_cancel)
        )
      }
    },
  )
}

/** A [DropdownSelector], or a read-only text field showing [selectedText] when [readOnly]. */
@Composable
private fun <T> DropdownSelectorOrText(
  label: String,
  selectedText: String,
  options: List<T>,
  optionText: @Composable (T) -> String,
  readOnly: Boolean,
  onSelect: (T) -> Unit,
) {
  if (readOnly) {
    OutlinedTextField(
      value = selectedText,
      onValueChange = {},
      label = { Text(label) },
      readOnly = true,
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
  } else {
    DropdownSelector(
      label = label,
      selectedText = selectedText,
      options = options,
      optionText = optionText,
      onSelect = onSelect,
    )
  }
}
