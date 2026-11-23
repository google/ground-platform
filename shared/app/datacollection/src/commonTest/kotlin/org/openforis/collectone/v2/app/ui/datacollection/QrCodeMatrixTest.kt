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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The GeoID QR code encoder (ISO/IEC 18004, level L, mask 0, versions 1–4). */
class QrCodeMatrixTest {
  private fun rows(matrix: Array<BooleanArray>): List<String> = matrix.map { row ->
    row.joinToString("") { if (it) "#" else "." }
  }

  private fun payloadOf(length: Int) = "geo:".padEnd(length, 'x')

  @Test
  fun version_growsWithThePayload_upToVersion4() {
    assertEquals(21, encodeQrMatrix(payloadOf(17)).size)
    assertEquals(25, encodeQrMatrix(payloadOf(18)).size)
    assertEquals(25, encodeQrMatrix(payloadOf(32)).size)
    assertEquals(29, encodeQrMatrix(payloadOf(53)).size)
    assertEquals(33, encodeQrMatrix(payloadOf(54)).size)
    assertTrue(encodeQrMatrix(payloadOf(54)).all { it.size == 33 })
  }

  @Test
  fun payloadsLongerThanVersion4Holds_areCutAt78Bytes() {
    val long = payloadOf(120)

    assertEquals(rows(encodeQrMatrix(long.take(78))), rows(encodeQrMatrix(long)))
  }

  @Test
  fun functionPatterns_areInPlace() {
    for (length in listOf(10, 30, 50, 70)) {
      val m = encodeQrMatrix(payloadOf(length))
      val size = m.size
      // Finder patterns: a dark 7×7 ring around a light ring around a dark 3×3 center.
      for ((top, left) in listOf(0 to 0, 0 to size - 7, size - 7 to 0)) {
        for (r in 0..6) for (c in 0..6) {
          val ring = maxOf(kotlin.math.abs(r - 3), kotlin.math.abs(c - 3))
          assertEquals(ring != 2, m[top + r][left + c], "finder ($top,$left) at ($r,$c), v$size")
        }
      }
      // Timing patterns alternate between the finders, starting dark.
      for (i in 8 until size - 8) {
        assertEquals(i % 2 == 0, m[6][i], "row timing at $i")
        assertEquals(i % 2 == 0, m[i][6], "column timing at $i")
      }
      // The dark module next to the bottom-left finder.
      assertTrue(m[size - 8][8])
      // Format information for level L and mask 0 (0x77C4), bits 0..5 along row 8.
      for (i in 0..5) assertEquals(((0x77C4 ushr i) and 1) != 0, m[8][i], "format bit $i")
    }
  }

  @Test
  fun version1Matrix_isStable() {
    assertEquals(VERSION_1_GOLDEN, rows(encodeQrMatrix("S2:10c4a89e2f")))
  }

  @Test
  fun largerMatrices_areStable() {
    assertEquals(
      LARGER_GOLDEN_HASHES,
      listOf(30, 50, 70).map { rows(encodeQrMatrix(payloadOf(it))).joinToString("\n").hashCode() },
    )
  }

  private companion object {
    val VERSION_1_GOLDEN: List<String> =
      listOf(
        "#######.##.##.#######",
        "#.....#.####..#.....#",
        "#.###.#.##.##.#.###.#",
        "#.###.#..#.#..#.###.#",
        "#.###.#.#.#.#.#.###.#",
        "#.....#.#...#.#.....#",
        "#######.#.#.#.#######",
        "........##.##........",
        "..#...######..###.###",
        ".##..#..#..#.#.###..#",
        "#.#...##....#####...#",
        "...##..#.#####.#.#..#",
        "####.##.#.####.###..#",
        "........#..####.###..",
        "#######.#..##.#.#..##",
        "#.....#...####..##...",
        "#.###.#.....##..##.#.",
        "#.###.#...#.######.#.",
        "#.###.#.#.#.#######.#",
        "#.....#..##....#.#.#.",
        "#######....##...##.##",
      )
    val LARGER_GOLDEN_HASHES: List<Int> = listOf(1981178757, 1344385835, -1503592697)
  }
}
