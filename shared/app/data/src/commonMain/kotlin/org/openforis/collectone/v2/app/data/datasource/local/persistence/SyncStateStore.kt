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
import openforis.collectone.v1.userdata.ClientSyncState
import openforis.collectone.v1.userdata.PendingMutationRecord
import openforis.collectone.v1.userdata.SurveyBaseRecord
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncState

/**
 * Durable storage of the sync engine's state on the device: the outbox, the server bases, the
 * install's mutation identity, and the sync tokens (`docs/technical/client/local-data-store.md`).
 *
 * Every write is atomic: after a crash the stored state is the one before or after the write.
 */
interface SyncStateStore {
  /** The stored state; an empty [ClientSyncState] when nothing was stored. */
  suspend fun load(): ClientSyncState

  /**
   * The account the stored state belongs to (see [replace]); blank when nothing was stored for a
   * real account.
   */
  suspend fun account(): String

  /**
   * Replaces everything stored with [state], belonging to [account]: the signed-in account's email,
   * or blank for sample and development accounts.
   */
  suspend fun replace(state: ClientSyncState, account: String = "")

  /** Applies [change] to what is stored. */
  suspend fun apply(change: SyncStateChange)
}

/** A record of a survey's data (an entity or a submission), keyed by survey and record ID. */
data class SurveyRecordKey(val surveyId: String, val recordId: String)

/**
 * Changes one [org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore] transaction
 * made to the sync state, applied in this order: [clearAll], then [deletedSurveyIds], then the
 * rest. A `null` record forgets the stored one.
 */
data class SyncStateChange(
  /** Forget everything stored first, including its account: the store was cleared. */
  val clearAll: Boolean = false,
  /** Surveys deleted from the store; their bases are forgotten. */
  val deletedSurveyIds: Set<String> = emptySet(),
  val upsertedMutations: List<PendingMutationRecord> = emptyList(),
  val deletedMutationIds: Set<String> = emptySet(),
  val surveyBases: Map<String, SurveyBaseRecord?> = emptyMap(),
  val entityBases: Map<SurveyRecordKey, EntityRecord?> = emptyMap(),
  val submissionBases: Map<SurveyRecordKey, SubmissionRecord?> = emptyMap(),
  val organizationBases: Map<String, OrganizationProto?> = emptyMap(),
  val libraryBases: Map<String, OrganizationLibraryRecord?> = emptyMap(),
  /** The new sync state, or `null` when it didn't change. */
  val syncState: SyncState? = null,
) {
  val isEmpty: Boolean
    get() = this == NONE

  private companion object {
    val NONE = SyncStateChange()
  }
}
