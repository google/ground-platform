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
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import io.grpc.StatusException
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.QuotaTier
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.server.data.UserDataReader

internal fun UserDataReader.resolveEffectiveQuota(
  survey: SurveyDef?,
  organizationId: String?,
): QuotaLimits {
  val global = organization(ALL_USERS_ORGANIZATION_ID)?.quota_limits
  val org = organizationId?.takeIf { it.isNotBlank() }?.let { organization(it)?.quota_limits }
  val s = survey?.quota_limits

  val effectiveTier =
    when {
      s?.tier != null && s.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> s.tier
      org?.tier != null && org.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> org.tier
      organizationId?.isNotBlank() == true -> QuotaTier.SPONSORED
      global?.tier != null && global.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED -> global.tier
      else -> QuotaTier.BASIC
    }

  val isSponsored = effectiveTier == QuotaTier.SPONSORED
  val defaultMaxSurveysPerOwner = if (isSponsored) 100 else 5
  val defaultMaxFormsPerSurvey = if (isSponsored) 10 else 5
  val defaultMaxQuestionsPerForm = if (isSponsored) 50 else 10
  val defaultMaxPhotoQuestionsPerForm = if (isSponsored) 10 else 1
  val defaultMaxPredefinedEntities = if (isSponsored) 10_000 else 1_000
  val defaultMaxAclUsers = if (isSponsored) 50 else 10
  val defaultMaxSurveysPerUser = if (isSponsored) 100 else 5
  val defaultMaxAdhocEntitiesPerUser = if (isSponsored) 200 else 100
  val defaultMaxSubmissionsPerEntityPerUser = if (isSponsored) 50 else 10
  val defaultMaxGeneratedEntities = if (isSponsored) 100_000 else 10_000
  val defaultMaxAreaVertices = if (isSponsored) 50_000 else 5_000

  fun resolveInt(sVal: Int?, orgVal: Int?, gVal: Int?, default: Int): Int =
    when {
      sVal != null && sVal > 0 -> sVal
      orgVal != null && orgVal > 0 -> orgVal
      gVal != null && gVal > 0 -> gVal
      else -> default
    }

  return QuotaLimits(
    tier = effectiveTier,
    max_surveys_per_owner =
      resolveInt(
        s?.max_surveys_per_owner,
        org?.max_surveys_per_owner,
        global?.max_surveys_per_owner,
        defaultMaxSurveysPerOwner,
      ),
    max_forms_per_survey =
      resolveInt(
        s?.max_forms_per_survey,
        org?.max_forms_per_survey,
        global?.max_forms_per_survey,
        defaultMaxFormsPerSurvey,
      ),
    max_questions_per_form =
      resolveInt(
        s?.max_questions_per_form,
        org?.max_questions_per_form,
        global?.max_questions_per_form,
        defaultMaxQuestionsPerForm,
      ),
    max_photo_questions_per_form =
      resolveInt(
        s?.max_photo_questions_per_form,
        org?.max_photo_questions_per_form,
        global?.max_photo_questions_per_form,
        defaultMaxPhotoQuestionsPerForm,
      ),
    max_predefined_entities =
      resolveInt(
        s?.max_predefined_entities,
        org?.max_predefined_entities,
        global?.max_predefined_entities,
        defaultMaxPredefinedEntities,
      ),
    max_acl_users =
      resolveInt(s?.max_acl_users, org?.max_acl_users, global?.max_acl_users, defaultMaxAclUsers),
    max_surveys_per_user =
      resolveInt(
        s?.max_surveys_per_user,
        org?.max_surveys_per_user,
        global?.max_surveys_per_user,
        defaultMaxSurveysPerUser,
      ),
    max_adhoc_entities_per_user =
      resolveInt(
        s?.max_adhoc_entities_per_user,
        org?.max_adhoc_entities_per_user,
        global?.max_adhoc_entities_per_user,
        defaultMaxAdhocEntitiesPerUser,
      ),
    max_submissions_per_entity_per_user =
      resolveInt(
        s?.max_submissions_per_entity_per_user,
        org?.max_submissions_per_entity_per_user,
        global?.max_submissions_per_entity_per_user,
        defaultMaxSubmissionsPerEntityPerUser,
      ),
    max_generated_entities =
      resolveInt(
        s?.max_generated_entities,
        org?.max_generated_entities,
        global?.max_generated_entities,
        defaultMaxGeneratedEntities,
      ),
    max_area_vertices =
      resolveInt(
        s?.max_area_vertices,
        org?.max_area_vertices,
        global?.max_area_vertices,
        defaultMaxAreaVertices,
      ),
  )
}

