/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.Country
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem

/** Survey and country filter of an organization's Impact view; `null` means all. */
data class ImpactFilter(val surveyId: String? = null, val countryCode: String? = null) {
  val isActive: Boolean
    get() = surveyId != null || countryCode != null
}

/** One organization's Impact view: its numbers under the current filter, and the filter choices. */
data class OrganizationImpactUiState(
  val summary: ImpactSummary,
  val filter: ImpactFilter = ImpactFilter(),
  /** Surveys to filter by. */
  val surveys: List<SurveyPreviewItem> = emptyList(),
  /** Countries its map features are in, to filter by. */
  val countries: List<Country> = emptyList(),
)

/**
 * UI state of the Impact views: the survey Impact tab of the web dashboard, the organization Impact
 * tab, and the platform-wide view for `"All users"` Managers. Numbers are recomputed shortly after
 * the underlying data changes.
 */
data class ImpactUiState(
  /** Whether the first computation hasn't finished yet. */
  val isLoading: Boolean = true,
  /** Numbers of every survey, by survey ID. */
  val surveys: Map<String, ImpactSummary> = emptyMap(),
  /** Numbers of every organization, by organization ID. */
  val organizations: Map<String, OrganizationImpactUiState> = emptyMap(),
  /** Platform-wide numbers, only for Managers of `"All users"`; `null` for everyone else. */
  val global: ImpactSummary? = null,
  /** Message about a finished download, shown in a snackbar. */
  val message: String? = null,
)
