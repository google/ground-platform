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
 * - `accept*` stores a server copy as the new base (unless the stored base has the same or a higher
 *   version: `revision`, `version`, or `record_version`; never `commit_timestamp`), rejects pending
 *   mutations for records deleted on the server, and rebases.
 * - `rebase*` recomputes the local copy from the base and the pending mutations, projects it into
 *   the domain collections the UI observes, and drops queued mutations that no longer change
 *   anything.
 */
class Rebaser(private val tx: LocalStoreTransaction) {

  // Entities and submissions ----------------------------------------------------------------------

  /**
   * Accepts server copies of entities and submissions of [surveyId] and rebases them. With
   * [replace] (full syncs), the copies replace the stored bases even when those look newer, for
   * example after the server's data was reset.
   */
  fun acceptData(
    surveyId: String,
    entities: List<EntityRecord>,
    submissions: List<SubmissionRecord>,
    replace: Boolean = false,
  ) {
    val target = MutationTarget.Data(surveyId)
    for (record in entities) {
      if (
        replace || isNewer(record.revision, tx.entityBase(surveyId, record.entity_id)?.revision)
      ) {
        tx.putEntityBase(surveyId, record.entity_id, record)
      }
      if (record.is_deleted) reject(target, entityKey(record.entity_id), DELETED_ELSEWHERE)
    }
    for (record in submissions) {
      val stored = tx.submissionBase(surveyId, record.submission_id)
      if (replace || isNewer(record.revision, stored?.revision)) {
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

  /** Accepts a server copy of an organization and rebases it ([replace]: see [acceptData]). */
  fun acceptOrganization(record: OrganizationProto, replace: Boolean = false) {
    val id = record.organization_id
    if (replace || isNewer(record.version, tx.organizationBase(id)?.version)) {
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

  /**
   * Accepts a server copy of an organization library and rebases it ([replace]: see [acceptData]).
   */
  fun acceptLibrary(record: OrganizationLibraryRecord, replace: Boolean = false) {
    val id = record.organization_id
    if (replace || isNewer(record.version, tx.libraryBase(id)?.version)) {
      tx.putLibraryBase(id, record)
    }
    if (record.is_deleted) reject(MutationTarget.Library(id), libraryKey(id), DELETED_ELSEWHERE)
    rebaseLibrary(id)
  }

  /**
   * Recomputes the local copy of the library of [organizationId]. A library deleted on the server
   * (a tombstone base) is removed unless pending mutations recreate it; a library without any base
   * (never synced) is left as is when nothing pending produces it.
   */
  fun rebaseLibrary(organizationId: String) {
    val base = tx.libraryBase(organizationId)
    val folded =
      RecordFolds.library(
        base,
        organizationId,
        pending(MutationTarget.Library(organizationId), libraryKey(organizationId)),
      )
    val value = folded.value?.takeUnless { it.is_deleted }
    when {
      value != null -> tx.putLibrary(GrpcProtoMapper.libraryFromProto(value))
      base?.is_deleted == true -> tx.deleteLibrary(organizationId)
    }
    drop(folded.noOpMutationIds)
  }

  // Surveys ---------------------------------------------------------------------------------------

  /**
   * Accepts server copies of a survey's revisions and ACL and rebases it. [overview] is the catalog
   * entry the copies were fetched for, used for the survey list when nothing is pending. [replace]:
   * see [acceptData].
   */
  fun acceptSurvey(
    surveyId: String,
    base: SurveyBase,
    overview: SurveyOverview? = null,
    replace: Boolean = false,
  ) {
    if (overview?.state == SurveyState.DELETED) {
      acceptSurveyDeleted(surveyId)
      return
    }
    val stored = tx.surveyBase(surveyId)
    if (replace || isNewer(recordVersionOf(base), stored?.let(::recordVersionOf))) {
      tx.putSurveyBase(surveyId, base)
    }
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

  // Full syncs
  // -------------------------------------------------------------------------------------

  /*
   * A full sync (after a sync token is invalidated, or for a scope without one) replaces the bases
   * of its scope: records the client holds a base for but that are missing from the full result
   * are forgotten. Pending mutations are kept and rebased onto what remains, so a record that only
   * exists through pending mutations (for example, created offline) stays.
   */

  /** Forgets the server copies of entities and submissions of [surveyId] and rebases them. */
  fun forgetData(
    surveyId: String,
    entityIds: Collection<String>,
    submissionIds: Collection<String>,
  ) {
    entityIds.forEach { tx.putEntityBase(surveyId, it, null) }
    submissionIds.forEach { tx.putSubmissionBase(surveyId, it, null) }
    rebaseData(surveyId, entityIds, submissionIds)
  }

  /** Forgets the server copy of an organization and rebases it. */
  fun forgetOrganization(organizationId: String) {
    tx.putOrganizationBase(organizationId, null)
    rebaseOrganization(organizationId)
  }

  /**
   * Forgets the server copy of an organization library and rebases it, which removes the local
   * library unless pending mutations still create it.
   */
  fun forgetLibrary(organizationId: String) {
    tx.putLibraryBase(organizationId, null)
    val pending = pending(MutationTarget.Library(organizationId), libraryKey(organizationId))
    if (RecordFolds.library(null, organizationId, pending).value == null) {
      tx.deleteLibrary(organizationId)
    } else {
      rebaseLibrary(organizationId)
    }
  }

  /**
   * Forgets the server copies of a survey. The survey (with its data) is removed unless pending
   * mutations still create it.
   */
  fun forgetSurvey(surveyId: String) {
    tx.putSurveyBase(surveyId, null)
    val pendingForSurvey =
      tx.outbox().filter {
        (it.target as? MutationTarget.Survey)?.surveyId == surveyId
      }
    if (RecordFolds.survey(null, surveyId, pendingForSurvey).value == null) {
      tx.deleteSurvey(surveyId)
    } else {
      rebaseSurvey(surveyId)
    }
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

    /**
     * True when a server copy with version [incoming] should replace the stored base with version
     * [stored] (`docs/technical/sync-engine.md`, "Sync Tokens"): when there is no stored base, or
     * the incoming version is higher. Copies with an equal or lower version are older or duplicate
     * deliveries, whatever their `commit_timestamp`.
     */
    internal fun isNewer(incoming: Long, stored: Long?): Boolean =
      stored == null || incoming > stored

    /**
     * The shared record version of a survey's base (`SurveyDef.record_version`): the highest one
     * across its saved revision, working copy, and ACL, since a `MutateSurvey` response may only
     * carry some of them.
     */
    fun recordVersionOf(base: SurveyBase): Long =
      maxOf(
        base.saved?.record_version ?: 0L,
        base.workingCopy?.record_version ?: 0L,
        base.acl?.record_version ?: 0L,
      )
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
  val isDownloaded = surveyId in syncState().downloadedSurveyIds
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
      .copy(isDownloaded = isDownloaded)
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
