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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings

/**
 * Resolves what a person may do in a survey from its sharing settings and the organization it
 * belongs to. Pure functions, shared by the web editor, the survey list, and tests.
 *
 * Access sources, from strongest to weakest:
 * 1. The survey owner is always a [CollaboratorRole.SURVEY_ORGANIZER].
 * 2. People on the survey's access control list get their accepted role.
 * 3. Managers of the survey's organization inherit [CollaboratorRole.SURVEY_ORGANIZER].
 * 4. With [SharingPolicy.ORGANIZATION], every active member of the organization can collect data.
 * 5. With [SharingPolicy.ANYONE_WITH_LINK] or [SharingPolicy.PUBLIC], anyone can collect data.
 */
object SurveyAccess {

  /**
   * The strongest role [email] has in a survey with [sharing] that belongs to [organization] (or
   * `null` for a personal survey), or `null` if they can't open it at all.
   */
  fun roleOf(
    email: String,
    sharing: SharingSettings,
    organization: Organization?,
  ): CollaboratorRole? {
    if (email.isNotBlank() && email.equals(sharing.ownerEmail, ignoreCase = true)) {
      return CollaboratorRole.SURVEY_ORGANIZER
    }
    val candidates = mutableListOf<CollaboratorRole>()
    sharing.collaborators
      .firstOrNull {
        it.email.equals(email, ignoreCase = true) && it.status == InvitationStatus.ACCEPTED
      }
      ?.let { candidates += it.role }
    if (organization != null && email.isNotBlank()) {
      if (organization.isManager(email)) candidates += CollaboratorRole.SURVEY_ORGANIZER
      if (sharing.policy == SharingPolicy.ORGANIZATION && organization.isMember(email)) {
        candidates += CollaboratorRole.DATA_COLLECTOR
      }
    }
    if (
      sharing.policy == SharingPolicy.ANYONE_WITH_LINK || sharing.policy == SharingPolicy.PUBLIC
    ) {
      candidates += CollaboratorRole.DATA_COLLECTOR
    }
    return candidates.maxByOrNull { it.ordinal }
  }

  /** Whether [email] can open the Survey editor for a survey with [sharing] in [organization]. */
  fun canManage(email: String, sharing: SharingSettings, organization: Organization?): Boolean =
    roleOf(email, sharing, organization) == CollaboratorRole.SURVEY_ORGANIZER

  /**
   * Problems in [sharing] given the survey's [organizationId], shown in the Sharing pane and
   * counted as publish-blocking issues.
   */
  fun issues(sharing: SharingSettings, organizationId: String?): List<String> = buildList {
    if (sharing.policy == SharingPolicy.ORGANIZATION && organizationId == null) {
      add(
        "\"${SharingPolicy.ORGANIZATION.label}\" needs an organization. Choose one in Survey " +
          "details or pick another general access option."
      )
    }
  }
}
