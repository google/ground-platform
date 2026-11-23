/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.mobile.ui.onboarding

import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

/**
 * The sections of Download survey's list: one per organization the user can see (by
 * [organizationNames], titled with the organization's name, in case-insensitive order of that
 * name), then [publicSectionTitle] for the surveys of no known organization, left out when there
 * are none. Each section keeps the order [surveys] came in.
 *
 * A survey whose organization isn't in [organizationNames] (a public survey, or one of an
 * organization the user isn't a member of) goes to the public section rather than to a section
 * named by an ID. Two organizations with the same name share one section.
 */
internal fun downloadSurveySections(
  surveys: List<SurveyPreviewItem>,
  organizationNames: Map<String, String>,
  publicSectionTitle: String,
): List<Pair<String, List<SurveyPreviewItem>>> {
  val organizationSections =
    surveys
      .mapNotNull { survey -> organizationNames[survey.organizationId]?.let { it to survey } }
      .groupBy({ it.first }, { it.second })
      .entries
      .sortedBy { it.key.lowercase() }
      .map { it.key to it.value }
  val publicSurveys = surveys.filter { organizationNames[it.organizationId] == null }
  return if (publicSurveys.isNotEmpty()) {
    organizationSections + (publicSectionTitle to publicSurveys)
  } else {
    organizationSections
  }
}
