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

import com.squareup.wire.Instant
import groundplatform.v2.data.AppliedMutationWatermark
import groundplatform.v2.data.AuditRecord
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ImpactEvent
import groundplatform.v2.data.MutationLogRecord
import groundplatform.v2.data.PlaceRecord
import groundplatform.v2.data.PlotLock
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.userdata.UserDataSeed

/**
 * [UserDataStore] that keeps everything in memory, initialized from [seed]. State lives in this
 * instance only: it isn't shared between server instances and is lost on restart.
 *
 * Commit timestamps come from a logical clock that advances one second per write, starting after
 * the latest timestamp in [seed]. Not thread-safe: callers serialize access (see [UserDataServer]).
 */
class InMemoryUserDataStore(private val seed: UserDataSeed = UserDataSeed()) : UserDataStore {
  private var epochSecond: Long = INITIAL_EPOCH_SECOND

  override var dataEpoch: String = newDataEpoch()
    private set

  private val organizations = linkedMapOf<String, Organization>()
  private val libraries = linkedMapOf<String, OrganizationLibraryRecord>()
  private val surveys = linkedMapOf<String, SurveyDef>()
  private val workingCopies = linkedMapOf<String, SurveyDef>()
  private val acls = linkedMapOf<String, SurveyAcl>()
  private val entities = linkedMapOf<String, LinkedHashMap<String, EntityRecord>>()
  private val submissions = linkedMapOf<String, LinkedHashMap<String, SubmissionRecord>>()
  private val plotLocks = linkedMapOf<Pair<String, String>, PlotLock>()
  private val impactEvents = linkedMapOf<String, ImpactEvent>()
  private val auditRecords = linkedMapOf<String, AuditRecord>()
  private val places = mutableListOf<PlaceRecord>()
  private val mutationLogs = mutableListOf<MutationLogRecord>()
  private val appliedMutationIds = LinkedHashSet<String>()
  private val watermarks = linkedMapOf<Pair<String, String>, AppliedMutationWatermark>()

  init {
    reset()
  }

  override fun watermark(): Instant = Instant.ofEpochSecond(epochSecond, 0L)

  override fun <T> read(block: (UserDataReader) -> T): T = block(Reader())

  override fun <T> write(block: (UserDataWriter) -> T): Committed<T> {
    val commitTimestamp = Instant.ofEpochSecond(epochSecond + 1L, 0L)
    val writer = Writer(commitTimestamp)
    val value = block(writer)
    epochSecond += 1L
    writer.writes.forEach { it(commitTimestamp) }
    return Committed(value, commitTimestamp)
  }

  override fun reset() {
    dataEpoch = newDataEpoch()
    organizations.clear()
    libraries.clear()
    surveys.clear()
    workingCopies.clear()
    acls.clear()
    entities.clear()
    submissions.clear()
    plotLocks.clear()
    impactEvents.clear()
    auditRecords.clear()
    places.clear()
    mutationLogs.clear()
    appliedMutationIds.clear()
    watermarks.clear()

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

  private open inner class Reader : UserDataReader {
    override val snapshotTimestamp: Instant = watermark()

    override fun organization(organizationId: String) = organizations[organizationId]

    override fun organizations() = organizations.values.toList()

    override fun library(organizationId: String) = libraries[organizationId]

    override fun libraries() = libraries.values.toList()

    override fun survey(surveyId: String) = surveys[surveyId]

    override fun surveys() = surveys.values.toList()

    override fun workingCopy(surveyId: String) = workingCopies[surveyId]

    override fun acl(surveyId: String) = acls[surveyId]

    override fun entity(surveyId: String, entityId: String) = entities[surveyId]?.get(entityId)

    override fun entities(surveyId: String) = entities[surveyId]?.values?.toList().orEmpty()

    override fun submission(surveyId: String, submissionId: String) =
      submissions[surveyId]?.get(submissionId)

    override fun submissions(surveyId: String) = submissions[surveyId]?.values?.toList().orEmpty()

    override fun plotLock(surveyId: String, entityId: String) = plotLocks[surveyId to entityId]

    override fun impactEvents() = impactEvents.values.toList()

    override fun auditRecords() = auditRecords.values.toList()

    override fun places() = places.toList()

    override fun mutationLogs() = mutationLogs.toList()

    override fun isMutationApplied(mutationId: String) = mutationId in appliedMutationIds

    override fun appliedWatermarks(clientId: String) =
      watermarks.filterKeys { it.first == clientId }.values.toList()
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
      writes += { ts -> surveys[survey.survey_id] = survey.copy(commit_timestamp = ts) }
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

    override fun recordApplied(
      scope: String,
      mutationIds: List<String>,
      clientId: String,
      clientSeq: Long,
    ) {
      writes += { ts ->
        appliedMutationIds += mutationIds.filter { it.isNotBlank() }
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
