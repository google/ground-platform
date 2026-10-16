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
package org.groundplatform.v2.server.userdata

import com.squareup.wire.Instant
import groundplatform.v2.data.AcquirePlotLockRequest
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ImpactEvent
import groundplatform.v2.data.ImpactEventType
import groundplatform.v2.data.ListEntitiesRequest
import groundplatform.v2.data.ListSubmissionsRequest
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.data.RecordImpactEventsRequest
import groundplatform.v2.data.ReleasePlotLockRequest
import groundplatform.v2.data.StreamEntitiesRequest
import groundplatform.v2.data.StreamImpactEventsRequest
import groundplatform.v2.data.StreamSubmissionsRequest
import groundplatform.v2.data.SubmissionMutation
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.LibraryFieldPatch
import groundplatform.v2.library.LibraryMutation
import groundplatform.v2.library.LocalizedText
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.StreamLibrariesRequest
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.ListOrganizationsRequest
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.StreamOrganizationsRequest
import groundplatform.v2.survey.StreamSurveyOverviewsRequest
import groundplatform.v2.survey.SurveyFieldPatch
import groundplatform.v2.survey.SurveyMutation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/**
 * Behavior of [UserDataBackend] loaded with the bundled sample seed, following
 * `docs/technical/sync-engine.md`: bounded delta streams ordered by `commit_timestamp`, catch-up by
 * server timestamps, tombstones, working copies, queries, plot locks, and impact events.
 */
class UserDataBackendTest {
  private val backend = UserDataBackend(loadSeed(null))

  private suspend fun <T> collect(call: suspend (SendChannel<T>) -> Unit): List<T> {
    val channel = Channel<T>(Channel.UNLIMITED)
    call(channel)
    channel.close()
    val items = mutableListOf<T>()
    for (item in channel) items += item
    return items
  }

  private fun isStrictlyIncreasing(timestamps: List<Instant?>): Boolean =
    timestamps.zipWithNext().all { (prev, curr) ->
      prev != null &&
        curr != null &&
        (curr.epochSecond > prev.epochSecond ||
          (curr.epochSecond == prev.epochSecond && curr.nano > prev.nano))
    }

  @Test
  fun bundledSeed_loadsSampleData() {
    val seed = loadSeed(null)
    assertEquals(6, seed.surveys.size)
    assertEquals(4, seed.organizations.size)
  }

  @Test
  fun streams_sendSeedDataInCommitTimestampOrder() = runBlocking {
    val orgs = collect { backend.StreamOrganizations(StreamOrganizationsRequest(), it) }
    assertEquals(4, orgs.size)
    assertTrue(isStrictlyIncreasing(orgs.map { it.commit_timestamp }))

    val libs = collect { backend.StreamLibraries(StreamLibrariesRequest(), it) }
    assertEquals(2, libs.size)
    assertTrue(isStrictlyIncreasing(libs.map { it.commit_timestamp }))

    val overviews = collect { backend.StreamSurveyOverviews(StreamSurveyOverviewsRequest(), it) }
    assertEquals(6, overviews.size)
    assertTrue(isStrictlyIncreasing(overviews.map { it.commit_timestamp }))
    assertTrue(overviews.single { it.survey_id == KENYA }.has_working_copy)

    val survey = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey
    assertNotNull(survey)
    assertTrue(survey.forms.isNotEmpty())

    val entities = collect { backend.StreamEntities(StreamEntitiesRequest(survey_id = KENYA), it) }
    assertEquals(5, entities.size)
    assertTrue(isStrictlyIncreasing(entities.map { it.commit_timestamp }))

    val submissions = collect {
      backend.StreamSubmissions(StreamSubmissionsRequest(survey_id = KENYA), it)
    }
    assertEquals(9, submissions.size)
    assertTrue(isStrictlyIncreasing(submissions.map { it.commit_timestamp }))
  }

