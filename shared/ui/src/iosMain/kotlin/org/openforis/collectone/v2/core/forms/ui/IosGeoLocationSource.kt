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
package org.openforis.collectone.v2.core.forms.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [GeoLocationSource] backed by iOS `CLLocationManager` (`CoreLocation`).
 *
 * Starts location updates while [currentFix] is collected and stops them when no collector remains,
 * requesting When-In-Use authorization if it hasn't been determined yet.
 */
@OptIn(ExperimentalForeignApi::class)
class IosGeoLocationSource(scope: CoroutineScope) : GeoLocationSource {
  override val currentFix: StateFlow<GeoLocationFix?> = callbackFlow {
    val manager = CLLocationManager()
    val delegate =
      object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(
          manager: CLLocationManager,
          didUpdateLocations: List<*>,
        ) {
          val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
          val (lat, lon) = location.coordinate.useContents { latitude to longitude }
          val accuracy = location.horizontalAccuracy.takeIf { it >= 0.0 }
          val altitude = if (location.verticalAccuracy >= 0.0) location.altitude else null
          trySend(
            GeoLocationFix(
              latitude = lat,
              longitude = lon,
              altitudeMeters = altitude,
              accuracyMeters = accuracy,
            )
          )
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
          trySend(null)
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
          if (isAuthorized(manager.authorizationStatus)) {
            manager.startUpdatingLocation()
          } else {
            trySend(null)
          }
        }
      }

    dispatch_async(dispatch_get_main_queue()) {
      manager.desiredAccuracy = kCLLocationAccuracyBest
      manager.delegate = delegate
      val status = manager.authorizationStatus
      when {
        isAuthorized(status) -> manager.startUpdatingLocation()
        status == kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
        else -> trySend(null)
      }
    }

    awaitClose {
      dispatch_async(dispatch_get_main_queue()) {
        manager.stopUpdatingLocation()
        manager.delegate = null
      }
    }
  }
    .stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), null)
}

private fun isAuthorized(status: CLAuthorizationStatus): Boolean =
  status == kCLAuthorizationStatusAuthorizedWhenInUse ||
    status == kCLAuthorizationStatusAuthorizedAlways
