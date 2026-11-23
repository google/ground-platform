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
package org.openforis.collectone.v2.web.ui.timeseries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.client.timeseries.PlotImageryRepository

class PlotImageryRepositoryTest {
  private val polygon =
    TimeSeriesPlot(
      surveyId = "survey-1",
      entityId = "plot-1",
      label = "Plot 1",
      geometryType = GeometryType.POLYGON,
      areaHectares = 4.8,
      vertices =
        listOf(LatLng(-0.4190, 36.9500), LatLng(-0.4190, 36.9520), LatLng(-0.4205, 36.9520)),
    )

  private val repository = PlotImageryRepository(SeedUserDataService().client())

  @Test
  fun statistics_areCachedPerPlot() = runTest {
    assertNull(repository.cachedStatistics(polygon))
    val loaded = repository.statistics(polygon)
    assertSame(loaded, repository.cachedStatistics(polygon))
    assertSame(loaded, repository.statistics(polygon))
  }

  @Test
  fun timeSeries_areCachedPerPlotAndSource() = runTest {
    val landsat = repository.timeSeries(polygon, TimeSeriesSource.LANDSAT)
    assertEquals(TimeSeriesSource.LANDSAT, landsat.source)
    assertTrue(landsat.size > 0)
    assertEquals(landsat.size, landsat.sceneIds.size)
    assertSame(landsat, repository.cachedTimeSeries(polygon, TimeSeriesSource.LANDSAT))
    assertNull(repository.cachedTimeSeries(polygon, TimeSeriesSource.SENTINEL_2))
  }

  @Test
  fun editingThePlotsGeometry_missesTheCache() = runTest {
    repository.statistics(polygon)
    val edited = polygon.copy(vertices = polygon.vertices + LatLng(-0.4205, 36.9500))
    assertNotEquals(PlotImageryRepository.plotKey(polygon), PlotImageryRepository.plotKey(edited))
    assertNull(repository.cachedStatistics(edited))
  }

  @Test
  fun sceneTiles_returnTheSignedTemplate() = runTest {
    val series = repository.timeSeries(polygon, TimeSeriesSource.LANDSAT)
    val tiles = repository.sceneTiles("survey-1", series.sceneIds.first(), SceneStretch.TRUE_COLOR)
    assertTrue(tiles.urlTemplate.isNotBlank())
  }

  @Test
  fun plotRef_sendsNoGeometryForDegeneratePolygons() {
    val twoVertices = polygon.copy(vertices = polygon.vertices.take(2))
    assertNull(PlotImageryRepository.plotRef(twoVertices).geometry)
    assertNull(PlotImageryRepository.plotRef(polygon.copy(geometryType = null)).geometry)
  }
}
