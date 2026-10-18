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
package org.groundplatform.v2.app.data.datasource.remote

import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityFieldPatch
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ListEntitiesRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.runDirect
import org.groundplatform.v2.app.data.datasource.local.store.sampleSyncDataSource
import org.groundplatform.v2.app.data.seed.SampleDataSeeder
import org.groundplatform.v2.app.data.sync.OutboxRecorder
import org.groundplatform.v2.app.data.sync.Rebaser

/**
 * Client side of `docs/technical/sync-engine.md`: [GrpcSyncDataSource] seeds a store from the user
 * data service with full syncs and catches up with `List*` calls in delta mode, storing each
 * scope's opaque sync token. The service itself is tested in `server/user-data-service`.
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
        geometry_type_label = "Polygon",
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
                EntityMutation(
                  entity_id = editedId,
                  update =
                    EntityPatch(
                      patches = listOf(EntityFieldPatch(key = "label", label_value = "Offline"))
                    ),
                )
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
}
