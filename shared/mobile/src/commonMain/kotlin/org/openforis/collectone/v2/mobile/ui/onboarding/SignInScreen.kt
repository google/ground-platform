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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.CloudAcaciaLogo
import org.openforis.collectone.v2.app.ui.common.DemoAttributionFooterBar
import org.openforis.collectone.v2.app.ui.common.DemoBadge
import org.openforis.collectone.v2.app.ui.common.GoogleLogoIcon
import org.openforis.collectone.v2.app.ui.common.LocalMobileTopInset
import org.openforis.collectone.v2.app.ui.common.MissionGraphic
import org.openforis.collectone.v2.app.ui.session.BackendErrorBanner
import org.openforis.collectone.v2.app.ui.session.UserDataSyncErrorBanner
import org.openforis.collectone.v2.core.forms.ui.LocalAppBrandFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res as UiRes
import org.openforis.collectone.v2.core.forms.ui.resources.sign_in_with_google
import org.openforis.collectone.v2.core.version.MobileUpdateStatus
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_sign_in_google_accounts_only
import org.openforis.collectone.v2.mobile.resources.mobile_sign_in_offline_error
import org.openforis.collectone.v2.mobile.resources.mobile_sign_in_prompt
import org.openforis.collectone.v2.mobile.resources.mobile_sign_in_signing_in
import org.openforis.collectone.v2.mobile.resources.mobile_sign_in_tagline
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.MobileNavHost
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.version.MobileUpdateRequiredScreen
import org.openforis.collectone.v2.mobile.ui.version.MobileUpdateSuggestedBanner

/**
 * Host of the Collect One mobile app's screens ([MobileNavHost], driven by [navController]) with
 * the sync and backend error banners over them and the demo attribution footer below:
 * 1. Sign In Screen (Sign in with Google only)
 * 2. Terms of Service Screen
 * 3. Download Survey Screen (with search by name or location, map thumbnail, and downloaded
 *    indicator)
 * 4. Main Survey UI (Map & List Views, Layers toggle, simplestyle-spec Marker Progression ○ → ◐ →
 *    ✓, and Navigation Drawer), its drawer pages, and the data collection form
 */
@Composable
fun MobileScreenHost(
  state: MobileAppState,
  navController: NavHostController = rememberNavController(),
) {
  val topInset = LocalMobileTopInset.current
  val clientVersionRepository = state.dataHolder.clientVersionRepository
  val updateStatus by clientVersionRepository.updateStatus.collectAsState()
  val isSuggestionDismissed by clientVersionRepository.isSuggestionDismissed.collectAsState()
  val scope = rememberCoroutineScope()
  var isCheckingUpdate by remember { mutableStateOf(false) }

  LaunchedEffect(clientVersionRepository) { clientVersionRepository.refreshPolicy() }

  Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      MobileNavHost(state, navController)
      val syncState by state.dataHolder.userDataSyncState.collectAsState()
      val backendError by state.dataHolder.backendError.collectAsState()
      val isOnline by state.dataHolder.connectivityRepository.observeIsOnline().collectAsState()
      val backStackEntry by navController.currentBackStackEntryAsState()
      val onDownloadSurvey =
        backStackEntry?.destination?.hasRoute(MobileRoute.DownloadSurvey::class) == true
      val isUpdateRequired = updateStatus is MobileUpdateStatus.Required
      if (!isUpdateRequired) {
        Column(
          modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          val suggested = updateStatus as? MobileUpdateStatus.Suggested
          if (suggested != null && !isSuggestionDismissed) {
            MobileUpdateSuggestedBanner(
              status = suggested,
              onDismiss = clientVersionRepository::dismissSuggestion,
            )
          }
          if (
            showsUserDataSyncErrorBanner(isOnline = isOnline, onDownloadSurvey = onDownloadSurvey)
          ) {
            UserDataSyncErrorBanner(
              state = syncState,
              onRetry = state.dataHolder::retryUserDataSync,
            )
          }
          BackendErrorBanner(
            error = backendError,
            onRetry = state.dataHolder::retryBackendSync,
            onDismiss = state.dataHolder::dismissBackendError,
          )
        }
      }
      DemoBadge(modifier = Modifier.align(Alignment.TopEnd).padding(top = topInset))
      val required = updateStatus as? MobileUpdateStatus.Required
      if (required != null) {
        MobileUpdateRequiredScreen(
          status = required,
          isChecking = isCheckingUpdate,
          onCheckAgain = {
            if (!isCheckingUpdate) {
              isCheckingUpdate = true
              scope.launch {
                try {
                  clientVersionRepository.refreshPolicy()
                  state.dataHolder.retryUserDataSync()
                } finally {
                  isCheckingUpdate = false
                }
              }
            }
          },
        )
      }
    }
    DemoAttributionFooterBar()
  }
}

/**
 * Whether the sync error banner may show. Download survey shows its own offline banner, which
 * already explains why surveys can't sync, so the sync error banner stays hidden there while the
 * device is offline.
 */
internal fun showsUserDataSyncErrorBanner(isOnline: Boolean, onDownloadSurvey: Boolean): Boolean =
  isOnline || !onDownloadSurvey

/** Test tag of the error/offline notice shown on [SignInScreen]. */
internal const val SIGN_IN_NOTICE_TAG = "signInNotice"

/**
 * The first screen of the app: Sign in with Google, under the [languageSelector] so the user can
 * pick the app's language before signing in. The button is disabled while [isSigningIn], so a slow
 * sign-in can't be started twice; while offline ([isOnline] is `false`), an offline sign-in message
 * is shown, and otherwise [notice] shows why the last attempt failed.
 */
@Composable
fun SignInScreen(
  onSignIn: () -> Unit,
  /** Language selector shown above the sign-in button. */
  languageSelector: @Composable () -> Unit = {},
  isSigningIn: Boolean = false,
  isOnline: Boolean = true,
  notice: String? = null,
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val brandFont = LocalAppBrandFontFamily.current

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(surfaceColor)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Top Brand Header
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))
      CloudAcaciaLogo(modifier = Modifier.size(68.dp))
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = "OPEN FORIS",
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.4.sp,
            ),
        )
        Text(
          text = "Collect One",
          style =
            MaterialTheme.typography.headlineMedium.copy(
              fontFamily = brandFont,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.6.sp,
            ),
        )
      }
      Text(
        text = stringResource(Res.string.mobile_sign_in_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Center Field Survey Illustration Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Stylized naturalistic mission & field survey illustration
        MissionGraphic(modifier = Modifier.fillMaxWidth().height(152.dp))

        Text(
          text = stringResource(Res.string.mobile_sign_in_prompt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Language Selector matching ground-android's SettingsSelectItem + Bottom Sign-In Actions
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      languageSelector()

      val displayNotice =
        if (!isOnline) {
          stringResource(Res.string.mobile_sign_in_offline_error)
        } else {
          notice?.takeIf { it.isNotBlank() }
        }
      displayNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier =
            Modifier.testTag(SIGN_IN_NOTICE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
        )
      }

      OutlinedButton(
        onClick = onSignIn,
        enabled = !isSigningIn,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors =
          ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (isSigningIn) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )
          } else {
            GoogleLogoIcon(size = 20.dp)
          }
          Text(
            text =
              if (isSigningIn) stringResource(Res.string.mobile_sign_in_signing_in)
              else stringResource(UiRes.string.sign_in_with_google),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Text(
        text = stringResource(Res.string.mobile_sign_in_google_accounts_only),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
