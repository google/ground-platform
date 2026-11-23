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
package org.openforis.collectone.v2.app.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.defaultAuthClient
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.offline.OfflineRegionClient
import org.openforis.collectone.v2.app.client.offline.defaultOfflineRegionClient
import org.openforis.collectone.v2.app.client.pdf.PdfExportClient
import org.openforis.collectone.v2.app.client.push.PushSubscriptionClient
import org.openforis.collectone.v2.app.client.storage.DeviceStorageClient
import org.openforis.collectone.v2.app.client.storage.defaultDeviceStorageClient
import org.openforis.collectone.v2.app.data.datasource.local.session.SignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.sync.SurveyTopicSubscriptionManager
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyStats
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.ClientVersionRepository
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.ImpactEventRepository
import org.openforis.collectone.v2.app.domain.repository.InviteRepository
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.LocationRepository
import org.openforis.collectone.v2.app.domain.repository.MediaCacheInfo
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.OutboxSyncRepository
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrlRepository
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository
import org.openforis.collectone.v2.app.domain.repository.SettingsRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyContent
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.repository.TransactionRunner
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.CompleteFormSubmissionUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.EstimateDeviceStorageUseCase
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase
import org.openforis.collectone.v2.app.domain.usecase.RecordImpactEventUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveFormDefForLaunchUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.SearchPlacesUseCase
import org.openforis.collectone.v2.app.domain.usecase.SyncMutationsUseCase

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
  val mutations: List<MutationLogItem> = emptyList(),
  val places: List<SurveyPlaceItem> = emptyList(),
  val userSettings: UserSettings = UserSettings(),
  val mediaCache: MediaCacheInfo = MediaCacheInfo(sizeLabel = "0 MB", fileCount = 0),
)

