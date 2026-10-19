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
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.library.ListLibrariesRequest
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.runDirect
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.data.seed.SampleDataSeeder
import org.groundplatform.v2.app.data.sync.ClientSyncStateCodec
import org.groundplatform.v2.app.data.sync.OutboxRecorder
import org.groundplatform.v2.app.data.sync.Rebaser

/**
 * Mobile sync of explicitly downloaded surveys (`docs/technical/sync-engine.md`, "Survey Downloads
 * & Working Copies"): [GrpcSyncDataSource] with `downloadedSurveysOnly` keeps only the surveys in
 * the device's downloaded set offline and lists the others from their overviews.
 */
class DownloadedSurveySyncTest {
  private val service = SeedUserDataService()
  private val sync = GrpcSyncDataSource(service.client(), downloadedSurveysOnly = true)
  private val store = InMemoryLocalStore()
  private val surveys = SurveyRepositoryImpl(store)
  private val kenyaId = SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID

  private fun seed() = runDirect {
    sync.seedStoreFromGrpc(
      store = store,
      defaultActiveSurveyId = kenyaId,
      seedVersion = SampleDataSeeder.SEED_VERSION,
    )
  }

  private fun pull() = runDirect { sync.pullIncrementalCatchUp(store) }

  private fun downloaded(): Set<String> = runDirect {
    store.transaction { syncState().downloadedSurveyIds }
  }

  private fun notDownloadedId(): String = runDirect {
    store.transaction { surveys().first { !it.isDownloaded }.id }
  }

  private fun listRequests(): List<ListSurveyOverviewsRequest> =
    service.listRequests.filterIsInstance<ListSurveyOverviewsRequest>()

  private fun entityRequests(surveyId: String): List<ListEntitiesRequest> =
    service.listRequests.filterIsInstance<ListEntitiesRequest>().filter { it.survey_id == surveyId }

  /** Records a pending edit of one of [surveyId]'s map features. */
  private fun recordPendingEdit(surveyId: String) = runDirect {
    store.transaction {
      val entityId = entities(surveyId).first().id
      OutboxRecorder()
        .record(
          this,
          MutationTarget.Data(surveyId),
          Rebaser.entityKey(entityId),
          MutationPayload.Data(
            DataMutation(
              entity_mutation =
                EntityMutation(
                  entity_id = entityId,
                  update =
                    EntityPatch(
                      patches = listOf(EntityFieldPatch(key = "label", label_value = "Offline"))
                    ),
                )
            )
          ),
        )
    }
  }

  @Test
  fun seed_downloadsTheDefaultSurveys_andListsTheOthersFromTheirOverviews(): Unit = runDirect {
    seed()
    val otherId = notDownloadedId()

    assertTrue(kenyaId in downloaded())
    assertFalse(otherId in downloaded())
    val downloadedIds = downloaded()
    store.transaction {
      assertEquals(downloadedIds, surveys().filter { it.isDownloaded }.map { it.id }.toSet())
      assertNotNull(surveyBase(kenyaId))
      assertTrue(entityCount(kenyaId) > 0)
      assertNotNull(survey(otherId))
      assertNull(surveyBase(otherId))
      assertEquals(0, entityCount(otherId))
      assertFalse(otherId in syncState().syncTokens.entitiesBySurvey)
    }
    assertTrue(entityRequests(otherId).isEmpty())
  }

  @Test
  fun pull_syncsDataForDownloadedSurveysOnly(): Unit = runDirect {
    seed()
    val otherId = notDownloadedId()
    service.listRequests.clear()

    pull()

    assertTrue(entityRequests(kenyaId).isNotEmpty())
    assertTrue(entityRequests(otherId).isEmpty())
    // The survey list's token lives in memory: it isn't stored with the persisted sync state.
    assertEquals("", store.transaction { syncState().syncTokens.surveys })
  }

  @Test
  fun downloadingASurvey_fetchesItsDefinitionAndData(): Unit = runDirect {
    seed()
    val otherId = notDownloadedId()

    surveys.setSurveyDownloaded(otherId, downloaded = true)
    pull()

    assertTrue(otherId in downloaded())
    assertEquals("", entityRequests(otherId).single().sync_token)
    store.transaction {
      assertNotNull(surveyBase(otherId))
      assertTrue(survey(otherId)!!.isDownloaded)
      assertTrue(otherId in syncState().syncTokens.entitiesBySurvey)
      assertTrue(otherId in syncState().syncTokens.submissionsBySurvey)
    }
  }

