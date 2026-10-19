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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.CodeListItem
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryPrograms
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.web.ui.formeditor.DropdownSelector
import org.openforis.collectone.v2.web.ui.state.LibraryEntryRow
import org.openforis.collectone.v2.web.ui.state.OrganizationLibraryUiState
import org.openforis.collectone.v2.web.ui.viewmodel.OrganizationActions

// The organization page's library tabs: Purposes, Dictionary, and Templates. On "All users" they
// edit the global library; elsewhere the organization's entries come first and global entries are
// read-only, with a Hide toggle for global templates and Purpose Packs.

// ---------------------------------------------------------------------------------------------
// Dictionary
// ---------------------------------------------------------------------------------------------

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
  var deleteError by remember(organization.id) { mutableStateOf<String?>(null) }

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
      title = { Text("Can't delete this concept") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
    )
  }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    OutlinedTextField(
      value = library.dictionaryQuery,
      onValueChange = actions::setDictionaryQuery,
      label = { Text("Search concepts") },
      placeholder = { Text("Label or keyword in any language, for example cultivo") },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      trailingIcon =
        if (library.dictionaryQuery.isNotEmpty()) {
          {
            IconButton(onClick = { actions.setDictionaryQuery("") }) {
              Icon(Icons.Outlined.Close, contentDescription = "Clear search")
            }
          }
        } else {
          null
        },
      supportingText = {
        Text("${library.concepts.size} of ${library.totalConceptCount} concepts")
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    val ownTitle =
      if (library.isGlobalLibrary) "Global concepts" else "${organization.name} concepts"
    DetailsCard(ownTitle) {
      Text(
        if (library.isGlobalLibrary) {
          "Standard fields available to every survey. Changes apply to all organizations."
        } else {
          "Standard fields only this organization uses. Their IDs start with " +
            "org.${organization.id}."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ConceptRows(
        rows = library.organizationConcepts,
        emptyText =
          when {
            library.dictionaryQuery.isNotBlank() -> "No matching concepts."
            library.isGlobalLibrary -> "No global concepts yet."
            else -> "No concepts yet."
          },
        onEdit = { editing = it },
        onDelete = { concept -> deleteError = actions.deleteConcept(organization.id, concept.id) },
        isLoading = isLoading,
      )
      if (library.canEdit) {
        OutlinedButton(onClick = { creating = true }) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Add concept")
        }
      }
    }

    if (!library.isGlobalLibrary) {
      DetailsCard("Global concepts") {
        Text(
          "Shared with every organization by All users. Only platform admins can change them.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConceptRows(
          rows = library.globalConcepts,
          emptyText =
            if (library.dictionaryQuery.isNotBlank()) "No matching concepts."
            else "No global concepts yet.",
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
              concept.dataType.label,
              concept.status.label.takeIf { concept.status != LibraryStatus.STABLE },
            ),
        )
        MonospaceId(concept.id)
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
          Icon(Icons.Outlined.Edit, contentDescription = "Edit ${concept.label.text}")
        }
        if (concept.status == LibraryStatus.DRAFT) {
          IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete ${concept.label.text}")
          }
        }
      }
    }
  }
}

