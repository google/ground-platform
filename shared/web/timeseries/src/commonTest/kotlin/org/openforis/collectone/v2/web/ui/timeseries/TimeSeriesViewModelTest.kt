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
package org.openforis.collectone.v2.web.ui.timeseries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.GeoDataServiceServer
import openforis.collectone.v1.survey.GetPlotStatisticsRequest
import openforis.collectone.v1.survey.GetPlotStatisticsResponse
import openforis.collectone.v1.survey.GetPlotTimeSeriesRequest
import openforis.collectone.v1.survey.GetPlotTimeSeriesResponse
import openforis.collectone.v1.survey.GetSceneTileUrlRequest
import openforis.collectone.v1.survey.GetSceneTileUrlResponse
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.client.timeseries.PlotImageryRepository

class TimeSeriesViewModelTest {
  private val shownScenes = mutableListOf<ImagerySource?>()

  private fun viewModel(client: CollectOneGrpcClient? = SeedUserDataService().client()) =
    TimeSeriesViewModel(
      repository = client?.let(::PlotImageryRepository),
      showSceneOnMap = { shownScenes += it },
      stepSceneDelayMs = 0L,
    )

  private fun feature(
    id: String = "plot-1",
    geometry: Geometry? =
      Geometry.Polygon(
        listOf(listOf(LatLng(-0.4190, 36.9500), LatLng(-0.4190, 36.9520), LatLng(-0.4205, 36.9520)))
      ),
  ) =
    GeospatialEntityItem(
      id = id,
      label = "Plot $id",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "plots-layer",
      geoId = "",
      geometry = geometry,
      areaHectares = 4.82,
      perimeterMeters = 880,
      colorHex = 0L,
      properties = emptyMap(),
      submissions = emptyList(),
    )

  @Test
  fun follow_loadsOnlyWhileVisible() {
    val vm = viewModel()
    vm.follow("survey-1", feature(), visible = false)
    assertEquals(Loadable.Idle, vm.uiState.value.series)
    assertEquals(Loadable.Idle, vm.uiState.value.statistics)

    vm.follow("survey-1", feature(), visible = true)
    val state = vm.uiState.value
    assertIs<Loadable.Loaded<*>>(state.statistics)
    val series = assertNotNull(state.loadedSeries)
    assertEquals(TimeSeriesSource.LANDSAT, series.source)
    assertTrue(series.validCount("NDVI") > 100)
    assertEquals("Plot plot-1", state.plot?.label)
  }

  @Test
  fun follow_skipsLinesAndFeaturesWithoutALocation() {
    val vm = viewModel()
    vm.follow(
      "survey-1",
      feature(
        geometry = Geometry.LineString(listOf(LatLng(-0.4198, 36.9500), LatLng(-0.4198, 36.9520)))
      ),
      visible = true,
    )
    assertFalse(vm.uiState.value.plot!!.isSupported)
    assertEquals(Loadable.Idle, vm.uiState.value.series)
  }

  @Test
  fun requests_sendTheFeaturesStoredGeometry() {
    val vm = viewModel()
    val polygon = feature()
    vm.follow("survey-1", polygon, visible = false)
    val plot = assertNotNull(vm.uiState.value.plot)
    assertEquals((polygon.geometry as Geometry.Polygon).rings.single(), plot.vertices)
    val polygonRef = PlotImageryRepository.plotRef(plot)
    assertEquals("plot-1", polygonRef.entity_id)
    // The ring is sent closed, like every stored polygon.
    assertEquals(
      (plot.vertices + plot.vertices.first()).map { it.latitude to it.longitude },
      assertNotNull(polygonRef.geometry?.polygon).rings.single().positions.map {
        it.latitude to it.longitude
      },
    )

    // Points are sent as points; like the server, the demo backend gives them no population.
    vm.follow(
      "survey-1",
      feature("plot-2", geometry = Geometry.Point(LatLng(-0.4198, 36.9512))),
      visible = true,
    )
    val point =
      assertNotNull(PlotImageryRepository.plotRef(vm.uiState.value.plot!!).geometry?.point)
    assertEquals(-0.4198, point.latitude, 1e-9)
    assertEquals(36.9512, point.longitude, 1e-9)
    val statistics = assertIs<Loadable.Loaded<PlotStatistics>>(vm.uiState.value.statistics)
    assertNull(statistics.value.population)
  }

  @Test
  fun withoutABackend_thePanelIsUnavailable() {
    val vm = viewModel(client = null)
    vm.follow("survey-1", feature(), visible = true)
    assertFalse(vm.uiState.value.isAvailable)
    assertEquals(Loadable.Idle, vm.uiState.value.series)
  }

