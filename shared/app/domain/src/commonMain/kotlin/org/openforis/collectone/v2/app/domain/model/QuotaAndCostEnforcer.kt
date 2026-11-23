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

import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.map.GeometryType

/** Result of a quota enforcement check. */
sealed interface QuotaCheckResult {
  data object Allowed : QuotaCheckResult

  data class Exceeded(
    /** Which limit was exceeded, localized by the presentation layer. */
    val message: DomainMessage
  ) : QuotaCheckResult {
    val isBlocked: Boolean
      get() = true
  }

  val isAllowed: Boolean
    get() = this is Allowed

  val isExceeded: Boolean
    get() = this is Exceeded

  /** Which limit was exceeded, or `null` when the check passed. */
  fun errorMessageOrNull(): DomainMessage? = (this as? Exceeded)?.message
}

/** Enforces tiered resource quotas across clients and backend. */
object QuotaAndCostEnforcer {

  /**
   * Validates whether [ownerEmail] (or [ownerUserId]) can create a new survey within
   * [organizationId]. Platform administrators ([isPlatformAdmin]) may own any number of surveys.
   * [existingSurveys] are the surveys stored locally, which never include surveys in Trash.
   */
  fun checkCanCreateSurvey(
    ownerEmail: String,
    organizationId: String?,
    existingSurveys: List<SurveyPreviewItem>,
    globalLimits: QuotaLimitsConfig? = null,
    organization: Organization? = null,
    excludeSurveyId: String? = null,
    ownerUserId: String = "",
    isPlatformAdmin: Boolean = false,
  ): QuotaCheckResult {
    if (isPlatformAdmin) return QuotaCheckResult.Allowed
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
        .count {
          (ownerEmail.isNotBlank() && it.ownerEmail.equals(ownerEmail, ignoreCase = true)) ||
            (ownerUserId.isNotBlank() && it.ownerEmail == ownerUserId)
        }
    if (effectiveLimits.maxSurveysPerOwner in 1..ownedSurveysCount) {
      return QuotaCheckResult.Exceeded(
        QuotaMessage.MAX_SURVEYS_PER_OWNER.message(
          ownedSurveysCount,
          effectiveLimits.maxSurveysPerOwner,
        )
      )
    }

    return QuotaCheckResult.Allowed
  }

  /** Validates a survey editor draft against the resolved effective quota limits. */
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
        QuotaCountedMessage.MAX_FORMS.message(
          formCount,
          formCount,
          effectiveLimits.maxFormsPerSurvey,
          DomainMessage.Label(effectiveLimits.tier),
        )
      )
    }

    // Check questions per form & photo questions per form
    draft.forms.forEach { editorForm ->
      val qCount = editorForm.form.questions.size
      if (effectiveLimits.maxQuestionsPerForm in 1 until qCount) {
        return QuotaCheckResult.Exceeded(
          QuotaCountedMessage.MAX_QUESTIONS.message(
            qCount,
            editorForm.form.title,
            qCount,
            effectiveLimits.maxQuestionsPerForm,
          )
        )
      }
      val photoQCount = editorForm.form.questions.count { q -> q.type == EditorQuestionType.PHOTO }
      if (effectiveLimits.maxPhotoQuestionsPerForm in 1 until photoQCount) {
        return QuotaCheckResult.Exceeded(
          QuotaCountedMessage.MAX_PHOTO_QUESTIONS.message(
            photoQCount,
            editorForm.form.title,
            photoQCount,
            effectiveLimits.maxPhotoQuestionsPerForm,
          )
        )
      }
    }

    // Check predefined entities and generated entities
    val predefinedEntitiesCount = draft.datasets.filter { !it.isGenerated }.sumOf { it.rows.size }
    if (effectiveLimits.maxPredefinedEntities in 1 until predefinedEntitiesCount) {
      return QuotaCheckResult.Exceeded(
        QuotaCountedMessage.MAX_PREDEFINED_ENTITIES.message(
          predefinedEntitiesCount,
          predefinedEntitiesCount,
          effectiveLimits.maxPredefinedEntities,
        )
      )
    }

    val generatedEntitiesCount = draft.datasets.filter { it.isGenerated }.sumOf { it.rows.size }
    if (effectiveLimits.maxGeneratedEntities in 1 until generatedEntitiesCount) {
      return QuotaCheckResult.Exceeded(
        QuotaCountedMessage.MAX_GENERATED_ENTITIES.message(
          generatedEntitiesCount,
          generatedEntitiesCount,
          effectiveLimits.maxGeneratedEntities,
        )
      )
    }

    // Check area vertices on survey area and polygon datasets
    val areaVertices = draft.details.surveyArea?.vertexCount ?: 0
    if (effectiveLimits.maxAreaVertices in 1 until areaVertices) {
      return QuotaCheckResult.Exceeded(
        QuotaCountedMessage.MAX_AREA_VERTICES.message(
          areaVertices,
          areaVertices,
          effectiveLimits.maxAreaVertices,
        )
      )
    }
    draft.datasets
      .filter { it.geometryType == GeometryType.POLYGON }
      .forEach { ds ->
        val maxPolygonRowVertices = ds.rows.maxOfOrNull { it.geometry.size } ?: 0
        if (effectiveLimits.maxAreaVertices in 1 until maxPolygonRowVertices) {
          return QuotaCheckResult.Exceeded(
            QuotaCountedMessage.MAX_DATASET_POLYGON_VERTICES.message(
              maxPolygonRowVertices,
              ds.displayName,
              maxPolygonRowVertices,
              effectiveLimits.maxAreaVertices,
            )
          )
        }
      }

    // Check ACL users
    val aclUsersCount = draft.sharing.collaborators.size
    if (effectiveLimits.maxAclUsers in 1 until aclUsersCount) {
      return QuotaCheckResult.Exceeded(
        QuotaCountedMessage.MAX_COLLABORATORS.message(
          aclUsersCount,
          aclUsersCount,
          effectiveLimits.maxAclUsers,
        )
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
        QuotaCountedMessage.MAX_SURVEYS_PER_USER.message(
          userActiveSurveysCount,
          userActiveSurveysCount,
          effectiveLimits.maxSurveysPerUser,
        )
      )
    }

    if (isNewAdhocEntity && effectiveLimits.maxAdhocEntitiesPerUser in 1..userAdhocEntitiesCount) {
      return QuotaCheckResult.Exceeded(
        QuotaCountedMessage.MAX_ADHOC_ENTITIES.message(
          userAdhocEntitiesCount,
          userAdhocEntitiesCount,
          effectiveLimits.maxAdhocEntitiesPerUser,
        )
      )
    }

    if (effectiveLimits.maxSubmissionsPerEntityPerUser in 1..userSubmissionsForEntityCount) {
      return QuotaCheckResult.Exceeded(
        QuotaCountedMessage.MAX_SUBMISSIONS_PER_ENTITY.message(
          userSubmissionsForEntityCount,
          userSubmissionsForEntityCount,
          effectiveLimits.maxSubmissionsPerEntityPerUser,
        )
      )
    }

    return QuotaCheckResult.Allowed
  }
}
