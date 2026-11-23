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
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.organization
import org.openforis.collectone.v2.web.ui.surveyEditor

/** The page changes that ViewModel outcomes ask for ([webNavigationRequests]). */
class WebNavigationRequestsTest {
  private val state =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
    }
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE

  @Test
  fun openingAnOrganization_opensItsPage_unlessItIsAlreadyShowing() {
    NavigationRecorder(state, start = OrganizationsRoute).use { navigation ->
      state.organization.openOrganization(kfs)
      state.organization.openOrganization(kfs)

      assertEquals<List<NavigationRequest>>(
        listOf(NavigationRequest.Open(OrganizationRoute(kfs))),
        navigation.requests,
      )
    }
  }

  @Test
  fun theOrganizationsList_isReturnedTo() {
    NavigationRecorder(state, start = OrganizationRoute(kfs)).use { navigation ->
      state.organization.openOrganizations()

      assertEquals<List<NavigationRequest>>(
        listOf(NavigationRequest.ReturnTo(OrganizationsRoute)),
        navigation.requests,
      )
    }
  }

  @Test
  fun theAdminPanel_opensOnce() {
    NavigationRecorder(state, start = SurveysRoute).use { navigation ->
      state.organization.openAdminPanel()
      state.organization.openAdminPanel()

      assertEquals<List<NavigationRequest>>(
        listOf(NavigationRequest.Open(AdminRoute())),
        navigation.requests,
      )
    }
  }

  @Test
  fun closingTheSurveyEditor_leavesItForTheSurveysDashboard() {
    val surveyId = state.viewModels.dashboard.uiState.value.activeSurveyId
    NavigationRecorder(state, start = SurveyEditorRoute(surveyId)).use { navigation ->
      state.surveyEditor.close()

      assertEquals<List<NavigationRequest>>(
        listOf(NavigationRequest.LeaveTo(SurveyDashboardRoute(surveyId))),
        navigation.requests,
      )
    }
  }

  @Test
  fun deletingTheSurvey_leavesTheEditorForTheSurveysList() {
    val surveyId = state.viewModels.dashboard.uiState.value.activeSurveyId
    NavigationRecorder(state, start = SurveyEditorRoute(surveyId)).use { navigation ->
      state.surveyEditor.deleteSurvey()

      assertEquals<List<NavigationRequest>>(
        listOf(NavigationRequest.LeaveTo(SurveysRoute)),
        navigation.requests,
      )
      assertTrue(state.viewModels.dashboard.uiState.value.surveys.none { it.id == surveyId })
    }
  }
}
