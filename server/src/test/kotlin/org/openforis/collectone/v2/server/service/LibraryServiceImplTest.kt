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
package org.openforis.collectone.v2.server.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.GetLibraryRequest
import openforis.collectone.v1.library.GetResolvedLibraryRequest
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.library.SearchConceptsRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.server.service.ServiceTestSeed.ORGANIZATION
import org.openforis.collectone.v2.server.service.ServiceTestSeed.OTHER_ORGANIZATION
import org.openforis.collectone.v2.server.service.ServiceTestSeed.UNLISTED_ORGANIZATION

/** Behavior of the library RPCs ([LibraryServiceImpl]) on [ServiceTestSeed]. */
open class LibraryServiceImplTest {
  /** A backend loaded with [seed]; overridden to test other stores. */
  protected open fun newBackend(seed: UserDataSeed): UserDataBackend = inMemoryBackend(seed)

  private val backend by lazy { newBackend(ServiceTestSeed.seed) }

  private suspend fun addSoilPhConcept() =
    backend.MutateLibrary(
      MutateLibraryRequest(
        organization_id = ORGANIZATION,
        mutations =
          listOf(
            LibraryMutation(
              upsert =
                OrganizationLibraryRecord(
                  bundle =
                    LibraryBundle(
                      concepts =
                        listOf(
                          ConceptDef(
                            id = "org.$ORGANIZATION.soil_ph",
                            label = LocalizedText(values = mapOf("en" to "Soil pH")),
                            data_type = DataType.TYPE_DOUBLE,
                          )
                        )
                    )
                )
            )
          ),
      )
    )

  @Test
  fun listLibraries_fullSync_returnsLibrariesInCommitOrder() = runTest {
    val libraries = backend.ListLibraries(ListLibrariesRequest()).libraries

    assertEquals(listOf(ORGANIZATION, OTHER_ORGANIZATION), libraries.map { it.organization_id })
  }

  @Test
  fun mutateLibrary_commitsAfterTheCurrentWatermark() = runTest {
    val watermark = backend.currentWatermark()

    val response = addSoilPhConcept()

    assertTrue(response.commit_timestamp!! > watermark)
  }

  @Test
  fun listLibraries_delta_returnsTheChangedLibrary() = runTest {
    val token = backend.ListLibraries(ListLibrariesRequest()).next_sync_token
    addSoilPhConcept()

    val delta = backend.ListLibraries(ListLibrariesRequest(sync_token = token)).libraries

    assertEquals(listOf(ORGANIZATION), delta.map { it.organization_id })
    assertEquals(
      listOf("org.$ORGANIZATION.soil_ph"),
      delta.single().bundle?.concepts?.map { it.id },
    )
  }

  /**
   * [ServiceTestSeed] with a global library, and [ORGANIZATION]'s library overriding part of it.
   */
  private val withGlobalLibrary by lazy {
    val seed = ServiceTestSeed.seed
    newBackend(
      seed.copy(
        organizations =
          seed.organizations +
            Organization(
              organization_id = ALL_USERS_ORGANIZATION_ID,
              name = "All Users",
              listed = false,
            ),
        libraries =
          seed.libraries.map {
            if (it.organization_id != ORGANIZATION) it
            else
              it.copy(
                bundle =
                  LibraryBundle(concepts = listOf(concept("tree_height", "Height (m)"), soilPh)),
                settings =
                  OrganizationLibrarySettings(
                    organization_id = ORGANIZATION,
                    hidden_global_entry_ids = listOf("species"),
                  ),
                version = 4L,
              )
          } +
            OrganizationLibraryRecord(
              organization_id = ALL_USERS_ORGANIZATION_ID,
              bundle =
                LibraryBundle(
                  concepts =
                    listOf(concept("species", "Species"), concept("tree_height", "Height")),
                  form_templates = listOf(FormTemplateDef(id = "plot-inventory")),
                ),
              settings = OrganizationLibrarySettings(organization_id = ALL_USERS_ORGANIZATION_ID),
              version = 9L,
            ),
      )
    )
  }

  @Test
  fun resolvedLibrary_overlaysTheOrganizationsEntriesOnTheGlobalOnes() = runTest {
    val resolved =
      withGlobalLibrary
        .GetResolvedLibrary(GetResolvedLibraryRequest(organization_id = ORGANIZATION))
        .resolved_library!!

    // Global entries keep their position; the organization's version of one replaces it.
    assertEquals(
      listOf("species" to "Species", "tree_height" to "Height (m)", soilPh.id to "Soil pH"),
      resolved.bundle!!.concepts.map { it.id to it.label!!.values.getValue("en") },
    )
    assertEquals(listOf("plot-inventory"), resolved.bundle!!.form_templates.map { it.id })
    // Hiding entries is the organization's setting; the clients apply it.
    assertEquals(listOf("species"), resolved.settings!!.hidden_global_entry_ids)
    assertEquals(4L, resolved.version)
  }

  @Test
  fun resolvedLibrary_withoutAnOrganization_isTheGlobalLibrary() = runTest {
    val resolved =
      withGlobalLibrary.GetResolvedLibrary(GetResolvedLibraryRequest()).resolved_library!!

    assertEquals(listOf("species", "tree_height"), resolved.bundle!!.concepts.map { it.id })
    assertEquals(ALL_USERS_ORGANIZATION_ID, resolved.settings!!.organization_id)
    assertEquals(1L, resolved.version)
  }

  @Test
  fun library_ofAnOrganizationWithoutOne_isAnEmptyFirstVersion() = runTest {
    val library =
      backend.GetLibrary(GetLibraryRequest(organization_id = UNLISTED_ORGANIZATION)).library!!

    assertEquals(UNLISTED_ORGANIZATION, library.organization_id)
    assertEquals(LibraryBundle(), library.bundle)
    assertEquals(UNLISTED_ORGANIZATION, library.settings!!.organization_id)
    assertEquals(1L, library.version)
  }

  @Test
  fun conceptSearch_matchesIdsAndLabels_inTheResolvedLibrary_ofTheRequestedType() = runTest {
    suspend fun search(query: String, type: DataType = DataType.DATA_TYPE_UNSPECIFIED) =
      withGlobalLibrary
        .SearchConcepts(
          SearchConceptsRequest(organization_id = ORGANIZATION, query = query, data_type = type)
        )
        .concepts
        .map { it.id to it.label!!.values.getValue("en") }

    assertEquals(listOf("tree_height" to "Height (m)"), search("HEIGHT"))
    assertEquals(listOf(soilPh.id to "Soil pH"), search(" soil "))
    assertEquals(listOf(soilPh.id to "Soil pH"), search("", DataType.TYPE_DOUBLE))
    assertEquals(emptyList(), search("soil", DataType.TYPE_STRING))
  }

  private companion object {
    fun concept(id: String, label: String) =
      ConceptDef(id = id, label = LocalizedText(values = mapOf("en" to label)))

    val soilPh =
      concept("org.$ORGANIZATION.soil_ph", "Soil pH").copy(data_type = DataType.TYPE_DOUBLE)
  }
}
