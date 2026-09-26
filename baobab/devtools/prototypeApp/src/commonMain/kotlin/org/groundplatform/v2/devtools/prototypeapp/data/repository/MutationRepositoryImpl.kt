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

import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository

/**
 * Concrete [MutationRepository] implementation backed by [PrototypeAppDataStore].
 */
class MutationRepositoryImpl(private val dataStore: PrototypeAppDataStore) : MutationRepository {
  override fun getMutations(): List<MutationLogItem> = dataStore.mutations

  override fun setMutations(mutations: List<MutationLogItem>) {
    dataStore.mutations = mutations
  }

  override fun prependMutations(newMutations: List<MutationLogItem>) {
    dataStore.mutations = newMutations + dataStore.mutations
  }

  override fun updateMutation(mutationId: String, transform: (MutationLogItem) -> MutationLogItem) {
    dataStore.mutations =
      dataStore.mutations.map { item -> if (item.id == mutationId) transform(item) else item }
  }

  override fun resetToDefaults() {
    dataStore.mutations = PrototypeAppDataStore.defaultMutations()
  }
}
