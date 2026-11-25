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

import kotlin.time.Instant
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.datacollection.SurveyBottomSheetUiState
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.ui.drawer.MobileDrawerActions
import org.openforis.collectone.v2.mobile.ui.drawer.MobileDrawerUiState
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionUiState
import org.openforis.collectone.v2.mobile.ui.uploads.UploadStatusCounts

/**
 * What the Main Survey shows besides its map ([SurveyMapUiState]) and data collection
 * ([DataCollectionUiState]): the app shell's drawer and user profile panel, the sync status, and
 * the persistent bottom sheet.
 */
data class MainSurveyUiState(
  /**
   * Whether the survey basemap shows. On web it renders behind the Compose canvas, so the screen
   * stays transparent while it does.
   */
  val isMapShowing: Boolean,
  val isDarkTheme: Boolean,
  val isDrawerOpen: Boolean,
  /** What the navigation drawer shows while [isDrawerOpen]. */
  val drawer: MobileDrawerUiState,
  val isUserProfileOpen: Boolean,
  val userProfile: SignedInUserUiState,
  /** How many records are uploading, synced, and failed, summarized by the upload status button. */
  val uploadStatusCounts: UploadStatusCounts,
  /** When the app last synced with the server, or null when it hasn't. */
  val lastSyncedAt: Instant?,
  /** The query in the floating search bar, which filters the searchable list. */
  val listSearchQuery: String,
  val bottomSheet: SurveyBottomSheetUiState,
  /** Which pane of the selected map feature's details shows. */
  val entityDetailsPane: EntityDetailsPane,
  /** Map features with changes not uploaded yet, marked on the map. */
  val pendingUploadEntityIds: Set<String>,
  /** Whether the user can collect data in the survey, which shows the data collection button. */
  val canCollectData: Boolean,
  /** Bounds of the selected map feature's geometry, which the map frames when it's selected. */
  val selectedEntityBounds: LngLatBounds?,
  /**
   * The area selection mode: while active, the map shows a frame to save an area for offline use
   * instead of its controls, sheet, and search bar.
   */
  val offlineAreaSelection: OfflineAreaSelectionUiState = OfflineAreaSelectionUiState(),
  val isOnline: Boolean = true,
)

/** The signed-in user, as the account avatar and the user profile panel show them. */
data class SignedInUserUiState(
  val name: String,
  /** Two-letter initials for the avatar badge (`"ML"` for `"Maya Lin"`). */
  val initials: String,
  val email: String,
  val photoUrl: String?,
  /** The user's organizations joined with " • ", or empty if they belong to none. */
  val organization: String,
)

/**
 * Intents of the Main Survey that the mobile app shell handles: the navigation drawer, the user
 * profile panel, signing out, and retrying failed uploads. The map, the bottom sheet, and data
 * collection report theirs to their own actions.
 */
interface MainSurveyActions : MobileDrawerActions {
  /** Opens or closes the user profile panel. */
  fun updateUserProfileOpen(open: Boolean)

  /** Signs out and returns to Sign in. */
  fun signOut()

  /** Retries every failed upload of the survey. */
  fun retryFailedUploads()

  /** Starts framing an area of the map to save for offline use. */
  fun startOfflineAreaSelection()

  /** Leaves the area selection mode (a save in progress continues). */
  fun closeOfflineAreaSelection()
}
