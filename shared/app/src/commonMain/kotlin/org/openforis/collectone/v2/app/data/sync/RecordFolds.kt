/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.data.sync

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyState
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.core.mutation.DataPatches
import org.openforis.collectone.v2.core.mutation.LibraryPatches
import org.openforis.collectone.v2.core.mutation.OrganizationPatches
import org.openforis.collectone.v2.core.mutation.SurveyPatches

/**
 * The local copy of a record: its server base with pending mutations applied in order, plus the
 * queued mutations that turned out not to change anything (they can be dropped from the outbox).
 */
data class Folded<T>(val value: T?, val noOpMutationIds: Set<String> = emptySet())

/**
 * Applies pending outbox mutations on top of server bases with the same patch functions the server
 * uses (`org.openforis.collectone.v2.core.mutation`), so the local copy previews what the server
 * will store. Rejected mutations are skipped. Server bookkeeping (revisions, commit timestamps) is
 * kept from the base.
 */
object RecordFolds {
  private fun PendingMutation.applies(): Boolean = state != OutboxState.REJECTED

  private fun PendingMutation.isQueued(): Boolean = state == OutboxState.QUEUED

  /** The entity [entityId] after [pending] (deleted or never created: `null`). */
  fun entity(
    base: EntityRecord?,
    entityId: String,
    pending: List<PendingMutation>,
  ): Folded<EntityRecord> {
    var current = base?.takeUnless { it.is_deleted }
    val noOps = mutableSetOf<String>()
    for (m in pending) {
      if (!m.applies()) continue
      val em = (m.payload as? MutationPayload.Data)?.mutation?.entity_mutation ?: continue
      if (em.entity_id != entityId) continue
      val delete = em.delete
      val upsert = em.upsert
      current =
        when {
          DataPatches.isEntityRootDelete(delete, upsert) -> null
          delete != null || upsert != null -> {
            val from = current ?: EntityRecord(entity_id = entityId, dataset_id = em.dataset_id)
            val next =
              DataPatches.applyEntity(from, delete, upsert)
                .copy(
                  entity_id = entityId,
                  dataset_id =
                    em.dataset_id.ifBlank {
                      upsert?.dataset_id.orEmpty().ifBlank { from.dataset_id }
                    },
                  revision = current?.revision ?: base?.revision ?: 0L,
                  commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
                )
            if (current != null && next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current?.takeUnless { it.is_deleted }, noOps)
  }

  /** The submission [submissionId] after [pending] (deleted or never created: `null`). */
  fun submission(
    base: SubmissionRecord?,
    submissionId: String,
    pending: List<PendingMutation>,
  ): Folded<SubmissionRecord> {
    var current = base?.takeUnless { it.is_deleted }
    val noOps = mutableSetOf<String>()
    for (m in pending) {
      if (!m.applies()) continue
      val sm = (m.payload as? MutationPayload.Data)?.mutation?.submission_mutation ?: continue
      if (sm.submission_id != submissionId) continue
      val delete = sm.delete
      val upsert = sm.upsert
      current =
        when {
          DataPatches.isSubmissionRootDelete(delete, upsert) -> null
          delete != null || upsert != null -> {
            val from =
              current ?: SubmissionRecord(submission_id = submissionId, form_id = sm.form_id)
            val next =
              DataPatches.applySubmission(from, delete, upsert)
                .copy(
                  submission_id = submissionId,
                  form_id =
                    sm.form_id.ifBlank { upsert?.form_id.orEmpty().ifBlank { from.form_id } },
                  revision = current?.revision ?: base?.revision ?: 0L,
                  commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
                )
            if (current != null && next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current?.takeUnless { it.is_deleted }, noOps)
  }

  /** The organization after [pending] (deleted: `null`). */
  fun organization(
    base: Organization?,
    organizationId: String,
    pending: List<PendingMutation>,
  ): Folded<Organization> {
    var current = base?.takeUnless { it.is_deleted }
    val noOps = mutableSetOf<String>()
    for (m in pending) {
      if (!m.applies()) continue
      val om = (m.payload as? MutationPayload.Organization)?.mutation ?: continue
      val delete = om.delete
      val upsert = om.upsert
      current =
        when {
          om.delete_organization -> null
          delete != null || upsert != null -> {
            val from = current ?: Organization(organization_id = organizationId)
            val next =
              OrganizationPatches.apply(from, delete, upsert)
                .copy(
                  organization_id = organizationId,
                  version = current?.version ?: base?.version ?: 0L,
                  commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
                )
            if (current != null && next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current?.takeUnless { it.is_deleted }, noOps)
  }

  /** The organization library after [pending]. */
  fun library(
    base: OrganizationLibraryRecord?,
    organizationId: String,
    pending: List<PendingMutation>,
  ): Folded<OrganizationLibraryRecord> {
    var current = base?.takeUnless { it.is_deleted }
    val noOps = mutableSetOf<String>()
    for (m in pending) {
      if (!m.applies()) continue
      val lm = (m.payload as? MutationPayload.Library)?.mutation ?: continue
      val delete = lm.delete
      val upsert = lm.upsert
      current =
        when {
          delete != null || upsert != null -> {
            val from = current ?: OrganizationLibraryRecord(organization_id = organizationId)
            val next =
              LibraryPatches.apply(from, delete, upsert)
                .copy(
                  organization_id = organizationId,
                  version = current?.version ?: base?.version ?: 0L,
                  commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
                )
            if (current != null && next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current, noOps)
  }

  /**
   * The survey's saved revision, working copy, and ACL after [pending], following `MutateSurvey`:
   * sparse `delete` and `upsert` mutations apply to the working copy (starting from the saved
   * revision) when sent with `working_copy`, saving promotes the working copy, and discarding drops
   * it.
   */
  fun survey(
    base: SurveyBase?,
    surveyId: String,
    pending: List<PendingMutation>,
  ): Folded<SurveyBase> {
    var saved = base?.saved
    var workingCopy = base?.workingCopy
    var acl = base?.acl
    val noOps = mutableSetOf<String>()
    for (m in pending) {
      if (!m.applies()) continue
      val target = m.target as? MutationTarget.Survey ?: continue
      if (target.surveyId != surveyId) continue
      val sm = (m.payload as? MutationPayload.Survey)?.mutation ?: continue
      val wc = target.workingCopy
      val before = Triple(saved, workingCopy, acl)
      fun targetDef() = if (wc) workingCopy ?: saved else saved
      fun setTarget(def: SurveyDef?) {
        if (wc) workingCopy = def else saved = def
      }
      val delSurvey = sm.delete
      val upSurvey = sm.upsert
      val delAcl = sm.acl_delete
      val upAcl = sm.acl_upsert
      when {
        // Locally, a trashed survey disappears at once, as it does from the server's survey list.
        sm.trash_survey -> {
          saved = saved?.copy(state = SurveyState.DELETED, trash_time = m.clientTimestamp)
          workingCopy = null
        }
        sm.restore_survey -> saved = saved?.copy(trash_time = null)
        sm.save_working_copy ->
          workingCopy?.let { toSave ->
            val number = (saved?.saved_revision_number ?: 0L) + 1L
            saved = toSave.copy(saved_revision_number = number, version = number.toString())
            workingCopy = null
          }
        sm.discard_working_copy -> workingCopy = null
        else -> {
          if (delSurvey != null || upSurvey != null) {
            val existingTarget = targetDef()
            if (existingTarget != null || upSurvey?.survey_id?.isNotEmpty() == true) {
              val from = existingTarget ?: SurveyDef(survey_id = surveyId)
              setTarget(SurveyPatches.apply(from, delSurvey, upSurvey).copy(survey_id = surveyId))
            }
          }
          if (delAcl != null || upAcl != null) {
            val from = acl ?: SurveyAcl(survey_id = surveyId)
            acl = SurveyPatches.applyAcl(from, delAcl, upAcl).copy(survey_id = surveyId)
          }
        }
      }
      val isPatch = upSurvey != null || delSurvey != null || upAcl != null || delAcl != null
      val isInitialCreate =
        before.first == null &&
          before.second == null &&
          (upSurvey?.survey_id?.isNotEmpty() == true || upAcl?.survey_id?.isNotEmpty() == true)
      if (
        isPatch && !isInitialCreate && m.isQueued() && before == Triple(saved, workingCopy, acl)
      ) {
        noOps += m.mutationId
      }
    }
    if (saved == null && workingCopy == null && acl == null) return Folded(null, noOps)
    return Folded(SurveyBase(saved, workingCopy, acl), noOps)
  }
}
