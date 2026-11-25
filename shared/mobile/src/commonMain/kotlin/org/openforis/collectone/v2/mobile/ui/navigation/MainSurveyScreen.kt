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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.ui.common.UserProfileSidePanelOverlay
import org.openforis.collectone.v2.app.ui.datacollection.CollectorConsentModalDialog
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsActions
import org.openforis.collectone.v2.app.ui.datacollection.EntityQrCodeModalDialog
import org.openforis.collectone.v2.app.ui.datacollection.PdfExportMessageSnackbar
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListActions
import org.openforis.collectone.v2.app.ui.datacollection.SharePdfToAppModalDialog
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.mobile.ui.common.SignOutConfirmationDialog
import org.openforis.collectone.v2.mobile.ui.common.SignOutConfirmationStep
import org.openforis.collectone.v2.mobile.ui.drawer.MainSurveyNavigationDrawerOverlay
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionActions

/**
 * The Main Survey (`AppScreen.MAIN_SURVEY`), the app's home once a survey is open: the survey map
 * ([SurveyMapView]) with its floating search bar and persistent bottom sheet (the searchable list,
 * or the selected map feature or submission), and the overlays opened over it: the layers sheet,
 * the forms sheet, the navigation drawer, the profile panel, and the record dialogs (QR code, PDF
 * sharing, collector consent).
 *
 * Stateless: whether each overlay shows comes from [uiState] and the feature ViewModels' states, so
 * the screen restores with them, and taps go to the action interfaces. On the web, the map renders
 * behind the Compose canvas, so the background is transparent while the map shows.
 */
@Composable
fun MainSurveyScreen(
  uiState: MainSurveyUiState,
  mapUiState: SurveyMapUiState,
  dataCollectionUiState: DataCollectionUiState,
  actions: MainSurveyActions,
  mapActions: SurveyMapActions,
  dataCollectionActions: DataCollectionActions,
  listActions: SearchableListActions,
  detailsActions: EntityDetailsActions,
  offlineAreaActions: OfflineAreaSelectionActions,
) {
  val surfaceColor =
    if (uiState.isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surface
  val activeQrEntity = dataCollectionUiState.activeQrCodeEntity
  val activePdfSheet = dataCollectionUiState.activeSharedPdfSheet
  var signOutStep by remember { mutableStateOf(SignOutConfirmationStep.NONE) }

  Box(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
    // Main Body: the unified Map + Top Search Bar + Persistent Bottom Sheet View
    SurveyMapView(
      uiState = uiState,
      mapUiState = mapUiState,
      dataCollectionUiState = dataCollectionUiState,
      actions = actions,
      mapActions = mapActions,
      dataCollectionActions = dataCollectionActions,
      listActions = listActions,
      detailsActions = detailsActions,
      offlineAreaActions = offlineAreaActions,
    )

    // Layers Modal Bottom Sheet (confined inside the mobile device frame)
    if (mapUiState.isLayersSheetOpen && !uiState.offlineAreaSelection.isActive) {
      LayersControlSheet(
        uiState = mapUiState,
        actions = mapActions,
        onSaveArea = actions::startOfflineAreaSelection,
      )
    }

    // Available Forms Modal Bottom Sheet (triggered by the bottom-centered FAB)
    if (dataCollectionUiState.isAvailableFormsSheetOpen) {
      AvailableFormsModalSheet(uiState = dataCollectionUiState, actions = dataCollectionActions)
    }

    // Slide-over Hamburger Navigation Drawer Overlay
    if (uiState.isDrawerOpen) {
      MainSurveyNavigationDrawerOverlay(uiState = uiState.drawer, actions = actions)
    }

    // Slide-over Right-Hand User Account Panel Overlay
    if (uiState.isUserProfileOpen) {
      val user = uiState.userProfile
      UserProfileSidePanelOverlay(
        userName = user.name,
        userEmail = user.email,
        userInitials = user.initials,
        organization = user.organization,
        userPhotoUrl = user.photoUrl,
        onDismissRequest = { actions.updateUserProfileOpen(false) },
        onSignOut = { signOutStep = SignOutConfirmationStep.WARNING },
      )
    }

    SignOutConfirmationDialog(
      step = signOutStep,
      isOnline = uiState.isOnline,
      onStepChange = { signOutStep = it },
      onConfirmSignOut = actions::signOut,
    )

    // Scannable Entity GeoID QR Code Modal Overlay
    if (activeQrEntity != null) {
      EntityQrCodeModalDialog(actions = dataCollectionActions, entity = activeQrEntity)
    }

    // Share PDF to Preferred App Modal Overlay (for both Entities and Submissions)
    if (activePdfSheet != null) {
      SharePdfToAppModalDialog(
        uiState = dataCollectionUiState,
        actions = dataCollectionActions,
        sheet = activePdfSheet,
      )
    }

    // Just-in-time Informed Consent Modal Overlay
    if (dataCollectionUiState.pendingCollectorConsent != null) {
      CollectorConsentModalDialog(
        uiState = dataCollectionUiState,
        actions = dataCollectionActions,
        // As wide as Material 3 lets a dialog be: on a phone, the screen less its margins.
        maxWidth = ConsentDialogMaxWidth,
      )
    }

    PdfExportMessageSnackbar(
      uiState = dataCollectionUiState,
      actions = dataCollectionActions,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

private val ConsentDialogMaxWidth = 560.dp
