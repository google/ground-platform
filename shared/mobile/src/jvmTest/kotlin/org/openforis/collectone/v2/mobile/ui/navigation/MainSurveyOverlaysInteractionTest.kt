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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Main Survey's overlays do when tapped: the forms button and its sheet, the layers sheet,
 * and the wayfinding banner.
 */
@OptIn(ExperimentalTestApi::class)
class MainSurveyOverlaysInteractionTest {

  @Test
  fun formsButton_opensTheFormsSheet_andCloseClosesIt() = runMobileNavigationTest { test ->
    val state = show()

    test.onNodeWithContentDescription("Collect data").performClick()
    test.waitForIdle()
    assertTrue(state.viewModels.dataCollection.uiState.value.isAvailableFormsSheetOpen)
    test.onNodeWithText("Available data collection forms").assertExists()

    test.onNodeWithContentDescription("Close Available Forms").performClick()
    test.waitForIdle()
    assertFalse(state.viewModels.dataCollection.uiState.value.isAvailableFormsSheetOpen)
    test.onNodeWithText("Available data collection forms").assertDoesNotExist()
  }

  @Test
  fun tappingAFormThatCanStart_startsItFromTheSheet() = runMobileNavigationTest { test ->
    val state = show()
    test.runOnIdle { state.viewModels.dataCollection.openAvailableFormsSheet() }
    test.waitForIdle()
    val dataCollection = state.viewModels.dataCollection.uiState.value
    val form =
      assertNotNull(
        dataCollection.mobileForms.firstOrNull {
          !it.requiresEntity || dataCollection.eligibleEntitiesForForm(it).isNotEmpty()
        },
        "the sample survey has a form that can start",
      )

    test.onNodeWithText(form.title).performScrollTo().performClick()
    test.waitForIdle()

    val after = state.viewModels.dataCollection.uiState.value
    assertFalse(after.isAvailableFormsSheetOpen)
    // The form opens at once, or after the collector's consent when the survey asks for it.
    assertTrue(after.isDataCollectionFormOpen || after.pendingCollectorConsent != null)
  }

  @Test
  fun layersSheet_closesWithItsCloseButton() = runMobileNavigationTest { test ->
    val state = show()
    test.runOnIdle { state.viewModels.surveyMap.updateLayersSheetOpen(true) }
    test.waitForIdle()
    test.onNodeWithText("Basemap").assertExists()

    test.onNodeWithContentDescription("Close layers").performClick()
    test.waitForIdle()

    assertFalse(state.viewModels.surveyMap.uiState.value.isLayersSheetOpen)
    test.onNodeWithText("Basemap").assertDoesNotExist()
  }

  @Test
  fun wayfindingBanner_showsWhileNavigating_andEndStopsIt() = runMobileNavigationTest { test ->
    val state = show()
    test.runOnIdle {
      val entity = state.viewModels.dataCollection.uiState.value.entities.first()
      state.viewModels.surveyMap.startNavigationToEntity(entity.id)
    }
    test.waitForIdle()
    assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)

    test.onNodeWithContentDescription("Stop Straight-Line Navigation").performClick()
    test.waitForIdle()

    assertNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    test.onNodeWithContentDescription("Stop Straight-Line Navigation").assertDoesNotExist()
  }
}
