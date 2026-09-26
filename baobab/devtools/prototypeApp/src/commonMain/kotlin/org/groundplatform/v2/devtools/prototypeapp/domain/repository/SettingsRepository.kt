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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

/**
 * Domain repository contract for managing user settings (`UserSettings`: language, measurement unit
 * system, Wi-Fi photo upload preference) and uploaded media cache metadata.
 */
interface SettingsRepository {
  fun getUserSettings(): UserSettings

  fun setUserSettings(settings: UserSettings)

  fun updateLanguage(languageCode: String): UserSettings

  fun updateMeasurementUnits(units: MeasurementUnitSystem): UserSettings

  fun updateUploadPhotosOnWifiOnly(wifiOnly: Boolean): UserSettings

  fun getUploadedMediaCacheSizeLabel(): String

  fun getUploadedMediaFileCount(): Int

  fun evictUploadedMediaCache(): Pair<Int, String>

  fun resetToDefaults()
}
