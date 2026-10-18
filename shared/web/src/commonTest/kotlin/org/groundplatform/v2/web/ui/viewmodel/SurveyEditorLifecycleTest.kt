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
package org.groundplatform.v2.web.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.app.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.data.seed.SampleSurveysData
import org.groundplatform.v2.app.domain.model.EffortComparison
import org.groundplatform.v2.app.domain.model.ImpactCoverage
import org.groundplatform.v2.app.domain.model.ImpactEventType
import org.groundplatform.v2.app.domain.model.SurveyLifecycleState
import org.groundplatform.v2.app.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.app.domain.model.editor.isoUtc
import org.groundplatform.v2.app.testing.testScope
import org.groundplatform.v2.web.domain.model.editor.SampleGenerationOutcome
import org.groundplatform.v2.web.domain.usecase.SurveyLifecycleFixture
import org.groundplatform.v2.web.ui.state.SurveyEditorSection
import org.groundplatform.v2.web.ui.surveyeditor.lifecycleActionOptions

/** Closing, archiving, and reopening a survey from the Survey editor, and the outcome card. */
class SurveyEditorLifecycleTest {
  private class Fixture(activeSurveyId: String = "00000036-1a1c-5c51-997f-28b67d813aa3") {
    val lifecycle = SurveyLifecycleFixture()
    val store = lifecycle.store
    val scope = testScope()

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
        scope = scope,
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
  fun close_makesTheEditorReadOnly_recordsTheEvent_andOffersTheCard() {
    val f = Fixture()
    f.viewModel.updateDetails { it.copy(title = "Unpublished edit") }
    assertTrue(f.ui.hasUnpublishedChanges)

    f.viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)

    assertEquals(SurveyLifecycleState.CLOSED, f.ui.lifecycleState)
    assertEquals(isoUtc(f.lifecycle.nowMillis), f.ui.closedAt)
    assertTrue(f.ui.isReadOnly)
    assertEquals("This survey is closed. Reopen it to make changes.", f.ui.readOnlyMessage)
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

    f.lifecycle.nowMillis += 60_000L
    f.viewModel.saveOutcome()

    assertNull(f.ui.outcomeCard)
    val outcome = assertNotNull(f.lifecycle.survey(f.ui.surveyId).outcome)
    assertEquals(
      setOf(SurveyOutcomeKind.SUBMITTED_EUDR_DDS, SurveyOutcomeKind.REPORTED_FERM),
      outcome.outcomes,
    )
    assertEquals(EffortComparison.SAME, outcome.effortComparison)
    assertEquals(isoUtc(f.lifecycle.nowMillis), outcome.answeredAt)
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
    assertEquals("This survey is archived. Reopen it to make changes.", f.ui.readOnlyMessage)
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
    assertEquals("Republish", f.ui.saveActionLabel)

    // Switch to DRAFT mode while holding a working edit: working edit is preserved.
    f.viewModel.updateDetails { it.copy(title = "Draft title in progress") }
    assertTrue(f.ui.hasUnpublishedChanges)
    f.viewModel.setLifecycleState(SurveyLifecycleState.DRAFT)

    assertEquals(SurveyLifecycleState.DRAFT, f.ui.lifecycleState)
    assertTrue(f.ui.isDraft)
    assertFalse(f.ui.isReadOnly)
    assertEquals("Save draft", f.ui.saveActionLabel)
    assertEquals("Draft title in progress", f.ui.details.title)
    assertTrue(f.ui.hasUnpublishedChanges)

    // Switching from DRAFT to PUBLISHED commits pending working edits and updates the save label.
    f.viewModel.setLifecycleState(SurveyLifecycleState.PUBLISHED)
    assertEquals(SurveyLifecycleState.PUBLISHED, f.ui.lifecycleState)
    assertFalse(f.ui.isDraft)
    assertEquals("Republish", f.ui.saveActionLabel)
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
    assertEquals("Publish survey", draftOptions.first().label)
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
    assertEquals("Switch to draft", publishedOptions.first().label)
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
    assertEquals("Reopen survey", closedOptions.first().label)

    val archivedOptions = lifecycleActionOptions(SurveyLifecycleState.ARCHIVED)
    assertEquals(
      listOf(SurveyLifecycleState.PUBLISHED, SurveyLifecycleState.DRAFT),
      archivedOptions.map { it.targetState },
    )
    assertEquals("Reopen survey", archivedOptions.first().label)
  }

  @Test
  fun withoutTheLifecycleUseCase_closingIsOff() {
    val f = Fixture()
    val viewModel = seededSurveyEditorViewModel(f.store)
    viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    assertEquals(SurveyLifecycleState.PUBLISHED, viewModel.ui.lifecycleState)
  }
}
