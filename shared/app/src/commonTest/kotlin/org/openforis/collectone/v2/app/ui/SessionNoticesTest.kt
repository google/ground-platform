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
package org.openforis.collectone.v2.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.session.TestAppSession

/** The session's notice: which outcomes show one, and which clear it. */
class SessionNoticesTest {
  private val session = TestAppSession(sampleDataHolder())

  /** The notice once shown: a notice in the user's language may be resolved asynchronously. */
  private suspend fun shownNotice(): String =
    withContext(Dispatchers.Default) {
      withTimeout(5.seconds) { session.notices.notice.filterNotNull().first() }
    }

  @Test
  fun selectingAPlace_noticesWhereTheMapIsCentered() = runTest {
    session.searchableListActions.selectPlace("place-karima-forest")

    val notice = shownNotice()
    assertTrue(notice.startsWith("Centered map on Karima Hill Forest Reserve ("), notice)
  }

  @Test
  fun navigatingToAPlace_noticesTheNavigationTarget() = runTest {
    session.searchableListActions.toggleNavigationToPlace("place-karima-forest")

    val notice = shownNotice()
    assertTrue(
      notice.startsWith("Straight-line navigation to Karima Hill Forest Reserve ("),
      notice,
    )
  }

  @Test
  fun navigatingToAMapFeature_noticesTheNavigationTarget() = runTest {
    val entity = session.viewModels.dataCollection.uiState.value.entities.first()

    session.viewModels.surveyMap.startNavigationToEntity(entity.id)

    val notice = shownNotice()
    assertTrue(notice.startsWith("Straight-line navigation to ${entity.label} ("), notice)
  }

  @Test
  fun navigatingToASubmission_noticesItsFormAndMapFeature() = runTest {
    val entity =
      session.viewModels.dataCollection.uiState.value.entities.first { it.submissions.isNotEmpty() }
    val submission = entity.submissions.first()

    session.viewModels.surveyMap.startNavigationToSubmission(submission.id)

    val notice = shownNotice()
    val expected =
      "Straight-line navigation to ${submission.formTitle} • " +
        "${entity.label.substringBefore(" •")} ("
    assertTrue(notice.startsWith(expected), notice)
  }

  @Test
  fun stoppingNavigation_clearsTheNotice() {
    session.searchableListActions.toggleNavigationToPlace("place-karima-forest")

    session.viewModels.surveyMap.stopNavigation()

    assertNull(session.notices.notice.value)
  }

  @Test
  fun confirmingSignOut_clearsTheNotice() {
    session.searchableListActions.selectPlace("place-karima-forest")

    session.viewModels.onboarding.confirmSignOut()

    assertNull(session.notices.notice.value)
  }

  @Test
  fun reset_clearsTheNoticeAndTheListSearch() {
    session.searchableListActions.selectPlace("place-karima-forest")
    session.searchableListActions.updateListSearchQuery("Karima")

    session.reset()

    assertNull(session.notices.notice.value)
    assertEquals("", session.viewModels.listSearch.uiState.value.query)
    assertTrue(session.viewModels.listSearch.uiState.value.places.isEmpty())
  }
}
