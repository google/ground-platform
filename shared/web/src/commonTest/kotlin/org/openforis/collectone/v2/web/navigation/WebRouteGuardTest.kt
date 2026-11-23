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

class WebRouteGuardTest {

  private val synced =
    WebRouteAccess(
      hasCompletedInitialSync = true,
      surveyIds = setOf("survey-1", "survey-2"),
      activeSurveyId = "survey-1",
      canManageActiveSurvey = true,
      isPlatformAdmin = false,
      canOpenOrganization = mapOf("org-listed" to true, "org-unlisted" to false),
    )

  private fun decide(
    route: WebRoute,
    access: WebRouteAccess = synced,
    hasBeenShown: Boolean = false,
  ) = WebRouteGuard.decide(route, access, hasBeenShown)

  private val unavailable = NavigationNotice.LINK_UNAVAILABLE

  @Test
  fun surveyPages_showTheActiveSurvey_andWaitWhileAnotherKnownSurveyOpens() {
    assertEquals(RouteDecision.Show, decide(SurveyDashboardRoute("survey-1")))
    assertEquals(RouteDecision.Show, decide(SurveyEditorRoute("survey-1")))
    assertEquals(RouteDecision.Wait, decide(SurveyDashboardRoute("survey-2")))
  }

  @Test
  fun anUnknownSurvey_waitsForTheInitialSync_thenGoesToTheSurveysListWithANotice() {
    val syncing = synced.copy(hasCompletedInitialSync = false)

    assertEquals(RouteDecision.Wait, decide(SurveyDashboardRoute("survey-9"), syncing))
    assertEquals(
      RouteDecision.Redirect(SurveysRoute, unavailable),
      decide(SurveyDashboardRoute("survey-9")),
    )
    assertEquals(
      RouteDecision.Redirect(SurveysRoute, unavailable),
      decide(SurveyEditorRoute("survey-9")),
    )
  }

  @Test
  fun aSurveyThatGoesAwayAfterShowing_returnsToTheSurveysListSilently() {
    assertEquals(
      RouteDecision.Redirect(SurveysRoute, notice = null),
      decide(SurveyDashboardRoute("survey-9"), hasBeenShown = true),
    )
  }

  @Test
  fun theSurveyEditor_ofASurveyTheUserCantManage_opensItsDashboard() {
    assertEquals(
      RouteDecision.Redirect(SurveyDashboardRoute("survey-1"), notice = null),
      decide(SurveyEditorRoute("survey-1"), synced.copy(canManageActiveSurvey = false)),
    )
  }

  @Test
  fun theAdminPanel_isOnlyForPlatformAdministrators() {
    assertEquals(RouteDecision.Show, decide(AdminRoute(), synced.copy(isPlatformAdmin = true)))
    assertEquals(
      RouteDecision.Wait,
      decide(AdminRoute(), synced.copy(hasCompletedInitialSync = false)),
    )
    assertEquals(RouteDecision.Redirect(SurveysRoute, unavailable), decide(AdminRoute()))
  }

  @Test
  fun organizationPages_ofListedOrJoinedOrganizationsShow_andOthersGoToTheOrganizationsList() {
    assertEquals(RouteDecision.Show, decide(OrganizationRoute("org-listed")))
    assertEquals(
      RouteDecision.Redirect(OrganizationsRoute, unavailable),
      decide(OrganizationRoute("org-unlisted")),
    )
  }

  @Test
  fun anUnknownOrganization_waitsForTheInitialSync_thenGoesToTheSurveysList() {
    assertEquals(
      RouteDecision.Wait,
      decide(OrganizationRoute("org-9"), synced.copy(hasCompletedInitialSync = false)),
    )
    assertEquals(
      RouteDecision.Redirect(SurveysRoute, unavailable),
      decide(OrganizationRoute("org-9")),
    )
    // A page that showed explains itself that its organization is gone.
    assertEquals(RouteDecision.Show, decide(OrganizationRoute("org-9"), hasBeenShown = true))
  }

  @Test
  fun pagesWithoutAccessRules_alwaysShow() {
    for (route in listOf(SurveysRoute, OrganizationsRoute, CreateSurveyRoute(), ImpactRoute("x"))) {
      assertEquals(RouteDecision.Show, decide(route, synced.copy(hasCompletedInitialSync = false)))
    }
  }
}
