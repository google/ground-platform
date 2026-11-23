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
package org.openforis.collectone.v2.server.messaging

import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.Role

/** Delivery outcome status for an outbound invitation email. */
enum class DeliveryStatus {
  SENT,
  LOGGED,
  SKIPPED_DUPLICATE,
  FAILED,
}

/** In-process survey collaborator invitation payload emitted by `MutateSurvey`. */
data class SurveyInviteNotification(
  val idempotencyKey: String = "",
  val surveyId: String,
  val surveyTitle: String = "",
  val organizationId: String = "",
  val organizationName: String = "",
  val recipientEmail: String,
  val role: Role = Role.ROLE_UNSPECIFIED,
  val inviteToken: String,
  val inviterEmail: String = "",
  val inviterDisplayName: String = "",
)

/** In-process organization member invitation payload emitted by `MutateOrganization`. */
data class OrganizationInviteNotification(
  val idempotencyKey: String = "",
  val organizationId: String,
  val organizationName: String = "",
  val recipientEmail: String,
  val role: OrganizationRole = OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED,
  val inviteToken: String,
  val inviterEmail: String = "",
  val inviterDisplayName: String = "",
)

/** Per-recipient delivery outcome returned by synchronous invite delivery calls. */
data class InviteDeliveryResult(
  val idempotencyKey: String,
  val recipientEmail: String,
  val status: DeliveryStatus,
  val providerMessageId: String = "",
  val errorMessage: String = "",
)
