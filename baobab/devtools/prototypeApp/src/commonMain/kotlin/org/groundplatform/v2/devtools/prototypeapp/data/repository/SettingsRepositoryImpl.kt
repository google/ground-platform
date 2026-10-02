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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GROUND_LANGUAGE_OPTIONS
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MediaCacheInfo
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SettingsRepository

/** [SettingsRepository] backed by preferences in the [LocalStore]. */
class SettingsRepositoryImpl(private val store: LocalStore) : SettingsRepository {
  override fun observeUserSettings(): Flow<UserSettings> =
    store.observePreferences().map { it.userSettings }.distinctUntilChanged()

  override fun observeMediaCache(): Flow<MediaCacheInfo> =
    store
      .observePreferences()
      .map { MediaCacheInfo(it.uploadedMediaCacheSizeLabel, it.uploadedMediaFileCount) }
      .distinctUntilChanged()

  override suspend fun getUserSettings(): UserSettings = store.transaction {
    preferences().userSettings
  }

  override suspend fun setUserSettings(settings: UserSettings) {
    store.transaction { updatePreferences { it.copy(userSettings = settings) } }
  }

  override suspend fun updateLanguage(languageCode: String): UserSettings {
    val valid = GROUND_LANGUAGE_OPTIONS.any { it.code == languageCode }
    return updateSettings { if (valid) it.copy(language = languageCode) else it }
  }

  override suspend fun updateMeasurementUnits(units: MeasurementUnitSystem): UserSettings =
    updateSettings {
      it.copy(measurementUnits = units)
    }

  override suspend fun updateUploadPhotosOnWifiOnly(wifiOnly: Boolean): UserSettings =
    updateSettings {
      it.copy(shouldUploadPhotosOnWifiOnly = wifiOnly)
    }

  override suspend fun evictUploadedMediaCache(): MediaCacheInfo = store.transaction {
    val before = preferences()
    updatePreferences { it.copy(uploadedMediaCacheSizeLabel = "0 MB", uploadedMediaFileCount = 0) }
    MediaCacheInfo(before.uploadedMediaCacheSizeLabel, before.uploadedMediaFileCount)
  }

  private suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings =
    store.transaction {
      updatePreferences { it.copy(userSettings = transform(it.userSettings)) }.userSettings
    }
}
