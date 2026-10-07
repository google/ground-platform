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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.WEBSITE_URL
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SettingsEvent

/** See [OnboardingViewModelTest] for why fixtures are built outside `runNow`. */
class SettingsViewModelTest {
  private class Fixture {
    val store = seededStore()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val settingsRepository = SettingsRepositoryImpl(store)
    val surveyRepository = SurveyRepositoryImpl(store)
    val viewModel =
      SettingsViewModel(
        settingsRepository = settingsRepository,
        surveyRepository = surveyRepository,
        mutationRepository = MutationRepositoryImpl(store),
        scope = scope,
      )
    val events = mutableListOf<SettingsEvent>()

    init {
      scope.launch { viewModel.events.collect { events += it } }
    }

    val uiState
      get() = viewModel.uiState.value
  }

  @Test
  fun updateLanguage_acceptsCodesAndLocales_andPersists() {
    val f = Fixture()
    assertEquals("en", f.uiState.languageCode)

    f.viewModel.updateLanguage("fr (Français)")
    assertEquals("fr", f.uiState.languageCode)
    assertEquals("fr (Français)", f.uiState.selectedLanguageLocale)
    assertEquals("Français", f.uiState.selectedLanguageDisplayName)
    assertEquals("fr", runNow { f.settingsRepository.getUserSettings() }.language)

    f.viewModel.updateLanguage("sw")
    assertEquals("sw", f.uiState.languageCode)
    assertTrue(f.uiState.selectedLanguageLocale.startsWith("sw ("))

    f.viewModel.updateLanguage("")
    assertEquals("en", f.uiState.languageCode)
  }

  @Test
  fun unitsAndUploadPreference_persistThroughRepository() {
    val f = Fixture()
    f.viewModel.updateMeasurementUnits(MeasurementUnitSystem.IMPERIAL)
    assertEquals(MeasurementUnitSystem.IMPERIAL, f.uiState.unitSystem)

    val before = f.uiState.settings.shouldUploadPhotosOnWifiOnly
    f.viewModel.updateUploadMediaOverUnmeteredConnectionOnly(!before)
    assertEquals(!before, f.uiState.settings.shouldUploadPhotosOnWifiOnly)

    val stored = runNow { f.settingsRepository.getUserSettings() }
    assertEquals(MeasurementUnitSystem.IMPERIAL, stored.measurementUnits)
    assertEquals(!before, stored.shouldUploadPhotosOnWifiOnly)
  }

  @Test
  fun visitWebsite_recordsUrlAndEmitsNotice() {
    val f = Fixture()
    assertNull(f.uiState.visitedWebsiteUrl)
    f.viewModel.visitWebsite()
    assertEquals(WEBSITE_URL, f.uiState.visitedWebsiteUrl)
    assertEquals(SettingsEvent.Notice("Opened $WEBSITE_URL"), f.events.last())
  }

  @Test
  fun evictUploadedMediaCache_marksCleared() {
    val f = Fixture()
    assertFalse(f.uiState.mediaCacheCleared)
    f.viewModel.evictUploadedMediaCache()
    assertTrue(f.uiState.mediaCacheCleared)
  }

  @Test
  fun toggleDarkTheme_flipsSessionTheme_andResetClears() {
    val f = Fixture()
    assertFalse(f.uiState.isDarkTheme)
    f.viewModel.toggleDarkTheme()
    assertTrue(f.uiState.isDarkTheme)
    f.viewModel.reset()
    assertFalse(f.uiState.isDarkTheme)
  }

  @Test
  fun offlineTilePackages_toggleRemoveAndStorageEstimate() {
    val f = Fixture()
    val packages = f.uiState.offlineTilePackages
    assertTrue(packages.isNotEmpty())
    val downloaded = packages.first { it.isDownloaded }
    val remote = packages.first { !it.isDownloaded }
    val imageryBefore = f.uiState.storage.downloadedImageryBytes

    // Prompting on a package that is not downloaded downloads it right away.
    f.viewModel.promptRemoveOfflineTilePackage(remote.id)
    assertNull(f.uiState.pendingRemovalTilePackageId)
    assertTrue(f.uiState.offlineTilePackages.first { it.id == remote.id }.isDownloaded)
    assertTrue(f.uiState.storage.downloadedImageryBytes > imageryBefore)

    // Prompting on a downloaded package asks first; dismiss keeps it.
    f.viewModel.promptRemoveOfflineTilePackage(downloaded.id)
    assertEquals(downloaded.id, f.uiState.pendingRemovalTilePackageId)
    f.viewModel.dismissRemoveOfflineTilePackage()
    assertNull(f.uiState.pendingRemovalTilePackageId)
    assertTrue(f.uiState.offlineTilePackages.first { it.id == downloaded.id }.isDownloaded)

    // Confirm removes it, in the UI state and in the repository.
    f.viewModel.promptRemoveOfflineTilePackage(downloaded.id)
    f.viewModel.confirmRemoveOfflineTilePackage()
    assertNull(f.uiState.pendingRemovalTilePackageId)
    assertFalse(f.uiState.offlineTilePackages.first { it.id == downloaded.id }.isDownloaded)
    assertFalse(
      runNow { f.surveyRepository.getOfflineTilePackages() }
        .first { it.id == downloaded.id }
        .isDownloaded
    )

    // Toggle flips back.
    f.viewModel.toggleOfflineTilePackage(downloaded.id)
    assertTrue(f.uiState.offlineTilePackages.first { it.id == downloaded.id }.isDownloaded)
  }
}
