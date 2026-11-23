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
package org.openforis.collectone.v2.web.ui.timeseries

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.openforis.collectone.v2.app.domain.model.editor.formatFixed
import org.openforis.collectone.v2.app.domain.model.editor.utcMillisToIsoDate

/**
 * Scatter-and-line chart of one band's plot means over time (Collect Earth Online Geo-Dash's
 * degradation chart), drawn in [color]. Points without a value (masked by clouds) are skipped.
 * Hovering shows an observation's date and value; clicking or ← / → selects one, Esc clears the
 * selection.
 */
@Composable
internal fun TimeSeriesChart(
  timesMs: List<Long>,
  values: List<Double>,
  selectedIndex: Int?,
  onSelect: (Int?) -> Unit,
  onStep: (Int) -> Unit,
  onClear: () -> Unit,
  contentDescription: String,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  val textMeasurer = rememberTextMeasurer()
  val labelStyle =
    MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
  val colors =
    ChartColors(
      line = color.copy(alpha = 0.45f),
      point = color,
      // Neutral, so the selection stands out from series of any color.
      selected = MaterialTheme.colorScheme.onSurface,
      grid = MaterialTheme.colorScheme.outlineVariant,
      tooltip = MaterialTheme.colorScheme.inverseSurface,
      tooltipText = MaterialTheme.colorScheme.inverseOnSurface,
    )
  val points = remember(timesMs, values) { ChartPoints.of(timesMs, values) }
  var hoverX by remember { mutableStateOf<Float?>(null) }
  var canvasSize by remember { mutableStateOf(Size.Zero) }
  val focusRequester = remember { FocusRequester() }

  Canvas(
    modifier =
      modifier
        .semantics { this.contentDescription = contentDescription }
        .onKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
          when (event.key) {
            Key.DirectionLeft -> onStep(-1)
            Key.DirectionRight -> onStep(1)
            Key.Escape -> onClear()
            else -> return@onKeyEvent false
          }
          true
        }
        .focusRequester(focusRequester)
        .focusable()
        .pointerInput(points) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              hoverX =
                when (event.type) {
                  PointerEventType.Exit -> null
                  else -> event.changes.firstOrNull()?.position?.x
                }
            }
          }
        }
        .pointerInput(points) {
          detectTapGestures { offset ->
            focusRequester.requestFocus()
            onSelect(ChartLayout(canvasSize, points, this).nearest(offset.x))
          }
        }
  ) {
    canvasSize = size
    if (points.isEmpty) return@Canvas
    val layout = ChartLayout(size, points, this)
    drawAxes(layout, textMeasurer, labelStyle, colors)
    drawSeries(layout, colors)
    selectedIndex?.let { index -> layout.offsetOf(index)?.let { drawSelected(layout, it, colors) } }
    hoverX?.let(layout::nearest)?.let { index ->
      drawTooltip(layout, index, textMeasurer, labelStyle, colors)
    }
  }
}

private data class ChartColors(
  val line: Color,
  val point: Color,
  val selected: Color,
  val grid: Color,
  val tooltip: Color,
  val tooltipText: Color,
)

/** The observations with a value, and the extent of their times and values. */
internal class ChartPoints
private constructor(
  val indices: List<Int>,
  val timesMs: List<Long>,
  val values: List<Double>,
) {
  val isEmpty: Boolean
    get() = indices.isEmpty()

  val minTime = timesMs.minOrNull() ?: 0L
  val maxTime = timesMs.maxOrNull() ?: 0L
  val minValue: Double
  val maxValue: Double

  init {
    val low = values.minOrNull() ?: 0.0
    val high = values.maxOrNull() ?: 1.0
    val pad = if (high - low < 1e-9) 0.1 else (high - low) * 0.08
    minValue = low - pad
    maxValue = high + pad
  }

  companion object {
    fun of(timesMs: List<Long>, values: List<Double>): ChartPoints {
      val indices = values.indices.filter { it < timesMs.size && !values[it].isNaN() }
      return ChartPoints(indices, indices.map { timesMs[it] }, indices.map { values[it] })
    }
  }
}

/** Pixel layout of a chart: plot area inside label margins, and data-to-pixel mapping. */
internal class ChartLayout(
  val size: Size,
  val points: ChartPoints,
  density: androidx.compose.ui.unit.Density,
) {
  val left = with(density) { 44.dp.toPx() }
  val right = size.width - with(density) { 12.dp.toPx() }
  val top = with(density) { 8.dp.toPx() }
  val bottom = size.height - with(density) { 20.dp.toPx() }

  private val timeSpan = (points.maxTime - points.minTime).coerceAtLeast(1L).toDouble()
  private val valueSpan = (points.maxValue - points.minValue).coerceAtLeast(1e-9)

  fun x(timeMs: Long): Float =
    left + ((timeMs - points.minTime) / timeSpan * (right - left)).toFloat()

  fun y(value: Double): Float =
    bottom - ((value - points.minValue) / valueSpan * (bottom - top)).toFloat()

  /** Pixel position of observation [index], or `null` if it has no value. */
  fun offsetOf(index: Int): Offset? {
    val position = points.indices.indexOf(index).takeIf { it >= 0 } ?: return null
    return Offset(x(points.timesMs[position]), y(points.values[position]))
  }

  /** The observation drawn nearest to horizontal position [px], or `null` without points. */
  fun nearest(px: Float): Int? {
    if (points.isEmpty || right <= left) return null
    var best = -1
    var bestDistance = Float.MAX_VALUE
    points.timesMs.forEachIndexed { position, time ->
      val distance = abs(x(time) - px)
      if (distance < bestDistance) {
        bestDistance = distance
        best = position
      }
    }
    return points.indices.getOrNull(best)
  }
}

