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
package org.openforis.collectone.v2.workbench.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SyncStatus

/**
 * The workbench's upload and download simulators change the right record or survey and nothing
 * else, keeping an entity's status consistent with its submissions.
 */
class WorkbenchSyncSimulationTest {
  private val repository = SurveyRepositoryImpl(seededStore())
  private val sampleEntities = runNow { repository.getEntities() }
  private val surveys = runNow { repository.getSurveys() }

  /** A sample entity with two submissions in [first] and [second] status, itself [status]. */
  private fun entity(
    id: String,
    status: SyncStatus,
    first: SyncStatus,
    second: SyncStatus,
  ): GeospatialEntityItem {
    val template = sampleEntities.first { it.submissions.isNotEmpty() }
    val submission = template.submissions.first()
    return template.copy(
      id = id,
      syncStatus = status,
      submissions =
        listOf(
          submission.copy(id = "$id-a", formTitle = "Form A", syncStatus = first),
          submission.copy(id = "$id-b", formTitle = "Form B", syncStatus = second),
        ),
    )
  }

  private fun standalone(id: String, status: SyncStatus): SubmissionPreviewItem =
    sampleEntities
      .first { it.submissions.isNotEmpty() }
      .submissions
      .first()
      .copy(id = id, entityId = "", formTitle = "Field log", syncStatus = status)

  @Test
  fun entitySynced_marksItsSubmissionsSynced_andLeavesOtherEntities() {
    val other = entity("e2", SyncStatus.FAILED, SyncStatus.FAILED, SyncStatus.UPLOADING)
    val entities =
      listOf(entity("e1", SyncStatus.FAILED, SyncStatus.FAILED, SyncStatus.UPLOADING), other)

    val updated = assertNotNull(entities.withEntitySyncStatus("e1", SyncStatus.SYNCED))

    assertEquals(SyncStatus.SYNCED, updated[0].syncStatus)
    assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.SYNCED), updated[0].submissions.statuses())
    assertSame(other, updated[1])
  }

  @Test
  fun entityFailedOrUploading_keepsItsSubmissions() {
    val entities = listOf(entity("e1", SyncStatus.SYNCED, SyncStatus.SYNCED, SyncStatus.UPLOADING))

    val updated = assertNotNull(entities.withEntitySyncStatus("e1", SyncStatus.FAILED))

    assertEquals(SyncStatus.FAILED, updated[0].syncStatus)
    assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.UPLOADING), updated[0].submissions.statuses())
  }

  @Test
  fun unknownEntity_changesNothing() {
    val entities = listOf(entity("e1", SyncStatus.SYNCED, SyncStatus.SYNCED, SyncStatus.SYNCED))
    assertNull(entities.withEntitySyncStatus("missing", SyncStatus.FAILED))
  }

  @Test
  fun submissionStatus_isDerivedIntoItsEntity() {
    val entities = listOf(entity("e1", SyncStatus.SYNCED, SyncStatus.SYNCED, SyncStatus.SYNCED))

    val failed = withSubmissionSyncStatus(entities, emptyList(), "e1-b", SyncStatus.FAILED)
    assertEquals(SyncStatus.FAILED, failed.entities[0].syncStatus)
    assertEquals(
      listOf(SyncStatus.SYNCED, SyncStatus.FAILED),
      failed.entities[0].submissions.statuses(),
    )
    assertEquals("Form B", failed.formTitle)
    // Not a standalone submission: nothing to save there.
    assertNull(failed.standaloneSubmissions)
  }

  @Test
  fun lastSubmissionSynced_marksTheEntitySynced_evenIfItWasFailed() {
    val entities = listOf(entity("e1", SyncStatus.FAILED, SyncStatus.SYNCED, SyncStatus.FAILED))

    val change = withSubmissionSyncStatus(entities, emptyList(), "e1-b", SyncStatus.SYNCED)

    assertEquals(SyncStatus.SYNCED, change.entities[0].syncStatus)
  }

  @Test
  fun anotherSubmissionStillUploading_keepsTheEntityUploading() {
    val entities =
      listOf(entity("e1", SyncStatus.UPLOADING, SyncStatus.UPLOADING, SyncStatus.FAILED))

    val change = withSubmissionSyncStatus(entities, emptyList(), "e1-b", SyncStatus.SYNCED)

    assertEquals(SyncStatus.UPLOADING, change.entities[0].syncStatus)
  }

  @Test
  fun standaloneSubmission_changesOnlyTheStandaloneList() {
    val entities = listOf(entity("e1", SyncStatus.SYNCED, SyncStatus.SYNCED, SyncStatus.SYNCED))
    val logs =
      listOf(standalone("log-1", SyncStatus.UPLOADING), standalone("log-2", SyncStatus.SYNCED))

    val change = withSubmissionSyncStatus(entities, logs, "log-1", SyncStatus.FAILED)

    assertEquals(entities, change.entities)
    assertEquals(
      listOf(SyncStatus.FAILED, SyncStatus.SYNCED),
      assertNotNull(change.standaloneSubmissions).statuses(),
    )
    assertEquals("Field log", change.formTitle)
  }

  @Test
  fun unknownSubmission_hasNoTitle_soNoNotice() {
    val entities = listOf(entity("e1", SyncStatus.SYNCED, SyncStatus.SYNCED, SyncStatus.SYNCED))

    val change = withSubmissionSyncStatus(entities, emptyList(), "missing", SyncStatus.FAILED)

    assertEquals(entities, change.entities)
    assertNull(change.standaloneSubmissions)
    assertNull(change.formTitle)
  }

  @Test
  fun downloadToggle_flipsOnlyThatSurvey() {
    val target = surveys.first()

    val (updated, toggled) = surveys.withDownloadToggled(target.id)

    assertEquals(!target.isDownloaded, assertNotNull(toggled).isDownloaded)
    assertEquals(surveys.drop(1), updated.drop(1))
    assertNull(surveys.withDownloadToggled("missing").second)
  }

  @Test
  fun details_replaceTheActiveSurveys_butBlankKeepsTheCurrentValue() {
    val active = surveys.first()

    val renamed = surveys.withDetails(active.id, title = "Renamed", description = " ")

    assertEquals("Renamed", renamed.first().title)
    assertEquals(active.description, renamed.first().description)
    assertEquals(surveys.drop(1), renamed.drop(1))
  }

  private fun List<SubmissionPreviewItem>.statuses() = map { it.syncStatus }
}
