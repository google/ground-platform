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
package org.groundplatform.v2.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A dependency-free renderer that draws [MapContent] on a Compose [Canvas] over a plain grid.
 *
 * It gives JVM previews and tests a working, interactive map, and stands in on other platforms
 * until their native renderers land. It doesn't draw basemap imagery or rasterize [MapIcon]s
 * (symbols show as dots), and ignores pitch.
 */
@Composable
internal fun PreviewMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  gestures: MapGestureOptions = MapGestureOptions(),
  modifier: Modifier = Modifier,
) {
  val currentContent by rememberUpdatedState(content)
  val currentOnEvent by rememberUpdatedState(onEvent)
  val textMeasurer = rememberTextMeasurer()

  ReportCameraIdle(cameraState, onEvent)

  Canvas(
    modifier.mapGestures(cameraState, gestures) { tap ->
      val hit = hitTest(currentContent, tap, cameraState::project, cameraState.unproject(tap))
      currentOnEvent(hit ?: MapEvent.BackgroundTapped(cameraState.unproject(tap)))
    }
  ) {
    drawBasemap(content.basemap, cameraState)
    val sources = content.sources.associateBy { it.id }
    for (layer in content.layers) {
      val features = sources[layer.sourceId]?.features ?: continue
      for (feature in features) {
        if (layer.filter?.matches(feature) == false) continue
        drawFeature(layer, feature, cameraState, textMeasurer)
      }
    }
    content.userLocation?.let { loc ->
      val p = cameraState.project(loc).toPx(this)
      drawCircle(Color.White, radius = 9.dp.toPx(), center = p)
      drawCircle(USER_LOCATION_BLUE, radius = 6.dp.toPx(), center = p)
    }
  }
}

private val USER_LOCATION_BLUE = Color(0xFF1A73E8)

private fun DrawScope.drawBasemap(basemap: Basemap, cameraState: MapCameraState) {
  val background = basemap.backgroundColor
  val grid = lerp(background, if (background.luminance() > 0.5f) Color.Black else Color.White, 0.1f)
  drawRect(background)
  // A grid fixed to the world, so panning and zooming are visible without imagery.
  val step = 128.dp.toPx()
  val (cx, cy) = WebMercator.toWorld(cameraState.position.center, cameraState.position.zoom)
  val originX = (size.width / 2 - (cx * density).toFloat()).mod(step)
  val originY = (size.height / 2 - (cy * density).toFloat()).mod(step)
  var x = originX
  while (x < size.width) {
    drawLine(grid, Offset(x, 0f), Offset(x, size.height))
    x += step
  }
  var y = originY
  while (y < size.height) {
    drawLine(grid, Offset(0f, y), Offset(size.width, y))
    y += step
  }
}

private fun DrawScope.drawFeature(
  layer: MapLayer,
  feature: MapFeature,
  cameraState: MapCameraState,
  textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
  val props = feature.properties
  val geometry = feature.geometry
  fun px(p: LatLng) = cameraState.project(p).toPx(this)
  when (layer) {
    is MapLayer.Fill -> {
      if (geometry !is Geometry.Polygon) return
      val path = Path().apply { fillType = PathFillType.EvenOdd }
      geometry.rings.forEach { ring -> path.addRing(ring.map(::px), close = true) }
      drawPath(path, layer.color.evaluate(props).copy(alpha = layer.opacity.evaluate(props)))
    }
    is MapLayer.Line -> {
      val widthPx = layer.width.evaluate(props).toPx()
      val effect =
        layer.dashPattern?.let { dashes ->
          PathEffect.dashPathEffect(dashes.map { it * widthPx }.toFloatArray())
        }
      val color = layer.color.evaluate(props).copy(alpha = layer.opacity.evaluate(props))
      lineRings(geometry).forEach { ring ->
        val path = Path().apply { addRing(ring.map(::px), close = false) }
        drawPath(path, color, style = Stroke(width = widthPx, pathEffect = effect))
      }
    }
    is MapLayer.Circle -> {
      if (geometry !is Geometry.Point) return
      val center = px(geometry.position)
      val radius = layer.radius.evaluate(props).toPx()
      drawCircle(layer.color.evaluate(props), radius, center)
      if (layer.strokeWidth.value > 0) {
        drawCircle(
          layer.strokeColor.evaluate(props),
          radius,
          center,
          style = Stroke(layer.strokeWidth.toPx()),
        )
      }
    }
    is MapLayer.Symbol -> {
      if (geometry !is Geometry.Point) return
      val center = px(geometry.position)
      if (layer.iconId != null) drawCircle(Color.DarkGray, 5.dp.toPx(), center)
      val label = layer.textProperty?.let { props[it] } ?: return
      drawText(
        textMeasurer,
        label,
        topLeft = center + Offset(8.dp.toPx(), -8.dp.toPx()),
        style = TextStyle(color = layer.textColor.evaluate(props), fontSize = 11.sp),
      )
    }
  }
}

private fun Path.addRing(points: List<Offset>, close: Boolean) {
  if (points.isEmpty()) return
  moveTo(points[0].x, points[0].y)
  for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
  if (close) close()
}

private fun DpOffset.toPx(scope: DrawScope) = with(scope) { Offset(x.toPx(), y.toPx()) }
