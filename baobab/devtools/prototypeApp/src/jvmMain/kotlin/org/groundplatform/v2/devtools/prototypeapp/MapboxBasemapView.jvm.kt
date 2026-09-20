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

internal actual fun syncPlatformMapboxViewport(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  visible: Boolean,
) = Unit

internal actual fun syncPlatformMapboxBasemap(
  surveyId: String,
  basemapType: String,
  isOfflineVisible: Boolean,
  panOffsetX: Float,
  panOffsetY: Float,
  userGpsX: Float,
  userGpsY: Float,
  featuresGeoJson: String,
) = Unit

internal actual fun panPlatformMapboxBasemap(dxPx: Float, dyPx: Float) = Unit

internal actual fun zoomPlatformMapboxBasemap(deltaZoom: Float) = Unit

internal actual fun handlePlatformMapboxClick(xPx: Float, yPx: Float): String = ""

internal actual fun hidePlatformMapboxBasemap() = Unit
