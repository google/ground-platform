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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import groundplatform.v2.forms.GeoPoint
import org.groundplatform.v2.core.forms.ui.CompactMapDrawingHost
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.core.forms.ui.addMapDrawingVertex
import org.groundplatform.v2.core.forms.ui.mapDrawingKindOf
import org.groundplatform.v2.core.forms.ui.mapDrawingVertices
import org.groundplatform.v2.core.forms.ui.setMapDrawingVertices
import org.groundplatform.v2.core.forms.ui.undoMapDrawingVertex
import org.groundplatform.v2.devtools.prototypeapp.ui.map.DraftGeometry
import org.groundplatform.v2.devtools.prototypeapp.ui.map.FormGeometryOverlay
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/**
 * A request to bring the question at [path] into view in the web form panel; [token] increases with
 * every request so the same question can be requested twice in a row.
 */
data class FormFocusRequest(val path: String, val token: Long)

/**
 * A request to frame [bounds] in the web dashboard's main map, zooming in no further than
 * [maxZoom]; [token] increases with every request so the same bounds can be requested twice.
 */
data class MapFramingRequest(val bounds: LngLatBounds, val maxZoom: Double, val token: Long)

/**
 * The geometry answers held by [controller]'s form, in field order: every `geopoint` / `geotrace` /
 * `geoshape` field with at least one vertex, except [excludePath] (the question being drawn, which
 * the draft overlay shows instead).
 */
fun formGeometryOverlays(
  controller: FormWizardController,
  excludePath: String? = null,
): List<FormGeometryOverlay> =
  controller.formState.fieldStates.mapNotNull { (path, fieldState) ->
    if (path == excludePath) return@mapNotNull null
    val kind = mapDrawingKindOf(fieldState.dataType) ?: return@mapNotNull null
    val vertices = mapDrawingVertices(fieldState, kind)
    if (vertices.isEmpty()) return@mapNotNull null
    FormGeometryOverlay(
      path = path,
      title = controller.questionTitleFor(path),
      kind = kind,
      vertices = vertices.map { LatLng(it.latitude, it.longitude) },
    )
  }

/**
 * How far in the map may zoom when framing a form geometry of this kind: a point has no extent and
 * a small line or polygon shouldn't fill the screen (matches the entity framing zooms).
 */
val MapDrawingKind.maxFramingZoom: Double
  get() =
    when (this) {
      MapDrawingKind.POINT -> 16.0
      MapDrawingKind.LINE,
      MapDrawingKind.POLYGON -> 17.0
    }

/** The bounds of a geometry answer's [vertices], or `null` when nothing has been drawn. */
fun formGeometryBounds(vertices: List<GeoPoint>): LngLatBounds? =
  if (vertices.isEmpty()) null
  else LngLatBounds.of(vertices.map { LatLng(it.latitude, it.longitude) })

/**
 * The web dashboard's "draw on the map" host ([CompactMapDrawingHost]) for the compact form
 * runner's geometry questions: a browser has no field GPS, so every `geopoint`, `geotrace`, and
 * `geoshape` question is answered by clicking the dashboard's main map.
 *
 * While [activeDrawingPath] is set, the main map routes its clicks to [addVertex], which writes the
 * vertex into the question through [controller]; a point is finished by its first click, lines and
 * polygons keep collecting vertices until the collector clicks **Done** ([stopDrawing]). The
 * in-progress geometry is read back from the form as [draftGeometry] for the map overlay. **Zoom to
 * fit** ([frameGeometry]) asks the main map to frame a question's geometry through [onFrame].
 *
 * Like [FormWizardController], this is UI infrastructure from `shared/ui` held in the data
 * collection UI state: its drawing fields are Compose snapshot state so the shared runner's cards
 * and the map overlay recompose as vertices land. `DataCollectionViewModel` owns one instance for
 * the open form.
 */
class WebMapDrawingHost(
  private val controller: () -> FormWizardController?,
  private val onFrame: (bounds: LngLatBounds, maxZoom: Double) -> Unit = { _, _ -> },
) : CompactMapDrawingHost {

  override var activeDrawingPath by mutableStateOf<String?>(null)
    private set

  /** Geometry kind of the question at [activeDrawingPath], or `null` when not drawing. */
  var activeDrawingKind by mutableStateOf<MapDrawingKind?>(null)
    private set

  /** True while map clicks are being turned into vertices. */
  val isDrawing: Boolean
    get() = activeDrawingPath != null

  /** The question's vertices when drawing started, restored by [cancelDrawing]. */
  private var verticesAtStart: List<GeoPoint> = emptyList()

  override fun startDrawing(path: String, kind: MapDrawingKind) {
    verticesAtStart =
      controller()
        ?.formState
        ?.fieldStates
        ?.get(path)
        ?.let { mapDrawingVertices(it, kind) }
        .orEmpty()
    activeDrawingPath = path
    activeDrawingKind = kind
  }

  override fun stopDrawing() {
    activeDrawingPath = null
    activeDrawingKind = null
    verticesAtStart = emptyList()
  }

  /** Stops drawing and puts back whatever the question held before [startDrawing]. */
  fun cancelDrawing() {
    val path = activeDrawingPath ?: return
    val kind = activeDrawingKind ?: return
    controller()?.setMapDrawingVertices(path, kind, verticesAtStart)
    stopDrawing()
  }

  override val canFrameGeometry: Boolean
    get() = true

  /** Frames the question's geometry in the main map; nothing happens for an empty answer. */
  override fun frameGeometry(path: String, kind: MapDrawingKind) {
    val fieldState = controller()?.formState?.fieldStates?.get(path) ?: return
    val bounds = formGeometryBounds(mapDrawingVertices(fieldState, kind)) ?: return
    onFrame(bounds, kind.maxFramingZoom)
  }

  /**
   * Adds a map click at [latLng] to the question being drawn. Ignored when nothing is being drawn
   * or no form is open. Placing a [MapDrawingKind.POINT] finishes the drawing, so one click is
   * enough.
   */
  fun addVertex(latLng: LatLng) {
    val path = activeDrawingPath ?: return
    val kind = activeDrawingKind ?: return
    val controller = controller() ?: return
    controller.addMapDrawingVertex(path, kind, latLng.latitude, latLng.longitude)
    if (kind == MapDrawingKind.POINT) stopDrawing()
  }

  /** Removes the last vertex drawn for the active question. */
  fun undoVertex() {
    val path = activeDrawingPath ?: return
    val kind = activeDrawingKind ?: return
    controller()?.undoMapDrawingVertex(path, kind)
  }

  /** The vertices currently drawn for the active question, or `null` when not drawing. */
  val draftGeometry: DraftGeometry?
    get() {
      val path = activeDrawingPath ?: return null
      val kind = activeDrawingKind ?: return null
      val fieldState = controller()?.formState?.fieldStates?.get(path) ?: return null
      val vertices = mapDrawingVertices(fieldState, kind).map { LatLng(it.latitude, it.longitude) }
      return DraftGeometry(kind, vertices)
    }
}
