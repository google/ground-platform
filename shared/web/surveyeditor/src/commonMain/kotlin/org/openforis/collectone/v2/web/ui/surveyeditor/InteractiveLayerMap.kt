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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.formatFixed
import org.openforis.collectone.v2.app.domain.model.editor.parseHexColor
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.map.SurveyBasemaps
import org.openforis.collectone.v2.app.ui.map.contentColorOnArgb
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.map.AppMap
import org.openforis.collectone.v2.map.Basemap
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.map.MapCameraState
import org.openforis.collectone.v2.map.MapDrag
import org.openforis.collectone.v2.map.MapDragHandler
import org.openforis.collectone.v2.map.MapEvent
import org.openforis.collectone.v2.map.MapInsets
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_map_add_line
import org.openforis.collectone.v2.web.resources.surveyeditor_map_add_part
import org.openforis.collectone.v2.web.resources.surveyeditor_map_add_point
import org.openforis.collectone.v2.web.resources.surveyeditor_map_add_polygon
import org.openforis.collectone.v2.web.resources.surveyeditor_map_draft_vertices
import org.openforis.collectone.v2.web.resources.surveyeditor_map_finish_line
import org.openforis.collectone.v2.web.resources.surveyeditor_map_finish_part
import org.openforis.collectone.v2.web.resources.surveyeditor_map_finish_point
import org.openforis.collectone.v2.web.resources.surveyeditor_map_finish_polygon
import org.openforis.collectone.v2.web.resources.surveyeditor_map_fit_to_features
import org.openforis.collectone.v2.web.resources.surveyeditor_map_generated_locked
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_continue_line
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_continue_polygon
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_locked
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_move_point
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_place_point
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_reshape
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_select
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_start_line
import org.openforis.collectone.v2.web.resources.surveyeditor_map_hint_start_polygon
import org.openforis.collectone.v2.web.resources.surveyeditor_map_title
import org.openforis.collectone.v2.web.resources.surveyeditor_map_undo
import org.openforis.collectone.v2.web.resources.surveyeditor_map_zoom_in
import org.openforis.collectone.v2.web.resources.surveyeditor_map_zoom_level
import org.openforis.collectone.v2.web.resources.surveyeditor_map_zoom_out
import org.openforis.collectone.v2.web.ui.formeditor.moved

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

/**
 * Interactive map for a Map layer: a [AppMap] with a satellite/terrain basemap and the layer's
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
 * @param featureNoun what the toolbar calls a new feature (e.g. [MapFeatureNoun.PART]); defaults to
 *   the geometry type.
 * @param showLabels whether to draw feature labels when zoomed in.
 * @param emptyCamera where the map starts when the layer has no features yet.
 */
