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
package org.groundplatform.v2.app.domain.usecase

import org.groundplatform.v2.app.domain.model.MutationSyncState
import org.groundplatform.v2.app.domain.model.SyncStatus
import org.groundplatform.v2.app.domain.repository.ConnectivityRepository
import org.groundplatform.v2.app.domain.repository.ImpactEventRepository
import org.groundplatform.v2.app.domain.repository.MutationRepository
import org.groundplatform.v2.app.domain.repository.OutboxSyncRepository
import org.groundplatform.v2.app.domain.repository.SurveyRepository
import org.groundplatform.v2.app.domain.repository.TransactionRunner

/**
 * Multi-repository domain use case orchestrating [MutationRepository] and [SurveyRepository] to
 * synchronize single or batch outbox mutations and update linked entity and submission
 * [SyncStatus].
 *
 * With an [outboxSyncRepository] (the sync engine is connected to the user data service), uploads
 * go through the real outbox: rejected changes are requeued and the sync engine runs now; states in
 * the `Uploads` view follow the outbox. Without one (demo and tests), uploads are simulated.
 */
class SyncMutationsUseCase(
  private val mutationRepository: MutationRepository,
  private val surveyRepository: SurveyRepository,
  private val transactionRunner: TransactionRunner,
  private val connectivityRepository: ConnectivityRepository = ConnectivityRepository(),
  /** Impact events recorded offline upload with the outbox. */
  private val impactEventRepository: ImpactEventRepository? = null,
  /** The sync engine's outbox, when connected to the user data service. */
  private val outboxSyncRepository: OutboxSyncRepository? = null,
) {
  /**
   * Synchronizes a single Outbox mutation ([mutationId]), transitioning its state to
   * [MutationSyncState.UPLOADED] and updating the target entity or submission [SyncStatus] to
   * [SyncStatus.SYNCED]. Returns the notice message if synchronized, or `null` if not found.
   */
  suspend fun syncSingleMutation(mutationId: String): String? {
    val outbox = outboxSyncRepository ?: return simulateSingleMutation(mutationId)
    val target =
      mutationRepository.getMutations().firstOrNull { it.id == mutationId } ?: return null
    if (!connectivityRepository.isOnline()) return OFFLINE_UPLOAD_NOTICE
    outbox.retryRecord(target.surveyId, target.entityId, target.submissionId)
    outbox.syncNow()
    return "Uploading \"${target.title}\""
  }

  private suspend fun simulateSingleMutation(mutationId: String): String? = transactionRunner tx@{
    val mutations = mutationRepository.getMutations()
    val target = mutations.firstOrNull { it.id == mutationId } ?: return@tx null
    if (!connectivityRepository.isOnline()) {
      return@tx OFFLINE_UPLOAD_NOTICE
    }
    val started = target.startedTimestamp ?: "2026-09-19 09:42:02 UTC"
    val completed = "2026-09-19 09:42:06 UTC"
    mutationRepository.updateMutation(mutationId) { item ->
      item.copy(
        state = MutationSyncState.UPLOADED,
        stateDetail = "Synced to Collect One Cloud • Commit rev #1052",
        startedTimestamp = started,
        completedTimestamp = completed,
      )
    }
    val subId = target.submissionId
    if (subId != null) {
      surveyRepository.updateSubmissionSyncStatus(subId, SyncStatus.SYNCED)
    } else {
      surveyRepository.updateEntitySyncStatus(target.entityId, SyncStatus.SYNCED)
    }
    "Uploaded mutation \"${target.title}\" ($completed)"
  }

  /**
   * Synchronizes all pending/in-progress mutations in the Outbox for [activeSurveyId],
   * transitioning them to [MutationSyncState.UPLOADED] and marking all entities and standalone
   * submissions in [SurveyRepository] as [SyncStatus.SYNCED]. Returns the notice message if any
   * outbox mutations were synced, or `null` when outbox is empty.
   */
  suspend fun syncAllOutboxMutations(activeSurveyId: String): String? {
    val outbox = outboxSyncRepository ?: return simulateAllOutboxMutations(activeSurveyId)
    val pending =
      mutationRepository.getMutations().count {
        it.surveyId == activeSurveyId && it.isOutbox
      }
    val pendingEvents = impactEventRepository?.getEvents()?.count { !it.isUploaded } ?: 0
    if (pending == 0 && pendingEvents == 0) return null
    if (!connectivityRepository.isOnline()) return OFFLINE_UPLOAD_NOTICE
    outbox.retryRejected(activeSurveyId)
    outbox.syncNow()
    val remaining =
      mutationRepository.getMutations().count {
        it.surveyId == activeSurveyId && it.isOutbox
      }
    return if (remaining == 0) {
      "Uploaded all pending changes to Collect One Cloud"
    } else {
      "Uploading $remaining pending change(s) to Collect One Cloud"
    }
  }

  private suspend fun simulateAllOutboxMutations(activeSurveyId: String): String? =
    transactionRunner tx@{
      val allMutations = mutationRepository.getMutations()
      val outboxCount = allMutations.count { it.surveyId == activeSurveyId && it.isOutbox }
      val pendingEvents = impactEventRepository?.getEvents()?.count { !it.isUploaded } ?: 0
      if (outboxCount == 0 && pendingEvents == 0) return@tx null
      if (!connectivityRepository.isOnline()) {
        return@tx OFFLINE_UPLOAD_NOTICE
      }
      // Impact events recorded offline (e.g. receipts) upload with the outbox.
      impactEventRepository?.markAllUploaded()
      if (outboxCount == 0) {
        return@tx "Uploaded $pendingEvents activity record(s) to Collect One Cloud"
      }
      val completed = "2026-09-19 09:42:10 UTC"
      mutationRepository.setMutations(
        allMutations.map { item ->
          if (item.isOutbox && item.surveyId == activeSurveyId) {
            item.copy(
              state = MutationSyncState.UPLOADED,
              stateDetail = "Synced to Collect One Cloud • Batch commit rev #1055",
              startedTimestamp = item.startedTimestamp ?: "2026-09-19 09:42:04 UTC",
              completedTimestamp = completed,
            )
          } else {
            item
          }
        }
      )
      surveyRepository.markAllSynced()
      "Uploaded all $outboxCount Outbox mutation(s) to Collect One Cloud ($completed)"
    }

  companion object {
    const val OFFLINE_UPLOAD_NOTICE =
      "Cannot upload while offline. Changes are saved on this device and will upload when connected."
  }
}
