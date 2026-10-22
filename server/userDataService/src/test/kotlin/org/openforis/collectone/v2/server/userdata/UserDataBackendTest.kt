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

import com.squareup.wire.Instant
import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.ImpactEventType
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListSubmissionsRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.SearchOrganizationsRequest
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation

/**
 * Behavior of [UserDataBackend] loaded with the bundled sample seed, following
 * `docs/technical/sync-engine.md`: `List*` RPCs in query and delta modes with opaque sync tokens,
 * tombstones, paging, working copies, queries, plot locks, and impact events.
 */
open class UserDataBackendTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

  private val backend by lazy { newBackend() }

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
  fun fullSyncs_returnSeedDataInCommitTimestampOrder() = runBlocking {
    val orgs = backend.ListOrganizations(ListOrganizationsRequest())
    assertEquals(4, orgs.organizations.size)
    assertTrue(isStrictlyIncreasing(orgs.organizations.map { it.commit_timestamp }))
    assertTrue(orgs.next_sync_token.isNotEmpty())
    assertEquals("", orgs.next_page_token)

    val libs = backend.ListLibraries(ListLibrariesRequest()).libraries
    assertEquals(2, libs.size)
    assertTrue(isStrictlyIncreasing(libs.map { it.commit_timestamp }))

    val overviews = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews
    assertEquals(6, overviews.size)
    assertTrue(isStrictlyIncreasing(overviews.map { it.commit_timestamp }))
    assertTrue(overviews.single { it.survey_id == KENYA }.has_working_copy)

    val survey = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey
    assertNotNull(survey)
    assertTrue(survey.forms.isNotEmpty())

    val entities = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).entities
    assertEquals(5, entities.size)
    assertTrue(isStrictlyIncreasing(entities.map { it.commit_timestamp }))

    val submissions = backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA)).submissions
    assertEquals(9, submissions.size)
    assertTrue(isStrictlyIncreasing(submissions.map { it.commit_timestamp }))
  }

  @Test
  fun mutations_getIncreasingTimestamps_andDeltasReturnOnlyLaterChanges() = runBlocking {
    val initialWatermark = backend.currentWatermark()
    val overviewToken = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).next_sync_token
    val orgToken = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token
    val entityToken = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    val submissionToken =
      backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA)).next_sync_token

    val surveyMut =
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          working_copy = true,
          mutations =
            listOf(
              SurveyMutation(upsert = SurveyDef(title = "Updated title")),
              SurveyMutation(save_working_copy = true),
            ),
        )
      )
    val midWatermark = surveyMut.commit_timestamp!!
    val orgTokenAtMid = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token

    val orgMut =
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = KENYA_FOREST_SERVICE,
          mutations =
            listOf(OrganizationMutation(upsert = Organization(description = "Updated KFS"))),
        )
      )
    val libMut =
      backend.MutateLibrary(
        MutateLibraryRequest(
          organization_id = KENYA_FOREST_SERVICE,
          mutations =
            listOf(
              LibraryMutation(
                upsert =
                  OrganizationLibraryRecord(
                    bundle =
                      LibraryBundle(
                        concepts =
                          listOf(
                            ConceptDef(
                              id = "org.00000013-31cc-5c95-90ea-379f624a5c72.soil_ph",
                              label = LocalizedText(values = mapOf("en" to "Soil pH")),
                              data_type = DataType.TYPE_DOUBLE,
                            )
                          )
                      )
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
                    entity_id = testId("entity-nyr-999"),
                    dataset_id = "coffee_parcels",
                    base_revision = 0L,
                    upsert =
                      EntityRecord(
                        entity_id = testId("entity-nyr-999"),
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
                    submission_id = testId("sub-nyr-999-1"),
                    form_id = "form-eudr-baseline",
                    base_revision = 0L,
                    upsert =
                      SubmissionRecord(
                        submission_id = testId("sub-nyr-999-1"),
                        form_id = "form-eudr-baseline",
                        entity_id = testId("entity-nyr-999"),
                      ),
                  ),
              ),
            ),
        )
      )

    assertTrue(midWatermark > initialWatermark)
    assertTrue(orgMut.commit_timestamp!! > midWatermark)
    assertTrue(libMut.commit_timestamp!! > orgMut.commit_timestamp!!)
    assertTrue(dataMut.commit_timestamp!! > libMut.commit_timestamp!!)

    // Saving the working copy promotes the edited title and clears `has_working_copy`.
    val overviews =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = overviewToken)).overviews
    assertEquals(listOf(KENYA), overviews.map { it.survey_id })
    assertEquals("Updated title", overviews.single().title)
    assertFalse(overviews.single().has_working_copy)
    assertNull(
      backend
        .GetSurvey(
          GetSurveyRequest(survey_id = KENYA, working_copy = GetSurveyRequest.WorkingCopy())
        )
        .survey
    )

    val orgsFromStart =
      backend.ListOrganizations(ListOrganizationsRequest(sync_token = orgToken)).organizations
    assertEquals(listOf("Updated KFS"), orgsFromStart.map { it.description })
    val orgsAfterMid =
      backend.ListOrganizations(ListOrganizationsRequest(sync_token = orgTokenAtMid))
    assertEquals(listOf("Updated KFS"), orgsAfterMid.organizations.map { it.description })
    // Polling again with the returned token finds nothing new.
    val idle =
      backend.ListOrganizations(ListOrganizationsRequest(sync_token = orgsAfterMid.next_sync_token))
    assertTrue(idle.organizations.isEmpty())

    val newEntities =
      backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = entityToken))
    assertEquals(listOf(testId("entity-nyr-999")), newEntities.entities.map { it.entity_id })
    val newSubmissions =
      backend.ListSubmissions(
        ListSubmissionsRequest(survey_id = KENYA, sync_token = submissionToken)
      )
    assertEquals(
      listOf(testId("sub-nyr-999-1")),
      newSubmissions.submissions.map { it.submission_id },
    )
  }

  @Test
  fun delta_includesTombstones() = runBlocking {
    val token = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    backend.MutateData(
      MutateDataRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-delete",
              entity_mutation =
                EntityMutation(
                  entity_id = "00000007-640e-5ed7-9874-4d6b6717f21d",
                  dataset_id = "coffee_parcels",
                  delete = EntityRecord(is_deleted = true),
                ),
            )
          ),
      )
    )
    val delta = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = token))
    assertEquals(
      listOf("00000007-640e-5ed7-9874-4d6b6717f21d"),
      delta.entities.map { it.entity_id },
    )
    assertTrue(delta.entities.single().is_deleted)
    // Query mode leaves deleted records out unless asked for them.
    val query = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA))
    assertTrue(query.entities.none { it.entity_id == "00000007-640e-5ed7-9874-4d6b6717f21d" })
    val withDeleted =
      backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, include_deleted = true))
    assertTrue(
      withDeleted.entities.any {
        it.entity_id == "00000007-640e-5ed7-9874-4d6b6717f21d" && it.is_deleted
      }
    )
  }

  @Test
  fun delta_pagesInCommitOrder_andPageTokensAreSyncTokens() = runBlocking {
    val token = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    val created = (1..5).map { i -> createEntity(testId("entity-paged-$i")) }
    assertEquals(5, created.size)

    val ids = mutableListOf<String>()
    var pageToken = ""
    var pages = 0
    var nextSyncToken: String
    do {
      val page =
        backend.ListEntities(
          ListEntitiesRequest(
            survey_id = KENYA,
            sync_token = token,
            page_token = pageToken,
            page_size = 2,
          )
        )
      ids += page.entities.map { it.entity_id }
      pageToken = page.next_page_token
      nextSyncToken = page.next_sync_token
      pages++
      // Exactly one of the two tokens is set on every page.
      assertTrue(pageToken.isEmpty() != nextSyncToken.isEmpty())
    } while (pageToken.isNotEmpty())
    assertEquals(3, pages)
    assertEquals((1..5).map { testId("entity-paged-$it") }, ids)

    // An intermediate page token resumes like a sync token.
    val firstPage =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = KENYA, sync_token = token, page_size = 2)
      )
    val resumed =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = KENYA, sync_token = firstPage.next_page_token)
      )
    assertEquals((3..5).map { testId("entity-paged-$it") }, resumed.entities.map { it.entity_id })

    // Nothing new after the last page; the empty poll still returns a usable token.
    val idle =
      backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = nextSyncToken))
    assertTrue(idle.entities.isEmpty())
    assertTrue(idle.next_sync_token.isNotEmpty())
    createEntity(testId("entity-after-idle"))
    val later =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = KENYA, sync_token = idle.next_sync_token)
      )
    assertEquals(listOf(testId("entity-after-idle")), later.entities.map { it.entity_id })
  }

  @Test
  fun query_pagesASnapshot_andItsSyncTokenCatchesLaterChanges() = runBlocking {
    val first =
      backend.ListEntities(
        ListEntitiesRequest(survey_id = KENYA, order_by = "label", page_size = 2)
      )
    assertEquals(2, first.entities.size)
    assertTrue(first.next_page_token.isNotEmpty())
    assertEquals("", first.next_sync_token)

    // A change between pages doesn't make the query skip records; it shows up in the delta.
    createEntity(testId("entity-between-pages"))
    val labels = first.entities.map { it.label }.toMutableList()
    var pageToken = first.next_page_token
    var syncToken = ""
    while (pageToken.isNotEmpty()) {
      val page =
        backend.ListEntities(
          ListEntitiesRequest(
            survey_id = KENYA,
            order_by = "label",
            page_size = 2,
            page_token = pageToken,
          )
        )
      labels += page.entities.map { it.label }
      pageToken = page.next_page_token
      syncToken = page.next_sync_token
    }
    assertEquals(labels.sorted(), labels)
    assertTrue(labels.size >= 5)

    val delta = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = syncToken))
    assertEquals(listOf(testId("entity-between-pages")), delta.entities.map { it.entity_id })
  }

  @Test
  fun delta_rejectsQueryFields_andScopeMismatches() = runBlocking {
    val token = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    val withOrder =
      assertFailsWith<StatusException> {
        backend.ListEntities(
          ListEntitiesRequest(survey_id = KENYA, sync_token = token, order_by = "label")
        )
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, withOrder.status.code)
    val withFilter =
      assertFailsWith<StatusException> {
        backend.ListSubmissions(
          ListSubmissionsRequest(
            survey_id = KENYA,
            sync_token =
              backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA)).next_sync_token,
            filter = mapOf("collector_name" to "x"),
          )
        )
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, withFilter.status.code)

    val otherSurvey =
      assertFailsWith<StatusException> {
        backend.ListEntities(ListEntitiesRequest(survey_id = "survey-other", sync_token = token))
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, otherSurvey.status.code)
    val otherResource =
      assertFailsWith<StatusException> {
        backend.ListSubmissions(ListSubmissionsRequest(survey_id = KENYA, sync_token = token))
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, otherResource.status.code)
    val malformed =
      assertFailsWith<StatusException> {
        backend.ListOrganizations(ListOrganizationsRequest(sync_token = "not a token"))
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, malformed.status.code)
  }

  @Test
  fun tokensFromAnotherBackendOrBeforeReset_failWithFailedPrecondition() = runBlocking {
    val token = backend.ListEntities(ListEntitiesRequest(survey_id = KENYA)).next_sync_token
    val restarted = newBackend()
    val fromOtherProcess =
      assertFailsWith<StatusException> {
        restarted.ListEntities(ListEntitiesRequest(survey_id = KENYA, sync_token = token))
      }
    assertEquals(Status.Code.FAILED_PRECONDITION, fromOtherProcess.status.code)

    val orgToken = backend.ListOrganizations(ListOrganizationsRequest()).next_sync_token
    backend.reset()
    val afterReset =
      assertFailsWith<StatusException> {
        backend.ListOrganizations(ListOrganizationsRequest(sync_token = orgToken))
      }
    assertEquals(Status.Code.FAILED_PRECONDITION, afterReset.status.code)
  }

  private suspend fun createEntity(id: String) =
    backend
      .MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "mut-$id",
                entity_mutation =
                  EntityMutation(
                    entity_id = id,
                    dataset_id = "coffee_parcels",
                    upsert = EntityRecord(entity_id = id, label = "Plot $id"),
                  ),
              )
            ),
        )
      )
      .commit_timestamp

  @Test
  fun workingCopyMutations_leaveTheSavedRevisionUnchanged() = runBlocking {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Draft"))),
      )
    )
    val saved = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!
    val workingCopy =
      backend.GetSurvey(
        GetSurveyRequest(survey_id = KENYA, working_copy = GetSurveyRequest.WorkingCopy())
      )
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
    assertTrue(firstPage.next_page_token.isNotEmpty())
    val rest =
      backend.ListEntities(
        ListEntitiesRequest(
          survey_id = KENYA,
          order_by = "label",
          page_token = firstPage.next_page_token,
        )
      )
    assertTrue(rest.next_sync_token.isNotEmpty())
    val labels = (firstPage.entities + rest.entities).map { it.label }
    assertEquals(5, labels.size)
    assertEquals(labels.sorted(), labels)

    val submissions =
      backend.ListSubmissions(
        ListSubmissionsRequest(survey_id = KENYA, order_by = "commit_timestamp desc")
      )
    assertEquals(9, submissions.submissions.size)
    assertTrue(isStrictlyIncreasing(submissions.submissions.reversed().map { it.commit_timestamp }))

    val directory = backend.SearchOrganizations(SearchOrganizationsRequest()).organizations
    assertTrue(directory.all { it.listed && !it.is_deleted })
  }

  @Test
  fun plotLocks_areHeldUntilReleased() = runBlocking {
    fun acquire(user: String) =
      AcquirePlotLockRequest(
        survey_id = KENYA,
        dataset_id = "coffee_parcels",
        entity_id = "00000007-640e-5ed7-9874-4d6b6717f21d",
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
          entity_id = "00000007-640e-5ed7-9874-4d6b6717f21d",
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
    val token = backend.ListImpactEvents(ListImpactEventsRequest()).next_sync_token
    val recorded =
      backend.RecordImpactEvents(
        RecordImpactEventsRequest(
          events =
            listOf(
              ImpactEvent(
                event_id = testId("ev-export-1"),
                type = ImpactEventType.EXPORT,
                survey_id = KENYA,
                organization_id = KENYA_FOREST_SERVICE,
              )
            )
        )
      )
    assertTrue(recorded.commit_timestamp!! > before)
    assertEquals(
      listOf(testId("ev-export-1")),
      backend.ListImpactEvents(ListImpactEventsRequest(sync_token = token)).events.map {
        it.event_id
      },
    )
  }

  @Test
  fun reset_restoresTheSeed() = runBlocking {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Changed"))),
      )
    )
    backend.reset()
    val title = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey?.title
    assertTrue(title != "Changed")
  }

  @Test
  fun surveyMutation_exceedingFormLimit_throwsResourceExhausted() = runBlocking {
    val exception =
      assertFailsWith<StatusException> {
        backend.MutateSurvey(
          MutateSurveyRequest(
            survey_id = testId("new-survey-1"),
            mutations =
              listOf(
                SurveyMutation(
                  upsert =
                    SurveyDef(
                      survey_id = testId("new-survey-1"),
                      title = "Exceeded Forms Survey",
                      quota_limits = QuotaLimits(max_forms_per_survey = 2),
                      forms =
                        listOf(
                          FormDef(form_id = "f1"),
                          FormDef(form_id = "f2"),
                          FormDef(form_id = "f3"),
                        ),
                    )
                )
              ),
          )
        )
      }
    assertEquals(Status.Code.RESOURCE_EXHAUSTED, exception.status.code)
    assertTrue(exception.message!!.contains("Form quota exceeded"))
  }

  @Test
  fun dataMutation_exceedingSubmissionsPerEntity_throwsResourceExhausted() = runBlocking {
    val initial = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            SurveyMutation(
              upsert =
                initial.copy(quota_limits = QuotaLimits(max_submissions_per_entity_per_user = 1))
            )
          ),
      )
    )

    val targetEntity = testId("entity-nyr-104")
    // First submission for targetEntity by collector-test
    backend.MutateData(
      MutateDataRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "sub-mut-1",
              submission_mutation =
                SubmissionMutation(
                  submission_id = testId("sub-test-1"),
                  form_id = "form-eudr-baseline",
                  upsert =
                    SubmissionRecord(
                      submission_id = testId("sub-test-1"),
                      form_id = "form-eudr-baseline",
                      entity_id = targetEntity,
                      audit_info =
                        openforis.collectone.v1.data.AuditInfo(created_by = "collector-test"),
                    ),
                ),
            )
          ),
      )
    )

    // Second submission by collector-test for same entity should fail
    val exception =
      assertFailsWith<StatusException> {
        backend.MutateData(
          MutateDataRequest(
            survey_id = KENYA,
            mutations =
              listOf(
                DataMutation(
                  mutation_id = "sub-mut-2",
                  submission_mutation =
                    SubmissionMutation(
                      submission_id = testId("sub-test-2"),
                      form_id = "form-eudr-baseline",
                      upsert =
                        SubmissionRecord(
                          submission_id = testId("sub-test-2"),
                          form_id = "form-eudr-baseline",
                          entity_id = targetEntity,
                          audit_info =
                            openforis.collectone.v1.data.AuditInfo(created_by = "collector-test"),
                        ),
                    ),
                )
              ),
          )
        )
      }
    assertEquals(Status.Code.RESOURCE_EXHAUSTED, exception.status.code)
    assertTrue(exception.message!!.contains("Submission quota exceeded"))
  }

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val KENYA_FOREST_SERVICE = "00000013-31cc-5c95-90ea-379f624a5c72"
  }
}
