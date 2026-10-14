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
package org.groundplatform.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.seededStore
import org.groundplatform.v2.app.data.seed.GlobalLibrarySeedData
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.domain.model.ConceptDataType
import org.groundplatform.v2.app.domain.model.LibraryConcept
import org.groundplatform.v2.app.domain.model.LocalizedText
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.OrganizationLibrary

class LibraryRepositoryTest {
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE
  private val mekong = SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE

  private fun concept(id: String, owner: String) =
    LibraryConcept(id, owner, LocalizedText.en(id), ConceptDataType.TEXT)

  @Test
  fun seededStore_holdsTheGlobalLibraryAndSampleOrganizationLibraries() = runNow {
    val repo = LibraryRepositoryImpl(seededStore())
    val global = repo.getLibrary(Organization.ALL_USERS_ID)
    assertTrue(global.isGlobal)
    assertEquals(GlobalLibrarySeedData.library(), global)
    assertTrue(global.concepts.size in 60..75, "about 67 global concepts")
    assertEquals(
      listOf(
        "producer_registration",
        "eudr_due_diligence",
        "ferm_restoration",
        "protected_area_management",
        "iplc_land_mapping",
        "lulc_training_data",
        "redd_plus_tfff",
        "timber_traceability",
      ),
      global.purposePacks.map { it.id },
    )
    assertNull(global.integrityError())
    val kenya = repo.getLibrary(kfs)
    assertEquals(2, kenya.concepts.size)
    assertNull(kenya.integrityError())
    // Organizations without a stored library get an empty one.
    assertEquals(OrganizationLibrary(mekong), repo.getLibrary(mekong))
  }

  @Test
  fun updateLibrary_storesValidChanges_perOrganization() = runNow {
    val store = seededStore()
    val repo = LibraryRepositoryImpl(store)
    val id = "org.org-mekong-mangrove-alliance.seedling_count"
    val updated =
      repo.updateLibrary(mekong) { it.copy(concepts = it.concepts + concept(id, mekong)) }
    assertNotNull(updated)
    assertEquals(listOf(id), repo.getLibrary(mekong).concepts.map { it.id })
    // Other libraries are untouched.
    assertEquals(2, repo.getLibrary(kfs).concepts.size)
  }

  @Test
  fun updateLibrary_refusesOwnershipViolations_andUnknownOrganizations() = runNow {
    val repo = LibraryRepositoryImpl(seededStore())
    // Shadowing a global ID.
    assertNull(
      repo.updateLibrary(kfs) { it.copy(concepts = it.concepts + concept("eudr.commodity", kfs)) }
    )
    // Another organization's prefix.
    assertNull(
      repo.updateLibrary(kfs) {
        it.copy(concepts = it.concepts + concept("org.org-mekong-mangrove-alliance.x", kfs))
      }
    )
    // Owned by someone else.
    assertNull(
      repo.updateLibrary(kfs) {
        it.copy(concepts = it.concepts + concept("org.org-kenya-forest-service.x", mekong))
      }
    )
    assertNull(repo.updateLibrary("org-missing") { it })
    assertEquals(2, repo.getLibrary(kfs).concepts.size)
  }

  /** Collects outside [runNow]: nested Unconfined coroutines would only run after its block. */
  @Test
  fun observeLibrary_emitsChanges_andDeletingTheOrganizationDeletesItsLibrary() {
    val store = seededStore()
    val repo = LibraryRepositoryImpl(store)
    val organizations = OrganizationRepositoryImpl(store)
    val seen = mutableListOf<Int>()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    repo.observeLibrary(kfs).onEach { seen += it.concepts.size }.launchIn(scope)
    runNow {
      repo.updateLibrary(kfs) {
        it.copy(concepts = it.concepts + concept("org.org-kenya-forest-service.new_one", kfs))
      }
    }
    runNow { organizations.deleteOrganization(kfs) }
    assertEquals(listOf(2, 3, 0), seen)
    assertTrue(kfs !in runNow { store.transaction { libraries() } })
    scope.coroutineContext[Job]!!.cancel()
  }
}
