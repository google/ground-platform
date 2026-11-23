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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.survey.CachedProfile as CachedProfileProto
import openforis.collectone.v1.survey.EarthEngineTileSource
import openforis.collectone.v1.survey.ImagerySource as ImagerySourceProto
import openforis.collectone.v1.survey.MembershipStatus as MembershipStatusProto
import openforis.collectone.v1.survey.Organization as OrganizationProto
import openforis.collectone.v1.survey.OrganizationMember as OrganizationMemberProto
import openforis.collectone.v1.survey.OrganizationRole as OrganizationRoleProto
import openforis.collectone.v1.survey.OrganizationType as OrganizationTypeProto
import openforis.collectone.v1.survey.QuotaLimits as QuotaLimitsProto
import openforis.collectone.v1.survey.QuotaTier as QuotaTierProto
import openforis.collectone.v1.survey.XyzTileSource
import org.openforis.collectone.v2.app.data.datasource.local.LibraryProtoMapper
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.QuotaTier

/**
 * Bidirectional mapper between Wire-generated organization and library Protocol Buffer messages
 * (`openforis.collectone.v1.survey.Organization`, `openforis.collectone.v1.library.*`) and
 * `shared/app` domain models stored in `LocalStore`.
 */
object ProtoOrganizationMapper {
  // ---------------------------------------------------------------------------
  // Organization ↔ OrganizationProto
  // ---------------------------------------------------------------------------

  fun organizationToProto(
    org: Organization,
    version: Long = 1L,
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): OrganizationProto =
    OrganizationProto(
      organization_id = org.id,
      name = org.name,
      description = org.description,
      website_url = org.websiteUrl,
      logo_url = org.logoUrl.orEmpty(),
      listed = org.isListed,
      members = org.members.map(::memberToProto),
      created_at = parseIsoToInstant(org.createdOn),
      imagery_sources = org.imagerySources.map(::imagerySourceToProto),
      organization_type =
        when (org.organizationType) {
          OrganizationType.GOVERNMENT_AGENCY -> OrganizationTypeProto.GOVERNMENT_AGENCY
          OrganizationType.COOPERATIVE -> OrganizationTypeProto.COOPERATIVE
          OrganizationType.IPLC_ORGANIZATION -> OrganizationTypeProto.IPLC_ORGANIZATION
          OrganizationType.NGO -> OrganizationTypeProto.NGO
          OrganizationType.COMPANY -> OrganizationTypeProto.COMPANY
          OrganizationType.RESEARCH -> OrganizationTypeProto.RESEARCH
          OrganizationType.OTHER -> OrganizationTypeProto.OTHER
          null -> OrganizationTypeProto.ORGANIZATION_TYPE_UNSPECIFIED
        },
      country_code = org.countryCode.orEmpty(),
      exclude_from_platform_aggregates = org.excludeFromPlatformAggregates,
      quota_limits = quotaLimitsToProto(org.quotaLimits),
      version = version,
      is_deleted = isDeleted,
      commit_timestamp = commitTimestamp,
    )

  fun organizationFromProto(proto: OrganizationProto): Organization {
    val isSynthetic = proto.organization_id == Organization.ALL_USERS_ID
    return Organization(
      id = proto.organization_id,
      name = proto.name,
      description = proto.description,
      websiteUrl = proto.website_url,
      logoUrl = proto.logo_url.takeIf { it.isNotBlank() },
      isListed = proto.listed,
      members = proto.members.map(::memberFromProto),
      createdOn = formatInstantDateOrIso(proto.created_at),
      imagerySources = proto.imagery_sources.map(::imagerySourceFromProto),
      organizationType =
        when (proto.organization_type) {
          OrganizationTypeProto.GOVERNMENT_AGENCY -> OrganizationType.GOVERNMENT_AGENCY
          OrganizationTypeProto.COOPERATIVE -> OrganizationType.COOPERATIVE
          OrganizationTypeProto.IPLC_ORGANIZATION -> OrganizationType.IPLC_ORGANIZATION
          OrganizationTypeProto.NGO -> OrganizationType.NGO
          OrganizationTypeProto.COMPANY -> OrganizationType.COMPANY
          OrganizationTypeProto.RESEARCH -> OrganizationType.RESEARCH
          OrganizationTypeProto.OTHER -> OrganizationType.OTHER
          OrganizationTypeProto.ORGANIZATION_TYPE_UNSPECIFIED -> null
        },
      countryCode = proto.country_code.takeIf { it.isNotBlank() },
      excludeFromPlatformAggregates = proto.exclude_from_platform_aggregates,
      isSynthetic = isSynthetic,
      quotaLimits = quotaLimitsFromProto(proto.quota_limits),
    )
  }