  @Test
  fun removingADownload_dropsTheLocalCopy_onlyAfterPendingUploadsDrain(): Unit = runDirect {
    seed()
    recordPendingEdit(kenyaId)
    val entityCount = store.transaction { entityCount(kenyaId) }

    surveys.setSurveyDownloaded(kenyaId, downloaded = false)
    pull()

    // The pending edit keeps the local copy.
    store.transaction {
      assertNotNull(surveyBase(kenyaId))
      assertEquals(entityCount, entityCount(kenyaId))
      assertFalse(survey(kenyaId)!!.isDownloaded)
    }

    // Once the outbox has drained, the survey stays listed without its definition or data.
    store.transaction { putOutbox(emptyList()) }
    pull()

    store.transaction {
      assertNotNull(survey(kenyaId))
      assertNull(surveyBase(kenyaId))
      assertEquals(0, entityCount(kenyaId))
      assertTrue(entityBases(kenyaId).isEmpty())
      assertTrue(submissionBases(kenyaId).isEmpty())
      assertFalse(kenyaId in syncState().syncTokens.entitiesBySurvey)
      assertFalse(kenyaId in syncState().syncTokens.submissionsBySurvey)
    }
  }

  @Test
  fun newSavedRevisionOfADownloadedSurvey_isFetchedThroughBatchGet(): Unit = runDirect {
    seed()
    pull()
    service.listRequests.clear()

    service.retitleSurvey(kenyaId, "Renamed survey")
    pull()

    assertEquals("Renamed survey", store.transaction { survey(kenyaId)?.title })
    assertEquals("Renamed survey", store.transaction { surveyBase(kenyaId)?.saved?.title })
    // The survey list is closed, so it wasn't queried.
    assertTrue(listRequests().isEmpty())
  }

  @Test
  fun surveyList_isCaughtUpOnlyWhileOpen_andStartsWithAFreshQueryOnEachOpen(): Unit = runDirect {
    seed()
    service.listRequests.clear()

    pull()
    assertTrue(listRequests().isEmpty())

    sync.setSurveyListOpen(true)
    pull()
    pull()
    assertEquals(2, listRequests().size)
    assertTrue(listRequests().last().sync_token.isNotEmpty())

    sync.setSurveyListOpen(false)
    pull()
    sync.setSurveyListOpen(true)
    pull()
    assertEquals(3, listRequests().size)
    assertEquals("", listRequests().last().sync_token)
  }

  @Test
  fun newSurveyInTheOpenList_isListedWithoutItsDefinition(): Unit = runDirect {
    seed()
    val otherId = notDownloadedId()
    store.transaction { deleteSurvey(otherId) }

    sync.setSurveyListOpen(true)
    service.listRequests.clear()
    service.retitleSurvey(otherId, "Shared with me")
    pull()

    store.transaction {
      assertEquals("Shared with me", survey(otherId)?.title)
      assertNull(surveyBase(otherId))
    }
  }

  @Test
  fun librariesAreNotSynced(): Unit = runDirect {
    seed()
    pull()

    assertTrue(service.listRequests.filterIsInstance<ListLibrariesRequest>().isEmpty())
    assertTrue(store.transaction { libraries() }.isEmpty())
  }

  @Test
  fun trashedDownloadedSurvey_leavesTheDownloadedSet_andIsRemoved(): Unit = runDirect {
    seed()

    service.trashSurvey(kenyaId)
    pull()

    assertFalse(kenyaId in downloaded())
    store.transaction {
      assertNull(survey(kenyaId))
      assertNull(surveyBase(kenyaId))
      assertEquals(0, entityCount(kenyaId))
    }
  }

  @Test
  fun trashedDownloadedSurvey_keepsItsLocalCopy_untilPendingUploadsDrain(): Unit = runDirect {
    seed()
    recordPendingEdit(kenyaId)

    service.trashSurvey(kenyaId)
    pull()

    assertFalse(kenyaId in downloaded())
    store.transaction { assertNotNull(surveyBase(kenyaId)) }

    store.transaction { putOutbox(emptyList()) }
    pull()

    store.transaction { assertNull(survey(kenyaId)) }
  }

  @Test
  fun purgedDownloadedSurvey_keepsItsLocalCopy_andStopsSyncingItsData(): Unit = runDirect {
    seed()
    val entityCount = store.transaction { entityCount(kenyaId) }

    service.purgeSurvey(kenyaId)
    service.listRequests.clear()
    pull()

    assertTrue(kenyaId in downloaded())
    assertTrue(entityRequests(kenyaId).isEmpty())
    store.transaction {
      assertTrue(survey(kenyaId)!!.isUnavailable)
      assertNotNull(surveyBase(kenyaId))
      assertEquals(entityCount, entityCount(kenyaId))
    }
  }

  @Test
  fun downloadedSet_survivesASaveAndRestore(): Unit = runDirect {
    seed()
    val otherId = notDownloadedId()
    surveys.setSurveyDownloaded(otherId, downloaded = true)
    val saved = store.transaction { ClientSyncStateCodec.capture(this) }

    val restored = InMemoryLocalStore()
    restored.transaction { ClientSyncStateCodec.restore(this, saved) }

    assertEquals(downloaded(), restored.transaction { syncState().downloadedSurveyIds })
    assertTrue(otherId in downloaded())
  }
}
