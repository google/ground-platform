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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 2. Terms of Service screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(state: PrototypeAppState) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Terms of Service",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Signed in as ${state.signedInUserEmail}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { state.declineTermsOfService() }) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        actions = { PrototypeDebugToolsButton(state = state) },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    bottomBar = {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = { state.declineTermsOfService() },
            modifier = Modifier.weight(0.42f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Text("Decline")
          }
          Button(
            onClick = { state.acceptTermsOfService() },
            enabled = state.termsCheckboxChecked,
            modifier = Modifier.weight(0.58f),
            shape = MaterialTheme.shapes.medium,
          ) {
            Text(
              text = "Agree & Continue",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { innerPadding ->
    // Scrollable Terms of Service Body
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Please review and accept the Ground Terms of Service before downloading surveys.",
        style = MaterialTheme.typography.bodyMedium,
        color = onSurfaceColor,
        fontWeight = FontWeight.SemiBold,
      )

      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          TermsSectionItem(
            number = "1",
            title = "Survey Data Collection & Sharing",
            body =
              "Field observations, GPS coordinates, geometries, and photos collected in Ground are shared with the survey organizers who granted you access to each survey.",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          TermsSectionItem(
            number = "2",
            title = "Offline Storage & Synchronization",
            body =
              "Downloaded survey definitions and map tiles are cached locally on your device for offline field work and automatically synchronized when connectivity is restored.",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          TermsSectionItem(
            number = "3",
            title = "Location & Sensor Permissions",
            body =
              "Ground uses device location services when capturing points, polygons, or transects during active data collection tasks.",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          TermsSectionItem(
            number = "4",
            title = "Privacy & Responsible Use",
            body =
              "Do not record sensitive personal data unless explicitly authorized and consented to under your organization's survey governance protocol.",
          )
        }
      }

      // Checkbox row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { state.setTermsChecked(!state.termsCheckboxChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = state.termsCheckboxChecked,
          onCheckedChange = { state.setTermsChecked(it) },
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "I have read and agree to the Ground Terms of Service.",
          style = MaterialTheme.typography.bodySmall,
          color = onSurfaceColor,
        )
      }
    }
  }
}

@Composable
private fun TermsSectionItem(number: String, title: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "$number. $title",
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      lineHeight = 18.sp,
    )
  }
}

/** Backward-compatible alias for [TermsOfServiceScreen]. */
@Composable
fun GroundTermsOfServiceScreen(state: PrototypeAppState) {
  TermsOfServiceScreen(state)
}
