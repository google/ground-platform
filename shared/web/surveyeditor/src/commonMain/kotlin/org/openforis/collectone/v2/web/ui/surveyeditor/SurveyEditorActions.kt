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

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.ImportedForm
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.core.sampling.SampleEstimate
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeActions

/**
 * Map feature edits made by drawing on an interactive map: the subset of [SurveyEditorActions] that
 * the Map layer editor's map needs, so other editors (e.g. the survey area Draw tab) can reuse the
 * map with a scratch feature store.
 */
interface MapFeatureEditor {
  /**
   * Appends an entity and returns its row key. Map layer features use [geometry] when given (e.g.
   * vertices drawn on the map), otherwise a small default shape at [at] or near existing features.
   */
  fun addRow(key: String, at: LatLng? = null, geometry: List<LatLng>? = null): String

  /**
   * Replaces a feature's vertices. Ignored for generated sample plots, whose geometry is locked.
   */
  fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>)
}

/** Moving around the Survey editor: the selected section, the panel layout, and Form editors. */
interface SurveyNavigationActions {
  fun select(section: SurveyEditorSection)

  /**
   * Sets the editor's left-hand navigation panel width to [widthDp], clamped to
   * [SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP]..[SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP].
   * Non-finite values are ignored.
   */
  fun updateSidePanelWidth(widthDp: Float)

  /** The Form editor of Form [formKey], created on first use and kept while the Form exists. */
  fun formEditor(formKey: String): FormEditorViewModel
}

/** Publishing, discarding, and the survey's lifecycle (closing, archiving, reopening). */
interface SurveyLifecycleActions {
  /**
   * Closes, archives, or reopens the survey (people who can manage it only). Closing or archiving a
   * published survey discards unpublished edits, records that it was closed, and then offers the
   * "What happened with this data?" card ([SurveyEditorUiState.outcomeCard]); it never waits for
   * the answer. Closed and archived surveys are read-only until reopened.
   */
  fun setLifecycleState(state: SurveyLifecycleState)

  /** Publishes the draft to the local data store, then leaves the editor. */
  fun publish()

  /** Throws away unpublished edits, restoring the published survey. */
  fun discardChanges()

  /** Throws away unpublished edits and leaves the editor. */
  fun close()

  /**
   * Moves the survey to Trash (people who can manage it only, in any lifecycle state, including
   * closed and archived) and leaves the editor. Unpublished edits are discarded. The survey
   * disappears for everyone and no longer counts toward its owner's survey quota.
   */
  fun deleteSurvey()

  /**
   * Prompts the organizer to continue editing or discard a previous unsaved working copy when
   * entering the Survey editor with uncommitted changes.
   */
  fun onEditorOpened()

  /** Dismisses the unsaved working-copy prompt and continues editing the working copy. */
  fun continueWorkingCopy()
}

/** Edits to the survey details: title, languages, area, organization, and quota limits. */
interface SurveyDetailsActions {
  fun updateDetails(transform: (SurveyDetails) -> SurveyDetails)

  /** Updates fine-grained quota limits and budget constraints for this survey. */
  fun updateQuotaLimits(transform: (QuotaLimitsConfig) -> QuotaLimitsConfig)

  /**
   * Adds a language to the supported languages list if not already present. If the default language
   * is currently empty, sets it to this language as well.
   */
  fun addSupportedLanguage(code: String)

  /**
   * Removes a language from supported languages. If the removed language was the default language,
   * resets it to the first remaining supported language or empty string.
   */
  fun removeSupportedLanguage(code: String)

  /** Sets the default language. Also ensures the language is included in supported languages. */
  fun setDefaultLanguage(code: String)

  /** Sets or clears the survey area and boundaries. */
  fun setSurveyArea(area: SurveyArea?)

  /**
   * Moves the survey into [organizationId], or makes it a personal survey when `null`. Clearing the
   * organization while general access is [SharingPolicy.ORGANIZATION] falls back to
   * [SharingPolicy.RESTRICTED] and sets [SurveyEditorUiState.organizationNotice], since nobody
   * could use that policy.
   */
  fun setOrganization(organizationId: String?)

  fun dismissOrganizationNotice()

  /**
   * Looks up places matching [query] near [center] for [surveyId]; [regionSubtitle] labels results
   * without a region of their own. Results are delivered to [onResults], which may be called after
   * this returns.
   */
  fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  )
}

/** Who can open the survey: general access and the collaborators on its access list. */
interface SharingActions {
  fun updateSharing(transform: (SharingSettings) -> SharingSettings)

