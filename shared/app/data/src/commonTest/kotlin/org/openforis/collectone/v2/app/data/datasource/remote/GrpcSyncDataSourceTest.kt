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
package org.openforis.collectone.v2.app.data.datasource.remote

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListEntitiesResponse
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.sync.SyncChangesRequest
import openforis.collectone.v1.sync.SyncChangesResponse
import openforis.collectone.v1.sync.SyncServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleSyncDataSource
import org.openforis.collectone.v2.app.data.seed.SampleDataSeeder
import org.openforis.collectone.v2.app.data.sync.OutboxRecorder
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.core.geo.GeometryProtos

/**
 * Client side of `docs/technical/sync-engine.md`: [GrpcSyncDataSource] seeds a store from the user
 * data service with full syncs and catches up with `List*` calls in delta mode, storing each
 * scope's opaque sync token. The service itself is tested in `server/`.
 */
class GrpcSyncDataSourceTest {
  private val service = SeedUserDataService()
  private val sync = sampleSyncDataSource(service.client())
  private val store = InMemoryLocalStore()
  private val kenyaId = SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID

  private fun seed() = runDirect {
    sync.seedStoreFromGrpc(
      store = store,
      defaultActiveSurveyId = kenyaId,
      seedVersion = SampleDataSeeder.SEED_VERSION,
    )
  }

  private fun entityRequests(): List<ListEntitiesRequest> =
    service.listRequests.filterIsInstance<ListEntitiesRequest>().filter { it.survey_id == kenyaId }

  @Test
  fun incrementalCatchUp_appliesUpsertsAndSoftDeleteTombstones(): Unit = runDirect {
    seed()
    val initialEntityCount = store.transaction { entityCount(kenyaId) }
    val firstEntityId = store.transaction { entities(kenyaId).first().id }

    service.deleteEntity(kenyaId, firstEntityId)
    service.putEntity(
      kenyaId,
      EntityRecord(
        entity_id = "entity-nyr-added",
        dataset_id = "coffee_parcels",
        dataset_name = "Smallholder Coffee Parcels",
        layer_id = "layer-coffee-parcels",
        label = "Plot NYR-ADDED",
        geometry = GeometryProtos.point(latitude = -0.4198, longitude = 36.9512),
      ),
    )
    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)

