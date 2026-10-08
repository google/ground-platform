/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.common

import org.groundplatform.v2.core.forms.media.MediaCaptureKind

/** The JVM target backs unit tests only; capture falls back to simulated media. */
internal actual val isPlatformMediaPickerAvailable: Boolean = false

internal actual val isPlatformLiveCaptureAvailable: Boolean = false

internal actual val platformPrefersFileInputCapture: Boolean = false

internal actual fun platformEpochMillis(): Long = System.currentTimeMillis()

internal actual fun openPlatformMediaPicker(
  accept: String,
  capture: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
) {
  onResult(PlatformPickResult.Failed("No camera or file picker on this platform"))
}

internal actual fun openPlatformLiveCapture(
  kind: MediaCaptureKind,
  facing: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
) {
  onResult(PlatformPickResult.Failed("No camera or microphone on this platform"))
}