  @Test
  fun selectSource_keepsTheBandWhenAvailableAndReloads() {
    val vm = viewModel()
    vm.follow("survey-1", feature(), visible = true)
    vm.selectBand("NBR")
    vm.selectSource(TimeSeriesSource.SENTINEL_2)
    assertEquals("NBR", vm.uiState.value.band)
    assertEquals(TimeSeriesSource.SENTINEL_2, vm.uiState.value.loadedSeries?.source)

    vm.selectSource(TimeSeriesSource.SENTINEL_1)
    assertEquals("VV", vm.uiState.value.band)
    assertEquals(TimeSeriesSource.SENTINEL_1, vm.uiState.value.loadedSeries?.source)
  }

  @Test
  fun selectObservation_pinsItsSceneOnTheMap() {
    val vm = viewModel()
    vm.follow("survey-1", feature(), visible = true)
    val series = vm.uiState.value.loadedSeries!!
    val index = series.values("NDVI").indexOfFirst { !it.isNaN() }

    vm.selectObservation(index)
    val pinned = assertNotNull(vm.uiState.value.pinnedScene)
    assertEquals(index, pinned.observation.index)
    assertEquals(SceneStretch.SWIR_NIR_RED, pinned.stretch)
    val tiles = assertIs<Loadable.Loaded<String>>(pinned.tiles)
    assertEquals(tiles.value, shownScenes.last()?.urlTemplate)
    assertEquals(TimeSeriesViewModel.PINNED_SCENE_SOURCE_ID, shownScenes.last()?.id)

    vm.selectStretch(SceneStretch.TRUE_COLOR)
    assertEquals(SceneStretch.TRUE_COLOR, vm.uiState.value.pinnedScene?.stretch)

    vm.clearPinnedScene()
    assertNull(vm.uiState.value.pinnedScene)
    assertNull(vm.uiState.value.selectedIndex)
    assertNull(shownScenes.last())
  }

  @Test
  fun stepObservation_movesBetweenObservationsWithValues() {
    val vm = viewModel()
    vm.follow("survey-1", feature(), visible = true)
    val values = vm.uiState.value.loadedSeries!!.values("NDVI")
    val valid = values.indices.filter { !values[it].isNaN() }

    vm.stepObservation(1)
    assertEquals(valid.first(), vm.uiState.value.selectedIndex)
    vm.stepObservation(1)
    assertEquals(valid[1], vm.uiState.value.selectedIndex)
    vm.stepObservation(-5)
    assertEquals(valid.first(), vm.uiState.value.selectedIndex)
    assertNotNull(vm.uiState.value.pinnedScene)
  }

  @Test
  fun selectingAnotherFeature_clearsThePinnedScene() {
    val vm = viewModel()
    vm.follow("survey-1", feature("plot-1"), visible = true)
    vm.stepObservation(1)
    assertNotNull(vm.uiState.value.pinnedScene)

    vm.follow("survey-1", feature("plot-2"), visible = true)
    assertNull(vm.uiState.value.pinnedScene)
    assertNull(shownScenes.last())
    assertEquals("plot-2", vm.uiState.value.plot?.entityId)
  }

  @Test
  fun failure_mapsGrpcStatuses() {
    fun reason(status: Int) =
      PlotImageryRepository.failure(GrpcWebException(status, "/path", "detail")).reason
    assertEquals(TimeSeriesError.NOT_UPLOADED, reason(GrpcWebTransport.NOT_FOUND))
    assertEquals(TimeSeriesError.UNSUPPORTED_PLOT, reason(GrpcWebTransport.INVALID_ARGUMENT))
    assertEquals(TimeSeriesError.BUSY, reason(8))
    assertEquals(TimeSeriesError.TIMEOUT, reason(4))
    assertEquals(TimeSeriesError.UNAVAILABLE, reason(GrpcWebTransport.UNAVAILABLE))
    assertEquals(TimeSeriesError.DENIED, reason(GrpcWebTransport.PERMISSION_DENIED))
    assertEquals(
      TimeSeriesError.OTHER,
      PlotImageryRepository.failure(IllegalStateException()).reason,
    )
  }

  @Test
  fun retry_reloadsFailedStatisticsSeriesAndPinnedScene() {
    val seed = SeedUserDataService().client()
    val flaky = FlakyGeoDataService(assertNotNull(seed.geoDataService))
    val client =
      CollectOneGrpcClient(
        surveyService = seed.surveyService,
        organizationService = seed.organizationService,
        libraryService = seed.libraryService,
        dataService = seed.dataService,
        geoDataService = flaky,
        syncService = seed.syncService,
      )
    val vm = viewModel(client)

    flaky.failStatistics = true
    flaky.failSeries = true
    vm.follow("survey-1", feature(), visible = true)
    assertIs<Loadable.Failed>(vm.uiState.value.statistics)
    assertIs<Loadable.Failed>(vm.uiState.value.series)

    // Recover statistics and series, then fail pinning a scene and retry it.
    flaky.failStatistics = false
    flaky.failSeries = false
    vm.retry()
    assertIs<Loadable.Loaded<PlotStatistics>>(vm.uiState.value.statistics)
    val series = assertNotNull(vm.uiState.value.loadedSeries)

    flaky.failScenes = true
    val firstValid = series.values("NDVI").indexOfFirst { !it.isNaN() }
    vm.selectObservation(firstValid)
    assertIs<Loadable.Failed>(assertNotNull(vm.uiState.value.pinnedScene).tiles)
    assertNull(shownScenes.last())

    flaky.failScenes = false
    vm.retry()
    assertIs<Loadable.Loaded<String>>(assertNotNull(vm.uiState.value.pinnedScene).tiles)
    assertNotNull(shownScenes.last())
  }

