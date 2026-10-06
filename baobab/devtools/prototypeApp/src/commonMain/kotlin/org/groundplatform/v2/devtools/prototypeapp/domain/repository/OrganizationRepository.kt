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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole

/**
 * Domain repository contract for organizations and their memberships.
 *
 * Membership mutations return the updated [Organization], or `null` if the organization doesn't
 * exist or the change isn't allowed (for example demoting the last Manager).
 */
interface OrganizationRepository {
  fun observeOrganizations(): Flow<List<Organization>>

  suspend fun getOrganizations(): List<Organization>

  suspend fun getOrganization(organizationId: String): Organization?

  /** Organizations in which [email] is an active member, in display order. */
  suspend fun getOrganizationsFor(email: String): List<Organization>

  /**
   * Creates [organization], making [creatorEmail] its first Manager if they aren't already an
   * active Manager in it. Fails (returns `null`) if the ID is already taken.
   */
  suspend fun createOrganization(
    organization: Organization,
    creatorEmail: String,
    creatorProfile: CachedProfile? = null,
  ): Organization?

  /** Replaces the organization's profile fields (name, description, URL, logo, listing). */
  suspend fun updateOrganization(
    organizationId: String,
    transform: (Organization) -> Organization,
  ): Organization?

  /** Deletes the organization. Its surveys become personal surveys of their owners. */
  suspend fun deleteOrganization(organizationId: String)

  /** Adds [email] as an invited member with [role] and a fresh invite [token]. */
  suspend fun inviteMember(
    organizationId: String,
    email: String,
    role: OrganizationRole,
    token: String,
  ): Organization?

  /** Records that [email] accepted their invite, caching their [profile]. */
  suspend fun acceptInvite(
    organizationId: String,
    email: String,
    userId: String,
    profile: CachedProfile,
  ): Organization?

  /** Records a request by [email] to join a listed organization. */
  suspend fun requestToJoin(
    organizationId: String,
    email: String,
    profile: CachedProfile? = null,
  ): Organization?

  /** Approves a pending join request, making [email] an active Member. */
  suspend fun approveRequest(organizationId: String, email: String): Organization?

  /** Changes an active member's role. Refuses to demote the last Manager. */
  suspend fun setMemberRole(
    organizationId: String,
    email: String,
    role: OrganizationRole,
  ): Organization?

  /**
   * Removes a member, pending invite, or join request. Refuses to remove the last active Manager.
   */
  suspend fun removeMember(organizationId: String, email: String): Organization?
}
