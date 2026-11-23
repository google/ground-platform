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
package org.openforis.collectone.v2.app.ui.map

import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrl
import org.openforis.collectone.v2.map.RasterTileLayer

/**
 * Earth Engine overlay tile URL resolution and TTL refresh in [SurveyMapViewModel].
 *
 * The refresh loop never goes idle while a layer is enabled, so these tests step virtual time with
 * [runCurrent] and [advanceTimeBy] rather than `advanceUntilIdle` (which would never return).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyMapViewModelEarthEngineTest {
  private val eeSourceId = "imagery-all-users-ee-s2-true-color"

  @Test
  fun initially_resolvesNoTileUrls() = runEarthEngineTest { viewModel, requests ->
    assertTrue(viewModel.uiState.value.resolvedOverlayTileUrls.isEmpty())
    assertFalse(viewModel.uiState.value.isEarthEngineLayerShown)
    assertTrue(requests.isEmpty())
  }

  @Test
  fun enablingEarthEngineLayer_resolvesItsTileUrl() = runEarthEngineTest { viewModel, requests ->
    viewModel.toggleImagerySource(eeSourceId)
    runCurrent()

    val enabled = viewModel.uiState.value
    assertTrue(enabled.isEarthEngineLayerShown)
    assertEquals(listOf(eeSourceId), requests.map { (_, sourceId) -> sourceId })
    val firstUrl = enabled.resolvedOverlayTileUrls.getValue(eeSourceId)
    assertTrue(firstUrl.endsWith("-1/tiles/{z}/{x}/{y}"))
    // The renderable source carries the resolved URL, so the basemap draws it.
    assertEquals(firstUrl, enabled.enabledImagerySources.single { it.id == eeSourceId }.urlTemplate)
  }

  @Test
  fun enabledEarthEngineLayer_isNotRefreshedBeforeTtl() =
    runEarthEngineTest { viewModel, requests ->
      viewModel.toggleImagerySource(eeSourceId)
      runCurrent()

      advanceTimeBy((TTL_SECONDS - 1) * 1000L)
      runCurrent()

      assertEquals(1, requests.size)
    }

  @Test
  fun enabledEarthEngineLayer_isRefreshedAfterTtl() = runEarthEngineTest { viewModel, requests ->
    viewModel.toggleImagerySource(eeSourceId)
    runCurrent()

    advanceTimeBy((TTL_SECONDS + 1) * 1000L)
    runCurrent()

    assertEquals(2, requests.size)
    assertTrue(
      viewModel.uiState.value.resolvedOverlayTileUrls
        .getValue(eeSourceId)
        .endsWith("-2/tiles/{z}/{x}/{y}")
    )
  }

  @Test
  fun failedResolution_isRetriedAfterThirtySeconds() =
    runEarthEngineTest(failedResolutions = 1) { viewModel, requests ->
      viewModel.toggleImagerySource(eeSourceId)
      runCurrent()
      assertTrue(viewModel.uiState.value.resolvedOverlayTileUrls.isEmpty())

      advanceTimeBy(RETRY_DELAY_SECONDS * 1000L + 1)
      runCurrent()

      assertEquals(2, requests.size)
      assertTrue(
        viewModel.uiState.value.resolvedOverlayTileUrls
          .getValue(eeSourceId)
          .endsWith("-2/tiles/{z}/{x}/{y}")
      )
    }

  @Test
  fun disablingEarthEngineLayer_clearsItsTileUrl() = runEarthEngineTest { viewModel, _ ->
    viewModel.toggleImagerySource(eeSourceId)
    runCurrent()

    viewModel.toggleImagerySource(eeSourceId)
    runCurrent()

    assertFalse(eeSourceId in viewModel.uiState.value.resolvedOverlayTileUrls)
    assertFalse(viewModel.uiState.value.isEarthEngineLayerShown)
  }

  @Test
  fun disablingEarthEngineLayer_stopsTheRefresh() = runEarthEngineTest { viewModel, requests ->
    viewModel.toggleImagerySource(eeSourceId)
    runCurrent()
    viewModel.toggleImagerySource(eeSourceId)
    runCurrent()

    advanceTimeBy(TTL_SECONDS * 2 * 1000L)
    runCurrent()

    assertEquals(1, requests.size)
  }

  @Test
  fun xyzLayer_doesNotShowEarthEngineAttribution() = runEarthEngineTest { viewModel, _ ->
    val xyzSourceId =
      viewModel.uiState.value.availableImagerySources
        .first { it.type == ImagerySourceType.XYZ_TILES }
        .id

    viewModel.toggleImagerySource(xyzSourceId)
    runCurrent()

    assertFalse(viewModel.uiState.value.isEarthEngineLayerShown)
  }

  @Test
  fun pinnedScene_showsEarthEngineAttribution() = runEarthEngineTest { viewModel, _ ->
    viewModel.showPinnedScene(pinnedScene)
    runCurrent()

    assertTrue(viewModel.uiState.value.isEarthEngineLayerShown)
  }

  @Test
  fun clearedPinnedScene_hidesEarthEngineAttribution() = runEarthEngineTest { viewModel, _ ->
    viewModel.showPinnedScene(pinnedScene)
    runCurrent()

    viewModel.showPinnedScene(null)
    runCurrent()

    assertFalse(viewModel.uiState.value.isEarthEngineLayerShown)
  }

  @Test
  fun surveyBasemaps_skipsUnresolvedEarthEngineSources() {
    val eeSource =
      ImagerySource(
        id = "imagery-unresolved",
        name = "Unresolved EE",
        urlTemplate = "",
        type = ImagerySourceType.EARTH_ENGINE,
      )
    val base = SurveyBasemaps.Streets

    val composite =
      SurveyBasemaps.withImagerySources(base = base, imagerySources = listOf(eeSource))

    assertEquals(base.overlays.size, composite.overlays.size)
  }

  @Test
  fun surveyBasemaps_drawsResolvedEarthEngineSources() {
    val url = "https://earthengine.googleapis.com/v1/projects/p/maps/m/tiles/{z}/{x}/{y}"
    val eeSource =
      ImagerySource(
        id = "imagery-resolved",
        name = "Resolved EE",
        urlTemplate = "",
        type = ImagerySourceType.EARTH_ENGINE,
      )
    val base = SurveyBasemaps.Satellite

    val composite =
      SurveyBasemaps.withImagerySources(
        base = base,
        imagerySources = listOf(eeSource),
        resolvedTileUrls = mapOf(eeSource.id to url),
      )

    assertEquals(base.overlays.size + 1, composite.overlays.size)
    assertEquals(url, (composite.overlays.last() as RasterTileLayer).urlTemplate)
  }

  private val pinnedScene =
    ImagerySource(
      id = "pinned-scene",
      name = "Landsat 8 2023-08-14",
      urlTemplate = "https://earthengine.googleapis.com/v1/mock/scene/{z}/{x}/{y}",
    )

  /**
   * Runs [body] against a [SurveyMapViewModel] over the sample data whose overlay tile URLs are
   * numbered by request (`…-1/tiles/…`, `…-2/tiles/…`) and valid for [TTL_SECONDS]; [body] also
   * receives the (survey, imagery source) of each request. The first [failedResolutions] requests
   * fail.
   */
  private fun runEarthEngineTest(
    failedResolutions: Int = 0,
    body: suspend TestScope.(SurveyMapViewModel, List<Pair<String, String>>) -> Unit,
  ) = runTest {
    val requests = mutableListOf<Pair<String, String>>()
    val dataHolder = sampleDataHolder()
    val viewModel =
      SurveyMapViewModel(
        surveyRepository = dataHolder.surveyRepository,
        organizationRepository = dataHolder.organizationRepository,
        settingsRepository = dataHolder.settingsRepository,
        locationRepository = dataHolder.locationRepository,
        placeRepository = dataHolder.placeRepository,
        connectivityRepository = dataHolder.connectivityRepository,
        clusterMapFeatures = dataHolder.clusterMapFeaturesUseCase,
        computeWayfindingNavigation = dataHolder.computeWayfindingNavigationUseCase,
        overlayTileUrls = { surveyId, sourceId ->
          requests += surveyId to sourceId
          if (requests.size <= failedResolutions) {
            DomainResult.Failure(DomainError.Network)
          } else {
            DomainResult.Success(
              OverlayTileUrl(
                urlTemplate =
                  "https://earthengine.googleapis.com/v1/mock/maps/$sourceId-${requests.size}" +
                    "/tiles/{z}/{x}/{y}",
                ttlSeconds = TTL_SECONDS,
              )
            )
          }
        },
        // Virtual time, so the test can step past tile URL expiry.
        dispatcher = StandardTestDispatcher(testScheduler),
      )
    try {
      runCurrent()
      body(viewModel, requests)
    } finally {
      viewModel.viewModelScope.cancel()
    }
  }

  private companion object {
    const val TTL_SECONDS = 7200L

    /** How long the view model waits before resolving a tile URL again after a failure. */
    const val RETRY_DELAY_SECONDS = 30L
  }
}
