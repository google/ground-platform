/*
 * Copyright 2026 The Collect One Authors.
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

package org.openforis.collectone.v2.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthException
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.data.datasource.device.AuthDataSource
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.SimulatedRole
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository
import org.openforis.collectone.v2.app.testing.takeReceivedItems
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.app.ui.navigation.DownloadSurveyEntryOrigin
import org.openforis.collectone.v2.app.ui.text.ErrorMessages

/**
 * Tests run outside [runNow] on purpose: the view model's [Dispatchers.Unconfined] scope only runs
 * nested coroutines after the current one yields, so a fixture built inside an Unconfined block
 * would not see its own state updates until the block ends.
 */
class OnboardingViewModelTest {
  /** A directory with one public survey and one survey only reachable by its share link. */
  private class FakeDirectory(
    private val otherPublicSurveys: List<SurveyPreviewItem> = emptyList(),
    /** Why every lookup fails, or null when lookups succeed. */
    private val failure: DomainError? = null,
  ) : SurveyDirectoryRepository {
    val publicSurvey =
      preview(id = "11111111-1111-4111-8111-111111111111", title = "Public mangroves")
    val linkedSurvey = preview(id = "22222222-2222-4222-8222-222222222222", title = "Linked plots")
    val downloaded = mutableListOf<SurveyPreviewItem>()

    override suspend fun searchPublicSurveys(query: String) = resultOf {
      (listOf(publicSurvey) + otherPublicSurveys).filter {
        it.title.contains(query, ignoreCase = true)
      }
    }

    override suspend fun lookUpSurvey(surveyId: String) = resultOf {
      listOf(publicSurvey, linkedSurvey).firstOrNull { it.id == surveyId }
    }

    private fun <T> resultOf(value: () -> T): DomainResult<T> =
      failure?.let { DomainResult.Failure(it) } ?: DomainResult.Success(value())

    override suspend fun downloadSurvey(survey: SurveyPreviewItem) {
      downloaded += survey
    }

    private fun preview(id: String, title: String) =
      SurveyPreviewItem(
        id = id,
        title = title,
        description = "",
        location = "",
        isDownloaded = false,
        entityCount = 0,
      )
  }

  private class Fixture(
    authClient: AuthClient = FakeAuthClient(),
    directory: SurveyDirectoryRepository? = null,
    val store: LocalStore = seededStore(),
    val hasCompletedInitialSync: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val savedState: SavedStateHandle = SavedStateHandle(),
  ) {
    val surveyRepository = SurveyRepositoryImpl(store)
    val connectivity = ConnectivityRepository()
    val viewModel =
      OnboardingViewModel(
        authRepository = AuthRepositoryImpl(AuthDataSource(authClient)),
        surveyRepository = surveyRepository,
        organizationRepository = OrganizationRepositoryImpl(store),
        connectivityRepository = connectivity,
        hasCompletedInitialSync = hasCompletedInitialSync,
        surveyDirectoryRepository = directory,
        directorySearchDelayMillis = 0L,
        savedState = savedState,
      )

    val uiState
      get() = viewModel.uiState.value
  }

  @Test
  fun signIn_thenAcceptTerms_navigatesToDownloadSurvey() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      assertFalse(f.uiState.isSignedIn)
      assertFalse(f.uiState.isSigningIn)

      f.viewModel.signInWithGoogle()
      assertTrue(f.uiState.isSignedIn)
      assertFalse(f.uiState.isSigningIn)
      assertTrue(f.uiState.profile.email.isNotBlank())
      assertEquals("ML", f.uiState.profile.initials)
      assertTrue(f.uiState.userOrganization.isNotBlank())
      assertEquals(OnboardingEvent.ShowScreen(AppScreen.TERMS_OF_SERVICE), expectMostRecentItem())
      assertFalse(f.uiState.termsCheckboxChecked)

      f.viewModel.setTermsChecked(true)
      assertTrue(f.uiState.termsCheckboxChecked)
      f.viewModel.setTermsChecked(false)
      assertFalse(f.uiState.termsCheckboxChecked)
      f.viewModel.setTermsChecked(true)
      f.viewModel.acceptTermsOfService()
      assertTrue(f.uiState.hasAcceptedTerms)
      assertFalse(f.uiState.termsCheckboxChecked)
      assertEquals(DownloadSurveyEntryOrigin.AFTER_TOS, f.uiState.entryOrigin)
      assertEquals(OnboardingEvent.ShowScreen(AppScreen.DOWNLOAD_SURVEY), expectMostRecentItem())

