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
package org.openforis.collectone.v2.core.forms.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * [GeoLocationSource] backed by the browser's Geolocation API (`navigator.geolocation`), asking for
 * high-accuracy (GNSS) fixes.
 *
 * The browser watches the position only while someone collects [currentFix] (a location question is
 * on screen), launched in [scope]. The fix is cleared when the browser reports an error (such as a
 * denied permission or no signal) and when the watch stops, so a stale fix is never recorded.
 *
 * Geolocation needs a secure context (HTTPS or `localhost`); elsewhere there's never a fix.
 */
class BrowserGeoLocationSource(scope: CoroutineScope) : GeoLocationSource {
  override val currentFix: StateFlow<GeoLocationFix?> = callbackFlow {
    val watchId =
      watchBrowserPosition(
        onPosition = { latitude, longitude, altitude, accuracy ->
          trySend(browserGeoLocationFix(latitude, longitude, altitude, accuracy))
        },
        onError = { trySend(null) },
      )
    awaitClose { if (watchId != NO_WATCH) clearBrowserPositionWatch(watchId) }
  }
    .stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
}

/** The watch id [watchBrowserPosition] returns when the browser has no Geolocation API. */
private const val NO_WATCH = -1

/**
 * Starts watching the device position, calling [onPosition] with each reading's latitude,
 * longitude, altitude, and accuracy (`NaN` when the browser doesn't report one) and [onError] on
 * each failure. Returns the id to pass to [clearBrowserPositionWatch], or [NO_WATCH] when the
 * browser has no Geolocation API (after calling [onError]).
 */
@JsFun(
  """(onPosition, onError) => {
    if (!navigator.geolocation) {
      onError();
      return -1;
    }
    return navigator.geolocation.watchPosition(
      (position) => {
        const c = position.coords;
        onPosition(c.latitude, c.longitude, c.altitude ?? NaN, c.accuracy ?? NaN);
      },
      () => onError(),
      { enableHighAccuracy: true },
    );
  }"""
)
private external fun watchBrowserPosition(
  onPosition: (Double, Double, Double, Double) -> Unit,
  onError: () -> Unit,
): Int

@JsFun("(watchId) => navigator.geolocation.clearWatch(watchId)")
private external fun clearBrowserPositionWatch(watchId: Int)
