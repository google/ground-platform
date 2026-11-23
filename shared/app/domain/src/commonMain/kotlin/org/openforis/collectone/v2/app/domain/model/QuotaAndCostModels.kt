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
enum class QuotaTier {
  UNSPECIFIED,
  BASIC,
  SPONSORED,
}

/**
 * Fine-grained resource quota limits governing survey creation, collaborator access, and
 * operational field collection across global, organization, and survey scopes.
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
 * Resolves effective quota limits by hierarchically cascading: Default Tier Baseline -> Global
 * Overrides -> Organization Overrides -> Survey Overrides.
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
    )
  }

  val withGlobal = overlay(base, global)
  val withOrg = overlay(withGlobal, organization)
  return overlay(withOrg, survey)
}

/** Breakdown of resource usage and quota utilization for an individual survey. */
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
  val violations: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
) {
  val hasViolations: Boolean
    get() = violations.isNotEmpty()
}

/** Breakdown of resource usage and quota utilization for an organization. */
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
  val violations: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
) {
  val hasViolations: Boolean
    get() = violations.isNotEmpty()
}

/**
 * Breakdown of resource usage and quota utilization for a user across all owned and joined surveys.
 */
data class UserCostSummary(
  val userEmail: String,
  val displayName: String,
  val ownedSurveyCount: Int,
  val activeSurveyCount: Int,
  val adhocEntityCount: Int,
  val submissionCount: Int,
  val photoCount: Int,
  val violations: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
) {
  val hasViolations: Boolean
    get() = violations.isNotEmpty()
}

/** Platform-wide aggregated resource and quota utilization report for platform administrators. */
data class PlatformCostReport(
  val totalOrganizationsCount: Int,
  val totalSurveysCount: Int,
  val totalSubmissionsCount: Int,
  val totalEntitiesCount: Int,
  val totalPhotosCount: Int,
  val organizations: List<OrganizationCostSummary>,
  val surveys: List<SurveyCostSummary>,
  val users: List<UserCostSummary>,
)
