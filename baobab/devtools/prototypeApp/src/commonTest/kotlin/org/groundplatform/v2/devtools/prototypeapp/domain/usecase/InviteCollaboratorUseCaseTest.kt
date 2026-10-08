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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.Collaborator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.InviteCollaboratorUseCase.Outcome

class InviteCollaboratorUseCaseTest {
  private var tokens = 0
  private val useCase = InviteCollaboratorUseCase(newInviteToken = { "token-${++tokens}" })
  private val sharing = SharingSettings(ownerEmail = "owner@example.org")

  @Test
  fun invite_rejectsBadAddressesAndTheOwner() {
    assertIs<Outcome.Rejected>(
      useCase.invite(sharing, "not an email", CollaboratorRole.DATA_COLLECTOR)
    )
    assertIs<Outcome.Rejected>(
      useCase.invite(sharing, "Owner@Example.org", CollaboratorRole.DATA_COLLECTOR)
    )
  }

  @Test
  fun invite_addsAPendingPersonWithAFreshToken_andUpdatesRolesOfExistingOnes() {
    val invited =
      assertIs<Outcome.Updated>(
        useCase.invite(sharing, " Ana@Example.org ", CollaboratorRole.DATA_COLLECTOR)
      )
    val person = invited.sharing.collaborators.single()
    assertEquals("ana@example.org", person.email)
    assertEquals(InvitationStatus.PENDING, person.status)
    assertEquals("token-1", person.inviteToken)

    val promoted =
      assertIs<Outcome.Updated>(
        useCase.invite(invited.sharing, "ana@example.org", CollaboratorRole.SURVEY_ORGANIZER)
      )
    assertEquals(1, promoted.sharing.collaborators.size)
    assertEquals(CollaboratorRole.SURVEY_ORGANIZER, promoted.sharing.collaborators.single().role)
    assertEquals("token-1", promoted.sharing.collaborators.single().inviteToken)
  }

  @Test
  fun resetInviteLink_onlyChangesPendingInvites() {
    val pending =
      Collaborator("p@example.org", CollaboratorRole.DATA_COLLECTOR, inviteToken = "old")
    val accepted =
      Collaborator(
        "a@example.org",
        CollaboratorRole.DATA_COLLECTOR,
        status = InvitationStatus.ACCEPTED,
      )
    val updated =
      useCase.resetInviteLink(
        sharing.copy(collaborators = listOf(pending, accepted)),
        "p@example.org",
      )
    assertNotEquals("old", updated.collaborators[0].inviteToken)
    val unchanged = useCase.resetInviteLink(updated, "a@example.org")
    assertEquals(updated, unchanged)
  }

  @Test
  fun acceptInvite_requiresANameAndAPendingInvite() {
    val pending = Collaborator("p@example.org", CollaboratorRole.DATA_COLLECTOR, inviteToken = "t")
    val withPending = sharing.copy(collaborators = listOf(pending))
    assertIs<Outcome.Rejected>(useCase.acceptInvite(withPending, "p@example.org", "  ", null))
    assertIs<Outcome.Rejected>(useCase.acceptInvite(withPending, "x@example.org", "X", null))

    val accepted =
      assertIs<Outcome.Updated>(
        useCase.acceptInvite(withPending, "p@example.org", "Pat", "avatar:1", "2026-01-01")
      )
    val person = accepted.sharing.collaborators.single()
    assertEquals(InvitationStatus.ACCEPTED, person.status)
    assertNull(person.inviteToken)
    assertEquals("uid-p", person.userId)
    assertEquals("Pat", person.profile?.displayName)
    assertTrue(
      useCase.acceptInvite(accepted.sharing, "p@example.org", "Pat", null) is Outcome.Rejected
    )
  }
}
