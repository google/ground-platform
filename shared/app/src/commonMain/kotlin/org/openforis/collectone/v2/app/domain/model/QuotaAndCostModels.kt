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

/** Tier classification applied to users, organizations, and surveys. */
enum class QuotaTier(val label: String) {
  UNSPECIFIED("Unspecified"),
  BASIC("Basic"),
  SPONSORED("Sponsored"),
}

/** Unit cost weights used to estimate monthly infrastructure and operational costs. */
data class CostUnitWeights(
  val usdPerSurveyMonth: Double = 0.50,
  val usdPerThousandEntities: Double = 0.10,
  val usdPerThousandSubmissions: Double = 0.25,
  val usdPerPhoto: Double = 0.01,
  val usdPerThousandGeneratedPlots: Double = 0.05,
)

/**
 * Fine-grained resource quota limits and budget configuration governing survey creation,
 * collaborator access, and operational field collection across global, organization, and survey
 * scopes.
 */
data class QuotaLimitsConfig(
  val tier: QuotaTier = QuotaTier.UNSPECIFIED,
  val maxSurveysPerOwner: Int = 0,
  val maxFormsPerSurvey: Int = 0,
  val maxQuestionsPerForm: Int = 0,
  val maxPhotoQuestionsPerForm: Int = 0,
  val maxPredefinedEntities: Int = 0,
  val maxAclUsers: Int = 0,
  val maxSurveysPerUser: Int = 0,
  val maxAdhocEntitiesPerUser: Int = 0,
  val maxSubmissionsPerEntityPerUser: Int = 0,
  val maxGeneratedEntities: Int = 0,
  val maxAreaVertices: Int = 0,
  val monthlyBudgetUsd: Double = 0.0,
  val costUnitWeights: CostUnitWeights = CostUnitWeights(),
) {
  companion object {
    /**
     * Default Basic Tier quotas from docs/technical/model/survey/04-access-control-and-quotas.md.
     */
    val BASIC =
      QuotaLimitsConfig(
        tier = QuotaTier.BASIC,
        maxSurveysPerOwner = 5,
        maxFormsPerSurvey = 5,
        maxQuestionsPerForm = 10,
        maxPhotoQuestionsPerForm = 1,
        maxPredefinedEntities = 1_000,
        maxAclUsers = 10,
        maxSurveysPerUser = 5,
        maxAdhocEntitiesPerUser = 100,
        maxSubmissionsPerEntityPerUser = 10,
        maxGeneratedEntities = 10_000,
        maxAreaVertices = 5_000,
        monthlyBudgetUsd = 0.0,
        costUnitWeights = CostUnitWeights(),
      )

    /** Default Sponsored Tier quotas for verified institutions and high-volume surveys. */
    val SPONSORED =
      QuotaLimitsConfig(
        tier = QuotaTier.SPONSORED,
        maxSurveysPerOwner = 100,
        maxFormsPerSurvey = 10,
        maxQuestionsPerForm = 50,
        maxPhotoQuestionsPerForm = 10,
        maxPredefinedEntities = 10_000,
        maxAclUsers = 50,
        maxSurveysPerUser = 100,
        maxAdhocEntitiesPerUser = 200,
        maxSubmissionsPerEntityPerUser = 50,
        maxGeneratedEntities = 100_000,
        maxAreaVertices = 50_000,
        monthlyBudgetUsd = 0.0,
        costUnitWeights = CostUnitWeights(),
      )

    fun forTier(tier: QuotaTier): QuotaLimitsConfig =
      when (tier) {
        QuotaTier.SPONSORED -> SPONSORED
        QuotaTier.BASIC,
        QuotaTier.UNSPECIFIED -> BASIC
      }
  }
}

/**
 * Resolves effective quota limits and cost controls by hierarchically cascading: Default Tier
 * Baseline -> Global Overrides -> Organization Overrides -> Survey Overrides.
 */
