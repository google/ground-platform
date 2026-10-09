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
package org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.formatFixed
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.parseHexColor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatHexColorCss
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.ui.common.GroundFilterChip
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.moved
import org.groundplatform.v2.devtools.prototypeapp.ui.map.GroundPin
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyBasemaps
import org.groundplatform.v2.devtools.prototypeapp.ui.map.contentColorOnArgb
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.MapFeatureEditor
import org.groundplatform.v2.map.Basemap
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.FeatureFilter
import org.groundplatform.v2.map.GeoJsonSource
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.GeometryType
import org.groundplatform.v2.map.GroundMap
import org.groundplatform.v2.map.LatLng as MapLatLng
import org.groundplatform.v2.map.LngLatBounds
import org.groundplatform.v2.map.MapCameraState
import org.groundplatform.v2.map.MapContent
import org.groundplatform.v2.map.MapDrag
import org.groundplatform.v2.map.MapDragHandler
import org.groundplatform.v2.map.MapEvent
import org.groundplatform.v2.map.MapFeature
import org.groundplatform.v2.map.MapIcon
import org.groundplatform.v2.map.MapInsets
import org.groundplatform.v2.map.MapLayer
import org.groundplatform.v2.map.MarkerAnchor
import org.groundplatform.v2.map.StyleValue

private enum class MapTool {
  SELECT,
  DRAW,
}

private const val VERTEX_HIT_PX = 12.0
private const val MIDPOINT_HIT_PX = 10.0
private const val LABEL_MIN_ZOOM = 13.0
private const val MIN_ZOOM = 1.0
private const val MAX_ZOOM = 20.0
private const val ZOOM_BUTTON_MS = 250

internal const val LAYER_EDITOR_SOURCE = "layer-editor-features"
/** Feature property marking the selected row (`"true"` or `"false"`). */
internal const val LAYER_EDITOR_SELECTED = "selected"

/**
 * Interactive map for a Map layer: a [GroundMap] with a satellite/terrain basemap and the layer's
 * features drawn in the layer style, plus Compose-drawn editing handles on top.
 * - Drag to pan, scroll or pinch to zoom, or use the zoom/fit buttons.
 * - Click a feature to select it (synced with the feature table).
 * - Drag vertices of the selected feature to reshape it, drag its midpoint handles to insert a
 *   vertex, or drag the feature itself to move it.
 * - "Add feature" draws a new point, line or polygon by clicking on the map; clicking the last (or,
 *   for polygons, the first) vertex again finishes it.
 *
 * When [geometryEditable] is false (generated sample plots), features can only be selected: the
 * drawing tool and vertex handles are hidden. The selected feature's sample points (its `samples`
 * property) are drawn on top.
 *
 * @param featureNoun what the toolbar calls a new feature (e.g. "part"); defaults to the geometry
 *   type.
 * @param showLabels whether to draw feature labels when zoomed in.
 * @param emptyCamera where the map starts when the layer has no features yet.
 */
