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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.navigation.DownloadSurveyEntryOrigin
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** Onboarding: sign in, Terms of Service, and finding, downloading, and removing surveys. */
class MobileOnboardingTest {
  private val state = MobileAppState(dataHolder = sampleDataHolder())
  private val onboarding = state.viewModels.onboarding

  private val onboardingUiState
    get() = onboarding.uiState.value

  private fun isDownloaded(surveyId: String) =
    state.viewModels.dashboard.uiState.value.surveys.first { it.id == surveyId }.isDownloaded

  private fun reachDownloadSurveyAfterTerms() {
    state.viewModels.onboarding.signInWithGoogle()
    onboarding.acceptTermsOfService()
  }

  private fun reachDownloadSurveyFromSurveyList() {
    reachDownloadSurveyAfterTerms()
    onboarding.openSurvey(DOWNLOADED_SURVEY)
    state.drawerSwitchSurveys()
    state.openDownloadMoreSurveysScreen()
  }

  // --- Sign in and Terms of Service ---

  @Test
  fun appStartsOnSignIn() {
    assertEquals(MobileRoute.SignIn, state.currentRoute)
    assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
  }

  @Test
  fun sampleSurveysAreListedAtStart() {
    assertEquals(7, state.viewModels.dashboard.uiState.value.surveys.size)
    assertEquals(7, onboardingUiState.filteredSurveys.size)
    assertTrue(onboardingUiState.downloadedSurveyCount >= 1)
  }

