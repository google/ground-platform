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
package org.openforis.collectone.v2.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlin.time.Instant
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.map.EntityGeometry
import org.openforis.collectone.v2.app.ui.map.WayfindingBadges
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.app.ui.session.ActiveSurveyAccess
import org.openforis.collectone.v2.app.ui.session.SurveyRecordsUiStates
import org.openforis.collectone.v2.app.ui.session.collectActiveSurveyAccess
import org.openforis.collectone.v2.app.ui.session.collectSurveyRecordsUiStates
import org.openforis.collectone.v2.app.ui.session.surveyBottomSheetUiState
import org.openforis.collectone.v2.app.ui.settings.SettingsUiState
import org.openforis.collectone.v2.mobile.ui.drawer.MobileDrawerUiState
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyPage
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyUiState
import org.openforis.collectone.v2.mobile.ui.navigation.SignedInUserUiState
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionUiState
import org.openforis.collectone.v2.mobile.ui.uploads.UploadStatusCounts

/**
 * What the Main Survey shows, following this session's shell, ViewModels, notice, and data, for
 * composition.
 */
@Composable
internal fun MobileAppState.collectMainSurveyUiState(): MainSurveyUiState {
  val records =
    collectSurveyRecordsUiStates(viewModels, dataHolder.appData, notices, isAlwaysOnline = false)
  val settings by viewModels.settings.uiState.collectAsState()
  val onboarding by viewModels.onboarding.uiState.collectAsState()
  val access = collectActiveSurveyAccess(viewModels, dataHolder.appData)
  val lastSyncedAt by dataHolder.lastSyncedAt.collectAsState()
  val offlineArea by offlineAreaSelection.uiState.collectAsState()
  return mainSurveyUiState(
    records = records,
    settings = settings,
    onboarding = onboarding,
    access = access,
    lastSyncedAt = lastSyncedAt,
    badges = viewModels.surveyMap,
    offlineArea = offlineArea,
  )
}

/**
 * What the Main Survey shows, from this session's shell and its ViewModels' states: [records] for
 * the map, sheet, and records, [settings] and [onboarding] for the drawer and the profile panel,
 * [access] for what the user may do in the survey, [lastSyncedAt] for the upload status, and
 * [offlineArea] for the area selection mode.
 */
internal fun MobileAppState.mainSurveyUiState(
  records: SurveyRecordsUiStates,
  settings: SettingsUiState,
  onboarding: OnboardingUiState,
  access: ActiveSurveyAccess,
  lastSyncedAt: Instant?,
  badges: WayfindingBadges,
  offlineArea: OfflineAreaSelectionUiState = offlineAreaSelection.uiState.value,
): MainSurveyUiState {
  val dataCollection = records.dataCollection
  val map = records.map
  val profile = onboarding.profile
  return MainSurveyUiState(
    isMapShowing = isMapShowing(dataCollection),
    isDarkTheme = settings.isDarkTheme,
    isDrawerOpen = isDrawerOpen,
    drawer = drawerUiState(settings, onboarding, records.dashboard, access),
    isUserProfileOpen = isUserProfileOpen,
    userProfile =
      SignedInUserUiState(
        name = profile.displayName,
        initials = profile.initials,
        email = profile.email,
        photoUrl = profile.photoUrl,
        organization = access.affiliation,
      ),
    uploadStatusCounts = UploadStatusCounts.fromMutations(records.dashboard.mutations),
    lastSyncedAt = lastSyncedAt,
    listSearchQuery = records.listSearch.query,
    bottomSheet = records.surveyBottomSheetUiState(badges),
    entityDetailsPane = records.dashboard.entityDetailsPane,
    pendingUploadEntityIds = records.dashboard.pendingUploadEntityIds,
    canCollectData = access.canCollectData,
    selectedEntityBounds = map.selectedEntity?.let(EntityGeometry::bounds),
    offlineAreaSelection = offlineArea,
    isOnline = onboarding.isOnline,
  )
}

/** What the navigation drawer shows. */
internal fun MobileAppState.drawerUiState(
  settings: SettingsUiState,
  onboarding: OnboardingUiState,
  dashboard: DashboardUiState,
  access: ActiveSurveyAccess,
): MobileDrawerUiState =
  MobileDrawerUiState(
    activePage = currentRoute as? MainSurveyPage,
    isDarkTheme = settings.isDarkTheme,
    activeSurveyTitle = access.activeSurvey.title,
    downloadedSurveyCount = onboarding.downloadedSurveyCount,
    pendingUploadCount = dashboard.outboxMutationCount,
    unitSystem = settings.unitSystem,
  )
