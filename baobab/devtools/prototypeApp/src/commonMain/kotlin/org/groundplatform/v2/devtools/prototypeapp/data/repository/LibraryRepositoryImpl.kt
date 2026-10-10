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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository

/** [LibraryRepository] backed by the [LocalStore], one library per organization. */
class LibraryRepositoryImpl(private val store: LocalStore) : LibraryRepository {
  override fun observeLibraries(): Flow<Map<String, OrganizationLibrary>> = store.observeLibraries()

  override fun observeLibrary(organizationId: String): Flow<OrganizationLibrary> =
    store
      .observeLibraries()
      .map { it[organizationId] ?: OrganizationLibrary(organizationId) }
      .distinctUntilChanged()

  override suspend fun getLibrary(organizationId: String): OrganizationLibrary = store.transaction {
    library(organizationId) ?: OrganizationLibrary(organizationId)
  }

  override suspend fun updateLibrary(
    organizationId: String,
    transform: (OrganizationLibrary) -> OrganizationLibrary,
  ): OrganizationLibrary? = store.transaction {
    if (organization(organizationId) == null) return@transaction null
    val existing = library(organizationId) ?: OrganizationLibrary(organizationId)
    val updated = transform(existing).copy(organizationId = organizationId)
    if (updated.integrityError() != null) return@transaction null
    putLibrary(updated)
    updated
  }
}
