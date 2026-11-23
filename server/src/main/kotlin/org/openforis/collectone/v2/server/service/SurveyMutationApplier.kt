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

import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.mutation.SurveyMutations
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.data.UserDataWriter
import org.openforis.collectone.v2.server.messaging.SurveyInviteNotification

/** What one `MutateSurvey` request wrote, and what to tell others about it. */
internal data class AppliedSurveyMutations(
  val saved: SurveyDef,
  val workingCopy: SurveyDef?,
  val acl: SurveyAcl,
  /** Whether the saved revision or the ACL changed, so subscribers refetch the definition. */
  val definitionChanged: Boolean,
  /** Invitations to email: pending ACL entries with a new invite token. */
  val invites: List<SurveyInviteNotification>,
)

/**
 * Applies the new mutations ([toApply]) of one `MutateSurvey` [request] in its write transaction
 * [tx], on behalf of [caller], who is already authorized to change the survey:
 * 1. folds each mutation into the survey's saved revision or working copy, its ACL, and its
 *    working-copy lifecycle (save or discard);
 * 2. assigns a new survey to its creator;
 * 3. enforces the closed-survey, organization membership, and design quota rules;
 * 4. stamps the server-owned saved revision number and audit info, which mutations can't set, and
 *    writes every part with the survey's next record version, and records the mutations applied.
 *
 * Field and snapshot mutations apply to the revision selected by `request.working_copy`.
 */
