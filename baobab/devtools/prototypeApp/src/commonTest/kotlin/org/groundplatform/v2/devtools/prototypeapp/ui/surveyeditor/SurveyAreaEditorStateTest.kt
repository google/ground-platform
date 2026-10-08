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
package org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.ui.common.TextFilePickResult

class SurveyAreaEditorStateTest {
  private val multiPolygon =
    """
    {"type": "Feature", "properties": {"name": "Twin lakes"},
     "geometry": {"type": "MultiPolygon", "coordinates": [
       [[[36.90,-0.40],[36.92,-0.40],[36.92,-0.42],[36.90,-0.40]]],
       [[[36.95,-0.40],[36.97,-0.40],[36.97,-0.42],[36.95,-0.42],[36.95,-0.40]],
        [[36.955,-0.405],[36.96,-0.405],[36.96,-0.41],[36.955,-0.405]]]
     ]}}
    """
      .trimIndent()

  @Test
  fun upload_geoJsonMultiPolygonBecomesParts() {
    val editor = SurveyAreaEditorState(initial = null)
    editor.loadFile("lakes.geojson", multiPolygon)

    val area = assertNotNull(editor.uploadedArea())
    assertEquals("Twin lakes", area.name)
    assertEquals(2, area.parts.size)
    assertEquals(listOf(3, 4), area.parts.map { it.size })
    assertEquals("${SurveyAreaEditorState.SOURCE_UPLOADED_PREFIX}lakes.geojson", area.sourceLabel)
    // The hole in the second part is dropped with a warning.
    assertTrue(editor.upload!!.result.warnings.isNotEmpty())
  }

  @Test
  fun upload_kmlPolygon() {
    val kml =
      """
      <kml xmlns="http://www.opengis.net/kml/2.2"><Document><Placemark><name>Block A</name>
        <Polygon><outerBoundaryIs><LinearRing><coordinates>
          36.90,-0.40,0 36.92,-0.40,0 36.92,-0.42,0 36.90,-0.40,0
        </coordinates></LinearRing></outerBoundaryIs></Polygon>
      </Placemark></Document></kml>
      """
        .trimIndent()
    val editor = SurveyAreaEditorState(initial = null)
    editor.onFilePicked(TextFilePickResult.Picked("block.kml", kml))

    val area = assertNotNull(editor.uploadedArea())
    assertEquals("Block A", area.name)
    assertEquals(1, area.parts.size)
  }

  @Test
  fun upload_pointsAreIgnored() {
    val geojson =
      """
      {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {},
         "geometry": {"type": "Point", "coordinates": [36.9, -0.4]}}
      ]}
      """
        .trimIndent()
    val editor = SurveyAreaEditorState(initial = null)
    editor.loadFile("wells.geojson", geojson)
    assertNull(editor.uploadedArea())
    assertEquals(1, editor.ignoredUploadFeatures)
  }

  @Test
  fun upload_invalidFileReportsErrors() {
    val editor = SurveyAreaEditorState(initial = null)
    editor.loadFile("broken.geojson", "{not json")
    assertNull(editor.uploadedArea())
    assertTrue(editor.upload!!.result.errors.isNotEmpty())
  }

  @Test
  fun shapefile_explainsItIsComingSoon() {
    val editor = SurveyAreaEditorState(initial = null)
    editor.chooseShapefile()
    assertEquals(SurveyAreaEditorState.SHAPEFILE_COMING_SOON, editor.uploadMessage)
    assertNull(editor.upload)
  }

  @Test
  fun pickerFailure_isShownAndCancelIsIgnored() {
    val editor = SurveyAreaEditorState(initial = null)
    editor.onFilePicked(TextFilePickResult.Cancelled)
    assertNull(editor.uploadMessage)
    editor.onFilePicked(TextFilePickResult.Failed("Couldn't read the file."))
    assertEquals("Couldn't read the file.", editor.uploadMessage)
  }

  @Test
  fun draw_addAndDeleteParts() {
    val editor = SurveyAreaEditorState(initial = null)
    assertNull(editor.drawnArea())

    editor.addDrawnPart(listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0)))
    editor.addDrawnPart(listOf(LatLng(2.0, 2.0), LatLng(2.0, 3.0), LatLng(3.0, 3.0)))
    editor.drawName = "  Two fields "

    val area = assertNotNull(editor.drawnArea())
    assertEquals("Two fields", area.name)
    assertEquals(2, area.parts.size)
    assertEquals(SurveyAreaEditorState.SOURCE_DRAWN, area.sourceLabel)

    editor.deleteDrawnPart(0)
    assertEquals(1, editor.drawnArea()?.parts?.size)
    editor.deleteDrawnPart(0)
    assertNull(editor.drawnArea())
  }

  @Test
  fun draw_startsFromTheCurrentArea() {
    val initial =
      SurveyArea(
        name = "Existing",
        parts = listOf(listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0))),
      )
    val editor = SurveyAreaEditorState(initial)
    assertEquals(initial.parts, editor.drawnParts)
    assertEquals("Existing", editor.drawName)
  }

  @Test
  fun editUploadInDrawTab_copiesParts() {
    val editor = SurveyAreaEditorState(initial = null)
    editor.loadFile("lakes.geojson", multiPolygon)
    editor.editUploadInDrawTab()
    assertEquals(SurveyAreaEditorTab.DRAW, editor.tab)
    assertEquals(2, editor.drawnParts.size)
    assertEquals("Twin lakes", editor.drawName)
  }
}