fun resolveEffectiveQuotaLimits(
  global: QuotaLimitsConfig? = null,
  organization: QuotaLimitsConfig? = null,
  survey: QuotaLimitsConfig? = null,
  hasOrganization: Boolean = false,
): QuotaLimitsConfig {
  val effectiveTier =
    survey?.tier?.takeIf { it != QuotaTier.UNSPECIFIED }
      ?: organization?.tier?.takeIf { it != QuotaTier.UNSPECIFIED }
      ?: (if (hasOrganization) QuotaTier.SPONSORED else null)
      ?: global?.tier?.takeIf { it != QuotaTier.UNSPECIFIED }
      ?: QuotaTier.BASIC

  val base = QuotaLimitsConfig.forTier(effectiveTier)

  fun overlay(current: QuotaLimitsConfig, override: QuotaLimitsConfig?): QuotaLimitsConfig {
    if (override == null) return current
    return current.copy(
      tier = effectiveTier,
      maxSurveysPerOwner =
        if (override.maxSurveysPerOwner > 0) override.maxSurveysPerOwner
        else current.maxSurveysPerOwner,
      maxFormsPerSurvey =
        if (override.maxFormsPerSurvey > 0) override.maxFormsPerSurvey
        else current.maxFormsPerSurvey,
      maxQuestionsPerForm =
        if (override.maxQuestionsPerForm > 0) override.maxQuestionsPerForm
        else current.maxQuestionsPerForm,
      maxPhotoQuestionsPerForm =
        if (override.maxPhotoQuestionsPerForm > 0) override.maxPhotoQuestionsPerForm
        else current.maxPhotoQuestionsPerForm,
      maxPredefinedEntities =
        if (override.maxPredefinedEntities > 0) override.maxPredefinedEntities
        else current.maxPredefinedEntities,
      maxAclUsers = if (override.maxAclUsers > 0) override.maxAclUsers else current.maxAclUsers,
      maxSurveysPerUser =
        if (override.maxSurveysPerUser > 0) override.maxSurveysPerUser
        else current.maxSurveysPerUser,
      maxAdhocEntitiesPerUser =
        if (override.maxAdhocEntitiesPerUser > 0) override.maxAdhocEntitiesPerUser
        else current.maxAdhocEntitiesPerUser,
      maxSubmissionsPerEntityPerUser =
        if (override.maxSubmissionsPerEntityPerUser > 0) override.maxSubmissionsPerEntityPerUser
        else current.maxSubmissionsPerEntityPerUser,
      maxGeneratedEntities =
        if (override.maxGeneratedEntities > 0) override.maxGeneratedEntities
        else current.maxGeneratedEntities,
      maxAreaVertices =
        if (override.maxAreaVertices > 0) override.maxAreaVertices else current.maxAreaVertices,
      monthlyBudgetUsd =
        if (override.monthlyBudgetUsd > 0.0) override.monthlyBudgetUsd
        else current.monthlyBudgetUsd,
      costUnitWeights =
        if (override.costUnitWeights != CostUnitWeights()) override.costUnitWeights
        else current.costUnitWeights,
    )
  }

  val withGlobal = overlay(base, global)
  val withOrg = overlay(withGlobal, organization)
  return overlay(withOrg, survey)
}

/** Computes estimated monthly USD cost given resource counts and unit weights. */
fun estimateMonthlyUsd(
  weights: CostUnitWeights,
  predefinedEntityCount: Int = 0,
  adhocEntityCount: Int = 0,
  generatedEntityCount: Int = 0,
  submissionCount: Int = 0,
  photoCount: Int = 0,
): Double {
  val surveyBase = weights.usdPerSurveyMonth
  val entitiesCost =
    ((predefinedEntityCount + adhocEntityCount) / 1000.0) * weights.usdPerThousandEntities
  val plotsCost = (generatedEntityCount / 1000.0) * weights.usdPerThousandGeneratedPlots
  val submissionsCost = (submissionCount / 1000.0) * weights.usdPerThousandSubmissions
  val photosCost = photoCount * weights.usdPerPhoto
  return surveyBase + entitiesCost + plotsCost + submissionsCost + photosCost
}

/** Breakdown of cost and quota utilization for an individual survey. */
data class SurveyCostSummary(
  val surveyId: String,
  val surveyTitle: String,
  val organizationId: String?,
  val organizationName: String,
  val ownerEmail: String,
  val effectiveQuotaLimits: QuotaLimitsConfig,
  val formCount: Int,
  val maxQuestionsInAnyForm: Int,
  val maxPhotoQuestionsInAnyForm: Int,
  val predefinedEntityCount: Int,
  val generatedEntityCount: Int,
  val adhocEntityCount: Int,
  val submissionCount: Int,
  val photoCount: Int,
  val aclUserCount: Int,
  val estimatedMonthlyUsd: Double,
  val monthlyBudgetUsd: Double,
  val violations: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
) {
  val isBudgetExceeded: Boolean
    get() = monthlyBudgetUsd > 0.0 && estimatedMonthlyUsd > monthlyBudgetUsd

  val budgetUtilizationFraction: Double
    get() =
      if (monthlyBudgetUsd > 0.0) (estimatedMonthlyUsd / monthlyBudgetUsd).coerceAtLeast(0.0)
      else 0.0
}

/** Breakdown of cost and quota utilization for an organization. */
data class OrganizationCostSummary(
  val organizationId: String,
  val organizationName: String,
  val tier: QuotaTier,
  val surveyCount: Int,
  val memberCount: Int,
  val predefinedEntityCount: Int,
  val generatedEntityCount: Int,
  val submissionCount: Int,
  val photoCount: Int,
  val estimatedMonthlyUsd: Double,
  val monthlyBudgetUsd: Double,
  val violations: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
) {
  val isBudgetExceeded: Boolean
    get() = monthlyBudgetUsd > 0.0 && estimatedMonthlyUsd > monthlyBudgetUsd

  val budgetUtilizationFraction: Double
    get() =
      if (monthlyBudgetUsd > 0.0) (estimatedMonthlyUsd / monthlyBudgetUsd).coerceAtLeast(0.0)
      else 0.0
}

/** Breakdown of cost and quota utilization for a user across all owned and joined surveys. */
data class UserCostSummary(
  val userEmail: String,
  val displayName: String,
  val ownedSurveyCount: Int,
  val activeSurveyCount: Int,
  val adhocEntityCount: Int,
  val submissionCount: Int,
  val photoCount: Int,
  val estimatedMonthlyUsd: Double,
  val violations: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
)

/** Platform-wide aggregated cost and resource report for platform administrators. */
data class PlatformCostReport(
  val totalEstimatedMonthlyUsd: Double,
  val totalBudgetUsd: Double,
  val totalOrganizationsCount: Int,
  val totalSurveysCount: Int,
  val totalSubmissionsCount: Int,
  val totalEntitiesCount: Int,
  val totalPhotosCount: Int,
  val organizations: List<OrganizationCostSummary>,
  val surveys: List<SurveyCostSummary>,
  val users: List<UserCostSummary>,
)
