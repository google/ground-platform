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
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import io.grpc.StatusException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.ImpactEventType
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListEntitiesResponse
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.service.ServiceTestSeed.DATASET
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ENTITY_A
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ENTITY_B
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ENTITY_C
import org.openforis.collectone.v2.server.service.ServiceTestSeed.FORM
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ORGANIZATION
import org.openforis.collectone.v2.server.service.ServiceTestSeed.OTHER_SURVEY
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SUBMISSION_1
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SUBMISSION_2
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SUBMISSION_3
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY

/**
 * Behavior of the data RPCs ([DataServiceImpl]) on [ServiceTestSeed], following
 * `docs/technical/sync-engine.md`: `List*` RPCs in query and delta modes with opaque sync tokens,
 * tombstones, paging, the submission quota, plot locks, and impact events.
 */
open class DataServiceImplTest {
  /** A backend loaded with [seed]; overridden to test other stores. */
  protected open fun newBackend(seed: UserDataSeed): UserDataBackend = inMemoryBackend(seed)

  private val backend by lazy { newBackend(ServiceTestSeed.seed) }

  private suspend fun entitySyncToken(): String =
    backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY)).next_sync_token

  private suspend fun entityIdsSince(syncToken: String): List<String> =
    backend
      .ListEntities(ListEntitiesRequest(survey_id = SURVEY, sync_token = syncToken))
      .entities
      .map {
        it.entity_id
      }

  private suspend fun createEntity(id: String, label: String = "Plot $id") =
    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-$id",
              entity_mutation =
                EntityMutation(
                  entity_id = id,
                  dataset_id = DATASET,
                  upsert = EntityRecord(entity_id = id, dataset_id = DATASET, label = label),
                ),
            )
          ),
      )
    )

  private suspend fun deleteEntity(id: String) =
    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-delete-$id",
              entity_mutation =
                EntityMutation(
                  entity_id = id,
                  dataset_id = DATASET,
                  upsert = EntityRecord(is_deleted = true),
                ),
            )
          ),
      )
    )

  private suspend fun createSubmission(id: String, entityId: String, collectorEmail: String = "") =
    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-$id",
              submission_mutation =
                SubmissionMutation(
                  submission_id = id,
                  form_id = FORM,
                  upsert =
                    SubmissionRecord(
                      submission_id = id,
                      form_id = FORM,
                      entity_id = entityId,
                      collector_email = collectorEmail,
                    ),
                ),
            )
          ),
      )
    )

  /** Reads an entity delta from [syncToken] in pages of two, returning every page. */
  private suspend fun deltaPages(syncToken: String): List<ListEntitiesResponse> {
    val pages = mutableListOf<ListEntitiesResponse>()
    var pageToken = ""
    do {
      val page =
        backend.ListEntities(
          ListEntitiesRequest(
            survey_id = SURVEY,
            sync_token = syncToken,
            page_token = pageToken,
            page_size = 2,
          )
        )
      pages += page
      pageToken = page.next_page_token
    } while (pageToken.isNotEmpty())
    return pages
  }

  /** Reads all entities in label order in pages of two, returning every page. */
  private suspend fun labelOrderedPages(
    beforeSecondPage: suspend () -> Unit = {}
  ): List<ListEntitiesResponse> {
    val pages = mutableListOf<ListEntitiesResponse>()
    var pageToken = ""
    do {
      val page =
        backend.ListEntities(
          ListEntitiesRequest(
            survey_id = SURVEY,
            order_by = "label",
            page_size = 2,
            page_token = pageToken,
          )
        )
      pages += page
      if (pages.size == 1) beforeSecondPage()
      pageToken = page.next_page_token
    } while (pageToken.isNotEmpty())
    return pages
  }

  private fun lockRequest(userId: String) =
    AcquirePlotLockRequest(
      survey_id = SURVEY,
      dataset_id = DATASET,
      entity_id = ENTITY_A,
      user_id = userId,
    )

  @Test
  fun listEntities_fullSync_returnsEntitiesInCommitOrder() = runTest {
    val entities = backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY)).entities

    assertEquals(listOf(ENTITY_C, ENTITY_A, ENTITY_B), entities.map { it.entity_id })
  }

  @Test
  fun listSubmissions_fullSync_returnsSubmissionsInCommitOrder() = runTest {
    val submissions =
      backend.ListSubmissions(ListSubmissionsRequest(survey_id = SURVEY)).submissions

    assertEquals(
      listOf(SUBMISSION_1, SUBMISSION_2, SUBMISSION_3),
      submissions.map { it.submission_id },
    )
  }

  @Test
  fun mutateData_commitsAfterTheCurrentWatermark() = runTest {
    val watermark = backend.currentWatermark()

    val response = createEntity(testId("entity-new"))

    assertTrue(response.commit_timestamp!! > watermark)
  }

  @Test
  fun listEntities_delta_returnsEntitiesCreatedSinceTheToken() = runTest {
    val token = entitySyncToken()
    createEntity(testId("entity-new"))

    assertEquals(listOf(testId("entity-new")), entityIdsSince(token))
  }

  @Test
  fun listSubmissions_delta_returnsSubmissionsCreatedSinceTheToken() = runTest {
    val token = backend.ListSubmissions(ListSubmissionsRequest(survey_id = SURVEY)).next_sync_token
    createSubmission(testId("submission-new"), ENTITY_A)

    val delta =
      backend.ListSubmissions(ListSubmissionsRequest(survey_id = SURVEY, sync_token = token))

    assertEquals(listOf(testId("submission-new")), delta.submissions.map { it.submission_id })
  }

  @Test
  fun listEntities_delta_returnsDeletedEntitiesAsTombstones() = runTest {
    val token = entitySyncToken()
    deleteEntity(ENTITY_A)

    val delta = backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY, sync_token = token))

    assertEquals(listOf(ENTITY_A to true), delta.entities.map { it.entity_id to it.is_deleted })
  }

  @Test
  fun listEntities_query_leavesOutDeletedEntities() = runTest {
    deleteEntity(ENTITY_A)

    val entities = backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY)).entities

    assertEquals(listOf(ENTITY_C, ENTITY_B), entities.map { it.entity_id })
  }

  @Test
  fun listEntities_queryWithIncludeDeleted_returnsDeletedEntities() = runTest {
    deleteEntity(ENTITY_A)

    val entities =
      backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY, include_deleted = true)).entities

    assertEquals(
      listOf(ENTITY_C to false, ENTITY_B to false, ENTITY_A to true),
      entities.map { it.entity_id to it.is_deleted },
    )
  }

  @Test
  fun listEntities_pagedDelta_returnsChangesInCommitOrder() = runTest {
    val token = entitySyncToken()
    val created = (1..5).map { testId("entity-paged-$it") }
    created.forEach { createEntity(it) }

    val pages = deltaPages(token)

    assertEquals(
      listOf(created.subList(0, 2), created.subList(2, 4), created.subList(4, 5)),
      pages.map { page -> page.entities.map { it.entity_id } },
    )
  }

  @Test
  fun listEntities_pagedDelta_setsAPageTokenOnEveryPageButTheLast() = runTest {
    val token = entitySyncToken()
    (1..5).forEach { createEntity(testId("entity-paged-$it")) }

    val pages = deltaPages(token)

    // Exactly one of the two tokens is set on every page: the last page has the sync token.
    assertEquals(
      listOf(true to false, true to false, false to true),
      pages.map { it.next_page_token.isNotEmpty() to it.next_sync_token.isNotEmpty() },
    )
  }

  @Test
  fun listEntities_deltaPageToken_resumesLikeASyncToken() = runTest {
    val token = entitySyncToken()
    val created = (1..5).map { testId("entity-paged-$it") }
    created.forEach { createEntity(it) }
    val firstPage =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = SURVEY, sync_token = token, page_size = 2)
      )

    assertEquals(created.subList(2, 5), entityIdsSince(firstPage.next_page_token))
  }

  @Test
  fun listEntities_emptyDelta_returnsATokenThatCatchesLaterChanges() = runTest {
    val idle =
      backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY, sync_token = entitySyncToken()))
    createEntity(testId("entity-after-idle"))

    assertEquals(emptyList(), idle.entities)
    assertEquals(listOf(testId("entity-after-idle")), entityIdsSince(idle.next_sync_token))
  }

  @Test
  fun listEntities_queryOrderedByLabel_pagesInLabelOrder() = runTest {
    val pages = labelOrderedPages()

    assertEquals(
      listOf(listOf("Plot A", "Plot B"), listOf("Plot C")),
      pages.map { page -> page.entities.map { it.label } },
    )
  }

  @Test
  fun listEntities_queryPages_setAPageTokenUntilTheLastPageWhichHasASyncToken() = runTest {
    val pages = labelOrderedPages()

    assertEquals(
      listOf(true to false, false to true),
      pages.map { it.next_page_token.isNotEmpty() to it.next_sync_token.isNotEmpty() },
    )
  }

  @Test
  fun listEntities_queryPages_readASnapshotThatIgnoresChangesBetweenPages() = runTest {
    val pages =
      labelOrderedPages(beforeSecondPage = { createEntity(testId("entity-new"), "Plot AA") })

    assertEquals(
      listOf("Plot A", "Plot B", "Plot C"),
      pages.flatMap { page -> page.entities.map { it.label } },
    )
  }

  @Test
  fun listEntities_querySyncToken_catchesChangesMadeBetweenPages() = runTest {
    val pages =
      labelOrderedPages(beforeSecondPage = { createEntity(testId("entity-new"), "Plot AA") })

    assertEquals(listOf(testId("entity-new")), entityIdsSince(pages.last().next_sync_token))
  }

  @Test
  fun listSubmissions_orderedByCommitTimestampDescending_returnsNewestFirst() = runTest {
    val submissions =
      backend
        .ListSubmissions(
          ListSubmissionsRequest(survey_id = SURVEY, order_by = "commit_timestamp desc")
        )
        .submissions

    assertEquals(
      listOf(SUBMISSION_3, SUBMISSION_2, SUBMISSION_1),
      submissions.map { it.submission_id },
    )
  }

  @Test
  fun listEntities_deltaWithOrderBy_failsWithInvalidArgument() = runTest {
    val token = entitySyncToken()

    val error =
      assertFailsWith<StatusException> {
        backend.ListEntities(
          ListEntitiesRequest(survey_id = SURVEY, sync_token = token, order_by = "label")
        )
      }

    assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
  }

  @Test
  fun listSubmissions_deltaWithFilter_failsWithInvalidArgument() = runTest {
    val token = backend.ListSubmissions(ListSubmissionsRequest(survey_id = SURVEY)).next_sync_token

    val error =
      assertFailsWith<StatusException> {
        backend.ListSubmissions(
          ListSubmissionsRequest(
            survey_id = SURVEY,
            sync_token = token,
            filter = mapOf("collector_name" to "x"),
          )
        )
      }

    assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
  }

  @Test
  fun listEntities_tokenFromAnotherSurvey_failsWithInvalidArgument() = runTest {
    val token = entitySyncToken()

    val error =
      assertFailsWith<StatusException> {
        backend.ListEntities(ListEntitiesRequest(survey_id = OTHER_SURVEY, sync_token = token))
      }

    assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
  }

  @Test
  fun listSubmissions_entitySyncToken_failsWithInvalidArgument() = runTest {
    val token = entitySyncToken()

    val error =
      assertFailsWith<StatusException> {
        backend.ListSubmissions(ListSubmissionsRequest(survey_id = SURVEY, sync_token = token))
      }

    assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
  }

  @Test
  fun mutateData_beyondTheSubmissionsPerEntityQuota_failsWithResourceExhausted() = runTest {
    val survey = backend.GetSurvey(GetSurveyRequest(survey_id = SURVEY)).survey!!
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            SurveyMutation(
              upsert =
                survey.copy(quota_limits = QuotaLimits(max_submissions_per_entity_per_user = 1))
            )
          ),
      )
    )
    val entity = testId("entity-quota")
    createSubmission(testId("submission-first"), entity, collectorEmail = "collector@example.org")

    val error =
      assertFailsWith<StatusException> {
        createSubmission(
          testId("submission-second"),
          entity,
          collectorEmail = "collector@example.org",
        )
      }

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
    assertTrue(error.message!!.contains("Submission quota exceeded"))
  }

  @Test
  fun mutateData_submission_stampsServerUpdatedAtAndKeepsClientUpdatedAt() = runTest {
    val id = testId("submission-stamped")
    val savedOnDevice = Instant.parse("2026-09-24T08:15:00Z")

    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-$id",
              submission_mutation =
                SubmissionMutation(
                  submission_id = id,
                  form_id = FORM,
                  upsert =
                    SubmissionRecord(
                      submission_id = id,
                      form_id = FORM,
                      client_updated_at = savedOnDevice,
                      server_updated_at = Instant.EPOCH,
                    ),
                ),
            )
          ),
      )
    )

    val stored =
      backend.ListSubmissions(ListSubmissionsRequest(survey_id = SURVEY)).submissions.single {
        it.submission_id == id
      }
    assertEquals(savedOnDevice, stored.client_updated_at)
    val serverUpdatedAt = assertNotNull(stored.server_updated_at)
    assertNotEquals(Instant.EPOCH, serverUpdatedAt, "server time, not the client's value")
    assertEquals(stored.audit_info?.last_modify_time, serverUpdatedAt, "the write's server clock")
  }

  @Test
  fun acquirePlotLock_onAnUnlockedPlot_locksItForTheCaller() = runTest {
    val lock = backend.AcquirePlotLock(lockRequest("uid-collector-one")).lock

    assertEquals("uid-collector-one", lock?.locked_by_user_id)
  }

  @Test
  fun acquirePlotLock_onAPlotLockedByAnother_returnsTheHolder() = runTest {
    backend.AcquirePlotLock(lockRequest("uid-collector-one"))

    val lock = backend.AcquirePlotLock(lockRequest("uid-field-lead")).lock

    assertEquals("uid-collector-one", lock?.locked_by_user_id)
  }

  @Test
  fun releasePlotLock_byTheHolder_releasesIt() = runTest {
    backend.AcquirePlotLock(lockRequest("uid-collector-one"))

    val response =
      backend.ReleasePlotLock(
        ReleasePlotLockRequest(
          survey_id = SURVEY,
          dataset_id = DATASET,
          entity_id = ENTITY_A,
          user_id = "uid-collector-one",
        )
      )

    assertTrue(response.released)
  }

  @Test
  fun acquirePlotLock_afterRelease_locksItForTheNextCaller() = runTest {
    backend.AcquirePlotLock(lockRequest("uid-collector-one"))
    backend.ReleasePlotLock(
      ReleasePlotLockRequest(
        survey_id = SURVEY,
        dataset_id = DATASET,
        entity_id = ENTITY_A,
        user_id = "uid-collector-one",
      )
    )

    val lock = backend.AcquirePlotLock(lockRequest("uid-field-lead")).lock

    assertEquals("uid-field-lead", lock?.locked_by_user_id)
  }

  @Test
  fun recordImpactEvents_commitsAfterTheCurrentWatermark() = runTest {
    val watermark = backend.currentWatermark()

    val response =
      backend.RecordImpactEvents(
        RecordImpactEventsRequest(events = listOf(exportEvent(testId("event-export"))))
      )

    assertTrue(response.commit_timestamp!! > watermark)
  }

  @Test
  fun listImpactEvents_delta_returnsEventsRecordedSinceTheToken() = runTest {
    val token = backend.ListImpactEvents(ListImpactEventsRequest()).next_sync_token
    backend.RecordImpactEvents(
      RecordImpactEventsRequest(events = listOf(exportEvent(testId("event-export"))))
    )

    val delta = backend.ListImpactEvents(ListImpactEventsRequest(sync_token = token))

    assertEquals(listOf(testId("event-export")), delta.events.map { it.event_id })
  }

  @Test
  fun listEntities_withFieldMask_entityIds_offset_andPropertyKeys() = runTest {
    val enriched =
      listOf("A", "B", "C").mapIndexed { index, suffix ->
        val id = testId("whisp-entity-$suffix")
        backend.MutateData(
          MutateDataRequest(
            survey_id = SURVEY,
            mutations =
              listOf(
                DataMutation(
                  client_id = "c1",
                  client_seq = 500L + index,
                  entity_mutation =
                    EntityMutation(
                      entity_id = id,
                      dataset_id = "sample_plots",
                      upsert =
                        EntityRecord(
                          entity_id = id,
                          dataset_id = "sample_plots",
                          survey_id = SURVEY,
                          label = "Plot $suffix",
                          properties =
                            mapOf(
                              "plot_id" to TypedValue(string_value = "P$suffix"),
                              "whisp_Country" to TypedValue(string_value = "KEN"),
                              "whisp_TMF_def_2020" to TypedValue(string_value = "$index.0"),
                            ),
                        ),
                    ),
                )
              ),
          )
        )
        id
      }

    // Schema-only query returns total_size and all distinct property keys including whisp_*.
    val schema =
      backend.ListEntities(
        ListEntitiesRequest(
          survey_id = SURVEY,
          dataset_id = "sample_plots",
          page_size = 1,
          field_mask = listOf("entity_id"),
          include_property_keys = true,
        )
      )
    assertEquals(3, schema.total_size)
    assertEquals(1, schema.entities.size)
    assertTrue(schema.entities.single().properties.isEmpty())
    assertTrue("whisp_Country" in schema.property_keys)
    assertTrue("whisp_TMF_def_2020" in schema.property_keys)

    // Random-access offset + order_by slices cleanly.
    val offsetPage =
      backend.ListEntities(
        ListEntitiesRequest(
          survey_id = SURVEY,
          dataset_id = "sample_plots",
          order_by = "label",
          offset = 1,
          page_size = 1,
        )
      )
    assertEquals(3, offsetPage.total_size)
    assertEquals(listOf("Plot B"), offsetPage.entities.map { it.label })

    // Point-batch entity_ids lookup + column-slice field_mask preserves order and returns only the
    // requested property keys.
    val tile =
      backend.ListEntities(
        ListEntitiesRequest(
          survey_id = SURVEY,
          dataset_id = "sample_plots",
          entity_ids = listOf(enriched[2], enriched[0]),
          field_mask = listOf("entity_id", "label", "properties.whisp_TMF_def_2020"),
        )
      )
    assertEquals(listOf(enriched[2], enriched[0]), tile.entities.map { it.entity_id })
    assertEquals(setOf("whisp_TMF_def_2020"), tile.entities[0].properties.keys)
    assertEquals("2.0", tile.entities[0].properties["whisp_TMF_def_2020"]?.string_value)
    assertEquals("0.0", tile.entities[1].properties["whisp_TMF_def_2020"]?.string_value)

    // Delta mode rejects offset / entity_ids / include_property_keys.
    val token = entitySyncToken()
    val error =
      assertFailsWith<StatusException> {
        backend.ListEntities(
          ListEntitiesRequest(survey_id = SURVEY, sync_token = token, offset = 1)
        )
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
  }

  private fun exportEvent(id: String) =
    ImpactEvent(
      event_id = id,
      type = ImpactEventType.EXPORT,
      survey_id = SURVEY,
      organization_id = ORGANIZATION,
    )
}
