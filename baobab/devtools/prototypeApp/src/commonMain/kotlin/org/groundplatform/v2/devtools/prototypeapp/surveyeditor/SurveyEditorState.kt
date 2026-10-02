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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import org.groundplatform.v2.devtools.prototypeapp.formeditor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.formeditor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.formeditor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.formeditor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.formeditor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorState
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.formeditor.moved
import org.groundplatform.v2.devtools.prototypeapp.formeditor.slugify

/** Which pane of the Survey editor is showing. */
sealed interface SurveyEditorSection {
  data object Details : SurveyEditorSection

  data object Sharing : SurveyEditorSection

  data class Form(val key: String) : SurveyEditorSection

  data class Dataset(val key: String) : SurveyEditorSection
}

/** A Form in the survey, each with its own [FormEditorState]. */
class SurveyFormEntry(val key: String, val editor: FormEditorState)

/**
 * Observable state for the Survey editor page: survey details, sharing, Forms, Map layers, and Data
 * tables, plus the currently selected section.
 *
 * Initialized from the published [SurveyEditorDraft] loaded from the local data store. Edits are
 * held here as an unpublished draft until [markPublished] is called (after [toDraft] has been saved
 * to the store) or they are thrown away with [discardChanges].
 */
class SurveyEditorState(draft: SurveyEditorDraft = SurveyEditorDraft.blank(surveyId = "")) {
  /** The last published version of the survey, which unpublished edits are compared against. */
  private var published: SurveyEditorDraft by mutableStateOf(draft)

  private var nextId by mutableStateOf(draft.nextKeyId)

  var details: SurveyDetails by mutableStateOf(draft.details)
    private set

  var sharing: SharingSettings by mutableStateOf(draft.sharing)
    private set

  var forms: List<SurveyFormEntry> by mutableStateOf(formEntries(draft))
    private set

  var datasets: List<EntityDataset> by mutableStateOf(draft.datasets)
    private set

  /** Snapshot of the current edits, for saving to the local data store. */
  fun toDraft(): SurveyEditorDraft =
    SurveyEditorDraft(
      details = details,
      sharing = sharing,
      forms = forms.map { SurveyEditorForm(it.key, it.editor.form) },
      datasets = datasets,
      nextKeyId = nextId,
    )

  /**
   * Whether the draft differs from the published survey. The key counter is ignored, so adding and
   * then deleting an item doesn't count as a change.
   */
  val hasUnpublishedChanges: Boolean
    get() = toDraft().copy(nextKeyId = 0) != published.copy(nextKeyId = 0)

  /** Number of validation issues across all Forms, Map layers, and Data tables in the draft. */
  val issueCount: Int
    get() = forms.sumOf { it.editor.issues.size } + datasets.sumOf { datasetIssues(it).size }

  /** Whether the draft can be published: it has unpublished changes and no validation issues. */
  val canPublish: Boolean
    get() = hasUnpublishedChanges && issueCount == 0

  /** Records the current draft as published. Call after saving [toDraft] to the data store. */
  fun markPublished() {
    published = toDraft()
  }

  /** Throws away unpublished edits, restoring the published survey. */
  fun discardChanges() {
    val draft = published
    nextId = draft.nextKeyId
    details = draft.details
    sharing = draft.sharing
    forms = formEntries(draft)
    datasets = draft.datasets
    val current = section
    val stillExists =
      when (current) {
        is SurveyEditorSection.Form -> forms.any { it.key == current.key }
        is SurveyEditorSection.Dataset -> datasets.any { it.key == current.key }
        else -> true
      }
    if (!stillExists) section = SurveyEditorSection.Details
  }

  private fun formEntries(draft: SurveyEditorDraft): List<SurveyFormEntry> =
    draft.forms.map { SurveyFormEntry(it.key, FormEditorState(it.form)) }

  var section: SurveyEditorSection by mutableStateOf(SurveyEditorSection.Details)
    private set

