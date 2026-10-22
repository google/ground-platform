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
package org.openforis.collectone.v2.server.userdata

import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyState

/**
 * Survey Trash, policy-based sharing (`sharing_role`), downloaded-survey lookups, the public survey
 * directory, and per-survey delta scopes (`docs/technical/backend/spanner-schema.md`).
 */
open class SurveyTrashAndSharingTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

  private val backend by lazy { newBackend() }

  private suspend fun mutateSurvey(vararg mutations: SurveyMutation) =
    backend.MutateSurvey(MutateSurveyRequest(survey_id = KENYA, mutations = mutations.toList()))

  private suspend fun listedIds() =
    backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews.map { it.survey_id }

  private suspend fun batchGet(vararg ids: String) =
    backend.BatchGetSurveyOverviews(BatchGetSurveyOverviewsRequest(survey_ids = ids.toList()))

  @Test
  fun trash_hidesTheSurvey_andDeltaSendsATombstone_untilRestored(): Unit = runBlocking {
    val token = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).next_sync_token
    val stateBefore = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!.state

    mutateSurvey(SurveyMutation(trash_survey = true))

    assertTrue(KENYA !in listedIds(), "query mode excludes trashed surveys")
    val tombstone =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = token)).overviews.single {
        it.survey_id == KENYA
      }
    assertEquals(SurveyState.DELETED, tombstone.state)
    assertNotNull(tombstone.trash_time)

    mutateSurvey(SurveyMutation(restore_survey = true))

    assertTrue(KENYA in listedIds())
    val restored = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!
    assertNull(restored.trash_time)
    assertEquals(stateBefore, restored.state, "restore keeps the lifecycle state")
  }

  @Test
  fun trashedSurvey_rejectsReads_butStillAcceptsUploads(): Unit = runBlocking {
    mutateSurvey(SurveyMutation(trash_survey = true))

    for (read in
      listOf<suspend () -> Unit>(
        { backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)) },
        { backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)) },
        { backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA)) },
      )) {
      assertFailsWith<IllegalStateException> { read() }
    }

    val upload =
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "trash-upload-1",
                submission_mutation =
                  SubmissionMutation(
                    submission_id = testId("submission-recorded-before-trash"),
                    form_id = "form-1",
                    upsert = SubmissionRecord(form_version = "1"),
                  ),
              )
            ),
        )
      )
    assertEquals(1, upload.results.size)
  }

  @Test
  fun trash_isIdempotent_andKeepsTheFirstTrashTime(): Unit = runBlocking {
    mutateSurvey(SurveyMutation(trash_survey = true))
    val first = batchGet(KENYA).results.single().overview!!.trash_time
    mutateSurvey(SurveyMutation(trash_survey = true))
    assertEquals(first, batchGet(KENYA).results.single().overview!!.trash_time)
  }

  @Test
  fun batchGet_returnsOverviewsInRequestOrder_andNotFoundForUnknownIds(): Unit = runBlocking {
    mutateSurvey(SurveyMutation(trash_survey = true))

    val results = batchGet("survey-unknown", KENYA).results

    assertEquals(listOf("survey-unknown", KENYA), results.map { it.survey_id })
    assertEquals(Status.Code.NOT_FOUND.value(), results[0].error_code)
    assertNull(results[0].overview)
    assertNotNull(results[1].overview!!.trash_time)
  }

  @Test
  fun batchGet_capsTheNumberOfIds(): Unit = runBlocking {
    val ids = List(UserDataBackend.MAX_BATCH_SURVEY_IDS + 1) { "survey-$it" }
    assertFailsWith<IllegalArgumentException> {
      backend.BatchGetSurveyOverviews(BatchGetSurveyOverviewsRequest(survey_ids = ids))
    }
  }

  @Test
  fun sharingRole_defaultsToDataCollector_andCanBeViewer(): Unit = runBlocking {
    assertEquals(Role.DATA_COLLECTOR, batchGet(KENYA).results.single().overview!!.default_role)

    mutateSurvey(SurveyMutation(acl_upsert = SurveyAcl(default_role = Role.VIEWER)))

    assertEquals(Role.VIEWER, batchGet(KENYA).results.single().overview!!.default_role)
  }

  @Test
  fun sharingRole_rejectsSurveyOrganizer(): Unit = runBlocking {
    assertFailsWith<IllegalArgumentException> {
      mutateSurvey(SurveyMutation(acl_upsert = SurveyAcl(default_role = Role.SURVEY_ORGANIZER)))
    }
  }

  @Test
  fun searchPublicSurveys_listsOnlyPublicSurveys_thatArentTrashed(): Unit = runBlocking {
    mutateSurvey(SurveyMutation(acl_upsert = SurveyAcl(sharing_policy = SharingPolicy.PUBLIC)))
    val title = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!.title

    val found =
      backend.SearchPublicSurveys(SearchPublicSurveysRequest(query = title.take(5).uppercase()))
    assertTrue(KENYA in found.overviews.map { it.survey_id })
    assertTrue(found.overviews.all { it.sharing_policy == SharingPolicy.PUBLIC })

    mutateSurvey(SurveyMutation(trash_survey = true))

    val afterTrash = backend.SearchPublicSurveys(SearchPublicSurveysRequest())
    assertTrue(KENYA !in afterTrash.overviews.map { it.survey_id })
  }

  @Test
  fun delta_rejectsSubSurveyFilters_butQueryModeAppliesThem(): Unit = runBlocking {
    val entityToken = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    val submissionToken =
      backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA)).next_sync_token
    val rejected =
      listOf<suspend () -> Unit>(
        {
          backend.ListEntities(
            ListEntitiesRequest(survey_id = KENYA, sync_token = entityToken, dataset_id = DATASET)
          )
        },
        {
          backend.ListEntities(
            ListEntitiesRequest(
              survey_id = KENYA,
              sync_token = entityToken,
              s2_cell_tokens = listOf("10d96b"),
            )
          )
        },
        {
          backend.ListSubmissions(
            ListSubmissionsRequest(survey_id = KENYA, sync_token = submissionToken, form_id = "f")
          )
        },
        {
          backend.ListSubmissions(
            ListSubmissionsRequest(survey_id = KENYA, sync_token = submissionToken, entity_id = "e")
          )
        },
      )
    for (call in rejected) {
      val error = assertFailsWith<StatusException> { call() }
      assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }

    val inDataset =
      backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, dataset_id = DATASET)).entities
    assertTrue(inDataset.isNotEmpty())
    assertTrue(inDataset.all { it.dataset_id == DATASET })
  }

  @Test
  fun saveWorkingCopy_recordsImmutableRevisions_servedBySavedVersion() = runBlocking {
    val v1 = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Kenya v2"))),
      )
    )
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(save_working_copy = true)),
      )
    )

    assertEquals("Kenya v2", backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey?.title)
    assertEquals(
      v1.title,
      backend
        .GetSurvey(GetSurveyRequest(survey_id = KENYA, saved_version = v1.version))
        .survey
        ?.title,
    )
    assertEquals(
      "Kenya v2",
      backend.GetSurvey(GetSurveyRequest(survey_id = KENYA, saved_version = "2")).survey?.title,
    )
  }

  @Test
  fun purgeTrashedSurveys_removesData_andLeavesADeletedTombstone() = runBlocking {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(trash_survey = true)),
      )
    )
    val trashedAt = batchGet(KENYA).results.single().overview!!.trash_time!!
    // Not yet past the cutoff: untouched.
    assertTrue(backend.purgeTrashedSurveys(olderThan = trashedAt).isEmpty())

    val token = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).next_sync_token
    val purged =
      backend.purgeTrashedSurveys(
        olderThan = com.squareup.wire.Instant.ofEpochSecond(trashedAt.epochSecond + 1L, 0)
      )
    assertEquals(listOf(KENYA), purged)

    val overview = batchGet(KENYA).results.single().overview!!
    assertEquals(SurveyState.DELETED, overview.state)
    assertEquals("", overview.title)
    assertNull(overview.trash_time)
    val delta =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = token)).overviews
    assertEquals(listOf(KENYA), delta.map { it.survey_id })
    assertEquals(SurveyState.DELETED, delta.single().state)
  }

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val DATASET = "coffee_parcels"
  }
}
