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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingActions
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.core.forms.ui.AppTheme

/**
 * Characterizes Download survey's empty-state copy when no surveys are shared yet vs. when a
 * non-blank search query filters out all surveys.
 */
@OptIn(ExperimentalTestApi::class)
class DownloadSurveyEmptyStateTest {
  private val noOpActions =
    object : OnboardingActions {
      override fun signInWithGoogle() {}

      override fun setTermsChecked(checked: Boolean) {}

      override fun acceptTermsOfService() {}

      override fun declineTermsOfService() {}

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
  fun blankQueryWithoutSurveys_showsNoSurveysSharedYetInsteadOfMatchedEmptyQuotes() =
    runComposeUiTest {
      setContent {
        AppTheme {
          DownloadSurveyScreen(
            uiState = OnboardingUiState(surveys = emptyList(), searchQuery = "   "),
            actions = noOpActions,
            notice = null,
            isDarkTheme = false,
          )
        }
      }

      onNodeWithText("No surveys shared with you yet").assertExists()
      onNodeWithText("No surveys match \"\"").assertDoesNotExist()
      onNodeWithText("No surveys match \"   \"").assertDoesNotExist()
    }

  @Test
  fun nonBlankQueryWithoutMatches_showsMatchedQueryMessage() = runComposeUiTest {
    setContent {
      AppTheme {
        DownloadSurveyScreen(
          uiState = OnboardingUiState(surveys = emptyList(), searchQuery = "  zzqx-survey  "),
          actions = noOpActions,
          notice = null,
          isDarkTheme = false,
        )
      }
    }

    onNodeWithText("No surveys match \"zzqx-survey\"").assertExists()
    onNodeWithText("No surveys shared with you yet").assertDoesNotExist()
  }
}
