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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation

/**
 * The quotas `MutateData` enforces when it creates map features, and the `RESOURCE_EXHAUSTED`
 * messages it fails with. Each test runs on a new survey with its own `quota_limits`.
 */
class DataQuotaTest {
  private val backend = sampleBackend()

  private suspend fun createSurvey(limits: QuotaLimits) {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            SurveyMutation(
              upsert =
                SurveyDef(
                  survey_id = SURVEY,
                  quota_limits = limits,
                  entity_datasets = listOf(EntityDatasetDef(id = DATASET)),
                )
            )
          ),
      )
    )
  }

  private suspend fun createEntity(entity: EntityRecord) {
    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "create-${entity.entity_id}",
              entity_mutation =
                EntityMutation(
                  entity_id = entity.entity_id,
                  dataset_id = DATASET,
                  upsert = entity.copy(dataset_id = DATASET),
                ),
            )
          ),
      )
    )
  }

  private suspend fun quotaFailure(entity: EntityRecord): Status {
    val error = assertFailsWith<StatusException> { createEntity(entity) }
    return error.status
  }

  @Test
  fun tooManyVertices_inANewFeature() = runTest {
    createSurvey(QuotaLimits(max_area_vertices = 3))
    val points = List(4) { i -> GeoPoint(latitude = i.toDouble(), longitude = i.toDouble()) }

    val status = quotaFailure(EntityRecord(entity_id = FIRST, shape = GeoShape(points = points)))

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Polygon vertex quota exceeded: entity \"$FIRST\" has 4 vertices, exceeding maximum of 3",
      status.description,
    )
  }

  @Test
  fun tooManyFieldCreatedFeatures() = runTest {
    createSurvey(QuotaLimits(max_adhoc_entities_per_user = 1))
    createEntity(EntityRecord(entity_id = FIRST, is_field_created = true))

    val status = quotaFailure(EntityRecord(entity_id = SECOND, is_field_created = true))

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Ad-hoc entity quota exceeded: maximum ad-hoc entities per user reached (1)",
      status.description,
    )
  }

  @Test
  fun tooManyPredefinedFeatures() = runTest {
    createSurvey(QuotaLimits(max_predefined_entities = 1))
    createEntity(EntityRecord(entity_id = FIRST))

    val status = quotaFailure(EntityRecord(entity_id = SECOND))

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.code)
    assertEquals(
      "Predefined entity quota exceeded: maximum predefined entities reached (1)",
      status.description,
    )
  }

  private companion object {
    val SURVEY = testId("data-quota-survey")
    const val DATASET = "plots"
    val FIRST = testId("data-quota-entity-1")
    val SECOND = testId("data-quota-entity-2")
  }
}