@Composable
fun InteractiveLayerMapCard(
  editor: MapFeatureEditor,
  dataset: EntityDataset,
  selectedRow: String?,
  onSelectRow: (String?) -> Unit,
  modifier: Modifier = Modifier,
  geometryEditable: Boolean = !dataset.isGenerated,
  featureNoun: MapFeatureNoun? = null,
  showLabels: Boolean = true,
  emptyCamera: CameraPosition = DEFAULT_EDITOR_CAMERA,
  startInDrawMode: Boolean = false,
  showAddButtonInHeader: Boolean = true,
  drawRequestToken: Int = 0,
  onDrawStateChange: ((isDrawing: Boolean, draftVertexCount: Int) -> Unit)? = null,
  backdropPolygons: List<List<LatLng>> = emptyList(),
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current.density
  val textMeasurer = rememberTextMeasurer()
  val labelStyle =
    MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
  val scope = rememberCoroutineScope()

  val cameraState = remember(dataset.key) { MapCameraState(emptyCamera) }
  var fitted by remember(dataset.key) { mutableStateOf(false) }
  var basemap by remember { mutableStateOf(EditorBasemap.TERRAIN) }
  var tool by
    remember(dataset.key) { mutableStateOf(if (startInDrawMode) MapTool.DRAW else MapTool.SELECT) }
  var draft by remember(dataset.key) { mutableStateOf(emptyList<LatLng>()) }
  var hover by remember { mutableStateOf<ScreenPoint?>(null) }

  // Map callbacks outlive recompositions; always read the latest inputs.
  val currentDataset by rememberUpdatedState(dataset)
  val currentSelected by rememberUpdatedState(selectedRow)
  val currentOnSelect by rememberUpdatedState(onSelectRow)
  val currentEditable by rememberUpdatedState(geometryEditable)
  val currentOnDrawStateChange by rememberUpdatedState(onDrawStateChange)

  LaunchedEffect(drawRequestToken) {
    if (drawRequestToken > 0) {
      tool = MapTool.DRAW
      draft = emptyList()
      currentOnSelect(null)
    }
  }

  LaunchedEffect(startInDrawMode, dataset.rows.isEmpty()) {
    if (startInDrawMode && dataset.rows.isEmpty() && tool != MapTool.DRAW) {
      tool = MapTool.DRAW
      draft = emptyList()
      currentOnSelect(null)
    }
  }

  LaunchedEffect(tool, draft.size) {
    currentOnDrawStateChange?.invoke(tool == MapTool.DRAW, draft.size)
  }

  fun screenOf(p: LatLng): ScreenPoint = cameraState.project(p).toScreenPoint()

  fun project(p: LatLng): ScreenPoint = screenOf(p)

  fun unproject(p: ScreenPoint): LatLng =
    cameraState.unproject(DpOffset(p.x.toFloat().dp, p.y.toFloat().dp))

  suspend fun fitAll(durationMs: Int = MapCameraState.DEFAULT_ANIMATION_MS) {
    val rowPoints = currentDataset.rows.flatMap { it.geometry }.map { it }
    val points = rowPoints.ifEmpty { backdropPolygons.flatten().map { it } }
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
    if (points.size < ds.geometryType.minVertices) return
    currentOnSelect(editor.addRow(ds.key, geometry = points))
    draft = emptyList()
    tool = MapTool.SELECT
  }

  fun handleTap(p: ScreenPoint) {
    val ds = currentDataset
    val kind = ds.geometryType
    when (tool) {
      MapTool.DRAW -> {
        val canFinish = draft.size >= kind.minVertices
        when {
          kind == GeometryType.POINT -> finishDraft(listOf(unproject(p)))
          canFinish && project(draft.last()).isNear(p, VERTEX_HIT_PX) -> finishDraft(draft)
          canFinish &&
            kind == GeometryType.POLYGON &&
            project(draft.first()).isNear(p, VERTEX_HIT_PX) -> finishDraft(draft)
          else -> draft = draft + unproject(p)
        }
      }
      MapTool.SELECT -> {
        val sel = currentSelected
        if (currentEditable && sel != null && kind != GeometryType.POINT) {
          val row = ds.rows.firstOrNull { it.key == sel }
          if (row != null && row.geometry.isNotEmpty()) {
            val pts = row.geometry.map(::project)
            if (pts.none { it.isNear(p, VERTEX_HIT_PX) }) {
              val mids = midpoints(pts, closed = kind == GeometryType.POLYGON)
              val mid = mids.indexOfFirst { it.isNear(p, MIDPOINT_HIT_PX) }
              if (mid >= 0) {
                val inserted =
                  row.geometry.toMutableList().apply { add(mid + 1, unproject(mids[mid])) }
                editor.updateGeometry(ds.key, sel, inserted)
                return
              }
            }
          }
        }
        currentOnSelect(hitTestFeature(ds, ::project, p))
      }
    }
  }

  fun vertexDrag(
    rowKey: String,
    index: Int,
    initialGeometry: List<LatLng>? = null,
  ): MapDrag =
    object : MapDrag {
      private var latestGeometry: List<LatLng>? = initialGeometry

      override fun onDrag(position: DpOffset) {
        val ds = currentDataset
        val baseGeometry =
          latestGeometry ?: ds.rows.firstOrNull { it.key == rowKey }?.geometry ?: return
        if (index !in baseGeometry.indices) return
        val moved = baseGeometry.toMutableList()
        moved[index] = unproject(position.toScreenPoint())
        latestGeometry = moved
        editor.updateGeometry(ds.key, rowKey, moved)
      }
    }

  fun featureDrag(rowKey: String, start: LatLng, original: List<LatLng>): MapDrag =
    object : MapDrag {
      override fun onDrag(position: DpOffset) {
        val now = unproject(position.toScreenPoint())
        val dLat = now.latitude - start.latitude
        val dLng = now.longitude - start.longitude
        editor.updateGeometry(
          currentDataset.key,
          rowKey,
          original.map { LatLng.coerced(it.latitude + dLat, it.longitude + dLng) },
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
    if (vertex >= 0) return vertexDrag(sel, vertex, row.geometry)
    if (ds.geometryType != GeometryType.POINT) {
      val mids = midpoints(pts, closed = ds.geometryType == GeometryType.POLYGON)
      val mid = mids.indexOfFirst { it.isNear(p, MIDPOINT_HIT_PX) }
      if (mid >= 0) {
        val inserted = row.geometry.toMutableList().apply { add(mid + 1, unproject(mids[mid])) }
        editor.updateGeometry(ds.key, sel, inserted)
        return vertexDrag(sel, mid + 1, inserted)
      }
    }
    if (hitTestFeature(ds, ::project, p) == sel) {
      return featureDrag(sel, unproject(p), row.geometry)
    }
    return null
  }

  // Fit to the layer's features once the viewport size is known (and when switching layers or
  // regenerating sample plots).
  val viewport = cameraState.viewportSize
  val viewportReady = viewport.width > 4.dp && viewport.height > 4.dp
  val hasRows = dataset.rows.isNotEmpty()
  val lastRun = dataset.generator?.lastRun
  LaunchedEffect(dataset.key, viewportReady, hasRows, lastRun, backdropPolygons) {
    if (viewportReady && (!fitted || (dataset.isGenerated && hasRows))) {
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
        LngLatBounds.of(row.geometry.map { it }),
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
  val withBackdrop =
    remember(baseContent, backdropPolygons, colors.primary) {
      withBackdropPolygons(baseContent, backdropPolygons, colors.primary)
    }
  // Sample points of generated plots (and any selected plot) go in an overlay source so sub-plot
  // layouts are visible immediately on the preview map.
  val samplePoints =
    remember(dataset.rows, selectedRow) { visiblePlotSamples(dataset, selectedRow) }
  val sampleSizeM = dataset.generator?.let(::samplePointSizeM) ?: DEFAULT_SAMPLE_SIZE_M
  val content =
    remember(withBackdrop, samplePoints, layerColor, sampleSizeM) {
      withSamplePointsOverlay(withBackdrop, samplePoints, layerColor, sampleSizeM)
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
        showAddButtonInHeader = showAddButtonInHeader,
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
        AppMap(
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
          // Quick clicks place vertices while drawing, so they mustn't zoom.
          doubleTapZoomEnabled = tool != MapTool.DRAW,
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
          if (geometryEditable && selected != null && dataset.geometryType != GeometryType.POINT) {
            val screen = selected.geometry.map(::project)
            midpoints(screen, closed = dataset.geometryType == GeometryType.POLYGON).forEach {
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
              val closed = dataset.geometryType == GeometryType.POLYGON && preview.size >= 3
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
            stringResource(
              if (geometryEditable)
                hintFor(tool, dataset.geometryType, selectedRow != null, draft.size)
              else Res.string.surveyeditor_map_hint_locked
            ),
          onZoomIn = {
            scope.launch {
              val ds = currentDataset
              val selectedCenter = currentSelected?.let { sel ->
                ds.rows.firstOrNull { it.key == sel }?.geometry?.centroidOrNull()
              }
              val focus =
                when {
                  selectedCenter != null -> selectedCenter
                  ds.isGenerated && ds.rows.isNotEmpty() -> {
                    val cam = cameraState.position.center
                    ds.rows
                      .mapNotNull { it.geometry.centroidOrNull() }
                      .minByOrNull { c ->
                        val dLat = c.latitude - cam.latitude
                        val dLng = (c.longitude - cam.longitude) * cos(cam.latitude * PI / 180.0)
                        dLat * dLat + dLng * dLng
                      }
                  }
                  else -> null
                }
              cameraState.zoomBy(1.0, focus)
            }
          },
          onZoomOut = { scope.launch { cameraState.zoomBy(-1.0) } },
          onFit = { scope.launch { fitAll() } },
        )
      }
    }
  }
}

private suspend fun MapCameraState.zoomBy(delta: Double, focus: LatLng? = null) {
  animateTo(
    position.copy(
      center = focus ?: position.center,
      zoom = (position.zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM),
    ),
    ZOOM_BUTTON_MS,
  )
}

/** A noun the map toolbar uses for new features instead of the geometry type. */
enum class MapFeatureNoun {
  /** A part of a multi-part survey area. */
  PART
}

@Composable
private fun MapToolbar(
  dataset: EntityDataset,
  editable: Boolean,
  featureNoun: MapFeatureNoun?,
  showAddButtonInHeader: Boolean,
  tool: MapTool,
  draftSize: Int,
  onStartDrawing: () -> Unit,
  onUndo: () -> Unit,
  onFinish: () -> Unit,
  onCancel: () -> Unit,
) {
  val addLabel =
    when {
      featureNoun == MapFeatureNoun.PART -> Res.string.surveyeditor_map_add_part
      dataset.geometryType == GeometryType.POINT -> Res.string.surveyeditor_map_add_point
      dataset.geometryType == GeometryType.LINE_STRING -> Res.string.surveyeditor_map_add_line
      else -> Res.string.surveyeditor_map_add_polygon
    }
  val finishLabel =
    when {
      featureNoun == MapFeatureNoun.PART -> Res.string.surveyeditor_map_finish_part
      dataset.geometryType == GeometryType.POINT -> Res.string.surveyeditor_map_finish_point
      dataset.geometryType == GeometryType.LINE_STRING -> Res.string.surveyeditor_map_finish_line
      else -> Res.string.surveyeditor_map_finish_polygon
    }
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      stringResource(Res.string.surveyeditor_map_title),
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
        stringResource(Res.string.surveyeditor_map_generated_locked),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else if (tool == MapTool.DRAW) {
      val min = dataset.geometryType.minVertices
      if (dataset.geometryType != GeometryType.POINT) {
        Text(
          stringResource(Res.string.surveyeditor_map_draft_vertices, draftSize, min),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo, enabled = draftSize > 0) {
          Text(stringResource(Res.string.surveyeditor_map_undo))
        }
        Button(onClick = onFinish, enabled = draftSize >= min) {
          Icon(Icons.Outlined.Done, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(finishLabel))
        }
      }
      TextButton(onClick = onCancel) {
        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(Res.string.surveyeditor_cancel))
      }
    } else if (showAddButtonInHeader) {
      FlatFilterChip(
        selected = false,
        onClick = onStartDrawing,
        label = { Text(stringResource(addLabel)) },
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
          FlatFilterChip(
            selected = basemap == option,
            onClick = { onBasemap(option) },
            label = { Text(option.displayLabel, style = MaterialTheme.typography.labelSmall) },
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
          Icon(
            Icons.Outlined.Add,
            contentDescription = stringResource(Res.string.surveyeditor_map_zoom_in),
            modifier = Modifier.size(18.dp),
          )
        }
        HorizontalDivider(modifier = Modifier.width(24.dp))
        IconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Outlined.Remove,
            contentDescription = stringResource(Res.string.surveyeditor_map_zoom_out),
            modifier = Modifier.size(18.dp),
          )
        }
        HorizontalDivider(modifier = Modifier.width(24.dp))
        IconButton(onClick = onFit, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Outlined.ZoomOutMap,
            contentDescription = stringResource(Res.string.surveyeditor_map_fit_to_features),
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
          Text(scale.length.toUiText().resolve(), style = MaterialTheme.typography.labelSmall)
          Box(
            modifier =
              Modifier.width(scale.widthPx.toFloat().dp)
                .height(5.dp)
                .border(1.5.dp, colors.onSurface)
                .background(colors.onSurface.copy(alpha = 0.15f))
          )
        }
        Text(
          stringResource(Res.string.surveyeditor_map_zoom_level, formatFixed(camera.zoom, 1)),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
        if (hoverLatLng != null) {
          Text(
            "${formatFixed(hoverLatLng.latitude, 5)}, ${formatFixed(hoverLatLng.longitude, 5)}",
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

private fun hintFor(
  tool: MapTool,
  kind: GeometryType,
  hasSelection: Boolean,
  draftSize: Int,
): StringResource =
  when {
    tool == MapTool.DRAW && kind == GeometryType.POINT ->
      Res.string.surveyeditor_map_hint_place_point
    tool == MapTool.DRAW && kind == GeometryType.LINE_STRING ->
      if (draftSize == 0) Res.string.surveyeditor_map_hint_start_line
      else Res.string.surveyeditor_map_hint_continue_line
    tool == MapTool.DRAW ->
      if (draftSize == 0) Res.string.surveyeditor_map_hint_start_polygon
      else Res.string.surveyeditor_map_hint_continue_polygon
    hasSelection && kind == GeometryType.POINT -> Res.string.surveyeditor_map_hint_move_point
    hasSelection -> Res.string.surveyeditor_map_hint_reshape
    else -> Res.string.surveyeditor_map_hint_select
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