/**
 * Compatibility facade over the app's Koin graph ([appModules]) for the code that still reads its
 * dependencies from one object (`MobileAppState`, `WebAppState`, `WorkbenchState`, and their
 * tests). It wires nothing itself: every member is resolved from [koin]. New code takes its
 * dependencies through its constructor instead, and the hosts get ViewModels with
 * `koinViewModel()`.
 *
 * Besides the dependencies it exposes, it combines repository flows into a single immutable
 * [appData] [StateFlow], and starts the initial sync from the user data service when the graph is
 * created.
 *
 * Writes run in [scope], which uses an immediate dispatcher, so writes to the in-memory store
 * (which never suspend) complete, and their results reach [appData], before [launch] returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDataHolder(
  /** The Koin graph this facade reads from. */
  val koin: Koin
) {
  private val config: AppConfig = koin.get()
  /** The device's local data store: the single source of truth the repositories read and write. */
  val localStore: LocalStore = koin.get()
  /**
   * The graph's app scope ([AppScope]): it lives as long as the graph, so work launched in it
   * outlives any one screen or session.
   */
  val scope: CoroutineScope = koin.get(AppScope)
  /** See [AppConfig.userDataServiceUrl]. */
  val userDataServiceUrl: String?
    get() = config.userDataServiceUrl

  /** See [AppConfig.syncDownloadedSurveysOnly]. */
  val syncDownloadedSurveysOnly: Boolean
    get() = config.syncDownloadedSurveysOnly

  /** See [AppConfig.enableSyncEngine]. */
  val enableSyncEngine: Boolean
    get() = config.enableSyncEngine

  val authClient: AuthClient = koin.get()
  val grpcClient: CollectOneGrpcClient? = koin.getOrNull()
  val grpcSyncDataSource: GrpcSyncDataSource? = koin.getOrNull()
  val connectivityRepository: ConnectivityRepository = koin.get()
  val pushSubscriptionClient: PushSubscriptionClient = koin.get()
  /**
   * Sync engine scheduler (outbox drain, then delta pull), when connected to the user data service.
   * Started after the initial sync succeeds.
   */
  val syncCoordinator: SyncCoordinator? = koin.getOrNull()
  val topicSubscriptionManager: SurveyTopicSubscriptionManager? = koin.getOrNull()
  val outboxSyncRepository: OutboxSyncRepository? = koin.getOrNull()
  /** See [EditStore]. */
  val editStore: LocalStore = koin.get(EditStore)
  val surveyRepository: SurveyRepository = koin.get()
  val mutationRepository: MutationRepository = koin.get()
  val settingsRepository: SettingsRepository = koin.get()
  val placeRepository: PlaceRepository = koin.get()
  val organizationRepository: OrganizationRepository = koin.get()
  val libraryRepository: LibraryRepository = koin.get()
  /** Finds public and link-shared surveys to download; null without a user data service. */
  val surveyDirectoryRepository: SurveyDirectoryRepository? = koin.getOrNull()
  val locationRepository: LocationRepository = koin.get()
  val authRepository: AuthRepository = koin.get()
  val clientVersionRepository: ClientVersionRepository = koin.get()
  val sampleDataRepository: SampleDataRepository = koin.get()
  val surveyEditorRepository: SurveyEditorRepository = koin.get()
  val transactionRunner: TransactionRunner = koin.get()
  val impactEventRepository: ImpactEventRepository = koin.get()
  /** Tile URLs of Earth Engine imagery overlays, when connected to the user data service. */
  val overlayTileUrlRepository: OverlayTileUrlRepository? = koin.getOrNull()
  /** Redeems survey and organization invitations with the user data service. */
  val inviteRepository: InviteRepository = koin.get()
  val recordImpactEventUseCase: RecordImpactEventUseCase = koin.get()
  val exportSurveyDataUseCase: ExportSurveyDataUseCase = koin.get()
  val resolveFormDefForLaunchUseCase: ResolveFormDefForLaunchUseCase = koin.get()
  val launchFormUseCase: LaunchFormUseCase = koin.get()
  val completeFormSubmissionUseCase: CompleteFormSubmissionUseCase = koin.get()
  val syncMutationsUseCase: SyncMutationsUseCase = koin.get()
  val createSurveyUseCase: CreateSurveyUseCase = koin.get()
  val resolveLibraryUseCase: ResolveLibraryUseCase = koin.get()
  val computeWayfindingNavigationUseCase: ComputeWayfindingNavigationUseCase = koin.get()
  val clusterMapFeaturesUseCase: ClusterMapFeaturesUseCase = koin.get()
  val searchPlacesUseCase: SearchPlacesUseCase = koin.get()
  val estimateDeviceStorageUseCase: EstimateDeviceStorageUseCase = koin.get()
  /** Delivers generated PDFs (records and receipts) on this platform. */
  val pdfExportClient: PdfExportClient = koin.get()

  /**
   * Runs the initial sync from the user data service, retries it, and starts the sync engine. Its
   * state is also exposed below for existing callers.
   */
  val userDataSync: UserDataSyncController = koin.get()

  /** See [UserDataSyncController.state]. */
  val userDataSyncState: StateFlow<UserDataSyncState>
    get() = userDataSync.state

  /** See [UserDataSyncController.hasCompletedInitialSync]. */
  val hasCompletedInitialSync: StateFlow<Boolean>
    get() = userDataSync.hasCompletedInitialSync

  /** See [UserDataSyncController.saveStatus]. */
  val saveStatus: StateFlow<SaveStatus>
    get() = userDataSync.saveStatus

  /** See [UserDataSyncController.lastSyncedAt]. */
  val lastSyncedAt: StateFlow<Instant?>
    get() = userDataSync.lastSyncedAt

  /** Surfaces backend errors to the UI; see [BackendErrorMonitor]. */
  val backendErrors: BackendErrorMonitor = koin.get()

  /** See [BackendErrorMonitor.error]. */
  val backendError: StateFlow<BackendOperationError?>
    get() = backendErrors.error

  init {
    // The initial sync syncs the restored account's data (or none when nobody is signed in).
    scope.launch {
      authRepository.restoreSession()
      userDataSync.start()
    }
  }

  /** Retries the initial sync after a [UserDataSyncState.Failed]. */
  fun retryUserDataSync() = userDataSync.retry()

  /** See [BackendErrorMonitor.dismiss]. */
  fun dismissBackendError() = backendErrors.dismiss()

  /** See [BackendErrorMonitor.retry]. */
  fun retryBackendSync() = backendErrors.retry()

  /** See [UserDataSyncController.setCollaborativeView]. */
  fun setCollaborativeView(isCollaborative: Boolean) =
    userDataSync.setCollaborativeView(isCollaborative)

  /** See [UserDataSyncController.setSurveyListOpen]. */
  fun setSurveyListOpen(isOpen: Boolean) = userDataSync.setSurveyListOpen(isOpen)

  /**
   * Everything the UI reads from the local data store, kept current as the store changes.
   *
   * Switching the active survey cancels the previous survey's content and draft subscriptions
   * (`flatMapLatest`), so a slow emission of the old survey never overwrites the new one. Shared
   * [SharingStarted.Eagerly], because the shells read `.value` outside composition and must not see
   * the empty initial [AppData] once the store has data.
   */
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
      ) { surveys, (activeId, content, draft), device, places, (settings, media) ->
        AppData(
          surveys = surveys,
          organizations = device.organizations,
          activeSurveyId = activeId,
          content = content,
          editorDraft = draft,
          surveyStats = device.stats,
          surveyConfigs = device.configs,
          mutations = device.mutations,
          places = places,
          userSettings = settings,
          mediaCache = media,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, AppData())

  /**
   * Creates a ViewModel of type [T] from the graph with an empty `SavedStateHandle` and
   * [parameters], for owners outside composition (tests and the workbench). Views use
   * `koinViewModel()` instead.
   */
  inline fun <reified T : ViewModel> newViewModel(vararg parameters: Any): T = koin.get {
    parametersOf(SavedStateHandle(), *parameters)
  }

  /** Launches [block] (typically a repository write or use case) in [scope]. */
  fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}

