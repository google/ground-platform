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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityDetailsPane
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UploadStatusFilter
import org.groundplatform.v2.devtools.prototypeapp.domain.model.entityDatasetLayersIn
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ProfileExportPlan
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary

/**
 * Screen state of the web dashboard (panel layout, data tables, layer selection, entity details
 * pane), the web Surveys landing page, the searchable list's filter tabs, and the `Uploads` drawer
 * sub-screen (mutation log and sync), observed as an immutable snapshot.
 */
data class DashboardUiState(
  /** Export choices for each dataset of the active survey, by dataset ID. */
  val exportOptions: Map<String, DatasetExportOptions> = emptyMap(),
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
  /** Whether the right-hand floating details card is expanded (vs. a compact pill). */
  val isDetailsPanelExpanded: Boolean = true,
  /** Whether the bottom data table panel is expanded. It never expands automatically. */
  val isDashboardTableExpanded: Boolean = false,
  /** Entity dataset whose table is active in the bottom panel, or `null` for the first one. */
  val dashboardTableDatasetId: String? = null,
  /**
   * Entity dataset ID of the map layer or data table selected in the left-hand panel, or `null`.
   * Mutually exclusive with the selected map feature and submission (owned by the map and shell).
   */
  val selectedLayerDatasetId: String? = null,
  /** Which pane of the selected entity's details surface is showing: properties or submissions. */
  val entityDetailsPane: EntityDetailsPane = EntityDetailsPane.PROPERTIES,

  // --- Searchable list ---
  val listSearchQuery: String = "",
  val listFilterTab: ListFilterTab = ListFilterTab.ALL,
  /** Entity dataset IDs whose map features are collapsed under their header row in the list. */
  val collapsedListDatasetIds: Set<String> = emptySet(),

  // --- Uploads ---
  /** Active status filter chip on the `Uploads` screen, or `null` when all uploads are shown. */
  val selectedUploadStatusFilter: UploadStatusFilter? = null,
  /**
   * Entity the `Uploads` screen is filtered to (opened from a map feature's details), or `null`.
   */
  val uploadsEntityFilterId: String? = null,
  /** All mutations, newest first. */
  val allMutationsSorted: List<MutationLogItem> = emptyList(),
  /** Mutations shown on the `Uploads` screen after the status and entity filters, newest first. */
  val filteredUploadMutations: List<MutationLogItem> = emptyList(),
  /** Pending, in-progress, or failed mutations, newest first. */
  val outboxMutations: List<MutationLogItem> = emptyList(),
  /** Completed mutations, newest first. */
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

  /** Currently visible entity dataset layers. */
  val visibleEntityDatasetLayers: List<MapLayerItem>
    get() = entityDatasetLayers.filter { it.isVisible }

  /**
   * Plural category label for the `Map features` tab and list section header: the single visible
   * entity dataset layer's plural domain label (e.g. `"Coffee Parcels"`), or `"Map features"` when
   * several (or none) are visible.
   */
  val activeEntitiesTabLabel: String
    get() =
      visibleEntityDatasetLayers.singleOrNull()?.pluralDomainLabel ?: ListFilterTab.ENTITIES.label

  /** Display label of a [ListFilterTab] chip. */
  fun tabLabelFor(tab: ListFilterTab): String =
    when (tab) {
      ListFilterTab.ENTITIES -> activeEntitiesTabLabel
      else -> tab.label
    }

  /**
   * Whether the map features of the entity dataset with [datasetId] are hidden in the list. While a
   * search query is active every dataset shows expanded so matches are never hidden; the stored
   * collapsed state is restored once the query is cleared.
   */
  fun isListDatasetCollapsed(datasetId: String): Boolean =
    listSearchQuery.isBlank() && datasetId in collapsedListDatasetIds

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
  val profilePlans: List<ProfileExportPlan> = emptyList(),
)
