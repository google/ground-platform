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
package org.openforis.collectone.v2.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compose Desktop reports mouse wheel scroll in notches. */
internal actual const val SCROLL_DELTA_PER_WHEEL_NOTCH = 1f

/** Desktop/JVM has no native map SDK; the Canvas [PreviewMap] is the renderer. */
@Composable
internal actual fun PlatformMap(
  content: MapContent,
  cameraState: MapCameraState,
  onEvent: (MapEvent) -> Unit,
  gestures: MapGestureOptions,
  modifier: Modifier,
) {
  PreviewMap(content, cameraState, onEvent, gestures, modifier)
}
