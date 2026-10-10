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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationType

class CreateOrganizationUseCaseTest {
  @Test
  fun uniqueOrganizationId_slugifiesTheName_andAddsANumericSuffixWhenTaken() {
    assertEquals(
      "org-kenya-forest-service",
      CreateOrganizationUseCase.uniqueOrganizationId("Kenya Forest Service", emptySet()),
    )
    assertEquals(
      "org-r-d-2026",
      CreateOrganizationUseCase.uniqueOrganizationId("  R&D (2026) ", emptySet()),
    )
    assertEquals(
      "org-organization",
      CreateOrganizationUseCase.uniqueOrganizationId("!!!", emptySet()),
    )
    assertEquals(
      "org-labs-3",
      CreateOrganizationUseCase.uniqueOrganizationId("Labs", setOf("org-labs", "org-labs-2")),
    )
    assertTrue(
      CreateOrganizationUseCase.uniqueOrganizationId("x".repeat(80), emptySet()).length <= 44
    )
  }

  @Test
  fun newOrganization_trimsFields_defaultsTheName_andCyclesPlaceholderLogos() {
    val useCase = CreateOrganizationUseCase(OrganizationRepositoryImpl(seededStore()))
    val existing = List(10) { Organization(id = "org-$it", name = "Org $it") }

    val org = useCase.newOrganization("  Mangrove Watch ", " Coastal ", false, existing)
    assertEquals("org-mangrove-watch", org.id)
    assertEquals("Mangrove Watch", org.name)
    assertEquals("Coastal", org.description)
    assertFalse(org.isListed)
    assertEquals("avatar:1", org.logoUrl)
    assertTrue(org.members.isEmpty())

    val untitled = useCase.newOrganization("   ", "", true, existing + org)
    assertEquals(CreateOrganizationUseCase.DEFAULT_NAME, untitled.name)
    assertEquals("org-untitled-organization", untitled.id)
    assertEquals("avatar:2", untitled.logoUrl)
  }

  @Test
  fun invoke_storesTheOrganization_withTheCreatorAsItsFirstManager() {
    val repository = OrganizationRepositoryImpl(seededStore())
    val useCase = CreateOrganizationUseCase(repository)
    val existing = runNow { repository.getOrganizations() }
    val org = useCase.newOrganization("Mangrove Watch", "", true, existing)

    val stored = runNow { useCase(org, "maya@example.org", CachedProfile("Maya Lin")) }
    assertNotNull(stored)
    assertTrue(stored.isManager("maya@example.org"))
    assertEquals("Maya Lin", stored.member("maya@example.org")?.displayName)
    assertEquals(existing.size + 1, runNow { repository.getOrganizations() }.size)

    // The ID is now taken.
    assertNull(runNow { useCase(org, "maya@example.org", null) })
  }

  @Test
  fun newOrganization_keepsTheType_andNormalizesTheCountryCode() {
    val useCase = CreateOrganizationUseCase(OrganizationRepositoryImpl(seededStore()))

    val org =
      useCase.newOrganization("Co-op", "", true, emptyList(), OrganizationType.COOPERATIVE, " ke ")
    assertEquals(OrganizationType.COOPERATIVE, org.organizationType)
    assertEquals("KE", org.countryCode)

    val unspecified = useCase.newOrganization("Lab", "", true, emptyList(), null, "   ")
    assertNull(unspecified.organizationType)
    assertNull(unspecified.countryCode)
    // Both default to not specified.
    assertNull(useCase.newOrganization("Lab", "", true, emptyList()).countryCode)
  }

  @Test
  fun invoke_persistsTheTypeAndCountry() {
    val repository = OrganizationRepositoryImpl(seededStore())
    val useCase = CreateOrganizationUseCase(repository)
    val existing = runNow { repository.getOrganizations() }
    val org =
      useCase.newOrganization(
        "Andes Research",
        "",
        true,
        existing,
        OrganizationType.RESEARCH,
        countryCode = "pe",
      )

    val stored = assertNotNull(runNow { useCase(org, "maya@example.org", null) })
    assertEquals(OrganizationType.RESEARCH, stored.organizationType)
    assertEquals("PE", stored.countryCode)
    val reloaded = assertNotNull(runNow { repository.getOrganization(org.id) })
    assertEquals(OrganizationType.RESEARCH, reloaded.organizationType)
    assertEquals("PE", reloaded.countryCode)
    assertEquals("Peru (PE)", reloaded.country?.label)
  }

  @Test
  fun invoke_acceptsABlankCountry_asNotSpecified() {
    val repository = OrganizationRepositoryImpl(seededStore())
    val useCase = CreateOrganizationUseCase(repository)

    val stored =
      assertNotNull(
        runNow {
          useCase(Organization(id = "org-x", name = "X", countryCode = " "), "a@b.org", null)
        }
      )
    assertNull(stored.countryCode)
    assertNull(stored.organizationType)
  }

  @Test
  fun invoke_refusesAnUnknownCountryCode_andStoresNothing() {
    val repository = OrganizationRepositoryImpl(seededStore())
    val useCase = CreateOrganizationUseCase(repository)
    val before = runNow { repository.getOrganizations() }.size

    for (code in listOf("XX", "Kenya", "KEN", "UK")) {
      val org = Organization(id = "org-$code", name = code, countryCode = code)
      assertNull(runNow { useCase(org, "a@b.org", null) }, code)
    }
    assertEquals(before, runNow { repository.getOrganizations() }.size)
  }

  @Test
  fun countryCodeError_allowsBlankAndIsoCodes_andRejectsAnythingElse() {
    assertNull(CreateOrganizationUseCase.countryCodeError(null))
    assertNull(CreateOrganizationUseCase.countryCodeError(""))
    assertNull(CreateOrganizationUseCase.countryCodeError("  "))
    assertNull(CreateOrganizationUseCase.countryCodeError("KE"))
    assertNull(CreateOrganizationUseCase.countryCodeError(" vn "))
    assertEquals(
      CreateOrganizationUseCase.INVALID_COUNTRY_ERROR,
      CreateOrganizationUseCase.countryCodeError("ZZ"),
    )
    assertEquals(
      CreateOrganizationUseCase.INVALID_COUNTRY_ERROR,
      CreateOrganizationUseCase.countryCodeError("Kenya (KE)"),
    )
  }
}
