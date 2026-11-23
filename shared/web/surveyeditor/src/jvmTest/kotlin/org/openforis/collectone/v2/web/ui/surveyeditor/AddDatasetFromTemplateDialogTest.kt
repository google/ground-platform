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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.map.GeometryType

/**
 * Creating Map layers and Data tables from library templates filters the picker to templates whose
 * Forms feed that kind of dataset, then creates and opens the new linked dataset.
 */
@OptIn(ExperimentalTestApi::class)
class AddDatasetFromTemplateDialogTest {

  @Test
  fun addMapLayerDialog_fromTemplate_listsGeometryTemplates_andOpensNewMapLayer() =
    runDesktopComposeUiTest(width = 1200, height = 900) {
      val store = seededStore()
      val editor = seededSurveyEditorViewModel(store, LibraryRepositoryImpl(store))
      var dismissed = false

      setContent {
        MaterialTheme {
          val state by editor.uiState.collectAsState()
          AddMapLayerDialog(
            uiState = state,
            actions = editor,
            onDismiss = { dismissed = true },
            offerTemplates = true,
          )
        }
      }

      onNodeWithText("From template").performClick()
      waitForIdle()

      // Geometry template is listed; table-only template is excluded.
      onNodeWithText("Deforestation compliance plot check").assertIsDisplayed()
      onAllNodesWithText("Producer & farm registration").assertCountEquals(0)

      onNodeWithText("Deforestation compliance plot check").performClick()
      waitForIdle()

      assertTrue(dismissed)
      val section = editor.ui.section as? SurveyEditorSection.Dataset
      assertNotNull(section)
      val layer = assertNotNull(editor.ui.dataset(section.key))
      assertEquals(DatasetKind.MAP_LAYER, layer.kind)
      assertEquals(GeometryType.POLYGON, layer.geometryType)
      assertTrue(layer.properties.none { it.name == "production_place" })
    }

  @Test
  fun addDataTableDialog_fromTemplate_listsTableTemplates_andOpensNewDataTable() =
    runDesktopComposeUiTest(width = 1200, height = 900) {
      val store = seededStore()
      val editor = seededSurveyEditorViewModel(store, LibraryRepositoryImpl(store))
      var dismissed = false

      setContent {
        MaterialTheme {
          val state by editor.uiState.collectAsState()
          AddDataTableDialog(
            actions = editor,
            onDismiss = { dismissed = true },
            library = state.library,
            onAddFromTemplate = { editor.addFormFromTemplate(it, openDataset = true) },
          )
        }
      }

      onNodeWithText("From template").performClick()
      waitForIdle()

      // Data-table template is listed; geometry template is excluded.
      onNodeWithText("Producer & farm registration").assertIsDisplayed()
      onAllNodesWithText("Deforestation compliance plot check").assertCountEquals(0)

      onNodeWithText("Producer & farm registration").performClick()
      waitForIdle()

      assertTrue(dismissed)
      val section = editor.ui.section as? SurveyEditorSection.Dataset
      assertNotNull(section)
      val table = assertNotNull(editor.ui.dataset(section.key))
      assertEquals(DatasetKind.DATA_TABLE, table.kind)
      assertEquals("producer_name", table.labelProperty)
    }
}
