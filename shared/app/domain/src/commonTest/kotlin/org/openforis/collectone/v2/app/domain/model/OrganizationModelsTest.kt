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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganizationModelsTest {

  @Test
  fun isPlatformAdmin_requiresActiveManagerOnAllUsersOrSyntheticOrganization() {
    val allUsersById =
      Organization(
        id = Organization.ALL_USERS_ID,
        name = "All users",
        isSynthetic = false,
        members =
          listOf(
            OrganizationMember(email = "admin@example.org", role = OrganizationRole.MANAGER),
            OrganizationMember(
              email = "invited@example.org",
              role = OrganizationRole.MANAGER,
              status = MembershipStatus.INVITED,
            ),
            OrganizationMember(email = "member@example.org", role = OrganizationRole.MEMBER),
          ),
      )
    val syntheticByFlag =
      Organization(
        id = "synthetic-platform",
        name = "Platform",
        isSynthetic = true,
        members =
          listOf(
            OrganizationMember(
              email = "ops@example.org",
              userId = "uid-ops",
              role = OrganizationRole.MANAGER,
            )
          ),
      )
    val regularOrg =
      Organization(
        id = "org-kfs",
        name = "Kenya Forest Service",
        isSynthetic = false,
        members =
          listOf(
            OrganizationMember(email = "org-manager@example.org", role = OrganizationRole.MANAGER)
          ),
      )

    assertTrue(allUsersById.isAllUsers)
    assertTrue(syntheticByFlag.isAllUsers)
    assertFalse(regularOrg.isAllUsers)

    assertTrue(allUsersById.isPlatformAdmin("admin@example.org"))
    assertFalse(allUsersById.isPlatformAdmin("invited@example.org"))
    assertFalse(allUsersById.isPlatformAdmin("member@example.org"))
    assertFalse(allUsersById.isPlatformAdmin(""))

    assertTrue(syntheticByFlag.isPlatformAdmin(email = "", userId = "uid-ops"))
    assertFalse(regularOrg.isPlatformAdmin("org-manager@example.org"))

    val organizations = listOf(regularOrg, allUsersById, syntheticByFlag)
    assertTrue(organizations.hasPlatformAdmin("admin@example.org"))
    assertTrue(organizations.hasPlatformAdmin(email = "", userId = "uid-ops"))
    assertFalse(organizations.hasPlatformAdmin("org-manager@example.org"))
    assertFalse(organizations.hasPlatformAdmin(""))
  }
}
