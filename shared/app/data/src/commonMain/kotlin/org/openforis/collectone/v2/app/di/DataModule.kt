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
package org.openforis.collectone.v2.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.places.MapboxOfflineAreaNameClient
import org.openforis.collectone.v2.app.client.storage.platformSyncStateFile
import org.openforis.collectone.v2.app.data.datasource.device.AuthDataSource
import org.openforis.collectone.v2.app.data.datasource.local.offline.InMemoryOfflineMapAreaStore
import org.openforis.collectone.v2.app.data.datasource.local.persistence.LocalDatabaseConnection
import org.openforis.collectone.v2.app.data.datasource.local.persistence.PersistentLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.persistence.SqlDelightActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.persistence.SqlDelightOfflineMapAreaStore
import org.openforis.collectone.v2.app.data.datasource.local.persistence.SqlDelightSignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.persistence.SqlDelightSyncStateStore
import org.openforis.collectone.v2.app.data.datasource.local.session.ActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemoryActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemorySignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.SignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.MapboxPlacesDataSource
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.ClientVersionPolicyTracker
import org.openforis.collectone.v2.app.data.repository.ClientVersionRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.ConnectivityRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.DeviceStorageRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.GrpcEntityTableRepository
import org.openforis.collectone.v2.app.data.repository.GrpcOverlayTileUrlRepository
import org.openforis.collectone.v2.app.data.repository.ImpactEventRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.InviteRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.LocationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OfflineMapRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OutboxSyncRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.ProfileImageLoaderImpl
import org.openforis.collectone.v2.app.data.repository.SampleDataRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SettingsRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyDirectoryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyMapThumbnailRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.sync.OutboxDrainer
import org.openforis.collectone.v2.app.data.sync.OutboxRecorder
import org.openforis.collectone.v2.app.data.sync.RecordingLocalStore
import org.openforis.collectone.v2.app.data.sync.SurveyTopicSubscriptionManager
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.data.sync.SyncStatePersistence
import org.openforis.collectone.v2.app.data.sync.WorkingCopyRecorder
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.ClientVersionRepository
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.DeviceStorageRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.ImpactEventRepository
import org.openforis.collectone.v2.app.domain.repository.InviteRepository
import org.openforis.collectone.v2.app.domain.repository.LibraryRepository
import org.openforis.collectone.v2.app.domain.repository.LocationRepository
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.OutboxSyncRepository
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrlRepository
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.domain.repository.ProfileImageLoader
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository
import org.openforis.collectone.v2.app.domain.repository.SettingsRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyEditorRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyMapThumbnailRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.repository.TransactionRunner

/**
 * The app-wide coroutine scope background work (sync, store observers, repository writes) runs in.
 *
 * It uses an immediate dispatcher, so writes to the in-memory store (which never suspend) complete,
 * and their results reach observers, before the call that launched them returns.
 */
val AppScope = named("AppScope")

/**
 * The store repositories write user edits to. With the sync engine, a [RecordingLocalStore] that
 * records each edit as fine-grained mutations in the outbox; otherwise the [LocalStore] itself.
 */
val EditStore = named("EditStore")

/**
 * Data layer of the app's dependency graph: the [LocalStore] (single source of truth), the user
 * data service client and sync engine, and the Domain Repository implementations. Every definition
 * is an app-wide singleton.
 *
 * Definitions that need a user data service exist only when [config] has one, so their consumers
 * resolve them with `getOrNull()`.
 */
fun dataModule(config: AppConfig): Module = module {
  storeDefinitions(config)
  userDataServiceDefinitions(config)
  repositoryDefinitions(config)
  syncControllerDefinitions(config)
}

