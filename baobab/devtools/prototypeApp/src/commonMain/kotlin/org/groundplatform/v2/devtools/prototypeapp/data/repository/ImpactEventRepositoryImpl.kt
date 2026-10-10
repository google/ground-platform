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

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ImpactEventRepository

/** [ImpactEventRepository] backed by the [LocalStore]. */
class ImpactEventRepositoryImpl(private val store: LocalStore) : ImpactEventRepository {
  override fun observeEvents(): Flow<List<ImpactEvent>> = store.observeImpactEvents()

  override suspend fun getEvents(): List<ImpactEvent> = store.transaction { impactEvents() }

  override suspend fun append(event: ImpactEvent) {
    store.transaction {
      val events = impactEvents()
      if (events.none { it.id == event.id }) putImpactEvents(events + event)
    }
  }

  override suspend fun markAllUploaded(): Int = store.transaction {
    val events = impactEvents()
    val pending = events.count { !it.isUploaded }
    if (pending > 0) putImpactEvents(events.map { it.copy(isUploaded = true) })
    pending
  }
}
