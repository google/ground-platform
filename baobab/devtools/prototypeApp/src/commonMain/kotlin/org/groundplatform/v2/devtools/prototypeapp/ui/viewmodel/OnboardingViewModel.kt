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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthSession
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyStats
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OnboardingEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OnboardingUiState

/** User intents of the onboarding screens. Implemented by [OnboardingViewModel]. */
interface OnboardingActions {
  fun signInWithGoogle()

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
  private val scope: CoroutineScope,
) : OnboardingActions {
  private data class Session(
    val hasAcceptedTerms: Boolean = false,
    val termsCheckboxChecked: Boolean = true,
    val entryOrigin: DownloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
    val isSignOutPromptOpen: Boolean = false,
    val searchQuery: String = "",
    val pendingRemovalSurveyId: String? = null,
  )

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
        session,
        ::buildUiState,
      )
      .stateIn(scope, SharingStarted.Eagerly, OnboardingUiState())

  private fun buildUiState(
    auth: AuthSession,
    surveys: List<SurveyPreviewItem>,
    organizations: List<Organization>,
    session: Session,
  ): OnboardingUiState {
    val organizationNames = organizations.associate { it.id to it.name }
    return OnboardingUiState(
      isSignedIn = auth.isSignedIn,
      profile = auth.profile,
      hasAcceptedTerms = session.hasAcceptedTerms,
      termsCheckboxChecked = session.termsCheckboxChecked,
      entryOrigin = session.entryOrigin,
      isSignOutPromptOpen = session.isSignOutPromptOpen,
      searchQuery = session.searchQuery,
      surveys = surveys,
      filteredSurveys = filterSurveys(surveys, session.searchQuery, organizationNames),
      downloadedSurveyCount = surveys.count { it.isDownloaded },
      organizationNames = organizationNames,
      pendingRemovalSurveyId = session.pendingRemovalSurveyId,
    )
  }

  /**
   * Records how the Download survey screen is being entered (see [OnboardingUiState.entryOrigin]).
   */
  fun setEntryOrigin(origin: DownloadSurveyEntryOrigin) {
    session.update { it.copy(entryOrigin = origin, isSignOutPromptOpen = false) }
  }

  /** Returns the flow to its initial state (used by the prototype's Reset). */
  fun reset() {
    session.value = Session()
    scope.launch { authRepository.signOut() }
  }

  /** Signs out without navigating (the caller decides where to go next). */
  fun signOut() {
    session.update { it.copy(hasAcceptedTerms = false, isSignOutPromptOpen = false) }
    scope.launch { authRepository.signOut() }
  }

  override fun signInWithGoogle() {
    scope.launch {
      authRepository.signInWithGoogle()
      val next =
        if (session.value.hasAcceptedTerms) {
          session.update { it.copy(entryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS) }
          AppScreen.DOWNLOAD_SURVEY
        } else {
          AppScreen.TERMS_OF_SERVICE
        }
      session.update { it.copy(isSignOutPromptOpen = false) }
      emit(OnboardingEvent.ShowScreen(next))
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
  }

  override fun clearSearchQuery() = updateSearchQuery("")

  override fun downloadSurvey(surveyId: String) {
    scope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = true) ?: return@launch
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
    session.update { it.copy(pendingRemovalSurveyId = null) }
    scope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = false) ?: return@launch
      emit(OnboardingEvent.Notice("Removed offline copy of \"${survey.title}\"."))
    }
  }

  override fun dismissRemoveDownloadedSurvey() {
    session.update { it.copy(pendingRemovalSurveyId = null) }
  }

  override fun openSurvey(surveyId: String) {
    scope.launch {
      val survey =
        surveyRepository.setSurveyDownloaded(surveyId, downloaded = true) ?: return@launch
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
