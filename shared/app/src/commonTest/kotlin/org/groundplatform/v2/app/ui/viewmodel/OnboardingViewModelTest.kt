/*
 * Copyright 2026 The Ground Authors.
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

package org.groundplatform.v2.app.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.client.auth.AuthClient
import org.groundplatform.v2.app.client.auth.AuthException
import org.groundplatform.v2.app.client.auth.AuthenticatedCollectorProfile
import org.groundplatform.v2.app.client.auth.FakeAuthClient
import org.groundplatform.v2.app.data.datasource.device.AuthDataSource
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.seededStore
import org.groundplatform.v2.app.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.app.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.app.domain.model.SimulatedRole
import org.groundplatform.v2.app.ui.state.OnboardingEvent

/**
 * Tests run outside [runNow] on purpose: the view model's [Dispatchers.Unconfined] scope only runs
 * nested coroutines after the current one yields, so a fixture built inside an Unconfined block
 * would not see its own state updates until the block ends.
 */
class OnboardingViewModelTest {
  private class Fixture(authClient: AuthClient = FakeAuthClient()) {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val surveyRepository = SurveyRepositoryImpl(store)
    val viewModel =
      OnboardingViewModel(
        authRepository = AuthRepositoryImpl(AuthDataSource(authClient)),
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
    assertFalse(f.uiState.isSigningIn)

    f.viewModel.signInWithGoogle()
    assertTrue(f.uiState.isSignedIn)
    assertFalse(f.uiState.isSigningIn)
    assertTrue(f.uiState.profile.email.isNotBlank())
    assertEquals("ML", f.uiState.profile.initials)
    assertTrue(f.uiState.userOrganization.isNotBlank())
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

  /** An [AuthClient] whose sign-in always fails with [error]. */
  private class FailingAuthClient(private val error: AuthException) : AuthClient {
    override val currentUser = MutableStateFlow<AuthenticatedCollectorProfile?>(null)

    override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile = throw error

    override suspend fun signOut() {}

    override suspend fun idToken(forceRefresh: Boolean): String? = null
  }

  @Test
  fun failedSignIn_staysSignedOut_andExplainsWhy() {
    val f = Fixture(FailingAuthClient(AuthException("auth/network-request-failed: offline")))

    f.viewModel.signInWithGoogle()

    assertFalse(f.uiState.isSignedIn)
    assertFalse(f.uiState.isSigningIn)
    val notice = assertIs<OnboardingEvent.Notice>(f.lastEvent())
    assertTrue("auth/network-request-failed" in notice.message, notice.message)
  }

  @Test
  fun devBuilds_askWhichRoleToSimulate_beforeSigningIn() {
    val client = FakeAuthClient(supportsDevRoles = true)
    val f = Fixture(client)

    f.viewModel.signInWithGoogle()
    assertFalse(f.uiState.isSignedIn)
    val dialog = assertNotNull(f.uiState.devSignIn)
    assertEquals(SimulatedRole.ADMINISTRATOR, dialog.selectedRole)
    assertFalse(dialog.usesRealAccounts)

    f.viewModel.selectSimulatedRole(SimulatedRole.REGULAR_USER)
    assertEquals(SimulatedRole.REGULAR_USER, f.uiState.devSignIn?.selectedRole)
    f.viewModel.confirmDevSignIn()

    assertNull(f.uiState.devSignIn)
    assertTrue(f.uiState.isSignedIn)
    assertEquals("ima.user@example.org", f.uiState.profile.email)
    assertEquals("user", client.requestHeaders()["x-dev-role"])

    // The next sign-in preselects the role used last.
    f.viewModel.signOut()
    f.viewModel.signInWithGoogle()
    assertEquals(SimulatedRole.REGULAR_USER, f.uiState.devSignIn?.selectedRole)
    f.viewModel.dismissDevSignIn()
    assertNull(f.uiState.devSignIn)
    assertFalse(f.uiState.isSignedIn)
  }

  @Test
  fun cancelledSignIn_isNotReported() {
    val f = Fixture(FailingAuthClient(AuthException("auth/popup-closed-by-user", cancelled = true)))

    f.viewModel.signInWithGoogle()

    assertFalse(f.uiState.isSignedIn)
    assertFalse(f.uiState.isSigningIn)
    assertTrue(f.events.none { it is OnboardingEvent.Notice })
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

  @Test
  fun downloadSurvey_promptsForOfflineImagery_andConfirmOrSkipUpdatesState() {
    val f = Fixture()
    val remote = f.uiState.surveys.first { !it.isDownloaded }

    f.viewModel.downloadSurvey(remote.id)
    assertEquals(remote.id, f.uiState.pendingOfflineImagerySurveyId)
    assertEquals(remote.id, f.uiState.pendingOfflineImagerySurvey?.id)

    f.viewModel.confirmOfflineImageryDownload()
    assertNull(f.uiState.pendingOfflineImagerySurveyId)
    assertTrue(remote.id in f.uiState.offlineImageryDownloadedSurveyIds)
    val imageryNotice = assertIs<OnboardingEvent.Notice>(f.lastEvent())
    assertTrue(imageryNotice.message.contains("Downloaded offline imagery for \"${remote.title}\""))

    f.viewModel.promptRemoveDownloadedSurvey(remote.id)
    f.viewModel.confirmRemoveDownloadedSurvey()
    assertFalse(remote.id in f.uiState.offlineImageryDownloadedSurveyIds)

    f.viewModel.downloadSurvey(remote.id)
    assertEquals(remote.id, f.uiState.pendingOfflineImagerySurveyId)
    f.viewModel.skipOfflineImageryDownload()
    assertNull(f.uiState.pendingOfflineImagerySurveyId)
    assertFalse(remote.id in f.uiState.offlineImageryDownloadedSurveyIds)
  }
}
