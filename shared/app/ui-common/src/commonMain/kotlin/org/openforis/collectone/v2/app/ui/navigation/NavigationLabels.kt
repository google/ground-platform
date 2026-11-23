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
package org.openforis.collectone.v2.app.ui.navigation

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_app_screen_download_survey_subtitle
import org.openforis.collectone.v2.app.resources.domain_app_screen_download_survey_title
import org.openforis.collectone.v2.app.resources.domain_app_screen_main_survey_subtitle
import org.openforis.collectone.v2.app.resources.domain_app_screen_main_survey_title
import org.openforis.collectone.v2.app.resources.domain_app_screen_sign_in_subtitle
import org.openforis.collectone.v2.app.resources.domain_app_screen_sign_in_title
import org.openforis.collectone.v2.app.resources.domain_app_screen_terms_of_service_subtitle
import org.openforis.collectone.v2.app.resources.domain_app_screen_terms_of_service_title
import org.openforis.collectone.v2.app.resources.domain_main_survey_view_mode_list
import org.openforis.collectone.v2.app.resources.domain_main_survey_view_mode_map

// Localized display labels of the navigation and screen models.

/** Localized title of this screen; resolve with `stringResource`. */
val AppScreen.titleRes: StringResource
  get() =
    when (this) {
      AppScreen.SIGN_IN -> Res.string.domain_app_screen_sign_in_title
      AppScreen.TERMS_OF_SERVICE -> Res.string.domain_app_screen_terms_of_service_title
      AppScreen.DOWNLOAD_SURVEY -> Res.string.domain_app_screen_download_survey_title
      AppScreen.MAIN_SURVEY -> Res.string.domain_app_screen_main_survey_title
    }

/** Localized one-line description of this screen; resolve with `stringResource`. */
val AppScreen.subtitleRes: StringResource
  get() =
    when (this) {
      AppScreen.SIGN_IN -> Res.string.domain_app_screen_sign_in_subtitle
      AppScreen.TERMS_OF_SERVICE -> Res.string.domain_app_screen_terms_of_service_subtitle
      AppScreen.DOWNLOAD_SURVEY -> Res.string.domain_app_screen_download_survey_subtitle
      AppScreen.MAIN_SURVEY -> Res.string.domain_app_screen_main_survey_subtitle
    }

/** Localized label of this view mode; resolve with `stringResource`. */
val MainSurveyViewMode.labelRes: StringResource
  get() =
    when (this) {
      MainSurveyViewMode.MAP -> Res.string.domain_main_survey_view_mode_map
      MainSurveyViewMode.LIST -> Res.string.domain_main_survey_view_mode_list
    }
