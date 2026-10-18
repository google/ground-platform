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
package org.groundplatform.v2.core.forms.ui

import groundplatform.v2.forms.DataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

class CompactMapDrawingTest {

  private val formXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms"
            xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Geometry Survey</h:title>
        <model>
          <instance>
            <data id="geometry_survey" version="1">
              <plot_center/>
              <trail_path/>
              <field_boundary/>
            </data>
          </instance>
          <bind nodeset="/data/plot_center" type="geopoint"/>
          <bind nodeset="/data/trail_path" type="geotrace"/>
          <bind nodeset="/data/field_boundary" type="geoshape"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/plot_center"><label>Plot Center</label></input>
        <input ref="/data/trail_path"><label>Trail Path</label></input>
        <input ref="/data/field_boundary"><label>Field Boundary</label></input>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private val point = "/data/plot_center"
  private val line = "/data/trail_path"
  private val polygon = "/data/field_boundary"

  private fun controller() =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(formXml))

  private fun vertices(controller: FormWizardController, path: String, kind: MapDrawingKind) =
    mapDrawingVertices(controller.formState.fieldStates.getValue(path), kind).map {
      it.latitude to it.longitude
    }

  @Test
  fun mapDrawingKindOf_mapsGeometryTypesOnly() {
    assertEquals(MapDrawingKind.POINT, mapDrawingKindOf(DataType.TYPE_GEOPOINT))
    assertEquals(MapDrawingKind.LINE, mapDrawingKindOf(DataType.TYPE_GEOTRACE))
    assertEquals(MapDrawingKind.POLYGON, mapDrawingKindOf(DataType.TYPE_GEOSHAPE))
    assertNull(mapDrawingKindOf(DataType.TYPE_STRING))
    assertNull(mapDrawingKindOf(DataType.TYPE_INT32))
  }

  @Test
  fun addMapDrawingVertex_pointIsReplacedByEachClick() {
    val controller = controller()
    assertTrue(vertices(controller, point, MapDrawingKind.POINT).isEmpty())

    val first = controller.addMapDrawingVertex(point, MapDrawingKind.POINT, -1.29, 36.82)
    assertEquals(1, first.size)
    assertEquals(listOf(-1.29 to 36.82), vertices(controller, point, MapDrawingKind.POINT))

    val second = controller.addMapDrawingVertex(point, MapDrawingKind.POINT, -1.30, 36.83)
    assertEquals(1, second.size)
    assertEquals(listOf(-1.30 to 36.83), vertices(controller, point, MapDrawingKind.POINT))
  }

  @Test
  fun addMapDrawingVertex_lineAndPolygonAppendInOrder() {
    val controller = controller()

    controller.addMapDrawingVertex(line, MapDrawingKind.LINE, 0.0, 0.0)
    val afterSecond = controller.addMapDrawingVertex(line, MapDrawingKind.LINE, 1.0, 1.0)
    assertEquals(2, afterSecond.size)
    assertEquals(listOf(0.0 to 0.0, 1.0 to 1.0), vertices(controller, line, MapDrawingKind.LINE))

    controller.addMapDrawingVertex(polygon, MapDrawingKind.POLYGON, 0.0, 0.0)
    controller.addMapDrawingVertex(polygon, MapDrawingKind.POLYGON, 0.0, 1.0)
    val afterThird = controller.addMapDrawingVertex(polygon, MapDrawingKind.POLYGON, 1.0, 1.0)
    assertEquals(3, afterThird.size)
    assertEquals(
      listOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0),
      vertices(controller, polygon, MapDrawingKind.POLYGON),
    )
    // Drawing one question leaves the others alone.
    assertEquals(2, vertices(controller, line, MapDrawingKind.LINE).size)
  }

  @Test
  fun undoMapDrawingVertex_removesLastVertex_andClearsWhenNoneLeft() {
    val controller = controller()
    controller.addMapDrawingVertex(line, MapDrawingKind.LINE, 0.0, 0.0)
    controller.addMapDrawingVertex(line, MapDrawingKind.LINE, 1.0, 1.0)
    controller.addMapDrawingVertex(line, MapDrawingKind.LINE, 2.0, 2.0)

    controller.undoMapDrawingVertex(line, MapDrawingKind.LINE)
    assertEquals(listOf(0.0 to 0.0, 1.0 to 1.0), vertices(controller, line, MapDrawingKind.LINE))

    controller.undoMapDrawingVertex(line, MapDrawingKind.LINE)
    controller.undoMapDrawingVertex(line, MapDrawingKind.LINE)
    assertTrue(vertices(controller, line, MapDrawingKind.LINE).isEmpty())

    // Undoing an empty question is a no-op.
    controller.undoMapDrawingVertex(line, MapDrawingKind.LINE)
    assertTrue(vertices(controller, line, MapDrawingKind.LINE).isEmpty())

    controller.addMapDrawingVertex(point, MapDrawingKind.POINT, -1.29, 36.82)
    controller.undoMapDrawingVertex(point, MapDrawingKind.POINT)
    assertTrue(vertices(controller, point, MapDrawingKind.POINT).isEmpty())
  }

  @Test
  fun setMapDrawingVertices_emptyClearsTheAnswer() {
    val controller = controller()
    controller.addMapDrawingVertex(polygon, MapDrawingKind.POLYGON, 0.0, 0.0)
    controller.addMapDrawingVertex(polygon, MapDrawingKind.POLYGON, 0.0, 1.0)
    controller.setMapDrawingVertices(polygon, MapDrawingKind.POLYGON, emptyList())
    assertTrue(vertices(controller, polygon, MapDrawingKind.POLYGON).isEmpty())
  }

  @Test
  fun mapDrawingSummary_describesProgressPerKind() {
    assertEquals("No point yet", mapDrawingSummary(MapDrawingKind.POINT, 0))
    assertEquals("Point placed", mapDrawingSummary(MapDrawingKind.POINT, 1))
    assertEquals("1 vertex", mapDrawingSummary(MapDrawingKind.LINE, 1))
    assertEquals("3 vertices", mapDrawingSummary(MapDrawingKind.POLYGON, 3))
    assertEquals("0 vertices", mapDrawingSummary(MapDrawingKind.POLYGON, 0))
  }

  @Test
  fun minVertices_matchValidGeometrySizes() {
    assertEquals(1, MapDrawingKind.POINT.minVertices)
    assertEquals(2, MapDrawingKind.LINE.minVertices)
    assertEquals(3, MapDrawingKind.POLYGON.minVertices)
  }
}
