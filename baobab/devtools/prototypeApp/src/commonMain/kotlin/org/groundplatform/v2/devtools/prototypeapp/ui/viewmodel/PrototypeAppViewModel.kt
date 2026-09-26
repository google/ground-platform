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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LocationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.PlaceRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ClusterMapFeaturesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CompleteFormSubmissionUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GenerateRandomSitesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchPlacesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.PrototypeUiState

/**
 * Clean Architecture MVVM ViewModel (`PrototypeAppViewModel`) for `devtools/prototypeApp` per
 * `docs/technical/client/architecture.md`:
 * - Exposes a single immutable [StateFlow] of [PrototypeUiState] (`uiState`).
 * - Calls Domain Repository interfaces ([SurveyRepository], [MutationRepository],
 *   [SettingsRepository], [PlaceRepository], [LocationRepository]) directly for simple CRUD.
 * - Delegates complex business logic, geometric computations, and multi-repository orchestration to
 *   dedicated Domain Use Cases ([CompleteFormSubmissionUseCase], [SyncMutationsUseCase],
 *   [ComputeWayfindingNavigationUseCase], [ClusterMapFeaturesUseCase], [SearchPlacesUseCase],
 *   [GenerateRandomSitesUseCase], [ResolveFormDefForLaunchUseCase]).
 */
