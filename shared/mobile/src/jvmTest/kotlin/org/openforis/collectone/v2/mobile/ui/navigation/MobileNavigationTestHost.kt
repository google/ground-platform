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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.mobileSampleDataHolder
import org.openforis.collectone.v2.mobile.ui.MobileAppState

/**
 * Hosts the mobile app's navigation graph in a Compose UI test, with a stand-in for the system Back
 * gesture: [pressBack] sends it through the app's back handlers and its `NavHost`, and
 * [systemBackCount] counts the ones nothing in the app handled (which would close the app).
 */
@OptIn(ExperimentalTestApi::class)
internal class MobileNavigationTestHost(private val test: ComposeUiTest) {
  private val backInput = DirectNavigationEventInput()
  private val dispatcherOwner =
    object : NavigationEventDispatcherOwner {
      override val navigationEventDispatcher =
        NavigationEventDispatcher(onBackCompletedFallback = { systemBackCount++ }).apply {
          addInput(backInput)
        }
    }

  /** System Back gestures the app left to the system. */
  var systemBackCount = 0
    private set

  /** The controller of the app's `NavHost`, once [show] has composed it. */
  lateinit var navController: NavHostController
    private set

  /**
   * Composes the mobile app as hosts do ([MobileAppSession] over [dataHolder]'s Koin graph,
   * starting at [initialRoute]) and returns its session.
   */
  fun show(
    initialRoute: MobileRoute = MobileRoute.MainSurvey,
    dataHolder: AppDataHolder = mobileSampleDataHolder(),
  ): MobileAppState {
    var session: MobileAppState? = null
    test.setContent {
      CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
        navController = rememberNavController()
        MobileAppSession(dataHolder, initialRoute) { state ->
          session = state
          MobileApp(state, navController = navController)
        }
      }
    }
    test.waitForIdle()
    return checkNotNull(session)
  }

  /** Sends a system Back gesture and waits for the app to settle. */
  fun pressBack() {
    test.runOnIdle { backInput.backCompleted() }
    test.waitForIdle()
  }
}

/** Runs [block] with a [MobileNavigationTestHost]. */
@OptIn(ExperimentalTestApi::class)
internal fun runMobileNavigationTest(block: MobileNavigationTestHost.(ComposeUiTest) -> Unit) =
  runComposeUiTest {
    MobileNavigationTestHost(this).block(this)
  }
