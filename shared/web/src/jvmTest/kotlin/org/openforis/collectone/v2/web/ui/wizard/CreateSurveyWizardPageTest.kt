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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.data.seed.SampleSurveyEditorData
import org.openforis.collectone.v2.web.CreateSurveyWizardDestination
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.navigation.CreateSurveyRoute
import org.openforis.collectone.v2.web.navigation.NavigationRecorder
import org.openforis.collectone.v2.web.navigation.NavigationRequest
import org.openforis.collectone.v2.web.navigation.RecordingPageNavigation
import org.openforis.collectone.v2.web.navigation.SurveysRoute
import org.openforis.collectone.v2.web.ui.ProvideWebSession
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.surveyEditor

@OptIn(ExperimentalTestApi::class)
class CreateSurveyWizardPageTest {

  @Test
  fun purposeStep_showsASpinnerInsteadOfOnlyOther_whileTheLibraryLoads() =
    runDesktopComposeUiTest(width = 1280, height = 1400) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      state.createSurveyWizard.updateTitle("Loading test")
      state.createSurveyWizard.nextStep() // MODE_AND_PURPOSE

      setContent {
        MaterialTheme {
          CreateSurveyWizardPage(
            uiState = state.createSurveyWizard.uiState.value,
            actions = state.createSurveyWizard,
            editorUiState = state.surveyEditor.uiState.value,
            editorActions = state.surveyEditor,
            isLibraryLoading = true,
            header = { _, _ -> },
          )
        }
      }
      waitForIdle()

