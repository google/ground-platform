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
package org.openforis.collectone.v2.android

import org.openforis.collectone.v2.core.forms.ui.GeoLocationFix

/**
 * A [fix] from one location provider, with when it was taken ([elapsedRealtimeMillis], on the
 * device's monotonic clock) so fixes from different providers can be compared.
 */
internal data class ProviderFix(
  val fix: GeoLocationFix,
  val provider: String,
  val elapsedRealtimeMillis: Long,
)

/**
 * Chooses between fixes from the GNSS receiver and the network provider: a much newer fix wins (the
 * other went stale, e.g. GPS lost indoors), otherwise the more accurate one, and a newer one that
 * isn't less accurate. This is the selection Android's location guide recommends for combining
 * providers without Google Play Services.
 */
internal object LocationFixSelector {
  /** How much newer a fix must be to replace a more accurate one. */
  const val STALE_AFTER_MILLIS = 30_000L

  /** How much less accurate, in meters, a newer fix from the same provider may be and still win. */
  const val SIGNIFICANTLY_LESS_ACCURATE_METERS = 200.0

  /** Whether [candidate] should replace [current] (no fix yet when `null`). */
  fun isBetter(candidate: ProviderFix, current: ProviderFix?): Boolean {
    if (current == null) return true
    val ageDelta = candidate.elapsedRealtimeMillis - current.elapsedRealtimeMillis
    val accuracyDelta = candidate.accuracyMeters - current.accuracyMeters
    val isNewer = ageDelta > 0
    return when {
      ageDelta > STALE_AFTER_MILLIS -> true
      ageDelta < -STALE_AFTER_MILLIS -> false
      accuracyDelta < 0 -> true
      isNewer && accuracyDelta == 0.0 -> true
      else ->
        isNewer &&
          accuracyDelta <= SIGNIFICANTLY_LESS_ACCURATE_METERS &&
          candidate.provider == current.provider
    }
  }

  /** The fix's accuracy radius; a fix without one counts as the least accurate. */
  private val ProviderFix.accuracyMeters: Double
    get() = fix.accuracyMeters ?: Double.MAX_VALUE
}
