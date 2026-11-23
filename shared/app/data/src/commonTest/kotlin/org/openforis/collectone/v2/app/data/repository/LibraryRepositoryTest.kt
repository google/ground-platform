/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.data.repository

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.seed.GlobalLibrarySeedData
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary

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
    assertTrue(global.concepts.size in 65..80, "about 75 global concepts")
    assertEquals(
      listOf(
        "producer_registration",
        "eudr_due_diligence",
        "ferm_restoration",
        "protected_area_management",
        "iplc_land_mapping",
        "lulc_training_data",
        "deforestation_activity_data",
        "forest_carbon_inventory",
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
    val id = "org.00000014-1894-5118-9a64-566bbe3875bf.seedling_count"
    val updated =
      repo.updateLibrary(mekong) { it.copy(concepts = it.concepts + concept(id, mekong)) }
    assertNotNull(updated)
    assertEquals(listOf(id), repo.getLibrary(mekong).concepts.map { it.id })
    // Other libraries are untouched.
    assertEquals(2, repo.getLibrary(kfs).concepts.size)
  }

  @Test
  fun updateLibrary_givesEditedTemplatesTheNextVersion() = runNow {
    val repo = LibraryRepositoryImpl(seededStore())
    val global = Organization.ALL_USERS_ID
    val id = "eudr_plot_registration"
    assertEquals(1, repo.getLibrary(global).formTemplate(id)?.version)
    // An edit that doesn't touch the template keeps its version.
    repo.updateLibrary(global) { it }
    assertEquals(1, repo.getLibrary(global).formTemplate(id)?.version)
    repo.updateLibrary(global) { library ->
      library.copy(
        formTemplates =
          library.formTemplates.map {
            if (it.id == id) it.copy(title = LocalizedText.en("EUDR plot check")) else it
          }
      )
    }
    assertEquals(2, repo.getLibrary(global).formTemplate(id)?.version)
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
        it.copy(concepts = it.concepts + concept("org.00000014-1894-5118-9a64-566bbe3875bf.x", kfs))
      }
    )
    // Owned by someone else.
    assertNull(
      repo.updateLibrary(kfs) {
        it.copy(
          concepts = it.concepts + concept("org.00000013-31cc-5c95-90ea-379f624a5c72.x", mekong)
        )
      }
    )
    assertNull(repo.updateLibrary("org-missing") { it })
    assertEquals(2, repo.getLibrary(kfs).concepts.size)
  }

  @Test
  fun observeLibrary_emitsChanges_andDeletingTheOrganizationDeletesItsLibrary() = runTest {
    val store = seededStore()
    val repo = LibraryRepositoryImpl(store)
    val organizations = OrganizationRepositoryImpl(store)
    repo.observeLibrary(kfs).test {
      assertEquals(2, awaitItem().concepts.size)

      repo.updateLibrary(kfs) {
        it.copy(
          concepts = it.concepts + concept("org.00000013-31cc-5c95-90ea-379f624a5c72.new_one", kfs)
        )
      }
      assertEquals(3, awaitItem().concepts.size)

      organizations.deleteOrganization(kfs)
      assertEquals(0, awaitItem().concepts.size)
    }
    assertTrue(kfs !in store.transaction { libraries() })
  }
}
