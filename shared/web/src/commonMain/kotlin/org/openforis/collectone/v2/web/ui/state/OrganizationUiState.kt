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
package org.openforis.collectone.v2.web.ui.state

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.web.domain.model.OrganizationMembersView
import org.openforis.collectone.v2.web.domain.model.OrganizationRelation
import org.openforis.collectone.v2.web.domain.model.canChangeMember
import org.openforis.collectone.v2.web.domain.model.discoverableBy
import org.openforis.collectone.v2.web.domain.model.membersViewFor
import org.openforis.collectone.v2.web.domain.model.organizationsOf
import org.openforis.collectone.v2.web.domain.model.relationTo
import org.openforis.collectone.v2.web.domain.model.searchOrganizations

/**
 * Screen state of the web organizations directory (`#organizations`) and organization page
 * (`#organization/<id>`), observed as an immutable snapshot.
 */
data class OrganizationUiState(
  // --- Data (from the local data store) ---
  /** All organizations in the local data store, in display order. */
  val organizations: List<Organization> = emptyList(),
  /** Every survey in the store; the organization page lists the ones in its organization. */
  val surveys: List<SurveyPreviewItem> = emptyList(),
  val activeSurveyId: String = "",
  val isSignedIn: Boolean = false,
  val signedInUserEmail: String = "",
  val signedInUserName: String = "",

  // --- Session ---
  /** ID of the organization shown on the organization page, or `null`. */
  val openOrganizationId: String? = null,
  /** Notice from the last organization action (e.g. a refused change), shown on the pages. */
  val notice: String? = null,
  /** Library of the open organization (Dictionary, Templates, and Purposes tabs). */
  val library: OrganizationLibraryUiState = OrganizationLibraryUiState(),
  /** Whether an in-flight organization or library remote mutation is in progress. */
  val isSaving: Boolean = false,
) {
  /** The organization shown on the organization page, if it still exists. */
  val openOrganization: Organization?
    get() = organization(openOrganizationId)

  /**
   * Whether the signed-in user is a platform administrator (i.e. Manager of the synthetic "All
   * users" organization).
   */
  val isPlatformAdmin: Boolean
    get() = organizations.any {
      it.id == Organization.ALL_USERS_ID && it.isManager(signedInUserEmail)
    }

  /** Organizations the signed-in user is an active member of. */
  val signedInUserOrganizations: List<Organization>
    get() = organizations.filter { it.isMember(signedInUserEmail) }

  /**
   * Organizations the signed-in user belongs to (excluding the synthetic `"All users"`
   * organization), the ones they manage first, then by name.
   */
  val myOrganizations: List<Organization>
    get() = organizations.filterNot { it.isSynthetic }.organizationsOf(signedInUserEmail)

  /** Listed organizations the signed-in user can ask to join, by name. */
  val discoverableOrganizations: List<Organization>
    get() = organizations.discoverableBy(signedInUserEmail)

  fun organization(organizationId: String?): Organization? = organizationId?.let { id ->
    organizations.firstOrNull { it.id == id }
  }

  /** [myOrganizations] filtered by [query] (name or description). */
  fun searchMyOrganizations(query: String): List<Organization> =
    myOrganizations.searchOrganizations(query)

  /** [discoverableOrganizations] filtered by [query] (name or description). */
  fun searchDiscoverableOrganizations(query: String): List<Organization> =
    discoverableOrganizations.searchOrganizations(query)

  /** Surveys that belong to [organizationId]. */
  fun surveysInOrganization(organizationId: String): List<SurveyPreviewItem> = surveys.filter {
    it.organizationId == organizationId
  }

  fun surveyCountInOrganization(organizationId: String): Int =
    surveysInOrganization(organizationId).size

  /** Whether the signed-in user manages [organization]. */
  fun managesOrganization(organization: Organization): Boolean =
    isSignedIn && organization.isManager(signedInUserEmail)

  /** Whether the signed-in user is an active member of [organization]. */
  fun isMemberOf(organization: Organization): Boolean = organization.isMember(signedInUserEmail)

  /** The signed-in user's relation to [organization] (for the directory's badges). */
  fun relationTo(organization: Organization): OrganizationRelation =
    organization.relationTo(signedInUserEmail)

  /** [organization]'s members grouped for display to the signed-in user. */
  fun membersView(organization: Organization): OrganizationMembersView =
    organization.membersViewFor(signedInUserEmail)

  /** Whether [member]'s role can be changed or they can be removed from [organization]. */
  fun canChangeMember(organization: Organization, member: OrganizationMember): Boolean =
    organization.canChangeMember(member)

  /** Whether [email] is the signed-in user's address. */
  fun isSelf(email: String): Boolean = email.equals(signedInUserEmail, ignoreCase = true)
}

/** One-off outcomes of organization actions that the app shell applies outside this slice. */
sealed interface OrganizationEvent {
  /** The organization page for [organizationId] was requested; the shell shows that page. */
  data class OrganizationOpened(val organizationId: String) : OrganizationEvent

  /** The platform admin panel was requested; the shell shows that page. */
  data object AdminPanelOpened : OrganizationEvent

  /** The organizations directory was requested; the shell shows that page. */
  data object OrganizationsOpened : OrganizationEvent

  /**
   * The imagery source with [sourceId] was removed from its organization; the shell turns it off on
   * the map if it was enabled.
   */
  data class ImagerySourceRemoved(val sourceId: String) : OrganizationEvent
}
