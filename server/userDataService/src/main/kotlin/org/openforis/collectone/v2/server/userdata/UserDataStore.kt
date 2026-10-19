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
package org.openforis.collectone.v2.server.userdata

import com.squareup.wire.Instant
import groundplatform.v2.data.AppliedMutationWatermark
import groundplatform.v2.data.AuditRecord
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ImpactEvent
import groundplatform.v2.data.MutationLogRecord
import groundplatform.v2.data.PlaceRecord
import groundplatform.v2.data.PlotLock
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.CachedProfile
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef

/**
 * Storage behind the user data RPCs ([UserDataBackend]). The backend owns the business rules
 * (validation, patches, versions, sync paging); a store only reads and writes records atomically.
 *
 * The contract is shaped for Cloud Spanner (`docs/technical/backend/spanner-schema.md`):
 * - [write] runs one read-write transaction. Reads inside it see the data as of the transaction
 *   start, not the transaction's own writes, so the backend keeps its own working copies.
 * - The store assigns one commit timestamp per [write] and stamps it on every record written
 *   (`commit_timestamp`); records read back always carry the timestamp of their last write.
 *   [Committed.commitTimestamp] returns it, so responses can carry it too.
 * - [read] sees one consistent snapshot.
 */
interface UserDataStore {
  /**
   * Random identifier of the current data. Sync tokens carry it, so tokens issued before the data
   * was reset are rejected and clients run a full sync.
   */
  val dataEpoch: String

  /** The latest commit timestamp; every later write commits strictly after it. */
  fun watermark(): Instant

  /** Runs [block] on a consistent snapshot. */
  fun <T> read(block: (UserDataReader) -> T): T

  /**
   * Runs [block] in one atomic transaction and commits its writes, or none of them if it throws.
   * Implementations may run [block] more than once (for example when Spanner aborts a transaction),
   * so it must not have side effects outside the transaction.
   */
  fun <T> write(block: (UserDataWriter) -> T): Committed<T>

  /** Replaces all data with the initial data and starts a new [dataEpoch]. */
  fun reset()
}

/**
 * A signed-in user. [accessEpoch] increases whenever the user's ACL entries or organization
 * memberships change; survey overview and organization list tokens carry it.
 */
data class UserAccount(
  val userId: String,
  /** Normalized (trimmed, lowercased) email, or empty. */
  val email: String = "",
  val emailVerified: Boolean = false,
  val profile: CachedProfile? = null,
  val accessEpoch: Long = 0L,
  val createTime: Instant? = null,
)

/** The value a [UserDataStore.write] block returned, and the transaction's commit timestamp. */
data class Committed<T>(val value: T, val commitTimestamp: Instant)

/** Reads records. Lists are in insertion order unless noted. */
interface UserDataReader {
  /**
   * When this snapshot was taken: every write committed at or before it is visible, and every later
   * write commits after it. Query-mode sync tokens start here, so no change is skipped.
   */
  val snapshotTimestamp: Instant

  fun organization(organizationId: String): Organization?

  fun organizations(): List<Organization>

  fun library(organizationId: String): OrganizationLibraryRecord?

  fun libraries(): List<OrganizationLibraryRecord>

  /*
   * Search candidates. Each returns at least every record whose searched text contains [query]
   * (case-insensitive) and may return more, so callers apply the exact match. A blank query
   * matches everything.
   */

  /** Listed, live organizations that may match [query] on name or description. */
  fun searchOrganizations(query: String): List<Organization>

  /** Surveys whose sharing policy is `PUBLIC` that may match [query] on title or description. */
  fun searchPublicSurveys(query: String): List<SurveyDef>

  /**
   * Concepts of the libraries of [organizationIds] that may match [query] on ID or label, resolved
   * by concept ID: a later organization's concept replaces an earlier one's, keeping its position.
   */
  fun searchConcepts(organizationIds: List<String>, query: String): List<ConceptDef>

  /** A survey's latest saved revision. */
  fun survey(surveyId: String): SurveyDef?

  /** The saved revision of a survey whose `version` is [version], including earlier revisions. */
  fun surveyRevision(surveyId: String, version: String): SurveyDef?

  /** Every survey's saved revision. */
  fun surveys(): List<SurveyDef>

  fun workingCopy(surveyId: String): SurveyDef?

  fun acl(surveyId: String): SurveyAcl?

