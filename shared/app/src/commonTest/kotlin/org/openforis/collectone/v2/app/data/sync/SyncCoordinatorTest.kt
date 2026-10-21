/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.sync

import com.squareup.wire.ofEpochSecond
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.DataMutationResult
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.data.MutateDataResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/** The sync loop: drain then pull, and nothing while offline. */
class SyncCoordinatorTest {
  private val store = InMemoryLocalStore()
  private val seed = SeedUserDataService()
  private val sent = mutableListOf<MutateDataRequest>()
  private val dataService =
    object : DataServiceServer by seed {
      override suspend fun MutateData(request: MutateDataRequest) =
        MutateDataResponse(
            results =
              request.mutations.map {
                DataMutationResult(
                  entity =
                    EntityRecord(
                      entity_id = it.entity_mutation!!.entity_id,
                      survey_id = request.survey_id,
                      revision = 1,
                      commit_timestamp = ofEpochSecond(1_900_000_000L, 0L),
                    )
                )
              }
          )
          .also { sent += request }
    }
  private val client = CollectOneGrpcClient(seed, seed, seed, dataService)
  private val connectivity = ConnectivityRepository(initialIsOnline = false)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
  private val coordinator =
    SyncCoordinator(
      store = store,
      syncDataSource = GrpcSyncDataSource(client),
      drainer = OutboxDrainer(store, client),
      connectivity = connectivity,
      scope = scope,
    )

  private fun recordEdit() = runNow {
    store.transaction {
      OutboxRecorder()
        .record(
          this,
          MutationTarget.Data("s1"),
          Rebaser.entityKey("e1"),
          MutationPayload.Data(
            DataMutation(
              entity_mutation = EntityMutation(entity_id = "e1", upsert = EntityRecord(label = "A"))
            )
          ),
        )
    }
  }

  @Test
  fun offline_syncNowDoesNothing() {
    recordEdit()

    runNow { coordinator.syncNow() }

    assertTrue(sent.isEmpty())
    assertEquals(1, runNow { store.transaction { outbox() } }.size)
    scope.cancel()
  }

  @Test
  fun online_syncNowDrainsThenPulls() {
    recordEdit()
    connectivity.setOnline(true)

    runNow { coordinator.syncNow() }

    assertEquals(1, sent.size)
    assertEquals(emptyList(), runNow { store.transaction { outbox() } })
    // The pull ran: the seed's surveys are in the store.
    assertTrue(runNow { store.transaction { surveys() } }.isNotEmpty())
    assertNull(coordinator.activity.value.lastError)
    scope.cancel()
  }

  @Test
  fun openSurveyList_pollsAtTheCollaborativeInterval() {
    assertEquals(SyncCoordinator.IDLE_POLL_MS, coordinator.pollIntervalMillis())
    coordinator.setSurveyListOpen(true)
    assertEquals(SyncCoordinator.COLLABORATIVE_POLL_MS, coordinator.pollIntervalMillis())
    coordinator.setSurveyListOpen(false)
    assertEquals(SyncCoordinator.IDLE_POLL_MS, coordinator.pollIntervalMillis())
    scope.cancel()
  }
}
