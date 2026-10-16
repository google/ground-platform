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

import com.squareup.wire.Instant
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.Organization as OrganizationProto
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyState
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.local.store.OutboxState
import org.groundplatform.v2.app.data.datasource.local.store.PendingMutation
import org.groundplatform.v2.app.data.datasource.local.store.SurveyBase
import org.groundplatform.v2.app.data.datasource.remote.GrpcProtoMapper
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.SyncStatus

/**
 * Keeps the local copy of synced records equal to their server base with pending outbox mutations
 * applied on top (`docs/technical/sync-engine.md`, "Rebase on Remote Changes"). Use it inside a
 * [LocalStoreTransaction]:
 * - `accept*` stores a server copy as the new base (unless the stored base is newer), rejects
 *   pending mutations for records deleted on the server, and rebases.
 * - `rebase*` recomputes the local copy from the base and the pending mutations, projects it into
 *   the domain collections the UI observes, and drops queued mutations that no longer change
 *   anything.
 */
class Rebaser(private val tx: LocalStoreTransaction) {

  // Entities and submissions ----------------------------------------------------------------------

  /** Accepts server copies of entities and submissions of [surveyId] and rebases them. */
  fun acceptData(
    surveyId: String,
    entities: List<EntityRecord>,
    submissions: List<SubmissionRecord>,
  ) {
    val target = MutationTarget.Data(surveyId)
    for (record in entities) {
      if (
        isNewer(
          record.commit_timestamp,
          tx.entityBase(surveyId, record.entity_id)?.commit_timestamp,
        )
      ) {
        tx.putEntityBase(surveyId, record.entity_id, record)
      }
      if (record.is_deleted) reject(target, entityKey(record.entity_id), DELETED_ELSEWHERE)
    }
    for (record in submissions) {
      val stored = tx.submissionBase(surveyId, record.submission_id)
      if (isNewer(record.commit_timestamp, stored?.commit_timestamp)) {
        tx.putSubmissionBase(surveyId, record.submission_id, record)
      }
      if (record.is_deleted) reject(target, submissionKey(record.submission_id), DELETED_ELSEWHERE)
    }
    rebaseData(surveyId, entities.map { it.entity_id }, submissions.map { it.submission_id })
  }

  /** Recomputes the local copies of [entityIds] and [submissionIds] in [surveyId]. */
  fun rebaseData(
    surveyId: String,
    entityIds: Collection<String>,
    submissionIds: Collection<String>,
  ) {
    if (entityIds.isEmpty() && submissionIds.isEmpty()) return
    val target = MutationTarget.Data(surveyId)
    val noOps = mutableSetOf<String>()
    val entityDeltas =
      entityIds.distinct().map { id ->
        val pending = pending(target, entityKey(id))
        val folded = RecordFolds.entity(tx.entityBase(surveyId, id), id, pending)
        noOps += folded.noOpMutationIds
        folded.value?.copy(sync_status_key = syncStatusOf(pending, folded.noOpMutationIds))
          ?: EntityRecord(entity_id = id, survey_id = surveyId, is_deleted = true)
      }
    val submissionDeltas =
      submissionIds.distinct().map { id ->
        val pending = pending(target, submissionKey(id))
        val folded = RecordFolds.submission(tx.submissionBase(surveyId, id), id, pending)
        noOps += folded.noOpMutationIds
        folded.value?.copy(sync_status_key = syncStatusOf(pending, folded.noOpMutationIds))
          ?: SubmissionRecord(submission_id = id, survey_id = surveyId, is_deleted = true)
      }
    tx.projectEntityAndSubmissionDeltas(surveyId, entityDeltas, submissionDeltas)
    drop(noOps)
  }

  // Organizations and libraries -------------------------------------------------------------------

  /** Accepts a server copy of an organization and rebases it. */
  fun acceptOrganization(record: OrganizationProto) {
    val id = record.organization_id
    if (isNewer(record.commit_timestamp, tx.organizationBase(id)?.commit_timestamp)) {
      tx.putOrganizationBase(id, record)
    }
    if (record.is_deleted)
      reject(MutationTarget.Organization(id), organizationKey(id), DELETED_ELSEWHERE)
    rebaseOrganization(id)
  }

  /** Recomputes the local copy of organization [organizationId]. */
  fun rebaseOrganization(organizationId: String) {
    val folded =
      RecordFolds.organization(
        tx.organizationBase(organizationId),
        organizationId,
        pending(MutationTarget.Organization(organizationId), organizationKey(organizationId)),
      )
    val value = folded.value
    if (value == null) {
      if (tx.organization(organizationId) != null) tx.deleteOrganization(organizationId)
    } else {
      tx.upsertOrganization(GrpcProtoMapper.organizationFromProto(value))
    }
    drop(folded.noOpMutationIds)
  }

