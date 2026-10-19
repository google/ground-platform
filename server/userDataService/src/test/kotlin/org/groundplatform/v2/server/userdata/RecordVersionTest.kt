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

import groundplatform.v2.data.AcquirePlotLockRequest
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityFieldPatch
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.GetEntityRequest
import groundplatform.v2.data.GetSubmissionRequest
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.data.ReleasePlotLockRequest
import groundplatform.v2.data.SubmissionFieldPatch
import groundplatform.v2.data.SubmissionMutation
import groundplatform.v2.data.SubmissionPatch
import groundplatform.v2.library.GetLibraryRequest
import groundplatform.v2.library.LibraryFieldPatch
import groundplatform.v2.library.LibraryMutation
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.survey.AcceptSurveyInviteRequest
import groundplatform.v2.survey.AccessRulesPatch
import groundplatform.v2.survey.BatchGetSurveyOverviewsRequest
import groundplatform.v2.survey.GetOrganizationRequest
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.JoinOrganizationRequest
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.SurveyFieldPatch
import groundplatform.v2.survey.SurveyMutation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Every write to a synced record strictly increases its version (`docs/technical/sync-engine.md`,
 * "Sync Tokens"), so clients can tell which copy is newer without comparing commit timestamps.
 */
open class RecordVersionTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

  private val backend by lazy { newBackend() }

  private suspend fun entityRevision() =
    backend.GetEntity(GetEntityRequest(survey_id = KENYA, entity_id = ENTITY)).entity!!.revision

  private suspend fun submissionRevision(id: String) =
    backend
      .GetSubmission(GetSubmissionRequest(survey_id = KENYA, submission_id = id))
      .submission!!
      .revision

  private suspend fun mutateEntity(mutation: EntityMutation) =
    backend.MutateData(
      MutateDataRequest(
        survey_id = KENYA,
        mutations = listOf(DataMutation(entity_mutation = mutation)),
      )
    )

  @Test
  fun entityWrites_bumpTheRevision_includingPlotLocks() = runBlocking {
    var revision = entityRevision()
    suspend fun assertBumped(what: String) {
      val next = entityRevision()
      assertEquals(revision + 1, next, what)
      revision = next
    }

    mutateEntity(
      EntityMutation(
        entity_id = ENTITY,
        dataset_id = DATASET,
        update = EntityPatch(patches = listOf(EntityFieldPatch(key = "label", label_value = "L"))),
      )
    )
    assertBumped("field patch")

    val stored = backend.GetEntity(GetEntityRequest(survey_id = KENYA, entity_id = ENTITY)).entity!!
    mutateEntity(EntityMutation(entity_id = ENTITY, dataset_id = DATASET, upsert = stored))
    assertBumped("upsert")

    backend.AcquirePlotLock(
      AcquirePlotLockRequest(
        survey_id = KENYA,
        dataset_id = DATASET,
        entity_id = ENTITY,
        user_id = "u1",
      )
    )
    assertBumped("acquire plot lock")

    backend.ReleasePlotLock(
      ReleasePlotLockRequest(
        survey_id = KENYA,
        dataset_id = DATASET,
        entity_id = ENTITY,
        user_id = "u1",
      )
    )
    assertBumped("release plot lock")

    mutateEntity(EntityMutation(entity_id = ENTITY, dataset_id = DATASET, delete = true))
    assertBumped("delete")
  }

  @Test
  fun submissionWrites_bumpTheRevision() = runBlocking {
    val id =
      backend
        .ListSubmissions(groundplatform.v2.data.ListSubmissionsRequest(survey_id = KENYA))
        .submissions
        .first()
        .submission_id
    val before = submissionRevision(id)
    backend.MutateData(
      MutateDataRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            DataMutation(
              submission_mutation =
                SubmissionMutation(
                  submission_id = id,
                  update =
                    SubmissionPatch(
                      patches =
                        listOf(SubmissionFieldPatch(key = "form_version", string_value = "v2"))
                    ),
                )
            ),
            DataMutation(
              submission_mutation = SubmissionMutation(submission_id = id, delete = true)
            ),
          ),
      )
    )
    // Each mutation in the request is its own write.
    assertEquals(before + 2, submissionRevision(id))
  }

  @Test
  fun organizationWrites_bumpTheVersionByOnePerRequest() = runBlocking {
    suspend fun version() =
      backend.GetOrganization(GetOrganizationRequest(organization_id = ORG)).organization!!.version
    val start = version()

    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORG,
        mutations =
          listOf(
            OrganizationMutation(
              update_field = OrganizationFieldPatch(key = "description", string_value = "a")
            ),
            OrganizationMutation(
              update_field = OrganizationFieldPatch(key = "name", string_value = "b")
            ),
          ),
      )
    )
    assertEquals(start + 1, version(), "patches")

    val current =
      backend.GetOrganization(GetOrganizationRequest(organization_id = ORG)).organization
    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORG,
        // A client-supplied version is ignored: the server owns it.
        mutations =
          listOf(OrganizationMutation(upsert_organization = current!!.copy(version = 99L))),
      )
    )
    assertEquals(start + 2, version(), "upsert")

    backend.JoinOrganization(
      JoinOrganizationRequest(organization_id = ORG, user_id = "u2", email = "u2@example.com")
    )
    assertEquals(start + 3, version(), "join")

    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORG,
        mutations = listOf(OrganizationMutation(delete_organization = true)),
      )
    )
    assertEquals(start + 4, version(), "delete")
    val created =
      backend
        .MutateOrganization(
          MutateOrganizationRequest(
            organization_id = testId("org-new"),
            mutations =
              listOf(
                OrganizationMutation(
                  upsert_organization =
                    Organization(organization_id = testId("org-new"), name = "New")
                )
              ),
          )
        )
        .organization!!
    assertEquals(1L, created.version)
  }

  @Test
  fun libraryWrites_bumpTheVersionByOnePerRequest() = runBlocking {
    suspend fun library() = backend.GetLibrary(GetLibraryRequest(organization_id = ORG)).library!!
    val start = library().version
    backend.MutateLibrary(
      MutateLibraryRequest(
        organization_id = ORG,
        mutations =
          listOf(
            LibraryMutation(
              update_field =
                LibraryFieldPatch(
                  key = "settings",
                  settings_value = OrganizationLibrarySettings(organization_id = ORG),
                )
            )
          ),
      )
    )
    assertEquals(start + 1, library().version, "patch")
    backend.MutateLibrary(
      MutateLibraryRequest(
        organization_id = ORG,
        mutations = listOf(LibraryMutation(upsert_library = library().copy(version = 50L))),
      )
    )
    assertEquals(start + 2, library().version, "upsert")
  }

  @Test
  fun surveyWrites_bumpTheSharedRecordVersion_butNotTheSavedRevision() = runBlocking {
    suspend fun saved() = backend.GetSurvey(GetSurveyRequest(survey_id = KENYA))
    suspend fun overview() =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews.single {
        it.survey_id == KENYA
      }
    val start = saved()
    val startVersion = start.survey!!.record_version
    assertEquals(startVersion, overview().record_version)

    // Working-copy edit: every part gets the new record version; the saved revision stays.
    val edited =
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
    val afterEdit = saved()
    assertEquals(startVersion + 1, edited.survey!!.record_version)
    assertEquals(startVersion + 1, afterEdit.survey!!.record_version)
    assertEquals(startVersion + 1, afterEdit.acl!!.record_version)
    assertEquals(startVersion + 1, overview().record_version)
    assertEquals(start.survey!!.version, afterEdit.survey!!.version)
    assertEquals(start.survey!!.saved_revision_number, afterEdit.survey!!.saved_revision_number)

    // Access rule change.
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations =
          listOf(
            SurveyMutation(
              update_access_rules = AccessRulesPatch(collector_consent_required = true)
            )
          ),
      )
    )
    assertEquals(startVersion + 2, overview().record_version)

    // Saving the working copy bumps both the record version and the saved revision.
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations = listOf(SurveyMutation(save_working_copy = true)),
      )
    )
    val afterSave = saved().survey!!
    assertEquals(startVersion + 3, afterSave.record_version)
    assertEquals(start.survey!!.saved_revision_number + 1, afterSave.saved_revision_number)

    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations =
          listOf(
            SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = "Again")),
            SurveyMutation(discard_working_copy = true),
          ),
      )
    )
    assertEquals(startVersion + 4, overview().record_version, "discard")

    backend.AcceptSurveyInvite(AcceptSurveyInviteRequest(survey_id = KENYA, invite_token = "none"))
    assertEquals(startVersion + 5, saved().acl!!.record_version, "invite")
    assertEquals(startVersion + 5, overview().record_version)

    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        mutations = listOf(SurveyMutation(trash_survey = true)),
      )
    )
    val trashed =
      backend
        .BatchGetSurveyOverviews(BatchGetSurveyOverviewsRequest(survey_ids = listOf(KENYA)))
        .results
        .single()
        .overview!!
    assertEquals(startVersion + 6, trashed.record_version, "trash")
    assertEquals(start.survey!!.saved_revision_number + 1, trashed.saved_revision_number)
  }

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val ENTITY = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val DATASET = "coffee_parcels"
    const val ORG = "00000013-31cc-5c95-90ea-379f624a5c72"
  }
}
