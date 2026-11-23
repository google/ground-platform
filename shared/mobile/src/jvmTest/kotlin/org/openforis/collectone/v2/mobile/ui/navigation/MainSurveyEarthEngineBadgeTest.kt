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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.ui.common.EARTH_ENGINE_MAP_BADGE_TAG
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.collectMainSurveyUiState

@OptIn(ExperimentalTestApi::class)
class MainSurveyEarthEngineBadgeTest {
  private val screenshotPath: String? = System.getenv("MOBILE_MAP_BADGE_PNG")

  @Test
  fun mobileMap_showsPoweredByEarthEngineBadgeOnlyWhenEarthEngineLayerIsEnabled() =
    runDesktopComposeUiTest(width = 404, height = 840) {
      val state =
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      setContent { AppTheme { MainSurveyScreenOf(state) } }
      waitForIdle()

      assertFalse(state.viewModels.surveyMap.uiState.value.isEarthEngineLayerShown)
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertDoesNotExist()

      val eeSource =
        state.viewModels.surveyMap.uiState.value.availableImagerySources.first {
          it.type == ImagerySourceType.EARTH_ENGINE
        }
      state.viewModels.surveyMap.toggleImagerySource(eeSource.id)
      waitForIdle()

      assertTrue(state.viewModels.surveyMap.uiState.value.isEarthEngineLayerShown)
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertExists()
      onNodeWithText("Powered by Google Earth Engine").assertExists()

      // Verify the badge sits cleanly within the mobile viewport without clipping.
      val root = onRoot().getUnclippedBoundsInRoot()
      val badge = onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).getUnclippedBoundsInRoot()
      assertTrue(badge.left >= root.left)
      assertTrue(badge.right <= root.right)

      screenshotPath?.let { path ->
        val bitmap = onRoot().captureToImage().asSkiaBitmap()
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)
        File(path).writeBytes(png!!.bytes)
      }

      state.viewModels.surveyMap.toggleImagerySource(eeSource.id)
      waitForIdle()
      assertFalse(state.viewModels.surveyMap.uiState.value.isEarthEngineLayerShown)
      onNodeWithTag(EARTH_ENGINE_MAP_BADGE_TAG).assertDoesNotExist()
    }
}

/** The Main Survey of [state], without the navigation graph and Koin. */
@Composable
private fun MainSurveyScreenOf(state: MobileAppState) {
  val mapUiState by state.viewModels.surveyMap.uiState.collectAsState()
  val dataCollectionUiState by state.viewModels.dataCollection.uiState.collectAsState()
  MainSurveyScreen(
    uiState = state.collectMainSurveyUiState(),
    mapUiState = mapUiState,
    dataCollectionUiState = dataCollectionUiState,
    actions = state,
    mapActions = state.viewModels.surveyMap,
    dataCollectionActions = state.viewModels.dataCollection,
    listActions = state.searchableListActions,
    detailsActions = state.entityDetailsActions,
    offlineAreaActions = state.offlineAreaSelection,
  )
}