  val mapLayers: List<EntityDataset>
    get() = datasets.filter { it.kind == DatasetKind.MAP_LAYER }

  val dataTables: List<EntityDataset>
    get() = datasets.filter { it.kind == DatasetKind.DATA_TABLE }

  val selectedForm: SurveyFormEntry?
    get() =
      (section as? SurveyEditorSection.Form)?.let { s -> forms.firstOrNull { it.key == s.key } }

  val selectedDataset: EntityDataset?
    get() =
      (section as? SurveyEditorSection.Dataset)?.let { s ->
        datasets.firstOrNull { it.key == s.key }
      }

  fun select(section: SurveyEditorSection) {
    this.section = section
  }

  fun datasetIssues(dataset: EntityDataset): List<DatasetIssue> =
    EntityDatasetValidator.validate(dataset, datasets.map { it.id })

  // Survey details & sharing ------------------------------------------------------------------

  fun updateDetails(transform: (SurveyDetails) -> SurveyDetails) {
    details = transform(details)
  }

  /**
   * Adds a language to the supported languages list if not already present. If [defaultLanguage] is
   * currently empty, sets it to this language as well.
   */
  fun addSupportedLanguage(code: String) {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty()) return
    updateDetails { current ->
      val currentList = current.supportedLanguages
      val newList = if (normalized in currentList) currentList else currentList + normalized
      val defaultLang = current.defaultLanguage.ifEmpty { normalized }
      current.copy(supportedLanguages = newList, defaultLanguage = defaultLang)
    }
  }

  /**
   * Removes a language from supported languages. If the removed language was the default language,
   * resets default language to the first remaining supported language or empty string.
   */
  fun removeSupportedLanguage(code: String) {
    val normalized = code.trim().lowercase()
    updateDetails { current ->
      val newList = current.supportedLanguages.filterNot { it.lowercase() == normalized }
      val newDefault =
        if (current.defaultLanguage.lowercase() == normalized) {
          newList.firstOrNull() ?: ""
        } else {
          current.defaultLanguage
        }
      current.copy(supportedLanguages = newList, defaultLanguage = newDefault)
    }
  }

  /** Sets the default language. Also ensures the language is included in [supportedLanguages]. */
  fun setDefaultLanguage(code: String) {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty()) return
    updateDetails { current ->
      val currentList = current.supportedLanguages
      val newList = if (normalized in currentList) currentList else currentList + normalized
      current.copy(defaultLanguage = normalized, supportedLanguages = newList)
    }
  }

  /** Sets or clears the survey area and boundaries. */
  fun setSurveyArea(area: SurveyArea?) {
    updateDetails { it.copy(surveyArea = area) }
  }

  fun updateSharing(transform: (SharingSettings) -> SharingSettings) {
    sharing = transform(sharing)
  }

  /**
   * Adds (or updates the role of) a collaborator. New people get a pending invite with a fresh
   * invite link token. Returns an error message, or `null`.
   */
  fun inviteCollaborator(email: String, role: CollaboratorRole): String? {
    val normalized = email.trim().lowercase()
    if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(normalized)) {
      return "Enter a valid email address."
    }
    if (normalized == sharing.ownerEmail.lowercase()) return "That's the survey owner."
    val existing = sharing.collaborators.indexOfFirst { it.email.lowercase() == normalized }
    val updated = sharing.collaborators.toMutableList()
    if (existing >= 0) {
      updated[existing] = updated[existing].copy(role = role)
    } else {
      updated += Collaborator(normalized, role, inviteToken = newInviteToken())
    }
    sharing = sharing.copy(collaborators = updated)
    return null
  }

  /** Invalidates the old invite link of a pending collaborator and issues a new one. */
  fun resetInviteLink(email: String) {
    updateCollaborator(email) {
      if (it.status == InvitationStatus.PENDING) it.copy(inviteToken = newInviteToken()) else it
    }
  }

  /**
   * Simulates the invitee opening their invite link, signing in, and accepting. Their account's
   * name and photo are cached on the ACL entry for display in the Sharing pane.
   */
  fun acceptInvite(
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String = "",
  ): String? {
    val name = displayName.trim()
    if (name.isEmpty()) return "Enter a name."
    val person = sharing.collaborators.firstOrNull { it.email == email } ?: return "Not invited."
    if (person.status == InvitationStatus.ACCEPTED) return "Already joined."
    updateCollaborator(email) {
      it.copy(
        status = InvitationStatus.ACCEPTED,
        inviteToken = null,
        userId = "uid-${email.substringBefore('@').replace('.', '-')}",
        profile = CachedProfile(name, photoUrl, cachedOn),
      )
    }
    return null
  }

  fun setCollaboratorRole(email: String, role: CollaboratorRole) {
    updateCollaborator(email) { it.copy(role = role) }
  }

  fun removeCollaborator(email: String) {
    sharing = sharing.copy(collaborators = sharing.collaborators.filterNot { it.email == email })
  }

  private fun updateCollaborator(email: String, transform: (Collaborator) -> Collaborator) {
    sharing =
      sharing.copy(
        collaborators = sharing.collaborators.map { if (it.email == email) transform(it) else it }
      )
  }

  private fun newInviteToken(): String {
    val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
    fun chunk() = (1..4).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    return "${chunk()}-${chunk()}"
  }

  // Forms ------------------------------------------------------------------------------------

  fun addForm() {
    val title = uniqueTitle("New form", forms.map { it.editor.form.title })
    val formId = FormIds.newFormId()
    val formKey = newKey("f")
    val datasetKey = newKey("d")

    val blankForm = EditorFormTemplates.blank(formId, title)
    val entry = SurveyFormEntry(formKey, FormEditorState(blankForm))

    // By default, create a linked entity (Map layer) for the new form.
    val linkedDataset =
      EntityDataset(
        key = datasetKey,
        kind = DatasetKind.MAP_LAYER,
        id = uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        geometryKind = GeometryKind.POINT,
        keyProperty = "id",
        labelProperty = "id",
        linkedFormKey = formKey,
        properties =
          listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)) +
            blankForm.questions
              .filter { it.type != EditorQuestionType.NOTE }
              .map { q ->
                EntityProperty(
                  name = q.name,
                  label = q.label.ifBlank { q.name },
                  type =
                    when {
                      q.type == EditorQuestionType.INTEGER -> PropertyType.INTEGER
                      q.type == EditorQuestionType.DECIMAL -> PropertyType.DECIMAL
                      q.type == EditorQuestionType.DATE -> PropertyType.DATE
                      else -> PropertyType.TEXT
                    },
                  required = q.required,
                )
              },
      )

    datasets = datasets + linkedDataset
    forms = forms + entry
    section = SurveyEditorSection.Form(entry.key)
  }

  /** Creates a new Map layer or Data table backed by and linked to [formKey]. */
  fun createDatasetForForm(formKey: String, kind: DatasetKind = DatasetKind.MAP_LAYER) {
    val formEntry = forms.firstOrNull { it.key == formKey } ?: return
    val form = formEntry.editor.form
    val datasetKey = newKey("d")
    val title =
      uniqueTitle(
        form.title.ifBlank { "New ${kind.singular.lowercase()}" },
        datasets.map { it.displayName },
      )
    val datasetId = uniqueId(slugify(title), datasets.map { it.id })

    val formProps =
      form.questions
        .filter { it.type != EditorQuestionType.NOTE }
        .map { q ->
          EntityProperty(
            name = q.name,
            label = q.label.ifBlank { q.name },
            type =
              when {
                q.type == EditorQuestionType.INTEGER -> PropertyType.INTEGER
                q.type == EditorQuestionType.DECIMAL -> PropertyType.DECIMAL
                q.type == EditorQuestionType.DATE -> PropertyType.DATE
                else -> PropertyType.TEXT
              },
            required = q.required,
          )
        }

    val idProp = EntityProperty("id", "ID", PropertyType.TEXT, required = true)
    val properties =
      if (formProps.any { it.name == "id" }) formProps else listOf(idProp) + formProps

    val dataset =
      EntityDataset(
        key = datasetKey,
        kind = kind,
        id = datasetId,
        displayName = title,
        geometryKind = GeometryKind.POINT,
        keyProperty = properties.first().name,
        labelProperty = properties.getOrNull(1)?.name ?: properties.first().name,
        linkedFormKey = formKey,
        properties = properties,
      )

    datasets = datasets + dataset
    section = SurveyEditorSection.Dataset(dataset.key)
  }

  /** Creates a new Form backed by and linked to [datasetKey]. */
  fun createFormForDataset(datasetKey: String) {
    val dataset = datasets.firstOrNull { it.key == datasetKey } ?: return
    val formKey = newKey("f")
    val title = uniqueTitle("${dataset.displayName} form", forms.map { it.editor.form.title })
    val formId = FormIds.newFormId()

    var questionIdx = 1
    val questions = mutableListOf<EditorQuestion>()

    if (dataset.kind == DatasetKind.MAP_LAYER) {
      questions +=
        EditorQuestion(
          key = "q${questionIdx++}",
          name = "location",
          type = EditorQuestionType.LOCATION,
          label = "Location",
          required = true,
        )
    }

    dataset.properties.forEach { prop ->
      val qType =
        when (prop.type) {
          PropertyType.INTEGER -> EditorQuestionType.INTEGER
          PropertyType.DECIMAL -> EditorQuestionType.DECIMAL
          PropertyType.DATE -> EditorQuestionType.DATE
          PropertyType.BOOLEAN -> EditorQuestionType.SELECT_ONE
          PropertyType.TEXT -> EditorQuestionType.TEXT
        }
      val choices =
        if (prop.type == PropertyType.BOOLEAN) {
          listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No"))
        } else {
          emptyList()
        }
      questions +=
        EditorQuestion(
          key = "q${questionIdx++}",
          name = prop.name,
          type = qType,
          label = prop.label,
          required = prop.required,
          choices = choices,
        )
    }

    val form = EditorForm(formId = formId, title = title, questions = questions)
    val formEntry = SurveyFormEntry(formKey, FormEditorState(form))

    // Update dataset to link to this form
    updateDataset(datasetKey) { it.copy(linkedFormKey = formKey) }

    forms = forms + formEntry
    section = SurveyEditorSection.Form(formKey)
  }

  /** Unlinks [datasetKey] from its linked form, making its schema directly editable. */
  fun unlinkDataset(datasetKey: String) {
    updateDataset(datasetKey) { it.copy(linkedFormKey = null) }
  }

  /** Synchronizes the schema of all datasets linked to [formEntry] with the form's questions. */
  fun syncDatasetsLinkedToForm(formEntry: SurveyFormEntry) {
    val form = formEntry.editor.form
    val formProps =
      form.questions
        .filter { it.type != EditorQuestionType.NOTE }
        .map { q ->
          EntityProperty(
            name = q.name,
            label = q.label.ifBlank { q.name },
            type =
              when {
                q.type == EditorQuestionType.INTEGER -> PropertyType.INTEGER
                q.type == EditorQuestionType.DECIMAL -> PropertyType.DECIMAL
                q.type == EditorQuestionType.DATE -> PropertyType.DATE
                else -> PropertyType.TEXT
              },
            required = q.required,
          )
        }

    val linkedDatasets = datasets.filter { it.linkedFormKey == formEntry.key }
    if (linkedDatasets.isEmpty()) return

    datasets = datasets.map { d ->
      if (d.linkedFormKey != formEntry.key) d
      else {
        // Retain existing key property if not in form, or ensure at least one property exists
        val idProp =
          d.properties.firstOrNull { it.name == d.keyProperty }
            ?: EntityProperty("id", "ID", PropertyType.TEXT, required = true)
        val combinedProps =
          if (formProps.any { it.name == idProp.name }) formProps else listOf(idProp) + formProps
        val keyProp =
          if (combinedProps.any { it.name == d.keyProperty }) d.keyProperty
          else combinedProps.first().name
        val labelProp =
          if (combinedProps.any { it.name == d.labelProperty }) d.labelProperty else keyProp

        d.copy(
          properties = combinedProps,
          keyProperty = keyProp,
          labelProperty = labelProp,
        )
      }
    }
  }

  fun deleteForm(key: String) {
    val index = forms.indexOfFirst { it.key == key }
    if (index < 0) return
    // Unlink any datasets linked to this deleted form
    datasets = datasets.map { if (it.linkedFormKey == key) it.copy(linkedFormKey = null) else it }
    forms = forms.filterNot { it.key == key }
    section =
      forms.getOrNull(index.coerceAtMost(forms.lastIndex))?.let { SurveyEditorSection.Form(it.key) }
        ?: SurveyEditorSection.Details
  }

  // Map layers & Data tables -----------------------------------------------------------------

  fun addDataset(kind: DatasetKind) {
    val title = uniqueTitle("New ${kind.singular.lowercase()}", datasets.map { it.displayName })
    val dataset =
      EntityDataset(
        key = newKey("d"),
        kind = kind,
        id = uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        geometryKind = GeometryKind.POINT,
        keyProperty = "id",
        labelProperty = "name",
        properties =
          listOf(
            EntityProperty("id", "ID", PropertyType.TEXT, required = true),
            EntityProperty("name", "Name", PropertyType.TEXT),
          ),
      )
    datasets = datasets + dataset
    section = SurveyEditorSection.Dataset(dataset.key)
  }

  fun deleteDataset(key: String) {
    val dataset = datasets.firstOrNull { it.key == key } ?: return
    val siblings = datasets.filter { it.kind == dataset.kind }
    val index = siblings.indexOf(dataset)
    datasets = datasets.filterNot { it.key == key }
    val remaining = siblings.filterNot { it.key == key }
    section =
      remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.let {
        SurveyEditorSection.Dataset(it.key)
      } ?: SurveyEditorSection.Details
  }

  fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset) {
    datasets = datasets.map { if (it.key == key) transform(it) else it }
  }

  /** Moves Form [key] to [toIndex] in the Forms list. */
  fun moveForm(key: String, toIndex: Int) {
    forms = forms.moved(forms.indexOfFirst { it.key == key }, toIndex)
  }

  /**
   * Moves Map layer or Data table [key] to [toIndex] among datasets of the same kind. Datasets of
   * the other kind keep their positions.
   */
  fun moveDataset(key: String, toIndex: Int) {
    val kind = datasets.firstOrNull { it.key == key }?.kind ?: return
    val siblings = datasets.filter { it.kind == kind }
    val reordered = siblings.moved(siblings.indexOfFirst { it.key == key }, toIndex).iterator()
    datasets = datasets.map { if (it.kind == kind) reordered.next() else it }
  }

  fun addProperty(key: String) {
    updateDataset(key) { d ->
      val name = uniqueId("property", d.properties.map { it.name })
      d.copy(properties = d.properties + EntityProperty(name, "New property"))
    }
  }

  /** Updates property [index]; renaming it also renames the matching cell values in every row. */
  fun updateProperty(key: String, index: Int, property: EntityProperty) {
    updateDataset(key) { d ->
      val old = d.properties.getOrNull(index) ?: return@updateDataset d
      val renamed = old.name != property.name
      d.copy(
        properties = d.properties.toMutableList().apply { set(index, property) },
        keyProperty = if (renamed && d.keyProperty == old.name) property.name else d.keyProperty,
        labelProperty =
          if (renamed && d.labelProperty == old.name) property.name else d.labelProperty,
        rows =
          if (!renamed) d.rows
          else
            d.rows.map { row ->
              val value = row.values[old.name]
              if (value == null) row
              else row.copy(values = row.values - old.name + (property.name to value))
            },
      )
    }
  }

  fun removeProperty(key: String, index: Int) {
    updateDataset(key) { d ->
      val removed = d.properties.getOrNull(index) ?: return@updateDataset d
      val remaining = d.properties.filterIndexed { i, _ -> i != index }
      d.copy(
        properties = remaining,
        keyProperty =
          if (d.keyProperty == removed.name) remaining.firstOrNull()?.name.orEmpty()
          else d.keyProperty,
        labelProperty =
          if (d.labelProperty == removed.name) remaining.firstOrNull()?.name.orEmpty()
          else d.labelProperty,
        rows = d.rows.map { it.copy(values = it.values - removed.name) },
      )
    }
  }

  /**
   * Appends an entity. Map layer features use [geometry] when given (e.g. vertices drawn on the
   * map), otherwise a small default shape at [at] or near existing features.
   */
  fun addRow(key: String, at: LatLng? = null, geometry: List<LatLng>? = null): String {
    val rowKey = newKey("r")
    updateDataset(key) { d ->
      val keyValue =
        uniqueId(
          "${d.id.take(3).uppercase()}-${d.rows.size + 1}",
          d.rows.map { it.values[d.keyProperty].orEmpty() },
        )
      val shape =
        if (d.kind == DatasetKind.MAP_LAYER) geometry ?: defaultGeometry(d, at) else emptyList()
      d.copy(rows = d.rows + EntityRow(rowKey, mapOf(d.keyProperty to keyValue), shape))
    }
    return rowKey
  }

  fun updateCell(key: String, rowKey: String, property: String, value: String) {
    updateDataset(key) { d ->
      d.copy(
        rows =
          d.rows.map {
            if (it.key == rowKey) it.copy(values = it.values + (property to value)) else it
          }
      )
    }
  }

  fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>) {
    updateDataset(key) { d ->
      d.copy(rows = d.rows.map { if (it.key == rowKey) it.copy(geometry = geometry) else it })
    }
  }

  fun removeRow(key: String, rowKey: String) {
    updateDataset(key) { d -> d.copy(rows = d.rows.filterNot { it.key == rowKey }) }
  }

  // Helpers ----------------------------------------------------------------------------------

  private fun defaultGeometry(d: EntityDataset, at: LatLng?): List<LatLng> {
    val all = d.rows.flatMap { it.geometry }
    val c =
      at
        ?: run {
          val center =
            if (all.isEmpty()) LatLng(-0.4180, 36.9530)
            else LatLng(all.map { it.lat }.average(), all.map { it.lng }.average())
          // Offset successive features so they don't overlap.
          val step = 0.002 * ((d.rows.size % 5) - 2)
          LatLng(center.lat + step, center.lng - step)
        }
    val s = 0.0015
    return when (d.geometryKind) {
      GeometryKind.POINT -> listOf(c)
      GeometryKind.LINE -> listOf(c, LatLng(c.lat + s, c.lng + s), LatLng(c.lat + s, c.lng + 2 * s))
      GeometryKind.POLYGON ->
        listOf(c, LatLng(c.lat, c.lng + s), LatLng(c.lat - s, c.lng + s), LatLng(c.lat - s, c.lng))
    }
  }

  private fun newKey(prefix: String) = "$prefix${nextId++}"

  private fun uniqueTitle(base: String, taken: List<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("$base $n" in taken) n++
    return "$base $n"
  }

  private fun uniqueId(base: String, taken: List<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
  }
}
