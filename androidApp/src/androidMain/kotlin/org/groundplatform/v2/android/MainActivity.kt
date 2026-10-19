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
import java.io.File
import org.groundplatform.v2.app.client.storage.SYNC_STATE_FILE_PROPERTY
import org.groundplatform.v2.app.di.AppDataHolder
import org.groundplatform.v2.app.di.requireUserDataServiceUrl
import org.groundplatform.v2.app.ui.common.RequireUserDataService
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.mobile.ui.onboarding.MobileScreenHost

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val token = getString(R.string.mapbox_access_token).trim()
    if (token.isNotEmpty()) {
      System.setProperty("ground.mapbox.accessToken", token)
    }
    // Read by platformUserDataServiceUrl(); see USER_DATA_SERVICE_URL in build.gradle.kts.
    val userDataServiceUrl = getString(R.string.user_data_service_url).trim()
    if (userDataServiceUrl.isNotEmpty()) {
      System.setProperty("groundplatform.userDataServiceUrl", userDataServiceUrl)
    }
    // Read by platformSyncStateFile(): offline edits and synced data survive restarts.
    System.setProperty(SYNC_STATE_FILE_PROPERTY, File(filesDir, "sync_state.binpb").absolutePath)
    enableEdgeToEdge()
    setContent {
      RequireUserDataService { serviceUrl ->
        MainActivityContent(
          state =
            remember {
              MobileAppState(
                dataHolder =
                  AppDataHolder(userDataServiceUrl = serviceUrl, syncDownloadedSurveysOnly = true)
              )
            }
        )
      }
    }
  }
}

/**
 * Android launcher entry point hosting the full-screen Ground 2.0 mobile app UI
 * ([MobileScreenHost]).
 */
@Composable
fun MainActivityContent(
  state: MobileAppState = remember {
    MobileAppState(
      dataHolder =
        AppDataHolder(
          userDataServiceUrl = requireUserDataServiceUrl(),
          syncDownloadedSurveysOnly = true,
        )
    )
  }
) {
  BackHandler(enabled = state.canHandleBack) { state.handleBack() }

  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { MobileScreenHost(state) }
    }
  }
}
