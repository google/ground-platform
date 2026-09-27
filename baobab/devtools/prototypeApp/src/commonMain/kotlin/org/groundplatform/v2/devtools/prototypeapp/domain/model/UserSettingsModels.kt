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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Measurement unit preference (per `docs/design/00-index.md` and `ground-android`
 * `MeasurementUnits`).
 */
enum class MeasurementUnitSystem(
  val label: String,
  val shortLabel: String,
  val areaUnit: String,
  val distanceUnit: String,
) {
  METRIC("Metric (ha, m)", "Metric", "ha", "m"),
  IMPERIAL("Imperial (acres, ft)", "Imperial", "acres", "ft"),
}

/**
 * Supported language option matching `arrays.xml` & `strings-untranslated.xml` in
 * `github.com/google/ground-android`.
 */
data class LanguageOption(val code: String, val label: String)

/** Backward-compatible alias for [LanguageOption]. */
typealias GroundLanguageOption = LanguageOption

/**
 * User settings model ported from `org.groundplatform.domain.model.settings.UserSettings` in
 * `github.com/google/ground-android`.
 */
data class UserSettings(
  val language: String = "en",
  val measurementUnits: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  val shouldUploadPhotosOnWifiOnly: Boolean = true,
)

const val WEBSITE_URL = "https://groundplatform.org/"

/** Backward-compatible alias for [WEBSITE_URL]. */
const val GROUND_WEBSITE_URL = WEBSITE_URL

/**
 * Official language entries and entry values from `github.com/google/ground-android`
 * (`app/src/main/res/values/arrays.xml` and `strings-untranslated.xml`), plus Kiswahili (`sw`).
 */
val LANGUAGE_OPTIONS: List<LanguageOption> =
  listOf(
    LanguageOption(code = "en", label = "English"),
    LanguageOption(code = "fr", label = "Français"),
    LanguageOption(code = "es", label = "Español"),
    LanguageOption(code = "pt", label = "Português"),
    LanguageOption(code = "vi", label = "Tiếng Việt"),
    LanguageOption(code = "th", label = "ไทย"),
    LanguageOption(code = "lo", label = "ພາສາລາວ"),
    LanguageOption(code = "km", label = "ភាសាខ្មែរ"),
    LanguageOption(code = "sw", label = "Kiswahili"),
  )

/** Backward-compatible alias for [LANGUAGE_OPTIONS]. */
val GROUND_LANGUAGE_OPTIONS: List<LanguageOption> = LANGUAGE_OPTIONS
