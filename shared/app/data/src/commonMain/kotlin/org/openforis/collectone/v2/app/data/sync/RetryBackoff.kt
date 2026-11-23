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

/**
 * Capped exponential backoff with jitter: the wait before retry number `attempt` (counting from 1)
 * doubles from [initialMillis] up to [maxMillis], and jitter shortens it by up to [jitterRatio] so
 * that clients failing together don't retry together. The wait never exceeds [maxMillis].
 */
class RetryBackoff(
  val initialMillis: Long = DEFAULT_INITIAL_MILLIS,
  val maxMillis: Long = DEFAULT_MAX_MILLIS,
  private val jitterRatio: Double = DEFAULT_JITTER_RATIO,
  private val random: Random = Random.Default,
) {
  init {
    require(initialMillis > 0 && maxMillis >= initialMillis) { "Invalid backoff range" }
    require(jitterRatio in 0.0..1.0) { "Jitter ratio must be between 0 and 1" }
  }

  /** The wait before retry number [attempt] (1 for the first retry), in milliseconds. */
  fun delayMillis(attempt: Int): Long {
    val doublings = (attempt.coerceAtLeast(1) - 1).coerceAtMost(MAX_DOUBLINGS)
    val base = (initialMillis shl doublings).coerceAtMost(maxMillis)
    val jitter = (base * jitterRatio * random.nextDouble()).toLong()
    return base - jitter
  }

  companion object {
    /** The first retry waits about 2 seconds. */
    const val DEFAULT_INITIAL_MILLIS = 2_000L

    /** No retry waits longer than a minute. */
    const val DEFAULT_MAX_MILLIS = 60_000L

    /** Jitter shortens each wait by up to a fifth. */
    const val DEFAULT_JITTER_RATIO = 0.2

    /** Enough doublings to pass any cap without overflowing a [Long]. */
    private const val MAX_DOUBLINGS = 30
  }
}
