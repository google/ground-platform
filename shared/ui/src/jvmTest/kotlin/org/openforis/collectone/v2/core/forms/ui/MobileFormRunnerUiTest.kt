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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.openforis.collectone.v2.core.forms.model.FinalizationResult

/**
 * Next / Skip / Review / Submit navigation and repeat hubs in the one-question-per-screen runner.
 */
@OptIn(ExperimentalTestApi::class)
class MobileFormRunnerUiTest {

  private fun ComposeUiTest.showRunner(
    controller: FormWizardController,
    onSubmitted: (FinalizationResult.Success) -> Unit = {},
  ) = setContent { AppTheme { MobileFormRunner(controller, onSubmitted = onSubmitted) } }

  @Test
  fun header_showsTheFormTitleAndStepPosition() = runComposeUiTest {
    showRunner(RunnerTestForms.speciesAndNotes())

    onNodeWithText("Tree Survey").assertExists()
    onNodeWithText("Step 1 of 3").assertExists()
  }

  @Test
  fun header_showsTheHostsContextInsteadOfTheStepPosition() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    setContent {
      AppTheme { MobileFormRunner(controller, headerContext = { Text("Plot 7 • GeoID 42") }) }
    }

    onNodeWithText("Tree Survey").assertExists()
    onNodeWithText("Plot 7 • GeoID 42").assertExists()
    onNodeWithText("Step 1 of 3").assertDoesNotExist()
  }

  @Test
  fun header_hasOneCloseButtonAndNoStepsButton() = runComposeUiTest {
    var closed = 0
    setContent {
      AppTheme { MobileFormRunner(RunnerTestForms.speciesAndNotes(), onClose = { closed++ }) }
    }

    onNodeWithText("Steps (3)").assertDoesNotExist()
    onNodeWithContentDescription("Close form").performClick()

    assertEquals(1, closed)
  }

  @Test
  fun stepChip_opensTheStepSheet_whichClosesWithItsCloseButton() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    showRunner(controller)
    onNodeWithText("All form steps (3)").assertDoesNotExist()

    onNodeWithText("1 / 3").performClick()

    onNodeWithText("All form steps (3)").assertExists()
    // The step's own Back / Next bar stays under the sheet's scrim, not inside the list.
    onNodeWithContentDescription("Close list").performClick()

    assertFalse(controller.isOverviewOpen)
    onNodeWithText("All form steps (3)").assertDoesNotExist()
    assertEquals(0, controller.currentStepIndex)
  }

  @Test
  fun stepSheet_tappingAStep_jumpsToItAndClosesTheSheet() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    showRunner(controller)

    onNodeWithText("1 / 3").performClick()
    onNodeWithText("2. ", substring = true).performClick()

    assertEquals(1, controller.currentStepIndex)
    assertFalse(controller.isOverviewOpen)
    onNodeWithText("2 / 3").assertExists()
  }

  @Test
  fun review_editIconJumpsBackToTheQuestion() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    controller.updateString("/data/species", "Baobab")
    controller.jumpToStep(controller.steps.lastIndex)
    showRunner(controller)

    onNodeWithText("Review answers").assertExists()
    onAllNodesWithContentDescription("Edit")[0].performClick()

    assertEquals(0, controller.currentStepIndex)
  }

  @Test
  fun next_onAnUnansweredRequiredQuestion_staysAndShowsTheError() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    showRunner(controller)

    onNodeWithText("Next").performClick()

    assertEquals(0, controller.currentStepIndex)
    onNodeWithText("Species is required").assertExists()
  }

  @Test
  fun next_afterAnsweringTheQuestion_movesToTheNextStep() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    showRunner(controller)

    onNode(hasSetTextAction()).performTextInput("Baobab")
    onNodeWithText("Next").performClick()

    onNodeWithText("Step 2 of 3").assertExists()
  }

  @Test
  fun lastQuestion_offersSkipAndReviewInsteadOfNext() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    controller.updateString("/data/species", "Baobab")
    controller.nextStep()
    showRunner(controller)

    onNodeWithText("Skip").assertExists()
    onNodeWithText("Review").performClick()

    assertIs<FormWizardStep.SummaryStep>(controller.currentStep)
  }

  @Test
  fun submit_onAValidForm_reportsTheFinalizedSubmission() = runComposeUiTest {
    val controller = RunnerTestForms.speciesAndNotes()
    controller.updateString("/data/species", "Baobab")
    controller.jumpToStep(controller.steps.lastIndex)
    var submitted: FinalizationResult.Success? = null
    showRunner(controller, onSubmitted = { submitted = it })

    onNodeWithText("Submit").performClick()

    assertNotNull(submitted)
    onNodeWithText("✓ Form finalized successfully").assertExists()
  }

  @Test
  fun repeatHub_withoutEntries_saysSo() = runComposeUiTest {
    showRunner(RunnerTestForms.branchRepeat())

    onNodeWithText("No repeat entries added yet.").assertExists()
  }

  @Test
  fun repeatHub_addEntry_opensTheNewEntrysFirstQuestion() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    showRunner(controller)

    onNodeWithText("Add Branch entry").performClick()

    onNodeWithText("Branch length (m)").assertExists()
    assertEquals(1, controller.currentStep.repeatContext?.repeatIndex)
  }

  @Test
  fun repeatHub_tappingAnEntry_opensIt() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.openRepeatHub("/data/branch")
    showRunner(controller)

    onNodeWithText("1 entry").assertExists()
    onNodeWithText("Not started", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithContentDescription("Not started", useUnmergedTree = true).assertExists()
    onNodeWithText("Add another Branch entry").assertExists()
    onNodeWithText("Branch #1").performClick()

    assertEquals(1, controller.currentStep.repeatContext?.repeatIndex)
  }

  @Test
  fun cascadingSelect_filtersOptionsByParentAndClearsStaleSelectionWhenParentChanges() =
    runComposeUiTest {
      val controller = RunnerTestForms.cascadingCountryRegion()
      showRunner(controller)

      // Skipping Country leaves Region prompting the collector to answer Country first.
      onNodeWithText("Skip").performClick()
      onNodeWithText("Select an answer for 'Country' first").assertExists()

      // Go back, pick Kenya, and advance to Region: Nairobi + Other show, Kampala does not.
      onNodeWithText("Back").performClick()
      onNodeWithText("Kenya").performClick()
      onNodeWithText("Next").performClick()
      onNodeWithText("Nairobi").assertExists()
      onNodeWithText("Other").assertExists()
      onAllNodes(hasText("Kampala")).let { assertEquals(0, it.fetchSemanticsNodes().size) }

      // Select Nairobi, then go back and switch Country to Uganda: Nairobi is pruned and Kampala
      // replaces it on the Region screen.
      onNodeWithText("Nairobi").performClick()
      assertEquals(
        "nairobi",
        controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value,
      )
      onNodeWithText("Back").performClick()
      onNodeWithText("Uganda").performClick()
      assertEquals(
        null,
        controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value,
      )
      onNodeWithText("Next").performClick()
      onNodeWithText("Kampala").assertExists()
      onNodeWithText("Other").assertExists()
      onAllNodes(hasText("Nairobi")).let { assertEquals(0, it.fetchSemanticsNodes().size) }
    }

  @Test
  fun repeatHub_deletingAnEmptyEntry_needsNoConfirmation() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.openRepeatHub("/data/branch")
    showRunner(controller)

    onNodeWithContentDescription("Delete Branch #1").performClick()

    onNodeWithText("No repeat entries added yet.").assertExists()
  }

  @Test
  fun repeatHub_deletingAnEntryWithAnswers_asksFirst() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.updateDouble("/data/branch[1]/length_m", 2.5)
    controller.openRepeatHub("/data/branch")
    showRunner(controller)

    onNodeWithContentDescription("Delete Branch #1").performClick()
    onNodeWithText("Delete Branch #1?").assertExists()
    onNodeWithText("Its 1 answer will be deleted too. This can't be undone.").assertExists()
    onNodeWithText("Cancel").performClick()
    onNodeWithText("Branch #1").assertExists()

    onNodeWithContentDescription("Delete Branch #1").performClick()
    onNodeWithText("Delete").performClick()
    onNodeWithText("No repeat entries added yet.").assertExists()
  }

  @Test
  fun entryBar_namesTheEntryAndLeadsBackToTheList() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    showRunner(controller)

    onNodeWithText("Branch #1").assertExists()
    onNodeWithText("Entry #1 of 1").assertExists()
    // Adding happens from the list, not mid-entry; a single entry hides prev/next arrows.
    onNodeWithText("Add", substring = true).assertDoesNotExist()
    onNodeWithContentDescription("Previous entry").assertDoesNotExist()
    onNodeWithContentDescription("Next entry").assertDoesNotExist()
    onNodeWithText("All entries").performClick()

    assertIs<FormWizardStep.RepeatHubStep>(controller.currentStep)
  }

  @Test
  fun entryBar_withMultipleEntries_stepsBetweenEntries() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.openRepeatInstance("/data/branch[1]")
    showRunner(controller)

    onNodeWithText("Branch #1").assertExists()
    onNodeWithText("Entry #1 of 2").assertExists()
    onNodeWithContentDescription("Previous entry").assertIsNotEnabled()
    onNodeWithContentDescription("Next entry").assertIsEnabled().performClick()

    onNodeWithText("Branch #2").assertExists()
    onNodeWithText("Entry #2 of 2").assertExists()
    onNodeWithContentDescription("Next entry").assertIsNotEnabled()
    onNodeWithContentDescription("Previous entry").assertIsEnabled().performClick()

    onNodeWithText("Branch #1").assertExists()
    assertEquals(1, controller.currentStep.repeatContext?.repeatIndex)
  }

  @Test
  fun entryBar_delete_returnsToTheList() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.updateDouble("/data/branch[1]/length_m", 2.5)
    showRunner(controller)

    onNodeWithContentDescription("Delete Branch #1").performClick()
    onNodeWithText("Delete").performClick()

    assertIs<FormWizardStep.RepeatHubStep>(controller.currentStep)
    onNodeWithText("No repeat entries added yet.").assertExists()
  }
}
