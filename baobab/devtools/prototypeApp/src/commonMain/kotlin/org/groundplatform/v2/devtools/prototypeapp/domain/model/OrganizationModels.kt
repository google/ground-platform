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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Role of a person in an [Organization] (the Ground 2.0 counterpart of a Collect Earth Online
 * institution affiliation).
 */
enum class OrganizationRole(val label: String, val description: String) {
  MEMBER("Member", "Listed as affiliated, can see the organization's surveys and create new ones."),
  MANAGER(
    "Manager",
    "Can edit the organization, manage members, and manage all of its surveys.",
  ),
}

/** Lifecycle of a person's membership in an [Organization]. */
enum class MembershipStatus(val label: String) {
  /** A Manager sent an invite link; waiting for the person to accept it. */
  INVITED("Invited"),
  /** The person asked to join; waiting for a Manager to approve. */
  REQUESTED("Requested to join"),
  /** Full member. */
  ACTIVE("Member"),
}

/** One person's membership in an [Organization]. */
data class OrganizationMember(
  val email: String,
  val role: OrganizationRole = OrganizationRole.MEMBER,
  val status: MembershipStatus = MembershipStatus.ACTIVE,
  /** Secret part of the invite link (`…/join/<token>`); cleared once accepted. */
  val inviteToken: String? = null,
  /** The person's account ID, known only after they sign in and accept. */
  val userId: String? = null,
  val profile: CachedProfile? = null,
) {
  /** Full name when known, otherwise the email address. */
  val displayName: String
    get() = profile?.displayName?.takeIf { it.isNotBlank() } ?: email

  val isActive: Boolean
    get() = status == MembershipStatus.ACTIVE

  val isActiveManager: Boolean
    get() = isActive && role == OrganizationRole.MANAGER
}

/** Kind of imagery source an [Organization] can configure in the organization editor. */
enum class ImagerySourceType(val label: String) {
  /** Web Mercator XYZ raster tile URL template (`https://.../{z}/{x}/{y}.png`). */
  XYZ_TILES("XYZ tile URL")
}

/**
 * A custom imagery layer configured on an [Organization] (including the synthetic `"All users"`
 * organization) that can be toggled in the basemap layers dialog on mobile and web.
 */
data class ImagerySource(
  val id: String,
  val name: String,
  val urlTemplate: String,
  val type: ImagerySourceType = ImagerySourceType.XYZ_TILES,
  /** Whether collectors on mobile are permitted to download tiles from this source offline. */
  val allowOfflineDownload: Boolean = false,
) {
  /** True when [urlTemplate] is an `http(s)://` URL with `{z}`, `{x}`, and `{y}` placeholders. */
  val isValidXyzUrl: Boolean
    get() = isValidXyzUrlTemplate(urlTemplate)

  companion object {
    fun isValidXyzUrlTemplate(url: String): Boolean {
      val trimmed = url.trim()
      val hasScheme =
        trimmed.startsWith("https://", ignoreCase = true) ||
          trimmed.startsWith("http://", ignoreCase = true)
      return hasScheme &&
        trimmed.contains("{z}", ignoreCase = true) &&
        trimmed.contains("{x}", ignoreCase = true) &&
        trimmed.contains("{y}", ignoreCase = true)
    }
  }
}

/**
 * Multi-tenant organization that surveys can optionally belong to (`SurveyDef.organization_id`): a
 * ministry, university lab, NGO, or community of practice. Mirrors Collect Earth Online
 * institutions.
 *
 * There is no separate owner: any active [OrganizationRole.MANAGER] can administer the
 * organization, and the last Manager can't be removed or demoted.
 */
data class Organization(
  val id: String,
  val name: String,
  val description: String = "",
  val websiteUrl: String = "",
  /** Logo image URL, or an `avatar:<n>` placeholder in the prototype. */
  val logoUrl: String? = null,
  /** Whether the organization appears in the public directory, where anyone can ask to join. */
  val isListed: Boolean = true,
  val members: List<OrganizationMember> = emptyList(),
  val createdOn: String = "",
  /** Custom imagery sources configured in the organization editor's `Imagery sources` tab. */
  val imagerySources: List<ImagerySource> = emptyList(),
  /**
   * True for the synthetic `"All users"` organization, which holds the platform-wide imagery
   * sources and the global library rather than survey memberships.
   *
   * Everyone is implicitly a read-only member of it: people can't leave, ask to join, or be invited
   * as [OrganizationRole.MEMBER], and it's never listed in the directory. Its explicit
   * [OrganizationRole.MANAGER]s are the deployment's platform admins: they edit the global library
   * and imagery sources, and get no access to other organizations' surveys.
   */
  val isSynthetic: Boolean = false,
) {
  val activeMembers: List<OrganizationMember>
    get() = members.filter { it.isActive }

  val managers: List<OrganizationMember>
    get() = members.filter { it.isActiveManager }

  val pendingRequests: List<OrganizationMember>
    get() = members.filter { it.status == MembershipStatus.REQUESTED }

  fun member(email: String): OrganizationMember? = members.firstOrNull {
    it.email.equals(email, ignoreCase = true)
  }

  /** Role of [email] if they are an active member, otherwise `null`. */
  fun roleOf(email: String): OrganizationRole? = member(email)?.takeIf { it.isActive }?.role

  /**
   * Whether [email] is an active Manager. For the synthetic `"All users"` organization, these are
   * the platform admins.
   */
  fun isManager(email: String): Boolean = roleOf(email) == OrganizationRole.MANAGER

  /**
   * Whether [email] is an active member who can see and create surveys in this organization. Always
   * false for the synthetic `"All users"` organization, which hosts no surveys: everyone is
   * implicitly a read-only member of it (see [isVisibleTo]), and its Managers are platform admins
   * (see [isManager]), not survey members.
   */
  fun isMember(email: String): Boolean = !isSynthetic && roleOf(email) != null

  /**
   * Whether [email] can see this organization: its members, plus everyone for the synthetic one.
   */
  fun isVisibleTo(email: String): Boolean = isSynthetic || isMember(email)

  companion object {
    /** Stable ID of the synthetic `"All users"` organization. */
    const val ALL_USERS_ID = "org-all-users"
  }
}
