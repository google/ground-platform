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
import kotlin.test.assertNull
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_geometry_line
import org.openforis.collectone.v2.app.resources.domain_geometry_point
import org.openforis.collectone.v2.app.resources.domain_geometry_polygon
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class GeometryTextTest {
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

  @Test
  fun coordinatesOfAPoint_areItsPosition() {
    val station = entity(Geometry.Point(LatLng(-0.4212, 36.9518)))

    assertEquals(coordinatesText(LatLng(-0.4212, 36.9518)), entityCoordinatesText(station))
  }

  @Test
  fun coordinatesOfAPolygon_areTheAverageOfItsRing() {
    val parcel =
      entity(
        Geometry.Polygon(
          listOf(
            listOf(
              LatLng(-0.4192, 36.9479),
              LatLng(-0.4192, 36.9483),
              LatLng(-0.4172, 36.9483),
              LatLng(-0.4172, 36.9479),
            )
          )
        )
      )

    assertEquals(coordinatesText(LatLng(-0.4182, 36.9481)), entityCoordinatesText(parcel))
  }

  @Test
  fun coordinatesOfALine_areItsFirstVertex() {
    val transect =
      entity(Geometry.LineString(listOf(LatLng(-0.4245, 36.9560), LatLng(-0.4245, 36.9580))))

    assertEquals(coordinatesText(LatLng(-0.4245, 36.9560)), entityCoordinatesText(transect))
  }

  @Test
  fun recordWithoutGeometry_hasNoCoordinates() {
    assertNull(entityCoordinatesText(entity(geometry = null)))
  }

  @Test
  fun geometryTypes_areNamedByStringResources() {
    assertEquals(Res.string.domain_geometry_point, GeometryType.POINT.labelRes)
    assertEquals(Res.string.domain_geometry_line, GeometryType.LINE_STRING.labelRes)
    assertEquals(Res.string.domain_geometry_polygon, GeometryType.POLYGON.labelRes)
  }
}
