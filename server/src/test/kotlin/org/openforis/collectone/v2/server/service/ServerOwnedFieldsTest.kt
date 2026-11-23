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

import com.squareup.wire.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.data.AuditInfo
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.GetSurveyResponse
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement

/**
 * `audit_info` and `SurveyDef.saved_revision_number` are server-owned: the server stamps them from
 * the authenticated caller and its own clock, and ignores whatever a client's mutation carries.
 */
open class ServerOwnedFieldsTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

  private val backend by lazy { newBackend() }

  private suspend fun <T> asCaller(caller: Caller, block: suspend UserDataBackend.() -> T): T =
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      backend.block()
    }

  private suspend fun survey(surveyId: String): GetSurveyResponse =
    asCaller(ORGANIZER) { GetSurvey(GetSurveyRequest(survey_id = surveyId)) }

  @Test
  fun surveyEdit_withForgedAuditInfoAndRevisionNumber_keepsServerValues() = runTest {
    val before = survey(KENYA).survey!!

    asCaller(ORGANIZER) {
      MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              SurveyMutation(
                upsert =
                  SurveyDef(
                    title = "Retitled",
                    audit_info = FORGED_AUDIT,
                    saved_revision_number = 99L,
                  )
              )
            ),
        )
      )
    }

    val after = survey(KENYA).survey!!
    assertEquals("Retitled", after.title)
    assertEquals(before.saved_revision_number, after.saved_revision_number)
    val audit = assertNotNull(after.audit_info)
    assertEquals(before.audit_info?.created_by.orEmpty(), audit.created_by)
    assertEquals(before.audit_info?.create_time, audit.create_time)
    assertEquals(ORGANIZER.userId, audit.last_modified_by)
    assertNotNull(audit.last_modify_time)
    assertNotEquals(FORGED_TIME, audit.last_modify_time)
  }

  @Test
  fun newSurvey_withForgedAuditInfoAndRevisionNumber_startsAtRevisionOneByItsCreator() = runTest {
    asCaller(ORGANIZER) {
      MutateSurvey(
        MutateSurveyRequest(
          survey_id = NEW_SURVEY,
          mutations =
            listOf(
              SurveyMutation(
                upsert =
                  SurveyDef(
                    survey_id = NEW_SURVEY,
                    title = "New",
                    audit_info = FORGED_AUDIT,
                    saved_revision_number = 7L,
                  )
              )
            ),
        )
      )
    }

    val created = survey(NEW_SURVEY).survey!!
    assertEquals(1L, created.saved_revision_number)
    val audit = assertNotNull(created.audit_info)
    assertEquals(ORGANIZER.userId, audit.created_by)
    assertEquals(ORGANIZER.userId, audit.last_modified_by)
    assertNotNull(audit.create_time)
    assertNotEquals(FORGED_TIME, audit.create_time)
    assertEquals(audit.create_time, audit.last_modify_time)
  }

  @Test
  fun savingAWorkingCopy_withAForgedRevisionNumber_takesTheNextServerNumber() = runTest {
    val before = survey(KENYA).survey!!.saved_revision_number

    asCaller(ORGANIZER) {
      MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          working_copy = true,
          mutations =
            listOf(
              SurveyMutation(upsert = SurveyDef(title = "Draft", saved_revision_number = 50L))
            ),
        )
      )
      MutateSurvey(
        MutateSurveyRequest(
          survey_id = KENYA,
          mutations = listOf(SurveyMutation(save_working_copy = true)),
        )
      )
    }

    val saved = survey(KENYA).survey!!
    assertEquals("Draft", saved.title)
    assertEquals(before + 1L, saved.saved_revision_number)
    assertEquals((before + 1L).toString(), saved.version)
  }

  @Test
  fun entityWrites_withForgedAuditInfo_areAttributedToTheCaller() = runTest {
    val created = mutateEntity(EntityRecord(entity_id = ENTITY, label = "Created"))
    val createdAudit = assertNotNull(created.audit_info)
    assertEquals(COLLECTOR.userId, createdAudit.created_by)
    assertEquals(COLLECTOR.userId, createdAudit.last_modified_by)
    assertNotNull(createdAudit.create_time)
    assertNotEquals(FORGED_TIME, createdAudit.create_time)

    val updated = mutateEntity(EntityRecord(label = "Updated"))

    assertEquals("Updated", updated.label)
    val updatedAudit = assertNotNull(updated.audit_info)
    assertEquals(COLLECTOR.userId, updatedAudit.created_by)
    assertEquals(createdAudit.create_time, updatedAudit.create_time)
    assertEquals(COLLECTOR.userId, updatedAudit.last_modified_by)
    assertNotEquals(FORGED_TIME, updatedAudit.last_modify_time)
  }

  @Test
  fun submissionWrites_withForgedAuditInfo_keepTheCreator() = runTest {
    val created = mutateSubmission(submissionUpsert("first"))
    assertEquals(COLLECTOR.userId, created.audit_info?.created_by)

    // A whole-record replacement carries the client's copy of every field, audit info included.
    val replaced = mutateSubmission(submissionUpsert("second"))

    val audit = assertNotNull(replaced.audit_info)
    assertEquals(COLLECTOR.userId, audit.created_by)
    assertEquals(created.audit_info?.create_time, audit.create_time)
    assertEquals(COLLECTOR.userId, audit.last_modified_by)
    assertNotEquals(FORGED_TIME, audit.last_modify_time)
  }

  @Test
  fun submissionFromTheDevelopmentFallbackCaller_isAttributedToItsCollectorEmail() = runTest {
    val upsert = submissionUpsert("dev").copy(collector_email = "collector.two@example.org")

    val created = backend.mutateSubmission(upsert)

    assertEquals("collector.two@example.org", created.audit_info?.created_by)
  }

  private suspend fun mutateEntity(upsert: EntityRecord): EntityRecord =
    asCaller(COLLECTOR) {
        MutateData(
          MutateDataRequest(
            survey_id = KENYA,
            mutations =
              listOf(
                DataMutation(
                  entity_mutation =
                    EntityMutation(
                      entity_id = ENTITY,
                      dataset_id = DATASET,
                      upsert =
                        upsert.copy(
                          dataset_id = DATASET,
                          is_field_created = true,
                          audit_info = FORGED_AUDIT,
                        ),
                    )
                )
              ),
          )
        )
      }
      .results
      .single()
      .entity!!

  private fun submissionUpsert(note: String) =
    SubmissionRecord(
      submission_id = SUBMISSION,
      form_id = FORM,
      entity_label = note,
      audit_info = FORGED_AUDIT,
    )

  private suspend fun mutateSubmission(upsert: SubmissionRecord): SubmissionRecord =
    asCaller(COLLECTOR) { mutateSubmission(upsert) }

  private suspend fun UserDataBackend.mutateSubmission(upsert: SubmissionRecord): SubmissionRecord =
    MutateData(
        MutateDataRequest(
          survey_id = KENYA,
          mutations =
            listOf(
              DataMutation(
                submission_mutation =
                  SubmissionMutation(submission_id = SUBMISSION, form_id = FORM, upsert = upsert)
              )
            ),
        )
      )
      .results
      .single()
      .submission!!

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val DATASET = "coffee_parcels"
    const val FORM = "coffee_baseline"
    val NEW_SURVEY = testId("survey-server-owned")
    val ENTITY = testId("entity-server-owned")
    val SUBMISSION = testId("submission-server-owned")

    val ORGANIZER = Caller("uid-field-lead", "field.lead@example.org", emailVerified = true)
    val COLLECTOR = Caller("uid-collector-one", "collector.one@example.org", emailVerified = true)

    val FORGED_TIME: Instant = Instant.ofEpochSecond(1L, 0L)
    val FORGED_AUDIT =
      AuditInfo(
        created_by = "uid-forger",
        create_time = FORGED_TIME,
        last_modified_by = "uid-forger",
        last_modify_time = FORGED_TIME,
      )
  }
}
