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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.app.domain.model.WEBSITE_URL
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** Settings: user settings, languages, and device storage. */
class MobileSettingsTest {
  private val state =
    MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
  private val settings = state.viewModels.settings

  private val settingsUiState
    get() = settings.uiState.value

  // --- User settings ---

  @Test
  fun defaultSettings_matchTheGroundAndroidDefaults() {
    assertEquals(
      UserSettings(
        language = "en",
        measurementUnits = MeasurementUnitSystem.METRIC,
        shouldUploadPhotosOnWifiOnly = false,
      ),
      state.dataHolder.appData.value.userSettings,
    )
  }

  @Test
  fun defaultLanguage_isEnglish() {
    assertEquals("en", state.viewModels.settings.uiState.value.languageCode)
    assertEquals("English", settingsUiState.selectedLanguageDisplayName)
    assertEquals("en (English)", settingsUiState.selectedLanguageLocale)
  }

  @Test
  fun updateMeasurementUnits_switchesTheMetricDefaultToImperial() {
    settings.updateMeasurementUnits(MeasurementUnitSystem.IMPERIAL)

    assertEquals(
      MeasurementUnitSystem.IMPERIAL,
      state.viewModels.surveyMap.uiState.value.unitSystem,
    )
  }

  @Test
  fun updateLanguage_byCode_selectsTheLanguage() {
    settings.updateLanguage("fr")

    assertEquals("fr", state.dataHolder.appData.value.userSettings.language)
    assertEquals("Français", settingsUiState.selectedLanguageDisplayName)
    assertEquals("fr (Français)", settingsUiState.selectedLanguageLocale)
  }

  @Test
  fun updateLanguage_byLabel_selectsTheLanguage() {
    settings.updateLanguage("Español")

    assertEquals("es", state.viewModels.settings.uiState.value.languageCode)
    assertEquals("Español", settingsUiState.selectedLanguageDisplayName)
  }

  @Test
  fun updateUploadMediaOverUnmeteredConnectionOnly_turnsOnWifiOnlyUploads() {
    settings.updateUploadMediaOverUnmeteredConnectionOnly(true)

    assertTrue(state.dataHolder.appData.value.userSettings.shouldUploadPhotosOnWifiOnly)
  }

  @Test
  fun visitWebsite_recordsTheVisitedUrl() {
    settings.visitWebsite()

    assertEquals(WEBSITE_URL, settingsUiState.visitedWebsiteUrl)
  }

  @Test
  fun visitWebsite_showsANoticeWithTheUrl() {
    settings.visitWebsite()

    assertTrue(state.notices.notice.value?.contains(WEBSITE_URL) == true)
  }

  // --- Device storage ---

  @Test
  fun deviceStorage_isMeasured_andItsPartsAddUpToTheTotal() {
    val storage = assertNotNull(settingsUiState.storage)

    assertTrue(storage.totalSpace.bytes > 0L)
    assertEquals(DataSize.ZERO, storage.savedImagery) // No map areas saved yet.
    assertEquals(
      storage.totalSpace,
      storage.freeSpace + storage.savedImagery + storage.data + storage.otherUsed,
    )
  }

  @Test
  fun deviceStorage_fractionsFitTheDevice() {
    val storage = assertNotNull(settingsUiState.storage)

    assertTrue(
      listOf(
          storage.imageryFraction,
          storage.dataFraction,
          storage.otherUsedFraction,
          storage.freeFraction,
        )
        .all { it in 0f..1f }
    )
    assertTrue(storage.imageryFraction + storage.dataFraction + storage.otherUsedFraction <= 1.0f)
  }
}