@Composable
internal fun InteractiveLayerMapCard(
  editor: MapFeatureEditor,
  dataset: EntityDataset,
  selectedRow: String?,
  onSelectRow: (String?) -> Unit,
  modifier: Modifier = Modifier,
  geometryEditable: Boolean = !dataset.isGenerated,
  featureNoun: String? = null,
  showLabels: Boolean = true,
  emptyCamera: CameraPosition = DEFAULT_EDITOR_CAMERA,
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current.density
  val textMeasurer = rememberTextMeasurer()
  val labelStyle =
    MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
  val scope = rememberCoroutineScope()

  val cameraState = remember(dataset.key) { MapCameraState(emptyCamera) }
  var fitted by remember(dataset.key) { mutableStateOf(false) }
  var basemap by remember { mutableStateOf(EditorBasemap.SATELLITE) }
  var tool by remember(dataset.key) { mutableStateOf(MapTool.SELECT) }
  var draft by remember(dataset.key) { mutableStateOf(emptyList<LatLng>()) }
  var hover by remember { mutableStateOf<ScreenPoint?>(null) }

  // Map callbacks outlive recompositions; always read the latest inputs.
  val currentDataset by rememberUpdatedState(dataset)
  val currentSelected by rememberUpdatedState(selectedRow)
  val currentOnSelect by rememberUpdatedState(onSelectRow)
  val currentEditable by rememberUpdatedState(geometryEditable)

  fun screenOf(p: MapLatLng): ScreenPoint = cameraState.project(p).toScreenPoint()

  fun project(p: LatLng): ScreenPoint = screenOf(p.toMapLatLng())

  fun unproject(p: ScreenPoint): LatLng =
    cameraState.unproject(DpOffset(p.x.toFloat().dp, p.y.toFloat().dp)).toEditorLatLng()

  suspend fun fitAll(durationMs: Int = MapCameraState.DEFAULT_ANIMATION_MS) {
    val points = currentDataset.rows.flatMap { it.geometry }.map { it.toMapLatLng() }
    when {
      points.isEmpty() -> cameraState.animateTo(emptyCamera, durationMs)
      points.distinct().size == 1 ->
        cameraState.animateTo(CameraPosition(points.first(), 16.0), durationMs)
      else ->
        cameraState.fitBounds(
          LngLatBounds.of(points),
          padding = MapInsets(64.dp, 64.dp, 64.dp, 64.dp),
          maxZoom = 18.0,
          durationMs = durationMs,
        )
    }
  }

  fun finishDraft(points: List<LatLng>) {
    val ds = currentDataset
    if (points.size < ds.geometryKind.minVertices) return
    currentOnSelect(editor.addRow(ds.key, geometry = points))
    draft = emptyList()
    tool = MapTool.SELECT
  }

  fun handleTap(p: ScreenPoint) {
    val ds = currentDataset
    val kind = ds.geometryKind
    when (tool) {
      MapTool.DRAW -> {
        val canFinish = draft.size >= kind.minVertices
        when {
          kind == GeometryKind.POINT -> finishDraft(listOf(unproject(p)))
          canFinish && project(draft.last()).isNear(p, VERTEX_HIT_PX) -> finishDraft(draft)
          canFinish &&
            kind == GeometryKind.POLYGON &&
            project(draft.first()).isNear(p, VERTEX_HIT_PX) -> finishDraft(draft)
          else -> draft = draft + unproject(p)
        }
      }
      MapTool.SELECT -> currentOnSelect(hitTestFeature(ds, ::project, p))
    }
  }

  fun vertexDrag(rowKey: String, index: Int): MapDrag =
    object : MapDrag {
      override fun onDrag(position: DpOffset) {
        val ds = currentDataset
        val row = ds.rows.firstOrNull { it.key == rowKey } ?: return
        if (index !in row.geometry.indices) return
        val moved = row.geometry.toMutableList()
        moved[index] = unproject(position.toScreenPoint())
        editor.updateGeometry(ds.key, rowKey, moved)
      }
    }

  fun featureDrag(rowKey: String, start: LatLng, original: List<LatLng>): MapDrag =
    object : MapDrag {
      override fun onDrag(position: DpOffset) {
        val now = unproject(position.toScreenPoint())
        val dLat = now.lat - start.lat
        val dLng = now.lng - start.lng
        editor.updateGeometry(
          currentDataset.key,
          rowKey,
          original.map { LatLng(it.lat + dLat, it.lng + dLng) },
        )
      }
    }

  /** Claims drags on the selected feature's handles or body; anything else pans the map. */
  fun pickDrag(p: ScreenPoint): MapDrag? {
    val ds = currentDataset
    val sel = currentSelected
    if (!currentEditable || tool != MapTool.SELECT || sel == null) return null
    val row = ds.rows.firstOrNull { it.key == sel } ?: return null
    if (row.geometry.isEmpty()) return null
    val pts = row.geometry.map(::project)
    val vertex = pts.indexOfFirst { it.isNear(p, VERTEX_HIT_PX) }
    if (vertex >= 0) return vertexDrag(sel, vertex)
    if (ds.geometryKind != GeometryKind.POINT) {
      val mids = midpoints(pts, closed = ds.geometryKind == GeometryKind.POLYGON)
      val mid = mids.indexOfFirst { it.isNear(p, MIDPOINT_HIT_PX) }
      if (mid >= 0) {
        val inserted = row.geometry.toMutableList().apply { add(mid + 1, unproject(mids[mid])) }
        editor.updateGeometry(ds.key, sel, inserted)
        return vertexDrag(sel, mid + 1)
      }
    }
    if (hitTestFeature(ds, ::project, p) == sel) {
      return featureDrag(sel, unproject(p), row.geometry)
    }
    return null
  }

  // Fit to the layer's features once the viewport size is known (and when switching layers).
  val viewport = cameraState.viewportSize
  val viewportReady = viewport.width > 4.dp && viewport.height > 4.dp
  LaunchedEffect(dataset.key, viewportReady) {
    if (viewportReady && !fitted) {
      fitAll(durationMs = 0)
      fitted = true
    }
  }

  // Bring a feature selected from the table into view, zooming out only if it doesn't fit.
  LaunchedEffect(selectedRow) {
    if (!fitted) return@LaunchedEffect
    val row = dataset.rows.firstOrNull { it.key == selectedRow } ?: return@LaunchedEffect
    if (row.geometry.isEmpty()) return@LaunchedEffect
    val w = cameraState.viewportSize.width.value.toDouble()
    val h = cameraState.viewportSize.height.value.toDouble()
    val onScreen = row.geometry.all { project(it).let { s -> s.x in 0.0..w && s.y in 0.0..h } }
    if (!onScreen) {
      cameraState.fitBounds(
        LngLatBounds.of(row.geometry.map { it.toMapLatLng() }),
        padding = MapInsets(80.dp, 80.dp, 80.dp, 80.dp),
        maxZoom = cameraState.position.zoom,
      )
    }
  }

  val layerColor = parseHexColor(dataset.style.colorHex)?.let { Color(it) } ?: colors.primary
  val mapBasemap =
    when (basemap) {
      EditorBasemap.SATELLITE -> SurveyBasemaps.Satellite
      EditorBasemap.TERRAIN -> SurveyBasemaps.Streets
      EditorBasemap.NONE -> Basemap.None(colors.surfaceContainerHigh)
    }
  val baseContent =
    remember(dataset, selectedRow, layerColor, mapBasemap) {
      layerEditorContent(dataset, selectedRow, layerColor, mapBasemap)
    }
  // Sample points of the selected plot go in a small overlay source, so they cost O(samples).
  val selectedSamples =
    remember(dataset.rows, selectedRow) {
      dataset.rows
        .firstOrNull { it.key == selectedRow }
        ?.values
        ?.get(SamplePlotProperties.SAMPLES)
        .orEmpty()
    }
  val content =
    remember(baseContent, selectedSamples, layerColor) {
      withSamplePointsOverlay(
        baseContent,
        SamplePlotProperties.parseGeotrace(selectedSamples),
        layerColor,
      )
    }

  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      MapToolbar(
        dataset = dataset,
        editable = geometryEditable,
        featureNoun = featureNoun,
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
            // Observed here rather than on the map so hover never interferes with map gestures.
            .pointerInput(density) {
              awaitPointerEventScope {
                while (true) {
                  val event = awaitPointerEvent()
                  when (event.type) {
                    PointerEventType.Move ->
                      hover =
                        event.changes.firstOrNull()?.position?.let {
                          ScreenPoint(it.x / density.toDouble(), it.y / density.toDouble())
                        }
                    PointerEventType.Exit -> hover = null
                    else -> Unit
                  }
                }
              }
            }
      ) {
        GroundMap(
          content = content,
          cameraState = cameraState,
          onEvent = { event ->
            when (event) {
              is MapEvent.FeatureTapped -> handleTap(screenOf(event.at))
              is MapEvent.BackgroundTapped -> handleTap(screenOf(event.at))
              else -> Unit
            }
          },
          modifier = Modifier.fillMaxSize(),
          dragHandler = MapDragHandler { pickDrag(it.toScreenPoint()) },
        )

        // Editing chrome. Draw-only (no pointer input), so gestures reach the map below.
        Canvas(modifier = Modifier.fillMaxSize()) {
          val zoom = cameraState.position.zoom // Redraw on every camera change.
          fun proj(p: LatLng): Offset =
            project(p).let { Offset((it.x * density).toFloat(), (it.y * density).toFloat()) }
          fun ScreenPoint.toOffset() = Offset((x * density).toFloat(), (y * density).toFloat())

          val casing = Color.Black.copy(alpha = 0.35f)
          val selected = dataset.rows.firstOrNull { it.key == selectedRow }
          if (geometryEditable && selected != null && dataset.geometryKind != GeometryKind.POINT) {
            val screen = selected.geometry.map(::project)
            midpoints(screen, closed = dataset.geometryKind == GeometryKind.POLYGON).forEach {
              val m = it.toOffset()
              drawCircle(Color.White.copy(alpha = 0.75f), radius = 4.5.dp.toPx(), center = m)
              drawCircle(layerColor, radius = 4.5.dp.toPx(), center = m, style = Stroke(1.5f))
            }
            screen.forEach {
              val v = it.toOffset()
              drawCircle(Color.White, radius = 6.dp.toPx(), center = v)
              drawCircle(colors.primary, radius = 6.dp.toPx(), center = v, style = Stroke(2.5f))
            }
          }

          // Feature labels on pills of the layer color, like the survey map's feature chips.
          if (showLabels && zoom >= LABEL_MIN_ZOOM) {
            val onLayerColor =
              Color(contentColorOnArgb(layerColor.toArgb().toLong() and 0xFFFFFFFFL))
            dataset.rows.forEach { row ->
              val pts = row.geometry.map(::proj)
              if (pts.isEmpty()) return@forEach
              val anchorX = pts.map { it.x }.average().toFloat()
              val anchorY = pts.maxOf { it.y } + 10.dp.toPx()
              // Skip off-screen features before the (comparatively costly) text measurement.
              if (anchorX !in -200f..size.width + 200f || anchorY !in -50f..size.height + 50f) {
                return@forEach
              }
              val label = dataset.labelOf(row)
              if (label.isBlank()) return@forEach
              val measured = textMeasurer.measure(label, labelStyle)
              val padX = 6.dp.toPx()
              val padY = 2.dp.toPx()
              val topLeft = Offset(anchorX - measured.size.width / 2f, anchorY)
              drawRoundRect(
                color = layerColor,
                topLeft = Offset(topLeft.x - padX, topLeft.y - padY),
                size = Size(measured.size.width + 2 * padX, measured.size.height + 2 * padY),
                cornerRadius = CornerRadius(8.dp.toPx()),
              )
              drawText(measured, color = onLayerColor, topLeft = topLeft)
            }
          }

          // In-progress drawing.
          if (tool == MapTool.DRAW) {
            val hoverOffset = hover?.toOffset()
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
                val r = if (i == 0 || i == pts.lastIndex) 7.dp.toPx() else 5.dp.toPx()
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

        MapOverlays(
          camera = cameraState.position,
          basemap = basemap,
          onBasemap = { basemap = it },
          hoverLatLng = hover?.let(::unproject),
          hint =
            if (geometryEditable)
              hintFor(tool, dataset.geometryKind, selectedRow != null, draft.size)
            else LOCKED_HINT,
          onZoomIn = { scope.launch { cameraState.zoomBy(1.0) } },
          onZoomOut = { scope.launch { cameraState.zoomBy(-1.0) } },
          onFit = { scope.launch { fitAll() } },
        )
      }
    }
  }
}

