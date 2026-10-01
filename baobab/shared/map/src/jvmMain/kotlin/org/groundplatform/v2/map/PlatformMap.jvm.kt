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
package org.groundplatform.v2.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
