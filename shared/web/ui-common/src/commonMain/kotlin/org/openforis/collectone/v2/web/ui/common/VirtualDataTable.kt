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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val DEFAULT_COLUMN_OVERSCAN = 2
private const val INITIAL_PREFETCH_ROWS = 20
private val ScrollbarThickness = 14.dp
private val ScrollbarThumbPadding = 3.dp
private val MinScrollbarThumbSize = 36.dp
private val HorizontalWheelStep = 40.dp

/** Semantics testTag applied to [VirtualDataTable]'s interactive horizontal scrollbar track. */
const val VIRTUAL_TABLE_HORIZONTAL_SCROLLBAR_TAG = "virtual_table_horizontal_scrollbar"

/** Semantics testTag applied to [VirtualDataTable]'s interactive vertical scrollbar track. */
const val VIRTUAL_TABLE_VERTICAL_SCROLLBAR_TAG = "virtual_table_vertical_scrollbar"

/**
 * Precomputes cumulative horizontal pixel offsets of [columnWidthsPx]: `offsets[i]` is the left
 * edge of column `i`, and `offsets[columnWidthsPx.size]` is the total column width.
 */
fun computeColumnOffsetsPx(columnWidthsPx: FloatArray): FloatArray {
  val offsets = FloatArray(columnWidthsPx.size + 1)
  var acc = 0f
  for (i in columnWidthsPx.indices) {
    offsets[i] = acc
    acc += columnWidthsPx[i].coerceAtLeast(1f)
  }
  offsets[columnWidthsPx.size] = acc
  return offsets
}

/**
 * Returns the inclusive column index range intersecting `[scrollPx, scrollPx + viewportWidthPx]`
 * (after [leadingWidthPx]), padded by [overscan] columns on each side. Runs in O(log C) via binary
 * search over [columnOffsetsPx].
 */
fun visibleVariableColumnRange(
  columnOffsetsPx: FloatArray,
  scrollPx: Float,
  viewportWidthPx: Int,
  leadingWidthPx: Float = 0f,
  overscan: Int = DEFAULT_COLUMN_OVERSCAN,
): IntRange {
  val columnCount = columnOffsetsPx.size - 1
  if (columnCount <= 0) return IntRange.EMPTY
  if (viewportWidthPx <= 0) return 0 until columnCount
  val visibleStart = (scrollPx - leadingWidthPx).coerceAtLeast(0f)
  val visibleEnd = (scrollPx + viewportWidthPx - leadingWidthPx).coerceAtLeast(visibleStart)
  val firstVisible = findColumnAtOffset(columnOffsetsPx, visibleStart)
  val lastVisible = findColumnAtOffset(columnOffsetsPx, visibleEnd)
  val first = (firstVisible - overscan).coerceIn(0, columnCount - 1)
  val last = (lastVisible + overscan).coerceIn(first, columnCount - 1)
  return first..last
}

private fun findColumnAtOffset(offsets: FloatArray, x: Float): Int {
  val lastCol = offsets.size - 2
  if (x <= 0f) return 0
  if (x >= offsets[lastCol]) return lastCol
  var low = 0
  var high = lastCol
  while (low < high) {
    val mid = (low + high + 1) ushr 1
    if (offsets[mid] <= x) low = mid else high = mid - 1
  }
  return low
}

/**
 * 2D-virtualized spreadsheet table with a sticky header, variable column widths, interactive
 * horizontal/vertical scrollbars, and viewport window notifications for lazy on-scroll loading.
 *
 * Only the visible rows (via [LazyColumn]) and visible columns (via [visibleVariableColumnRange]
 * with leading/trailing spacers) are composed, keeping DOM and Skia work O(visibleRows ×
 * visibleCols) regardless of dataset size.
 */
