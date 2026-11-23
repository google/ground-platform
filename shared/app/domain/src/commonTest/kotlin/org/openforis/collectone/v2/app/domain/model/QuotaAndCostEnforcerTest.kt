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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class QuotaAndCostEnforcerTest {
  @Test
  fun checkCanCreateSurvey_blocksOwnersAtTheirLimit_butNotPlatformAdmins() {
    val owner = "owner@example.org"
    val owned =
      List(2) { i ->
        SurveyPreviewItem(
          id = "s$i",
          title = "Survey $i",
          description = "",
          location = "",
          isDownloaded = false,
          entityCount = 0,
          ownerEmail = owner,
        )
      }
    val limits = QuotaLimitsConfig.BASIC.copy(maxSurveysPerOwner = 2)

    val forOwner =
      QuotaAndCostEnforcer.checkCanCreateSurvey(
        ownerEmail = owner,
        organizationId = null,
        existingSurveys = owned,
        globalLimits = limits,
      )
    val forAdmin =
      QuotaAndCostEnforcer.checkCanCreateSurvey(
        ownerEmail = owner,
        organizationId = null,
        existingSurveys = owned,
        globalLimits = limits,
        isPlatformAdmin = true,
      )
    // Deleted surveys leave the local list, so they stop counting.
    val afterDeleting =
      QuotaAndCostEnforcer.checkCanCreateSurvey(
        ownerEmail = owner,
        organizationId = null,
        existingSurveys = owned.drop(1),
        globalLimits = limits,
      )

    assertIs<QuotaCheckResult.Exceeded>(forOwner)
    assertEquals(QuotaCheckResult.Allowed, forAdmin)
    assertEquals(QuotaCheckResult.Allowed, afterDeleting)
  }

  @Test
  fun resolveEffectiveQuotaLimits_defaultsToSponsoredForOrganizationSurvey() {
    val personalLimits = resolveEffectiveQuotaLimits(hasOrganization = false)
    assertEquals(QuotaTier.BASIC, personalLimits.tier)
    assertEquals(1, personalLimits.maxPhotoQuestionsPerForm)

    val orgSurveyLimits = resolveEffectiveQuotaLimits(hasOrganization = true)
    assertEquals(QuotaTier.SPONSORED, orgSurveyLimits.tier)
    assertEquals(10, orgSurveyLimits.maxPhotoQuestionsPerForm)
  }

  @Test
  fun checkSurveyDesign_countsOnlyPhotoQuestionTypes_andEnforcesPolygonDatasetVertexLimits() {
    val formWithTextNamedPhoto =
      SurveyEditorForm(
        key = "f1",
        form =
          EditorForm(
            formId = "f1",
            title = "Field Assessment",
            questions =
              listOf(
                EditorQuestion(
                  key = "q1",
                  name = "photo_notes",
                  label = "Photo notes and image description",
                  type = EditorQuestionType.TEXT,
                ),
                EditorQuestion(
                  key = "q2",
                  name = "canopy_photo",
                  label = "Canopy photo",
                  type = EditorQuestionType.PHOTO,
                ),
              ),
          ),
      )

    val draft =
      SurveyEditorDraft(
        details =
          SurveyDetails(
            surveyId = "s1",
            title = "Survey 1",
            description = "",
            quotaLimits = QuotaLimitsConfig(maxPhotoQuestionsPerForm = 1, maxAreaVertices = 4),
          ),
        forms = listOf(formWithTextNamedPhoto),
        datasets = emptyList(),
        sharing = SharingSettings(ownerEmail = "owner@example.org"),
      )

    assertTrue(QuotaAndCostEnforcer.checkSurveyDesign(draft).isAllowed)

    val overVertexPolygonDataset =
      EntityDataset(
        key = "d1",
        kind = DatasetKind.MAP_LAYER,
        id = "parcels",
        displayName = "Parcels",
        geometryType = GeometryType.POLYGON,
        keyProperty = "id",
        labelProperty = "name",
        properties =
          listOf(
            EntityProperty(name = "id", label = "ID"),
            EntityProperty(name = "name", label = "Name"),
          ),
        rows =
          listOf(
            EntityRow(
              key = "r1",
              values = mapOf("id" to "p1", "name" to "Parcel 1"),
              geometry =
                listOf(
                  LatLng(0.0, 0.0),
                  LatLng(0.0, 1.0),
                  LatLng(1.0, 1.0),
                  LatLng(1.0, 0.0),
                  LatLng(0.5, -0.5),
                ),
            )
          ),
      )

    val result =
      QuotaAndCostEnforcer.checkSurveyDesign(
        draft.copy(datasets = listOf(overVertexPolygonDataset))
      )
    assertFalse(result.isAllowed)
    assertEquals(
      QuotaCountedMessage.MAX_DATASET_POLYGON_VERTICES.message(5, "Parcels", 5, 4),
      assertIs<QuotaCheckResult.Exceeded>(result).message,
    )
  }
}
