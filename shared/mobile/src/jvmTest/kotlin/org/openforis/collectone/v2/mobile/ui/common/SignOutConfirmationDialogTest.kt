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
package org.openforis.collectone.v2.mobile.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.core.forms.ui.AppTheme

@OptIn(ExperimentalTestApi::class)
class SignOutConfirmationDialogTest {
  @Test
  fun onlineSignOut_warnsAboutInternetRequirementAndSignsOutOnceConfirmed() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      var step by mutableStateOf(SignOutConfirmationStep.WARNING)
      var signOutCount = 0

      setContent {
        AppTheme {
          SignOutConfirmationDialog(
            step = step,
            isOnline = true,
            onStepChange = { step = it },
            onConfirmSignOut = { signOutCount++ },
          )
        }
      }
      waitForIdle()

      onNodeWithText("Sign out?").assertExists()
      onNodeWithText("internet connection", substring = true).assertExists()
      onNodeWithText("Sign out").performClick()
      waitForIdle()

      assertEquals(1, signOutCount)
      assertEquals(SignOutConfirmationStep.NONE, step)
    }

  @Test
  fun offlineSignOut_asksTwiceBeforeSigningOut() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      var step by mutableStateOf(SignOutConfirmationStep.WARNING)
      var signOutCount = 0

      setContent {
        AppTheme {
          SignOutConfirmationDialog(
            step = step,
            isOnline = false,
            onStepChange = { step = it },
            onConfirmSignOut = { signOutCount++ },
          )
        }
      }
      waitForIdle()

      onNodeWithText("Sign out?").assertExists()
      onNodeWithText("Continue").performClick()
      waitForIdle()

      assertEquals(0, signOutCount)
      assertEquals(SignOutConfirmationStep.OFFLINE_SECOND_CONFIRM, step)
      onNodeWithText("You are currently offline").assertExists()

      onNodeWithText("Sign out anyway").performClick()
      waitForIdle()

      assertEquals(1, signOutCount)
      assertEquals(SignOutConfirmationStep.NONE, step)
    }
}
