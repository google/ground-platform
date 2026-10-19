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
package org.openforis.collectone.v2.app.domain.model

import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft

/** Result of a quota or budget enforcement check. */
sealed interface QuotaCheckResult {
  data object Allowed : QuotaCheckResult

  data class Exceeded(val reason: String) : QuotaCheckResult {
    val isBlocked: Boolean
      get() = true
  }

  val isAllowed: Boolean
    get() = this is Allowed

  val isExceeded: Boolean
    get() = this is Exceeded

  fun errorMessageOrNull(): String? = (this as? Exceeded)?.reason
}

/**
 * Enforces tiered resource quotas and monthly USD budget constraints across clients and backend.
 */
object QuotaAndCostEnforcer {

  /** Validates whether [ownerEmail] can create a new survey within [organizationId]. */
  fun checkCanCreateSurvey(
    ownerEmail: String,
    organizationId: String?,
    existingSurveys: List<SurveyPreviewItem>,
    globalLimits: QuotaLimitsConfig? = null,
    organization: Organization? = null,
    excludeSurveyId: String? = null,
  ): QuotaCheckResult {
    val hasOrg =
      (organization != null && !organization.isSynthetic) ||
        (!organizationId.isNullOrBlank() && organizationId != Organization.ALL_USERS_ID)
    val effectiveLimits =
      resolveEffectiveQuotaLimits(
        global = globalLimits,
        organization = organization?.quotaLimits,
        hasOrganization = hasOrg,
      )

    val ownedSurveysCount =
      existingSurveys
        .filter { excludeSurveyId == null || it.id != excludeSurveyId }
        .count { it.ownerEmail.equals(ownerEmail, ignoreCase = true) }
    if (effectiveLimits.maxSurveysPerOwner in 1..ownedSurveysCount) {
      return QuotaCheckResult.Exceeded(
        "Maximum surveys per owner limit reached ($ownedSurveysCount of ${effectiveLimits.maxSurveysPerOwner}). Upgrade tier or delete old surveys."
      )
    }

    if (organization != null && organization.quotaLimits.monthlyBudgetUsd > 0.0) {
      val orgSurveys = existingSurveys.filter {
        it.organizationId == organization.id &&
          (excludeSurveyId == null || it.id != excludeSurveyId)
      }
      val currentEstimatedOrgSpend =
        orgSurveys.size * effectiveLimits.costUnitWeights.usdPerSurveyMonth
      if (
        currentEstimatedOrgSpend + effectiveLimits.costUnitWeights.usdPerSurveyMonth >
          organization.quotaLimits.monthlyBudgetUsd
      ) {
        return QuotaCheckResult.Exceeded(
          "Creating another survey would exceed organization monthly budget cap (${formatUsdAmount(organization.quotaLimits.monthlyBudgetUsd)})."
        )
      }
    }

    return QuotaCheckResult.Allowed
  }

