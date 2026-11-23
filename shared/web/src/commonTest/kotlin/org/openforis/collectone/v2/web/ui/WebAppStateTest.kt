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
package org.openforis.collectone.v2.web.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

class WebAppStateTest {
  private fun SurveyMap.overlayIds() =
    content.sources.first { it.id == SurveyMapContent.OVERLAY_SOURCE }.features.map { it.id }

  @Test
  fun webEntityDetailsTabs_openingSubmissionSelectsHistoryAndSwitchingTabsKeepsIt() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val entity =
      state.viewModels.dataCollection.uiState.value.entities.first { it.submissions.isNotEmpty() }
    val submission = entity.submissions.first()
    state.viewModels.surveyMap.selectEntity(entity.id)

    state.viewModels.dashboard.selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)
    assertEquals(
      EntityDetailsPane.SUBMISSIONS,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )

    // Opening a submission (e.g. from `Uploads`) lands on the `History` tab.
    state.viewModels.dashboard.selectEntityDetailsTab(EntityDetailsPane.PROPERTIES)
    state.viewModels.dataCollection.selectSubmissionDetail(submission.id)
    assertEquals(
      EntityDetailsPane.SUBMISSIONS,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )

    // Switching to `Data` and back keeps the opened submission.
    state.viewModels.dashboard.selectEntityDetailsTab(EntityDetailsPane.PROPERTIES)
    assertEquals(
      EntityDetailsPane.PROPERTIES,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
    assertEquals(submission.id, state.viewModels.dataCollection.uiState.value.selectedSubmissionId)
    state.viewModels.dashboard.selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)
    assertEquals(submission.id, state.viewModels.dataCollection.uiState.value.selectedSubmissionId)
  }

  @Test
  fun focusWebFormQuestion_issuesRequestsWithIncreasingTokens_andClearsWithTheForm() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )
    assertNull(state.viewModels.dataCollection.uiState.value.webFormFocusRequest)

    state.viewModels.dataCollection.focusWebFormQuestion("/data/plot_boundary")
    val first = assertNotNull(state.viewModels.dataCollection.uiState.value.webFormFocusRequest)
    assertEquals("/data/plot_boundary", first.path)

    // Clicking the same geometry again re-fires with a new token.
    state.viewModels.dataCollection.focusWebFormQuestion("/data/plot_boundary")
    val second = assertNotNull(state.viewModels.dataCollection.uiState.value.webFormFocusRequest)
    assertEquals(first.path, second.path)
    assertTrue(second.token > first.token)

    state.viewModels.dataCollection.consumeWebFormFocusRequest()
    assertNull(state.viewModels.dataCollection.uiState.value.webFormFocusRequest)

    state.viewModels.dataCollection.focusWebFormQuestion("/data/plot_center_gps")
    assertNotNull(state.viewModels.dataCollection.uiState.value.webFormFocusRequest)
    state.viewModels.dataCollection.closeActiveFormRunner()
    assertNull(state.viewModels.dataCollection.uiState.value.webFormFocusRequest)
  }
}
