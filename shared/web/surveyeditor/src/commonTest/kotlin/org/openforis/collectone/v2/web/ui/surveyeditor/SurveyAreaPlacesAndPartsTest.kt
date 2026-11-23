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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.PlaceSource
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaSource
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/** [placeToSurveyArea], [DrawnPartsEditor], and how the Draw tab starts from the current area. */
class SurveyAreaPlacesAndPartsTest {
  private fun place(bounds: LngLatBounds?, source: PlaceSource? = PlaceSource.PLACES_API) =
    SurveyPlaceItem(
      id = "p1",
      name = "Nyeri",
      categoryLabel = "Town",
      regionSubtitle = "Kenya",
      position = LatLng(-0.42, 36.95),
      normalizedX = 0f,
      normalizedY = 0f,
      bounds = bounds,
      targetZoom = 11f,
      source = source,
    )

  @Test
  fun placeToSurveyArea_usesThePlacesBoundingBox() {
    val area = placeToSurveyArea(place(LngLatBounds(36.9, -0.5, 37.0, -0.4)))

    assertEquals("Nyeri", area.name)
    assertEquals(
      listOf(LatLng(-0.4, 36.9), LatLng(-0.4, 37.0), LatLng(-0.5, 37.0), LatLng(-0.5, 36.9)),
      area.parts.single(),
    )
    assertEquals(LatLng(-0.42, 36.95), area.center)
    assertEquals(11.0, area.zoom)
    assertEquals(SurveyAreaSource.PlaceSearch, area.source)
  }

  @Test
  fun placeToSurveyArea_withoutBounds_drawsASmallBoxAroundThePlace() {
    val area = placeToSurveyArea(place(bounds = null, source = null))

    val box = area.parts.single()
    assertEquals(4, box.size)
    assertEquals(-0.42 + 0.015, box.maxOf { it.latitude }, 1e-9)
    assertEquals(36.95 - 0.015, box.minOf { it.longitude }, 1e-9)
    assertEquals(SurveyAreaSource.Coordinates, area.source)
  }

  @Test
  fun placeToSurveyArea_worldSpanningBounds_areNarrowedAroundThePlace() {
    // A country whose bounding box crosses the antimeridian comes back as [-180, 180].
    val area = placeToSurveyArea(place(LngLatBounds(-180.0, -10.0, 180.0, 10.0)))

    val box = area.parts.single()
    val lngs = box.map { it.longitude }
    assertEquals(36.95 - 28.0, lngs.min(), 1e-9) // 1.4 × the 20° latitude span, either side
    assertEquals(36.95 + 28.0, lngs.max(), 1e-9)
    assertEquals(listOf(10.0, -10.0), box.map { it.latitude }.distinct())
  }

  private val scratch =
    EntityDataset(
      key = "parts",
      kind = DatasetKind.MAP_LAYER,
      id = "part",
      displayName = "Parts",
      geometryType = GeometryType.POLYGON,
      keyProperty = "id",
      labelProperty = "id",
      properties = emptyList(),
    )
  private val triangle = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0))

  @Test
  fun drawnPartsEditor_numbersNewParts() {
    val editor = DrawnPartsEditor(scratch)

    assertEquals("r1000", editor.addRow("parts", geometry = triangle))
    assertEquals("r1001", editor.addRow("parts", geometry = triangle))
    assertEquals(listOf("PAR-1", "PAR-2"), editor.dataset.rows.map { it.values["id"] })
  }

  @Test
  fun drawnPartsEditor_editsOnlyItsOwnDataset() {
    val editor = DrawnPartsEditor(scratch)
    val key = editor.addRow("parts", geometry = triangle)

    assertEquals("", editor.addRow("other", geometry = triangle))
    editor.updateGeometry("other", key, triangle.take(1))
    editor.removeRow("other", key)
    assertEquals(listOf(triangle), editor.dataset.rows.map { it.geometry })

    val moved = triangle.map { LatLng(it.latitude + 1, it.longitude) }
    editor.updateGeometry("parts", key, moved)
    assertEquals(listOf(moved), editor.dataset.rows.map { it.geometry })
    editor.removeRow("parts", key)
    assertTrue(editor.dataset.rows.isEmpty())
  }

  @Test
  fun drawTab_doesNotLoadAreasTooDetailedToEditByHand() {
    val detailed =
      List(MAX_DRAW_EDIT_VERTICES + 1) { i ->
        val a = 2 * kotlin.math.PI * i / (MAX_DRAW_EDIT_VERTICES + 1)
        LatLng(kotlin.math.sin(a), kotlin.math.cos(a))
      }
    val area = SurveyArea(name = "Coast", parts = listOf(detailed), source = SurveyAreaSource.Drawn)

    assertTrue(SurveyAreaEditorState(area).drawnParts.isEmpty())
  }
}
