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
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.web.ui.InviteDeepLink

class WebUrlsTest {

  private fun page(path: String, hash: String = ""): WebRoute? =
    (WebUrls.parse(path, hash) as? WebLink.Page)?.route

  @Test
  fun everyRoute_roundTripsThroughItsUrl() {
    val routes =
      listOf(
        SurveysRoute,
        SurveyDashboardRoute("survey-1"),
        SurveyEditorRoute("survey-1"),
        SurveyEditorRoute("survey-1", section = "sharing"),
        SurveyEditorRoute("survey-1", section = "forms/q1"),
        SurveyEditorRoute("survey-1", section = "datasets/d1"),
        CreateSurveyRoute(),
        CreateSurveyRoute(step = "survey-area"),
        CreateSurveyRoute(organizationId = "org-kfs"),
        CreateSurveyRoute(organizationId = "org-kfs", step = "sharing"),
        OrganizationsRoute,
        OrganizationRoute("org-kfs"),
        OrganizationRoute("org-kfs", tab = "cost-controls"),
        ImpactRoute(),
        ImpactRoute("org-kfs"),
        AdminRoute(),
        AdminRoute(tab = "purposes"),
      )

    for (route in routes) {
      assertEquals(route, page(WebUrls.pathOf(route)), WebUrls.pathOf(route))
    }
  }

  @Test
  fun pathOf_namesThePageAndItsArguments() {
    assertEquals("/surveys", WebUrls.pathOf(SurveysRoute))
    assertEquals("/surveys/s-1/dashboard", WebUrls.pathOf(SurveyDashboardRoute("s-1")))
    assertEquals(
      "/surveys/s-1/editor/forms/q1",
      WebUrls.pathOf(SurveyEditorRoute("s-1", "forms/q1")),
    )
    assertEquals("/surveys/new", WebUrls.pathOf(CreateSurveyRoute()))
    assertEquals(
      "/organizations/org-kfs/surveys/new/mode-and-purpose",
      WebUrls.pathOf(CreateSurveyRoute("org-kfs", "mode-and-purpose")),
    )
    assertEquals(
      "/organizations/org-kfs/members",
      WebUrls.pathOf(OrganizationRoute("org-kfs", "members")),
    )
    assertEquals("/impact/org-kfs", WebUrls.pathOf(ImpactRoute("org-kfs")))
    assertEquals("/admin", WebUrls.pathOf(AdminRoute()))
  }

  @Test
  fun idsWithReservedCharacters_arePercentEncodedAndDecoded() {
    val route = SurveyDashboardRoute("a b/c?é")

    val path = WebUrls.pathOf(route)

    assertEquals("/surveys/a%20b%2Fc%3F%C3%A9/dashboard", path)
    assertEquals(route, page(path))
  }

  @Test
  fun shortcuts_openTheirPages() {
    assertEquals(SurveysRoute, page("/"))
    assertEquals(SurveysRoute, page(""))
    assertEquals(SurveyDashboardRoute("s-1"), page("/surveys/s-1"))
    assertEquals(SurveyDashboardRoute("s-1"), page("/surveys/s-1/dashboard/"))
  }

  @Test
  fun theAllUsersOrganization_opensTheAdminPanel() {
    assertEquals(AdminRoute(), page("/organizations/${Organization.ALL_USERS_ID}"))
    assertEquals(
      AdminRoute(tab = "members"),
      page("/organizations/${Organization.ALL_USERS_ID}/members"),
    )
  }

  @Test
  fun unknownPagesTabsStepsAndSections_areUnknownLinks() {
    for (path in
      listOf(
        "/prototype",
        "/surveys/s-1/settings",
        "/surveys/s-1/editor/forms",
        "/surveys/s-1/editor/questions/q1",
        "/surveys/new/step-7",
        "/organizations/org-kfs/history",
        "/organizations/org-kfs/members/extra",
        "/impact/org-kfs/extra",
        "/admin/history",
      )) {
      assertEquals(WebLink.Unknown, WebUrls.parse(path), path)
    }
  }

  @Test
  fun invitationHashes_areInvites() {
    assertEquals(
      WebLink.Invite(InviteDeepLink(InviteDeepLink.Kind.SURVEY, "survey-123", "inv_token_abc")),
      WebUrls.parse("/", "#join/survey-123/inv_token_abc"),
    )
    assertEquals(
      WebLink.Invite(InviteDeepLink(InviteDeepLink.Kind.ORGANIZATION, "org-kfs", "tok/en")),
      WebUrls.parse("/", "#org-join/org-kfs/tok/en"),
    )
    // Incomplete invitations open the surveys list.
    assertEquals(SurveysRoute, page("/", "#join/survey-123"))
  }

  @Test
  fun hashesOfEarlierConsoleVersions_openTheirPages() {
    assertEquals(SurveysRoute, page("/", "#surveys"))
    assertEquals(CreateSurveyRoute(), page("/", "#create-survey"))
    assertEquals(OrganizationsRoute, page("/", "#organizations"))
    assertEquals(OrganizationRoute("org-kfs"), page("/", "#organization/org-kfs"))
    assertEquals(OrganizationsRoute, page("/", "#organization"))
    assertEquals(AdminRoute(), page("/", "#organization/${Organization.ALL_USERS_ID}"))
    assertEquals(AdminRoute(), page("/", "#admin"))
    assertEquals(ImpactRoute(), page("/", "#impact"))
    assertEquals(ImpactRoute("org-kfs"), page("/", "#impact/org-kfs"))
    // Their survey pages named no survey.
    for (hash in listOf("#dashboard", "#web", "#survey-editor", "#form-editor")) {
      assertEquals(SurveysRoute, page("/", hash), hash)
    }
    // Unknown hashes are ignored, and hashes are only read on the root path.
    assertEquals(SurveysRoute, page("/", "#prototype"))
    assertEquals(OrganizationsRoute, page("/organizations", "#impact"))
  }
}
