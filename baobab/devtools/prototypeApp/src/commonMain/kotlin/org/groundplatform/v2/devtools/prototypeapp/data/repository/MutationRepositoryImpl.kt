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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository

/** [MutationRepository] backed by the [LocalStore]. */
class MutationRepositoryImpl(private val store: LocalStore) : MutationRepository {
  override fun observeMutations(): Flow<List<MutationLogItem>> = store.observeMutations()

  override suspend fun getMutations(): List<MutationLogItem> = store.transaction { mutations() }

  override suspend fun setMutations(mutations: List<MutationLogItem>) {
    store.transaction { putMutations(mutations) }
  }

  override suspend fun prependMutations(newMutations: List<MutationLogItem>) {
    store.transaction { putMutations(newMutations + mutations()) }
  }

  override suspend fun updateMutation(
    mutationId: String,
    transform: (MutationLogItem) -> MutationLogItem,
  ) {
    store.transaction {
      putMutations(mutations().map { if (it.id == mutationId) transform(it) else it })
    }
  }
}
