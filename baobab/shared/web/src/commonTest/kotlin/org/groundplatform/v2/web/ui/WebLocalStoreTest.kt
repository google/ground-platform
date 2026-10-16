/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.web.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.sampleDataHolder
import org.groundplatform.v2.app.data.seed.SampleSurveyEditorData
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.publishedForm
import org.groundplatform.v2.web.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.web.ui.viewmodel.ui

/**
 * End-to-end checks that the web console reads from and writes through the local data store, and
 * that it stays online when the device it shares the store with is offline.
 */
class WebLocalStoreTest {
  @Test
  fun surveyEditor_loadsSampleDraftFromStore() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val editor = surveyEditorViewModel(state.activeSurveyEditorDraft)
    assertEquals(state.activeSurvey.title, editor.ui.details.title)
    // Forms and Map layers are derived from the survey data; Data tables come from the seed.
    assertEquals(state.forms.map { it.id }, editor.ui.draft.forms.map { it.form.formId })
    assertEquals(
      state.mapLayers.map { it.datasetId ?: it.id },
      editor.ui.mapLayers.map { it.id },
    )
    assertEquals(
      SampleSurveyEditorData.farmers().id to SampleSurveyEditorData.treeSpecies().id,
      editor.ui.dataTables.map { it.id }.let { it[0] to it[1] },
    )
    assertEquals(SampleSurveyEditorData.kenyaSharing(), editor.ui.sharing)

    // Surveys without a stored draft still open with their Forms and Map layers.
    val other = WebAppState(dataHolder = sampleDataHolder())
    other.openSurveyOnWeb("survey-sample-plots-forest")
    val derived = surveyEditorViewModel(other.activeSurveyEditorDraft)
    assertEquals(other.activeSurvey.title, derived.ui.details.title)
    assertEquals(other.forms.map { it.id }, derived.ui.draft.forms.map { it.form.formId })
    assertEquals(other.mapLayers.size, derived.ui.mapLayers.size)
    assertTrue(derived.ui.dataTables.isEmpty())
  }

  @Test
  fun surveyEditor_editsAreSavedToStoreAndSurviveSurveySwitches() {
    val state = WebAppState(dataHolder = sampleDataHolder())
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

    state.openSurveyOnWeb("survey-sample-plots-forest")
    state.openSurveyOnWeb("survey-kenya-coffee")
    val reopened = surveyEditorViewModel(state.activeSurveyEditorDraft)
    assertEquals(editor.ui.draft, reopened.ui.draft)

    runNow { state.dataHolder.sampleDataRepository.resetToSampleData() }
    assertEquals(initial.datasets, state.activeSurveyEditorDraft.datasets)
  }

  @Test
  fun webApp_staysOnlineWhenTheDeviceIsOffline() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val entity =
      state.entities.first { e ->
        e.submissions.any { state.isSubmissionStoredOnDevice(it) } &&
          e.submissions.any { !state.isSubmissionStoredOnDevice(it) }
      }

    state.dataHolder.connectivityRepository.setOnline(false)
    assertTrue(state.isOnline)

    // Full submission history (including other collectors' submissions) stays visible.
    assertEquals(
      entity.submissions.size,
      state.availableGroupedSubmissionsForEntity(entity).sumOf { it.submissions.size },
    )

    // Places search and the PLACES filter tab stay available.
    state.dashboard.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(ListFilterTab.PLACES, state.listFilterTab)
    state.updateListSearchQuery("Othaya")
    assertTrue(state.filteredListPlaces.isNotEmpty())

    // Signing out and back in succeeds.
    state.signOut()
    assertFalse(state.isSignedIn)
    state.signInWithGoogle()
    assertTrue(state.isSignedIn)
  }

  @Test
  fun fromHash_ignoresUnknownAndEmptyHashes() {
    assertNull(WebPage.fromHash("#prototype"))
    assertNull(WebPage.fromHash(""))
  }
}
