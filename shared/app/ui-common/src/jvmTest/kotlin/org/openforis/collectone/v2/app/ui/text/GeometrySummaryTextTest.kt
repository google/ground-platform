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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

class GeometrySummaryTextTest {
  private fun entity(geometry: Geometry?) =
    GeospatialEntityItem(
      id = "plot-1",
      label = "Plot 1",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "layer-plots",
      geoId = "",
      geometry = geometry,
      areaHectares = 1.0,
      perimeterMeters = 400,
      colorHex = 0xFF2E7D32L,
      properties = emptyMap(),
      submissions = emptyList(),
    )

  private val plotBoundary =
    Geometry.Polygon(
      listOf(
        listOf(LatLng(-0.4, 36.9), LatLng(-0.4, 36.91), LatLng(-0.41, 36.91), LatLng(-0.4, 36.9))
      )
    )

  private val trail = Geometry.LineString(listOf(LatLng(-0.4, 36.9), LatLng(-0.41, 36.91)))

  private fun english(
    entity: GeospatialEntityItem,
    unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  ): String = runBlocking { entityGeometrySummaryText(entity, unitSystem).resolveString() }

  @Test
  fun point_isSummarizedByItsCoordinates() {
    val station = entity(Geometry.Point(LatLng(-0.4212, 36.9518)))

    assertEquals("Point • 0.4212°S, 36.9518°E", english(station))
  }

  @Test
  fun metricPolygon_isSummarizedByHectaresAndMeters() {
    val plot = entity(plotBoundary).copy(areaHectares = 1.42, perimeterMeters = 480)

    assertEquals("Polygon • 1.42 ha, 480 m perimeter", english(plot))
  }

  @Test
  fun metricPolygon_keepsTheStoredHectaresAsWritten() {
    val plot = entity(plotBoundary).copy(areaHectares = 2.0)

    assertEquals("Polygon • 2.0 ha, 400 m perimeter", english(plot))
  }

  @Test
  fun imperialPolygon_isSummarizedByAcresAndFeet() {
    val plot = entity(plotBoundary).copy(areaHectares = 1.42, perimeterMeters = 480)

    assertEquals(
      "Polygon • 3.51 acres, 1575 ft perimeter",
      english(plot, MeasurementUnitSystem.IMPERIAL),
    )
  }

  @Test
  fun line_isSummarizedByItsLength() {
    val path = entity(trail).copy(perimeterMeters = 1250)

    assertEquals("Line • 1250 m", english(path))
    assertEquals("Line • 4101 ft", english(path, MeasurementUnitSystem.IMPERIAL))
  }
}
