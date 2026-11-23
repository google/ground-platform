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
package org.openforis.collectone.v2.app.domain.model

import google.earthengine.v1main.Expression
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.core.access.AccessCaller
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID

/**
 * Role of a person in an [Organization] (the Collect One counterpart of a Collect Earth Online
 * institution affiliation).
 */
enum class OrganizationRole {
  MEMBER,
  MANAGER,
}

/** Lifecycle of a person's membership in an [Organization]. */
enum class MembershipStatus {
  /** A Manager sent an invite link; waiting for the person to accept it. */
  INVITED,
  /** The person asked to join; waiting for a Manager to approve. */
  REQUESTED,
  /** Full member. */
  ACTIVE,
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
enum class ImagerySourceType {
  /** Web Mercator XYZ raster tile URL template (`https://.../{z}/{x}/{y}.png`). */
  XYZ_TILES,
  /** Server-computed Google Earth Engine tile layer. */
  EARTH_ENGINE,
}

/**
 * A custom imagery layer configured on an [Organization] (including the synthetic `"All users"`
 * organization) that can be toggled in the basemap layers dialog on mobile and web.
 */
data class ImagerySource(
  val id: String,
  val name: String,
  val urlTemplate: String = "",
  val type: ImagerySourceType = ImagerySourceType.XYZ_TILES,
  /** Whether collectors on mobile are permitted to download tiles from this source offline. */
  val allowOfflineDownload: Boolean = false,
  /** Serialized Earth Engine expression proto for server-side map rendering. */
  val expression: Expression? = null,
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
 * Kind of [Organization], optionally recorded on its profile (`Organization.organization_type`).
 */
enum class OrganizationType {
  GOVERNMENT_AGENCY,
  COOPERATIVE,
  IPLC_ORGANIZATION,
  NGO,
  COMPANY,
  RESEARCH,
  OTHER,
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
  /** Logo image URL, or an `avatar:<n>` placeholder until logos can be uploaded. */
  val logoUrl: String? = null,
  /** Whether the organization appears in the public directory, where anyone can ask to join. */
  val isListed: Boolean = true,
  val members: List<OrganizationMember> = emptyList(),
  val createdOn: String = "",
  /** Custom imagery sources configured in the organization editor's `Imagery sources` tab. */
  val imagerySources: List<ImagerySource> = emptyList(),
  /** Kind of organization, or `null` when not specified (always `null` for `"All users"`). */
  val organizationType: OrganizationType? = null,
  /**
   * Upper-case ISO 3166-1 alpha-2 code of the country the organization is based in (see
   * [Countries]), or `null` when not specified (always `null` for `"All users"`).
   */
  val countryCode: String? = null,
  /**
   * Whether the organization's data is kept out of platform-wide impact numbers
   * (`Organization.exclude_from_platform_aggregates`): global, country, and grid cell totals and
   * organization-suggested indicators. Its own dashboards are unaffected. Always false for `"All
   * users"`.
   */
  val excludeFromPlatformAggregates: Boolean = false,
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
  val quotaLimits: QuotaLimitsConfig = QuotaLimitsConfig(),
) {
  val activeMembers: List<OrganizationMember>
    get() = members.filter { it.isActive }

  val managers: List<OrganizationMember>
    get() = members.filter { it.isActiveManager }

  val pendingRequests: List<OrganizationMember>
    get() = members.filter { it.status == MembershipStatus.REQUESTED }

  /**
   * Whether the profile has [organizationType] and [countryCode]. False for the synthetic `"All
   * users"` organization, which is platform-wide rather than a real-world organization.
   */
  val hasTypeAndCountry: Boolean
    get() = !isSynthetic

  /** The [Country] for [countryCode], or `null` when not specified or not a known code. */
  val country: Country?
    get() = Countries.byCode(countryCode)

  /**
   * Default [SurveyArea] for new surveys in this organization: the bounding box of [country], or
   * `null` when [countryCode] is not set.
   */
  fun defaultSurveyArea(): SurveyArea? = country?.surveyArea

  fun member(email: String, userId: String = ""): OrganizationMember? {
    val caller = AccessCaller(userId = userId, email = email)
    return members.firstOrNull { caller.matches(it.userId, it.email) }
  }

  /** Role of [email] or [userId] if they are an active member, otherwise `null`. */
  fun roleOf(email: String, userId: String = ""): OrganizationRole? =
    member(email, userId)?.takeIf { it.isActive }?.role

  /**
   * Whether [email] or [userId] is an active Manager. For the synthetic `"All users"` organization,
   * these are the platform admins.
   */
  fun isManager(email: String, userId: String = ""): Boolean =
    roleOf(email, userId) == OrganizationRole.MANAGER

  /**
   * Whether this is the platform-wide `"All users"` organization (identified by [ALL_USERS_ID] or
   * [isSynthetic]).
   */
  val isAllUsers: Boolean
    get() = id == ALL_USERS_ID || isSynthetic

  /**
   * Whether [email] or [userId] is a platform administrator (an active Manager of the `"All users"`
   * / [isSynthetic] organization).
   */
  fun isPlatformAdmin(email: String, userId: String = ""): Boolean =
    isAllUsers && isManager(email, userId)

  /**
   * Whether [email] or [userId] is an active member who can see and create surveys in this
   * organization. Always false for the synthetic `"All users"` organization, which hosts no
   * surveys: everyone is implicitly a read-only member of it (see [isVisibleTo]), and its Managers
   * are platform admins (see [isManager]), not survey members.
   */
  fun isMember(email: String, userId: String = ""): Boolean =
    !isSynthetic && roleOf(email, userId) != null

  /**
   * Whether [email] or [userId] can see this organization: its members, plus everyone for the
   * synthetic one.
   */
  fun isVisibleTo(email: String, userId: String = ""): Boolean =
    isSynthetic || isMember(email, userId)

  companion object {
    /** Stable ID of the synthetic `"All users"` organization. */
    const val ALL_USERS_ID = ALL_USERS_ORGANIZATION_ID
  }
}

/**
 * Whether [email] or [userId] manages the `"All users"` (or [Organization.isSynthetic])
 * organization in this collection, making them a platform administrator.
 */
fun Iterable<Organization>.hasPlatformAdmin(email: String, userId: String = ""): Boolean = any {
  it.isPlatformAdmin(email, userId)
}
