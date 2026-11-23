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

import com.google.cloud.Timestamp
import com.google.cloud.spanner.Key
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.ReadContext
import com.google.cloud.spanner.ResultSet
import com.google.cloud.spanner.Statement
import com.squareup.wire.Instant
import openforis.collectone.v1.data.AppliedMutationWatermark
import openforis.collectone.v1.data.AuditRecord
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ImpactEvent
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.PlaceRecord
import openforis.collectone.v1.data.PlotLock
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.UserDataSeed

/**
 * [UserDataReader] over one Spanner read context of [SpannerUserDataStore]: a strong read-only
 * transaction for reads, or the read-write transaction itself for writes ([SpannerUserDataWriter]),
 * so the reads of a write block run in (and lock rows for) that transaction.
 *
 * Records are `PROTO` columns decoded with Wire, with `commit_timestamp` taken from the row's
 * `CommitTs` column rather than the stored message. Lookups by an ID that isn't a UUID find nothing
 * instead of failing, since every key column is a UUID. Places and mutation log examples come from
 * [seed], not Spanner.
 */
internal open class SpannerUserDataReader(
  private val ctx: ReadContext,
  override val snapshotTimestamp: Instant,
  private val seed: UserDataSeed,
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
      query(statement) { decode(Organization.ADAPTER, it).copy(commit_timestamp = it.commitTs()) }
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
    val statement = Statement.newBuilder(sql).bind("policy").to(SharingPolicy.PUBLIC.value.toLong())
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

  override fun entitiesByIds(
    surveyId: String,
    entityIds: Collection<String>,
    includeDeleted: Boolean,
  ): List<EntityRecord> {
    val surveyUuid = uuidOrNull(surveyId) ?: return emptyList()
    val keys = KeySet.newBuilder()
    var count = 0
    for (id in entityIds.distinct()) {
      val entityUuid = uuidOrNull(id) ?: continue
      keys.addKey(Key.of(surveyUuid, entityUuid))
      count++
    }
    if (count == 0) return emptyList()
    return ctx.read("Entities", keys.build(), listOf("Record", "CommitTs")).use { rs ->
      buildList {
        while (rs.next()) {
          val record = entityOf(rs)
          if (includeDeleted || !record.is_deleted) add(record)
        }
      }
    }
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
        Statement.newBuilder("SELECT DISTINCT SurveyId FROM SurveyAclEntries WHERE UserId = @user")
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

private const val SURVEY_COLUMNS = "SELECT SavedDef AS Record, CommitTs FROM Surveys"
private const val USER_COLUMNS =
  "SELECT UserId, EmailNormalized, EmailVerified, Profile, AccessEpoch, CreateTime FROM Users"
