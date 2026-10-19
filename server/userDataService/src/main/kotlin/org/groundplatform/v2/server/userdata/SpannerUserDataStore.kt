/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.server.userdata

import com.google.cloud.ByteArray
import com.google.cloud.Timestamp
import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.ErrorCode
import com.google.cloud.spanner.Key
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.ReadContext
import com.google.cloud.spanner.ResultSet
import com.google.cloud.spanner.SpannerException
import com.google.cloud.spanner.Statement
import com.google.cloud.spanner.TransactionContext
import com.google.cloud.spanner.Value
import com.squareup.wire.Instant
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
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
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.CachedProfile
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMember
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.userdata.UserDataSeed
import io.grpc.StatusRuntimeException
import java.util.UUID

/**
 * [UserDataStore] backed by Cloud Spanner (the emulator in development), with the schema in
 * `src/main/resources/spanner/schema.sql` (apply it with [SpannerSchema.apply]).
 *
 * Records are stored as `PROTO` columns encoded with Wire; their `commit_timestamp` comes from the
 * row's `CommitTs` column, written with Spanner's commit timestamp. [write] runs a read-write
 * transaction (Spanner may run the block more than once if it aborts); [read] runs a strong
 * read-only transaction, whose read timestamp is the snapshot timestamp.
 *
 * Record keys are UUIDs: reads of a non-UUID ID find nothing, and writes of one fail with
 * `IllegalArgumentException` (`INVALID_ARGUMENT`).
 *
 * [seed] provides the data [reset] loads and the static places and mutation log examples, which
 * aren't stored in Spanner.
 */