      onNodeWithContentDescription("Loading purposes…").assertIsDisplayed()
      onAllNodesWithText("Loading purposes…").assertCountEquals(0)
      onAllNodesWithText("Other").assertCountEquals(0)
    }

  @Test
  fun purposeStep_listsOrganizationPacksWithGlobalOnes_andOffersStarterTemplates() =
    runDesktopComposeUiTest(width = 1280, height = 2400) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Sections test")
      wizard.nextStep() // MODE_AND_PURPOSE
      wizard.togglePurposePack("eudr_due_diligence")

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      // No separate "<organization> purposes" section; the organization pack carries its badge.
      val orgName = assertNotNull(wizard.uiState.value.selectedOrganization).name
      onAllNodesWithText("$orgName purposes").assertCountEquals(0)
      onNodeWithText("Coop annual certification audit").assertIsDisplayed()
      assertTrue(onAllNodesWithText(orgName).fetchSemanticsNodes().isNotEmpty())
      onNodeWithContentDescription("Loading purposes…").assertDoesNotExist()

      // The purpose's starter templates are offered, and the program chips follow.
      onNodeWithText("Starting template").assertIsDisplayed()
      // "UK Forest Risk Commodities" is both a starter template and a program chip.
      onAllNodesWithText("UK Forest Risk Commodities").assertCountEquals(2)
      onNodeWithText("Reports to: EU Deforestation Regulation (EUDR)").assertIsDisplayed()
      onNodeWithText("Reporting programs & frameworks").assertIsDisplayed()
      onNodeWithText("Fairtrade").assertIsDisplayed()
    }

  @Test
  fun clickingCloseButtonPromptsConfirmationBeforeDiscardingSurvey() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val workingId = state.createSurveyWizard.uiState.value.workingSurveyId
      val navigation = NavigationRecorder(state, start = CreateSurveyRoute())

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      // Clicking X ("Cancel survey creation") opens the discard confirmation dialog without
      // immediately discarding the survey.
      onNodeWithContentDescription("Cancel survey creation").performClick()
      waitForIdle()

      onNodeWithText("Discard survey?").assertIsDisplayed()
      onNodeWithText("If you leave now, this survey and any changes you've made will be lost.")
        .assertIsDisplayed()
      assertTrue(navigation.requests.isEmpty())
      assertTrue(state.viewModels.dashboard.uiState.value.surveys.any { it.id == workingId })

      // Clicking "Keep editing" dismisses the dialog and keeps the survey in the wizard.
      onNodeWithText("Keep editing").performClick()
      waitForIdle()

      assertTrue(navigation.requests.isEmpty())
      assertTrue(state.viewModels.dashboard.uiState.value.surveys.any { it.id == workingId })

      // Clicking X again and confirming "Discard" discards the survey and leaves the wizard.
      onNodeWithContentDescription("Cancel survey creation").performClick()
      waitForIdle()
      onNodeWithText("Discard").performClick()
      waitForIdle()

      assertEquals<List<NavigationRequest>>(
        listOf(NavigationRequest.Back(fallback = SurveysRoute)),
        navigation.requests,
      )
      assertFalse(state.viewModels.dashboard.uiState.value.surveys.any { it.id == workingId })
      navigation.close()
    }

  @Test
  fun surveyTitleIsFocusedWhenTheDesignerOpens_withoutFlaggingItOnBlur() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
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
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Map features test")
      wizard.nextStep() // MODE_AND_PURPOSE
      // A purpose that doesn't call for a particular way of creating map features.
      wizard.togglePurposePack("eudr_due_diligence")
      wizard.nextStep() // SURVEY_AREA
      state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
      wizard.nextStep() // MAP_FEATURES
      assertEquals(CreateSurveyWizardStep.MAP_FEATURES, wizard.uiState.value.currentStep)

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      val sampling = "Generate sample plots"
      val importing = "Import map features (GeoJSON / KML)"
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
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      val importing = "Import map features (GeoJSON / KML)"
      onNodeWithText("Generate sample plots").assertIsDisplayed()
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
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Map features test")
      wizard.nextStep() // MODE_AND_PURPOSE
      wizard.togglePurposePack("lulc_training_data")
      wizard.nextStep() // SURVEY_AREA
      state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
      wizard.nextStep() // MAP_FEATURES
      wizard.selectMapFeatureSource(WizardMapFeatureSource.SAMPLE_PLOTS)

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
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

  @Test
  fun returningToWizardWithUnsavedDraftDisplaysContinueOrDiscardDialog() =
    runDesktopComposeUiTest(width = 1280, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val firstWorkingId = state.createSurveyWizard.uiState.value.workingSurveyId
      state.createSurveyWizard.updateTitle("Previous Draft Survey")

      // Leave the wizard without canceling and come back.
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      onNodeWithText("Continue editing unsaved draft?").assertIsDisplayed()
      onNodeWithText("Continue editing").performClick()
      waitForIdle()

      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)
      assertEquals("Previous Draft Survey", state.createSurveyWizard.uiState.value.title)
      assertEquals(firstWorkingId, state.createSurveyWizard.uiState.value.workingSurveyId)

      // Leave and re-open again, then click "Discard draft".
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      waitForIdle()

      onNodeWithText("Continue editing unsaved draft?").assertIsDisplayed()
      onNodeWithText("Discard draft").performClick()
      waitForIdle()

      assertFalse(state.createSurveyWizard.uiState.value.isPromptingUnsavedDraft)
      assertFalse(state.viewModels.dashboard.uiState.value.surveys.any { it.id == firstWorkingId })
      assertEquals("", state.createSurveyWizard.uiState.value.title)
    }

  @Test
  fun sampleDesignPreview_enablesUpdatePreviewButtonAfterChangingDesign() =
    runDesktopComposeUiTest(width = 1280, height = 1400) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      wizard.updateTitle("Sampling preview button test")
      wizard.nextStep() // MODE_AND_PURPOSE
      wizard.togglePurposePack("lulc_training_data")
      wizard.nextStep() // SURVEY_AREA (keep default Kenya survey area)
      wizard.nextStep() // MAP_FEATURES
      wizard.selectMapFeatureSource(WizardMapFeatureSource.SAMPLE_PLOTS)

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      val page =
        onNode(hasScrollAction() and hasAnyDescendant(hasText("How are map features created?")))
      val settings =
        onNode(
          hasScrollAction() and
            hasAnyDescendant(hasText("Primary map layer settings")) and
            !hasAnyDescendant(hasText("How are map features created?"))
        )
      page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
      settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
      waitForIdle()

      onNodeWithText("Show preview").assertIsDisplayed().assertIsEnabled().performClick()
      waitForIdle()

      onNodeWithText("Update preview").assertIsDisplayed().assertIsEnabled()

      // Change sampling design parameters and verify "Update preview" remains enabled and clickable
      val layerKey = wizard.uiState.value.primaryMapLayer!!.key
      state.surveyEditor.updateSampleDesign(layerKey) { it.copy(seed = 42L) }
      waitForIdle()

      settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
      waitForIdle()
      onNodeWithText("Preview is out of date — update preview").assertIsDisplayed()
      settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
      waitForIdle()
      onNodeWithText("Update preview").assertIsDisplayed().assertIsEnabled().performClick()
      waitForIdle()

      onAllNodesWithText("Preview is out of date — update preview").assertCountEquals(0)
      onNodeWithText("Update preview").assertIsDisplayed().assertIsEnabled()
    }

  private val organizationHint = "Running a survey under an organization unlocks"

  @Test
  fun overviewStep_showsOrganizationSelector_forOrganizationMembers() =
    runDesktopComposeUiTest(width = 1280, height = 1400) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      onNodeWithText(organizationHint, substring = true).assertIsDisplayed()
    }

  @Test
  fun overviewStep_hidesOrganizationSelector_whenNotAMemberOfAnyOrganization() =
    runDesktopComposeUiTest(width = 1280, height = 1400) {
      val dataHolder = sampleDataHolder()
      runDirect {
        dataHolder.localStore.transaction { organizations().forEach { deleteOrganization(it.id) } }
      }
      val state = WebAppState(dataHolder = dataHolder)
      state.createSurveyWizard.startWizard(null)
      assertTrue(state.createSurveyWizard.uiState.value.memberOrganizations.isEmpty())

      setContent {
        MaterialTheme {
          ProvideWebSession(state) {
            CreateSurveyWizardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      onAllNodesWithText(organizationHint, substring = true).assertCountEquals(0)
    }
}
