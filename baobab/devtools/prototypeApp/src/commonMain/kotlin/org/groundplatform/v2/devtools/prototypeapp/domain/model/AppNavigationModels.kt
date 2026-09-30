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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/** Screens in the Ground 2.0 Mobile UI onboarding and survey workflow. */
enum class AppScreen(val stepNumber: Int, val title: String, val subtitle: String) {
  SIGN_IN(stepNumber = 1, title = "Sign In", subtitle = "Google authentication entry point"),
  TERMS_OF_SERVICE(
    stepNumber = 2,
    title = "Terms of Service",
    subtitle = "Data governance & platform terms acceptance",
  ),
  DOWNLOAD_SURVEY(
    stepNumber = 3,
    title = "Download Survey",
    subtitle = "Browse shared surveys or search by name/location",
  ),
  MAIN_SURVEY(
    stepNumber = 4,
    title = "Main Survey (Map & List)",
    subtitle =
      "Survey map, map layers, location & submission bottom sheets, searchable list & drawer",
  ),
}

/** Backward-compatible alias for [AppScreen]. */
typealias PrototypeScreen = AppScreen

/** Primary view mode inside the Main Survey screen (`Map` vs `List`). */
enum class MainSurveyViewMode(val label: String) {
  MAP("Map"),
  LIST("List"),
}

/**
 * Pane shown in a selected map feature's details surface (the web dashboard's floating card or the
 * mobile bottom sheet): its properties (the entity's current state) or its `1:N` submissions (the
 * entity's history).
 */
enum class EntityDetailsPane {
  PROPERTIES,
  SUBMISSIONS,
}

/**
 * Category filter tabs inside the Main Survey searchable bottom sheet (`All`, `Places`, and `Map
 * features`).
 */
enum class ListFilterTab(val label: String) {
  ALL("All"),
  PLACES("Places"),
  ENTITIES("Map features"),
}

/** Sub-screens opened from the Hamburger Navigation Drawer inside the Main Survey UI. */
enum class MainDrawerSubView {
  NONE,
  SWITCH_SURVEYS,
  UPLOADS,
  OUTBOX,
  UPLOADED,
  MANAGE_OFFLINE_MAPS,
  SETTINGS,
}

/** Distinguishes how the user navigated to the "Download surveys" screen. */
enum class DownloadSurveyEntryOrigin {
  /** Shown after accepting the Terms of Service during onboarding (Back prompts to sign out). */
  AFTER_TOS,
  /** Accessed from the Downloaded Surveys list in the Main Survey UI (Back returns to the list). */
  SURVEY_LIST,
}
