/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SimulatedRole
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyStats
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.ui.navigation.AppScreen
import org.openforis.collectone.v2.app.ui.navigation.DownloadSurveyEntryOrigin
import org.openforis.collectone.v2.app.ui.text.ErrorMessages
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.saveInto
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn

/** User intents of the onboarding screens. Implemented by [OnboardingViewModel]. */
interface OnboardingActions {
  /** Signs in; in development builds, opens the development sign-in dialog after signing in. */
  fun signInWithGoogle()

  /** Picks the role the development sign-in dialog will simulate. */
  fun selectSimulatedRole(role: SimulatedRole) {}

  /** Closes the development sign-in dialog and applies the selected role. */
  fun confirmDevSignIn() {}

  fun dismissDevSignIn() {}

  fun setTermsChecked(checked: Boolean)

  fun acceptTermsOfService()

  fun declineTermsOfService()

  /**
   * Back from the Download survey screen: returns to the Survey list or asks before signing out.
   */
  fun navigateBackFromDownloadSurvey()

  fun confirmSignOut()

  fun dismissSignOutPrompt()

  fun updateSearchQuery(query: String)

  fun clearSearchQuery()

  /** Marks [surveyId] as downloaded for offline use. */
  fun downloadSurvey(surveyId: String)

  /**
   * Downloads [surveyId] from [OnboardingUiState.directoryResults] (a public or link-shared survey
   * the survey list doesn't include).
   */
  fun downloadDirectorySurvey(surveyId: String) {}

  /**
   * Asks before removing [surveyId]'s offline copy; downloads it instead if it isn't downloaded.
   */
  fun promptRemoveDownloadedSurvey(surveyId: String)

  fun confirmRemoveDownloadedSurvey()

  fun dismissRemoveDownloadedSurvey()

  /** Downloads [surveyId] if needed, makes it active, and opens the Main Survey UI. */
  fun openSurvey(surveyId: String)
}

/**
 * ViewModel for the onboarding flow (Sign In → Terms of Service → Download survey).
 *
 * Reads the account from [AuthRepository] and the survey directory from [SurveyRepository] and
 * [OrganizationRepository]; owns the flow's session state (terms checkbox, search query, prompts).
 * Screen changes and notices that outlive this flow are published as [events] for the app shell.
 */
