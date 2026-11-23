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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataMutationResult
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.SurveyServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.push.PushSubscriptionClient
import org.openforis.collectone.v2.app.client.push.surveyDefTopic
import org.openforis.collectone.v2.app.client.push.surveyEntitiesTopic
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

  @Test
  fun pushSubscriptionsActive_suppressesIdleForegroundPollOnly() {
    coordinator.setForeground(true)
    assertTrue(coordinator.shouldPollInForeground())

    coordinator.setPushSubscriptionsActive(true)
    assertFalse(coordinator.shouldPollInForeground())

    coordinator.setSurveyListOpen(true)
    assertTrue(coordinator.shouldPollInForeground())
    coordinator.setSurveyListOpen(false)
    assertFalse(coordinator.shouldPollInForeground())

    coordinator.setCollaborativeView(true)
    assertTrue(coordinator.shouldPollInForeground())
    coordinator.setCollaborativeView(false)
    assertFalse(coordinator.shouldPollInForeground())
    scope.cancel()
  }

  @Test
  fun scopedSync_defAndEntities_onlyCallsTargetedRpcs() {
    var batchOverviewCalls = 0
    var getSurveyCalls = 0
    var listEntitiesCalls = 0
    var listSubmissionsCalls = 0

    val trackingSurveyService =
      object : SurveyServiceServer by seed {
        override suspend fun BatchGetSurveyOverviews(request: BatchGetSurveyOverviewsRequest) =
          seed.BatchGetSurveyOverviews(request).also { batchOverviewCalls++ }

        override suspend fun GetSurvey(request: GetSurveyRequest) =
          seed.GetSurvey(request).also { getSurveyCalls++ }
      }
    val trackingDataService =
      object : DataServiceServer by seed {
        override suspend fun ListEntities(request: ListEntitiesRequest) =
          seed.ListEntities(request).also { listEntitiesCalls++ }

        override suspend fun ListSubmissions(request: ListSubmissionsRequest) =
          seed.ListSubmissions(request).also { listSubmissionsCalls++ }
      }
    val trackingClient =
      CollectOneGrpcClient(trackingSurveyService, seed, seed, trackingDataService)
    val mobileDataSource = GrpcSyncDataSource(trackingClient, downloadedSurveysOnly = true)
    val mobileCoordinator =
      SyncCoordinator(
        store = store,
        syncDataSource = mobileDataSource,
        drainer = OutboxDrainer(store, trackingClient),
        connectivity = connectivity,
        scope = scope,
      )
    connectivity.setOnline(true)
    runNow {
      mobileDataSource.seedStoreFromGrpc(store, defaultActiveSurveyId = "", seedVersion = 1)
    }
    val downloadedId = runNow { store.transaction { syncState().downloadedSurveyIds.first() } }
    val storedVersion = runNow {
      store.transaction { surveyBase(downloadedId)?.let(Rebaser::recordVersionOf) ?: 1L }
    }

    batchOverviewCalls = 0
    getSurveyCalls = 0
    listEntitiesCalls = 0
    listSubmissionsCalls = 0

    // Definition tickle with current record_version: skipped without RPCs.
    runNow {
      mobileCoordinator.syncScopedNow(
        surveyId = downloadedId,
        scope = SurveySyncScope.DEFINITION,
        minRecordVersion = storedVersion,
      )
    }
    assertEquals(0, batchOverviewCalls)
    assertEquals(0, listEntitiesCalls)
    assertEquals(0, listSubmissionsCalls)

    // Definition tickle with higher record_version: calls BatchGetSurveyOverviews only (no
    // ListEntities / ListSubmissions).
    runNow {
      mobileCoordinator.syncScopedNow(
        surveyId = downloadedId,
        scope = SurveySyncScope.DEFINITION,
        minRecordVersion = storedVersion + 1,
      )
    }
    assertEquals(1, batchOverviewCalls)
    assertEquals(0, listEntitiesCalls)
    assertEquals(0, listSubmissionsCalls)

    // Entities tickle: calls ListEntities only (no BatchGetSurveyOverviews / GetSurvey /
    // ListSubmissions).
    batchOverviewCalls = 0
    runNow {
      mobileCoordinator.syncScopedNow(
        surveyId = downloadedId,
        scope = SurveySyncScope.ENTITIES,
      )
    }
    assertEquals(0, batchOverviewCalls)
    assertEquals(0, getSurveyCalls)
    assertEquals(1, listEntitiesCalls)
    assertEquals(0, listSubmissionsCalls)
    scope.cancel()
  }

  /** Survey IDs of the `ListEntities` and `ListSubmissions` calls, in order. */
  private val listEntitiesSurveyIds = mutableListOf<String>()
  private val listSubmissionsSurveyIds = mutableListOf<String>()

  private val trackingDataSource =
    GrpcSyncDataSource(
      CollectOneGrpcClient(
        seed,
        seed,
        seed,
        object : DataServiceServer by seed {
          override suspend fun ListEntities(request: ListEntitiesRequest) =
            seed.ListEntities(request).also { listEntitiesSurveyIds += request.survey_id }

          override suspend fun ListSubmissions(request: ListSubmissionsRequest) =
            seed.ListSubmissions(request).also { listSubmissionsSurveyIds += request.survey_id }
        },
      )
    )

  private val trackingCoordinator =
    SyncCoordinator(
      store = store,
      syncDataSource = trackingDataSource,
      drainer = OutboxDrainer(store, client),
      connectivity = connectivity,
      scope = scope,
    )

  private fun seedTrackingStore(): List<String> {
    runNow {
      trackingDataSource.seedStoreFromGrpc(store, defaultActiveSurveyId = "", seedVersion = 1)
    }
    listEntitiesSurveyIds.clear()
    listSubmissionsSurveyIds.clear()
    return runNow { store.transaction { surveys().map { it.id } } }
  }

  @Test
  fun comingOnlineRightAfterSeed_skipsTheRedundantPull() {
    seedTrackingStore()
    trackingCoordinator.start()

    connectivity.setOnline(true)
    trackingCoordinator.setForeground(true)

    assertEquals(emptyList(), listEntitiesSurveyIds)
    assertEquals(emptyList(), listSubmissionsSurveyIds)
    trackingCoordinator.stop()
    scope.cancel()
  }

  @Test
  fun syncNow_pullsEvenRightAfterSeed() {
    val surveyIds = seedTrackingStore()
    connectivity.setOnline(true)

    runNow { trackingCoordinator.syncNow() }

    assertEquals(surveyIds.toSet(), listEntitiesSurveyIds.toSet())
    assertEquals(surveyIds.toSet(), listSubmissionsSurveyIds.toSet())
    scope.cancel()
  }

  @Test
  fun focusingSurveyInCollaborativeView_pullsOnlyThatSurveysData() {
    val surveyIds = seedTrackingStore()
    assertTrue(surveyIds.size > 1)
    val focused = surveyIds.last()
    trackingCoordinator.start()
    connectivity.setOnline(true)
    trackingCoordinator.setCollaborativeView(true)

    trackingCoordinator.setFocusedSurvey(focused)

    assertEquals(listOf(focused), listEntitiesSurveyIds)
    assertEquals(listOf(focused), listSubmissionsSurveyIds)

    // Focusing the same survey again doesn't pull again.
    trackingCoordinator.setFocusedSurvey(focused)
    assertEquals(listOf(focused), listEntitiesSurveyIds)
    trackingCoordinator.stop()
    scope.cancel()
  }

  @Test
  fun focusingSurveyOutsideCollaborativeView_doesNotPull() {
    val surveyIds = seedTrackingStore()
    trackingCoordinator.start()
    connectivity.setOnline(true)

    trackingCoordinator.setFocusedSurvey(surveyIds.first())

    assertEquals(emptyList(), listEntitiesSurveyIds)
    trackingCoordinator.stop()
    scope.cancel()
  }

  @Test
  fun topicSubscriptionManager_subscribesAndUnsubscribesDownloadedSurveys() {
    val subscribedTopics = mutableSetOf<String>()
    val fakePushClient =
      object : PushSubscriptionClient {
        override val isAvailable: Boolean = true

        override suspend fun subscribe(topic: String) {
          subscribedTopics += topic
        }

        override suspend fun unsubscribe(topic: String) {
          subscribedTopics -= topic
        }
      }
    val mobileDataSource = GrpcSyncDataSource(client, downloadedSurveysOnly = true)
    runNow {
      mobileDataSource.seedStoreFromGrpc(store, defaultActiveSurveyId = "", seedVersion = 1)
    }
    val downloadedIds = runNow { store.transaction { syncState().downloadedSurveyIds } }
    assertTrue(downloadedIds.isNotEmpty())

    val manager =
      SurveyTopicSubscriptionManager(
        store = store,
        pushClient = fakePushClient,
        coordinator = coordinator,
        scope = scope,
      )
    manager.start()

    for (sid in downloadedIds) {
      assertTrue(surveyDefTopic(sid) in subscribedTopics)
      assertTrue(surveyEntitiesTopic(sid) in subscribedTopics)
    }

    val removedId = downloadedIds.first()
    runNow {
      store.transaction { updateSurvey(removedId) { it.copy(isDownloaded = false) } }
    }
    assertFalse(surveyDefTopic(removedId) in subscribedTopics)
    assertFalse(surveyEntitiesTopic(removedId) in subscribedTopics)
    manager.stop()
    scope.cancel()
  }
}
