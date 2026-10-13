/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.web.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import org.groundplatform.v2.app.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.workbench.ui.WorkbenchState

/** [WebDashboardPageTest] cases that run on the combined prototype state. */
class WebDashboardPageWithPrototypeTest {

  private val state = WebAppState()
  private val entity = state.entities.first { it.submissions.isNotEmpty() }
  private val datasetEntities = state.entities.filter { it.datasetId == entity.datasetId }

  private fun buildTables(selectedEntityId: String? = entity.id) =
    buildDashboardDataTables(
      entities = state.entities,
      selectedEntityId = selectedEntityId,
      relatedLabel = { e, value -> state.relatedEntityForPropertyValue(e, value)?.label },
    )

  private fun layerTable() = buildTables().first { it.datasetId == entity.datasetId }

  @Test
  fun updateActiveSurveyDetails_updatesActiveSurveyTitleAndDescription() {
    val testState = WebAppState()
    WorkbenchState(MobileAppState(dataHolder = testState.dataHolder))
      .updateActiveSurveyDetails(
        title = "Updated Survey Title",
        description = "Updated survey description from the survey editor.",
      )

    assertEquals("Updated Survey Title", testState.activeSurvey.title)
    assertEquals(
      "Updated survey description from the survey editor.",
      testState.activeSurvey.description,
    )
  }
}
