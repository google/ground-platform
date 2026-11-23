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

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableSchema

/** Load state of one cell in a lazy-loaded virtual table. */
sealed interface TableCellLoadState {
  data class Loaded(val value: String) : TableCellLoadState

  data object Loading : TableCellLoadState
}

/**
 * 2D bounded-LRU tile cache for lazy-loading entity table cell values on scroll.
 *
 * Rows are partitioned into pages of [rowPageSize] and columns into chunks of [colChunkSize]. Only
 * tiles intersecting the active viewport are fetched from [repository], and at most
 * [maxCachedTiles] tiles are retained in memory; scrolling away evicts older tiles so browser RAM
 * stays O(viewport) even for tens of thousands of rows and hundreds of columns.
 */
class PagedEntityTableCache(
  val surveyId: String,
  val datasetId: String,
  private val repository: EntityTableRepository,
  val rowPageSize: Int = DEFAULT_ROW_PAGE_SIZE,
  val colChunkSize: Int = DEFAULT_COL_CHUNK_SIZE,
  val maxCachedTiles: Int = DEFAULT_MAX_CACHED_TILES,
) {
  /** Identifies one `(rowPage, colChunk)` tile of the 2D table grid. */
  data class TileKey(val rowPage: Int, val colChunk: Int)

  private val _schema = MutableStateFlow<EntityTableSchema?>(null)
  val schema: StateFlow<EntityTableSchema?> = _schema.asStateFlow()

  private val _revision = MutableStateFlow(0)
  /** Monotonic counter incremented whenever cached tiles or local edits change. */
  val revision: StateFlow<Int> = _revision.asStateFlow()

  private data class CachedTile(
    val entityIds: List<String>,
    val columnKeys: List<String>,
    val columnKeySet: Set<String>,
    val values: Map<String, Map<String, String>>,
  )

  private data class InFlightTile(
    val entityIds: List<String>,
    val columnKeys: List<String>,
    val job: Job,
  )

  // Insertion-ordered map used as an LRU cache (oldest entry first, most-recently-used last).
  private val tiles = LinkedHashMap<TileKey, CachedTile>()
  private val inFlight = LinkedHashMap<TileKey, InFlightTile>()
  private val localOverrides = LinkedHashMap<String, MutableMap<String, String>>()
  private var schemaJob: Job? = null
  private var activeRowPages: IntRange = IntRange.EMPTY
  private var activeColChunks: IntRange = IntRange.EMPTY

  /** Number of 2D tiles currently retained in memory (never exceeds [maxCachedTiles]). */
  val cachedTileCount: Int
    get() = tiles.size

  /** Fetches the dataset's full column schema once so wide/enriched columns appear immediately. */
  fun ensureSchemaLoaded(scope: CoroutineScope) {
    if (_schema.value != null || schemaJob?.isActive == true) return
    schemaJob = scope.launch {
      try {
        _schema.value = repository.fetchSchema(surveyId, datasetId)
        bumpRevision()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        // Leave schema null so the next open or scroll attempt can retry.
      }
    }
  }

  /**
   * Ensures all 2D tiles overlapping [rowRange] × [colRange] are cached or loading, cancelling
   * stale far-away in-flight requests and evicting least-recently-used off-screen tiles.
   */
  fun onViewportChanged(
    scope: CoroutineScope,
    rowRange: IntRange,
    colRange: IntRange,
    rowEntityIds: List<String>,
    columnKeys: List<String>,
  ) {
    val hasViewport = !rowRange.isEmpty() && !colRange.isEmpty()
    val hasData = rowEntityIds.isNotEmpty() && columnKeys.isNotEmpty()
    if (!hasViewport || !hasData) return

    val firstRowPage = rowRange.first.coerceIn(0, rowEntityIds.lastIndex) / rowPageSize
    val lastRowPage = rowRange.last.coerceIn(0, rowEntityIds.lastIndex) / rowPageSize
    val firstColChunk = colRange.first.coerceIn(0, columnKeys.lastIndex) / colChunkSize
    val lastColChunk = colRange.last.coerceIn(0, columnKeys.lastIndex) / colChunkSize
    activeRowPages = firstRowPage..lastRowPage
    activeColChunks = firstColChunk..lastColChunk

    cancelStaleRequests(firstRowPage, lastRowPage, firstColChunk, lastColChunk)

    for (rp in activeRowPages) {
      val pageEntityIds = slicePage(rowEntityIds, rp, rowPageSize).filter { it.isNotBlank() }
      if (pageEntityIds.isEmpty()) continue
      for (cc in activeColChunks) {
        val chunkColumnKeys = slicePage(columnKeys, cc, colChunkSize).filter { it.isNotBlank() }
        if (chunkColumnKeys.isNotEmpty()) {
          ensureTileLoaded(scope, TileKey(rp, cc), pageEntityIds, chunkColumnKeys)
        }
      }
    }
  }

  private fun slicePage(items: List<String>, page: Int, pageSize: Int): List<String> {
    val start = page * pageSize
    val endExclusive = minOf(start + pageSize, items.size)
    return if (start < endExclusive) items.subList(start, endExclusive).toList() else emptyList()
  }

  private fun ensureTileLoaded(
    scope: CoroutineScope,
    key: TileKey,
    entityIds: List<String>,
    columnKeys: List<String>,
  ) {
    val existing = tiles[key]
    if (existing != null && existing.matches(entityIds, columnKeys)) {
      tiles.remove(key)
      tiles[key] = existing
      return
    }
    val active = inFlight[key]
    if (active != null && active.job.isActive) {
      if (active.entityIds == entityIds && active.columnKeys == columnKeys) return
      active.job.cancel()
      inFlight.remove(key)
    }
    val columnKeySet = columnKeys.toSet()
    val job = scope.launch {
      try {
        val fetched =
          repository.fetchEntityProperties(
            surveyId = surveyId,
            datasetId = datasetId,
            entityIds = entityIds,
            propertyKeys = columnKeys,
          )
        tiles.remove(key)
        tiles[key] = CachedTile(entityIds, columnKeys, columnKeySet, fetched)
        evictOverflow()
        bumpRevision()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        // Drop from inFlight so subsequent scrolls can retry cleanly.
      } finally {
        if (inFlight[key]?.job == coroutineContext[Job]) {
          inFlight.remove(key)
        }
      }
    }
    inFlight[key] = InFlightTile(entityIds, columnKeys, job)
  }

  private fun CachedTile.matches(ids: List<String>, cols: List<String>): Boolean =
    entityIds == ids && columnKeys == cols

  /**
   * Returns the current [TableCellLoadState] for `(entityId, columnKey)` at `(rowIndex, colIndex)`.
   *
   * When [hasLocalValue] is true (e.g. a core synced property or local edit), [localValue] is
   * returned immediately without waiting for the remote tile.
   */
  fun cellState(
    entityId: String,
    rowIndex: Int,
    colIndex: Int,
    columnKey: String,
    hasLocalValue: Boolean = false,
    localValue: String = "",
  ): TableCellLoadState {
    val override = localOverrides[entityId]?.get(columnKey)
    val tile =
      if (rowIndex >= 0 && colIndex >= 0) {
        tiles[TileKey(rowIndex / rowPageSize, colIndex / colChunkSize)]
      } else {
        null
      }
    return when {
      override != null -> TableCellLoadState.Loaded(override)
      hasLocalValue -> TableCellLoadState.Loaded(localValue)
      rowIndex < 0 || colIndex < 0 -> TableCellLoadState.Loaded("")
      tile == null || columnKey !in tile.columnKeySet -> TableCellLoadState.Loading
      else -> TableCellLoadState.Loaded(tile.values[entityId]?.get(columnKey).orEmpty())
    }
  }

  /** Records an optimistic user edit so the edited cell updates immediately before publish. */
  fun putLocalValue(entityId: String, columnKey: String, value: String) {
    localOverrides.getOrPut(entityId) { LinkedHashMap() }[columnKey] = value
    bumpRevision()
  }

  /** Clears cached tiles and cancels in-flight requests. */
  fun clear() {
    schemaJob?.cancel()
    schemaJob = null
    inFlight.values.forEach { it.job.cancel() }
    inFlight.clear()
    tiles.clear()
    localOverrides.clear()
    _schema.value = null
    bumpRevision()
  }

  private fun cancelStaleRequests(
    firstRowPage: Int,
    lastRowPage: Int,
    firstColChunk: Int,
    lastColChunk: Int,
  ) {
    val midRow = (firstRowPage + lastRowPage) / 2
    val midCol = (firstColChunk + lastColChunk) / 2
    val iter = inFlight.entries.iterator()
    while (iter.hasNext()) {
      val (key, entry) = iter.next()
      val farRow = abs(key.rowPage - midRow) > CANCEL_DISTANCE_PAGES
      val farCol = abs(key.colChunk - midCol) > CANCEL_DISTANCE_PAGES
      if (farRow || farCol) {
        entry.job.cancel()
        iter.remove()
      }
    }
  }

  private fun evictOverflow() {
    if (tiles.size <= maxCachedTiles) return
    val iter = tiles.entries.iterator()
    while (tiles.size > maxCachedTiles && iter.hasNext()) {
      val (key, _) = iter.next()
      if (key.rowPage !in activeRowPages || key.colChunk !in activeColChunks) {
        iter.remove()
      }
    }
    // If all tiles are somehow inside a giant viewport, still enforce the hard cap.
    val hardIter = tiles.entries.iterator()
    while (tiles.size > maxCachedTiles && hardIter.hasNext()) {
      hardIter.next()
      hardIter.remove()
    }
  }

  private fun bumpRevision() {
    _revision.value = _revision.value + 1
  }

  companion object {
    const val DEFAULT_ROW_PAGE_SIZE = 50
    const val DEFAULT_COL_CHUNK_SIZE = 24
    const val DEFAULT_MAX_CACHED_TILES = 12
    private const val CANCEL_DISTANCE_PAGES = 2
  }
}