  /**
   * Validates a survey editor draft against the resolved effective quota limits and budget caps.
   */
  fun checkSurveyDesign(
    draft: SurveyEditorDraft,
    organization: Organization? = null,
    globalLimits: QuotaLimitsConfig? = null,
  ): QuotaCheckResult {
    val orgId = draft.details.organizationId
    val hasOrg =
      (organization != null && !organization.isSynthetic) ||
        (!orgId.isNullOrBlank() && orgId != Organization.ALL_USERS_ID)
    val effectiveLimits =
      resolveEffectiveQuotaLimits(
        global = globalLimits,
        organization = organization?.quotaLimits,
        survey = draft.details.quotaLimits,
        hasOrganization = hasOrg,
      )

    // Check forms count
    val formCount = draft.forms.size
    if (effectiveLimits.maxFormsPerSurvey in 1 until formCount) {
      return QuotaCheckResult.Exceeded(
        "Survey contains $formCount forms, exceeding the limit of ${effectiveLimits.maxFormsPerSurvey} for ${effectiveLimits.tier.label} tier."
      )
    }

    // Check questions per form & photo questions per form
    draft.forms.forEach { editorForm ->
      val qCount = editorForm.form.questions.size
      if (effectiveLimits.maxQuestionsPerForm in 1 until qCount) {
        return QuotaCheckResult.Exceeded(
          "Form \"${editorForm.form.title}\" has $qCount questions, exceeding the limit of ${effectiveLimits.maxQuestionsPerForm}."
        )
      }
      val photoQCount =
        editorForm.form.questions.count { q ->
          val label = q.label.lowercase()
          val name = q.name.lowercase()
          label.contains("photo") ||
            label.contains("picture") ||
            label.contains("image") ||
            name.contains("photo")
        }
      if (effectiveLimits.maxPhotoQuestionsPerForm in 1 until photoQCount) {
        return QuotaCheckResult.Exceeded(
          "Form \"${editorForm.form.title}\" has $photoQCount photo questions, exceeding the limit of ${effectiveLimits.maxPhotoQuestionsPerForm}."
        )
      }
    }

    // Check predefined entities and generated entities
    val predefinedEntitiesCount = draft.datasets.filter { !it.isGenerated }.sumOf { it.rows.size }
    if (effectiveLimits.maxPredefinedEntities in 1 until predefinedEntitiesCount) {
      return QuotaCheckResult.Exceeded(
        "Survey has $predefinedEntitiesCount predefined entities, exceeding the limit of ${effectiveLimits.maxPredefinedEntities}."
      )
    }

    val generatedEntitiesCount = draft.datasets.filter { it.isGenerated }.sumOf { it.rows.size }
    if (effectiveLimits.maxGeneratedEntities in 1 until generatedEntitiesCount) {
      return QuotaCheckResult.Exceeded(
        "Sample design generated $generatedEntitiesCount plots, exceeding the limit of ${effectiveLimits.maxGeneratedEntities}."
      )
    }

    // Check area vertices
    val areaVertices = draft.details.surveyArea?.vertexCount ?: 0
    if (effectiveLimits.maxAreaVertices in 1 until areaVertices) {
      return QuotaCheckResult.Exceeded(
        "Survey area has $areaVertices vertices, exceeding the limit of ${effectiveLimits.maxAreaVertices}."
      )
    }

    // Check ACL users
    val aclUsersCount = draft.sharing.collaborators.size
    if (effectiveLimits.maxAclUsers in 1 until aclUsersCount) {
      return QuotaCheckResult.Exceeded(
        "Access control list has $aclUsersCount collaborators, exceeding the limit of ${effectiveLimits.maxAclUsers}."
      )
    }

    // Check estimated survey cost against monthly budget
    val estimatedSurveyMonthlyUsd =
      estimateMonthlyUsd(
        weights = effectiveLimits.costUnitWeights,
        predefinedEntityCount = predefinedEntitiesCount,
        generatedEntityCount = generatedEntitiesCount,
      )
    if (
      effectiveLimits.monthlyBudgetUsd > 0.0 &&
        estimatedSurveyMonthlyUsd > effectiveLimits.monthlyBudgetUsd
    ) {
      return QuotaCheckResult.Exceeded(
        "Survey design estimated monthly cost (${formatUsdAmount(estimatedSurveyMonthlyUsd)}) exceeds survey monthly budget cap (${formatUsdAmount(effectiveLimits.monthlyBudgetUsd)})."
      )
    }

    return QuotaCheckResult.Allowed
  }

  /**
   * Validates whether [userEmail] can collect another submission or create an ad-hoc entity in
   * [survey].
   */
  fun checkCanCollectSubmission(
    userEmail: String,
    survey: SurveyPreviewItem,
    isNewAdhocEntity: Boolean = false,
    userSubmissionsForEntityCount: Int = 0,
    userAdhocEntitiesCount: Int = 0,
    userActiveSurveysCount: Int = 0,
    organization: Organization? = null,
    globalLimits: QuotaLimitsConfig? = null,
  ): QuotaCheckResult {
    val orgId = survey.organizationId
    val hasOrg =
      (organization != null && !organization.isSynthetic) ||
        (!orgId.isNullOrBlank() && orgId != Organization.ALL_USERS_ID)
    val effectiveLimits =
      resolveEffectiveQuotaLimits(
        global = globalLimits,
        organization = organization?.quotaLimits,
        survey = survey.quotaLimits,
        hasOrganization = hasOrg,
      )

    if (effectiveLimits.maxSurveysPerUser in 1 until userActiveSurveysCount) {
      return QuotaCheckResult.Exceeded(
        "User is active in $userActiveSurveysCount surveys, exceeding the limit of ${effectiveLimits.maxSurveysPerUser}."
      )
    }

    if (isNewAdhocEntity && effectiveLimits.maxAdhocEntitiesPerUser in 1..userAdhocEntitiesCount) {
      return QuotaCheckResult.Exceeded(
        "User has created $userAdhocEntitiesCount ad-hoc entities, reaching the limit of ${effectiveLimits.maxAdhocEntitiesPerUser}."
      )
    }

    if (effectiveLimits.maxSubmissionsPerEntityPerUser in 1..userSubmissionsForEntityCount) {
      return QuotaCheckResult.Exceeded(
        "User has recorded $userSubmissionsForEntityCount submissions for this entity, reaching the limit of ${effectiveLimits.maxSubmissionsPerEntityPerUser}."
      )
    }

    return QuotaCheckResult.Allowed
  }
}
