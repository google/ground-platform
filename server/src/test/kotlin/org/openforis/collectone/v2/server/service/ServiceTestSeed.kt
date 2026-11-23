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
import com.squareup.wire.ofEpochSecond
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.userdata.SurveySeed
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore

/**
 * A small, explicit seed for the service tests, so they assert on records they can see rather than
 * on the bundled sample data. Each record has its own commit timestamp, in declaration order.
 *
 * Organizations: [ORGANIZATION] and [OTHER_ORGANIZATION] are listed in the directory,
 * [UNLISTED_ORGANIZATION] is not, and [DELETED_ORGANIZATION] is a tombstone. [SURVEY] belongs to
 * [ORGANIZATION], has a working copy, three [DATASET] map features ([ENTITY_C], [ENTITY_A],
 * [ENTITY_B], committed in that order so label order differs from commit order) and three
 * submissions. [OTHER_SURVEY] is empty.
 */
object ServiceTestSeed {
  val ORGANIZATION = testId("org-forest-service")
  val OTHER_ORGANIZATION = testId("org-agroforestry-cooperative")
  val UNLISTED_ORGANIZATION = testId("org-private-lab")
  val DELETED_ORGANIZATION = testId("org-closed-trust")

  val SURVEY = testId("survey-parcels")
  val OTHER_SURVEY = testId("survey-other")
  const val SURVEY_TITLE = "Parcel baseline"
  const val WORKING_COPY_TITLE = "Parcel baseline (draft)"
  const val DATASET = "parcels"
  const val FORM = "form-baseline"

  val ENTITY_C = testId("entity-c")
  val ENTITY_A = testId("entity-a")
  val ENTITY_B = testId("entity-b")

  val SUBMISSION_1 = testId("submission-1")
  val SUBMISSION_2 = testId("submission-2")
  val SUBMISSION_3 = testId("submission-3")

  private const val BASE_EPOCH_SECOND = 1_767_225_600L // 2026-01-01T00:00:00Z

  private fun committedAt(order: Long): Instant = ofEpochSecond(BASE_EPOCH_SECOND + order, 0L)

  private fun organization(id: String, name: String, order: Long) =
    Organization(
      organization_id = id,
      name = name,
      listed = true,
      version = 1L,
      commit_timestamp = committedAt(order),
    )

  private fun entity(id: String, label: String, order: Long) =
    EntityRecord(
      entity_id = id,
      survey_id = SURVEY,
      dataset_id = DATASET,
      label = label,
      revision = 1L,
      commit_timestamp = committedAt(order),
    )

  private fun submission(id: String, entityId: String, order: Long) =
    SubmissionRecord(
      submission_id = id,
      survey_id = SURVEY,
      form_id = FORM,
      entity_id = entityId,
      revision = 1L,
      commit_timestamp = committedAt(order),
    )

  private fun survey(id: String, title: String, order: Long) =
    SurveyDef(
      survey_id = id,
      organization_id = ORGANIZATION,
      title = title,
      state = SurveyState.PUBLISHED,
      forms = listOf(FormDef(form_id = FORM)),
      record_version = 1L,
      commit_timestamp = committedAt(order),
    )

  val seed: UserDataSeed =
    UserDataSeed(
      organizations =
        listOf(
          organization(ORGANIZATION, "Forest Service", order = 1),
          organization(OTHER_ORGANIZATION, "Agroforestry Cooperative", order = 2),
          organization(UNLISTED_ORGANIZATION, "Private Lab", order = 3).copy(listed = false),
          organization(DELETED_ORGANIZATION, "Closed Trust", order = 4).copy(is_deleted = true),
        ),
      libraries =
        listOf(
          OrganizationLibraryRecord(
            organization_id = ORGANIZATION,
            bundle = LibraryBundle(),
            version = 1L,
            commit_timestamp = committedAt(5),
          ),
          OrganizationLibraryRecord(
            organization_id = OTHER_ORGANIZATION,
            bundle = LibraryBundle(),
            version = 1L,
            commit_timestamp = committedAt(6),
          ),
        ),
      surveys =
        listOf(
          SurveySeed(
            survey = survey(SURVEY, SURVEY_TITLE, order = 7),
            working_copy = survey(SURVEY, WORKING_COPY_TITLE, order = 8),
            entities =
              listOf(
                entity(ENTITY_C, "Plot C", order = 9),
                entity(ENTITY_A, "Plot A", order = 10),
                entity(ENTITY_B, "Plot B", order = 11),
              ),
            submissions =
              listOf(
                submission(SUBMISSION_1, ENTITY_C, order = 12),
                submission(SUBMISSION_2, ENTITY_A, order = 13),
                submission(SUBMISSION_3, ENTITY_B, order = 14),
              ),
          ),
          SurveySeed(survey = survey(OTHER_SURVEY, "Other survey", order = 15)),
        ),
    )
}

/** A backend over an in-memory store loaded with [seed]. */
fun inMemoryBackend(seed: UserDataSeed = ServiceTestSeed.seed): UserDataBackend =
  UserDataBackend(InMemoryUserDataStore(seed))
