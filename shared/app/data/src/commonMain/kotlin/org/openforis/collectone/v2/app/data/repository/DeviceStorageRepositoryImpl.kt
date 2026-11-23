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
package org.openforis.collectone.v2.app.data.repository

import co.touchlab.kermit.Logger
import org.openforis.collectone.v2.app.client.storage.DeviceStorageClient
import org.openforis.collectone.v2.app.client.storage.DeviceStorageSpace
import org.openforis.collectone.v2.app.data.error.domainResultOf
import org.openforis.collectone.v2.app.domain.error.getOrNull
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.repository.DeviceStorageRepository

private val log = Logger.withTag("DeviceStorageRepository")

/**
 * [DeviceStorageRepository] that measures the device through a [DeviceStorageClient] on every call
 * (free space changes as anything on the device writes). A failed measurement reads as unknown.
 */
class DeviceStorageRepositoryImpl(private val client: DeviceStorageClient) :
  DeviceStorageRepository {
  override suspend fun freeSpace(): DataSize? = space()?.let { DataSize(it.freeBytes) }

  override suspend fun totalSpace(): DataSize? = space()?.let { DataSize(it.totalBytes) }

  private suspend fun space(): DeviceStorageSpace? {
    val space = domainResultOf { client.space() }.getOrNull()
    if (space == null) log.d { "Device storage can't be measured" }
    return space
  }
}
