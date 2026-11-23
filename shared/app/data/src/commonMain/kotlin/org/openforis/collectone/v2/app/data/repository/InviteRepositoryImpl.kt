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
package org.openforis.collectone.v2.app.data.repository

import openforis.collectone.v1.survey.AcceptSurveyInviteRequest
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.JoinOrganizationRequest
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.error.domainResultOf
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.data.sync.SyncCoordinator
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.repository.InviteRedemption
import org.openforis.collectone.v2.app.domain.repository.InviteRepository
import org.openforis.collectone.v2.app.domain.repository.Invitee

/**
 * [InviteRepository] over the user data service ([grpcClient], null without one): accepted surveys
 * and organizations are rebased into [localStore] in place, then [syncCoordinator] (when the sync
 * engine runs) syncs the rest.
 */
class InviteRepositoryImpl(
  private val localStore: LocalStore,
  private val grpcClient: CollectOneGrpcClient?,
  private val syncCoordinator: SyncCoordinator?,
) : InviteRepository {

  override suspend fun acceptSurveyInvite(
    surveyId: String,
    token: String,
    invitee: Invitee,
  ): DomainResult<InviteRedemption> {
    val client = grpcClient ?: return DomainResult.Success(InviteRedemption.NO_SERVICE)
    return domainResultOf {
      val response =
        client.acceptSurveyInvite(
          AcceptSurveyInviteRequest(
            survey_id = surveyId,
            invite_token = token,
            user_id = invitee.userId,
            email = invitee.email,
            profile =
              CachedProfile(
                display_name = invitee.displayName,
                avatar_url = invitee.photoUrl.orEmpty(),
              ),
          )
        )
      if (response.survey != null || response.acl != null) {
        localStore.transaction {
          val stored = surveyBase(surveyId) ?: SurveyBase()
          Rebaser(this)
            .acceptSurvey(
              surveyId = surveyId,
              base =
                stored.copy(
                  saved = response.survey ?: stored.saved,
                  acl = response.acl ?: stored.acl,
                ),
              replace = true,
            )
        }
      }
      syncCoordinator?.requestSync()
      InviteRedemption.ACCEPTED_BY_SERVICE
    }
  }

  override suspend fun joinOrganization(
    organizationId: String,
    token: String,
    invitee: Invitee,
  ): DomainResult<InviteRedemption> {
    val client = grpcClient ?: return DomainResult.Success(InviteRedemption.NO_SERVICE)
    return domainResultOf {
      val response =
        client.joinOrganization(
          JoinOrganizationRequest(
            organization_id = organizationId,
            invite_token = token,
            user_id = invitee.userId,
            email = invitee.email,
          )
        )
      response.organization?.let { orgProto ->
        localStore.transaction { Rebaser(this).acceptOrganization(orgProto, replace = true) }
      }
      syncCoordinator?.requestSync()
      InviteRedemption.ACCEPTED_BY_SERVICE
    }
  }
}
