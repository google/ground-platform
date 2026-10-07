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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem

/**
 * Screen state for the onboarding flow (Sign In → Terms of Service → Download survey), observed by
 * those screens as an immutable snapshot.
 */
data class OnboardingUiState(
  val isSignedIn: Boolean = false,
  val profile: AuthProfile = AuthProfile("", "", ""),
  val hasAcceptedTerms: Boolean = false,
  val termsCheckboxChecked: Boolean = true,
  /** How the Download survey screen was reached, which decides what its Back button does. */
  val entryOrigin: DownloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
  /** True while the "going back will sign you out" confirmation is open. */
  val isSignOutPromptOpen: Boolean = false,
  val searchQuery: String = "",
  /** All surveys shared with the user, in store order. */
  val surveys: List<SurveyPreviewItem> = emptyList(),
  /** [surveys] matching [searchQuery] by title, description, location, or organization. */
  val filteredSurveys: List<SurveyPreviewItem> = emptyList(),
  val downloadedSurveyCount: Int = 0,
  /** Organization display names by ID, for survey cards and search. */
  val organizationNames: Map<String, String> = emptyMap(),
  /** ID of a downloaded survey whose offline copy is pending removal confirmation. */
  val pendingRemovalSurveyId: String? = null,
) {
  val isDownloadSurveyAccessedFromSurveyList: Boolean
    get() = entryOrigin == DownloadSurveyEntryOrigin.SURVEY_LIST
}

/**
 * One-off outcomes of onboarding actions that the app shell acts on (navigation and transient
 * notices). The onboarding screens themselves only render [OnboardingUiState].
 */
sealed interface OnboardingEvent {
  /** Move the mobile flow to [screen]. */
  data class ShowScreen(val screen: AppScreen) : OnboardingEvent

  /** [surveyId] is downloaded and active; show it in the Main Survey UI with [notice]. */
  data class SurveyOpened(val surveyId: String, val notice: String) : OnboardingEvent

  /** Back from Download survey when it was reached from the Survey list: return there. */
  data object ReturnToSurveyList : OnboardingEvent

  /** The user signed out; clear any survey-scoped UI. */
  data object SignedOut : OnboardingEvent

  /** A transient message about a download or removal. */
  data class Notice(val message: String) : OnboardingEvent
}
