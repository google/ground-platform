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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization

class ManageImagerySourcesUseCaseTest {
  private val url = "https://tile.opentopomap.org/{z}/{x}/{y}.png"

  @Test
  fun validationError_requiresANameAndAnXyzTemplate() {
    val useCase = ManageImagerySourcesUseCase(OrganizationRepositoryImpl(seededStore()))
    assertEquals(
      "Enter a name for the imagery source.",
      useCase.validationError("  ", url, ImagerySourceType.XYZ_TILES),
    )
    assertEquals(
      "Enter an http(s):// XYZ tile URL containing {z}, {x}, and {y}.",
      useCase.validationError("Topo", "https://example.org/{z}/{x}", ImagerySourceType.XYZ_TILES),
    )
    assertNull(useCase.validationError("Topo", " $url ", ImagerySourceType.XYZ_TILES))
  }

  @Test
  fun uniqueSourceId_combinesOrganizationAndName_andAddsANumericSuffixWhenTaken() {
    val org = Organization(id = "org-kfs", name = "KFS")
    assertEquals(
      "imagery-kfs-usgs-topo",
      ManageImagerySourcesUseCase.uniqueSourceId(org, "USGS Topo!", emptySet()),
    )
    assertEquals(
      "imagery-kfs-source",
      ManageImagerySourcesUseCase.uniqueSourceId(org, "###", emptySet()),
    )
    assertEquals(
      "imagery-kfs-topo-2",
      ManageImagerySourcesUseCase.uniqueSourceId(org, "Topo", setOf("imagery-kfs-topo")),
    )
  }

  @Test
  fun newSource_trimsFields_andAvoidsIdsTakenByAnyOrganization() {
    val useCase = ManageImagerySourcesUseCase(OrganizationRepositoryImpl(seededStore()))
    val kfs = Organization(id = "org-kfs", name = "KFS")
    val other =
      Organization(
        id = "org-other",
        name = "Other",
        imagerySources = listOf(ImagerySource("imagery-kfs-topo", "Taken", url)),
      )
    val source =
      useCase.newSource(
        kfs,
        listOf(kfs, other),
        " Topo ",
        " $url ",
        ImagerySourceType.XYZ_TILES,
        true,
      )
    assertEquals("imagery-kfs-topo-2", source.id)
    assertEquals("Topo", source.name)
    assertEquals(url, source.urlTemplate)
    assertTrue(source.allowOfflineDownload)
  }

  @Test
  fun add_update_setOfflineAllowed_andRemove_writeThroughTheRepository() {
    val repository = OrganizationRepositoryImpl(seededStore())
    val useCase = ManageImagerySourcesUseCase(repository)
    val orgId = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
    val source = ImagerySource("imagery-kfs-topo", "Topo", url, allowOfflineDownload = false)

    val added = assertNotNull(runNow { useCase.add(orgId, source) })
    assertEquals(source, added.imagerySources.last())

    val updated =
      assertNotNull(runNow { useCase.update(orgId, source.id, " Topo 2 ", " $url ", true) })
    val edited = updated.imagerySources.first { it.id == source.id }
    assertEquals("Topo 2", edited.name)
    assertEquals(url, edited.urlTemplate)
    assertTrue(edited.allowOfflineDownload)

    val toggled = assertNotNull(runNow { useCase.setOfflineAllowed(orgId, source.id, false) })
    assertFalse(toggled.imagerySources.first { it.id == source.id }.allowOfflineDownload)

    val removed = assertNotNull(runNow { useCase.remove(orgId, source.id) })
    assertTrue(removed.imagerySources.none { it.id == source.id })
    assertEquals(added.imagerySources.size - 1, removed.imagerySources.size)
  }
}
