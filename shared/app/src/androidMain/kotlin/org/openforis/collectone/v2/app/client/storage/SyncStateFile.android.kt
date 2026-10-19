/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.io.File

/** A file in the app's private files directory, whose path `MainActivity` sets. */
actual fun platformSyncStateFile(): SyncStateFile? =
  System.getProperty(SYNC_STATE_FILE_PROPERTY)
    ?.takeIf { it.isNotBlank() }
    ?.let {
      AndroidSyncStateFile(File(it))
    }

/** [SyncStateFile] in a local file, replaced atomically through a temporary file. */
private class AndroidSyncStateFile(private val file: File) : SyncStateFile {
  override fun read(): ByteArray? = file.takeIf { it.isFile }?.readBytes()

  override fun write(bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val temp = File(file.path + ".tmp")
    temp.writeBytes(bytes)
    if (!temp.renameTo(file)) {
      file.delete()
      check(temp.renameTo(file)) { "Couldn't replace $file" }
    }
  }
}
