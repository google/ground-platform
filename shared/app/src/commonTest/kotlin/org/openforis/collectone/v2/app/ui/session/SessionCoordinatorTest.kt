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
package org.openforis.collectone.v2.app.ui.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.app.ui.navigation.DownloadSurveyEntryOrigin

/**
 * How [SessionCoordinator] routes the feature ViewModels' events to each other, the notice, and the
 * shell, including a coordinator rebuilt around the same ViewModels (as after an Android
 * configuration change).
 */
class SessionCoordinatorTest {
  private val holder = sampleDataHolder()
  private val viewModels = AppViewModels.create(holder)

  @Test
  fun closedCoordinator_stopsRoutingTheViewModelsEvents() {
    val notices = NoticeController()
    SessionCoordinator(holder, viewModels, notices, shell = object : SessionShell {}).close()

    viewModels.surveyMap.selectPlace(checkNotNull(viewModels.surveyMap.findPlaceById(KARIMA)))

    assertNull(notices.notice.value)
  }

  @Test
  fun rebuiltCoordinator_routesTheViewModelsEventsOnce() {
    val shellCalls = mutableListOf<String>()
    val shell =
      object : SessionShell {
        override fun onMapShown() {
          shellCalls += "map shown"
        }
      }
    SessionCoordinator(holder, viewModels, NoticeController(), shell).close()
    SessionCoordinator(holder, viewModels, NoticeController(), shell)

    viewModels.surveyMap.selectEntity(NYR_104)

    assertEquals(listOf("map shown"), shellCalls)
  }

  @Test
  fun aRestoredSelection_isHighlightedOnTheMap() {
    viewModels.dataCollection.onEntitySelected(NYR_104)

    SessionCoordinator(holder, viewModels, NoticeController(), shell = object : SessionShell {})

    assertEquals(NYR_104, viewModels.surveyMap.uiState.value.selectedEntityId)
  }

  @Test
  fun openingASurvey_activatesItAndTellsTheShell() {
    val shell = RecordingShell()
    SessionCoordinator(holder, viewModels, NoticeController(), shell)
    val other = holder.appData.value.surveys.first { it.id != holder.appData.value.activeSurveyId }

    viewModels.onboarding.openSurvey(other.id)

    assertEquals(other.id, holder.appData.value.activeSurveyId)
    assertEquals(listOf("survey activated ${other.id}", "survey opened"), shell.calls)
  }

  @Test
  fun openingASurvey_noticesWhatWasLoaded() {
    val notices = NoticeController()
    SessionCoordinator(holder, viewModels, notices, RecordingShell())
    val survey = holder.appData.value.surveys.first()

    viewModels.onboarding.openSurvey(survey.id)

    val notice = checkNotNull(notices.notice.value)
    assertTrue(notice.startsWith("Loaded survey \"${survey.title}\""), notice)
  }

  @Test
  fun signingOut_clearsTheNoticeAndAsksForSignIn() {
    val notices = NoticeController()
    val shell = RecordingShell()
    SessionCoordinator(holder, viewModels, notices, shell)
    notices.show("Stale notice")

    viewModels.onboarding.confirmSignOut()

    assertNull(notices.notice.value)
    assertEquals(listOf("signed out", "screen ${AppScreen.SIGN_IN}"), shell.calls)
  }

  @Test
  fun signingOutInAnotherSession_tellsThisSessionsShell() {
    val shell = RecordingShell()
    SessionCoordinator(holder, viewModels, NoticeController(), shell)
    val otherSession = AppViewModels.create(holder)
    otherSession.onboarding.signInWithGoogle()
    check(viewModels.onboarding.uiState.value.isSignedIn) {
      "Signing in is shared by both sessions"
    }

    otherSession.onboarding.confirmSignOut()

    assertTrue("signed out elsewhere" in shell.calls, shell.calls.toString())
    assertTrue("signed out" !in shell.calls, shell.calls.toString())
  }

  @Test
  fun backFromDownloadSurveyOpenedFromTheSurveyList_returnsToItWithoutANotice() {
    val notices = NoticeController()
    val shell = RecordingShell()
    SessionCoordinator(holder, viewModels, notices, shell)
    notices.show("Stale notice")
    viewModels.onboarding.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)

    viewModels.onboarding.navigateBackFromDownloadSurvey()

    assertNull(notices.notice.value)
    assertEquals(listOf("returned to survey list"), shell.calls)
  }

  @Test
  fun openingUploadsFromTheDashboard_tellsTheShell() {
    val shell = RecordingShell()
    SessionCoordinator(holder, viewModels, NoticeController(), shell)

    viewModels.dashboard.showUploads(filter = null)

    assertEquals(listOf("uploads opened"), shell.calls)
  }

  @Test
  fun aSurveyOpenedOnTheWeb_clearsTheSelectionsOfThePreviousSurvey() {
    val shell = RecordingShell()
    SessionCoordinator(holder, viewModels, NoticeController(), shell)
    viewModels.surveyMap.selectEntity(NYR_104)
    val other = holder.appData.value.surveys.first { it.id != holder.appData.value.activeSurveyId }

    viewModels.dashboard.openSurveyOnWeb(other.id)

    assertNull(viewModels.dataCollection.uiState.value.selectedEntityId)
    assertEquals("survey activated ${other.id}", shell.calls.last())
  }

  @Test
  fun reset_clearsTheNotice() {
    val notices = NoticeController()
    val coordinator = SessionCoordinator(holder, viewModels, notices, RecordingShell())
    notices.show("Stale notice")

    coordinator.reset()

    assertNull(notices.notice.value)
  }

  /** Records the shell reactions the coordinator asks for, in order. */
  private class RecordingShell : SessionShell {
    val calls = mutableListOf<String>()

    override fun onScreenRequested(screen: AppScreen) {
      calls += "screen $screen"
    }

    override fun onSurveyOpened() {
      calls += "survey opened"
    }

    override fun onReturnToSurveyList() {
      calls += "returned to survey list"
    }

    override fun onSignedOut() {
      calls += "signed out"
    }

    override fun onSignedOutElsewhere() {
      calls += "signed out elsewhere"
    }

    override fun onMapShown() {
      calls += "map shown"
    }

    override fun onUploadsOpened() {
      calls += "uploads opened"
    }

    override fun onSurveyActivated(surveyId: String) {
      calls += "survey activated $surveyId"
    }
  }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val KARIMA = "place-karima-forest"
  }
}