  fun entity(surveyId: String, entityId: String): EntityRecord?

  /**
   * A survey's entities, only those in dataset [datasetId] when it isn't blank, and only live ones
   * unless [includeDeleted].
   */
  fun entities(
    surveyId: String,
    datasetId: String = "",
    includeDeleted: Boolean = true,
  ): List<EntityRecord>

  /**
   * Up to [limit] of a survey's entities (including tombstones) committed after [after], in commit
   * order: by commit timestamp, then entity ID.
   */
  fun entitiesChangedAfter(surveyId: String, after: SyncPosition, limit: Int): List<EntityRecord>

  fun submission(surveyId: String, submissionId: String): SubmissionRecord?

  /**
   * A survey's submissions, only those for form [formId] when it isn't blank, and only live ones
   * unless [includeDeleted].
   */
  fun submissions(
    surveyId: String,
    formId: String = "",
    includeDeleted: Boolean = true,
  ): List<SubmissionRecord>

  /** Like [entitiesChangedAfter], for submissions. */
  fun submissionsChangedAfter(
    surveyId: String,
    after: SyncPosition,
    limit: Int,
  ): List<SubmissionRecord>

  fun plotLock(surveyId: String, entityId: String): PlotLock?

  fun impactEvents(): List<ImpactEvent>

  fun auditRecords(): List<AuditRecord>

  fun places(): List<PlaceRecord>

  fun mutationLogs(): List<MutationLogRecord>

  fun user(userId: String): UserAccount?

  /** Users whose normalized email is [email]. */
  fun usersByEmail(email: String): List<UserAccount>

  /** Surveys with an ACL entry for normalized email [email]. */
  fun surveyIdsWithAclEmail(email: String): List<String>

  /** Organizations with a member (or invite) for normalized email [email]. */
  fun organizationIdsWithMemberEmail(email: String): List<String>

  /**
   * Saved revisions of the surveys owned by user [userId] or normalized email [email]
   * (`SurveyDef.owner_id`, or the ACL's `owner_email`). Blank arguments match nothing.
   */
  fun surveysOwnedBy(userId: String, email: String): List<SurveyDef>

  /** Surveys with an ACL entry bound to user [userId]. */
  fun surveyIdsWithAclUser(userId: String): List<String>

  /** Organizations with a member bound to user [userId]. */
  fun organizationIdsWithMemberUser(userId: String): List<String>

  /** Saved revisions of the surveys that belong to organization [organizationId]. */
  fun surveysInOrganization(organizationId: String): List<SurveyDef>

  /** Highest applied `client_seq` per scope for [clientId]. */
  fun appliedWatermarks(clientId: String): List<AppliedMutationWatermark>
}

/**
 * Reads (as of the transaction start) and buffers writes. Written records get the transaction's
 * commit timestamp; a `put` replaces the whole record.
 */
interface UserDataWriter : UserDataReader {
  /**
   * The time of this write, for time-based rules such as plot lock leases. It isn't the commit
   * timestamp, which only exists once the transaction commits ([Committed.commitTimestamp]).
   */
  val now: Instant

  fun putOrganization(organization: Organization)

  fun putLibrary(library: OrganizationLibraryRecord)

  fun putSurvey(survey: SurveyDef)

  /** Appends [survey] as an immutable saved revision (`saved_revision_number` / `version`). */
  fun putSurveyRevision(survey: SurveyDef)

  /**
   * Purges a trashed survey: removes its working copy, revisions, ACL, entities, plot locks,
   * submissions, and audit records, and replaces its saved definition with [tombstone].
   */
  fun purgeSurvey(tombstone: SurveyDef)

  /** Stores [survey]'s working copy, or removes it when [workingCopy] is null. */
  fun putWorkingCopy(surveyId: String, workingCopy: SurveyDef?)

  fun putAcl(acl: SurveyAcl)

  fun putEntity(entity: EntityRecord)

  fun putSubmission(submission: SubmissionRecord)

  /** Stores the plot lock of an entity, or removes it when [lock] is null. */
  fun putPlotLock(surveyId: String, entityId: String, lock: PlotLock?)

  fun putImpactEvent(event: ImpactEvent)

  fun putUser(user: UserAccount)

  /** Raises [clientId]'s `client_seq` watermark for [scope] to [clientSeq] (it never goes down). */
  fun recordApplied(scope: String, clientId: String, clientSeq: Long)
}
