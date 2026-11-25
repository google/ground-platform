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
import androidx.compose.ui.graphics.Color

/** What shows behind a system bar (the status bar at the top, the navigation bar at the bottom). */
internal enum class SystemBarBackdrop {
  /** The map, which extends behind the bar: nothing is drawn over it. */
  MAP,
  /** A screen's surface, as its top app bar and page have. */
  SURFACE,
  /** The background of the data collection form, which its top and bottom bars share. */
  FORM,
  /** The Main Survey's bottom sheet, which reaches the bottom of the display. */
  SHEET,
}

/** The backdrops of the two system bars for one screen. */
internal data class SystemBarBackdrops(val top: SystemBarBackdrop, val bottom: SystemBarBackdrop)

/**
 * The backdrops of the system bars, chosen to match the container next to each bar so the screen
 * reads as continuous:
 * - on the main map, the map behind the status bar and the bottom sheet behind the navigation bar
 *   (or the map there too while an offline area is selected, when the sheet is hidden);
 * - on the data collection form, the form's own background;
 * - everywhere else, the surface the screens and their top app bars use.
 */
internal fun mobileSystemBarBackdrops(
  isMainMapScreenShowing: Boolean,
  isSelectingOfflineArea: Boolean,
  isDataCollectionFormShowing: Boolean,
): SystemBarBackdrops =
  when {
    isMainMapScreenShowing && isSelectingOfflineArea ->
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.MAP)
    isMainMapScreenShowing ->
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.SHEET)
    isDataCollectionFormShowing ->
      SystemBarBackdrops(top = SystemBarBackdrop.FORM, bottom = SystemBarBackdrop.FORM)
    else -> SystemBarBackdrops(top = SystemBarBackdrop.SURFACE, bottom = SystemBarBackdrop.SURFACE)
  }

/** The color drawn behind a system bar with this backdrop. */
@Composable
internal fun SystemBarBackdrop.color(): Color =
  when (this) {
    SystemBarBackdrop.MAP -> Color.Transparent
    SystemBarBackdrop.SURFACE -> MaterialTheme.colorScheme.surface
    SystemBarBackdrop.FORM -> MaterialTheme.colorScheme.background
    SystemBarBackdrop.SHEET -> MaterialTheme.colorScheme.surfaceContainerLow
  }
