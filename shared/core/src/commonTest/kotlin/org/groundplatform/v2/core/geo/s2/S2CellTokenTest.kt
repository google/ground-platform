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
package org.groundplatform.v2.core.geo.s2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class S2CellTokenTest {
  @Test
  fun faceCells_haveCanonicalTokens() {
    // Level-0 cells are the six cube faces: ids (2·face + 1) << 60.
    assertEquals("1", s2CellToken(0.0, 0.0, 0))
    assertEquals("3", s2CellToken(0.0, 90.0, 0))
    assertEquals("5", s2CellToken(90.0, 0.0, 0))
    assertEquals("7", s2CellToken(0.0, 180.0, 0))
    assertEquals("9", s2CellToken(0.0, -90.0, 0))
    assertEquals("b", s2CellToken(-90.0, 0.0, 0))
  }

  @Test
  fun level13Tokens_areStableAndLocal() {
    val a = s2CellToken(-1.2921, 36.8219)
    assertEquals(a, s2CellToken(-1.2921, 36.8219))
    assertTrue(a.length in 1..16)
    // Points ~5 km apart fall in different level-13 cells.
    assertNotEquals(a, s2CellToken(-1.2921, 36.8669))
    // A level-13 id uses face (3 bits) + 26 position bits + the marker bit, i.e. ≤ 8 hex digits.
    assertTrue(s2CellToken(10.123, 20.456).length <= 8)
  }
}
