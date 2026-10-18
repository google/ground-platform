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
package org.groundplatform.v2.web.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import org.groundplatform.v2.app.ui.common.CloudAcaciaLogo
import org.groundplatform.v2.app.ui.common.DevSignInDialog
import org.groundplatform.v2.app.ui.common.GoogleLogoIcon
import org.groundplatform.v2.app.ui.common.GroundMissionGraphic
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.common.WebAppHeader
import org.groundplatform.v2.web.ui.common.WebHeaderContext
import org.groundplatform.v2.web.ui.common.WebLanguageSelector

/**
 * Sign-in gate for the web app, shown in place of any web page while the user is signed out.
 * Signing in returns to the page they were opening.
 */
@Composable
internal fun WebSignInPage(state: WebAppState, onSignIn: () -> Unit) {
  val brandFont = LocalGroundBrandFontFamily.current
  val onboardingUiState by state.onboarding.uiState.collectAsState()
  val settingsUiState by state.settings.uiState.collectAsState()
  onboardingUiState.devSignIn?.let { dialog ->
    DevSignInDialog(
      state = dialog,
      onSelectRole = state.onboarding::selectSimulatedRole,
      onConfirm = state.onboarding::confirmDevSignIn,
      onDismiss = state.onboarding::dismissDevSignIn,
    )
  }

  Column(modifier = Modifier.fillMaxSize()) {
    WebAppHeader(
      state = state,
      onSignOut = null,
      showLanguageSelector = false,
      context = { WebHeaderContext(title = "Sign in") },
      actions = {
        WebLanguageSelector(
          currentLanguageCode = settingsUiState.languageCode,
          onLanguageChange = state.settings::updateLanguage,
        )
      },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      ElevatedCard(modifier = Modifier.width(460.dp)) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          CloudAcaciaLogo(modifier = Modifier.size(56.dp))
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
              "Map ecosystems, design field surveys, and verify ground observations with your organization.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          GroundMissionGraphic(modifier = Modifier.fillMaxWidth().height(164.dp))
          Text(
            text = "Sign in to see the surveys and organizations shared with your account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          OutlinedButton(
            onClick = onSignIn,
            enabled = !onboardingUiState.isSigningIn,
            modifier = Modifier.fillMaxWidth().height(48.dp),
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
              if (onboardingUiState.isSigningIn) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.primary,
                )
              } else {
                GoogleLogoIcon(size = 20.dp)
              }
              Text(
                text = if (onboardingUiState.isSigningIn) "Signing in…" else "Sign in with Google",
                style = MaterialTheme.typography.titleSmall,
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
  }
}
