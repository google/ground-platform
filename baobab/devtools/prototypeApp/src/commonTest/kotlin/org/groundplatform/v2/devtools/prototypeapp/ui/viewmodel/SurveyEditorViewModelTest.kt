/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorUiState

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
}
