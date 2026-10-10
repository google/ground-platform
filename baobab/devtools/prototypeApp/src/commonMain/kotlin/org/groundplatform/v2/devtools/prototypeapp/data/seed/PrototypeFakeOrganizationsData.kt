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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationMember
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole

/**
 * Sample organizations for the prototype. Includes the synthetic [ALL_USERS] organization (whose
 * imagery sources are available across all surveys), plus [KENYA_FOREST_SERVICE],
 * [MEKONG_MANGROVE_ALLIANCE], and the listed [OPEN_FORIS_COMMUNITY].
 */
internal object PrototypeFakeOrganizationsData {
  const val ALL_USERS = Organization.ALL_USERS_ID
  const val KENYA_FOREST_SERVICE = "org-kenya-forest-service"
  const val MEKONG_MANGROVE_ALLIANCE = "org-mekong-mangrove-alliance"
  const val OPEN_FORIS_COMMUNITY = "org-open-foris-community"

  /** Email of the prototype's signed-in user. */
  const val SIGNED_IN_EMAIL = "maya.lin@groundplatform.org"

  private val signedInProfile = CachedProfile("Maya Lin", "avatar:2", "2026-02-10")

  fun defaultOrganizations(): List<Organization> =
    listOf(
      Organization(
        id = ALL_USERS,
        name = "All users",
        description =
          "Synthetic platform-wide organization providing shared imagery sources and the global " +
            "library to every Ground user across all surveys.",
        logoUrl = "avatar:2",
        isListed = false,
        isSynthetic = true,
        createdOn = "2025-01-01",
        // Its Managers are the platform admins. The prototype's signed-in user is one, so the demo
        // can edit the global imagery sources and library; everyone else is a read-only member.
        members =
          listOf(
            OrganizationMember(
              email = SIGNED_IN_EMAIL,
              role = OrganizationRole.MANAGER,
              profile = signedInProfile,
              userId = "uid-maya-lin",
            )
          ),
        imagerySources =
          listOf(
            ImagerySource(
              id = "imagery-all-users-opentopomap",
              name = "OpenTopoMap Contours",
              urlTemplate = "https://tile.opentopomap.org/{z}/{x}/{y}.png",
              type = ImagerySourceType.XYZ_TILES,
              allowOfflineDownload = true,
            )
          ),
      ),
      Organization(
        id = KENYA_FOREST_SERVICE,
        name = "Kenya Forest Service",
        description =
          "National forest monitoring, agroforestry extension, and EUDR traceability programs " +
            "across Kenya's highland counties.",
        websiteUrl = "https://www.kenyaforestservice.org",
        logoUrl = "avatar:0",
        isListed = true,
        createdOn = "2026-01-12",
        imagerySources =
          listOf(
            ImagerySource(
              id = "imagery-kfs-hillshade",
              name = "Kenya Forest Hillshade",
              urlTemplate =
                "https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}",
              type = ImagerySourceType.XYZ_TILES,
              allowOfflineDownload = true,
            )
          ),
        members =
          listOf(
            OrganizationMember(
              email = SIGNED_IN_EMAIL,
              role = OrganizationRole.MANAGER,
              profile = signedInProfile,
              userId = "uid-maya-lin",
            ),
            OrganizationMember(
              email = "organizer@example.org",
              role = OrganizationRole.MANAGER,
              profile = CachedProfile("Amina Wanjiru", "avatar:3", "2026-01-12"),
              userId = "uid-organizer",
            ),
            OrganizationMember(
              email = "field.lead@example.org",
              role = OrganizationRole.MEMBER,
              profile = CachedProfile("Daniel Kiprop", "avatar:0", "2026-01-20"),
              userId = "uid-field-lead",
            ),
            OrganizationMember(
              email = "collector.one@example.org",
              role = OrganizationRole.MEMBER,
              profile = CachedProfile("Grace Njeri", "avatar:5", "2026-02-02"),
              userId = "uid-collector-one",
            ),
            OrganizationMember(
              email = "james.otieno@example.org",
              role = OrganizationRole.MEMBER,
              status = MembershipStatus.REQUESTED,
              profile = CachedProfile("James Otieno", "avatar:7"),
            ),
            OrganizationMember(
              email = "wanjiku.m@example.org",
              role = OrganizationRole.MEMBER,
              status = MembershipStatus.INVITED,
              inviteToken = "kfs-7f3a9c",
            ),
          ),
      ),
      Organization(
        id = MEKONG_MANGROVE_ALLIANCE,
        name = "Mekong Delta Mangrove Alliance",
        description =
          "Coalition of provincial agencies and NGOs restoring intertidal mangrove belts in the " +
            "Mekong Delta.",
        websiteUrl = "https://example.org/mekong-mangroves",
        logoUrl = "avatar:4",
        isListed = true,
        createdOn = "2025-11-03",
        imagerySources =
          listOf(
            ImagerySource(
              id = "imagery-mma-osm",
              name = "Mekong Delta OpenStreetMap",
              urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
              type = ImagerySourceType.XYZ_TILES,
              allowOfflineDownload = false,
            )
          ),
        members =
          listOf(
            OrganizationMember(
              email = "linh.tran@example.org",
              role = OrganizationRole.MANAGER,
              profile = CachedProfile("Linh Trần", "avatar:1", "2025-11-03"),
              userId = "uid-linh-tran",
            ),
            OrganizationMember(
              email = SIGNED_IN_EMAIL,
              role = OrganizationRole.MEMBER,
              profile = signedInProfile,
              userId = "uid-maya-lin",
            ),
            OrganizationMember(
              email = "minh.nguyen@example.org",
              role = OrganizationRole.MEMBER,
              profile = CachedProfile("Minh Nguyễn", "avatar:6", "2025-12-15"),
              userId = "uid-minh-nguyen",
            ),
          ),
      ),
      Organization(
        id = OPEN_FORIS_COMMUNITY,
        name = "Open Foris Community",
        description =
          "Open community of practice sharing survey templates and methods for forest and land " +
            "monitoring.",
        websiteUrl = "https://openforis.org",
        logoUrl = "avatar:8",
        isListed = true,
        createdOn = "2025-09-01",
        members =
          listOf(
            OrganizationMember(
              email = "community@example.org",
              role = OrganizationRole.MANAGER,
              profile = CachedProfile("Sofia Rossi", "avatar:1", "2025-09-01"),
              userId = "uid-community",
            )
          ),
      ),
    )

  /**
   * Organization each sample survey belongs to, or `null` for personal surveys. Keyed by survey ID.
   */
  fun organizationIdForSurvey(surveyId: String): String? =
    when (surveyId) {
      "survey-kenya-coffee",
      "survey-single-point-land-use",
      "survey-household-past-individuals" -> KENYA_FOREST_SERVICE
      "survey-serengeti-corridor" -> MEKONG_MANGROVE_ALLIANCE
      else -> null
    }

  /** Owner of each sample survey. */
  fun ownerEmailForSurvey(surveyId: String): String =
    when (surveyId) {
      "survey-kenya-coffee" -> "organizer@example.org"
      "survey-serengeti-corridor" -> "linh.tran@example.org"
      else -> SIGNED_IN_EMAIL
    }
}
