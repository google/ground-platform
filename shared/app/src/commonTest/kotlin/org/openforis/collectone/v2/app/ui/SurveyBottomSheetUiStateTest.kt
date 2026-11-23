/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.datacollection.SurveyBottomSheetUiState
import org.openforis.collectone.v2.app.ui.session.TestAppSession
import org.openforis.collectone.v2.app.ui.session.surveyBottomSheetUiState

/** What the survey's bottom sheet shows: the searchable list, a map feature, or a submission. */
class SurveyBottomSheetUiStateTest {
  private val session = TestAppSession(sampleDataHolder())

  @Test
  fun nothingSelected_showsTheSearchableList() {
    val sheet =
      assertIs<SurveyBottomSheetUiState.SearchableList>(
        session.surveyRecords.surveyBottomSheetUiState(session.viewModels.surveyMap)
      )

    assertEquals(5, sheet.list.entityGroups.sumOf { it.entities.size })
  }

  @Test
  fun aSelectedMapFeature_showsItsDetails() {
    session.viewModels.surveyMap.selectEntity(NYR_104)

    val sheet =
      assertIs<SurveyBottomSheetUiState.EntityDetails>(
        session.surveyRecords.surveyBottomSheetUiState(session.viewModels.surveyMap)
      )
    assertEquals(NYR_104, sheet.details.entity.id)
  }

  @Test
  fun aSelectedStandaloneSubmission_showsTheSubmission() {
    val submission = session.viewModels.dataCollection.uiState.value.standaloneSubmissions.first()

    session.viewModels.dataCollection.selectSubmissionDetail(submission.id)

    val sheet =
      assertIs<SurveyBottomSheetUiState.SubmissionDetails>(
        session.surveyRecords.surveyBottomSheetUiState(session.viewModels.surveyMap)
      )
    assertEquals(submission.id, sheet.details.submission.id)
  }

  @Test
  fun selectingALayer_clearsTheSelectedMapFeatureAndPlace() {
    session.searchableListActions.selectPlace("place-karima-forest")
    session.viewModels.surveyMap.selectEntity(NYR_104)

    session.searchableListActions.selectLayer(
      session.viewModels.dataCollection.uiState.value.entities.first().datasetId
    )

    assertIs<SurveyBottomSheetUiState.SearchableList>(
      session.surveyRecords.surveyBottomSheetUiState(session.viewModels.surveyMap)
    )
    assertEquals(null, session.viewModels.surveyMap.uiState.value.selectedPlace)
  }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
  }
}
