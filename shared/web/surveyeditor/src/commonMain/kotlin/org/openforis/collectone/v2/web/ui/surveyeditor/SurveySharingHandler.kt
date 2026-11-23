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
package org.openforis.collectone.v2.web.ui.surveyeditor

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase
import org.openforis.collectone.v2.web.ui.text.messageRes

/**
 * [SharingActions] of the Survey editor: the access policy, collaborators, and invites, applied to
 * the draft of [session].
 */
internal class SurveySharingHandler(
  private val session: SurveyDraftSession,
  private val inviteCollaboratorUseCase: InviteCollaboratorUseCase,
) : SharingActions {
  override fun updateSharing(transform: (SharingSettings) -> SharingSettings) = session.edit {
    sharing = transform(sharing)
  }

  override fun inviteCollaborator(email: String, role: CollaboratorRole): StringResource? {
    if (session.uiState.isReadOnly) return null
    return when (
      val outcome = inviteCollaboratorUseCase.invite(session.draft.sharing, email, role)
    ) {
      is InviteCollaboratorUseCase.Outcome.Rejected -> outcome.reason.messageRes
      is InviteCollaboratorUseCase.Outcome.Updated -> {
        updateSharing { outcome.sharing }
        null
      }
    }
  }

  override fun resetInviteLink(email: String) = updateSharing {
    inviteCollaboratorUseCase.resetInviteLink(it, email)
  }

  override fun acceptInvite(
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String,
  ): StringResource? {
    if (session.uiState.isReadOnly) return null
    return when (
      val outcome =
        inviteCollaboratorUseCase.acceptInvite(
          session.draft.sharing,
          email,
          displayName,
          photoUrl,
          cachedOn,
        )
    ) {
      is InviteCollaboratorUseCase.Outcome.Rejected -> outcome.reason.messageRes
      is InviteCollaboratorUseCase.Outcome.Updated -> {
        updateSharing { outcome.sharing }
        null
      }
    }
  }

  override fun setCollaboratorRole(email: String, role: CollaboratorRole) = updateSharing { s ->
    s.copy(
      collaborators = s.collaborators.map { if (it.email == email) it.copy(role = role) else it }
    )
  }

  override fun removeCollaborator(email: String) = updateSharing { s ->
    s.copy(collaborators = s.collaborators.filterNot { it.email == email })
  }
}
