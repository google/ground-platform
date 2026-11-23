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
package org.openforis.collectone.v2.app.ui.text

import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_measurement
import org.openforis.collectone.v2.app.resources.app_unit_gigabyte
import org.openforis.collectone.v2.app.resources.app_unit_kilobyte
import org.openforis.collectone.v2.app.resources.app_unit_megabyte
import org.openforis.collectone.v2.app.resources.app_unit_terabyte

/**
 * The decimal units a [DataSize] is written in, smallest first, and the amount below which each
 * keeps one decimal: below 10 for kB and MB (`4.2 MB`, `48 MB`), below 100 for GB and TB, where a
 * whole number hides too much (`12.4 GB`, `120 GB`).
 */
private enum class DataSizeUnit(
  val bytes: Double,
  val nameRes: StringResource,
  val oneDecimalBelow: Double,
) {
  KILOBYTE(1e3, Res.string.app_unit_kilobyte, 10.0),
  MEGABYTE(1e6, Res.string.app_unit_megabyte, 10.0),
  GIGABYTE(1e9, Res.string.app_unit_gigabyte, 100.0),
  TERABYTE(1e12, Res.string.app_unit_terabyte, 100.0),
}

/** The amount at which the next larger unit takes over (`1,000 kB` is written `1.0 MB`). */
private const val NEXT_UNIT_AT = 1000.0

/**
 * This size in the user's language, e.g. `4.2 MB`, `48 MB`, or `12.4 GB`.
 *
 * Units are decimal (SI: 1 MB = 1,000,000 bytes), as modern Android and iOS show file and storage
 * sizes, so the free space we show matches the system's storage settings. The amount has one
 * decimal below 10 and none from 10 up, in the largest unit (kB to TB) that keeps it at least 1;
 * sizes under 1 kB are written in kB (`0.4 kB`), and nothing as `0 kB`. Like [amountText], the
 * number isn't localized yet, only the unit.
 */
fun DataSize.toUiText(): UiText {
  val units = DataSizeUnit.entries
  var index = units.indexOfLast { abs(bytes.toDouble()) >= it.bytes }.coerceAtLeast(0)
  var amount = rounded(bytes / units[index].bytes, units[index])
  // Rounding can reach the next unit (999.6 kB is 1.0 MB).
  if (abs(amount) >= NEXT_UNIT_AT && index < units.lastIndex) {
    index++
    amount = rounded(bytes / units[index].bytes, units[index])
  }
  val amountText =
    if (amount != 0.0 && abs(amount) < units[index].oneDecimalBelow) amount.toString()
    else amount.roundToLong().toString()
  return Res.string.app_measurement.toUiText(amountText, units[index].nameRes)
}

/**
 * [value] to one decimal below the [unit]'s threshold (in MB, 9.96 is 10, then whole), else whole.
 */
private fun rounded(value: Double, unit: DataSizeUnit): Double {
  val tenths = (value * 10).roundToLong() / 10.0
  return if (abs(tenths) < unit.oneDecimalBelow) tenths else value.roundToLong().toDouble()
}
