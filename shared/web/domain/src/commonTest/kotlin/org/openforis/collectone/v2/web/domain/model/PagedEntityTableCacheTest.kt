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
package org.openforis.collectone.v2.web.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableSchema

@OptIn(ExperimentalCoroutinesApi::class)
class PagedEntityTableCacheTest {
  private class FakeEntityTableRepository(
    private val allRowIds: List<String>,
    private val allColumns: List<String>,
  ) : EntityTableRepository {
    override val isLazyLoadingEnabled: Boolean = true
    var schemaCalls = 0
    val fetchedTiles = mutableListOf<Pair<List<String>, List<String>>>()

    override suspend fun fetchSchema(surveyId: String, datasetId: String): EntityTableSchema {
      schemaCalls++
      return EntityTableSchema(totalCount = allRowIds.size, propertyKeys = allColumns)
    }

    override suspend fun fetchEntityProperties(
      surveyId: String,
      datasetId: String,
      entityIds: List<String>,
      propertyKeys: List<String>,
    ): Map<String, Map<String, String>> {
      fetchedTiles += entityIds to propertyKeys
      return entityIds.associateWith { id ->
        propertyKeys.associateWith { col -> "$id:$col" }
      }
    }

    override suspend fun fetchEntityProperties(
      surveyId: String,
      datasetId: String,
      entityId: String,
    ): Map<String, String> = allColumns.associateWith { col -> "$entityId:$col" }

    override suspend fun fetchAllEntitiesForExport(
      surveyId: String,
      datasetId: String,
    ): List<GeospatialEntityItem> = emptyList()
  }

  @Test
  fun fetchesOnlyVisibleTiles_andEvictsOldPagesToStayBoundedInMemory() = runTest {
    val rowIds = List(414) { "e-$it" }
    val cols = List(246) { i -> if (i < 16) "col_$i" else "whisp_metric_${i - 16}" }
    val repo = FakeEntityTableRepository(rowIds, cols)
    val cache =
      PagedEntityTableCache(
        surveyId = "s1",
        datasetId = "sample_plots",
        repository = repo,
        rowPageSize = 50,
        colChunkSize = 24,
        maxCachedTiles = 4,
      )

    cache.ensureSchemaLoaded(this)
    advanceUntilIdle()
    assertEquals(1, repo.schemaCalls)
    assertEquals(246, cache.schema.value?.propertyKeys?.size)

    // Initial viewport: rows 0..10, cols 0..6 → fetches only tile (0, 0).
    cache.onViewportChanged(this, 0..10, 0..6, rowIds, cols)
    assertEquals(TableCellLoadState.Loading, cache.cellState("e-0", 0, 5, "col_5"))
    advanceUntilIdle()

    assertEquals(1, repo.fetchedTiles.size)
    assertEquals(50, repo.fetchedTiles[0].first.size)
    assertEquals(24, repo.fetchedTiles[0].second.size)
    val loaded = assertIs<TableCellLoadState.Loaded>(cache.cellState("e-0", 0, 5, "col_5"))
    assertEquals("e-0:col_5", loaded.value)
    // Off-screen whisp column has not been loaded yet.
    assertEquals(TableCellLoadState.Loading, cache.cellState("e-0", 0, 100, "whisp_metric_84"))

    // Scroll across 10 different row pages & whisp column chunks: cache stays bounded at 4 tiles.
    for (step in 1..8) {
      val r = step * 50
      val c = step * 24
      cache.onViewportChanged(this, r..(r + 5), c..(c + 5), rowIds, cols)
      advanceUntilIdle()
      assertTrue(
        cache.cachedTileCount <= 4,
        "Expected bounded LRU cache <= 4 tiles, got ${cache.cachedTileCount}",
      )
    }

    // Tile (0, 0) was evicted from memory, while the latest scrolled whisp tile is loaded.
    assertEquals(TableCellLoadState.Loading, cache.cellState("e-0", 0, 5, "col_5"))
    val latestLoaded =
      assertIs<TableCellLoadState.Loaded>(cache.cellState("e-400", 400, 192, cols[192]))
    assertEquals("e-400:${cols[192]}", latestLoaded.value)
  }

  @Test
  fun localOverride_takesPrecedenceOverLoadingAndCachedValues() = runTest {
    val rowIds = listOf("e-0")
    val cols = listOf("notes")
    val repo = FakeEntityTableRepository(rowIds, cols)
    val cache = PagedEntityTableCache("s1", "d1", repo)

    cache.putLocalValue("e-0", "notes", "edited")
    val state = assertIs<TableCellLoadState.Loaded>(cache.cellState("e-0", 0, 0, "notes"))
    assertEquals("edited", state.value)
  }

  @Test
  fun columnExpansionAfterSchemaLoad_refetchesFirstTileWithNewlyDiscoveredColumns() = runTest {
    val rowIds = listOf("e-0", "e-1")
    val expandedCols = listOf("plot_id", "whisp_Area", "whisp_Country")
    val repo = FakeEntityTableRepository(rowIds, expandedCols)
    val cache = PagedEntityTableCache("s1", "sample_plots", repo)

    // Frame 1 before remote schema arrives: only local column ["plot_id"] is known.
    cache.onViewportChanged(this, 0..1, 0..0, rowIds, listOf("plot_id"))
    advanceUntilIdle()
    assertEquals(1, repo.fetchedTiles.size)
    assertEquals(listOf("plot_id"), repo.fetchedTiles[0].second)

    // Frame 2 after remote schema arrives: columns expand to include whisp_* keys.
    assertEquals(TableCellLoadState.Loading, cache.cellState("e-0", 0, 1, "whisp_Area"))
    cache.onViewportChanged(this, 0..1, 0..2, rowIds, expandedCols)
    advanceUntilIdle()

    assertEquals(2, repo.fetchedTiles.size)
    assertEquals(expandedCols, repo.fetchedTiles[1].second)
    val area = assertIs<TableCellLoadState.Loaded>(cache.cellState("e-0", 0, 1, "whisp_Area"))
    assertEquals("e-0:whisp_Area", area.value)
  }
}
