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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.ui.session.RequireUserDataService
import org.openforis.collectone.v2.core.forms.ui.LocalGeoLocationSource
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.services.MonitorDeviceConnectivity
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.permissions.LocationPermissionRationaleDialog

/**
 * The Android app's only activity. Hosts the shared mobile app ([MobileAppSession] over the
 * process-wide graph of [MobileSyncRuntime]) and owns what needs an activity: the location
 * permission, asked when the first location question opens rather than at launch, and the GNSS
 * updates, which run only while a location question is on screen and the app is visible.
 */
class MainActivity : ComponentActivity() {
  private val geoLocationSource by lazy { AndroidGeoLocationSource(this, lifecycleScope) }

  /** Whether the app asked for location permission (or explained why) in this session. */
  private var hasAskedForLocation = false

  /** Whether the location rationale is showing, before asking again a user who denied it. */
  private var isLocationRationaleShowing by mutableStateOf(false)

  private val locationPermissionRequest =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      if (geoLocationSource.isLocationNeeded.value) geoLocationSource.start()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    MobileSyncRuntime.initialize(this)
    enableEdgeToEdge()
    hasAskedForLocation = savedInstanceState?.getBoolean(HAS_ASKED_FOR_LOCATION) ?: false
    followLocationQuestions()
    setContent {
      CompositionLocalProvider(LocalGeoLocationSource provides geoLocationSource) {
        RequireUserDataService { serviceUrl ->
          val dataHolder =
            remember(serviceUrl) {
              requireNotNull(MobileSyncRuntime.getOrCreateDataHolder(this, serviceUrl))
            }
          MobileAppSession(dataHolder) { state ->
            MainActivityContent(state)
            if (isLocationRationaleShowing) {
              LocationPermissionRationaleDialog(
                state = state,
                onContinue = {
                  isLocationRationaleShowing = false
                  requestLocationPermission()
                },
                onDismiss = { isLocationRationaleShowing = false },
              )
            }
          }
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(HAS_ASKED_FOR_LOCATION, hasAskedForLocation)
  }

  /**
   * Receives location fixes while a location question is on screen and the app is visible, asking
   * for the permission when the first one opens (rather than at launch).
   */
  private fun followLocationQuestions() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        try {
          geoLocationSource.isLocationNeeded.collect { isNeeded ->
            if (isNeeded) {
              askForLocationIfNeeded()
              geoLocationSource.start()
            } else {
              geoLocationSource.stop()
            }
          }
        } finally {
          geoLocationSource.stop()
        }
      }
    }
  }

  private fun askForLocationIfNeeded() {
    val step =
      LocationPermissionStep.of(
        isGranted = geoLocationSource.hasLocationPermission(),
        hasAsked = hasAskedForLocation,
        shouldShowRationale = shouldShowRequestPermissionRationale(FINE_LOCATION),
      )
    when (step) {
      LocationPermissionStep.NONE -> Unit
      LocationPermissionStep.REQUEST -> requestLocationPermission()
      LocationPermissionStep.SHOW_RATIONALE -> isLocationRationaleShowing = true
    }
    if (step != LocationPermissionStep.NONE) hasAskedForLocation = true
  }

  // Android 12+ ignores a precise location request that doesn't also ask for approximate location.
  private fun requestLocationPermission() {
    locationPermissionRequest.launch(
      arrayOf(FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )
  }

  private companion object {
    const val HAS_ASKED_FOR_LOCATION = "hasAskedForLocation"
    const val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
  }
}

/**
 * The mobile app ([MobileApp]) fed with the device's connectivity, which the shell turns into the
 * offline and back-online notices.
 */
@Composable
fun MainActivityContent(state: MobileAppState) {
  val context = LocalContext.current
  val connectivityMonitor = remember(context) { AndroidConnectivityMonitor(context) }
  MonitorDeviceConnectivity(state, connectivityMonitor)

  MobileApp(state)
}
