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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.forms.model.FinalizationResult

/** Submission and repeat hubs in the stacked (web) form runner. */
@OptIn(ExperimentalTestApi::class)
class CompactFormRunnerUiTest {

  private fun ComposeUiTest.showRunner(
    controller: FormWizardController,
    onSubmitted: (FinalizationResult.Success) -> Unit = {},
  ) = setContent {
    AppTheme {
      CompactFormRunner(
        controller,
        modifier = Modifier.verticalScroll(rememberScrollState()),
        onSubmitted = onSubmitted,
      )
    }
  }

  @Test
  fun header_showsTheFormTitleAndAnsweredCount() = runComposeUiTest {
    showRunner(RunnerTestForms.speciesAndNotes())

    onNodeWithText("Tree Survey").assertExists()
    onNodeWithText("0 of 2 answered").assertExists()
  }

  @Test
  fun submit_withAnUnansweredRequiredQuestion_flagsItInsteadOfSubmitting() = runComposeUiTest {
    var submitted: FinalizationResult.Success? = null
    showRunner(RunnerTestForms.speciesAndNotes(), onSubmitted = { submitted = it })

    onNodeWithText("Review & submit").performClick()

    assertNull(submitted)
    onNodeWithText("1 question needs attention before submitting.").assertExists()
    onNodeWithText("Species is required").assertExists()
  }

  @Test
  fun submit_onceTheRequiredQuestionIsAnswered_reportsTheSubmission() = runComposeUiTest {
    var submitted: FinalizationResult.Success? = null
    showRunner(RunnerTestForms.speciesAndNotes(), onSubmitted = { submitted = it })

    onAllNodes(hasSetTextAction())[0].performTextInput("Baobab")
    onNodeWithText("Submit").performClick()

    assertNotNull(submitted)
  }

  @Test
  fun repeatHub_withoutEntries_saysSo() = runComposeUiTest {
    showRunner(RunnerTestForms.branchRepeat())

    onNodeWithText("No repeat entries added yet.").assertExists()
  }

  @Test
  fun repeatSection_addEntry_framesTheEntrysQuestionsInline() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    showRunner(controller)

    onNodeWithText("Add Branch entry").performClick()

