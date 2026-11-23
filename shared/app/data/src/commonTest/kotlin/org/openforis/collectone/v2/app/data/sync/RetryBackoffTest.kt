/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.sync

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Waits between sync retries: doubling, capped, and shortened by jitter. */
class RetryBackoffTest {
  /** A [Random] whose doubles are always [value], to pin the jitter. */
  private fun fixedRandom(value: Double) =
    object : Random() {
      override fun nextBits(bitCount: Int): Int = 0

      override fun nextDouble(): Double = value
    }

  @Test
  fun withoutJitter_doublesFromTwoSecondsUpToOneMinute() {
    val backoff = RetryBackoff(random = fixedRandom(0.0))

    assertEquals(
      listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L),
      (1..7).map(backoff::delayMillis),
    )
  }

  @Test
  fun fullJitter_shortensTheWaitByAFifth() {
    val backoff = RetryBackoff(random = fixedRandom(1.0))

    assertEquals(1_600L, backoff.delayMillis(1))
    assertEquals(48_000L, backoff.delayMillis(10))
  }

  @Test
  fun jitteredWaits_neverExceedTheCap() {
    val backoff = RetryBackoff(random = Random(seed = 42))

    val waits = (1..200).map { backoff.delayMillis(it % 12 + 1) }

    assertTrue(waits.all { it in 1_600L..60_000L }, "waits out of range: $waits")
  }

  @Test
  fun hugeAttemptCounts_stayAtTheCap() {
    val backoff = RetryBackoff(random = fixedRandom(0.0))

    assertEquals(60_000L, backoff.delayMillis(Int.MAX_VALUE))
  }

  @Test
  fun attemptsBelowOne_waitTheInitialDelay() {
    val backoff = RetryBackoff(random = fixedRandom(0.0))

    assertEquals(2_000L, backoff.delayMillis(0))
  }
}
