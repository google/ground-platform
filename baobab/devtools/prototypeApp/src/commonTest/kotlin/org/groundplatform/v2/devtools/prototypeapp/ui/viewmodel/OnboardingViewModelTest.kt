/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.device.PrototypeAuthDataSource
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OnboardingEvent

/**
 * Tests run outside [runNow] on purpose: the view model's [Dispatchers.Unconfined] scope only runs
 * nested coroutines after the current one yields, so a fixture built inside an Unconfined block
 * would not see its own state updates until the block ends.
 */
class OnboardingViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val surveyRepository = SurveyRepositoryImpl(store)
    val viewModel =
      OnboardingViewModel(
        authRepository = AuthRepositoryImpl(PrototypeAuthDataSource()),
        surveyRepository = surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(store),
        scope = scope,
      )
    val events = mutableListOf<OnboardingEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
    }

    val uiState
      get() = viewModel.uiState.value

    fun lastEvent() = events.last()
  }

  @Test
  fun signIn_thenAcceptTerms_navigatesToDownloadSurvey() {
    val f = Fixture()
    assertFalse(f.uiState.isSignedIn)

    f.viewModel.signInWithGoogle()
    assertTrue(f.uiState.isSignedIn)
    assertTrue(f.uiState.profile.email.isNotBlank())
    assertEquals(OnboardingEvent.ShowScreen(AppScreen.TERMS_OF_SERVICE), f.lastEvent())

    f.viewModel.setTermsChecked(false)
    assertFalse(f.uiState.termsCheckboxChecked)
    f.viewModel.setTermsChecked(true)
    f.viewModel.acceptTermsOfService()
    assertTrue(f.uiState.hasAcceptedTerms)
    assertEquals(DownloadSurveyEntryOrigin.AFTER_TOS, f.uiState.entryOrigin)
    assertEquals(OnboardingEvent.ShowScreen(AppScreen.DOWNLOAD_SURVEY), f.lastEvent())

    // Signing in again after accepting terms skips the Terms of Service screen.
    f.viewModel.signOut()
    assertFalse(f.uiState.isSignedIn)
  }

  @Test
  fun declineTerms_signsOutAndReturnsToSignIn() {
    val f = Fixture()
    f.viewModel.signInWithGoogle()
    f.viewModel.declineTermsOfService()

    assertFalse(f.uiState.isSignedIn)
    assertFalse(f.uiState.hasAcceptedTerms)
    assertTrue(OnboardingEvent.SignedOut in f.events)
    assertEquals(OnboardingEvent.ShowScreen(AppScreen.SIGN_IN), f.lastEvent())
  }

  @Test
  fun searchQuery_filtersByTitleLocationAndOrganization() {
    val f = Fixture()
    val all = f.uiState.surveys
    assertTrue(all.isNotEmpty())
    assertEquals(all, f.uiState.filteredSurveys)

    f.viewModel.updateSearchQuery("mangrove")
    assertTrue(f.uiState.filteredSurveys.isNotEmpty())
    assertTrue(f.uiState.filteredSurveys.all { it.title.contains("Mangrove", ignoreCase = true) })

    f.viewModel.updateSearchQuery("Kenya Forest Service")
    val orgMatches = f.uiState.filteredSurveys
    assertTrue(orgMatches.isNotEmpty())
    assertTrue(
      orgMatches.all { f.uiState.organizationNames[it.organizationId] == "Kenya Forest Service" }
    )

    f.viewModel.updateSearchQuery("no-such-survey-xyz")
    assertTrue(f.uiState.filteredSurveys.isEmpty())

    f.viewModel.clearSearchQuery()
    assertEquals("", f.uiState.searchQuery)
    assertEquals(all, f.uiState.filteredSurveys)
  }

  @Test
  fun downloadAndRemove_updateRepositoryAndEmitNotices() {
    val f = Fixture()
    val remote = f.uiState.surveys.first { !it.isDownloaded }
    val before = f.uiState.downloadedSurveyCount

    f.viewModel.downloadSurvey(remote.id)
    assertTrue(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
    assertEquals(before + 1, f.uiState.downloadedSurveyCount)
    val downloaded = assertIs<OnboardingEvent.Notice>(f.lastEvent())
    assertTrue(downloaded.message.contains("Downloaded \"${remote.title}\""))

    f.viewModel.promptRemoveDownloadedSurvey(remote.id)
    assertEquals(remote.id, f.uiState.pendingRemovalSurveyId)
    f.viewModel.dismissRemoveDownloadedSurvey()
    assertNull(f.uiState.pendingRemovalSurveyId)

    f.viewModel.promptRemoveDownloadedSurvey(remote.id)
    f.viewModel.confirmRemoveDownloadedSurvey()
    assertNull(f.uiState.pendingRemovalSurveyId)
    assertFalse(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
    assertEquals(before, f.uiState.downloadedSurveyCount)
    val removed = assertIs<OnboardingEvent.Notice>(f.lastEvent())
    assertTrue(removed.message.contains("Removed offline copy"))
  }

  @Test
  fun promptRemove_onUndownloadedSurvey_downloadsInstead() {
    val f = Fixture()
    val remote = f.uiState.surveys.first { !it.isDownloaded }
    f.viewModel.promptRemoveDownloadedSurvey(remote.id)
    assertNull(f.uiState.pendingRemovalSurveyId)
    assertTrue(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
  }

  @Test
  fun backFromDownloadSurvey_dependsOnEntryOrigin() {
    val f = Fixture()
    f.viewModel.signInWithGoogle()
    f.viewModel.acceptTermsOfService()

    // Reached after Terms of Service: Back asks before signing out.
    f.viewModel.navigateBackFromDownloadSurvey()
    assertTrue(f.uiState.isSignOutPromptOpen)
    f.viewModel.dismissSignOutPrompt()
    assertFalse(f.uiState.isSignOutPromptOpen)
    assertTrue(f.uiState.isSignedIn)

    f.viewModel.navigateBackFromDownloadSurvey()
    f.viewModel.confirmSignOut()
    assertFalse(f.uiState.isSignedIn)
    assertFalse(f.uiState.isSignOutPromptOpen)
    assertEquals(OnboardingEvent.ShowScreen(AppScreen.SIGN_IN), f.lastEvent())

    // Reached from the Survey list: Back simply returns there.
    f.viewModel.signInWithGoogle()
    f.viewModel.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)
    assertTrue(f.uiState.isDownloadSurveyAccessedFromSurveyList)
    f.viewModel.navigateBackFromDownloadSurvey()
    assertFalse(f.uiState.isSignOutPromptOpen)
    assertEquals(OnboardingEvent.ReturnToSurveyList, f.lastEvent())
    assertTrue(f.uiState.isSignedIn)
  }

  @Test
  fun openSurvey_activatesSurveyAndEmitsSurveyOpened() {
    val f = Fixture()
    val remote = f.uiState.surveys.first { !it.isDownloaded }

    f.viewModel.openSurvey(remote.id)

    assertEquals(remote.id, runNow { f.surveyRepository.getActiveSurveyId() })
    assertTrue(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
    val opened = assertIs<OnboardingEvent.SurveyOpened>(f.lastEvent())
    assertEquals(remote.id, opened.surveyId)
    assertTrue(opened.notice.contains("Loaded survey \"${remote.title}\""))
  }

  @Test
  fun reset_clearsSessionAndSignsOut() {
    val f = Fixture()
    f.viewModel.signInWithGoogle()
    f.viewModel.acceptTermsOfService()
    f.viewModel.updateSearchQuery("coffee")

    f.viewModel.reset()

    assertFalse(f.uiState.isSignedIn)
    assertFalse(f.uiState.hasAcceptedTerms)
    assertEquals("", f.uiState.searchQuery)
  }
}