private fun DrawScope.drawAxes(
  layout: ChartLayout,
  textMeasurer: TextMeasurer,
  style: TextStyle,
  colors: ChartColors,
) {
  // Horizontal grid lines and value labels.
  for (tick in niceTicks(layout.points.minValue, layout.points.maxValue, 5)) {
    val y = layout.y(tick)
    if (y < layout.top - 1 || y > layout.bottom + 1) continue
    drawLine(colors.grid, Offset(layout.left, y), Offset(layout.right, y), strokeWidth = 1f)
    val label = textMeasurer.measure(formatTick(tick), style)
    drawText(
      label,
      topLeft = Offset(layout.left - label.size.width - 6f, y - label.size.height / 2f),
    )
  }
  // Year labels.
  val firstYear = yearOf(layout.points.minTime)
  val lastYear = yearOf(layout.points.maxTime)
  val step = ((lastYear - firstYear) / 10 + 1).coerceAtLeast(1)
  for (year in firstYear..lastYear + 1 step step) {
    val x = layout.x(yearStartMs(year))
    if (x < layout.left || x > layout.right) continue
    drawLine(colors.grid, Offset(x, layout.bottom), Offset(x, layout.bottom + 4f), strokeWidth = 1f)
    val label = textMeasurer.measure(year.toString(), style)
    drawText(label, topLeft = Offset(x - label.size.width / 2f, layout.bottom + 4f))
  }
}

private fun DrawScope.drawSeries(layout: ChartLayout, colors: ChartColors) {
  val points = layout.points
  val path = Path()
  points.timesMs.forEachIndexed { i, time ->
    val x = layout.x(time)
    val y = layout.y(points.values[i])
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  drawPath(path, colors.line, style = Stroke(width = 1.5f))
  val radius = 2.5.dp.toPx()
  points.timesMs.forEachIndexed { i, time ->
    drawCircle(colors.point, radius, Offset(layout.x(time), layout.y(points.values[i])))
  }
}

/** The selected observation: a guide line across the plot area and a ringed marker. */
private fun DrawScope.drawSelected(layout: ChartLayout, center: Offset, colors: ChartColors) {
  drawLine(
    colors.selected.copy(alpha = 0.6f),
    Offset(center.x, layout.top),
    Offset(center.x, layout.bottom),
    strokeWidth = 1.5.dp.toPx(),
  )
  drawCircle(colors.selected, 6.dp.toPx(), center)
  drawCircle(Color.White, 6.dp.toPx(), center, style = Stroke(width = 2.dp.toPx()))
}

private fun DrawScope.drawTooltip(
  layout: ChartLayout,
  index: Int,
  textMeasurer: TextMeasurer,
  style: TextStyle,
  colors: ChartColors,
) {
  val center = layout.offsetOf(index) ?: return
  val position = layout.points.indices.indexOf(index)
  drawCircle(colors.point, 4.5.dp.toPx(), center)
  val text =
    "${utcMillisToIsoDate(layout.points.timesMs[position])}  " +
      formatTick(layout.points.values[position], decimals = 3)
  val measured = textMeasurer.measure(text, style.copy(color = colors.tooltipText))
  val padding = 6.dp.toPx()
  val width = measured.size.width + padding * 2
  val height = measured.size.height + padding * 2
  val x =
    (center.x - width / 2).coerceIn(layout.left, (layout.right - width).coerceAtLeast(layout.left))
  val y = if (center.y - height - 10f > layout.top) center.y - height - 10f else center.y + 10f
  drawRoundRect(colors.tooltip, Offset(x, y), Size(width, height), CornerRadius(6f, 6f))
  drawText(measured, topLeft = Offset(x + padding, y + padding))
}

/** About [count] round tick values covering [min]..[max]. */
internal fun niceTicks(min: Double, max: Double, count: Int): List<Double> {
  if (max <= min || count <= 0) return listOf(min)
  val rawStep = (max - min) / count
  val magnitude = 10.0.pow(floor(log10(rawStep)))
  val step =
    when (rawStep / magnitude) {
      in 0.0..1.5 -> 1.0
      in 1.5..3.0 -> 2.0
      in 3.0..7.0 -> 5.0
      else -> 10.0
    } * magnitude
  val first = ceil(min / step) * step
  return generateSequence(first) { it + step }.takeWhile { it <= max + step * 1e-6 }.toList()
}

private fun formatTick(value: Double, decimals: Int = 2): String =
  if (abs(value) >= 100) formatFixed(value, 0) else formatFixed(value, decimals)

/** The UTC calendar year of [timeMs] (epoch milliseconds). */
internal fun yearOf(timeMs: Long): Int =
  Instant.fromEpochMilliseconds(timeMs).toLocalDateTime(TimeZone.UTC).year

/** Epoch milliseconds of New Year (January 1, 00:00 UTC) of [year]. */
internal fun yearStartMs(year: Int): Long =
  LocalDate(year, 1, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
