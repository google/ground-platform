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

import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LANGUAGE_OPTIONS
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.WEBSITE_URL
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.EstimateDeviceStorageUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SettingsEvent
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SettingsUiState

/** User intents of the Settings and Offline maps screens. Implemented by [SettingsViewModel]. */
interface SettingsActions {
  /**
   * Sets the app language from a language code (`"fr"`) or a formatted locale (`"fr (Français)"`).
   */
  fun updateLanguage(languageCodeOrLocale: String)

  fun updateMeasurementUnits(units: MeasurementUnitSystem)

  fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean)

  /** Records a click on "Visit website" in the Help section. */
  fun visitWebsite(url: String = WEBSITE_URL)

  /** Evicts uploaded media attachments from the device cache. */
  fun evictUploadedMediaCache()

  fun toggleDarkTheme()

  /** Toggles the download state of an offline basemap tile package. */
  fun toggleOfflineTilePackage(packageId: String)

  /** Asks before removing a downloaded tile package; downloads it if not downloaded yet. */
  fun promptRemoveOfflineTilePackage(packageId: String)

  fun confirmRemoveOfflineTilePackage()

  fun dismissRemoveOfflineTilePackage()
}

/**
 * ViewModel for user settings, the session theme, and offline basemap tile packages.
 *
 * Preferences are read from and written through [SettingsRepository]; tile packages through
 * [SurveyRepository]. The device storage breakdown is modelled by [EstimateDeviceStorageUseCase]
 * from the tile packages, the mutation log, and the active survey's entities.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  private val surveyRepository: SurveyRepository,
  mutationRepository: MutationRepository,
  private val scope: CoroutineScope,
  private val estimateDeviceStorage: EstimateDeviceStorageUseCase = EstimateDeviceStorageUseCase(),
) : SettingsActions {
  private data class Session(
    val isDarkTheme: Boolean = false,
    val selectedLanguageLocale: String = "en (English)",
    val visitedWebsiteUrl: String? = null,
    val mediaCacheCleared: Boolean = false,
    val pendingRemovalTilePackageId: String? = null,
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

  val uiState: StateFlow<SettingsUiState> =
    combine(
        settingsRepository.observeUserSettings(),
        surveyRepository.observeOfflineTilePackages(),
        mutationRepository.observeMutations(),
        activeSurveyEntityCount,
        session,
        ::buildUiState,
      )
      .stateIn(scope, SharingStarted.Eagerly, SettingsUiState())

  private fun buildUiState(
    settings: UserSettings,
    tilePackages: List<OfflineTilePackageItem>,
    mutations: List<MutationLogItem>,
    entityCount: Int,
    session: Session,
  ) =
    SettingsUiState(
      settings = settings,
      storage = estimateDeviceStorage(tilePackages, mutations.size, entityCount),
      offlineTilePackages = tilePackages,
      pendingRemovalTilePackageId = session.pendingRemovalTilePackageId,
      visitedWebsiteUrl = session.visitedWebsiteUrl,
      mediaCacheCleared = session.mediaCacheCleared,
      isDarkTheme = session.isDarkTheme,
      selectedLanguageLocale = session.selectedLanguageLocale,
    )

  /** Returns session-only state to its defaults (used by the prototype's Reset). */
  fun reset() {
    session.value = Session()
  }

  override fun updateLanguage(languageCodeOrLocale: String) {
    val trimmed = languageCodeOrLocale.trim()
    val codeCandidate = trimmed.substringBefore(" ").lowercase()
    val matched = LANGUAGE_OPTIONS.firstOrNull {
      it.code.equals(trimmed, ignoreCase = true) ||
        it.code.equals(codeCandidate, ignoreCase = true) ||
        it.label.equals(trimmed, ignoreCase = true) ||
        "${it.code} (${it.label})".equals(trimmed, ignoreCase = true)
    }
    val (code, locale) =
      if (matched != null) matched.code to "${matched.code} (${matched.label})"
      else codeCandidate.ifEmpty { "en" } to trimmed.ifEmpty { "en (English)" }
    session.update { it.copy(selectedLanguageLocale = locale) }
    scope.launch { settingsRepository.updateLanguage(code) }
  }

  override fun updateMeasurementUnits(units: MeasurementUnitSystem) {
    scope.launch { settingsRepository.updateMeasurementUnits(units) }
  }

  override fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean) {
    scope.launch { settingsRepository.updateUploadPhotosOnWifiOnly(enabled) }
  }

  override fun visitWebsite(url: String) {
    session.update { it.copy(visitedWebsiteUrl = url) }
    _events.tryEmit(SettingsEvent.Notice("Opened $url"))
  }

  override fun evictUploadedMediaCache() {
    session.update { it.copy(mediaCacheCleared = true) }
    scope.launch { settingsRepository.evictUploadedMediaCache() }
  }

  override fun toggleDarkTheme() {
    session.update { it.copy(isDarkTheme = !it.isDarkTheme) }
  }

  override fun toggleOfflineTilePackage(packageId: String) {
    val tilePackage = uiState.value.offlineTilePackages.firstOrNull { it.id == packageId } ?: return
    scope.launch {
      surveyRepository.setOfflineTilePackageDownloaded(packageId, !tilePackage.isDownloaded)
    }
  }

  override fun promptRemoveOfflineTilePackage(packageId: String) {
    val tilePackage = uiState.value.offlineTilePackages.firstOrNull { it.id == packageId }
    if (tilePackage != null && tilePackage.isDownloaded) {
      session.update { it.copy(pendingRemovalTilePackageId = packageId) }
    } else {
      toggleOfflineTilePackage(packageId)
    }
  }

  override fun confirmRemoveOfflineTilePackage() {
    val packageId = session.value.pendingRemovalTilePackageId ?: return
    session.update { it.copy(pendingRemovalTilePackageId = null) }
    scope.launch { surveyRepository.setOfflineTilePackageDownloaded(packageId, downloaded = false) }
  }

  override fun dismissRemoveOfflineTilePackage() {
    session.update { it.copy(pendingRemovalTilePackageId = null) }
  }
}
