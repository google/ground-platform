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
package org.groundplatform.v2.app.data.sync

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyState
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.datasource.local.store.SurveyBase
import org.groundplatform.v2.core.mutation.DataPatches
import org.groundplatform.v2.core.mutation.LibraryPatches
import org.groundplatform.v2.core.mutation.OrganizationPatches
import org.groundplatform.v2.core.mutation.SurveyPatches

/**
 * The local copy of a record: its server base with pending mutations applied in order, plus the
 * queued mutations that turned out not to change anything (they can be dropped from the outbox).
 */
data class Folded<T>(val value: T?, val noOpMutationIds: Set<String> = emptySet())

/**
 * Applies pending outbox mutations on top of server bases with the same patch functions the server
 * uses (`org.groundplatform.v2.core.mutation`), so the local copy previews what the server will
 * store. Rejected mutations are skipped. Server bookkeeping (revisions, commit timestamps) is kept
 * from the base.
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
      val upsert = em.upsert
      val update = em.update
      current =
        when {
          em.delete == true -> null
          upsert != null ->
            upsert.copy(
              entity_id = entityId,
              dataset_id = em.dataset_id.ifBlank { upsert.dataset_id },
              revision = current?.revision ?: base?.revision ?: 0L,
              commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
            )
          update != null && current != null -> {
            val next = DataPatches.applyEntity(current, update)
            if (next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current, noOps)
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
      val upsert = sm.upsert
      val update = sm.update
      current =
        when {
          sm.delete == true -> null
          upsert != null ->
            upsert.copy(
              submission_id = submissionId,
              form_id = sm.form_id.ifBlank { upsert.form_id },
              revision = current?.revision ?: base?.revision ?: 0L,
              commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
            )
          update != null && current != null -> {
            val next = DataPatches.applySubmission(current, update)
            if (next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current, noOps)
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
      val upsert = om.upsert_organization
      val patch = om.update_field
      current =
        when {
          om.delete_organization == true -> null
          upsert != null ->
            upsert.copy(
              organization_id = organizationId,
              version = current?.version ?: base?.version ?: 0L,
              commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
            )
          patch != null -> {
            val from = current ?: Organization(organization_id = organizationId)
            val next = OrganizationPatches.applyField(from, patch)
            if (next == current && m.isQueued()) noOps += m.mutationId
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
      val upsert = lm.upsert_library
      val patch = lm.update_field
      current =
        when {
          upsert != null ->
            upsert.copy(
              organization_id = organizationId,
              version = current?.version ?: base?.version ?: 0L,
              commit_timestamp = current?.commit_timestamp ?: base?.commit_timestamp,
            )
          patch != null -> {
            val from = current ?: OrganizationLibraryRecord(organization_id = organizationId)
            val next = LibraryPatches.applyField(from, patch)
            if (next == current && m.isQueued()) noOps += m.mutationId
            next
          }
          else -> current
        }
    }
    return Folded(current, noOps)
  }

  /**
   * The survey's saved revision, working copy, and ACL after [pending], following `MutateSurvey`:
   * field and snapshot mutations apply to the working copy (starting from the saved revision) when
   * sent with `working_copy`, saving promotes the working copy, and discarding drops it.
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
      fun setTarget(def: groundplatform.v2.survey.SurveyDef?) {
        if (wc) workingCopy = def else saved = def
      }
      val upSurvey = sm.upsert_survey
      val upAcl = sm.upsert_acl
      val field = sm.update_field
      val role = sm.update_acl_role
      val access = sm.update_access_rules
      when {
        sm.delete_survey == true -> {
          saved = saved?.copy(state = SurveyState.DELETED)
          workingCopy = null
        }
        upSurvey != null -> setTarget(upSurvey.copy(survey_id = surveyId))
        upAcl != null -> acl = upAcl.copy(survey_id = surveyId)
        sm.save_working_copy == true ->
          workingCopy?.let { toSave ->
            val number = (saved?.saved_revision_number ?: 0L) + 1L
            saved = toSave.copy(saved_revision_number = number, version = number.toString())
            workingCopy = null
          }
        sm.discard_working_copy == true -> workingCopy = null
        field != null -> targetDef()?.let { setTarget(SurveyPatches.applyField(it, field)) }
        role != null ->
          acl = SurveyPatches.applyAclRole(acl ?: SurveyAcl(survey_id = surveyId), role)
        access != null ->
          acl = SurveyPatches.applyAccessRules(acl ?: SurveyAcl(survey_id = surveyId), access)
      }
      val isFieldPatch = field != null || role != null || access != null
      if (isFieldPatch && m.isQueued() && before == Triple(saved, workingCopy, acl)) {
        noOps += m.mutationId
      }
    }
    if (saved == null && workingCopy == null && acl == null) return Folded(null, noOps)
    return Folded(SurveyBase(saved, workingCopy, acl), noOps)
  }
}
