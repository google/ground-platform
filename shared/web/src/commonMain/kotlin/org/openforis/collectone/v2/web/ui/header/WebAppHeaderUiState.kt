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
package org.openforis.collectone.v2.web.ui.header

import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

/**
 * What the [WebAppHeader] shows on every page of the web console: whether changes are being saved,
 * the app language, and the signed-in user's profile.
 */
internal data class WebAppHeaderUiState(
  /**
   * Whether a survey, an organization, or the outbox is being saved, or failed to save; see
   * [headerSaveStatus].
   */
  val saveStatus: SaveStatus = SaveStatus.IDLE,
  val languageCode: String = "",
  val userName: String = "",
  val userEmail: String = "",
  val userInitials: String = "",
  val userPhotoUrl: String? = null,
  /** Names of the signed-in user's organizations, as one line. */
  val userOrganizations: String = "",
  /** Whether the signed-in user is a platform administrator, who can open the admin panel. */
  val isPlatformAdmin: Boolean = false,
)

/**
 * The header's [SaveStatus]: saving while the Survey editor or an organization page writes
 * ([isSavingLocally]) or the sync engine sends edits ([outbox]), else failed while edits wait to
 * retry a failed send ([outbox]) or the server rejected one the user hasn't dismissed yet
 * ([hasUnacknowledgedRejection]), so the header never claims "Saved" for edits that weren't.
 */
internal fun headerSaveStatus(
  isSavingLocally: Boolean,
  outbox: SaveStatus,
  hasUnacknowledgedRejection: Boolean = false,
): SaveStatus =
  when {
    isSavingLocally || outbox == SaveStatus.SAVING -> SaveStatus.SAVING
    outbox == SaveStatus.FAILED || hasUnacknowledgedRejection -> SaveStatus.FAILED
    else -> SaveStatus.IDLE
  }

/**
 * Whether the header confirms "Saved" when its save status goes from [previous] to [current]: only
 * when a save finished without failing.
 */
internal fun showsSavedConfirmation(previous: SaveStatus, current: SaveStatus): Boolean =
  previous == SaveStatus.SAVING && current == SaveStatus.IDLE

/** Intents of the [WebAppHeader]. */
internal interface WebAppHeaderActions {
  /** Opens the surveys landing page; what the logo does unless the page says otherwise. */
  fun openSurveys()

  fun updateLanguage(languageCode: String)

  /** Opens the organizations directory from the profile menu. */
  fun openOrganizations()

  /** Opens the platform admin panel; only offered when [WebAppHeaderUiState.isPlatformAdmin]. */
  fun openAdminPanel()
}

/**
 * The active survey as the dashboard's [WebTopToolbar] shows it, with the surveys its
 * [WebSurveySwitcher] offers.
 */
internal data class WebSurveyToolbarUiState(
  val activeSurveyId: String,
  val activeSurvey: SurveyPreviewItem,
  /** Name of the active survey's organization, if it belongs to one. */
  val organizationName: String? = null,
  /** Up to 5 recently viewed surveys, starting with the active one. */
  val recentSurveys: List<SurveyPreviewItem> = emptyList(),
  /** Whether the signed-in user may open the Survey editor and change the survey's lifecycle. */
  val canManageSurvey: Boolean = false,
  /** Whether the signed-in user can collect data in the survey. */
  val canCollectData: Boolean = false,
  val hasUnpublishedChanges: Boolean = false,
)
