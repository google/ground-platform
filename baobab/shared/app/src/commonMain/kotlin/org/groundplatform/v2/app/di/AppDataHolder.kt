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
package org.groundplatform.v2.app.di

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.client.auth.AuthClient
import org.groundplatform.v2.app.client.auth.defaultAuthClient
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.client.grpc.GrpcWebException
import org.groundplatform.v2.app.client.grpc.GrpcWebTransport
import org.groundplatform.v2.app.client.grpc.remoteUserDataGrpcClient
import org.groundplatform.v2.app.client.lifecycle.guardUnsentChangesOnUnload
import org.groundplatform.v2.app.client.lifecycle.observeAppForeground
import org.groundplatform.v2.app.client.storage.platformSyncStateFile
import org.groundplatform.v2.app.data.datasource.device.AuthDataSource
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.groundplatform.v2.app.data.datasource.remote.MapboxPlacesDataSource
import org.groundplatform.v2.app.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.app.data.repository.ConnectivityRepositoryImpl
import org.groundplatform.v2.app.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.app.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.app.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.app.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.app.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.app.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.app.data.repository.OutboxSyncRepositoryImpl
import org.groundplatform.v2.app.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.app.data.repository.SampleDataRepositoryImpl
import org.groundplatform.v2.app.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.data.sync.OutboxDrainer
import org.groundplatform.v2.app.data.sync.RecordingLocalStore
import org.groundplatform.v2.app.data.sync.SyncCoordinator
import org.groundplatform.v2.app.data.sync.SyncStatePersistence
import org.groundplatform.v2.app.data.sync.WorkingCopyRecorder
import org.groundplatform.v2.app.domain.model.MutationLogItem
import org.groundplatform.v2.app.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.SurveyConfig
import org.groundplatform.v2.app.domain.model.SurveyPlaceItem
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyStats
import org.groundplatform.v2.app.domain.model.UserSettings
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.model.editor.isoUtc
import org.groundplatform.v2.app.domain.repository.AuthRepository
import org.groundplatform.v2.app.domain.repository.ConnectivityRepository
import org.groundplatform.v2.app.domain.repository.ImpactEventRepository
import org.groundplatform.v2.app.domain.repository.LibraryRepository
import org.groundplatform.v2.app.domain.repository.LocationRepository
import org.groundplatform.v2.app.domain.repository.MediaCacheInfo
import org.groundplatform.v2.app.domain.repository.MutationRepository
import org.groundplatform.v2.app.domain.repository.OrganizationRepository
import org.groundplatform.v2.app.domain.repository.OutboxSyncRepository
import org.groundplatform.v2.app.domain.repository.PlaceRepository
import org.groundplatform.v2.app.domain.repository.SampleDataRepository
import org.groundplatform.v2.app.domain.repository.SettingsRepository
import org.groundplatform.v2.app.domain.repository.SurveyContent
import org.groundplatform.v2.app.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.app.domain.repository.SurveyRepository
import org.groundplatform.v2.app.domain.repository.TransactionRunner
import org.groundplatform.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.groundplatform.v2.app.domain.usecase.CompleteFormSubmissionUseCase
import org.groundplatform.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.groundplatform.v2.app.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.groundplatform.v2.app.domain.usecase.LaunchFormUseCase
import org.groundplatform.v2.app.domain.usecase.RecordImpactEventUseCase
import org.groundplatform.v2.app.domain.usecase.ResolveFormDefForLaunchUseCase
import org.groundplatform.v2.app.domain.usecase.ResolveLibraryUseCase
import org.groundplatform.v2.app.domain.usecase.SearchPlacesUseCase
import org.groundplatform.v2.app.domain.usecase.SyncMutationsUseCase
import org.groundplatform.v2.app.ui.common.platformEpochMillis

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
 * Dependency container and reactive data holder of the mobile app and the web console per
 * `docs/technical/client/architecture.md`:
 * - Owns the [LocalStore] (single source of truth), Domain Repository instances, the Domain Use
 *   Cases both the mobile app and the web console use, and the shared [scope]. Use cases only one
 *   client uses are wired by that client (`WebUseCases` in `shared/web`, `WorkbenchState` in the
 *   workbench) from the repositories exposed here.
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
  /**
   * Base URL of the user data service (`server/user-data-service`), e.g. `http://localhost:8080`.
   * When null, nothing is synced and the store keeps whatever it holds (empty for a new store).
   */
  val userDataServiceUrl: String? = null,
  /**
   * Signs the user in and supplies the ID tokens user data service calls carry: Firebase in
   * browsers when configured, otherwise a [org.groundplatform.v2.app.client.auth.FakeAuthClient].
   */
  val authClient: AuthClient = defaultAuthClient(),
  val grpcClient: CollectOneGrpcClient? = userDataServiceUrl?.let {
    remoteUserDataGrpcClient(
      GrpcWebTransport(it, authToken = authClient::idToken, headers = authClient::requestHeaders)
    )
  },
  val grpcSyncDataSource: GrpcSyncDataSource? = grpcClient?.let(::GrpcSyncDataSource),
  /**
   * Whether local edits are queued in the outbox and synced continuously (drain, then pull). On by
   * default when connected to a real service ([userDataServiceUrl]); tests that pass an in-memory
   * [grpcClient] keep the simulated uploads unless they turn it on.
   */
  val enableSyncEngine: Boolean = userDataServiceUrl != null,
  val connectivityRepository: ConnectivityRepository = ConnectivityRepositoryImpl(),
  /**
   * Sync engine scheduler (outbox drain, then delta pull), when connected to the user data service.
   * Started after the initial sync succeeds.
   */
  val syncCoordinator: SyncCoordinator? =
    if (enableSyncEngine && grpcClient != null && grpcSyncDataSource != null) {
      SyncCoordinator(
        store = localStore,
        syncDataSource = grpcSyncDataSource,
        drainer = OutboxDrainer(localStore, grpcClient),
        connectivity = connectivityRepository,
        scope = scope,
      )
    } else {
      null
    },
  val outboxSyncRepository: OutboxSyncRepository? = syncCoordinator?.let {
    OutboxSyncRepositoryImpl(localStore, it)
  },
  /**
   * The store repositories write user edits to. With the sync engine, a [RecordingLocalStore] that
   * records each edit as fine-grained mutations in the outbox; otherwise [localStore] itself.
   */
  val editStore: LocalStore =
    if (syncCoordinator != null) RecordingLocalStore(localStore) else localStore,
  val surveyRepository: SurveyRepository = SurveyRepositoryImpl(editStore),
  val mutationRepository: MutationRepository =
    MutationRepositoryImpl(localStore, trackOutbox = syncCoordinator != null),
  val settingsRepository: SettingsRepository = SettingsRepositoryImpl(localStore),
  val placeRepository: PlaceRepository =
    PlaceRepositoryImpl(store = localStore, remoteDataSource = MapboxPlacesDataSource()),
  val organizationRepository: OrganizationRepository = OrganizationRepositoryImpl(editStore),
  val libraryRepository: LibraryRepository = LibraryRepositoryImpl(editStore),
  val locationRepository: LocationRepository = LocationRepositoryImpl(),
  val authRepository: AuthRepository = AuthRepositoryImpl(AuthDataSource(authClient, scope)),
  val sampleDataRepository: SampleDataRepository =
    SampleDataRepositoryImpl(localStore, grpcSyncDataSource),
  val surveyEditorRepository: SurveyEditorRepository =
    SurveyEditorRepositoryImpl(
      store = editStore,
      workingCopyRecorder = syncCoordinator?.let { WorkingCopyRecorder(localStore) },
    ),
  val transactionRunner: TransactionRunner = LocalStoreTransactionRunner(editStore),
  val impactEventRepository: ImpactEventRepository = ImpactEventRepositoryImpl(localStore),
  val recordImpactEventUseCase: RecordImpactEventUseCase =
    RecordImpactEventUseCase(
      impactEventRepository = impactEventRepository,
      surveyRepository = surveyRepository,
      surveyEditorRepository = surveyEditorRepository,
      authRepository = authRepository,
      connectivityRepository = connectivityRepository,
      now = { isoUtc(platformEpochMillis()) },
    ),
  val exportSurveyDataUseCase: ExportSurveyDataUseCase =
    ExportSurveyDataUseCase(recordImpactEventUseCase),
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
      impactEventRepository = impactEventRepository,
      outboxSyncRepository = outboxSyncRepository,
    ),
  val createSurveyUseCase: CreateSurveyUseCase =
    CreateSurveyUseCase(
      surveyRepository = surveyRepository,
      surveyEditorRepository = surveyEditorRepository,
      transactionRunner = transactionRunner,
      libraryRepository = libraryRepository,
    ),
  val resolveLibraryUseCase: ResolveLibraryUseCase = ResolveLibraryUseCase(),
  val computeWayfindingNavigationUseCase: ComputeWayfindingNavigationUseCase =
    ComputeWayfindingNavigationUseCase(),
  val clusterMapFeaturesUseCase: ClusterMapFeaturesUseCase = ClusterMapFeaturesUseCase(),
  val searchPlacesUseCase: SearchPlacesUseCase = SearchPlacesUseCase(),
) {
  /**
   * Keeps the sync state on the device between launches (mobile only; see [platformSyncStateFile]).
   */
  private val syncStatePersistence: SyncStatePersistence? by lazy {
    if (syncCoordinator == null) null
    else platformSyncStateFile()?.let { SyncStatePersistence(localStore, it, scope) }
  }

  /** Disconnects the sync engine from platform lifecycle hooks, once started. */
  private var stopSyncLifecycle: (() -> Unit)? = null

  private val mutableUserDataSyncState =
    MutableStateFlow(
      if (grpcSyncDataSource == null) UserDataSyncState.NotConfigured else UserDataSyncState.Syncing
    )

  /** Progress of the initial sync from the user data service, for reporting failures. */
  val userDataSyncState: StateFlow<UserDataSyncState> = mutableUserDataSyncState.asStateFlow()

  private val mutableHasCompletedInitialSync = MutableStateFlow(grpcSyncDataSource == null)

  /**
   * Whether the initial sync has finished (either successfully or with a failure). True ensures UI
   * spinners only appear on the first load when data is empty, not during periodic background
   * refreshes.
   */
  val hasCompletedInitialSync: StateFlow<Boolean> = mutableHasCompletedInitialSync.asStateFlow()

  /**
   * Whether the sync engine is actively draining unsent local mutations from the outbox. False
   * during background polling when the outbox has no pending mutations.
   */
  val isDrainingOutbox: StateFlow<Boolean> =
    if (syncCoordinator == null) {
      MutableStateFlow(false)
    } else {
      combine(
          syncCoordinator.activity.map { it.isSyncing },
          localStore.observeOutbox().map { outbox ->
            outbox.count { it.state != OutboxState.REJECTED }
          },
        ) { isSyncing, pendingCount ->
          isSyncing && pendingCount > 0
        }
        .stateIn(scope, SharingStarted.Eagerly, false)
    }

  init {
    launch { syncFromUserDataService() }
  }

  /** Retries the initial sync after a [UserDataSyncState.Failed]. */
  fun retryUserDataSync() {
    launch { syncFromUserDataService() }
  }

  private suspend fun syncFromUserDataService() {
    if (grpcSyncDataSource == null) {
      sampleDataRepository.seedIfNeeded()
      mutableHasCompletedInitialSync.value = true
      return
    }
    mutableUserDataSyncState.value = UserDataSyncState.Syncing
    // Offline edits and the last synced data saved on the device replace the initial download;
    // the sync engine then catches up from the saved sync tokens.
    val coordinator = syncCoordinator
    if (coordinator != null && syncStatePersistence?.restore() == true) {
      syncStatePersistence?.start()
      startSyncEngine(coordinator)
      mutableUserDataSyncState.value = UserDataSyncState.Synced
      mutableHasCompletedInitialSync.value = true
      return
    }
    mutableUserDataSyncState.value =
      try {
        sampleDataRepository.seedIfNeeded()
        syncStatePersistence?.start()
        syncCoordinator?.let(::startSyncEngine)
        mutableHasCompletedInitialSync.value = true
        UserDataSyncState.Synced
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        println("Couldn't sync from the user data service at $userDataServiceUrl: $e")
        val message =
          (e as? GrpcWebException)?.description
            ?: e.message
            ?: e::class.simpleName
            ?: "Unknown error"
        mutableHasCompletedInitialSync.value = true
        UserDataSyncState.Failed(serviceUrl = userDataServiceUrl, message = message)
      }
  }

  /**
   * Starts [coordinator] and connects it to the platform: polling while the app is in the
   * foreground, and (on the web) a leave-page confirmation while the outbox has unsent changes.
   */
  private fun startSyncEngine(coordinator: SyncCoordinator) {
    coordinator.start()
    if (stopSyncLifecycle != null) return
    // Each sync may store new bases and sync tokens; save them.
    syncStatePersistence?.let { persistence ->
      coordinator.activity
        .map { it.isSyncing }
        .distinctUntilChanged()
        .onEach { syncing -> if (!syncing) persistence.saveSoon() }
        .launchIn(scope)
    }
    // Signing in or switching the simulated development role changes what the server returns.
    authClient.currentUser
      .drop(1)
      .distinctUntilChanged()
      .onEach { if (it != null) coordinator.requestSync() }
      .launchIn(scope)
    val hasUnsentChanges =
      localStore
        .observeOutbox()
        .map { outbox -> outbox.any { it.state != OutboxState.REJECTED } }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val stopForeground = observeAppForeground(coordinator::setForeground)
    val stopGuard = guardUnsentChangesOnUnload { hasUnsentChanges.value }
    stopSyncLifecycle = {
      stopForeground()
      stopGuard()
    }
  }

  /**
   * Whether a collaborative view (survey editor, live data table) is on screen; the sync engine
   * polls more often while one is.
   */
  fun setCollaborativeView(isCollaborative: Boolean) {
    syncCoordinator?.setCollaborativeView(isCollaborative)
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
