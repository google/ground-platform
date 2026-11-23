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
package org.openforis.collectone.v2.web.ui.surveys

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_personal_surveys
import org.openforis.collectone.v2.web.resources.web_survey_access_organization_manager
import org.openforis.collectone.v2.web.resources.web_survey_access_organization_member
import org.openforis.collectone.v2.web.resources.web_survey_access_owner
import org.openforis.collectone.v2.web.resources.web_survey_access_shared
import org.openforis.collectone.v2.web.resources.web_survey_filter_all
import org.openforis.collectone.v2.web.resources.web_survey_filter_mine

/** Filter chips on the web surveys page. */
sealed interface SurveyListFilter {
  /** Every survey the user can open. */
  data object All : SurveyListFilter

  /** Surveys the user owns or that belong to the personal surveys section. */
  data object Mine : SurveyListFilter

  /** Surveys that belong to one organization the user is a member of. */
  data class InOrganization(val organization: Organization) : SurveyListFilter

  companion object {
    /** The chips to show [userEmail]: `All`, `My surveys`, then one per organization they're in. */
    fun chipsFor(organizations: List<Organization>, userEmail: String): List<SurveyListFilter> =
      listOf(All, Mine) + organizations.filter { it.isMember(userEmail) }.map { InOrganization(it) }
  }
}

/** Display label of this filter chip: `All`, `My surveys`, or the organization's name. */
val SurveyListFilter.label: String
  @Composable
  get() =
    when (this) {
      SurveyListFilter.All -> stringResource(Res.string.web_survey_filter_all)
      SurveyListFilter.Mine -> stringResource(Res.string.web_survey_filter_mine)
      is SurveyListFilter.InOrganization -> organization.name
    }

/** How the signed-in user relates to a survey, shown as a chip on its card. */
enum class SurveyAccessLabel(val label: StringResource) {
  OWNER(Res.string.web_survey_access_owner),
  ORGANIZATION_MANAGER(Res.string.web_survey_access_organization_manager),
  ORGANIZATION_MEMBER(Res.string.web_survey_access_organization_member),
  SHARED(Res.string.web_survey_access_shared),
}

/** A group of survey cards under one heading on the web surveys page. */
data class SurveyListSection(
  /** The organization the surveys belong to, or `null` for personal surveys. */
  val organization: Organization?,
  val surveys: List<SurveyPreviewItem>,
) {
  val title: String
    @Composable get() = organization?.name ?: stringResource(Res.string.web_personal_surveys)
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
          SurveyListFilter.Mine ->
            survey.ownerEmail.equals(userEmail, ignoreCase = true) ||
              organizations.none { it.id == survey.organizationId }
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
