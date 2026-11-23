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
package org.openforis.collectone.v2.app.data.mapper

import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.PeerDataVisibility as PeerDataVisibilityProto
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SharingPolicy as SharingPolicyProto
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy

/**
 * Maps a survey's sharing settings (collaborators, roles, sharing policy, and peer data visibility)
 * between domain models and `SurveyAcl`. Unset or unknown proto values decode to `RESTRICTED`,
 * `DATA_COLLECTOR`, `PENDING`, and `ALL`, so an ACL written by an older or newer client still maps
 * to a valid setting.
 */
internal object SurveyAclMapper {
  fun collaboratorRoleToProto(role: CollaboratorRole?): Role =
    when (role) {
      CollaboratorRole.VIEWER -> Role.VIEWER
      CollaboratorRole.DATA_COLLECTOR -> Role.DATA_COLLECTOR
      CollaboratorRole.SURVEY_ORGANIZER -> Role.SURVEY_ORGANIZER
      null -> Role.DATA_COLLECTOR
    }

  fun collaboratorRoleFromProto(protoRole: Role?): CollaboratorRole =
    when (protoRole) {
      Role.VIEWER -> CollaboratorRole.VIEWER
      Role.SURVEY_ORGANIZER -> CollaboratorRole.SURVEY_ORGANIZER
      else -> CollaboratorRole.DATA_COLLECTOR
    }

  fun collaboratorToProto(c: Collaborator): AclEntry =
    AclEntry(
      user_id = c.userId.orEmpty(),
      email = c.email,
      role = collaboratorRoleToProto(c.role),
      invitation_status =
        when (c.status) {
          InvitationStatus.PENDING -> AclEntry.InvitationStatus.PENDING
          InvitationStatus.ACCEPTED -> AclEntry.InvitationStatus.ACCEPTED
        },
      invite_token = c.inviteToken.orEmpty(),
      profile = c.profile?.let(ProtoOrganizationMapper::cachedProfileToProto),
    )

  fun collaboratorFromProto(proto: AclEntry): Collaborator =
    Collaborator(
      email = proto.email,
      role = collaboratorRoleFromProto(proto.role),
      status =
        when (proto.invitation_status) {
          AclEntry.InvitationStatus.ACCEPTED -> InvitationStatus.ACCEPTED
          else -> InvitationStatus.PENDING
        },
      inviteToken = proto.invite_token.takeIf { it.isNotBlank() },
      userId = proto.user_id.takeIf { it.isNotBlank() },
      profile = proto.profile?.let(ProtoOrganizationMapper::cachedProfileFromProto),
    )

  fun sharingPolicyToProto(policy: SharingPolicy): SharingPolicyProto =
    when (policy) {
      SharingPolicy.RESTRICTED -> SharingPolicyProto.RESTRICTED
      SharingPolicy.ORGANIZATION -> SharingPolicyProto.ORGANIZATION
      SharingPolicy.ANYONE_WITH_LINK -> SharingPolicyProto.ANYONE_WITH_LINK
      SharingPolicy.PUBLIC -> SharingPolicyProto.PUBLIC
    }

  fun sharingPolicyFromProto(proto: SharingPolicyProto): SharingPolicy =
    when (proto) {
      SharingPolicyProto.ORGANIZATION -> SharingPolicy.ORGANIZATION
      SharingPolicyProto.ANYONE_WITH_LINK -> SharingPolicy.ANYONE_WITH_LINK
      SharingPolicyProto.PUBLIC -> SharingPolicy.PUBLIC
      else -> SharingPolicy.RESTRICTED
    }

  fun peerDataVisibilityToProto(v: PeerDataVisibility): PeerDataVisibilityProto =
    when (v) {
      PeerDataVisibility.ALL -> PeerDataVisibilityProto.ALL
      PeerDataVisibility.OWN_ONLY -> PeerDataVisibilityProto.OWN_ONLY
    }

  fun peerDataVisibilityFromProto(proto: PeerDataVisibilityProto): PeerDataVisibility =
    when (proto) {
      PeerDataVisibilityProto.OWN_ONLY -> PeerDataVisibility.OWN_ONLY
      else -> PeerDataVisibility.ALL
    }
}
