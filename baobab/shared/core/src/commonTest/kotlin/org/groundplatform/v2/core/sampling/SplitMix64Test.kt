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
package org.groundplatform.v2.core.sampling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitMix64Test {
  @Test
  fun nextLong_matchesReferenceVectors() {
    // Reference outputs of Vigna's splitmix64.c for seeds 0, 1 and 42.
    assertEquals(
      listOf(-2152535657050944081L, 7960286522194355700L, 487617019471545679L),
      SplitMix64(0).let { r -> List(3) { r.nextLong() } },
    )
    assertEquals(
      listOf(-7995527694508729151L, -4689498862643123097L, -534904783426661026L),
      SplitMix64(1).let { r -> List(3) { r.nextLong() } },
    )
    assertEquals(
      listOf(-4767286540954276203L, 2949826092126892291L, 5139283748462763858L),
      SplitMix64(42).let { r -> List(3) { r.nextLong() } },
    )
  }

  @Test
  fun nextDouble_usesTop53Bits() {
    val expected = (-2152535657050944081L ushr 11).toDouble() / (1L shl 53).toDouble()
    assertEquals(expected, SplitMix64(0).nextDouble())
  }

  @Test
  fun nextInt_isInRangeAndDeterministic() {
    val a = SplitMix64(7).let { r -> List(1000) { r.nextInt(10) } }
    val b = SplitMix64(7).let { r -> List(1000) { r.nextInt(10) } }
    assertEquals(a, b)
    assertTrue(a.all { it in 0 until 10 })
    assertEquals(10, a.toSet().size)
  }

  @Test
  fun derive_isStableAcrossPlatforms() {
    // FNV-1a offset basis 0xCBF29CE484222325.
    assertEquals(-3750763034362895579L, SplitMix64.fnv1a64(""))
    assertEquals(SplitMix64.derive(1, "P000001"), SplitMix64.derive(1, "P000001"))
    assertTrue(SplitMix64.derive(1, "P000001") != SplitMix64.derive(1, "P000002"))
    assertTrue(SplitMix64.derive(1, 3) != SplitMix64.derive(2, 3))
  }
}
