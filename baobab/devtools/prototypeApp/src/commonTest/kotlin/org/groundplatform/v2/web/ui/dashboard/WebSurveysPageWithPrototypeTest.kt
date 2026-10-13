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
package org.groundplatform.v2.web.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.web.ui.WebAppState

/** [WebSurveysPageTest] cases that run on the combined prototype state. */
class WebSurveysPageWithPrototypeTest {
  private val state = WebAppState()
  private val user = state.signedInUserEmail
  private val kfs = state.organization(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)!!
  private val mekong = state.organization(PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE)!!

  @Test
  fun openSurveyOnWeb_switchesActiveSurveyWithoutTouchingMobileFlow() {
    val prototype = PrototypeAppState()
    val testState = prototype.web
    val screenBefore = prototype.mobile.currentScreen
    val target = testState.surveys.first { it.id != testState.activeSurveyId }
    val wasDownloaded = target.isDownloaded
    testState.openSurveyOnWeb(target.id)
    assertEquals(target.id, testState.activeSurveyId)
    assertEquals(screenBefore, prototype.mobile.currentScreen)
    assertEquals(wasDownloaded, testState.surveys.first { it.id == target.id }.isDownloaded)
    assertTrue(testState.hasOpenableActiveSurvey)
    // Unknown IDs are ignored.
    testState.openSurveyOnWeb("survey-missing")
    assertEquals(target.id, testState.activeSurveyId)
  }
}
