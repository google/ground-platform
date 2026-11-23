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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableSchema
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.ui.common.VIRTUAL_TABLE_HORIZONTAL_SCROLLBAR_TAG
import org.openforis.collectone.v2.web.ui.common.columnGroupChipTestTag
import org.openforis.collectone.v2.web.ui.common.columnGroupCollapseButtonTestTag
import org.openforis.collectone.v2.web.ui.common.columnGroupHeaderTestTag

@OptIn(ExperimentalTestApi::class)
class EntityDatasetEditorTest {

  @Test
  fun visibleColumnRange_handlesEmptyNarrowAndScrolledWideTables() {
    assertEquals(
      IntRange.EMPTY,
      visibleColumnRange(
        columnCount = 0,
        scrollPx = 0,
        viewportWidthPx = 900,
        leadingWidthPx = 44f,
        columnWidthPx = 160f,
      ),
    )
    assertEquals(
      0..3,
      visibleColumnRange(
        columnCount = 4,
        scrollPx = 0,
        viewportWidthPx = 900,
        leadingWidthPx = 44f,
        columnWidthPx = 160f,
      ),
    )
    assertEquals(
      0..7,
      visibleColumnRange(
        columnCount = 246,
        scrollPx = 0,
        viewportWidthPx = 900,
        leadingWidthPx = 44f,
        columnWidthPx = 160f,
      ),
    )
    assertEquals(
      8..18,
      visibleColumnRange(
        columnCount = 246,
        scrollPx = 1644,
        viewportWidthPx = 960,
        leadingWidthPx = 44f,
        columnWidthPx = 160f,
      ),
    )
    assertEquals(
      240..245,
      visibleColumnRange(
        columnCount = 246,
        scrollPx = 38_800,
        viewportWidthPx = 900,
        leadingWidthPx = 44f,
        columnWidthPx = 160f,
      ),
    )
    assertEquals(
      245..245,
      visibleColumnRange(
        columnCount = 246,
        scrollPx = 40_000,
        viewportWidthPx = 900,
        leadingWidthPx = 44f,
        columnWidthPx = 160f,
      ),
    )
  }

