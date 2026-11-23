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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.openforis.collectone.v2.app.ui.navigation.AppScreen

/**
 * A destination of the mobile app's navigation graph. Routes carry no data: what a screen shows
 * comes from its ViewModels and the local data store. Their serial names are the URL fragments the
 * mobile app shows in the browser (`#survey`, `#uploads`), so keep them short and stable.
 */
@Serializable
sealed interface MobileRoute {
  /** Sign in with Google. */
  @Serializable @SerialName("sign-in") data object SignIn : MobileRoute

  /** The Terms of Service, accepted once after signing in (and viewable from the drawer). */
  @Serializable @SerialName("terms") data object TermsOfService : MobileRoute

  /** The directory of surveys shared with the user, to download for offline use. */
  @Serializable @SerialName("download-survey") data object DownloadSurvey : MobileRoute

  /** The active survey's map, searchable list, and navigation drawer. */
  @Serializable @SerialName("survey") data object MainSurvey : MobileRoute

  /** The data collection form opened from the Main Survey. */
  @Serializable @SerialName("form") data object DataCollection : MobileRoute

  /** The surveys already on the device (the drawer's `Surveys` item). */
  @Serializable @SerialName("surveys") data object DownloadedSurveys : MainSurveyPage

  /** Changes waiting for, being, or already uploaded (the drawer's `Uploads` item). */
  @Serializable @SerialName("uploads") data object Uploads : MainSurveyPage

  /** Offline basemap packages (the drawer's `Offline maps` item). */
  @Serializable @SerialName("offline-maps") data object OfflineMaps : MainSurveyPage

  /** App settings (the drawer's `Settings` item). */
  @Serializable @SerialName("settings") data object Settings : MainSurveyPage

  companion object {
    /** Every route, for mapping a back stack entry back to its route. */
    val all: List<MobileRoute> =
      listOf(
        SignIn,
        TermsOfService,
        DownloadSurvey,
        MainSurvey,
        DataCollection,
        DownloadedSurveys,
        Uploads,
        OfflineMaps,
        Settings,
      )
  }
}

/** A page the navigation drawer opens over the [MobileRoute.MainSurvey]; Back returns to it. */
@Serializable sealed interface MainSurveyPage : MobileRoute

/** The route of the onboarding or survey step [this] names (as onboarding events do). */
fun AppScreen.toRoute(): MobileRoute =
  when (this) {
    AppScreen.SIGN_IN -> MobileRoute.SignIn
    AppScreen.TERMS_OF_SERVICE -> MobileRoute.TermsOfService
    AppScreen.DOWNLOAD_SURVEY -> MobileRoute.DownloadSurvey
    AppScreen.MAIN_SURVEY -> MobileRoute.MainSurvey
  }

/**
 * The onboarding or survey step this route belongs to: the Main Survey's pages and form are in it.
 */
val MobileRoute.appScreen: AppScreen
  get() =
    when (this) {
      MobileRoute.SignIn -> AppScreen.SIGN_IN
      MobileRoute.TermsOfService -> AppScreen.TERMS_OF_SERVICE
      MobileRoute.DownloadSurvey -> AppScreen.DOWNLOAD_SURVEY
      MobileRoute.MainSurvey,
      MobileRoute.DataCollection,
      is MainSurveyPage -> AppScreen.MAIN_SURVEY
    }
