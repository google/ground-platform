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
package org.openforis.collectone.v2.app.ui.session

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.SimulatedRole
import org.openforis.collectone.v2.app.ui.onboarding.DevSignInUiState

/** The developer sign-in dialog explains the sign-in in use and reports the chosen role. */
@OptIn(ExperimentalTestApi::class)
class DevSignInDialogTest {
  private val calls = mutableListOf<String>()

  @Composable
  private fun Dialog(usesRealAccounts: Boolean) {
    MaterialTheme {
      DevSignInDialog(
        state = DevSignInUiState(SimulatedRole.ADMINISTRATOR, usesRealAccounts),
        onSelectRole = { calls += "role $it" },
        onConfirm = { calls += "confirm" },
        onDismiss = { calls += "dismiss" },
      )
    }
  }

  @Test
  fun withRealAccounts_explainsTheRoleIsSimulatedOverTheDevelopersAccount() =
    runDesktopComposeUiTest {
      setContent { Dialog(usesRealAccounts = true) }

      onNodeWithText("You're signed in with your Google account", substring = true)
        .assertIsDisplayed()
    }

  @Test
  fun withoutRealAccounts_explainsTheSampleUsers() = runDesktopComposeUiTest {
    setContent { Dialog(usesRealAccounts = false) }

    onNodeWithText("you'll sign in as a sample user", substring = true).assertIsDisplayed()
  }

  @Test
  fun choosingARoleAndContinuing_reportsEachIntent() = runDesktopComposeUiTest {
    setContent { Dialog(usesRealAccounts = true) }

    onNodeWithContentDescription("Administrator").assertIsSelected()
    onNodeWithContentDescription("Regular user").performClick()
    onNodeWithText("Continue").performClick()

    assertEquals(listOf("role ${SimulatedRole.REGULAR_USER}", "confirm"), calls)
  }
}
