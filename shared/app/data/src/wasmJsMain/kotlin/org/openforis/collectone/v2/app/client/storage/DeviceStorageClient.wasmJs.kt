/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.client.storage

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * The origin's storage quota, from `navigator.storage.estimate()`: what the browser lets this site
 * store (free = quota − usage). Null in browsers without the Storage API.
 */
actual fun defaultDeviceStorageClient(): DeviceStorageClient = DeviceStorageClient {
  parseStorageEstimate(jsStorageEstimate().await<JsString?>()?.toString())
}

/** Reads `"<quota>,<usage>"` from [jsStorageEstimate]; null when it is empty or malformed. */
internal fun parseStorageEstimate(text: String?): DeviceStorageSpace? {
  val (quota, usage) =
    text?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 } ?: return null
  if (quota <= 0) return null
  return DeviceStorageSpace(
    freeBytes = (quota - usage).toLong().coerceAtLeast(0),
    totalBytes = quota.toLong(),
  )
}

/**
 * Resolves to `"<quota>,<usage>"` in bytes, or an empty string when the estimate isn't available.
 */
@JsFun(
  """() => (navigator.storage && navigator.storage.estimate)
    ? navigator.storage.estimate()
        .then((e) => (e.quota ? String(e.quota) + ',' + String(e.usage || 0) : ''))
        .catch(() => '')
    : Promise.resolve('')"""
)
private external fun jsStorageEstimate(): Promise<JsString?>
