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
package org.groundplatform.v2.web.ui.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.sampleDataHolder
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.data.seed.SampleSurveyEditorData
import org.groundplatform.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.groundplatform.v2.web.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.WebPage

@OptIn(ExperimentalTestApi::class)
class CreateSurveyWizardPageTest {

  @Test
  fun clickingCloseButtonPromptsConfirmationBeforeDiscardingSurvey() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val workingId = state.createSurveyWizard.uiState.value.workingSurveyId

      setContent { MaterialTheme { CreateSurveyWizardPage(state = state) } }
      waitForIdle()

      // Clicking X ("Cancel survey creation") opens the discard confirmation dialog without
      // immediately discarding the survey.
      onNodeWithContentDescription("Cancel survey creation").performClick()
      waitForIdle()

      onNodeWithText("Discard survey?").assertIsDisplayed()
      onNodeWithText("If you leave now, this survey and any changes you've made will be lost.")
        .assertIsDisplayed()
      assertEquals(WebPage.CREATE_SURVEY, state.activePage)
      assertTrue(state.surveys.any { it.id == workingId })

      // Clicking "Keep editing" dismisses the dialog and keeps the survey in the wizard.
      onNodeWithText("Keep editing").performClick()
      waitForIdle()

      assertEquals(WebPage.CREATE_SURVEY, state.activePage)
      assertTrue(state.surveys.any { it.id == workingId })

      // Clicking X again and confirming "Discard" discards the survey and leaves the wizard.
      onNodeWithContentDescription("Cancel survey creation").performClick()
      waitForIdle()
      onNodeWithText("Discard").performClick()
      waitForIdle()

      assertEquals(WebPage.WEB_SURVEYS, state.activePage)
      assertFalse(state.surveys.any { it.id == workingId })
    }

  @Test
  fun surveyTitleIsFocusedWhenTheDesignerOpens_withoutFlaggingItOnBlur() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

      setContent { MaterialTheme { CreateSurveyWizardPage(state = state) } }
      waitForIdle()

      val title = onNode(hasSetTextAction() and hasText("Survey title *"))
      title.assertIsFocused()

      // Moving on without typing doesn't flag the untouched title as missing.
      onNode(hasSetTextAction() and hasText("Description", substring = true)).performClick()
      waitForIdle()
      title.assertIsNotFocused()
      onAllNodesWithText("Enter a survey title to continue.").assertCountEquals(0)
    }

  @Test
  fun choosingHowMapFeaturesAreCreatedCollapsesTheChoice_untilChange() =
    runDesktopComposeUiTest(width = 1280, height = 1400) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Map features test")
      wizard.nextStep() // MODE_AND_PURPOSE
      // A purpose that doesn't call for a particular way of creating map features.
      wizard.togglePurposePack("eudr_due_diligence")
      wizard.nextStep() // SURVEY_AREA
      state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
      wizard.nextStep() // MAP_FEATURES
      assertEquals(CreateSurveyWizardStep.MAP_FEATURES, wizard.uiState.value.currentStep)

      setContent { MaterialTheme { CreateSurveyWizardPage(state = state) } }
      waitForIdle()

      val sampling = WizardMapFeatureSource.SAMPLE_PLOTS.label
      val importing = WizardMapFeatureSource.IMPORT_FILE.label
      // All options are offered until one is chosen.
      onNodeWithText(importing).assertIsDisplayed()
      onAllNodesWithText("Change").assertCountEquals(0)

      onNodeWithText(sampling).performClick()
      waitForIdle()
      assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, wizard.uiState.value.mapFeatureSource)
      // Collapsed to the chosen option; the others are gone.
      onNodeWithText(sampling).assertIsDisplayed()
      onAllNodesWithText(importing).assertCountEquals(0)

      onNodeWithText("Change").performClick()
      waitForIdle()
      onNodeWithText(importing).assertIsDisplayed()
    }

  @Test
  fun mapFeatureSourcePreselectedByThePurposeStartsCollapsed() =
    runDesktopComposeUiTest(width = 1280, height = 1400) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Map features test")
      wizard.nextStep() // MODE_AND_PURPOSE
      // LULC training data calls for sample plots.
      wizard.togglePurposePack("lulc_training_data")
      wizard.nextStep() // SURVEY_AREA
      state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
      wizard.nextStep() // MAP_FEATURES
      assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, wizard.uiState.value.mapFeatureSource)
      assertTrue(wizard.uiState.value.mapFeatureSourcePreselected)

      setContent { MaterialTheme { CreateSurveyWizardPage(state = state) } }
      waitForIdle()

      val importing = WizardMapFeatureSource.IMPORT_FILE.label
      onNodeWithText(WizardMapFeatureSource.SAMPLE_PLOTS.label).assertIsDisplayed()
      onNodeWithText("Preselected for the purpose", substring = true).assertIsDisplayed()
      onAllNodesWithText(importing).assertCountEquals(0)

      onNodeWithText("Change").performClick()
      waitForIdle()
      onNodeWithText(importing).assertIsDisplayed()
    }

  @Test
  fun mapStaysInViewWhileScrollingTheMapLayerSettings() =
    runDesktopComposeUiTest(width = 1280, height = 1000) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Map features test")
      wizard.nextStep() // MODE_AND_PURPOSE
      wizard.togglePurposePack("lulc_training_data")
      wizard.nextStep() // SURVEY_AREA
      state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
      wizard.nextStep() // MAP_FEATURES
      wizard.selectMapFeatureSource(WizardMapFeatureSource.SAMPLE_PLOTS)

      setContent { MaterialTheme { CreateSurveyWizardPage(state = state) } }
      waitForIdle()

      val page =
        onNode(hasScrollAction() and hasAnyDescendant(hasText("How are map features created?")))
      val settings =
        onNode(
          hasScrollAction() and
            hasAnyDescendant(hasText("Primary map layer settings")) and
            !hasAnyDescendant(hasText("How are map features created?"))
        )
      // Scroll the page down to the map, then the settings (the sample design is longer than the
      // map) all the way down with their own scroll.
      page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
      settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
      waitForIdle()
      onNodeWithText("Random seed").assertIsDisplayed()
      onNodeWithContentDescription("Zoom in").assertIsDisplayed()
    }
}
