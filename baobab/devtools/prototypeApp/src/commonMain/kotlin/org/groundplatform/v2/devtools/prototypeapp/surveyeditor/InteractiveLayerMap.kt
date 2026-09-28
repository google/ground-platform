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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

private enum class MapTool {
  SELECT,
  DRAW,
}

/** What an in-progress drag gesture is manipulating. */
private sealed interface MapDrag {
  data object Pan : MapDrag

  data class Vertex(val rowKey: String, val index: Int) : MapDrag

  data class Feature(val rowKey: String, val start: LatLng, val original: List<LatLng>) : MapDrag
}

private const val VERTEX_HIT_PX = 12.0
private const val MIDPOINT_HIT_PX = 10.0
private const val LABEL_MIN_ZOOM = 13.0

/**
 * Interactive map for a Map layer: a live satellite/terrain basemap (web) with the layer's features
 * drawn on top in the layer style.
 *
 * - Drag to pan, scroll or double-click to zoom, or use the zoom/fit buttons.
 * - Click a feature to select it (synced with the feature table).
 * - Drag vertices of the selected feature to reshape it, drag its midpoint handles to insert a
 *   vertex, or drag the feature itself to move it.
 * - "Add feature" draws a new point, line or polygon by clicking on the map.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InteractiveLayerMapCard(
  state: SurveyEditorState,
  dataset: EntityDataset,
  selectedRow: String?,
  onSelectRow: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current.density
  val textMeasurer = rememberTextMeasurer()
  val labelStyle =
    MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)

  var viewW by remember { mutableStateOf(0.0) }
  var viewH by remember { mutableStateOf(0.0) }
  var windowLeft by remember { mutableStateOf(0f) }
  var windowTop by remember { mutableStateOf(0f) }
  var camera by remember(dataset.key) { mutableStateOf<MapCamera?>(null) }
  var basemap by remember {
    mutableStateOf(
      if (isLayerEditorBasemapSupported) EditorBasemap.SATELLITE else EditorBasemap.NONE
    )
  }
  var tool by remember(dataset.key) { mutableStateOf(MapTool.SELECT) }
  var draft by remember(dataset.key) { mutableStateOf(emptyList<LatLng>()) }
  var hover by remember { mutableStateOf<ScreenPoint?>(null) }
  var drag by remember { mutableStateOf<MapDrag?>(null) }

  // Gesture handlers outlive recompositions; always read the latest inputs.
  val currentDataset by rememberUpdatedState(dataset)
  val currentSelected by rememberUpdatedState(selectedRow)
  val currentOnSelect by rememberUpdatedState(onSelectRow)

  fun fitAll() {
    if (viewW > 4) {
      camera =
        MapCamera.fit(currentDataset.rows.flatMap { it.geometry }, viewW, viewH, padding = 64.0)
    }
  }

  fun finishDraft(points: List<LatLng>) {
    val ds = currentDataset
    if (points.size < ds.geometryKind.minVertices) return
    currentOnSelect(state.addRow(ds.key, geometry = points))
    draft = emptyList()
    tool = MapTool.SELECT
  }

  fun toCss(o: Offset) = ScreenPoint(o.x / density.toDouble(), o.y / density.toDouble())

  fun handleTap(p: ScreenPoint) {
    val cam = camera ?: return
    val ds = currentDataset
    when (tool) {
      MapTool.DRAW -> {
        val ll = cam.unproject(p, viewW, viewH)
        when {
          ds.geometryKind == GeometryKind.POINT -> finishDraft(listOf(ll))
          ds.geometryKind == GeometryKind.POLYGON &&
            draft.size >= ds.geometryKind.minVertices &&
            cam.project(draft.first(), viewW, viewH).distanceSquaredTo(p) <=
              VERTEX_HIT_PX * VERTEX_HIT_PX -> finishDraft(draft)
          else -> draft = draft + ll
        }
      }
      MapTool.SELECT -> currentOnSelect(hitTestFeature(ds, cam, viewW, viewH, p))
    }
  }

  fun pickDrag(p: ScreenPoint): MapDrag {
    val cam = camera ?: return MapDrag.Pan
    val ds = currentDataset
    val sel = currentSelected
    if (tool != MapTool.SELECT || sel == null) return MapDrag.Pan
    val row = ds.rows.firstOrNull { it.key == sel } ?: return MapDrag.Pan
    if (row.geometry.isEmpty()) return MapDrag.Pan
    val pts = row.geometry.map { cam.project(it, viewW, viewH) }
    val vertex = pts.indexOfFirst { it.distanceSquaredTo(p) <= VERTEX_HIT_PX * VERTEX_HIT_PX }
    if (vertex >= 0) return MapDrag.Vertex(sel, vertex)
    if (ds.geometryKind != GeometryKind.POINT) {
      val mids = midpoints(pts, closed = ds.geometryKind == GeometryKind.POLYGON)
      val mid = mids.indexOfFirst { it.distanceSquaredTo(p) <= MIDPOINT_HIT_PX * MIDPOINT_HIT_PX }
      if (mid >= 0) {
        val inserted =
          row.geometry.toMutableList().apply {
            add(mid + 1, cam.unproject(mids[mid], viewW, viewH))
          }
        state.updateGeometry(ds.key, sel, inserted)
        return MapDrag.Vertex(sel, mid + 1)
      }
    }
    if (hitTestFeature(ds, cam, viewW, viewH, p) == sel) {
      return MapDrag.Feature(sel, cam.unproject(p, viewW, viewH), row.geometry)
    }
    return MapDrag.Pan
  }

  fun handleDrag(position: Offset, amount: Offset) {
    val cam = camera ?: return
    val ds = currentDataset
    when (val d = drag) {
      is MapDrag.Vertex -> {
        val row = ds.rows.firstOrNull { it.key == d.rowKey } ?: return
        if (d.index !in row.geometry.indices) return
        val moved = row.geometry.toMutableList()
        moved[d.index] = cam.unproject(toCss(position), viewW, viewH)
        state.updateGeometry(ds.key, d.rowKey, moved)
      }
      is MapDrag.Feature -> {
        val now = cam.unproject(toCss(position), viewW, viewH)
        val dLat = now.lat - d.start.lat
        val dLng = now.lng - d.start.lng
        state.updateGeometry(
          ds.key,
          d.rowKey,
          d.original.map { LatLng(it.lat + dLat, it.lng + dLng) },
        )
      }
      else ->
        camera =
          cam.panBy(amount.x / density.toDouble(), amount.y / density.toDouble(), viewW, viewH)
    }
  }

  // Fit to the layer's features once the viewport size is known (and when switching layers).
  LaunchedEffect(dataset.key, viewW > 4) { if (camera == null) fitAll() }

  // Bring a feature selected from the table into view.
  LaunchedEffect(selectedRow) {
    val cam = camera ?: return@LaunchedEffect
    val row = dataset.rows.firstOrNull { it.key == selectedRow } ?: return@LaunchedEffect
    if (row.geometry.isEmpty() || viewW <= 4) return@LaunchedEffect
    val onScreen =
      row.geometry.all {
        val s = cam.project(it, viewW, viewH)
        s.x in 0.0..viewW && s.y in 0.0..viewH
      }
    if (!onScreen) {
      val fitted =
        MapCamera.fit(row.geometry, viewW, viewH, padding = 80.0, maxZoom = max(cam.zoom, 12.0))
      camera = if (fitted.zoom < cam.zoom) fitted else cam.copy(center = fitted.center)
    }
  }

  val cam = camera
  val showBasemap = isLayerEditorBasemapSupported && basemap != EditorBasemap.NONE
  SideEffect {
    if (cam != null && viewW > 4 && showBasemap) {
      syncLayerEditorBasemap(
        leftPx = windowLeft,
        topPx = windowTop,
        widthPx = viewW.toFloat(),
        heightPx = viewH.toFloat(),
        borderRadiusPx = 12f,
        centerLat = cam.center.lat,
        centerLng = cam.center.lng,
        zoom = cam.zoom,
        basemap = basemap.name,
      )
    } else {
      hideLayerEditorBasemap()
    }
  }
  DisposableEffect(Unit) { onDispose { hideLayerEditorBasemap() } }

  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      MapToolbar(
        dataset = dataset,
        tool = tool,
        draftSize = draft.size,
        onStartDrawing = {
          tool = MapTool.DRAW
          draft = emptyList()
          currentOnSelect(null)
        },
        onUndo = { draft = draft.dropLast(1) },
        onFinish = { finishDraft(draft) },
        onCancel = {
          tool = MapTool.SELECT
          draft = emptyList()
        },
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .clip(MaterialTheme.shapes.medium)
      ) {
        Canvas(
          modifier =
            Modifier.fillMaxSize()
              .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInWindow()
                windowLeft = pos.x / density
                windowTop = pos.y / density
                viewW = coordinates.size.width / density.toDouble()
                viewH = coordinates.size.height / density.toDouble()
              }
              .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val dy = change.scrollDelta.y
                val c = camera ?: return@onPointerEvent
                if (dy != 0f) {
                  val step = (-dy * 0.5).coerceIn(-1.0, 1.0)
                  camera = c.zoomAround(step, toCss(change.position), viewW, viewH)
                  change.consume()
                }
              }
              .onPointerEvent(PointerEventType.Move) { event ->
                hover = event.changes.firstOrNull()?.position?.let(::toCss)
              }
              .onPointerEvent(PointerEventType.Exit) { hover = null }
              .pointerInput(dataset.key, density) {
                detectTapGestures(
                  onDoubleTap = { pos ->
                    val p = toCss(pos)
                    if (tool == MapTool.DRAW && currentDataset.geometryKind != GeometryKind.POINT) {
                      val ll = camera?.unproject(p, viewW, viewH)
                      finishDraft(if (ll != null) draft + ll else draft)
                    } else {
                      camera = camera?.zoomAround(1.0, p, viewW, viewH)
                    }
                  },
                  onTap = { pos -> handleTap(toCss(pos)) },
                )
              }
              .pointerInput(dataset.key, density) {
                detectDragGestures(
                  onDragStart = { pos -> drag = pickDrag(toCss(pos)) },
                  onDragEnd = { drag = null },
                  onDragCancel = { drag = null },
                  onDrag = { change, amount ->
                    change.consume()
                    handleDrag(change.position, amount)
                  },
                )
              }
        ) {
          val c = cam ?: return@Canvas
          val w = size.width / density.toDouble()
          val h = size.height / density.toDouble()
          fun proj(p: LatLng): Offset =
            c.project(p, w, h).let {
              Offset((it.x * density).toFloat(), (it.y * density).toFloat())
            }

          if (showBasemap) {
            // Punch a transparent hole so the Mapbox basemap behind the Compose canvas shows.
            drawRect(color = Color.Transparent, blendMode = BlendMode.Clear)
          } else {
            drawRect(colors.surfaceContainerHigh)
            drawGrid(colors.outlineVariant.copy(alpha = 0.5f), 48.dp.toPx())
          }

          val layerColor =
            parseHexColor(dataset.style.colorHex)?.let { Color(it) } ?: colors.primary
          val strokeWidth = dataset.style.strokeWidth.toFloat().dp.toPx()
          val halo = Color.White.copy(alpha = 0.9f)
          val casing = Color.Black.copy(alpha = 0.35f)

          // Unselected features first, selected on top.
          val ordered = dataset.rows.sortedBy { it.key == selectedRow }
          ordered.forEach { row ->
            val pts = row.geometry.map(::proj)
            if (pts.isEmpty()) return@forEach
            val selected = row.key == selectedRow
            when (dataset.geometryKind) {
              GeometryKind.POINT -> {
                val center = pts.first()
                if (selected) drawCircle(halo, radius = 14.dp.toPx(), center = center)
                drawCircle(casing, radius = 10.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 9.dp.toPx(), center = center)
                drawCircle(layerColor, radius = 7.dp.toPx(), center = center)
              }
              GeometryKind.LINE -> {
                val path = polyline(pts, closed = false)
                if (selected) drawPath(path, halo, style = Stroke(strokeWidth + 6.dp.toPx()))
                drawPath(path, casing, style = Stroke(strokeWidth + 2.dp.toPx()))
                drawPath(path, layerColor, style = Stroke(strokeWidth))
              }
              GeometryKind.POLYGON -> {
                val path = polyline(pts, closed = true)
                drawPath(path, layerColor.copy(alpha = dataset.style.fillOpacity.toFloat()))
                if (selected) drawPath(path, halo, style = Stroke(strokeWidth + 6.dp.toPx()))
                drawPath(path, casing, style = Stroke(strokeWidth + 2.dp.toPx()))
                drawPath(path, layerColor, style = Stroke(strokeWidth))
              }
            }
            if (selected && dataset.geometryKind != GeometryKind.POINT) {
              val screen = row.geometry.map { c.project(it, w, h) }
              midpoints(screen, closed = dataset.geometryKind == GeometryKind.POLYGON).forEach {
                val m = Offset((it.x * density).toFloat(), (it.y * density).toFloat())
                drawCircle(Color.White.copy(alpha = 0.75f), radius = 4.5.dp.toPx(), center = m)
                drawCircle(layerColor, radius = 4.5.dp.toPx(), center = m, style = Stroke(1.5f))
              }
              pts.forEach {
                drawCircle(Color.White, radius = 6.dp.toPx(), center = it)
                drawCircle(colors.primary, radius = 6.dp.toPx(), center = it, style = Stroke(2.5f))
              }
            }
          }

          // Feature labels (pill backgrounds keep them legible over imagery).
          if (c.zoom >= LABEL_MIN_ZOOM) {
            dataset.rows.forEach { row ->
              val pts = row.geometry.map(::proj)
              if (pts.isEmpty()) return@forEach
              val label = dataset.labelOf(row)
              if (label.isBlank()) return@forEach
              val measured = textMeasurer.measure(label, labelStyle)
              val anchorX = pts.map { it.x }.average().toFloat()
              val anchorY = pts.maxOf { it.y } + 10.dp.toPx()
              val padX = 6.dp.toPx()
              val padY = 2.dp.toPx()
              val topLeft = Offset(anchorX - measured.size.width / 2f, anchorY)
              drawRoundRect(
                color = colors.surface.copy(alpha = 0.88f),
                topLeft = Offset(topLeft.x - padX, topLeft.y - padY),
                size = Size(measured.size.width + 2 * padX, measured.size.height + 2 * padY),
                cornerRadius = CornerRadius(8.dp.toPx()),
              )
              drawText(measured, topLeft = topLeft)
            }
          }

          // In-progress drawing.
          if (tool == MapTool.DRAW) {
            val hoverOffset = hover?.let {
              Offset((it.x * density).toFloat(), (it.y * density).toFloat())
            }
            val pts = draft.map(::proj)
            if (pts.isNotEmpty()) {
              val preview = if (hoverOffset != null) pts + hoverOffset else pts
              val closed = dataset.geometryKind == GeometryKind.POLYGON && preview.size >= 3
              val path = polyline(preview, closed)
              if (closed) drawPath(path, layerColor.copy(alpha = 0.2f))
              drawPath(
                path,
                Color.White,
                style =
                  Stroke(
                    2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                  ),
              )
              pts.forEachIndexed { i, p ->
                val r = if (i == 0) 7.dp.toPx() else 5.dp.toPx()
                drawCircle(Color.White, radius = r, center = p)
                drawCircle(layerColor, radius = r, center = p, style = Stroke(2.5f))
              }
            }
            if (hoverOffset != null) {
              drawCircle(
                Color.White,
                radius = 5.dp.toPx(),
                center = hoverOffset,
                style = Stroke(2f),
              )
              drawCircle(casing, radius = 7.dp.toPx(), center = hoverOffset, style = Stroke(1f))
            }
          }
        }

        if (cam != null) {
          MapOverlays(
            camera = cam,
            basemap = basemap,
            onBasemap = { basemap = it },
            hoverLatLng = hover?.let { cam.unproject(it, viewW, viewH) },
            hint = hintFor(tool, dataset.geometryKind, selectedRow != null, draft.size),
            onZoomIn = {
              camera = cam.zoomAround(1.0, ScreenPoint(viewW / 2, viewH / 2), viewW, viewH)
            },
            onZoomOut = {
              camera = cam.zoomAround(-1.0, ScreenPoint(viewW / 2, viewH / 2), viewW, viewH)
            },
            onFit = ::fitAll,
          )
        }
      }
    }
  }
}

@Composable
private fun MapToolbar(
  dataset: EntityDataset,
  tool: MapTool,
  draftSize: Int,
  onStartDrawing: () -> Unit,
  onUndo: () -> Unit,
  onFinish: () -> Unit,
  onCancel: () -> Unit,
) {
  val noun =
    when (dataset.geometryKind) {
      GeometryKind.POINT -> "point"
      GeometryKind.LINE -> "line"
      GeometryKind.POLYGON -> "polygon"
    }
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      "Map",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
    )
    if (tool == MapTool.DRAW) {
      val min = dataset.geometryKind.minVertices
      if (dataset.geometryKind != GeometryKind.POINT) {
        Text(
          "$draftSize / $min+ vertices",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo, enabled = draftSize > 0) { Text("Undo") }
        Button(onClick = onFinish, enabled = draftSize >= min) {
          Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Finish $noun")
        }
      }
      TextButton(onClick = onCancel) {
        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Cancel")
      }
    } else {
      FilterChip(
        selected = false,
        onClick = onStartDrawing,
        label = { Text("Add $noun") },
        leadingIcon = {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        },
      )
    }
  }
}

@Composable
private fun MapOverlays(
  camera: MapCamera,
  basemap: EditorBasemap,
  onBasemap: (EditorBasemap) -> Unit,
  hoverLatLng: LatLng?,
  hint: String,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onFit: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val pill = RoundedCornerShape(16.dp)
  val overlayColor = colors.surface.copy(alpha = 0.92f)
  Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
    // Basemap picker.
    Surface(
      modifier = Modifier.align(Alignment.TopStart),
      shape = pill,
      color = overlayColor,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          Icons.Default.Layers,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = colors.onSurfaceVariant,
        )
        if (isLayerEditorBasemapSupported) {
          EditorBasemap.entries.forEach { option ->
            FilterChip(
              selected = basemap == option,
              onClick = { onBasemap(option) },
              label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier.height(28.dp),
            )
          }
        } else {
          Text(
            "Basemap available in the web build",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
          )
        }
      }
    }

    // Zoom / fit controls.
    Surface(
      modifier = Modifier.align(Alignment.TopEnd),
      shape = RoundedCornerShape(12.dp),
      color = overlayColor,
      shadowElevation = 2.dp,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onZoomIn, modifier = Modifier.size(36.dp)) {
          Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
        }
        HorizontalDivider(modifier = Modifier.width(24.dp))
        IconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Default.Remove,
            contentDescription = "Zoom out",
            modifier = Modifier.size(18.dp),
          )
        }
        HorizontalDivider(modifier = Modifier.width(24.dp))
        IconButton(onClick = onFit, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Default.ZoomOutMap,
            contentDescription = "Fit to features",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    // Scale bar, zoom level and cursor coordinates.
    val scale = ScaleBar.forCamera(camera)
    Surface(
      modifier = Modifier.align(Alignment.BottomStart),
      shape = pill,
      color = overlayColor,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column {
          Text(scale.label, style = MaterialTheme.typography.labelSmall)
          Box(
            modifier =
              Modifier.width(scale.widthPx.toFloat().dp)
                .height(5.dp)
                .border(1.5.dp, colors.onSurface)
                .background(colors.onSurface.copy(alpha = 0.15f))
          )
        }
        Text(
          "z ${formatFixed(camera.zoom, 1)}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
        if (hoverLatLng != null) {
          Text(
            "${formatFixed(hoverLatLng.lat, 5)}, ${formatFixed(hoverLatLng.lng, 5)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant,
          )
        }
      }
    }

    // Contextual hint.
    Surface(
      modifier = Modifier.align(Alignment.BottomEnd).padding(start = 280.dp),
      shape = pill,
      color = overlayColor,
      shadowElevation = 2.dp,
    ) {
      Text(
        hint,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }
  }
}

private fun hintFor(tool: MapTool, kind: GeometryKind, hasSelection: Boolean, draftSize: Int) =
  when {
    tool == MapTool.DRAW && kind == GeometryKind.POINT -> "Click the map to place the point"
    tool == MapTool.DRAW && kind == GeometryKind.LINE ->
      if (draftSize == 0) "Click to start the line"
      else "Click to add vertices • Double-click to finish"
    tool == MapTool.DRAW ->
      if (draftSize == 0) "Click to start the polygon"
      else "Click to add vertices • Click the first vertex or double-click to finish"
    hasSelection && kind == GeometryKind.POINT -> "Drag the point to move it"
    hasSelection ->
      "Drag vertices to reshape • Drag ○ handles to add a vertex • Drag inside to move"
    else -> "Drag to pan • Scroll or double-click to zoom • Click a feature to select it"
  }

/** Midpoints of each segment, used as "insert vertex" handles. */
internal fun midpoints(pts: List<ScreenPoint>, closed: Boolean): List<ScreenPoint> {
  if (pts.size < 2) return emptyList()
  val segments =
    pts.zipWithNext() +
      if (closed && pts.size >= 3) listOf(pts.last() to pts.first()) else emptyList()
  return segments.map { (a, b) -> ScreenPoint((a.x + b.x) / 2, (a.y + b.y) / 2) }
}