    store.transaction {
      assertEquals(initialEntityCount, entityCount(kenyaId))
      assertNull(entity(kenyaId, firstEntityId))
      assertNotNull(entity(kenyaId, "entity-nyr-added"))
    }
  }

  @Test
  fun syncToken_isPersisted_andSentOnTheNextPoll() = runDirect {
    seed()
    val seeded = sync.currentSyncTokens().entitiesBySurvey.getValue(kenyaId)
    assertEquals(seeded, store.transaction { syncState().syncTokens.entitiesBySurvey[kenyaId] })
    assertTrue(entityRequests().all { it.sync_token.isEmpty() }, "the seed is a full sync")

    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)
    assertEquals(seeded, entityRequests().last().sync_token)
    val afterIdlePoll = store.transaction { syncState().syncTokens.entitiesBySurvey[kenyaId] }
    assertNotNull(afterIdlePoll)

    service.putEntity(
      kenyaId,
      EntityRecord(entity_id = "entity-new", dataset_id = "coffee_parcels"),
    )
    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)
    assertEquals(afterIdlePoll, entityRequests().last().sync_token)
    val advanced = store.transaction { syncState().syncTokens.entitiesBySurvey.getValue(kenyaId) }
    assertNotEquals(afterIdlePoll, advanced)

    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)
    assertEquals(advanced, entityRequests().last().sync_token)
  }

  @Test
  fun catchUp_pagesThroughDeltas_andStoresTheTokenAfterEachPage() = runDirect {
    seed()
    service.maxPageSize = 2
    val added = (1..5).map { "entity-paged-$it" }
    added.forEach { service.putEntity(kenyaId, EntityRecord(entity_id = it, dataset_id = "d")) }
    val requestsBefore = entityRequests().size

    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)

    val pageRequests = entityRequests().drop(requestsBefore)
    assertEquals(3, pageRequests.size)
    // Every page of the sequence sends the stored token; later pages add the page token.
    assertEquals(listOf(false, true, true), pageRequests.map { it.page_token.isNotEmpty() })
    store.transaction { added.forEach { assertNotNull(entity(kenyaId, it), it) } }
  }

  @Test
  fun expiredToken_fullResyncRemovesMissingRecords_andKeepsPendingMutations() = runDirect {
    seed()
    val (editedId, removedId) = store.transaction { entities(kenyaId).take(2).map { it.id } }
    store.transaction {
      OutboxRecorder()
        .record(
          this,
          MutationTarget.Data(kenyaId),
          Rebaser.entityKey(editedId),
          MutationPayload.Data(
            DataMutation(
              entity_mutation =
                EntityMutation(entity_id = editedId, upsert = EntityRecord(label = "Offline"))
            )
          ),
        )
    }
    val pending = store.transaction { outbox() }
    val oldToken = sync.currentSyncTokens().entitiesBySurvey.getValue(kenyaId)

    // The server's data is reset: the entity disappears without a tombstone and old tokens expire.
    service.removeEntity(kenyaId, removedId)
    service.invalidateSyncTokens()
    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)

    store.transaction {
      assertNull(entity(kenyaId, removedId))
      assertNull(entityBase(kenyaId, removedId))
      assertEquals("Offline", entity(kenyaId, editedId)?.label)
      assertEquals(pending, outbox())
    }
    // The client fell back to a full sync (a query without a token) and stored its new token.
    assertEquals("", entityRequests().last().sync_token)
    val newToken = sync.currentSyncTokens().entitiesBySurvey.getValue(kenyaId)
    assertNotEquals(oldToken, newToken)
    assertEquals(newToken, store.transaction { syncState().syncTokens.entitiesBySurvey[kenyaId] })

    // Polling resumes in delta mode from the new token.
    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)
    assertEquals(newToken, entityRequests().last().sync_token)
  }

  @Test
  fun deltaLibraryTombstone_removesTheLocalLibrary(): Unit = runDirect {
    seed()
    val orgId = store.transaction {
      libraries().keys.first { it != "00000000-0000-4000-8000-000000000001" }
    }

    service.deleteLibrary(orgId)
    sync.pullIncrementalCatchUp(store = store)

    store.transaction {
      assertNull(library(orgId))
      assertTrue(libraryBase(orgId)!!.is_deleted)
    }
  }

  @Test
  fun libraryMissingFromAFullResync_isRemovedLocally(): Unit = runDirect {
    seed()
    val orgId = store.transaction {
      libraries().keys.first { it != "00000000-0000-4000-8000-000000000001" }
    }

    service.removeLibrary(orgId)
    service.invalidateSyncTokens()
    sync.pullIncrementalCatchUp(store = store)

    store.transaction {
      assertNull(library(orgId))
      assertNull(libraryBase(orgId))
      assertNotNull(library("00000000-0000-4000-8000-000000000001"))
    }
  }

  @Test
  fun catchUp_sendsEveryScopeInOneSyncChangesCall() = runDirect {
    seed()
    service.syncChangesRequests.clear()

    sync.pullIncrementalCatchUp(store)

    val request = service.syncChangesRequests.single()
    val scopes = request.cursors.map { it.scope!! }
    val surveyIds = store.transaction { surveys().map { it.id } }
    assertEquals(1, scopes.count { it.organizations != null })
    assertEquals(1, scopes.count { it.libraries != null })
    assertEquals(1, scopes.count { it.survey_overviews != null })
    assertEquals(1, scopes.count { it.impact_events != null })
    assertEquals(surveyIds.toSet(), scopes.mapNotNull { it.survey_entities }.toSet())
    assertEquals(surveyIds.toSet(), scopes.mapNotNull { it.survey_submissions }.toSet())
    // Every seeded scope continues from its token (the seed doesn't fetch impact events).
    assertTrue(
      request.cursors.filter { it.scope!!.impact_events == null }.all { it.sync_token.isNotEmpty() }
    )
    assertTrue(request.include_survey_definitions)
  }

  @Test
  fun catchUpOnAnEmptyStore_usesInlinedSurveyDefinitions_andSyncsNewSurveysData() = runDirect {
    val getSurveyRequests = mutableListOf<GetSurveyRequest>()
    val countingSurveyService =
      object : SurveyServiceServer by service {
        override suspend fun GetSurvey(request: GetSurveyRequest): GetSurveyResponse {
          getSurveyRequests += request
          return service.GetSurvey(request)
        }
      }
    val client =
      CollectOneGrpcClient(
        surveyService = countingSurveyService,
        organizationService = service,
        libraryService = service,
        dataService = service,
        syncService = service,
      )

    GrpcSyncDataSource(client).pullIncrementalCatchUp(store)

    // One call for the account-wide scopes, one for the data of the surveys it added.
    assertEquals(2, service.syncChangesRequests.size)
    val surveyIds = store.transaction { surveys().map { it.id } }
    assertTrue(surveyIds.isNotEmpty())
    assertEquals(
      surveyIds.toSet(),
      service.syncChangesRequests[1].cursors.mapNotNull { it.scope!!.survey_entities }.toSet(),
    )
    store.transaction { assertTrue(entityCount(kenyaId) > 0) }
    // Saved revisions came inlined; only working copies were fetched.
    assertTrue(getSurveyRequests.all { it.working_copy != null })
  }

  @Test
  fun serviceWithoutSyncChanges_fallsBackToTheListRpcs() = runDirect {
    var syncChangesCalls = 0
    val unimplemented =
      object : SyncServiceServer {
        override suspend fun SyncChanges(request: SyncChangesRequest): SyncChangesResponse {
          syncChangesCalls++
          throw GrpcWebException(GrpcWebTransport.UNIMPLEMENTED, "/SyncChanges", null)
        }
      }
    val client =
      CollectOneGrpcClient(
        surveyService = service,
        organizationService = service,
        libraryService = service,
        dataService = service,
        syncService = unimplemented,
      )
    val fallbackSync = GrpcSyncDataSource(client)

    fallbackSync.pullIncrementalCatchUp(store)
    fallbackSync.pullIncrementalCatchUp(store)

    // Asked once, then remembered that the service doesn't implement it.
    assertEquals(1, syncChangesCalls)
    assertTrue(service.listRequests.filterIsInstance<ListEntitiesRequest>().isNotEmpty())
    store.transaction { assertTrue(entityCount(kenyaId) > 0) }
  }

  @Test
  fun seed_storesOrganizationsAndLibraries_beforeSurveyDataArrives() {
    val pendingEntityCalls = mutableListOf<Continuation<Unit>>()
    val slowDataService =
      object : DataServiceServer by service {
        override suspend fun ListEntities(request: ListEntitiesRequest): ListEntitiesResponse {
          suspendCoroutine<Unit> { pendingEntityCalls += it }
          return service.ListEntities(request)
        }
      }
    val client =
      CollectOneGrpcClient(
        surveyService = service,
        organizationService = service,
        libraryService = service,
        dataService = slowDataService,
        syncService = service,
      )
    var seeded: Result<Unit>? = null
    suspend {
        GrpcSyncDataSource(client)
          .seedStoreFromGrpc(
            store = store,
            defaultActiveSurveyId = kenyaId,
            seedVersion = SampleDataSeeder.SEED_VERSION,
          )
      }
      .startCoroutine(Continuation(EmptyCoroutineContext) { seeded = it })

    // Entities are still loading, but the wizard can already list purposes.
    assertTrue(pendingEntityCalls.isNotEmpty())
    assertNull(seeded)
    runDirect {
      store.transaction {
        assertTrue(libraries().isNotEmpty())
        assertTrue(organizations().isNotEmpty())
        assertTrue(surveys().isEmpty())
      }
    }

    while (pendingEntityCalls.isNotEmpty()) pendingEntityCalls.removeAt(0).resume(Unit)
    assertNotNull(seeded).getOrThrow()
    runDirect { store.transaction { assertTrue(entityCount(kenyaId) > 0) } }
  }
}
