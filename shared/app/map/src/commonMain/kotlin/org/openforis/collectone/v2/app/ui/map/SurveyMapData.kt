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
package org.openforis.collectone.v2.app.ui.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.domain.repository.SettingsRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository

/**
 * Everything the survey map reads from the local data store, as one snapshot. The repositories stay
 * the single source of truth; the ViewModel only holds the latest snapshot to read synchronously in
 * its actions.
 */
internal data class SurveyMapData(
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val activeSurveyId: String = "",
  val content: SurveyContent = SurveyContent(),
  val organizations: List<Organization> = emptyList(),
  val unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  val localPlaces: List<SurveyPlaceItem> = emptyList(),
  val isOnline: Boolean = true,
  val savedImagery: SavedImagery = SavedImagery(),
) {
  val anchor: SurveyMapAnchor
    get() = SurveyMapAnchor.forSurvey(activeSurveyId)

  /** The active survey, or the first one while the active ID is unknown or stale. */
  val activeSurvey: SurveyPreviewItem?
    get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.firstOrNull()

  val entities: List<GeospatialEntityItem>
    get() = content.entities

  val mapLayers: List<MapLayerItem>
    get() = content.mapLayers

  val visibleLayerIds: Set<String>
    get() = content.mapLayers.filter { it.isVisible }.map { it.id }.toSet()

  /** Submissions attached to map features, then standalone ones (field logs without a feature). */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = entities.flatMap { it.submissions } + content.standaloneSubmissions

  val submissionGeometries: List<SubmissionGeometryPolygon>
    get() = content.submissionGeometries
}

/** The READY layers of the saved map areas, by imagery type. */
internal data class SavedImagery(
  val satellite: List<SavedImageryArea> = emptyList(),
  val terrain: List<SavedImageryArea> = emptyList(),
)

/**
 * Observes the [SurveyMapData] snapshot. Survey content follows the active survey
 * (`flatMapLatest`), so switching surveys drops the previous survey's content stream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeSurveyMapData(
  surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  settingsRepository: SettingsRepository,
  placeRepository: PlaceRepository,
  connectivityRepository: ConnectivityRepository,
  offlineMapRepository: OfflineMapRepository?,
): Flow<SurveyMapData> =
  combine(
    combine(
      surveyRepository.observeSurveys(),
      surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
        surveyRepository.observeSurveyContent(id).map { id to it }
      },
      organizationRepository.observeOrganizations(),
    ) { surveys, (activeId, content), organizations ->
      Triple(surveys, activeId to content, organizations)
    },
    settingsRepository.observeUserSettings(),
    placeRepository.observeLocalPlaces(),
    connectivityRepository.observeIsOnline(),
    observeSavedImagery(offlineMapRepository),
  ) { (surveys, activePair, organizations), settings, places, isOnline, savedImagery ->
    SurveyMapData(
      surveys = surveys,
      activeSurveyId = activePair.first,
      content = activePair.second,
      organizations = organizations,
      unitSystem = settings.measurementUnits,
      localPlaces = places,
      isOnline = isOnline,
      savedImagery = savedImagery,
    )
  }

/**
 * The READY layers of the saved areas. Download progress updates the areas many times a second, so
 * only changes to the set of READY layers (an area added, removed, or finished) pass, and the map
 * isn't redrawn while an area downloads.
 */
private fun observeSavedImagery(offlineMapRepository: OfflineMapRepository?): Flow<SavedImagery> =
  offlineMapRepository
    ?.observeAreas()
    ?.map { areas ->
      SavedImagery(
        satellite = areas.readyLayers(OfflineImageryType.SATELLITE),
        terrain = areas.readyLayers(OfflineImageryType.TERRAIN),
      )
    }
    ?.distinctUntilChanged() ?: flowOf(SavedImagery())

internal fun List<OfflineMapArea>.readyLayers(type: OfflineImageryType): List<SavedImageryArea> =
  mapNotNull { area ->
    area
      .layer(type)
      ?.takeIf { it.status == OfflineAreaLayerStatus.READY }
      ?.let { SavedImageryArea(area.id, area.bounds, it.size) }
  }
