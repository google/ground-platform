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
package org.openforis.collectone.v2.app.ui.navigation

/** Screens in the Collect One Mobile UI onboarding and survey workflow. */
enum class AppScreen {
  SIGN_IN,
  TERMS_OF_SERVICE,
  DOWNLOAD_SURVEY,
  MAIN_SURVEY,
}

/** Primary view mode inside the Main Survey screen (`Map` vs `List`). */
enum class MainSurveyViewMode {
  MAP,
  LIST,
}

/**
 * Pane shown in a selected map feature's details surface (the web dashboard's floating card or the
 * mobile bottom sheet): its properties (the entity's current state), its `1:N` submissions (the
 * entity's history), or its Open Foris Whisp risk analysis.
 */
enum class EntityDetailsPane {
  PROPERTIES,
  SUBMISSIONS,
  RISK_ANALYSIS,
}

/** Distinguishes how the user navigated to the "Download surveys" screen. */
enum class DownloadSurveyEntryOrigin {
  /** Shown after accepting the Terms of Service during onboarding (Back prompts to sign out). */
  AFTER_TOS,
  /** Accessed from the Downloaded Surveys list in the Main Survey UI (Back returns to the list). */
  SURVEY_LIST,
}
