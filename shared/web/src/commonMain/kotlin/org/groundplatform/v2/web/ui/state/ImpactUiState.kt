/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.web.ui.state

import org.groundplatform.v2.app.domain.model.Country
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.web.domain.model.ImpactSummary

/** Survey and country filter of an organization's Impact view; `null` means all. */
data class ImpactFilter(val surveyId: String? = null, val countryCode: String? = null) {
  val isActive: Boolean
    get() = surveyId != null || countryCode != null
}

/** One organization's Impact view: its numbers under the current filter, and the filter choices. */
data class OrganizationImpactUiState(
  val summary: ImpactSummary,
  val filter: ImpactFilter = ImpactFilter(),
  /** Surveys to filter by. */
  val surveys: List<SurveyPreviewItem> = emptyList(),
  /** Countries its map features are in, to filter by. */
  val countries: List<Country> = emptyList(),
)

/** What the Impact page's content pane shows. */
sealed interface ImpactDashboardFocus {
  /** Headline numbers of every organization on the signed-in user's dashboard. */
  data object Overview : ImpactDashboardFocus

  /** Numbers across all of Collect One. */
  data object Platform : ImpactDashboardFocus

  /** One organization's numbers. */
  data class OrganizationFocus(val organizationId: String) : ImpactDashboardFocus
}

/**
 * An organization people can put on their Impact page, with the numbers it publishes. Every
 * organization publishes anonymous totals; nobody outside it sees its submissions, map features, or
 * answers.
 */
data class ImpactDashboardOrganization(
  val organization: Organization,
  /** Whether the signed-in user is a member, and so can also see its full numbers. */
  val isMember: Boolean,
  /** Whether the signed-in user is one of its Managers (Organizers). */
  val isManager: Boolean,
  /** Its published numbers, under its country filter. */
  val published: OrganizationImpactUiState,
) {
  val id: String
    get() = organization.id
}

/**
 * The Impact page (`#impact`): published numbers of the organizations the signed-in user chose to
 * follow, and of all of Collect One.
 */
data class ImpactDashboardUiState(
  /**
   * Organizations the signed-in user can add, by name: listed organizations plus the ones they
   * belong to (unlisted organizations stay hidden from everyone else).
   */
  val available: List<ImpactDashboardOrganization> = emptyList(),
  /** IDs of the organizations on the user's dashboard, in the order they were added. */
  val selectedIds: List<String> = emptyList(),
  val focus: ImpactDashboardFocus = ImpactDashboardFocus.Overview,
  /**
   * Whether members see the published view of their own organizations (what everyone else sees)
   * instead of their full numbers.
   */
  val isPreviewingPublished: Boolean = false,
) {
  /** The organizations on the user's dashboard, in [selectedIds] order. */
  val selected: List<ImpactDashboardOrganization>
    get() = selectedIds.mapNotNull(::organization)

  fun organization(id: String): ImpactDashboardOrganization? = available.firstOrNull { it.id == id }
}

/**
 * UI state of the Impact views: the Impact page, the survey Impact pane, and the platform-wide
 * view. Numbers are recomputed shortly after the underlying data changes.
 */
data class ImpactUiState(
  /** Whether the first computation hasn't finished yet. */
  val isLoading: Boolean = true,
  /** Numbers of every survey, by survey ID. */
  val surveys: Map<String, ImpactSummary> = emptyMap(),
  /**
   * Full numbers of the organizations the signed-in user belongs to, by organization ID. Everyone
   * else sees an organization's published numbers ([ImpactDashboardOrganization.published]).
   */
  val organizations: Map<String, OrganizationImpactUiState> = emptyMap(),
  /**
   * Platform-wide numbers: the full view for Managers of `"All users"`, the published view
   * ([ImpactSummary.isPublished]) for everyone else.
   */
  val global: ImpactSummary? = null,
  /** The Impact page. */
  val dashboard: ImpactDashboardUiState = ImpactDashboardUiState(),
  /** Message about a finished download, shown in a snackbar. */
  val message: String? = null,
)
