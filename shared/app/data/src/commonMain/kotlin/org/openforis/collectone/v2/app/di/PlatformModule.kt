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

import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.defaultAuthClient
import org.openforis.collectone.v2.app.client.offline.OfflineRegionClient
import org.openforis.collectone.v2.app.client.offline.defaultOfflineRegionClient
import org.openforis.collectone.v2.app.client.pdf.PdfExportClient
import org.openforis.collectone.v2.app.client.pdf.PlatformPdfExportClient
import org.openforis.collectone.v2.app.client.push.PushSubscriptionClient
import org.openforis.collectone.v2.app.client.push.defaultPushSubscriptionClient
import org.openforis.collectone.v2.app.client.storage.DeviceStorageClient
import org.openforis.collectone.v2.app.client.storage.SqlDriverFactory
import org.openforis.collectone.v2.app.client.storage.defaultDeviceStorageClient
import org.openforis.collectone.v2.app.data.datasource.local.persistence.LocalDatabaseConnection
import org.openforis.collectone.v2.app.data.datasource.local.session.SignedInSessionStore
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/**
 * Platform services of the app's dependency graph, supplied by each host: sign-in ([AuthClient]),
 * push messages ([PushSubscriptionClient]), PDF delivery ([PdfExportClient]), offline map areas
 * ([OfflineRegionClient]), the device's storage ([DeviceStorageClient]) and local database
 * ([SqlDriverFactory]), and the wall [Clock] everything that needs the current time reads, so tests
 * can control it. Each service defaults to this platform's implementation; hosts pass their own
 * (for example the auth client they already restored the session with).
 */
fun platformModule(
  authClient: () -> AuthClient = ::defaultAuthClient,
  pushSubscriptionClient: () -> PushSubscriptionClient = ::defaultPushSubscriptionClient,
  pdfExportClient: () -> PdfExportClient = ::PlatformPdfExportClient,
  /**
   * Opens the device's local database, which keeps the sync engine's state (offline edits and the
   * last synced data) between launches, through one [LocalDatabaseConnection] that is closed when
   * the graph stops. Null (the default) keeps everything in memory.
   */
  localDatabase: (() -> SqlDriverFactory)? = null,
  /**
   * Saves map tile regions for offline use, given whether the device is online (from the graph's
   * [ConnectivityRepository]): simulated in the browser, unavailable on Android and iOS until their
   * native clients land.
   */
  offlineRegionClient: (isOnline: () -> Boolean) -> OfflineRegionClient =
    ::defaultOfflineRegionClient,
  deviceStorageClient: () -> DeviceStorageClient = ::defaultDeviceStorageClient,
  /**
   * Keeps the signed-in account (and its Terms of Service acceptance) between launches, for hosts
   * without a [localDatabase] that still keep it elsewhere (the web console, in the browser's
   * storage). Null (the default) uses the [localDatabase], or else keeps it only in memory.
   */
  signedInSessionStore: (() -> SignedInSessionStore)? = null,
): Module = module {
  single { authClient() }
  single { pushSubscriptionClient() }
  single { pdfExportClient() }
  single {
    val connectivity = get<ConnectivityRepository>()
    offlineRegionClient(connectivity::isOnline)
  }
  single { deviceStorageClient() }
  single<Clock> { Clock.System }
  signedInSessionStore?.let { create -> single<SignedInSessionStore> { create() } }
  localDatabase?.let { open ->
    single { open() }
    // The stores share one connection, closed when the graph stops.
    single { LocalDatabaseConnection(get()) }.withOptions { onClose { it?.closeNow() } }
  }
}
