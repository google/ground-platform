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

/** What the signed-in user can open, as far as the console knows so far. */
data class WebRouteAccess(
  /** Whether the first sync from the user data service finished, so missing data is really gone. */
  val hasCompletedInitialSync: Boolean,
  /** The surveys the signed-in user can see. */
  val surveyIds: Set<String>,
  val activeSurveyId: String,
  /** Whether the signed-in user may open the Survey editor of the active survey. */
  val canManageActiveSurvey: Boolean,
  val isPlatformAdmin: Boolean,
  /**
   * Every known organization by ID: whether the signed-in user may open its page (it's listed, or
   * they are a member).
   */
  val canOpenOrganization: Map<String, Boolean>,
)

/** Whether a page shows, waits for data, or sends the user elsewhere. */
sealed interface RouteDecision {
  data object Show : RouteDecision

  /** The page's data hasn't arrived yet. */
  data object Wait : RouteDecision

  /** The user can't see the page; show [route] instead, explaining why with [notice]. */
  data class Redirect(val route: WebRoute, val notice: NavigationNotice?) : RouteDecision
}

/**
 * Decides whether the signed-in user can see a page they opened, by link or from another page:
 * - A survey page whose survey they can't see goes to the surveys list, and the Survey editor of a
 *   survey they can't manage goes to its dashboard.
 * - The admin panel is only for platform administrators.
 * - An unlisted organization's page is only for its members; others go to the organizations list.
 *
 * Missing data counts as missing only once the initial sync finished. A page that already showed
 * redirects silently when its data goes away (a survey was deleted, say); a link that never showed
 * its page explains why with [NavigationNotice.LINK_UNAVAILABLE].
 */
object WebRouteGuard {

  fun decide(route: WebRoute, access: WebRouteAccess, hasBeenShown: Boolean): RouteDecision {
    val notice = NavigationNotice.LINK_UNAVAILABLE.takeUnless { hasBeenShown }
    return when (route) {
      is SurveyDashboardRoute ->
        decideSurveyPage(route.surveyId, access, notice) { RouteDecision.Show }
      is SurveyEditorRoute ->
        decideSurveyPage(route.surveyId, access, notice) {
          if (access.canManageActiveSurvey) {
            RouteDecision.Show
          } else {
            RouteDecision.Redirect(SurveyDashboardRoute(route.surveyId), notice = null)
          }
        }
      is AdminRoute ->
        when {
          access.isPlatformAdmin -> RouteDecision.Show
          !access.hasCompletedInitialSync -> RouteDecision.Wait
          else -> RouteDecision.Redirect(SurveysRoute, notice)
        }
      is OrganizationRoute ->
        when (access.canOpenOrganization[route.organizationId]) {
          true -> RouteDecision.Show
          false -> RouteDecision.Redirect(OrganizationsRoute, notice)
          null ->
            when {
              !access.hasCompletedInitialSync -> RouteDecision.Wait
              // The page explains that its organization is gone.
              hasBeenShown -> RouteDecision.Show
              else -> RouteDecision.Redirect(SurveysRoute, notice)
            }
        }
      SurveysRoute,
      OrganizationsRoute,
      is CreateSurveyRoute,
      is ImpactRoute -> RouteDecision.Show
    }
  }

  /**
   * A page of the survey [surveyId]: [decideOpenSurvey] once it's the active survey,
   * [RouteDecision.Wait] while it opens or the initial sync may still bring it, or else back to the
   * surveys list.
   */
  private fun decideSurveyPage(
    surveyId: String,
    access: WebRouteAccess,
    notice: NavigationNotice?,
    decideOpenSurvey: () -> RouteDecision,
  ): RouteDecision =
    when {
      surveyId in access.surveyIds && surveyId == access.activeSurveyId -> decideOpenSurvey()
      surveyId in access.surveyIds || !access.hasCompletedInitialSync -> RouteDecision.Wait
      else -> RouteDecision.Redirect(SurveysRoute, notice)
    }
}
