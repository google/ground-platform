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
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveyEditorData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.publishedForm
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

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
  fun dataHolderAppData_mirrorsStoreData() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.addRandomSites(count = 25)
    val appData = state.dataHolder.appData.value
    assertEquals(state.entities, appData.content.entities)
    assertEquals(state.mutations, appData.mutations)
    assertEquals(state.activeSurveyId, appData.activeSurveyId)
  }

  @Test
  fun settings_areWrittenToTheStore() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.updateUnitSystem(MeasurementUnitSystem.IMPERIAL)
    state.updateSelectedLanguage("fr")
    state.updateUploadMediaOverUnmeteredConnectionOnly(true)
    assertEquals(
      UserSettings("fr", MeasurementUnitSystem.IMPERIAL, shouldUploadPhotosOnWifiOnly = true),
      state.dataHolder.appData.value.userSettings,
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
    val editor = surveyEditorViewModel(state.activeSurveyEditorDraft)
    assertEquals(state.activeSurvey.title, editor.ui.details.title)
    // Forms and Map layers are derived from the survey data; Data tables come from the seed.
    assertEquals(state.forms.map { it.id }, editor.ui.draft.forms.map { it.form.formId })
    assertEquals(
      state.mapLayers.map { it.datasetId ?: it.id },
      editor.ui.mapLayers.map { it.id },
    )
    assertEquals(
      PrototypeFakeSurveyEditorData.farmers().id to PrototypeFakeSurveyEditorData.treeSpecies().id,
      editor.ui.dataTables.map { it.id }.let { it[0] to it[1] },
    )
    assertEquals(PrototypeFakeSurveyEditorData.kenyaSharing(), editor.ui.sharing)

    // Surveys without a stored draft still open with their Forms and Map layers.
    val other = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    other.openSurvey("survey-sample-plots-forest")
    val derived = surveyEditorViewModel(other.activeSurveyEditorDraft)
    assertEquals(other.activeSurvey.title, derived.ui.details.title)
    assertEquals(other.forms.map { it.id }, derived.ui.draft.forms.map { it.form.formId })
    assertEquals(other.mapLayers.size, derived.ui.mapLayers.size)
    assertTrue(derived.ui.dataTables.isEmpty())
  }

  @Test
  fun surveyEditor_editsAreSavedToStoreAndSurviveSurveySwitches() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val initial = state.activeSurveyEditorDraft
    val editor = surveyEditorViewModel(initial)
    editor.updateDetails { it.copy(title = "Renamed survey") }
    editor.addDataset(DatasetKind.DATA_TABLE)
    editor.addForm()
    state.saveSurveyEditorDraft("survey-kenya-coffee", editor.ui.draft)

    // The title is shown in the survey list; generated FormDefinitions are published to the survey
    // config.
    assertEquals("Renamed survey", state.activeSurvey.title)
    val config = state.dataHolder.appData.value.content.config
    val draft = editor.ui.draft
    val newEntry = draft.forms.last()
    val publishedForm = config?.formsById?.get(newEntry.form.formId)
    assertEquals(draft.publishedForm(newEntry), publishedForm)
    // New Forms add rows to their linked Data table by default.
    assertEquals("1", publishedForm?.proto?.model?.entities?.firstOrNull()?.create_condition)

    state.openSurvey("survey-sample-plots-forest")
    state.openSurvey("survey-kenya-coffee")
    val reopened = surveyEditorViewModel(state.activeSurveyEditorDraft)
    assertEquals(editor.ui.draft, reopened.ui.draft)

    state.resetPrototypeFlow()
    assertEquals(initial.datasets, state.activeSurveyEditorDraft.datasets)
  }
}
