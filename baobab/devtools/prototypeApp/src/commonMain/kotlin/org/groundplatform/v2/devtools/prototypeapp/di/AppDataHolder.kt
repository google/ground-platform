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
package org.groundplatform.v2.devtools.prototypeapp.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.remote.MapboxPlacesDataSource
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ConnectivityRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SampleDataRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyStats
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LocationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MediaCacheInfo
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.PlaceRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SampleDataRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyContent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.TransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ClusterMapFeaturesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CompleteFormSubmissionUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateOrganizationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GeneratePrototypeRandomSitesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GenerateSamplePlotsUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteCollaboratorUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteOrganizationMemberUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.LaunchFormUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageImagerySourcesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchPlacesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.common.platformEpochMillis

/**
 * Snapshot of everything the UI reads from the local data store: all surveys, the active survey's
 * content, and device-level data. Derived from repository flows; never edited directly.
 */
data class AppData(
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val organizations: List<Organization> = emptyList(),
  val activeSurveyId: String = "",
  val content: SurveyContent = SurveyContent(),
  /** The Survey editor's draft of the active survey (stored, or derived from [content]). */
  val editorDraft: SurveyEditorDraft = SurveyEditorDraft.blank(surveyId = ""),
  val surveyStats: Map<String, SurveyStats> = emptyMap(),
  val surveyConfigs: Map<String, SurveyConfig> = emptyMap(),
  val offlineTilePackages: List<OfflineTilePackageItem> = emptyList(),
  val mutations: List<MutationLogItem> = emptyList(),
  val places: List<SurveyPlaceItem> = emptyList(),
  val userSettings: UserSettings = UserSettings(),
  val mediaCache: MediaCacheInfo = MediaCacheInfo(sizeLabel = "0 MB", fileCount = 0),
)

