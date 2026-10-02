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

import kotlin.math.roundToInt

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

/**
 * Breakdown of device storage usage for offline map tiles, field data, and available space.
 *
 * @param totalBytes Total storage capacity of the device file system.
 * @param downloadedImageryBytes Space occupied by downloaded map tiles and satellite/aerial
 *   imagery.
 * @param dataBytes Space occupied by survey definitions, master data, form submissions, and pending
 *   mutations.
 * @param otherUsedBytes Space occupied by OS system files, other applications, and media.
 */
data class DeviceStorageInfo(
  val totalBytes: Long = 64L * 1024L * 1024L * 1024L, // 64 GB
  val downloadedImageryBytes: Long = 1_850_000_000L, // 1.85 GB (~1.8 GB imagery)
  val dataBytes: Long = 420_000_000L, // 420 MB data (forms, submissions, master data)
  val otherUsedBytes: Long = 18_200_000_000L, // 18.2 GB system & other apps
) {
  /** Free / available device storage space. */
  val freeBytes: Long
    get() = (totalBytes - downloadedImageryBytes - dataBytes - otherUsedBytes).coerceAtLeast(0L)

  /** Fraction (0.0 to 1.0) of total storage occupied by downloaded imagery. */
  val imageryFraction: Float
    get() =
      if (totalBytes > 0L)
        (downloadedImageryBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
      else 0f

  /** Fraction (0.0 to 1.0) of total storage occupied by surveys and data. */
  val dataFraction: Float
    get() =
      if (totalBytes > 0L) (dataBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

  /** Fraction (0.0 to 1.0) of total storage occupied by other system files and apps. */
  val otherUsedFraction: Float
    get() =
      if (totalBytes > 0L) (otherUsedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
      else 0f

  /** Fraction (0.0 to 1.0) of total storage that is free / available. */
  val freeFraction: Float
    get() =
      if (totalBytes > 0L) (freeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

  /** Formatted human-readable label for total storage (e.g. "64.0 GB"). */
  val totalStorageLabel: String
    get() = formatBytes(totalBytes)

  /** Formatted human-readable label for free storage (e.g. "43.5 GB"). */
  val freeStorageLabel: String
    get() = formatBytes(freeBytes)

  /** Formatted human-readable label for downloaded imagery storage (e.g. "1.9 GB"). */
  val downloadedImageryStorageLabel: String
    get() = formatBytes(downloadedImageryBytes)

  /** Formatted human-readable label for data storage (e.g. "420 MB"). */
  val dataStorageLabel: String
    get() = formatBytes(dataBytes)

  /** Formatted human-readable label for other used space. */
  val otherUsedStorageLabel: String
    get() = formatBytes(otherUsedBytes)

  companion object {
    fun formatBytes(bytes: Long): String {
      val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
      if (gb >= 1.0) {
        val rounded = (gb * 10.0).roundToInt() / 10.0
        return "$rounded GB"
      }
      val mb = bytes.toDouble() / (1024.0 * 1024.0)
      val roundedMb = (mb * 10.0).roundToInt() / 10.0
      return "$roundedMb MB"
    }
  }
}

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
