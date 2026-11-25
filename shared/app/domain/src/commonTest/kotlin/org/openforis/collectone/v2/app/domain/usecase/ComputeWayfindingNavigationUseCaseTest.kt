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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.map.LatLng

class ComputeWayfindingNavigationUseCaseTest {
  private val useCase = ComputeWayfindingNavigationUseCase()

  @Test
  fun invoke_formatsImperialDistance() {
    val vectorImperial =
      useCase(
        fromNormalizedX = 0.20f,
        fromNormalizedY = 0.20f,
        targetNormalizedX = 0.50f,
        targetNormalizedY = 0.50f,
        unitSystem = MeasurementUnitSystem.IMPERIAL,
      )
    assertTrue(vectorImperial.distance.unit in setOf(MeasurementUnit.FOOT, MeasurementUnit.MILE))
  }

  private fun distanceTo(
    targetNormalizedX: Float,
    targetNormalizedY: Float,
    unitSystem: MeasurementUnitSystem,
  ): Measurement =
    useCase(
        fromNormalizedX = 0f,
        fromNormalizedY = 0f,
        targetNormalizedX = targetNormalizedX,
        targetNormalizedY = targetNormalizedY,
        unitSystem = unitSystem,
      )
      .distance

  @Test
  fun invoke_writesShortDistancesInWholeMetersOrFeet() {
    assertEquals(
      Measurement(156.0, MeasurementUnit.METER, decimals = 0),
      distanceTo(0.1f, 0f, MeasurementUnitSystem.METRIC),
    )
    assertEquals(
      Measurement(512.0, MeasurementUnit.FOOT, decimals = 0),
      distanceTo(0.1f, 0f, MeasurementUnitSystem.IMPERIAL),
    )
  }

  @Test
  fun invoke_writesLongDistancesInTenthsOfKilometersOrMiles() {
    assertEquals(
      Measurement(1.6, MeasurementUnit.KILOMETER, decimals = 1),
      distanceTo(1f, 0f, MeasurementUnitSystem.METRIC),
    )
    assertEquals(
      Measurement(5108.0, MeasurementUnit.FOOT, decimals = 0),
      distanceTo(1f, 0f, MeasurementUnitSystem.IMPERIAL),
    )
    assertEquals(
      Measurement(2.0, MeasurementUnit.KILOMETER, decimals = 1),
      distanceTo(0f, -1f, MeasurementUnitSystem.METRIC),
    )
    assertEquals(
      Measurement(1.2, MeasurementUnit.MILE, decimals = 1),
      distanceTo(0f, -1f, MeasurementUnitSystem.IMPERIAL),
    )
  }

  @Test
  fun invoke_usesGeodesicDistanceAndBearingAtHighLatitude() {
    // 1° longitude at 60°N is ~55.6 km (half the equatorial span), due east (90°).
    val vector =
      useCase(
        from = LatLng(60.0, 0.0),
        to = LatLng(60.0, 1.0),
        unitSystem = MeasurementUnitSystem.METRIC,
      )
    assertTrue(
      vector.distanceMeters in 55_400..55_800,
      "Expected ~55.6 km, got ${vector.distanceMeters}",
    )
    assertEquals(90, vector.bearingDegrees)
    assertEquals("E", vector.cardinalDirection)
  }

  @Test
  fun resolveActiveNavigationState_resolvesTargetEntityVector() = runNow {
    val surveyRepo = SurveyRepositoryImpl(seededStore())
    val entities = surveyRepo.getEntities()
    val navState =
      useCase.resolveActiveNavigationState(
        navigationTargetKind = NavigationTargetKind.ENTITY,
        navigationTargetId = entities.first().id,
        fromNormalizedX = 0.50f,
        fromNormalizedY = 0.50f,
        unitSystem = MeasurementUnitSystem.METRIC,
        entities = entities,
        allSubmissions = emptyList(),
        submissionGeometries = surveyRepo.getSubmissionGeometries(),
        findPlaceById = { null },
        anchor = SurveyMapAnchor.forSurvey(SurveyMapAnchor.DEFAULT_SURVEY_ID),
      )
    assertNotNull(navState)
    assertEquals(NavigationTargetKind.ENTITY, navState.targetKind)
  }
}
