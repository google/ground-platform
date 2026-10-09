/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.device.DeviceNetworkDataSource
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository

/** Default implementation of [ConnectivityRepository] backed by [DeviceNetworkDataSource]. */
class ConnectivityRepositoryImpl(
  private val dataSource: DeviceNetworkDataSource = DeviceNetworkDataSource()
) : ConnectivityRepository {
  override fun observeIsOnline(): StateFlow<Boolean> = dataSource.isOnline

  override fun isOnline(): Boolean = dataSource.isOnline()

  override fun setOnline(online: Boolean) {
    dataSource.setOnline(online)
  }

  override fun reset() {
    dataSource.reset()
  }
}
