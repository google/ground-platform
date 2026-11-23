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
package org.openforis.collectone.v2.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.mapper.toKotlinInstant
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.formatMutationTime
import org.openforis.collectone.v2.app.domain.repository.MutationRepository

/**
 * [MutationRepository] backed by the [LocalStore].
 *
 * When [trackOutbox] is on (the sync engine is connected), the state of each logged change in the
 * `Uploads` view comes from the outbox mutations for its record: queued, in progress, retrying, or
 * failed while any are pending, and uploaded once the server has confirmed them all. Pending outbox
 * data mutations without a matching log entry (for example after restoring persisted sync state on
 * relaunch) are included, and entries orphaned by deleted surveys are excluded.
 */
class MutationRepositoryImpl(
  private val store: LocalStore,
  private val trackOutbox: Boolean = false,
) : MutationRepository {
  override fun observeMutations(): Flow<List<MutationLogItem>> =
    if (!trackOutbox) {
      store.observeMutations()
    } else {
      combine(
        store.observeMutations(),
        store.observeOutbox(),
        store.observeSurveys(),
        ::reconcileWithOutbox,
      )
    }

  override suspend fun getMutations(): List<MutationLogItem> = store.transaction {
    if (trackOutbox) reconcileWithOutbox(mutations(), outbox(), surveys()) else mutations()
  }

  override suspend fun setMutations(mutations: List<MutationLogItem>) {
    store.transaction { putMutations(mutations) }
  }

  override suspend fun prependMutations(newMutations: List<MutationLogItem>) {
    store.transaction { putMutations(newMutations + mutations()) }
  }

  override suspend fun updateMutation(
    mutationId: String,
    transform: (MutationLogItem) -> MutationLogItem,
  ) {
    store.transaction {
      putMutations(mutations().map { if (it.id == mutationId) transform(it) else it })
    }
  }

  private fun reconcileWithOutbox(
    log: List<MutationLogItem>,
    outbox: List<PendingMutation>,
    surveys: List<SurveyPreviewItem>,
  ): List<MutationLogItem> {
    val knownSurveyIds = surveys.mapTo(mutableSetOf()) { it.id }
    val liveLog =
      if (knownSurveyIds.isEmpty()) log else log.filter { it.surveyId in knownSurveyIds }
    val dataOutbox = outbox.filter { m ->
      val target = m.target
      target is MutationTarget.Data &&
        (knownSurveyIds.isEmpty() || target.surveyId in knownSurveyIds) &&
        m.hasDataPayload()
    }
    val mappedLog = liveLog.map { withOutboxState(it, dataOutbox) }
    val coveredKeys = liveLog.mapTo(mutableSetOf()) { it.surveyId to recordKeyOf(it) }
    val unmatchedGroups =
      dataOutbox
        .groupBy { (it.target as MutationTarget.Data).surveyId to it.recordKey }
        .filterKeys { it !in coveredKeys }
    if (unmatchedGroups.isEmpty()) return mappedLog
    val synthesized = unmatchedGroups.mapNotNull { (key, pending) ->
      synthesizeOutboxLogItem(surveyId = key.first, recordKey = key.second, pending = pending)
    }
    return synthesized.reversed() + mappedLog
  }

  private fun withOutboxState(
    item: MutationLogItem,
    outbox: List<PendingMutation>,
  ): MutationLogItem {
    val recordKey = recordKeyOf(item)
    val pending = outbox.filter {
      it.target == MutationTarget.Data(item.surveyId) && it.recordKey == recordKey
    }
    val state =
      when {
        pending.isNotEmpty() -> syncStateOf(pending)
        item.isOutbox -> MutationSyncState.UPLOADED
        else -> item.state
      }
    val lastError = lastErrorOf(state, pending)
    if (state == item.state) return item.copy(lastError = lastError)
    val detail =
      when (state) {
        MutationSyncState.FAILED,
        MutationSyncState.RETRYING ->
          pending.firstNotNullOfOrNull { it.lastErrorDetail } ?: item.stateDetail
        MutationSyncState.UPLOADED -> "Synced to Collect One Cloud"
        else -> item.stateDetail
      }
    val completed =
      if (state == MutationSyncState.UPLOADED) {
        item.completedTimestamp ?: item.startedTimestamp ?: item.operationTimestamp
      } else {
        item.completedTimestamp
      }
    return item.copy(
      state = state,
      stateDetail = detail,
      completedTimestamp = completed,
      lastError = lastError,
    )
  }

  private fun recordKeyOf(item: MutationLogItem): String =
    if (item.operationKind.isEntityOperation) {
      Rebaser.entityKey(item.entityId)
    } else {
      item.submissionId?.takeIf { it.isNotBlank() }?.let(Rebaser::submissionKey)
        ?: Rebaser.entityKey(item.entityId)
    }

  private fun syncStateOf(pending: List<PendingMutation>): MutationSyncState =
    when {
      pending.any { it.state == OutboxState.REJECTED } -> MutationSyncState.FAILED
      pending.any { it.state == OutboxState.IN_FLIGHT } -> MutationSyncState.UPLOADING
      pending.any { it.state == OutboxState.RETRYING } -> MutationSyncState.RETRYING
      else -> MutationSyncState.QUEUED
    }

  private fun lastErrorOf(state: MutationSyncState, pending: List<PendingMutation>) =
    if (state == MutationSyncState.FAILED || state == MutationSyncState.RETRYING) {
      pending.firstNotNullOfOrNull { it.lastError }
    } else {
      null
    }

  private fun PendingMutation.hasDataPayload(): Boolean {
    val data = (payload as? MutationPayload.Data)?.mutation ?: return false
    return data.entity_mutation != null || data.submission_mutation != null
  }

  private fun synthesizeOutboxLogItem(
    surveyId: String,
    recordKey: String,
    pending: List<PendingMutation>,
  ): MutationLogItem? {
    val latest =
      pending.lastOrNull()?.let { (it.payload as? MutationPayload.Data)?.mutation } ?: return null
    val state = syncStateOf(pending)
    val timestamp =
      pending
        .asReversed()
        .firstNotNullOfOrNull { it.clientTimestamp }
        ?.let { formatMutationTime(it.toKotlinInstant()) }
        .orEmpty()
    val detail =
      pending.firstNotNullOfOrNull { it.lastErrorDetail }
        ?: if (state == MutationSyncState.UPLOADING) "Uploading" else "Waiting to upload"
    val isCreate = pending.first().baseRevision == 0L
    val sm = latest.submission_mutation
    if (sm != null) {
      val subId = sm.submission_id.ifBlank { recordKey.removePrefix("submission/") }
      val isDelete = sm.upsert?.is_deleted == true
      val opKind =
        when {
          isDelete -> MutationOperationKind.DELETE_SUBMISSION
          isCreate -> MutationOperationKind.CREATE_SUBMISSION
          else -> MutationOperationKind.UPDATE_SUBMISSION
        }
      val entityId = sm.upsert?.entity_id.orEmpty()
      val title =
        sm.upsert?.form_title?.takeIf { it.isNotBlank() }
          ?: sm.form_id.ifBlank { "Form submission" }
      val targetLabel =
        sm.upsert?.entity_label?.takeIf { it.isNotBlank() }
          ?: entityId.ifBlank { "Standalone Field Log" }
      return MutationLogItem(
        id = pending.first().mutationId,
        surveyId = surveyId,
        operationKind = opKind,
        title = title,
        targetLabel = targetLabel,
        entityId = entityId,
        submissionId = subId.ifBlank { null },
        actorName = sm.upsert?.collector_name.orEmpty(),
        state = state,
        stateDetail = detail,
        operationTimestamp = timestamp,
        startedTimestamp = timestamp.ifBlank { null },
        completedTimestamp = null,
        lastError = lastErrorOf(state, pending),
      )
    }
    val em = latest.entity_mutation ?: return null
    val entityId = em.entity_id.ifBlank { recordKey.removePrefix("entity/") }
    val isDelete = em.upsert?.is_deleted == true
    val opKind =
      when {
        isDelete -> MutationOperationKind.DELETE_ENTITY
        isCreate -> MutationOperationKind.CREATE_ENTITY
        else -> MutationOperationKind.UPDATE_ENTITY
      }
    val label = em.upsert?.label?.takeIf { it.isNotBlank() } ?: entityId
    return MutationLogItem(
      id = pending.first().mutationId,
      surveyId = surveyId,
      operationKind = opKind,
      title = label,
      targetLabel = label,
      entityId = entityId,
      submissionId = null,
      actorName = "",
      state = state,
      stateDetail = detail,
      operationTimestamp = timestamp,
      startedTimestamp = timestamp.ifBlank { null },
      completedTimestamp = null,
      lastError = lastErrorOf(state, pending),
    )
  }
}
