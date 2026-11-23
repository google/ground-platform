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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/** The web dashboard's draw-on-map host and the form geometry helpers around it. */
class WebMapDrawingHostTest {
  private val formXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Geometry Survey</h:title>
        <model>
          <instance>
            <data id="geometry_survey" version="1">
              <plot_center/>
              <field_boundary/>
              <notes/>
            </data>
          </instance>
          <bind nodeset="/data/plot_center" type="geopoint"/>
          <bind nodeset="/data/field_boundary" type="geoshape"/>
          <bind nodeset="/data/notes" type="string"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/plot_center"><label>Plot Center</label></input>
        <input ref="/data/field_boundary"><label>Field Boundary</label></input>
        <input ref="/data/notes"><label>Notes</label></input>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private val point = "/data/plot_center"
  private val polygon = "/data/field_boundary"

  private val controller =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(formXml))
  private val framed = mutableListOf<Pair<LngLatBounds, Double>>()
  private val host =
    WebMapDrawingHost(controller = { controller }, onFrame = { b, zoom -> framed += b to zoom })

  @Test
  fun clicks_areIgnoredWhileNothingIsBeingDrawn() {
    host.addVertex(LatLng(-1.0, 36.0))
    host.undoVertex()
    host.cancelDrawing()

    assertFalse(host.isDrawing)
    assertNull(host.draftGeometry)
    assertTrue(formGeometryOverlays(controller).isEmpty())
  }

  @Test
  fun aPoint_isPlacedByItsFirstClick_whichStopsTheDrawing() {
    host.startDrawing(point, MapDrawingKind.POINT)
    assertTrue(host.isDrawing)
    assertEquals(MapDrawingKind.POINT, host.activeDrawingKind)

    host.addVertex(LatLng(-1.29, 36.82))

    assertFalse(host.isDrawing)
    val overlay = formGeometryOverlays(controller).single()
    assertEquals(point, overlay.path)
    assertEquals("Plot Center", overlay.title)
    assertEquals(listOf(LatLng(-1.29, 36.82)), overlay.vertices)
  }

  @Test
  fun aPolygon_collectsVerticesUntilDone_andItsDraftIsLeftOutOfTheOverlays() {
    host.startDrawing(polygon, MapDrawingKind.POLYGON)
    host.addVertex(LatLng(-1.0, 36.0))
    host.addVertex(LatLng(-1.0, 36.1))
    host.addVertex(LatLng(-1.1, 36.1))
    host.undoVertex()

    assertTrue(host.isDrawing)
    assertEquals(MapDrawingKind.POLYGON, host.draftGeometry?.kind)
    assertEquals(listOf(LatLng(-1.0, 36.0), LatLng(-1.0, 36.1)), host.draftGeometry?.vertices)
    assertTrue(formGeometryOverlays(controller, excludePath = host.activeDrawingPath).isEmpty())

    host.stopDrawing()

    assertNull(host.draftGeometry)
    assertEquals(polygon, formGeometryOverlays(controller).single().path)
  }

  @Test
  fun cancelDrawing_putsBackTheAnswerFromBeforeTheDrawingStarted() {
    host.startDrawing(polygon, MapDrawingKind.POLYGON)
    host.addVertex(LatLng(-1.0, 36.0))
    host.stopDrawing()

    host.startDrawing(polygon, MapDrawingKind.POLYGON)
    host.addVertex(LatLng(-2.0, 37.0))
    host.cancelDrawing()

    assertFalse(host.isDrawing)
    assertEquals(listOf(LatLng(-1.0, 36.0)), formGeometryOverlays(controller).single().vertices)
  }

  @Test
  fun frameGeometry_framesDrawnAnswersOnly_withTheirKindsZoomLimit() {
    host.frameGeometry(polygon, MapDrawingKind.POLYGON)
    assertTrue(framed.isEmpty())

    host.startDrawing(point, MapDrawingKind.POINT)
    host.addVertex(LatLng(-1.29, 36.82))
    host.startDrawing(polygon, MapDrawingKind.POLYGON)
    host.addVertex(LatLng(-1.0, 36.0))
    host.addVertex(LatLng(-1.2, 36.3))
    host.frameGeometry(point, MapDrawingKind.POINT)
    host.frameGeometry(polygon, MapDrawingKind.POLYGON)

    assertEquals(listOf(16.0, 17.0), framed.map { it.second })
    assertEquals(
      LngLatBounds.of(listOf(LatLng(-1.0, 36.0), LatLng(-1.2, 36.3))),
      framed.last().first,
    )
    assertTrue(host.canFrameGeometry)
  }

  @Test
  fun withoutAnOpenForm_drawingWritesNothing() {
    val closed = WebMapDrawingHost(controller = { null })

    closed.startDrawing(polygon, MapDrawingKind.POLYGON)
    closed.addVertex(LatLng(-1.0, 36.0))
    closed.frameGeometry(polygon, MapDrawingKind.POLYGON)

    assertTrue(closed.isDrawing)
    assertNull(closed.draftGeometry)
    assertNull(formGeometryBounds(emptyList()))
  }
}
