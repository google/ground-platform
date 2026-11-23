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
package org.openforis.collectone.v2.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn

/**
 * Decides how the mobile app launches ([launch]) from the data on the device: once the session is
 * restored ([restoredSession]) and the signed-in user's stored data is in the local store
 * ([hasLoadedLocalData], right away when signed out), but never after the network. A ViewModel, so
 * sessions rebuilt after an Android configuration change start the same way without deciding again.
 */
internal class MobileLaunchViewModel(
  private val restoredSession: StateFlow<AuthSession?>,
  private val hasLoadedLocalData: StateFlow<Boolean>,
  private val surveyRepository: SurveyRepository,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
) : ViewModel(viewModelScopeOn(dispatcher)) {
  private val decidedLaunch = MutableStateFlow<MobileLaunch?>(null)

  /** How the app launches, or null until it's known; fixed once known. */
  val launch: StateFlow<MobileLaunch?> = decidedLaunch.asStateFlow()

  init {
    viewModelScope.launch { decidedLaunch.value = awaitLaunch() }
  }

  private suspend fun awaitLaunch(): MobileLaunch {
    // Reads the session once both are known rather than the first restored value, which can trail
    // the account the auth client restored.
    combine(restoredSession, hasLoadedLocalData) { session, isLoaded ->
        session != null && isLoaded
      }
      .first { it }
    val session = checkNotNull(restoredSession.value)
    val openSurveyId = surveyRepository.observeOpenSurveyId()
    val hasSurveysOnDevice = surveyRepository.getSurveys().any { it.isDownloaded }
    return MobileLaunch(
      startRoute =
        launchRoute(
          session,
          isSurveyOpen = openSurveyId.value != null,
          hasSurveysOnDevice = hasSurveysOnDevice,
        ),
      isSurveyOpen = { openSurveyId.value != null },
    )
  }
}
