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

@JsFun("() => typeof window !== 'undefined' && !!window.GroundMediaCaptureBridge")
private external fun jsHasMediaCaptureBridge(): Boolean

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

internal actual val isPlatformMediaPickerAvailable: Boolean
  get() = jsHasMediaCaptureBridge()

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