/** One line of a concept's unit, list values, aggregation, privacy, and languages. */
private fun conceptSummary(concept: LibraryConcept): String =
  listOfNotNull(
      concept.unit.takeIf { it.isNotBlank() }?.let { "Unit $it" },
      concept.codeList
        .takeIf { it.isNotEmpty() }
        ?.let { items ->
          val shown = items.take(4).joinToString { it.label.text }
          "${items.size} values: $shown" + if (items.size > 4) "…" else ""
        },
      concept.aggregation.label,
      concept.privacyClass.label,
      concept.pillar?.label,
      concept.label.values.keys.sorted().joinToString(" ").uppercase(),
      "v${concept.version}",
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
  onSave: (concept: LibraryConcept, isNew: Boolean) -> String?,
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
  var goals by remember { mutableStateOf(original?.goals.orEmpty().joinToString(", ")) }
  var status by remember { mutableStateOf(original?.status ?: LibraryStatus.DRAFT) }
  val codes = remember {
    mutableStateListOf<CodeListDraft>().apply {
      original?.codeList?.forEach {
        add(CodeListDraft(it.code, it.label.get(LocalizedText.DEFAULT_LANGUAGE), true, it))
      }
    }
  }
  var error by remember { mutableStateOf<String?>(null) }
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
          isNew -> "Add concept"
          readOnly -> original?.label?.text.orEmpty()
          else -> "Edit concept"
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
          label = { Text("Label (English)") },
          supportingText = { Text("Also the default question label when linked") },
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
                label = { Text("Vocabulary") },
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
              label = { Text("Name") },
              supportingText = { Text("ID: ${library.newConceptId(effectiveName, vocabulary)}") },
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
              modifier = Modifier.weight(1f),
            )
          }
        } else {
          original?.let { MonospaceId("${it.id} · version ${it.version}") }
          if (original != null && original.label.values.size > 1) {
            Text(
              "Translations: " +
                original.label.values.entries
                  .filter { it.key != LocalizedText.DEFAULT_LANGUAGE }
                  .joinToString { "${it.key} ${it.value}" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (English)") },
          minLines = 2,
          readOnly = readOnly,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(modifier = Modifier.weight(1f)) {
            DropdownSelectorOrText(
              label = "Data type",
              selectedText = dataType.label,
              options = ConceptDataType.entries,
              optionText = { it.label },
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
            label = { Text("Unit (optional)") },
            placeholder = { Text("kg") },
            supportingText = { Text("UCUM code") },
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.width(150.dp),
          )
        }
        if (dataType.hasCodeList) {
          Text("List values", style = MaterialTheme.typography.titleSmall)
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
                label = { Text("Value") },
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
                label = { Text("Label (English)") },
                singleLine = true,
                readOnly = readOnly,
                modifier = Modifier.weight(1f),
              )
              if (!readOnly && !draft.isExisting) {
                IconButton(onClick = { codes.removeAt(index) }) {
                  Icon(Icons.Outlined.Close, contentDescription = "Remove value")
                }
              }
            }
          }
          if (!readOnly) {
            TextButton(onClick = { codes.add(CodeListDraft("", "", isExisting = false)) }) {
              Icon(Icons.Outlined.Add, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Add value")
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(modifier = Modifier.weight(1f)) {
            DropdownSelectorOrText(
              label = "Aggregation",
              selectedText = aggregation.label,
              options = ConceptAggregation.entries,
              optionText = { it.label },
              readOnly = readOnly,
              onSelect = { aggregation = it },
            )
          }
          Box(modifier = Modifier.weight(1f)) {
            DropdownSelectorOrText(
              label = "Privacy",
              selectedText = privacy.label,
              options = PrivacyClass.entries,
              optionText = { it.label },
              readOnly = readOnly,
              onSelect = { privacy = it },
            )
          }
        }
        Text(
          "${aggregation.description} ${privacy.description}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = goals,
            onValueChange = { goals = it },
            label = {
              Text(
                if (library.isGlobalLibrary) "Goals (optional)" else "Suggested goals (optional)"
              )
            },
            supportingText = { Text("Separate goal IDs with commas") },
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.weight(1f),
          )
          Box(modifier = Modifier.width(170.dp)) {
            DropdownSelectorOrText(
              label = "Pillar",
              selectedText = pillar?.label ?: "None",
              options = listOf<ImpactPillar?>(null) + ImpactPillar.entries,
              optionText = { it?.label ?: "None" },
              readOnly = readOnly,
              onSelect = { pillar = it },
            )
          }
        }
        Box(modifier = Modifier.widthIn(max = 220.dp)) {
          DropdownSelectorOrText(
            label = "Status",
            selectedText = status.label,
            options = LibraryStatus.entries,
            optionText = { it.label },
            readOnly = readOnly,
            onSelect = { status = it },
          )
        }
        if (original?.references?.isNotEmpty() == true) {
          Text(
            "References: ${original.references.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
    confirmButton = {
      if (!readOnly) {
        Button(
          onClick = { error = onSave(build(), isNew) },
          enabled = label.isNotBlank() && (!isNew || effectiveName.isNotBlank()),
        ) {
          Text(if (isNew) "Add concept" else "Save")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(if (readOnly) "Close" else "Cancel") }
    },
  )
}

/** A [DropdownSelector], or a read-only text field showing [selectedText] when [readOnly]. */
@Composable
private fun <T> DropdownSelectorOrText(
  label: String,
  selectedText: String,
  options: List<T>,
  optionText: (T) -> String,
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

// ---------------------------------------------------------------------------------------------
// Templates
// ---------------------------------------------------------------------------------------------

/** The organization's Form templates, then global ones with a Hide toggle. */
@Composable
internal fun TemplatesPane(
  library: OrganizationLibraryUiState,
  actions: OrganizationActions,
  organization: Organization,
  isLoading: Boolean = false,
) {
  var creating by remember(organization.id) { mutableStateOf(false) }
  var editingDetails by remember(organization.id) { mutableStateOf<FormTemplate?>(null) }
  var editingQuestions by remember(organization.id) { mutableStateOf<FormTemplate?>(null) }
  var deleting by remember(organization.id) { mutableStateOf<FormTemplate?>(null) }
  val availablePurposes = remember(library.purposePacks) { library.purposePacks.map { it.entry } }

  if (creating) {
    TemplateDialog(
      title = "New template",
      initialTitle = "",
      initialDescription = "",
      initialPurposePackIds = emptyList(),
      availablePurposes = availablePurposes,
      onSave = { title, description, purposePackIds ->
        actions
          .createTemplate(
            organizationId = organization.id,
            title = title,
            description = description,
            questions = emptyList(),
            purposePackIds = purposePackIds,
          )
          .also {
            if (it == null) creating = false
          }
      },
      onDismiss = { creating = false },
    )
  }
  editingDetails?.let { template ->
    val initialPurposePackIds =
      remember(library.purposePacks, template.id) {
        library.purposePacks
          .map { it.entry }
          .filter { template.id in it.formTemplateIds }
          .map { it.id }
      }
    TemplateDialog(
      title = "Edit template",
      initialTitle = template.title.text,
      initialDescription = template.description.text,
      initialPurposePackIds = initialPurposePackIds,
      availablePurposes = availablePurposes,
      onSave = { title, description, purposePackIds ->
        actions
          .updateTemplateDetails(
            organizationId = template.organizationId,
            templateId = template.id,
            title = title,
            description = description,
            purposePackIds = purposePackIds,
          )
          .also {
            if (it == null) editingDetails = null
          }
      },
      onDismiss = { editingDetails = null },
    )
  }
  editingQuestions?.let { template ->
    val availableConcepts =
      remember(library.organizationConcepts, library.globalConcepts) {
        (library.organizationConcepts + library.globalConcepts).map { it.entry }
      }
    TemplateQuestionsDialog(
      template = template,
      availableConcepts = availableConcepts,
      onSave = { questions ->
        actions.updateTemplateQuestions(template.organizationId, template.id, questions).also {
          if (it == null) editingQuestions = null
        }
      },
      onDismiss = { editingQuestions = null },
    )
  }
  deleting?.let { template ->
    ConfirmDeleteDialog(
      title = "Delete ${template.title.text}?",
      body =
        "It's removed from the library and from Purpose Packs that use it. Surveys already " +
          "created from it keep their forms.",
      confirmLabel = "Delete template",
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
      DetailsCard("${organization.name} templates") {
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
              "No templates yet. Templates saved from this organization's forms appear here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
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
              onEditDetails = { editingDetails = row.entry },
              onEditQuestions = { editingQuestions = row.entry },
              onDelete = { deleting = row.entry },
            ) {
              actions.setGlobalEntryHidden(organization.id, row.entry.id, it)
            }
          }
        }
        if (library.canEdit) {
          OutlinedButton(onClick = { creating = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("+ New template")
          }
        }
      }
    }
    DetailsCard("Global templates") {
      Text(
        if (library.isGlobalLibrary) {
          "Forms every organization can add to its surveys."
        } else if (library.canEdit) {
          "Shared by All users. Hide the ones ${organization.name} doesn't use; surveys already " +
            "created from them are unaffected."
        } else {
          "Shared by All users. Ask a Manager to hide the ones ${organization.name} doesn't use."
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
            "No global templates yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
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
            onEditDetails = { editingDetails = row.entry },
            onEditQuestions = { editingQuestions = row.entry },
            onDelete = { deleting = row.entry },
          ) {
            actions.setGlobalEntryHidden(organization.id, row.entry.id, it)
          }
        }
      }
      if (library.isGlobalLibrary && library.canEdit) {
        OutlinedButton(onClick = { creating = true }) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("+ New template")
        }
      }
    }
  }
}

@Composable
private fun TemplateRow(
  row: LibraryEntryRow<FormTemplate>,
  associatedPurposes: List<PurposePack>,
  onEditDetails: () -> Unit,
  onEditQuestions: () -> Unit,
  onDelete: () -> Unit,
  onSetHidden: (Boolean) -> Unit,
) {
  val template = row.entry
  val questionCount = template.form.questions.size
  val linkedCount = template.linkedQuestionCount
  val badges = listOfNotNull("Hidden".takeIf { row.isHidden }, statusBadge(template.status))
  val summary =
    "$questionCount ${if (questionCount == 1) "question" else "questions"} · $linkedCount " +
      "linked to concepts"

  LibraryEntryCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EntryTitleRow(title = template.title.text, badges = badges, dimmed = row.isHidden)
          MonospaceId(template.id)
          if (template.description.text.isNotBlank()) {
            Text(template.description.text, style = MaterialTheme.typography.bodySmall)
          }
          Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (row.canEdit) {
          TextButton(onClick = onEditQuestions) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Edit questions")
          }
          IconButton(onClick = onEditDetails) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${template.title.text}")
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete ${template.title.text}")
          }
        }
        if (row.canHide) {
          TextButton(onClick = { onSetHidden(!row.isHidden) }) {
            Icon(
              if (row.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
              contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (row.isHidden) "Show" else "Hide")
          }
        }
      }
      if (associatedPurposes.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            "Purposes:",
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
      if (template.form.questions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          template.form.questions.forEachIndexed { idx, q ->
            val conceptSuffix = q.conceptLink?.conceptId?.let { " · → $it" } ?: ""
            val reqSuffix = if (q.required) " *" else ""
            Text(
              text = "${idx + 1}. ${q.label}$reqSuffix (${q.type.label}$conceptSuffix)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Purposes
// ---------------------------------------------------------------------------------------------

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
      title = "New purpose",
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
      title = "Edit purpose",
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
      title = "Delete ${pack.title.text}?",
      body = "It's no longer offered when creating surveys. Existing surveys keep their forms.",
      confirmLabel = "Delete purpose",
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
      DetailsCard("${organization.name} purposes") {
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
              "No purposes yet.",
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
            Text("+ New purpose")
          }
        }
      }
    }
    DetailsCard("Global purposes") {
      Text(
        if (library.isGlobalLibrary) {
          "Purposes every organization can pick from when creating a survey."
        } else if (library.canEdit) {
          "Shared by All users. Hiding a template also hides purposes that only use hidden " +
            "templates."
        } else {
          "Shared by All users. Ask a Manager to hide the ones ${organization.name} doesn't use."
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
            "No global purposes yet.",
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
          Text("+ New purpose")
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
        pack.pillar?.label,
        "Hidden".takeIf { row.isHidden },
        "Hidden with its templates".takeIf { hiddenByTemplates },
        statusBadge(pack.status),
      ),
    summary =
      listOfNotNull(
          pack.programIds
            .takeIf { it.isNotEmpty() }
            ?.let {
              "Reporting programs & frameworks: ${it.joinToString { id -> LibraryPrograms.label(id) }}"
            },
          pack.exportProfileIds.takeIf { it.isNotEmpty() }?.let { "Exports: ${it.joinToString()}" },
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
              "Templates:",
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

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

private fun statusBadge(status: LibraryStatus): String? =
  status.label.takeIf { status != LibraryStatus.STABLE }

/** A template or Purpose Pack row: details, then Rename/Delete or Hide/Show actions. */
@Composable
private fun EntryRowLayout(
  title: String,
  id: String? = null,
  description: String,
  badges: List<String>,
  summary: String,
  dimmed: Boolean,
  row: LibraryEntryRow<*>,
  entryLabel: String,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onSetHidden: (Boolean) -> Unit,
  extraContent: (@Composable () -> Unit)? = null,
) {
  LibraryEntryCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EntryTitleRow(title = title, badges = badges, dimmed = dimmed)
          if (id != null) {
            MonospaceId(id)
          }
          if (description.isNotBlank()) {
            Text(description, style = MaterialTheme.typography.bodySmall)
          }
          if (summary.isNotBlank()) {
            Text(
              summary,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (row.canEdit) {
          IconButton(onClick = onRename) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit $entryLabel")
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete $entryLabel")
          }
        }
        if (row.canHide) {
          TextButton(onClick = { onSetHidden(!row.isHidden) }) {
            Icon(
              if (row.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
              contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (row.isHidden) "Show" else "Hide")
          }
        }
      }
      extraContent?.invoke()
    }
  }
}

@Composable
private fun LibraryEntryCard(content: @Composable () -> Unit) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) { content() }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryTitleRow(title: String, badges: List<String>, dimmed: Boolean = false) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
      modifier = Modifier.align(Alignment.CenterVertically),
    )
    badges.forEach { badge ->
      Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.align(Alignment.CenterVertically),
      ) {
        Text(
          badge,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun MonospaceId(id: String) {
  Text(
    id,
    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Edits the English title, description, and associated purposes of a template. */
@Composable
private fun TemplateDialog(
  title: String,
  initialTitle: String,
  initialDescription: String,
  initialPurposePackIds: List<String>,
  availablePurposes: List<PurposePack>,
  onSave: (title: String, description: String, purposePackIds: List<String>) -> String?,
  onDismiss: () -> Unit,
) {
  var entryTitle by remember { mutableStateOf(initialTitle) }
  var description by remember { mutableStateOf(initialDescription) }
  val selectedPurposes = remember {
    mutableStateListOf<String>().apply { addAll(initialPurposePackIds) }
  }
  var error by remember { mutableStateOf<String?>(null) }
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
          label = { Text("Title (English)") },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it) } },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (English)") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        if (availablePurposes.isNotEmpty()) {
          Text(
            "Associated purposes",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            availablePurposes.forEach { purpose ->
              val isChecked = purpose.id in selectedPurposes
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      if (isChecked) selectedPurposes.remove(purpose.id)
                      else selectedPurposes.add(purpose.id)
                    }
                    .padding(vertical = 2.dp),
              ) {
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { checked ->
                    if (checked) selectedPurposes.add(purpose.id)
                    else selectedPurposes.remove(purpose.id)
                  },
                )
                Spacer(Modifier.width(8.dp))
                Text(purpose.title.text, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { error = onSave(entryTitle, description, selectedPurposes.toList()) },
        enabled = entryTitle.isNotBlank(),
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
    ) -> String?,
  onDismiss: () -> Unit,
) {
  var entryTitle by remember { mutableStateOf(initialTitle) }
  var description by remember { mutableStateOf(initialDescription) }
  var pillar by remember { mutableStateOf(initialPillar) }
  val selectedTemplates = remember {
    mutableStateListOf<String>().apply { addAll(initialTemplateIds) }
  }
  var error by remember { mutableStateOf<String?>(null) }
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
          label = { Text("Title (English)") },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it) } },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (English)") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        val pillarOptions = listOf<ImpactPillar?>(null) + ImpactPillar.entries
        DropdownSelector(
          label = "MAP Pillar",
          selectedText = pillar?.label ?: "None",
          options = pillarOptions,
          optionText = { it?.label ?: "None" },
          onSelect = { pillar = it },
        )
        if (availableTemplates.isNotEmpty()) {
          Text(
            "Associated templates",
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
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** Edits the English title and description of a template or Purpose Pack. */
@Composable
private fun RenameEntryDialog(
  title: String,
  initialTitle: String,
  initialDescription: String,
  onSave: (title: String, description: String) -> String?,
  onDismiss: () -> Unit,
) {
  var entryTitle by remember { mutableStateOf(initialTitle) }
  var description by remember { mutableStateOf(initialDescription) }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = entryTitle,
          onValueChange = {
            entryTitle = it
            error = null
          },
          label = { Text("Title (English)") },
          singleLine = true,
          isError = error != null,
          supportingText = error?.let { { Text(it) } },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (English)") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { error = onSave(entryTitle, description) },
        enabled = entryTitle.isNotBlank(),
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun ConfirmDeleteDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
