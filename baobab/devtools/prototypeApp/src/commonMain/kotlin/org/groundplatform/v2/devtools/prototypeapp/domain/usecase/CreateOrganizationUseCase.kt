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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository

/**
 * Creates an organization managed by the signed-in user.
 *
 * Building the organization ([newOrganization]) is pure, so callers can hand out its ID right away;
 * persisting it ([invoke]) stores it with the creator as its first Manager.
 */
class CreateOrganizationUseCase(private val organizationRepository: OrganizationRepository) {
  /**
   * A new listed-or-unlisted organization named [name] (or `"Untitled organization"` when blank)
   * with an ID unique among [existingOrganizations] and a placeholder logo that cycles through the
   * prototype's avatars.
   */
  fun newOrganization(
    name: String,
    description: String,
    isListed: Boolean,
    existingOrganizations: List<Organization>,
  ): Organization {
    val trimmedName = name.trim().ifBlank { DEFAULT_NAME }
    return Organization(
      id = uniqueOrganizationId(trimmedName, existingOrganizations.map { it.id }.toSet()),
      name = trimmedName,
      description = description.trim(),
      isListed = isListed,
      logoUrl = "avatar:${existingOrganizations.size % AVATAR_COUNT}",
    )
  }

  /** Stores [organization] (from [newOrganization]) with [creatorEmail] as its first Manager. */
  suspend operator fun invoke(
    organization: Organization,
    creatorEmail: String,
    creatorProfile: CachedProfile?,
  ): Organization? =
    organizationRepository.createOrganization(organization, creatorEmail, creatorProfile)

  companion object {
    const val DEFAULT_NAME = "Untitled organization"

    /** Number of placeholder `avatar:<n>` images the prototype cycles through. */
    private const val AVATAR_COUNT = 9

    /** An organization ID derived from [name] (`org-<slug>`), made unique among [takenIds]. */
    fun uniqueOrganizationId(name: String, takenIds: Set<String>): String {
      val slug =
        name
          .lowercase()
          .replace(Regex("[^a-z0-9]+"), "-")
          .trim('-')
          .ifBlank { "organization" }
          .take(40)
      val base = "org-$slug"
      if (base !in takenIds) return base
      var n = 2
      while ("$base-$n" in takenIds) n++
      return "$base-$n"
    }
  }
}
