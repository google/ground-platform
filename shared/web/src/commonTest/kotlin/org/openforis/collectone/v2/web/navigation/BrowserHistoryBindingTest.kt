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
package org.openforis.collectone.v2.web.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.web.ui.InviteDeepLink

class BrowserHistoryBindingTest {

  private fun initialBackStack(history: MemoryBrowserHistory) =
    BrowserHistoryBinding.initialBackStack(history, WebUrls.parse(history.path, history.hash))

  @Test
  fun aBackStack_roundTripsThroughAHistoryState() {
    val routes =
      listOf(SurveysRoute, SurveyDashboardRoute("s-1"), SurveyEditorRoute("s-1", "sharing"))

    val state = BrowserHistoryBinding.encodeBackStack(routes)

    assertEquals("/surveys\n/surveys/s-1/dashboard\n/surveys/s-1/editor/sharing", state)
    assertEquals(routes, BrowserHistoryBinding.decodeBackStack(state))
  }

  @Test
  fun statesThatArentBackStacks_decodeToNull() {
    assertNull(BrowserHistoryBinding.decodeBackStack(null))
    assertNull(BrowserHistoryBinding.decodeBackStack("/surveys\n/no-such-page"))
  }

  @Test
  fun onReload_theStoredBackStackOpens() {
    val history = MemoryBrowserHistory("/impact", initialState = "/surveys\n/impact")

    assertEquals(listOf(SurveysRoute, ImpactRoute()), initialBackStack(history))
  }

  @Test
  fun aStoredBackStackOfAnotherPage_isIgnored() {
    val history = MemoryBrowserHistory("/organizations", initialState = "/surveys\n/impact")

    assertEquals(listOf(OrganizationsRoute), initialBackStack(history))
  }

  @Test
  fun unknownLinksAndInvitations_startOnTheSurveysList() {
    assertEquals(listOf(SurveysRoute), initialBackStack(MemoryBrowserHistory("/no-such-page")))
    val invite = MemoryBrowserHistory("/", initialHash = "#join/s-1/token")
    assertEquals(
      WebLink.Invite(InviteDeepLink(InviteDeepLink.Kind.SURVEY, "s-1", "token")),
      WebUrls.parse(invite.path, invite.hash),
    )
    assertEquals(listOf(SurveysRoute), initialBackStack(invite))
  }

  @Test
  fun memoryHistory_dropsForwardEntriesOnPush_likeABrowser() {
    val history = MemoryBrowserHistory("/surveys")
    history.push("/impact", "/surveys\n/impact")
    history.back()

    history.push("/organizations", "/surveys\n/organizations")

    assertEquals(listOf("/surveys", "/organizations"), history.paths)
  }
}
