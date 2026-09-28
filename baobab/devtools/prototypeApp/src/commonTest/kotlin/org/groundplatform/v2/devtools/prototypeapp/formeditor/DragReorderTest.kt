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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DragReorderTest {

  @Test
  fun dragTargetIndex_roundsToNearestSlotAndClamps() {
    assertEquals(1, dragTargetIndex(from = 1, delta = 40f, pitch = 100f, count = 4))
    assertEquals(2, dragTargetIndex(from = 1, delta = 60f, pitch = 100f, count = 4))
    assertEquals(0, dragTargetIndex(from = 1, delta = -160f, pitch = 100f, count = 4))
    assertEquals(3, dragTargetIndex(from = 1, delta = 900f, pitch = 100f, count = 4))
  }

  @Test
  fun dragShiftFor_opensGapAtTarget() {
    // Dragging item 1 to slot 3: items 2 and 3 slide back one slot.
    assertEquals(listOf(0, 0, -1, -1, 0), (0..4).map { dragShiftFor(it, from = 1, target = 3) })
    // Dragging item 3 to slot 1: items 1 and 2 slide forward one slot.
    assertEquals(listOf(0, 1, 1, 0, 0), (0..4).map { dragShiftFor(it, from = 3, target = 1) })
  }

  @Test
  fun moved_relocatesElement() {
    assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
    assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
    assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, 5))
  }

  @Test
  fun state_tracksDragAndReportsDrop() {
    val drag = DragReorderState()
    drag.start("q2", index = 1, count = 4, pitch = 100f)
    drag.dragBy(130f)
    assertEquals(2, drag.targetIndex)
    assertEquals(-1, drag.shiftFor(2))
    assertEquals(0, drag.shiftFor(3))

    drag.dragBy(10_000f) // Clamped to half a slot past the end.
    assertEquals(250f, drag.offset)

    assertEquals("q2" to 3, drag.end())
    assertFalse(drag.isDragging)
    assertNull(drag.end())
  }
}
