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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.core.forms.ui.AppTheme

/** The success screen after a submission, and the upload status icons on the saved feature. */
@OptIn(ExperimentalTestApi::class)
class SubmissionFlowUiTest {

  private val noOpEntityActions =
    object : EntityDetailsActions {
      override fun selectEntity(entityId: String?) = Unit

      override fun showDatasetInTable(datasetId: String) = Unit

      override fun toggleNavigationToEntity(entityId: String) = Unit

      override fun openUploadsForEntity(entityId: String) = Unit

      override fun showEntityProperties() = Unit

      override fun showEntitySubmissions() = Unit

      override fun selectSubmissionDetail(submissionId: String?) = Unit

      override fun launchFormForEntity(entityId: String, formId: String) = Unit

      override fun cycleEntitySyncStatus(entityId: String) = Unit

      override fun cycleSubmissionSyncStatus(submissionId: String) = Unit

      override fun openEntityQrCode(entityId: String) = Unit

      override fun shareEntityPdf(entityId: String) = Unit

      override fun shareSubmissionPdf(submissionId: String) = Unit
    }

  /** A feature whose `crop` was written by a submission still waiting to upload. */
  private val entity =
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
      properties = mapOf("crop" to "Coffee", "owner" to "Imported owner"),
      submissions =
        listOf(
          SubmissionPreviewItem(
            id = "sub-1",
            entityId = "plot-1",
            formId = "form-1",
            formTitle = "Plot visit",
            formVersion = "1",
            collectorName = "Dr. Amina Osei",
            collectorEmail = "amina@example.org",
            fields =
              listOf(
                SubmissionFieldEntry(
                  questionName = "crop",
                  questionLabel = "Crop",
                  answerValue = "Coffee",
                )
              ),
            syncStatus = SyncStatus.UPLOADING,
          )
        ),
    )

  private val details =
    EntityDetailsUiState(
      entity = entity,
      viewer = SubmissionViewer(email = "amina@example.org", isOrganizer = true),
    )

  @Test
  fun successScreen_confirmsTheDataWasSaved_andContinues() = runComposeUiTest {
    var continued = 0
    setContent { AppTheme { SubmissionSuccessScreen(onContinue = { continued++ }) } }

    onNodeWithText("Data collection complete").assertExists()
    onNodeWithText("saved on your device", substring = true).assertExists()
    onNodeWithText("Continue").performClick()

    assertEquals(1, continued)
  }

  @Test
  fun mobileProperties_showTheUploadStatusOfFieldsASubmissionWrote() = runComposeUiTest {
    setContent {
      AppTheme {
        EntityPropertiesPane(uiState = details, actions = noOpEntityActions, isWeb = false)
      }
    }

    // Only `crop` was written by a submission; `owner` is an imported value.
    onNodeWithContentDescription("Uploading", useUnmergedTree = true).assertExists()
    assertEquals(
      1,
      onAllNodesWithTag(FIELD_SYNC_STATUS_ICON_TAG, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size,
    )
  }

  @Test
  fun webProperties_showNoFieldUploadStatus() = runComposeUiTest {
    setContent {
      AppTheme {
        EntityPropertiesPane(uiState = details, actions = noOpEntityActions, isWeb = true)
      }
    }

    assertEquals(
      0,
      onAllNodesWithTag(FIELD_SYNC_STATUS_ICON_TAG, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size,
    )
  }
}
