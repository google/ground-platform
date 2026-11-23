/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.app.di.AppData
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.FormSubmissionsGroup
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.domain.model.displayProperties
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsUiState
import org.openforis.collectone.v2.app.ui.datacollection.FormLauncher
import org.openforis.collectone.v2.app.ui.datacollection.PlaceListItem
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListUiState
import org.openforis.collectone.v2.app.ui.datacollection.SubmissionDetailsUiState
import org.openforis.collectone.v2.app.ui.datacollection.SurveyBottomSheetUiState
import org.openforis.collectone.v2.app.ui.listsearch.ListSearchUiState
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.map.WayfindingBadges

/**
 * The feature ViewModels' states that the survey's record surfaces (the mobile bottom sheet, the
 * searchable list, and the details cards) are built from.
 * - [viewer]: the signed-in user as a viewer of the active survey's submissions.
 * - [isOnline]: whether the client is online; the web console always is.
 * - [notice]: the session's notice, shown in the details surfaces.
 */
data class SurveyRecordsUiStates(
  val dashboard: DashboardUiState,
  val dataCollection: DataCollectionUiState,
  val map: SurveyMapUiState,
  val listSearch: ListSearchUiState,
  val viewer: SubmissionViewer,
  val isOnline: Boolean,
  val notice: String?,
)

/**
 * The session's latest [SurveyRecordsUiStates]: its [viewModels]' states, the signed-in user's
 * access to the active survey of [data], and its [notice]. [isAlwaysOnline] clients (the web
 * console) ignore the device's connectivity.
 */
fun surveyRecordsUiStatesOf(
  viewModels: AppViewModels,
  data: AppData,
  notice: String?,
  isAlwaysOnline: Boolean,
): SurveyRecordsUiStates =
  surveyRecordsUiStatesOf(
    dashboard = viewModels.dashboard.uiState.value,
    dataCollection = viewModels.dataCollection.uiState.value,
    map = viewModels.surveyMap.uiState.value,
    listSearch = viewModels.listSearch.uiState.value,
    viewer = ActiveSurveyAccess.of(data, viewModels.onboarding.uiState.value).submissionViewer,
    notice = notice,
    isAlwaysOnline = isAlwaysOnline,
  )

/** Like [surveyRecordsUiStatesOf], following the states as they change, for composition. */
@Composable
fun collectSurveyRecordsUiStates(
  viewModels: AppViewModels,
  data: StateFlow<AppData>,
  notices: NoticeController,
  isAlwaysOnline: Boolean,
): SurveyRecordsUiStates {
  val dashboard by viewModels.dashboard.uiState.collectAsState()
  val dataCollection by viewModels.dataCollection.uiState.collectAsState()
  val map by viewModels.surveyMap.uiState.collectAsState()
  val listSearch by viewModels.listSearch.uiState.collectAsState()
  val onboarding by viewModels.onboarding.uiState.collectAsState()
  val appData by data.collectAsState()
  val notice by notices.notice.collectAsState()
  return surveyRecordsUiStatesOf(
    dashboard = dashboard,
    dataCollection = dataCollection,
    map = map,
    listSearch = listSearch,
    viewer = ActiveSurveyAccess.of(appData, onboarding).submissionViewer,
    notice = notice,
    isAlwaysOnline = isAlwaysOnline,
  )
}

private fun surveyRecordsUiStatesOf(
  dashboard: DashboardUiState,
  dataCollection: DataCollectionUiState,
  map: SurveyMapUiState,
  listSearch: ListSearchUiState,
  viewer: SubmissionViewer,
  notice: String?,
  isAlwaysOnline: Boolean,
) =
  SurveyRecordsUiStates(
    dashboard = dashboard,
    dataCollection = dataCollection,
    map = map,
    listSearch = listSearch,
    viewer = viewer,
    isOnline = isAlwaysOnline || map.isOnline,
    notice = notice,
  )

/**
 * The details state of [entity] as the web dashboard ([onWeb]) or the mobile app shows it, with its
 * distance and bearing badges from [badges].
 */
fun SurveyRecordsUiStates.entityDetailsUiState(
  entity: GeospatialEntityItem,
  onWeb: Boolean,
  badges: WayfindingBadges,
): EntityDetailsUiState =
  EntityDetailsUiState(
    entity = entity,
    viewer = viewer,
    layer = dashboard.mapLayers.firstOrNull { it.id == entity.layerId },
    pane = dashboard.entityDetailsPane,
    openSubmission =
      dataCollection.selectedSubmission
        ?.takeIf { it.entityId == entity.id }
        ?.let { submissionDetailsUiState(it, badges) },
    // Seeing every submission requires a connection: the mobile app lists only the ones stored on
    // the device while offline.
    submissionGroups =
      if (onWeb) submissionGroupsOf(entity) else availableSubmissionGroupsOf(entity),
    isOnline = isOnline,
    unitSystem = map.unitSystem,
    formLaunchers =
      if (dataCollection.canCollectData) {
        dataCollection.formsForEntity(entity, onWeb).map { form ->
          FormLauncher(form = form, isEnabled = dataCollection.isFormButtonEnabled(entity, form))
        }
      } else {
        emptyList()
      },
    notice = notice,
    isNavigating = map.isNavigatingToEntity(entity.id),
    wayfinding = badges.distanceAndBearingToEntity(entity.id),
    pendingUploadCount = dashboard.pendingUploadCountForEntity(entity.id),
    relatedRecords =
      entity.displayProperties
        .mapNotNull { (_, value) ->
          dashboard.relatedEntityForPropertyValue(entity, value)?.let { value to it }
        }
        .toMap(),
    formDefs = dataCollection.formDefs,
  )