class SpannerUserDataStore(
  private val client: DatabaseClient,
  private val seed: UserDataSeed = UserDataSeed(),
) : UserDataStore {

  override val dataEpoch: String
    get() =
      client.singleUse().executeQuery(Statement.of("SELECT DataEpoch FROM ServiceMetadata")).use {
        if (it.next()) it.getString(0) else NO_EPOCH
      }

  override fun watermark(): Instant =
    client.singleUseReadOnlyTransaction().use { tx ->
      tx.executeQuery(Statement.of("SELECT 1")).use { it.next() }
      tx.readTimestamp.toInstant()
    }

  override fun <T> read(block: (UserDataReader) -> T): T =
    client.readOnlyTransaction().use { tx ->
      tx.executeQuery(Statement.of("SELECT 1")).use { it.next() }
      block(Reader(tx, tx.readTimestamp.toInstant()))
    }

  override fun <T> write(block: (UserDataWriter) -> T): Committed<T> {
    val runner = client.readWriteTransaction()
    val value =
      try {
        runner.run { tx ->
          val writer = Writer(tx, Timestamp.now().toInstant())
          block(writer).also { writer.flush() }
        }
      } catch (e: SpannerException) {
        // The client wraps exceptions thrown by the block; rethrow them unwrapped.
        val cause = e.cause
        throw if (
          e.errorCode == ErrorCode.UNKNOWN && cause is Exception && cause !is StatusRuntimeException
        )
          cause
        else e
      }
    @Suppress("UNCHECKED_CAST")
    return Committed(value as T, runner.commitTimestamp.toInstant())
  }

  /**
   * Deletes all data and loads [seed], keeping the seed's commit timestamps (Spanner accepts past
   * timestamps in commit timestamp columns) so delta sync order matches the in-memory store.
   */
  override fun reset() {
    client.write(
      SpannerSchema.tables.reversed().map { Mutation.delete(it, KeySet.all()) } +
        Mutation.newInsertOrUpdateBuilder("ServiceMetadata")
          .set("Singleton")
          .to(true)
          .set("DataEpoch")
          .to(UUID.randomUUID().toString())
          .build()
    )
    val mutations = mutableListOf<Mutation>()
    for (org in seed.organizations) {
      mutations += organizationRow(org, org.commit_timestamp)
      org.members.forEach { mutations += memberRow(org.organization_id, it, org.commit_timestamp) }
    }
    for (library in seed.libraries) {
      mutations += libraryRow(library, library.commit_timestamp)
      mutations += conceptRows(library)
    }
    for (surveySeed in seed.surveys) {
      val survey = surveySeed.survey ?: continue
      mutations += surveyRow(survey, survey.commit_timestamp, surveySeed.acl)
      if (survey.saved_revision_number > 0L) {
        mutations += revisionRow(survey, survey.commit_timestamp)
      }
      surveySeed.acl?.let { acl ->
        acl.entries.forEach { mutations += aclEntryRow(survey.survey_id, it, acl.commit_timestamp) }
      }
      surveySeed.working_copy?.let {
        mutations += workingCopyRow(survey.survey_id, it, it.commit_timestamp)
      }
      surveySeed.entities.forEach { mutations += entityRow(it, it.commit_timestamp) }
      surveySeed.submissions.forEach { mutations += submissionRow(it, it.commit_timestamp) }
    }
    mutations.chunked(SEED_BATCH_SIZE).forEach { client.write(it) }
  }

  private open inner class Reader(
    private val ctx: ReadContext,
    override val snapshotTimestamp: Instant,
  ) : UserDataReader {
    override fun organization(organizationId: String) =
      one("SELECT Record, CommitTs FROM Organizations WHERE OrganizationId = @id", organizationId) {
        decode(Organization.ADAPTER, it)
          .copy(members = members(organizationId), commit_timestamp = it.commitTs())
      }

    override fun organizations(): List<Organization> =
      withMembers(Statement.of("SELECT Record, CommitTs FROM Organizations"))

    override fun searchOrganizations(query: String): List<Organization> {
      val sql = buildString {
        append("SELECT Record, CommitTs FROM Organizations ")
        append("WHERE IFNULL(Listed, FALSE) AND NOT IFNULL(IsDeleted, FALSE)")
        if (query.isNotBlank()) append(" AND SEARCH_SUBSTRING(SearchTokens, @q)")
      }
      val statement = Statement.newBuilder(sql)
      if (query.isNotBlank()) statement.bind("q").to(query.trim())
      return withMembers(statement.build())
    }

    /** Organizations read by [statement] (`Record`, `CommitTs`), with their members attached. */
    private fun withMembers(statement: Statement): List<Organization> {
      val orgs =
        query(statement) {
          decode(Organization.ADAPTER, it).copy(commit_timestamp = it.commitTs())
        }
      if (orgs.isEmpty()) return orgs
      val membersByOrg =
        query(
            Statement.newBuilder(
                "SELECT OrganizationId, Record FROM OrganizationMembers " +
                  "WHERE CAST(OrganizationId AS STRING) IN UNNEST(@ids)"
              )
              .bind("ids")
              .toStringArray(orgs.map { it.organization_id })
              .build()
          ) {
            it.getUuid("OrganizationId").toString() to decode(OrganizationMember.ADAPTER, it)
          }
          .groupBy({ it.first }, { it.second })
      return orgs.map { it.copy(members = membersByOrg[it.organization_id].orEmpty()) }
    }

    override fun searchPublicSurveys(query: String): List<SurveyDef> {
      val sql = buildString {
        append("SELECT SavedDef AS Record, CommitTs FROM Surveys ")
        append("WHERE CAST(SharingPolicy AS INT64) = @policy")
        if (query.isNotBlank()) append(" AND SEARCH_SUBSTRING(SearchTokens, @q)")
      }
      val statement =
        Statement.newBuilder(sql).bind("policy").to(SharingPolicy.PUBLIC.value.toLong())
      if (query.isNotBlank()) statement.bind("q").to(query.trim())
      return query(statement.build()) {
        decode(SurveyDef.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }
    }

    override fun searchConcepts(organizationIds: List<String>, query: String): List<ConceptDef> {
      val orgs = organizationIds.distinct().filter { uuidOrNull(it) != null }
      if (orgs.isEmpty()) return emptyList()
      val sql = buildString {
        append("SELECT OrganizationId, Position, Concept AS Record FROM LibraryConcepts ")
        append("WHERE CAST(OrganizationId AS STRING) IN UNNEST(@orgs)")
        if (query.isNotBlank()) {
          // Every library's version of a matching concept, so an override that doesn't match
          // still replaces the concept it overrides.
          append(" AND ConceptId IN (SELECT ConceptId FROM LibraryConcepts ")
          append("WHERE CAST(OrganizationId AS STRING) IN UNNEST(@orgs) ")
          append("AND SEARCH_SUBSTRING(SearchTokens, @q))")
        }
      }
      val statement = Statement.newBuilder(sql).bind("orgs").toStringArray(orgs)
      if (query.isNotBlank()) statement.bind("q").to(query.trim())
      val rows =
        query(statement.build()) {
          Triple(
            it.getUuid("OrganizationId").toString(),
            it.getLong("Position"),
            decode(ConceptDef.ADAPTER, it),
          )
        }
      val resolved = LinkedHashMap<String, ConceptDef>()
      for (org in orgs) {
        rows
          .filter { it.first == org }
          .sortedBy { it.second }
          .forEach { resolved[it.third.id] = it.third }
      }
      return resolved.values.toList()
    }

    private fun members(organizationId: String): List<OrganizationMember> =
      all("SELECT Record FROM OrganizationMembers WHERE OrganizationId = @id", organizationId) {
        decode(OrganizationMember.ADAPTER, it)
      }

    override fun library(organizationId: String) =
      one(
        "SELECT Record, CommitTs FROM OrganizationLibraries WHERE OrganizationId = @id",
        organizationId,
      ) {
        decode(OrganizationLibraryRecord.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun libraries() =
      all("SELECT Record, CommitTs FROM OrganizationLibraries") {
        decode(OrganizationLibraryRecord.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun survey(surveyId: String) =
      one("SELECT SavedDef AS Record, CommitTs FROM Surveys WHERE SurveyId = @id", surveyId) {
        decode(SurveyDef.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun surveyRevision(surveyId: String, version: String): SurveyDef? {
      val fromHistory =
        bindIds(
            "SELECT Def AS Record, CommitTs FROM SurveyRevisions " +
              "WHERE SurveyId = @id AND Version = @version LIMIT 1",
            arrayOf(surveyId),
          ) {
            bind("version").to(version)
          }
          ?.let {
            query(it) { rs -> decode(SurveyDef.ADAPTER, rs).copy(commit_timestamp = rs.commitTs()) }
          }
          ?.firstOrNull()
      return fromHistory ?: survey(surveyId)?.takeIf { it.version == version }
    }

    override fun surveys() =
      all("SELECT SavedDef AS Record, CommitTs FROM Surveys") {
        decode(SurveyDef.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun workingCopy(surveyId: String) =
      one(
        "SELECT Def AS Record, CommitTs FROM SurveyWorkingCopies WHERE SurveyId = @id",
        surveyId,
      ) {
        decode(SurveyDef.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun acl(surveyId: String) =
      one(
        "SELECT AclRules AS Record, AclCommitTs AS CommitTs FROM Surveys " +
          "WHERE SurveyId = @id AND AclRules IS NOT NULL",
        surveyId,
      ) {
        decode(SurveyAcl.ADAPTER, it)
          .copy(entries = aclEntries(surveyId), commit_timestamp = it.commitTs())
      }

    private fun aclEntries(surveyId: String): List<AclEntry> =
      all("SELECT Record FROM SurveyAclEntries WHERE SurveyId = @id", surveyId) {
        decode(AclEntry.ADAPTER, it)
      }

    override fun entity(surveyId: String, entityId: String) =
      one(
        "SELECT Record, CommitTs FROM Entities WHERE SurveyId = @id AND EntityId = @id2",
        surveyId,
        entityId,
      ) {
        decode(EntityRecord.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun entities(surveyId: String, datasetId: String, includeDeleted: Boolean) =
      narrowed("Entities", "DatasetId", surveyId, datasetId, includeDeleted, ::entityOf)

    override fun entitiesChangedAfter(surveyId: String, after: SyncPosition, limit: Int) =
      changedAfter("Entities", "EntityId", "EntitiesDelta", surveyId, after, limit, ::entityOf)

    override fun submission(surveyId: String, submissionId: String) =
      one(
        "SELECT Record, CommitTs FROM Submissions WHERE SurveyId = @id AND SubmissionId = @id2",
        surveyId,
        submissionId,
      ) {
        decode(SubmissionRecord.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun submissions(surveyId: String, formId: String, includeDeleted: Boolean) =
      narrowed("Submissions", "FormId", surveyId, formId, includeDeleted, ::submissionOf)

    override fun submissionsChangedAfter(surveyId: String, after: SyncPosition, limit: Int) =
      changedAfter(
        "Submissions",
        "SubmissionId",
        "SubmissionsDelta",
        surveyId,
        after,
        limit,
        ::submissionOf,
      )

    override fun plotLock(surveyId: String, entityId: String) =
      one(
        "SELECT Lock AS Record FROM PlotLocks WHERE SurveyId = @id AND EntityId = @id2",
        surveyId,
        entityId,
      ) {
        decode(PlotLock.ADAPTER, it)
      }

    override fun impactEvents() =
      all("SELECT Record, CommitTs FROM ImpactEvents") {
        decode(ImpactEvent.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun auditRecords() =
      all("SELECT Record, CommitTs FROM AuditRecords") {
        decode(AuditRecord.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    override fun places(): List<PlaceRecord> = seed.places

    override fun mutationLogs(): List<MutationLogRecord> = seed.mutation_logs

    override fun user(userId: String): UserAccount? =
      users(Statement.newBuilder("$USER_COLUMNS WHERE UserId = @id").bind("id").to(userId).build())
        .firstOrNull()

    override fun usersByEmail(email: String): List<UserAccount> =
      users(
        Statement.newBuilder("$USER_COLUMNS WHERE EmailNormalized = @email")
          .bind("email")
          .to(email)
          .build()
      )

    private fun users(statement: Statement): List<UserAccount> =
      query(statement) { rs ->
        UserAccount(
          userId = rs.getString("UserId"),
          email = if (rs.isNull("EmailNormalized")) "" else rs.getString("EmailNormalized"),
          emailVerified = rs.getBoolean("EmailVerified"),
          profile =
            if (rs.isNull("Profile")) null
            else CachedProfile.ADAPTER.decode(rs.getBytes("Profile").toByteArray()),
          accessEpoch = rs.getLong("AccessEpoch"),
          createTime = rs.getTimestamp("CreateTime").toInstant(),
        )
      }

    override fun surveyIdsWithAclEmail(email: String): List<String> =
      query(
        Statement.newBuilder(
            "SELECT DISTINCT SurveyId FROM SurveyAclEntries WHERE EmailNormalized = @email"
          )
          .bind("email")
          .to(email)
          .build()
      ) {
        it.getUuid("SurveyId").toString()
      }

    override fun organizationIdsWithMemberEmail(email: String): List<String> =
      query(
        Statement.newBuilder(
            "SELECT DISTINCT OrganizationId FROM OrganizationMembers WHERE EmailNormalized = @email"
          )
          .bind("email")
          .to(email)
          .build()
      ) {
        it.getUuid("OrganizationId").toString()
      }

    override fun surveysOwnedBy(userId: String, email: String): List<SurveyDef> {
      val owners = listOf(userId, email).filter { it.isNotBlank() }.distinct()
      if (owners.isEmpty()) return emptyList()
      // Two equality lookups, one through each index (`SurveysByOwner`, `SurveysByOwnerEmail`).
      val byOwnerId =
        surveysWhere(
          Statement.newBuilder("$SURVEY_COLUMNS WHERE OwnerId IN UNNEST(@owners)")
            .bind("owners")
            .toStringArray(owners)
            .build()
        )
      val byOwnerEmail =
        if (email.isBlank()) emptyList()
        else
          surveysWhere(
            Statement.newBuilder("$SURVEY_COLUMNS WHERE OwnerEmail = @email")
              .bind("email")
              .to(email)
              .build()
          )
      return (byOwnerId + byOwnerEmail).distinctBy { it.survey_id }
    }

    override fun surveyIdsWithAclUser(userId: String): List<String> =
      if (userId.isBlank()) emptyList()
      else
        query(
          Statement.newBuilder(
              "SELECT DISTINCT SurveyId FROM SurveyAclEntries WHERE UserId = @user"
            )
            .bind("user")
            .to(userId)
            .build()
        ) {
          it.getUuid("SurveyId").toString()
        }

    override fun organizationIdsWithMemberUser(userId: String): List<String> =
      if (userId.isBlank()) emptyList()
      else
        query(
          Statement.newBuilder(
              "SELECT DISTINCT OrganizationId FROM OrganizationMembers WHERE UserId = @user"
            )
            .bind("user")
            .to(userId)
            .build()
        ) {
          it.getUuid("OrganizationId").toString()
        }

    override fun surveysInOrganization(organizationId: String): List<SurveyDef> =
      all("$SURVEY_COLUMNS WHERE OrganizationId = @id", organizationId) {
        decode(SurveyDef.ADAPTER, it).copy(commit_timestamp = it.commitTs())
      }

    private fun surveysWhere(statement: Statement): List<SurveyDef> =
      query(statement) { decode(SurveyDef.ADAPTER, it).copy(commit_timestamp = it.commitTs()) }

    override fun appliedWatermarks(clientId: String): List<AppliedMutationWatermark> =
      ctx
        .executeQuery(
          Statement.newBuilder(
              "SELECT Scope, MaxClientSeq, CommitTs FROM ClientMutationWatermarks " +
                "WHERE ClientId = @id ORDER BY Scope"
            )
            .bind("id")
            .to(clientId)
            .build()
        )
        .use { rs ->
          buildList {
            while (rs.next()) {
              add(
                AppliedMutationWatermark(
                  scope = rs.getString("Scope"),
                  client_seq = rs.getLong("MaxClientSeq"),
                  commit_timestamp = rs.commitTs(),
                )
              )
            }
          }
        }

    private fun entityOf(rs: ResultSet) =
      decode(EntityRecord.ADAPTER, rs).copy(commit_timestamp = rs.commitTs())

    private fun submissionOf(rs: ResultSet) =
      decode(SubmissionRecord.ADAPTER, rs).copy(commit_timestamp = rs.commitTs())

    /**
     * Rows of a survey's [table] filtered on the generated columns: [keyColumn] equal to [key] when
     * it isn't blank, and `IsDeleted` false unless [includeDeleted].
     */
    private fun <T> narrowed(
      table: String,
      keyColumn: String,
      surveyId: String,
      key: String,
      includeDeleted: Boolean,
      decode: (ResultSet) -> T,
    ): List<T> {
      val sql = buildString {
        append("SELECT Record, CommitTs FROM $table WHERE SurveyId = @id")
        if (key.isNotBlank()) append(" AND $keyColumn = @key")
        if (!includeDeleted) append(" AND NOT IFNULL(IsDeleted, FALSE)")
      }
      val statement = bindIds(sql, arrayOf(surveyId)) { if (key.isNotBlank()) bind("key").to(key) }
      return statement?.let { query(it, decode) }.orEmpty()
    }

    /**
     * Up to [limit] rows of a survey's [table] after [after] in commit order, read through the
     * [index] on `(SurveyId, CommitTs, <idColumn>)`. A UUID's string form orders like the UUID, so
     * the position's ID compares as a string.
     */
    private fun <T> changedAfter(
      table: String,
      idColumn: String,
      index: String,
      surveyId: String,
      after: SyncPosition,
      limit: Int,
      decode: (ResultSet) -> T,
    ): List<T> {
      val sql =
        "SELECT Record, CommitTs FROM $table@{FORCE_INDEX=$index} WHERE SurveyId = @id AND " +
          "(CommitTs > @ts OR (CommitTs = @ts AND CAST($idColumn AS STRING) > @afterId)) " +
          "ORDER BY CommitTs, $idColumn LIMIT @limit"
      val statement =
        bindIds(sql, arrayOf(surveyId)) {
          bind("ts").to(Timestamp.ofTimeSecondsAndNanos(after.epochSecond, after.nano))
          bind("afterId").to(after.recordId)
          bind("limit").to(limit.toLong())
        }
      return statement?.let { query(it, decode) }.orEmpty()
    }

    private fun <T> query(statement: Statement, decode: (ResultSet) -> T): List<T> =
      ctx.executeQuery(statement).use { rs -> buildList { while (rs.next()) add(decode(rs)) } }

    /** The single row matching [sql] with `@id` / `@id2` bound to UUID [ids], decoded. */
    private fun <T> one(sql: String, vararg ids: String, decode: (ResultSet) -> T): T? {
      val statement = bindIds(sql, ids) ?: return null
      return ctx.executeQuery(statement).use { if (it.next()) decode(it) else null }
    }

    private fun <T> all(sql: String, vararg ids: String, decode: (ResultSet) -> T): List<T> {
      val statement = bindIds(sql, ids) ?: return emptyList()
      return ctx.executeQuery(statement).use { rs ->
        buildList { while (rs.next()) add(decode(rs)) }
      }
    }

    /** [sql] with its ID parameters bound, or null when an ID isn't a UUID (so nothing matches). */
    private fun bindIds(
      sql: String,
      ids: Array<out String>,
      bindOthers: Statement.Builder.() -> Unit = {},
    ): Statement? {
      val builder = Statement.newBuilder(sql)
      ids.forEachIndexed { index, id ->
        val uuid = uuidOrNull(id) ?: return null
        builder.bind(if (index == 0) "id" else "id${index + 1}").to(uuid)
      }
      builder.bindOthers()
      return builder.build()
    }
  }

  private inner class Writer(private val tx: TransactionContext, override val now: Instant) :
    Reader(tx, now), UserDataWriter {

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

    private val wasNewSurvey = HashSet<String>()

    override fun putSurvey(survey: SurveyDef) {
      val id = survey.survey_id
      uuid(id)
      if (id !in pendingSurveys && survey(id) == null) wasNewSurvey += id
      pendingSurveys[id] = survey
    }

    override fun putSurveyRevision(survey: SurveyDef) = tx.buffer(revisionRow(survey, null))

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
      tx.buffer(
        Mutation.newInsertOrUpdateBuilder("Surveys")
          .set("SurveyId")
          .to(id)
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
      )
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
      val key = Key.of(uuid(surveyId), uuid(entityId))
      if (lock == null) {
        tx.buffer(Mutation.delete("PlotLocks", key))
      } else {
        tx.buffer(
          Mutation.newInsertOrUpdateBuilder("PlotLocks")
            .set("SurveyId")
            .to(uuid(surveyId))
            .set("EntityId")
            .to(uuid(entityId))
            .set("Lock")
            .to(proto(PlotLock.ADAPTER, lock, "groundplatform.v2.data.PlotLock"))
            .build()
        )
      }
    }

    override fun putImpactEvent(event: ImpactEvent) =
      tx.buffer(
        Mutation.newInsertOrUpdateBuilder("ImpactEvents")
          .set("EventId")
          .to(uuid(event.event_id))
          .set("Record")
          .to(
            proto(
              ImpactEvent.ADAPTER,
              event.copy(commit_timestamp = null),
              "groundplatform.v2.data.ImpactEvent",
            )
          )
          .set("CommitTs")
          .to(Value.COMMIT_TIMESTAMP)
          .build()
      )

    override fun putUser(user: UserAccount) =
      tx.buffer(
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
      )

    override fun recordApplied(scope: String, clientId: String, clientSeq: Long) {
      if (clientId.isBlank() || clientSeq <= 0L) return
      val previous = appliedWatermarks(clientId).firstOrNull { it.scope == scope }?.client_seq
      if (previous != null && previous >= clientSeq) return
      tx.buffer(
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
      )
    }
  }

  private fun organizationRow(org: Organization, commitTs: Instant?): Mutation =
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

  private fun memberRow(
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

  private fun libraryRow(library: OrganizationLibraryRecord, commitTs: Instant?): Mutation =
    Mutation.newInsertOrUpdateBuilder("OrganizationLibraries")
      .set("OrganizationId")
      .to(uuid(library.organization_id))
      .set("Record")
      .to(proto(OrganizationLibraryRecord.ADAPTER, library.copy(commit_timestamp = null), LIBRARY))
      .set("CommitTs")
      .to(commitTimestampValue(commitTs))
      .build()

  /** One `LibraryConcepts` row per concept in [library]'s bundle, with its search text. */
  private fun conceptRows(library: OrganizationLibraryRecord): List<Mutation> =
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
  private fun surveyRow(survey: SurveyDef, commitTs: Instant?, acl: SurveyAcl? = null): Mutation {
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
  private fun aclUpdate(acl: SurveyAcl): Mutation =
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

  private fun aclEntryRow(surveyId: String, entry: AclEntry, commitTs: Instant?): Mutation =
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

  private fun revisionRow(survey: SurveyDef, commitTs: Instant?): Mutation =
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

  private fun workingCopyRow(surveyId: String, def: SurveyDef, commitTs: Instant?): Mutation =
    Mutation.newInsertOrUpdateBuilder("SurveyWorkingCopies")
      .set("SurveyId")
      .to(uuid(surveyId))
      .set("Def")
      .to(proto(SurveyDef.ADAPTER, def.copy(commit_timestamp = null), SURVEY_DEF))
      .set("CommitTs")
      .to(commitTimestampValue(commitTs))
      .build()

  private fun entityRow(entity: EntityRecord, commitTs: Instant?): Mutation =
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

  private fun submissionRow(submission: SubmissionRecord, commitTs: Instant?): Mutation =
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

  companion object {
    /** [dataEpoch] before [reset] first wrote one. */
    const val NO_EPOCH = "unset"

    private const val SEED_BATCH_SIZE = 200
    private const val ORGANIZATION = "groundplatform.v2.survey.Organization"
    private const val ORGANIZATION_MEMBER = "groundplatform.v2.survey.OrganizationMember"
    private const val LIBRARY = "groundplatform.v2.library.OrganizationLibraryRecord"
    private const val CONCEPT = "groundplatform.v2.library.ConceptDef"
    private const val SURVEY_DEF = "groundplatform.v2.survey.SurveyDef"
    private const val SURVEY_ACL = "groundplatform.v2.survey.SurveyAcl"
    private const val ACL_ENTRY = "groundplatform.v2.survey.AclEntry"
    private const val CACHED_PROFILE = "groundplatform.v2.survey.CachedProfile"
    private const val SURVEY_COLUMNS = "SELECT SavedDef AS Record, CommitTs FROM Surveys"
    private const val USER_COLUMNS =
      "SELECT UserId, EmailNormalized, EmailVerified, Profile, AccessEpoch, CreateTime FROM Users"

    private fun emailKey(email: String, userId: String): String =
      email.trim().lowercase().ifEmpty { userId.trim() }

    private const val ENTITY = "groundplatform.v2.data.EntityRecord"
    private const val SUBMISSION = "groundplatform.v2.data.SubmissionRecord"

    private fun uuidOrNull(id: String): UUID? = runCatching { UUID.fromString(id) }.getOrNull()

    private fun uuid(id: String): UUID =
      requireNotNull(uuidOrNull(id)) { "Record IDs must be UUIDs, got \"$id\"" }

    private fun <M : Message<M, *>> proto(adapter: ProtoAdapter<M>, message: M, type: String) =
      Value.protoMessage(ByteArray.copyFrom(adapter.encode(message)), type)

    private fun <M> decode(adapter: ProtoAdapter<M>, rs: ResultSet): M =
      adapter.decode(rs.getBytes("Record").toByteArray())

    /** Spanner's commit timestamp, or [explicit] (an earlier time, e.g. from the seed). */
    private fun commitTimestampValue(explicit: Instant?): Value =
      if (explicit == null) Value.timestamp(Value.COMMIT_TIMESTAMP)
      else Value.timestamp(Timestamp.ofTimeSecondsAndNanos(explicit.epochSecond, explicit.nano))

    private fun ResultSet.commitTs(): Instant = getTimestamp("CommitTs").toInstant()

    private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())
  }
}