class OnboardingViewModel(
  private val authRepository: AuthRepository,
  private val surveyRepository: SurveyRepository,
  private val organizationRepository: OrganizationRepository,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  private val connectivityRepository: ConnectivityRepository,
  /** Whether the first sync from the user data service has finished. */
  private val hasCompletedInitialSync: StateFlow<Boolean>,
  /** Finds public and link-shared surveys; null when not connected to the user data service. */
  private val surveyDirectoryRepository: SurveyDirectoryRepository? = null,
  /** How long [updateSearchQuery] waits for typing to pause before looking up surveys. */
  private val directorySearchDelayMillis: Long = 400L,
  /** Keeps the survey search query across Android process death. */
  private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(viewModelScopeOn(dispatcher)), OnboardingActions {
  private data class Session(
    /**
     * The account that accepted the Terms of Service in this session, until [AuthRepository] has
     * stored it; the acceptance only applies to that account.
     */
    val termsAcceptedEmail: String? = null,
    val isSigningIn: Boolean = false,
    /**
     * Holds [OnboardingUiState.isSignedIn] `false` while a development build has authenticated the
     * underlying [AuthSession] but hasn't finished opening (or signing out from) the simulated-role
     * picker dialog, preventing premature shell navigation.
     */
    val isAwaitingDevRole: Boolean = false,
    val termsCheckboxChecked: Boolean = false,
    val entryOrigin: DownloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
    val isSignOutPromptOpen: Boolean = false,
    val searchQuery: String = "",
    val pendingRemovalSurveyId: String? = null,
    val devSignIn: DevSignInUiState? = null,
  )

  /** Whether the survey list has synced once, and whether it can sync now. */
  private data class SyncProgress(val hasCompletedInitialSync: Boolean, val isOnline: Boolean)

  private val directorySearch =
    SurveyDirectorySearch(
      viewModelScope,
      surveyDirectoryRepository,
      connectivityRepository,
      directorySearchDelayMillis,
    )

  private val session = MutableStateFlow(Session(searchQuery = savedState[SEARCH_QUERY_KEY] ?: ""))

  init {
    saveInto(savedState, SEARCH_QUERY_KEY, session) { it.searchQuery }
  }

  private val _events =
    MutableSharedFlow<OnboardingEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Navigation and notice outcomes, in order, for the app shell to apply. */
  val events: Flow<OnboardingEvent> = _events.asSharedFlow()

  val uiState: StateFlow<OnboardingUiState> =
    combine(
        authRepository.observeSession(),
        surveyRepository.observeSurveys(),
        organizationRepository.observeOrganizations(),
        combine(hasCompletedInitialSync, connectivityRepository.observeIsOnline(), ::SyncProgress),
        combine(session, directorySearch.state, ::Pair),
      ) { auth, surveys, organizations, syncProgress, (session, directory) ->
        buildUiState(auth, surveys, organizations, syncProgress, session, directory)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingUiState())

  private fun buildUiState(
    auth: AuthSession,
    surveys: List<SurveyPreviewItem>,
    organizations: List<Organization>,
    syncProgress: SyncProgress,
    session: Session,
    directory: SurveyDirectorySearch.State,
  ): OnboardingUiState {
    val organizationNames = organizations.associate { it.id to it.name }
    val userOrganization =
      userOrganizationLine(organizations, auth.profile.email, auth.profile.organizationName)
    return OnboardingUiState(
      isSignedIn = auth.isSignedIn && !session.isAwaitingDevRole && session.devSignIn == null,
      isSigningIn = session.isSigningIn,
      profile = auth.profile,
      hasAcceptedTerms =
        auth.hasAcceptedTerms ||
          (auth.isSignedIn && session.termsAcceptedEmail == auth.profile.email),
      termsCheckboxChecked = session.termsCheckboxChecked,
      entryOrigin = session.entryOrigin,
      isSignOutPromptOpen = session.isSignOutPromptOpen,
      searchQuery = session.searchQuery,
      isLoadingSurveys = !syncProgress.hasCompletedInitialSync && surveys.isEmpty(),
      isOnline = syncProgress.isOnline,
      surveys = surveys,
      filteredSurveys = surveys.matchingQuery(session.searchQuery, organizationNames),
      downloadedSurveyCount = surveys.count { it.isDownloaded },
      organizationNames = organizationNames,
      userOrganization = userOrganization,
      pendingRemovalSurveyId = session.pendingRemovalSurveyId,
      devSignIn = session.devSignIn,
      directoryResults =
        surveys
          .map { it.id }
          .toSet()
          .let { listed -> directory.results.filter { it.id !in listed } },
      isSearchingDirectory = directory.isSearching,
    )
  }

  /**
   * Records how the Download survey screen is being entered (see [OnboardingUiState.entryOrigin]).
   */
  fun setEntryOrigin(origin: DownloadSurveyEntryOrigin) {
    session.update { it.copy(entryOrigin = origin, isSignOutPromptOpen = false) }
  }

  /** Returns the flow to its initial state (used by the workbench's Reset). */
  fun reset() {
    session.value = Session()
    directorySearch.clearResults()
    viewModelScope.launch { authRepository.signOut() }
  }

  /** Signs out without navigating (the caller decides where to go next). */
  fun signOut() {
    session.update {
      it.copy(
        termsAcceptedEmail = null,
        termsCheckboxChecked = false,
        isSignOutPromptOpen = false,
        isAwaitingDevRole = false,
        devSignIn = null,
      )
    }
    viewModelScope.launch { authRepository.signOut() }
  }

  override fun signInWithGoogle() = signInWithGoogle(ignoreOffline = false)

  /**
   * Starts Google sign-in. Mobile callers leave [ignoreOffline] `false` so offline taps fail fast
   * with a notice; the web console passes `true` because it ignores device connectivity.
   */
  fun signInWithGoogle(ignoreOffline: Boolean) {
    if (!ignoreOffline && !connectivityRepository.isOnline()) {
      emit(
        OnboardingEvent.Notice(
          "Cannot sign in while offline. Connect to the internet and try again."
        )
      )
      return
    }
    if (session.value.isSigningIn) return
    startSignIn()
  }

  override fun selectSimulatedRole(role: SimulatedRole) {
    session.update { s -> s.copy(devSignIn = s.devSignIn?.copy(selectedRole = role)) }
  }

  override fun confirmDevSignIn() {
    val dialog = session.value.devSignIn ?: return
    authRepository.setSimulatedRole(dialog.selectedRole)
    session.update { it.copy(devSignIn = null, isAwaitingDevRole = false) }
    completeSignInNavigation()
  }

  override fun dismissDevSignIn() {
    if (session.value.devSignIn == null) return
    session.update { it.copy(devSignIn = null, isAwaitingDevRole = true) }
    viewModelScope.launch {
      try {
        authRepository.signOut()
      } finally {
        session.update { it.copy(isAwaitingDevRole = false) }
      }
    }
  }

  private fun startSignIn() {
    if (session.value.isSigningIn) return
    val mode = authRepository.signInMode()
    session.update { it.copy(isSigningIn = true, isAwaitingDevRole = mode.simulatesRoles) }
    viewModelScope.launch {
      try {
        val signIn = authRepository.signInWithGoogle(role = null)
        if (signIn is DomainResult.Failure) {
          // A sign-in window the user closed isn't an error.
          if (signIn.error != DomainError.Cancelled) {
            emit(OnboardingEvent.Failure(ErrorMessages.signInFailed(signIn.error)))
          }
          return@launch
        }
        val latestMode = authRepository.signInMode()
        if (latestMode.simulatesRoles) {
          session.update {
            it.copy(
              isSigningIn = false,
              isAwaitingDevRole = false,
              devSignIn = DevSignInUiState(latestMode.lastRole, latestMode.usesRealAccounts),
            )
          }
          return@launch
        }
        completeSignInNavigation()
      } finally {
        session.update { it.copy(isSigningIn = false, isAwaitingDevRole = false) }
      }
    }
  }

  /**
   * Routes to Download survey when the signed-in account has already accepted the Terms of Service
   * (either persisted in [AuthRepository] or recorded in [Session.termsAcceptedEmail] while that
   * write is still in flight), and to Terms of Service otherwise.
   */
  private fun completeSignInNavigation() {
    val next =
      if (
        session.value.termsAcceptedEmail == uiState.value.profile.email ||
          uiState.value.hasAcceptedTerms
      ) {
        session.update { it.copy(entryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS) }
        AppScreen.DOWNLOAD_SURVEY
      } else {
        session.update { it.copy(termsCheckboxChecked = false) }
        AppScreen.TERMS_OF_SERVICE
      }
    session.update { it.copy(isSignOutPromptOpen = false) }
    emit(OnboardingEvent.ShowScreen(next))
  }

  override fun setTermsChecked(checked: Boolean) {
    session.update { it.copy(termsCheckboxChecked = checked) }
  }

  override fun acceptTermsOfService() {
    session.update {
      it.copy(
        termsCheckboxChecked = false,
        termsAcceptedEmail = uiState.value.profile.email,
        entryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
        isSignOutPromptOpen = false,
      )
    }
    emit(OnboardingEvent.ShowScreen(AppScreen.DOWNLOAD_SURVEY))
    // Kept on the device for this account until signing out, so relaunching doesn't ask again.
    viewModelScope.launch { authRepository.acceptTerms() }
  }

  override fun declineTermsOfService() {
    signOut()
    emit(OnboardingEvent.SignedOut)
    emit(OnboardingEvent.ShowScreen(AppScreen.SIGN_IN))
  }

  override fun navigateBackFromDownloadSurvey() {
    if (session.value.entryOrigin == DownloadSurveyEntryOrigin.SURVEY_LIST) {
      session.update { it.copy(isSignOutPromptOpen = false) }
      emit(OnboardingEvent.ReturnToSurveyList)
    } else {
      session.update { it.copy(isSignOutPromptOpen = true) }
    }
  }

  override fun confirmSignOut() {
    signOut()
    emit(OnboardingEvent.SignedOut)
    emit(OnboardingEvent.ShowScreen(AppScreen.SIGN_IN))
  }

  override fun dismissSignOutPrompt() {
    session.update { it.copy(isSignOutPromptOpen = false) }
  }

  override fun updateSearchQuery(query: String) {
    session.update { it.copy(searchQuery = query) }
    directorySearch.search(query)
  }

  override fun downloadDirectorySurvey(surveyId: String) {
    val survey = directorySearch.result(surveyId) ?: return
    val directory = surveyDirectoryRepository ?: return
    if (!connectivityRepository.isOnline()) {
      emit(
        OnboardingEvent.Notice(
          "Cannot download survey while offline. Connect to the internet and try again."
        )
      )
      return
    }
    viewModelScope.launch {
      directory.downloadSurvey(survey)
      emit(OnboardingEvent.Notice("Downloading \"${survey.title}\" for offline use."))
    }
  }

  override fun clearSearchQuery() = updateSearchQuery("")

  override fun downloadSurvey(surveyId: String) {
    if (!connectivityRepository.isOnline()) {
      emit(
        OnboardingEvent.Notice(
          "Cannot download survey while offline. Connect to the internet and try again."
        )
      )
      return
    }
    viewModelScope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = true) ?: return@launch
      emit(OnboardingEvent.Notice("Downloaded \"${survey.title}\" for offline use."))
    }
  }

  override fun promptRemoveDownloadedSurvey(surveyId: String) {
    val survey = uiState.value.surveys.firstOrNull { it.id == surveyId }
    if (survey != null && survey.isDownloaded) {
      session.update { it.copy(pendingRemovalSurveyId = surveyId) }
    } else {
      downloadSurvey(surveyId)
    }
  }

  override fun confirmRemoveDownloadedSurvey() {
    val surveyId = session.value.pendingRemovalSurveyId ?: return
    session.update { it.copy(pendingRemovalSurveyId = null) }
    viewModelScope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = false) ?: return@launch
      emit(OnboardingEvent.Notice("Removed offline copy of \"${survey.title}\"."))
    }
  }

  override fun dismissRemoveDownloadedSurvey() {
    session.update { it.copy(pendingRemovalSurveyId = null) }
  }

  /**
   * Opens [surveyId] in the Main Survey UI. Already-downloaded surveys open offline immediately;
   * undownloaded ones require connectivity, mark the survey downloaded first, and snapshot its
   * entity and submission counts for the launch notice.
   */
  override fun openSurvey(surveyId: String) {
    val existing = uiState.value.surveys.firstOrNull { it.id == surveyId }
    if (existing != null && !existing.isDownloaded && !connectivityRepository.isOnline()) {
      emit(
        OnboardingEvent.Notice(
          "Cannot download survey while offline. Connect to the internet and try again."
        )
      )
      return
    }
    viewModelScope.launch {
      val survey =
        if (existing?.isDownloaded == true) {
          existing
        } else {
          surveyRepository.setSurveyDownloaded(surveyId, downloaded = true) ?: return@launch
        }
      surveyRepository.setActiveSurveyId(surveyId)
      val stats = surveyRepository.observeSurveyStats().first()[surveyId] ?: SurveyStats(0, 0)
      emit(
        OnboardingEvent.SurveyOpened(
          surveyId = surveyId,
          notice =
            "Loaded survey \"${survey.title}\" (${stats.entityCount} entities, " +
              "${stats.submissionCount} preloaded submissions).",
        )
      )
    }
  }

  private fun emit(event: OnboardingEvent) {
    _events.tryEmit(event)
  }
}

/** [SavedStateHandle] key of the survey search query. */
private const val SEARCH_QUERY_KEY = "searchQuery"
