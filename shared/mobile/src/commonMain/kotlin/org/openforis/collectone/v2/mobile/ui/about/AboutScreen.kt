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
package org.openforis.collectone.v2.mobile.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res as AppRes
import org.openforis.collectone.v2.app.resources.app_about
import org.openforis.collectone.v2.app.ui.common.AboutContent
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * The About screen ([MobileRoute.About]), opened from the navigation drawer: the product name and
 * app version, what Collect One is for and its mission, and the link to the project's contributors
 * ([AboutContent], shared with the web console), in a scrolling column under a top bar whose Back
 * arrow calls [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(AppRes.string.app_about)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
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
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      AboutContent()
    }
  }
}