    onNodeWithText("Branch #1").assertExists()
    onNodeWithText("Not started").assertExists()
    onNodeWithText("Branch length (m)").assertExists()
    onNodeWithText("Add another Branch entry").assertExists()
    // The entry's frame names it, so cards no longer repeat their position or a dead link.
    onNodeWithText("Entry #1 of 1", substring = true).assertDoesNotExist()
    onNodeWithText("All entries").assertDoesNotExist()
    onNodeWithText("Edit").assertDoesNotExist()
    assertEquals(1, controller.steps.count { it.repeatContext?.repeatIndex == 1 })
  }

  @Test
  fun cascadingSelect_filtersOptionsPrunesStaleChildAndReopensCardInWebRunner() = runComposeUiTest {
    val controller = RunnerTestForms.cascadingCountryRegion()
    showRunner(controller)

    // Before Country is answered, Region prompts the collector to select Country first.
    onNodeWithText("Select an answer for 'Country' first").assertExists()

    // Selecting Kenya reveals Nairobi + Other on Region (and auto-collapses Country).
    onNodeWithText("Kenya").performClick()
    onNodeWithText("Nairobi").assertExists()
    onNodeWithText("Other").assertExists()
    onAllNodes(hasText("Kampala")).let { assertEquals(0, it.fetchSemanticsNodes().size) }

    // Selecting Nairobi answers Region (2 of 2 answered) and auto-collapses Region.
    onNodeWithText("Nairobi").performClick()
    onNodeWithText("2 of 2 answered").assertExists()

    // Re-open Country and switch to Uganda: stale "nairobi" is pruned (back to 1 of 2 answered)
    // and Region automatically re-expands showing Kampala + Other.
    onNodeWithText("Country").performClick()
    onNodeWithText("Uganda").performClick()
    assertNull(
      controller.formState.findFieldState("/data/region")?.value?.scalar_value?.string_value
    )
    onNodeWithText("1 of 2 answered").assertExists()
    onNodeWithText("Kampala").assertExists()
    onNodeWithText("Other").assertExists()
    onAllNodes(hasText("Nairobi")).let { assertEquals(0, it.fetchSemanticsNodes().size) }
  }

  @Test
  fun repeatSection_entryStatus_followsItsAnswers() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    showRunner(controller)

    onNode(hasSetTextAction()).performTextInput("3")

    onNodeWithText("Complete").assertExists()
  }

  @Test
  fun repeatSection_deletingAnEntryWithAnswers_asksFirst() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.addRepeatInstanceAndOpen("/data/branch")
    controller.updateDouble("/data/branch[1]/length_m", 2.5)
    showRunner(controller)

    onNodeWithText("2 entries").assertExists()
    onNodeWithContentDescription("Delete Branch #1").performClick()
    onNodeWithText("Delete Branch #1?").assertExists()
    onNodeWithText("Delete").performClick()

    onNodeWithText("1 entry").assertExists()
    assertNull(onAllNodesWithText("Branch #2").fetchSemanticsNodes().firstOrNull())
  }

  @Test
  fun repeatSection_deletingAnEmptyEntry_needsNoConfirmation() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    showRunner(controller)

    onNodeWithContentDescription("Delete Branch #1").performClick()

    onNodeWithText("No repeat entries added yet.").assertExists()
  }

  @Test
  fun repeatSection_readOnly_hasNoDeleteAndADisabledAdd() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    setContent {
      AppTheme {
        CompactFormRunner(
          controller,
          modifier = Modifier.verticalScroll(rememberScrollState()),
          readOnly = true,
        )
      }
    }

    onNodeWithContentDescription("Delete Branch #1").assertDoesNotExist()
    onNodeWithText("Add another Branch entry").assertIsNotEnabled()
  }

  @Test
  fun datePicker_inCompactKmlBalloonViewport_rendersWithoutOverlappingRowsOrButtons() =
    runDesktopComposeUiTest(width = 356, height = 280) {
      val controller =
        formController(
          title = "Farm Survey",
          model =
            """
            <instance><data id="farms" version="1"><visit_date/></data></instance>
            <bind nodeset="/data/visit_date" type="date"/>
            """,
          body =
            """
            <input ref="/data/visit_date"><label>Date of farm visit</label></input>
            """,
        )
      setContent {
        AppTheme {
          CompactFormRunner(
            controller,
            modifier = Modifier.verticalScroll(rememberScrollState()),
          )
        }
      }

      onNodeWithContentDescription("Select date").performClick()
      waitForIdle()

      // FormEnvironment.DEFAULT clock is 2026-09-17; advance to October 2026 to match the
      // Google Earth KML balloon screenshot scenario.
      onNodeWithText("September 2026").assertExists()
      onNodeWithContentDescription("Next month").performClick()
      waitForIdle()
      onNodeWithText("October 2026").assertExists()

      val monthHeaderBounds = onNodeWithText("October 2026").fetchSemanticsNode().boundsInRoot
      val weekdayBounds = onNodeWithText("W").fetchSemanticsNode().boundsInRoot
      val week1DayBounds =
        onAllNodesWithText(" 1, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val week2DayBounds =
        onAllNodesWithText(" 8, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val week3DayBounds =
        onAllNodesWithText(" 15, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val week4DayBounds =
        onAllNodesWithText(" 22, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val week5DayBounds =
        onAllNodesWithText(" 29, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val cancelBounds = onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
      val okBounds = onNodeWithText("OK").fetchSemanticsNode().boundsInRoot

      // Verify strict vertical ordering with no overlap between month bar, weekday header,
      // each calendar week row, and the Cancel/OK footer buttons inside the 280dp balloon height.
      assertTrue(
        monthHeaderBounds.bottom <= weekdayBounds.top + 1f,
        "Month bar ($monthHeaderBounds) overlapped weekday header ($weekdayBounds)",
      )
      assertTrue(
        weekdayBounds.bottom <= week1DayBounds.top + 1f,
        "Weekday header ($weekdayBounds) overlapped week 1 ($week1DayBounds)",
      )
      assertTrue(
        week1DayBounds.bottom <= week2DayBounds.top + 1f,
        "Week 1 ($week1DayBounds) overlapped week 2 ($week2DayBounds)",
      )
      assertTrue(
        week2DayBounds.bottom <= week3DayBounds.top + 1f,
        "Week 2 ($week2DayBounds) overlapped week 3 ($week3DayBounds)",
      )
      assertTrue(
        week3DayBounds.bottom <= week4DayBounds.top + 1f,
        "Week 3 ($week3DayBounds) overlapped week 4 ($week4DayBounds)",
      )
      assertTrue(
        week4DayBounds.bottom <= week5DayBounds.top + 1f,
        "Week 4 ($week4DayBounds) overlapped week 5 ($week5DayBounds)",
      )
      assertTrue(
        week5DayBounds.bottom <= okBounds.top + 1f,
        "Week 5 ($week5DayBounds) overlapped OK button ($okBounds)",
      )
      assertTrue(
        week5DayBounds.bottom <= cancelBounds.top + 1f,
        "Week 5 ($week5DayBounds) overlapped Cancel button ($cancelBounds)",
      )
      assertTrue(
        okBounds.bottom <= 280f,
        "OK button ($okBounds) overflowed the 280dp KML balloon viewport height",
      )

      // Pick October 15, 2026 and confirm.
      onAllNodesWithText(" 15, ", substring = true, useUnmergedTree = true).onFirst().performClick()
      onNodeWithText("OK").performClick()
      waitForIdle()

      val dateProto =
        controller.formState.findFieldState("/data/visit_date")?.value?.scalar_value?.date_value
      assertEquals(2026, dateProto?.year)
      assertEquals(10, dateProto?.month)
      assertEquals(15, dateProto?.day)
    }

  @Test
  fun datePicker_sixWeekMonthAndYearPicker_fitAndSelectCleanlyInCompactBalloon() =
    runDesktopComposeUiTest(width = 356, height = 280) {
      val controller =
        formController(
          title = "Farm Survey",
          model =
            """
            <instance><data id="farms" version="1"><visit_date/></data></instance>
            <bind nodeset="/data/visit_date" type="date"/>
            """,
          body =
            """
            <input ref="/data/visit_date"><label>Date of farm visit</label></input>
            """,
        )
      setContent {
        AppTheme {
          CompactFormRunner(
            controller,
            modifier = Modifier.verticalScroll(rememberScrollState()),
          )
        }
      }

      onNodeWithContentDescription("Select date").performClick()
      waitForIdle()

      // Navigate back to August 2026 (a 6-week month: Aug 1 is Saturday, Aug 30-31 are in row 6).
      onNodeWithContentDescription("Previous month").performClick()
      waitForIdle()
      onNodeWithText("August 2026").assertExists()

      val weekdayBounds = onNodeWithText("W").fetchSemanticsNode().boundsInRoot
      val firstWeekBounds =
        onAllNodesWithText(" 1, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val sixthWeekBounds =
        onAllNodesWithText(" 31, ", substring = true, useUnmergedTree = true)
          .onFirst()
          .fetchSemanticsNode()
          .boundsInRoot
      val okBounds = onNodeWithText("OK").fetchSemanticsNode().boundsInRoot

      assertTrue(
        weekdayBounds.bottom <= firstWeekBounds.top + 1f,
        "Weekday header ($weekdayBounds) overlapped week 1 ($firstWeekBounds) in 6-week month",
      )
      assertTrue(
        sixthWeekBounds.bottom <= okBounds.top + 1f,
        "Sixth week ($sixthWeekBounds) overlapped OK button ($okBounds) in 6-week month",
      )

      // Toggle the year picker, select 2025, and pick August 31, 2025.
      onNodeWithText("August 2026").performClick()
      waitForIdle()
      onNodeWithText("2025").performClick()
      waitForIdle()
      onNodeWithText("August 2025").assertExists()

      onAllNodesWithText(" 31, ", substring = true, useUnmergedTree = true).onFirst().performClick()
      onNodeWithText("OK").performClick()
      waitForIdle()

      val dateProto =
        controller.formState.findFieldState("/data/visit_date")?.value?.scalar_value?.date_value
      assertEquals(2025, dateProto?.year)
      assertEquals(8, dateProto?.month)
      assertEquals(31, dateProto?.day)
    }

  @Test
  fun googleEarthVariant_incompatibleGeometry_showsTitleHintAndFallbackWarning_andSubmits() =
    runComposeUiTest {
      val baseController =
        formController(
          title = "Plot Check",
          model =
            """
            <instance>
              <data id="plot_check" version="1">
                <observer/>
                <gps_center/>
              </data>
            </instance>
            <bind nodeset="/data/observer" type="string" required="true()"/>
            <bind nodeset="/data/gps_center" type="geopoint" required="true()"/>
            """,
          body =
            """
            <input ref="/data/observer"><label>Observer name</label></input>
            <input ref="/data/gps_center">
              <label>Plot GPS center</label>
              <hint>Stand directly over the center monument</hint>
            </input>
            """,
        )
      val controller =
        FormWizardController(
          formDef = baseController.session.compiledForm.formDef,
          variant = FormVariant.GOOGLE_EARTH,
        )
      var submitted: FinalizationResult.Success? = null
      setContent {
        AppTheme {
          CompactFormRunner(
            controller,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onSubmitted = { submitted = it },
            variant = FormVariant.GOOGLE_EARTH,
          )
        }
      }

      // Question title and hint text are rendered alongside the user-friendly fallback warning,
      // while the incompatible geometry control is omitted.
      onNodeWithText("Plot GPS center").assertExists()
      onNodeWithText("Stand directly over the center monument").assertExists()
      onNodeWithTag("variant_incompatible_warning_/data/gps_center").assertExists()
      onNodeWithText(
          "Location questions aren't available on Google Earth. This question will be skipped. Contact the survey organizer to resolve this."
        )
        .assertExists()
      onNodeWithText("Capture GPS", substring = true).assertDoesNotExist()

      // Answering the compatible required field allows submission cleanly.
      onAllNodes(hasSetTextAction())[0].performTextInput("Ada Lovelace")
      onNodeWithText("Submit").performClick()
      assertNotNull(submitted)
    }

  @Test
  fun webVariant_gpsOnlyGeometry_rendersMapDrawingWithoutCompatibilityWarning() = runComposeUiTest {
    val controller =
      formController(
        title = "Plot Check",
        model =
          """
            <instance>
              <data id="plot_check" version="1">
                <gps_center/>
              </data>
            </instance>
            <bind nodeset="/data/gps_center" type="geopoint" required="true()"/>
            """,
        body =
          """
            <input ref="/data/gps_center">
              <label>Plot GPS center</label>
              <hint>Click the map to place the point</hint>
            </input>
            """,
      )
    setContent {
      AppTheme {
        CompactFormRunner(
          controller,
          modifier = Modifier.verticalScroll(rememberScrollState()),
          geometryInput = CompactGeometryInput.MapDrawing(),
          variant = FormVariant.WEB,
        )
      }
    }

    onNodeWithText("Plot GPS center *").assertExists()
    onNodeWithText("Click the map to place the point").assertExists()
    onNodeWithTag("variant_incompatible_warning_/data/gps_center").assertDoesNotExist()
    onNodeWithText("Draw on map").assertExists()
  }

  @Test
  fun unansweredQuestions_showOneBasedNumberBadge_andAnsweredSwitchToCheckmarkWithoutRenumbering() =
    runComposeUiTest {
      val controller =
        formController(
          title = "Plot Check",
          model =
            """
            <instance>
              <data id="plot_check" version="1">
                <species/>
                <notes/>
                <observer/>
              </data>
            </instance>
            <bind nodeset="/data/species" type="string" required="true()"/>
            <bind nodeset="/data/notes" type="string"/>
            <bind nodeset="/data/observer" type="string"/>
            """,
          body =
            """
            <input ref="/data/species"><label>Tree species</label></input>
            <input ref="/data/notes"><label>Field notes</label></input>
            <input ref="/data/observer"><label>Observer name</label></input>
            """,
        )
      showRunner(controller)

      // All three start unanswered with 1-based numbered grey circle badges.
      onNodeWithTag("compact_question_number_1", useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Pending")
      onNodeWithTag("compact_question_number_2", useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Pending")
      onNodeWithTag("compact_question_number_3", useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Pending")
      onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
      assertEquals(
        0,
        onAllNodesWithContentDescription("Answered", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .size,
      )

      // Answer question 1: checkmark replaces badge 1 while questions 2 and 3 keep their numbers.
      onAllNodes(hasSetTextAction())[0].performTextInput("Baobab")
      waitForIdle()
      onNodeWithTag("compact_question_number_1", useUnmergedTree = true).assertDoesNotExist()
      onNodeWithContentDescription("Answered", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithTag("compact_question_number_2", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithTag("compact_question_number_3", useUnmergedTree = true).assertIsDisplayed()

      // Clear question 1: numbered badge 1 returns.
      onAllNodes(hasSetTextAction())[0].performTextClearance()
      waitForIdle()
      onNodeWithTag("compact_question_number_1", useUnmergedTree = true).assertIsDisplayed()
      assertEquals(
        0,
        onAllNodesWithContentDescription("Answered", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .size,
      )

      // Skip optional question 2: skipped indicator replaces badge 2 while 1 and 3 stay numbered.
      onAllNodesWithText("Skip").onFirst().performClick()
      waitForIdle()
      onNodeWithTag("compact_question_number_1", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithTag("compact_question_number_2", useUnmergedTree = true).assertDoesNotExist()
      onNodeWithContentDescription("Skipped", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithTag("compact_question_number_3", useUnmergedTree = true).assertIsDisplayed()
    }

  @Test
  fun questionFilter_numbersFirstVisibleQuestionAsOne() = runComposeUiTest {
    val controller =
      formController(
        title = "Entity Form",
        model =
          """
          <instance>
            <data id="entity_form" version="1">
              <plot_ref/>
              <land_cover/>
              <notes/>
            </data>
          </instance>
          <bind nodeset="/data/plot_ref" type="string"/>
          <bind nodeset="/data/land_cover" type="string"/>
          <bind nodeset="/data/notes" type="string"/>
          """,
        body =
          """
          <input ref="/data/plot_ref"><label>Target plot</label></input>
          <input ref="/data/land_cover"><label>Land cover</label></input>
          <input ref="/data/notes"><label>Notes</label></input>
          """,
      )
    setContent {
      AppTheme {
        CompactFormRunner(
          controller = controller,
          modifier = Modifier.verticalScroll(rememberScrollState()),
          questionFilter = { it.stepKey != "/data/plot_ref" },
        )
      }
    }

    onNodeWithTag("compact_question_number_1", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("compact_question_number_2", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("compact_question_number_3", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun compactStatusIcon_rendersMultiDigitNumbersAndThemeVariants() = runComposeUiTest {
    setContent {
      GoogleEarthTheme {
        Row {
          CompactStatusIcon(status = CompactQuestionStatus.PENDING, questionNumber = 1)
          CompactStatusIcon(status = CompactQuestionStatus.PENDING, questionNumber = 12)
          CompactStatusIcon(status = CompactQuestionStatus.PENDING, questionNumber = 105)
          CompactStatusIcon(status = CompactQuestionStatus.ANSWERED, questionNumber = 2)
          CompactStatusIcon(status = CompactQuestionStatus.SKIPPED, questionNumber = 3)
          CompactStatusIcon(status = CompactQuestionStatus.INVALID, questionNumber = 4)
        }
      }
    }

    onNodeWithTag("compact_question_number_1", useUnmergedTree = true)
      .assertIsDisplayed()
      .assertContentDescriptionEquals("Pending")
    onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("compact_question_number_12", useUnmergedTree = true)
      .assertIsDisplayed()
      .assertContentDescriptionEquals("Pending")
    onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithTag("compact_question_number_105", useUnmergedTree = true)
      .assertIsDisplayed()
      .assertContentDescriptionEquals("Pending")
    onNodeWithText("105", useUnmergedTree = true).assertIsDisplayed()
    // Non-pending statuses keep their icons instead of showing a number badge.
    onNodeWithTag("compact_question_number_2", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("compact_question_number_3", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("compact_question_number_4", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithContentDescription("Answered", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithContentDescription("Skipped", useUnmergedTree = true).assertIsDisplayed()
    onNodeWithContentDescription("Needs attention", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun tabTraversal_skipsQuestionAndSectionHeadingsAndReadOnlyCards() = runComposeUiTest {
    val controller =
      formController(
        title = "Plot Survey",
        model =
          """
          <instance>
            <data id="tab_test" version="1">
              <species/>
              <site><soil/><slope/></site>
              <note_item>Measure from the center stake.</note_item>
              <count/>
            </data>
          </instance>
          <bind nodeset="/data/species" type="string" required="true()"/>
          <bind nodeset="/data/site/soil" type="string" required="true()"/>
          <bind nodeset="/data/site/slope" type="string" required="true()"/>
          <bind nodeset="/data/note_item" type="string" readonly="true()"/>
          <bind nodeset="/data/count" type="int" required="true()"/>
          """,
        body =
          """
          <input ref="/data/species"><label>Tree species</label></input>
          <group ref="/data/site" appearance="field-list">
            <label>Site details</label>
            <input ref="/data/site/soil"><label>Soil type</label></input>
            <input ref="/data/site/slope"><label>Slope grade</label></input>
          </group>
          <input ref="/data/note_item"><label>Field note</label></input>
          <input ref="/data/count"><label>Tree count</label></input>
          """,
      )
    showRunner(controller)

    // Section heading keeps accessibility heading semantics (separate from question card eyebrows).
    onNode(hasText("Site details") and isHeading()).assertExists()

    val inputs = onAllNodes(hasSetTextAction())
    assertEquals(4, inputs.fetchSemanticsNodes().size)

    // Start inside the first question's text field and tab forward through every editable input.
    inputs[0].performClick()
    waitForIdle()
    inputs[0].assertIsFocused()

    inputs[0].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    inputs[1].assertIsFocused()
    onNode(hasText("Site details") and isHeading())
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    onNodeWithText("Soil type *").assertIsNotFocused()

    inputs[1].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    inputs[2].assertIsFocused()
    onNodeWithText("Slope grade *").assertIsNotFocused()

    // Read-only note card (and its heading + chevron) is skipped completely.
    inputs[2].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    inputs[3].assertIsFocused()
    onNodeWithText("Field note").assertIsNotFocused()
    onNodeWithText("Tree count *").assertIsNotFocused()

    // Forward tab from the last input goes straight to the Review & submit action button.
    inputs[3].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("Review & submit").assertIsFocused()

    // Reverse traversal (Shift+Tab) also skips every card heading, chevron, and section heading.
    onNodeWithText("Review & submit").performKeyInput {
      withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) }
    }
    waitForIdle()
    inputs[3].assertIsFocused()

    inputs[3].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    waitForIdle()
    inputs[2].assertIsFocused()

    inputs[2].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    waitForIdle()
    inputs[1].assertIsFocused()

    inputs[1].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    waitForIdle()
    inputs[0].assertIsFocused()
  }

  @Test
  fun tabTraversal_googleEarthVariant_skipsIncompatibleCardHeadingsAndChevrons() =
    runComposeUiTest {
      val baseController =
        formController(
          title = "Earth Plot Check",
          model =
            """
          <instance>
            <data id="earth_tab" version="1">
              <observer/>
              <canopy_photo/>
              <remarks/>
            </data>
          </instance>
          <bind nodeset="/data/observer" type="string" required="true()"/>
          <bind nodeset="/data/canopy_photo" type="binary" required="true()"/>
          <bind nodeset="/data/remarks" type="string" required="true()"/>
          """,
          body =
            """
          <input ref="/data/observer"><label>Observer name</label></input>
          <upload ref="/data/canopy_photo" mediatype="image/*"><label>Canopy photo</label></upload>
          <input ref="/data/remarks"><label>Remarks</label></input>
          """,
        )
      val controller =
        FormWizardController(
          formDef = baseController.session.compiledForm.formDef,
          variant = FormVariant.GOOGLE_EARTH,
        )
      setContent {
        AppTheme {
          CompactFormRunner(
            controller,
            showHeader = false,
            variant = FormVariant.GOOGLE_EARTH,
            modifier = Modifier.verticalScroll(rememberScrollState()),
          )
        }
      }

      val inputs = onAllNodes(hasSetTextAction())
      assertEquals(2, inputs.fetchSemanticsNodes().size)

      inputs[0].performClick()
      waitForIdle()
      inputs[0].assertIsFocused()

      // Tab jumps from Observer directly to Remarks, skipping the incompatible photo card and the
      // Remarks heading row / chevron button.
      inputs[0].performKeyInput { pressKey(Key.Tab) }
      waitForIdle()
      inputs[1].assertIsFocused()

      inputs[1].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
      waitForIdle()
      inputs[0].assertIsFocused()
    }

  @Test
  fun questionCardHeaderAndChevron_remainClickableToCollapseAndExpand_andCollapsedHeadersAreSkipped() =
    runComposeUiTest {
      val controller =
        formController(
          title = "Tree Survey",
          model =
            """
            <instance><data id="toggle_tab" version="1"><first/><second/></data></instance>
            <bind nodeset="/data/first" type="int" required="true()"/>
            <bind nodeset="/data/second" type="int" required="true()"/>
            """,
          body =
            """
            <input ref="/data/first"><label>First count</label></input>
            <input ref="/data/second"><label>Second count</label></input>
            """,
        )
      showRunner(controller)

      assertEquals(2, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)

      // Clicking the header row collapses the first card.
      onNodeWithText("First count *").performClick()
      waitForIdle()
      assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
      onNodeWithContentDescription("Expand question").assertExists()

      // Clicking inside the second input and pressing Shift+Tab skips the collapsed card's heading
      // and chevron (landing on Collapse all in the form header, never the collapsed card header).
      onAllNodes(hasSetTextAction())[0].performClick()
      waitForIdle()
      onAllNodes(hasSetTextAction())[0].assertIsFocused()
      onAllNodes(hasSetTextAction())[0].performKeyInput {
        withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) }
      }
      waitForIdle()
      onNodeWithText("Collapse all").assertIsFocused()
      onNodeWithText("First count *").assertIsNotFocused()
      onNodeWithContentDescription("Expand question").assertIsNotFocused()

      // Clicking the chevron re-expands the first card.
      onNodeWithContentDescription("Expand question").performClick()
      waitForIdle()
      assertEquals(2, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
      assertEquals(
        2,
        onAllNodesWithContentDescription("Collapse question").fetchSemanticsNodes().size,
      )
    }

  @Test
  fun tabTraversal_answeringFirstIntegerInput_autoCollapsesAndMovesDirectlyToSecondInput() =
    runComposeUiTest {
      val controller =
        formController(
          title = "Plot Measurements",
          model =
            """
            <instance><data id="auto_collapse" version="1"><dbh/><height/></data></instance>
            <bind nodeset="/data/dbh" type="int" required="true()"/>
            <bind nodeset="/data/height" type="int" required="true()"/>
            """,
          body =
            """
            <input ref="/data/dbh"><label>Diameter (cm)</label></input>
            <input ref="/data/height"><label>Height (m)</label></input>
            """,
        )
      showRunner(controller)

      val inputs = onAllNodes(hasSetTextAction())
      inputs[0].performClick()
      inputs[0].performTextInput("42")
      waitForIdle()

      // While typing, the first card remains open. Tabbing out moves focus directly to the second
      // card's input and auto-collapses the first card.
      inputs[0].performKeyInput { pressKey(Key.Tab) }
      waitForIdle()

      assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
      onAllNodes(hasSetTextAction())[0].assertIsFocused()
      onNodeWithText("42").assertExists()
    }

  @Test
  fun tabTraversal_repeatSection_preservesHeadingSemanticsAndSkipsCardHeaders() = runComposeUiTest {
    val controller = RunnerTestForms.branchRepeat()
    controller.addRepeatInstanceAndOpen("/data/branch")
    showRunner(controller)

    onNode(hasText("Branch") and isHeading()).assertExists()
    onNode(hasText("Branch #1") and isHeading()).assertExists()

    val input = onNode(hasSetTextAction())
    input.performClick()
    waitForIdle()
    input.assertIsFocused()

    // Shift+Tab skips the "Branch length (m)" card heading and chevron and moves straight to the
    // entry's delete action button.
    input.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    waitForIdle()
    onNodeWithText("Branch length (m)").assertIsNotFocused()
    onNodeWithContentDescription("Collapse question").assertIsNotFocused()
    onNodeWithContentDescription("Delete Branch #1").assertIsFocused()
  }

  @Test
  fun tabTraversal_acrossTextSelectOneAndBoolean_skipsEveryQuestionHeading() = runComposeUiTest {
    val controller =
      formController(
        title = "Mixed Controls Survey",
        model =
          """
          <instance>
            <data id="mixed_tab" version="1">
              <observer/>
              <land_cover/>
              <verified/>
              <remarks/>
            </data>
          </instance>
          <bind nodeset="/data/observer" type="string" required="true()"/>
          <bind nodeset="/data/land_cover" type="string" required="true()"/>
          <bind nodeset="/data/verified" type="boolean" required="true()"/>
          <bind nodeset="/data/remarks" type="string" required="true()"/>
          """,
        body =
          """
          <input ref="/data/observer"><label>Observer name</label></input>
          <select1 ref="/data/land_cover">
            <label>Land cover</label>
            <item><label>Forest</label><value>forest</value></item>
            <item><label>Cropland</label><value>cropland</value></item>
          </select1>
          <input ref="/data/verified"><label>Verified</label></input>
          <input ref="/data/remarks"><label>Remarks</label></input>
          """,
      )
    showRunner(controller)

    val textInputs = onAllNodes(hasSetTextAction())
    textInputs[0].performClick()
    waitForIdle()
    textInputs[0].assertIsFocused()

    // Text → first select1 option (skips "Land cover *" heading row and chevron).
    textInputs[0].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("Land cover *").assertIsNotFocused()
    onNodeWithText("Forest").assertIsFocused()

    // Select1 first option → second option.
    onNodeWithText("Forest").performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("Cropland").assertIsFocused()

    // Select1 last option → boolean "Yes" button (skips "Verified *" heading row and chevron).
    onNodeWithText("Cropland").performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("Verified *").assertIsNotFocused()
    onNodeWithText("Yes").assertIsFocused()

    // Boolean "Yes" → "No" → next text input (skips "Remarks *" heading row and chevron).
    onNodeWithText("Yes").performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("No").assertIsFocused()

    onNodeWithText("No").performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("Remarks *").assertIsNotFocused()
    textInputs[1].assertIsFocused()
  }
}
