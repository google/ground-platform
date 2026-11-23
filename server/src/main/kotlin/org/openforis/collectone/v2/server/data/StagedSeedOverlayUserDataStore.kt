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
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy

/**
 * [UserDataStore] wrapper for staged (`--no-traffic`) server revisions sharing a live Spanner
 * database (`SEED_AREA_BACKFILL_ENABLED=false`).
 *
 * Any sample survey in [seed] not yet present in [primary] is served from an in-memory [overlay],
 * and writes targeting those staged seed surveys commit strictly in memory without mutating live
 * Spanner. When every seed survey already exists in [primary], [create] returns [primary] directly.
 */
internal class StagedSeedOverlayUserDataStore
private constructor(
  private val primary: UserDataStore,
  private val overlay: InMemoryUserDataStore,
  private val overlaySurveyIds: Set<String>,
  private val seedLaunchConfigsBySurveyId:
    Map<String, List<openforis.collectone.v1.survey.FormLaunchConfig>>,
) : UserDataStore {

  override val dataEpoch: String
    get() = primary.dataEpoch

  override suspend fun watermark(): Instant = maxInstant(primary.watermark(), overlay.watermark())

  override suspend fun <T> read(block: (UserDataReader) -> T): T = primary.read { p ->
    overlay.readSync { o -> block(MergedReader(p, o)) }
  }

  override suspend fun <T> write(block: (UserDataWriter) -> T): Committed<T> {
    val primaryWatermark = primary.watermark()
    overlay.advanceClockAtLeast(primaryWatermark.epochSecond + 1L)

    var requiresPrimaryWrite = false
    primary.read { p ->
      overlay.readSync { o ->
        val probe = ProbeDryRunWriter(p, o) { requiresPrimaryWrite = true }
        runCatching { block(probe) }
      }
    }
    if (!requiresPrimaryWrite) {
      return primary.read { p ->
        overlay.writeSync { o -> block(ProbeWriter(p, o) {}) }
      }
    }
    return primary.write { pTx ->
      overlay.writeSync { oTx -> block(RoutingWriter(pTx, oTx)) }.value
    }
  }

  override suspend fun reset() {
    overlay.reset()
  }

  override suspend fun clientVersionPolicy(): MobileClientVersionPolicy? =
    overlay.clientVersionPolicy() ?: primary.clientVersionPolicy()

  override suspend fun saveClientVersionPolicy(policy: MobileClientVersionPolicy) {
    overlay.saveClientVersionPolicy(policy)
  }

  private fun patchUneditedSeedSurvey(survey: SurveyDef?): SurveyDef? {
    if (survey == null || survey.record_version > 1L) return survey
    val seedConfigs = seedLaunchConfigsBySurveyId[survey.survey_id] ?: return survey
    return if (survey.form_launch_configs != seedConfigs) {
      survey.copy(form_launch_configs = seedConfigs)
    } else {
      survey
    }
  }

  private abstract inner class BaseMergedReader(
    protected val p: UserDataReader,
    protected val o: UserDataReader,
  ) : UserDataReader {
    override val snapshotTimestamp: Instant = maxInstant(p.snapshotTimestamp, o.snapshotTimestamp)

    override fun organization(organizationId: String): Organization? =
      p.organization(organizationId) ?: o.organization(organizationId)

    override fun organizations(): List<Organization> = p.organizations()

    override fun library(organizationId: String): OrganizationLibraryRecord? =
      p.library(organizationId) ?: o.library(organizationId)

    override fun libraries(): List<OrganizationLibraryRecord> = p.libraries()

    override fun searchOrganizations(query: String): List<Organization> =
      p.searchOrganizations(query)

    override fun searchPublicSurveys(query: String): List<SurveyDef> =
      p.searchPublicSurveys(query).mapNotNull(::patchUneditedSeedSurvey) +
        o.searchPublicSurveys(query)

    override fun searchConcepts(organizationIds: List<String>, query: String): List<ConceptDef> =
      p.searchConcepts(organizationIds, query)

    override fun impactEvents(): List<ImpactEvent> = p.impactEvents() + o.impactEvents()

    override fun auditRecords(): List<AuditRecord> = p.auditRecords() + o.auditRecords()

    override fun places(): List<PlaceRecord> = p.places()

    override fun mutationLogs(): List<MutationLogRecord> = p.mutationLogs()

    override fun user(userId: String): UserAccount? = o.user(userId) ?: p.user(userId)

    override fun usersByEmail(email: String): List<UserAccount> =
      (o.usersByEmail(email) + p.usersByEmail(email)).distinctBy { it.userId }

    override fun surveyIdsWithAclEmail(email: String): List<String> =
      (p.surveyIdsWithAclEmail(email) + o.surveyIdsWithAclEmail(email)).distinct()

    override fun organizationIdsWithMemberEmail(email: String): List<String> =
      p.organizationIdsWithMemberEmail(email)

    override fun surveyIdsWithAclUser(userId: String): List<String> =
      (p.surveyIdsWithAclUser(userId) + o.surveyIdsWithAclUser(userId)).distinct()

    override fun organizationIdsWithMemberUser(userId: String): List<String> =
      p.organizationIdsWithMemberUser(userId)

    override fun appliedWatermarks(clientId: String): List<AppliedMutationWatermark> {
      val merged = LinkedHashMap<String, AppliedMutationWatermark>()
      for (wm in p.appliedWatermarks(clientId) + o.appliedWatermarks(clientId)) {
        val prev = merged[wm.scope]
        if (prev == null || wm.client_seq > prev.client_seq) {
          merged[wm.scope] = wm
        }
      }
      return merged.values.toList()
    }
  }

  private open inner class MergedReader(
    p: UserDataReader,
    o: UserDataReader,
  ) : BaseMergedReader(p, o) {
    override fun survey(surveyId: String): SurveyDef? =
      if (surveyId in overlaySurveyIds) o.survey(surveyId)
      else patchUneditedSeedSurvey(p.survey(surveyId))

    override fun surveyRevision(surveyId: String, version: String): SurveyDef? =
      if (surveyId in overlaySurveyIds) o.surveyRevision(surveyId, version)
      else patchUneditedSeedSurvey(p.surveyRevision(surveyId, version))

    override fun surveys(): List<SurveyDef> =
      p.surveys().mapNotNull(::patchUneditedSeedSurvey) + o.surveys()

    override fun workingCopy(surveyId: String): SurveyDef? =
      if (surveyId in overlaySurveyIds) o.workingCopy(surveyId) else p.workingCopy(surveyId)

    override fun acl(surveyId: String): SurveyAcl? =
      if (surveyId in overlaySurveyIds) o.acl(surveyId) else p.acl(surveyId)

    override fun entity(surveyId: String, entityId: String): EntityRecord? =
      if (surveyId in overlaySurveyIds) o.entity(surveyId, entityId)
      else p.entity(surveyId, entityId)

    override fun entitiesByIds(
      surveyId: String,
      entityIds: Collection<String>,
      includeDeleted: Boolean,
    ): List<EntityRecord> =
      if (surveyId in overlaySurveyIds) o.entitiesByIds(surveyId, entityIds, includeDeleted)
      else p.entitiesByIds(surveyId, entityIds, includeDeleted)

    override fun entities(
      surveyId: String,
      datasetId: String,
      includeDeleted: Boolean,
    ): List<EntityRecord> =
      if (surveyId in overlaySurveyIds) o.entities(surveyId, datasetId, includeDeleted)
      else p.entities(surveyId, datasetId, includeDeleted)

    override fun entitiesChangedAfter(
      surveyId: String,
      after: SyncPosition,
      limit: Int,
    ): List<EntityRecord> =
      if (surveyId in overlaySurveyIds) o.entitiesChangedAfter(surveyId, after, limit)
      else p.entitiesChangedAfter(surveyId, after, limit)

    override fun submission(surveyId: String, submissionId: String): SubmissionRecord? =
      if (surveyId in overlaySurveyIds) o.submission(surveyId, submissionId)
      else p.submission(surveyId, submissionId)

    override fun submissions(
      surveyId: String,
      formId: String,
      includeDeleted: Boolean,
    ): List<SubmissionRecord> =
      if (surveyId in overlaySurveyIds) o.submissions(surveyId, formId, includeDeleted)
      else p.submissions(surveyId, formId, includeDeleted)

    override fun submissionsChangedAfter(
      surveyId: String,
      after: SyncPosition,
      limit: Int,
    ): List<SubmissionRecord> =
      if (surveyId in overlaySurveyIds) o.submissionsChangedAfter(surveyId, after, limit)
      else p.submissionsChangedAfter(surveyId, after, limit)

    override fun plotLock(surveyId: String, entityId: String): PlotLock? =
      if (surveyId in overlaySurveyIds) o.plotLock(surveyId, entityId)
      else p.plotLock(surveyId, entityId)

    override fun surveysOwnedBy(userId: String, email: String): List<SurveyDef> =
      p.surveysOwnedBy(userId, email).mapNotNull(::patchUneditedSeedSurvey) +
        o.surveysOwnedBy(userId, email)

    override fun surveysInOrganization(organizationId: String): List<SurveyDef> =
      p.surveysInOrganization(organizationId).mapNotNull(::patchUneditedSeedSurvey) +
        o.surveysInOrganization(organizationId)
  }

  private fun isOverlayScope(scope: String): Boolean = overlaySurveyIds.any { id ->
    scope == "surveys/$id" || scope.startsWith("surveys/$id/") || scope == "data/$id"
  }

  private inner class ProbeDryRunWriter(
    p: UserDataReader,
    o: UserDataReader,
    private val onPrimaryWrite: () -> Unit,
  ) : MergedReader(p, o), UserDataWriter {
    override val now: Instant = snapshotTimestamp

    override fun putOrganization(organization: Organization) = onPrimaryWrite()

    override fun putLibrary(library: OrganizationLibraryRecord) = onPrimaryWrite()

    override fun putSurvey(survey: SurveyDef) {
      if (survey.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putSurveyRevision(survey: SurveyDef) {
      if (survey.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun purgeSurvey(tombstone: SurveyDef) {
      if (tombstone.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putWorkingCopy(surveyId: String, workingCopy: SurveyDef?) {
      if (surveyId !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putAcl(acl: SurveyAcl) {
      if (acl.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putEntity(entity: EntityRecord) {
      if (entity.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putSubmission(submission: SubmissionRecord) {
      if (submission.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putPlotLock(surveyId: String, entityId: String, lock: PlotLock?) {
      if (surveyId !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putImpactEvent(event: ImpactEvent) {
      if (event.survey_id !in overlaySurveyIds) onPrimaryWrite()
    }

    override fun putUser(user: UserAccount) = Unit

    override fun recordApplied(scope: String, clientId: String, clientSeq: Long) {
      if (!isOverlayScope(scope)) onPrimaryWrite()
    }
  }

  private inner class ProbeWriter(
    p: UserDataReader,
    private val oTx: UserDataWriter,
    private val onPrimaryWrite: () -> Unit,
  ) : MergedReader(p, oTx), UserDataWriter {
    override val now: Instant = oTx.now

    override fun putOrganization(organization: Organization) = onPrimaryWrite()

    override fun putLibrary(library: OrganizationLibraryRecord) = onPrimaryWrite()

    override fun putSurvey(survey: SurveyDef) {
      if (survey.survey_id in overlaySurveyIds) oTx.putSurvey(survey) else onPrimaryWrite()
    }

    override fun putSurveyRevision(survey: SurveyDef) {
      if (survey.survey_id in overlaySurveyIds) oTx.putSurveyRevision(survey) else onPrimaryWrite()
    }

    override fun purgeSurvey(tombstone: SurveyDef) {
      if (tombstone.survey_id in overlaySurveyIds) oTx.purgeSurvey(tombstone) else onPrimaryWrite()
    }

    override fun putWorkingCopy(surveyId: String, workingCopy: SurveyDef?) {
      if (surveyId in overlaySurveyIds) oTx.putWorkingCopy(surveyId, workingCopy)
      else onPrimaryWrite()
    }

    override fun putAcl(acl: SurveyAcl) {
      if (acl.survey_id in overlaySurveyIds) oTx.putAcl(acl) else onPrimaryWrite()
    }

    override fun putEntity(entity: EntityRecord) {
      if (entity.survey_id in overlaySurveyIds) oTx.putEntity(entity) else onPrimaryWrite()
    }

    override fun putSubmission(submission: SubmissionRecord) {
      if (submission.survey_id in overlaySurveyIds) oTx.putSubmission(submission)
      else onPrimaryWrite()
    }

    override fun putPlotLock(surveyId: String, entityId: String, lock: PlotLock?) {
      if (surveyId in overlaySurveyIds) oTx.putPlotLock(surveyId, entityId, lock)
      else onPrimaryWrite()
    }

    override fun putImpactEvent(event: ImpactEvent) {
      if (event.survey_id in overlaySurveyIds) oTx.putImpactEvent(event) else onPrimaryWrite()
    }

    override fun putUser(user: UserAccount) = oTx.putUser(user)

    override fun recordApplied(scope: String, clientId: String, clientSeq: Long) {
      if (isOverlayScope(scope)) oTx.recordApplied(scope, clientId, clientSeq) else onPrimaryWrite()
    }
  }

  private inner class RoutingWriter(
    private val pTx: UserDataWriter,
    private val oTx: UserDataWriter,
  ) : MergedReader(pTx, oTx), UserDataWriter {
    override val now: Instant = pTx.now

    override fun putOrganization(organization: Organization) = pTx.putOrganization(organization)

    override fun putLibrary(library: OrganizationLibraryRecord) = pTx.putLibrary(library)

    override fun putSurvey(survey: SurveyDef) {
      if (survey.survey_id in overlaySurveyIds) oTx.putSurvey(survey) else pTx.putSurvey(survey)
    }

    override fun putSurveyRevision(survey: SurveyDef) {
      if (survey.survey_id in overlaySurveyIds) oTx.putSurveyRevision(survey)
      else pTx.putSurveyRevision(survey)
    }

    override fun purgeSurvey(tombstone: SurveyDef) {
      if (tombstone.survey_id in overlaySurveyIds) oTx.purgeSurvey(tombstone)
      else pTx.purgeSurvey(tombstone)
    }

    override fun putWorkingCopy(surveyId: String, workingCopy: SurveyDef?) {
      if (surveyId in overlaySurveyIds) oTx.putWorkingCopy(surveyId, workingCopy)
      else pTx.putWorkingCopy(surveyId, workingCopy)
    }

    override fun putAcl(acl: SurveyAcl) {
      if (acl.survey_id in overlaySurveyIds) oTx.putAcl(acl) else pTx.putAcl(acl)
    }

    override fun putEntity(entity: EntityRecord) {
      if (entity.survey_id in overlaySurveyIds) oTx.putEntity(entity) else pTx.putEntity(entity)
    }

    override fun putSubmission(submission: SubmissionRecord) {
      if (submission.survey_id in overlaySurveyIds) oTx.putSubmission(submission)
      else pTx.putSubmission(submission)
    }

    override fun putPlotLock(surveyId: String, entityId: String, lock: PlotLock?) {
      if (surveyId in overlaySurveyIds) oTx.putPlotLock(surveyId, entityId, lock)
      else pTx.putPlotLock(surveyId, entityId, lock)
    }

    override fun putImpactEvent(event: ImpactEvent) {
      if (event.survey_id in overlaySurveyIds) oTx.putImpactEvent(event)
      else pTx.putImpactEvent(event)
    }

    override fun putUser(user: UserAccount) = pTx.putUser(user)

    override fun recordApplied(scope: String, clientId: String, clientSeq: Long) {
      if (isOverlayScope(scope)) oTx.recordApplied(scope, clientId, clientSeq)
      else pTx.recordApplied(scope, clientId, clientSeq)
    }
  }

  companion object {
    suspend fun create(primary: UserDataStore, seed: UserDataSeed): UserDataStore {
      val missingSeeds = primary.read { r ->
        seed.surveys.filter { surveySeed ->
          val id = surveySeed.survey?.survey_id
          !id.isNullOrBlank() && r.survey(id) == null
        }
      }
      val seedLaunchConfigs =
        seed.surveys
          .mapNotNull { s -> s.survey?.let { it.survey_id to it.form_launch_configs } }
          .toMap()
      if (missingSeeds.isEmpty() && seedLaunchConfigs.isEmpty()) return primary
      val primaryWatermark = primary.watermark()
      val overlay =
        InMemoryUserDataStore(
            UserDataSeed(
              organizations = seed.organizations,
              libraries = seed.libraries,
              surveys = missingSeeds,
            )
          )
          .also { it.advanceClockAtLeast(primaryWatermark.epochSecond) }
      return StagedSeedOverlayUserDataStore(
        primary = primary,
        overlay = overlay,
        overlaySurveyIds = missingSeeds.mapNotNull { it.survey?.survey_id }.toSet(),
        seedLaunchConfigsBySurveyId = seedLaunchConfigs,
      )
    }

    private fun maxInstant(a: Instant, b: Instant): Instant =
      when {
        a.epochSecond > b.epochSecond -> a
        b.epochSecond > a.epochSecond -> b
        a.nano >= b.nano -> a
        else -> b
      }
  }
}
