/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.web.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.ui.map.SurveyMap
import org.groundplatform.v2.app.ui.map.SurveyMapContent
import org.groundplatform.v2.app.ui.map.SurveyMapIds
import org.groundplatform.v2.app.ui.map.SurveyMarker
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.core.forms.ui.mapDrawingVertices
import org.groundplatform.v2.core.forms.ui.setMapDrawingVertices
import org.groundplatform.v2.map.LatLng

/** Drawing a form's geometry answers on the web dashboard's main map. */
class WebAppStateMapDrawingTest {
  /** A web app whose forms launch with the all-field-types example XForms. */
  private fun webAppWithWorkbenchForms(): WebAppState =
    WebAppState().also { web ->
      web.dataCollection.updateCustomFormDef(
        XFormsXmlSerializer.deserializeFormDef(WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml),
        isPreset = true,
      )
    }

  private fun SurveyMap.overlayIds() =
    content.sources.first { it.id == SurveyMapContent.OVERLAY_SOURCE }.features.map { it.id }

  @Test
  fun webMapDrawing_pointIsPlacedByOneClick_andDrawingStopsWithTheForm() {
    val state = webAppWithWorkbenchForms()
    val host = state.webMapDrawing
    assertFalse(host.isDrawing)

    // Clicks do nothing until a question asks to be drawn.
    state.addWebMapDrawingVertex(LatLng(-1.29, 36.82))
    assertNull(state.webMapDraftGeometry)

    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val path = "/data/plot_center_gps"
    assertTrue(controller.formState.fieldStates.containsKey(path))

    host.startDrawing(path, MapDrawingKind.POINT)
    assertTrue(host.isDrawing)
    assertEquals(path, host.activeDrawingPath)
    assertEquals(MapDrawingKind.POINT, host.activeDrawingKind)
    assertEquals(MapDrawingKind.POINT, state.webMapDraftGeometry?.kind)

    state.addWebMapDrawingVertex(LatLng(-1.29, 36.82))
    val placed =
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POINT)
    assertEquals(1, placed.size)
    assertEquals(-1.29, placed.single().latitude)
    assertEquals(36.82, placed.single().longitude)
    // One click places a point and ends the drawing.
    assertFalse(host.isDrawing)
    assertNull(state.webMapDraftGeometry)

