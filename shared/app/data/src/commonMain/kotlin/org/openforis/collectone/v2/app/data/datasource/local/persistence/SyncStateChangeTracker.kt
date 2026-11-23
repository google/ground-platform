/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.datasource.local.persistence

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.Organization as OrganizationProto
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncState

/**
 * A [LocalStoreTransaction] that notes which parts of the sync state (outbox, bases, sync state)
 * [tx] writes, so [changes] can describe them for a [SyncStateStore]. Every other call goes
 * straight to [tx].
 */
internal class SyncStateChangeTracker(private val tx: LocalStoreTransaction) :
  LocalStoreTransaction by tx {
  /** Whether the whole store was cleared in this transaction. */
  var cleared = false
    private set

  private var outboxWritten = false
  private var syncStateWritten = false
  private val deletedSurveyIds = mutableSetOf<String>()
  private val surveyBaseIds = mutableSetOf<String>()
  private val entityBaseKeys = mutableSetOf<SurveyRecordKey>()
  private val submissionBaseKeys = mutableSetOf<SurveyRecordKey>()
  private val organizationBaseIds = mutableSetOf<String>()
  private val libraryBaseIds = mutableSetOf<String>()

  /** Whether the stored outbox may differ from the one before this transaction. */
  val outboxChanged: Boolean
    get() = cleared || outboxWritten

  /** Notes that the store was cleared; called before the store's own `clear`. */
  fun onCleared() {
    cleared = true
    outboxWritten = false
    syncStateWritten = false
    deletedSurveyIds.clear()
    surveyBaseIds.clear()
    entityBaseKeys.clear()
    submissionBaseKeys.clear()
    organizationBaseIds.clear()
    libraryBaseIds.clear()
  }

  override fun putOutbox(mutations: List<PendingMutation>) {
    tx.putOutbox(mutations)
    outboxWritten = true
  }

  override fun deleteSurvey(surveyId: String) {
    tx.deleteSurvey(surveyId)
    deletedSurveyIds += surveyId
  }

  override fun putSurveyBase(surveyId: String, base: SurveyBase?) {
    tx.putSurveyBase(surveyId, base)
    surveyBaseIds += surveyId
  }

  override fun putEntityBase(surveyId: String, entityId: String, record: EntityRecord?) {
    tx.putEntityBase(surveyId, entityId, record)
    entityBaseKeys += SurveyRecordKey(surveyId, entityId)
  }

  override fun putSubmissionBase(
    surveyId: String,
    submissionId: String,
    record: SubmissionRecord?,
  ) {
    tx.putSubmissionBase(surveyId, submissionId, record)
    submissionBaseKeys += SurveyRecordKey(surveyId, submissionId)
  }

  override fun putOrganizationBase(organizationId: String, record: OrganizationProto?) {
    tx.putOrganizationBase(organizationId, record)
    organizationBaseIds += organizationId
  }

  override fun putLibraryBase(organizationId: String, record: OrganizationLibraryRecord?) {
    tx.putLibraryBase(organizationId, record)
    libraryBaseIds += organizationId
  }

  override fun putSyncState(state: SyncState) {
    tx.putSyncState(state)
    syncStateWritten = true
  }

  // Delegation would send this default method to [tx], bypassing putSyncState above.
  override fun updateSyncState(transform: (SyncState) -> SyncState): SyncState {
    val updated = transform(syncState())
    putSyncState(updated)
    return updated
  }

  /**
   * The sync state changes this transaction made, read from its final state. [persistedOutbox] is
   * the outbox as stored before the transaction, keyed by mutation ID.
   */
  fun changes(persistedOutbox: Map<String, PendingMutation>): SyncStateChange {
    val outbox = if (outboxChanged) outbox() else emptyList()
    val before = if (cleared) emptyMap() else persistedOutbox
    return SyncStateChange(
      clearAll = cleared,
      deletedSurveyIds = deletedSurveyIds.toSet(),
      upsertedMutations =
        outbox.filter { before[it.mutationId] != it }.map(ClientSyncStateCodec::toRecord),
      deletedMutationIds =
        if (outboxChanged) before.keys - outbox.map { it.mutationId }.toSet() else emptySet(),
      surveyBases =
        surveyBaseIds.associateWith { id ->
          surveyBase(id)?.let { ClientSyncStateCodec.toRecord(id, it) }
        },
      entityBases = entityBaseKeys.associateWith { entityBase(it.surveyId, it.recordId) },
      submissionBases =
        submissionBaseKeys.associateWith { submissionBase(it.surveyId, it.recordId) },
      organizationBases = organizationBaseIds.associateWith(::organizationBase),
      libraryBases = libraryBaseIds.associateWith(::libraryBase),
      syncState = if (syncStateWritten) syncState() else null,
    )
  }
}
