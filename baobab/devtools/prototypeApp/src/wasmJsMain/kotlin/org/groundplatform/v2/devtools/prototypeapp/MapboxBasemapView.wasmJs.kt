/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.devtools.prototypeapp

@JsFun(
  "(leftPx, topPx, widthPx, heightPx, borderRadiusPx, visible) => { " +
    "if (window.GroundMapboxBridge) { " +
    "window.GroundMapboxBridge.syncViewport(leftPx, topPx, widthPx, heightPx, borderRadiusPx, visible); " +
    "} }"
)
private external fun jsSyncMapboxViewport(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  visible: Boolean,
)

@JsFun(
  "(surveyId, basemapType, isOfflineVisible, panOffsetX, panOffsetY, userGpsX, userGpsY, featuresGeoJson) => { " +
    "if (window.GroundMapboxBridge) { " +
    "window.GroundMapboxBridge.syncState(surveyId, basemapType, isOfflineVisible, panOffsetX, panOffsetY, userGpsX, userGpsY, featuresGeoJson); " +
    "} }"
)
private external fun jsSyncMapboxBasemap(
  surveyId: String,
  basemapType: String,
  isOfflineVisible: Boolean,
  panOffsetX: Float,
  panOffsetY: Float,
  userGpsX: Float,
  userGpsY: Float,
  featuresGeoJson: String,
)

@JsFun(
  "(dxPx, dyPx) => { " +
    "if (window.GroundMapboxBridge) { " +
    "window.GroundMapboxBridge.panByPixels(dxPx, dyPx); " +
    "} }"
)
private external fun jsPanMapboxBasemap(dxPx: Float, dyPx: Float)

@JsFun(
  "(deltaZoom) => { " +
    "if (window.GroundMapboxBridge && window.GroundMapboxBridge.zoomBy) { " +
    "window.GroundMapboxBridge.zoomBy(deltaZoom); " +
    "} }"
)
private external fun jsZoomMapboxBasemap(deltaZoom: Float)

@JsFun(
  "(xPx, yPx) => { " +
    "if (window.GroundMapboxBridge && window.GroundMapboxBridge.handleMapClick) { " +
    "return String(window.GroundMapboxBridge.handleMapClick(xPx, yPx) || ''); " +
    "} " +
    "return ''; }"
)
private external fun jsHandleMapboxClick(xPx: Float, yPx: Float): String

@JsFun(
  "() => { " +
    "if (window.GroundMapboxBridge) { " +
    "window.GroundMapboxBridge.syncViewport(0, 0, 0, 0, 0, false); " +
    "} }"
)
private external fun jsHideMapboxBasemap()

internal actual fun syncPlatformMapboxViewport(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  visible: Boolean,
) {
  jsSyncMapboxViewport(leftPx, topPx, widthPx, heightPx, borderRadiusPx, visible)
}

internal actual fun syncPlatformMapboxBasemap(
  surveyId: String,
  basemapType: String,
  isOfflineVisible: Boolean,
  panOffsetX: Float,
  panOffsetY: Float,
  userGpsX: Float,
  userGpsY: Float,
  featuresGeoJson: String,
) {
  jsSyncMapboxBasemap(
    surveyId,
    basemapType,
    isOfflineVisible,
    panOffsetX,
    panOffsetY,
    userGpsX,
    userGpsY,
    featuresGeoJson,
  )
}

internal actual fun panPlatformMapboxBasemap(dxPx: Float, dyPx: Float) {
  jsPanMapboxBasemap(dxPx, dyPx)
}

internal actual fun zoomPlatformMapboxBasemap(deltaZoom: Float) {
  jsZoomMapboxBasemap(deltaZoom)
}

internal actual fun handlePlatformMapboxClick(xPx: Float, yPx: Float): String =
  jsHandleMapboxClick(xPx, yPx)

internal actual fun hidePlatformMapboxBasemap() {
  jsHideMapboxBasemap()
}