@Composable
fun VirtualDataTable(
  rowCount: Int,
  rowKey: (Int) -> Any,
  columnWidths: List<Dp>,
  modifier: Modifier = Modifier,
  listState: LazyListState = remember { LazyListState() },
  horizontalScroll: ScrollState = rememberScrollState(),
  leadingWidth: Dp = 0.dp,
  trailingWidth: Dp = 0.dp,
  headerBackground: Color = Color.Transparent,
  showHeaderDivider: Boolean = false,
  scrollToRowIndex: Int = -1,
  onVisibleWindowChanged: ((rowRange: IntRange, colRange: IntRange) -> Unit)? = null,
  emptyContent: @Composable () -> Unit = {},
  leadingHeader: (@Composable () -> Unit)? = null,
  headerCell: @Composable (colIndex: Int, width: Dp) -> Unit,
  trailingHeader: (@Composable () -> Unit)? = null,
  rowModifier: @Composable (rowIndex: Int) -> Modifier = { Modifier },
  leadingCell: (@Composable (rowIndex: Int) -> Unit)? = null,
  cell: @Composable (rowIndex: Int, colIndex: Int, width: Dp) -> Unit,
  trailingCell: (@Composable (rowIndex: Int) -> Unit)? = null,
) {
  ScrollToRowEffect(scrollToRowIndex, rowCount, listState)

  BoxWithConstraints(modifier = modifier) {
    val density = LocalDensity.current
    val viewportWidthPx = constraints.maxWidth
    val leadingWidthPx = with(density) { leadingWidth.toPx() }
    val columnOffsetsPx =
      remember(columnWidths, density) {
        computeColumnOffsetsPx(
          FloatArray(columnWidths.size) { with(density) { columnWidths[it].toPx() } }
        )
      }
    val tableWidth =
      maxOf(leadingWidth + columnWidths.fold(0.dp) { acc, w -> acc + w } + trailingWidth, maxWidth)

    val visibleColumns by
      remember(columnOffsetsPx, viewportWidthPx, leadingWidthPx) {
        derivedStateOf {
          visibleVariableColumnRange(
            columnOffsetsPx,
            horizontalScroll.value.toFloat(),
            viewportWidthPx,
            leadingWidthPx,
          )
        }
      }

    VirtualTableViewportEffect(
      rowCount,
      columnOffsetsPx,
      viewportWidthPx,
      listState,
      horizontalScroll,
      { visibleColumns },
      onVisibleWindowChanged,
    )

    val leadingSpacerWidth = computeLeadingSpacerWidth(visibleColumns, columnOffsetsPx, density)
    val trailingSpacerWidth = computeTrailingSpacerWidth(visibleColumns, columnOffsetsPx, density)
    val hasHorizontalOverflow = horizontalScroll.maxValue > 0
    val hasVerticalOverflow = rowCount > 0 && listState.layoutInfo.visibleItemsInfo.size < rowCount

    Box(modifier = Modifier.fillMaxSize()) {
      VirtualTableScrollableGrid(
        rowCount = rowCount,
        rowKey = rowKey,
        columnWidths = columnWidths,
        visibleColumns = visibleColumns,
        leadingSpacerWidth = leadingSpacerWidth,
        trailingSpacerWidth = trailingSpacerWidth,
        tableWidth = tableWidth,
        listState = listState,
        horizontalScroll = horizontalScroll,
        hasHorizontalOverflow = hasHorizontalOverflow,
        hasVerticalOverflow = hasVerticalOverflow,
        headerBackground = headerBackground,
        showHeaderDivider = showHeaderDivider,
        emptyContent = emptyContent,
        leadingHeader = leadingHeader,
        headerCell = headerCell,
        trailingHeader = trailingHeader,
        rowModifier = rowModifier,
        leadingCell = leadingCell,
        cell = cell,
        trailingCell = trailingCell,
      )
      VirtualTableScrollbars(
        horizontalScroll = horizontalScroll,
        listState = listState,
        rowCount = rowCount,
        hasHorizontalOverflow = hasHorizontalOverflow,
        hasVerticalOverflow = hasVerticalOverflow,
      )
    }
  }
}

@Composable
private fun ScrollToRowEffect(scrollToRowIndex: Int, rowCount: Int, listState: LazyListState) {
  LaunchedEffect(scrollToRowIndex, rowCount) {
    if (scrollToRowIndex in 0 until rowCount) {
      val alreadyVisible =
        listState.layoutInfo.visibleItemsInfo.any { it.index == scrollToRowIndex }
      if (!alreadyVisible) listState.animateScrollToItem(scrollToRowIndex)
    }
  }
}

