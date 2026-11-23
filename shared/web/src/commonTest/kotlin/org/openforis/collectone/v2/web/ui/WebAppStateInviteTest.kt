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
package org.openforis.collectone.v2.web.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.di.AppDataHolder

/** Redeeming `#join/…` and `#org-join/…` invitation deep links in the web console. */
class WebAppStateInviteTest {
  private var isRedeemed = false

  @Test
  fun surveyInvite_opensTheSurveyOnceSignedIn() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val invited =
      state.viewModels.dashboard.uiState.value.surveys.last {
        it.id != state.viewModels.dashboard.uiState.value.activeSurveyId
      }

    state.redeemInviteDeepLink(
      InviteDeepLink(kind = InviteDeepLink.Kind.SURVEY, targetId = invited.id, token = "token"),
      state.organization,
    ) {
      isRedeemed = true
    }
    assertFalse(isRedeemed)
    state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
    state.viewModels.onboarding.acceptTermsOfService()

    assertTrue(isRedeemed)
    assertEquals(invited.id, state.viewModels.dashboard.uiState.value.activeSurveyId)
  }

  @Test
  fun organizationInvite_opensTheOrganizationOnceSignedIn() {
    val state = WebAppState(dataHolder = sampleDataHolder())

    state.redeemInviteDeepLink(ORGANIZATION_INVITE, state.organization) { isRedeemed = true }
    state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
    state.viewModels.onboarding.acceptTermsOfService()

    assertTrue(isRedeemed)
    assertEquals(SampleOrganizationsData.KENYA_FOREST_SERVICE, state.openOrganizationId)
  }

  @Test
  fun organizationInvite_withoutUserDataService_opensTheOrganizationOnceSignedIn() {
    val state = WebAppState(dataHolder = AppDataHolder(localStore = seededStore()))

    state.redeemInviteDeepLink(ORGANIZATION_INVITE, state.organization) { isRedeemed = true }
    state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
    state.viewModels.onboarding.acceptTermsOfService()

    assertTrue(isRedeemed)
    assertEquals(SampleOrganizationsData.KENYA_FOREST_SERVICE, state.openOrganizationId)
  }

  @Test
  fun invite_waitsForTheTermsOfService() {
    val state = WebAppState(dataHolder = sampleDataHolder())

    state.redeemInviteDeepLink(ORGANIZATION_INVITE, state.organization) { isRedeemed = true }
    state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)

    assertFalse(isRedeemed)
    state.viewModels.onboarding.acceptTermsOfService()

    assertTrue(isRedeemed)
    assertEquals(SampleOrganizationsData.KENYA_FOREST_SERVICE, state.openOrganizationId)
  }

  @Test
  fun invite_isNotRedeemed_whenTheTermsAreDeclined() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val activeSurveyId = state.viewModels.dashboard.uiState.value.activeSurveyId
    val invited = state.viewModels.dashboard.uiState.value.surveys.last { it.id != activeSurveyId }

    state.redeemInviteDeepLink(
      InviteDeepLink(kind = InviteDeepLink.Kind.SURVEY, targetId = invited.id, token = "token"),
      state.organization,
    ) {
      isRedeemed = true
    }
    state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
    state.viewModels.onboarding.declineTermsOfService()

    assertFalse(isRedeemed)
    assertEquals(activeSurveyId, state.viewModels.dashboard.uiState.value.activeSurveyId)
  }

  private companion object {
    val ORGANIZATION_INVITE =
      InviteDeepLink(
        kind = InviteDeepLink.Kind.ORGANIZATION,
        targetId = SampleOrganizationsData.KENYA_FOREST_SERVICE,
        token = "token",
      )
  }
}