internal fun countUserQuestions(form: openforis.collectone.v1.forms.FormDef): Int {
  val bindings = form.model?.bindings
  if (!bindings.isNullOrEmpty()) {
    return bindings.count {
      it.type != DataType.TYPE_MESSAGE &&
        !it.field_path.startsWith("orx:") &&
        !it.field_path.substringAfterLast('/').startsWith("__")
    }
  }
  return countViewControls(form.view?.components.orEmpty())
}

internal fun countViewControls(components: List<openforis.collectone.v1.forms.ViewComponent>): Int =
  components.sumOf { comp ->
    val ctrl = comp.control
    val grp = comp.group
    val rep = comp.repeat
    when {
      ctrl != null -> 1
      grp != null -> countViewControls(grp.components)
      rep != null -> countViewControls(rep.components)
      else -> 0
    }
  }

internal fun countPhotoControls(
  components: List<openforis.collectone.v1.forms.ViewComponent>
): Int = components.sumOf { comp ->
  val ctrl = comp.control
  val grp = comp.group
  val rep = comp.repeat
  when {
    ctrl != null -> {
      val media = ctrl.media_type.lowercase()
      if (
        ctrl.type == openforis.collectone.v1.forms.ControlType.CONTROL_UPLOAD &&
          (media.isBlank() || media.startsWith("image"))
      ) {
        1
      } else {
        0
      }
    }
    grp != null -> countPhotoControls(grp.components)
    rep != null -> countPhotoControls(rep.components)
    else -> 0
  }
}

internal fun countPhotoQuestions(form: openforis.collectone.v1.forms.FormDef): Int {
  val components = form.view?.components.orEmpty()
  if (components.isNotEmpty()) {
    return countPhotoControls(components)
  }
  return form.model?.bindings?.count { it.type == DataType.TYPE_BINARY } ?: 0
}

/** The `RESOURCE_EXHAUSTED` error for a request that exceeds a quota, described by [message]. */
internal fun quotaExceeded(message: String): StatusException =
  StatusException(Status.RESOURCE_EXHAUSTED.withDescription(message))

/**
 * Fails with [quotaExceeded] when [design] (the saved revision or working copy a `MutateSurvey`
 * request produces for survey [surveyId]) exceeds its effective quota: surveys per [owner] (for a
 * new survey, unless [exemptFromSurveysPerOwner]), forms, questions, photo questions, collaborators
 * on [acl], dataset features, and polygon vertices. [ownerEmail] is the owner's normalized email,
 * or empty if unknown.
 */
internal fun UserDataReader.checkSurveyDesignQuotas(
  surveyId: String,
  design: SurveyDef,
  owner: String,
  acl: SurveyAcl,
  isNewSurvey: Boolean,
  ownerEmail: String = "",
  exemptFromSurveysPerOwner: Boolean = false,
) {
  val limits = resolveEffectiveQuota(design, design.organization_id)
  if (isNewSurvey && !exemptFromSurveysPerOwner) {
    checkSurveysPerOwner(surveyId, owner, ownerEmail, limits)
  }
  checkFormQuotas(design, limits)
  if (limits.max_acl_users > 0 && acl.entries.size > limits.max_acl_users) {
    throw quotaExceeded(
      "ACL quota exceeded: survey has ${acl.entries.size} collaborators, exceeding maximum of " +
        "${limits.max_acl_users}"
    )
  }
  checkDatasetQuotas(design, limits)
  checkVertexQuotas(design, limits)
}

/**
 * Fails when [owner] (a user ID or email, also known by [ownerEmail]) already owns
 * `max_surveys_per_owner` surveys. Surveys in Trash or deleted don't count.
 */
