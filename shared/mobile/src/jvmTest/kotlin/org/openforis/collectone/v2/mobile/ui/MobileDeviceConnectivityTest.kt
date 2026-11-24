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
package org.openforis.collectone.v2.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.testing.awaitItemMatching
import org.openforis.collectone.v2.app.testing.expectNoItemsWithin
import org.openforis.collectone.v2.mobile.services.ConnectivityMonitor
import org.openforis.collectone.v2.mobile.services.MonitorDeviceConnectivity
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** Real device connectivity reported by the host's [ConnectivityMonitor]. */
@OptIn(ExperimentalTestApi::class)
class MobileDeviceConnectivityTest {
  private val dataHolder = sampleDataHolder()
  private val state = MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = dataHolder)

  /** Awaits the next notice, whose text loads asynchronously from the string resources. */
  private suspend fun ReceiveTurbine<String?>.awaitNotice(): String? = awaitItemMatching {
    it != null
  }

  @Test
  fun goingOffline_marksTheAppOffline() {
    state.updateDeviceConnectivity(isOnline = false)

    assertFalse(dataHolder.connectivityRepository.isOnline())
    assertFalse(state.viewModels.surveyMap.uiState.value.isOnline)
  }

  @Test
  fun goingOffline_showsTheOfflineNotice() = runTest {
    state.notices.show(null)

    state.notices.notice.test {
      state.updateDeviceConnectivity(isOnline = false)

      val notice = awaitNotice()
      assertTrue(notice?.startsWith("Device offline: Places search disabled.") == true, "$notice")
    }
  }

  @Test
  fun comingBackOnline_showsTheOnlineNotice() = runTest {
    state.notices.notice.test {
      state.updateDeviceConnectivity(isOnline = false)
      awaitNotice()
      state.notices.show(null)

      state.updateDeviceConnectivity(isOnline = true)

      assertTrue(state.viewModels.surveyMap.uiState.value.isOnline)
      assertEquals("Device online: Places API search enabled.", awaitNotice())
    }
  }

  @Test
  fun goingOfflineOnDownloadSurvey_showsNoNotice() = runTest {
    val onDownload =
      MobileAppState(initialRoute = MobileRoute.DownloadSurvey, dataHolder = sampleDataHolder())
    onDownload.notices.clear()

    onDownload.notices.notice.test {
      assertNull(awaitItem())

      onDownload.updateDeviceConnectivity(isOnline = false)

      expectNoItemsWithin(NOTICE_TEXT_LOAD_WINDOW)
    }
  }

  @Test
  fun comingBackOnlineOnDownloadSurvey_showsNoNotice() = runTest {
    val onDownload =
      MobileAppState(initialRoute = MobileRoute.DownloadSurvey, dataHolder = sampleDataHolder())
    onDownload.updateDeviceConnectivity(isOnline = false)
    onDownload.notices.clear()

    onDownload.notices.notice.test {
      assertNull(awaitItem())

      onDownload.updateDeviceConnectivity(isOnline = true)

      expectNoItemsWithin(NOTICE_TEXT_LOAD_WINDOW)
    }
  }

  @Test
  fun goingOfflineOnSignIn_showsNoNotice() = runTest {
    val onSignIn =
      MobileAppState(initialRoute = MobileRoute.SignIn, dataHolder = sampleDataHolder())
    onSignIn.notices.clear()

    onSignIn.notices.notice.test {
      assertNull(awaitItem())

      onSignIn.updateDeviceConnectivity(isOnline = false)

      expectNoItemsWithin(NOTICE_TEXT_LOAD_WINDOW)
    }
  }

  @Test
  fun comingBackOnlineOnSignIn_clearsNoticeAndShowsNoOnlineNotice() = runTest {
    val onSignIn =
      MobileAppState(initialRoute = MobileRoute.SignIn, dataHolder = sampleDataHolder())
    onSignIn.updateDeviceConnectivity(isOnline = false)
    onSignIn.notices.show("Cannot sign in while offline. Connect to the internet and try again.")

    onSignIn.notices.notice.test {
      assertEquals(
        "Cannot sign in while offline. Connect to the internet and try again.",
        awaitItem(),
      )

      onSignIn.updateDeviceConnectivity(isOnline = true)

      assertNull(awaitItem())
      expectNoItemsWithin(NOTICE_TEXT_LOAD_WINDOW)
    }
  }

  @Test
  fun reportOfUnchangedConnectivity_isIgnored() {
    state.notices.show("Earlier notice")

    state.updateDeviceConnectivity(isOnline = true)

    assertEquals("Earlier notice", state.notices.notice.value)
  }

  @Test
  fun monitorDeviceConnectivity_feedsReportsWhileShownAndStopsWhenRemoved() =
    runDesktopComposeUiTest {
      var report: ((Boolean) -> Unit)? = null
      var isStopped = false
      val monitor = ConnectivityMonitor { onChange ->
        report = onChange
        onChange(false)
        return@ConnectivityMonitor { isStopped = true }
      }
      var isMonitoring by mutableStateOf(true)
      setContent { if (isMonitoring) MonitorDeviceConnectivity(state, monitor) }
      waitForIdle()

      assertFalse(dataHolder.connectivityRepository.isOnline())
      report?.invoke(true)
      assertTrue(dataHolder.connectivityRepository.isOnline())

      isMonitoring = false
      waitForIdle()
      assertTrue(isStopped)
    }

  private companion object {
    /** Long enough for a notice's text to load from the string resources, had one been shown. */
    val NOTICE_TEXT_LOAD_WINDOW = 300.milliseconds
  }
}
