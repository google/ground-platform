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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The back stack a [MobileNavigator] keeps until a `NavHost` attaches its controller. */
class MobileNavigatorTest {
  private val changes = mutableListOf<MobileRoute>()
  private val navigator = MobileNavigator(MobileRoute.MainSurvey, onRouteChanged = changes::add)

  @Test
  fun startsAtTheStartRoute() {
    assertEquals(MobileRoute.MainSurvey, navigator.currentRoute)
    assertEquals(listOf(MobileRoute.MainSurvey), navigator.backStack)
  }

  @Test
  fun navigate_pushesTheRoute() {
    navigator.navigate(MobileRoute.Settings)

    assertEquals(listOf(MobileRoute.MainSurvey, MobileRoute.Settings), navigator.backStack)
    assertEquals(listOf<MobileRoute>(MobileRoute.Settings), changes)
  }

  @Test
  fun navigate_toTheCurrentRoute_changesNothing() {
    navigator.navigate(MobileRoute.MainSurvey)

    assertEquals(listOf(MobileRoute.MainSurvey), navigator.backStack)
    assertTrue(changes.isEmpty())
  }

  @Test
  fun back_popsTheCurrentRoute() {
    navigator.navigate(MobileRoute.Uploads)

    assertTrue(navigator.back())

    assertEquals(MobileRoute.MainSurvey, navigator.currentRoute)
  }

  @Test
  fun back_atTheBottomOfTheBackStack_isLeftToTheSystem() {
    assertFalse(navigator.back())

    assertEquals(MobileRoute.MainSurvey, navigator.currentRoute)
  }

  @Test
  fun resetTo_clearsTheBackStack() {
    navigator.navigate(MobileRoute.DownloadedSurveys)

    navigator.resetTo(MobileRoute.SignIn)

    assertEquals(listOf(MobileRoute.SignIn), navigator.backStack)
  }

  @Test
  fun popBackTo_keepsTheRouteOnTop() {
    navigator.navigate(MobileRoute.DownloadedSurveys)
    navigator.navigate(MobileRoute.DownloadSurvey)

    assertTrue(navigator.popBackTo(MobileRoute.DownloadedSurveys))

    assertEquals(listOf(MobileRoute.MainSurvey, MobileRoute.DownloadedSurveys), navigator.backStack)
  }

  @Test
  fun popBackTo_aRouteNotOnTheBackStack_changesNothing() {
    navigator.navigate(MobileRoute.Settings)

    assertFalse(navigator.popBackTo(MobileRoute.Uploads))

    assertEquals(listOf(MobileRoute.MainSurvey, MobileRoute.Settings), navigator.backStack)
  }
}
