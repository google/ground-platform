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

import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SampleDataSeeder
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SampleDataRepository

/** [SampleDataRepository] that seeds the [LocalStore] via [SampleDataSeeder]. */
class SampleDataRepositoryImpl(store: LocalStore) : SampleDataRepository {
  private val seeder = SampleDataSeeder(store)

  override suspend fun seedIfNeeded() = seeder.seedIfNeeded()

  override suspend fun resetToSampleData() = seeder.reset()
}