  @Test
  fun follow_reusesCachedResultsAndUpdatesMetadataWithoutRefetching() {
    val seed = SeedUserDataService().client()
    val counting = FlakyGeoDataService(assertNotNull(seed.geoDataService))
    val client =
      CollectOneGrpcClient(
        surveyService = seed.surveyService,
        organizationService = seed.organizationService,
        libraryService = seed.libraryService,
        dataService = seed.dataService,
        geoDataService = counting,
        syncService = seed.syncService,
      )
    val vm = viewModel(client)

    val plot1 = feature("plot-1")
    vm.follow("survey-1", plot1, visible = true)
    assertEquals(1, counting.statisticsCalls)
    assertEquals(1, counting.seriesCalls)

    // Renaming the plot without changing its geometry updates the label without refetching.
    vm.follow("survey-1", plot1.copy(label = "Renamed plot"), visible = true)
    assertEquals("Renamed plot", vm.uiState.value.plot?.label)
    assertEquals(1, counting.statisticsCalls)

    // Switching to another plot and back serves statistics and series from the repository cache.
    vm.follow("survey-1", feature("plot-2"), visible = true)
    assertEquals(2, counting.statisticsCalls)
    vm.follow("survey-1", plot1, visible = true)
    assertEquals(2, counting.statisticsCalls)
    assertEquals(2, counting.seriesCalls)

    // Clearing the followed plot resets plot state.
    vm.follow(null, null, visible = true)
    assertNull(vm.uiState.value.plot)
  }

  @Test
  fun uiStateHelpers_handleOutOfBoundsBandsStretchesAndPlatformLabels() {
    val vm = viewModel()
    vm.follow("survey-1", feature(), visible = true)
    vm.selectBand("NON_EXISTENT_BAND")
    assertEquals("NDVI", vm.uiState.value.band)

    vm.selectObservation(999_999)
    assertNull(vm.uiState.value.selectedObservation)
    assertNull(vm.uiState.value.pinnedScene)

    vm.stepObservation(1)
    assertNotNull(vm.uiState.value.selectedObservation)
    vm.selectObservation(null)
    assertNull(vm.uiState.value.selectedObservation)
    assertNull(vm.uiState.value.pinnedScene)

    assertEquals(
      listOf(SceneStretch.SAR_VV_VH_RATIO),
      TimeSeriesUiState.stretchesOf(TimeSeriesSource.SENTINEL_1),
    )
    assertEquals("Landsat 8", TimeSeriesUiState.platformLabel("LANDSAT_8"))
    assertEquals("Sentinel-2A", TimeSeriesUiState.platformLabel("SENTINEL-2A"))
    assertEquals("MODIS", TimeSeriesUiState.platformLabel("MODIS"))
  }

  private class FlakyGeoDataService(private val delegate: GeoDataServiceServer) :
    GeoDataServiceServer by delegate {
    var failStatistics = false
    var failSeries = false
    var failScenes = false
    var statisticsCalls = 0
    var seriesCalls = 0

    override suspend fun GetPlotStatistics(
      request: GetPlotStatisticsRequest
    ): GetPlotStatisticsResponse {
      statisticsCalls++
      if (failStatistics) throw GrpcWebException(GrpcWebTransport.UNAVAILABLE, "/stats", "offline")
      return delegate.GetPlotStatistics(request)
    }

    override suspend fun GetPlotTimeSeries(
      request: GetPlotTimeSeriesRequest
    ): GetPlotTimeSeriesResponse {
      seriesCalls++
      if (failSeries) throw GrpcWebException(GrpcWebTransport.UNAVAILABLE, "/series", "offline")
      return delegate.GetPlotTimeSeries(request)
    }

    override suspend fun GetSceneTileUrl(request: GetSceneTileUrlRequest): GetSceneTileUrlResponse {
      if (failScenes) throw GrpcWebException(GrpcWebTransport.UNAVAILABLE, "/scene", "offline")
      return delegate.GetSceneTileUrl(request)
    }
  }
}
