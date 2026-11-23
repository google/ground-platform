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

import com.squareup.wire.ofEpochSecond
import io.grpc.Status
import io.grpc.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyArea
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement

/**
 * The design quotas `MutateSurvey` enforces on a survey's saved revision or working copy, and the
 * `RESOURCE_EXHAUSTED` messages it fails with. Each test creates a survey whose own `quota_limits`
 * the request's content exceeds.
 */
class SurveyDesignQuotaTest {
  private val backend = sampleBackend()

  private suspend fun createSurvey(survey: SurveyDef, acl: SurveyAcl? = null) {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = survey.survey_id,
        mutations = listOf(SurveyMutation(upsert = survey, acl_upsert = acl)),
      )
    )
  }

  private suspend fun quotaFailure(survey: SurveyDef, acl: SurveyAcl? = null): Status {
    val error = assertFailsWith<StatusException> { createSurvey(survey, acl) }
    return error.status
  }

  private fun formWith(vararg types: DataType) =
    FormDef(
      form_id = "f1",
      model =
        ModelDef(
          bindings =
            types.mapIndexed { i, type -> FieldBinding(field_path = "/data/q$i", type = type) }
        ),
    )

  private fun square(vertices: Int) =
    List(vertices) { i ->
      GeometryProtos.position(latitude = i.toDouble(), longitude = i.toDouble())
    }

  @Test
  fun tooManyQuestions_inAForm() = runTest {
    val status =
      quotaFailure(
        SurveyDef(
          survey_id = NEW_SURVEY,
          quota_limits = QuotaLimits(max_questions_per_form = 1),
          forms = listOf(formWith(DataType.TYPE_STRING, DataType.TYPE_STRING)),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Question quota exceeded: form \"f1\" has 2 questions, exceeding maximum of 1",
      status.description,
    )
  }

  @Test
  fun tooManyPhotoQuestions_inAForm() = runTest {
    val status =
      quotaFailure(
        SurveyDef(
          survey_id = NEW_SURVEY,
          quota_limits = QuotaLimits(max_photo_questions_per_form = 1),
          forms = listOf(formWith(DataType.TYPE_BINARY, DataType.TYPE_BINARY)),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Photo question quota exceeded: form \"f1\" has 2 photo questions, exceeding maximum of 1",
      status.description,
    )
  }

  @Test
  fun tooManyCollaborators_onTheAcl() = runTest {
    val status =
      quotaFailure(
        SurveyDef(survey_id = NEW_SURVEY, quota_limits = QuotaLimits(max_acl_users = 1)),
        SurveyAcl(
          survey_id = NEW_SURVEY,
          entries =
            listOf(
              AclEntry(email = "a@example.org", role = Role.DATA_COLLECTOR),
              AclEntry(email = "b@example.org", role = Role.DATA_COLLECTOR),
            ),
        ),
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "ACL quota exceeded: survey has 2 collaborators, exceeding maximum of 1",
      status.description,
    )
  }

  @Test
  fun tooManyPredefinedRows_inDatasets() = runTest {
    val status =
      quotaFailure(
        SurveyDef(
          survey_id = NEW_SURVEY,
          quota_limits = QuotaLimits(max_predefined_entities = 1),
          entity_datasets =
            listOf(
              EntityDatasetDef(
                id = "plots",
                rows = listOf(DataTableRow(row_key = "r1"), DataTableRow(row_key = "r2")),
              )
            ),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Predefined entity quota exceeded: survey datasets contain 2 predefined rows, exceeding " +
        "maximum of 1",
      status.description,
    )
  }

  @Test
  fun tooManySurveyAreaVertices() = runTest {
    val status =
      quotaFailure(
        SurveyDef(
          survey_id = NEW_SURVEY,
          quota_limits = QuotaLimits(max_area_vertices = 3),
          map_config =
            MapConfig(
              survey_area =
                SurveyArea(boundary = GeometryProtos.multiPolygon(listOf(listOf(square(4)))))
            ),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Survey area vertex quota exceeded: survey area has 4 vertices, exceeding maximum of 3",
      status.description,
    )
  }

  @Test
  fun tooManyVertices_inADatasetFeature() = runTest {
    val status =
      quotaFailure(
        SurveyDef(
          survey_id = NEW_SURVEY,
          quota_limits = QuotaLimits(max_area_vertices = 3),
          entity_datasets =
            listOf(
              EntityDatasetDef(
                id = "plots",
                display_name = "Parcels",
                rows =
                  listOf(
                    DataTableRow(
                      row_key = "r1",
                      geometry = GeometryProtos.polygon(listOf(square(4))),
                    )
                  ),
              )
            ),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Polygon vertex quota exceeded: dataset \"Parcels\" has a feature with 4 vertices, " +
        "exceeding maximum of 3",
      status.description,
    )
  }

  @Test
  fun tooManySurveys_forOneOwner() = runTest {
    val limits = QuotaLimits(max_surveys_per_owner = 1)
    createSurvey(SurveyDef(survey_id = NEW_SURVEY, owner_id = OWNER, quota_limits = limits))

    val status =
      quotaFailure(SurveyDef(survey_id = OTHER_SURVEY, owner_id = OWNER, quota_limits = limits))

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Survey quota exceeded: maximum surveys per owner reached (1)",
      status.description,
    )
  }

  @Test
  fun surveyInTrash_doesntCountTowardTheOwnersSurveys() = runTest {
    val limits = QuotaLimits(max_surveys_per_owner = 1)
    createSurvey(SurveyDef(survey_id = NEW_SURVEY, owner_id = OWNER, quota_limits = limits))

    trash(NEW_SURVEY)

    createSurvey(SurveyDef(survey_id = OTHER_SURVEY, owner_id = OWNER, quota_limits = limits))
  }

  @Test
  fun surveysOwnedByEmail_countForTheSignedInOwner_untilTrashed() = runTest {
    val limits = QuotaLimits(max_surveys_per_owner = 1)
    // Web clients name the owner by email.
    createSurvey(SurveyDef(survey_id = NEW_SURVEY, owner_id = USER.email, quota_limits = limits))

    val error =
      assertFailsWith<StatusException> {
        asCaller(USER) { createSurvey(SurveyDef(survey_id = OTHER_SURVEY, quota_limits = limits)) }
      }
    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)

    trash(NEW_SURVEY)
    asCaller(USER) { createSurvey(SurveyDef(survey_id = OTHER_SURVEY, quota_limits = limits)) }
  }

  @Test
  fun administrators_canOwnAnyNumberOfSurveys() = runTest {
    val limits = QuotaLimits(max_surveys_per_owner = 1)
    asCaller(ADMIN) {
      createSurvey(SurveyDef(survey_id = NEW_SURVEY, quota_limits = limits))
      createSurvey(SurveyDef(survey_id = OTHER_SURVEY, quota_limits = limits))
      createSurvey(SurveyDef(survey_id = THIRD_SURVEY, quota_limits = limits))
    }
  }

  @Test
  fun regularUsers_areStillLimited_whenAnAdministratorIsNot() = runTest {
    val limits = QuotaLimits(max_surveys_per_owner = 1)
    asCaller(USER) { createSurvey(SurveyDef(survey_id = NEW_SURVEY, quota_limits = limits)) }

    val error =
      assertFailsWith<StatusException> {
        asCaller(USER) { createSurvey(SurveyDef(survey_id = OTHER_SURVEY, quota_limits = limits)) }
      }
    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
  }

  @Test
  fun trashingASurvey_dropsItsWorkingCopy() = runTest {
    createSurvey(SurveyDef(survey_id = NEW_SURVEY, owner_id = OWNER, title = "Saved"))
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = NEW_SURVEY,
        working_copy = true,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Unsaved edit"))),
      )
    )

    trash(NEW_SURVEY)
    // Restore it so the working copy can be read again.
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = NEW_SURVEY,
        mutations = listOf(SurveyMutation(delete = SurveyDef(trash_time = ofEpochSecond(1L, 0L)))),
      )
    )

    val workingCopy =
      backend
        .GetSurvey(
          GetSurveyRequest(survey_id = NEW_SURVEY, working_copy = GetSurveyRequest.WorkingCopy())
        )
        .survey
    assertNull(workingCopy)
  }

  private suspend fun trash(surveyId: String) {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = surveyId,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(trash_time = ofEpochSecond(1L, 0L)))),
      )
    )
  }

  private suspend fun <T> asCaller(caller: Caller, block: suspend () -> T): T =
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      block()
    }

  private companion object {
    val NEW_SURVEY = testId("design-quota-survey-1")
    val OTHER_SURVEY = testId("design-quota-survey-2")
    val THIRD_SURVEY = testId("design-quota-survey-3")
    const val OWNER = "uid-quota-owner"
    val USER =
      Caller(userId = "uid-quota-user", email = "quota.user@example.org", emailVerified = true)
    /** An administrator named by the deployment. */
    val ADMIN =
      Caller(
        userId = "uid-quota-admin",
        email = "quota.admin@example.org",
        emailVerified = true,
        isAdmin = true,
      )
  }
}
