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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.mobile.ui.state.MobileWorkbenchHooks

/**
 * What the Main Survey's own controls do when tapped: the search bar's menu button, account avatar,
 * search field, and upload status button.
 */
@OptIn(ExperimentalTestApi::class)
class MainSurveyScreenInteractionTest {

  @Test
  fun tappingTheMenuButton_opensTheDrawer() = runMobileNavigationTest { test ->
    val state = show()

    test.onNodeWithContentDescription("Open Navigation Drawer").performClick()
    test.waitForIdle()

    assertTrue(state.isDrawerOpen)
  }

  @Test
  fun tappingTheAvatar_opensTheAccountPanel() = runMobileNavigationTest { test ->
    val state = show()

    test.onNode(hasContentDescription("Account menu", substring = true)).performClick()
    test.waitForIdle()

    assertTrue(state.isUserProfileOpen)
    test.onNodeWithText("Account").assertExists()
  }

  @Test
  fun signingOutFromTheAccountPanel_returnsToSignIn() = runMobileNavigationTest { test ->
    val state = show()
    test.runOnIdle { state.updateUserProfileOpen(true) }
    test.waitForIdle()

    test.onNodeWithText("Sign out").performClick()
    test.waitForIdle()
    test.onNodeWithText("Sign out?").assertExists()
    test.onNodeWithText("Sign out").performClick()
    test.waitForIdle()

    assertFalse(state.isUserProfileOpen)
    assertEquals(MobileRoute.SignIn, state.currentRoute)
  }

  @Test
  fun typingASearch_expandsTheListSheetWithTheQuery() = runMobileNavigationTest { test ->
    val state = show()

    test.onNode(hasSetTextAction()).performTextInput("Parcel")
    test.waitForIdle()

    assertEquals("Parcel", state.viewModels.listSearch.uiState.value.query)
    assertTrue(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun typingASearch_withAMapFeatureSelected_returnsToTheList() = runMobileNavigationTest { test ->
    val state = show()
    test.runOnIdle {
      state.viewModels.surveyMap.selectEntity(
        state.viewModels.dataCollection.uiState.value.entities.first().id
      )
    }
    test.waitForIdle()

    test.onNode(hasSetTextAction()).performTextInput("Parcel")
    test.waitForIdle()

    assertEquals(null, state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun clearingTheSearch_emptiesTheQuery() = runMobileNavigationTest { test ->
    val state = show()
    test.onNode(hasSetTextAction()).performTextInput("Parcel")
    test.waitForIdle()

    test.onNodeWithContentDescription("Clear Search").performClick()
    test.waitForIdle()

    assertEquals("", state.viewModels.listSearch.uiState.value.query)
  }

  @Test
  fun retryingFailedUploadsFromTheStatusButton_retriesThem() = runMobileNavigationTest { test ->
    val state = show()
    var retries = 0
    test.runOnIdle {
      state.workbenchHooks =
        object : MobileWorkbenchHooks {
          override fun onSurveyActivated(surveyId: String) = Unit

          override fun cycleEntitySyncStatus(entityId: String) = Unit

          override fun cycleSubmissionSyncStatus(submissionId: String) = Unit

          override fun retryFailedUploads() {
            retries++
          }
        }
      val failed =
        state.viewModels.dataCollection.uiState.value.entities
          .first()
          .copy(syncStatus = SyncStatus.FAILED)
      val others = state.viewModels.dataCollection.uiState.value.entities.drop(1)
      state.dataHolder.launch {
        state.dataHolder.surveyRepository.setEntities(listOf(failed) + others)
      }
    }
    test.waitForIdle()

    test.onNode(hasContentDescription("failed to upload", substring = true)).performClick()
    test.waitForIdle()
    test.onNodeWithText("Retry failed uploads").performClick()
    test.waitForIdle()

    assertEquals(1, retries)
  }
}
