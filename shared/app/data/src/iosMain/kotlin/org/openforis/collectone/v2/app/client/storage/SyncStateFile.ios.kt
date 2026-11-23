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
package org.openforis.collectone.v2.app.client.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** `sync_state.binpb` in the iOS app's `Documents` directory, from before the local database. */
actual fun platformSyncStateFile(): SyncStateFile? {
  val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
  val docsDir = (dirs.firstOrNull() as? String)?.takeIf { it.isNotBlank() } ?: return null
  return IosSyncStateFile("$docsDir/sync_state.binpb")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosSyncStateFile(private val path: String) : SyncStateFile {
  override fun read(): ByteArray? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    return bytes
  }

  override fun delete() {
    if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
      NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
  }
}
