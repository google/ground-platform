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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.EntityType
import openforis.collectone.v1.survey.GeometryStyle
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SamplingDesign
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.server.service.ServiceTestSeed.FORM
import org.openforis.collectone.v2.server.service.ServiceTestSeed.OTHER_SURVEY
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY_TITLE
import org.openforis.collectone.v2.server.service.ServiceTestSeed.WORKING_COPY_TITLE

/**
 * Behavior of the survey RPCs ([SurveyServiceImpl]) on [ServiceTestSeed]: overviews, saved
 * revisions and working copies, deltas, and the design and membership quotas.
 */
open class SurveyServiceImplTest {
  /** A backend loaded with [seed]; overridden to test other stores. */
  protected open fun newBackend(seed: UserDataSeed): UserDataBackend = inMemoryBackend(seed)

  private val backend by lazy { newBackend(ServiceTestSeed.seed) }

  private suspend fun savedSurvey(): SurveyDef? =
    backend.GetSurvey(GetSurveyRequest(survey_id = SURVEY)).survey

  private suspend fun workingCopy(): SurveyDef? =
    backend
      .GetSurvey(
        GetSurveyRequest(survey_id = SURVEY, working_copy = GetSurveyRequest.WorkingCopy())
      )
      .survey

  private suspend fun editWorkingCopyTitle(title: String) =
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = SURVEY,
        working_copy = true,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = title))),
      )
    )

  private suspend fun saveWorkingCopy() =
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = SURVEY,
        working_copy = true,
        mutations = listOf(SurveyMutation(save_working_copy = true)),
      )
    )

  private suspend fun createSurvey(survey: SurveyDef, acl: SurveyAcl? = null) =
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = survey.survey_id,
        mutations = listOf(SurveyMutation(upsert = survey, acl_upsert = acl)),
      )
    )

  private suspend fun quotaFailure(survey: SurveyDef): StatusException =
    assertFailsWith<StatusException> { createSurvey(survey) }

  @Test
  fun listSurveyOverviews_fullSync_returnsSurveysInCommitOrder() = runTest {
    val overviews = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews

    assertEquals(listOf(SURVEY, OTHER_SURVEY), overviews.map { it.survey_id })
  }

  @Test
  fun listSurveyOverviews_flagsSurveysWithAWorkingCopy() = runTest {
    val overviews = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews

    assertEquals(
      mapOf(SURVEY to true, OTHER_SURVEY to false),
      overviews.associate { it.survey_id to it.has_working_copy },
    )
  }

  @Test
  fun getSurvey_returnsTheSavedRevision() = runTest {
    val survey = savedSurvey()

    assertEquals(SURVEY_TITLE, survey?.title)
    assertEquals(listOf(FORM), survey?.forms?.map { it.form_id })
  }

  @Test
  fun getSurvey_withWorkingCopy_returnsTheWorkingCopy() = runTest {
    assertEquals(WORKING_COPY_TITLE, workingCopy()?.title)
  }

  @Test
  fun mutateSurvey_commitsAfterTheCurrentWatermark() = runTest {
    val watermark = backend.currentWatermark()

    val response = editWorkingCopyTitle("Draft")

    assertTrue(response.commit_timestamp!! > watermark)
  }

  @Test
  fun workingCopyEdit_updatesTheWorkingCopy() = runTest {
    editWorkingCopyTitle("Draft")

    assertEquals("Draft", workingCopy()?.title)
  }

  @Test
  fun workingCopyEdit_leavesTheSavedRevisionUnchanged() = runTest {
    editWorkingCopyTitle("Draft")

    assertEquals(SURVEY_TITLE, savedSurvey()?.title)
  }

  @Test
  fun saveWorkingCopy_promotesItToTheSavedRevision() = runTest {
    editWorkingCopyTitle("Updated title")

    saveWorkingCopy()

    assertEquals("Updated title", savedSurvey()?.title)
  }

  @Test
  fun saveWorkingCopy_removesTheWorkingCopy() = runTest {
    saveWorkingCopy()

    assertNull(workingCopy())
  }

  @Test
  fun listSurveyOverviews_delta_returnsTheSavedSurveyWithoutAWorkingCopy() = runTest {
    val token = backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).next_sync_token
    editWorkingCopyTitle("Updated title")
    saveWorkingCopy()

    val delta = backend.ListSurveyOverviews(ListSurveyOverviewsRequest(sync_token = token))

    assertEquals(listOf(SURVEY), delta.overviews.map { it.survey_id })
    assertEquals("Updated title", delta.overviews.single().title)
    assertEquals(false, delta.overviews.single().has_working_copy)
  }

  @Test
  fun listSurveyOverviews_includesTheSurveysQuotaLimits() = runTest {
    val surveyId = testId("survey-with-limits")
    val limits = QuotaLimits(max_forms_per_survey = 1, max_generated_entities = 50)
    createSurvey(SurveyDef(survey_id = surveyId, title = "Limited", quota_limits = limits))

    val overview =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews.single {
        it.survey_id == surveyId
      }

    assertEquals(limits, overview.quota_limits)
  }

  @Test
  fun mutateSurvey_withMoreFormsThanTheQuota_failsWithResourceExhausted() = runTest {
    val error =
      quotaFailure(
        SurveyDef(
          survey_id = testId("survey-too-many-forms"),
          title = "Too many forms",
          quota_limits = QuotaLimits(max_forms_per_survey = 2),
          forms = listOf(FormDef(form_id = "f1"), FormDef(form_id = "f2"), FormDef(form_id = "f3")),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
    assertTrue(error.message!!.contains("Form quota exceeded"))
  }

  @Test
  fun mutateSurvey_withMoreGeneratedFeaturesThanTheQuota_failsWithResourceExhausted() = runTest {
    val error =
      quotaFailure(
        SurveyDef(
          survey_id = testId("survey-too-many-generated-features"),
          title = "Too many generated features",
          quota_limits = QuotaLimits(max_generated_entities = 50),
          entity_datasets =
            listOf(
              EntityDatasetDef(id = "sample-plots", sampling = SamplingDesign(feature_count = 75L))
            ),
        )
      )

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
    assertTrue(error.message!!.contains("Generated entity quota exceeded"))
  }

  @Test
  fun mutateSurvey_withPhotoQuestionsWithinTheQuota_succeeds() = runTest {
    val surveyId = testId("survey-one-photo-question")
    val photoForm =
      FormDef(
        form_id = "f1",
        model =
          ModelDef(
            bindings =
              listOf(
                FieldBinding(field_path = "/data/description", type = DataType.TYPE_STRING),
                FieldBinding(field_path = "/data/tree_photo", type = DataType.TYPE_BINARY),
              )
          ),
        view =
          ViewDef(
            components =
              listOf(
                ViewComponent(
                  control =
                    ControlDef(field_ref = "/data/description", type = ControlType.CONTROL_INPUT)
                ),
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/tree_photo",
                      type = ControlType.CONTROL_UPLOAD,
                      media_type = "image/*",
                    )
                ),
              )
          ),
      )

    createSurvey(
      SurveyDef(
        survey_id = surveyId,
        title = "One photo question",
        quota_limits = QuotaLimits(max_photo_questions_per_form = 1),
        forms = listOf(photoForm),
      )
    )

    assertEquals(
      listOf("f1"),
      backend.GetSurvey(GetSurveyRequest(survey_id = surveyId)).survey?.forms?.map { it.form_id },
    )
  }

  @Test
  fun acceptSurveyInvite_beyondTheMembershipQuota_failsWithResourceExhausted() = runTest {
    val email = "invited-collector@example.org"
    val userId = "uid-invited-collector"
    val joined = testId("survey-already-joined")
    val invited = testId("survey-invited-to")
    createSurvey(
      SurveyDef(survey_id = joined, title = "Joined"),
      SurveyAcl(
        survey_id = joined,
        entries =
          listOf(
            AclEntry(
              user_id = userId,
              email = email,
              role = Role.DATA_COLLECTOR,
              invitation_status = AclEntry.InvitationStatus.ACCEPTED,
            )
          ),
      ),
    )
    createSurvey(
      SurveyDef(
        survey_id = invited,
        title = "Invited",
        quota_limits = QuotaLimits(max_surveys_per_user = 1),
      ),
      SurveyAcl(
        survey_id = invited,
        entries =
          listOf(
            AclEntry(
              email = email,
              role = Role.DATA_COLLECTOR,
              invitation_status = AclEntry.InvitationStatus.PENDING,
              invite_token = "invite-token",
            )
          ),
      ),
    )

    val error =
      assertFailsWith<StatusException> {
        backend.AcceptSurveyInvite(
          AcceptSurveyInviteRequest(
            survey_id = invited,
            invite_token = "invite-token",
            user_id = userId,
          )
        )
      }

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.status.code)
    assertTrue(error.message!!.contains("Survey membership quota exceeded"))
  }

  @Test
  fun surveyOverviews_populateThumbnailFeaturesWithLayerColorsCappedAt150() = runTest {
    val surveyId = testId("survey-thumbnail-features")
    seedMangroveThumbnailSurvey(surveyId)

    val listed =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews.single {
        it.survey_id == surveyId
      }
    assertEquals(150, listed.thumbnail_features.size)
    assertTrue(listed.thumbnail_features.all { it.color_hex == 0xFF00897BL })
    assertTrue(listed.thumbnail_features.none { it.entity_id == testId("hidden-1") })
    assertTrue(listed.thumbnail_features.none { it.entity_id == testId("row-no-geo") })

    val batched =
      backend
        .BatchGetSurveyOverviews(BatchGetSurveyOverviewsRequest(survey_ids = listOf(surveyId)))
        .results
        .single()
        .overview!!
    assertEquals(listed.thumbnail_features, batched.thumbnail_features)

    val searched =
      backend.SearchPublicSurveys(SearchPublicSurveysRequest(query = "mangrove")).overviews.single {
        it.survey_id == surveyId
      }
    assertEquals(listed.thumbnail_features, searched.thumbnail_features)
  }

  @Test
  fun surveyOverviews_fallBackToWorkingCopyGeospatialDatasetsWhenLiveEntitiesEmpty() = runTest {
    val surveyId = testId("survey-draft-dataset-thumbnails")
    createSurvey(
      SurveyDef(
        survey_id = surveyId,
        title = "Draft survey with working-copy plots",
        entity_count = 0,
      )
    )
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = surveyId,
        working_copy = true,
        mutations =
          listOf(
            SurveyMutation(
              upsert =
                SurveyDef(
                  survey_id = surveyId,
                  title = "Draft survey with working-copy plots",
                  entity_datasets =
                    listOf(
                      EntityDatasetDef(
                        id = "sample-plots",
                        type = EntityType.GEOSPATIAL,
                        default_style = GeometryStyle(color = "#00897B"),
                        rows =
                          listOf(
                            DataTableRow(
                              row_key = "plot-1",
                              geometry =
                                GeometryProtos.polygon(
                                  listOf(
                                    listOf(
                                      GeometryProtos.position(-1.30, 36.80),
                                      GeometryProtos.position(-1.30, 36.81),
                                      GeometryProtos.position(-1.29, 36.81),
                                      GeometryProtos.position(-1.29, 36.80),
                                    )
                                  )
                                ),
                            ),
                            DataTableRow(
                              row_key = "plot-2",
                              geometry = GeometryProtos.point(-1.28, 36.82),
                            ),
                          ),
                      )
                    ),
                )
            )
          ),
      )
    )

    val overview =
      backend.ListSurveyOverviews(ListSurveyOverviewsRequest()).overviews.single {
        it.survey_id == surveyId
      }
    assertEquals(2, overview.entity_count)
    assertEquals(listOf("plot-1", "plot-2"), overview.thumbnail_features.map { it.entity_id })
    assertTrue(overview.thumbnail_features.all { it.color_hex == 0xFF00897BL })
  }

  private suspend fun seedMangroveThumbnailSurvey(surveyId: String) {
    createSurvey(
      SurveyDef(
        survey_id = surveyId,
        title = "Mangrove catalog survey",
        map_config =
          MapConfig(
            layers =
              listOf(
                LayerDef(
                  id = "layer-plots",
                  entity_dataset_id = "plots",
                  visible_by_default = true,
                  default_style = GeometryStyle(color = "#00897B"),
                ),
                LayerDef(
                  id = "layer-hidden",
                  entity_dataset_id = "hidden",
                  visible_by_default = false,
                  default_style = GeometryStyle(color = "#AD1457"),
                ),
              )
          ),
      ),
      SurveyAcl(survey_id = surveyId, sharing_policy = SharingPolicy.PUBLIC),
    )

    val visibleMutations =
      (0 until 155).map { idx ->
        pointEntityMutation(
          mutationId = testId("mut-vis-$idx"),
          surveyId = surveyId,
          entityId = testId("plot-$idx"),
          datasetId = "plots",
          layerId = "layer-plots",
          lat = 9.8200 + idx * 0.0001,
          lng = 106.3400,
        )
      }
    val extraMutations =
      listOf(
        pointEntityMutation(
          mutationId = testId("mut-hidden"),
          surveyId = surveyId,
          entityId = testId("hidden-1"),
          datasetId = "hidden",
          layerId = "layer-hidden",
          lat = 9.81,
          lng = 106.33,
        ),
        DataMutation(
          mutation_id = testId("mut-tabular"),
          entity_mutation =
            EntityMutation(
              entity_id = testId("row-no-geo"),
              dataset_id = "plots",
              upsert =
                EntityRecord(
                  entity_id = testId("row-no-geo"),
                  survey_id = surveyId,
                  dataset_id = "plots",
                  layer_id = "layer-plots",
                  label = "Row without geometry",
                ),
            ),
        ),
      )

    backend.MutateData(
      MutateDataRequest(survey_id = surveyId, mutations = visibleMutations + extraMutations)
    )
  }

  private fun pointEntityMutation(
    mutationId: String,
    surveyId: String,
    entityId: String,
    datasetId: String,
    layerId: String,
    lat: Double,
    lng: Double,
  ): DataMutation =
    DataMutation(
      mutation_id = mutationId,
      entity_mutation =
        EntityMutation(
          entity_id = entityId,
          dataset_id = datasetId,
          upsert =
            EntityRecord(
              entity_id = entityId,
              survey_id = surveyId,
              dataset_id = datasetId,
              layer_id = layerId,
              label = entityId,
              geometry = GeometryProtos.point(lat, lng),
            ),
        ),
    )
}