  fun memberToProto(member: OrganizationMember): OrganizationMemberProto =
    OrganizationMemberProto(
      user_id = member.userId.orEmpty(),
      email = member.email,
      role =
        when (member.role) {
          OrganizationRole.MEMBER -> OrganizationRoleProto.MEMBER
          OrganizationRole.MANAGER -> OrganizationRoleProto.MANAGER
        },
      status =
        when (member.status) {
          MembershipStatus.INVITED -> MembershipStatusProto.INVITED
          MembershipStatus.REQUESTED -> MembershipStatusProto.REQUESTED
          MembershipStatus.ACTIVE -> MembershipStatusProto.ACTIVE
        },
      invite_token = member.inviteToken.orEmpty(),
      profile = member.profile?.let(::cachedProfileToProto),
    )

  fun memberFromProto(proto: OrganizationMemberProto): OrganizationMember =
    OrganizationMember(
      email = proto.email,
      role =
        when (proto.role) {
          OrganizationRoleProto.MANAGER -> OrganizationRole.MANAGER
          else -> OrganizationRole.MEMBER
        },
      status =
        when (proto.status) {
          MembershipStatusProto.INVITED -> MembershipStatus.INVITED
          MembershipStatusProto.REQUESTED -> MembershipStatus.REQUESTED
          else -> MembershipStatus.ACTIVE
        },
      inviteToken = proto.invite_token.takeIf { it.isNotBlank() },
      userId = proto.user_id.takeIf { it.isNotBlank() },
      profile = proto.profile?.let(::cachedProfileFromProto),
    )

  fun imagerySourceToProto(source: ImagerySource): ImagerySourceProto =
    when (source.type) {
      ImagerySourceType.EARTH_ENGINE ->
        ImagerySourceProto(
          imagery_source_id = source.id,
          name = source.name,
          earth_engine = EarthEngineTileSource(expression = source.expression),
        )
      ImagerySourceType.XYZ_TILES ->
        ImagerySourceProto(
          imagery_source_id = source.id,
          name = source.name,
          xyz_tiles =
            XyzTileSource(
              url_template = source.urlTemplate,
              allow_offline_download = source.allowOfflineDownload,
            ),
        )
    }

  fun imagerySourceFromProto(proto: ImagerySourceProto): ImagerySource {
    val ee = proto.earth_engine
    return if (ee != null) {
      ImagerySource(
        id = proto.imagery_source_id,
        name = proto.name,
        urlTemplate = "",
        type = ImagerySourceType.EARTH_ENGINE,
        allowOfflineDownload = false,
        expression = ee.expression,
      )
    } else {
      ImagerySource(
        id = proto.imagery_source_id,
        name = proto.name,
        urlTemplate = proto.xyz_tiles?.url_template.orEmpty(),
        type = ImagerySourceType.XYZ_TILES,
        allowOfflineDownload = proto.xyz_tiles?.allow_offline_download ?: false,
      )
    }
  }

  fun cachedProfileToProto(profile: CachedProfile): CachedProfileProto =
    CachedProfileProto(
      display_name = profile.displayName,
      avatar_url = profile.photoUrl.orEmpty(),
      cached_on = parseIsoToInstant(profile.cachedOn),
    )

  fun cachedProfileFromProto(proto: CachedProfileProto): CachedProfile =
    CachedProfile(
      displayName = proto.display_name,
      photoUrl = proto.avatar_url.takeIf { it.isNotBlank() },
      cachedOn = formatInstantDateOrIso(proto.cached_on),
    )

