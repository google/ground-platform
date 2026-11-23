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
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy

/**
 * In-memory [UserDataStore] initialized from [seed], used by unit tests (without Spanner) and by
 * [StagedSeedOverlayUserDataStore] for non-mutating staged previews. `UserDataStoreContractTest`
 * checks that it behaves like [SpannerUserDataStore], the service's persistent store.
 *
 * Commit timestamps come from a logical clock that advances one second per write, starting after
 * the latest timestamp in [seed]. Reads, writes, and resets run one at a time (like Spanner's
 * serializable transactions), so concurrent requests see each other's writes whole or not at all.
 */
class InMemoryUserDataStore(private val seed: UserDataSeed = UserDataSeed()) : UserDataStore {
  private var epochSecond: Long = INITIAL_EPOCH_SECOND

  override var dataEpoch: String = newDataEpoch()
    private set

  private val organizations = linkedMapOf<String, Organization>()
  private val libraries = linkedMapOf<String, OrganizationLibraryRecord>()
  private val surveys = linkedMapOf<String, SurveyDef>()
  private val surveyRevisions = linkedMapOf<Pair<String, String>, SurveyDef>()
  private val workingCopies = linkedMapOf<String, SurveyDef>()
  private val acls = linkedMapOf<String, SurveyAcl>()
  private val entities = linkedMapOf<String, LinkedHashMap<String, EntityRecord>>()
  private val submissions = linkedMapOf<String, LinkedHashMap<String, SubmissionRecord>>()
  private val plotLocks = linkedMapOf<Pair<String, String>, PlotLock>()
  private val impactEvents = linkedMapOf<String, ImpactEvent>()
  private val auditRecords = linkedMapOf<String, AuditRecord>()
  private val places = mutableListOf<PlaceRecord>()
  private val mutationLogs = mutableListOf<MutationLogRecord>()
  private val users = linkedMapOf<String, UserAccount>()
  private val watermarks = linkedMapOf<Pair<String, String>, AppliedMutationWatermark>()
  private var storedClientVersionPolicy: MobileClientVersionPolicy? = null

  init {
    loadInitialData()
  }

  /** Advances the logical commit clock to at least [minEpochSecond]. */
  internal fun advanceClockAtLeast(minEpochSecond: Long) =
    synchronized(this) {
      if (minEpochSecond > epochSecond) {
        epochSecond = minEpochSecond
      }
    }

  override suspend fun watermark(): Instant = synchronized(this) { latestCommit() }

  private fun latestCommit(): Instant = Instant.ofEpochSecond(epochSecond, 0L)

  override suspend fun <T> read(block: (UserDataReader) -> T): T = readSync(block)

  internal fun <T> readSync(block: (UserDataReader) -> T): T =
    synchronized(this) { block(Reader()) }

  override suspend fun <T> write(block: (UserDataWriter) -> T): Committed<T> = writeSync(block)

  internal fun <T> writeSync(block: (UserDataWriter) -> T): Committed<T> =
    synchronized(this) {
      val commitTimestamp = Instant.ofEpochSecond(epochSecond + 1L, 0L)
      val writer = Writer(commitTimestamp)
      val value = block(writer)
      epochSecond += 1L
      writer.writes.forEach { it(commitTimestamp) }
      Committed(value, commitTimestamp)
    }

  override suspend fun reset(): Unit = synchronized(this) { loadInitialData() }

  override suspend fun clientVersionPolicy(): MobileClientVersionPolicy? =
    synchronized(this) { storedClientVersionPolicy }

  override suspend fun saveClientVersionPolicy(policy: MobileClientVersionPolicy): Unit =
    synchronized(this) {
      storedClientVersionPolicy = policy.normalized()
    }

  private fun loadInitialData() {
    dataEpoch = newDataEpoch()
    organizations.clear()
    libraries.clear()
    surveys.clear()
    surveyRevisions.clear()
    workingCopies.clear()
    acls.clear()
    entities.clear()
    submissions.clear()
    plotLocks.clear()
    impactEvents.clear()
    auditRecords.clear()
    places.clear()
    mutationLogs.clear()
    watermarks.clear()
    users.clear()
    storedClientVersionPolicy = null

    val timestamps = mutableListOf<Instant?>()
    seed.organizations.forEach {
      organizations[it.organization_id] = it
      timestamps += it.commit_timestamp
    }
    seed.libraries.forEach {
      libraries[it.organization_id] = it
      timestamps += it.commit_timestamp
    }
    for (surveySeed in seed.surveys) {
      val survey = surveySeed.survey ?: continue
      val surveyId = survey.survey_id
      surveys[surveyId] = survey
      if (survey.version.isNotBlank()) surveyRevisions[surveyId to survey.version] = survey
      surveySeed.acl?.let { acls[surveyId] = it }
      surveySeed.working_copy?.let { workingCopies[surveyId] = it }
      entities[surveyId] = surveySeed.entities.associateByTo(LinkedHashMap()) { it.entity_id }
      submissions[surveyId] =
        surveySeed.submissions.associateByTo(LinkedHashMap()) { it.submission_id }
      timestamps += survey.commit_timestamp
      timestamps += surveySeed.acl?.commit_timestamp
      timestamps += surveySeed.entities.map { it.commit_timestamp }
      timestamps += surveySeed.submissions.map { it.commit_timestamp }
    }
    places += seed.places
    mutationLogs += seed.mutation_logs
    timestamps += seed.mutation_logs.map { it.commit_timestamp }

    epochSecond =
      maxOf(INITIAL_EPOCH_SECOND, timestamps.maxOfOrNull { it?.epochSecond ?: 0L } ?: 0L)
  }