/**
 * What [InteractiveLayerMapCard] draws with the map's style layers: every row of [dataset] as a
 * feature styled like the layer, with the [selectedRow] haloed and on top.
 */
internal fun layerEditorContent(
  dataset: EntityDataset,
  selectedRow: String?,
  layerColor: Color,
  basemap: Basemap,
): MapContent {
  val features =
    dataset.rows
      .sortedBy { it.key == selectedRow }
      .mapNotNull { row ->
        val geometry = row.geometry.toMapGeometry(dataset.geometryKind) ?: return@mapNotNull null
        MapFeature(
          id = row.key,
          geometry = geometry,
          properties = mapOf(LAYER_EDITOR_SELECTED to (row.key == selectedRow).toString()),
        )
      }
  val stroke = dataset.style.strokeWidth.toFloat().dp
  val src = LAYER_EDITOR_SOURCE
  val points = FeatureFilter.GeometryTypeIs(GeometryType.POINT)
  val notPoints = FeatureFilter.Not(points)
  val isSelected = FeatureFilter.Equals(LAYER_EDITOR_SELECTED, "true")
  val casing = Color.Black.copy(alpha = 0.35f)
  val halo = Color.White.copy(alpha = 0.9f)
  val pinColor = formatHexColorCss(layerColor.toArgb().toLong())
  val iconName = dataset.style.iconName
  val pin =
    MapIcon(
      GroundPin.iconId(pinColor, iconName, isPending = false),
      GroundPin.svg(pinColor, iconName, isPending = false),
    )
  return MapContent(
    basemap = basemap,
    sources = listOf(GeoJsonSource(src, features)),
    layers =
      listOf(
        MapLayer.Fill(
          id = "layer-editor-fill",
          sourceId = src,
          color = StyleValue.Constant(layerColor),
          opacity = StyleValue.Constant(dataset.style.fillOpacity.toFloat()),
        ),
        MapLayer.Line(
          id = "layer-editor-halo",
          sourceId = src,
          filter = FeatureFilter.All(listOf(notPoints, isSelected)),
          color = StyleValue.Constant(halo),
          width = StyleValue.Constant(stroke + 6.dp),
        ),
        MapLayer.Line(
          id = "layer-editor-casing",
          sourceId = src,
          filter = notPoints,
          color = StyleValue.Constant(casing),
          width = StyleValue.Constant(stroke + 2.dp),
        ),
        MapLayer.Line(
          id = "layer-editor-line",
          sourceId = src,
          filter = notPoints,
          color = StyleValue.Constant(layerColor),
          width = StyleValue.Constant(stroke),
        ),
        // Points are drawn as the same Ground pin the survey map uses: layer color and icon.
        MapLayer.Symbol(
          id = "layer-editor-point",
          sourceId = src,
          filter = points,
          iconId = StyleValue.Constant(pin.id),
          iconSize = StyleValue.Match(LAYER_EDITOR_SELECTED, mapOf("true" to 1.85f), 1.5f),
          iconAnchor = MarkerAnchor.BOTTOM,
          // Moves the pin down so its tip, not the bottom of its shadow, sits on the location.
          iconOffset = DpOffset(0.dp, (GroundPin.HEIGHT - GroundPin.TIP_Y).toFloat().dp),
        ),
      ),
    icons = listOf(pin),
  )
}

