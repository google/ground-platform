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
package org.groundplatform.v2.app.data.datasource.local.store

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.groundplatform.v2.app.data.datasource.remote.SeedUserDataService
import org.groundplatform.v2.app.data.seed.writeSampleData
import org.groundplatform.v2.app.di.AppDataHolder

/** Runs [block] to completion on an immediate dispatcher, as the app does for store writes. */
fun <T> runNow(block: suspend () -> T): T {
  var result: Result<T>? = null
  CoroutineScope(Dispatchers.Unconfined).launch { result = runCatching { block() } }
  return checkNotNull(result) { "Block suspended; expected it to complete immediately" }
    .getOrThrow()
}

/** Returns an [InMemoryLocalStore] filled with the sample data fixtures. */
fun seededStore(): InMemoryLocalStore =
  InMemoryLocalStore().also { store -> runDirect { store.transaction { writeSampleData() } } }

/**
 * Returns an [AppDataHolder] over a [seededStore], synced from an in-memory [SeedUserDataService]
 * (so resetting to sample data restores the fixtures, as with a real service).
 */
fun sampleDataHolder(): AppDataHolder =
  AppDataHolder(localStore = seededStore(), grpcClient = SeedUserDataService().client())

/** Returns a sync data source backed by an in-memory [SeedUserDataService]. */
fun sampleSyncDataSource(
  client: CollectOneGrpcClient = SeedUserDataService().client()
): GrpcSyncDataSource = GrpcSyncDataSource(client)

/**
 * Runs [block] synchronously without a dispatcher, so it also works when called from inside
 * [runNow]. The block must not suspend.
 */
fun <T> runDirect(block: suspend () -> T): T {
  var result: Result<T>? = null
  block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
  return checkNotNull(result) { "Block suspended; expected it to complete immediately" }
    .getOrThrow()
}
