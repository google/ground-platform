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
package org.groundplatform.v2.web.ui.formeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.seed.FormEditorSamples
import org.groundplatform.v2.app.domain.model.editor.ChoiceSource
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.DateRule
import org.groundplatform.v2.app.domain.model.editor.EditorDataset
import org.groundplatform.v2.app.domain.model.editor.EditorDatasetRow
import org.groundplatform.v2.app.domain.model.editor.EditorForm
import org.groundplatform.v2.app.domain.model.editor.EditorQuestion
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.friendlyDate
import org.groundplatform.v2.web.ui.viewmodel.FormEditorViewModel
import org.groundplatform.v2.web.ui.viewmodel.formEditorViewModel
import org.groundplatform.v2.web.ui.viewmodel.ui

/** Every interactive element of the Form editor must respond to a single click or tap. */
@OptIn(ExperimentalTestApi::class)
class FormEditorClickTest {

  private fun withEditor(
    form: EditorForm = FormEditorSamples.shadeTreeVisit(),
    datasets: List<EditorDataset> = emptyList(),
    block: androidx.compose.ui.test.ComposeUiTest.(FormEditorViewModel) -> Unit,
  ) =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      val state = formEditorViewModel(form, datasets = datasets)
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          FormEditorPage(uiState = uiState, actions = state, isDarkTheme = false)
        }
      }
      block(state)
    }

  @Test
  fun singleClickOnScreenCardSelectsQuestion() = withEditor { state ->
    onAllNodesWithText("Are shade trees present?", substring = true, useUnmergedTree = true)
      .onFirst()
      .performClick()
    waitForIdle()
    assertEquals("q3", state.ui.selectedKey)
  }

  @Test
  fun singleClickOnStartNodeOpensFormSettings() = withEditor { state ->
    runOnIdle { state.select("q1") }
    waitForIdle()
    onNodeWithContentDescription("Start: Shade Tree Farm Visit").performClick()
    waitForIdle()
    assertTrue(state.ui.isFormSettingsSelected)

    runOnIdle {
      state.updateCtaLabel("Record farm visit")
      state.select("q1")
    }
    waitForIdle()
    onNodeWithContentDescription("Start: Record farm visit").performClick()
    waitForIdle()
    assertTrue(state.ui.isFormSettingsSelected)
  }

  @Test
  fun singleClickOnFormSettingsButtonOpensFormSettings() = withEditor { state ->
    onNodeWithText("Form settings").performClick()
    waitForIdle()
    assertTrue(state.ui.isFormSettingsSelected)
  }

  @Test
  fun singleTapOnAddQuestionAffordanceOpensMenu() = withEditor { state ->
    // Touch input has no hover, so every "+" must already be hit-testable before the first tap.
    onAllNodesWithContentDescription("Add question here")
      .assertCountEquals(state.ui.form.questions.size + 1)
    onAllNodesWithContentDescription("Add question here").onFirst().performTouchInput { click() }
    waitForIdle()
    // The menu popup is composed last; earlier matches are screen cards.
    onAllNodesWithText("Paragraph").onLast().performClick()
    waitForIdle()
    assertEquals(EditorQuestionType.LONG_TEXT, state.ui.form.questions.first().type)
  }

  @Test
  fun clickDuringAutoScrollStillSelects() = withEditor { state ->
    mainClock.autoAdvance = false
    // Selecting an off-screen screen scrolls the canvas to it; a click that lands while that
    // scroll is still running must not be swallowed by the scroll container.
    runOnIdle { state.select("q7") }
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(32)
    onAllNodesWithText("Observed issues", substring = true, useUnmergedTree = true)
      .onFirst()
      .performTouchInput { click() }
    mainClock.advanceTimeBy(1_000)
    assertEquals("q6", state.ui.selectedKey)
  }

  @Test
  fun mouseClickRightAfterSelectingOffscreenScreenStillSelects() = withEditor { state ->
    mainClock.autoAdvance = false
    runOnIdle { state.select("q7") }
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    onNodeWithText("Form settings").performClick()
    mainClock.advanceTimeBy(1_000)
    assertTrue(state.ui.isFormSettingsSelected)
  }

  @Test
  fun singleClickOnChoiceColorSwatchAppliesColor() = withEditor { state ->
    runOnIdle { state.select("q3") }
    onAllNodesWithContentDescription("Choice color: none").onFirst().performClick()
    waitForIdle()
    onNodeWithContentDescription("Blue").performClick()
    waitForIdle()
    assertEquals("#2278CF", state.ui.form.find("q3")!!.choices[0].colorHex)
  }

  @Test
  fun singleClickOnAdvancedHeaderRevealsName() = withEditor { state ->
    runOnIdle { state.select("q3") }
    waitForIdle()
    // Collapsed by default: the data column name is hidden.
    onAllNodesWithText("Name").assertCountEquals(0)
    onNodeWithContentDescription("Expand advanced").performScrollTo().performClick()
    waitForIdle()
    onNodeWithText("Name").assertExists()
    onNodeWithText("Data column name. Letters, digits, _ . -").assertExists()
    onNodeWithText("Minimum selections").assertDoesNotExist()
    onNodeWithText("No validation rules for this question type.").assertExists()
    onNodeWithContentDescription("Collapse advanced").performClick()
    waitForIdle()
    onAllNodesWithText("Name").assertCountEquals(0)
  }

  @Test
  fun advancedSectionAutoExpandsOnInvalidName() = withEditor { state ->
    runOnIdle {
      state.select("q4")
      state.updateQuestion("q4") { it.copy(name = "1 bad name") }
    }
    waitForIdle()
    onNodeWithText("Name").assertExists()
    onNodeWithContentDescription("Advanced settings have errors").assertExists()
    onNodeWithText("Minimum").assertExists()
  }

  @Test
  fun advancedSectionStaysOpenWhenSelectingAnotherQuestion() = withEditor { state ->
    runOnIdle { state.select("q3") }
    waitForIdle()
    onNodeWithContentDescription("Expand advanced").performScrollTo().performClick()
    waitForIdle()
    assertTrue(state.ui.isAdvancedExpanded)
    runOnIdle { state.select("q5") }
    waitForIdle()
    onNodeWithText("Name").assertExists()
    onNodeWithContentDescription("Collapse advanced").assertExists()
  }

  @Test
  fun datePickerStoresIsoDateAndShowsFriendlyDate() = withEditor { state ->
    runOnIdle {
      state.select("q1")
      state.setAdvancedExpanded(true)
      state.updateValidation("q1") { it.copy(dateRule = DateRule.BETWEEN) }
    }
    waitForIdle()
    onNodeWithContentDescription("Pick Earliest date").performScrollTo().performClick()
    waitForIdle()
    // The picker opens on the current month; pick its 15th.
    onAllNodesWithText(" 15, ", substring = true, useUnmergedTree = true).onFirst().performClick()
    onNodeWithText("OK").performClick()
    waitForIdle()
    val expected = java.time.LocalDate.now().withDayOfMonth(15).toString()
    assertEquals(expected, state.ui.form.find("q1")!!.validation?.min)
    onNodeWithText(friendlyDate(expected), useUnmergedTree = true).assertExists()

    onNodeWithContentDescription("Clear Earliest date").performClick()
    waitForIdle()
    assertEquals(null, state.ui.form.find("q1")!!.validation?.min?.ifEmpty { null })
  }

  @Test
  fun dateQuestionCalendarWidgetPicksAndClearsDateInFormRunner() = withEditor { state ->
    runOnIdle { state.startPreview() }
    waitForIdle()

    onNodeWithContentDescription("Select date").performClick()
    waitForIdle()
    onAllNodesWithText(" 15, ", substring = true, useUnmergedTree = true).onFirst().performClick()
    onNodeWithText("OK").performClick()
    waitForIdle()

    val expectedDate = java.time.LocalDate.now().withDayOfMonth(15)
    val expectedIso = expectedDate.toString()
    onNodeWithText(expectedIso, useUnmergedTree = true).assertExists()

    val step =
      state.ui.previewController!!.currentStep
        as org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep
    val dateProto = step.control.fieldState.value?.scalar_value?.date_value
    assertEquals(expectedDate.year, dateProto?.year)
    assertEquals(expectedDate.monthValue, dateProto?.month)
    assertEquals(15, dateProto?.day)

    onNodeWithContentDescription("Clear date").performClick()
    waitForIdle()
    val clearedStep =
      state.ui.previewController!!.currentStep
        as org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep
    assertTrue(clearedStep.control.fieldState.isEmpty)
  }

  @Test
  fun clickWithSlightMouseMovementSelectsScreen() = withEditor { state ->
    // A real click rarely keeps the mouse perfectly still between press and release.
    onAllNodesWithText("Are shade trees present?", substring = true, useUnmergedTree = true)
      .onFirst()
      .performMouseInput {
        moveTo(center)
        press()
        moveBy(Offset(2f, 1f))
        release()
      }
    waitForIdle()
    assertEquals("q3", state.ui.selectedKey)
  }

  @Test
  fun singleClickOnScreenAfterTypingInLabelSelectsIt() = withEditor { state ->
    runOnIdle { state.select("q1") }
    waitForIdle()
    onNodeWithText("Label").performClick()
    onNodeWithText("Label").performTextInput(" today")
    waitForIdle()
    onAllNodesWithText("Are shade trees present?", substring = true, useUnmergedTree = true)
      .onFirst()
      .performClick()
    waitForIdle()
    assertEquals("q3", state.ui.selectedKey)
  }

  @Test
  fun dragPastSlopStillReordersScreens() = withEditor { state ->
    val firstKey = state.ui.form.questions.first().key
    onAllNodesWithText(
        state.ui.form.questions.first().label,
        substring = true,
        useUnmergedTree = true,
      )
      .onFirst()
      .performMouseInput {
        moveTo(center)
        press()
        repeat(40) { moveBy(Offset(30f, 0f)) }
        release()
      }
    waitForIdle()
    // The first screen moved later.
    assertTrue(state.ui.form.questions.indexOfFirst { it.key == firstKey } > 0)
  }

  @Test
  fun advancedStatusMarkerToggleAndAddRuleWorkInFormProperties() = withEditor { state ->
    runOnIdle {
      AdvancedDisclosure.expanded = true
      state.selectForm()
    }
    waitForIdle()
    assertTrue(!state.ui.form.saveTo.status.enabled)
    onNodeWithText("Set status marker").performScrollTo().performClick()
    waitForIdle()
    assertTrue(state.ui.form.saveTo.status.enabled)
    assertEquals(1, state.ui.form.saveTo.status.rules.size)
    assertEquals("Surveyed", state.ui.form.saveTo.status.rules.single().badge.label)
    assertEquals("Pending", state.ui.form.saveTo.status.defaultBadge.label)

    onNodeWithText("Add status rule").performScrollTo().performClick()
    waitForIdle()
    assertEquals(2, state.ui.form.saveTo.status.rules.size)
    runOnIdle { AdvancedDisclosure.expanded = null }
  }

  @Test
  fun draggingSeparatorResizesRightHandPropertiesPanel() = withEditor { state ->
    val initialWidth = state.ui.sidePanelWidthDp
    // Dragging the separator left widens the right-hand panel.
    onNodeWithContentDescription("Resize side panel").performMouseInput {
      moveTo(center)
      press()
      repeat(5) { moveBy(Offset(-20f, 0f)) }
      release()
    }
    waitForIdle()
    val widenedWidth = state.ui.sidePanelWidthDp
    assertTrue(
      widenedWidth > initialWidth,
      "Expected dragging left to widen right-hand panel ($initialWidth -> $widenedWidth)",
    )

    // Dragging the separator right narrows the right-hand panel.
    onNodeWithContentDescription("Resize side panel").performMouseInput {
      moveTo(center)
      press()
      repeat(5) { moveBy(Offset(20f, 0f)) }
      release()
    }
    waitForIdle()
    assertTrue(
      state.ui.sidePanelWidthDp < widenedWidth,
      "Expected dragging right to narrow right-hand panel ($widenedWidth -> ${state.ui.sidePanelWidthDp})",
    )
  }

  @Test
  fun choiceSourceSegmentedButtonsSwitchBetweenManualMapLayerAndDataTable() =
    withEditor(
      datasets =
        listOf(
          EditorDataset(
            id = "sample_plots",
            displayName = "Sample plots",
            isMapLayer = true,
            keyProperty = "plot_id",
            labelProperty = "plot_name",
            properties = emptyList(),
            rows = listOf(EditorDatasetRow("P-1", "Plot 1")),
          ),
          EditorDataset(
            id = "tree_species",
            displayName = "Tree species",
            isMapLayer = false,
            keyProperty = "code",
            labelProperty = "common_name",
            properties = emptyList(),
            rows = listOf(EditorDatasetRow("grevillea", "Silky oak")),
          ),
        )
    ) { state ->
      runOnIdle { state.select("q3") }
      waitForIdle()
      assertEquals(ChoiceSource.MANUAL, state.ui.form.find("q3")?.choiceSource)

      onNodeWithText("Manual list").performScrollTo().performClick()
      waitForIdle()
      onNodeWithText("Map layer").performClick()
      waitForIdle()
      assertEquals(ChoiceSource.MAP_LAYER, state.ui.form.find("q3")?.choiceSource)
      assertEquals("sample_plots", state.ui.form.find("q3")?.choiceDatasetId)

      onAllNodesWithText("Map layer").onFirst().performScrollTo().performClick()
      waitForIdle()
      onNodeWithText("Data table").performClick()
      waitForIdle()
      assertEquals(ChoiceSource.DATA_TABLE, state.ui.form.find("q3")?.choiceSource)
      assertEquals("tree_species", state.ui.form.find("q3")?.choiceDatasetId)

      onAllNodesWithText("Data table").onFirst().performScrollTo().performClick()
      waitForIdle()
      onNodeWithText("Manual list").performClick()
      waitForIdle()
      assertEquals(ChoiceSource.MANUAL, state.ui.form.find("q3")?.choiceSource)
      assertEquals(null, state.ui.form.find("q3")?.choiceDatasetId)
    }

  @Test
  fun choosingDataTableWithNoneAvailableOpensCreateFlowAndKeepsDataTableSelected() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      var createRequest: Pair<DatasetKind, String>? = null
      val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit(), datasets = emptyList())
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          FormEditorPage(
            uiState = uiState,
            actions = state,
            isDarkTheme = false,
            onCreateChoiceDataset = { kind, questionKey -> createRequest = kind to questionKey },
          )
        }
      }
      runOnIdle { state.select("q3") }
      waitForIdle()

      onNodeWithText("Manual list").performScrollTo().performClick()
      waitForIdle()
      onNodeWithText("Data table").performClick()
      waitForIdle()

      // Opens the create flow instead of reverting to "Manual list".
      assertEquals(DatasetKind.DATA_TABLE to "q3", createRequest)
      assertEquals(ChoiceSource.DATA_TABLE, state.ui.choiceSourceFor(state.ui.form.find("q3")!!))
      onNodeWithText("No data tables are available in this survey yet.").assertExists()
      onAllNodesWithText("New data table").assertCountEquals(1)
    }

  @Test
  fun allowAddEntityToggleAndCreateFormForDatasetInChoicesEditor() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      var createdDatasetKey: String? = null
      val datasetsFlow =
        kotlinx.coroutines.flow.MutableStateFlow(
          listOf(
            EditorDataset(
              id = "tree_species",
              displayName = "Tree species",
              isMapLayer = false,
              keyProperty = "code",
              labelProperty = "common_name",
              properties = emptyList(),
              rows = listOf(EditorDatasetRow("grevillea", "Silky oak")),
              key = "d_species",
              linkedFormKey = null,
            )
          )
        )
      val state =
        formEditorViewModel(FormEditorSamples.shadeTreeVisit(), datasets = datasetsFlow.value)
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          val datasets by datasetsFlow.collectAsState()
          FormEditorPage(
            uiState = uiState.copy(datasets = datasets),
            actions = state,
            isDarkTheme = false,
            onCreateFormForDataset = { dsKey ->
              createdDatasetKey = dsKey
              datasetsFlow.value =
                datasetsFlow.value.map {
                  if (it.key == dsKey) {
                    it.copy(linkedFormKey = "f_species", linkedFormTitle = "Tree species form")
                  } else {
                    it
                  }
                }
            },
          )
        }
      }
      runOnIdle { state.select("q3") }
      waitForIdle()

      onNodeWithText("Manual list").performScrollTo().performClick()
      waitForIdle()
      onNodeWithText("Data table").performClick()
      waitForIdle()
      assertEquals(false, state.ui.form.find("q3")?.allowAddEntity)

      onNodeWithText("Create form for Tree species").performScrollTo().performClick()
      waitForIdle()
      assertEquals("d_species", createdDatasetKey)
      assertEquals(true, state.ui.form.find("q3")?.allowAddEntity)
    }

  @Test
  fun formCreatedFromMapLayerShowsLinkedInToolbarAndFormProperties() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      var openedDatasetId: String? = null
      var unlinkedDatasetKey: String? = null
      val datasetsFlow =
        kotlinx.coroutines.flow.MutableStateFlow(
          listOf(
            EditorDataset(
              id = "coffee_parcels",
              displayName = "Coffee parcels",
              isMapLayer = true,
              keyProperty = "parcel_id",
              labelProperty = "parcel_name",
              properties = emptyList(),
              rows = listOf(EditorDatasetRow("NYR-104", "Gatura Ridge")),
              isLinkedToThisForm = true,
              key = "d1",
              linkedFormKey = "f1",
              linkedFormTitle = "Coffee parcels form",
            )
          )
        )
      val state =
        formEditorViewModel(FormEditorSamples.shadeTreeVisit(), datasets = datasetsFlow.value)
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          val datasets by datasetsFlow.collectAsState()
          FormEditorPage(
            uiState = uiState.copy(datasets = datasets),
            actions = state,
            isDarkTheme = false,
            onCreateDataset = {},
            onOpenDataset = { id -> openedDatasetId = id },
            onUnlinkDataset = { dsKey ->
              unlinkedDatasetKey = dsKey
              datasetsFlow.value =
                datasetsFlow.value.map {
                  if (it.key == dsKey) it.copy(isLinkedToThisForm = false, linkedFormKey = null)
                  else it
                }
            },
          )
        }
      }
      waitForIdle()
      // The toolbar shows neither "Create map layer" nor a "Linked to" chip; the link is shown in
      // Form settings.
      onNodeWithText("Create map layer").assertDoesNotExist()
      onAllNodesWithText("Linked to Coffee parcels").assertCountEquals(0)

      // Form properties shows the linked card with "Edit map layer" and "Unlink map layer".
      runOnIdle { state.selectFormSettings() }
      waitForIdle()
      onNodeWithText("Edit map layer").performScrollTo().performClick()
      waitForIdle()
      assertEquals("coffee_parcels", openedDatasetId)

      onNodeWithText("Unlink map layer").performScrollTo().performClick()
      waitForIdle()
      assertEquals("d1", unlinkedDatasetKey)
      onNodeWithText("Create map layer").assertExists()
    }

  @Test
  fun previewOverlayTogglesDarkThemeAndScrollsLongFlowList() = withEditor { state ->
    runOnIdle {
      repeat(15) { i ->
        state.addQuestion(EditorQuestionType.LONG_TEXT)
        val key = state.ui.selectedKey!!
        state.updateQuestion(key) { it.copy(label = "Extra step ${i + 1}") }
      }
      state.startPreview()
    }
    waitForIdle()

    // Mobile device preview frame starts in light theme and toggles to dark theme when clicked.
    onNodeWithText("Light").assertExists().performClick()
    waitForIdle()
    onNodeWithText("Dark").assertExists().performClick()
    waitForIdle()
    onNodeWithText("Light").assertExists()

    // Flow preview card keeps its footer buttons visible while scrolling its internal step list.
    onAllNodesWithText("Extra step 15").onLast().performScrollTo().performClick()
    waitForIdle()
    onNodeWithText("Back to editor").assertExists().performClick()
    waitForIdle()
    assertEquals(null, state.ui.previewController)
  }

  @Test
  fun previousAndNextButtonsAndOverflowMenuManageQuestionInPropertiesPanel() = withEditor { state ->
    val initialOrder = state.ui.form.questions.map { it.key }
    runOnIdle { state.select("q1") }
    waitForIdle()
    assertEquals("q1", state.ui.selectedKey)

    onAllNodesWithText("Next").onLast().performClick()
    waitForIdle()
    assertEquals("q2", state.ui.selectedKey)
    assertEquals(initialOrder, state.ui.form.questions.map { it.key })

    onNodeWithText("Previous").performClick()
    waitForIdle()
    assertEquals("q1", state.ui.selectedKey)
    assertEquals(initialOrder, state.ui.form.questions.map { it.key })

    // Duplicate and Delete are in the question options overflow menu.
    val initialCount = state.ui.form.questions.size
    onNodeWithContentDescription("Question options").performClick()
    waitForIdle()
    onNodeWithText("Duplicate").performClick()
    waitForIdle()
    assertEquals(initialCount + 1, state.ui.form.questions.size)

    onNodeWithContentDescription("Question options").performClick()
    waitForIdle()
    onNodeWithText("Delete").performClick()
    waitForIdle()
    assertEquals(initialCount, state.ui.form.questions.size)
  }

  @Test
  fun toolbarAddQuestionButtonAddsQuestionAndOverflowMenuConfirmsDeleteForm() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      var deleted = false
      val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          FormEditorPage(
            uiState = uiState,
            actions = state,
            isDarkTheme = false,
            onDelete = { deleted = true },
          )
        }
      }
      val initialCount = state.ui.form.questions.size
      onNodeWithText("Add question").performClick()
      waitForIdle()
      onAllNodesWithText("Number").onLast().performClick()
      waitForIdle()
      assertEquals(initialCount + 1, state.ui.form.questions.size)

      // Delete form is inside the More options menu and asks for confirmation.
      onNodeWithContentDescription("More options").performClick()
      waitForIdle()
      onNodeWithText("Download XForms XML").assertExists()
      onNodeWithText("Delete form").performClick()
      waitForIdle()
      onNodeWithText("Delete form?").assertExists()
      onNodeWithText("Cancel").performClick()
      waitForIdle()
      assertEquals(false, deleted)

      onNodeWithContentDescription("More options").performClick()
      waitForIdle()
      onNodeWithText("Delete form").performClick()
      waitForIdle()
      onAllNodesWithText("Delete form").onLast().performClick()
      waitForIdle()
      assertEquals(true, deleted)
    }

  @Test
  fun toolbarUndoAndRedoButtonsRewindAndReplayFormEdits() = withEditor { state ->
    val initialCount = state.ui.form.questions.size
    assertEquals(false, state.ui.canUndo)
    assertEquals(false, state.ui.canRedo)

    onNodeWithText("Add question").performClick()
    waitForIdle()
    onAllNodesWithText("Number").onLast().performClick()
    waitForIdle()
    assertEquals(initialCount + 1, state.ui.form.questions.size)
    assertEquals(true, state.ui.canUndo)
    assertEquals(false, state.ui.canRedo)

    onNodeWithContentDescription("Undo").performClick()
    waitForIdle()
    assertEquals(initialCount, state.ui.form.questions.size)
    assertEquals(false, state.ui.canUndo)
    assertEquals(true, state.ui.canRedo)

    onNodeWithContentDescription("Redo").performClick()
    waitForIdle()
    assertEquals(initialCount + 1, state.ui.form.questions.size)
    assertEquals(true, state.ui.canUndo)
    assertEquals(false, state.ui.canRedo)
  }

  @Test
  fun toolbarLanguageSelectorSwitchesActiveLanguageAndEditsLocalizedLabels() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      val state =
        formEditorViewModel(
          form = FormEditorSamples.shadeTreeVisit(),
          library =
            org.groundplatform.v2.web.ui.state.FormLibraryContext(
              language = "en",
              supportedLanguages = listOf("en", "es"),
            ),
        )
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          FormEditorPage(uiState = uiState, actions = state, isDarkTheme = false)
        }
      }
      waitForIdle()

      // Language selector shows English by default.
      onNodeWithContentDescription("Form language: English").assertExists().performClick()
      waitForIdle()
      onNodeWithText("English • Default").assertExists()
      onNodeWithText("Spanish").assertExists().performClick()
      waitForIdle()

      assertEquals("es", state.ui.activeLanguage)
      onNodeWithContentDescription("Form language: Spanish").assertExists()

      // Select Q3 ("has_shade_trees") and verify the properties panel prompts for Spanish labels.
      runOnIdle {
        state.select("q3")
        state.updateQuestionLabel("q3", "¿Hay árboles de sombra?")
        state.updateChoiceLabel("q3", 0, "Sí")
      }
      waitForIdle()

      onAllNodesWithText("¿Hay árboles de sombra?").onFirst().assertExists()
      assertEquals("Are shade trees present?", state.ui.form.find("q3")?.label)
      assertEquals("¿Hay árboles de sombra?", state.ui.form.find("q3")?.labelIn("es", "en"))
      assertEquals("Yes", state.ui.form.find("q3")?.choices?.first()?.label)
      assertEquals("Sí", state.ui.form.find("q3")?.choices?.first()?.labelIn("es", "en"))
    }

  @Test
  fun previewTargetToggleAndFloatingAddQuestionWorkAcrossMobileAndWeb() = withEditor { state ->
    val initialCount = state.ui.form.questions.size
    // Switch to Web layout via the preview header's Mobile/Web toggle.
    onNodeWithText("Web").performClick()
    waitForIdle()
    onNodeWithText("${initialCount} questions • Web layout").assertExists()

    // Floating "Add question" button remains accessible in Web preview mode.
    onNodeWithText("Add question").performClick()
    waitForIdle()
    onAllNodesWithText("Number").onLast().performClick()
    waitForIdle()
    assertEquals(initialCount + 1, state.ui.form.questions.size)
    onNodeWithText("${initialCount + 1} questions • Web layout").assertExists()

    // Switch back to Mobile flow canvas via the preview header's toggle.
    onNodeWithText("Mobile").performClick()
    waitForIdle()
    onNodeWithText("${initialCount + 1} screens •", substring = true).assertExists()
  }

  @Test
  fun singleClickOnGhostQuestionCardAppendsQuestion() =
    withEditor(
      form =
        EditorForm(
          formId = "f_ghost_test",
          title = "Ghost Card Test Form",
          questions =
            listOf(
              EditorQuestion(
                key = "q1",
                name = "q1",
                type = EditorQuestionType.TEXT,
                label = "First question",
              )
            ),
        )
    ) { state ->
      val initialCount = state.ui.form.questions.size
      onNodeWithContentDescription("Add a question at end of flow").performClick()
      waitForIdle()
      onAllNodesWithText("Number").onLast().performClick()
      waitForIdle()
      assertEquals(initialCount + 1, state.ui.form.questions.size)
      assertEquals(EditorQuestionType.NUMBER, state.ui.form.questions.last().type)
    }

  @Test
  fun addQuestionMenuOffersSectionAndCreatesSectionContainer() =
    withEditor(
      form =
        EditorForm(
          formId = "f_section_test",
          title = "Section Test Form",
          questions =
            listOf(
              EditorQuestion(
                key = "q1",
                name = "q1",
                type = EditorQuestionType.TEXT,
                label = "First question",
              )
            ),
        )
    ) { state ->
      val initialCount = state.ui.form.questions.size
      val initialSections = state.ui.form.sections.size
      onNodeWithText("Add question").performClick()
      waitForIdle()
      onAllNodesWithText("Section").onLast().performClick()
      waitForIdle()
      // Section starts empty without auto-added dummy questions
      assertEquals(initialCount, state.ui.form.questions.size)
      assertEquals(initialSections + 1, state.ui.form.sections.size)
      val newSection = state.ui.form.sections.last()
      assertEquals(newSection.id, state.ui.selectedSectionId)
      onNodeWithText("Group of questions in the form flow").assertExists()
      onNodeWithText("Section title").assertExists()

      // Inner ghost card is rendered inside the empty section container, and clicking it adds
      // question with sectionId set
      onNodeWithContentDescription("Add question inside ${newSection.title.ifBlank { "section" }}")
        .assertExists()
        .performClick()
      waitForIdle()
      onAllNodesWithText("Number").onLast().performClick()
      waitForIdle()
      assertEquals(initialCount + 1, state.ui.form.questions.size)
      val addedQuestion = state.ui.form.questions.last()
      assertEquals(newSection.id, addedQuestion.sectionId)
    }

  @Test
  fun singleClickOnSectionContainerSelectsSectionProperties() = withEditor { state ->
    var secId = ""
    runOnIdle {
      secId = state.addSection("Farm Details", repeatable = true)
      state.setQuestionSection("q1", secId)
      state.select("q2")
    }
    waitForIdle()
    assertEquals(null, state.ui.selectedSectionId)
    onAllNodesWithText("Repeated section: Farm Details", substring = true).onFirst().performClick()
    waitForIdle()
    assertEquals(secId, state.ui.selectedSectionId)
    onAllNodesWithText("Repeated section").onFirst().assertExists()
    onNodeWithText("Collectors can add multiple entries.").assertExists()
  }
}