internal class SurveyMutationApplier(
  private val tx: UserDataWriter,
  private val request: MutateSurveyRequest,
  private val caller: Caller?,
  private val toApply: List<SurveyMutation>,
) {
  private val surveyId = request.survey_id
  private val existingSaved: SurveyDef? = tx.survey(surveyId)
  private val beforeAcl: SurveyAcl? = tx.acl(surveyId)
  private val existingAcl = beforeAcl ?: SurveyAcl(survey_id = surveyId)
  private val isNewSurvey = existingSaved == null

  private val existingWorkingCopy: SurveyDef? = tx.workingCopy(surveyId)

  private var saved =
    existingSaved ?: SurveyDef(survey_id = surveyId, state = SurveyState.PUBLISHED)
  private var workingCopy = existingWorkingCopy
  private var acl = existingAcl
  private var savedRevisionChanged = false

  /** Server-owned: a new survey starts at saved revision 1, and each save increments it. */
  private var savedRevisionNumber = existingSaved?.saved_revision_number ?: 1L

  private val savesWorkingCopy = toApply.any { it.save_working_copy }
  private val isDiscardOnly = toApply.all {
    it.discard_working_copy &&
      it.upsert == null &&
      it.delete == null &&
      it.acl_upsert == null &&
      it.acl_delete == null &&
      !it.save_working_copy
  }

  private var target: SurveyDef
    get() = if (request.working_copy) workingCopy ?: saved else saved
    set(def) {
      if (request.working_copy) workingCopy = def else saved = def
    }

  fun apply(): AppliedSurveyMutations {
    checkRequest()
    for (mutation in toApply) {
      foldSurvey(mutation)
      foldAcl(mutation)
      foldWorkingCopyLifecycle(mutation)
    }
    assignToCreator()
    checkNotClosed()
    checkOrganizationMembership()
    if (shouldCheckDesignQuotas()) {
      val owner = target.owner_id.ifBlank { saved.owner_id.ifBlank { acl.owner_id } }
      tx.checkSurveyDesignQuotas(
        surveyId = surveyId,
        design = target,
        owner = owner,
        ownerEmail = ownerEmail(owner),
        acl = acl,
        isNewSurvey = isNewSurvey,
        exemptFromSurveysPerOwner = isAuthenticatedAdmin(),
      )
    }
    return write()
  }

  /**
   * The normalized email of the survey's [owner], so surveys owned by that person count toward
   * their quota whether they were stored under their user ID or their email: an email-shaped owner
   * ID, the ACL's owner, or the verified email of the caller creating the survey.
   */
  private fun ownerEmail(owner: String): String {
    val stored =
      listOf(owner, target.owner_id, saved.owner_id, acl.owner_id).firstOrNull { '@' in it }
    if (stored != null) return stored.trim().lowercase()
    val creator = caller?.takeUnless { it.isDefault } ?: return ""
    return if (creator.emailVerified && (owner.isBlank() || owner == creator.userId)) {
      creator.normalizedEmail
    } else {
      ""
    }
  }

  /**
   * Whether the caller is a signed-in administrator, who may own any number of surveys. The
   * development fallback user and calls without a caller don't count, so quotas still apply to
   * them.
   */
  private fun isAuthenticatedAdmin(): Boolean =
    caller != null && !caller.isDefault && Access(tx, caller).isAdmin

  private fun checkRequest() {
    require(toApply.none { it.acl_upsert?.default_role == Role.SURVEY_ORGANIZER }) {
      "default_role can't be SURVEY_ORGANIZER; organizers must be named on the ACL"
    }
    val replacesWholeRecord = toApply.any {
      (it.upsert?.survey_id?.isNotEmpty() == true &&
        it.upsert?.trash_time == null &&
        it.delete == null) ||
        (it.acl_upsert?.survey_id?.isNotEmpty() == true && it.acl_delete == null)
    }
    if (replacesWholeRecord) {
      val baseTarget =
        if (request.working_copy) tx.workingCopy(surveyId) ?: tx.survey(surveyId)
        else tx.survey(surveyId)
      checkBaseVersion(request.base_version, baseTarget?.version, "survey $surveyId")
    }
  }

  /**
   * Applies [mutation]'s survey patch or replacement, and its Trash change. The server-owned fields
   * of the upsert are ignored ([write] stamps them).
   */
  private fun foldSurvey(mutation: SurveyMutation) {
    val delSurvey = mutation.delete
    val upSurvey = mutation.upsert?.withoutServerOwnedFields()
    if (delSurvey?.trash_time != null) {
      saved = saved.copy(trash_time = null)
    }
    if (upSurvey?.trash_time != null) {
      if (saved.trash_time == null) saved = saved.copy(trash_time = tx.now)
      // A survey in Trash can't be edited, so its unsaved edits are dropped.
      workingCopy = null
    }
    val delSurveyWithoutTrash = delSurvey?.copy(trash_time = null)?.takeIf { it != SurveyDef() }
    val upSurveyWithoutTrash = upSurvey?.copy(trash_time = null)?.takeIf { it != SurveyDef() }
    if (
      upSurveyWithoutTrash != null &&
        upSurveyWithoutTrash.survey_id.isNotEmpty() &&
        delSurveyWithoutTrash == null
    ) {
      target = upSurveyWithoutTrash.copy(survey_id = surveyId, trash_time = saved.trash_time)
    } else if (delSurveyWithoutTrash != null || upSurveyWithoutTrash != null) {
      target = SurveyMutations.apply(target, delSurveyWithoutTrash, upSurveyWithoutTrash)
    }
  }

  /** Applies [mutation]'s ACL patch or replacement. */
  private fun foldAcl(mutation: SurveyMutation) {
    val delAcl = mutation.acl_delete
    val upAcl = mutation.acl_upsert
    if (upAcl != null && upAcl.survey_id.isNotEmpty() && delAcl == null) {
      acl = upAcl.copy(survey_id = surveyId)
    } else if (delAcl != null || upAcl != null) {
      acl = SurveyMutations.applyAcl(acl, delAcl, upAcl)
    }
  }

  /** Saves the working copy as the next saved revision, or discards it, as [mutation] asks. */
  private fun foldWorkingCopyLifecycle(mutation: SurveyMutation) {
    if (mutation.save_working_copy) {
      val toSave = workingCopy
      if (toSave != null) {
        savedRevisionNumber += 1L
        saved = toSave.copy(version = savedRevisionNumber.toString())
        workingCopy = null
        savedRevisionChanged = true
      }
    }
    if (mutation.discard_working_copy) {
      workingCopy = null
    }
  }

  /** A new survey belongs to its creator unless the request names an owner. */
  private fun assignToCreator() {
    val creator = caller?.takeUnless { it.isDefault }
    if (!isNewSurvey || creator == null) return
    if (saved.owner_id.isBlank()) {
      saved = saved.copy(owner_id = creator.userId)
    }
    val wc = workingCopy
    if (wc != null && wc.owner_id.isBlank()) {
      workingCopy = wc.copy(owner_id = creator.userId)
    }
  }

  /**
   * Whether the saved revision changed beyond its lifecycle fields (state, outcome, Trash) and the
   * server-owned fields.
   */
  private fun savedContentChanged(existing: SurveyDef): Boolean =
    saved
      .copy(
        state = existing.state,
        closed_at = existing.closed_at,
        outcome = existing.outcome,
        trash_time = existing.trash_time,
      )
      .withoutServerOwnedFields() != existing.withoutServerOwnedFields()

  /**
   * A closed or archived survey takes only lifecycle changes (outcome, archiving, Trash, discarding
   * a working copy), unless the request reopens it or switches it to draft.
   */
  private fun checkNotClosed() {
    val existing = existingSaved ?: return
    val explicitlyUnlocked =
      !request.working_copy &&
        !savesWorkingCopy &&
        (saved.state == SurveyState.PUBLISHED || saved.state == SurveyState.DRAFT)
    if (!existing.isClosedOrArchived || explicitlyUnlocked) return
    val invalidTransition =
      existing.state == SurveyState.ARCHIVED && saved.state == SurveyState.CLOSED
    val workingCopyEdit = request.working_copy && !isDiscardOnly
    val designChanged =
      workingCopyEdit || savesWorkingCopy || acl != existingAcl || savedContentChanged(existing)
    if (invalidTransition || designChanged) {
      throw closedSurveyError(existing, ClosedSurveyRefusal.EDITS)
    }
  }

  /** Moving a survey into an organization takes an active membership there (or administrator). */
  private fun checkOrganizationMembership() {
    val newOrgId = target.organization_id
    val oldOrgId = existingSaved?.organization_id.orEmpty()
    if (newOrgId.isNotBlank() && (isNewSurvey || newOrgId != oldOrgId)) {
      val access = Access(tx, caller)
      if (!access.isAdmin && !access.isActiveMember(newOrgId)) {
        throw Access.denied("organization $newOrgId")
      }
    }
  }

  /**
   * Design quotas apply to surveys that can still change: not deleted, closed, archived, or in
   * Trash, and not to requests that only discard a working copy or switch to draft.
   */
  private fun shouldCheckDesignQuotas(): Boolean {
    val existing = existingSaved
    val isSwitchToDraftWithoutDesignChanges =
      existing != null &&
        !request.working_copy &&
        saved.state == SurveyState.DRAFT &&
        acl == existingAcl &&
        !savedContentChanged(existing)
    return saved.state != SurveyState.DELETED &&
      saved.state != SurveyState.CLOSED &&
      saved.state != SurveyState.ARCHIVED &&
      saved.trash_time == null &&
      !isDiscardOnly &&
      !isSwitchToDraftWithoutDesignChanges
  }

  /**
   * Always writes the saved revision, so overview delta polls see working-copy and access changes,
   * and bumps the survey's shared record version on all of its parts.
   */
  private fun write(): AppliedSurveyMutations {
    val recordVersion = tx.nextSurveyRecordVersion(surveyId)
    val definitionChanged =
      isNewSurvey ||
        !request.working_copy ||
        savedRevisionChanged ||
        toApply.any {
          it.upsert?.trash_time != null ||
            it.delete?.trash_time != null ||
            it.acl_delete != null ||
            it.acl_upsert != null
        }
    val stamp = AuditStamp(caller?.takeUnless { it.isDefault }?.userId.orEmpty(), tx.now)
    val finalSaved = stampedSaved(stamp).copy(record_version = recordVersion)
    val finalWorkingCopy =
      stampedWorkingCopy(stamp, finalSaved)?.copy(record_version = recordVersion)
    tx.putSurvey(finalSaved)
    if (savedRevisionChanged) tx.putSurveyRevision(finalSaved)
    val access = AccessChanges()
    val writtenAcl = tx.putAclTracked(acl.copy(record_version = recordVersion), access)
    tx.applyAccessChanges(access)
    tx.putWorkingCopy(surveyId, finalWorkingCopy)
    tx.recordApplied(
      scope = "surveys/$surveyId",
      identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
    )
    return AppliedSurveyMutations(
      saved = finalSaved,
      workingCopy = finalWorkingCopy,
      acl = writtenAcl,
      definitionChanged = definitionChanged,
      invites = newInvites(writtenAcl, effectiveDef = finalWorkingCopy ?: finalSaved, finalSaved),
    )
  }

  /**
   * The saved revision with its server-owned fields: the saved revision number, and audit info
   * stamped by [stamp] when this request creates or changes it.
   */
  private fun stampedSaved(stamp: AuditStamp): SurveyDef {
    val stored = existingSaved
    val audit =
      when {
        stored == null -> stamp.created()
        changedFrom(stored, saved) -> stamp.modified(stored.audit_info)
        else -> stored.audit_info
      }
    return saved.copy(saved_revision_number = savedRevisionNumber, audit_info = audit)
  }

  /**
   * The working copy with its server-owned fields: the number of the saved revision it edits, and
   * audit info stamped by [stamp] when this request creates or changes it. A new working copy keeps
   * the survey's creation details from [finalSaved].
   */
  private fun stampedWorkingCopy(stamp: AuditStamp, finalSaved: SurveyDef): SurveyDef? {
    val current = workingCopy ?: return null
    val stored = existingWorkingCopy
    val audit =
      when {
        stored == null -> stamp.modified(finalSaved.audit_info)
        changedFrom(stored, current) -> stamp.modified(stored.audit_info)
        else -> stored.audit_info
      }
    return current.copy(saved_revision_number = savedRevisionNumber, audit_info = audit)
  }

  /** Whether [current] differs from [stored] beyond server-owned fields and bookkeeping. */
  private fun changedFrom(stored: SurveyDef, current: SurveyDef): Boolean {
    fun content(def: SurveyDef) =
      def.withoutServerOwnedFields().copy(record_version = 0L, commit_timestamp = null)
    return content(stored) != content(current)
  }

  /** Pending entries of [writtenAcl] whose invite token is new, as invitations to email. */
  private fun newInvites(
    writtenAcl: SurveyAcl,
    effectiveDef: SurveyDef,
    finalSaved: SurveyDef,
  ): List<SurveyInviteNotification> {
    val previousInviteTokensByEmail =
      beforeAcl
        ?.entries
        .orEmpty()
        .filter { it.email.isNotBlank() }
        .associate { it.email.trim().lowercase() to it.invite_token }
    val orgName =
      effectiveDef.organization_id
        .takeIf { it.isNotBlank() }
        ?.let { tx.organization(it)?.name }
        .orEmpty()
    return writtenAcl.entries
      .filter { entry ->
        entry.invitation_status == AclEntry.InvitationStatus.PENDING &&
          entry.email.isNotBlank() &&
          entry.invite_token.isNotBlank() &&
          previousInviteTokensByEmail[entry.email.trim().lowercase()] != entry.invite_token
      }
      .map { entry ->
        val normalizedEmail = entry.email.trim().lowercase()
        SurveyInviteNotification(
          idempotencyKey = "survey:$surveyId:$normalizedEmail:${entry.invite_token}",
          surveyId = surveyId,
          surveyTitle = effectiveDef.title.ifBlank { finalSaved.title },
          organizationId = effectiveDef.organization_id,
          organizationName = orgName,
          recipientEmail = entry.email.trim(),
          role = entry.role,
          inviteToken = entry.invite_token,
          inviterEmail = caller?.email.orEmpty(),
          inviterDisplayName = caller?.displayName.orEmpty(),
        )
      }
  }
}
