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
package org.groundplatform.v2.devtools.prototypeapp

import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem

/** Filter chips on the web surveys page. */
sealed interface SurveyListFilter {
  val label: String

  /** Every survey the user can open. */
  data object All : SurveyListFilter {
    override val label = "All"
  }

  /** Surveys the user owns. */
  data object Mine : SurveyListFilter {
    override val label = "My surveys"
  }

  /** Surveys that belong to one organization the user is a member of. */
  data class InOrganization(val organization: Organization) : SurveyListFilter {
    override val label = organization.name
  }

  companion object {
    /** The chips to show [userEmail]: `All`, `My surveys`, then one per organization they're in. */
    fun chipsFor(organizations: List<Organization>, userEmail: String): List<SurveyListFilter> =
      listOf(All, Mine) + organizations.filter { it.isMember(userEmail) }.map { InOrganization(it) }
  }
}

/** How the signed-in user relates to a survey, shown as a chip on its card. */
enum class SurveyAccessLabel(val label: String) {
  OWNER("Owner"),
  ORGANIZATION_MANAGER("Organization manager"),
  ORGANIZATION_MEMBER("Organization member"),
  SHARED("Shared with you"),
}

/** A group of survey cards under one heading on the web surveys page. */
data class SurveyListSection(
  /** The organization the surveys belong to, or `null` for personal surveys. */
  val organization: Organization?,
  val surveys: List<SurveyPreviewItem>,
) {
  val title: String
    get() = organization?.name ?: "Personal surveys"
}

/** Pure list logic behind the web surveys page, kept free of Compose so it can be unit tested. */
object WebSurveysList {
  /** Relationship between [userEmail] and [survey], given the [organizations] it may belong to. */
  fun accessLabel(
    survey: SurveyPreviewItem,
    organizations: List<Organization>,
    userEmail: String,
  ): SurveyAccessLabel {
    if (survey.ownerEmail.equals(userEmail, ignoreCase = true)) return SurveyAccessLabel.OWNER
    val organization = organizations.firstOrNull { it.id == survey.organizationId }
    return when (organization?.roleOf(userEmail)) {
      OrganizationRole.MANAGER -> SurveyAccessLabel.ORGANIZATION_MANAGER
      OrganizationRole.MEMBER -> SurveyAccessLabel.ORGANIZATION_MEMBER
      null -> SurveyAccessLabel.SHARED
    }
  }

  /** Surveys matching [filter] and the free-text [query] (title, description, location, or org). */
  fun filter(
    surveys: List<SurveyPreviewItem>,
    organizations: List<Organization>,
    userEmail: String,
    filter: SurveyListFilter,
    query: String = "",
  ): List<SurveyPreviewItem> {
    val trimmed = query.trim()
    return surveys.filter { survey ->
      val inFilter =
        when (filter) {
          SurveyListFilter.All -> true
          SurveyListFilter.Mine -> survey.ownerEmail.equals(userEmail, ignoreCase = true)
          is SurveyListFilter.InOrganization -> survey.organizationId == filter.organization.id
        }
      inFilter &&
        (trimmed.isEmpty() ||
          survey.title.contains(trimmed, ignoreCase = true) ||
          survey.description.contains(trimmed, ignoreCase = true) ||
          survey.location.contains(trimmed, ignoreCase = true) ||
          organizations
            .firstOrNull { it.id == survey.organizationId }
            ?.name
            ?.contains(trimmed, ignoreCase = true) == true)
    }
  }

  /**
   * Groups [surveys] into sections: one per organization in [organizations] order (only those with
   * surveys), then personal surveys last. Surveys whose organization is unknown count as personal.
   */
  fun sections(
    surveys: List<SurveyPreviewItem>,
    organizations: List<Organization>,
  ): List<SurveyListSection> {
    val byOrganization = surveys.groupBy { survey ->
      organizations.firstOrNull { it.id == survey.organizationId }
    }
    val organizationSections = organizations.mapNotNull { org ->
      byOrganization[org]?.let { SurveyListSection(organization = org, surveys = it) }
    }
    val personal =
      byOrganization[null]?.let { SurveyListSection(organization = null, surveys = it) }
    return organizationSections + listOfNotNull(personal)
  }
}
