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

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.QuotaTier

class ProtoOrganizationMapperRoundTripTest {
  private val quotaLimits =
    QuotaLimitsConfig(
      tier = QuotaTier.SPONSORED,
      maxSurveysPerOwner = 1,
      maxFormsPerSurvey = 2,
      maxQuestionsPerForm = 3,
      maxPhotoQuestionsPerForm = 4,
      maxPredefinedEntities = 5,
      maxAclUsers = 6,
      maxSurveysPerUser = 7,
      maxAdhocEntitiesPerUser = 8,
      maxSubmissionsPerEntityPerUser = 9,
      maxGeneratedEntities = 10,
      maxAreaVertices = 11,
    )

  @Test
  fun quotaLimits_roundTrip() {
    val proto = ProtoOrganizationMapper.quotaLimitsToProto(quotaLimits)

    assertEquals(quotaLimits, ProtoOrganizationMapper.quotaLimitsFromProto(proto))
  }

  @Test
  fun cachedProfile_roundTrips() {
    val profile =
      CachedProfile(
        displayName = "Amina Otieno",
        photoUrl = "https://example.org/amina.png",
        cachedOn = "2026-02-01T08:30:00Z",
      )

    val proto = ProtoOrganizationMapper.cachedProfileToProto(profile)

    assertEquals(profile, ProtoOrganizationMapper.cachedProfileFromProto(proto))
  }

  @Test
  fun organization_roundTripsWithMembersImageryAndQuotas() {
    val organization =
      Organization(
        id = "org-1",
        name = "Highland Coffee Cooperative",
        description = "Smallholder coffee growers",
        websiteUrl = "https://example.org",
        logoUrl = "https://example.org/logo.png",
        isListed = false,
        members =
          listOf(
            OrganizationMember(
              email = "manager@example.org",
              role = OrganizationRole.MANAGER,
              status = MembershipStatus.ACTIVE,
              userId = "user-1",
              profile = CachedProfile(displayName = "Amina Otieno", cachedOn = "2026-02-01"),
            ),
            OrganizationMember(
              email = "invitee@example.org",
              status = MembershipStatus.INVITED,
              inviteToken = "invite-token",
            ),
          ),
        createdOn = "2026-03-01",
        imagerySources =
          listOf(
            ImagerySource(
              id = "imagery-osm",
              name = "OpenStreetMap",
              urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
              type = ImagerySourceType.XYZ_TILES,
              allowOfflineDownload = true,
            )
          ),
        organizationType = OrganizationType.COOPERATIVE,
        countryCode = "KE",
        excludeFromPlatformAggregates = true,
        quotaLimits = quotaLimits,
      )

    val proto = ProtoOrganizationMapper.organizationToProto(organization)

    assertEquals(organization, ProtoOrganizationMapper.organizationFromProto(proto))
  }
}