  /** Accepts a server copy of an organization library and rebases it. */
  fun acceptLibrary(record: OrganizationLibraryRecord) {
    val id = record.organization_id
    if (isNewer(record.commit_timestamp, tx.libraryBase(id)?.commit_timestamp)) {
      tx.putLibraryBase(id, record)
    }
    rebaseLibrary(id)
  }

  /** Recomputes the local copy of the library of [organizationId]. */
  fun rebaseLibrary(organizationId: String) {
    val folded =
      RecordFolds.library(
        tx.libraryBase(organizationId),
        organizationId,
        pending(MutationTarget.Library(organizationId), libraryKey(organizationId)),
      )
    folded.value
      ?.takeUnless { it.is_deleted }
      ?.let { tx.putLibrary(GrpcProtoMapper.libraryFromProto(it)) }
    drop(folded.noOpMutationIds)
  }

  // Surveys ---------------------------------------------------------------------------------------

  /**
   * Accepts server copies of a survey's revisions and ACL and rebases it. [overview] is the catalog
   * entry the copies were fetched for, used for the survey list when nothing is pending.
   */
  fun acceptSurvey(surveyId: String, base: SurveyBase, overview: SurveyOverview? = null) {
    if (overview?.state == SurveyState.DELETED) {
      acceptSurveyDeleted(surveyId)
      return
    }
    val stored = tx.surveyBase(surveyId)
    val newer = isNewer(base.saved?.commit_timestamp, stored?.saved?.commit_timestamp)
    if (stored == null || newer) tx.putSurveyBase(surveyId, base)
    rebaseSurvey(surveyId, overview)
  }

  /** Removes a survey deleted on the server, rejecting its pending mutations. */
  fun acceptSurveyDeleted(surveyId: String) {
    for (m in tx.outbox()) {
      val forSurvey =
        when (val t = m.target) {
          is MutationTarget.Survey -> t.surveyId == surveyId
          is MutationTarget.Data -> t.surveyId == surveyId
          else -> false
        }
      if (forSurvey) reject(m.target, m.recordKey, DELETED_ELSEWHERE)
    }
    tx.deleteSurvey(surveyId)
  }

  /** Recomputes the local copy of survey [surveyId]. */
  fun rebaseSurvey(surveyId: String, overview: SurveyOverview? = null) {
    val pendingForSurvey =
      tx.outbox().filter {
        (it.target as? MutationTarget.Survey)?.surveyId == surveyId
      }
    val folded = RecordFolds.survey(tx.surveyBase(surveyId), surveyId, pendingForSurvey)
    val effective = folded.value
    if (effective != null) {
      val nothingPending = pendingForSurvey.none { it.state != OutboxState.REJECTED }
      tx.projectSurvey(surveyId, effective, overview.takeIf { nothingPending })
    }
    drop(folded.noOpMutationIds)
  }

  // Outbox helpers --------------------------------------------------------------------------------

  /**
   * Sync status shown for a record: failed when a mutation was rejected, uploading while any other
   * mutation is pending, and synced otherwise.
   */
  private fun syncStatusOf(pending: List<PendingMutation>, dropped: Set<String>): String {
    val remaining = pending.filterNot { it.mutationId in dropped }
    return when {
      remaining.any { it.state == OutboxState.REJECTED } -> SyncStatus.FAILED.name
      remaining.isNotEmpty() -> SyncStatus.UPLOADING.name
      else -> SyncStatus.SYNCED.name
    }
  }

  private fun pending(target: MutationTarget, recordKey: String): List<PendingMutation> =
    tx.outbox().filter { it.target == target && it.recordKey == recordKey }

  private fun drop(mutationIds: Set<String>) {
    if (mutationIds.isEmpty()) return
    tx.putOutbox(tx.outbox().filterNot { it.mutationId in mutationIds })
  }

  /**
   * Marks the not-yet-confirmed mutations for a record as rejected with [reason]. In-flight ones
   * are rejected too: the server ignores changes to a deleted record.
   */
  private fun reject(target: MutationTarget, recordKey: String, reason: String) {
    val outbox = tx.outbox()
    if (outbox.none { it.target == target && it.recordKey == recordKey }) return
    tx.putOutbox(
      outbox.map {
        if (it.target == target && it.recordKey == recordKey && it.state != OutboxState.REJECTED) {
          it.copy(state = OutboxState.REJECTED, lastError = reason)
        } else {
          it
        }
      }
    )
  }