  @Test
  fun mutations_getIncreasingTimestamps_andCatchUpFiltersBySinceAndUntil() = runBlocking {
    val initialWatermark = backend.currentWatermark()

    val surveyMut =
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          working_copy = true,
          mutations =
            listOf(
              SurveyMutation(
                update_field = SurveyFieldPatch(key = "title", string_value = "Updated title")
              ),
              SurveyMutation(save_working_copy = true),
            ),
        )
      )
    val midWatermark = surveyMut.commit_timestamp!!

    val orgMut =
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = KENYA_FOREST_SERVICE,
          mutations =
            listOf(
              OrganizationMutation(
                update_field =
                  OrganizationFieldPatch(key = "description", string_value = "Updated KFS")
              )
            ),
        )
      )

    val libMut =
      backend.MutateLibrary(
        MutateLibraryRequest(
          organization_id = KENYA_FOREST_SERVICE,
          mutations =
            listOf(
              LibraryMutation(
                update_field =
                  LibraryFieldPatch(
                    key = "concepts/org.org-kenya-forest-service.soil_ph",
                    concept_value =
                      ConceptDef(
                        id = "org.org-kenya-forest-service.soil_ph",
                        label = LocalizedText(values = mapOf("en" to "Soil pH")),
                        data_type = DataType.TYPE_DOUBLE,
                      ),
                  )
              )
            ),
        )
      )

    val dataMut =
      backend.MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "mut-create-entity",
                entity_mutation =
                  EntityMutation(
                    entity_id = "entity-nyr-999",
                    dataset_id = "coffee_parcels",
                    base_revision = 0L,
                    upsert =
                      EntityRecord(
                        entity_id = "entity-nyr-999",
                        dataset_id = "coffee_parcels",
                        label = "Plot NYR-999",
                        properties = mapOf("farmer_owner" to TypedValue(string_value = "Wanjiku")),
                      ),
                  ),
              ),
              DataMutation(
                mutation_id = "mut-create-sub",
                submission_mutation =
                  SubmissionMutation(
                    submission_id = "sub-nyr-999-1",
                    form_id = "form-eudr-baseline",
                    base_revision = 0L,
                    upsert =
                      SubmissionRecord(
                        submission_id = "sub-nyr-999-1",
                        form_id = "form-eudr-baseline",
                        entity_id = "entity-nyr-999",
                      ),
                  ),
              ),
            ),
        )
      )

    assertTrue(midWatermark.epochSecond > initialWatermark.epochSecond)
    assertTrue(orgMut.commit_timestamp!!.epochSecond > midWatermark.epochSecond)
    assertTrue(libMut.commit_timestamp!!.epochSecond > orgMut.commit_timestamp!!.epochSecond)
    assertTrue(dataMut.commit_timestamp!!.epochSecond > libMut.commit_timestamp!!.epochSecond)

    // Saving the working copy promotes the edited title and clears `has_working_copy`.
    val overviews = collect {
      backend.StreamSurveyOverviews(
        StreamSurveyOverviewsRequest(since_timestamp = initialWatermark),
        it,
      )
    }
    assertEquals(listOf(KENYA), overviews.map { it.survey_id })
    assertEquals("Updated title", overviews.single().title)
    assertFalse(overviews.single().has_working_copy)
    assertNull(backend.GetSurvey(GetSurveyRequest(survey_id = KENYA, working_copy = true)).survey)

    val orgsBeforeMid = collect {
      backend.StreamOrganizations(
        StreamOrganizationsRequest(
          since_timestamp = initialWatermark,
          until_timestamp = midWatermark,
        ),
        it,
      )
    }
    assertTrue(orgsBeforeMid.isEmpty())
    val orgsAfterMid = collect {
      backend.StreamOrganizations(StreamOrganizationsRequest(since_timestamp = midWatermark), it)
    }
    assertEquals(listOf("Updated KFS"), orgsAfterMid.map { it.description })

    val newEntities = collect {
      backend.StreamEntities(
        StreamEntitiesRequest(survey_id = KENYA, since_timestamp = initialWatermark),
        it,
      )
    }
    assertEquals(listOf("entity-nyr-999"), newEntities.map { it.entity_id })
    val newSubmissions = collect {
      backend.StreamSubmissions(
        StreamSubmissionsRequest(survey_id = KENYA, since_timestamp = initialWatermark),
        it,
      )
    }
    assertEquals(listOf("sub-nyr-999-1"), newSubmissions.map { it.submission_id })
  }

  @Test
  fun workingCopyMutations_leaveTheSavedRevisionUnchanged() = runBlocking {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations =
          listOf(
            SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = "Draft"))
          ),
      )
    )
    val saved = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!
    val workingCopy = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA, working_copy = true))
    assertEquals("Draft", workingCopy.survey?.title)
    assertTrue(saved.title != "Draft")
  }

  @Test
  fun queries_sortAndPage_andSearchTheOrganizationDirectory() = runBlocking {
    val firstPage =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = KENYA, order_by = "label", page_size = 2)
      )
    assertEquals(2, firstPage.entities.size)
    assertEquals("2", firstPage.next_page_token)
    assertNotNull(firstPage.server_watermark)
    val rest =
      backend.ListEntities(
        ListEntitiesRequest(
          survey_id = KENYA,
          order_by = "label",
          page_token = firstPage.next_page_token,
        )
      )
    val labels = (firstPage.entities + rest.entities).map { it.label }
    assertEquals(5, labels.size)
    assertEquals(labels.sorted(), labels)

    val submissions =
      backend.ListSubmissions(
        ListSubmissionsRequest(survey_id = KENYA, order_by = "commit_timestamp desc")
      )
    assertEquals(9, submissions.submissions.size)
    assertTrue(isStrictlyIncreasing(submissions.submissions.reversed().map { it.commit_timestamp }))

    val directory = backend.ListOrganizations(ListOrganizationsRequest()).organizations
    assertTrue(directory.all { it.listed && !it.is_deleted })
  }

  @Test
  fun plotLocks_areHeldUntilReleased() = runBlocking {
    fun acquire(user: String) =
      AcquirePlotLockRequest(
        survey_id = KENYA,
        dataset_id = "coffee_parcels",
        entity_id = "entity-nyr-104",
        user_id = user,
      )
    assertEquals(
      "uid-collector-one",
      backend.AcquirePlotLock(acquire("uid-collector-one")).lock?.locked_by_user_id,
    )
    // Another collector sees the existing holder while the lock is held.
    assertEquals(
      "uid-collector-one",
      backend.AcquirePlotLock(acquire("uid-field-lead")).lock?.locked_by_user_id,
    )
    val released =
      backend.ReleasePlotLock(
        ReleasePlotLockRequest(
          survey_id = KENYA,
          dataset_id = "coffee_parcels",
          entity_id = "entity-nyr-104",
          user_id = "uid-collector-one",
        )
      )
    assertTrue(released.released)
    assertEquals(
      "uid-field-lead",
      backend.AcquirePlotLock(acquire("uid-field-lead")).lock?.locked_by_user_id,
    )
  }

  @Test
  fun impactEvents_getServerCommitTimestamps() = runBlocking {
    val before = backend.currentWatermark()
    val recorded =
      backend.RecordImpactEvents(
        RecordImpactEventsRequest(
          events =
            listOf(
              ImpactEvent(
                event_id = "ev-export-1",
                type = ImpactEventType.EXPORT,
                survey_id = KENYA,
                organization_id = KENYA_FOREST_SERVICE,
              )
            )
        )
      )
    assertTrue(recorded.commit_timestamp!!.epochSecond > before.epochSecond)
    val streamed = collect {
      backend.StreamImpactEvents(StreamImpactEventsRequest(since_timestamp = before), it)
    }
    assertEquals(listOf("ev-export-1"), streamed.map { it.event_id })
  }

  @Test
  fun reset_restoresTheSeed() = runBlocking {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = "Changed"))
          ),
      )
    )
    backend.reset()
    val title = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey?.title
    assertTrue(title != "Changed")
  }

  companion object {
    const val KENYA = "survey-kenya-coffee"
    const val KENYA_FOREST_SERVICE = "org-kenya-forest-service"
  }
}
