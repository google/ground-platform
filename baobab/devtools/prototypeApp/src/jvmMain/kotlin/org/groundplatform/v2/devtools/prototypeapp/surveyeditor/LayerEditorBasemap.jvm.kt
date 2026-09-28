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

internal actual val isLayerEditorBasemapSupported: Boolean = false

internal actual fun syncLayerEditorBasemap(
  leftPx: Float,
  topPx: Float,
  widthPx: Float,
  heightPx: Float,
  borderRadiusPx: Float,
  centerLat: Double,
  centerLng: Double,
  zoom: Double,
  basemap: String,
) = Unit

internal actual fun hideLayerEditorBasemap() = Unit
