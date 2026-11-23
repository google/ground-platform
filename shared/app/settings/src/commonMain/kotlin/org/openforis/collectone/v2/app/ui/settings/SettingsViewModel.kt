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

package org.openforis.collectone.v2.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.model.DeviceStorageInfo
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.app.domain.model.WEBSITE_URL
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.domain.repository.SettingsRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.usecase.EstimateDeviceStorageUseCase
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn

/** User intents of the Settings screen. Implemented by [SettingsViewModel]. */
interface SettingsActions {
  /**
   * Sets the app language from a language code (`"fr"`) or a formatted locale (`"fr (Français)"`).
   */
  fun updateLanguage(languageCodeOrLocale: String)

  fun updateMeasurementUnits(units: MeasurementUnitSystem)

  fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean)

  /** Records a click on "Visit website" in the Help section. */
  fun visitWebsite(url: String = WEBSITE_URL)

  fun evictUploadedMediaCache()

  fun toggleDarkTheme()
}

/**
 * ViewModel for user settings, the session theme, and the device storage breakdown.
 *
 * Preferences are read from and written through [SettingsRepository]. The device storage breakdown
 * comes from [EstimateDeviceStorageUseCase]: the measured device space, the map areas saved for
 * offline use ([OfflineMapRepository]), and the mutation log and the active survey's entities.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  surveyRepository: SurveyRepository,
  mutationRepository: MutationRepository,
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  private val connectivityRepository: ConnectivityRepository,
  offlineMapRepository: OfflineMapRepository,
  private val estimateDeviceStorage: EstimateDeviceStorageUseCase,
) : ViewModel(viewModelScopeOn(dispatcher)), SettingsActions {
  /**
   * Session-only UI flags that intentionally reset across launches (unlike [UserSettings], which
   * persists through [SettingsRepository]).
   */
  private data class Session(
    val isDarkTheme: Boolean = false,
    val visitedWebsiteUrl: String? = null,
    val mediaCacheCleared: Boolean = false,
  )

  private val session = MutableStateFlow(Session())

  private val _events =
    MutableSharedFlow<SettingsEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /** Transient notices for the app shell to show. */
  val events: Flow<SettingsEvent> = _events.asSharedFlow()

  private val activeSurveyEntityCount: Flow<Int> =
    surveyRepository.observeActiveSurveyId().flatMapLatest { surveyId ->
      surveyRepository.observeSurveyContent(surveyId).map { it.entities.size }
    }

  /**
   * The device storage breakdown, re-estimated whenever saved areas or local record counts change;
   * `mapLatest` cancels an in-flight measurement if a newer change arrives first.
   */
  private val storage: Flow<DeviceStorageInfo?> =
    combine(
        offlineMapRepository.observeAreas(),
        mutationRepository.observeMutations().map { it.size },
        activeSurveyEntityCount,
        ::StorageInputs,
      )
      .mapLatest { estimateDeviceStorage(it.areas, it.mutationCount, it.entityCount) }

  private data class StorageInputs(
    val areas: List<OfflineMapArea>,
    val mutationCount: Int,
    val entityCount: Int,
  )

  val uiState: StateFlow<SettingsUiState> =
    combine(settingsRepository.observeUserSettings(), storage, session, ::buildUiState)
      .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

  private fun buildUiState(settings: UserSettings, storage: DeviceStorageInfo?, session: Session) =
    SettingsUiState(
      settings = settings,
      storage = storage,
      visitedWebsiteUrl = session.visitedWebsiteUrl,
      mediaCacheCleared = session.mediaCacheCleared,
      isDarkTheme = session.isDarkTheme,
    )

  /** Returns session-only state to its defaults (used by the workbench's Reset). */
  fun reset() {
    session.value = Session()
  }

  override fun updateLanguage(languageCodeOrLocale: String) {
    val code = languageCodeFrom(languageCodeOrLocale)
    viewModelScope.launch { settingsRepository.updateLanguage(code) }
  }

  override fun updateMeasurementUnits(units: MeasurementUnitSystem) {
    viewModelScope.launch { settingsRepository.updateMeasurementUnits(units) }
  }

  override fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.updateUploadPhotosOnWifiOnly(enabled) }
  }

  override fun visitWebsite(url: String) {
    if (!connectivityRepository.isOnline()) {
      _events.tryEmit(
        SettingsEvent.Notice(
          "Cannot open website while offline. Connect to the internet and try again."
        )
      )
      return
    }
    session.update { it.copy(visitedWebsiteUrl = url) }
    _events.tryEmit(SettingsEvent.Notice("Opened $url"))
  }

  override fun evictUploadedMediaCache() {
    session.update { it.copy(mediaCacheCleared = true) }
    viewModelScope.launch { settingsRepository.evictUploadedMediaCache() }
  }

  override fun toggleDarkTheme() {
    session.update { it.copy(isDarkTheme = !it.isDarkTheme) }
  }
}
