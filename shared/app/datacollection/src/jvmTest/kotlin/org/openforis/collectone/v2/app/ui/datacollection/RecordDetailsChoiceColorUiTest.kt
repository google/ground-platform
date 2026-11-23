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
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.core.forms.ui.AppTheme

class RecordDetailsChoiceColorUiTest {

  private val noOpEntityActions =
    object : EntityDetailsActions {
      override fun selectEntity(entityId: String?) = Unit

      override fun showDatasetInTable(datasetId: String) = Unit

      override fun toggleNavigationToEntity(entityId: String) = Unit

      override fun openUploadsForEntity(entityId: String) = Unit

      override fun showEntityProperties() = Unit

      override fun selectSubmissionDetail(submissionId: String?) = Unit

      override fun launchFormForEntity(entityId: String, formId: String) = Unit

      override fun cycleEntitySyncStatus(entityId: String) = Unit

      override fun cycleSubmissionSyncStatus(submissionId: String) = Unit

      override fun openEntityQrCode(entityId: String) = Unit

      override fun shareEntityPdf(entityId: String) = Unit

      override fun shareSubmissionPdf(submissionId: String) = Unit
    }

  @Test
  fun submissionFullDetailsCard_rendersColorPillsForColorCodedChoiceFields() = runComposeUiTest {
    val submission =
      SubmissionPreviewItem(
        id = "sub-1",
        entityId = "plot-1",
        formId = "form-1",
        formTitle = "Land Use Verification",
        formVersion = "1",
        collectorName = "Dr. Amina Osei",
        collectorEmail = "amina@example.org",
        fields =
          listOf(
            SubmissionFieldEntry(
              questionName = "land_use",
              questionLabel = "Primary Land Use Class",
              answerValue = "primary_forest (Primary / Intact Natural Forest)",
            ),
            SubmissionFieldEntry(
              questionName = "notes",
              questionLabel = "Field Notes",
              answerValue = "Dense closed canopy",
            ),
          ),
      )

    setContent {
      AppTheme {
        SubmissionFullDetailsCard(
          uiState =
            SubmissionDetailsUiState(
              submission = submission,
              viewer = SubmissionViewer(email = "amina@example.org", isOrganizer = true),
            ),
          backLabel = "Back",
          onBack = {},
          onSharePdf = {},
        )
      }
    }

    onNodeWithTag(
        "choice-color-pill-primary_forest (Primary / Intact Natural Forest)",
        useUnmergedTree = true,
      )
      .assertExists()
    onNodeWithText("Dense closed canopy").assertExists()
    onNodeWithTag("choice-color-pill-Dense closed canopy", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun entityPropertiesPane_rendersColorPillsForColorCodedEntityProperties() = runComposeUiTest {
    val entity =
      GeospatialEntityItem(
        id = "plot-1",
        label = "Plot 1",
        datasetId = "sample_plots",
        datasetName = "Sample Plots",
        layerId = "sample_plots",
        geoId = "",
        geometry = null,
        areaHectares = 0.0,
        perimeterMeters = 0,
        colorHex = 0xFF2E7D32L,
        properties =
          mapOf(
            "land_use" to "forest_land (Forest land)",
            "elevation_m" to "1820",
          ),
        submissions = emptyList(),
      )

    setContent {
      AppTheme {
        EntityPropertiesPane(
          uiState =
            EntityDetailsUiState(
              entity = entity,
              viewer = SubmissionViewer(email = "amina@example.org", isOrganizer = true),
            ),
          actions = noOpEntityActions,
          isWeb = true,
        )
      }
    }

    onNodeWithTag("choice-color-pill-forest_land (Forest land)", useUnmergedTree = true)
      .assertExists()
    onNodeWithText("1820").assertExists()
    onNodeWithTag("choice-color-pill-1820", useUnmergedTree = true).assertDoesNotExist()
  }
}