  // ---------------------------------------------------------------------------
  // QuotaLimits ↔ QuotaLimitsProto
  // ---------------------------------------------------------------------------

  fun quotaTierToProto(tier: QuotaTier): QuotaTierProto =
    when (tier) {
      QuotaTier.BASIC -> QuotaTierProto.BASIC
      QuotaTier.SPONSORED -> QuotaTierProto.SPONSORED
      QuotaTier.UNSPECIFIED -> QuotaTierProto.QUOTA_TIER_UNSPECIFIED
    }

  fun quotaTierFromProto(proto: QuotaTierProto?): QuotaTier =
    when (proto) {
      QuotaTierProto.BASIC -> QuotaTier.BASIC
      QuotaTierProto.SPONSORED -> QuotaTier.SPONSORED
      else -> QuotaTier.UNSPECIFIED
    }

  fun quotaLimitsToProto(limits: QuotaLimitsConfig): QuotaLimitsProto =
    QuotaLimitsProto(
      tier = quotaTierToProto(limits.tier),
      max_surveys_per_owner = limits.maxSurveysPerOwner,
      max_forms_per_survey = limits.maxFormsPerSurvey,
      max_questions_per_form = limits.maxQuestionsPerForm,
      max_photo_questions_per_form = limits.maxPhotoQuestionsPerForm,
      max_predefined_entities = limits.maxPredefinedEntities,
      max_acl_users = limits.maxAclUsers,
      max_surveys_per_user = limits.maxSurveysPerUser,
      max_adhoc_entities_per_user = limits.maxAdhocEntitiesPerUser,
      max_submissions_per_entity_per_user = limits.maxSubmissionsPerEntityPerUser,
      max_generated_entities = limits.maxGeneratedEntities,
      max_area_vertices = limits.maxAreaVertices,
    )

  fun quotaLimitsFromProto(proto: QuotaLimitsProto?): QuotaLimitsConfig {
    if (proto == null) return QuotaLimitsConfig()
    return QuotaLimitsConfig(
      tier = quotaTierFromProto(proto.tier),
      maxSurveysPerOwner = proto.max_surveys_per_owner,
      maxFormsPerSurvey = proto.max_forms_per_survey,
      maxQuestionsPerForm = proto.max_questions_per_form,
      maxPhotoQuestionsPerForm = proto.max_photo_questions_per_form,
      maxPredefinedEntities = proto.max_predefined_entities,
      maxAclUsers = proto.max_acl_users,
      maxSurveysPerUser = proto.max_surveys_per_user,
      maxAdhocEntitiesPerUser = proto.max_adhoc_entities_per_user,
      maxSubmissionsPerEntityPerUser = proto.max_submissions_per_entity_per_user,
      maxGeneratedEntities = proto.max_generated_entities,
      maxAreaVertices = proto.max_area_vertices,
    )
  }

  // ---------------------------------------------------------------------------
  // OrganizationLibrary ↔ OrganizationLibraryRecord
  // ---------------------------------------------------------------------------

  fun libraryToProto(
    library: OrganizationLibrary,
    version: Long = 1L,
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): OrganizationLibraryRecord =
    OrganizationLibraryRecord(
      organization_id = library.organizationId,
      bundle = LibraryProtoMapper.toBundle(library, includeOrganizationId = true),
      settings = LibraryProtoMapper.toProto(library.settings, library.organizationId),
      version = version,
      is_deleted = isDeleted,
      commit_timestamp = commitTimestamp,
    )

  fun libraryFromProto(proto: OrganizationLibraryRecord): OrganizationLibrary {
    val parsed =
      LibraryProtoMapper.fromBundle(
        bundle = proto.bundle ?: openforis.collectone.v1.library.LibraryBundle(),
        organizationId = proto.organization_id,
        settings =
          proto.settings?.let(LibraryProtoMapper::fromProto)
            ?: org.openforis.collectone.v2.app.domain.model.OrganizationLibrarySettings(),
      )
    return parsed.copy(
      formTemplates =
        parsed.formTemplates.map { tpl ->
          tpl.copy(
            form = tpl.form.copy(questions = tpl.form.questions.map { q -> q.copy(key = q.name) })
          )
        }
    )
  }
}
