/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.mobile.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.ui.common.CloudAcaciaLogo
import org.groundplatform.v2.app.ui.common.GoogleLogoIcon
import org.groundplatform.v2.app.ui.common.GroundMissionGraphic
import org.groundplatform.v2.app.ui.datacollection.DataCollectionFormScreen
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily
import org.groundplatform.v2.core.forms.ui.resources.Res
import org.groundplatform.v2.core.forms.ui.resources.sign_in_with_google
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.mobile.ui.navigation.MainSurveyScreen
import org.groundplatform.v2.mobile.ui.settings.SignInLanguageSelector
import org.jetbrains.compose.resources.stringResource

/**
 * Host composable that switches between the 4 Ground mobile app screens:
 * 1. Sign In Screen (Sign in with Google only)
 * 2. Terms of Service Screen
 * 3. Download Survey Screen (with search by name or location, map thumbnail, and downloaded
 *    indicator)
 * 4. Main Survey UI (Map & List Views, Layers toggle, simplestyle-spec Marker Progression ○ → ◐ →
 *    ✓, and Navigation Drawer)
 */
@Composable
fun MobileScreenHost(state: MobileAppState) {
  val dataCollectionUiState = state.dataCollectionUiState
  if (
    dataCollectionUiState.isDataCollectionFormOpen &&
      dataCollectionUiState.activeFormWizardController != null
  ) {
    DataCollectionFormScreen(state)
    return
  }
  val onboarding = state.onboarding
  val onboardingUiState by onboarding.uiState.collectAsState()
  val settingsUiState by state.settings.uiState.collectAsState()
  when (state.currentScreen) {
    AppScreen.SIGN_IN ->
      SignInScreen(
        onSignIn = onboarding::signInWithGoogle,
        languageSelector = {
          SignInLanguageSelector(
            languageCode = settingsUiState.languageCode,
            onLanguageChange = state.settings::updateLanguage,
          )
        },
      )
    AppScreen.TERMS_OF_SERVICE ->
      TermsOfServiceScreen(
        uiState = onboardingUiState,
        actions = onboarding,
      )
    AppScreen.DOWNLOAD_SURVEY ->
      DownloadSurveyScreen(
        uiState = onboardingUiState,
        actions = onboarding,
        notice = state.activeSurveyNotice,
        isDarkTheme = state.isDarkTheme,
      )
    AppScreen.MAIN_SURVEY -> MainSurveyScreen(state)
  }
}

/**
 * 1. Sign In screen (Sign in with Google + Language selector matching Ground SettingsSelectItem).
 */
@Composable
fun SignInScreen(
  onSignIn: () -> Unit,
  /** Language selector shown above the sign-in button. */
  languageSelector: @Composable () -> Unit = {},
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val brandFont = LocalGroundBrandFontFamily.current

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
        text =
          "Collect field observations, map boundaries, and synchronize survey records offline.",
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
        GroundMissionGraphic(modifier = Modifier.fillMaxWidth().height(152.dp))

        Text(
          text = "Sign in to access surveys shared with your account.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Language Selector matching Ground SettingsSelectItem + Bottom Sign-In Actions
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      languageSelector()

      OutlinedButton(
        onClick = onSignIn,
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
          GoogleLogoIcon(size = 20.dp)
          Text(
            text = stringResource(Res.string.sign_in_with_google),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Text(
        text = "Authentication is currently limited to Google Accounts.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
