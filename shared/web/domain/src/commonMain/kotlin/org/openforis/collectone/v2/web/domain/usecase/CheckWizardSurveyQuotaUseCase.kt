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
package org.openforis.collectone.v2.web.domain.usecase

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.QuotaAndCostEnforcer
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.hasPlatformAdmin

/**
 * Decides whether the Survey Creation Wizard may store its working survey: first whether the
 * signed-in user may own another survey in the draft's organization, then whether the draft's
 * design (Forms, map features, sample plots) stays within the organization's limits. The first
 * exceeded limit wins, so the organizer fixes one problem at a time.
 *
 * Limits come from the draft's organization, falling back to the platform-wide ones set on the
 * synthetic "All users" organization; platform administrators (its Managers) have no survey count
 * limit.
 */
class CheckWizardSurveyQuotaUseCase {
  /**
   * Checks [draft] against [organizations] (as the Survey editor lists them, including the
   * synthetic one) for [signedInUserEmail], counting [existingSurveys] except the wizard's own
   * [workingSurveyId], which already exists as a blank survey while the wizard runs.
   */
  operator fun invoke(
    draft: SurveyEditorDraft,
    organizations: List<Organization>,
    existingSurveys: List<SurveyPreviewItem>,
    signedInUserEmail: String,
    workingSurveyId: String,
  ): QuotaCheckResult {
    val organizationId = draft.details.organizationId
    val organization = organizationId?.let { id -> organizations.firstOrNull { it.id == id } }
    val globalLimits = organizations.firstOrNull { it.isSynthetic }?.quotaLimits
    val isPlatformAdmin = organizations.hasPlatformAdmin(signedInUserEmail)
    val createCheck =
      QuotaAndCostEnforcer.checkCanCreateSurvey(
        ownerEmail = signedInUserEmail,
        organizationId = organizationId,
        existingSurveys = existingSurveys,
        globalLimits = globalLimits,
        organization = organization,
        excludeSurveyId = workingSurveyId,
        isPlatformAdmin = isPlatformAdmin,
      )
    if (createCheck is QuotaCheckResult.Exceeded) return createCheck
    return QuotaAndCostEnforcer.checkSurveyDesign(
      draft = draft,
      organization = organization,
      globalLimits = globalLimits,
    )
  }
}