  @Test
  fun signInWithGoogle_showsTheTermsOfService() {
    state.viewModels.onboarding.signInWithGoogle()

    assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)
    assertEquals(MobileRoute.TermsOfService, state.currentRoute)
  }

  @Test
  fun acceptTermsOfService_showsDownloadSurvey() {
    state.viewModels.onboarding.signInWithGoogle()

    onboarding.acceptTermsOfService()

    assertTrue(onboardingUiState.hasAcceptedTerms)
    assertEquals(MobileRoute.DownloadSurvey, state.currentRoute)
  }

  @Test
  fun declineTermsOfService_signsOutToSignIn() {
    state.viewModels.onboarding.signInWithGoogle()

    onboarding.declineTermsOfService()

    assertEquals(MobileRoute.SignIn, state.currentRoute)
    assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
    assertFalse(onboardingUiState.hasAcceptedTerms)
  }

  // --- Survey search ---

  @Test
  fun searchQuery_matchesSurveyTitles() {
    onboarding.updateSearchQuery("Mangrove")

    assertEquals(
      listOf("Mekong Delta Mangrove Restoration"),
      onboardingUiState.filteredSurveys.map { it.title },
    )
  }

  @Test
  fun searchQuery_matchesSurveyLocations() {
    onboarding.updateSearchQuery("Nyeri")

    assertEquals(
      listOf("Nyeri County, Kenya"),
      onboardingUiState.filteredSurveys.map { it.location },
    )
  }

  @Test
  fun searchQuery_matchesOrganizationNames() {
    onboarding.updateSearchQuery("Forest Service")

    assertEquals(3, onboardingUiState.filteredSurveys.size)
    assertTrue(
      onboardingUiState.filteredSurveys.all {
        it.organizationId == "00000013-31cc-5c95-90ea-379f624a5c72"
      }
    )
  }

  @Test
  fun clearSearchQuery_listsEverySurveyAgain() {
    onboarding.updateSearchQuery("Brazil")

    onboarding.clearSearchQuery()

    assertEquals(7, onboardingUiState.filteredSurveys.size)
  }

  // --- Opening a survey ---

  @Test
  fun openSurvey_showsTheSurveyOnTheMap() {
    onboarding.openSurvey(DOWNLOADED_SURVEY)

    assertEquals(MobileRoute.MainSurvey, state.currentRoute)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals(DOWNLOADED_SURVEY, state.activeSurveyAccess.activeSurvey.id)
  }

  @Test
  fun openSurvey_selectsNoMapFeature() {
    onboarding.openSurvey(DOWNLOADED_SURVEY)

    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntity)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  // --- Leaving Download survey ---

  @Test
  fun downloadSurveyAfterTerms_remembersWhereItWasOpenedFrom() {
    reachDownloadSurveyAfterTerms()

    assertEquals(DownloadSurveyEntryOrigin.AFTER_TOS, onboardingUiState.entryOrigin)
    assertFalse(onboardingUiState.isSignOutPromptOpen)
  }

  @Test
  fun backFromDownloadSurveyAfterTerms_asksBeforeSigningOut() {
    reachDownloadSurveyAfterTerms()

    onboarding.navigateBackFromDownloadSurvey()

    assertTrue(onboardingUiState.isSignOutPromptOpen)
    assertEquals(MobileRoute.DownloadSurvey, state.currentRoute)
    assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)
  }

  @Test
  fun dismissingTheSignOutPrompt_staysSignedInOnDownloadSurvey() {
    reachDownloadSurveyAfterTerms()
    onboarding.navigateBackFromDownloadSurvey()

    onboarding.dismissSignOutPrompt()

    assertFalse(onboardingUiState.isSignOutPromptOpen)
    assertEquals(MobileRoute.DownloadSurvey, state.currentRoute)
    assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)
  }

  @Test
  fun confirmingTheSignOutPrompt_signsOutToSignIn() {
    reachDownloadSurveyAfterTerms()
    onboarding.navigateBackFromDownloadSurvey()

    onboarding.confirmSignOut()

    assertFalse(onboardingUiState.isSignOutPromptOpen)
    assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
    assertFalse(onboardingUiState.hasAcceptedTerms)
    assertEquals(MobileRoute.SignIn, state.currentRoute)
  }

  @Test
  fun downloadSurveyFromTheSurveyList_remembersWhereItWasOpenedFrom() {
    reachDownloadSurveyFromSurveyList()

    assertEquals(MobileRoute.DownloadSurvey, state.currentRoute)
    assertEquals(DownloadSurveyEntryOrigin.SURVEY_LIST, onboardingUiState.entryOrigin)
    assertTrue(onboardingUiState.isDownloadSurveyAccessedFromSurveyList)
  }

  @Test
  fun backFromDownloadSurveyOpenedFromTheSurveyList_returnsToTheListWithoutAsking() {
    reachDownloadSurveyFromSurveyList()

    onboarding.navigateBackFromDownloadSurvey()

    assertFalse(onboardingUiState.isSignOutPromptOpen)
    assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)
    assertEquals(MobileRoute.DownloadedSurveys, state.currentRoute)
  }

  // --- Removing downloaded surveys ---

  @Test
  fun promptRemoveDownloadedSurvey_asksBeforeRemoving() {
    onboarding.promptRemoveDownloadedSurvey(DOWNLOADED_SURVEY)

    assertEquals(DOWNLOADED_SURVEY, onboardingUiState.pendingRemovalSurveyId)
    assertTrue(isDownloaded(DOWNLOADED_SURVEY))
  }

  @Test
  fun dismissRemoveDownloadedSurvey_keepsTheSurvey() {
    onboarding.promptRemoveDownloadedSurvey(DOWNLOADED_SURVEY)

    onboarding.dismissRemoveDownloadedSurvey()

    assertNull(onboardingUiState.pendingRemovalSurveyId)
    assertTrue(isDownloaded(DOWNLOADED_SURVEY))
  }

  @Test
  fun confirmRemoveDownloadedSurvey_removesTheOfflineCopy() {
    onboarding.promptRemoveDownloadedSurvey(DOWNLOADED_SURVEY)

    onboarding.confirmRemoveDownloadedSurvey()

    assertNull(onboardingUiState.pendingRemovalSurveyId)
    assertFalse(isDownloaded(DOWNLOADED_SURVEY))
  }

  @Test
  fun confirmRemoveDownloadedSurvey_confirmsTheRemoval() {
    onboarding.promptRemoveDownloadedSurvey(DOWNLOADED_SURVEY)

    onboarding.confirmRemoveDownloadedSurvey()

    assertTrue(state.notices.notice.value?.contains("Removed offline copy") == true)
  }

  @Test
  fun promptRemoveDownloadedSurvey_downloadsASurveyThatIsNotDownloaded() {
    onboarding.promptRemoveDownloadedSurvey(NOT_DOWNLOADED_SURVEY)

    assertNull(onboardingUiState.pendingRemovalSurveyId)
    assertTrue(isDownloaded(NOT_DOWNLOADED_SURVEY))
  }

  private companion object {
    const val DOWNLOADED_SURVEY = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val NOT_DOWNLOADED_SURVEY = "00000035-caf4-571e-b907-f506f92666ab"
  }
}
