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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import org.openforis.collectone.v2.web.ui.organization.OrganizationEvent
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorEvent
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardEvent

/** A page change that a ViewModel's outcome asks for. */
sealed interface NavigationRequest {
  /** See [WebNavigator.open]. */
  data class Open(val route: WebRoute) : NavigationRequest

  /** See [WebNavigator.returnTo]. */
  data class ReturnTo(val route: WebRoute) : NavigationRequest

  /** See [WebNavigator.leaveTo]. */
  data class LeaveTo(val route: WebRoute) : NavigationRequest

  /** See [WebNavigator.replace]. */
  data class Replace(val route: WebRoute) : NavigationRequest

  /** See [WebNavigator.back]. */
  data class Back(val fallback: WebRoute) : NavigationRequest
}

/** Applies [request] to the back stack. */
fun WebNavigator.navigate(request: NavigationRequest) {
  when (request) {
    is NavigationRequest.Open -> open(request.route)
    is NavigationRequest.ReturnTo -> returnTo(request.route)
    is NavigationRequest.LeaveTo -> leaveTo(request.route)
    is NavigationRequest.Replace -> replace(request.route)
    is NavigationRequest.Back -> back(request.fallback)
  }
}

/**
 * The page changes that the outcomes of the console's ViewModels ask for ([organizationEvents],
 * [surveyEditorEvents], and [createSurveyWizardEvents]), on the page [currentRoute] returns when
 * each outcome arrives:
 * - Opening an organization, the admin panel, or the organizations list (also after leaving or
 *   deleting an organization) shows that page.
 * - Publishing or closing the Survey editor returns to the survey's dashboard; a publish that
 *   completes after the organizer left the editor doesn't pull them back. Deleting the survey
 *   returns to the surveys list.
 * - Finishing the Survey Creation Wizard shows the new survey's editor in its place; canceling it
 *   returns to the page it was opened from.
 */
fun webNavigationRequests(
  organizationEvents: Flow<OrganizationEvent>,
  surveyEditorEvents: Flow<SurveyEditorEvent>,
  createSurveyWizardEvents: Flow<CreateSurveyWizardEvent>,
  activeSurveyId: () -> String,
  currentRoute: () -> WebRoute?,
): Flow<NavigationRequest> =
  merge(
    organizationEvents.mapNotNull { organizationRequest(it, currentRoute()) },
    surveyEditorEvents.mapNotNull { surveyEditorRequest(it, currentRoute(), activeSurveyId()) },
    createSurveyWizardEvents.mapNotNull { createSurveyWizardRequest(it, currentRoute()) },
  )

private fun organizationRequest(event: OrganizationEvent, current: WebRoute?): NavigationRequest? =
  when (event) {
    is OrganizationEvent.OrganizationOpened ->
      NavigationRequest.Open(OrganizationRoute(event.organizationId)).takeUnless {
        (current as? OrganizationRoute)?.organizationId == event.organizationId
      }
    OrganizationEvent.AdminPanelOpened ->
      NavigationRequest.Open(AdminRoute()).takeUnless { current is AdminRoute }
    OrganizationEvent.OrganizationsOpened -> NavigationRequest.ReturnTo(OrganizationsRoute)
    is OrganizationEvent.ImagerySourceRemoved -> null
  }

private fun surveyEditorRequest(
  event: SurveyEditorEvent,
  current: WebRoute?,
  activeSurveyId: String,
): NavigationRequest? {
  val surveyId = (current as? SurveyEditorRoute)?.surveyId ?: activeSurveyId
  return when (event) {
    SurveyEditorEvent.Published ->
      if (current is SurveyEditorRoute) {
        NavigationRequest.LeaveTo(SurveyDashboardRoute(surveyId))
      } else {
        null
      }
    SurveyEditorEvent.Closed -> NavigationRequest.LeaveTo(SurveyDashboardRoute(surveyId))
    SurveyEditorEvent.Deleted -> NavigationRequest.LeaveTo(SurveysRoute)
  }
}

private fun createSurveyWizardRequest(
  event: CreateSurveyWizardEvent,
  current: WebRoute?,
): NavigationRequest =
  when (event) {
    is CreateSurveyWizardEvent.OpenSurveyEditor ->
      if (current is CreateSurveyRoute) {
        NavigationRequest.Replace(SurveyEditorRoute(event.surveyId))
      } else {
        NavigationRequest.Open(SurveyEditorRoute(event.surveyId))
      }
    CreateSurveyWizardEvent.Cancelled ->
      NavigationRequest.Back(
        fallback =
          (current as? CreateSurveyRoute)?.organizationId?.let(::OrganizationRoute) ?: SurveysRoute
      )
  }
