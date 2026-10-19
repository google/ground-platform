/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.domain.model.AuthSession
import org.groundplatform.v2.app.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.SignInException
import org.groundplatform.v2.app.domain.model.SimulatedRole
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyStats
import org.groundplatform.v2.app.domain.model.editor.SurveyAccess
import org.groundplatform.v2.app.domain.repository.AuthRepository
import org.groundplatform.v2.app.domain.repository.ConnectivityRepository
import org.groundplatform.v2.app.domain.repository.OrganizationRepository
import org.groundplatform.v2.app.domain.repository.SurveyDirectoryRepository
import org.groundplatform.v2.app.domain.repository.SurveyRepository
import org.groundplatform.v2.app.ui.state.DevSignInUiState
import org.groundplatform.v2.app.ui.state.OnboardingEvent
import org.groundplatform.v2.app.ui.state.OnboardingUiState

/** User intents of the onboarding screens. Implemented by [OnboardingViewModel]. */
interface OnboardingActions {
  /** Signs in; in development builds, opens the development sign-in dialog first. */
  fun signInWithGoogle()

  /** Picks the role the development sign-in dialog will sign in as. */
  fun selectSimulatedRole(role: SimulatedRole) {}

  /** Closes the development sign-in dialog and signs in with the selected role. */
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

  /** Prompts the user to download offline basemap imagery for [surveyId]. */
  fun promptOfflineImageryDownload(surveyId: String) {}

  /** Downloads offline basemap imagery for the pending survey. */
  fun confirmOfflineImageryDownload() {}

