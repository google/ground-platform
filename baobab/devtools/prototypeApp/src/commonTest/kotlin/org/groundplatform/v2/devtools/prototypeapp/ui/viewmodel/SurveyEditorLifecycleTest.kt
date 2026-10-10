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
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCoverage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyLifecycleState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SampleGenerationOutcome
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoUtc
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SurveyLifecycleFixture
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection

/** Closing, archiving, and reopening a survey from the Survey editor, and the outcome card. */
class SurveyEditorLifecycleTest {
  private class Fixture(activeSurveyId: String = "survey-single-point-land-use") {
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
    assertEquals(PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL, outcome.answeredBy)
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
    val f = Fixture(activeSurveyId = PrototypeFakeSurveysData.CLOSED_SURVEY_ID)
    assertEquals(SurveyLifecycleState.CLOSED, f.ui.lifecycleState)
    assertEquals(PrototypeFakeSurveysData.CLOSED_SURVEY_CLOSED_AT, f.ui.closedAt)
    assertTrue(f.ui.isReadOnly)
    assertNull(f.ui.outcomeCard)
  }

  @Test
  fun withoutTheLifecycleUseCase_closingIsOff() {
    val f = Fixture()
    val viewModel = seededSurveyEditorViewModel(f.store)
    viewModel.setLifecycleState(SurveyLifecycleState.CLOSED)
    assertEquals(SurveyLifecycleState.PUBLISHED, viewModel.ui.lifecycleState)
  }
}
