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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

/** Uploaded media cache usage shown in Settings. */
data class MediaCacheInfo(val sizeLabel: String, val fileCount: Int)

/** Domain repository contract for user settings and the uploaded media cache. */
interface SettingsRepository {
  fun observeUserSettings(): Flow<UserSettings>

  fun observeMediaCache(): Flow<MediaCacheInfo>

  suspend fun getUserSettings(): UserSettings

  suspend fun setUserSettings(settings: UserSettings)

  /** Sets the language if [languageCode] is supported; returns the resulting settings. */
  suspend fun updateLanguage(languageCode: String): UserSettings

  suspend fun updateMeasurementUnits(units: MeasurementUnitSystem): UserSettings

  suspend fun updateUploadPhotosOnWifiOnly(wifiOnly: Boolean): UserSettings

  /** Clears the uploaded media cache; returns what was evicted. */
  suspend fun evictUploadedMediaCache(): MediaCacheInfo
}
