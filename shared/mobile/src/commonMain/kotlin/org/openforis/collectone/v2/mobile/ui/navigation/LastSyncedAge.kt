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
package org.openforis.collectone.v2.mobile.ui.navigation

import kotlin.time.Instant

/** How long ago the last successful sync finished, in the largest whole unit. */
internal sealed interface LastSyncedAge {
  /** Less than a minute ago (or a clock that moved backwards). */
  data object JustNow : LastSyncedAge

  data class Minutes(val count: Int) : LastSyncedAge

  data class Hours(val count: Int) : LastSyncedAge

  data class Days(val count: Int) : LastSyncedAge
}

/** The age at [now] of a sync that finished at [lastSyncedAt]. */
internal fun lastSyncedAge(lastSyncedAt: Instant, now: Instant): LastSyncedAge {
  val minutes = (now - lastSyncedAt).inWholeMinutes
  return when {
    minutes < 1 -> LastSyncedAge.JustNow
    minutes < MINUTES_PER_HOUR -> LastSyncedAge.Minutes(minutes.toInt())
    minutes < MINUTES_PER_DAY -> LastSyncedAge.Hours((minutes / MINUTES_PER_HOUR).toInt())
    else -> LastSyncedAge.Days((minutes / MINUTES_PER_DAY).toInt())
  }
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
