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
package org.openforis.collectone.v2.mobile.ui.offlinemaps

import kotlin.math.round
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.OfflineAreaEstimate
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.coordinatesText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_crosses_antimeridian
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate_failed
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate_size
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimating
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_failed
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_near
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_not_enough_space
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_offline
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_tile_limit
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_too_large
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_unavailable

// The texts of saved map areas, shared by the area selection mode and Offline maps.

/** The name of a saved or framed area: its place name, or "Area near" its center. */
fun offlineAreaNameText(name: OfflineAreaName): UiText =
  when (name) {
    is OfflineAreaName.Named -> UiText.Plain(name.text)
    is OfflineAreaName.NearCoordinates ->
      Res.string.mobile_offline_area_near.toUiText(coordinatesText(name.position))
  }

/** "Estimating size…", while the frame's estimate runs. */
internal fun estimatingText(): UiText = Res.string.mobile_offline_area_estimating.toUiText()

/**
 * The card's message for [estimate]: its size and the free space when it can be saved, else why
 * not.
 */
internal fun estimateText(estimate: OfflineAreaEstimate): UiText =
  when (val verdict = estimate.verdict) {
    OfflineAreaVerdict.Ready -> {
      val size = estimate.total().toUiText()
      estimate.freeSpace?.let {
        Res.string.mobile_offline_area_estimate.toUiText(size, it.toUiText())
      } ?: Res.string.mobile_offline_area_estimate_size.toUiText(size)
    }
    is OfflineAreaVerdict.TooLarge ->
      Res.string.mobile_offline_area_too_large.toUiText(
        squareKilometers(verdict.areaKm2),
        squareKilometers(verdict.maxAreaKm2),
      )
    is OfflineAreaVerdict.NotEnoughSpace ->
      Res.string.mobile_offline_area_not_enough_space.toUiText(verdict.shortfall.toUiText())
    OfflineAreaVerdict.Offline -> Res.string.mobile_offline_area_offline.toUiText()
    OfflineAreaVerdict.CrossesAntimeridian ->
      Res.string.mobile_offline_area_crosses_antimeridian.toUiText()
    OfflineAreaVerdict.Unavailable -> Res.string.mobile_offline_area_unavailable.toUiText()
    is OfflineAreaVerdict.Failed -> Res.string.mobile_offline_area_estimate_failed.toUiText()
  }

/** Why saving an area stopped: no connection, the device's imagery limit, or another error. */
internal fun saveFailureText(failure: DomainError?): UiText =
  when (failure) {
    DomainError.Network -> Res.string.mobile_offline_area_offline.toUiText()
    DomainError.QuotaExceeded -> Res.string.mobile_offline_area_tile_limit.toUiText()
    else -> Res.string.mobile_offline_area_failed.toUiText()
  }

/** [km2] as a whole number of km², e.g. "140 km²". */
private fun squareKilometers(km2: Double): UiText =
  Measurement(round(km2), MeasurementUnit.SQUARE_KILOMETER, 0).toUiText()
