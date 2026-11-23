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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.ImpactCoverage
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleFixture
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_publish_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_reopen_survey
import org.openforis.collectone.v2.web.resources.surveyeditor_lifecycle_switch_to_draft
import org.openforis.collectone.v2.web.resources.surveyeditor_read_only_archived
import org.openforis.collectone.v2.web.resources.surveyeditor_read_only_closed
import org.openforis.collectone.v2.web.resources.surveyeditor_republish
import org.openforis.collectone.v2.web.resources.surveyeditor_save_changes

/** Closing, archiving, and reopening a survey from the Survey editor, and the outcome card. */
class SurveyEditorLifecycleTest {
  private class Fixture(activeSurveyId: String = "00000036-1a1c-5c51-997f-28b67d813aa3") {
    val lifecycle = SurveyLifecycleFixture()
    val store = lifecycle.store

    init {
      runNow { lifecycle.surveyRepository.setActiveSurveyId(activeSurveyId) }
    }

    val viewModel =
      SurveyEditorViewModel(
        surveyRepository = lifecycle.surveyRepository,
        surveyEditorRepository = SurveyEditorRepositoryImpl(store),
        organizationRepository = OrganizationRepositoryImpl(store),
        authRepository = lifecycle.authRepository,
        placeRepository = PlaceRepositoryImpl(store),
        surveyLifecycle = lifecycle.lifecycle,
        impactEventRepository = lifecycle.impactEventRepository,
      )

    val ui
      get() = viewModel.ui
  }

  @Test
  fun publishedSurvey_isEditable_andManagedByItsOwner() {
    val f = Fixture()
    assertEquals(SurveyLifecycleState.PUBLISHED, f.ui.lifecycleState)
    assertTrue(f.ui.canManageSurvey)
    assertFalse(f.ui.isReadOnly)
    assertNull(f.ui.readOnlyMessage)
    assertNull(f.ui.outcomeCard)
  }

  @Test
  fun deleteSurvey_discardsEdits_removesTheSurvey_andClearsTheActiveSurvey() {
    val f = Fixture()
    val surveyId = f.ui.surveyId
    f.viewModel.updateDetails { it.copy(title = "Unpublished edit") }

    f.viewModel.deleteSurvey()

    assertFalse(f.ui.isDirty)
    val surveys = runNow { f.lifecycle.surveyRepository.getSurveys() }
    assertTrue(surveys.none { it.id == surveyId })
    assertEquals("", runNow { f.lifecycle.surveyRepository.getActiveSurveyId() })
  }

  @Test
  fun deleteSurvey_worksOnAClosedSurvey() {
    val f = Fixture()
    val surveyId = f.ui.surveyId
    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    assertTrue(f.ui.isReadOnly)

    f.viewModel.deleteSurvey()

    val surveys = runNow { f.lifecycle.surveyRepository.getSurveys() }
    assertTrue(surveys.none { it.id == surveyId })
  }

  @Test
  fun close_makesTheEditorReadOnly_recordsTheEvent_andOffersTheCard() {
    val f = Fixture()
    f.viewModel.updateDetails { it.copy(title = "Unpublished edit") }
    assertTrue(f.ui.hasUnpublishedChanges)

    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)