internal const val LAYER_EDITOR_SAMPLES_SOURCE = "layer-editor-samples"

/**
 * [content] plus [samples] (the selected plot's sample points) drawn above everything else. Returns
 * [content] unchanged when there are none.
 *
 * Sample points are hollow rings, so interpreters can see the imagery inside each one: a ring in
 * [layerColor] over a slightly wider white halo, which keeps it legible on both dark and bright
 * imagery.
 */
internal fun withSamplePointsOverlay(
  content: MapContent,
  samples: List<LatLng>,
  layerColor: Color,
): MapContent {
  if (samples.isEmpty()) return content
  val features = samples.mapIndexed { i, p ->
    MapFeature(id = "sample-${i + 1}", geometry = Geometry.Point(p.toMapLatLng()))
  }
  return content.copy(
    sources = content.sources + GeoJsonSource(LAYER_EDITOR_SAMPLES_SOURCE, features),
    layers =
      content.layers +
        listOf(
          MapLayer.Circle(
            id = "layer-editor-samples-halo",
            sourceId = LAYER_EDITOR_SAMPLES_SOURCE,
            color = StyleValue.Constant(Color.Transparent),
            radius = StyleValue.Constant(SAMPLE_RING_RADIUS),
            strokeColor = StyleValue.Constant(Color.White),
            strokeWidth = 3.5.dp,
          ),
          MapLayer.Circle(
            id = "layer-editor-samples",
            sourceId = LAYER_EDITOR_SAMPLES_SOURCE,
            color = StyleValue.Constant(Color.Transparent),
            radius = StyleValue.Constant(SAMPLE_RING_RADIUS + 1.dp),
            strokeColor = StyleValue.Constant(layerColor),
            strokeWidth = 1.5.dp,
          ),
        ),
  )
}