/**
 * The app scope, the [config] itself, the [LocalStore], the [ActiveSurveyStore], and device
 * repositories.
 *
 * The [LocalStore] writes through to the device's local database when the platform module provides
 * one ([LocalDatabaseConnection]), and the [ActiveSurveyStore] keeps its survey there; otherwise
 * (the web console, tests, and the workbench) both are only in memory.
 *
 * When the graph stops (for example when the Android app switches to another user data service),
 * the app scope is cancelled, stopping sync and background writes (the platform module closes the
 * database).
 */
private fun Module.storeDefinitions(config: AppConfig) {
  single<CoroutineScope>(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
    .withOptions { onClose { it?.cancel() } }
  single { config }
  single<LocalStore> {
    getOrNull<LocalDatabaseConnection>()?.let { database ->
      PersistentLocalStore(InMemoryLocalStore(), SqlDelightSyncStateStore(database))
    } ?: InMemoryLocalStore()
  }
  // With the device's local database, the survey the user had open survives relaunches.
  single<ActiveSurveyStore> {
    getOrNull<LocalDatabaseConnection>()?.let(::SqlDelightActiveSurveyStore)
      ?: InMemoryActiveSurveyStore()
  }
  single<ConnectivityRepository> { ConnectivityRepositoryImpl() }
  single<LocationRepository> { LocationRepositoryImpl() }
  single<ProfileImageLoader> { ProfileImageLoaderImpl() }
  single<LocalStore>(EditStore) {
    if (config.runsSyncEngine) RecordingLocalStore(get(), OutboxRecorder(clock = get()))
    else get<LocalStore>()
  }
}

/** The user data service client, and the sync engine when [AppConfig.runsSyncEngine]. */
private fun Module.userDataServiceDefinitions(config: AppConfig) {
  single { ClientVersionPolicyTracker() }
  if (config.hasUserDataService) {
    single<CollectOneGrpcClient> {
      val tracker = get<ClientVersionPolicyTracker>()
      checkNotNull(
        config.createUserDataClient(
          authClient = get<AuthClient>(),
          onResponseMetadata = tracker::recordFromResponseMetadata,
        )
      )
    }
    single {
      GrpcSyncDataSource(
        get(),
        downloadedSurveysOnly = config.syncDownloadedSurveysOnly,
        trackOutbox = config.runsSyncEngine,
        lazyLoadEntityProperties = config.lazyLoadEntityProperties,
      )
    }
    single<EntityTableRepository> {
      GrpcEntityTableRepository(
        grpcClient = get(),
        store = get(),
        isLazyLoadingEnabled = config.lazyLoadEntityProperties,
      )
    }
    single<SurveyDirectoryRepository> { SurveyDirectoryRepositoryImpl(get(), get()) }
    single<OverlayTileUrlRepository> { GrpcOverlayTileUrlRepository(get()) }
    single<SurveyMapThumbnailRepository> { SurveyMapThumbnailRepositoryImpl() }
  }
  if (config.runsSyncEngine) {
    single {
      val authClient = get<AuthClient>()
      SyncCoordinator(
        store = get(),
        syncDataSource = get(),
        drainer = OutboxDrainer(get(), get(), clock = get()),
        connectivity = get(),
        scope = get(AppScope),
        clock = get(),
        // Signing out clears the store, which would otherwise trigger syncs without credentials.
        canSync = { !authClient.usesRealAccounts || authClient.currentUser.value != null },
      )
    }
    single<OutboxSyncRepository> { OutboxSyncRepositoryImpl(get(), get(), connectivity = get()) }
    if (config.syncDownloadedSurveysOnly) {
      // FCM topic subscriptions (`survey_<id>_def`, `survey_<id>_entities`) of downloaded surveys.
      single {
        SurveyTopicSubscriptionManager(
          store = get(),
          pushClient = get(),
          coordinator = get(),
          scope = get(AppScope),
        )
      }
    }
  }
}

/** The Domain Repository implementations. */
private fun Module.repositoryDefinitions(config: AppConfig) {
  single<SurveyRepository> { SurveyRepositoryImpl(get(EditStore), get()) }
  single<MutationRepository> { MutationRepositoryImpl(get(), trackOutbox = config.runsSyncEngine) }
  single<SettingsRepository> { SettingsRepositoryImpl(get()) }
  single<PlaceRepository> {
    PlaceRepositoryImpl(store = get(), remoteDataSource = MapboxPlacesDataSource())
  }
  single<OrganizationRepository> { OrganizationRepositoryImpl(get(EditStore)) }
  single<LibraryRepository> { LibraryRepositoryImpl(get(EditStore)) }
  single<AuthRepository> {
    AuthRepositoryImpl(
      dataSource = AuthDataSource(get(), get(AppScope)),
      // With the host's own store or the device's local database, the signed-in account survives
      // relaunches.
      sessionStore =
        getOrNull<SignedInSessionStore>()
          ?: getOrNull<LocalDatabaseConnection>()?.let(::SqlDelightSignedInSessionStore)
          ?: InMemorySignedInSessionStore(),
      scope = get(AppScope),
    )
  }
  single<SampleDataRepository> { SampleDataRepositoryImpl(get(), getOrNull()) }
  single<SurveyEditorRepository> {
    SurveyEditorRepositoryImpl(
      store = get(EditStore),
      workingCopyRecorder =
        if (config.runsSyncEngine) WorkingCopyRecorder(get(), OutboxRecorder(clock = get()))
        else null,
    )
  }
  single<TransactionRunner> { LocalStoreTransactionRunner(get(EditStore)) }
  single<ImpactEventRepository> { ImpactEventRepositoryImpl(get()) }
  single<InviteRepository> { InviteRepositoryImpl(get(), getOrNull(), getOrNull()) }
  single<OfflineMapRepository> {
    OfflineMapRepositoryImpl(
      // With the device's local database, saved map areas survive relaunches. Signing out keeps
      // them: the imagery belongs to the device, not the account.
      store =
        getOrNull<LocalDatabaseConnection>()?.let(::SqlDelightOfflineMapAreaStore)
          ?: InMemoryOfflineMapAreaStore(),
      regionClient = get(),
      nameClient = MapboxOfflineAreaNameClient(),
      clock = get(),
      scope = get(AppScope),
    )
  }
  single<DeviceStorageRepository> { DeviceStorageRepositoryImpl(get()) }
  single<ClientVersionRepository> {
    ClientVersionRepositoryImpl(
      tracker = get(),
      grpcClient = getOrNull(),
      scope = get(AppScope),
      clientVersion = config.clientVersion,
    )
  }
}

/** The initial sync, the backend error monitor, and the [AppDataHolder] that starts them. */
private fun Module.syncControllerDefinitions(config: AppConfig) {
  // Runs the initial sync and then the sync engine once AppDataHolder starts it.
  single {
    val localStore = get<LocalStore>()
    val scope = get<CoroutineScope>(AppScope)
    UserDataSyncController(
      serviceUrl = config.userDataServiceUrl,
      syncDataSource = getOrNull(),
      authClient = get(),
      localStore = localStore,
      sampleDataRepository = get(),
      surveyRepository = get(),
      syncEngine =
        getOrNull<SyncCoordinator>()?.let { coordinator ->
          UserDataSyncController.SyncEngine(
            coordinator = coordinator,
            topicSubscriptionManager = getOrNull(),
            // Keeps the sync state on the device between launches, when it has a local database.
            statePersistence = {
              (localStore as? PersistentLocalStore)?.let {
                SyncStatePersistence(it, legacyFile = platformSyncStateFile())
              }
            },
          )
        },
      scope = scope,
    )
  }
  single {
    BackendErrorMonitor(
      localStore = get(),
      syncCoordinator = getOrNull(),
      connectivityRepository = get(),
      initialSyncState = get<UserDataSyncController>().state,
      authClient = get(),
      scope = get(AppScope),
    )
  }
}