    assertEquals(SurveyLifecycleState.CLOSED, f.ui.lifecycleState)
    assertEquals(isoUtc(f.lifecycle.clock.now()), f.ui.closedAt)
    assertTrue(f.ui.isReadOnly)
    assertEquals(Res.string.surveyeditor_read_only_closed, f.ui.readOnlyMessage)
    // Unpublished edits are discarded when closing.
    assertFalse(f.ui.isDirty)
    assertTrue(f.ui.details.title != "Unpublished edit")
    // The survey closed event covers the survey's map features and shows in the Activity list.
    val event = f.lifecycle.closedEvents(f.ui.surveyId).single()
    val coverage = ImpactCoverage.of(f.lifecycle.entities(f.ui.surveyId))
    assertEquals(coverage.featureCount, event.featureCount)
    assertEquals(coverage.areaHa, event.areaHa)
    assertEquals(ImpactEventType.SURVEY_CLOSED, f.ui.activity.first().type)
    // The card doesn't hold up closing: the survey is closed whether or not it's answered.
    val card = assertNotNull(f.ui.outcomeCard)
    assertEquals(f.ui.surveyId, card.surveyId)
    assertTrue(card.outcomes.isEmpty())
    assertFalse(card.canSave)
  }

  @Test
  fun closedSurvey_ignoresEdits_publishing_andSampleGeneration() {
    val f = Fixture()
    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    val before = f.ui.draft

    f.viewModel.updateDetails { it.copy(title = "Ignored") }
    f.viewModel.addForm()
    f.viewModel.publish()

    assertEquals(before, f.ui.draft)
    assertFalse(f.ui.isDirty)
    assertFalse(f.ui.canPublish)
    assertFalse(f.ui.isSaving)
    val generated = runNow { f.viewModel.generateSample("missing") }
    assertIs<SampleGenerationOutcome.Failed>(generated)
    // Navigating stays possible.
    f.viewModel.select(SurveyEditorSection.Sharing)
    assertIs<SurveyEditorSection.Sharing>(f.ui.section)
  }

  @Test
  fun outcomeCard_notYetIsExclusive_andSaveStoresTheAnswer() {
    val f = Fixture()
    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)

    f.viewModel.toggleOutcome(SurveyOutcomeKind.SHARED_WITH_BUYERS)
    f.viewModel.toggleOutcome(SurveyOutcomeKind.NOT_YET)
    assertEquals(setOf(SurveyOutcomeKind.NOT_YET), f.ui.outcomeCard?.outcomes)
    f.viewModel.toggleOutcome(SurveyOutcomeKind.SUBMITTED_EUDR_DDS)
    f.viewModel.toggleOutcome(SurveyOutcomeKind.REPORTED_FERM)
    f.viewModel.setEffortComparison(EffortComparison.SAME)
    assertTrue(f.ui.outcomeCard!!.canSave)

    f.lifecycle.clock.advanceBy(1.minutes)
    f.viewModel.saveOutcome()

    assertNull(f.ui.outcomeCard)
    val outcome = assertNotNull(f.lifecycle.survey(f.ui.surveyId).outcome)
    assertEquals(
      setOf(SurveyOutcomeKind.SUBMITTED_EUDR_DDS, SurveyOutcomeKind.REPORTED_FERM),
      outcome.outcomes,
    )
    assertEquals(EffortComparison.SAME, outcome.effortComparison)
    assertEquals(isoUtc(f.lifecycle.clock.now()), outcome.answeredAt)
    assertEquals(SampleOrganizationsData.SIGNED_IN_EMAIL, outcome.answeredBy)
  }

  @Test
  fun outcomeCard_skip_keepsTheSurveyClosedWithoutAnAnswer() {
    val f = Fixture()
    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    f.viewModel.toggleOutcome(SurveyOutcomeKind.LAND_TITLING)

    f.viewModel.skipOutcome()

    assertNull(f.ui.outcomeCard)
    val survey = f.lifecycle.survey(f.ui.surveyId)
    assertEquals(SurveyLifecycleState.CLOSED, survey.state)
    assertNull(survey.outcome)
  }

  @Test
  fun archiveClosed_doesNotAskAgain_andReopenRestoresEditing() {
    val f = Fixture()
    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    f.viewModel.skipOutcome()

    f.viewModel.setLifecycleState(SurveyLifecycleState.ARCHIVED)
    assertEquals(SurveyLifecycleState.ARCHIVED, f.ui.lifecycleState)
    assertEquals(Res.string.surveyeditor_read_only_archived, f.ui.readOnlyMessage)
    assertNull(f.ui.outcomeCard)
    assertEquals(1, f.lifecycle.closedEvents(f.ui.surveyId).size)

    f.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertEquals(SurveyLifecycleState.PUBLISHED, f.ui.lifecycleState)
    assertNull(f.ui.closedAt)
    f.viewModel.updateDetails { it.copy(title = "Editable again") }
    assertEquals("Editable again", f.ui.details.title)
    assertTrue(f.ui.canPublish)
  }

  @Test
  fun reopen_hidesAnOpenCard() {
    val f = Fixture()
    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    assertNotNull(f.ui.outcomeCard)
    f.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertNull(f.ui.outcomeCard)
  }

  @Test
  fun seededClosedSurvey_opensReadOnly() {
    val f = Fixture(activeSurveyId = SampleSurveysData.CLOSED_SURVEY_ID)
    assertEquals(SurveyLifecycleState.CLOSED, f.ui.lifecycleState)
    assertEquals(SampleSurveysData.CLOSED_SURVEY_CLOSED_AT, f.ui.closedAt)
    assertTrue(f.ui.isReadOnly)
    assertNull(f.ui.outcomeCard)
  }

  @Test
  fun draftSurvey_isEditable_savesOnTransitionToPublished_andUpdatesSaveActionLabel() {
    val f = Fixture()
    assertEquals(SurveyLifecycleState.PUBLISHED, f.ui.lifecycleState)
    assertFalse(f.ui.isDraft)
    assertEquals(Res.string.surveyeditor_republish, f.ui.saveActionLabel)

    // Switch to DRAFT mode while holding a working edit: working edit is preserved.
    f.viewModel.updateDetails { it.copy(title = "Draft title in progress") }
    assertTrue(f.ui.hasUnpublishedChanges)
    f.viewModel.setLifecycleState(SurveyLifecycleState.DRAFT)

    assertEquals(SurveyLifecycleState.DRAFT, f.ui.lifecycleState)
    assertTrue(f.ui.isDraft)
    assertFalse(f.ui.isReadOnly)
    assertEquals(Res.string.surveyeditor_save_changes, f.ui.saveActionLabel)
    assertEquals("Draft title in progress", f.ui.details.title)
    assertTrue(f.ui.hasUnpublishedChanges)

    // Switching from DRAFT to PUBLISHED commits pending working edits and updates the save label.
    f.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertEquals(SurveyLifecycleState.PUBLISHED, f.ui.lifecycleState)
    assertFalse(f.ui.isDraft)
    assertEquals(Res.string.surveyeditor_republish, f.ui.saveActionLabel)
    assertEquals("Draft title in progress", f.ui.details.title)
    assertFalse(f.ui.hasUnpublishedChanges)
  }

  @Test
  fun lifecycleActionOptions_providesExpectedTransitionsForEachState() {
    val draftOptions = lifecycleActionOptions(SurveyLifecycleState.DRAFT)
    assertEquals(
      listOf(SurveyLifecycleState.PUBLISHED, SurveyLifecycleState.ARCHIVED),
      draftOptions.map { it.targetState },
    )
    assertEquals(Res.string.surveyeditor_lifecycle_publish_survey, draftOptions.first().label)
    assertFalse(draftOptions.first().requiresConfirmation)

    val publishedOptions = lifecycleActionOptions(SurveyLifecycleState.PUBLISHED)
    assertEquals(
      listOf(
        SurveyLifecycleState.DRAFT,
        SurveyLifecycleState.CLOSED,
        SurveyLifecycleState.ARCHIVED,
      ),
      publishedOptions.map { it.targetState },
    )
    assertEquals(Res.string.surveyeditor_lifecycle_switch_to_draft, publishedOptions.first().label)
    assertFalse(publishedOptions.first().requiresConfirmation)

    val closedOptions = lifecycleActionOptions(SurveyLifecycleState.CLOSED)
    assertEquals(
      listOf(
        SurveyLifecycleState.PUBLISHED,
        SurveyLifecycleState.DRAFT,
        SurveyLifecycleState.ARCHIVED,
      ),
      closedOptions.map { it.targetState },
    )
    assertEquals(Res.string.surveyeditor_lifecycle_reopen_survey, closedOptions.first().label)

    val archivedOptions = lifecycleActionOptions(SurveyLifecycleState.ARCHIVED)
    assertEquals(
      listOf(SurveyLifecycleState.PUBLISHED, SurveyLifecycleState.DRAFT),
      archivedOptions.map { it.targetState },
    )
    assertEquals(Res.string.surveyeditor_lifecycle_reopen_survey, archivedOptions.first().label)
  }

  @Test
  fun withoutTheLifecycleUseCase_closingIsOff() {
    val f = Fixture()
    val viewModel = seededSurveyEditorViewModel(f.store)
    viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    assertEquals(SurveyLifecycleState.PUBLISHED, viewModel.ui.lifecycleState)
  }

  @Test
  fun closedSurvey_propagatesReadOnlyToFormEditor_blocksInvites_andDiscardsStoredWorkingCopy() {
    val f = Fixture()
    val formKey = f.ui.forms.first().key
    val formEditor = f.viewModel.formEditor(formKey)

    // Invite a pending collaborator while still published, then save.
    val originalQuestionsCount = formEditor.ui.form.questions.size
    formEditor.updateTitle("Saved before close")
    assertTrue(formEditor.ui.canUndo)
    f.viewModel.inviteCollaborator("pending@example.org", CollaboratorRole.DATA_COLLECTOR)
    f.viewModel.publish()

    // Make an uncommitted working-copy edit before closing.
    val savedFormTitle = f.viewModel.formEditor(formKey).ui.form.title
    assertEquals("Saved before close", savedFormTitle)
    f.viewModel.formEditor(formKey).updateTitle("Unpublished edit before close")
    assertTrue(f.viewModel.formEditor(formKey).ui.canUndo)
    runNow {
      f.store.transaction {
        putSurveyWorkingCopyDraft(f.ui.surveyId, f.ui.draft)
      }
    }

    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)

    // Working copy in store was discarded cleanly before transitioning state.
    assertNull(runNow { f.store.transaction { surveyWorkingCopyDraft(f.ui.surveyId) } })

    // FormEditorViewModel becomes read-only reactively and ignores mutations / undo.
    val closedFormEditor = f.viewModel.formEditor(formKey)
    assertTrue(closedFormEditor.ui.isReadOnly)
    assertFalse(closedFormEditor.ui.canUndo)
    assertEquals(savedFormTitle, closedFormEditor.ui.form.title)
    closedFormEditor.updateTitle("Should be ignored")
    closedFormEditor.addQuestion(EditorQuestionType.LONG_TEXT)
    closedFormEditor.undo()
    assertEquals(savedFormTitle, closedFormEditor.ui.form.title)
    assertEquals(originalQuestionsCount, closedFormEditor.ui.form.questions.size)

    // Collaborator invites and invite acceptance are ignored while closed.
    val collabsBefore = f.ui.sharing.collaborators
    f.viewModel.inviteCollaborator("another@example.org", CollaboratorRole.VIEWER)
    f.viewModel.acceptInvite(
      email = "pending@example.org",
      displayName = "Pending User",
      photoUrl = null,
      cachedOn = "2026-10-30T00:00:00Z",
    )
    assertEquals(collabsBefore, f.ui.sharing.collaborators)

    // Reopening restores FormEditorViewModel mutability reactively.
    f.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertFalse(closedFormEditor.ui.isReadOnly)
    closedFormEditor.updateTitle("Updated after reopen")
    assertEquals("Updated after reopen", closedFormEditor.ui.form.title)
  }

  @Test
  fun publish_andLifecyclePublish_blockVariantIncompatibleForms_andSelectOffendingQuestion() {
    val f = Fixture()
    val formKey = f.ui.forms.first().key
    val formEditor = f.viewModel.formEditor(formKey)

    // 1. Switching a location question on a web-enabled form to GPS-only still allows publishing.
    val gpsQuestionKey =
      formEditor.ui.form.questions.first { it.type == EditorQuestionType.LOCATION }.key
    formEditor.updateCapture(gpsQuestionKey, GeometryCapture.GPS_ONLY)
    formEditor.selectPreviewTarget(FormPreviewTarget.MOBILE)
    formEditor.selectFormSettings()
    f.viewModel.select(SurveyEditorSection.Details)

    assertEquals(0, f.ui.issueCount)
    assertTrue(f.ui.canPublish)
    f.viewModel.publish()
    assertFalse(f.ui.hasUnpublishedChanges)

    // 2. On a map-feature-updating form (SaveToMode.UPDATE, eligible for Open in Google Earth),
    // enabling Open in Google Earth while incompatible questions exist blocks saving/publishing.
    val earthFixture = Fixture(activeSurveyId = "00000033-6b50-51cd-ac94-baea2ea06d65")
    earthFixture.viewModel.setLifecycleState(SurveyLifecycleState.DRAFT)
    assertEquals(SurveyLifecycleState.DRAFT, earthFixture.ui.lifecycleState)
    val earthFormKey = earthFixture.ui.forms.first().key
    val earthFormEditor = earthFixture.viewModel.formEditor(earthFormKey)

    earthFormEditor.addQuestion(EditorQuestionType.PHOTO)
    val photoQuestionKey = assertNotNull(earthFormEditor.ui.selectedKey)
    earthFormEditor.updateQuestionLabel(photoQuestionKey, "Canopy photo")
    earthFormEditor.updateOpenInGoogleEarth(true)
    earthFormEditor.selectPreviewTarget(FormPreviewTarget.MOBILE)
    earthFormEditor.selectFormSettings()
    earthFixture.viewModel.select(SurveyEditorSection.Details)

    assertTrue(earthFixture.ui.issueCount > 0)
    assertFalse(earthFixture.ui.canPublish)

    // Both publish() (Save changes on draft) and setLifecycleState(PUBLISHED) must refuse to save
    // and navigate to the offending form & question on the Web preview target.
    earthFixture.viewModel.publish()
    assertTrue(earthFixture.ui.hasUnpublishedChanges)
    assertEquals(SurveyEditorSection.Form(earthFormKey), earthFixture.ui.section)
    assertEquals(FormPreviewTarget.WEB, earthFormEditor.ui.previewTarget)
    assertEquals(photoQuestionKey, earthFormEditor.ui.selectedKey)

    earthFixture.viewModel.select(SurveyEditorSection.Details)
    earthFixture.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertEquals(SurveyLifecycleState.DRAFT, earthFixture.ui.lifecycleState)
    assertEquals(SurveyEditorSection.Form(earthFormKey), earthFixture.ui.section)

    // Once Open in Google Earth is turned off, validation clears and publishing succeeds.
    earthFormEditor.updateOpenInGoogleEarth(false)
    assertEquals(0, earthFixture.ui.issueCount)
    earthFixture.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertEquals(SurveyLifecycleState.PUBLISHED, earthFixture.ui.lifecycleState)
    assertFalse(earthFixture.ui.hasUnpublishedChanges)
  }
}
