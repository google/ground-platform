/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.web.domain.model.OrganizationRelation
import org.openforis.collectone.v2.web.domain.usecase.InviteOrganizationMemberUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase

/** The English text of the organization pages' relation badges and refusals. */
class OrganizationTextTest {
  private val store = seededStore()
  private val inviteMember = InviteOrganizationMemberUseCase(OrganizationRepositoryImpl(store))
  private val manageLibrary = ManageLibraryUseCase(LibraryRepositoryImpl(store))

  private val organization =
    Organization(
      id = "org-x",
      name = "Mangrove Watch",
      members =
        listOf(
          OrganizationMember("amy@example.org", profile = CachedProfile("Amy Adams")),
          OrganizationMember("inv@example.org", status = MembershipStatus.INVITED),
          OrganizationMember(
            "req@example.org",
            status = MembershipStatus.REQUESTED,
            profile = CachedProfile("Req Person"),
          ),
        ),
    )

  private fun english(domainMessage: DomainMessage?): String? = runBlocking {
    when (val message = domainMessage?.toOrganizationMessage()) {
      null -> null
      is OrganizationMessage.Resource -> getString(message.resource, *message.args.toTypedArray())
      is OrganizationMessage.Text -> message.text
    }
  }

  @Test
  fun relationBadges() {
    assertEquals(
      listOf("You manage", "Member", "Invited", "Requested to join", null),
      OrganizationRelation.entries.map { relation ->
        relation.labelRes?.let { runBlocking { getString(it) } }
      },
    )
  }

  @Test
  fun inviteRefusals() {
    assertEquals(
      "Enter a valid email address.",
      english(inviteMember.inviteError(organization, "nope")),
    )
    assertEquals(
      "Organization not found.",
      english(inviteMember.inviteError(null, "new@example.org")),
    )
    assertEquals(
      "Everyone is already a member of All users. Invite people as Managers.",
      english(
        inviteMember.inviteError(
          organization.copy(name = "All users", isSynthetic = true),
          "new@example.org",
        )
      ),
    )
    assertEquals(
      "Amy Adams is already a member.",
      english(inviteMember.inviteError(organization, "amy@example.org")),
    )
    assertEquals(
      "inv@example.org has already been invited.",
      english(inviteMember.inviteError(organization, "inv@example.org")),
    )
    assertEquals(
      "Req Person has asked to join. Approve their request instead.",
      english(inviteMember.inviteError(organization, "req@example.org")),
    )
  }

  @Test
  fun acceptRefusal() {
    assertEquals("Enter a name.", english(inviteMember.acceptError(" ")))
  }

  @Test
  fun conceptDeletionRefusals() = runNow {
    val library =
      LibraryRepositoryImpl(store).getLibrary(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    assertEquals(
      "This concept no longer exists.",
      english(manageLibrary.deleteConceptError(library, "org.missing")),
    )
    assertEquals(
      "Only draft concepts can be deleted. Deprecate it instead, so existing links keep working.",
      english(
        manageLibrary.deleteConceptError(
          library,
          "org.00000013-31cc-5c95-90ea-379f624a5c72.cherry_delivery_kg",
        )
      ),
    )
  }
}