  companion object {
    const val DELETED_ELSEWHERE = "Deleted by another collaborator"

    fun surveyKey(surveyId: String) = "survey/$surveyId"

    fun entityKey(entityId: String) = "entity/$entityId"

    fun submissionKey(submissionId: String) = "submission/$submissionId"

    fun organizationKey(organizationId: String) = "organization/$organizationId"

    fun libraryKey(organizationId: String) = "library/$organizationId"

    /** True when [incoming] isn't older than [stored] (unknown timestamps count as newer). */
    internal fun isNewer(incoming: Instant?, stored: Instant?): Boolean {
      if (incoming == null || stored == null) return true
      return incoming.getEpochSecond() > stored.getEpochSecond() ||
        (incoming.getEpochSecond() == stored.getEpochSecond() &&
          incoming.getNano() >= stored.getNano())
    }
  }
}

/**
 * Projects entity and submission records (with `is_deleted` tombstones) into the survey's domain
 * collections, linking submissions to their map features.
 */
internal fun LocalStoreTransaction.projectEntityAndSubmissionDeltas(
  surveyId: String,
  entDeltas: List<EntityRecord>,
  subDeltas: List<SubmissionRecord>,
) {
  val existingEntities = entities(surveyId)
  val existingStandalone = standaloneSubmissions(surveyId)
  val existingGeoms = submissionGeometries(surveyId)

  val allSubmissionsById = LinkedHashMap<String, SubmissionPreviewItem>()
  existingEntities.flatMap { it.submissions }.forEach { allSubmissionsById[it.id] = it }
  existingStandalone.forEach { allSubmissionsById[it.id] = it }

  val allGeomsById = LinkedHashMap<String, SubmissionGeometryPolygon>()
  existingGeoms.forEach { allGeomsById[it.id] = it }

  for (subProto in subDeltas) {
    if (subProto.is_deleted) {
      allSubmissionsById.remove(subProto.submission_id)
      val toRemoveGeoms =
        allGeomsById.values.filter { it.submissionId == subProto.submission_id }.map { it.id }
      toRemoveGeoms.forEach { allGeomsById.remove(it) }
    } else {
      allSubmissionsById[subProto.submission_id] = GrpcProtoMapper.submissionFromProto(subProto)
      subProto.geometry_polygons.forEach { polyProto ->
        val poly = GrpcProtoMapper.geometryPolygonFromProto(polyProto)
        allGeomsById[poly.id] = poly
      }
    }
  }

  val linkedSubsByEntityId =
    allSubmissionsById.values.filter { it.entityId.isNotBlank() }.groupBy { it.entityId }
  val standaloneList = allSubmissionsById.values.filter { it.entityId.isBlank() }

  val entityMap = LinkedHashMap<String, GeospatialEntityItem>()
  existingEntities.forEach { ent ->
    entityMap[ent.id] = ent.copy(submissions = linkedSubsByEntityId[ent.id].orEmpty())
  }

  for (entProto in entDeltas) {
    if (entProto.is_deleted) {
      entityMap.remove(entProto.entity_id)
    } else {
      val subs = linkedSubsByEntityId[entProto.entity_id].orEmpty()
      entityMap[entProto.entity_id] = GrpcProtoMapper.entityFromProto(entProto, subs)
    }
  }

  putEntities(surveyId, entityMap.values.toList())
  putStandaloneSubmissions(surveyId, standaloneList)
  putSubmissionGeometries(surveyId, allGeomsById.values.toList())
}

/**
 * Projects a survey's revisions and ACL into the survey list, forms, map layers, config, and (for a
 * working copy) the survey editor draft. Uses [overview] for the survey list entry when given.
 */
internal fun LocalStoreTransaction.projectSurvey(
  surveyId: String,
  effective: SurveyBase,
  overview: SurveyOverview?,
) {
  val surveyDef = effective.saved ?: effective.workingCopy ?: return
  val forms = GrpcProtoMapper.formsFromProto(surveyDef)
  val mapLayers = GrpcProtoMapper.mapLayersFromProto(surveyDef)
  val config = GrpcProtoMapper.surveyConfigFromProto(surveyDef)
  val currentEntities = entities(surveyId)
  val preview =
    if (overview != null) {
      GrpcProtoMapper.surveyPreviewFromOverviewProto(
        overview = overview,
        mapLayers = mapLayers,
        entities = currentEntities,
      )
    } else {
      GrpcProtoMapper.surveyPreviewFromProto(
        surveyDef = surveyDef,
        acl = effective.acl,
        mapLayers = mapLayers,
        entities = currentEntities,
      )
    }
  upsertSurvey(preview)
  putForms(surveyId, forms)
  putMapLayers(surveyId, mapLayers)
  putSurveyConfig(surveyId, config)
  val workingCopy = effective.workingCopy
  if (workingCopy != null) {
    putSurveyEditorDraft(
      surveyId,
      GrpcProtoMapper.surveyEditorDraftFromProto(
        surveyDef = workingCopy,
        acl = effective.acl,
        surveyPreview = preview,
        forms = forms,
        config = config,
        mapLayers = mapLayers,
        entities = currentEntities,
      ),
    )
  }
}