/** The details state of [submission], with the distance and bearing badges of its geometries. */
fun SurveyRecordsUiStates.submissionDetailsUiState(
  submission: SubmissionPreviewItem,
  badges: WayfindingBadges,
): SubmissionDetailsUiState =
  SubmissionDetailsUiState(
    submission = submission,
    viewer = viewer,
    fieldWayfinding =
      submission.fields
        .mapNotNull { field ->
          badges.distanceAndBearingToSubmissionField(submission.id, field)?.let { field to it }
        }
        .toMap(),
    formDefs = dataCollection.formDefs,
  )

/** What the mobile survey's bottom sheet shows: the selected record, or the searchable list. */
fun SurveyRecordsUiStates.surveyBottomSheetUiState(
  badges: WayfindingBadges
): SurveyBottomSheetUiState {
  val entity = dataCollection.selectedEntity
  val submission = dataCollection.selectedSubmission
  return when {
    entity != null ->
      SurveyBottomSheetUiState.EntityDetails(entityDetailsUiState(entity, onWeb = false, badges))
    submission != null ->
      SurveyBottomSheetUiState.SubmissionDetails(submissionDetailsUiState(submission, badges))
    else -> SurveyBottomSheetUiState.SearchableList(searchableListUiState(badges))
  }
}

/**
 * The searchable list of map features, data tables, and places. While a query is active every
 * dataset shows expanded so matches are never hidden, and any dataset with an active status filter
 * stays expanded while its filter is set; collapsed datasets come back once cleared.
 */
fun SurveyRecordsUiStates.searchableListUiState(badges: WayfindingBadges): SearchableListUiState =
  SearchableListUiState(
    surveyTitle = dataCollection.activeSurveyTitle,
    surveyLocation = map.surveyLocationLabel,
    query = listSearch.query,
    isOnline = isOnline,
    isSheetExpanded = map.isEntityBottomSheetExpanded,
    entitiesCountNoun = map.activeEntitiesCountNoun,
    hasMapLayers = dashboard.mapLayers.any { it.sourceType == LayerSourceType.ENTITY_DATASET },
    entityGroups = listSearch.entityGroups,
    activeStatusFilters = listSearch.selectedStatuses,
    collapsedDatasetIds =
      if (listSearch.query.isNotBlank()) {
        emptySet()
      } else {
        dashboard.collapsedListDatasetIds - listSearch.selectedStatuses.keys
      },
    selectedEntityId = dataCollection.selectedEntityId,
    selectedLayerDatasetId = dashboard.selectedLayerDatasetId,
    places =
      listSearch.places.map { place ->
        PlaceListItem(
          place = place,
          isSelected = map.selectedPlace?.id == place.id,
          isNavigating = map.isNavigatingToPlace(place.id),
          wayfinding = badges.distanceAndBearingToPlace(place),
        )
      },
  )

/**
 * Submissions of [entity] grouped by their form, using the form's title as the group heading (or
 * the submission's own form title when the survey no longer has the form).
 */
fun SurveyRecordsUiStates.submissionGroupsOf(
  entity: GeospatialEntityItem
): List<FormSubmissionsGroup> =
  entity.submissions
    .groupBy { it.formId }
    .map { (formId, submissions) ->
      val first = submissions.first()
      val form =
        dataCollection.forms.firstOrNull { it.id == formId }
          ?: FormPreviewItem(
            id = formId,
            title = first.formTitle,
            description = "",
            version = first.formVersion,
            targetDatasetId = entity.datasetId,
            targetDatasetName = entity.datasetName,
            questionCount = first.fields.size,
            ctaLabel = first.formTitle,
            targetTypeNoun = entity.typeNoun,
          )
      FormSubmissionsGroup(form = form, submissions = submissions)
    }

/**
 * Submissions of [entity] available to show, grouped by form. Seeing the full list requires a
 * connection: while offline only submissions stored on the device are listed.
 */
fun SurveyRecordsUiStates.availableSubmissionGroupsOf(
  entity: GeospatialEntityItem
): List<FormSubmissionsGroup> {
  val groups = submissionGroupsOf(entity)
  if (isOnline) return groups
  return groups
    .map { group ->
      group.copy(submissions = group.submissions.filter(dashboard::isSubmissionStoredOnDevice))
    }
    .filter { it.submissions.isNotEmpty() }
}
