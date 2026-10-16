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

import groundplatform.v2.data.EntityRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.runDirect
import org.groundplatform.v2.app.data.datasource.local.store.sampleSyncDataSource
import org.groundplatform.v2.app.data.seed.SampleDataSeeder

/**
 * Client side of `docs/technical/sync-engine.md`: [GrpcSyncDataSource] seeds a store from the user
 * data service and applies later deltas (upserts and tombstones) by server watermark. The service
 * itself is tested in `server/user-data-service`.
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

  @Test
  fun incrementalCatchUp_appliesUpsertsAndSoftDeleteTombstones() = runDirect {
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
  fun catchUp_advancesTheWatermarkOnlyWhenNewRecordsArrive() = runDirect {
    seed()
    val seeded = sync.currentWatermarks().entitiesWatermarkBySurvey.getValue(kenyaId)

    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)
    assertEquals(seeded, sync.currentWatermarks().entitiesWatermarkBySurvey[kenyaId])

    val committed =
      service.putEntity(
        kenyaId,
        EntityRecord(entity_id = "entity-new", dataset_id = "coffee_parcels"),
      )
    sync.pullIncrementalCatchUp(store = store, surveyId = kenyaId)
    assertEquals(committed, sync.currentWatermarks().entitiesWatermarkBySurvey[kenyaId])
  }
}