@Composable
private fun VirtualTableViewportEffect(
  rowCount: Int,
  columnOffsetsPx: FloatArray,
  viewportWidthPx: Int,
  listState: LazyListState,
  horizontalScroll: ScrollState,
  visibleColumns: () -> IntRange,
  onVisibleWindowChanged: ((rowRange: IntRange, colRange: IntRange) -> Unit)?,
) {
  val latestWindowCallback by rememberUpdatedState(onVisibleWindowChanged)
  LaunchedEffect(rowCount, columnOffsetsPx, viewportWidthPx, listState, horizontalScroll) {
    snapshotFlow {
      val visibleItems = listState.layoutInfo.visibleItemsInfo
      val rowRange =
        when {
          visibleItems.isNotEmpty() -> visibleItems.first().index..visibleItems.last().index
          rowCount > 0 -> 0..minOf(rowCount - 1, INITIAL_PREFETCH_ROWS - 1)
          else -> IntRange.EMPTY
        }
      rowRange to visibleColumns()
    }
      .distinctUntilChanged()
      .collect { (rowRange, colRange) -> latestWindowCallback?.invoke(rowRange, colRange) }
  }
}

private fun computeLeadingSpacerWidth(
  visibleColumns: IntRange,
  columnOffsetsPx: FloatArray,
  density: androidx.compose.ui.unit.Density,
): Dp =
  if (visibleColumns.isEmpty() || visibleColumns.first == 0) 0.dp
  else with(density) { columnOffsetsPx[visibleColumns.first].toDp() }

private fun computeTrailingSpacerWidth(
  visibleColumns: IntRange,
  columnOffsetsPx: FloatArray,
  density: androidx.compose.ui.unit.Density,
): Dp {
  val lastCol = columnOffsetsPx.size - 2
  if (visibleColumns.isEmpty() || visibleColumns.last >= lastCol) return 0.dp
  val trailingPx =
    (columnOffsetsPx[lastCol + 1] - columnOffsetsPx[visibleColumns.last + 1]).coerceAtLeast(0f)
  return with(density) { trailingPx.toDp() }
}

