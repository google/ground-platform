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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.UserSettings
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/**
 * End-to-end checks that the mobile app reads from and writes through the local data store, which
 * is the single source of truth for app data.
 */
class MobileLocalStoreTest {
  /** Appends [count] map features (copies of the first, without submissions) through the store. */
  private fun MobileAppState.addEntities(count: Int) {
    val existing = viewModels.dataCollection.uiState.value.entities
    val added =
      List(count) { i -> existing.first().copy(id = "entity-extra-$i", submissions = emptyList()) }
    dataHolder.launch { dataHolder.surveyRepository.setEntities(existing + added) }
  }

  @Test
  fun submittedForm_survivesSwitchingSurveysAndBack() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    assertEquals(
      "00000033-6b50-51cd-ac94-baea2ea06d65",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    val before =
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
        .submissions
        .size

    state.viewModels.dataCollection.launchFormForEntity(
      entityId = "00000009-1699-51bb-b7a3-f90a60bedd4d",
      formId = "form-household-interview",
    )
    state.viewModels.dataCollection.completeActiveFormSubmission()
    assertEquals(
      before + 1,
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
        .submissions
        .size,
    )

    state.viewModels.onboarding.openSurvey("00000034-c088-5ab9-8b98-e6bccb3dca71")
    assertEquals(
      "00000034-c088-5ab9-8b98-e6bccb3dca71",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertTrue(
      state.viewModels.dataCollection.uiState.value.entities.none {
        it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d"
      }
    )

    state.viewModels.onboarding.openSurvey("00000033-6b50-51cd-ac94-baea2ea06d65")
    assertEquals(
      before + 1,
      state.viewModels.dataCollection.uiState.value.entities
        .first { it.id == "00000009-1699-51bb-b7a3-f90a60bedd4d" }
        .submissions
        .size,
    )
  }

  @Test
  fun dataHolderAppData_mirrorsStoreData() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.addEntities(count = 25)
    val appData = state.dataHolder.appData.value
    assertEquals(state.viewModels.dataCollection.uiState.value.entities, appData.content.entities)
    assertEquals(state.viewModels.dashboard.uiState.value.mutations, appData.mutations)
    assertEquals(state.viewModels.dashboard.uiState.value.activeSurveyId, appData.activeSurveyId)
  }

  @Test
  fun settings_areWrittenToTheStore() {
    val state =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    state.viewModels.settings.updateMeasurementUnits(MeasurementUnitSystem.IMPERIAL)
    state.viewModels.settings.updateLanguage("fr")
    state.viewModels.settings.updateUploadMediaOverUnmeteredConnectionOnly(true)
    assertEquals(
      UserSettings("fr", MeasurementUnitSystem.IMPERIAL, shouldUploadPhotosOnWifiOnly = true),
      state.dataHolder.appData.value.userSettings,
    )
  }

  @Test
  fun appStates_doNotShareData() {
    val first =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    val second =
      MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
    first.addEntities(count = 10)
    assertEquals(
      first.viewModels.dataCollection.uiState.value.entities.size - 10,
      second.viewModels.dataCollection.uiState.value.entities.size,
    )
  }
}
