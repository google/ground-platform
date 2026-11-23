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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan

/** Importing Data tables and Map layers into the Survey editor's draft. */
class SurveyEditorDatasetImportTest {

  private val speciesTable =
    DataTableImportPlan(
      name = "Species",
      properties = listOf(EntityProperty("code", "Code"), EntityProperty("name", "Name")),
      keyProperty = "code",
      labelProperty = "name",
      rows =
        listOf(mapOf("code" to "QR", "name" to "Oak"), mapOf("code" to "PS", "name" to "Pine")),
    )

  private val wellsLayer =
    MapLayerImportPlan(
      name = "Wells",
      geometryType = GeometryType.POINT,
      properties = listOf(EntityProperty("name", "Name")),
      keyProperty = "name",
      labelProperty = "name",
      rows =
        listOf(
          mapOf("name" to "North well") to listOf(LatLng(-0.41, 36.93)),
          mapOf("name" to "South well") to listOf(LatLng(-0.43, 36.94)),
        ),
      skippedFeatures = 0,
    )

  @Test
  fun importDataTable_keepsThePlansColumnsAndKeys() {
    val viewModel = surveyEditorViewModel()

    val key = viewModel.importDataTable(speciesTable)

    val table = viewModel.ui.datasets.single { it.key == key }
    assertEquals(DatasetKind.DATA_TABLE, table.kind)
    assertEquals(speciesTable.properties, table.properties)
    assertEquals("code", table.keyProperty)
    assertEquals("name", table.labelProperty)
    assertEquals(listOf("QR", "PS"), table.rows.map { it.values["code"] })
  }

  @Test
  fun importDataTable_givesEveryRowItsOwnKey() {
    val viewModel = surveyEditorViewModel()

    val key = viewModel.importDataTable(speciesTable)

    val rowKeys = viewModel.ui.datasets.single { it.key == key }.rows.map { it.key }
    assertEquals(rowKeys.distinct(), rowKeys)
  }

  @Test
  fun importingTheSameTableTwice_makesTitlesAndIdsUnique() {
    val viewModel = surveyEditorViewModel()

    val first = viewModel.importDataTable(speciesTable)
    val second = viewModel.importDataTable(speciesTable)

    val firstTable = viewModel.ui.datasets.single { it.key == first }
    val secondTable = viewModel.ui.datasets.single { it.key == second }
    assertNotEquals(firstTable.displayName, secondTable.displayName)
    assertNotEquals(firstTable.id, secondTable.id)
  }

  @Test
  fun importMapLayer_keepsGeometryAndOpensTheLayer() {
    val viewModel = surveyEditorViewModel()

    val key = viewModel.importMapLayer(wellsLayer)

    val layer = viewModel.ui.datasets.single { it.key == key }
    assertEquals(DatasetKind.MAP_LAYER, layer.kind)
    assertEquals(GeometryType.POINT, layer.geometryType)
    assertEquals(listOf(LatLng(-0.41, 36.93)), layer.rows.first().geometry)
    assertEquals(SurveyEditorSection.Dataset(key), viewModel.ui.section)
    assertTrue(viewModel.ui.hasUnpublishedChanges)
  }

  @Test
  fun importMapLayer_withoutSelecting_staysOnTheCurrentSection() {
    val viewModel = surveyEditorViewModel()
    val sectionBefore = viewModel.ui.section

    val key = viewModel.importMapLayer(wellsLayer, selectAfterImport = false)

    assertNotNull(viewModel.ui.datasets.singleOrNull { it.key == key })
    assertEquals(sectionBefore, viewModel.ui.section)
  }

  @Test
  fun removeRow_ofAnImportedLayer_dropsOnlyThatFeature() {
    val viewModel = surveyEditorViewModel()
    val key = viewModel.importMapLayer(wellsLayer)
    val north = viewModel.ui.datasets.single { it.key == key }.rows.first()

    viewModel.removeRow(key, north.key)

    val remaining = viewModel.ui.datasets.single { it.key == key }.rows
    assertEquals(listOf("South well"), remaining.map { it.values["name"] })
  }
}