/**
 * Dependency container and reactive data holder for `devtools/prototypeApp` per
 * `docs/technical/client/architecture.md`:
 * - Owns the [LocalStore] (single source of truth), Domain Repository instances, Domain Use Cases,
 *   and the shared [scope].
 * - Combines repository flows into a single immutable [appData] [StateFlow].
 * - Holds no UI session state; each feature ViewModel (`OnboardingViewModel`, `SettingsViewModel`,
 *   `SurveyMapViewModel`, `DashboardViewModel`, `OrganizationViewModel`, `SurveyEditorViewModel`,
 *   `DataCollectionViewModel`, `WorkbenchViewModel`) owns its own session state and exposes its own
 *   `uiState` [StateFlow].
 *
 * Writes run in [scope]. By default it uses an immediate dispatcher, so writes to the in-memory
 * store (which never suspend) complete, and their results reach [appData], before [launch] returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDataHolder(
  val localStore: LocalStore = InMemoryLocalStore(),
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
  val connectivityRepository: ConnectivityRepository = ConnectivityRepositoryImpl(),
  val surveyRepository: SurveyRepository = SurveyRepositoryImpl(localStore),
  val mutationRepository: MutationRepository = MutationRepositoryImpl(localStore),
  val settingsRepository: SettingsRepository = SettingsRepositoryImpl(localStore),
  val placeRepository: PlaceRepository =
    PlaceRepositoryImpl(
      store = localStore,
      remoteDataSource = MapboxPlacesDataSource(isOnline = connectivityRepository::isOnline),
    ),
  val organizationRepository: OrganizationRepository = OrganizationRepositoryImpl(localStore),
  val locationRepository: LocationRepository = LocationRepositoryImpl(),
  val authRepository: AuthRepository = AuthRepositoryImpl(),
  val sampleDataRepository: SampleDataRepository = SampleDataRepositoryImpl(localStore),
  val surveyEditorRepository: SurveyEditorRepository = SurveyEditorRepositoryImpl(localStore),
  val transactionRunner: TransactionRunner = LocalStoreTransactionRunner(localStore),
  val resolveFormDefForLaunchUseCase: ResolveFormDefForLaunchUseCase =
    ResolveFormDefForLaunchUseCase(),
  val launchFormUseCase: LaunchFormUseCase = LaunchFormUseCase(resolveFormDefForLaunchUseCase),
  val completeFormSubmissionUseCase: CompleteFormSubmissionUseCase =
    CompleteFormSubmissionUseCase(
      surveyRepository = surveyRepository,
      mutationRepository = mutationRepository,
      transactionRunner = transactionRunner,
      resolveFormDefForLaunchUseCase = resolveFormDefForLaunchUseCase,
    ),
  val syncMutationsUseCase: SyncMutationsUseCase =
    SyncMutationsUseCase(
      mutationRepository = mutationRepository,
      surveyRepository = surveyRepository,
      transactionRunner = transactionRunner,
      connectivityRepository = connectivityRepository,
    ),
  val createSurveyUseCase: CreateSurveyUseCase =
    CreateSurveyUseCase(
      surveyRepository = surveyRepository,
      surveyEditorRepository = surveyEditorRepository,
      transactionRunner = transactionRunner,
    ),
  val createOrganizationUseCase: CreateOrganizationUseCase =
    CreateOrganizationUseCase(organizationRepository),
  val inviteOrganizationMemberUseCase: InviteOrganizationMemberUseCase =
    InviteOrganizationMemberUseCase(organizationRepository),
  val manageImagerySourcesUseCase: ManageImagerySourcesUseCase =
    ManageImagerySourcesUseCase(organizationRepository),
  val computeWayfindingNavigationUseCase: ComputeWayfindingNavigationUseCase =
    ComputeWayfindingNavigationUseCase(),
  val clusterMapFeaturesUseCase: ClusterMapFeaturesUseCase = ClusterMapFeaturesUseCase(),
  val searchPlacesUseCase: SearchPlacesUseCase = SearchPlacesUseCase(),
  val generateRandomSitesUseCase: GeneratePrototypeRandomSitesUseCase =
    GeneratePrototypeRandomSitesUseCase(surveyRepository = surveyRepository),
  val generateSamplePlotsUseCase: GenerateSamplePlotsUseCase =
    GenerateSamplePlotsUseCase(now = { isoUtc(platformEpochMillis()) }),
  val inviteCollaboratorUseCase: InviteCollaboratorUseCase = InviteCollaboratorUseCase(),
) {
  init {
    launch { sampleDataRepository.seedIfNeeded() }
  }

  /** Everything the UI reads from the local data store, kept current as the store changes. */
  val appData: StateFlow<AppData> =
    combine(
        surveyRepository.observeSurveys(),
        surveyRepository.observeActiveSurveyId().flatMapLatest { id ->
          combine(
            surveyRepository.observeSurveyContent(id),
            surveyEditorRepository.observeDraft(id),
          ) { content, draft ->
            Triple(id, content, draft)
          }
        },
        combine(
          surveyRepository.observeSurveyStats(),
          surveyRepository.observeSurveyConfigs(),
          surveyRepository.observeOfflineTilePackages(),
          mutationRepository.observeMutations(),
          organizationRepository.observeOrganizations(),
          ::DeviceData,
        ),
        placeRepository.observeLocalPlaces(),
        combine(
          settingsRepository.observeUserSettings(),
          settingsRepository.observeMediaCache(),
          ::Pair,
        ),
      ) {
        surveys,
        (activeId, content, draft),
        (stats, configs, tiles, mutations, organizations),
        places,
        (settings, media) ->
        AppData(
          surveys = surveys,
          organizations = organizations,
          activeSurveyId = activeId,
          content = content,
          editorDraft = draft,
          surveyStats = stats,
          surveyConfigs = configs,
          offlineTilePackages = tiles,
          mutations = mutations,
          places = places,
          userSettings = settings,
          mediaCache = media,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, AppData())

  /** Launches [block] (typically a repository write or use case) in [scope]. */
  fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}

private data class DeviceData(
  val stats: Map<String, SurveyStats>,
  val configs: Map<String, SurveyConfig>,
  val tiles: List<OfflineTilePackageItem>,
  val mutations: List<MutationLogItem>,
  val organizations: List<Organization>,
)
