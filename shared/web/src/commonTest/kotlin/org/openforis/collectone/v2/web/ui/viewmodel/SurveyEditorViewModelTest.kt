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
package org.openforis.collectone.v2.web.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.testing.testScope
import org.openforis.collectone.v2.web.domain.model.editor.SurveyEditorMutation
import org.openforis.collectone.v2.web.ui.state.SurveyEditorEvent
import org.openforis.collectone.v2.web.ui.state.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.state.SurveyEditorUiState

/**
 * Lifecycle of the Survey editor ViewModel: live draft until the first edit, publishing through the
 * repository, discarding, per-Form editors, and events. Editing rules themselves are covered by
 * `SurveyEditorTest`, `SampleGenerationTest`, and the Form editor tests.
 */
class SurveyEditorViewModelTest {
  private class Fixture {
    val draft = SurveyEditorSamples.draft()
    val repository = FakeSurveyEditorRepository(draft)
    val scope = testScope()
    val viewModel = surveyEditorViewModel(draft, editorRepository = repository, scope = scope)
    val events = mutableListOf<SurveyEditorEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
    }

    val uiState: SurveyEditorUiState
      get() = viewModel.uiState.value
  }

  @Test
  fun initialState_showsTheLiveDraft_withNothingToPublish() {
    val f = Fixture()
    assertEquals(f.draft, f.uiState.draft)
    assertEquals(f.draft, f.uiState.opened)
    assertFalse(f.uiState.isDirty)
    assertFalse(f.uiState.hasUnpublishedChanges)
    assertFalse(f.uiState.canPublish)
    assertEquals(0, f.uiState.issueCount)
    assertIs<SurveyEditorSection.Details>(f.uiState.section)
    assertEquals(SurveyEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
  }

  @Test
  fun untouchedEditor_followsRepositoryChanges() {
    val f = Fixture()
    val renamed = f.draft.copy(details = f.draft.details.copy(title = "Renamed elsewhere"))
    f.repository.drafts.value = renamed
    assertEquals("Renamed elsewhere", f.uiState.details.title)
    assertFalse(f.uiState.hasUnpublishedChanges)
  }

  @Test
  fun firstEdit_freezesTheOpenedSnapshot() {
    val f = Fixture()
    f.viewModel.updateDetails { it.copy(title = "Edited") }
    assertTrue(f.uiState.isDirty)
    assertTrue(f.uiState.hasUnpublishedChanges)
    assertEquals(f.draft, f.uiState.opened)

    // Repository changes no longer show through while there are edits.
    f.repository.drafts.value = f.draft.copy(details = f.draft.details.copy(title = "Elsewhere"))
    assertEquals("Edited", f.uiState.details.title)
    assertEquals(f.draft, f.uiState.opened)
  }

  @Test
  fun noOpEdit_doesNotStartASession() {
    val f = Fixture()
    f.viewModel.updateDetails { it }
    assertFalse(f.uiState.isDirty)
  }

  @Test
  fun publish_savesThroughTheRepository_withTheOpenedSnapshot_andLeavesTheEditor() {
    val f = Fixture()
    f.viewModel.updateDetails { it.copy(title = "Edited") }
    assertTrue(f.uiState.canPublish)

    f.viewModel.publish()
    val (saved, previous) = f.repository.saved.single()
    assertEquals("Edited", saved.details.title)
    assertEquals(f.draft, previous)
    assertFalse(f.uiState.isDirty)
    assertFalse(f.uiState.isSaving)
    assertFalse(f.uiState.hasUnpublishedChanges)
    assertEquals("Edited", f.uiState.details.title)
    assertEquals(listOf<SurveyEditorEvent>(SurveyEditorEvent.Published), f.events)
  }

  @Test
  fun close_discardsEdits_andLeavesTheEditor() {
    val f = Fixture()
    f.viewModel.addDataset(DatasetKind.DATA_TABLE)
    val added = assertNotNull(f.uiState.selectedDataset)
    f.viewModel.close()
    assertFalse(f.uiState.isDirty)
    assertNull(f.uiState.dataset(added.key))
    // The selection pointed at a discarded dataset, so it falls back to Details.
    assertIs<SurveyEditorSection.Details>(f.uiState.section)
    assertEquals(listOf<SurveyEditorEvent>(SurveyEditorEvent.Closed), f.events)
    assertTrue(f.repository.saved.isEmpty())
  }

  @Test
  fun discardChanges_keepsASelectionThatStillExists() {
    val f = Fixture()
    f.viewModel.select(SurveyEditorSection.Form("f1"))
    f.viewModel.updateDetails { it.copy(title = "Edited") }
    f.viewModel.discardChanges()
    assertEquals(SurveyEditorSection.Form("f1"), f.uiState.section)
    assertEquals(f.draft.details.title, f.uiState.details.title)
  }

  @Test
  fun formEditor_editsTheDraftsForm_andIsReusedWhileTheFormExists() {
    val f = Fixture()
    val editor = f.viewModel.formEditor("f1")
    assertSame(editor, f.viewModel.formEditor("f1"))
    assertEquals(f.draft.forms.first().form, editor.uiState.value.form)

    editor.updateTitle("Visit v2")
    assertEquals("Visit v2", f.uiState.form("f1")?.form?.title)
    assertEquals("Visit v2", editor.uiState.value.form.title)
    assertTrue(f.uiState.hasUnpublishedChanges)

    // The Form editor sees the survey's datasets, with its own linked one flagged.
    assertEquals(f.draft.datasets.size, editor.uiState.value.datasets.size)

    f.viewModel.deleteForm("f1")
    val replacement = f.viewModel.formEditor("f1")
    assertFalse(replacement === editor)
  }

  @Test
  fun formQuestionEdits_syncLinkedDatasets() {
    val f = Fixture()
    f.viewModel.addForm()
    val formKey = (f.uiState.section as SurveyEditorSection.Form).key
    val linked = f.uiState.datasets.single { it.linkedFormKey == formKey }
    assertEquals(DatasetKind.DATA_TABLE, linked.kind)

    f.viewModel.formEditor(formKey).addQuestion(EditorQuestionType.LOCATION)
    val synced = f.uiState.dataset(linked.key)!!
    assertEquals(DatasetKind.MAP_LAYER, synced.kind)
    assertTrue(synced.properties.any { it.name == "location" })
  }

  @Test
  fun sharingInvites_goThroughTheUseCase() {
    val f = Fixture()
    assertEquals(
      "Enter a valid email address.",
      f.viewModel.inviteCollaborator("nope", CollaboratorRole.DATA_COLLECTOR),
    )
    assertFalse(f.uiState.isDirty)
    assertNull(f.viewModel.inviteCollaborator("new@example.org", CollaboratorRole.DATA_COLLECTOR))
    assertTrue(f.uiState.sharing.collaborators.any { it.email == "new@example.org" })
  }

  @Test
  fun clearingTheOrganization_fallsBackToRestricted_withANotice() {
    val f = Fixture()
    f.viewModel.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }
    f.viewModel.setOrganization(null)
    assertEquals(SharingPolicy.RESTRICTED, f.uiState.sharing.policy)
    assertNotNull(f.uiState.organizationNotice)
    f.viewModel.dismissOrganizationNotice()
    assertNull(f.uiState.organizationNotice)
  }

  @Test
  fun sidePanelWidth_isClamped() {
    val f = Fixture()
    f.viewModel.updateSidePanelWidth(SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
    f.viewModel.updateSidePanelWidth(SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
    f.viewModel.updateSidePanelWidth(Float.NaN)
    assertEquals(SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP, f.uiState.sidePanelWidthDp)
  }

  @Test
  fun reset_dropsEditsAndSelection() {
    val f = Fixture()
    f.viewModel.select(SurveyEditorSection.Sharing)
    f.viewModel.updateDetails { it.copy(title = "Edited") }
    f.viewModel.reset()
    assertFalse(f.uiState.isDirty)
    assertIs<SurveyEditorSection.Details>(f.uiState.section)
  }

  @Test
  fun seededStore_editsTheActiveSurvey_andPublishesToIt() {
    val store = seededStore()
    val scope = testScope()
    val viewModel = seededSurveyEditorViewModel(store, scope)
    val surveys = SurveyRepositoryImpl(store)
    val activeId = viewModel.uiState.value.surveyId
    assertTrue(activeId.isNotBlank())
    assertEquals(activeId, viewModel.uiState.value.details.surveyId)
    assertTrue(viewModel.uiState.value.organizations.isNotEmpty())

    viewModel.updateDetails { it.copy(title = "Published title") }
    viewModel.publish()
    assertEquals("Published title", viewModel.uiState.value.details.title)
    assertFalse(viewModel.uiState.value.hasUnpublishedChanges)

    // Switching the active survey starts a fresh session on that survey's draft.
    val otherId = runNow { surveys.getSurveys().first { it.id != activeId }.id }
    runNow { surveys.setActiveSurveyId(otherId) }
    assertEquals(otherId, viewModel.uiState.value.surveyId)
    assertEquals(otherId, viewModel.uiState.value.details.surveyId)
    assertFalse(viewModel.uiState.value.isDirty)
  }

  @Test
  fun formEditorUndoRedo_rewindsAndReplaysSurveyDraftAndLinkedDatasets() {
    val f = Fixture()
    f.viewModel.addForm()
    val formKey = (f.uiState.section as SurveyEditorSection.Form).key
    val linked = f.uiState.datasets.single { it.linkedFormKey == formKey }
    assertEquals(DatasetKind.DATA_TABLE, linked.kind)

    val editor = f.viewModel.formEditor(formKey)
    assertFalse(editor.ui.canUndo)
    assertFalse(editor.ui.canRedo)

    // 1. Add a LOCATION question: mutates both the form and the linked dataset (DATA_TABLE ->
    // MAP_LAYER).
    editor.addQuestion(EditorQuestionType.LOCATION)
    val locationKey = assertNotNull(editor.ui.selectedKey)
    assertEquals(DatasetKind.MAP_LAYER, f.uiState.dataset(linked.key)?.kind)
    assertTrue(f.uiState.dataset(linked.key)!!.properties.any { it.name == "location" })
    assertTrue(editor.ui.canUndo)

    // 2. Switch save-to mode via SurveyEditorViewModel: drops the empty linked dataset and switches
    // the form to UPDATE mode in a single undoable mutation.
    f.viewModel.setFormSaveToMode(formKey, SaveToMode.UPDATE)
    assertEquals(SaveToMode.UPDATE, editor.ui.form.saveTo.mode)
    assertNull(f.uiState.dataset(linked.key))
    assertEquals(2, editor.ui.undoStack.size)

    // Undo step 2: restores CREATE mode and the linked MAP_LAYER dataset.
    editor.undo()
    assertEquals(SaveToMode.CREATE, editor.ui.form.saveTo.mode)
    assertEquals(DatasetKind.MAP_LAYER, f.uiState.dataset(linked.key)?.kind)
    assertEquals(locationKey, editor.ui.selectedKey)
    assertTrue(editor.ui.canUndo)
    assertTrue(editor.ui.canRedo)

    // Undo step 1: removes the LOCATION question and rewinds the linked dataset back to DATA_TABLE.
    editor.undo()
    assertNull(editor.ui.form.questions.firstOrNull { it.key == locationKey })
    assertEquals(DatasetKind.DATA_TABLE, f.uiState.dataset(linked.key)?.kind)
    assertFalse(f.uiState.dataset(linked.key)!!.properties.any { it.name == "location" })
    assertFalse(editor.ui.canUndo)
    assertTrue(editor.ui.canRedo)

    // Redo step 1 and step 2.
    editor.redo()
    assertNotNull(editor.ui.form.questions.firstOrNull { it.key == locationKey })
    assertEquals(DatasetKind.MAP_LAYER, f.uiState.dataset(linked.key)?.kind)
    editor.redo()
    assertEquals(SaveToMode.UPDATE, editor.ui.form.saveTo.mode)
    assertNull(f.uiState.dataset(linked.key))
    assertFalse(editor.ui.canRedo)

    // Discarding survey changes clears the form editor's undo/redo stacks.
    f.viewModel.discardChanges()
    assertFalse(editor.ui.canUndo)
    assertFalse(editor.ui.canRedo)
  }

  @Test
  fun surveyEditorMutation_betweenRewindsAndReplaysCompositeSurveyChanges() {
    val f = Fixture()
    val before = f.uiState.draft
    f.viewModel.updateDetails { it.copy(title = "Updated Survey Title") }
    f.viewModel.addForm()
    f.viewModel.addDataset(DatasetKind.DATA_TABLE)
    val after = f.uiState.draft

    val mutation = assertNotNull(SurveyEditorMutation.between(before, after, formKey = "f1"))
    assertTrue(mutation.steps.isNotEmpty())
    assertEquals(before, mutation.rewind(after))
    assertEquals(after, mutation.replay(before))
  }

  @Test
  fun saveActionLabel_isSaveDraftForDraftSurveys_andRepublishForPublishedSurveys() {
    val f = Fixture()
    assertEquals("Republish", f.uiState.saveActionLabel)
    assertEquals(
      "Save draft",
      f.uiState
        .copy(
          lifecycleState = org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState.DRAFT
        )
        .saveActionLabel,
    )
    assertEquals(
      "Republish",
      f.uiState
        .copy(
          lifecycleState =
            org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState.PUBLISHED
        )
        .saveActionLabel,
    )
  }

  @Test
  fun updateQuotaLimits_updatesDraftDetails() {
    val f = Fixture()
    val limits = QuotaLimitsConfig(tier = QuotaTier.BASIC, monthlyBudgetUsd = 50.0)
    f.viewModel.updateQuotaLimits { limits }
    assertEquals(QuotaTier.BASIC, f.uiState.details.quotaLimits.tier)
    assertEquals(50.0, f.uiState.details.quotaLimits.monthlyBudgetUsd)
  }

  @Test
  fun publish_whenSurveyDesignExceedsQuota_abortsAndSetsNotice() {
    val f = Fixture()
    f.viewModel.updateQuotaLimits { it.copy(maxFormsPerSurvey = 1) }
    f.viewModel.addForm()
    f.viewModel.publish()
    assertNotNull(f.uiState.organizationNotice)
    assertTrue(f.uiState.organizationNotice!!.contains("exceeding the limit of 1"))
    assertFalse(f.events.any { it is SurveyEditorEvent.Published })
  }

  @Test
  fun createDatasetForChoiceQuestion_createsDatasetAndBindsToFormQuestionWithoutLeavingForm() {
    val f = Fixture()
    f.viewModel.addForm()
    val formKey = (f.uiState.section as SurveyEditorSection.Form).key
    val editor = f.viewModel.formEditor(formKey)
    editor.addQuestion(EditorQuestionType.SELECT_ONE)
    val qIndex = editor.ui.form.questions.lastIndex

    // Create Map Layer dataset for question
    val initialDatasetCount = f.uiState.datasets.size
    f.viewModel.createDatasetForChoiceQuestion(formKey, qIndex, DatasetKind.MAP_LAYER)
    assertEquals(initialDatasetCount + 1, f.uiState.datasets.size)
    val createdMapLayer = f.uiState.datasets.last()
    assertEquals(DatasetKind.MAP_LAYER, createdMapLayer.kind)
    val updatedForm = f.uiState.form(formKey)!!.form
    val choiceQuestion = updatedForm.questions[qIndex]
    assertEquals(ChoiceSource.MAP_LAYER, choiceQuestion.choiceSource)
    assertEquals(createdMapLayer.id, choiceQuestion.choiceDatasetId)
    // Verify user is still on the Form section, not navigated away to Dataset
    assertEquals(SurveyEditorSection.Form(formKey), f.uiState.section)

    // Call from FormEditorViewModel delegate
    editor.createDatasetForChoiceQuestion(qIndex, DatasetKind.DATA_TABLE)
    assertEquals(initialDatasetCount + 2, f.uiState.datasets.size)
    val createdDataTable = f.uiState.datasets.last()
    assertEquals(DatasetKind.DATA_TABLE, createdDataTable.kind)
    val updatedForm2 = f.uiState.form(formKey)!!.form
    val choiceQuestion2 = updatedForm2.questions[qIndex]
    assertEquals(ChoiceSource.DATA_TABLE, choiceQuestion2.choiceSource)
    assertEquals(createdDataTable.id, choiceQuestion2.choiceDatasetId)
    assertEquals(SurveyEditorSection.Form(formKey), f.uiState.section)
  }
}
