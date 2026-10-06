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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import groundplatform.v2.forms.GeoPoint
import org.groundplatform.v2.core.forms.ui.CompactMapDrawingHost
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.core.forms.ui.addMapDrawingVertex
import org.groundplatform.v2.core.forms.ui.mapDrawingKindOf
import org.groundplatform.v2.core.forms.ui.mapDrawingVertices
import org.groundplatform.v2.core.forms.ui.setMapDrawingVertices
import org.groundplatform.v2.core.forms.ui.undoMapDrawingVertex
import org.groundplatform.v2.devtools.prototypeapp.map.DraftGeometry
import org.groundplatform.v2.devtools.prototypeapp.map.FormGeometryOverlay
import org.groundplatform.v2.map.LatLng

/**
 * A request to bring the question at [path] into view in the web form panel; [token] increases with
 * every request so the same question can be requested twice in a row.
 */
data class FormFocusRequest(val path: String, val token: Long)

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
 * The web dashboard's "draw on the map" host ([CompactMapDrawingHost]) for the compact form
 * runner's geometry questions: a browser has no field GPS, so every `geopoint`, `geotrace`, and
 * `geoshape` question is answered by clicking the dashboard's main map.
 *
 * While [activeDrawingPath] is set, the main map routes its clicks to [addVertex], which writes the
 * vertex into the question through [controller]; a point is finished by its first click, lines and
 * polygons keep collecting vertices until the collector clicks **Done** ([stopDrawing]). The
 * in-progress geometry is read back from the form as [draftGeometry] for the map overlay.
 */
class WebMapDrawingHost(private val controller: () -> FormWizardController?) :
  CompactMapDrawingHost {

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

/**
 * Floating instructions over the dashboard map while a geometry is being drawn: what to click, how
 * many vertices are placed versus needed, and **Undo** (lines and polygons), **Done** (once enough
 * vertices are placed), and **Cancel** actions.
 */
@Composable
internal fun WebMapDrawingHint(
  draft: DraftGeometry,
  onUndo: () -> Unit,
  onDone: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val kind = draft.kind
  val count = draft.vertices.size
  val progress =
    when (kind) {
      MapDrawingKind.POINT -> "Click the map to place the point"
      else -> "Click the map to add vertices • $count of at least ${kind.minVertices}"
    }
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    tonalElevation = 3.dp,
    shadowElevation = 4.dp,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Edit,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(text = progress, style = MaterialTheme.typography.labelLarge)
      if (kind != MapDrawingKind.POINT) {
        TextButton(onClick = onUndo, enabled = count > 0) { Text("Undo") }
        Button(onClick = onDone, enabled = draft.isComplete) { Text("Done") }
      }
      TextButton(onClick = onCancel) { Text("Cancel drawing") }
    }
  }
}
