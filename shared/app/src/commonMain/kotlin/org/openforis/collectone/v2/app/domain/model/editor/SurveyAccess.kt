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
package org.openforis.collectone.v2.app.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.InviteLinks
import org.openforis.collectone.v2.app.domain.model.Organization

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
  private const val SURVEY_BASE_URL = "https://ground.example.org/surveys/"

  /** Direct URL for opening or sharing a survey by [surveyId]. */
  fun surveyUrl(surveyId: String): String = "$SURVEY_BASE_URL${surveyId.ifBlank { "draft" }}"

  /**
   * The survey ID in [text] when it is a survey link ([surveyUrl], from any host, optionally with a
   * query or fragment) or a bare survey ID (a UUID), or `null` otherwise.
   */
  fun surveyIdFromLink(text: String): String? {
    val trimmed = text.trim()
    val path = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
    val candidate =
      if ("/surveys/" in path) path.substringAfterLast("/surveys/") else path.takeIf { '/' !in it }
    return candidate?.takeIf { SURVEY_ID.matches(it) }
  }

  private val SURVEY_ID =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

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

/** Direct URL for opening or sharing a survey by [surveyId]. */
fun InviteLinks.surveyUrl(surveyId: String): String = SurveyAccess.surveyUrl(surveyId)
