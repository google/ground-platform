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
package org.openforis.collectone.v2.web.domain.usecase

import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.InviteLinks
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings

/**
 * Rules for the people on a survey's access list: inviting them (with a fresh invite link),
 * reissuing links, and accepting invites. Pure transformations of [SharingSettings]; the Survey
 * editor keeps the result in its draft until it's published.
 */
class InviteCollaboratorUseCase(private val newInviteToken: () -> String = InviteLinks::newToken) {

  /** Result of a change to the access list. */
  sealed interface Outcome {
    data class Updated(val sharing: SharingSettings) : Outcome

    /** The change was refused, for [reason]. */
    data class Rejected(val reason: Rejection) : Outcome
  }

  /** Why a change to the access list was refused. */
  enum class Rejection {
    INVALID_EMAIL,
    /** The address is the survey owner's. */
    IS_OWNER,
    NAME_REQUIRED,
    /** The person accepting isn't on the access list. */
    NOT_INVITED,
    ALREADY_JOINED,
  }

  /**
   * Adds [email] to [sharing] with [role], or updates their role if they're already listed. New
   * people get a pending invite with a fresh invite link token.
   */
  fun invite(sharing: SharingSettings, email: String, role: CollaboratorRole): Outcome {
    val normalized = email.trim().lowercase()
    if (!EMAIL.matches(normalized)) return Outcome.Rejected(Rejection.INVALID_EMAIL)
    if (normalized == sharing.ownerEmail.lowercase()) {
      return Outcome.Rejected(Rejection.IS_OWNER)
    }
    val existing = sharing.collaborators.indexOfFirst { it.email.lowercase() == normalized }
    val updated = sharing.collaborators.toMutableList()
    if (existing >= 0) {
      updated[existing] = updated[existing].copy(role = role)
    } else {
      updated += Collaborator(normalized, role, inviteToken = newInviteToken())
    }
    return Outcome.Updated(sharing.copy(collaborators = updated))
  }

  /** Invalidates the old invite link of pending collaborator [email] and issues a new one. */
  fun resetInviteLink(sharing: SharingSettings, email: String): SharingSettings =
    sharing.update(email) {
      if (it.status == InvitationStatus.PENDING) it.copy(inviteToken = newInviteToken()) else it
    }

  /**
   * Simulates invitee [email] opening their invite link, signing in, and accepting. Their account's
   * [displayName] and [photoUrl] are cached on the access list entry for display.
   */
  fun acceptInvite(
    sharing: SharingSettings,
    email: String,
    displayName: String,
    photoUrl: String?,
    cachedOn: String = "",
  ): Outcome {
    val name = displayName.trim()
    if (name.isEmpty()) return Outcome.Rejected(Rejection.NAME_REQUIRED)
    val person =
      sharing.collaborators.firstOrNull { it.email == email }
        ?: return Outcome.Rejected(Rejection.NOT_INVITED)
    if (person.status == InvitationStatus.ACCEPTED)
      return Outcome.Rejected(Rejection.ALREADY_JOINED)
    return Outcome.Updated(
      sharing.update(email) {
        it.copy(
          status = InvitationStatus.ACCEPTED,
          inviteToken = null,
          userId = "uid-${email.substringBefore('@').replace('.', '-')}",
          profile = CachedProfile(name, photoUrl, cachedOn),
        )
      }
    )
  }

  private fun SharingSettings.update(
    email: String,
    transform: (Collaborator) -> Collaborator,
  ): SharingSettings =
    copy(collaborators = collaborators.map { if (it.email == email) transform(it) else it })

  private companion object {
    val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }
}
