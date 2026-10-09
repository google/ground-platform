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
package org.groundplatform.v2.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.ui.onboarding.MobileScreenHost

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val token = getString(R.string.mapbox_access_token).trim()
    if (token.isNotEmpty()) {
      System.setProperty("ground.mapbox.accessToken", token)
    }
    enableEdgeToEdge()
    setContent { MainActivityContent() }
  }
}

/**
 * Android launcher entry point hosting the full-screen Ground 2.0 mobile prototype UI
 * ([MobileScreenHost]).
 */
@Composable
fun MainActivityContent(state: PrototypeAppState = remember { PrototypeAppState() }) {
  val dataCollectionUiState = state.dataCollectionUiState
  val canHandleBack =
    dataCollectionUiState.isDataCollectionFormOpen ||
      state.activeDrawerSubView != MainDrawerSubView.NONE ||
      state.isDrawerOpen ||
      state.selectedSubmissionId != null ||
      state.selectedEntityId != null ||
      state.currentScreen == PrototypeScreen.DOWNLOAD_SURVEY ||
      state.currentScreen == PrototypeScreen.TERMS_OF_SERVICE

  BackHandler(enabled = canHandleBack) {
    when {
      dataCollectionUiState.isDataCollectionFormOpen -> state.closeActiveFormRunner()
      state.activeDrawerSubView != MainDrawerSubView.NONE -> state.closeDrawerSubView()
      state.isDrawerOpen -> state.updateDrawerOpen(false)
      state.selectedSubmissionId != null -> state.selectSubmissionDetail(null)
      state.selectedEntityId != null -> state.selectEntity(null)
      state.currentScreen == PrototypeScreen.DOWNLOAD_SURVEY ->
        state.navigateBackFromDownloadSurvey()
      state.currentScreen == PrototypeScreen.TERMS_OF_SERVICE -> state.declineTermsOfService()
    }
  }

  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { MobileScreenHost(state) }
    }
  }
}