private data class DeviceData(
  val stats: Map<String, SurveyStats>,
  val configs: Map<String, SurveyConfig>,
  val mutations: List<MutationLogItem>,
  val organizations: List<Organization>,
)

/**
 * Creates the app's dependency graph for [config] in its own Koin application (not the global one)
 * and returns its [AppDataHolder]: for tests and hosts that don't start Koin themselves.
 * [overrides] replace definitions of the graph, for example the [LocalStore].
 */
fun createAppDataHolder(
  config: AppConfig = AppConfig(),
  platform: Module = platformModule(),
  overrides: Module = module {},
): AppDataHolder = koinApplication { modules(appModules(config, platform) + overrides) }.koin.get()

/**
 * Creates an [AppDataHolder] over [localStore], in its own Koin application. A [grpcClient] stands
 * in for the user data service at [userDataServiceUrl] (tests pass an in-memory service);
 * [offlineRegionClient] and [deviceStorageClient] replace the platform's (tests pass fixed ones).
 */
@Suppress("FunctionNaming") // Keeps the constructor-style call sites of tests and hosts.
fun AppDataHolder(
  localStore: LocalStore = InMemoryLocalStore(),
  userDataServiceUrl: String? = null,
  authClient: AuthClient = defaultAuthClient(),
  grpcClient: CollectOneGrpcClient? = null,
  syncDownloadedSurveysOnly: Boolean = false,
  enableSyncEngine: Boolean = userDataServiceUrl != null,
  offlineRegionClient: (isOnline: () -> Boolean) -> OfflineRegionClient =
    ::defaultOfflineRegionClient,
  deviceStorageClient: () -> DeviceStorageClient = ::defaultDeviceStorageClient,
  /** See the `platformModule` parameter of the same name. */
  signedInSessionStore: (() -> SignedInSessionStore)? = null,
): AppDataHolder =
  createAppDataHolder(
    config =
      AppConfig(
        userDataServiceUrl = userDataServiceUrl,
        syncDownloadedSurveysOnly = syncDownloadedSurveysOnly,
        userDataClient =
          grpcClient?.let { client -> { _ -> client } }
            ?: AppConfig(userDataServiceUrl).userDataClient,
        enableSyncEngine = enableSyncEngine,
      ),
    platform =
      platformModule(
        authClient = { authClient },
        offlineRegionClient = offlineRegionClient,
        deviceStorageClient = deviceStorageClient,
        signedInSessionStore = signedInSessionStore,
      ),
    overrides = module { single { localStore } },
  )
