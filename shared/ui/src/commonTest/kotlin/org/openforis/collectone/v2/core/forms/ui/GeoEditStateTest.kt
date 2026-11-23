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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.GeoPoint

class GeoEditStateTest {

  private val a = GeoPoint(latitude = 1.0, longitude = 1.0)
  private val b = GeoPoint(latitude = 2.0, longitude = 2.0)
  private val c = GeoPoint(latitude = 3.0, longitude = 3.0)

  @Test
  fun undo_returnsTheVerticesBeforeTheLastRecordedEdit() {
    val state = GeoEditState()
    state.recordEdit(listOf(a))

    assertEquals(listOf(a), state.undo(listOf(a, b)))
  }

  @Test
  fun undo_withoutHistory_dropsTheLastVertex() {
    val state = GeoEditState()

    assertEquals(listOf(a), state.undo(listOf(a, b)))
  }

  @Test
  fun undo_withoutHistoryOrVertices_hasNothingToUndo() {
    val state = GeoEditState()

    assertNull(state.undo(emptyList()))
    assertFalse(state.canUndo(emptyList()))
  }

  @Test
  fun redo_reappliesTheVerticesThatWereUndone() {
    val state = GeoEditState()
    state.recordEdit(listOf(a))
    state.undo(listOf(a, b))

    assertEquals(listOf(a, b), state.redo(listOf(a)))
  }

  @Test
  fun redo_withNothingUndone_hasNothingToRedo() {
    val state = GeoEditState()

    assertNull(state.redo(listOf(a)))
    assertFalse(state.canRedo)
  }

  @Test
  fun recordEdit_discardsTheRedoHistory() {
    val state = GeoEditState()
    state.recordEdit(listOf(a))
    state.undo(listOf(a, b))

    state.recordEdit(listOf(a))

    assertFalse(state.canRedo)
  }

  @Test
  fun redo_makesTheRedoneStateUndoableAgain() {
    val state = GeoEditState()
    state.recordEdit(listOf(a, b))
    state.undo(listOf(a, b, c))
    state.redo(listOf(a, b))

    assertEquals(listOf(a, b), state.undo(listOf(a, b, c)))
  }

  @Test
  fun panByPixels_whenPanIsNotAllowed_keepsTheMapOnGps() {
    val state = GeoEditState()

    state.panByPixels(dxPx = -100f, dyPx = 0f, widthPx = 400f, heightPx = 200f, panAllowed = false)

    assertFalse(state.isPanned(panAllowed = true))
  }

  @Test
  fun panByPixels_draggingLeft_movesTheCenterEast() {
    val state = GeoEditState()

    state.panByPixels(dxPx = -100f, dyPx = 0f, widthPx = 400f, heightPx = 200f, panAllowed = true)

    assertTrue(state.panOffsetLon > 0.0)
    assertTrue(state.isPanned(panAllowed = true))
  }

  @Test
  fun panByPixels_staysWithinTheMaximumPan() {
    val state = GeoEditState()

    repeat(100) {
      state.panByPixels(dxPx = -400f, dyPx = 0f, widthPx = 400f, heightPx = 200f, panAllowed = true)
    }

    assertEquals(GeoEditState.MAX_PAN_DEG, state.panOffsetLon)
  }

  @Test
  fun isPanned_isFalseWhenPanningIsNotAllowed() {
    val state = GeoEditState()
    state.panByPixels(dxPx = -100f, dyPx = 0f, widthPx = 400f, heightPx = 200f, panAllowed = true)

    assertFalse(state.isPanned(panAllowed = false))
  }

  @Test
  fun recenter_returnsTheMapToGps() {
    val state = GeoEditState()
    state.panByPixels(dxPx = -100f, dyPx = 40f, widthPx = 400f, heightPx = 200f, panAllowed = true)

    state.recenter()

    assertFalse(state.isPanned(panAllowed = true))
  }

  @Test
  fun zoomBy_staysWithinTheZoomRange() {
    val state = GeoEditState()

    repeat(20) { state.zoomBy(0.75f) }

    assertEquals(GeoEditState.MAX_ZOOM, state.zoomLevel)
  }

  @Test
  fun toggleWalking_startsAndStopsWalking() {
    val state = GeoEditState()

    state.toggleWalking()
    assertTrue(state.isWalking)
    state.toggleWalking()

    assertFalse(state.isWalking)
  }
}
