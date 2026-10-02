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
package org.groundplatform.v2.devtools.prototypeapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorState
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.publishedFormXml

/**
 * End-to-end checks that [PrototypeAppState] reads from and writes through the local data store,
 * which is the single source of truth for app data.
 */
class LocalStoreAppStateTest {
  @Test
  fun submittedForm_survivesSwitchingSurveysAndBack() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertEquals("survey-kenya-coffee", state.activeSurveyId)
    val before = state.entities.first { it.id == "entity-nyr-112" }.submissions.size

    state.launchFormForEntity(entityId = "entity-nyr-112", formId = "form-household-interview")
    state.completeActiveFormSubmission()
    assertEquals(before + 1, state.entities.first { it.id == "entity-nyr-112" }.submissions.size)

    state.openSurvey("survey-sample-plots-forest")
    assertEquals("survey-sample-plots-forest", state.activeSurveyId)
    assertTrue(state.entities.none { it.id == "entity-nyr-112" })

    state.openSurvey("survey-kenya-coffee")
    assertEquals(before + 1, state.entities.first { it.id == "entity-nyr-112" }.submissions.size)
  }

  @Test
  fun viewModelUiState_mirrorsStoreData() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.addRandomSites(count = 25)
    val ui = state.uiState.value
    assertEquals(state.entities, ui.entities)
    assertEquals(state.mutations, ui.mutations)
    assertEquals(state.activeSurveyId, ui.activeSurveyId)
  }

  @Test
  fun settings_areWrittenToTheStore() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.updateUnitSystem(MeasurementUnitSystem.IMPERIAL)
    state.updateSelectedLanguage("fr")
    state.updateUploadMediaOverUnmeteredConnectionOnly(true)
    assertEquals(
      UserSettings("fr", MeasurementUnitSystem.IMPERIAL, shouldUploadPhotosOnWifiOnly = true),
      state.viewModel.appData.value.userSettings,
    )
  }

  @Test
  fun reset_reseedsSampleData() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val initialEntities = state.entities
    state.addRandomSites(count = 10)
    state.syncAllOutboxMutations()
    assertEquals(initialEntities.size + 10, state.entities.size)

    state.resetPrototypeFlow()
    assertEquals("survey-kenya-coffee", state.activeSurveyId)
    assertEquals(initialEntities, state.entities)
    assertTrue(state.mutations.any { it.isOutbox })
  }

  @Test
  fun appStates_doNotShareData() {
    val first = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val second = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    first.addRandomSites(count = 10)
    assertEquals(first.entities.size - 10, second.entities.size)
  }

  @Test
  fun surveyEditor_loadsSampleDraftFromStore() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val editor = SurveyEditorState(state.activeSurveyEditorDraft)
    assertEquals(state.activeSurvey.title, editor.details.title)
    assertEquals(SurveyEditorSamples.draft().datasets, editor.datasets)
    assertEquals(2, editor.forms.size)

    val other = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    other.openSurvey("survey-sample-plots-forest")
    val blank = SurveyEditorState(other.activeSurveyEditorDraft)
    assertEquals(other.activeSurvey.title, blank.details.title)
    assertTrue(blank.datasets.isEmpty())
  }

  @Test
  fun surveyEditor_editsAreSavedToStoreAndSurviveSurveySwitches() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val editor = SurveyEditorState(state.activeSurveyEditorDraft)
    editor.updateDetails { it.copy(title = "Renamed survey") }
    editor.addDataset(DatasetKind.DATA_TABLE)
    editor.addForm()
    state.saveSurveyEditorDraft("survey-kenya-coffee", editor.toDraft())

    // The title is shown in the survey list; generated XForms are published to the survey config.
    assertEquals("Renamed survey", state.activeSurvey.title)
    val config = state.viewModel.appData.value.content.config
    val draft = editor.toDraft()
    val newEntry = draft.forms.last()
    val publishedXml = config?.formXmlById?.get(newEntry.form.formId)
    assertEquals(draft.publishedFormXml(newEntry), publishedXml)
    // New Forms add rows to their linked Data table by default.
    assertTrue(publishedXml.orEmpty().contains("create=\"1\""))

    state.openSurvey("survey-sample-plots-forest")
    state.openSurvey("survey-kenya-coffee")
    val reopened = SurveyEditorState(state.activeSurveyEditorDraft)
    assertEquals(editor.toDraft(), reopened.toDraft())

    state.resetPrototypeFlow()
    assertEquals(SurveyEditorSamples.draft().datasets, state.activeSurveyEditorDraft.datasets)
  }
}
