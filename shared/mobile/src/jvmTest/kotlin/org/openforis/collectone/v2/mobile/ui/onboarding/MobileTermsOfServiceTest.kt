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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingActions
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.core.forms.ui.AppTheme

@OptIn(ExperimentalTestApi::class)
class MobileTermsOfServiceTest {
  private val continueButton = "Continue"
  private val declineButton = "Decline"
  private val checkbox = "I have read and agree to the Collect One Terms of Service."

  private class RecordingActions(private val onTermsChecked: (Boolean) -> Unit) :
    OnboardingActions {
    var acceptCount = 0
    var declineCount = 0

    override fun signInWithGoogle() {}

    override fun setTermsChecked(checked: Boolean) {
      onTermsChecked(checked)
    }

    override fun acceptTermsOfService() {
      acceptCount++
    }

    override fun declineTermsOfService() {
      declineCount++
    }

    override fun navigateBackFromDownloadSurvey() {}

    override fun confirmSignOut() {}

    override fun dismissSignOutPrompt() {}

    override fun updateSearchQuery(query: String) {}

    override fun clearSearchQuery() {}

    override fun downloadSurvey(surveyId: String) {}

    override fun promptRemoveDownloadedSurvey(surveyId: String) {}

    override fun confirmRemoveDownloadedSurvey() {}

    override fun dismissRemoveDownloadedSurvey() {}

    override fun openSurvey(surveyId: String) {}
  }

  @Test
  fun checkbox_startsUncheckedAndDisabledUntilScrolledToBottom_andContinueFollowsCheckbox() =
    runDesktopComposeUiTest(width = 412, height = 860) {
      var uiState by
        mutableStateOf(
          OnboardingUiState(
            isSignedIn = true,
            profile = AuthProfile("Maya Lin", "maya.lin@collect-one.example.org", "ML"),
          )
        )
      val actions = RecordingActions { checked ->
        uiState = uiState.copy(termsCheckboxChecked = checked)
      }

      setContent { AppTheme { TermsOfServiceScreen(uiState = uiState, actions = actions) } }
      waitForIdle()

      // Initial state: checkbox unchecked & disabled, Continue disabled.
      onNodeWithContentDescription(checkbox).assertIsDisplayed().assertIsOff().assertIsNotEnabled()
      onNodeWithContentDescription(continueButton).assertIsDisplayed().assertIsNotEnabled()

      // Clicking the disabled checkbox before scrolling does nothing.
      onNodeWithContentDescription(checkbox).performClick()
      waitForIdle()
      onNodeWithContentDescription(checkbox).assertIsOff().assertIsNotEnabled()
      onNodeWithContentDescription(continueButton).assertIsNotEnabled()

      // Partial scroll keeps the checkbox disabled.
      onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100f) }
      waitForIdle()
      onNodeWithContentDescription(checkbox).assertIsNotEnabled()
      onNodeWithContentDescription(continueButton).assertIsNotEnabled()

      // Scrolling to the bottom enables the checkbox (still unchecked, Continue still disabled).
      onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, 10_000f)
      }
      waitForIdle()
      onNodeWithContentDescription(checkbox).assertIsOff().assertIsEnabled()
      onNodeWithContentDescription(continueButton).assertIsNotEnabled()

      // Scrolling back up keeps the checkbox enabled.
      onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, -10_000f)
      }
      waitForIdle()
      onNodeWithContentDescription(checkbox).assertIsEnabled()

      // Checking enables Continue; unchecking disables Continue again.
      onNodeWithContentDescription(checkbox).performClick()
      waitForIdle()
      onNodeWithContentDescription(checkbox).assertIsOn()
      onNodeWithContentDescription(continueButton).assertIsEnabled()

      onNodeWithContentDescription(checkbox).performClick()
      waitForIdle()
      onNodeWithContentDescription(checkbox).assertIsOff()
      onNodeWithContentDescription(continueButton).assertIsNotEnabled()

      // Re-checking and tapping Continue accepts the terms.
      onNodeWithContentDescription(checkbox).performClick()
      waitForIdle()
      onNodeWithContentDescription(continueButton).performClick()
      waitForIdle()
      assertEquals(1, actions.acceptCount)
    }

  @Test
  fun decline_remainsEnabledBeforeScrolling() =
    runDesktopComposeUiTest(width = 412, height = 860) {
      val actions = RecordingActions {}
      setContent {
        AppTheme {
          TermsOfServiceScreen(
            uiState =
              OnboardingUiState(
                isSignedIn = true,
                profile = AuthProfile("Maya Lin", "maya.lin@collect-one.example.org", "ML"),
              ),
            actions = actions,
          )
        }
      }
      waitForIdle()

      onNodeWithContentDescription(declineButton).assertIsEnabled().performClick()
      waitForIdle()
      assertEquals(1, actions.declineCount)
    }
}
