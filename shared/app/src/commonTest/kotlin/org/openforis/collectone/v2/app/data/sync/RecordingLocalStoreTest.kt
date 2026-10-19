/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.domain.model.SyncStatus

/** Local edits through [RecordingLocalStore] become fine-grained outbox mutations. */
class RecordingLocalStoreTest {
  private val raw = seededStore()
  private val store = RecordingLocalStore(raw)
  private val surveyId = runNow {
    raw.transaction { surveys().first { entities(it.id).isNotEmpty() }.id }
  }

  private fun outbox(): List<PendingMutation> = runNow { raw.transaction { outbox() } }

  private fun firstEntity() = runNow { raw.transaction { entities(surveyId).first() } }

  @Test
  fun editingAProperty_recordsOnePropertyPatch() {
    val entity = firstEntity()
    runNow {
      store.transaction {
        upsertEntity(surveyId, entity.copy(properties = entity.properties + ("crop" to "coffee")))
      }
    }

    val pending = outbox().single()
    assertEquals(MutationTarget.Data(surveyId), pending.target)
    assertEquals(Rebaser.entityKey(entity.id), pending.recordKey)
    val update = (pending.payload as MutationPayload.Data).mutation.entity_mutation!!.update!!
    assertEquals(listOf("properties/crop"), update.patches.map { it.key })
    assertEquals("coffee", update.patches.single().property_value?.string_value)
  }

  @Test
  fun syncStatusChanges_areNotRecorded() {
    val entity = firstEntity()
    runNow {
      store.transaction { upsertEntity(surveyId, entity.copy(syncStatus = SyncStatus.FAILED)) }
    }

    assertEquals(emptyList(), outbox())
  }

  @Test
  fun newEntity_isUpserted_andDeletingItBeforeSending_cancelsBoth() {
    val entity = firstEntity().copy(id = "entity-new-1", submissions = emptyList())
    runNow { store.transaction { upsertEntity(surveyId, entity) } }

    val created = outbox().single()
    assertNotNull((created.payload as MutationPayload.Data).mutation.entity_mutation!!.upsert)

    runNow { store.transaction { deleteEntity(surveyId, entity.id) } }
    assertEquals(emptyList(), outbox())
  }

  @Test
  fun renamingAnOrganization_recordsANamePatch() {
    val org = runNow { raw.transaction { organizations().first { it.name.isNotBlank() } } }
    runNow { store.transaction { updateOrganization(org.id) { it.copy(name = "Renamed") } } }

    val pending = outbox().single()
    assertEquals(MutationTarget.Organization(org.id), pending.target)
    val patch = (pending.payload as MutationPayload.Organization).mutation.update_field!!
    assertEquals("name", patch.key)
    assertEquals("Renamed", patch.string_value)
  }

  @Test
  fun editingASurveyTitle_recordsASavedRevisionPatch() {
    runNow { store.transaction { updateSurvey(surveyId) { it.copy(title = "New title") } } }

    val pending = outbox().single()
    assertEquals(MutationTarget.Survey(surveyId, workingCopy = false), pending.target)
    val patch = (pending.payload as MutationPayload.Survey).mutation.update_field!!
    assertEquals("title", patch.key)
    assertEquals("New title", patch.string_value)
  }

  @Test
  fun togglingLayerVisibility_isNotRecorded() {
    runNow {
      store.transaction {
        putMapLayers(surveyId, mapLayers(surveyId).map { it.copy(isVisible = !it.isVisible) })
      }
    }

    assertEquals(emptyList(), outbox())
  }

  @Test
  fun nestedTransactions_recordOnce_whenTheOuterOneEnds() {
    val entity = firstEntity()
    runNow {
      store.transaction {
        store.transaction { upsertEntity(surveyId, entity.copy(label = "First")) }
        store.transaction { upsertEntity(surveyId, entity.copy(label = "Second")) }
        assertEquals(emptyList(), outbox())
      }
    }

    val update =
      (outbox().single().payload as MutationPayload.Data).mutation.entity_mutation!!.update!!
    assertEquals("Second", update.patches.single { it.key == "label" }.label_value)
  }

  @Test
  fun writesToTheUnderlyingStore_areNotRecorded() {
    val entity = firstEntity()
    runNow { raw.transaction { upsertEntity(surveyId, entity.copy(label = "Downloaded")) } }

    assertTrue(outbox().isEmpty())
  }
}

/** Survey editor autosave to the working copy. */
class WorkingCopyRecorderTest {
  private val raw = seededStore()
  private val recorder = WorkingCopyRecorder(raw)
  private val surveyId = runNow { raw.transaction { surveys().first().id } }
  private val editors =
    org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl(
      raw,
      workingCopyRecorder = recorder,
    )

  private fun outbox(): List<PendingMutation> = runNow { raw.transaction { outbox() } }

  @Test
  fun autosave_queuesOneDebouncedPatchPerChangedKey() {
    val draft = runNow { editors.getDraft(surveyId) }
    runNow {
      editors.autosaveDraft(surveyId, draft.copy(details = draft.details.copy(title = "T")))
      editors.autosaveDraft(surveyId, draft.copy(details = draft.details.copy(title = "Ti")))
      editors.autosaveDraft(surveyId, draft.copy(details = draft.details.copy(title = "Title")))
    }

    val pending = outbox()
    val titlePatches = pending.filter {
      (it.payload as MutationPayload.Survey).mutation.update_field?.key == "title"
    }
    assertEquals(1, titlePatches.size)
    assertEquals(
      "Title",
      (titlePatches.single().payload as MutationPayload.Survey)
        .mutation
        .update_field!!
        .string_value,
    )
    assertTrue(pending.all { it.target == MutationTarget.Survey(surveyId, workingCopy = true) })
    assertTrue(pending.all { it.notBefore != null })
  }

  @Test
  fun discard_dropsQueuedAutosaves_andQueuesDiscard() {
    val draft = runNow { editors.getDraft(surveyId) }
    runNow {
      editors.autosaveDraft(surveyId, draft.copy(details = draft.details.copy(title = "Unsaved")))
      editors.discardWorkingCopy(surveyId)
    }

    val pending = outbox().single()
    assertEquals(true, (pending.payload as MutationPayload.Survey).mutation.discard_working_copy)
  }

  @Test
  fun discard_withoutAutosave_doesNothing() {
    runNow { editors.discardWorkingCopy(surveyId) }

    assertEquals(emptyList(), outbox())
  }
}
