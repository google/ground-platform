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

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus

class PropertySyncStatusesTest {

  private fun submission(id: String, syncStatus: SyncStatus, vararg questions: String) =
    SubmissionPreviewItem(
      id = id,
      entityId = "plot-1",
      formId = "form-1",
      formTitle = "Plot visit",
      formVersion = "1",
      collectorName = "Amina",
      collectorEmail = "amina@example.org",
      fields =
        questions.map {
          SubmissionFieldEntry(questionName = it, questionLabel = it, answerValue = "x")
        },
      syncStatus = syncStatus,
    )

  @Test
  fun withoutSubmissions_noPropertyHasAStatus() {
    assertEquals(
      emptyMap(),
      propertySyncStatuses(
        mapOf("crop" to "coffee"),
        submissions = emptyList(),
        formDefs = emptyList(),
      ),
    )
  }

  @Test
  fun propertiesNamedLikeAnsweredQuestions_takeTheirSubmissionsStatus() {
    val statuses =
      propertySyncStatuses(
        properties = mapOf("crop" to "coffee", "owner" to "Imported", "trees" to "12"),
        submissions =
          listOf(submission("s1", SyncStatus.UPLOADING, "crop", "group/trees", "notes")),
        formDefs = emptyList(),
      )

    // `owner` was never answered, and `notes` isn't a property of the feature.
    assertEquals(mapOf("crop" to SyncStatus.UPLOADING, "trees" to SyncStatus.UPLOADING), statuses)
  }

  @Test
  fun theLatestSubmissionThatWroteAProperty_decidesItsStatus() {
    val statuses =
      propertySyncStatuses(
        properties = mapOf("crop" to "cocoa", "trees" to "12"),
        // Newest first.
        submissions =
          listOf(
            submission("s2", SyncStatus.UPLOADING, "crop"),
            submission("s1", SyncStatus.SYNCED, "crop", "trees"),
          ),
        formDefs = emptyList(),
      )

    assertEquals(mapOf("crop" to SyncStatus.UPLOADING, "trees" to SyncStatus.SYNCED), statuses)
  }

  @Test
  fun questionsSavedToAProperty_markThatPropertyNotTheirOwnName() {
    val formDef =
      FormDef(
        model =
          ModelDef(
            bindings = listOf(FieldBinding(field_path = "/data/main_crop", entity_saveto = "crop"))
          )
      )

    val statuses =
      propertySyncStatuses(
        properties = mapOf("crop" to "coffee", "main_crop" to "Imported"),
        submissions = listOf(submission("s1", SyncStatus.FAILED, "main_crop")),
        formDefs = listOf(formDef),
      )

    assertEquals(mapOf("crop" to SyncStatus.FAILED), statuses)
  }
}
