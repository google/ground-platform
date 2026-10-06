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
package org.groundplatform.v2.devtools.prototypeapp

import org.groundplatform.v2.core.forms.media.MediaCaptureKind

@JsFun("() => typeof window !== 'undefined' && !!window.GroundMediaCaptureBridge")
private external fun jsHasMediaCaptureBridge(): Boolean

@JsFun(
  "() => typeof window !== 'undefined' && !!window.GroundMediaCaptureBridge && " +
    "!!window.GroundMediaCaptureBridge.isLiveCaptureSupported && " +
    "window.GroundMediaCaptureBridge.isLiveCaptureSupported()"
)
private external fun jsIsLiveCaptureSupported(): Boolean

@JsFun(
  "() => typeof window !== 'undefined' && !!window.GroundMediaCaptureBridge && " +
    "!!window.GroundMediaCaptureBridge.prefersFileInputCapture && " +
    "window.GroundMediaCaptureBridge.prefersFileInputCapture()"
)
private external fun jsPrefersFileInputCapture(): Boolean

@JsFun("() => Date.now()") private external fun jsDateNow(): Double

@JsFun(
  "(accept, capture, maxPixels, callback) => " +
    "window.GroundMediaCaptureBridge.open(accept, capture, maxPixels, callback)"
)
private external fun jsOpenMediaPicker(
  accept: String,
  capture: String,
  maxPixels: Int,
  callback: (String, String, String, Double, Double, Double, String) -> Unit,
)

@JsFun(
  "(kind, facing, maxPixels, callback) => " +
    "window.GroundMediaCaptureBridge.openLive(kind, facing, maxPixels, callback)"
)
private external fun jsOpenLiveCapture(
  kind: String,
  facing: String,
  maxPixels: Int,
  callback: (String, String, String, Double, Double, Double, String) -> Unit,
)

internal actual val isPlatformMediaPickerAvailable: Boolean
  get() = jsHasMediaCaptureBridge()

internal actual val isPlatformLiveCaptureAvailable: Boolean
  get() = jsIsLiveCaptureSupported()

internal actual val platformPrefersFileInputCapture: Boolean
  get() = jsPrefersFileInputCapture()

internal actual fun platformEpochMillis(): Long = jsDateNow().toLong()

internal actual fun openPlatformMediaPicker(
  accept: String,
  capture: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
) {
  if (!jsHasMediaCaptureBridge()) {
    onResult(PlatformPickResult.Failed("Media capture bridge (media-capture-bridge.js) not loaded"))
    return
  }
  jsOpenMediaPicker(accept, capture, maxPixels) {
    fileName,
    mimeType,
    base64,
    durationMs,
    widthPx,
    heightPx,
    error ->
    onResult(browserPickResult(fileName, mimeType, base64, durationMs, widthPx, heightPx, error))
  }
}

internal actual fun openPlatformLiveCapture(
  kind: MediaCaptureKind,
  facing: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
) {
  if (!jsHasMediaCaptureBridge()) {
    onResult(PlatformPickResult.Failed("Media capture bridge (media-capture-bridge.js) not loaded"))
    return
  }
  val jsKind =
    when (kind) {
      MediaCaptureKind.PHOTO -> "photo"
      MediaCaptureKind.VIDEO -> "video"
      MediaCaptureKind.AUDIO -> "audio"
      MediaCaptureKind.FILE -> {
        onResult(PlatformPickResult.Failed("Generic files can't be captured live."))
        return
      }
    }
  jsOpenLiveCapture(jsKind, facing, maxPixels) {
    fileName,
    mimeType,
    base64,
    durationMs,
    widthPx,
    heightPx,
    error ->
    onResult(browserPickResult(fileName, mimeType, base64, durationMs, widthPx, heightPx, error))
  }
}
