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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.resolveEffectiveQuotaLimits

/**
 * Caps the sample plots a survey may hold across all its generated Map layers, using the survey's
 * effective quota limits (its own, then its organization's, then the platform-wide ones held by the
 * synthetic organization).
 */
object SampleQuotaPolicy {
  /** Outcome of [check]. */
  sealed interface Result {
    data object WithinLimit : Result

    /** Keeping the new plots would bring the survey to [totalPlots], above [limit]. */
    data class Exceeded(val totalPlots: Int, val limit: Int) : Result
  }

  /**
   * Checks whether replacing the plots of generated layer [datasetKey] of [draft] with
   * [generatedPlotCount] new ones keeps the survey within its `maxGeneratedEntities` limit. A limit
   * of `0` means no limit.
   */
  fun check(
    draft: SurveyEditorDraft,
    datasetKey: String,
    generatedPlotCount: Int,
    organizations: List<Organization>,
  ): Result {
    val organizationId = draft.details.organizationId
    val organization = organizationId?.let { id -> organizations.firstOrNull { it.id == id } }
    val hasOrganization =
      (organization != null && !organization.isSynthetic) ||
        (!organizationId.isNullOrBlank() && organizationId != Organization.ALL_USERS_ID)
    val limit =
      resolveEffectiveQuotaLimits(
          global = organizations.firstOrNull { it.isSynthetic }?.quotaLimits,
          organization = organization?.quotaLimits,
          survey = draft.details.quotaLimits,
          hasOrganization = hasOrganization,
        )
        .maxGeneratedEntities
    val otherPlots =
      draft.datasets.filter { it.isGenerated && it.key != datasetKey }.sumOf { it.rows.size }
    val totalPlots = otherPlots + generatedPlotCount
    return if (limit in 1 until totalPlots) Result.Exceeded(totalPlots, limit)
    else Result.WithinLimit
  }
}
