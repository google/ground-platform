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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorIssue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDatasetValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleGenerationProgress
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyAccess
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyDetails
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toEditorDataset

/** Which pane of the Survey editor is showing. */
sealed interface SurveyEditorSection {
  data object Details : SurveyEditorSection

  data object Sharing : SurveyEditorSection

  data class Form(val key: String) : SurveyEditorSection

  data class Dataset(val key: String) : SurveyEditorSection
}

/**
 * Screen state of the Survey editor page (survey details, sharing, Forms, Map layers, and Data
 * tables), observed as an immutable snapshot.
 *
 * [draft] holds the edits; [opened] is the draft the editor started from, which unpublished edits
 * are compared against. Until the first edit both are the survey's live draft from the local data
 * store; from then on they're frozen until the edits are published or discarded.
 */
data class SurveyEditorUiState(
  // --- Data (from the local data store) ---
  /** ID of the survey being edited (the active survey). */
  val surveyId: String = "",
  /** The draft the editor opened, which unpublished edits are compared against. */
  val opened: SurveyEditorDraft = SurveyEditorDraft.blank(surveyId = ""),
  /** The draft being edited. */
  val draft: SurveyEditorDraft = opened,
  /** All organizations, for the survey's organization picker and header. */
  val organizations: List<Organization> = emptyList(),
  val signedInUserEmail: String = "",
  /** The local gazetteer, searched by the survey area picker. */
  val localPlaces: List<SurveyPlaceItem> = emptyList(),
  /** Submissions that reference each dataset's features, by dataset ID. */
  val submissionCountByDatasetId: Map<String, Int> = emptyMap(),

  // --- Session ---
  /** Whether [draft] holds edits made since [opened] (even ones that cancel out). */
  val isDirty: Boolean = false,
  /** Whether the draft is being published to the local data store. */
  val isSaving: Boolean = false,
  val section: SurveyEditorSection = SurveyEditorSection.Details,
  /**
   * Width of the editor's left-hand navigation panel, in dp; always within
   * [MIN_SIDE_PANEL_WIDTH_DP]..[MAX_SIDE_PANEL_WIDTH_DP].
   */
  val sidePanelWidthDp: Float = DEFAULT_SIDE_PANEL_WIDTH_DP,
  /** The sample generation in progress, if any. */
  val generation: SampleGenerationProgress? = null,
  /** Last generation error per dataset key, shown as a dataset issue until the next attempt. */
  val generationErrors: Map<String, String> = emptyMap(),
  /**
   * Notice about a side effect of the last organization change (the general access policy was
   * changed back to "Restricted"), or `null`.
   */
  val organizationNotice: String? = null,
) {
  val details: SurveyDetails
    get() = draft.details

  val sharing: SharingSettings
    get() = draft.sharing

  val forms: List<SurveyEditorForm>
    get() = draft.forms

  val datasets: List<EntityDataset>
    get() = draft.datasets

  /**
   * Whether the draft differs from the opened survey. The key counter is ignored, so adding and
   * then deleting an item doesn't count as a change.
   */
  val hasUnpublishedChanges: Boolean
    get() = draft.copy(nextKeyId = 0) != opened.copy(nextKeyId = 0)

  /**
   * Problems in the sharing settings (e.g. an organization-only policy without an organization).
   */
  val sharingIssues: List<String>
    get() = SurveyAccess.issues(sharing, details.organizationId)

  /** Number of validation issues across sharing and all Forms, Map layers, and Data tables. */
  val issueCount: Int
    get() =
      sharingIssues.size +
        forms.sumOf { formIssues(it).size } +
        datasets.sumOf { datasetIssues(it).size }

  /** Whether the draft can be published: it has unpublished changes and no validation issues. */
  val canPublish: Boolean
    get() = hasUnpublishedChanges && issueCount == 0 && !isSaving

  val mapLayers: List<EntityDataset>
    get() = datasets.filter { it.kind == DatasetKind.MAP_LAYER }

  val dataTables: List<EntityDataset>
    get() = datasets.filter { it.kind == DatasetKind.DATA_TABLE }

  val selectedForm: SurveyEditorForm?
    get() = (section as? SurveyEditorSection.Form)?.let { s -> form(s.key) }

  val selectedDataset: EntityDataset?
    get() = (section as? SurveyEditorSection.Dataset)?.let { s -> dataset(s.key) }

  fun form(key: String): SurveyEditorForm? = forms.firstOrNull { it.key == key }

  fun dataset(key: String): EntityDataset? = datasets.firstOrNull { it.key == key }

  /** The survey's Map layers and Data tables as seen by the save-to logic of Form [formKey]. */
  fun datasetCatalog(formKey: String): List<EditorDataset> = datasets.map {
    it.toEditorDataset(formKey)
  }

  /** Validation issues of [entry]'s Form, including its save-to logic. */
  fun formIssues(entry: SurveyEditorForm): List<EditorIssue> {
    val catalog = datasetCatalog(entry.key)
    return FormEditorValidator.validate(entry.form, catalog) +
      SaveToValidator.validate(entry.form, catalog)
  }

  /** Validation issues of [dataset], plus its last sample generation error, if any. */
  fun datasetIssues(dataset: EntityDataset): List<DatasetIssue> {
    val issues = EntityDatasetValidator.validate(dataset, datasets.map { it.id })
    val error = generationErrors[dataset.key] ?: return issues
    return issues + DatasetIssue(error)
  }

  /** Forms whose submissions update features of [dataset]. */
  fun formsUpdating(dataset: EntityDataset): List<SurveyEditorForm> = forms.filter {
    val saveTo = it.form.saveTo
    saveTo.mode == SaveToMode.UPDATE && saveTo.targetDatasetId == dataset.id
  }

  // --- Sample plots ---

  /** Polygon Map layers that can provide strata (generated sample plots can't). */
  val strataLayers: List<EntityDataset>
    get() = mapLayers.filter { it.geometryKind == GeometryKind.POLYGON && !it.isGenerated }

  /** Whether sample plots can be generated: there's a survey area or a polygon Map layer. */
  val canGenerateSamplePlots: Boolean
    get() = !details.surveyArea?.parts.isNullOrEmpty() || strataLayers.isNotEmpty()

  /** The last generation error for dataset [key], if any. */
  fun generationError(key: String): String? = generationErrors[key]

  /** Number of submissions that reference [dataset]'s features. */
  fun submissionsReferencing(dataset: EntityDataset): Int =
    submissionCountByDatasetId[dataset.id] ?: 0

  /** Why [dataset]'s sample plots can't be regenerated, or `null` if they can. */
  fun regenerateBlockedReason(dataset: EntityDataset): String? {
    if (dataset.generator?.lastRun == null) return null
    val n = submissionsReferencing(dataset)
    if (n == 0) return null
    return "Regenerating is turned off because $n ${if (n == 1) "submission references" else "submissions reference"} " +
      "these sample plots. To try a different design, add a new sample plots map layer."
  }

  // --- Organizations ---

  fun organization(organizationId: String?): Organization? = organizationId?.let { id ->
    organizations.firstOrNull { it.id == id }
  }

  /** Organizations the signed-in user is an active member of. */
  val signedInUserOrganizations: List<Organization>
    get() = organizations.filter { it.isMember(signedInUserEmail) }

  companion object {
    /** Default width of the Survey editor's left-hand navigation panel, in dp. */
    const val DEFAULT_SIDE_PANEL_WIDTH_DP = 280f

    /** Narrowest the Survey editor's left-hand navigation panel can be dragged, in dp. */
    const val MIN_SIDE_PANEL_WIDTH_DP = 240f

    /** Widest the Survey editor's left-hand navigation panel can be dragged, in dp. */
    const val MAX_SIDE_PANEL_WIDTH_DP = 560f
  }
}

/** One-off outcomes of Survey editor actions that the app shell applies outside this slice. */
sealed interface SurveyEditorEvent {
  /** The draft was published to the local data store; the shell leaves the editor. */
  data object Published : SurveyEditorEvent

  /** Unpublished edits were discarded and the editor closed; the shell leaves the editor. */
  data object Closed : SurveyEditorEvent
}
