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
package org.openforis.collectone.v2.server.data

import com.google.cloud.spanner.Key
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.TransactionContext
import com.squareup.wire.Instant
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.PlotLock
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.UserDataSeed

/**
 * [UserDataWriter] that buffers Spanner mutations in a read-write transaction of
 * [SpannerUserDataStore]. Spanner applies buffered mutations only at commit, so reads in the same
 * block see the snapshot from before the block's writes.
 *
 * Writes of a survey and of its ACL are held back until [flush], which the store calls when the
 * block returns: both live in one `Surveys` row, and a new row must be inserted with its
 * definition, so they are merged into one mutation. Spanner may run the block again when the
 * transaction aborts, so each attempt gets a new writer.
 */
internal class SpannerUserDataWriter(
  private val tx: TransactionContext,
  override val now: Instant,
  seed: UserDataSeed,
) : SpannerUserDataReader(tx, now, seed), UserDataWriter {

  override fun putOrganization(organization: Organization) {
    val id = uuid(organization.organization_id)
    tx.buffer(organizationRow(organization, null))
    tx.buffer(Mutation.delete("OrganizationMembers", KeySet.prefixRange(Key.of(id))))
    organization.members.forEach { tx.buffer(memberRow(organization.organization_id, it, null)) }
  }

  override fun putLibrary(library: OrganizationLibraryRecord) {
    val id = uuid(library.organization_id)
    tx.buffer(libraryRow(library, null))
    tx.buffer(Mutation.delete("LibraryConcepts", KeySet.prefixRange(Key.of(id))))
    conceptRows(library).forEach(tx::buffer)
  }

  // A survey and its ACL share a row; a new row must be written with its definition, so writes
  // to both are merged into one mutation when the block ends ([flush]).
  private val pendingSurveys = linkedMapOf<String, SurveyDef>()
  private val pendingAcls = linkedMapOf<String, SurveyAcl>()

  // Surveys this block creates (no stored row when first put): their first saved revision is also
  // recorded in SurveyRevisions, which later saves do with putSurveyRevision.
  private val wasNewSurvey = HashSet<String>()

  override fun putSurvey(survey: SurveyDef) {
    val id = survey.survey_id
    uuid(id)
    if (id !in pendingSurveys && survey(id) == null) wasNewSurvey += id
    pendingSurveys[id] = survey
  }

  override fun putSurveyRevision(survey: SurveyDef) = tx.buffer(revisionRow(survey, null))

  /**
   * Deletes the survey's child rows and replaces its row with [tombstone], so delta syncs still
   * report the purge. Pending writes of the survey or its ACL in this block are dropped.
   */
  override fun purgeSurvey(tombstone: SurveyDef) {
    val id = uuid(tombstone.survey_id)
    val prefix = KeySet.prefixRange(Key.of(id))
    for (child in
      listOf(
        "SurveyWorkingCopies",
        "SurveyRevisions",
        "SurveyAclEntries",
        "PlotLocks",
        "Entities",
        "Submissions",
        "AuditRecords",
      )) {
      tx.buffer(Mutation.delete(child, prefix))
    }
    pendingSurveys.remove(tombstone.survey_id)
    pendingAcls.remove(tombstone.survey_id)
    tx.buffer(purgedSurveyRow(tombstone))
  }

  override fun putWorkingCopy(surveyId: String, workingCopy: SurveyDef?) {
    if (workingCopy == null) {
      tx.buffer(Mutation.delete("SurveyWorkingCopies", Key.of(uuid(surveyId))))
    } else {
      tx.buffer(workingCopyRow(surveyId, workingCopy, null))
    }
  }

  override fun putAcl(acl: SurveyAcl) {
    uuid(acl.survey_id)
    pendingAcls[acl.survey_id] = acl
  }

  /**
   * Buffers the survey and ACL writes held back during the block: one row write per survey (with
   * its ACL when both changed), an ACL-only update for surveys whose definition didn't change, and
   * a full rewrite of each changed ACL's entries. The store calls it once, after the block.
   */
  fun flush() {
    for ((id, survey) in pendingSurveys) {
      tx.buffer(surveyRow(survey, null, pendingAcls[id]))
      if (id in wasNewSurvey && survey.saved_revision_number > 0L) {
        tx.buffer(revisionRow(survey, null))
      }
    }
    for ((id, acl) in pendingAcls) {
      if (id !in pendingSurveys) tx.buffer(aclUpdate(acl))
      tx.buffer(Mutation.delete("SurveyAclEntries", KeySet.prefixRange(Key.of(uuid(id)))))
      acl.entries.forEach { tx.buffer(aclEntryRow(id, it, null)) }
    }
    pendingSurveys.clear()
    pendingAcls.clear()
  }

  override fun putEntity(entity: EntityRecord) = tx.buffer(entityRow(entity, null))

  override fun putSubmission(submission: SubmissionRecord) =
    tx.buffer(submissionRow(submission, null))

  override fun putPlotLock(surveyId: String, entityId: String, lock: PlotLock?) {
    if (lock == null) {
      tx.buffer(Mutation.delete("PlotLocks", Key.of(uuid(surveyId), uuid(entityId))))
    } else {
      tx.buffer(plotLockRow(surveyId, entityId, lock))
    }
  }

  override fun putImpactEvent(event: ImpactEvent) = tx.buffer(impactEventRow(event))

  override fun putUser(user: UserAccount) = tx.buffer(userRow(user))

  // The watermark only moves forward, so a late replay of an older mutation can't lower it.
  override fun recordApplied(scope: String, clientId: String, clientSeq: Long) {
    if (clientId.isBlank() || clientSeq <= 0L) return
    val previous = appliedWatermarks(clientId).firstOrNull { it.scope == scope }?.client_seq
    if (previous != null && previous >= clientSeq) return
    tx.buffer(watermarkRow(scope, clientId, clientSeq))
  }
}