      // Signing in again after accepting terms skips the Terms of Service screen.
      f.viewModel.signOut()
      assertFalse(f.uiState.isSignedIn)
    }
  }

  @Test
  fun termsCheckbox_defaultsUnchecked_andResetsOnDeclineAndSignOut() {
    val f = Fixture()
    assertFalse(f.uiState.termsCheckboxChecked)

    f.viewModel.signInWithGoogle()
    assertFalse(f.uiState.termsCheckboxChecked)

    f.viewModel.setTermsChecked(true)
    assertTrue(f.uiState.termsCheckboxChecked)

    f.viewModel.declineTermsOfService()
    assertFalse(f.uiState.termsCheckboxChecked)

    f.viewModel.signInWithGoogle()
    assertFalse(f.uiState.termsCheckboxChecked)
  }

  /** An [AuthClient] whose sign-in always fails with [error]. */
  private class FailingAuthClient(private val error: AuthException) : AuthClient {
    override val currentUser = MutableStateFlow<AuthenticatedCollectorProfile?>(null)

    override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile = throw error

    override suspend fun signOut() {}

    override suspend fun idToken(forceRefresh: Boolean): String? = null
  }

  @Test
  fun failedSignIn_staysSignedOut_andExplainsWhy() = runTest {
    val f = Fixture(FailingAuthClient(AuthException("auth/network-request-failed: offline")))
    f.viewModel.events.test {
      f.viewModel.signInWithGoogle()

      assertFalse(f.uiState.isSignedIn)
      assertFalse(f.uiState.isSigningIn)
      assertEquals(
        OnboardingEvent.Failure(ErrorMessages.signInFailed(DomainError.Network)),
        expectMostRecentItem(),
      )
    }
  }

  @Test
  fun failedSignIn_forAnUnrecognizedReason_givesNoRawErrorText() = runTest {
    val error = AuthException("auth/popup-blocked")
    val f = Fixture(FailingAuthClient(error))
    f.viewModel.events.test {
      f.viewModel.signInWithGoogle()

      assertEquals(
        OnboardingEvent.Failure(ErrorMessages.signInFailed(DomainError.Unknown(error))),
        expectMostRecentItem(),
      )
    }
  }

  @Test
  fun devBuilds_askWhichRoleToSimulate_afterSigningIn() {
    val client = FakeAuthClient(supportsDevRoles = true)
    val f = Fixture(client)

    f.viewModel.signInWithGoogle()
    assertTrue(client.isSignedIn())
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

    // The next sign-in preselects the role used last, and dismissing signs out.
    f.viewModel.signOut()
    f.viewModel.signInWithGoogle()
    assertEquals(SimulatedRole.REGULAR_USER, f.uiState.devSignIn?.selectedRole)
    f.viewModel.dismissDevSignIn()
    assertNull(f.uiState.devSignIn)
    assertFalse(f.uiState.isSignedIn)
    assertFalse(client.isSignedIn())
  }

  @Test
  fun cancelledSignIn_isNotReported() = runTest {
    val f = Fixture(FailingAuthClient(AuthException("auth/popup-closed-by-user", cancelled = true)))

    f.viewModel.events.test {
      f.viewModel.signInWithGoogle()

      assertFalse(f.uiState.isSignedIn)
      assertFalse(f.uiState.isSigningIn)
      val events = takeReceivedItems()
      assertTrue(events.none { it is OnboardingEvent.Notice || it is OnboardingEvent.Failure })
    }
  }

  @Test
  fun declineTerms_signsOutAndReturnsToSignIn() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      f.viewModel.signInWithGoogle()
      f.viewModel.declineTermsOfService()

      assertFalse(f.uiState.isSignedIn)
      assertFalse(f.uiState.hasAcceptedTerms)
      val events = takeReceivedItems()
      assertTrue(OnboardingEvent.SignedOut in events)
      assertEquals(OnboardingEvent.ShowScreen(AppScreen.SIGN_IN), events.last())
    }
  }

  @Test
  fun searchQuery_filtersByTitleLocationAndOrganization() {
    val f = Fixture()
    val all = f.uiState.surveys
    assertTrue(all.isNotEmpty())
    assertEquals(all, f.uiState.filteredSurveys)

    f.viewModel.updateSearchQuery("mangrove")
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
  fun searchQuery_findsSurveysByTheCoordinatesOfTheirAreaCentroid() {
    val f = Fixture()

    // The Arusha sample survey's area is centered on 3.38°S, 36.68°E.
    f.viewModel.updateSearchQuery("36.6800")

    assertEquals(listOf("Arusha, Tanzania"), f.uiState.filteredSurveys.map { it.location })
  }

  @Test
  fun downloadAndRemove_updateRepositoryAndEmitNotices() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      val remote = f.uiState.surveys.first { !it.isDownloaded }
      val before = f.uiState.downloadedSurveyCount

      f.viewModel.downloadSurvey(remote.id)
      assertTrue(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
      assertEquals(before + 1, f.uiState.downloadedSurveyCount)
      val downloaded = assertIs<OnboardingEvent.Notice>(expectMostRecentItem())
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
      val removed = assertIs<OnboardingEvent.Notice>(expectMostRecentItem())
      assertTrue(removed.message.contains("Removed offline copy"))
    }
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
  fun backFromDownloadSurvey_dependsOnEntryOrigin() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
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
      assertEquals(OnboardingEvent.ShowScreen(AppScreen.SIGN_IN), expectMostRecentItem())

      // Reached from the Survey list: Back simply returns there.
      f.viewModel.signInWithGoogle()
      f.viewModel.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)
      assertTrue(f.uiState.isDownloadSurveyAccessedFromSurveyList)
      f.viewModel.navigateBackFromDownloadSurvey()
      assertFalse(f.uiState.isSignOutPromptOpen)
      assertEquals(OnboardingEvent.ReturnToSurveyList, expectMostRecentItem())
      assertTrue(f.uiState.isSignedIn)
    }
  }

  @Test
  fun openSurvey_activatesSurveyAndEmitsSurveyOpened() = runTest {
    val f = Fixture()
    f.viewModel.events.test {
      val remote = f.uiState.surveys.first { !it.isDownloaded }

      f.viewModel.openSurvey(remote.id)

      assertEquals(remote.id, runNow { f.surveyRepository.getActiveSurveyId() })
      assertTrue(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
      val opened = assertIs<OnboardingEvent.SurveyOpened>(expectMostRecentItem())
      assertEquals(remote.id, opened.surveyId)
      assertTrue(opened.notice.contains("Loaded survey \"${remote.title}\""))
    }
  }

  @Test
  fun anotherAccountSignedIn_hasNotAcceptedTheTerms() {
    val authClient = FakeAuthClient()
    val f = Fixture(authClient)
    f.viewModel.signInWithGoogle()
    f.viewModel.acceptTermsOfService()

    // A simulated role switch signs in as another account without signing out.
    authClient.signInAs(displayName = "Robin Okafor", email = "robin.okafor@example.org")

    assertTrue(f.uiState.isSignedIn)
    assertFalse(f.uiState.hasAcceptedTerms)
  }

  @Test
  fun theAccountThatAccepted_keepsItsAcceptanceAfterAnotherAccount() {
    val authClient = FakeAuthClient()
    val f = Fixture(authClient)
    f.viewModel.signInWithGoogle()
    f.viewModel.acceptTermsOfService()
    authClient.signInAs(displayName = "Robin Okafor", email = "robin.okafor@example.org")

    authClient.signInAs()

    assertTrue(f.uiState.hasAcceptedTerms)
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
  fun searchQuery_findsPublicSurveysTheListDoesNotInclude() {
    val directory = FakeDirectory()
    val f = Fixture(directory = directory)

    f.viewModel.updateSearchQuery("mangroves")
    assertEquals(listOf(directory.publicSurvey), f.uiState.directoryResults)

    f.viewModel.clearSearchQuery()
    assertTrue(f.uiState.directoryResults.isEmpty())
  }

  @Test
  fun pastedShareLink_findsTheLinkedSurvey_andDownloadsIt() {
    val directory = FakeDirectory()
    val f = Fixture(directory = directory)

    f.viewModel.updateSearchQuery(SurveyAccess.surveyUrl(directory.linkedSurvey.id))
    assertEquals(listOf(directory.linkedSurvey), f.uiState.directoryResults)

    f.viewModel.downloadDirectorySurvey(directory.linkedSurvey.id)
    assertEquals(listOf(directory.linkedSurvey), directory.downloaded)
  }

  @Test
  fun failedDirectoryLookup_showsNoExtraResults() {
    val directory = FakeDirectory(failure = DomainError.Network)
    val f = Fixture(directory = directory)

    f.viewModel.updateSearchQuery("mangroves")

    assertTrue(f.uiState.directoryResults.isEmpty())
    assertFalse(f.uiState.isSearchingDirectory)
  }

  @Test
  fun directoryResults_leaveOutSurveysAlreadyListed() {
    val listed = Fixture().uiState.surveys.first()
    val f = Fixture(directory = FakeDirectory(otherPublicSurveys = listOf(listed)))

    f.viewModel.updateSearchQuery(listed.title)
    assertTrue(f.uiState.directoryResults.none { it.id == listed.id })
  }

  @Test
  fun goingOffline_reportsTheDeviceOffline() {
    val f = Fixture()

    f.connectivity.setOnline(false)

    assertFalse(f.uiState.isOnline)
  }

  @Test
  fun comingBackOnline_reportsTheDeviceOnline() {
    val f = Fixture()
    f.connectivity.setOnline(false)

    f.connectivity.setOnline(true)

    assertTrue(f.uiState.isOnline)
  }

  @Test
  fun offline_signInAndDownloads_explainWhyInsteadOfTrying() = runTest {
    val directory = FakeDirectory()
    val f = Fixture(directory = directory)
    f.viewModel.updateSearchQuery("mangroves")
    val remote = f.uiState.surveys.first { !it.isDownloaded }
    f.connectivity.setOnline(false)

    f.viewModel.events.test {
      f.viewModel.signInWithGoogle()
      assertTrue(assertIs<OnboardingEvent.Notice>(awaitItem()).message.startsWith("Cannot sign in"))
      assertFalse(f.uiState.isSignedIn)

      f.viewModel.downloadSurvey(remote.id)
      f.viewModel.openSurvey(remote.id)
      f.viewModel.downloadDirectorySurvey(directory.publicSurvey.id)
      repeat(3) {
        val notice = assertIs<OnboardingEvent.Notice>(awaitItem())
        assertTrue(notice.message.startsWith("Cannot download survey while offline"))
      }
      expectNoEvents()
    }
    assertFalse(f.uiState.surveys.first { it.id == remote.id }.isDownloaded)
    assertTrue(directory.downloaded.isEmpty())
  }

  @Test
  fun offline_theWebConsoleStillSignsIn() {
    val f = Fixture()
    f.connectivity.setOnline(false)

    f.viewModel.signInWithGoogle(ignoreOffline = true)

    assertTrue(f.uiState.isSignedIn)
  }

  @Test
  fun signingInAgain_afterAcceptingTheTerms_goesStraightToDownloadSurvey() = runTest {
    val f = Fixture()
    f.viewModel.signInWithGoogle()
    f.viewModel.acceptTermsOfService()
    f.viewModel.setEntryOrigin(DownloadSurveyEntryOrigin.SURVEY_LIST)

    f.viewModel.events.test {
      f.viewModel.signInWithGoogle()
      assertEquals(OnboardingEvent.ShowScreen(AppScreen.DOWNLOAD_SURVEY), expectMostRecentItem())
    }
    assertEquals(DownloadSurveyEntryOrigin.AFTER_TOS, f.uiState.entryOrigin)
  }

  @Test
  fun openSurvey_onADownloadedSurvey_opensItOffline() = runTest {
    val f = Fixture()
    val downloaded = f.uiState.surveys.first { it.isDownloaded }
    f.connectivity.setOnline(false)

    f.viewModel.events.test {
      f.viewModel.openSurvey(downloaded.id)
      val opened = assertIs<OnboardingEvent.SurveyOpened>(expectMostRecentItem())
      assertEquals(downloaded.id, opened.surveyId)
    }
    assertEquals(downloaded.id, runNow { f.surveyRepository.getActiveSurveyId() })
  }

  @Test
  fun downloadDirectorySurvey_ignoresSurveysNotAmongTheResults() {
    val directory = FakeDirectory()
    val f = Fixture(directory = directory)

    f.viewModel.downloadDirectorySurvey(directory.linkedSurvey.id)

    assertTrue(directory.downloaded.isEmpty())
  }

  @Test
  fun savedState_restoresAndUpdatesSearchQuery() {
    val savedState = SavedStateHandle()
    val first = Fixture(savedState = savedState)
    first.viewModel.updateSearchQuery("mangrove")
    assertEquals("mangrove", savedState["searchQuery"])

    val restored = Fixture(savedState = savedState)
    assertEquals("mangrove", restored.uiState.searchQuery)
    assertTrue(
      restored.uiState.filteredSurveys.all { it.title.contains("Mangrove", ignoreCase = true) }
    )
  }

  @Test
  fun isLoadingSurveys_isTrueOnlyBeforeInitialSyncCompletes_whenSurveysAreEmpty() {
    val synced = MutableStateFlow(false)
    val f = Fixture(store = InMemoryLocalStore(), hasCompletedInitialSync = synced)
    assertTrue(f.uiState.isLoadingSurveys)

    synced.value = true
    assertFalse(f.uiState.isLoadingSurveys)
  }
}