private fun UserDataReader.checkSurveysPerOwner(
  surveyId: String,
  owner: String,
  ownerEmail: String,
  limits: QuotaLimits,
) {
  if (owner.isBlank() || limits.max_surveys_per_owner <= 0) return
  val ownerSurveys =
    surveysOwnedBy(userId = owner, email = ownerEmail).count {
      it.survey_id != surveyId && it.state != SurveyState.DELETED && it.trash_time == null
    }
  if (ownerSurveys >= limits.max_surveys_per_owner) {
    throw quotaExceeded(
      "Survey quota exceeded: maximum surveys per owner reached (${limits.max_surveys_per_owner})"
    )
  }
}

private fun checkFormQuotas(design: SurveyDef, limits: QuotaLimits) {
  val distinctForms =
    design.forms.let { forms ->
      if (forms.size > 1) forms.filter { it.form_id != "__primary_form__" } else forms
    }
  if (limits.max_forms_per_survey > 0 && distinctForms.size > limits.max_forms_per_survey) {
    throw quotaExceeded(
      "Form quota exceeded: survey has ${distinctForms.size} forms, exceeding maximum of " +
        "${limits.max_forms_per_survey}"
    )
  }
  distinctForms.forEach { checkFormContentQuotas(it, limits) }
}

private fun checkFormContentQuotas(form: FormDef, limits: QuotaLimits) {
  val questionsCount = countUserQuestions(form)
  if (limits.max_questions_per_form > 0 && questionsCount > limits.max_questions_per_form) {
    throw quotaExceeded(
      "Question quota exceeded: form \"${form.form_id}\" has $questionsCount questions, " +
        "exceeding maximum of ${limits.max_questions_per_form}"
    )
  }
  val photoQuestions = countPhotoQuestions(form)
  if (
    limits.max_photo_questions_per_form > 0 && photoQuestions > limits.max_photo_questions_per_form
  ) {
    throw quotaExceeded(
      "Photo question quota exceeded: form \"${form.form_id}\" has $photoQuestions photo " +
        "questions, exceeding maximum of ${limits.max_photo_questions_per_form}"
    )
  }
}

private fun checkDatasetQuotas(design: SurveyDef, limits: QuotaLimits) {
  if (limits.max_predefined_entities > 0) {
    val predefinedDatasetRows =
      design.entity_datasets.filter { it.sampling == null }.sumOf { it.rows.size }
    if (predefinedDatasetRows > limits.max_predefined_entities) {
      throw quotaExceeded(
        "Predefined entity quota exceeded: survey datasets contain $predefinedDatasetRows " +
          "predefined rows, exceeding maximum of ${limits.max_predefined_entities}"
      )
    }
  }
  if (limits.max_generated_entities > 0) {
    val generatedDatasetEntities =
      design.entity_datasets
        .filter { it.sampling != null }
        .sumOf { ds -> maxOf(ds.rows.size.toLong(), ds.sampling?.feature_count ?: 0L) }
    if (generatedDatasetEntities > limits.max_generated_entities) {
      throw quotaExceeded(
        "Generated entity quota exceeded: survey datasets contain $generatedDatasetEntities " +
          "generated sample plots, exceeding maximum of ${limits.max_generated_entities}"
      )
    }
  }
}

private fun checkVertexQuotas(design: SurveyDef, limits: QuotaLimits) {
  if (limits.max_area_vertices <= 0) return
  val surveyArea = design.map_config?.survey_area
  if (surveyArea != null) {
    val totalVertices = surveyArea.boundary?.let(GeometryProtos::vertexCount) ?: 0
    if (totalVertices > limits.max_area_vertices) {
      throw quotaExceeded(
        "Survey area vertex quota exceeded: survey area has $totalVertices vertices, exceeding " +
          "maximum of ${limits.max_area_vertices}"
      )
    }
  }
  for (ds in design.entity_datasets) {
    val maxRowVertices =
      ds.rows.maxOfOrNull { row -> row.geometry?.let(GeometryProtos::vertexCount) ?: 0 } ?: 0
    if (maxRowVertices > limits.max_area_vertices) {
      throw quotaExceeded(
        "Polygon vertex quota exceeded: dataset \"${ds.display_name.ifBlank { ds.id }}\" has a " +
          "feature with $maxRowVertices vertices, exceeding maximum of ${limits.max_area_vertices}"
      )
    }
  }
}
