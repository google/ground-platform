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
package org.openforis.collectone.v2.web.ui.wizard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.data.seed.SampleSurveyEditorData
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.surveyEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/**
 * Field validation of sample plots in the create-survey wizard, built on its interpretation Form.
 */
class WizardPlotReviewTest {
  private val state = WebAppState(dataHolder = sampleDataHolder())
  private val wizard = state.createSurveyWizard

  private val ui
    get() = wizard.uiState.value

  private val forms: List<SurveyEditorForm>
    get() = state.surveyEditor.uiState.value.draft.forms

  private fun form(key: String?) = forms.single { it.key == key }.form

  /** Walks a visual interpretation survey of sample plots to Step 4 (`Map features`). */
  private fun reachSamplePlots(purpose: String = "lulc_training_data") {
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    wizard.updateTitle("Land cover with field checks")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.VISUAL_CLASSIFICATION)
    wizard.togglePurposePack(purpose)
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    assertEquals(CreateSurveyWizardStep.MAP_FEATURES, ui.currentStep)
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, ui.mapFeatureSource)
  }

  @Test
  fun fieldValidation_turnsTheInterpretationFormIntoTheDeskCheck() {
    reachSamplePlots()
    assertTrue(ui.offersPlotFieldValidation)
    assertFalse(assertNotNull(ui.plotReview).options.fieldValidation)
    val primary = assertNotNull(ui.primaryForm)
    val formCount = forms.size

    wizard.setPlotFieldValidation(true)
    val review = assertNotNull(ui.plotReview)
    assertEquals(primary.key, review.deskFormKey)
    assertTrue(review.options.fieldValidation)
    // The interpretation Form is the desk check, plus one mobile-only field Form.
    assertEquals(formCount + 1, forms.size)
    val desk = form(primary.key)
    assertEquals(primary.form.title, desk.title)
    assertEquals(FormAvailability.WEB, desk.availability)
    assertTrue(desk.questions.any { it.name == PlotReviewWorkflow.NEEDS_FIELD_CHECK })
    assertTrue(desk.saveTo.status.enabled)
    assertEquals(FormAvailability.MOBILE, form(review.fieldFormKey).availability)
  }

  @Test
  fun fieldValidation_survivesTheWizardsFormSync() {
    reachSamplePlots()
    wizard.setPlotFieldValidation(true)
    val options = assertNotNull(ui.plotReview).options
    wizard.updatePlotFieldValidation(options.copy(autoFlagBelowConfidence = 30, spotCheckCount = 4))

    // Moving on syncs the wizard's Forms, which leaves the review's Forms alone.
    wizard.nextStep() // FORM_QUESTIONS
    val review = assertNotNull(ui.plotReview)
    assertEquals(30, review.options.autoFlagBelowConfidence)
    assertEquals(4, review.options.spotCheckCount)
    assertEquals(FormAvailability.WEB, form(review.deskFormKey).availability)
    assertEquals(FormAvailability.MOBILE, form(review.fieldFormKey).availability)

    // A new purpose replaces the interpretation Form; the review moves to the new one.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("deforestation_activity_data")
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    val moved = assertNotNull(ui.plotReview)
    assertEquals(ui.primaryForm?.key, moved.deskFormKey)
    assertEquals(30, moved.options.autoFlagBelowConfidence)
    assertEquals(4, moved.options.spotCheckCount)
    assertEquals(1, forms.count { it.key == moved.fieldFormKey })
  }

  @Test
  fun turningFieldValidationOff_restoresTheInterpretationForm() {
    reachSamplePlots()
    val before = assertNotNull(ui.primaryForm)
    wizard.setPlotFieldValidation(true)

    wizard.setPlotFieldValidation(false)
    val review = assertNotNull(ui.plotReview)
    assertFalse(review.options.fieldValidation)
    assertNull(review.fieldFormKey)
    val restored = form(before.key)
    assertEquals(before.form.questions.map { it.name }, restored.questions.map { it.name })
    assertEquals(before.form.availability, restored.availability)
    assertFalse(restored.saveTo.status.enabled)
  }

  @Test
  fun leavingSamplePlots_removesTheReview() {
    reachSamplePlots()
    wizard.setPlotFieldValidation(true)

    wizard.selectMapFeatureSource(WizardMapFeatureSource.FIELD_FREEFORM)
    assertFalse(ui.offersPlotFieldValidation)
    assertTrue(
      forms.none { it.form.questions.any { q -> q.name == PlotReviewWorkflow.FIELD_LAND_COVER } }
    )
    assertTrue(
      forms.none { it.form.questions.any { q -> q.name == PlotReviewWorkflow.NEEDS_FIELD_CHECK } }
    )
  }
}
