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
package org.openforis.collectone.v2.app.domain.repository

import org.openforis.collectone.v2.app.domain.error.DomainResult

/** Who accepts an invitation: the signed-in user. */
data class Invitee(
  val userId: String,
  val email: String,
  val displayName: String,
  val photoUrl: String?,
)

/** How an invitation was redeemed. */
enum class InviteRedemption {
  /** The user data service accepted it, and what it returned is stored. */
  ACCEPTED_BY_SERVICE,

  /** There is no user data service: the caller accepts the invitation locally instead. */
  NO_SERVICE,
}

/** Redeems invitations to surveys and organizations with the user data service. */
interface InviteRepository {
  /**
   * Accepts the invitation [token] to survey [surveyId] for [invitee], stores the survey and access
   * list the service returns, and asks for a sync. Fails with why the service didn't accept it.
   */
  suspend fun acceptSurveyInvite(
    surveyId: String,
    token: String,
    invitee: Invitee,
  ): DomainResult<InviteRedemption>

  /**
   * Joins organization [organizationId] with the invitation [token] for [invitee], stores the
   * organization the service returns, and asks for a sync. Fails with why the service didn't accept
   * it.
   */
  suspend fun joinOrganization(
    organizationId: String,
    token: String,
    invitee: Invitee,
  ): DomainResult<InviteRedemption>
}
