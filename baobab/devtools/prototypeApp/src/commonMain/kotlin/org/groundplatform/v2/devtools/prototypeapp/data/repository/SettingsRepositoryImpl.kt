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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GROUND_LANGUAGE_OPTIONS
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository

/**
 * Concrete [SettingsRepository] implementation backed by [PrototypeAppDataStore].
 */
class SettingsRepositoryImpl(private val dataStore: PrototypeAppDataStore) : SettingsRepository {
  override fun getUserSettings(): UserSettings = dataStore.userSettings

  override fun setUserSettings(settings: UserSettings) {
    dataStore.userSettings = settings
  }

  override fun updateLanguage(languageCode: String): UserSettings {
    val validOption = GROUND_LANGUAGE_OPTIONS.firstOrNull { it.code == languageCode }
    if (validOption != null) {
      dataStore.userSettings = dataStore.userSettings.copy(language = validOption.code)
    }
    return dataStore.userSettings
  }

  override fun updateMeasurementUnits(units: MeasurementUnitSystem): UserSettings {
    dataStore.userSettings = dataStore.userSettings.copy(measurementUnits = units)
    return dataStore.userSettings
  }

  override fun updateUploadPhotosOnWifiOnly(wifiOnly: Boolean): UserSettings {
    dataStore.userSettings = dataStore.userSettings.copy(shouldUploadPhotosOnWifiOnly = wifiOnly)
    return dataStore.userSettings
  }

  override fun getUploadedMediaCacheSizeLabel(): String = dataStore.uploadedMediaCacheSizeLabel

  override fun getUploadedMediaFileCount(): Int = dataStore.uploadedMediaFileCount

  override fun evictUploadedMediaCache(): Pair<Int, String> {
    val evictedCount = dataStore.uploadedMediaFileCount
    val evictedSize = dataStore.uploadedMediaCacheSizeLabel
    dataStore.uploadedMediaFileCount = 0
    dataStore.uploadedMediaCacheSizeLabel = "0 MB"
    return evictedCount to evictedSize
  }

  override fun resetToDefaults() {
    dataStore.userSettings = PrototypeAppDataStore.defaultUserSettings()
    dataStore.uploadedMediaCacheSizeLabel =
      PrototypeAppDataStore.DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL
    dataStore.uploadedMediaFileCount = PrototypeAppDataStore.DEFAULT_UPLOADED_MEDIA_FILE_COUNT
  }
}