  private abstract inner class BaseReader : UserDataReader {
    override val snapshotTimestamp: Instant = latestCommit()

    override fun organization(organizationId: String) = organizations[organizationId]

    override fun organizations() = organizations.values.toList()

    override fun library(organizationId: String) = libraries[organizationId]

    override fun libraries() = libraries.values.toList()

    // Search candidates are every record passing the non-text filters; callers match the text.
    override fun searchOrganizations(query: String) =
      organizations.values.filter { it.listed && !it.is_deleted }

    override fun searchPublicSurveys(query: String) =
      surveys.values.filter { acls[it.survey_id]?.sharing_policy == SharingPolicy.PUBLIC }

    override fun searchConcepts(organizationIds: List<String>, query: String): List<ConceptDef> {
      val resolved = LinkedHashMap<String, ConceptDef>()
      for (id in organizationIds.distinct()) {
        libraries[id]?.bundle?.concepts?.forEach { resolved[it.id] = it }
      }
      return resolved.values.toList()
    }

    override fun impactEvents() = impactEvents.values.toList()

    override fun auditRecords() = auditRecords.values.toList()

    override fun places() = places.toList()

    override fun mutationLogs() = mutationLogs.toList()

    override fun user(userId: String) = users[userId]

    override fun usersByEmail(email: String) = users.values.filter { it.email == email }

    override fun surveyIdsWithAclEmail(email: String) =
      acls.values
        .filter { acl -> acl.entries.any { it.email.trim().lowercase() == email } }
        .map { it.survey_id }

    override fun organizationIdsWithMemberEmail(email: String) =
      organizations.values
        .filter { org -> org.members.any { it.email.trim().lowercase() == email } }
        .map { it.organization_id }

    override fun surveyIdsWithAclUser(userId: String) =
      if (userId.isBlank()) emptyList()
      else
        acls.values.filter { acl -> acl.entries.any { it.user_id == userId } }.map { it.survey_id }

    override fun organizationIdsWithMemberUser(userId: String) =
      if (userId.isBlank()) emptyList()
      else
        organizations.values
          .filter { org -> org.members.any { it.user_id == userId } }
          .map { it.organization_id }

    override fun appliedWatermarks(clientId: String) =
      watermarks.filterKeys { it.first == clientId }.values.toList()
  }

  private open inner class Reader : BaseReader() {
    override fun survey(surveyId: String) = surveys[surveyId]

    override fun surveyRevision(surveyId: String, version: String) =
      surveyRevisions[surveyId to version] ?: surveys[surveyId]?.takeIf { it.version == version }

    override fun surveys() = surveys.values.toList()

    override fun workingCopy(surveyId: String) = workingCopies[surveyId]

    override fun acl(surveyId: String) = acls[surveyId]

    override fun entity(surveyId: String, entityId: String) = entities[surveyId]?.get(entityId)

    override fun entities(surveyId: String, datasetId: String, includeDeleted: Boolean) =
      entities[surveyId]
        ?.values
        ?.filter {
          (datasetId.isBlank() || it.dataset_id == datasetId) && (includeDeleted || !it.is_deleted)
        }
        .orEmpty()

    override fun entitiesChangedAfter(surveyId: String, after: SyncPosition, limit: Int) =
      entities[surveyId]
        ?.values
        ?.changedAfter(after, limit, EntityRecord::commit_timestamp, EntityRecord::entity_id)
        .orEmpty()

    override fun submission(surveyId: String, submissionId: String) =
      submissions[surveyId]?.get(submissionId)

    override fun submissions(surveyId: String, formId: String, includeDeleted: Boolean) =
      submissions[surveyId]
        ?.values
        ?.filter {
          (formId.isBlank() || it.form_id == formId) && (includeDeleted || !it.is_deleted)
        }
        .orEmpty()

    override fun submissionsChangedAfter(surveyId: String, after: SyncPosition, limit: Int) =
      submissions[surveyId]
        ?.values
        ?.changedAfter(
          after,
          limit,
          SubmissionRecord::commit_timestamp,
          SubmissionRecord::submission_id,
        )
        .orEmpty()

    override fun plotLock(surveyId: String, entityId: String) = plotLocks[surveyId to entityId]

    override fun surveysOwnedBy(userId: String, email: String) =
      surveys.values.filter { s ->
        (userId.isNotBlank() && s.owner_id == userId) ||
          (email.isNotBlank() &&
            (s.owner_id == email || acls[s.survey_id]?.owner_id?.trim()?.lowercase() == email))
      }

    override fun surveysInOrganization(organizationId: String) =
      surveys.values.filter { it.organization_id == organizationId }
  }

