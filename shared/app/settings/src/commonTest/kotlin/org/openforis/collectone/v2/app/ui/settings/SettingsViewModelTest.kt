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

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SettingsRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayer
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.app.domain.model.WEBSITE_URL
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.usecase.EstimateDeviceStorageUseCase
import org.openforis.collectone.v2.app.testing.FakeDeviceStorageRepository
import org.openforis.collectone.v2.app.testing.FakeOfflineMapRepository
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Fixtures are built outside [runNow] on purpose: the view model's `Dispatchers.Unconfined` scope
 * only runs nested coroutines after the current one yields, so a fixture built inside an Unconfined
 * block would not see its own state updates until the block ends.
 */
class SettingsViewModelTest {
  private class Fixture(storage: FakeDeviceStorageRepository = FakeDeviceStorageRepository()) {
    val store = seededStore()
    val offlineMaps = FakeOfflineMapRepository()
    val settingsRepository = SettingsRepositoryImpl(store)
    val surveyRepository = SurveyRepositoryImpl(store)
    val connectivity = ConnectivityRepository()
    val viewModel =
      SettingsViewModel(
        settingsRepository = settingsRepository,
        surveyRepository = surveyRepository,
        mutationRepository = MutationRepositoryImpl(store),
        connectivityRepository = connectivity,
        offlineMapRepository = offlineMaps,
        estimateDeviceStorage = EstimateDeviceStorageUseCase(storage),
      )

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
  fun visitWebsite_recordsUrlAndEmitsNotice() = runTest {
    val f = Fixture()
    assertNull(f.uiState.visitedWebsiteUrl)
    f.viewModel.events.test {
      f.viewModel.visitWebsite()
      assertEquals(WEBSITE_URL, f.uiState.visitedWebsiteUrl)
      assertEquals(SettingsEvent.Notice("Opened $WEBSITE_URL"), awaitItem())
    }
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
  fun storage_isMeasured_withTheSavedAreasAsImagery() {
    val f = Fixture()
    f.offlineMaps.areas.value =
      listOf(
        OfflineMapArea(
          id = "area-1",
          name = OfflineAreaName.Named("Othaya, Nyeri"),
          bounds = LngLatBounds(west = 36.9, south = -0.45, east = 36.95, north = -0.4),
          createdAt = FakeOfflineMapRepository.CREATED_AT,
          layers =
            listOf(
              OfflineAreaLayer(
                OfflineImageryType.SATELLITE,
                OfflineAreaLayerStatus.READY,
                size = DataSize.ofMegabytes(38),
              ),
              OfflineAreaLayer(
                OfflineImageryType.TERRAIN,
                OfflineAreaLayerStatus.READY,
                size = DataSize.ofMegabytes(10),
              ),
            ),
        )
      )

    val storage = assertNotNull(f.uiState.storage)
    assertEquals(DataSize.ofMegabytes(64_000), storage.totalSpace)
    assertEquals(DataSize.ofMegabytes(12_400), storage.freeSpace)
    assertEquals(DataSize.ofMegabytes(48), storage.savedImagery)
    // The parts add up to the total, so the free space stays as measured.
    assertEquals(
      storage.totalSpace,
      storage.freeSpace + storage.savedImagery + storage.data + storage.otherUsed,
    )
  }

  @Test
  fun storage_isNull_whenTheDeviceCannotMeasureIt() {
    val f = Fixture(storage = FakeDeviceStorageRepository(free = null, total = null))
    assertNull(f.uiState.storage)
  }

  @Test
  fun selectedLanguageLocale_followsTheStoredLanguage_evenAfterReset() {
    val f = Fixture()
    assertEquals("en (English)", f.uiState.selectedLanguageLocale)

    f.viewModel.updateLanguage("Français")
    f.viewModel.reset()

    assertEquals("fr (Français)", f.uiState.selectedLanguageLocale)

    // Unsupported language codes are ignored by SettingsRepository, so the UI locale does not
    // drift away from the stored language.
    f.viewModel.updateLanguage("xx")
    assertEquals("fr", f.uiState.languageCode)
    assertEquals("fr (Français)", f.uiState.selectedLanguageLocale)

    // If stored settings hold an unlisted code directly, the state falls back gracefully.
    val unlisted = SettingsUiState(settings = UserSettings(language = "xx"))
    assertEquals("xx", unlisted.selectedLanguageLocale)
    assertEquals("English", unlisted.selectedLanguageDisplayName)
  }

  @Test
  fun visitWebsite_offline_explainsWhy_andRecordsNoVisit() = runTest {
    val f = Fixture()
    f.connectivity.setOnline(false)
    f.viewModel.events.test {
      f.viewModel.visitWebsite()
      val notice = awaitItem()
      assertTrue(
        notice is SettingsEvent.Notice && notice.message.startsWith("Cannot open website"),
        notice.toString(),
      )
    }
    assertNull(f.uiState.visitedWebsiteUrl)
  }
}