class PrototypeAppViewModel(
  initialScreen: PrototypeScreen = PrototypeScreen.SIGN_IN,
  val dataStore: PrototypeAppDataStore = PrototypeAppDataStore(),
  val surveyRepository: SurveyRepository = SurveyRepositoryImpl(dataStore),
  val mutationRepository: MutationRepository = MutationRepositoryImpl(dataStore),
  val settingsRepository: SettingsRepository = SettingsRepositoryImpl(dataStore),
  val placeRepository: PlaceRepository = PlaceRepositoryImpl(dataStore),
  val locationRepository: LocationRepository = LocationRepositoryImpl(),
  val resolveFormDefForLaunchUseCase: ResolveFormDefForLaunchUseCase =
    ResolveFormDefForLaunchUseCase(),
  val completeFormSubmissionUseCase: CompleteFormSubmissionUseCase =
    CompleteFormSubmissionUseCase(
      surveyRepository = surveyRepository,
      mutationRepository = mutationRepository,
      resolveFormDefForLaunchUseCase = resolveFormDefForLaunchUseCase,
    ),
  val syncMutationsUseCase: SyncMutationsUseCase =
    SyncMutationsUseCase(
      mutationRepository = mutationRepository,
      surveyRepository = surveyRepository,
    ),
  val computeWayfindingNavigationUseCase: ComputeWayfindingNavigationUseCase =
    ComputeWayfindingNavigationUseCase(),
  val clusterMapFeaturesUseCase: ClusterMapFeaturesUseCase = ClusterMapFeaturesUseCase(),
  val searchPlacesUseCase: SearchPlacesUseCase = SearchPlacesUseCase(),
  val generateRandomSitesUseCase: GenerateRandomSitesUseCase =
    GenerateRandomSitesUseCase(surveyRepository = surveyRepository),
) {
  private val _uiState =
    MutableStateFlow(
      PrototypeUiState(
        currentScreen = initialScreen,
        surveys = surveyRepository.getSurveys(),
        activeSurveyId = surveyRepository.getActiveSurveyId(),
        mapLayers = surveyRepository.getMapLayers(),
        forms = surveyRepository.getForms(),
        entities = surveyRepository.getEntities(),
        standaloneSubmissions = surveyRepository.getStandaloneSubmissions(),
        submissionGeometries = surveyRepository.getSubmissionGeometries(),
        offlineTilePackages = surveyRepository.getOfflineTilePackages(),
        mutations = mutationRepository.getMutations(),
        places = placeRepository.getLocalPlaces(),
        userSettings = settingsRepository.getUserSettings(),
        uploadedMediaCacheSizeLabel = settingsRepository.getUploadedMediaCacheSizeLabel(),
        uploadedMediaFileCount = settingsRepository.getUploadedMediaFileCount(),
      )
    )

  /** Single immutable stream of UI state observed by the Presentation Layer. */
  val uiState: StateFlow<PrototypeUiState> = _uiState.asStateFlow()

  /** Atomically updates [uiState] using [transform]. */
  fun updateUiState(transform: (PrototypeUiState) -> PrototypeUiState) {
    _uiState.update(transform)
  }

  /** Synchronizes repository-backed collections into [uiState]. */
  fun syncFromRepositories(
    extraTransform: (PrototypeUiState) -> PrototypeUiState = { it }
  ) {
    val locationSnapshot = locationRepository.getLocationSnapshot()
    _uiState.update { current ->
      extraTransform(
        current.copy(
          surveys = surveyRepository.getSurveys(),
          activeSurveyId = surveyRepository.getActiveSurveyId(),
          mapLayers = surveyRepository.getMapLayers(),
          forms = surveyRepository.getForms(),
          entities = surveyRepository.getEntities(),
          standaloneSubmissions = surveyRepository.getStandaloneSubmissions(),
          submissionGeometries = surveyRepository.getSubmissionGeometries(),
          offlineTilePackages = surveyRepository.getOfflineTilePackages(),
          mutations = mutationRepository.getMutations(),
          places = placeRepository.getLocalPlaces(),
          userSettings = settingsRepository.getUserSettings(),
          uploadedMediaCacheSizeLabel = settingsRepository.getUploadedMediaCacheSizeLabel(),
          uploadedMediaFileCount = settingsRepository.getUploadedMediaFileCount(),
          userGpsNormalizedX = locationSnapshot.normalizedX,
          userGpsNormalizedY = locationSnapshot.normalizedY,
          userGpsCoordinatesLabel = locationSnapshot.coordinatesLabel,
          gnssSatelliteCount = locationSnapshot.gnssSatelliteCount,
          gnssAccuracyMeters = locationSnapshot.gnssAccuracyMeters,
        )
      )
    }
  }

  /** Completes Google Sign-In and advances `uiState.currentScreen` to Terms of Service. */
  fun signInWithGoogle() {
    _uiState.update { current ->
      current.copy(
        isSignedIn = true,
        currentScreen = PrototypeScreen.TERMS_OF_SERVICE,
      )
    }
  }

  /** Accepts Terms of Service and advances `uiState.currentScreen` to Download Survey. */
  fun acceptTermsOfService() {
    _uiState.update { current ->
      if (!current.termsCheckboxChecked) {
        current
      } else {
        current.copy(
          hasAcceptedTerms = true,
          currentScreen = PrototypeScreen.DOWNLOAD_SURVEY,
        )
      }
    }
  }

  /** Downloads and activates [surveyId] via [SurveyRepository], transitioning to Main Survey. */
  fun selectSurvey(surveyId: String) {
    surveyRepository.downloadSurvey(surveyId)
    surveyRepository.loadSurveyDatasets(surveyId)
    syncFromRepositories { current ->
      current.copy(
        currentScreen = PrototypeScreen.MAIN_SURVEY,
        selectedEntityId = null,
        selectedSubmissionId = null,
      )
    }
  }

  /** Toggles layer visibility via [SurveyRepository] and updates [uiState]. */
  fun toggleLayerVisibility(layerId: String) {
    surveyRepository.toggleLayerVisibility(layerId)
    syncFromRepositories()
  }

  /** Synchronizes a single Outbox mutation via [SyncMutationsUseCase] and updates [uiState]. */
  fun syncMutationNow(mutationId: String) {
    val notice = syncMutationsUseCase.syncSingleMutation(mutationId) ?: return
    syncFromRepositories { it.copy(activeSurveyNotice = notice) }
  }

  /** Synchronizes all Outbox mutations via [SyncMutationsUseCase] and updates [uiState]. */
  fun syncAllOutboxMutations() {
    val notice =
      syncMutationsUseCase.syncAllOutboxMutations(_uiState.value.activeSurveyId) ?: return
    syncFromRepositories { it.copy(activeSurveyNotice = notice) }
  }

  /** Generates and appends [count] random polygon sites via [GenerateRandomSitesUseCase]. */
  fun addRandomSites(count: Int = 5_000) {
    if (count <= 0) return
    val totalCount = generateRandomSitesUseCase(count)
    syncFromRepositories { current ->
      current.copy(
        currentScreen = PrototypeScreen.MAIN_SURVEY,
        activeSurveyNotice =
          "Added $count random polygon features ($totalCount total map features)",
      )
    }
  }

  /** Updates measurement unit system via [SettingsRepository] and updates [uiState]. */
  fun updateUnitSystem(
    unitSystem: org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
  ) {
    settingsRepository.updateMeasurementUnits(unitSystem)
    syncFromRepositories()
  }

  /** Updates selected language via [SettingsRepository] and updates [uiState]. */
  fun updateSelectedLanguage(languageCode: String) {
    settingsRepository.updateLanguage(languageCode)
    syncFromRepositories()
  }

  /** Resets repositories and [uiState] to defaults. */
  fun resetPrototypeFlow() {
    surveyRepository.resetToDefaults()
    locationRepository.resetToDefaults()
    _uiState.value =
      PrototypeUiState(
        currentScreen = PrototypeScreen.SIGN_IN,
        surveys = surveyRepository.getSurveys(),
        activeSurveyId = surveyRepository.getActiveSurveyId(),
        mapLayers = surveyRepository.getMapLayers(),
        forms = surveyRepository.getForms(),
        entities = surveyRepository.getEntities(),
        standaloneSubmissions = surveyRepository.getStandaloneSubmissions(),
        submissionGeometries = surveyRepository.getSubmissionGeometries(),
        offlineTilePackages = surveyRepository.getOfflineTilePackages(),
        mutations = mutationRepository.getMutations(),
        places = placeRepository.getLocalPlaces(),
        userSettings = settingsRepository.getUserSettings(),
        uploadedMediaCacheSizeLabel = settingsRepository.getUploadedMediaCacheSizeLabel(),
        uploadedMediaFileCount = settingsRepository.getUploadedMediaFileCount(),
      )
  }
}