  /** Reads committed data and buffers writes, applied in order once the block returns. */
  private inner class Writer(override val now: Instant) : Reader(), UserDataWriter {
    val writes = mutableListOf<(Instant) -> Unit>()

    override fun putOrganization(organization: Organization) {
      writes += { ts ->
        organizations[organization.organization_id] = organization.copy(commit_timestamp = ts)
      }
    }

    override fun putLibrary(library: OrganizationLibraryRecord) {
      writes += { ts -> libraries[library.organization_id] = library.copy(commit_timestamp = ts) }
    }

    override fun putSurvey(survey: SurveyDef) {
      writes += { ts ->
        val stamped = survey.copy(commit_timestamp = ts)
        surveys[survey.survey_id] = stamped
        if (
          stamped.version.isNotBlank() && (survey.survey_id to stamped.version) !in surveyRevisions
        ) {
          surveyRevisions[survey.survey_id to stamped.version] = stamped
        }
      }
    }

    override fun putSurveyRevision(survey: SurveyDef) {
      writes += { ts ->
        surveyRevisions[survey.survey_id to survey.version] = survey.copy(commit_timestamp = ts)
      }
    }

    override fun purgeSurvey(tombstone: SurveyDef) {
      val id = tombstone.survey_id
      writes += { ts ->
        surveys[id] = tombstone.copy(commit_timestamp = ts)
        surveyRevisions.keys.removeAll { it.first == id }
        workingCopies.remove(id)
        acls.remove(id)
        entities.remove(id)
        submissions.remove(id)
        plotLocks.keys.removeAll { it.first == id }
        auditRecords.values.removeAll { it.survey_id == id }
      }
    }

    override fun putWorkingCopy(surveyId: String, workingCopy: SurveyDef?) {
      writes += { ts ->
        if (workingCopy == null) workingCopies.remove(surveyId)
        else workingCopies[surveyId] = workingCopy.copy(commit_timestamp = ts)
      }
    }

    override fun putAcl(acl: SurveyAcl) {
      writes += { ts -> acls[acl.survey_id] = acl.copy(commit_timestamp = ts) }
    }

    override fun putEntity(entity: EntityRecord) {
      writes += { ts ->
        entities.getOrPut(entity.survey_id) { LinkedHashMap() }[entity.entity_id] =
          entity.copy(commit_timestamp = ts)
      }
    }

    override fun putSubmission(submission: SubmissionRecord) {
      writes += { ts ->
        submissions.getOrPut(submission.survey_id) { LinkedHashMap() }[submission.submission_id] =
          submission.copy(commit_timestamp = ts)
      }
    }

    override fun putPlotLock(surveyId: String, entityId: String, lock: PlotLock?) {
      writes += {
        if (lock == null) plotLocks.remove(surveyId to entityId)
        else plotLocks[surveyId to entityId] = lock
      }
    }

    override fun putImpactEvent(event: ImpactEvent) {
      writes += { ts -> impactEvents[event.event_id] = event.copy(commit_timestamp = ts) }
    }

    override fun putUser(user: UserAccount) {
      writes += { users[user.userId] = user }
    }

    override fun recordApplied(scope: String, clientId: String, clientSeq: Long) {
      writes += { ts ->
        if (clientId.isNotBlank() && clientSeq > 0L) {
          val key = clientId to scope
          val previous = watermarks[key]
          if (previous == null || clientSeq > previous.client_seq) {
            watermarks[key] =
              AppliedMutationWatermark(scope = scope, client_seq = clientSeq, commit_timestamp = ts)
          }
        }
      }
    }
  }

  companion object {
    /** Server clock start when the seed has no timestamps. */
    const val INITIAL_EPOCH_SECOND: Long = 1_770_000_000L

    private fun newDataEpoch(): String = java.util.UUID.randomUUID().toString()
  }
}