    // Closing the form while a line is being drawn cancels the drawing.
    host.startDrawing("/data/riparian_transect", MapDrawingKind.LINE)
    assertTrue(host.isDrawing)
    state.closeActiveFormRunner()
    assertFalse(host.isDrawing)
    assertNull(state.webMapDraftGeometry)
  }

  @Test
  fun webMapDrawing_polygonCollectsVertices_rendersDraftOverlay_andRoutesAroundSelection() {
    val state = webAppWithWorkbenchForms()
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val path = "/data/plot_boundary"
    val host = state.webMapDrawing
    val before =
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POLYGON)

    // Redrawing starts from the stored answer; Clear in the widget empties it first.
    host.startDrawing(path, MapDrawingKind.POLYGON)
    controller.setMapDrawingVertices(path, MapDrawingKind.POLYGON, emptyList())
    assertEquals(0, state.webMapDraftGeometry?.vertices?.size)

    state.addWebMapDrawingVertex(LatLng(0.0, 0.0))
    state.addWebMapDrawingVertex(LatLng(0.0, 1.0))
    assertTrue(host.isDrawing)
    val twoVertices = assertNotNull(state.webMapDraftGeometry)
    assertEquals(2, twoVertices.vertices.size)
    assertFalse(twoVertices.isComplete)
    // Two vertices draw as a line, not a polygon.
    val lineOverlay = SurveyMapContent.main(state, showNavigation = false).overlayIds()
    assertTrue(SurveyMapContent.DRAFT_SHAPE_ID in lineOverlay)
    assertEquals(
      SurveyMapContent.KIND_DRAFT_LINE,
      SurveyMapContent.draftFeatures(twoVertices)
        .first { it.id == SurveyMapContent.DRAFT_SHAPE_ID }
        .properties[SurveyMapContent.PROP_KIND],
    )

    state.addWebMapDrawingVertex(LatLng(1.0, 1.0))
    val threeVertices = assertNotNull(state.webMapDraftGeometry)
    assertTrue(threeVertices.isComplete)
    val draft = SurveyMapContent.draftFeatures(threeVertices)
    assertEquals(
      SurveyMapContent.KIND_DRAFT_POLYGON,
      draft
        .first { it.id == SurveyMapContent.DRAFT_SHAPE_ID }
        .properties[SurveyMapContent.PROP_KIND],
    )
    assertEquals(
      3,
      draft.count {
        it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_DRAFT_VERTEX
      },
    )
    val overlayIds = SurveyMapContent.main(state, showNavigation = false).overlayIds()
    assertTrue(SurveyMapContent.draftVertexId(2) in overlayIds)

    host.undoVertex()
    assertEquals(2, state.webMapDraftGeometry?.vertices?.size)

    // Cancelling puts the question back the way it was before drawing started.
    host.cancelDrawing()
    assertFalse(host.isDrawing)
    assertEquals(
      before,
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POLYGON),
    )

    // Done keeps what was drawn.
    host.startDrawing(path, MapDrawingKind.POLYGON)
    state.addWebMapDrawingVertex(LatLng(5.0, 5.0))
    host.stopDrawing()
    assertFalse(host.isDrawing)
    assertEquals(
      before.size + 1,
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POLYGON)
        .size,
    )
  }

  @Test
  fun webFormGeometries_followTheOpenForm_andSkipTheOneBeingDrawn() {
    val state = webAppWithWorkbenchForms()
    assertTrue(state.webFormGeometries.isEmpty())

    // The sample form opens with a point, a line, and a polygon already answered.
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val byPath = state.webFormGeometries.associateBy { it.path }
    val point = assertNotNull(byPath["/data/plot_center_gps"])
    val line = assertNotNull(byPath["/data/riparian_transect"])
    val polygon = assertNotNull(byPath["/data/plot_boundary"])
    assertEquals(MapDrawingKind.POINT, point.kind)
    assertEquals(MapDrawingKind.LINE, line.kind)
    assertEquals(MapDrawingKind.POLYGON, polygon.kind)
    assertEquals(1, point.vertices.size)
    assertEquals(LatLng(-1.292066, 36.821946), point.vertices.single())
    assertEquals(2, line.vertices.size)
    assertTrue(polygon.vertices.size >= 3)
    assertEquals(controller.questionTitleFor("/data/plot_boundary"), polygon.title)
    assertTrue(polygon.title.isNotBlank())

    // Only geometry fields with a value are listed.
    controller.setMapDrawingVertices("/data/riparian_transect", MapDrawingKind.LINE, emptyList())
    assertEquals(
      setOf("/data/plot_center_gps", "/data/plot_boundary"),
      state.webFormGeometries.map { it.path }.toSet(),
    )

    // The question being drawn is left to the draft overlay, and comes back after Done.
    state.webMapDrawing.startDrawing("/data/plot_boundary", MapDrawingKind.POLYGON)
    assertEquals(listOf("/data/plot_center_gps"), state.webFormGeometries.map { it.path })
    state.webMapDrawing.stopDrawing()
    assertEquals(
      setOf("/data/plot_center_gps", "/data/plot_boundary"),
      state.webFormGeometries.map { it.path }.toSet(),
    )

    // The main map shows them as form-geometry features (above entities) with a title label.
    val map = SurveyMapContent.main(state, showNavigation = false)
    val overlay =
      map.content.sources
        .first { it.id == SurveyMapContent.OVERLAY_SOURCE }
        .features
        .associateBy { it.id }
    val pointFeature = assertNotNull(overlay[SurveyMapIds.formGeometry("/data/plot_center_gps")])
    assertEquals(
      SurveyMapContent.KIND_FORM_POINT,
      pointFeature.properties[SurveyMapContent.PROP_KIND],
    )
    val polygonFeature = assertNotNull(overlay[SurveyMapIds.formGeometry("/data/plot_boundary")])
    assertEquals(
      SurveyMapContent.KIND_FORM_POLYGON,
      polygonFeature.properties[SurveyMapContent.PROP_KIND],
    )
    assertNotNull(overlay[SurveyMapIds.formGeometryVertex("/data/plot_boundary", 0)])
    val label = map.markers[SurveyMapIds.formGeometry("/data/plot_boundary")]
    assertEquals(SurveyMarker.FormQuestion(polygon.title, MapDrawingKind.POLYGON), label)
    val layerIds = map.content.layers.map { it.id }
    assertTrue(layerIds.indexOf("form-geometry-outline") > layerIds.indexOf("entity-pins"))
    assertTrue(layerIds.indexOf("form-geometry-outline") < layerIds.indexOf("draft-outline"))

    // Closing the form clears the overlay.
    state.closeActiveFormRunner()
    assertTrue(state.webFormGeometries.isEmpty())
  }

  @Test
  fun frameGeometry_requestsBoundsContainingEveryVertex_withIncreasingTokens() {
    val state = webAppWithWorkbenchForms()
    val host = state.webMapDrawing
    assertTrue(host.canFrameGeometry)
    assertNull(state.webMapFramingRequest)

    // No form open: nothing to frame.
    host.frameGeometry("/data/plot_boundary", MapDrawingKind.POLYGON)
    assertNull(state.webMapFramingRequest)

    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val polygonVertices =
      mapDrawingVertices(
        controller.formState.fieldStates.getValue("/data/plot_boundary"),
        MapDrawingKind.POLYGON,
      )
    assertTrue(polygonVertices.size >= 3)

    host.frameGeometry("/data/plot_boundary", MapDrawingKind.POLYGON)
    val polygonRequest = assertNotNull(state.webMapFramingRequest)
    polygonVertices.forEach { vertex ->
      assertTrue(LatLng(vertex.latitude, vertex.longitude) in polygonRequest.bounds, "$vertex")
    }
    assertEquals(17.0, polygonRequest.maxZoom)
    assertTrue(polygonRequest.bounds.north > polygonRequest.bounds.south)

    // A point frames as zero-size bounds at the point, zoomed in less far.
    host.frameGeometry("/data/plot_center_gps", MapDrawingKind.POINT)
    val pointRequest = assertNotNull(state.webMapFramingRequest)
    assertTrue(pointRequest.token > polygonRequest.token)
    assertEquals(LatLng(-1.292066, 36.821946), pointRequest.bounds.center)
    assertEquals(16.0, pointRequest.maxZoom)

    // Framing the same geometry again re-fires with a new token.
    host.frameGeometry("/data/plot_center_gps", MapDrawingKind.POINT)
    val again = assertNotNull(state.webMapFramingRequest)
    assertTrue(again.token > pointRequest.token)
    assertEquals(pointRequest.bounds, again.bounds)

    // An empty answer has nothing to frame and leaves the last request alone.
    controller.setMapDrawingVertices("/data/riparian_transect", MapDrawingKind.LINE, emptyList())
    host.frameGeometry("/data/riparian_transect", MapDrawingKind.LINE)
    assertEquals(again, state.webMapFramingRequest)
  }
}
