/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.web.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleSurveyEditorData
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.publishedForm
import org.openforis.collectone.v2.app.ui.session.availableSubmissionGroupsOf
import org.openforis.collectone.v2.web.ui.surveyeditor.surveyEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/**
 * End-to-end checks that the web console reads from and writes through the local data store, and
 * that it stays online when the device it shares the store with is offline.
 */
class WebLocalStoreTest {
  @Test
  fun surveyEditor_loadsSampleDraftFromStore() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val editor = surveyEditorViewModel(state.dataHolder.appData.value.editorDraft)
    assertEquals(state.activeSurveyAccess.activeSurvey.title, editor.ui.details.title)
    // Forms and Map layers are derived from the survey data; Data tables come from the seed.
    assertEquals(
      state.viewModels.dataCollection.uiState.value.forms.map { it.id },
      editor.ui.draft.forms.map { it.form.formId },
    )
    assertEquals(
      state.viewModels.dashboard.uiState.value.mapLayers.map { it.datasetId ?: it.id },
      editor.ui.mapLayers.map { it.id },
    )
    assertEquals(
      SampleSurveyEditorData.farmers().id to SampleSurveyEditorData.treeSpecies().id,
      editor.ui.dataTables.map { it.id }.let { it[0] to it[1] },
    )
    assertEquals(SampleSurveyEditorData.kenyaSharing(), editor.ui.sharing)

    // Surveys without a stored draft still open with their Forms and Map layers.
    val other = WebAppState(dataHolder = sampleDataHolder())
    other.openSurveyOnWeb("00000034-c088-5ab9-8b98-e6bccb3dca71")
    val derived = surveyEditorViewModel(other.dataHolder.appData.value.editorDraft)
    assertEquals(other.activeSurveyAccess.activeSurvey.title, derived.ui.details.title)
    assertEquals(
      other.viewModels.dataCollection.uiState.value.forms.map { it.id },
      derived.ui.draft.forms.map { it.form.formId },
    )
    assertEquals(other.viewModels.dashboard.uiState.value.mapLayers.size, derived.ui.mapLayers.size)
    assertTrue(derived.ui.dataTables.isEmpty())
  }

  @Test
  fun surveyEditor_editsAreSavedToStoreAndSurviveSurveySwitches() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val initial = state.dataHolder.appData.value.editorDraft
    val editor = surveyEditorViewModel(initial)
    editor.updateDetails { it.copy(title = "Renamed survey") }
    editor.addDataset(DatasetKind.DATA_TABLE)
    editor.addForm()
    state.dataHolder.launch {
      state.dataHolder.surveyEditorRepository.saveDraft(
        "00000033-6b50-51cd-ac94-baea2ea06d65",
        editor.ui.draft,
      )
    }

    // The title is shown in the survey list; generated FormDefinitions are published to the survey
    // config.
    assertEquals("Renamed survey", state.activeSurveyAccess.activeSurvey.title)
    val config = state.dataHolder.appData.value.content.config
    val draft = editor.ui.draft
    val newEntry = draft.forms.last()
    val publishedForm = config?.formsById?.get(newEntry.form.formId)
    assertEquals(draft.publishedForm(newEntry), publishedForm)
    // New Forms add rows to their linked Data table by default.
    assertEquals("1", publishedForm?.proto?.model?.entities?.firstOrNull()?.create_condition)

    state.openSurveyOnWeb("00000034-c088-5ab9-8b98-e6bccb3dca71")
    state.openSurveyOnWeb("00000033-6b50-51cd-ac94-baea2ea06d65")
    val reopened = surveyEditorViewModel(state.dataHolder.appData.value.editorDraft)
    assertEquals(editor.ui.draft, reopened.ui.draft)

    runNow { state.dataHolder.sampleDataRepository.resetToSampleData() }
    assertEquals(initial.datasets, state.dataHolder.appData.value.editorDraft.datasets)
  }

  @Test
  fun webApp_staysOnlineWhenTheDeviceIsOffline() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val entity =
      state.viewModels.dataCollection.uiState.value.entities.first { e ->
        e.submissions.any {
          state.viewModels.dashboard.uiState.value.isSubmissionStoredOnDevice(it)
        } &&
          e.submissions.any {
            !state.viewModels.dashboard.uiState.value.isSubmissionStoredOnDevice(it)
          }
      }

    state.dataHolder.connectivityRepository.setOnline(false)
    assertTrue(state.surveyRecords.isOnline)

    // Full submission history (including other collectors' submissions) stays visible.
    assertEquals(
      entity.submissions.size,
      state.surveyRecords.availableSubmissionGroupsOf(entity).sumOf { it.submissions.size },
    )

    // Places search stays available.
    state.searchableListActions.updateListSearchQuery("Othaya")
    assertTrue(state.viewModels.listSearch.uiState.value.places.isNotEmpty())

    // Signing out and back in succeeds.
    state.signOut()
    assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
    state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
    state.viewModels.onboarding.acceptTermsOfService()
    assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)
  }
}