  /**
   * Adds (or updates the role of) a collaborator. New people get a pending invite with a fresh
   * invite link token. Returns an error message, or `null`.
   */
  fun inviteCollaborator(email: String, role: CollaboratorRole): StringResource?

  /** Invalidates the old invite link of a pending collaborator and issues a new one. */
  fun resetInviteLink(email: String)

  /**
   * Simulates the invitee opening their invite link, signing in, and accepting. Their account's
   * name and photo are cached on the ACL entry for display in the Sharing pane. Returns an error
   * message, or `null`.
   */
  fun acceptInvite(
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String = "",
  ): StringResource?

  fun setCollaboratorRole(email: String, role: CollaboratorRole)

  fun removeCollaborator(email: String)
}

/** The survey's list of Forms: adding (blank, imported, or from templates), ordering, deleting. */
interface FormCatalogActions {
  /** Adds a blank Form with a linked Map layer or Data table and opens it. */
  fun addForm()

  /**
   * Adds [imported] as a new Form, linking or creating its target dataset as appropriate, and opens
   * it. Returns the new Form's key.
   */
  fun importForm(imported: ImportedForm): String

  /**
   * Adds a copy of template [templateId] from the survey's resolved library (with a linked Map
   * layer or Data table, like [addForm]) and opens it, or the new dataset when [openDataset] (for
   * **Add map layer / data table › From template**). Later edits to the template don't change the
   * copy. Returns the new Form's key, or `null` if the template isn't available.
   */
  fun addFormFromTemplate(templateId: String, openDataset: Boolean = false): String?

  /**
   * Adds the Forms of Purpose Pack [packId]'s starter template [starterTemplateId] (like
   * [addFormFromTemplate]), adds the purpose to the survey if it isn't there yet, and adds
   * [programIds] to the survey's programs. A purpose can contribute Forms from several of its
   * templates. This is how a survey gets more than one purpose. Opens the first new Form and
   * returns its key, or `null` if the purpose or its templates aren't available.
   */
  fun addFormsForPurpose(
    packId: String,
    starterTemplateId: String,
    programIds: List<String>,
  ): String?

  fun deleteForm(key: String)

  /** Moves Form [key] to [toIndex] in the Forms list. */
  fun moveForm(key: String, toIndex: Int)
}

/** The survey's purposes and programs (`SurveyDef.purpose_ids` and `program_ids`). */
interface PurposeActions {
  /**
   * Adds or removes Purpose Pack [packId] for the survey (`SurveyDef.purpose_ids`); a survey can
   * have several. Removing a purpose keeps its Forms.
   */
  fun togglePurpose(packId: String)

  /** Updates the free-text description for the "Other" purpose option. */
  fun updateOtherPurpose(text: String)

  /** Selects or clears program [programId] for the survey (`SurveyDef.program_ids`). */
  fun toggleProgram(programId: String)
}

/** Links between Forms and the Map layers and Data tables their submissions add to or update. */
interface FormDatasetLinkActions {
  /**
   * Creates a new Map layer or Data table backed by and linked to [formKey]. [kind] defaults to a
   * Map layer if the form has a geometry question, otherwise a Data table. When [open] is true, the
   * new dataset is selected.
   */
  fun createDatasetForForm(formKey: String, kind: DatasetKind? = null, open: Boolean = true)

  /**
   * Creates a new Map layer or Data table of [kind] and automatically binds question at
   * [questionIndex] of the currently open Form to it, keeping the user in the Form editor.
   */
  fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind)

  /**
   * Creates a new Map layer or Data table of [kind] and automatically binds question at
   * [questionIndex] of Form [formKey] to it, keeping the user in the Form editor.
   */
  fun createDatasetForChoiceQuestion(formKey: String, questionIndex: Int, kind: DatasetKind)

  /**
   * Creates a new Form backed by and linked to [datasetKey]. When [open] is true, the new Form is
   * selected in the Survey editor.
   */
  fun createFormForDataset(datasetKey: String, open: Boolean = true)

  /**
   * Switches Form [formKey] between adding new features and updating existing ones.
   *
   * Switching to updates detaches the dataset the form was adding to: it's deleted if it has no
   * features yet, otherwise kept but unlinked. Switching back to adding features creates a new
   * linked dataset if none is linked.
   */
  fun setFormSaveToMode(formKey: String, mode: SaveToMode)

  /** Unlinks [datasetKey] from its linked form, making its schema directly editable. */
  fun unlinkDataset(datasetKey: String)
}

/** Map layers and Data tables: their schema, features or rows, order, and file imports. */
interface DatasetActions : MapFeatureEditor {
  fun addDataset(kind: DatasetKind)

