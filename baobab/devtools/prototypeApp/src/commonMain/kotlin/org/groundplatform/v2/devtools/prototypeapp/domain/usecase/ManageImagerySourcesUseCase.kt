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

import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository

/**
 * Adds, edits, and removes the custom [ImagerySource]s configured on an organization in the
 * organization editor's `Imagery sources` tab.
 *
 * Validation ([validationError]) and building a new source ([newSource]) are pure so the form can
 * show problems inline; the writes go through [OrganizationRepository.updateOrganization].
 */
class ManageImagerySourcesUseCase(private val organizationRepository: OrganizationRepository) {
  /** Why [name] / [urlTemplate] don't describe a valid source of [type], or `null` if they do. */
  fun validationError(name: String, urlTemplate: String, type: ImagerySourceType): String? {
    if (name.isBlank()) return "Enter a name for the imagery source."
    if (
      type == ImagerySourceType.XYZ_TILES &&
        !ImagerySource.isValidXyzUrlTemplate(urlTemplate.trim())
    ) {
      return "Enter an http(s):// XYZ tile URL containing {z}, {x}, and {y}."
    }
    return null
  }

  /**
   * A new, validated source for [organization] with an ID unique across [allOrganizations] (the
   * basemap layers dialog mixes `"All users"` and survey-organization sources).
   */
  fun newSource(
    organization: Organization,
    allOrganizations: List<Organization>,
    name: String,
    urlTemplate: String,
    type: ImagerySourceType,
    allowOfflineDownload: Boolean,
  ): ImagerySource {
    val trimmedName = name.trim()
    val takenIds = allOrganizations.flatMap { it.imagerySources }.mapTo(mutableSetOf()) { it.id }
    return ImagerySource(
      id = uniqueSourceId(organization, trimmedName, takenIds),
      name = trimmedName,
      urlTemplate = urlTemplate.trim(),
      type = type,
      allowOfflineDownload = allowOfflineDownload,
    )
  }

  /** Appends [source] (from [newSource]) to [organizationId]. */
  suspend fun add(organizationId: String, source: ImagerySource): Organization? =
    organizationRepository.updateOrganization(organizationId) { current ->
      current.copy(imagerySources = current.imagerySources + source)
    }

  /** Replaces the name, URL, and offline permission of [sourceId] on [organizationId]. */
  suspend fun update(
    organizationId: String,
    sourceId: String,
    name: String,
    urlTemplate: String,
    allowOfflineDownload: Boolean,
  ): Organization? =
    organizationRepository.updateOrganization(organizationId) { current ->
      current.copy(
        imagerySources =
          current.imagerySources.map { src ->
            if (src.id == sourceId) {
              src.copy(
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                allowOfflineDownload = allowOfflineDownload,
              )
            } else {
              src
            }
          }
      )
    }

  /** Toggles whether offline download on mobile is permitted for [sourceId] on [organizationId]. */
  suspend fun setOfflineAllowed(
    organizationId: String,
    sourceId: String,
    allowOfflineDownload: Boolean,
  ): Organization? =
    organizationRepository.updateOrganization(organizationId) { current ->
      current.copy(
        imagerySources =
          current.imagerySources.map { src ->
            if (src.id == sourceId) src.copy(allowOfflineDownload = allowOfflineDownload) else src
          }
      )
    }

  /** Removes [sourceId] from [organizationId]. */
  suspend fun remove(organizationId: String, sourceId: String): Organization? =
    organizationRepository.updateOrganization(organizationId) { current ->
      current.copy(imagerySources = current.imagerySources.filterNot { it.id == sourceId })
    }

  companion object {
    /**
     * A source ID derived from the organization and [name] (`imagery-<organization slug>-<name
     * slug>`), made unique among [takenIds].
     */
    fun uniqueSourceId(organization: Organization, name: String, takenIds: Set<String>): String {
      val slug =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "source" }.take(32)
      val base = "imagery-${organization.id.removePrefix("org-")}-$slug"
      if (base !in takenIds) return base
      var n = 2
      while ("$base-$n" in takenIds) n++
      return "$base-$n"
    }
  }
}
