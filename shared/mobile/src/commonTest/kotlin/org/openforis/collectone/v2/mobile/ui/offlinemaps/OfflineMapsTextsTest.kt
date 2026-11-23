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

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.OfflineAreaEstimate
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_crosses_antimeridian
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate_failed
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_estimate_size
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_failed
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_not_enough_space
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_too_large
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_unavailable

/** The area selection card words each verdict of an estimate, and why a save stopped. */
class OfflineMapsTextsTest {
  private val types = setOf(OfflineImageryType.entries.first())
  private val size = DataSize.ofMegabytes(48)

  private fun estimate(verdict: OfflineAreaVerdict, freeSpace: DataSize? = null) =
    OfflineAreaEstimate(
      areaKm2 = 31.0,
      types = types,
      verdict = verdict,
      sizes = types.associateWith { size },
      freeSpace = freeSpace,
    )

  @Test
  fun ready_showsTheSize_andTheFreeSpaceWhenKnown() {
    val free = DataSize.ofMegabytes(2_000)
    assertEquals(
      Res.string.mobile_offline_area_estimate.toUiText(size.toUiText(), free.toUiText()),
      estimateText(estimate(OfflineAreaVerdict.Ready, freeSpace = free)),
    )
    // Some devices can't report their free space.
    assertEquals(
      Res.string.mobile_offline_area_estimate_size.toUiText(size.toUiText()),
      estimateText(estimate(OfflineAreaVerdict.Ready)),
    )
  }

  @Test
  fun tooLarge_namesBothAreasInWholeSquareKilometers() {
    fun km2(value: Double) = Measurement(value, MeasurementUnit.SQUARE_KILOMETER, 0).toUiText()
    assertEquals(
      Res.string.mobile_offline_area_too_large.toUiText(km2(140.0), km2(50.0)),
      estimateText(estimate(OfflineAreaVerdict.TooLarge(maxAreaKm2 = 50.0, areaKm2 = 139.6))),
    )
  }

  @Test
  fun notEnoughSpace_namesTheShortfall() {
    val shortfall = DataSize.ofMegabytes(12)
    assertEquals(
      Res.string.mobile_offline_area_not_enough_space.toUiText(shortfall.toUiText()),
      estimateText(estimate(OfflineAreaVerdict.NotEnoughSpace(shortfall))),
    )
  }

  @Test
  fun otherVerdicts_sayWhyTheAreaCantBeSaved() {
    assertEquals(
      Res.string.mobile_offline_area_crosses_antimeridian.toUiText(),
      estimateText(estimate(OfflineAreaVerdict.CrossesAntimeridian)),
    )
    assertEquals(
      Res.string.mobile_offline_area_unavailable.toUiText(),
      estimateText(estimate(OfflineAreaVerdict.Unavailable)),
    )
    assertEquals(
      Res.string.mobile_offline_area_estimate_failed.toUiText(),
      estimateText(estimate(OfflineAreaVerdict.Failed(DomainError.Network))),
    )
  }

  @Test
  fun saveFailure_ofAnotherKind_orUnknown_isGeneric() {
    val generic = Res.string.mobile_offline_area_failed.toUiText()
    assertEquals(generic, saveFailureText(DomainError.Validation))
    assertEquals(generic, saveFailureText(null))
  }
}