  fun deleteDataset(key: String)

  /** Edits dataset [key]; renaming its ID is followed by Forms that update it. */
  fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset)

  /**
   * Moves Map layer or Data table [key] to [toIndex] among datasets of the same kind. Datasets of
   * the other kind keep their positions.
   */
  fun moveDataset(key: String, toIndex: Int)

  fun addProperty(key: String)

  /**
   * Updates property [index]; renaming it also renames the matching cell values in every row and
   * the property references of Forms that update the dataset.
   */
  fun updateProperty(key: String, index: Int, property: EntityProperty)

  fun removeProperty(key: String, index: Int)

  fun updateCell(key: String, rowKey: String, property: String, value: String)

  fun removeRow(key: String, rowKey: String)

  /** Creates a Map layer from [plan] and optionally opens it. Returns its key. */
  fun importMapLayer(plan: MapLayerImportPlan, selectAfterImport: Boolean = true): String

  /** Creates a Data table from [plan] and opens it. Returns its key. */
  fun importDataTable(plan: DataTableImportPlan): String

  /**
   * Reads file [fileName] with contents [text] as a Data table, with ambiguous numeric dates in
   * [dateOrder], for the import preview. Nothing changes until [importDataTable].
   */
  fun previewDataTableImport(
    fileName: String,
    text: String,
    dateOrder: DateOrder = DateOrder.DAY_FIRST,
  ): DataTableImportPreview

  /**
   * Reads GeoJSON or KML file [fileName] with contents [text] as a Map layer, for the import
   * preview. Nothing changes until [importMapLayer].
   */
  fun previewMapLayerImport(fileName: String, text: String): MapLayerImportPreview

  /**
   * Repository for lazy-loading server-backed dataset table tiles on scroll when lightweight web
   * sync is active, or `null` when all entity properties are already local.
   */
  fun tableRepository(): EntityTableRepository? = null
}

/** Generated sample plots Map layers: their sample design and the generation runs. */
interface SamplePlotActions {
  /**
   * Adds an empty sample plots Map layer with a default design and opens it. The design uses the
   * survey area if there is one, otherwise the first polygon Map layer as strata. Returns its key.
   */
  fun addSamplePlotsLayer(): String

  /** Edits the sample design of generated layer [key]. */
  fun updateSampleDesign(key: String, transform: (SampleDesignConfig) -> SampleDesignConfig)

  /** Picks a new random seed for generated layer [key]. */
  fun rerollSeed(key: String)

  /** The area [config] draws plots from, or why it isn't available. */
  fun samplingArea(config: SampleDesignConfig): SamplingAreaResult

  /** Distinct stratum IDs of [config]'s strata layer, in order of first appearance. */
  fun strataIds(config: SampleDesignConfig): List<String>

  /**
   * Live estimate (area and approximate plot count) for generated layer [key], or `null` if the
   * area isn't available or the engine can't estimate.
   */
  fun estimateSample(key: String): SampleEstimate?

  /**
   * Whether [dataset]'s sample plots were generated from a different area or design than the
   * current one, so they should be regenerated.
   */
  fun isDesignStale(dataset: EntityDataset): Boolean

  /**
   * Generates (or regenerates) the sample plots of layer [datasetKey] from its design in the
   * background; progress shows in [SurveyEditorUiState.generation].
   */
  fun generateSamplePlots(datasetKey: String)

  /**
   * Cancels the running generation, which stops at the next chunk boundary and keeps the previous
   * plots.
   */
  fun cancelGeneration()
}

/** The Forms that review the plots of a generated sample plots layer. */
interface PlotReviewActions {
  /**
   * Sets up the Forms that review the plots of generated layer [datasetKey] for [options]: a
   * web-only desk check and, with [PlotReviewOptions.fieldValidation], a mobile-only field check
   * (see [PlotReviewWorkflow.apply]).
   */
  fun updatePlotReview(datasetKey: String, options: PlotReviewOptions)

  /** Removes the Forms that review the plots of generated layer [datasetKey]. */
  fun removePlotReview(datasetKey: String)
}

/**
 * User intents of the Survey editor page, grouped into focused interfaces so each pane can depend
 * on just the ones it uses. Implemented by [SurveyEditorViewModel].
 */
interface SurveyEditorActions :
  SurveyNavigationActions,
  SurveyLifecycleActions,
  SurveyDetailsActions,
  SharingActions,
  FormCatalogActions,
  PurposeActions,
  FormDatasetLinkActions,
  DatasetActions,
  SamplePlotActions,
  PlotReviewActions,
  SurveyOutcomeActions
