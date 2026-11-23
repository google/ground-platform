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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.VerticalScrollBar
import org.openforis.collectone.v2.app.ui.common.rememberHasScrolledToBottom
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingActions
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.app.ui.onboarding.TermsOfServiceParagraphs
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_terms_of_service_title
import org.openforis.collectone.v2.web.resources.web_tos_agree_checkbox
import org.openforis.collectone.v2.web.resources.web_tos_continue
import org.openforis.collectone.v2.web.resources.web_tos_decline
import org.openforis.collectone.v2.web.resources.web_tos_intro
import org.openforis.collectone.v2.web.resources.web_tos_signed_in_as
import org.openforis.collectone.v2.web.ui.common.WebLanguageSelector

private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * Terms of Service gate for the web console, shown in place of any web page after every sign-in
 * until the signed-in user accepts the terms. The agreement checkbox starts unchecked and disabled
 * until the user scrolls to the bottom of the terms, and Continue stays disabled until the checkbox
 * is ticked. Accepting opens the page they were opening; declining signs them out and returns to
 * the sign-in page.
 *
 * The acceptance lasts until the user signs out (see `AuthRepository.acceptTerms`), so reloading
 * the page doesn't ask again, but signing in again (as the same or another account) does.
 *
 * @param header the page header; it receives the page's header actions (the language selector, so
 *   the terms can be read in another language before accepting them).
 */
@Composable
internal fun WebTermsOfServicePage(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
  languageCode: String,
  onLanguageChange: (String) -> Unit,
  header: @Composable (headerActions: @Composable RowScope.() -> Unit) -> Unit,
) {
  val termsScrollState =
    rememberSaveable(uiState.profile.email, saver = ScrollState.Saver) { ScrollState(0) }
  val hasScrolledToBottom =
    rememberHasScrolledToBottom(termsScrollState, resetKey = uiState.profile.email)

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      header {
        WebLanguageSelector(currentLanguageCode = languageCode, onLanguageChange = onLanguageChange)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        ElevatedCard(
          modifier = Modifier.width(560.dp),
          colors =
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = stringResource(Res.string.web_terms_of_service_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(Res.string.web_tos_signed_in_as, uiState.profile.email),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = stringResource(Res.string.web_tos_intro),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            TermsOfServiceText(scrollState = termsScrollState)
            AgreeCheckbox(
              checked = uiState.termsCheckboxChecked,
              enabled = hasScrolledToBottom,
              onCheckedChange = actions::setTermsChecked,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
              OutlinedButton(onClick = actions::declineTermsOfService) {
                Text(stringResource(Res.string.web_tos_decline))
              }
              Button(
                onClick = actions::acceptTermsOfService,
                enabled = hasScrolledToBottom && uiState.termsCheckboxChecked,
              ) {
                Text(stringResource(Res.string.web_tos_continue))
              }
            }
          }
        }
      }
    }
  }
}

/** The terms themselves, scrolling inside their card when they're longer than it. */
@Composable
private fun TermsOfServiceText(scrollState: ScrollState) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 22.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TermsOfServiceParagraphs.forEach { paragraph ->
          Text(
            text = paragraph,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Box(modifier = Modifier.matchParentSize()) {
        VerticalScrollBar(
          scrollState = scrollState,
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .fillMaxHeight()
              .padding(vertical = 8.dp, horizontal = 2.dp),
        )
      }
    }
  }
}

/** The "I have read and agree" checkbox, enabled after scrolling to the bottom of the terms. */
@Composable
private fun AgreeCheckbox(
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .toggleable(
          value = checked,
          enabled = enabled,
          role = Role.Checkbox,
          onValueChange = onCheckedChange,
        )
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    Text(
      text = stringResource(Res.string.web_tos_agree_checkbox),
      style = MaterialTheme.typography.bodyMedium,
      color =
        if (enabled) {
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
        },
    )
  }
}