  /** Skips downloading offline basemap imagery for the pending survey. */
  fun skipOfflineImageryDownload() {}

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
  private val scope: CoroutineScope,
  private val connectivityRepository: ConnectivityRepository = ConnectivityRepository(),
  private val hasCompletedInitialSync: StateFlow<Boolean> = MutableStateFlow(true),
  /** Finds public and link-shared surveys; null when not connected to the user data service. */
  private val surveyDirectoryRepository: SurveyDirectoryRepository? = null,
  /** How long [updateSearchQuery] waits for typing to pause before looking up surveys. */
  private val directorySearchDelayMillis: Long = 400L,
) : OnboardingActions {
  private data class Session(
    val hasAcceptedTerms: Boolean = false,
    val isSigningIn: Boolean = false,
    val termsCheckboxChecked: Boolean = true,
    val entryOrigin: DownloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
    val isSignOutPromptOpen: Boolean = false,
    val searchQuery: String = "",
    val pendingRemovalSurveyId: String? = null,
    val pendingOfflineImagerySurveyId: String? = null,
    val offlineImageryDownloadedSurveyIds: Set<String> = emptySet(),
    val devSignIn: DevSignInUiState? = null,
    val directoryResults: List<SurveyPreviewItem> = emptyList(),
    val isSearchingDirectory: Boolean = false,
  )

  private var directorySearch: Job? = null

  private val session = MutableStateFlow(Session())

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
        hasCompletedInitialSync,
        session,
        ::buildUiState,
      )
      .stateIn(scope, SharingStarted.Eagerly, OnboardingUiState())

  private fun buildUiState(
    auth: AuthSession,
    surveys: List<SurveyPreviewItem>,
    organizations: List<Organization>,
    initialSyncDone: Boolean,
    session: Session,
  ): OnboardingUiState {
    val organizationNames = organizations.associate { it.id to it.name }
    val userOrganization =
      organizations
        .filter { it.isMember(auth.profile.email) }
        .joinToString(" • ") { it.name }
        .ifBlank { auth.profile.organizationName }
    return OnboardingUiState(
      isSignedIn = auth.isSignedIn,
      isSigningIn = session.isSigningIn,
      profile = auth.profile,
      hasAcceptedTerms = session.hasAcceptedTerms,
      termsCheckboxChecked = session.termsCheckboxChecked,
      entryOrigin = session.entryOrigin,
      isSignOutPromptOpen = session.isSignOutPromptOpen,
      searchQuery = session.searchQuery,
      isLoadingSurveys = !initialSyncDone && surveys.isEmpty(),
      surveys = surveys,
      filteredSurveys = filterSurveys(surveys, session.searchQuery, organizationNames),
      downloadedSurveyCount = surveys.count { it.isDownloaded },
      organizationNames = organizationNames,
      userOrganization = userOrganization,
      pendingRemovalSurveyId = session.pendingRemovalSurveyId,
      pendingOfflineImagerySurveyId = session.pendingOfflineImagerySurveyId,
      offlineImageryDownloadedSurveyIds = session.offlineImageryDownloadedSurveyIds,
      devSignIn = session.devSignIn,
      directoryResults =
        surveys
          .map { it.id }
          .toSet()
          .let { listed ->
            session.directoryResults.filter { it.id !in listed }
          },
      isSearchingDirectory = session.isSearchingDirectory,
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
    scope.launch { authRepository.signOut() }
  }

  /** Signs out without navigating (the caller decides where to go next). */
  fun signOut() {
    session.update { it.copy(hasAcceptedTerms = false, isSignOutPromptOpen = false) }
    scope.launch { authRepository.signOut() }
  }

  override fun signInWithGoogle() = signInWithGoogle(ignoreOffline = false)

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
    val mode = authRepository.signInMode()
    if (mode.simulatesRoles) {
      session.update { it.copy(devSignIn = DevSignInUiState(mode.lastRole, mode.usesRealAccounts)) }
      return
    }
    startSignIn(role = null)
  }

  override fun selectSimulatedRole(role: SimulatedRole) {
    session.update { s -> s.copy(devSignIn = s.devSignIn?.copy(selectedRole = role)) }
  }

  override fun confirmDevSignIn() {
    val dialog = session.value.devSignIn ?: return
    session.update { it.copy(devSignIn = null) }
    startSignIn(dialog.selectedRole)
  }

  override fun dismissDevSignIn() {
    session.update { it.copy(devSignIn = null) }
  }

  private fun startSignIn(role: SimulatedRole?) {
    if (session.value.isSigningIn) return
    session.update { it.copy(isSigningIn = true) }
    scope.launch {
      try {
        try {
          authRepository.signInWithGoogle(role)
        } catch (e: CancellationException) {
          throw e
        } catch (e: SignInException) {
          // A sign-in window the user closed isn't an error.
          if (!e.cancelled) emit(OnboardingEvent.Notice("Couldn't sign in: ${e.message}"))
          return@launch
        } catch (e: Exception) {
          emit(OnboardingEvent.Notice("Couldn't sign in: ${e.message}"))
          return@launch
        }
        val next =
          if (session.value.hasAcceptedTerms) {
            session.update { it.copy(entryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS) }
            AppScreen.DOWNLOAD_SURVEY
          } else {
            AppScreen.TERMS_OF_SERVICE
          }
        session.update { it.copy(isSignOutPromptOpen = false) }
        emit(OnboardingEvent.ShowScreen(next))
      } finally {
        session.update { it.copy(isSigningIn = false) }
      }
    }
  }

  override fun setTermsChecked(checked: Boolean) {
    session.update { it.copy(termsCheckboxChecked = checked) }
  }

  override fun acceptTermsOfService() {
    session.update {
      it.copy(
        termsCheckboxChecked = true,
        hasAcceptedTerms = true,
        entryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
        isSignOutPromptOpen = false,
      )
    }
    emit(OnboardingEvent.ShowScreen(AppScreen.DOWNLOAD_SURVEY))
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
    searchDirectory(query)
  }

  /**
   * Looks up surveys the survey list doesn't include once typing pauses: the survey a share link or
   * survey ID points to, or public surveys matching [query].
   */
  private fun searchDirectory(query: String) {
    directorySearch?.cancel()
    val directory = surveyDirectoryRepository
    val trimmed = query.trim()
    if (directory == null || trimmed.length < MIN_DIRECTORY_QUERY_LENGTH) {
      session.update { it.copy(directoryResults = emptyList(), isSearchingDirectory = false) }
      return
    }
    directorySearch = scope.launch {
      delay(directorySearchDelayMillis)
      if (!connectivityRepository.isOnline()) return@launch
      session.update { it.copy(isSearchingDirectory = true) }
      val results =
        try {
          val linkedId = SurveyAccess.surveyIdFromLink(trimmed)
          if (linkedId != null) listOfNotNull(directory.lookUpSurvey(linkedId))
          else directory.searchPublicSurveys(trimmed)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          emptyList()
        }
      session.update { it.copy(directoryResults = results, isSearchingDirectory = false) }
    }
  }

  override fun downloadDirectorySurvey(surveyId: String) {
    val survey = session.value.directoryResults.firstOrNull { it.id == surveyId } ?: return
    val directory = surveyDirectoryRepository ?: return
    if (!connectivityRepository.isOnline()) {
      emit(
        OnboardingEvent.Notice(
          "Cannot download survey while offline. Connect to the internet and try again."
        )
      )
      return
    }
    scope.launch {
      directory.downloadSurvey(survey)
      session.update { it.copy(pendingOfflineImagerySurveyId = surveyId) }
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
    scope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = true) ?: return@launch
      session.update { it.copy(pendingOfflineImagerySurveyId = surveyId) }
      emit(
        OnboardingEvent.Notice(
          "Downloaded \"${survey.title}\" (${survey.offlineSizeLabel}) for offline use."
        )
      )
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
    session.update {
      it.copy(
        pendingRemovalSurveyId = null,
        pendingOfflineImagerySurveyId =
          it.pendingOfflineImagerySurveyId?.takeUnless { id -> id == surveyId },
        offlineImageryDownloadedSurveyIds = it.offlineImageryDownloadedSurveyIds - surveyId,
      )
    }
    scope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = false) ?: return@launch
      emit(OnboardingEvent.Notice("Removed offline copy of \"${survey.title}\"."))
    }
  }

  override fun dismissRemoveDownloadedSurvey() {
    session.update { it.copy(pendingRemovalSurveyId = null) }
  }

  override fun promptOfflineImageryDownload(surveyId: String) {
    session.update { it.copy(pendingOfflineImagerySurveyId = surveyId) }
  }

  override fun confirmOfflineImageryDownload() {
    val surveyId = session.value.pendingOfflineImagerySurveyId ?: return
    val survey = uiState.value.surveys.firstOrNull { it.id == surveyId }
    session.update {
      it.copy(
        pendingOfflineImagerySurveyId = null,
        offlineImageryDownloadedSurveyIds = it.offlineImageryDownloadedSurveyIds + surveyId,
      )
    }
    if (survey != null) {
      emit(OnboardingEvent.Notice("Downloaded offline imagery for \"${survey.title}\"."))
    }
  }

  override fun skipOfflineImageryDownload() {
    session.update { it.copy(pendingOfflineImagerySurveyId = null) }
  }

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
    scope.launch {
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

  private companion object {
    /** Shortest query that looks up public surveys. */
    const val MIN_DIRECTORY_QUERY_LENGTH = 2

    fun filterSurveys(
      surveys: List<SurveyPreviewItem>,
      query: String,
      organizationNames: Map<String, String>,
    ): List<SurveyPreviewItem> {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) return surveys
      return surveys.filter { survey ->
        survey.title.contains(trimmed, ignoreCase = true) ||
          survey.description.contains(trimmed, ignoreCase = true) ||
          survey.location.contains(trimmed, ignoreCase = true) ||
          survey.coordinatesLabel.contains(trimmed, ignoreCase = true) ||
          organizationNames[survey.organizationId]?.contains(trimmed, ignoreCase = true) == true
      }
    }
  }
}
