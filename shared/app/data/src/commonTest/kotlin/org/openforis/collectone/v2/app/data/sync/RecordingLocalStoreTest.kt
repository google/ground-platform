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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.map.GeometryType

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
    val upsert = (pending.payload as MutationPayload.Data).mutation.entity_mutation!!.upsert!!
    assertEquals(setOf("crop"), upsert.properties.keys)
    assertEquals("coffee", upsert.properties["crop"]?.string_value)
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
    val upsert = (pending.payload as MutationPayload.Organization).mutation.upsert!!
    assertEquals("Renamed", upsert.name)
  }

  @Test
  fun editingASurveyTitle_recordsASavedRevisionPatch() {
    runNow { store.transaction { updateSurvey(surveyId) { it.copy(title = "New title") } } }

    val pending = outbox().single()
    assertEquals(MutationTarget.Survey(surveyId, workingCopy = false), pending.target)
    val upsert = (pending.payload as MutationPayload.Survey).mutation.upsert!!
    assertEquals("New title", upsert.title)
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

    val upsert =
      (outbox().single().payload as MutationPayload.Data).mutation.entity_mutation!!.upsert!!
    assertEquals("Second", upsert.label)
  }

  @Test
  fun writesToTheUnderlyingStore_areNotRecorded() {
    val entity = firstEntity()
    runNow { raw.transaction { upsertEntity(surveyId, entity.copy(label = "Downloaded")) } }

    assertTrue(outbox().isEmpty())
  }

  @Test
  fun newSubmission_isUpsertedWithoutServerOwnedFields() {
    val entity = runNow {
      raw.transaction { entities(surveyId).first { it.submissions.isNotEmpty() } }
    }
    val submission =
      entity.submissions
        .first()
        .copy(
          id = "submission-new-1",
          collectorEmail = "c@example.org",
          serverUpdatedAt = Instant.parse("2026-09-24T08:17:00Z"),
        )
    runNow {
      store.transaction {
        upsertEntity(surveyId, entity.copy(submissions = entity.submissions + submission))
      }
    }

    val upsert =
      (outbox().single().payload as MutationPayload.Data).mutation.submission_mutation!!.upsert!!
    assertEquals("submission-new-1", upsert.submission_id)
    assertEquals("c@example.org", upsert.collector_email)
    assertNull(upsert.audit_info)
    assertNull(upsert.server_updated_at)
  }

  @Test
  fun newSurvey_isUpsertedWithoutItsSavedRevisionNumber() {
    val survey = runNow { raw.transaction { survey(surveyId)!! } }
    runNow { store.transaction { upsertSurvey(survey.copy(id = "survey-new-1")) } }

    val upserts = outbox().mapNotNull { (it.payload as MutationPayload.Survey).mutation.upsert }
    val created = upserts.single()
    assertEquals("survey-new-1", created.survey_id)
    assertEquals(0L, created.saved_revision_number)
  }

  @Test
  fun runtimeEntityAndLayerUpdatesViaSurveyRepository_doNotRecordSurveyMutations() {
    val repo = org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl(store)
    val entity = firstEntity().copy(id = "entity-offline-1", submissions = emptyList())
    runNow {
      repo.setActiveSurveyId(surveyId)
      repo.upsertEntities(listOf(entity))
      repo.toggleLayerVisibility(repo.getMapLayers().first().id)
    }

    val pending = outbox()
    assertEquals(1, pending.size)
    assertEquals(MutationTarget.Data(surveyId), pending.single().target)
    assertTrue(pending.none { it.target is MutationTarget.Survey })
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
      (it.payload as MutationPayload.Survey).mutation.upsert?.title?.isNotEmpty() == true
    }
    assertEquals(1, titlePatches.size)
    assertEquals(
      "Title",
      (titlePatches.single().payload as MutationPayload.Survey).mutation.upsert!!.title,
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

  @Test
  fun autosave_overSyncedServerWorkingCopy_recordsOnlyNewEdits() {
    val saved = runNow { editors.getDraft(surveyId) }
    val syncedWorkingCopy =
      saved.copy(details = saved.details.copy(description = "Already on server working copy"))
    runNow { raw.transaction { putSurveyWorkingCopyDraft(surveyId, syncedWorkingCopy) } }

    // Resuming the server's working copy without new edits records nothing in the outbox.
    runNow { editors.autosaveDraft(surveyId, syncedWorkingCopy) }
    assertEquals(emptyList(), outbox())

    // Subsequent edits record only what changed since the server's working copy.
    runNow {
      editors.autosaveDraft(
        surveyId,
        syncedWorkingCopy.copy(details = syncedWorkingCopy.details.copy(title = "New title")),
      )
    }
    val mutations = outbox().map { (it.payload as MutationPayload.Survey).mutation }
    assertTrue(mutations.any { it.upsert?.title == "New title" })
    assertTrue(mutations.none { it.upsert?.description?.isNotEmpty() == true })
  }

  @Test
  fun saveDraft_afterWorkingCopySyncedToServer_preservesFormsLayersAndPurposes() {
    val recordingStore = RecordingLocalStore(raw)
    val recordingEditors =
      org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl(
        recordingStore,
        workingCopyRecorder = recorder,
      )
    val blankDef =
      openforis.collectone.v1.survey.SurveyDef(
        survey_id = surveyId,
        title = "Untitled survey",
        saved_revision_number = 1L,
        record_version = 1L,
      )
    val workingCopyDef =
      blankDef.copy(
        title = "EUDR Plot Survey",
        purpose_ids = listOf("eudr_due_diligence"),
        record_version = 2L,
      )
    runNow {
      raw.transaction {
        putForms(surveyId, emptyList())
        putMapLayers(surveyId, emptyList())
        putSurveyConfig(
          surveyId,
          org.openforis.collectone.v2.app.domain.model.SurveyConfig(),
        )
        Rebaser(this)
          .acceptSurvey(
            surveyId,
            org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase(
              saved = blankDef,
              workingCopy = workingCopyDef,
            ),
            replace = true,
          )
      }
    }

    val draftToPublish = runNow {
      val current = recordingEditors.getDraft(surveyId)
      val newLayer =
        org.openforis.collectone.v2.app.domain.model.editor.EntityDataset(
          key = "d1",
          kind = org.openforis.collectone.v2.app.domain.model.editor.DatasetKind.MAP_LAYER,
          id = "plots",
          displayName = "Plots",
          geometryType = GeometryType.POLYGON,
          keyProperty = "id",
          labelProperty = "id",
          properties =
            listOf(
              org.openforis.collectone.v2.app.domain.model.editor.EntityProperty(
                name = "id",
                label = "ID",
                type = org.openforis.collectone.v2.app.domain.model.editor.PropertyType.TEXT,
                required = true,
              )
            ),
        )
      val newForm =
        org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm(
          key = "f1",
          form =
            org.openforis.collectone.v2.app.domain.model.editor.EditorForm(
              formId = "form-plots",
              title = "Plot Assessment",
              saveTo =
                org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo(
                  mode = org.openforis.collectone.v2.app.domain.model.editor.SaveToMode.CREATE
                ),
              questions =
                listOf(
                  org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion(
                    key = "q1",
                    name = "boundary",
                    label = "Plot boundary",
                    type =
                      org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
                        .POLYGON,
                  )
                ),
            ),
        )
      current.copy(
        details =
          current.details.copy(
            title = "EUDR Plot Survey",
            purposeIds = listOf("eudr_due_diligence"),
          ),
        datasets = listOf(newLayer.copy(linkedFormKey = "f1")),
        forms = listOf(newForm),
      )
    }

    runNow { recordingEditors.saveDraft(surveyId, draftToPublish) }

    val savedForms = runNow { raw.transaction { forms(surveyId) } }
    val savedLayers = runNow { raw.transaction { mapLayers(surveyId) } }
    val savedConfig = runNow { raw.transaction { surveyConfig(surveyId) } }
    assertEquals(listOf("form-plots"), savedForms.map { it.id })
    assertEquals(listOf("plots"), savedLayers.map { it.datasetId })
    assertEquals(listOf("eudr_due_diligence"), savedConfig?.purposeIds)

    val savedMutations =
      outbox()
        .filter { it.target == MutationTarget.Survey(surveyId, workingCopy = false) }
        .map { (it.payload as MutationPayload.Survey).mutation }
    assertTrue(savedMutations.any { it.upsert?.forms?.isNotEmpty() == true })
    assertTrue(savedMutations.any { it.upsert?.map_config?.layers?.isNotEmpty() == true })
    assertTrue(savedMutations.any { it.upsert?.purpose_ids == listOf("eudr_due_diligence") })
    val workingCopyMutations =
      outbox()
        .filter { it.target == MutationTarget.Survey(surveyId, workingCopy = true) }
        .map { (it.payload as MutationPayload.Survey).mutation }
    assertTrue(workingCopyMutations.any { it.discard_working_copy })
  }

  private fun seedServerBase():
    org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase = runNow {
    raw.transaction {
      val (def, acl) =
        org.openforis.collectone.v2.app.data.mapper.ProtoSurveyMapper.surveyToProto(
          survey = survey(surveyId)!!,
          forms = forms(surveyId),
          mapLayers = mapLayers(surveyId),
          config = surveyConfig(surveyId),
          editorDraft = surveyEditorDraft(surveyId),
        )
      val base =
        org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase(
          saved = def.copy(record_version = 1L),
          acl = acl.copy(record_version = 1L),
        )
      Rebaser(this).acceptSurvey(surveyId, base, replace = true)
      base
    }
  }

  private fun applyQueuedSurveyMutations(
    baseDef: openforis.collectone.v1.survey.SurveyDef,
    workingCopy: Boolean,
    nextVersion: Long,
  ): openforis.collectone.v1.survey.SurveyDef =
    outbox()
      .filter { it.target == MutationTarget.Survey(surveyId, workingCopy = workingCopy) }
      .map { (it.payload as MutationPayload.Survey).mutation }
      .fold(baseDef) { def, m ->
        org.openforis.collectone.v2.core.mutation.SurveyMutations.apply(def, m.delete, m.upsert)
      }
      .copy(record_version = nextVersion)

  @Test
  fun autosaveAndPublish_insertedQuestionsBetweenSteps_keepExactPositionAfterServerMerge() {
    val baseBefore = seedServerBase()
    val recordingEditors =
      org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl(
        RecordingLocalStore(raw),
        workingCopyRecorder = recorder,
      )
    val initialDraft = runNow { recordingEditors.getDraft(surveyId) }
    val targetEntry = initialDraft.forms.first()
    val originalNames = targetEntry.form.questions.map { it.name }
    assertTrue(originalNames.size >= 2)

    val qStart =
      org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion(
        key = "q_insert_start",
        name = "inserted_at_start",
        label = "Inserted at start",
        type = org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType.DATE,
      )
    val qBetween =
      org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion(
        key = "q_insert_between",
        name = "inserted_between_one_and_two",
        label = "Inserted between Q1 and Q2",
        type = org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType.NUMBER,
      )
    val editedQuestions =
      targetEntry.form.questions.toMutableList().apply {
        add(0, qStart)
        add(2, qBetween)
      }
    val editedDraft =
      initialDraft.copy(
        forms =
          listOf(targetEntry.copy(form = targetEntry.form.copy(questions = editedQuestions))) +
            initialDraft.forms.drop(1)
      )
    val expectedNames = listOf(qStart.name, originalNames[0], qBetween.name) + originalNames.drop(1)

    runNow { recordingEditors.autosaveDraft(surveyId, editedDraft) }
    val serverWc =
      applyQueuedSurveyMutations(baseBefore.saved!!, true, baseBefore.saved.record_version + 1L)
    runNow {
      raw.transaction {
        putOutbox(emptyList())
        Rebaser(this).acceptSurvey(surveyId, baseBefore.copy(workingCopy = serverWc))
      }
    }
    val reloadedWc = runNow { assertNotNull(recordingEditors.getWorkingCopyDraft(surveyId)) }
    assertEquals(expectedNames, reloadedWc.forms.first().form.questions.map { it.name })

    runNow { recordingEditors.saveDraft(surveyId, editedDraft, previous = initialDraft) }
    val serverSaved =
      applyQueuedSurveyMutations(baseBefore.saved, false, baseBefore.saved.record_version + 2L)
    runNow {
      raw.transaction {
        putOutbox(emptyList())
        Rebaser(this)
          .acceptSurvey(surveyId, baseBefore.copy(saved = serverSaved, workingCopy = null))
      }
    }
    val publishedDraft = runNow { recordingEditors.getDraft(surveyId) }
    assertEquals(expectedNames, publishedDraft.forms.first().form.questions.map { it.name })
  }
}
