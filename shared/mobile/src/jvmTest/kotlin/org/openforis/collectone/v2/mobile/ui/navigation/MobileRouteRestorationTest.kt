/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.mobileSampleDataHolder
import org.openforis.collectone.v2.mobile.ui.MobileAppState

/**
 * The back stack survives Android process death: the `NavHost` saves it with the rest of the UI
 * state, and the session rebuilt after the restart (which starts at Sign in, as the Android app
 * does) adopts it.
 */
@OptIn(ExperimentalTestApi::class)
class MobileRouteRestorationTest {
  private val dataHolder = mobileSampleDataHolder()
  private val sessions = mutableListOf<MobileAppState>()

  /** A process of the app, with the UI state it restored from its last process ([registry]). */
  private class AppProcess(saved: Map<String, List<Any?>>?) {
    val registry = SaveableStateRegistry(saved) { true }
  }

  @Test
  fun processDeath_restoresTheCurrentRouteAndTheBackStackUnderIt() = runComposeUiTest {
    val dispatcherOwner =
      object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
      }
    var process by mutableStateOf<AppProcess?>(AppProcess(saved = null))
    var initialRoute: MobileRoute = MobileRoute.MainSurvey
    setContent {
      process?.let { process ->
        CompositionLocalProvider(
          LocalSaveableStateRegistry provides process.registry,
          LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        ) {
          MobileAppSession(dataHolder, initialRoute) { state ->
            if (sessions.lastOrNull() !== state) sessions.add(state)
            MobileApp(state)
          }
        }
      }
    }
    runOnIdle { sessions.last().drawerOpenSettings() }
    waitForIdle()

    // The Android app starts over at Sign in, restoring the UI state its last process saved.
    initialRoute = MobileRoute.SignIn
    val saved = runOnIdle { checkNotNull(process).registry.performSave() }
    runOnIdle { process = null }
    waitForIdle()
    runOnIdle { process = AppProcess(saved) }
    waitForIdle()

    val restored = sessions.last()
    assertEquals(2, sessions.size, "the session is rebuilt")
    assertEquals(MobileRoute.Settings, restored.currentRoute)
    assertEquals(listOf(MobileRoute.MainSurvey, MobileRoute.Settings), restored.navigator.backStack)
  }
}
