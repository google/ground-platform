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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openforis.collectone.v2.core.forms.ui.GeoLocationFix
import org.openforis.collectone.v2.core.forms.ui.GeoLocationSource

/**
 * [GeoLocationSource] backed by the platform's [LocationManager], without Google Play Services: the
 * GNSS receiver ([LocationManager.GPS_PROVIDER]) for precise fixes and the network provider
 * ([LocationManager.NETWORK_PROVIDER], Wi-Fi and cell towers) for a quick rough fix until GPS locks
 * or when it can't (indoors). [LocationFixSelector] keeps the better and fresher of their fixes.
 *
 * Google's fused location provider would be faster and more battery-aware, but it needs Google Play
 * Services, which some field devices and open-source distributions don't have; it could be offered
 * later as an optional build variant behind this same interface.
 *
 * Reports no fix until the user grants location permission (precise for GPS; approximate is enough
 * for the network provider), a provider is enabled, and it produces a fix. The host calls [start]
 * while a location question is on screen ([isLocationNeeded]) and the app is visible, and [stop]
 * otherwise.
 */
class AndroidGeoLocationSource(context: Context, scope: CoroutineScope) : GeoLocationSource {
  private val appContext = context.applicationContext
  private val locationManager = appContext.getSystemService(LocationManager::class.java)
  private val fix = MutableStateFlow<GeoLocationFix?>(null)
  private var bestFix: ProviderFix? = null

  override val currentFix: StateFlow<GeoLocationFix?> = fix.asStateFlow()

  /** Whether a location question is on screen: something collects [currentFix]. */
  val isLocationNeeded: StateFlow<Boolean> =
    fix.subscriptionCount.map { it > 0 }.stateIn(scope, SharingStarted.Eagerly, false)

  private val listeners = PROVIDERS.associateWith { provider -> ProviderListener(provider) }

  /** Whether the user has granted location permission, precise or approximate. */
  fun hasLocationPermission(): Boolean = hasPreciseLocation() || isGranted(COARSE)

  private fun hasPreciseLocation(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

  private fun isGranted(permission: String) =
    appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

  /**
   * Starts receiving fixes from every provider the device has and the granted permission allows.
   * Safe to call again, e.g. after permission is granted.
   */
  @SuppressLint("MissingPermission") // Checked by hasLocationPermission() and hasPreciseLocation().
  fun start() {
    if (locationManager == null || !hasLocationPermission()) return
    for ((provider, listener) in listeners) {
      if (provider !in locationManager.allProviders) continue
      if (provider == LocationManager.GPS_PROVIDER && !hasPreciseLocation()) continue
      // Re-registering the same listener replaces its previous request.
      locationManager.requestLocationUpdates(
        provider,
        UPDATE_INTERVAL_MILLIS,
        /* minDistanceM= */ 0f,
        listener,
        Looper.getMainLooper(),
      )
    }
  }

  /** Stops receiving fixes and forgets the last one, which would go stale. */
  fun stop() {
    listeners.values.forEach { locationManager?.removeUpdates(it) }
    bestFix = null
    fix.value = null
  }

  private fun onFix(candidate: ProviderFix) {
    if (LocationFixSelector.isBetter(candidate, bestFix)) {
      bestFix = candidate
      fix.value = candidate.fix
    }
  }

  /** Drops the current fix if [provider], now disabled, reported it. */
  private fun forgetFixFrom(provider: String) {
    if (bestFix?.provider == provider) {
      bestFix = null
      fix.value = null
    }
  }

  /** Receives one provider's fixes. Overrides every callback: they're default only from API 30. */
  private inner class ProviderListener(private val provider: String) : LocationListener {
    override fun onLocationChanged(location: Location) {
      onFix(
        ProviderFix(
          location.toGeoLocationFix(),
          provider,
          location.elapsedRealtimeNanos / NANOS_PER_MILLI,
        )
      )
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) = forgetFixFrom(this.provider)

    @Deprecated("Deprecated in Android API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
  }

  private companion object {
    const val UPDATE_INTERVAL_MILLIS = 1_000L
    const val NANOS_PER_MILLI = 1_000_000L
    const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
    val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
  }
}

private fun Location.toGeoLocationFix(): GeoLocationFix =
  GeoLocationFix(
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = if (hasAltitude()) altitude else null,
    accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
  )
