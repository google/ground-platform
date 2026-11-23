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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.organization
import org.openforis.collectone.v2.web.ui.surveyEditor

/**
 * A [WebPageNavigation] for pages shown without a `NavHost`: it keeps only the page the last change
 * asked for, as [currentRoute].
 */
class RecordingPageNavigation(start: WebRoute = SurveysRoute) : WebPageNavigation {
  var currentRoute: WebRoute = start
    private set

  override fun open(route: WebRoute) {
    currentRoute = route
  }

  override fun returnTo(route: WebRoute) {
    currentRoute = route
  }

  override fun update(route: WebRoute) {
    currentRoute = route
  }
}

/**
 * Records the [NavigationRequest]s that [state]'s ViewModels make (see [webNavigationRequests]),
 * starting on [start]. [currentRoute] follows each request; [NavigationRequest.Back] has no back
 * stack to pop here, so it goes to its fallback. [close] stops recording.
 */
class NavigationRecorder(state: WebAppState, start: WebRoute) : AutoCloseable {
  var currentRoute: WebRoute = start
    private set

  val requests = mutableListOf<NavigationRequest>()

  private val scope = CoroutineScope(Dispatchers.Unconfined)

  init {
    scope.launch {
      webNavigationRequests(
          organizationEvents = state.organization.events,
          surveyEditorEvents = state.surveyEditor.events,
          createSurveyWizardEvents = state.createSurveyWizard.events,
          activeSurveyId = { state.viewModels.dashboard.uiState.value.activeSurveyId },
          currentRoute = { currentRoute },
        )
        .collect { request ->
          requests += request
          currentRoute =
            when (request) {
              is NavigationRequest.Open -> request.route
              is NavigationRequest.ReturnTo -> request.route
              is NavigationRequest.LeaveTo -> request.route
              is NavigationRequest.Replace -> request.route
              is NavigationRequest.Back -> request.fallback
            }
        }
    }
  }

  override fun close() = scope.cancel()
}
