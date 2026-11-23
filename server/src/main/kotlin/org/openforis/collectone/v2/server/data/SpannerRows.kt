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

import com.google.cloud.ByteArray
import com.google.cloud.Timestamp
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.ResultSet
import com.google.cloud.spanner.Value
import com.squareup.wire.Instant
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import java.util.UUID
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.PlotLock
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef

// The Spanner rows of each record type, and the encoding they share with the reads in
// SpannerUserDataReader. SpannerUserDataStore.reset passes the seed's commit timestamps;
// SpannerUserDataWriter passes null, for Spanner's commit timestamp. Records are stored without
// their commit_timestamp (reads take it from CommitTs), organizations without their members and
// ACLs without their entries (each has its own table).

internal fun organizationRow(org: Organization, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("Organizations")
    .set("OrganizationId")
    .to(uuid(org.organization_id))
    .set("Record")
    .to(
      proto(
        Organization.ADAPTER,
        org.copy(members = emptyList(), commit_timestamp = null),
        ORGANIZATION,
      )
    )
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .build()

internal fun memberRow(
  organizationId: String,
  member: OrganizationMember,
  commitTs: Instant?,
): Mutation =
  Mutation.newInsertOrUpdateBuilder("OrganizationMembers")
    .set("OrganizationId")
    .to(uuid(organizationId))
    .set("EmailNormalized")
    .to(emailKey(member.email, member.user_id))
    .set("Record")
    .to(proto(OrganizationMember.ADAPTER, member, ORGANIZATION_MEMBER))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .build()

internal fun libraryRow(library: OrganizationLibraryRecord, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("OrganizationLibraries")
    .set("OrganizationId")
    .to(uuid(library.organization_id))
    .set("Record")
    .to(proto(OrganizationLibraryRecord.ADAPTER, library.copy(commit_timestamp = null), LIBRARY))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .build()

/** One `LibraryConcepts` row per concept in [library]'s bundle, with its search text. */
internal fun conceptRows(library: OrganizationLibraryRecord): List<Mutation> =
  library.bundle?.concepts.orEmpty().mapIndexed { position, concept ->
    Mutation.newInsertOrUpdateBuilder("LibraryConcepts")
      .set("OrganizationId")
      .to(uuid(library.organization_id))
      .set("ConceptId")
      .to(concept.id)
      .set("Concept")
      .to(proto(ConceptDef.ADAPTER, concept, CONCEPT))
      .set("Position")
      .to(position.toLong())
      .set("SearchText")
      .to((listOf(concept.id) + concept.label?.values?.values.orEmpty()).joinToString(" "))
      .build()
  }

/** Writes [survey], and [acl] if set (seed loads pass the seed's ACL commit timestamp). */
internal fun surveyRow(survey: SurveyDef, commitTs: Instant?, acl: SurveyAcl? = null): Mutation {
  val builder =
    Mutation.newInsertOrUpdateBuilder("Surveys")
      .set("SurveyId")
      .to(uuid(survey.survey_id))
      .set("SavedDef")
      .to(proto(SurveyDef.ADAPTER, survey.copy(commit_timestamp = null), SURVEY_DEF))
      .set("CommitTs")
      .to(commitTimestampValue(commitTs))
  if (acl != null) {
    builder
      .set("AclRules")
      .to(
        proto(
          SurveyAcl.ADAPTER,
          acl.copy(entries = emptyList(), commit_timestamp = null),
          SURVEY_ACL,
        )
      )
      .set("AclCommitTs")
      .to(commitTimestampValue(if (commitTs == null) null else acl.commit_timestamp))
  }
  return builder.build()
}

/** Updates an existing survey's ACL. */
internal fun aclUpdate(acl: SurveyAcl): Mutation =
  Mutation.newUpdateBuilder("Surveys")
    .set("SurveyId")
    .to(uuid(acl.survey_id))
    .set("AclRules")
    .to(
      proto(
        SurveyAcl.ADAPTER,
        acl.copy(entries = emptyList(), commit_timestamp = null),
        SURVEY_ACL,
      )
    )
    .set("AclCommitTs")
    .to(Value.COMMIT_TIMESTAMP)
    .build()

internal fun aclEntryRow(surveyId: String, entry: AclEntry, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("SurveyAclEntries")
    .set("SurveyId")
    .to(uuid(surveyId))
    .set("EmailNormalized")
    .to(emailKey(entry.email, entry.user_id))
    .set("Record")
    .to(proto(AclEntry.ADAPTER, entry, ACL_ENTRY))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .build()

internal fun revisionRow(survey: SurveyDef, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("SurveyRevisions")
    .set("SurveyId")
    .to(uuid(survey.survey_id))
    .set("RevisionNumber")
    .to(survey.saved_revision_number.coerceAtLeast(1L))
    .set("Def")
    .to(proto(SurveyDef.ADAPTER, survey.copy(commit_timestamp = null), SURVEY_DEF))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .build()

internal fun workingCopyRow(surveyId: String, def: SurveyDef, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("SurveyWorkingCopies")
    .set("SurveyId")
    .to(uuid(surveyId))
    .set("Def")
    .to(proto(SurveyDef.ADAPTER, def.copy(commit_timestamp = null), SURVEY_DEF))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .build()

internal fun entityRow(entity: EntityRecord, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("Entities")
    .set("SurveyId")
    .to(uuid(entity.survey_id))
    .set("EntityId")
    .to(uuid(entity.entity_id))
    .set("Record")
    .to(proto(EntityRecord.ADAPTER, entity.copy(commit_timestamp = null), ENTITY))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .set("DeletedAt")
    .to(if (entity.is_deleted) commitTimestampValue(commitTs) else Value.timestamp(null))
    .build()

internal fun submissionRow(submission: SubmissionRecord, commitTs: Instant?): Mutation =
  Mutation.newInsertOrUpdateBuilder("Submissions")
    .set("SurveyId")
    .to(uuid(submission.survey_id))
    .set("SubmissionId")
    .to(uuid(submission.submission_id))
    .set("Record")
    .to(proto(SubmissionRecord.ADAPTER, submission.copy(commit_timestamp = null), SUBMISSION))
    .set("CommitTs")
    .to(commitTimestampValue(commitTs))
    .set("DeletedAt")
    .to(if (submission.is_deleted) commitTimestampValue(commitTs) else Value.timestamp(null))
    .build()

/** The tombstone row of a purged survey: no ACL, and `PurgedAt` set to the commit time. */
internal fun purgedSurveyRow(tombstone: SurveyDef): Mutation =
  Mutation.newInsertOrUpdateBuilder("Surveys")
    .set("SurveyId")
    .to(uuid(tombstone.survey_id))
    .set("SavedDef")
    .to(proto(SurveyDef.ADAPTER, tombstone.copy(commit_timestamp = null), SURVEY_DEF))
    .set("AclRules")
    .to(Value.protoMessage(null, SURVEY_ACL))
    .set("AclCommitTs")
    .to(Value.timestamp(null))
    .set("PurgedAt")
    .to(Value.COMMIT_TIMESTAMP)
    .set("CommitTs")
    .to(Value.COMMIT_TIMESTAMP)
    .build()

internal fun plotLockRow(surveyId: String, entityId: String, lock: PlotLock): Mutation =
  Mutation.newInsertOrUpdateBuilder("PlotLocks")
    .set("SurveyId")
    .to(uuid(surveyId))
    .set("EntityId")
    .to(uuid(entityId))
    .set("Lock")
    .to(proto(PlotLock.ADAPTER, lock, PLOT_LOCK))
    .build()

internal fun impactEventRow(event: ImpactEvent): Mutation =
  Mutation.newInsertOrUpdateBuilder("ImpactEvents")
    .set("EventId")
    .to(uuid(event.event_id))
    .set("Record")
    .to(proto(ImpactEvent.ADAPTER, event.copy(commit_timestamp = null), IMPACT_EVENT))
    .set("CommitTs")
    .to(Value.COMMIT_TIMESTAMP)
    .build()

/**
 * A user's row. Its key is the auth provider's user ID (not a UUID); an account without an email
 * stores a null `EmailNormalized`, and one without a [UserAccount.createTime] is stamped now.
 */
internal fun userRow(user: UserAccount): Mutation =
  Mutation.newInsertOrUpdateBuilder("Users")
    .set("UserId")
    .to(user.userId)
    .set("EmailNormalized")
    .to(user.email.ifEmpty { null })
    .set("EmailVerified")
    .to(user.emailVerified)
    .set("Profile")
    .to(
      user.profile?.let { proto(CachedProfile.ADAPTER, it, CACHED_PROFILE) }
        ?: Value.protoMessage(null, CACHED_PROFILE)
    )
    .set("AccessEpoch")
    .to(user.accessEpoch)
    .set("CreateTime")
    .to(
      user.createTime?.let { Timestamp.ofTimeSecondsAndNanos(it.epochSecond, it.nano) }
        ?: Timestamp.now()
    )
    .set("CommitTs")
    .to(Value.COMMIT_TIMESTAMP)
    .build()

/** The highest `client_seq` of [clientId]'s mutations applied in [scope]. */
internal fun watermarkRow(scope: String, clientId: String, clientSeq: Long): Mutation =
  Mutation.newInsertOrUpdateBuilder("ClientMutationWatermarks")
    .set("ClientId")
    .to(clientId)
    .set("Scope")
    .to(scope)
    .set("MaxClientSeq")
    .to(clientSeq)
    .set("CommitTs")
    .to(Value.COMMIT_TIMESTAMP)
    .build()

/**
 * The key of a member or ACL entry row: the normalized email, or the user ID for an entry without
 * one, so both kinds of entry stay unique per organization or survey.
 */
internal fun emailKey(email: String, userId: String): String =
  email.trim().lowercase().ifEmpty { userId.trim() }

internal fun uuidOrNull(id: String): UUID? = runCatching { UUID.fromString(id) }.getOrNull()

/** [id] as the UUID of a key column; IDs that aren't UUIDs fail with `INVALID_ARGUMENT`. */
internal fun uuid(id: String): UUID =
  requireNotNull(uuidOrNull(id)) { "Record IDs must be UUIDs, got \"$id\"" }

/** [message] as a `PROTO` column value of the fully qualified proto [type]. */
internal fun <M : Message<M, *>> proto(adapter: ProtoAdapter<M>, message: M, type: String): Value =
  Value.protoMessage(ByteArray.copyFrom(adapter.encode(message)), type)

/** The current row's `Record` column (or a column aliased `AS Record`), decoded. */
internal fun <M> decode(adapter: ProtoAdapter<M>, rs: ResultSet): M =
  adapter.decode(rs.getBytes("Record").toByteArray())

/** Spanner's commit timestamp, or [explicit] (an earlier time, e.g. from the seed). */
internal fun commitTimestampValue(explicit: Instant?): Value =
  if (explicit == null) Value.timestamp(Value.COMMIT_TIMESTAMP)
  else Value.timestamp(Timestamp.ofTimeSecondsAndNanos(explicit.epochSecond, explicit.nano))

internal fun ResultSet.commitTs(): Instant = getTimestamp("CommitTs").toInstant()

internal fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())

// The fully qualified types of the PROTO columns (schema.sql's proto bundle).
private const val ORGANIZATION = "openforis.collectone.v1.survey.Organization"
private const val ORGANIZATION_MEMBER = "openforis.collectone.v1.survey.OrganizationMember"
private const val LIBRARY = "openforis.collectone.v1.library.OrganizationLibraryRecord"
private const val CONCEPT = "openforis.collectone.v1.library.ConceptDef"
private const val SURVEY_DEF = "openforis.collectone.v1.survey.SurveyDef"
private const val SURVEY_ACL = "openforis.collectone.v1.survey.SurveyAcl"
private const val ACL_ENTRY = "openforis.collectone.v1.survey.AclEntry"
private const val CACHED_PROFILE = "openforis.collectone.v1.survey.CachedProfile"
private const val ENTITY = "openforis.collectone.v1.data.EntityRecord"
private const val SUBMISSION = "openforis.collectone.v1.data.SubmissionRecord"
private const val PLOT_LOCK = "openforis.collectone.v1.data.PlotLock"
private const val IMPACT_EVENT = "openforis.collectone.v1.data.ImpactEvent"
