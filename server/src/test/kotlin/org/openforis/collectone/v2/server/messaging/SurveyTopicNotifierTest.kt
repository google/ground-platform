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
package org.openforis.collectone.v2.server.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.AcquirePlotLockRequest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.ReleasePlotLockRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.server.app.loadSeed
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.service.RecordVersionTest
import org.openforis.collectone.v2.server.service.UserDataBackend

/**
 * Verifies that `survey_<survey_id>_def` and `survey_<survey_id>_entities` FCM topic tickles are
 * published only for the matching mutations (and that working-copy autosaves and submission-only
 * writes do not spam mobile subscribers).
 */
class SurveyTopicNotifierTest {
  private data class SentMessage(
    val topic: String,
    val collapseKey: String,
    val data: Map<String, String>,
  )

  private val sent = mutableListOf<SentMessage>()
  private val notifier =
    DebouncedSurveyTopicNotifier(
      sender = { topic, collapseKey, data -> sent += SentMessage(topic, collapseKey, data) },
      entityDebounceMs = 0L,
    )
  private val backend = UserDataBackend(InMemoryUserDataStore(loadSeed(null)), notifier)

  @Test
  fun surveyMutations_publishDefTopicOnlyForSavedOrAclChanges() = runTest {
    sent.clear()

    // 1. Working-copy autosave should NOT publish to survey_<id>_def.
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = RecordVersionTest.KENYA,
        working_copy = true,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Draft title"))),
      )
    )
    assertTrue(sent.isEmpty(), "Working-copy autosaves must not publish to _def topic")

    // 2. Saving the working copy publishes to survey_<id>_def with the new record_version.
    val savedResp =
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = RecordVersionTest.KENYA,
          working_copy = true,
          mutations = listOf(SurveyMutation(save_working_copy = true)),
        )
      )
    assertEquals(1, sent.size)
    assertEquals("survey_${RecordVersionTest.KENYA}_def", sent.single().topic)
    assertEquals("def", sent.single().data["scope"])
    assertEquals(
      savedResp.survey!!.record_version.toString(),
      sent.single().data["record_version"],
    )

    // 3. ACL change publishes to survey_<id>_def.
    sent.clear()
    val aclResp =
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = RecordVersionTest.KENYA,
          mutations =
            listOf(SurveyMutation(acl_upsert = SurveyAcl(collector_consent_required = true))),
        )
      )
    assertEquals(1, sent.size)
    assertEquals("survey_${RecordVersionTest.KENYA}_def", sent.single().topic)
    assertEquals(aclResp.acl!!.record_version.toString(), sent.single().data["record_version"])

    // 4. Accepting an invite publishes to survey_<id>_def.
    sent.clear()
    backend.AcceptSurveyInvite(
      AcceptSurveyInviteRequest(survey_id = RecordVersionTest.KENYA, invite_token = "tok")
    )
    assertEquals(1, sent.size)
    assertEquals("survey_${RecordVersionTest.KENYA}_def", sent.single().topic)
  }

  @Test
  fun dataAndPlotLockMutations_publishEntitiesTopicOnlyWhenEntitiesChange() = runTest {
    sent.clear()

    // 1. Submission-only mutation does NOT publish to survey_<id>_entities.
    backend.MutateData(
      MutateDataRequest(
        survey_id = RecordVersionTest.KENYA,
        mutations =
          listOf(
            DataMutation(
              submission_mutation =
                SubmissionMutation(
                  submission_id = "00000000-0000-4000-8000-000000000999",
                  form_id = "f1",
                  upsert =
                    SubmissionRecord(
                      submission_id = "00000000-0000-4000-8000-000000000999",
                      form_id = "f1",
                    ),
                )
            )
          ),
      )
    )
    assertTrue(sent.isEmpty(), "Submission-only writes must not publish to _entities topic")

    // 2. Entity mutation publishes to survey_<id>_entities.
    backend.MutateData(
      MutateDataRequest(
        survey_id = RecordVersionTest.KENYA,
        mutations =
          listOf(
            DataMutation(
              entity_mutation =
                EntityMutation(
                  entity_id = RecordVersionTest.ENTITY,
                  dataset_id = RecordVersionTest.DATASET,
                  upsert = EntityRecord(label = "Updated parcel"),
                )
            )
          ),
      )
    )
    assertEquals(1, sent.size)
    assertEquals("survey_${RecordVersionTest.KENYA}_entities", sent.single().topic)
    assertEquals("entities", sent.single().data["scope"])
    assertEquals(RecordVersionTest.KENYA, sent.single().data["survey_id"])

    // 3. AcquirePlotLock and ReleasePlotLock publish to survey_<id>_entities.
    sent.clear()
    backend.AcquirePlotLock(
      AcquirePlotLockRequest(
        survey_id = RecordVersionTest.KENYA,
        dataset_id = RecordVersionTest.DATASET,
        entity_id = RecordVersionTest.ENTITY,
        user_id = "u-1",
      )
    )
    assertEquals(1, sent.size)
    assertEquals("survey_${RecordVersionTest.KENYA}_entities", sent.single().topic)

    sent.clear()
    backend.ReleasePlotLock(
      ReleasePlotLockRequest(
        survey_id = RecordVersionTest.KENYA,
        dataset_id = RecordVersionTest.DATASET,
        entity_id = RecordVersionTest.ENTITY,
        user_id = "u-1",
      )
    )
    assertEquals(1, sent.size)
    assertEquals("survey_${RecordVersionTest.KENYA}_entities", sent.single().topic)
  }
}
