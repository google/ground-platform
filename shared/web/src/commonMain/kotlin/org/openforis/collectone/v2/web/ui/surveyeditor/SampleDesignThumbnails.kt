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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode

/** Colors used to draw a sample design thumbnail. */
internal data class ThumbnailColors(
  /** Fill of the area being sampled. */
  val area: Color,
  /** Outline of the area being sampled. */
  val areaOutline: Color,
  /** Sample plots. */
  val plot: Color,
  /** Sample points inside a plot. */
  val sample: Color,
)

/**
 * A single-choice picker that shows each option as a selectable card with a drawn thumbnail above
 * its label, in place of a dropdown.
 *
 * All options sit on one row: the row is split into [THUMBNAIL_COLUMNS] equal slots (or one per
 * option if there are more), so cards shrink with the panel instead of wrapping, and pickers with
 * fewer options use cards of the same size as their neighbors.
 */
@Composable
internal fun <T> ThumbnailSelector(
  label: String,
  options: List<T>,
  selected: T,
  optionText: (T) -> String,
  onSelect: (T) -> Unit,
  thumbnail: DrawScope.(option: T, colors: ThumbnailColors) -> Unit,
  enabled: Boolean = true,
  supportingText: String? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      options.forEach { option ->
        ThumbnailCard(
          text = optionText(option),
          isSelected = option == selected,
          enabled = enabled,
          onClick = { onSelect(option) },
          draw = { colors -> thumbnail(option, colors) },
          modifier = Modifier.weight(1f),
        )
      }
      repeat(THUMBNAIL_COLUMNS - options.size) { Spacer(Modifier.weight(1f)) }
    }
    if (supportingText != null) {
      Text(
        supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ThumbnailCard(
  text: String,
  isSelected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  draw: DrawScope.(ThumbnailColors) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val thumbnailColors =
    ThumbnailColors(
      area = colors.surfaceContainerHighest,
      areaOutline = colors.outline,
      plot = if (isSelected) colors.secondary else colors.primary,
      sample = colors.tertiary,
    )
  OutlinedCard(
    onClick = onClick,
    enabled = enabled,
    modifier =
      modifier.semantics {
        role = Role.RadioButton
        selected = isSelected
      },
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (isSelected) colors.secondaryContainer else colors.surface,
        contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
        disabledContainerColor = if (isSelected) colors.secondaryContainer else colors.surface,
        disabledContentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        if (isSelected) 2.dp else 1.dp,
        if (isSelected) colors.secondary else colors.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Canvas(modifier = Modifier.widthIn(max = THUMBNAIL_MAX_SIZE).fillMaxWidth().aspectRatio(1f)) {
        draw(thumbnailColors)
      }
      Text(
        text,
        // No extra letter spacing, so labels fit the narrow cards of a four-option row.
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Short label of this method for its thumbnail card; the full name is shown below the picker. */
internal val SampleMethod.cardLabel: String
  get() =
    when (this) {
      SampleMethod.SYSTEMATIC_GRID -> "Grid"
      SampleMethod.SIMPLE_RANDOM -> "Random"
      SampleMethod.STRATIFIED_RANDOM -> "Stratified"
      SampleMethod.CLUSTER -> "Cluster"
    }

/** Short label of this plot shape for its thumbnail card. */
internal val PlotShapeOption.cardLabel: String
  get() = if (this == PlotShapeOption.POINT) "Point" else label

/** Short label of this sample point layout for its thumbnail card. */
internal val SubPlotMode.cardLabel: String
  get() = if (this == SubPlotMode.CENTER) "Center" else label

/** Slots per picker row; every picker in the sample design panel has at most this many options. */
private const val THUMBNAIL_COLUMNS = 4

/** Thumbnails fill their card's width up to this size. */
private val THUMBNAIL_MAX_SIZE = 56.dp

// --- Thumbnails ---
//
// All drawings use coordinates relative to the (square) canvas: (0, 0) is the top-left corner and
// (1, 1) the bottom-right, so they scale with the card.

/** A plot layout over the area being sampled. */
internal fun DrawScope.drawSampleMethodThumbnail(method: SampleMethod, colors: ThumbnailColors) {
  drawArea(colors)
  when (method) {
    SampleMethod.SYSTEMATIC_GRID -> {
      val steps = listOf(0.23f, 0.41f, 0.59f, 0.77f)
      steps.forEach { y -> steps.forEach { x -> drawPlotDot(x, y, colors) } }
    }
    SampleMethod.SIMPLE_RANDOM -> RANDOM_PLOTS.forEach { (x, y) -> drawPlotDot(x, y, colors) }
    SampleMethod.STRATIFIED_RANDOM -> {
      // Three strata of different tints, each with its own random plots.
      val bounds = listOf(AREA_INSET, 0.38f, 0.63f, 1f - AREA_INSET)
      val tints = listOf(0.10f, 0.22f, 0.34f)
      tints.forEachIndexed { i, alpha ->
        drawRect(
          color = colors.plot.copy(alpha = alpha),
          topLeft = rel(bounds[i], AREA_INSET),
          size = Size((bounds[i + 1] - bounds[i]) * u, (1f - 2 * AREA_INSET) * u),
        )
      }
      listOf(bounds[1], bounds[2]).forEach { x ->
        drawLine(
          color = colors.areaOutline,
          start = rel(x, AREA_INSET),
          end = rel(x, 1f - AREA_INSET),
          strokeWidth = 1.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        )
      }
      STRATIFIED_PLOTS.forEach { (x, y) -> drawPlotDot(x, y, colors) }
    }
    SampleMethod.CLUSTER -> {
      val offset = 0.065f
      CLUSTER_CENTERS.forEach { (cx, cy) ->
        for (dx in listOf(-offset, offset)) {
          for (dy in listOf(-offset, offset)) {
            drawPlotDot(cx + dx, cy + dy, colors, radius = 0.04f)
          }
        }
      }
    }
  }
}

/** A single plot of [shape]. */
internal fun DrawScope.drawPlotShapeThumbnail(shape: PlotShapeOption, colors: ThumbnailColors) {
  drawArea(colors)
  when (shape) {
    PlotShapeOption.SQUARE,
    PlotShapeOption.CIRCLE -> drawPlotOutline(shape, colors, halfSize = 0.26f)
    PlotShapeOption.POINT -> {
      drawCircle(color = Color.White, radius = 0.1f * u, center = rel(0.5f, 0.5f))
      drawCircle(color = colors.plot, radius = 0.075f * u, center = rel(0.5f, 0.5f))
    }
  }
}

/**
 * A plot of [plotShape] (a square for point plots, which have no sample points) with the sample
 * points of [mode] drawn as hollow rings, as on the layer map.
 */
internal fun DrawScope.drawSubPlotThumbnail(
  mode: SubPlotMode,
  plotShape: PlotShapeOption,
  colors: ThumbnailColors,
) {
  drawArea(colors)
  val shape = if (plotShape == PlotShapeOption.CIRCLE) plotShape else PlotShapeOption.SQUARE
  val halfSize = 0.36f
  drawPlotOutline(shape, colors, halfSize)
  val points: List<Pair<Float, Float>> =
    when (mode) {
      SubPlotMode.NONE -> emptyList()
      SubPlotMode.CENTER -> listOf(0f to 0f)
      SubPlotMode.GRID -> {
        // Inset the grid further inside circles so the corner points stay within the plot.
        val step = if (shape == PlotShapeOption.CIRCLE) 0.17f else 0.21f
        listOf(-step, 0f, step).flatMap { dy -> listOf(-step, 0f, step).map { dx -> dx to dy } }
      }
      SubPlotMode.RANDOM -> RANDOM_SAMPLE_OFFSETS
    }
  points.forEach { (dx, dy) -> drawSampleRing(0.5f + dx, 0.5f + dy, colors) }
}

private const val AREA_INSET = 0.08f

/** Random-looking but fixed plot positions, so thumbnails don't change between frames. */
private val RANDOM_PLOTS =
  listOf(
    0.24f to 0.27f,
    0.52f to 0.19f,
    0.78f to 0.30f,
    0.36f to 0.47f,
    0.64f to 0.52f,
    0.82f to 0.70f,
    0.20f to 0.70f,
    0.47f to 0.74f,
    0.68f to 0.84f,
    0.30f to 0.86f,
  )

private val STRATIFIED_PLOTS =
  listOf(
    0.22f to 0.28f,
    0.27f to 0.62f,
    0.19f to 0.82f,
    0.50f to 0.22f,
    0.47f to 0.55f,
    0.54f to 0.80f,
    0.76f to 0.34f,
    0.82f to 0.58f,
    0.73f to 0.84f,
  )

private val CLUSTER_CENTERS = listOf(0.30f to 0.30f, 0.70f to 0.42f, 0.40f to 0.72f)

/** Offsets from the plot center; all well within the inscribed circle of the plot. */
private val RANDOM_SAMPLE_OFFSETS =
  listOf(-0.16f to -0.12f, 0.12f to -0.19f, 0.03f to 0.03f, -0.12f to 0.18f, 0.19f to 0.11f)

/** Side of the square canvas, in pixels. */
private val DrawScope.u: Float
  get() = minOf(size.width, size.height)

/** The point at relative coordinates ([x], [y]), centered in the canvas. */
private fun DrawScope.rel(x: Float, y: Float): Offset =
  Offset((size.width - u) / 2 + x * u, (size.height - u) / 2 + y * u)

private fun DrawScope.drawArea(colors: ThumbnailColors) {
  val topLeft = rel(AREA_INSET, AREA_INSET)
  val areaSize = Size((1f - 2 * AREA_INSET) * u, (1f - 2 * AREA_INSET) * u)
  val corner = CornerRadius(0.1f * u)
  drawRoundRect(color = colors.area, topLeft = topLeft, size = areaSize, cornerRadius = corner)
  drawRoundRect(
    color = colors.areaOutline,
    topLeft = topLeft,
    size = areaSize,
    cornerRadius = corner,
    style = Stroke(width = 1.dp.toPx()),
  )
}

private fun DrawScope.drawPlotDot(
  x: Float,
  y: Float,
  colors: ThumbnailColors,
  radius: Float = 0.045f,
) {
  drawCircle(color = colors.plot, radius = radius * u, center = rel(x, y))
}

private fun DrawScope.drawPlotOutline(
  shape: PlotShapeOption,
  colors: ThumbnailColors,
  halfSize: Float,
) {
  val fill = colors.plot.copy(alpha = 0.22f)
  val stroke = Stroke(width = 2.dp.toPx())
  if (shape == PlotShapeOption.CIRCLE) {
    drawCircle(color = fill, radius = halfSize * u, center = rel(0.5f, 0.5f))
    drawCircle(color = colors.plot, radius = halfSize * u, center = rel(0.5f, 0.5f), style = stroke)
  } else {
    val topLeft = rel(0.5f - halfSize, 0.5f - halfSize)
    val side = Size(2 * halfSize * u, 2 * halfSize * u)
    drawRect(color = fill, topLeft = topLeft, size = side)
    drawRect(color = colors.plot, topLeft = topLeft, size = side, style = stroke)
  }
}

private fun DrawScope.drawSampleRing(x: Float, y: Float, colors: ThumbnailColors) {
  val radius = 0.055f * u
  drawCircle(Color.White, radius, rel(x, y), style = Stroke(width = 3.dp.toPx()))
  drawCircle(colors.sample, radius, rel(x, y), style = Stroke(width = 1.75.dp.toPx()))
}
