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
package org.openforis.collectone.v2.app.ui.onboarding

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.plainCoordinatesLabel

/**
 * The surveys matching the Download survey screen's search [query], case-insensitively, in their
 * original order: by title, description, location, organization name ([organizationNames] by ID),
 * or the coordinates of their area's centroid (as [plainCoordinatesLabel] writes them, e.g.
 * `1.28°S, 36.82°E`). A blank query matches every survey.
 */
internal fun List<SurveyPreviewItem>.matchingQuery(
  query: String,
  organizationNames: Map<String, String>,
): List<SurveyPreviewItem> {
  val trimmed = query.trim()
  if (trimmed.isEmpty()) return this
  return filter { survey ->
    survey.title.contains(trimmed, ignoreCase = true) ||
      survey.description.contains(trimmed, ignoreCase = true) ||
      survey.location.contains(trimmed, ignoreCase = true) ||
      survey.coordinatesMatch(trimmed) ||
      organizationNames[survey.organizationId]?.contains(trimmed, ignoreCase = true) == true
  }
}

private fun SurveyPreviewItem.coordinatesMatch(query: String): Boolean {
  val centroid = surveyArea?.centroid ?: return false
  return plainCoordinatesLabel(centroid).contains(query, ignoreCase = true)
}

/**
 * The affiliation line of the signed-in user's profile card: the organizations [email] is a member
 * of, joined by " • ", or the [profileOrganizationName] from the account when they belong to none.
 */
internal fun userOrganizationLine(
  organizations: List<Organization>,
  email: String,
  profileOrganizationName: String,
): String =
  organizations
    .filter { it.isMember(email) }
    .joinToString(" • ") { it.name }
    .ifBlank { profileOrganizationName }
