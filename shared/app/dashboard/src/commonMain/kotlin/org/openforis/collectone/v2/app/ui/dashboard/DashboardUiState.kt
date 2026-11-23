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
package org.openforis.collectone.v2.app.ui.dashboard

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.model.entityDatasetLayersIn
import org.openforis.collectone.v2.app.domain.model.relatedEntityForPropertyValue
import org.openforis.collectone.v2.app.domain.usecase.ProfileExportPlan
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

/**
 * Screen state of the web dashboard (panel layout, data tables, layer selection, entity details
 * pane), the web Surveys landing page, the searchable list's collapsed datasets, and the `Uploads`
 * drawer sub-screen (mutation log and sync), observed as an immutable snapshot.
 */
data class DashboardUiState(
  /** Export choices for each dataset of the active survey, by dataset ID. */
  val exportOptions: Map<String, DatasetExportOptions> = emptyMap(),
  /** Whether the active survey has at least one map layer with an Open in Google Earth form. */
  val hasGoogleEarthForms: Boolean = false,
  // --- Data (from the local data store) ---
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val activeSurveyId: String = "",
  val organizations: List<Organization> = emptyList(),
  val signedInUserEmail: String = "",
  /**
   * Resolved library of a new survey in each organization the signed-in user belongs to, keyed by
   * organization ID (`""` for a personal survey), for the Create survey dialog's Purpose Packs.
   */
  val surveyLibraries: Map<String, ResolvedLibrary> = emptyMap(),
  /** Map features of the active survey (every record, with or without geometry). */
  val entities: List<GeospatialEntityItem> = emptyList(),
  /** The active survey's map layers, styled with the published Survey editor draft (if any). */
  val mapLayers: List<MapLayerItem> = emptyList(),
  /** Local mutation log of the active survey, in store order. */
  val mutations: List<MutationLogItem> = emptyList(),

  // --- Web dashboard layout ---
  /**
   * Whether the left-hand panel (searchable list) is expanded; collapsing gives a full-width map.
   */
  val isSidePanelExpanded: Boolean = true,
  /** Width of the left-hand panel when expanded, in dp (kept while collapsed). */
  val sidePanelWidthDp: Float = DEFAULT_SIDE_PANEL_WIDTH_DP,
  /**
   * Whether the right-hand floating panel (details card or open form) is expanded (vs. a compact
   * pill).
   */
  val isDetailsPanelExpanded: Boolean = true,
  /** Whether the bottom data table panel is expanded. It never expands automatically. */
  val isDashboardTableExpanded: Boolean = false,
  /** Entity dataset whose table is active in the bottom panel, or `null` for the first one. */
  val dashboardTableDatasetId: String? = null,
  /**
   * Whether the dedicated "Time series & imagery" bottom sheet is open (triggered from the selected
   * map feature's details card, or replacing the table bar while in data collection mode).
   */
  val isTimeSeriesOpen: Boolean = false,
  /**
   * Whether the "Time series & imagery" bottom sheet is expanded (showing the chart) vs. collapsed
   * to its header bar.
   */
  val isTimeSeriesExpanded: Boolean = false,
  /**
   * Entity dataset ID of the map layer or data table selected in the left-hand panel, or `null`.
   * Mutually exclusive with the selected map feature and submission (owned by the map and shell).
   */
  val selectedLayerDatasetId: String? = null,
  /** Which pane of the selected entity's details surface is showing: properties or submissions. */
  val entityDetailsPane: EntityDetailsPane = EntityDetailsPane.PROPERTIES,

  // --- Searchable list ---
  /** Entity dataset IDs whose map features are collapsed under their header row in the list. */
  val collapsedListDatasetIds: Set<String> = emptySet(),

  // --- Uploads ---
  /** Active status filter chip on the `Uploads` screen, or `null` when all uploads are shown. */
  val selectedUploadStatusFilter: UploadStatusFilter? = null,
  /**
   * Entity the `Uploads` screen is filtered to (opened from a map feature's details), or `null`.
   */
  val uploadsEntityFilterId: String? = null,
  val allMutationsSorted: List<MutationLogItem> = emptyList(),
  /** Mutations shown on the `Uploads` screen after the status and entity filters, newest first. */
  val filteredUploadMutations: List<MutationLogItem> = emptyList(),
  /** Pending, in-progress, or failed mutations, newest first. */
  val outboxMutations: List<MutationLogItem> = emptyList(),
  val uploadedMutations: List<MutationLogItem> = emptyList(),
  /** IDs of map features with at least one not-yet-uploaded mutation. */
  val pendingUploadEntityIds: Set<String> = emptySet(),
  /** Activity records (e.g. PDF receipts) recorded offline and waiting to upload on sync. */
  val pendingActivityRecordCount: Int = 0,
) {
  // --- Surveys ---

  /** Whether the active survey exists in the store, so survey pages can open it. */
  val hasOpenableActiveSurvey: Boolean
    get() = surveys.any { it.id == activeSurveyId }

  /**
   * Resolved library of a new survey in [organizationId] (or a personal survey when `null`): its
   * pickable Purpose Packs are the organization's, then the global ones not hidden.
   */
  fun surveyLibrary(organizationId: String?): ResolvedLibrary =
    surveyLibraries[organizationId.orEmpty()] ?: ResolvedLibrary(organizationId = organizationId)

  /** Organizations the signed-in user is an active member of. */
  val signedInUserOrganizations: List<Organization>
    get() = organizations.filter { it.isMember(signedInUserEmail) }

  fun organization(organizationId: String?): Organization? = organizationId?.let { id ->
    organizations.firstOrNull { it.id == id }
  }

  // --- Datasets & list ---

  /** Layers backed by an entity dataset that own map features in the active survey. */
  val entityDatasetLayers: List<MapLayerItem>
    get() = entities.entityDatasetLayersIn(mapLayers)

  val visibleEntityDatasetLayers: List<MapLayerItem>
    get() = entityDatasetLayers.filter { it.isVisible }

  /** Resolves a property [value] of [entity] that references another record, if any. */
  fun relatedEntityForPropertyValue(
    entity: GeospatialEntityItem,
    value: String,
  ): GeospatialEntityItem? = entities.relatedEntityForPropertyValue(entity, value)

  // --- Uploads ---

  /** Entity the `Uploads` screen is currently filtered to, if any. */
  val uploadsEntityFilter: GeospatialEntityItem?
    get() = uploadsEntityFilterId?.let { id -> entities.firstOrNull { it.id == id } }

  val outboxMutationCount: Int
    get() = outboxMutations.size

  val uploadedMutationCount: Int
    get() = uploadedMutations.size

  /** Count of mutations matching [filter] (and the entity filter) on the `Uploads` screen. */
  fun uploadCountForFilter(filter: UploadStatusFilter): Int = mutations.count {
    it.uploadStatusFilter == filter &&
      (uploadsEntityFilterId == null || it.entityId == uploadsEntityFilterId)
  }

  /** Number of local mutations (any upload state) recorded for the entity with [entityId]. */
  fun uploadCountForEntity(entityId: String): Int = mutations.count { it.entityId == entityId }

  /** Number of not-yet-uploaded mutations recorded for the entity with [entityId]. */
  fun pendingUploadCountForEntity(entityId: String): Int = mutations.count {
    it.entityId == entityId && it.isOutbox
  }

  /**
   * True when [submission] is stored on this device, i.e. it was recorded locally and appears in
   * the local mutation log. Other collectors' submissions are only fetched when online.
   */
  fun isSubmissionStoredOnDevice(submission: SubmissionPreviewItem): Boolean = mutations.any {
    it.submissionId == submission.id
  }

  companion object {
    /** Default width of the web dashboard's left-hand panel, in dp. */
    const val DEFAULT_SIDE_PANEL_WIDTH_DP = 300f

    /** Narrowest the web dashboard's left-hand panel can be dragged, in dp. */
    const val MIN_SIDE_PANEL_WIDTH_DP = 240f

    /** Widest the web dashboard's left-hand panel can be dragged, in dp. */
    const val MAX_SIDE_PANEL_WIDTH_DP = 560f
  }
}

/** One-off outcomes of dashboard actions that the app shell applies outside this slice. */
sealed interface DashboardEvent {
  /**
   * [surveyId] became the active survey (opened or created on the web); the shell clears every
   * selection scoped to the previous survey.
   */
  data class SurveyActivated(val surveyId: String) : DashboardEvent

  /** The `Uploads` screen was requested; the shell closes the drawer and shows the sub-screen. */
  data object UploadsOpened : DashboardEvent

  data class Notice(val message: String) : DashboardEvent
}

/**
 * How a dataset of the active survey can be exported: CSV always, GeoJSON for Map layers, and the
 * export profiles its survey's purposes enable (with how their fields map and what's missing).
 */
data class DatasetExportOptions(
  val datasetId: String,
  val hasGeometry: Boolean,
  /** Whether this map layer has at least one form with **Open in Google Earth** enabled. */
  val hasGoogleEarthForms: Boolean = false,
  val profilePlans: List<ProfileExportPlan> = emptyList(),
)
