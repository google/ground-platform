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
package org.openforis.collectone.v2.app.data.datasource.local.store

import org.openforis.collectone.v2.app.client.offline.OfflineRegionClient
import org.openforis.collectone.v2.app.client.offline.UnavailableOfflineRegionClient
import org.openforis.collectone.v2.app.client.storage.DeviceStorageClient
import org.openforis.collectone.v2.app.client.storage.DeviceStorageSpace
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppDataHolder

/**
 * Returns an [AppDataHolder] over a [seededStore], synced from an in-memory [SeedUserDataService]
 * (so resetting to sample data restores the fixtures, as with a real service).
 *
 * The device reports [SampleDeviceStorage] whatever disk the tests run on, and can't save map areas
 * unless [offlineRegionClient] says otherwise (e.g. a `SimulatedOfflineRegionClient`).
 */
fun sampleDataHolder(
  offlineRegionClient: (isOnline: () -> Boolean) -> OfflineRegionClient = {
    UnavailableOfflineRegionClient()
  }
): AppDataHolder =
  AppDataHolder(
    localStore = seededStore(),
    grpcClient = SeedUserDataService().client(),
    offlineRegionClient = offlineRegionClient,
    deviceStorageClient = { SampleDeviceStorage },
  )

/** A 64 GB device with 12.4 GB free, as the sample data's device storage. */
val SampleDeviceStorage = DeviceStorageClient {
  DeviceStorageSpace(freeBytes = 12_400_000_000, totalBytes = 64_000_000_000)
}
