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

import org.openforis.collectone.v2.app.ui.navigation.AppScreen

/**
 * How a client's shell (the mobile app's routes and drawer, or the web console's pages) follows the
 * outcomes [SessionCoordinator] routes out of the feature ViewModels. Each reaction does nothing by
 * default; a client overrides the ones its shell shows.
 */
interface SessionShell {
  /** The onboarding flow asks to show [screen] (Sign In, Terms of Service, Download survey). */
  fun onScreenRequested(screen: AppScreen) {}

  /** A survey was opened from the onboarding flow or the survey list and is now active. */
  fun onSurveyOpened() {}

  /** The onboarding flow asks to return to the list of downloaded surveys. */
  fun onReturnToSurveyList() {}

  /** The user signed out through this session's onboarding flow. */
  fun onSignedOut() {}

  /**
   * The signed-in user was signed out without this session's involvement (for example from another
   * session sharing the same data store).
   */
  fun onSignedOutElsewhere() {}

  /** A map feature or place was selected, so the map should be visible. */
  fun onMapShown() {}

  /** Straight-line navigation started, so the survey map should be visible. */
  fun onNavigationStarted() {}

  /** The `Uploads` screen was opened. */
  fun onUploadsOpened() {}

  /** The Available Forms sheet was opened. */
  fun onFormsSheetOpened() {}

  /** Data collection asks to show the searchable list. */
  fun onListShown() {}

  /** A data collection form was opened. */
  fun onFormOpened() {}

  /** The survey with [surveyId] became active in this session. */
  fun onSurveyActivated(surveyId: String) {}
}
