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
package org.openforis.collectone.v2.mobile.ui.drawer

import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyPage

/** What the navigation drawer shows. */
data class MobileDrawerUiState(
  /** The drawer page currently open, whose menu item is selected, or null on the Main Survey. */
  val activePage: MainSurveyPage?,
  val isDarkTheme: Boolean,
  val activeSurveyTitle: String,
  val downloadedSurveyCount: Int,
  /** Changes not uploaded yet, shown as a badge on the Uploads item. */
  val pendingUploadCount: Int,
  /** The measurement units, named in the Settings item's subtitle. */
  val unitSystem: MeasurementUnitSystem,
)

/** User intents of the navigation drawer. Implemented by the mobile app shell. */
interface MobileDrawerActions {
  /** Opens or closes the drawer. */
  fun updateDrawerOpen(open: Boolean)

  /** Opens the downloaded surveys sub-screen. */
  fun drawerSwitchSurveys()

  /** Opens the Uploads sub-screen. */
  fun drawerOpenUploads()

  /** Opens the Offline maps sub-screen. */
  fun drawerManageOfflineMaps()

  /** Opens the Settings sub-screen. */
  fun drawerOpenSettings()

  /** Opens the Terms of Service screen. */
  fun drawerViewTermsOfService()
}