/** Inner radius of a sample point ring; the ring itself is drawn outside it. */
private val SAMPLE_RING_RADIUS = 5.dp

/**
 * These vertices as a map geometry of [kind], or the simplest geometry they can form while still
 * being drawn (e.g. a two-vertex polygon renders as a line).
 */
internal fun List<LatLng>.toMapGeometry(kind: GeometryKind): Geometry? {
  val pts = map { it.toMapLatLng() }
  return when {
    pts.isEmpty() -> null
    pts.size == 1 || kind == GeometryKind.POINT -> Geometry.Point(pts.first())
    kind == GeometryKind.POLYGON && pts.size >= 3 -> Geometry.Polygon(listOf(pts))
    else -> Geometry.LineString(pts)
  }
}

private suspend fun MapCameraState.zoomBy(delta: Double) {
  animateTo(
    position.copy(zoom = (position.zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)),
    ZOOM_BUTTON_MS,
  )
}

@Composable
private fun MapToolbar(
  dataset: EntityDataset,
  editable: Boolean,
  featureNoun: String?,
  tool: MapTool,
  draftSize: Int,
  onStartDrawing: () -> Unit,
  onUndo: () -> Unit,
  onFinish: () -> Unit,
  onCancel: () -> Unit,
) {
  val noun =
    featureNoun
      ?: when (dataset.geometryKind) {
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
    if (!editable) {
      Icon(
        Icons.Outlined.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Text(
        "Generated geometry can't be edited",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else if (tool == MapTool.DRAW) {
      val min = dataset.geometryKind.minVertices
      if (dataset.geometryKind != GeometryKind.POINT) {
        Text(
          "$draftSize / $min+ vertices",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo, enabled = draftSize > 0) { Text("Undo") }
        Button(onClick = onFinish, enabled = draftSize >= min) {
          Icon(Icons.Outlined.Done, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Finish $noun")
        }
      }
      TextButton(onClick = onCancel) {
        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Cancel")
      }
    } else {
      GroundFilterChip(
        selected = false,
        onClick = onStartDrawing,
        label = { Text("Add $noun") },
        leadingIcon = {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        },
      )
    }
  }
}

@Composable
private fun MapOverlays(
  camera: CameraPosition,
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
  // The extra bottom space keeps the map's attribution line uncovered.
  Box(
    modifier =
      Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 26.dp)
  ) {
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
          Icons.Outlined.Layers,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = colors.onSurfaceVariant,
        )
        EditorBasemap.entries.forEach { option ->
          GroundFilterChip(
            selected = basemap == option,
            onClick = { onBasemap(option) },
            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.height(28.dp),
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
          Icon(Icons.Outlined.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
        }
        HorizontalDivider(modifier = Modifier.width(24.dp))
        IconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Outlined.Remove,
            contentDescription = "Zoom out",
            modifier = Modifier.size(18.dp),
          )
        }
        HorizontalDivider(modifier = Modifier.width(24.dp))
        IconButton(onClick = onFit, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Outlined.ZoomOutMap,
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

private const val LOCKED_HINT =
  "Drag to pan • Scroll or pinch to zoom • Click a plot to see its sample points"

private fun hintFor(tool: MapTool, kind: GeometryKind, hasSelection: Boolean, draftSize: Int) =
  when {
    tool == MapTool.DRAW && kind == GeometryKind.POINT -> "Click the map to place the point"
    tool == MapTool.DRAW && kind == GeometryKind.LINE ->
      if (draftSize == 0) "Click to start the line"
      else "Click to add vertices • Click the last vertex again to finish"
    tool == MapTool.DRAW ->
      if (draftSize == 0) "Click to start the polygon"
      else "Click to add vertices • Click the first or last vertex to finish"
    hasSelection && kind == GeometryKind.POINT -> "Drag the point to move it"
    hasSelection ->
      "Drag vertices to reshape • Drag ○ handles to add a vertex • Drag inside to move"
    else -> "Drag to pan • Scroll or pinch to zoom • Click a feature to select it"
  }

/** Midpoints of each segment, used as "insert vertex" handles. */
internal fun midpoints(pts: List<ScreenPoint>, closed: Boolean): List<ScreenPoint> {
  if (pts.size < 2) return emptyList()
  val segments =
    pts.zipWithNext() +
      if (closed && pts.size >= 3) listOf(pts.last() to pts.first()) else emptyList()
  return segments.map { (a, b) -> ScreenPoint((a.x + b.x) / 2, (a.y + b.y) / 2) }
}

private fun DpOffset.toScreenPoint() = ScreenPoint(x.value.toDouble(), y.value.toDouble())

private fun ScreenPoint.isNear(o: ScreenPoint, tolerance: Double): Boolean {
  val dx = x - o.x
  val dy = y - o.y
  return dx * dx + dy * dy <= tolerance * tolerance
}

private fun polyline(pts: List<Offset>, closed: Boolean) =
  Path().apply {
    moveTo(pts.first().x, pts.first().y)
    pts.drop(1).forEach { lineTo(it.x, it.y) }
    if (closed) close()
  }
