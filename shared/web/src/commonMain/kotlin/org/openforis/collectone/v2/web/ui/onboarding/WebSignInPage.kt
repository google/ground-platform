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
package org.openforis.collectone.v2.web.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.CloudAcaciaLogo
import org.openforis.collectone.v2.app.ui.common.GoogleLogoIcon
import org.openforis.collectone.v2.app.ui.common.MissionGraphic
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingActions
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.app.ui.session.DevSignInDialog
import org.openforis.collectone.v2.core.forms.ui.LocalAppBrandFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_sign_in_description
import org.openforis.collectone.v2.web.resources.web_sign_in_google_accounts_only
import org.openforis.collectone.v2.web.resources.web_sign_in_tagline
import org.openforis.collectone.v2.web.resources.web_sign_in_with_google
import org.openforis.collectone.v2.web.resources.web_signing_in
import org.openforis.collectone.v2.web.ui.common.WebLanguageSelector

/**
 * Sign-in gate for the web app, shown in place of any web page while the user is signed out.
 * Signing in returns to the page they were opening.
 *
 * The gate shows the sign-in card from [uiState], with [notice] (why the user was signed out, if
 * anything) and the development sign-in dialog.
 *
 * @param header the page header; it receives the page's header actions (the language selector).
 */
@Composable
internal fun WebSignInPage(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
  languageCode: String,
  notice: String?,
  onSignIn: () -> Unit,
  onLanguageChange: (String) -> Unit,
  header: @Composable (headerActions: @Composable RowScope.() -> Unit) -> Unit,
) {
  val brandFont = LocalAppBrandFontFamily.current
  uiState.devSignIn?.let { dialog ->
    DevSignInDialog(
      state = dialog,
      onSelectRole = actions::selectSimulatedRole,
      onConfirm = actions::confirmDevSignIn,
      onDismiss = actions::dismissDevSignIn,
    )
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      header {
        WebLanguageSelector(currentLanguageCode = languageCode, onLanguageChange = onLanguageChange)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ElevatedCard(
          modifier = Modifier.width(460.dp),
          colors =
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
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
              text = stringResource(Res.string.web_sign_in_tagline),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            MissionGraphic(modifier = Modifier.fillMaxWidth().height(164.dp))
            Text(
              text = stringResource(Res.string.web_sign_in_description),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            notice
              ?.takeIf { it.isNotBlank() }
              ?.let { notice ->
                Text(
                  text = notice,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
                )
              }
            OutlinedButton(
              onClick = onSignIn,
              enabled = !uiState.isSigningIn,
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
                if (uiState.isSigningIn) {
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
                    stringResource(
                      if (uiState.isSigningIn) Res.string.web_signing_in
                      else Res.string.web_sign_in_with_google
                    ),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
            Text(
              text = stringResource(Res.string.web_sign_in_google_accounts_only),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
  }
}
