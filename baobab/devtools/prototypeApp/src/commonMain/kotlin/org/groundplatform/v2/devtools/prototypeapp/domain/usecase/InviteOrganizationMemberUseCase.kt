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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InviteLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository

/**
 * Invites a person to an organization by email, and simulates the invite lifecycle (re-issuing the
 * link and the invitee accepting it).
 *
 * Validation ([inviteError]) is pure so the form can show the problem inline; the writes ([invoke],
 * [resetInviteLink], [acceptInvite]) go through [OrganizationRepository].
 */
class InviteOrganizationMemberUseCase(private val organizationRepository: OrganizationRepository) {
  /**
   * Why [email] can't be invited to [organization], or `null` when it can: the address is invalid,
   * the organization is missing, or the person is already a member, invitee, or requester.
   */
  fun inviteError(organization: Organization?, email: String): String? {
    val normalized = normalizeEmail(email)
    if (!EMAIL_PATTERN.matches(normalized)) return "Enter a valid email address."
    if (organization == null) return "Organization not found."
    val existing = organization.member(normalized) ?: return null
    return when (existing.status) {
      MembershipStatus.ACTIVE -> "${existing.displayName} is already a member."
      MembershipStatus.INVITED -> "${existing.email} has already been invited."
      MembershipStatus.REQUESTED ->
        "${existing.displayName} has asked to join. Approve their request instead."
    }
  }

  /** Adds [email] (validated with [inviteError]) to [organizationId] as an invited [role]. */
  suspend operator fun invoke(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): Organization? =
    organizationRepository.inviteMember(
      organizationId,
      normalizeEmail(email),
      role,
      token = InviteLinks.newToken(),
    )

  /**
   * Issues a new invite link for [email]'s pending invite, invalidating the old one.
   *
   * TODO: [OrganizationRepository.updateOrganization] only applies profile fields and keeps the
   *   stored membership list, so the new token isn't persisted yet (carried over from the previous
   *   app-state implementation). Add a dedicated membership write to the repository to fix this.
   */
  suspend fun resetInviteLink(organizationId: String, email: String): Organization? =
    organizationRepository.updateOrganization(organizationId) { org ->
      org.copy(
        members =
          org.members.map {
            if (it.email == email && it.status == MembershipStatus.INVITED) {
              it.copy(inviteToken = InviteLinks.newToken())
            } else {
              it
            }
          }
      )
    }

  /** Why the (simulated) invitee can't accept with [displayName], or `null` when they can. */
  fun acceptError(displayName: String): String? =
    if (displayName.isBlank()) "Enter a name." else null

  /**
   * Records [email] accepting their invite to [organizationId] as [displayName] (validated with
   * [acceptError]) with an account ID derived from the address.
   */
  suspend fun acceptInvite(
    organizationId: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): Organization? =
    organizationRepository.acceptInvite(
      organizationId,
      email,
      userId = userIdFor(email),
      profile = CachedProfile(displayName.trim(), photoUrl, ""),
    )

  companion object {
    private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    /** The prototype's stand-in account ID for [email] (`uid-<local part>`). */
    fun userIdFor(email: String): String = "uid-${email.substringBefore('@').replace('.', '-')}"
  }
}
