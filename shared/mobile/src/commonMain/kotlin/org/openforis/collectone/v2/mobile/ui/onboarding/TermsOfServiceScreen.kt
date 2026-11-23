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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.VerticalScrollBar
import org.openforis.collectone.v2.app.ui.common.rememberHasScrolledToBottom
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingActions
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.app.ui.onboarding.TermsOfServiceParagraphs
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.resources.mobile_terms_of_service_title
import org.openforis.collectone.v2.mobile.resources.mobile_tos_agree_checkbox
import org.openforis.collectone.v2.mobile.resources.mobile_tos_continue
import org.openforis.collectone.v2.mobile.resources.mobile_tos_decline
import org.openforis.collectone.v2.mobile.resources.mobile_tos_intro
import org.openforis.collectone.v2.mobile.resources.mobile_tos_signed_in_as

private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * Terms of Service, shown after the first sign-in. The agreement checkbox starts unchecked and
 * disabled until the user scrolls to the bottom of the terms, and Continue stays disabled until the
 * checkbox is ticked; declining (or closing) signs the user out and returns to Sign in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val declineLabel = stringResource(Res.string.mobile_tos_decline)
  val continueLabel = stringResource(Res.string.mobile_tos_continue)
  val agreeCheckboxLabel = stringResource(Res.string.mobile_tos_agree_checkbox)
  val termsScrollState =
    rememberSaveable(uiState.profile.email, saver = ScrollState.Saver) { ScrollState(0) }
  val hasScrolledToBottom =
    rememberHasScrolledToBottom(termsScrollState, resetKey = uiState.profile.email)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(Res.string.mobile_terms_of_service_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = stringResource(Res.string.mobile_tos_signed_in_as, uiState.profile.email),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { actions.declineTermsOfService() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(Res.string.mobile_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    bottomBar = {
      TermsOfServiceBottomBar(
        declineLabel = declineLabel,
        continueLabel = continueLabel,
        continueEnabled = hasScrolledToBottom && uiState.termsCheckboxChecked,
        onDecline = actions::declineTermsOfService,
        onContinue = actions::acceptTermsOfService,
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = stringResource(Res.string.mobile_tos_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = onSurfaceColor,
        fontWeight = FontWeight.SemiBold,
      )

      TermsOfServiceCard(
        scrollState = termsScrollState,
        modifier = Modifier.fillMaxWidth().weight(1f),
      )

      TermsOfServiceAgreeCheckbox(
        checked = uiState.termsCheckboxChecked,
        enabled = hasScrolledToBottom,
        label = agreeCheckboxLabel,
        onCheckedChange = actions::setTermsChecked,
      )
    }
  }
}

@Composable
private fun TermsOfServiceBottomBar(
  declineLabel: String,
  continueLabel: String,
  continueEnabled: Boolean,
  onDecline: () -> Unit,
  onContinue: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 3.dp,
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics {
          isTraversalGroup = true
          traversalIndex = 2f
        },
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedButton(
        onClick = onDecline,
        modifier =
          Modifier.weight(0.42f).semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = declineLabel
            traversalIndex = 0f
          },
        shape = MaterialTheme.shapes.medium,
      ) {
        Text(declineLabel)
      }
      Button(
        onClick = onContinue,
        enabled = continueEnabled,
        modifier =
          Modifier.weight(0.58f).semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = continueLabel
            traversalIndex = 1f
          },
        shape = MaterialTheme.shapes.medium,
      ) {
        Text(
          text = continueLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun TermsOfServiceCard(scrollState: ScrollState, modifier: Modifier = Modifier) {
  OutlinedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 22.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TermsOfServiceParagraphs.forEach { paragraph ->
          Text(
            text = paragraph,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
          )
        }
      }
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

@Composable
private fun TermsOfServiceAgreeCheckbox(
  checked: Boolean,
  enabled: Boolean,
  label: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .toggleable(
          value = checked,
          enabled = enabled,
          role = Role.Checkbox,
          onValueChange = onCheckedChange,
        )
        .semantics(mergeDescendants = true) {
          contentDescription = label
          traversalIndex = 1f
        }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = null,
      enabled = enabled,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = if (enabled) onSurfaceColor else onSurfaceColor.copy(alpha = DISABLED_CONTENT_ALPHA),
    )
  }
}
