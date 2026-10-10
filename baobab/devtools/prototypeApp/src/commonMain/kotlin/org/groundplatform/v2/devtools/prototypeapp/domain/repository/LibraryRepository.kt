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
package org.groundplatform.v2.devtools.prototypeapp.domain.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary

/**
 * Domain repository contract for organization libraries (concepts, Form templates, Purpose Packs,
 * export profiles, and library settings), keyed by organization ID. The `"All users"`
 * organization's library is the global library.
 *
 * An organization with no stored library has an empty one.
 */
interface LibraryRepository {
  /** Every stored library, keyed by organization ID. */
  fun observeLibraries(): Flow<Map<String, OrganizationLibrary>>

  /** The library of [organizationId] (empty if none is stored). */
  fun observeLibrary(organizationId: String): Flow<OrganizationLibrary>

  suspend fun getLibrary(organizationId: String): OrganizationLibrary

  /**
   * Replaces the library of [organizationId] with `transform(existing)`. Returns the stored result,
   * or `null` (leaving the library unchanged) if the organization doesn't exist or the result
   * breaks the ownership rules ([OrganizationLibrary.integrityError]).
   */
  suspend fun updateLibrary(
    organizationId: String,
    transform: (OrganizationLibrary) -> OrganizationLibrary,
  ): OrganizationLibrary?
}
