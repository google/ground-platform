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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind

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
    assertTrue(
      vectorImperial.formattedDistance.endsWith("ft") ||
        vectorImperial.formattedDistance.endsWith("mi")
    )
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
        userGpsCoordinatesLabel = "-0.4198°, 36.9512°",
        entities = entities,
        allSubmissions = emptyList(),
        submissionGeometries = surveyRepo.getSubmissionGeometries(),
        findPlaceById = { null },
      )
    assertNotNull(navState)
    assertEquals(NavigationTargetKind.ENTITY, navState.targetKind)
  }
}
