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

package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceStorageInfo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LANGUAGE_OPTIONS
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

/**
 * Screen state for Settings, the Sign In language selector, and the Offline maps drawer sub-screen,
 * observed as an immutable snapshot.
 */
data class SettingsUiState(
  val settings: UserSettings = UserSettings(),
  /** Modelled device storage breakdown (see `EstimateDeviceStorageUseCase`). */
  val storage: DeviceStorageInfo = DeviceStorageInfo(),
  val offlineTilePackages: List<OfflineTilePackageItem> = emptyList(),
  /** ID of a downloaded tile package whose removal is pending confirmation. */
  val pendingRemovalTilePackageId: String? = null,
  /** Last URL opened from the Help section, shown as a notice on the Settings screen. */
  val visitedWebsiteUrl: String? = null,
  /** True once uploaded media attachments were evicted from the device cache. */
  val mediaCacheCleared: Boolean = false,
  /** Light vs. dark Ground Material 3 theme (prototype session preference, not persisted). */
  val isDarkTheme: Boolean = false,
  /** Formatted locale of the selected language, e.g. `"fr (Français)"`. */
  val selectedLanguageLocale: String = "en (English)",
) {
  val languageCode: String
    get() = settings.language

  val unitSystem: MeasurementUnitSystem
    get() = settings.measurementUnits

  /** Display label of the selected language (e.g. `"English"`, `"Français"`). */
  val selectedLanguageDisplayName: String
    get() = LANGUAGE_OPTIONS.firstOrNull { it.code == languageCode }?.label ?: "English"
}

/** One-off outcomes of settings actions that the app shell surfaces as transient notices. */
sealed interface SettingsEvent {
  data class Notice(val message: String) : SettingsEvent
}
