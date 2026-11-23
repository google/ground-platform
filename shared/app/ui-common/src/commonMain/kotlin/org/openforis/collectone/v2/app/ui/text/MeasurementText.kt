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
package org.openforis.collectone.v2.app.ui.text

import kotlin.math.roundToLong
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_gnss_accuracy
import org.openforis.collectone.v2.app.resources.app_measurement
import org.openforis.collectone.v2.app.resources.app_unit_acre
import org.openforis.collectone.v2.app.resources.app_unit_foot
import org.openforis.collectone.v2.app.resources.app_unit_hectare
import org.openforis.collectone.v2.app.resources.app_unit_kilometer
import org.openforis.collectone.v2.app.resources.app_unit_meter
import org.openforis.collectone.v2.app.resources.app_unit_mile
import org.openforis.collectone.v2.app.resources.app_unit_square_kilometer

/** This unit's name in the user's language, e.g. `ha` or `acres`; resolve with `stringResource`. */
val MeasurementUnit.nameRes: StringResource
  get() =
    when (this) {
      MeasurementUnit.METER -> Res.string.app_unit_meter
      MeasurementUnit.KILOMETER -> Res.string.app_unit_kilometer
      MeasurementUnit.FOOT -> Res.string.app_unit_foot
      MeasurementUnit.MILE -> Res.string.app_unit_mile
      MeasurementUnit.HECTARE -> Res.string.app_unit_hectare
      MeasurementUnit.ACRE -> Res.string.app_unit_acre
      MeasurementUnit.SQUARE_KILOMETER -> Res.string.app_unit_square_kilometer
    }

/**
 * The number of this measurement, the same in every language: a whole number without a fraction
 * (`120`), otherwise the value as Kotlin writes it (`2.97`, `2.0`). There's no locale-aware number
 * formatting yet, so only the unit is localized.
 */
fun Measurement.amountText(): String =
  if (decimals == 0) value.roundToLong().toString() else value.toString()

/** This measurement in the user's language, e.g. `120 m` or `2.97 acres`. */
fun Measurement.toUiText(): UiText = measurementText(amountText(), unit)

/**
 * [amount] (a number already written, e.g. with thousands separators) followed by [unit]'s name in
 * the user's language, e.g. `1,234 km²`.
 */
fun measurementText(amount: String, unit: MeasurementUnit): UiText =
  Res.string.app_measurement.toUiText(amount, unit.nameRes)

/** The horizontal accuracy of a GPS fix in the user's language, e.g. `±2.1 m`. */
fun gnssAccuracyText(accuracy: Measurement): UiText =
  Res.string.app_gnss_accuracy.toUiText(accuracy.toUiText())
