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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import openforis.collectone.v1.forms.GeoPoint

/**
 * Editing state of one geo question's built-in map that is not part of the answer: how far the
 * collector panned and zoomed, whether GPS walking is on, and the undo / redo history of the
 * question's vertices.
 *
 * A geopoint answer is handled as a list of at most one vertex (empty when cleared), so geopoint,
 * geotrace, and geoshape questions share the same history rules.
 * [FormWizardController.geoEditState] keeps one instance per question path, so the state outlives
 * recompositions and survives leaving the question and coming back.
 */
class GeoEditState {
  /** Latitude offset of the map center from the GPS fix, in degrees (0 when following GPS). */
  var panOffsetLat: Double by mutableStateOf(0.0)
    private set

  /** Longitude offset of the map center from the GPS fix, in degrees (0 when following GPS). */
  var panOffsetLon: Double by mutableStateOf(0.0)
    private set

  /** Map zoom level, between [MIN_ZOOM] and [MAX_ZOOM]. */
  var zoomLevel: Float by mutableStateOf(DEFAULT_ZOOM)
    private set

  /** Whether a geotrace / geoshape is recording the GPS fix as the collector walks. */
  var isWalking: Boolean by mutableStateOf(false)
    private set

  private var undoHistory: List<List<GeoPoint>> by mutableStateOf(emptyList())
  private var redoHistory: List<List<GeoPoint>> by mutableStateOf(emptyList())

  /** Whether [redo] has an undone edit to reapply. */
  val canRedo: Boolean
    get() = redoHistory.isNotEmpty()

  /** Whether [undo] can change [vertices]: an earlier edit to revert, or a vertex to drop. */
  fun canUndo(vertices: List<GeoPoint>): Boolean = undoHistory.isNotEmpty() || vertices.isNotEmpty()

  /** Whether the map center is away from the GPS fix; never true when panning is not allowed. */
  fun isPanned(panAllowed: Boolean): Boolean =
    panAllowed && (abs(panOffsetLat) > PAN_EPSILON || abs(panOffsetLon) > PAN_EPSILON)

  /**
   * Pans the map by a drag of ([dxPx], [dyPx]) pixels on a map of [widthPx] × [heightPx] pixels.
   * Ignored when [panAllowed] is false or the map has no size yet.
   */
  fun panByPixels(
    dxPx: Float,
    dyPx: Float,
    widthPx: Float,
    heightPx: Float,
    panAllowed: Boolean,
  ) {
    if (!panAllowed || widthPx <= 0f || heightPx <= 0f) return
    val spanDeg = 0.0016 * (DEFAULT_ZOOM / zoomLevel.coerceIn(13f, 20f))
    panOffsetLon = (panOffsetLon - (dxPx / widthPx) * spanDeg).coerceIn(-MAX_PAN_DEG, MAX_PAN_DEG)
    panOffsetLat = (panOffsetLat + (dyPx / heightPx) * spanDeg).coerceIn(-MAX_PAN_DEG, MAX_PAN_DEG)
  }

  /** Zooms in (positive [delta]) or out, staying between [MIN_ZOOM] and [MAX_ZOOM]. */
  fun zoomBy(delta: Float) {
    zoomLevel = (zoomLevel + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
  }

  /** Centers the map on the GPS fix again. */
  fun recenter() {
    panOffsetLat = 0.0
    panOffsetLon = 0.0
  }

  /** Starts or stops GPS walking. */
  fun toggleWalking() {
    isWalking = !isWalking
  }

  /** Stops GPS walking. */
  fun stopWalking() {
    isWalking = false
  }

  /**
   * Remembers [vertices] as the state to return to on [undo], ahead of a new edit. A new edit
   * discards the redo history.
   */
  fun recordEdit(vertices: List<GeoPoint>) {
    undoHistory = undoHistory + listOf(vertices)
    redoHistory = emptyList()
  }

  /**
   * Returns the vertices to show after undoing from [vertices]: the state before the last recorded
   * edit, or, with no history, [vertices] without its last vertex. Returns `null` when there is
   * nothing to undo. [vertices] becomes available to [redo].
   */
  fun undo(vertices: List<GeoPoint>): List<GeoPoint>? {
    val previous =
      undoHistory.lastOrNull()?.also { undoHistory = undoHistory.dropLast(1) }
        ?: vertices.takeIf { it.isNotEmpty() }?.dropLast(1)
        ?: return null
    redoHistory = redoHistory + listOf(vertices)
    return previous
  }

  /**
   * Returns the vertices to show after redoing the last undone edit, or `null` when nothing was
   * undone. [vertices] becomes available to [undo] again.
   */
  fun redo(vertices: List<GeoPoint>): List<GeoPoint>? {
    val next = redoHistory.lastOrNull() ?: return null
    redoHistory = redoHistory.dropLast(1)
    undoHistory = undoHistory + listOf(vertices)
    return next
  }

  companion object {
    const val DEFAULT_ZOOM: Float = DEFAULT_GEO_MAP_ZOOM
    const val MIN_ZOOM: Float = 13.5f
    const val MAX_ZOOM: Float = 19.5f

    /** How far the map may be panned from the GPS fix, in degrees (about 2 km). */
    const val MAX_PAN_DEG: Double = 0.02

    /** Pan offsets smaller than this (about 5 cm) count as centered on the GPS fix. */
    private const val PAN_EPSILON: Double = 0.0000005
  }
}