  @Test
  fun largeWideMapLayer_virtualizesRowsAndColumns_andEditsCellOnClick() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val properties =
        listOf(EntityProperty("plot_id", "Plot ID", PropertyType.TEXT, required = true)) +
          (1..99).map { i -> EntityProperty("col_$i", "Column $i", PropertyType.TEXT) }
      val rows =
        (1..500).map { r ->
          EntityRow(
            key = "r$r",
            values = mapOf("plot_id" to "P$r", "col_1" to "val-$r-1", "col_99" to "val-$r-99"),
            geometry = listOf(LatLng(0.0 + r * 0.001, 36.0)),
          )
        }
      val dataset =
        EntityDataset(
          key = "d101",
          kind = DatasetKind.MAP_LAYER,
          id = "sample_plots",
          displayName = "Sample plots",
          geometryType = GeometryType.POINT,
          keyProperty = "plot_id",
          labelProperty = "plot_id",
          properties = properties,
          rows = rows,
        )
      val draft = SurveyEditorDraft.blank("survey-large").copy(datasets = listOf(dataset))
      val state = surveyEditorViewModel(draft)
      state.select(SurveyEditorSection.Dataset(dataset.key))

      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          val current = uiState.selectedDataset!!
          EntityDatasetEditor(uiState = uiState, actions = state, dataset = current)
        }
      }
      waitForIdle()

      // First row is visible and the 99 col_* columns are collapsed by default.
      onNodeWithText("P1").assertIsDisplayed()
      onNodeWithTag(columnGroupHeaderTestTag("col_")).assertIsDisplayed()
      onAllNodesWithText("val-1-1").assertCountEquals(0)

      // Expand col_* in place via the inline collapsed group header.
      onNodeWithTag(columnGroupHeaderTestTag("col_")).performClick()
      waitForIdle()
      onNodeWithText("val-1-1").assertIsDisplayed()

      // Off-screen rows and off-screen columns are STILL NOT eagerly composed.
      onAllNodesWithText("P500").assertCountEquals(0)
      onAllNodesWithText("Column 99").assertCountEquals(0)
      onAllNodesWithText("val-1-99").assertCountEquals(0)

      // Clicking an editable cell switches it to a BasicTextField and updates the dataset.
      onNodeWithText("val-1-1").performClick()
      waitForIdle()
      onNodeWithText("val-1-1").performTextInput("!")
      waitForIdle()

      val updated = state.ui.dataset("d101")!!.rows.first().values["col_1"].orEmpty()
      assertTrue(updated.contains("!"), "Expected edited cell to include typed character: $updated")

      // Clicking the inline collapse button on the group's first header collapses col_* back.
      onNodeWithTag(columnGroupCollapseButtonTestTag("col_")).performClick()
      waitForIdle()
      onNodeWithTag(columnGroupHeaderTestTag("col_")).assertIsDisplayed()
      onAllNodesWithText("val-1-1").assertCountEquals(0)
    }

  @Test
  fun lazyTableRepository_discoversWhispColumns_andLoadsOnlyVisibleTiles() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val remoteCols = listOf("plot_id") + (1..230).map { i -> "whisp_metric_$i" }
      val fetchedTiles = mutableListOf<Pair<Int, List<String>>>()
      val fakeRepo =
        object : EntityTableRepository {
          override val isLazyLoadingEnabled: Boolean = true

          override suspend fun fetchSchema(surveyId: String, datasetId: String): EntityTableSchema =
            EntityTableSchema(totalCount = 414, propertyKeys = remoteCols)

          override suspend fun fetchEntityProperties(
            surveyId: String,
            datasetId: String,
            entityIds: List<String>,
            propertyKeys: List<String>,
          ): Map<String, Map<String, String>> {
            fetchedTiles += entityIds.size to propertyKeys
            return entityIds.associateWith { id ->
              propertyKeys.associateWith { col -> "$col:$id" }
            }
          }

          override suspend fun fetchEntityProperties(
            surveyId: String,
            datasetId: String,
            entityId: String,
          ): Map<String, String>? = null

          override suspend fun fetchAllEntitiesForExport(
            surveyId: String,
            datasetId: String,
          ): List<GeospatialEntityItem> = emptyList()
        }

      val rows =
        (1..414).map { r ->
          EntityRow(
            key = "r$r",
            values = mapOf("plot_id" to "P$r"),
            geometry = listOf(LatLng(0.0 + r * 0.001, 36.0)),
          )
        }
      val dataset =
        EntityDataset(
          key = "d101",
          kind = DatasetKind.MAP_LAYER,
          id = "sample_plots",
          displayName = "Sample plots",
          geometryType = GeometryType.POINT,
          keyProperty = "plot_id",
          labelProperty = "plot_id",
          properties =
            listOf(EntityProperty("plot_id", "Plot ID", PropertyType.TEXT, required = true)),
          rows = rows,
        )
      val draft = SurveyEditorDraft.blank("survey-lazy").copy(datasets = listOf(dataset))
      val baseState = surveyEditorViewModel(draft)
      baseState.select(SurveyEditorSection.Dataset(dataset.key))
      val actionsWithLazyRepo =
        object : SurveyEditorActions by baseState {
          override fun tableRepository(): EntityTableRepository = fakeRepo
        }

      setContent {
        MaterialTheme {
          val uiState by baseState.uiState.collectAsState()
          val current = uiState.selectedDataset!!
          EntityDatasetEditor(uiState = uiState, actions = actionsWithLazyRepo, dataset = current)
        }
      }
      waitForIdle()

      // All 230 whisp_* columns start collapsed by default and are NOT fetched from the server.
      onNodeWithText("P1").assertIsDisplayed()
      onNodeWithTag(columnGroupHeaderTestTag("whisp_")).assertIsDisplayed()
      onNodeWithTag(columnGroupChipTestTag("whisp_")).assertIsDisplayed()
      onAllNodesWithText("whisp_metric_1").assertCountEquals(0)
      assertTrue(
        fetchedTiles.all { (_, cols) -> cols.none { it.startsWith("whisp_") } },
        "Collapsed whisp_* columns should not be fetched until expanded, got $fetchedTiles",
      )

      // Expand whisp_* via the inline collapsed column header.
      onNodeWithTag(columnGroupHeaderTestTag("whisp_")).performClick()
      waitForIdle()

      onNodeWithText("whisp_metric_1").assertIsDisplayed()
      // Only the visible tile(s) were fetched (<= 50 rows x <= 24 cols), never all 414 x 231 cells.
      assertTrue(
        fetchedTiles.any { (_, cols) -> cols.any { it.startsWith("whisp_") } },
        "Expected expanding whisp_* to trigger lazy fetch of initial visible tile",
      )
      assertTrue(
        fetchedTiles.all { (rowCount, cols) -> rowCount <= 50 && cols.size <= 24 },
        "Expected bounded tile fetches, got $fetchedTiles",
      )
      // Far-off whisp columns are not eagerly composed.
      onAllNodesWithText("whisp_metric_230").assertCountEquals(0)

      // Clicking midway along the horizontal scrollbar jumps into deep whisp_* columns and lazily
      // fetches only that scrolled column chunk.
      val fetchesBeforeScroll = fetchedTiles.size
      onNodeWithTag(VIRTUAL_TABLE_HORIZONTAL_SCROLLBAR_TAG).performTouchInput {
        click(percentOffset(0.5f, 0.5f))
      }
      waitForIdle()

      assertTrue(
        fetchedTiles.size > fetchesBeforeScroll,
        "Expected horizontal scrollbar click to trigger lazy fetch for newly visible whisp_* chunk",
      )
      assertTrue(
        fetchedTiles.all { (rowCount, cols) -> rowCount <= 50 && cols.size <= 24 },
        "Expected all scrolled tile fetches to remain bounded, got $fetchedTiles",
      )
      // Origin column whisp_metric_1 scrolled out of view while mid-table whisp_* columns show.
      onAllNodesWithText("whisp_metric_1").assertCountEquals(0)

      // Toolbar chip collapses whisp_* back cleanly from anywhere in the scrolled table.
      onNodeWithTag(columnGroupChipTestTag("whisp_")).performClick()
      waitForIdle()
      onNodeWithTag(columnGroupHeaderTestTag("whisp_")).assertIsDisplayed()
    }

  @Test
  fun lazyTableRepository_asyncTileCompletion_replacesLoadingPlaceholderWithoutExtraScroll() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val tileGate = CompletableDeferred<Unit>()
      val fakeRepo =
        object : EntityTableRepository {
          override val isLazyLoadingEnabled: Boolean = true

          override suspend fun fetchSchema(surveyId: String, datasetId: String): EntityTableSchema =
            EntityTableSchema(totalCount = 2, propertyKeys = listOf("plot_id", "whisp_Country"))

          override suspend fun fetchEntityProperties(
            surveyId: String,
            datasetId: String,
            entityIds: List<String>,
            propertyKeys: List<String>,
          ): Map<String, Map<String, String>> {
            tileGate.await()
            return entityIds.associateWith { mapOf("whisp_Country" to "ETH") }
          }

          override suspend fun fetchEntityProperties(
            surveyId: String,
            datasetId: String,
            entityId: String,
          ): Map<String, String>? = null

          override suspend fun fetchAllEntitiesForExport(
            surveyId: String,
            datasetId: String,
          ): List<GeospatialEntityItem> = emptyList()
        }

      val dataset =
        EntityDataset(
          key = "d101",
          kind = DatasetKind.MAP_LAYER,
          id = "sample_plots",
          displayName = "Sample plots",
          geometryType = GeometryType.POINT,
          keyProperty = "plot_id",
          labelProperty = "plot_id",
          properties =
            listOf(EntityProperty("plot_id", "Plot ID", PropertyType.TEXT, required = true)),
          rows =
            listOf(
              EntityRow("r1", mapOf("plot_id" to "P1"), listOf(LatLng(0.0, 36.0))),
              EntityRow("r2", mapOf("plot_id" to "P2"), listOf(LatLng(0.1, 36.1))),
            ),
        )
      val draft = SurveyEditorDraft.blank("survey-async").copy(datasets = listOf(dataset))
      val baseState = surveyEditorViewModel(draft)
      baseState.select(SurveyEditorSection.Dataset(dataset.key))
      val actionsWithLazyRepo =
        object : SurveyEditorActions by baseState {
          override fun tableRepository(): EntityTableRepository = fakeRepo
        }

      setContent {
        MaterialTheme {
          val uiState by baseState.uiState.collectAsState()
          EntityDatasetEditor(
            uiState = uiState,
            actions = actionsWithLazyRepo,
            dataset = uiState.selectedDataset!!,
          )
        }
      }
      waitForIdle()

      // Schema loaded -> column header visible, but cell tile is still suspended on tileGate.
      onNodeWithText("whisp_Country").assertIsDisplayed()
      onAllNodesWithText("ETH").assertCountEquals(0)

      // Complete tile fetch with ZERO further scrolling -> cells must recompose immediately.
      tileGate.complete(Unit)
      waitForIdle()
      onAllNodesWithText("ETH").assertCountEquals(2)
    }
}
