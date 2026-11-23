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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber

/** The file system of the app's sandbox. */
@OptIn(ExperimentalForeignApi::class)
actual fun defaultDeviceStorageClient(): DeviceStorageClient = DeviceStorageClient {
  val attributes =
    NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), error = null)
  val free = (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
  val total = (attributes?.get(NSFileSystemSize) as? NSNumber)?.longLongValue
  if (free == null || total == null) null else DeviceStorageSpace(free, total)
}
