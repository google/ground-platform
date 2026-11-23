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

import org.openforis.collectone.v2.app.domain.model.editor.Collaborator

/**
 * Usage and quota report for the platform console: what each survey, organization, and user holds
 * (forms, map features by origin, submissions, photos, collaborators) against the quota limits that
 * apply to them ([resolveEffectiveQuotaLimits]).
 *
 * A limit of 0 means unlimited, so a count is only a violation when the limit is positive and the
 * count exceeds it. Photos are counted from answer values that look like image file names or media
 * references, since submission previews don't carry question types.
 */
object CostTrackingCalculator {

  /** Counts photo attachments inside a [SubmissionPreviewItem]. */
  fun countPhotos(submission: SubmissionPreviewItem): Int =
    submission.fields.count { field ->
      val v = field.answerValue.trim().lowercase()
      v.endsWith(".jpg") ||
        v.endsWith(".jpeg") ||
        v.endsWith(".png") ||
        v.endsWith(".webp") ||
        v.startsWith("photo:") ||
        v.startsWith("media:")
    }

  /** Generates a complete platform resource and quota report from active domain entities. */
  fun calculatePlatformReport(
    organizations: List<Organization>,
    surveys: List<SurveyPreviewItem>,
    formsBySurvey: Map<String, List<FormPreviewItem>> = emptyMap(),
    entitiesBySurvey: Map<String, List<GeospatialEntityItem>> = emptyMap(),
    submissionsBySurvey: Map<String, List<SubmissionPreviewItem>> = emptyMap(),
    aclsBySurvey: Map<String, List<Collaborator>> = emptyMap(),
    globalQuotaLimits: QuotaLimitsConfig? = null,
  ): PlatformCostReport {
    val orgsById = organizations.associateBy { it.id }

    val surveySummaries = surveys.map { survey ->
      val org = survey.organizationId?.let { orgsById[it] }
      val hasOrg =
        (org != null && !org.isSynthetic) ||
          (!survey.organizationId.isNullOrBlank() &&
            survey.organizationId != Organization.ALL_USERS_ID)
      val effectiveLimits =
        resolveEffectiveQuotaLimits(
          global = globalQuotaLimits,
          organization = org?.quotaLimits,
          survey = survey.quotaLimits,
          hasOrganization = hasOrg,
        )
      val forms = formsBySurvey[survey.id].orEmpty()
      val entities = entitiesBySurvey[survey.id].orEmpty()
      val subs = submissionsBySurvey[survey.id].orEmpty()
      val acl = aclsBySurvey[survey.id].orEmpty()

      val predefined = entities.count { !it.isFieldCreated && !it.isGenerated }
      val generated = entities.count { it.isGenerated }
      val adhoc = entities.count { it.isFieldCreated }
      val subCount = subs.size
      val photoCount = subs.sumOf { countPhotos(it) }
      val aclUserCount = acl.size
      val formCount = forms.size
      val maxQuestions = forms.maxOfOrNull { it.questionCount } ?: 0
      val maxPhotoQuestions = 0 // In preview, question types are aggregated into questionCount

      val violations = mutableListOf<String>()
      val warnings = mutableListOf<String>()

      if (effectiveLimits.maxFormsPerSurvey in 1 until formCount) {
        violations += "Forms limit exceeded ($formCount > ${effectiveLimits.maxFormsPerSurvey})"
      }
      if (effectiveLimits.maxQuestionsPerForm in 1 until maxQuestions) {
        violations +=
          "Questions per form limit exceeded ($maxQuestions > ${effectiveLimits.maxQuestionsPerForm})"
      }
      if (effectiveLimits.maxPredefinedEntities in 1 until predefined) {
        violations +=
          "Predefined entities limit exceeded ($predefined > ${effectiveLimits.maxPredefinedEntities})"
      }
      if (effectiveLimits.maxGeneratedEntities in 1 until generated) {
        violations +=
          "Generated entities limit exceeded ($generated > ${effectiveLimits.maxGeneratedEntities})"
      }
      if (effectiveLimits.maxAclUsers in 1 until aclUserCount) {
        violations += "ACL users limit exceeded ($aclUserCount > ${effectiveLimits.maxAclUsers})"
      }

      SurveyCostSummary(
        surveyId = survey.id,
        surveyTitle = survey.title,
        organizationId = survey.organizationId,
        organizationName = org?.name ?: "Personal Survey",
        ownerEmail = survey.ownerEmail,
        effectiveQuotaLimits = effectiveLimits,
        formCount = formCount,
        maxQuestionsInAnyForm = maxQuestions,
        maxPhotoQuestionsInAnyForm = maxPhotoQuestions,
        predefinedEntityCount = predefined,
        generatedEntityCount = generated,
        adhocEntityCount = adhoc,
        submissionCount = subCount,
        photoCount = photoCount,
        aclUserCount = aclUserCount,
        violations = violations,
        warnings = warnings,
      )
    }

    val surveysByOrg = surveySummaries.groupBy { it.organizationId }

    val orgSummaries =
      organizations
        .filterNot { it.isSynthetic }
        .map { org ->
          val orgSurveys = surveysByOrg[org.id].orEmpty()
          val totalPredefined = orgSurveys.sumOf { it.predefinedEntityCount }
          val totalGenerated = orgSurveys.sumOf { it.generatedEntityCount }
          val totalSubs = orgSurveys.sumOf { it.submissionCount }
          val totalPhotos = orgSurveys.sumOf { it.photoCount }

          val violations = orgSurveys.flatMap { s -> s.violations.map { "${s.surveyTitle}: $it" } }
          val warnings = orgSurveys.flatMap { s -> s.warnings.map { "${s.surveyTitle}: $it" } }

          OrganizationCostSummary(
            organizationId = org.id,
            organizationName = org.name,
            tier =
              org.quotaLimits.tier.takeIf { it != QuotaTier.UNSPECIFIED } ?: QuotaTier.SPONSORED,
            surveyCount = orgSurveys.size,
            memberCount = org.activeMembers.size,
            predefinedEntityCount = totalPredefined,
            generatedEntityCount = totalGenerated,
            submissionCount = totalSubs,
            photoCount = totalPhotos,
            violations = violations,
            warnings = warnings,
          )
        }

    val allUserEmails = mutableSetOf<String>()
    surveys.forEach { survey ->
      if (survey.ownerEmail.isNotBlank()) allUserEmails += survey.ownerEmail
      aclsBySurvey[survey.id].orEmpty().forEach {
        if (it.email.isNotBlank()) allUserEmails += it.email
      }
      submissionsBySurvey[survey.id].orEmpty().forEach {
        if (it.collectorEmail.isNotBlank()) allUserEmails += it.collectorEmail
      }
    }
    organizations.forEach { org ->
      org.members.forEach { if (it.email.isNotBlank()) allUserEmails += it.email }
    }

    val userSummaries = allUserEmails.map { email ->
      val ownedSurveys = surveys.filter { it.ownerEmail.equals(email, ignoreCase = true) }
      val activeSurveys = surveys.filter { survey ->
        survey.ownerEmail.equals(email, ignoreCase = true) ||
          aclsBySurvey[survey.id].orEmpty().any { it.email.equals(email, ignoreCase = true) } ||
          submissionsBySurvey[survey.id].orEmpty().any {
            it.collectorEmail.equals(email, ignoreCase = true)
          }
      }

      val allUserSubs =
        submissionsBySurvey.values.flatten().filter {
          it.collectorEmail.equals(email, ignoreCase = true)
        }
      val userPhotos = allUserSubs.sumOf { countPhotos(it) }

      val allUserEntities =
        entitiesBySurvey.values.flatten().filter { entity ->
          entity.isFieldCreated &&
            entity.submissions.any { it.collectorEmail.equals(email, ignoreCase = true) }
        }

      val violations = mutableListOf<String>()
      val warnings = mutableListOf<String>()

      // Check if user exceeded standard basic limits across unowned surveys
      val defaultLimits = globalQuotaLimits ?: QuotaLimitsConfig.BASIC
      if (defaultLimits.maxSurveysPerOwner in 1 until ownedSurveys.size) {
        violations +=
          "Owned surveys quota exceeded (${ownedSurveys.size} > ${defaultLimits.maxSurveysPerOwner})"
      }
      if (defaultLimits.maxSurveysPerUser in 1 until activeSurveys.size) {
        violations +=
          "Joined surveys quota exceeded (${activeSurveys.size} > ${defaultLimits.maxSurveysPerUser})"
      }
      if (defaultLimits.maxAdhocEntitiesPerUser in 1 until allUserEntities.size) {
        violations +=
          "Ad-hoc entities quota exceeded (${allUserEntities.size} > ${defaultLimits.maxAdhocEntitiesPerUser})"
      }

      UserCostSummary(
        userEmail = email,
        displayName = email.substringBefore("@").replace(".", " ").capitalizeWords(),
        ownedSurveyCount = ownedSurveys.size,
        activeSurveyCount = activeSurveys.size,
        adhocEntityCount = allUserEntities.size,
        submissionCount = allUserSubs.size,
        photoCount = userPhotos,
        violations = violations,
        warnings = warnings,
      )
    }

    val totalSubs = surveySummaries.sumOf { it.submissionCount }
    val totalEntities = surveySummaries.sumOf {
      it.predefinedEntityCount + it.adhocEntityCount + it.generatedEntityCount
    }
    val totalPhotos = surveySummaries.sumOf { it.photoCount }

    return PlatformCostReport(
      totalOrganizationsCount = orgSummaries.size,
      totalSurveysCount = surveys.size,
      totalSubmissionsCount = totalSubs,
      totalEntitiesCount = totalEntities,
      totalPhotosCount = totalPhotos,
      organizations =
        orgSummaries.sortedByDescending {
          it.submissionCount + it.predefinedEntityCount + it.generatedEntityCount + it.surveyCount
        },
      surveys =
        surveySummaries.sortedByDescending {
          it.submissionCount +
            it.predefinedEntityCount +
            it.generatedEntityCount +
            it.adhocEntityCount
        },
      users = userSummaries.sortedByDescending { it.submissionCount + it.adhocEntityCount },
    )
  }

  private fun String.capitalizeWords(): String =
    split(" ").joinToString(" ") { word ->
      word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