@Composable
private fun VirtualTableScrollableGrid(
  rowCount: Int,
  rowKey: (Int) -> Any,
  columnWidths: List<Dp>,
  visibleColumns: IntRange,
  leadingSpacerWidth: Dp,
  trailingSpacerWidth: Dp,
  tableWidth: Dp,
  listState: LazyListState,
  horizontalScroll: ScrollState,
  hasHorizontalOverflow: Boolean,
  hasVerticalOverflow: Boolean,
  headerBackground: Color,
  showHeaderDivider: Boolean,
  emptyContent: @Composable () -> Unit,
  leadingHeader: (@Composable () -> Unit)?,
  headerCell: @Composable (colIndex: Int, width: Dp) -> Unit,
  trailingHeader: (@Composable () -> Unit)?,
  rowModifier: @Composable (rowIndex: Int) -> Modifier,
  leadingCell: (@Composable (rowIndex: Int) -> Unit)?,
  cell: @Composable (rowIndex: Int, colIndex: Int, width: Dp) -> Unit,
  trailingCell: (@Composable (rowIndex: Int) -> Unit)?,
) {
  val wheelStepPx = with(LocalDensity.current) { HorizontalWheelStep.toPx() }
  Box(
    modifier =
      Modifier.fillMaxSize()
        .padding(
          end = if (hasVerticalOverflow) ScrollbarThickness else 0.dp,
          bottom = if (hasHorizontalOverflow) ScrollbarThickness else 0.dp,
        )
        .horizontalWheelScroll(horizontalScroll, wheelStepPx)
        .horizontalScroll(horizontalScroll)
  ) {
    Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
      Row(
        modifier = Modifier.fillMaxWidth().background(headerBackground),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        leadingHeader?.invoke()
        if (leadingSpacerWidth > 0.dp) Spacer(Modifier.width(leadingSpacerWidth))
        for (col in visibleColumns) headerCell(col, columnWidths[col])
        if (trailingSpacerWidth > 0.dp) Spacer(Modifier.width(trailingSpacerWidth))
        trailingHeader?.invoke()
      }
      if (showHeaderDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
      if (rowCount == 0) {
        emptyContent()
      } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
          items(count = rowCount, key = rowKey) { rowIndex ->
            Row(modifier = rowModifier(rowIndex), verticalAlignment = Alignment.CenterVertically) {
              leadingCell?.invoke(rowIndex)
              if (leadingSpacerWidth > 0.dp) Spacer(Modifier.width(leadingSpacerWidth))
              for (col in visibleColumns) cell(rowIndex, col, columnWidths[col])
              if (trailingSpacerWidth > 0.dp) Spacer(Modifier.width(trailingSpacerWidth))
              trailingCell?.invoke(rowIndex)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VirtualTableScrollbars(
  horizontalScroll: ScrollState,
  listState: LazyListState,
  rowCount: Int,
  hasHorizontalOverflow: Boolean,
  hasVerticalOverflow: Boolean,
) {
  if (hasHorizontalOverflow) {
    VirtualHorizontalScrollbar(
      scrollState = horizontalScroll,
      modifier =
        Modifier.align(Alignment.BottomStart)
          .fillMaxWidth()
          .padding(end = if (hasVerticalOverflow) ScrollbarThickness else 0.dp)
          .height(ScrollbarThickness),
    )
  }
  if (hasVerticalOverflow) {
    VirtualVerticalScrollbar(
      listState = listState,
      rowCount = rowCount,
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .fillMaxHeight()
          .padding(bottom = if (hasHorizontalOverflow) ScrollbarThickness else 0.dp)
          .width(ScrollbarThickness),
    )
  }
}

/** Subtle Material 3 placeholder shown in a cell while its lazy-loaded page tile is in flight. */
@Composable
fun VirtualTableLoadingCell(
  width: Dp,
  height: Dp,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.width(width).height(height).padding(horizontal = 10.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth(0.65f)
          .height(10.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
    )
  }
}

/**
 * Bridges trackpad horizontal wheel deltas (`scrollDelta.x`) and Shift+wheel (`scrollDelta.y`) to
 * [horizontalScroll] before the nested vertical [LazyColumn] consumes the scroll event.
 */
private fun Modifier.horizontalWheelScroll(
  horizontalScroll: ScrollState,
  wheelStepPx: Float,
): Modifier =
  pointerInput(horizontalScroll, wheelStepPx) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.type != PointerEventType.Scroll) continue
        var dx = 0f
        event.changes.forEach { change ->
          dx += change.scrollDelta.x
          if (event.keyboardModifiers.isShiftPressed && change.scrollDelta.x == 0f) {
            dx += change.scrollDelta.y
          }
        }
        if (dx != 0f) {
          horizontalScroll.dispatchRawDelta(dx * wheelStepPx)
          event.changes.forEach { it.consume() }
        }
      }
    }
  }

@Composable
private fun VirtualHorizontalScrollbar(
  scrollState: ScrollState,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val maxScroll = scrollState.maxValue
  if (maxScroll <= 0) return

  BoxWithConstraints(
    modifier =
      modifier
        .testTag(VIRTUAL_TABLE_HORIZONTAL_SCROLLBAR_TAG)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
  ) {
    val trackPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val minThumbPx = with(density) { MinScrollbarThumbSize.toPx() }.coerceAtMost(trackPx)
    val totalPx = trackPx + maxScroll.toFloat()
    val thumbPx = (trackPx * (trackPx / totalPx)).coerceIn(minThumbPx, trackPx)
    val travelPx = (trackPx - thumbPx).coerceAtLeast(1f)
    val fraction = (scrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
    val thumbOffsetPx = (fraction * travelPx).roundToInt()

    val currentThumbPx by rememberUpdatedState(thumbPx)
    val currentTravelPx by rememberUpdatedState(travelPx)
    val currentMaxScroll by rememberUpdatedState(maxScroll)
    val currentThumbOffsetPx by rememberUpdatedState(thumbOffsetPx)

    Box(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(scrollState) {
            detectTapGestures { offset ->
              val f = ((offset.x - currentThumbPx / 2f) / currentTravelPx).coerceIn(0f, 1f)
              scope.launch { scrollState.scrollTo((f * currentMaxScroll).roundToInt()) }
            }
          }
          .pointerInput(scrollState) {
            var dragOffset = 0f
            detectHorizontalDragGestures(
              onDragStart = { start ->
                val onThumb =
                  start.x >= currentThumbOffsetPx &&
                    start.x <= currentThumbOffsetPx + currentThumbPx
                dragOffset =
                  if (onThumb) currentThumbOffsetPx.toFloat()
                  else (start.x - currentThumbPx / 2f).coerceIn(0f, currentTravelPx)
                if (!onThumb) {
                  val f = (dragOffset / currentTravelPx).coerceIn(0f, 1f)
                  scope.launch { scrollState.scrollTo((f * currentMaxScroll).roundToInt()) }
                }
              },
              onHorizontalDrag = { change, dragAmount ->
                change.consume()
                dragOffset = (dragOffset + dragAmount).coerceIn(0f, currentTravelPx)
                val f = (dragOffset / currentTravelPx).coerceIn(0f, 1f)
                scope.launch { scrollState.scrollTo((f * currentMaxScroll).roundToInt()) }
              },
            )
          }
    ) {
      Box(
        modifier =
          Modifier.offset { IntOffset(thumbOffsetPx, 0) }
            .width(with(density) { thumbPx.toDp() })
            .fillMaxHeight()
            .padding(vertical = ScrollbarThumbPadding)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
      )
    }
  }
}

@Composable
private fun VirtualVerticalScrollbar(
  listState: LazyListState,
  rowCount: Int,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
  if (rowCount <= visibleCount) return

  BoxWithConstraints(
    modifier =
      modifier
        .testTag(VIRTUAL_TABLE_VERTICAL_SCROLLBAR_TAG)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
  ) {
    val trackPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val minThumbPx = with(density) { MinScrollbarThumbSize.toPx() }.coerceAtMost(trackPx)
    val thumbPx =
      (trackPx * (visibleCount.toFloat() / rowCount.toFloat())).coerceIn(minThumbPx, trackPx)
    val travelPx = (trackPx - thumbPx).coerceAtLeast(1f)
    val maxFirstIndex = (rowCount - visibleCount).coerceAtLeast(1)
    val fraction =
      (listState.firstVisibleItemIndex.toFloat() / maxFirstIndex.toFloat()).coerceIn(0f, 1f)
    val thumbOffsetPx = (fraction * travelPx).roundToInt()

    val currentThumbPx by rememberUpdatedState(thumbPx)
    val currentTravelPx by rememberUpdatedState(travelPx)
    val currentMaxFirstIndex by rememberUpdatedState(maxFirstIndex)
    val currentRowCount by rememberUpdatedState(rowCount)
    val currentThumbOffsetPx by rememberUpdatedState(thumbOffsetPx)

    Box(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(listState) {
            detectTapGestures { offset ->
              val f = ((offset.y - currentThumbPx / 2f) / currentTravelPx).coerceIn(0f, 1f)
              val target = (f * currentMaxFirstIndex).roundToInt().coerceIn(0, currentRowCount - 1)
              scope.launch { listState.scrollToItem(target) }
            }
          }
          .pointerInput(listState) {
            var dragOffset = 0f
            detectVerticalDragGestures(
              onDragStart = { start ->
                val onThumb =
                  start.y >= currentThumbOffsetPx &&
                    start.y <= currentThumbOffsetPx + currentThumbPx
                dragOffset =
                  if (onThumb) currentThumbOffsetPx.toFloat()
                  else (start.y - currentThumbPx / 2f).coerceIn(0f, currentTravelPx)
                if (!onThumb) {
                  val f = (dragOffset / currentTravelPx).coerceIn(0f, 1f)
                  val target =
                    (f * currentMaxFirstIndex).roundToInt().coerceIn(0, currentRowCount - 1)
                  scope.launch { listState.scrollToItem(target) }
                }
              },
              onVerticalDrag = { change, dragAmount ->
                change.consume()
                dragOffset = (dragOffset + dragAmount).coerceIn(0f, currentTravelPx)
                val f = (dragOffset / currentTravelPx).coerceIn(0f, 1f)
                val target =
                  (f * currentMaxFirstIndex).roundToInt().coerceIn(0, currentRowCount - 1)
                scope.launch { listState.scrollToItem(target) }
              },
            )
          }
    ) {
      Box(
        modifier =
          Modifier.offset { IntOffset(0, thumbOffsetPx) }
            .height(with(density) { thumbPx.toDp() })
            .fillMaxWidth()
            .padding(horizontal = ScrollbarThumbPadding)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
      )
    }
  }
}
