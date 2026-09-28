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

internal actual val isPlatformMediaPickerAvailable: Boolean
  get() = js("typeof window !== 'undefined' && !!window.GroundMediaCaptureBridge") as Boolean

internal actual fun platformEpochMillis(): Long = (js("Date.now()") as Double).toLong()

internal actual fun openPlatformMediaPicker(
  accept: String,
  capture: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
) {
  val bridge = js("window.GroundMediaCaptureBridge")
  if (bridge == null || bridge == undefined) {
    onResult(PlatformPickResult.Failed("Media capture bridge (media-capture-bridge.js) not loaded"))
    return
  }
  val callback =
    {
      fileName: String,
      mimeType: String,
      base64: String,
      durationMs: Double,
      widthPx: Double,
      heightPx: Double,
      error: String ->
      onResult(browserPickResult(fileName, mimeType, base64, durationMs, widthPx, heightPx, error))
    }
  bridge.open(accept, capture, maxPixels, callback)
}
