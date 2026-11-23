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

/** The device's storage in bytes: [freeBytes] the app may still use, of [totalBytes] in all. */
data class DeviceStorageSpace(val freeBytes: Long, val totalBytes: Long)

/** Measures the device's storage. */
fun interface DeviceStorageClient {
  /** The storage space now, or null when this platform can't measure it. */
  suspend fun space(): DeviceStorageSpace?
}

/**
 * This platform's [DeviceStorageClient]: the data partition on Android, the origin's storage quota
 * in the browser (`navigator.storage.estimate()`), the home directory's file system on the JVM, and
 * the app's file system on iOS.
 */
expect fun defaultDeviceStorageClient(): DeviceStorageClient
