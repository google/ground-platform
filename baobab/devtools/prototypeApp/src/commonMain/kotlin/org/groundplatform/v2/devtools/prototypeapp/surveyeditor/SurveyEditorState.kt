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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.yield
import org.groundplatform.v2.core.sampling.GeneratedPlot
import org.groundplatform.v2.core.sampling.SampleEstimate
import org.groundplatform.v2.core.sampling.SamplingArea
import org.groundplatform.v2.core.sampling.SamplingEngine
import org.groundplatform.v2.core.sampling.SamplingException
import org.groundplatform.v2.core.sampling.Stratum
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InviteLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.Collaborator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDatasetValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GenerationRecord
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LayerStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PropertyType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleAreaSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleMethod
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyDetails
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.slugify
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toEditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toGeoCoord
import org.groundplatform.v2.devtools.prototypeapp.formeditor.FormEditorState
import org.groundplatform.v2.devtools.prototypeapp.formeditor.moved
import org.groundplatform.v2.devtools.prototypeapp.platformEpochMillis

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
 *
 * Sample plot layers are generated with [samplingEngine] (by default [SamplingEngine.Default]).
 * [submissionCount] returns how many submissions reference the features of a dataset (by dataset
 * ID), which blocks regenerating its sample plots. Between generated chunks the state calls
 * [yieldBetweenChunks] so single-threaded platforms (WasmJS) stay responsive.
 */
