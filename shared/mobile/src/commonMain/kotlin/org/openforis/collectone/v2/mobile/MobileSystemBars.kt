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
package org.openforis.collectone.v2.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** What shows behind a system bar (the status bar at the top, the navigation bar at the bottom). */
internal enum class SystemBarBackdrop {
  /**
   * Whatever the screen draws there: nothing is drawn over it. On the Main Survey that is the map,
   * or the bottom sheet once it covers the screen.
   */
  MAP,
  /** A screen's surface, as its top app bar and page have. */
  SURFACE,
  /** The Main Survey's bottom sheet, which reaches the bottom of the display. */
  SHEET,
  /** The banner at the top of the data collection form. */
  FORM_BANNER,
  /** The bar with the form's Back and Next actions. */
  FORM_ACTIONS,
  /** The bar with the actions of the form step that picks a map feature. */
  PICKER_ACTIONS,
}

/** The backdrops of the two system bars for one screen. */
internal data class SystemBarBackdrops(val top: SystemBarBackdrop, val bottom: SystemBarBackdrop)

/** What the open data collection form shows at its top and bottom edges. */
internal data class FormEdges(
  val isSubmissionConfirmationShown: Boolean = false,
  val isFeaturePickerStep: Boolean = false,
)

/**
 * The backdrops of the system bars, chosen to match the container next to each bar so the screen
 * reads as continuous:
 * - on the main map, the screen's own content behind the status bar and the bottom sheet behind the
 *   navigation bar (or the map there too while an offline area is selected, when the sheet is
 *   hidden; or the surface of a map feature's history while that covers the map);
 * - on the data collection form ([openForm]), the form's banner at the top and its action bar at
 *   the bottom, and the plain surface once the submission is confirmed;
 * - everywhere else, the surface the screens and their top app bars use.
 */
internal fun mobileSystemBarBackdrops(
  isMainMapScreenShowing: Boolean,
  isSelectingOfflineArea: Boolean,
  openForm: FormEdges?,
  isFeatureHistoryShowing: Boolean = false,
): SystemBarBackdrops =
  when {
    isMainMapScreenShowing && isSelectingOfflineArea ->
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.MAP)
    // The history screen covers the main map and draws its own surface behind the status bar.
    isMainMapScreenShowing && isFeatureHistoryShowing ->
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.SURFACE)
    isMainMapScreenShowing ->
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.SHEET)
    openForm == null || openForm.isSubmissionConfirmationShown ->
      SystemBarBackdrops(top = SystemBarBackdrop.SURFACE, bottom = SystemBarBackdrop.SURFACE)
    else ->
      SystemBarBackdrops(
        top = SystemBarBackdrop.FORM_BANNER,
        bottom =
          if (openForm.isFeaturePickerStep) SystemBarBackdrop.PICKER_ACTIONS
          else SystemBarBackdrop.FORM_ACTIONS,
      )
  }

/** The color drawn behind a system bar with this backdrop. */
@Composable
internal fun SystemBarBackdrop.color(): Color =
  when (this) {
    SystemBarBackdrop.MAP -> Color.Transparent
    SystemBarBackdrop.SURFACE -> MaterialTheme.colorScheme.surface
    SystemBarBackdrop.SHEET -> MaterialTheme.colorScheme.surfaceContainerLow
    SystemBarBackdrop.FORM_BANNER -> MaterialTheme.colorScheme.primary
    SystemBarBackdrop.FORM_ACTIONS -> MaterialTheme.colorScheme.surfaceContainer
    SystemBarBackdrop.PICKER_ACTIONS -> MaterialTheme.colorScheme.surfaceContainerLow
  }

/** The backdrops of the system bars for the screen [state] shows now. */
@Composable
internal fun currentSystemBarBackdrops(
  state: MobileAppState,
  dataCollection: DataCollectionUiState,
): SystemBarBackdrops {
  val offlineAreaSelection by state.offlineAreaSelection.uiState.collectAsState()
  val dashboard by state.viewModels.dashboard.uiState.collectAsState()
  val isFormShowing =
    dataCollection.isDataCollectionFormOpen &&
      (state.currentRoute == MobileRoute.MainSurvey ||
        state.currentRoute == MobileRoute.DataCollection)
  return mobileSystemBarBackdrops(
    isMainMapScreenShowing = state.isMainMapScreenShowing(dataCollection),
    isSelectingOfflineArea = offlineAreaSelection.isActive,
    isFeatureHistoryShowing =
      dataCollection.selectedEntity != null &&
        (dashboard.entityDetailsPane == EntityDetailsPane.SUBMISSIONS ||
          dataCollection.selectedSubmission != null),
    openForm =
      if (isFormShowing) {
        FormEdges(
          isSubmissionConfirmationShown = dataCollection.isSubmissionConfirmationShown,
          isFeaturePickerStep = dataCollection.isCurrentFormStepEntityRef,
        )
      } else {
        null
      },
  )
}

/**
 * The colors behind the two system bars for the screen [state] shows now, for hosts that draw the
 * bars themselves (a simulated device frame). [Color.Transparent] means the screen's own content
 * shows behind the bar.
 */
data class MobileSystemBarColors(val top: Color, val bottom: Color)

/** The [MobileSystemBarColors] of the screen [state] shows now, as [MobileApp] paints them. */
@Composable
fun mobileSystemBarColors(state: MobileAppState): MobileSystemBarColors {
  val dataCollection by state.viewModels.dataCollection.uiState.collectAsState()
  val backdrops = currentSystemBarBackdrops(state, dataCollection)
  return MobileSystemBarColors(top = backdrops.top.color(), bottom = backdrops.bottom.color())
}
