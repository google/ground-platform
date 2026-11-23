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
package org.openforis.collectone.v2.app.ui.datacollection

import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Form launches of [DataCollectionViewModel] over the seeded sample data that its tests in
 * `:shared:app` don't cover: the forms sheet toggle, the workbench shortcut, and the `entityref`
 * step's dataset check. Fixtures are built outside any coroutine, like the ViewModel's other tests.
 */
class DataCollectionFormLaunchTest {
  @Test
  fun toggleAvailableFormsSheet_opensAndCloses() {
    val f = DataCollectionFixture()

    f.viewModel.toggleAvailableFormsSheet()
    assertTrue(f.uiState.isAvailableFormsSheetOpen)
    f.viewModel.toggleAvailableFormsSheet()
    assertFalse(f.uiState.isAvailableFormsSheetOpen)
  }

  @Test
  fun launchActiveOrDefaultFormForTesting_opensTheFormTheWorkbenchChooses() {
    val f = DataCollectionFixture()
    val expected = assertNotNull(chooseTestingFormLaunch(f.uiState))

    f.viewModel.launchActiveOrDefaultFormForTesting()

    assertTrue(f.uiState.isDataCollectionFormOpen)
    when (expected) {
      is TestingFormLaunch.ForEntity -> {
        assertEquals(expected.formId, f.uiState.activeDataCollectionFormId)
        assertEquals(expected.entityId, f.uiState.activeDataCollectionEntityId)
      }
      is TestingFormLaunch.FromFab -> {
        assertEquals(expected.formId, f.uiState.activeDataCollectionFormId)
        assertNull(f.uiState.activeDataCollectionEntityId)
      }
    }
  }

  @Test
  fun launchedForms_startOnTheViewModelsClock() {
    val f = DataCollectionFixture()
    // The fixture's ViewModel clock: 2023-11-14T22:13:20Z, not the engine's fixed 2026-09-17.
    val clockTime = ofEpochSecond(1_700_000_000L, 0L)

    f.viewModel.launchFormFromFab(f.uiState.forms.first { !it.requiresEntity }.id)
    assertEquals(clockTime, startTimeOfActiveForm(f))

    f.viewModel.closeActiveFormRunner()
    f.viewModel.launchFormFromFab(f.uiState.forms.first { it.requiresEntity }.id)
    assertEquals(clockTime, startTimeOfActiveForm(f))
  }

  private fun startTimeOfActiveForm(f: DataCollectionFixture) =
    assertNotNull(f.uiState.activeFormWizardController)
      .formState
      .recordInstance
      .metadata
      ?.start_time

  @Test
  fun selectEntityRefForActiveForm_ignoresFeaturesOfOtherDataTables() {
    val f = DataCollectionFixture()
    val form = f.uiState.forms.first { it.requiresEntity }
    val outsider =
      assertNotNull(f.uiState.entities.firstOrNull { it.datasetId != form.targetDatasetId })
    f.viewModel.launchFormFromFab(form.id)

    f.viewModel.selectEntityRefForActiveForm(outsider.id)
    f.viewModel.selectEntityRefForActiveForm("missing")

    assertNull(f.uiState.activeDataCollectionEntityId)
    assertTrue(f.uiState.wasFormLaunchedWithoutEntity)
  }
}
