/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.domain.model

import kotlin.math.roundToInt

/**
 * Measurement unit preference (per `docs/design/00-index.md` and `ground-android`
 * `MeasurementUnits`).
 */
enum class MeasurementUnitSystem(
  /** The unit of areas in this system, e.g. on the drawer's Settings entry. */
  val areaUnit: MeasurementUnit
) {
  METRIC(MeasurementUnit.HECTARE),
  IMPERIAL(MeasurementUnit.ACRE);

  /** A map feature's length (a line's length or a polygon's perimeter), in whole meters or feet. */
  fun length(meters: Int): Measurement =
    if (this == METRIC) {
      Measurement(meters.toDouble(), MeasurementUnit.METER, decimals = 0)
    } else {
      Measurement((meters * FEET_PER_METER).roundToInt().toDouble(), MeasurementUnit.FOOT, 0)
    }

  /** A map feature's area: its hectares as stored, or acres rounded to 2 decimal places. */
  fun area(hectares: Double): Measurement =
    if (this == METRIC) {
      Measurement(hectares, MeasurementUnit.HECTARE, decimals = null)
    } else {
      val acres = ((hectares * ACRES_PER_HECTARE) * 100.0).roundToInt() / 100.0
      Measurement(acres, MeasurementUnit.ACRE, decimals = 2)
    }

  /**
   * A walking distance of [meters]: whole meters or feet, or tenths of a kilometer or mile from 1
   * km or 1 mi on.
   */
  fun distance(meters: Int): Measurement =
    if (this == METRIC) {
      if (meters >= 1000) {
        Measurement(((meters / 100.0).roundToInt()) / 10.0, MeasurementUnit.KILOMETER, 1)
      } else {
        Measurement(meters.toDouble(), MeasurementUnit.METER, decimals = 0)
      }
    } else {
      val feet = (meters * FEET_PER_METER).roundToInt()
      if (feet >= 5280) {
        Measurement(((feet / 528.0).roundToInt()) / 10.0, MeasurementUnit.MILE, decimals = 1)
      } else {
        Measurement(feet.toDouble(), MeasurementUnit.FOOT, decimals = 0)
      }
    }

  /** A horizontal GNSS accuracy of [meters]: meters as measured, or feet cut to tenths. */
  fun gnssAccuracy(meters: Double): Measurement =
    if (this == METRIC) {
      Measurement(meters, MeasurementUnit.METER, decimals = null)
    } else {
      val feet = ((meters * FEET_PER_METER) * 10.0).toInt() / 10.0
      Measurement(feet, MeasurementUnit.FOOT, decimals = 1)
    }
}

/**
 * Supported language option matching `arrays.xml` & `strings-untranslated.xml` in
 * `github.com/google/ground-android`. [label] is the language's native name (its endonym, e.g.
 * `Español`), which every language picker shows in every UI language.
 */
data class LanguageOption(val code: String, val label: String) {
  companion object {
    /**
     * The order of language pickers: by native name ([label]), lower-cased and compared character
     * by character in Unicode order, the code breaking ties. It's the same in every UI language and
     * on every platform: Latin-script names alphabetically, then the other scripts in Unicode order
     * (Thai, Lao, Khmer). A Latin name starting with an accented letter would sort after `z`, so
     * revisit this before adding one.
     */
    val NATIVE_NAME_ORDER: Comparator<LanguageOption> =
      compareBy<LanguageOption> { it.label.lowercase() }.thenBy { it.code }
  }
}

/**
 * User settings model ported from `org.openforis.collectone.domain.model.settings.UserSettings` in
 * `github.com/google/ground-android`.
 */
data class UserSettings(
  val language: String = "en",
  val measurementUnits: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  val shouldUploadPhotosOnWifiOnly: Boolean = true,
)

/**
 * The device's storage, as Settings and Offline maps show it: the measured [totalSpace] and
 * [freeSpace], the map areas saved for offline use ([savedImagery]), and an approximation of the
 * app's field data ([data]). The rest of the used space is the system's and other apps'
 * ([otherUsed]), so the parts always add up to [totalSpace] and the free space stays as measured.
 */
data class DeviceStorageInfo(
  val totalSpace: DataSize,
  val freeSpace: DataSize,
  /** What the saved map areas take: the sum of their layers' sizes. */
  val savedImagery: DataSize,
  /** Surveys, map features, submissions, and pending changes on the device (approximate). */
  val data: DataSize,
) {
  /** Space the system and other apps use: what is used beyond [savedImagery] and [data]. */
  val otherUsed: DataSize
    get() =
      DataSize(
        (totalSpace.bytes - freeSpace.bytes - savedImagery.bytes - data.bytes).coerceAtLeast(0)
      )

  /** Share (0 to 1) of [totalSpace] the saved map areas take. */
  val imageryFraction: Float
    get() = fractionOf(savedImagery)

  /** Share (0 to 1) of [totalSpace] the field data takes. */
  val dataFraction: Float
    get() = fractionOf(data)

  /** Share (0 to 1) of [totalSpace] the system and other apps use. */
  val otherUsedFraction: Float
    get() = fractionOf(otherUsed)

  /** Share (0 to 1) of [totalSpace] that is free. */
  val freeFraction: Float
    get() = fractionOf(freeSpace)

  private fun fractionOf(size: DataSize): Float =
    if (totalSpace.bytes > 0L) (size.bytes.toFloat() / totalSpace.bytes.toFloat()).coerceIn(0f, 1f)
    else 0f
}

const val WEBSITE_URL = "https://groundplatform.org/"

/**
 * Official language entries and entry values from `github.com/google/ground-android`
 * (`app/src/main/res/values/arrays.xml` and `strings-untranslated.xml`), plus Kiswahili (`sw`), in
 * the order language pickers list them ([LanguageOption.NATIVE_NAME_ORDER]).
 */
val LANGUAGE_OPTIONS: List<LanguageOption> =
  listOf(
      LanguageOption(code = "en", label = "English"),
      LanguageOption(code = "es", label = "Español"),
      LanguageOption(code = "fr", label = "Français"),
      LanguageOption(code = "sw", label = "Kiswahili"),
      LanguageOption(code = "pt", label = "Português"),
      LanguageOption(code = "vi", label = "Tiếng Việt"),
      LanguageOption(code = "th", label = "ไทย"),
      LanguageOption(code = "lo", label = "ພາສາລາວ"),
      LanguageOption(code = "km", label = "ភាសាខ្មែរ"),
    )
    .sortedWith(LanguageOption.NATIVE_NAME_ORDER)
