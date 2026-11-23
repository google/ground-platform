/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.core.mutation

import openforis.collectone.v1.survey.ImagerySource
import openforis.collectone.v1.survey.MembershipStatus
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.OrganizationType
import openforis.collectone.v1.survey.QuotaLimits

/** Applies sparse `Organization` `delete` and `upsert` masks. */
object OrganizationMutations {
  /** Applies [delete] then merges [upsert]. */
  fun apply(
    org: Organization,
    delete: Organization? = null,
    upsert: Organization? = null,
  ): Organization {
    val afterDelete = if (delete != null) applyDelete(org, delete) else org
    return if (upsert != null) merge(afterDelete, upsert) else afterDelete
  }

  fun applyDelete(org: Organization, delete: Organization): Organization {
    var o = org
    if (delete.name.isNotEmpty()) o = o.copy(name = "")
    if (delete.description.isNotEmpty()) o = o.copy(description = "")
    if (delete.website_url.isNotEmpty()) o = o.copy(website_url = "")
    if (delete.logo_url.isNotEmpty()) o = o.copy(logo_url = "")
    if (delete.listed) o = o.copy(listed = false)
    if (delete.created_at != null) o = o.copy(created_at = null)
    if (delete.organization_type != OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED) {
      o = o.copy(organization_type = OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED)
    }
    if (delete.country_code.isNotEmpty()) o = o.copy(country_code = "")
    if (delete.exclude_from_platform_aggregates) {
      o = o.copy(exclude_from_platform_aggregates = false)
    }
    if (delete.is_deleted) o = o.copy(is_deleted = false)
    if (delete.quota_limits != null) {
      val q = delete.quota_limits
      o =
        if (q == QuotaLimits()) o.copy(quota_limits = null)
        else o.copy(quota_limits = o.quota_limits?.let { SurveyMutations.applyQuotaDelete(it, q) })
    }
    if (delete.imagery_sources.isNotEmpty()) {
      o =
        o.copy(
          imagery_sources =
            o.imagery_sources.applyKeyedDelete(
              deleteItems = delete.imagery_sources,
              keyOf = { it.imagery_source_id },
              isLeaf = { it == ImagerySource(imagery_source_id = it.imagery_source_id) },
              applyBranchDelete = { b, d ->
                var s = b
                if (d.name.isNotEmpty()) s = s.copy(name = "")
                if (d.xyz_tiles != null) s = s.copy(xyz_tiles = null)
                s
              },
            )
        )
    }
    if (delete.members.isNotEmpty()) {
      o =
        o.copy(
          members =
            o.members.applyKeyedDelete(
              deleteItems = delete.members,
              keyOf = MutationKeys::memberKey,
              isLeaf = {
                it.role == OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED &&
                  it.status == MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED &&
                  it.updated_at == null &&
                  it.invite_token.isEmpty() &&
                  it.profile == null
              },
              applyBranchDelete = { b, d ->
                var m = b
                if (d.role != OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED) {
                  m = m.copy(role = OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED)
                }
                if (d.status != MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED) {
                  m = m.copy(status = MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED)
                }
                if (d.invite_token.isNotEmpty()) m = m.copy(invite_token = "")
                if (d.profile != null) m = m.copy(profile = null)
                m
              },
            )
        )
    }
    return o
  }

  fun merge(base: Organization, upsert: Organization): Organization {
    var o = base
    if (upsert.organization_id.isNotEmpty()) o = o.copy(organization_id = upsert.organization_id)
    if (upsert.name.isNotEmpty()) o = o.copy(name = upsert.name)
    if (upsert.description.isNotEmpty()) o = o.copy(description = upsert.description)
    if (upsert.website_url.isNotEmpty()) o = o.copy(website_url = upsert.website_url)
    if (upsert.logo_url.isNotEmpty()) o = o.copy(logo_url = upsert.logo_url)
    if (upsert.listed) o = o.copy(listed = true)
    if (upsert.created_at != null) o = o.copy(created_at = upsert.created_at)
    if (upsert.organization_type != OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED) {
      o = o.copy(organization_type = upsert.organization_type)
    }
    if (upsert.country_code.isNotEmpty()) o = o.copy(country_code = upsert.country_code)
    if (upsert.exclude_from_platform_aggregates) {
      o = o.copy(exclude_from_platform_aggregates = true)
    }
    if (upsert.is_deleted) o = o.copy(is_deleted = true)
    if (upsert.quota_limits != null) {
      o =
        o.copy(
          quota_limits =
            o.quota_limits?.let { SurveyMutations.mergeQuotaLimits(it, upsert.quota_limits) }
              ?: upsert.quota_limits
        )
    }
    if (upsert.imagery_sources.isNotEmpty()) {
      o =
        o.copy(
          imagery_sources =
            o.imagery_sources.mergeKeyedUpsert(
              upsertItems = upsert.imagery_sources,
              keyOf = { it.imagery_source_id },
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (upsert.members.isNotEmpty()) {
      o =
        o.copy(
          members =
            o.members.mergeKeyedUpsert(
              upsertItems = upsert.members,
              keyOf = MutationKeys::memberKey,
              mergeItem = { b, u ->
                b.copy(
                  user_id = u.user_id.ifBlank { b.user_id },
                  email = u.email.ifBlank { b.email },
                  role =
                    if (u.role != OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED) u.role
                    else b.role,
                  status =
                    if (u.status != MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED) u.status
                    else b.status,
                  updated_at = u.updated_at ?: b.updated_at,
                  invite_token = u.invite_token.ifBlank { b.invite_token },
                  profile = u.profile ?: b.profile,
                  id = u.id.ifBlank { b.id },
                  sort_order = if (u.sort_order != 0) u.sort_order else b.sort_order,
                )
              },
              orderOf = { it.sort_order },
            )
        )
    }
    return o
  }
}
