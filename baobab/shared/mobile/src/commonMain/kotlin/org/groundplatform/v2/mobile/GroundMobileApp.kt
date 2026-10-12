/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.mobile.ui.onboarding.MobileScreenHost

/**
 * Root of the Ground 2.0 mobile app, shared by every mobile host (`androidApp`, `iosApp`, and the
 * Wasm mobile build): `Sign In` → `Terms of Service` → `Download survey` → `Main Survey UI`.
 *
 * Hosts wire the system Back gesture to [MobileAppState.canHandleBack] and
 * [MobileAppState.handleBack]. The root surface turns transparent while the basemap shows, because
 * on web the Mapbox basemap renders behind the Compose canvas.
 */
@Composable
fun GroundMobileApp(state: MobileAppState = remember { MobileAppState() }) {
  GroundTheme(darkTheme = state.isDarkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = if (state.isMapShowing) Color.Transparent else MaterialTheme.colorScheme.surface,
    ) {
      Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { MobileScreenHost(state) }
    }
  }
}