class SurveyEditorState(
  draft: SurveyEditorDraft = SurveyEditorDraft.blank(surveyId = ""),
  samplingEngine: SamplingEngine? = null,
  private val submissionCount: (datasetId: String) -> Int = { 0 },
  private val yieldBetweenChunks: suspend () -> Unit = { yield() },
  private val now: () -> String = { isoUtc(platformEpochMillis()) },
) {
  /** Resolved on first use, so creating an editor never depends on the engine. */
  private val engine: SamplingEngine by lazy { samplingEngine ?: SamplingEngine.Default }

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

  /** The sample generation in progress, if any. */
  var generation: SampleGenerationProgress? by mutableStateOf(null)
    private set

  /** Last generation error per dataset key, shown as a dataset issue until the next attempt. */
  private var generationErrors: Map<String, String> by mutableStateOf(emptyMap())

  private var cancelRequested = false

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

  /**
   * Problems in the sharing settings (e.g. an organization-only policy without an organization).
   */
  val sharingIssues: List<String>
    get() = SurveyAccess.issues(sharing, details.organizationId)

  /**
   * Number of validation issues across sharing and all Forms, Map layers, and Data tables in the
   * draft.
   */
  val issueCount: Int
    get() =
      sharingIssues.size +
        forms.sumOf { it.editor.issues.size } +
        datasets.sumOf { datasetIssues(it).size }

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
    generationErrors = emptyMap()
    organizationNotice = null
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
    draft.forms.map { newFormEntry(it.key, it.form) }

  /** Creates the editor for Form [key], whose save-to logic sees this survey's datasets. */
  private fun newFormEntry(key: String, form: EditorForm): SurveyFormEntry =
    SurveyFormEntry(key, FormEditorState(form) { datasets.map { it.toEditorDataset(key) } })

  var section: SurveyEditorSection by mutableStateOf(SurveyEditorSection.Details)
    private set

  /**
   * Width of the Survey editor's left-hand navigation panel, in dp. Adjusted by dragging the
   * panel's right border ([updateSidePanelWidth]); always within [MIN_SIDE_PANEL_WIDTH_DP] and
   * [MAX_SIDE_PANEL_WIDTH_DP].
   */
  var sidePanelWidthDp by mutableStateOf(DEFAULT_SIDE_PANEL_WIDTH_DP)
    private set

  /**
   * Sets the Survey editor's left-hand navigation panel width to [widthDp], clamped to
   * [MIN_SIDE_PANEL_WIDTH_DP]..[MAX_SIDE_PANEL_WIDTH_DP]. Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float) {
    if (!widthDp.isFinite()) return
    sidePanelWidthDp = widthDp.coerceIn(MIN_SIDE_PANEL_WIDTH_DP, MAX_SIDE_PANEL_WIDTH_DP)
  }

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

  fun datasetIssues(dataset: EntityDataset): List<DatasetIssue> {
    val issues = EntityDatasetValidator.validate(dataset, datasets.map { it.id })
    val error = generationErrors[dataset.key] ?: return issues
    return issues + DatasetIssue(error)
  }

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

  /**
   * Notice about a side effect of the last [setOrganization] call (the general access policy was
   * changed back to "Restricted"), or `null`. Cleared with [dismissOrganizationNotice].
   */
  var organizationNotice: String? by mutableStateOf(null)
    private set

  fun dismissOrganizationNotice() {
    organizationNotice = null
  }

  /**
   * Moves the survey into [organizationId], or makes it a personal survey when `null`. Clearing the
   * organization while general access is [SharingPolicy.ORGANIZATION] falls back to
   * [SharingPolicy.RESTRICTED] and sets [organizationNotice], since nobody could use that policy.
   */
  fun setOrganization(organizationId: String?) {
    val normalized = organizationId?.takeIf { it.isNotBlank() }
    organizationNotice = null
    updateDetails { it.copy(organizationId = normalized) }
    if (normalized == null && sharing.policy == SharingPolicy.ORGANIZATION) {
      sharing = sharing.copy(policy = SharingPolicy.RESTRICTED)
      organizationNotice =
        "General access was changed to \"${SharingPolicy.RESTRICTED.label}\" because the survey " +
          "no longer belongs to an organization."
    }
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

  private fun newInviteToken(): String = InviteLinks.newToken()

  // Forms ------------------------------------------------------------------------------------

  fun addForm() {
    val title = uniqueTitle("New form", forms.map { it.editor.form.title })
    val formId = FormIds.newFormId()
    val formKey = newKey("f")
    val datasetKey = newKey("d")

    val blankForm = EditorFormTemplates.blank(formId, title)
    val entry = newFormEntry(formKey, blankForm)

    // By default, submissions add to a linked Map layer (if the form captures a location) or Data
    // table (otherwise).
    val linkedDataset =
      EntityDataset(
        key = datasetKey,
        kind = datasetKindFor(blankForm),
        id = uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        geometryKind = GeometryKind.POINT,
        keyProperty = "id",
        labelProperty = "id",
        linkedFormKey = formKey,
        properties =
          listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)) +
            formProperties(blankForm),
      )

    datasets = datasets + linkedDataset
    forms = forms + entry
    section = SurveyEditorSection.Form(entry.key)
  }

  /**
   * Creates a new Map layer or Data table backed by and linked to [formKey]. [kind] defaults to a
   * Map layer if the form has a geometry question, otherwise a Data table. When [open] is true, the
   * new dataset is selected.
   */
  fun createDatasetForForm(formKey: String, kind: DatasetKind? = null, open: Boolean = true) {
    val formEntry = forms.firstOrNull { it.key == formKey } ?: return
    val form = formEntry.editor.form
    val resolvedKind = kind ?: datasetKindFor(form)
    val datasetKey = newKey("d")
    val title =
      uniqueTitle(
        form.title.ifBlank { "New ${resolvedKind.singular.lowercase()}" },
        datasets.map { it.displayName },
      )
    val datasetId = uniqueId(slugify(title), datasets.map { it.id })

    val formProps = formProperties(form)

    val idProp = EntityProperty("id", "ID", PropertyType.TEXT, required = true)
    val properties =
      if (formProps.any { it.name == "id" }) formProps else listOf(idProp) + formProps

    val dataset =
      EntityDataset(
        key = datasetKey,
        kind = resolvedKind,
        id = datasetId,
        displayName = title,
        geometryKind = geometryKindFor(form),
        keyProperty = properties.first().name,
        labelProperty = properties.getOrNull(1)?.name ?: properties.first().name,
        linkedFormKey = formKey,
        properties = properties,
      )

    datasets = datasets + dataset
    if (open) section = SurveyEditorSection.Dataset(dataset.key)
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
      // The geometry question matches the layer's features: a point, line, or polygon.
      val geometryType =
        when (dataset.geometryKind) {
          GeometryKind.POINT -> EditorQuestionType.LOCATION
          GeometryKind.LINE -> EditorQuestionType.LINE
          GeometryKind.POLYGON -> EditorQuestionType.POLYGON
        }
      questions +=
        EditorQuestion(
          key = "q${questionIdx++}",
          name = "location",
          type = geometryType,
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
    val formEntry = newFormEntry(formKey, form)

    // Update dataset to link to this form
    updateDataset(datasetKey) { it.copy(linkedFormKey = formKey) }

    forms = forms + formEntry
    section = SurveyEditorSection.Form(formKey)
  }

  /** Unlinks [datasetKey] from its linked form, making its schema directly editable. */
  fun unlinkDataset(datasetKey: String) {
    updateDataset(datasetKey) { it.copy(linkedFormKey = null) }
  }

  /**
   * Synchronizes the schema of all datasets linked to [formEntry] with the form's questions. A
   * linked dataset is a Map layer if the form has a geometry question (whose type sets the layer's
   * point / line / polygon kind), otherwise a Data table.
   */
  fun syncDatasetsLinkedToForm(formEntry: SurveyFormEntry) {
    val form = formEntry.editor.form
    val formProps = formProperties(form)
    val kind = datasetKindFor(form)

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
          kind = kind,
          // Generated layers take their geometry from the sample design, not the form.
          geometryKind =
            if (form.hasGeometry && !d.isGenerated) geometryKindFor(form) else d.geometryKind,
          properties = combinedProps,
          keyProperty = keyProp,
          labelProperty = labelProp,
        )
      }
    }
  }

  /**
   * Switches Form [formKey] between adding new features and updating existing ones.
   *
   * Switching to updates detaches the dataset the form was adding to: it's deleted if it has no
   * features yet, otherwise kept but unlinked. Switching back to adding features creates a new
   * linked dataset if none is linked.
   */
  fun setFormSaveToMode(formKey: String, mode: SaveToMode) {
    val entry = forms.firstOrNull { it.key == formKey } ?: return
    // Pick the update target while the form's own dataset is still linked, so it isn't chosen.
    entry.editor.setSaveToMode(mode)
    when (mode) {
      SaveToMode.UPDATE -> {
        val linked = datasets.filter { it.linkedFormKey == formKey }
        val emptyKeys = linked.filter { it.rows.isEmpty() }.map { it.key }.toSet()
        datasets =
          datasets
            .filterNot { it.key in emptyKeys }
            .map { if (it.linkedFormKey == formKey) it.copy(linkedFormKey = null) else it }
      }
      SaveToMode.CREATE ->
        if (datasets.none { it.linkedFormKey == formKey }) {
          createDatasetForForm(formKey, open = false)
        }
    }
  }

  /** Forms whose submissions update features of [dataset]. */
  fun formsUpdating(dataset: EntityDataset): List<SurveyFormEntry> = forms.filter {
    val saveTo = it.editor.form.saveTo
    saveTo.mode == SaveToMode.UPDATE && saveTo.targetDatasetId == dataset.id
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
    val old = datasets.firstOrNull { it.key == key } ?: return
    val updated = transform(old)
    datasets = datasets.map { if (it.key == key) updated else it }
    if (updated.id != old.id) {
      forms.forEach { it.editor.renameTargetDataset(old.id, updated.id) }
    }
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

  /**
   * Updates property [index]; renaming it also renames the matching cell values in every row and
   * the property references of Forms that update the dataset.
   */
  fun updateProperty(key: String, index: Int, property: EntityProperty) {
    val dataset = datasets.firstOrNull { it.key == key } ?: return
    val oldName = dataset.properties.getOrNull(index)?.name
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
    if (oldName != null && oldName != property.name) {
      forms.forEach { it.editor.renameTargetProperty(dataset.id, oldName, property.name) }
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

  /**
   * Replaces a feature's vertices. Ignored for generated sample plots, whose geometry is locked.
   */
  fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>) {
    updateDataset(key) { d ->
      if (d.isGenerated) d
      else d.copy(rows = d.rows.map { if (it.key == rowKey) it.copy(geometry = geometry) else it })
    }
  }

  fun removeRow(key: String, rowKey: String) {
    updateDataset(key) { d -> d.copy(rows = d.rows.filterNot { it.key == rowKey }) }
  }

  // Map layer import ---------------------------------------------------------------------------

  /** Creates a Map layer from [plan] (see [MapLayerImporter]) and opens it. Returns its key. */
  fun importMapLayer(plan: MapLayerImportPlan): String {
    val title = uniqueTitle(plan.name, datasets.map { it.displayName })
    val datasetKey = newKey("d")
    val dataset =
      EntityDataset(
        key = datasetKey,
        kind = DatasetKind.MAP_LAYER,
        id = uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        geometryKind = plan.geometryKind,
        keyProperty = plan.keyProperty,
        labelProperty = plan.labelProperty,
        properties = plan.properties,
        rows = plan.rows.map { (values, geometry) -> EntityRow(newKey("r"), values, geometry) },
      )
    datasets = datasets + dataset
    section = SurveyEditorSection.Dataset(datasetKey)
    return datasetKey
  }

  // Sample plots -------------------------------------------------------------------------------

  /** Polygon Map layers that can provide strata (generated sample plots can't). */
  val strataLayers: List<EntityDataset>
    get() = mapLayers.filter { it.geometryKind == GeometryKind.POLYGON && !it.isGenerated }

  /** Whether sample plots can be generated: there's a survey area or a polygon Map layer. */
  val canGenerateSamplePlots: Boolean
    get() = !details.surveyArea?.parts.isNullOrEmpty() || strataLayers.isNotEmpty()

  /**
   * Adds an empty sample plots Map layer with a default design and opens it. The design uses the
   * survey area if there is one, otherwise the first polygon Map layer as strata.
   */
  fun addSamplePlotsLayer(): String {
    val title = uniqueTitle("Sample plots", datasets.map { it.displayName })
    val strata = strataLayers.firstOrNull()
    val design =
      if (!details.surveyArea?.parts.isNullOrEmpty() || strata == null) {
        SampleDesignConfig()
      } else {
        SampleDesignConfig(
          method = SampleMethod.STRATIFIED_RANDOM,
          areaSource = SampleAreaSource.StrataLayer(strata.key, strata.labelProperty),
        )
      }
    val dataset =
      EntityDataset(
        key = newKey("d"),
        kind = DatasetKind.MAP_LAYER,
        id = uniqueId(slugify(title), datasets.map { it.id }),
        displayName = title,
        description = "Sample plots generated from a statistical sample design.",
        geometryKind = design.geometryKind,
        keyProperty = SamplePlotProperties.PLOT_ID,
        labelProperty = SamplePlotProperties.PLOT_ID,
        properties = SamplePlotProperties.schema,
        style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
        generator = design,
      )
    datasets = datasets + dataset
    section = SurveyEditorSection.Dataset(dataset.key)
    return dataset.key
  }

  /** Edits the sample design of generated layer [key]. */
  fun updateSampleDesign(key: String, transform: (SampleDesignConfig) -> SampleDesignConfig) {
    updateDataset(key) { d -> d.generator?.let { d.copy(generator = transform(it)) } ?: d }
  }

  /** Picks a new random seed for generated layer [key]. */
  fun rerollSeed(key: String) {
    updateSampleDesign(key) { it.copy(seed = Random.nextLong(1, 1_000_000)) }
  }

  /** The area [config] draws plots from, or why it isn't available. */
  fun samplingArea(config: SampleDesignConfig): SamplingAreaResult =
    when (val source = config.areaSource) {
      SampleAreaSource.SurveyArea -> {
        val parts = details.surveyArea?.parts.orEmpty().filter { it.size >= 3 }
        if (parts.isEmpty()) {
          SamplingAreaResult.Unavailable(
            "Set a survey area in Survey details, or use a polygon map layer as strata."
          )
        } else {
          SamplingAreaResult.Ready(
            SamplingArea.fromSurveyArea(parts.map { part -> part.map { it.toGeoCoord() } })
          )
        }
      }
      is SampleAreaSource.StrataLayer -> {
        val layer = datasets.firstOrNull { it.key == source.datasetKey }
        val polygons = layer?.rows.orEmpty().filter { it.geometry.size >= 3 }
        when {
          layer == null ->
            SamplingAreaResult.Unavailable("The strata map layer was deleted. Choose another one.")
          layer.geometryKind != GeometryKind.POLYGON ->
            SamplingAreaResult.Unavailable("Strata must come from a polygon map layer.")
          polygons.isEmpty() ->
            SamplingAreaResult.Unavailable(
              "\"${layer.displayName}\" has no polygons yet. Add polygons to use it as strata."
            )
          else ->
            SamplingAreaResult.Ready(
              SamplingArea(
                polygons.map { row ->
                  Stratum(
                    id = stratumId(layer, row, source.stratumProperty),
                    ring = row.geometry.map { it.toGeoCoord() },
                  )
                }
              )
            )
        }
      }
    }

  /** Distinct stratum IDs of [config]'s strata layer, in order of first appearance. */
  fun strataIds(config: SampleDesignConfig): List<String> {
    val source = config.areaSource as? SampleAreaSource.StrataLayer ?: return emptyList()
    val layer = datasets.firstOrNull { it.key == source.datasetKey } ?: return emptyList()
    return layer.rows
      .filter { it.geometry.size >= 3 }
      .map { stratumId(layer, it, source.stratumProperty) }
      .distinct()
  }

  private fun stratumId(layer: EntityDataset, row: EntityRow, property: String): String =
    row.values[property]?.trim()?.takeIf { it.isNotEmpty() }
      ?: row.values[layer.keyProperty]?.trim()?.takeIf { it.isNotEmpty() }
      ?: row.key

  /**
   * Live estimate (area and approximate plot count) for generated layer [key], or `null` if the
   * area isn't available or the engine can't estimate.
   */
  fun estimateSample(key: String): SampleEstimate? {
    val config = datasets.firstOrNull { it.key == key }?.generator ?: return null
    val area = (samplingArea(config) as? SamplingAreaResult.Ready)?.area ?: return null
    return try {
      engine.estimate(area, config.toSampleDesign())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      // NotImplementedError while the default engine is being built, or bad parameters.
      null
    }
  }

  private class HashCacheEntry(
    val areaRef: Any?,
    val property: String?,
    val design: SampleDesignConfig,
    val hash: String?,
  )

  /** Per-dataset cache so the hash is only recomputed when its inputs change (by identity). */
  private val hashCache = mutableMapOf<String, HashCacheEntry>()

  /**
   * Fingerprint of generated layer [dataset]'s current inputs (area and design), or `null` if the
   * area isn't available. Cached, so calling it on every recomposition is cheap.
   */
  fun currentInputHash(dataset: EntityDataset): String? {
    val config = dataset.generator ?: return null
    val design = config.withoutProvenance()
    val (areaRef, property) =
      when (val source = config.areaSource) {
        SampleAreaSource.SurveyArea -> details.surveyArea?.parts to null
        is SampleAreaSource.StrataLayer ->
          datasets.firstOrNull { it.key == source.datasetKey }?.rows to source.stratumProperty
      }
    hashCache[dataset.key]?.let { c ->
      if (c.areaRef === areaRef && c.property == property && c.design == design) return c.hash
    }
    val hash =
      (samplingArea(config) as? SamplingAreaResult.Ready)?.let {
        SampleDesignInputs.hash(it.area, config)
      }
    hashCache[dataset.key] = HashCacheEntry(areaRef, property, design, hash)
    return hash
  }

  /**
   * Whether [dataset]'s sample plots were generated from a different area or design than the
   * current one, so they should be regenerated.
   */
  fun isDesignStale(dataset: EntityDataset): Boolean {
    val lastRun = dataset.generator?.lastRun ?: return false
    return currentInputHash(dataset) != lastRun.inputHash
  }

  /** Number of submissions that reference [dataset]'s features. */
  fun submissionsReferencing(dataset: EntityDataset): Int = submissionCount(dataset.id)

  /** Why [dataset]'s sample plots can't be regenerated, or `null` if they can. */
  fun regenerateBlockedReason(dataset: EntityDataset): String? {
    if (dataset.generator?.lastRun == null) return null
    val n = submissionsReferencing(dataset)
    if (n == 0) return null
    return "Regenerating is turned off because $n ${if (n == 1) "submission references" else "submissions reference"} " +
      "these sample plots. To try a different design, add a new sample plots map layer."
  }

  /** The last generation error for dataset [key], if any. */
  fun generationError(key: String): String? = generationErrors[key]

  /**
   * Generates the sample plots of layer [datasetKey] from its design, replacing its features.
   *
   * Plots are produced in chunks; between chunks progress is published in [generation] and
   * [yieldBetweenChunks] runs. Features are only replaced once generation succeeds, so cancelling
   * ([cancelGeneration]) or a failure leaves the previous plots untouched. Failures are also
   * reported as a dataset issue until the next attempt.
   */
  suspend fun generateSample(datasetKey: String): SampleGenerationOutcome {
    if (generation != null) {
      return SampleGenerationOutcome.Failed("Sample plots are already being generated.")
    }
    val dataset =
      datasets.firstOrNull { it.key == datasetKey }
        ?: return SampleGenerationOutcome.Failed("This map layer no longer exists.")
    val config =
      dataset.generator
        ?: return SampleGenerationOutcome.Failed("This map layer doesn't have a sample design.")
    regenerateBlockedReason(dataset)?.let {
      return SampleGenerationOutcome.Blocked(it)
    }
    val area =
      when (val result = samplingArea(config)) {
        is SamplingAreaResult.Unavailable -> return fail(datasetKey, result.message)
        is SamplingAreaResult.Ready -> result.area
      }
    val design = config.toSampleDesign()
    val inputHash = SampleDesignInputs.hash(area, config)
    generationErrors = generationErrors - datasetKey
    cancelRequested = false
    val expected =
      try {
        engine.estimate(area, design).estimatedPlotCount
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        0
      }
    val plots = ArrayList<GeneratedPlot>()
    generation = SampleGenerationProgress(datasetKey, 0, expected)
    try {
      yieldBetweenChunks()
      val chunks = engine.generateChunks(area, design).iterator()
      while (true) {
        if (cancelRequested) return SampleGenerationOutcome.Cancelled
        if (!chunks.hasNext()) break
        plots.addAll(chunks.next())
        generation = SampleGenerationProgress(datasetKey, plots.size, expected)
        yieldBetweenChunks()
      }
      if (cancelRequested) return SampleGenerationOutcome.Cancelled
    } catch (e: CancellationException) {
      throw e
    } catch (e: SamplingException) {
      // The design can't be satisfied (e.g. too many plots, or they can't fit that far apart).
      val message = e.message.orEmpty().ifBlank { "This design can't be generated" }
      return fail(datasetKey, if (message.endsWith('.')) message else "$message.")
    } catch (e: Throwable) {
      return fail(
        datasetKey,
        e.message?.takeIf { it.isNotBlank() }?.let { "Couldn't generate sample plots: $it" }
          ?: "Couldn't generate sample plots. Check the design and try again.",
      )
    } finally {
      generation = null
    }
    if (plots.isEmpty()) {
      return fail(
        datasetKey,
        "No sample plots fit in the area. Try a smaller spacing or plot size, or a larger area.",
      )
    }
    updateDataset(datasetKey) { d ->
      d.copy(
        rows = SamplePlotRows.toRows(datasetKey, plots, previous = d.rows),
        geometryKind = config.geometryKind,
        generator =
          (d.generator ?: config).copy(lastRun = GenerationRecord(now(), inputHash, plots.size)),
      )
    }
    return SampleGenerationOutcome.Generated(plots.size)
  }

  /** Same as [generateSample]; reads better at call sites that replace existing plots. */
  suspend fun regenerateSample(datasetKey: String): SampleGenerationOutcome =
    generateSample(datasetKey)

  /** Stops the running generation at the next chunk boundary, keeping the previous plots. */
  fun cancelGeneration() {
    if (generation != null) cancelRequested = true
  }

  private fun fail(datasetKey: String, message: String): SampleGenerationOutcome.Failed {
    generationErrors = generationErrors + (datasetKey to message)
    return SampleGenerationOutcome.Failed(message)
  }

  // Helpers ----------------------------------------------------------------------------------

  /** Dataset kind matching [form]: a Map layer if it captures a geometry, else a Data table. */
  private fun datasetKindFor(form: EditorForm): DatasetKind =
    if (form.hasGeometry) DatasetKind.MAP_LAYER else DatasetKind.DATA_TABLE

  /**
   * Geometry kind of a Map layer fed by [form]: that of its primary geometry question (Point, Line,
   * or Polygon), defaulting to points when the form has none.
   */
  private fun geometryKindFor(form: EditorForm): GeometryKind =
    when (form.primaryGeometryQuestion?.type) {
      EditorQuestionType.LINE -> GeometryKind.LINE
      EditorQuestionType.POLYGON -> GeometryKind.POLYGON
      else -> GeometryKind.POINT
    }

  /** Dataset properties mirroring [form]'s questions (notes excluded). */
  private fun formProperties(form: EditorForm): List<EntityProperty> =
    form.questions
      .filter { it.type != EditorQuestionType.NOTE }
      .map { q ->
        EntityProperty(
          name = q.name,
          label = q.label.ifBlank { q.name },
          type =
            when (q.type) {
              EditorQuestionType.INTEGER -> PropertyType.INTEGER
              EditorQuestionType.DECIMAL -> PropertyType.DECIMAL
              EditorQuestionType.DATE -> PropertyType.DATE
              else -> PropertyType.TEXT
            },
          required = q.required,
        )
      }

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

  companion object {
    /** Default width of the Survey editor's left-hand navigation panel, in dp. */
    const val DEFAULT_SIDE_PANEL_WIDTH_DP = 280f

    /** Narrowest the Survey editor's left-hand navigation panel can be dragged, in dp. */
    const val MIN_SIDE_PANEL_WIDTH_DP = 240f

    /** Widest the Survey editor's left-hand navigation panel can be dragged, in dp. */
    const val MAX_SIDE_PANEL_WIDTH_DP = 560f
  }
}