private fun ScreenPoint.distanceSquaredTo(o: ScreenPoint): Double {
  val dx = x - o.x
  val dy = y - o.y
  return dx * dx + dy * dy
}

private fun polyline(pts: List<Offset>, closed: Boolean) =
  Path().apply {
    moveTo(pts.first().x, pts.first().y)
    pts.drop(1).forEach { lineTo(it.x, it.y) }
    if (closed) close()
  }

private fun DrawScope.drawGrid(color: Color, step: Float) {
  var gx = 0f
  while (gx < size.width) {
    drawLine(color, Offset(gx, 0f), Offset(gx, size.height), 1f)
    gx += step
  }
  var gy = 0f
  while (gy < size.height) {
    drawLine(color, Offset(0f, gy), Offset(size.width, gy), 1f)
    gy += step
  }
}

/** Formats [v] with exactly [decimals] fraction digits (no platform `String.format` in common). */
internal fun formatFixed(v: Double, decimals: Int): String {
  var factor = 1L
  repeat(decimals) { factor *= 10 }
  val scaled = kotlin.math.round(kotlin.math.abs(v) * factor).toLong()
  val whole = scaled / factor
  val frac = (scaled % factor).toString().padStart(decimals, '0')
  val sign = if (v < 0 && scaled != 0L) "-" else ""
  return if (decimals == 0) "$sign$whole" else "$sign$whole.$frac"
}
