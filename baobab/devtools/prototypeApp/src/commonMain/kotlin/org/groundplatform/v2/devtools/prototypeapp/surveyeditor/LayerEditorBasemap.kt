/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

/**
 * Whether this platform can render a live basemap (Mapbox GL JS) behind the Compose canvas. When
 * `false` (JVM), the Map layer editor draws a plain grid instead.
 */
internal expect val isLayerEditorBasemapSupported: Boolean

/**
 * Positions the Map layer editor's basemap DOM container at the given window rectangle (CSS px)
 * behind the transparent Compose map viewport and moves its camera to match [MapCamera].
 *
 * The basemap is non-interactive: Compose owns all gestures and renders the features on top, so
 * this is a pure "follow the camera" sync. Implemented by `window.GroundLayerEditorMap` in
 * `layer-editor-map.js`.
 */
internal expect fun syncLayerEditorBasemap(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  centerLat: Double,
  centerLng: Double,
  zoom: Double,
  basemap: String,
)

/** Hides the Map layer editor's basemap container. */
internal expect fun hideLayerEditorBasemap()
